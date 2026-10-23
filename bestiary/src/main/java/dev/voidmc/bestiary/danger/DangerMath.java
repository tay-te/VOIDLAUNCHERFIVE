package dev.voidmc.bestiary.danger;

/**
 * Danger (BESTIARY.md §6): a place's danger 1.0-5.0 from distance, depth, night and dimension, and what it changes
 * (health, damage, poise, build-up, recovery, elite chance, affixes), with game difficulty on top (§6.3). Pure maths
 * (JUnit: {@code DangerMathTest}); every ring and step comes from {@link DangerConfig}.
 */
public final class DangerMath {
	/** §6.2 by whole danger 1..5; fractional danger interpolates. */
	static final float[] HEALTH = {1.0F, 1.5F, 2.2F, 3.2F, 4.5F};
	static final float[] DAMAGE = {1.0F, 1.25F, 2.0F, 2.8F, 3.6F};
	static final float[] POISE = {1.0F, 1.2F, 1.4F, 1.7F, 2.0F};
	static final float[] BUILD_UP = {1.0F, 1.2F, 1.4F, 1.6F, 1.8F};
	static final float[] RECOVERY = {1.0F, 0.95F, 0.9F, 0.85F, 0.8F};
	static final float[] ELITE = {0F, 0.02F, 0.04F, 0.06F, 0.08F};

	private DangerMath() {
	}

	public static float clamp(float d) {
		return Math.max(1, Math.min(5, d));
	}

	/** Interpolates a §6.2 column at a fractional danger. */
	public static float column(float[] col, float danger) {
		float d = clamp(danger) - 1;
		int i = (int) Math.floor(d);
		if (i >= col.length - 1) {
			return col[col.length - 1];
		}
		float f = d - i;
		return col[i] + (col[i + 1] - col[i]) * f;
	}

	public static float health(float danger) {
		return column(HEALTH, danger);
	}

	public static float damage(float danger) {
		return column(DAMAGE, danger);
	}

	public static float poise(float danger) {
		return column(POISE, danger);
	}

	public static float buildUp(float danger) {
		return column(BUILD_UP, danger);
	}

	public static float recovery(float danger) {
		return column(RECOVERY, danger);
	}

	public static float eliteChance(float danger) {
		return column(ELITE, danger);
	}

	/** Affixes an elite gets: D2 one, D3 one or two, D4 two, D5 three ({@code roll} 0..1 picks within a band). */
	public static int affixes(float danger, float roll) {
		float d = clamp(danger);
		if (d < 2) {
			return 1;
		}
		if (d < 3) {
			return 1;
		}
		if (d < 4) {
			return roll < 0.5F ? 1 : 2;
		}
		if (d < 5) {
			return 2;
		}
		return 3;
	}

	/**
	 * The distance part: 1 under the first ring, +1 past each ring (2, 5, 10 km by default), eased over {@code blend}
	 * blocks across each ring so there is no wall.
	 */
	public static float distance(double blocks, double[] rings, double blend) {
		float d = 1;
		for (double r : rings) {
			d += (float) Math.max(0, Math.min(1, (blocks - (r - blend / 2)) / Math.max(1, blend)));
		}
		return d;
	}

	/** The depth part: 20+ blocks under the surface +0.5; in the great-tunnel band (y 12-44) that deep, +1.0 instead. */
	public static float depth(int belowSurface, int y, int tunnelLow, int tunnelHigh) {
		if (belowSurface < 20) {
			return 0;
		}
		return y >= tunnelLow && y <= tunnelHigh ? 1.0F : 0.5F;
	}

	/** Game difficulty (§6.3): 0 peaceful, 1 easy, 2 normal, 3 hard. */
	public static float difficultyDamage(int difficulty) {
		return switch (difficulty) {
			case 1 -> 0.6F;
			case 3 -> 1.25F;
			default -> 1F;
		};
	}

	public static float difficultyHealth(int difficulty) {
		return switch (difficulty) {
			case 1 -> 0.8F;
			case 3 -> 1.15F;
			default -> 1F;
		};
	}

	/** Token step: easy -1, hard +1, Mythic +1 more. */
	public static int difficultyTokens(int difficulty, boolean mythic) {
		int s = difficulty == 1 ? -1 : difficulty == 3 ? 1 : 0;
		return s + (mythic ? 1 : 0);
	}

	/** Elites and bosses in a party: health × (1 + 0.6 per extra player within 32 blocks). */
	public static float partyHealth(int players) {
		return 1 + 0.6F * Math.max(0, players - 1);
	}
}
