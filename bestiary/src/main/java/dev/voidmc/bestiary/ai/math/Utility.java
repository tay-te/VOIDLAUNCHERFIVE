package dev.voidmc.bestiary.ai.math;

/**
 * Utility scoring (BESTIARY.md §2.1): score = weight × the product of the consideration values × (1 ± noise); the
 * best option wins, or one of the top two at random when they are within 10 % (so a creature is not predictable to
 * the tick). Pure maths.
 */
public final class Utility {
	/** Plus or minus 15 % noise on every score. */
	public static final float NOISE = 0.15F;
	/** Top two within this share of the best: either may be picked. */
	public static final float TIE = 0.10F;

	private Utility() {
	}

	/** {@code weight × Π values}, then noise from {@code roll} (0..1, 0.5 = none). */
	public static float score(float weight, float[] values, int n, float roll) {
		float s = Math.max(0, weight);
		for (int i = 0; i < n && s > 0; i++) {
			s *= Curves.clamp01(values[i]);
		}
		return s * (1 + (roll * 2 - 1) * NOISE);
	}

	/**
	 * The index to pick from {@code scores[0..n)}: the best, or the second best when it is within {@link #TIE} of the best
	 * and {@code coin} is below 0.5. -1 when nothing scores above zero.
	 */
	public static int pick(float[] scores, int n, float coin) {
		int best = -1, second = -1;
		for (int i = 0; i < n; i++) {
			if (scores[i] <= 0) {
				continue;
			}
			if (best < 0 || scores[i] > scores[best]) {
				second = best;
				best = i;
			} else if (second < 0 || scores[i] > scores[second]) {
				second = i;
			}
		}
		if (best >= 0 && second >= 0 && scores[second] >= scores[best] * (1 - TIE) && coin < 0.5F) {
			return second;
		}
		return best;
	}

	/** Repetition penalty: the same option again loses 25 % per repeat, down to 40 %. */
	public static float repetition(int repeats) {
		return Math.max(0.4F, 1 - 0.25F * repeats);
	}
}
