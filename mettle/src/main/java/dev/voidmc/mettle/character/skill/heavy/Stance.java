package dev.voidmc.mettle.character.skill.heavy;

import dev.voidmc.mettle.Mettle;
import dev.voidmc.mettle.character.Perks;
import dev.voidmc.mettle.character.skill.Combat;
import dev.voidmc.mettle.character.skill.HeavyWeaponsSkill;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

/**
 * How a heavy-weapon fighter stands: Iron Grip (no knockback and +2 armour toughness while a heavy weapon is in hand)
 * and Berserker's rage below 40 % health (no knockback, no slowing). Both are conditional attribute modifiers, so they
 * use their own ids outside {@code perk/} (EffectsApplier rebuilds those and would strip a conditional one) and are
 * re-checked when the held item changes, when the player is hurt, and every quarter second.
 */
public final class Stance {
	public static final Identifier IRON_GRIP_KNOCKBACK = Mettle.id("heavy_weapons/iron_grip/knockback_resistance");
	public static final Identifier IRON_GRIP_TOUGHNESS = Mettle.id("heavy_weapons/iron_grip/armor_toughness");
	public static final Identifier BERSERKER_KNOCKBACK = Mettle.id("heavy_weapons/berserker/knockback_resistance");

	/** Players in a rage right now, so the chip is sent once per rage rather than every check. */
	private static final Set<UUID> RAGING = new HashSet<>();

	private Stance() {
	}

	/** Berserker is active: the perk, and below 40 % health. */
	public static boolean raging(Player player) {
		return HeavyRules.berserk(player.getHealth(), player.getMaxHealth()) && Perks.has(player, HeavyWeaponsSkill.BERSERKER);
	}

	public static void update(ServerPlayer player) {
		boolean grip = Combat.melee(player.getMainHandItem()) == Combat.Weapon.HEAVY && Perks.has(player, HeavyWeaponsSkill.IRON_GRIP);
		toggle(player, Attributes.KNOCKBACK_RESISTANCE, IRON_GRIP_KNOCKBACK, 1.0, grip);
		toggle(player, Attributes.ARMOR_TOUGHNESS, IRON_GRIP_TOUGHNESS, 2.0, grip);

		boolean rage = !player.isDeadOrDying() && raging(player);
		toggle(player, Attributes.KNOCKBACK_RESISTANCE, BERSERKER_KNOCKBACK, 1.0, rage);
		if (!rage) {
			RAGING.remove(player.getUUID());
		} else if (RAGING.add(player.getUUID())) {
			// Shrug off what was already slowing you.
			player.removeEffect(MobEffects.SLOWNESS);
			Stagger.clearSlow(player);
			HeavyWeaponsSkill.chip(player, HeavyWeaponsSkill.BERSERKER);
		}
	}

	private static void toggle(ServerPlayer player, Holder<Attribute> attribute, Identifier id, double amount, boolean want) {
		AttributeInstance instance = player.getAttribute(attribute);
		if (instance == null || want == instance.hasModifier(id)) {
			return;
		}
		if (want) {
			instance.addTransientModifier(new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_VALUE));
		} else {
			instance.removeModifier(id);
		}
	}

	public static void forget(UUID player) {
		RAGING.remove(player);
	}
}
