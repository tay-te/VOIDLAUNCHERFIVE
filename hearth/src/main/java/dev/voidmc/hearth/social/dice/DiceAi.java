package dev.voidmc.hearth.social.dice;

import java.util.random.RandomGenerator;

/**
 * How a folk decides at the dice table: which scoring dice to set aside, and whether to bank or roll on. It weighs, for
 * every valid set of dice it could keep, banking now against the expected worth of rolling what is left (the exact odds
 * of a bust and the average score of a roll of n dice, worked out once from the rules), scaled by the folk's
 * {@link DicePersonality} and the state of the game (far behind and the other player close to winning: bolder). Pure.
 */
public final class DiceAi {
	/** Chance that a roll of n dice scores nothing, n = 0..6 (index 0 unused). */
	static final double[] BUST = new double[7];
	/** Average best score of a roll of n dice that does score. */
	static final double[] GAIN = new double[7];

	static {
		for (int n = 1; n <= 6; n++) {
			int[] counts = new int[7];
			double[] acc = new double[3];
			odds(counts, 1, n, n, acc);
			double total = Math.pow(6, n);
			BUST[n] = acc[0] / total;
			GAIN[n] = acc[1] / Math.max(1, acc[2]);
		}
	}

	private DiceAi() {
	}

	/**
	 * @param keep bit mask over {@code free} of the dice to set aside (never empty, always a valid selection)
	 * @param bank true to bank the turn after setting them aside, false to roll the rest
	 */
	public record Decision(int keep, boolean bank) {
	}

	/**
	 * Decides on a roll that scores.
	 *
	 * @param free       the dice just rolled (not already set aside this turn)
	 * @param keptBefore how many dice were already set aside this turn
	 * @param turnPoints points banked in the turn so far (before this roll's dice)
	 */
	public static Decision decide(int[] free, int keptBefore, int turnPoints, int myScore, int theirScore, int target,
		DicePersonality personality, RandomGenerator random) {
		double boldness = personality.boldness;
		int theirGap = target - theirScore;
		int myGap = target - myScore;
		if (theirGap <= 500 && myGap > theirGap + 500) {
			boldness += 0.25; // desperate: they are about to win
		} else if (myScore - theirScore > 1200) {
			boldness -= 0.12; // comfortably ahead
		}
		// A little noise so the same folk does not play like a machine.
		boldness *= 0.95 + random.nextDouble() * 0.1;

		int bestKeep = 0;
		boolean bestBank = true;
		double bestValue = -1;
		int n = free.length;
		for (int sub = 1; sub < 1 << n; sub++) {
			int s = Farkle.scoreMask(free, sub);
			if (s <= 0) {
				continue;
			}
			int points = turnPoints + s;
			int left = n - Integer.bitCount(sub);
			int dice = left == 0 ? Farkle.DICE : left; // hot dice: all six again
			double bankValue = points;
			if (myScore + points >= target) {
				bankValue += 100_000; // banking wins the game
			}
			double rollValue = (1 - BUST[dice]) * (points + GAIN[dice]) * boldness;
			// Keeping fewer dice of the same worth leaves more to roll; prefer it on ties.
			double tie = left * 0.01;
			if (bankValue + tie > bestValue) {
				bestValue = bankValue + tie;
				bestKeep = sub;
				bestBank = true;
			}
			if (rollValue + tie > bestValue) {
				bestValue = rollValue + tie;
				bestKeep = sub;
				bestBank = false;
			}
		}
		if (bestKeep == 0) {
			throw new IllegalArgumentException("decide() needs a roll that scores");
		}
		return new Decision(bestKeep, bestBank);
	}

	public static double bustChance(int dice) {
		return BUST[Math.max(1, Math.min(6, dice))];
	}

	/** Enumerates handfuls of n dice with their multinomial weight: acc = {bust weight, gain sum, scoring weight}. */
	private static void odds(int[] c, int face, int left, int n, double[] acc) {
		if (face == 6) {
			c[6] = left;
			double w = multinomial(c, n);
			int key = Farkle.pack(c);
			int best = bestOf(c, key);
			if (best == 0) {
				acc[0] += w;
			} else {
				acc[1] += w * best;
				acc[2] += w;
			}
			c[6] = 0;
			return;
		}
		for (int k = 0; k <= left; k++) {
			c[face] = k;
			odds(c, face + 1, left - k, n, acc);
		}
		c[face] = 0;
	}

	private static int bestOf(int[] c, int key) {
		int[] faces = new int[Farkle.DICE];
		int i = 0;
		for (int f = 1; f <= 6; f++) {
			for (int k = 0; k < c[f]; k++) {
				faces[i++] = f;
			}
		}
		return Farkle.bestScore(java.util.Arrays.copyOf(faces, i));
	}

	private static double multinomial(int[] c, int n) {
		double r = factorial(n);
		for (int f = 1; f <= 6; f++) {
			r /= factorial(c[f]);
		}
		return r;
	}

	private static double factorial(int n) {
		double r = 1;
		for (int i = 2; i <= n; i++) {
			r *= i;
		}
		return r;
	}
}
