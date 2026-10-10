package dev.voidmc.mettle.character.skill.smithing;

import dev.voidmc.mettle.character.skill.smithing.Forged.Mark;
import dev.voidmc.mettle.character.skill.smithing.Forged.Reforge;

/** The Smithing tree's numbers (TREES.md §10), pure so JUnit can hold them to the design. */
public final class SmithingRules {
	/** Thrifty: chance per rank that a recipe with ingots gives one back. */
	public static final double THRIFTY_PER_RANK = 0.05;
	public static final double RIVETED = 0.30;
	public static final double FORGED = 0.25;
	public static final double FOLDED_STEEL = 1.0;
	public static final double MASTERWORK_DURABILITY = 1.0 / 3.0;
	public static final double STURDY = 0.50;
	public static final double MASTERWORK_CHANCE = 0.10;
	public static final float LIGHT_HAND_WEIGHT = 0.75f;
	public static final float LIGHT_WEIGHT = 0.5f;
	public static final double TRUE_TEMPER = 0.15;
	public static final double HONED_DIG = 0.30;
	public static final double HONED_DAMAGE = 2.0;
	public static final int HONE_HITS = 60;
	public static final int HONE_BLOCKS = 300;
	/** Black Iron: one netherite upgrade in three hands the template back. */
	public static final double BLACK_IRON_CHANCE = 1.0 / 3.0;

	private SmithingRules() {
	}

	/** Extra durability as a fraction of the item's own (bonuses add: Riveted and Masterwork together are +63 %). */
	public static double durabilityBonus(Forged forged) {
		double bonus = 0;
		if (forged.has(Mark.RIVETED)) {
			bonus += RIVETED;
		}
		if (forged.has(Mark.FORGED)) {
			bonus += FORGED;
		}
		if (forged.has(Mark.FOLDED_STEEL)) {
			bonus += FOLDED_STEEL;
		}
		if (forged.has(Mark.MASTERWORK)) {
			bonus += MASTERWORK_DURABILITY;
		}
		if (forged.reforge() == Reforge.STURDY) {
			bonus += STURDY;
		}
		// Cursed cards take some away.
		if (forged.has(Mark.BRITTLE_EDGE)) {
			bonus += dev.voidmc.mettle.character.draft.Cursed.BRITTLE_EDGE_DURABILITY;
		}
		if (forged.has(Mark.SLAPDASH)) {
			bonus += dev.voidmc.mettle.character.draft.Cursed.SLAPDASH_DURABILITY;
		}
		return bonus;
	}

	public static int maxDamage(int base, Forged forged) {
		return base <= 0 ? base : (int) Math.max(1, Math.round(base * Math.max(0.1, 1 + durabilityBonus(forged))));
	}

	/** What a stamped item weighs against its plain weight (Light Hand and a Light reforge multiply). */
	public static float weight(Forged forged) {
		float m = 1f;
		if (forged.has(Mark.LIGHT_HAND)) {
			m *= LIGHT_HAND_WEIGHT;
		}
		if (forged.reforge() == Reforge.LIGHT) {
			m *= LIGHT_WEIGHT;
		}
		return m;
	}

	/** Shieldwright: the shield's own wear per block, halved. */
	public static float shieldWear(float wear) {
		return wear / 2f;
	}

	/**
	 * Masterwork: whether a smith's n-th piece is one. A hash of the smith and a count that only moves when a piece is
	 * actually taken, so moving ingredients around never re-rolls it, and every copy of the same craft agrees.
	 */
	public static boolean masterwork(long smith, long n) {
		long z = smith + (n + 1) * 0x9E3779B97F4A7C15L;
		z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
		z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
		z ^= z >>> 31;
		return (z >>> 11) * 0x1.0p-53 < MASTERWORK_CHANCE;
	}

	/**
	 * Tinker and A Hundred Hammers on an anvil repair or rename. {@code cost} is vanilla's (prior work {@code tax}
	 * included); the result is never below 1.
	 */
	public static int anvilCost(long cost, long tax, int tinker, boolean hundredHammers) {
		long next = cost;
		if (hundredHammers) {
			next -= tax;
		}
		next -= tinker;
		return (int) Math.max(1, Math.min(Integer.MAX_VALUE, next));
	}

	/**
	 * Salvager: nuggets back for grinding down a metal piece of {@code units} ingots: half its metal when unworn, less
	 * the more it is worn. 0 when nothing would come back.
	 */
	public static int salvage(int units, int damage, int maxDamage) {
		if (units <= 0) {
			return 0;
		}
		double left = maxDamage <= 0 ? 1 : Math.max(0, maxDamage - damage) / (double) maxDamage;
		return (int) Math.floor(units * 9 / 2.0 * left);
	}
}
