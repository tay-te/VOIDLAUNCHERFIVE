package dev.voidmc.mettle.character.skill.defense;

import java.util.function.DoubleSupplier;

/**
 * The Defense tree's numbers and the small sums behind them, kept free of game state so JUnit can check them
 * (TREES.md §7). Times are in ticks.
 */
public final class DefenseRules {
	/** Bulwark: a shield covers this much either side of where you look (vanilla 90°, so only your back is open). */
	public static final double BULWARK_ANGLE = 135.0;
	/** Perfect Block: a hit within this long of the shield coming up is reflected. */
	public static final int PERFECT_BLOCK_WINDOW = 5;
	public static final float PERFECT_BLOCK_SHARE = 0.5f;
	/** Shield Bash: a hit this soon after lowering the shield bashes. */
	public static final int SHIELD_BASH_WINDOW = 10;
	/** Knockback power that throws a mob about three blocks on the ground (friction keeps ~45 % a tick). */
	public static final double THROW_POWER = 1.4;
	public static final int DAZE_TICKS = 20;
	public static final float GUARDIAN_SHARE = 0.6f;
	public static final double GUARDIAN_ALLY_RANGE = 4.0;
	public static final double GUARDIAN_TAUNT_RANGE = 8.0;
	public static final int LAST_STAND_COOLDOWN = 12_000;
	public static final float LAST_STAND_HEALTH = 2.0f;
	public static final int LAST_STAND_RESISTANCE_TICKS = 80;
	public static final int SECOND_WIND_COOLDOWN = 6_000;
	public static final float SECOND_WIND_BELOW = 8.0f;
	public static final int SECOND_WIND_TICKS = 100;
	public static final int HOLD_THE_LINE_MAX = 5;
	public static final int HOLD_THE_LINE_TICKS = 100;
	public static final float MENDER_SPARE = 0.3f;
	public static final float JUGGERNAUT_DAMAGE = 4.0f;
	public static final int STILL_STANDING_COOLDOWN = 1_200;
	public static final float STILL_STANDING_OVER = 10.0f;
	public static final float STILL_STANDING_FEAT = 15.0f;
	public static final float ROLL_SHARE = 0.25f;
	public static final int ROLL_SECONDS = 4;
	public static final float PINCUSHION_CHANCE = 0.5f;
	public static final int PINCUSHION_TARGET = 300;

	private DefenseRules() {
	}

	/**
	 * Bulwark: the angle a shield compares against its own blocking angle, squeezed so that 135° reads as 90°. Scaling
	 * (rather than swapping in a new angle) keeps any shield's own shape, modded ones included.
	 */
	public static double bulwarkAngle(double radians) {
		return radians * 90.0 / BULWARK_ANGLE;
	}

	/** Perfect Block: whether a hit landed in the first quarter second the shield was actually up. */
	public static boolean perfectBlock(int ticksUsing, int blockDelay) {
		int up = ticksUsing - blockDelay;
		return up >= 0 && up < PERFECT_BLOCK_WINDOW;
	}

	public static boolean ready(long now, long readyAt) {
		return now >= readyAt;
	}

	public static int holdTheLine(int stacks) {
		return Math.min(HOLD_THE_LINE_MAX, stacks + 1);
	}

	/** Unbowed: half the duration, never below a tick; infinite effects stay infinite. */
	public static int unbowed(int duration) {
		return duration < 0 ? duration : Math.max(1, duration / 2);
	}

	/** Mender: each point of wear is spared with {@link #MENDER_SPARE} odds. */
	public static int mender(int amount, DoubleSupplier random) {
		int kept = 0;
		for (int i = 0; i < amount; i++) {
			if (random.getAsDouble() >= MENDER_SPARE) {
				kept++;
			}
		}
		return kept;
	}

	/** Battle-Worn: wear that would take a piece to 0 durability stops at 1. */
	public static int battleWorn(int amount, int damage, int maxDamage) {
		return Math.max(0, Math.min(amount, maxDamage - 1 - damage));
	}

	/** Still Standing: a hit over 10 is cut by a third. */
	public static float stillStanding(float damage) {
		return damage > STILL_STANDING_OVER ? damage * 2f / 3f : damage;
	}

	/** Roll with the Blow: the share of a hit held back, to be dealt over {@link #ROLL_SECONDS}. */
	public static float rollDeferred(float damage) {
		return damage * ROLL_SHARE;
	}
}
