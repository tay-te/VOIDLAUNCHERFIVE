package dev.voidmc.fray.api;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * The damage pipeline (CONTRACTS.md §4.2). Every Fray hit goes through {@link #deal}: attacker pools, situational
 * multipliers, the defender's guard, armour and resistances, status build-up, ceilings, then vanilla's
 * {@code hurtServer} with Fray's {@code fray:combat} damage type.
 *
 * @since 0.1.0 (2026-10-21)
 */
public interface DamageService {
	/** Runs the whole pipeline for one hit on the server thread. */
	DamageBreakdown deal(LivingEntity target, HitContext hit);

	/** The target's resistance to a kind, -1.0 (double damage) to 0.9 (a 10 % floor). */
	float resistance(LivingEntity target, DamageKind kind);

	/**
	 * A weapon's base spec: {@code baseDamage} split by the weapon's {@code fray:damage_split} (or the vanilla defaults by
	 * item tag), its poise factor and build-up.
	 */
	DamageSpec specFor(ItemStack weapon, float baseDamage, float poiseMultiplier);
}
