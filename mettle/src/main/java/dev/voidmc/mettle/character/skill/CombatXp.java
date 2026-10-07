package dev.voidmc.mettle.character.skill;

/**
 * The combat XP numbers (BALANCE.md §3) and the sums behind them, free of game state so the balance model and JUnit
 * use exactly what {@link Combat} pays.
 */
public final class CombatXp {
	/** Blades and Heavy Weapons: per point of damage dealt. */
	public static final double MELEE_PER_DAMAGE = 1.0;
	/** Marksmanship: per point of damage dealt, plus one per {@link #RANGED_BLOCKS_PER_XP} blocks of distance. */
	public static final double RANGED_PER_DAMAGE = 1.5;
	public static final double RANGED_BLOCKS_PER_XP = 5.0;
	/** Kill bonus: this share of the target's max health. */
	public static final double KILL_SHARE = 0.25;
	/** Defense: per point a shield blocked, and per point armour absorbed. */
	public static final double DEFENSE_PER_BLOCKED = 8.0;
	public static final double DEFENSE_PER_ABSORBED = 8.0;
	/** Defense: at most this much a minute, so standing in front of a zombie behind a shield is not a way to train. */
	public static final double DEFENSE_CAP_PER_MINUTE = 60.0;

	private CombatXp() {
	}

	/** XP for one hit that dealt {@code dealt} damage from {@code distance} blocks, before the target factor. */
	public static double hit(boolean ranged, double dealt, double distance) {
		return ranged ? RANGED_PER_DAMAGE * dealt + distance / RANGED_BLOCKS_PER_XP : MELEE_PER_DAMAGE * dealt;
	}

	/** The kill bonus for a target with this max health, before the target factor. */
	public static double kill(double maxHealth) {
		return maxHealth * KILL_SHARE;
	}

	/**
	 * Vanilla's armour share of a hit ({@code CombatRules#getDamageAfterAbsorb} without enchantments): what fraction of
	 * {@code damage} armour and toughness stop.
	 */
	public static double armourFraction(double armour, double toughness, double damage) {
		if (armour <= 0) {
			return 0;
		}
		double real = Math.clamp(armour - damage / (2.0 + toughness / 4.0), armour * 0.2, 20.0);
		return real / 25.0;
	}

	/** Defense XP for a hit: what the shield blocked, and what armour absorbed of the rest. */
	public static double defense(double blocked, double absorbed) {
		return DEFENSE_PER_BLOCKED * Math.max(0, blocked) + DEFENSE_PER_ABSORBED * Math.max(0, absorbed);
	}
}
