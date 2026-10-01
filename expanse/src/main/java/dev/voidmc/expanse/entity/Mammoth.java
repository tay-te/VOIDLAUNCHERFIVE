package dev.voidmc.expanse.entity;

import dev.voidmc.expanse.Expanse;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Shearable;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.BreedGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.FollowParentGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.TemptGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.storage.loot.LootTable;
import org.jspecify.annotations.Nullable;

/**
 * Woolly mammoth: slow, enormous, peaceful until struck, and then it will not stop until whatever
 * hurt it is gone. Shears take its winter coat (brown wool), which grows back in about five minutes.
 */
public class Mammoth extends Animal implements Shearable {
	private static final EntityDataAccessor<Boolean> SHEARED = SynchedEntityData.defineId(Mammoth.class, EntityDataSerializers.BOOLEAN);
	private static final ResourceKey<LootTable> SHEARING = ResourceKey.create(Registries.LOOT_TABLE, Expanse.id("shearing/mammoth"));
	private static final int REGROW_TICKS = 6000;
	private int regrow;

	public Mammoth(EntityType<? extends Mammoth> type, Level level) {
		super(type, level);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Animal.createAnimalAttributes()
			.add(Attributes.MAX_HEALTH, 70.0)
			.add(Attributes.MOVEMENT_SPEED, 0.18F)
			.add(Attributes.KNOCKBACK_RESISTANCE, 0.85)
			.add(Attributes.ATTACK_DAMAGE, 9.0)
			.add(Attributes.ATTACK_KNOCKBACK, 1.5)
			.add(Attributes.STEP_HEIGHT, 1.1);
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.25, true));
		this.goalSelector.addGoal(2, new BreedGoal(this, 1.0));
		this.goalSelector.addGoal(3, new TemptGoal(this, 1.1, this::isFood, false));
		this.goalSelector.addGoal(4, new FollowParentGoal(this, 1.1));
		this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8));
		this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 10.0F));
		this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
		this.targetSelector.addGoal(1, new HurtByTargetGoal(this).setAlertOthers());
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(SHEARED, false);
	}

	public boolean isSheared() {
		return this.entityData.get(SHEARED);
	}

	private void setSheared(boolean sheared) {
		this.entityData.set(SHEARED, sheared);
		this.regrow = sheared ? REGROW_TICKS : 0;
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putBoolean("Sheared", this.isSheared());
		output.putInt("Regrow", this.regrow);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		this.entityData.set(SHEARED, input.getBooleanOr("Sheared", false));
		this.regrow = input.getIntOr("Regrow", 0);
	}

	@Override
	public void aiStep() {
		super.aiStep();
		if (!this.level().isClientSide() && this.isSheared() && --this.regrow <= 0) {
			this.setSheared(false);
		}
	}

	@Override
	public InteractionResult mobInteract(Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (stack.is(Items.SHEARS)) {
			if (this.level() instanceof ServerLevel level && this.readyForShearing()) {
				this.shear(level, SoundSource.PLAYERS, stack);
				this.gameEvent(GameEvent.SHEAR, player);
				stack.hurtAndBreak(1, player, hand.asEquipmentSlot());
				return InteractionResult.SUCCESS_SERVER;
			}
			return InteractionResult.CONSUME;
		}
		return super.mobInteract(player, hand);
	}

	@Override
	public void shear(ServerLevel level, SoundSource source, ItemStack tool) {
		level.playSound(null, this, SoundEvents.SHEEP_SHEAR, source, 1.0F, 0.7F);
		this.dropFromShearingLootTable(level, SHEARING, tool, (l, drop) -> {
			for (int i = 0; i < drop.getCount(); i++) {
				this.spawnAtLocation(l, drop.copyWithCount(1), 2.0F);
			}
		});
		this.setSheared(true);
	}

	@Override
	public boolean readyForShearing() {
		return this.isAlive() && !this.isSheared() && !this.isBaby();
	}

	@Override
	public boolean isFood(ItemStack stack) {
		return stack.is(Items.HAY_BLOCK) || stack.is(Items.WHEAT);
	}

	@Override
	public @Nullable AgeableMob getBreedOffspring(ServerLevel level, AgeableMob partner) {
		return ExpanseEntities.MAMMOTH.create(level, EntitySpawnReason.BREEDING);
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return SoundEvents.CAMEL_AMBIENT;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.CAMEL_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.CAMEL_DEATH;
	}

	@Override
	protected void playStepSound(BlockPos pos, BlockState state) {
		this.playSound(SoundEvents.CAMEL_STEP, 0.6F, 0.6F);
	}

	@Override
	public float getVoicePitch() {
		return super.getVoicePitch() * 0.55F;
	}

	@Override
	protected float getSoundVolume() {
		return 1.4F;
	}
}
