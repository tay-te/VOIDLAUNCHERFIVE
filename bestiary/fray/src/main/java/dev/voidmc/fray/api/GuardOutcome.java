package dev.voidmc.fray.api;

/**
 * How the defender met a hit (pipeline stage 4).
 *
 * @since 0.1.0 (2026-10-21)
 */
public enum GuardOutcome {
	/** Taken in full. */
	NONE,
	/** Inside a dodge's i-frames. */
	DODGED,
	/** Inside the first three i-frame ticks: a perfect dodge. */
	SLIP,
	/** The active tick landed inside the parry window: no damage, attacker staggered. */
	PARRIED,
	/** Reduced by a guard; the blocked part cost stamina. */
	BLOCKED,
	/** Stamina ran out while blocking: staggered and Winded. */
	GUARD_BREAK
}
