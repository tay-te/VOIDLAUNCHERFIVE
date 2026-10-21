package dev.voidmc.fray.api;

import java.util.List;
import java.util.Map;

/**
 * What one hit did, stage by stage (CONTRACTS.md §10 {@code DAMAGE_DEALT}).
 *
 * @param base        stage 1 total
 * @param attacker    stage 2 total (after add / increased / more, capped by the ceiling)
 * @param situational stage 3 multiplier applied (1 when none)
 * @param guard       stage 4 outcome
 * @param perKind     stage 5 result per kind (after armour and resistances)
 * @param total       what was handed to vanilla (stage 8)
 * @param poise       poise damage dealt
 * @param procs       statuses that procced from this hit
 * @param killed      the target died from it
 * @since 0.1.0 (2026-10-21)
 */
public record DamageBreakdown(float base, float attacker, float situational, GuardOutcome guard, Map<DamageKind, Float> perKind, float total, float poise,
	List<StatusType> procs, boolean crit, boolean killed) {
	public static DamageBreakdown avoided(float base, GuardOutcome guard) {
		return new DamageBreakdown(base, base, 1, guard, Map.of(), 0, 0, List.of(), false, false);
	}

	public boolean landed() {
		return this.total > 0 || this.guard == GuardOutcome.NONE || this.guard == GuardOutcome.BLOCKED || this.guard == GuardOutcome.GUARD_BREAK;
	}
}
