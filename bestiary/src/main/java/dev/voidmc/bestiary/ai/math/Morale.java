package dev.voidmc.bestiary.ai.math;

/**
 * Morale (BESTIARY.md §5): packs flee when the leader dies or below 30 % of their number; a creature alone flees under
 * 20 % health; undead, constructs and bosses never flee (their profile says {@code flees: false}). Pure maths.
 */
public final class Morale {
	private Morale() {
	}

	public static boolean flees(boolean canFlee, boolean inPack, boolean leaderDied, float packAliveShare, float healthShare, float aloneBelow,
		float packBelow) {
		if (!canFlee) {
			return false;
		}
		if (inPack) {
			return leaderDied || packAliveShare < packBelow || healthShare < aloneBelow * 0.5F;
		}
		return healthShare < aloneBelow;
	}
}
