package dev.voidmc.fray.api;

import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.Nullable;

/**
 * Pipeline stage 4 for a creature that is hit (players have Fray's own guard): Bestiary's shields and parries
 * (the revenant soldier, Shielded elites). Called for every Fray hit on a non-player that isn't a status proc; the first
 * listener with an answer other than {@link Result#NONE} decides.
 * <ul>
 * <li>{@code PARRIED}: no damage; a player who struck is Staggered 10 ticks and their action interrupted.</li>
 * <li>{@code BLOCKED}: {@code keep} of each kind gets through (poise and build-up halved, as for players).</li>
 * <li>{@code DODGED}: no damage (a sidestep).</li>
 * </ul>
 *
 * @since 0.2.0 (2026-10-22)
 */
@FunctionalInterface
public interface CreatureGuard {
	record Result(GuardOutcome outcome, float keep) {
		public static final Result NONE = new Result(GuardOutcome.NONE, 1);
	}

	Result guard(LivingEntity defender, @Nullable LivingEntity attacker, HitContext hit, int flags);
}
