package dev.voidmc.mettle.character.skill.defense;

import dev.voidmc.mettle.Mettle;
import dev.voidmc.mettle.character.Perks;
import dev.voidmc.mettle.character.skill.DefenseSkill;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.Npc;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Path B, Ironclad: armour, and lasting the fight. Mender and Battle-Worn change wear (from
 * {@code mixin.defense.ItemStackMixin}); Unbowed changes effects as they land; Second Wind and Hold the Line react to
 * hits; Juggernaut is an attribute while in full armour plus a check each tick while sprinting.
 *
 * <p>Modifier ids here do not start with {@code perk/}, so the effects applier leaves them alone; {@link #refresh}
 * keeps them right (every half second, on equipment change and after respawn, which drops every modifier).
 */
public final class Ironclad {
	private static final Identifier HOLD_THE_LINE = Mettle.id("defense/hold_the_line");
	private static final Identifier JUGGERNAUT = Mettle.id("defense/juggernaut");
	private static final Identifier BATTLE_WORN = Mettle.id("defense/battle_worn");
	private static final List<Holder<MobEffect>> UNBOWED = List.of(MobEffects.POISON, MobEffects.WITHER, MobEffects.SLOWNESS, MobEffects.WEAKNESS);
	private static final int BOWL_COOLDOWN = 20;

	private record Hold(int stacks, long until) {
	}

	private static final Map<UUID, Hold> HOLDING = new HashMap<>();
	private static final Map<UUID, Integer> WORN_PIECES = new HashMap<>();
	private static final Map<Entity, Long> BOWLED_AT = new WeakHashMap<>();

	private Ironclad() {
	}

	// ---------------------------------------------------------------- wear

	/** ItemStack#hurtAndBreak's amount for gear a player wears: Mender spares some, Battle-Worn stops at 1 durability. */
	public static int wear(ItemStack stack, int amount, LivingEntity owner, EquipmentSlot slot) {
		if (amount <= 0 || !(owner instanceof ServerPlayer player) || !stack.isDamageableItem()) {
			return amount;
		}
		boolean armour = Armour.isArmourSlot(slot) && Armour.value(stack, slot)[0] > 0;
		boolean shield = stack.has(DataComponents.BLOCKS_ATTACKS);
		if (!armour && !shield) {
			return amount;
		}
		if (Perks.has(player, DefenseSkill.MENDER)) {
			int kept = DefenseRules.mender(amount, () -> player.getRandom().nextDouble());
			if (kept < amount) {
				DefenseSkill.feedback(player, DefenseSkill.MENDER, Component.translatable(DefenseSkill.chip(DefenseSkill.MENDER)), 1200);
			}
			amount = kept;
		}
		if (armour && Perks.has(player, DefenseSkill.BATTLE_WORN)) {
			amount = DefenseRules.battleWorn(amount, stack.getDamageValue(), stack.getMaxDamage());
		}
		return amount;
	}

	// ---------------------------------------------------------------- effects

	/** LivingEntity#addEffect's effect: Unbowed halves poison, wither, slowness and weakness on a player in full armour. */
	public static MobEffectInstance effect(LivingEntity entity, MobEffectInstance effect) {
		if (!(entity instanceof ServerPlayer player) || effect.getDuration() <= 1 || UNBOWED.stream().noneMatch(effect::is)
			|| !Perks.has(player, DefenseSkill.UNBOWED) || !Armour.full(player)) {
			return effect;
		}
		DefenseSkill.feedback(player, DefenseSkill.UNBOWED, Component.translatable(DefenseSkill.chip(DefenseSkill.UNBOWED),
			effect.getEffect().value().getDisplayName()), 40);
		return new MobEffectInstance(effect.getEffect(), DefenseRules.unbowed(effect.getDuration()), effect.getAmplifier(), effect.isAmbient(),
			effect.isVisible(), effect.showIcon());
	}

	// ---------------------------------------------------------------- hits taken

	public static void afterHurt(ServerPlayer player, DamageSource source, float taken, boolean blocked) {
		long now = player.level().getGameTime();
		// Second Wind: below 4 hearts, a burst of Regeneration II, once every five minutes.
		if (player.isAlive() && player.getHealth() < DefenseRules.SECOND_WIND_BELOW && Perks.has(player, DefenseSkill.SECOND_WIND)
			&& Cooldowns.ready(player, Cooldowns.SECOND_WIND)) {
			Cooldowns.start(player, Cooldowns.SECOND_WIND, DefenseRules.SECOND_WIND_COOLDOWN);
			player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, DefenseRules.SECOND_WIND_TICKS, 1));
			DefenseSkill.feedback(player, DefenseSkill.SECOND_WIND, Component.translatable(DefenseSkill.chip(DefenseSkill.SECOND_WIND)));
		}
		// Hold the Line: a blow from something (not fire or a fall) while standing your ground hardens you.
		if (source.getEntity() != null && standingStill(player) && Perks.has(player, DefenseSkill.HOLD_THE_LINE)) {
			Hold before = HOLDING.get(player.getUUID());
			int stacks = DefenseRules.holdTheLine(before == null ? 0 : before.stacks());
			HOLDING.put(player.getUUID(), new Hold(stacks, now + DefenseRules.HOLD_THE_LINE_TICKS));
			setModifier(player, Attributes.ARMOR, HOLD_THE_LINE, stacks);
			if (before == null || before.stacks() != stacks) {
				DefenseSkill.feedback(player, DefenseSkill.HOLD_THE_LINE, Component.translatable(DefenseSkill.chip(DefenseSkill.HOLD_THE_LINE), stacks));
			}
		}
	}

	/** Whether the player's client reported no walking this tick (getting knocked about is not walking away). */
	public static boolean standingStill(ServerPlayer player) {
		return player.getKnownMovement().horizontalDistanceSqr() < 0.0025;
	}

	public static int holdTheLineStacks(ServerPlayer player) {
		Hold hold = HOLDING.get(player.getUUID());
		return hold == null ? 0 : hold.stacks();
	}

	// ---------------------------------------------------------------- every half second

	/** Puts every Ironclad modifier in the state the player's perks and gear call for. Cheap when nothing changed. */
	public static void refresh(ServerPlayer player) {
		long now = player.level().getGameTime();
		Hold hold = HOLDING.get(player.getUUID());
		if (hold != null && (now >= hold.until() || !Perks.has(player, DefenseSkill.HOLD_THE_LINE))) {
			HOLDING.remove(player.getUUID());
			hold = null;
		}
		setModifier(player, Attributes.ARMOR, HOLD_THE_LINE, hold == null ? 0 : hold.stacks());

		// Juggernaut: in full armour nothing knocks you back, blows or blasts.
		double steady = Perks.has(player, DefenseSkill.JUGGERNAUT) && Armour.full(player) ? 1.0 : 0.0;
		setModifier(player, Attributes.KNOCKBACK_RESISTANCE, JUGGERNAUT, steady);
		setModifier(player, Attributes.EXPLOSION_KNOCKBACK_RESISTANCE, JUGGERNAUT, steady);

		// Battle-Worn: a piece held at 1 durability protects at half strength until it is repaired.
		double armour = 0;
		double toughness = 0;
		int worn = 0;
		ItemStack newest = ItemStack.EMPTY;
		if (Perks.has(player, DefenseSkill.BATTLE_WORN)) {
			for (EquipmentSlot slot : Armour.SLOTS) {
				ItemStack stack = player.getItemBySlot(slot);
				if (stack.isDamageableItem() && stack.nextDamageWillBreak()) {
					double[] value = Armour.value(stack, slot);
					if (value[0] > 0) {
						armour += value[0];
						toughness += value[1];
						worn++;
						newest = stack;
					}
				}
			}
		}
		setModifier(player, Attributes.ARMOR, BATTLE_WORN, -armour / 2);
		setModifier(player, Attributes.ARMOR_TOUGHNESS, BATTLE_WORN, -toughness / 2);
		int before = WORN_PIECES.getOrDefault(player.getUUID(), 0);
		if (worn != before) {
			WORN_PIECES.put(player.getUUID(), worn);
			if (worn > before) {
				DefenseSkill.feedback(player, DefenseSkill.BATTLE_WORN, Component.translatable(DefenseSkill.chip(DefenseSkill.BATTLE_WORN), newest.getHoverName()));
			}
		}
	}

	private static void setModifier(ServerPlayer player, Holder<Attribute> attribute, Identifier id, double amount) {
		AttributeInstance instance = player.getAttribute(attribute);
		if (instance == null) {
			return;
		}
		AttributeModifier current = instance.getModifier(id);
		if (amount == 0) {
			if (current != null) {
				instance.removeModifier(id);
			}
			return;
		}
		if (current == null || current.amount() != amount) {
			instance.removeModifier(id);
			instance.addTransientModifier(new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_VALUE));
		}
	}

	// ---------------------------------------------------------------- Juggernaut's charge (every tick, sprinters only)

	/** Juggernaut: sprinting in full armour into a mob bowls it over. Returns how many were bowled (for tests). */
	public static int juggernautCharge(ServerPlayer player) {
		if (!player.isSprinting() || player.isSpectator() || player.isPassenger() || !Perks.has(player, DefenseSkill.JUGGERNAUT)
			|| !Armour.full(player)) {
			return 0;
		}
		Vec3 heading = new Vec3(player.getKnownMovement().x, 0, player.getKnownMovement().z);
		if (heading.lengthSqr() < 1.0E-4) {
			Vec3 look = player.getLookAngle();
			heading = new Vec3(look.x, 0, look.z);
		}
		if (heading.lengthSqr() < 1.0E-6) {
			return 0;
		}
		Vec3 dir = heading.normalize();
		ServerLevel level = player.level();
		long now = level.getGameTime();
		AABB reach = player.getBoundingBox().expandTowards(dir.x * 0.6, 0, dir.z * 0.6).inflate(0.2, 0, 0.2);
		int bowled = 0;
		for (Mob mob : level.getEntitiesOfClass(Mob.class, reach, m -> bowlable(player, m))) {
			Vec3 to = new Vec3(mob.getX() - player.getX(), 0, mob.getZ() - player.getZ());
			Long last = BOWLED_AT.get(mob);
			if (to.dot(dir) <= 0 || (last != null && now - last < BOWL_COOLDOWN && now >= last)) {
				continue;
			}
			BOWLED_AT.put(mob, now);
			// Caused by the player but not dealt with a weapon, so no weapon skill learns from it.
			DamageSource source = new DamageSource(player.damageSources().playerAttack(player).typeHolder(), null, player);
			mob.hurtServer(level, source, DefenseRules.JUGGERNAUT_DAMAGE);
			mob.knockback(DefenseRules.THROW_POWER, player.getX() - mob.getX(), player.getZ() - mob.getZ(), source, DefenseRules.JUGGERNAUT_DAMAGE);
			Daze.apply(mob, DefenseRules.DAZE_TICKS);
			level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 1f, 0.6f);
			DefenseSkill.feedback(player, DefenseSkill.JUGGERNAUT, Component.translatable(DefenseSkill.chip(DefenseSkill.JUGGERNAUT), mob.getDisplayName()));
			bowled++;
		}
		return bowled;
	}

	/** Hostiles, and anything else that is not a villager, someone's pet or a baby. */
	private static boolean bowlable(ServerPlayer player, Mob mob) {
		if (!mob.isAlive() || mob.hasPassenger(player) || mob instanceof Npc || (mob instanceof OwnableEntity pet && pet.getOwnerReference() != null)) {
			return false;
		}
		return mob instanceof Enemy || !mob.isBaby();
	}

	public static void forget(UUID player) {
		HOLDING.remove(player);
		WORN_PIECES.remove(player);
	}
}
