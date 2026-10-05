package dev.voidmc.mettle.character.skill.heavy;

import dev.voidmc.mettle.Mettle;
import dev.voidmc.mettle.character.skill.HeavyWeaponsSkill;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Throwing Axe: an axe in flight. It strikes the first thing in its path for the full strength of a melee swing (the
 * thrower's attack damage with that axe, plus its enchantments), then drops; it sticks where it hits a block and lies
 * there until its thrower walks over it. Beyond 16 blocks it loses its edge and tumbles to the ground.
 *
 * <p>The item it carries is the thrown stack itself, so enchantments, name and wear come back with it. It is not an
 * arrow on purpose: Combat counts arrow hits as Marksmanship, and this is axe work (see {@link HeavyWeaponsSkill}).
 */
public class ThrownAxe extends ThrowableItemProjectile {
	public static final EntityType<ThrownAxe> TYPE = register();

	private static final EntityDataAccessor<Boolean> LANDED = SynchedEntityData.defineId(ThrownAxe.class, EntityDataSerializers.BOOLEAN);
	/** After this long on the ground anyone may pick it up, so an axe is never lost to a thrower who left. */
	private static final int OWNER_ONLY_TICKS = 1200;

	private double damage = 1.0;
	private double originX;
	private double originZ;
	private boolean dealtDamage;
	/** Thrown in creative: the axe stayed in hand, so this copy is never picked up and goes away after landing. */
	private boolean creativeCopy;
	private int landedTicks;

	public ThrownAxe(EntityType<? extends ThrownAxe> type, Level level) {
		super(type, level);
	}

	private ThrownAxe(Level level, ServerPlayer owner, ItemStack axe, double damage) {
		super(TYPE, owner, level, axe);
		this.damage = damage;
		this.originX = owner.getX();
		this.originZ = owner.getZ();
	}

	private static EntityType<ThrownAxe> register() {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, Mettle.id("thrown_axe"));
		return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, EntityType.Builder.<ThrownAxe>of(ThrownAxe::new, MobCategory.MISC)
			.sized(0.5f, 0.5f).clientTrackingRange(4).updateInterval(10).build(key));
	}

	/** Throws the axe in the player's main hand. The hit is worth what a full swing with it would deal right now. */
	public static void throwFrom(ServerPlayer player, ServerLevel level, ItemStack held) {
		double damage = player.getAttributeValue(Attributes.ATTACK_DAMAGE);
		ItemStack flying = held.copyWithCount(1);
		boolean creative = player.hasInfiniteMaterials();
		if (!creative) {
			flying.hurtWithoutBreaking(1, player);
		}
		ThrownAxe axe = new ThrownAxe(level, player, flying, damage);
		axe.creativeCopy = creative;
		axe.shootFromRotation(player, player.getXRot(), player.getYRot(), 0f, (float) HeavyRules.THROW_SPEED, 1.0f);
		level.addFreshEntity(axe);
		if (!creative) {
			held.shrink(1);
		}
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.TRIDENT_THROW, SoundSource.PLAYERS, 1f, 0.7f);
		player.awardStat(Stats.ITEM_USED.get(flying.getItem()));
		HeavyWeaponsSkill.chip(player, HeavyWeaponsSkill.THROWING_AXE);
	}

	@Override
	protected Item getDefaultItem() {
		return Items.IRON_AXE;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(LANDED, false);
	}

	public boolean landed() {
		return this.entityData.get(LANDED);
	}

	private void setLanded(boolean landed) {
		this.entityData.set(LANDED, landed);
		this.landedTicks = 0;
	}

	@Override
	protected double getDefaultGravity() {
		// Heavier than a snowball: a thrown axe drops a little over its sixteen blocks.
		return 0.04;
	}

	@Override
	public void tick() {
		if (this.landed()) {
			this.baseTick();
			if (this.level() instanceof ServerLevel) {
				this.landedTicks++;
				if (this.creativeCopy && this.landedTicks > 40) {
					this.discard();
				} else if (this.landedTicks % 5 == 0 && this.level().noCollision(new AABB(this.position(), this.position()).inflate(0.06))) {
					// The block it was stuck in is gone: fall again.
					this.setLanded(false);
					this.dealtDamage = true;
				}
			}
			return;
		}
		super.tick();
		if (!this.dealtDamage && this.level() instanceof ServerLevel) {
			double dx = this.getX() - this.originX;
			double dz = this.getZ() - this.originZ;
			if (dx * dx + dz * dz > HeavyRules.THROW_RANGE * HeavyRules.THROW_RANGE) {
				// Out of range: the throw is spent and the axe drops.
				this.dealtDamage = true;
				this.setDeltaMovement(this.getDeltaMovement().multiply(0.2, 0.5, 0.2));
			}
		}
	}

	@Override
	protected boolean canHitEntity(Entity entity) {
		return !this.dealtDamage && super.canHitEntity(entity);
	}

	@Override
	protected void onHitEntity(EntityHitResult result) {
		Entity target = result.getEntity();
		this.dealtDamage = true;
		Entity owner = this.getOwner();
		if (this.level() instanceof ServerLevel level) {
			DamageSource source = this.damageSources().thrown(this, owner == null ? this : owner);
			float amount = EnchantmentHelper.modifyDamage(level, this.getItem(), target, source, (float) this.damage);
			float before = target instanceof LivingEntity living ? living.getHealth() : 0f;
			if (target.hurtServer(level, source, amount) && target instanceof LivingEntity living) {
				EnchantmentHelper.doPostAttackEffectsWithItemSource(level, living, source, this.getItem());
				if (owner instanceof ServerPlayer player) {
					HeavyWeaponsSkill.thrownHit(player, living, before - Math.max(0f, living.getHealth()));
				}
			}
			level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.TRIDENT_HIT, SoundSource.PLAYERS, 1f, 0.8f);
		}
		// Glance off and drop at the target's feet.
		this.setDeltaMovement(this.getDeltaMovement().multiply(-0.05, 0.1, -0.05));
	}

	@Override
	protected void onHitBlock(BlockHitResult result) {
		super.onHitBlock(result);
		Vec3 movement = this.getDeltaMovement();
		// Bite into the block: stop just short of where it struck.
		this.setPos(result.getLocation().subtract(movement.normalize().scale(0.05)));
		this.setDeltaMovement(Vec3.ZERO);
		this.setLanded(true);
		this.dealtDamage = true;
		if (!this.level().isClientSide()) {
			this.playSound(SoundEvents.TRIDENT_HIT_GROUND, 1f, 0.8f);
		}
	}

	@Override
	public void playerTouch(Player player) {
		if (this.level().isClientSide() || !this.landed() || this.creativeCopy) {
			return;
		}
		boolean mayTake = this.ownedBy(player) || this.getOwner() == null || this.landedTicks > OWNER_ONLY_TICKS;
		if (mayTake && player.getInventory().add(this.getItem().copy())) {
			player.take(this, 1);
			this.discard();
		}
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putBoolean("landed", this.landed());
		output.putDouble("damage", this.damage);
		output.putBoolean("dealt_damage", this.dealtDamage);
		output.putBoolean("creative_copy", this.creativeCopy);
		output.putInt("landed_ticks", this.landedTicks);
		output.putDouble("origin_x", this.originX);
		output.putDouble("origin_z", this.originZ);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		this.setLanded(input.getBooleanOr("landed", false));
		this.damage = input.getDoubleOr("damage", 1.0);
		this.dealtDamage = input.getBooleanOr("dealt_damage", false);
		this.creativeCopy = input.getBooleanOr("creative_copy", false);
		this.landedTicks = input.getIntOr("landed_ticks", 0);
		this.originX = input.getDoubleOr("origin_x", this.getX());
		this.originZ = input.getDoubleOr("origin_z", this.getZ());
	}

	@Override
	public boolean shouldRenderAtSqrDistance(double distance) {
		return distance < 64 * 64;
	}
}
