package dev.voidmc.bestiary.boss;

import java.util.List;

/**
 * The encounter rules as pure maths (BESTIARY.md §8; JUnit {@code BossMathTest}): who is in the arena, when a fight
 * resets, multiplayer health, who earns a personal roll, the "one red in any three" rule and when a boss is stuck.
 */
public final class BossMath {
	/** Personal loot: a share of the damage, or time in the arena (§8.2). */
	public static final float LOOT_SHARE = 0.10F;
	public static final int LOOT_PRESENCE_TICKS = 30 * 20;
	/** Health per extra player in the fight (BESTIARY §6.3: +60 % each). */
	public static final float PARTY_STEP = 0.6F;

	private BossMath() {
	}

	/** Inside a vertical cylinder: horizontal distance within {@code radius}, height from {@code floor - below} to {@code floor + above}. */
	public static boolean inCylinder(double x, double y, double z, double cx, double floor, double cz, double radius, double below, double above) {
		double dx = x - cx, dz = z - cz;
		return dx * dx + dz * dz <= radius * radius && y >= floor - below && y <= floor + above;
	}

	/** Max health factor for a fight with {@code players} participants (never below 1). */
	public static float partyHealth(int players) {
		return 1 + PARTY_STEP * Math.max(0, players - 1);
	}

	/** What damage on a boss is divided by: the party's factor times the difficulty's health factor. */
	public static float toughness(int players, float difficultyHealth) {
		return partyHealth(players) * difficultyHealth;
	}

	/** The new health when the max changes from {@code oldMax} to {@code newMax}: the same share, never 0 for a living boss. */
	public static float keepShare(float health, float oldMax, float newMax) {
		if (oldMax <= 0) {
			return newMax;
		}
		return Math.max(Math.min(1, health), Math.min(newMax, health / oldMax * newMax));
	}

	/** Whether the arena has been empty long enough to reset ({@code emptySince} 0 = someone is there). */
	public static boolean resetDue(long emptySince, long now, int resetTicks) {
		return emptySince > 0 && now - emptySince >= resetTicks;
	}

	/** A participant earns a personal roll with 10 % of the damage dealt to the boss or 30 s in the arena. */
	public static boolean earnsLoot(float damage, float totalDamage, int presenceTicks) {
		return totalDamage > 0 && damage / totalDamage >= LOOT_SHARE - 1e-6F || presenceTicks >= LOOT_PRESENCE_TICKS;
	}

	/**
	 * BESTIARY §8.1 rule 2: at most one red move in any three. {@code recent} holds the colours of the last moves, newest
	 * last; a red move may start only if neither of the last two was red.
	 */
	public static boolean redAllowed(List<String> recent) {
		int n = recent.size();
		for (int i = Math.max(0, n - 2); i < n; i++) {
			if (recent.get(i).equals("red")) {
				return false;
			}
		}
		return true;
	}

	/**
	 * The stuck watchdog: a boss that wants to move but has covered less than {@code minMove} blocks in {@code window}
	 * ticks is stuck.
	 */
	public static boolean stuck(double moved, long since, long now, int window, double minMove) {
		return now - since >= window && moved < minMove;
	}
}
