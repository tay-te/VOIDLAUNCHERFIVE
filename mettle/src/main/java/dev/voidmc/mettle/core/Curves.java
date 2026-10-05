package dev.voidmc.mettle.core;

/**
 * The level curves (BALANCE.md §1–§2). Fast at first (skill level 1 in a minute), mastery after about forty hours of
 * one activity.
 */
public final class Curves {
	public static final int SKILL_CAP = 20;
	public static final int CHARACTER_CAP = 50;

	private Curves() {
	}

	/** Total skill XP needed to reach a skill level: 10 × L^2.66. */
	public static double skillXpFor(int level) {
		return level <= 0 ? 0 : Math.round(10 * Math.pow(level, 2.66));
	}

	public static int skillLevel(double xp) {
		int level = 0;
		while (level < SKILL_CAP && xp >= skillXpFor(level + 1)) {
			level++;
		}
		return level;
	}

	/** Total character XP needed to reach a character level: 25 × (L − 1)^2.31. Level 1 is free. */
	public static double characterXpFor(int level) {
		return level <= 1 ? 0 : Math.round(25 * Math.pow(level - 1, 2.31));
	}

	public static int characterLevel(double totalXp) {
		int level = 1;
		while (level < CHARACTER_CAP && totalXp >= characterXpFor(level + 1)) {
			level++;
		}
		return level;
	}

	/** Tier gates (TREES.md §1): skill level and governing attribute needed for tiers 1–4. */
	public static int tierSkillGate(int tier) {
		return switch (tier) {
			case 1 -> 2;
			case 2 -> 6;
			case 3 -> 12;
			default -> 18;
		};
	}

	public static int tierAttributeGate(int tier) {
		return switch (tier) {
			case 1 -> 0;
			case 2 -> 6;
			case 3 -> 10;
			default -> 14;
		};
	}
}
