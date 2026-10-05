package dev.voidmc.mettle.character.skill.athletics;

import dev.voidmc.mettle.api.BandRules;
import dev.voidmc.mettle.api.LoadBand;

/**
 * Athletics' numbers and the pure rules behind them (BALANCE.md §3, TREES.md §8), free of game state so JUnit can check
 * them. Everything that touches a player lives in the other classes of this package and asks here.
 */
public final class AthleticsRules {
	/** Centimetres per base XP point (BALANCE.md §3: 40 m sprinted, 20 m swum, 10 m climbed). */
	public static final double SPRINT_CM_PER_XP = 4000;
	public static final double SWIM_CM_PER_XP = 2000;
	public static final double CLIMB_CM_PER_XP = 1000;
	/** Base Athletics XP a player can earn from moving in one minute (1200 ticks). */
	public static final double XP_CAP_PER_MINUTE = 120;
	public static final long MINUTE = 1200;

	/** Runner: sprint speed per rank (×total, on top of vanilla's sprint). */
	public static final double RUNNER_PER_RANK = 0.03;
	/** Courier's Stride: unbroken sprint ticks before the stride, and its speed. */
	public static final int STRIDE_TICKS = 400;
	public static final double STRIDE_SPEED = 0.15;
	/** Vanilla's sprint is ×1.3; Hauler's Stride halves the bonus: 1.15 / 1.3. */
	public static final double HAULERS_SPRINT = 1.15 / 1.3 - 1.0;
	/** Steady Pace: the Burdened band's −10 % speed becomes −5 % (burden's own penalty is switched off, see {@link #bands}). */
	public static final double STEADY_PACE_SPEED = -0.05;
	/** Vaulter: step height while sprinting (vanilla 0.6 → 1.1, a full block). */
	public static final double VAULTER_STEP = 0.5;
	/** Spring: jump strength while sprinting (0.42 → 0.505 lifts the apex from 1.25 to about 1.75 blocks). */
	public static final double SPRING_JUMP = 0.085;
	/** Spring: an extra forward push on a sprint-jump, on top of vanilla's 0.2 (about a block further). */
	public static final double SPRING_PUSH = 0.1;
	/** Swimmer: swim speed multiplier in water. */
	public static final float SWIMMER_SPEED = 1.3f;

	/** Freerunner: how far above the jump's natural apex (1.25) a scramble may climb, and how fast. */
	public static final double SCRAMBLE_HEIGHT = 2.0;
	public static final double JUMP_APEX = 1.25;
	public static final double SCRAMBLE_SPEED = 0.3;

	/** Ox: the Mettle part of the load penalty is halved. Strong Back: Burdened begins at 85 %. */
	public static final float STRONG_BACK_BURDENED = 0.85f;
	public static final float OX_PENALTY = 0.5f;

	/** Mountain Goat: standing above this height, in places this far apart, this many times. */
	public static final double PEAK_Y = 220;
	public static final double PEAK_SPACING = 500;
	public static final int PEAKS = 10;
	/** Ironlung: centimetres swum under water (2 km). Flushed to the feat counter in 100 m steps. */
	public static final long IRONLUNG_CM = 200_000;
	public static final long IRONLUNG_FLUSH_CM = 10_000;

	private AthleticsRules() {
	}

	/** The movement kinds Athletics pays for. */
	public enum Move {
		SPRINT(SPRINT_CM_PER_XP), SWIM(SWIM_CM_PER_XP), CLIMB(CLIMB_CM_PER_XP);

		public final double cmPerXp;

		Move(double cmPerXp) {
			this.cmPerXp = cmPerXp;
		}
	}

	/** BALANCE.md §3: ×2 Burdened, ×3 Overburdened or Pinned. */
	public static double loadMultiplier(LoadBand band) {
		return switch (band) {
			case LIGHT -> 1;
			case BURDENED -> 2;
			case OVERBURDENED, PINNED -> 3;
		};
	}

	/** Base XP (before the favoured and Wits multipliers) for {@code cm} centimetres of one kind of movement. */
	public static double xp(Move move, long cm, LoadBand band) {
		return cm / move.cmPerXp * loadMultiplier(band);
	}

	/**
	 * The 120-a-minute cap, as a fixed window of game time. Not thread safe; one per player, on the server thread.
	 */
	public static final class Meter {
		private long window = Long.MIN_VALUE;
		private double spent;

		/** How much of {@code xp} may still be paid this minute; records it as spent. */
		public double take(double xp, long gameTime) {
			long w = Math.floorDiv(gameTime, MINUTE);
			if (w != window) {
				window = w;
				spent = 0;
			}
			double allowed = Math.max(0, Math.min(xp, XP_CAP_PER_MINUTE - spent));
			spent += allowed;
			return allowed;
		}

		public double spent() {
			return spent;
		}

		/** What may still be paid in the minute that {@code gameTime} falls in. */
		public double remaining(long gameTime) {
			return Math.floorDiv(gameTime, MINUTE) == window ? Math.max(0, XP_CAP_PER_MINUTE - spent) : XP_CAP_PER_MINUTE;
		}
	}

	/**
	 * The load bands after the Porter perks (TREES.md §8). {@code loadRatio} is load ÷ capacity, used only by Steady
	 * Pace, which switches off burden's own Burdened penalty (its −10 % speed, ×1.5 hunger) and puts its own −5 % in its
	 * place ({@link #STEADY_PACE_SPEED}).
	 */
	public static BandRules bands(BandRules rules, boolean strongBack, boolean beastOfBurden, boolean ox, boolean steadyPace, float loadRatio) {
		float burdened = strongBack ? Math.max(rules.burdened(), STRONG_BACK_BURDENED) : rules.burdened();
		// Never let Burdened start past Overburdened, whatever else moved the thresholds.
		burdened = Math.min(burdened, rules.overburdened());
		float penalty = ox ? rules.penaltyScale() * OX_PENALTY : rules.penaltyScale();
		if (steadyPace && loadRatio >= burdened && loadRatio < rules.overburdened()) {
			penalty = 0f;
		}
		boolean pinnable = rules.canBePinned() && !beastOfBurden;
		return new BandRules(burdened, rules.overburdened(), rules.pinned(), penalty, pinnable);
	}

	/** Steady Pace's own slowdown while Burdened, halved again by Ox. */
	public static double steadyPaceSpeed(boolean ox) {
		return ox ? STEADY_PACE_SPEED * OX_PENALTY : STEADY_PACE_SPEED;
	}

	/** The sprint speed modifiers (×total) for a sprinting player. */
	public static double sprintSpeed(int runnerRank, boolean stride) {
		return RUNNER_PER_RANK * runnerRank + (stride ? STRIDE_SPEED : 0);
	}

	/**
	 * Whether the sprint gate (Player#hasEnoughFoodToDoExhaustiveManoeuvres) should say yes. Athletics only ever turns a
	 * no into a yes for its own reasons, so other mods' and burden's refusals stand: Marathon ignores hunger, Hauler's
	 * Stride ignores being Overburdened; nothing ignores Pinned.
	 */
	public static boolean canSprint(boolean original, boolean enoughFood, LoadBand band, boolean marathon, boolean haulersStride) {
		if (original || band == LoadBand.PINNED) {
			return original;
		}
		boolean foodOk = enoughFood || marathon;
		if (band == LoadBand.OVERBURDENED) {
			return haulersStride && foodOk;
		}
		// With food enough, the no came from something else; leave it.
		return marathon && !enoughFood;
	}

	/** Sprint hunger: half with Marathon, none in Courier's Stride. */
	public static float sprintExhaustion(float amount, boolean marathon, boolean stride) {
		if (stride) {
			return 0f;
		}
		return marathon ? amount * 0.5f : amount;
	}

	/** Mountain Goat: whether a peak at (x, z) is far enough from every one already counted. */
	public static boolean newPeak(long[] peaks, int x, int z) {
		for (long packed : peaks) {
			double dx = x - peakX(packed);
			double dz = z - peakZ(packed);
			if (dx * dx + dz * dz < PEAK_SPACING * PEAK_SPACING) {
				return false;
			}
		}
		return true;
	}

	public static long packPeak(int x, int z) {
		return ((long) x << 32) | (z & 0xFFFFFFFFL);
	}

	public static int peakX(long packed) {
		return (int) (packed >> 32);
	}

	public static int peakZ(long packed) {
		return (int) packed;
	}

	/** Freerunner: the highest a scramble may lift the feet, from where the jump left the ground. */
	public static double scrambleTop(double jumpStartY) {
		return jumpStartY + JUMP_APEX + SCRAMBLE_HEIGHT;
	}

	/** "village_plains" → "Village Plains" (structures have no names of their own). */
	public static String pretty(String path) {
		String last = path.substring(path.lastIndexOf('/') + 1);
		StringBuilder out = new StringBuilder();
		for (String word : last.split("_")) {
			if (!word.isEmpty()) {
				out.append(out.isEmpty() ? "" : " ").append(word.substring(0, 1).toUpperCase(java.util.Locale.ROOT)).append(word.substring(1));
			}
		}
		return out.toString();
	}
}
