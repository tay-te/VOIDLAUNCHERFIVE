package dev.voidmc.fray.api;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/**
 * The defender-favoured rules (COMBAT.md §10.3) for whoever lands hits on players: Fray Motion's creature sweeps and
 * Bestiary's AI. A creature's hit on a player that goes through {@code hurtServer} (vanilla or Fray damage) is already
 * held for the defender's grace and checked against i-frames, parry and block by Fray; these hooks let a hit
 * sweep ask the same questions up front (to skip a hit-stop or play a "whiff" instead).
 *
 * @since 0.1.0 (2026-10-21)
 */
public interface DefenseHooks {
	/** Ticks a hit on this player is held so a dodge or parry pressed in time (but still in flight) can win: half the ping, at most 3. */
	int graceTicks(ServerPlayer player);

	/** What would happen to a hit from {@code attacker} landing now with these {@link HitFlags} (no side effects). */
	GuardOutcome preview(LivingEntity defender, LivingEntity attacker, int hitFlags);
}
