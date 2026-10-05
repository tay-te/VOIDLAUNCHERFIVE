package dev.voidmc.mettle.character.skill.blades;

import dev.voidmc.mettle.Mettle;
import dev.voidmc.mettle.character.Perks;
import dev.voidmc.mettle.character.skill.BladesSkill;
import dev.voidmc.mettle.character.skill.Combat;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.AttackRange;

/**
 * Perks that depend on what is in your hand: Fencer (swing speed), Measure (reach), Wide Arc (sweep damage) only while
 * a blade is held, Light Feet only on blade hits, and Weapon Master's switch. The modifiers have their own ids
 * ({@code mettle:blades/*}), outside the perk applier's {@code mettle:perk/*} set, because they come and go with the
 * held item; they are re-checked on every hand change and every half second (respawn drops modifiers).
 */
public final class BladeGear {
	private static final Identifier FENCER_SPEED = Mettle.id("blades/fencer");
	private static final Identifier MEASURE_REACH = Mettle.id("blades/measure");
	private static final Identifier WIDE_ARC_SWEEP = Mettle.id("blades/wide_arc");
	public static final double MEASURE = 0.5;
	public static final double WIDE_ARC = 0.75;

	private BladeGear() {
	}

	public static void refresh(ServerPlayer player) {
		ItemStack held = player.getMainHandItem();
		boolean blade = Combat.melee(held) == Combat.Weapon.BLADE;
		boolean sword = held.is(ItemTags.SWORDS);
		set(player, Attributes.ATTACK_SPEED, FENCER_SPEED, blade ? BladeMath.fencer(Perks.rank(player, BladesSkill.FENCER)) : 0,
			AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		// Spears carry their own reach; Measure stretches it in reach() instead.
		set(player, Attributes.ENTITY_INTERACTION_RANGE, MEASURE_REACH, sword && Perks.has(player, BladesSkill.MEASURE) ? MEASURE : 0,
			AttributeModifier.Operation.ADD_VALUE);
		// Vanilla sweeps deal 1 + ratio × hit; +0.75 makes it three quarters of the hit (only swords sweep).
		set(player, Attributes.SWEEPING_DAMAGE_RATIO, WIDE_ARC_SWEEP, sword && Perks.has(player, BladesSkill.WIDE_ARC) ? WIDE_ARC : 0,
			AttributeModifier.Operation.ADD_VALUE);
	}

	/** Measure for spears: their attack range is an item component, read through LivingEntity#getAttackRangeWith. */
	public static AttackRange reach(LivingEntity entity, ItemStack weapon, AttackRange range) {
		if (!(entity instanceof Player player) || !weapon.is(ItemTags.SPEARS) || !weapon.has(DataComponents.ATTACK_RANGE)
			|| !Perks.has(player, BladesSkill.MEASURE)) {
			return range;
		}
		float more = (float) MEASURE;
		return new AttackRange(range.minReach(), range.maxReach() + more, range.minCreativeReach(), range.maxCreativeReach() + more,
			range.hitboxMargin(), range.mobFactor());
	}

	/** Light Feet: a blade hit doesn't stop your sprint or slow you. Both sides, so the client doesn't stop either. */
	public static boolean keepsSprint(Player player) {
		return Perks.has(player, BladesSkill.LIGHT_FEET) && Combat.melee(player.getMainHandItem()) == Combat.Weapon.BLADE;
	}

	/** Light Feet saved a sprint: a chip, at most every two seconds. */
	public static void keptSprint(ServerPlayer player) {
		BladeState state = BladeState.of(player);
		long now = player.level().getGameTime();
		if (player.isSprinting() && now - state.lightFeetChipAt >= 40) {
			state.lightFeetChipAt = now;
			BladesSkill.chip(player, BladesSkill.LIGHT_FEET, "light_feet");
		}
	}

	/**
	 * Weapon Master: taking up a blade after a heavy weapon, or the other way round, arms a critical for the new one.
	 * Other items in between don't matter; switching to another weapon kind disarms it.
	 */
	public static void switched(ServerPlayer player, ItemStack to) {
		BladeState state = BladeState.of(player);
		Combat.Weapon now = Combat.melee(to);
		if (now != state.weaponMasterArmed) {
			state.weaponMasterArmed = null;
		}
		if (now == null) {
			return;
		}
		if (state.lastWeapon != null && state.lastWeapon != now && player.level().getGameTime() >= state.weaponMasterReady
			&& Perks.has(player, BladesSkill.WEAPON_MASTER)) {
			state.weaponMasterArmed = now;
		}
		state.lastWeapon = now;
	}

	private static void set(ServerPlayer player, Holder<Attribute> attribute, Identifier id, double amount, AttributeModifier.Operation op) {
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
		if (current == null || current.amount() != amount || current.operation() != op) {
			instance.removeModifier(id);
			instance.addTransientModifier(new AttributeModifier(id, amount, op));
		}
	}
}
