package dev.voidmc.hearth.social.dice;

/**
 * The scoring rules of tavern dice (a Farkle game, scored as the dice games of Bohemian taverns are): six dice; a single
 * 1 is 100 and a single 5 is 50; three of a kind is the face × 100 (three 1s are 1000); each die beyond three doubles it;
 * a run of 1–5 is 500, 2–6 is 750 and 1–6 is 1500. Pure and allocation-free after class load: the best score of every
 * possible handful of up to six dice is computed once.
 *
 * <p>A handful is "counts": {@code counts[f]} dice showing face {@code f} (index 0 unused).
 */
public final class Farkle {
	public static final int DICE = 6;
	/** No valid way to score every die in the handful. */
	public static final int INVALID = -1;

	/** Best score using every die, by packed counts ({@link #pack}); {@link #INVALID} where some die cannot score. */
	private static final int[] BEST_ALL = new int[pow7()];
	/** Best score of any scoring subset (the most a roll can give), by packed counts; 0 = a bust. */
	private static final int[] BEST_ANY = new int[pow7()];

	static {
		java.util.Arrays.fill(BEST_ALL, Integer.MIN_VALUE);
		int[] c = new int[7];
		fill(c, 1, 0);
	}

	private Farkle() {
	}

	/** Score of a selection of dice faces (1–6), using every die, or {@link #INVALID}. Empty scores 0. */
	public static int score(int... faces) {
		int[] c = new int[7];
		for (int f : faces) {
			if (f < 1 || f > 6) {
				return INVALID;
			}
			c[f]++;
		}
		return scoreCounts(c);
	}

	/** Score using every die of these counts, or {@link #INVALID}. */
	public static int scoreCounts(int[] counts) {
		int total = 0;
		for (int f = 1; f <= 6; f++) {
			total += counts[f];
		}
		if (total > DICE) {
			return INVALID;
		}
		return BEST_ALL[pack(counts)];
	}

	/** The most any subset of these faces can score: 0 means the roll is a bust. */
	public static int bestScore(int... faces) {
		int[] c = new int[7];
		for (int f : faces) {
			c[f]++;
		}
		return BEST_ANY[pack(c)];
	}

	/** Whether some subset of these faces scores (otherwise the roll is a bust). */
	public static boolean scores(int... faces) {
		return bestScore(faces) > 0;
	}

	/**
	 * Which of these dice can be part of some valid selection (bit i set for die i), so the table can show the player
	 * which dice score. A die is useful if any valid selection includes it.
	 */
	public static int scoringMask(int[] faces) {
		int n = faces.length;
		int mask = 0;
		for (int sub = 1; sub < 1 << n; sub++) {
			if ((sub & ~mask) == 0) {
				continue;
			}
			if (scoreMask(faces, sub) > 0) {
				mask |= sub;
			}
		}
		return mask;
	}

	/** Score of the dice of {@code faces} picked by bit mask {@code sub}, or {@link #INVALID}. */
	public static int scoreMask(int[] faces, int sub) {
		int[] c = new int[7];
		for (int i = 0; i < faces.length; i++) {
			if ((sub >> i & 1) != 0) {
				c[faces[i]]++;
			}
		}
		return scoreCounts(c);
	}

	/** Value of {@code k} (3–6) dice of face {@code f}. */
	public static int ofAKind(int face, int k) {
		int base = face == 1 ? 1000 : face * 100;
		return base << (k - 3);
	}

	static int pack(int[] c) {
		int p = 0;
		for (int f = 6; f >= 1; f--) {
			p = p * 7 + c[f];
		}
		return p;
	}

	private static int pow7() {
		int p = 1;
		for (int i = 0; i < 6; i++) {
			p *= 7;
		}
		return p;
	}

	/** Enumerates every handful of at most six dice and records both tables. */
	private static void fill(int[] c, int face, int used) {
		if (face > 6) {
			int key = pack(c);
			BEST_ALL[key] = best(c.clone());
			BEST_ANY[key] = bestAny(c);
			return;
		}
		for (int k = 0; k + used <= DICE; k++) {
			c[face] = k;
			fill(c, face + 1, used + k);
		}
		c[face] = 0;
	}

	/** Best score using every die (memoised in BEST_ALL), or INVALID. */
	private static int best(int[] c) {
		int key = pack(c);
		if (BEST_ALL[key] != Integer.MIN_VALUE) {
			return BEST_ALL[key];
		}
		int total = 0;
		for (int f = 1; f <= 6; f++) {
			total += c[f];
		}
		if (total == 0) {
			BEST_ALL[key] = 0;
			return 0;
		}
		int best = INVALID;
		if (run(c, 1, 6)) {
			best = Math.max(best, take(c, 1, 6, 1500));
		}
		if (run(c, 1, 5)) {
			best = Math.max(best, take(c, 1, 5, 500));
		}
		if (run(c, 2, 6)) {
			best = Math.max(best, take(c, 2, 6, 750));
		}
		for (int f = 1; f <= 6; f++) {
			for (int k = 3; k <= c[f]; k++) {
				c[f] -= k;
				int rest = best(c);
				c[f] += k;
				if (rest != INVALID) {
					best = Math.max(best, ofAKind(f, k) + rest);
				}
			}
		}
		if (c[1] > 0) {
			c[1]--;
			int rest = best(c);
			c[1]++;
			if (rest != INVALID) {
				best = Math.max(best, 100 + rest);
			}
		}
		if (c[5] > 0) {
			c[5]--;
			int rest = best(c);
			c[5]++;
			if (rest != INVALID) {
				best = Math.max(best, 50 + rest);
			}
		}
		BEST_ALL[key] = best;
		return best;
	}

	private static boolean run(int[] c, int from, int to) {
		for (int f = from; f <= to; f++) {
			if (c[f] == 0) {
				return false;
			}
		}
		return true;
	}

	private static int take(int[] c, int from, int to, int value) {
		for (int f = from; f <= to; f++) {
			c[f]--;
		}
		int rest = best(c);
		for (int f = from; f <= to; f++) {
			c[f]++;
		}
		return rest == INVALID ? INVALID : value + rest;
	}

	/** The best-scoring subset of a handful (every sub-handful's full score, maximised). */
	private static int bestAny(int[] c) {
		int[] sub = new int[7];
		return bestAnyRec(c, sub, 1);
	}

	private static int bestAnyRec(int[] c, int[] sub, int face) {
		if (face > 6) {
			return Math.max(0, best(sub.clone()));
		}
		int best = 0;
		for (int k = 0; k <= c[face]; k++) {
			sub[face] = k;
			best = Math.max(best, bestAnyRec(c, sub, face + 1));
		}
		sub[face] = 0;
		return best;
	}
}
