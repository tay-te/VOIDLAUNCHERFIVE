package dev.voidmc.mettle.character.skill.stealth;

import dev.voidmc.mettle.compat.HearthKeys;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Stealth's numbers and rules free of the game (docs/BUILD_FEEL.md §14.1), so they can be tested: XP, how far hostiles
 * notice a player, what reaches Hearth through which channel (the effective level, a bonus key, a price factor), and the
 * fallbacks while the loaded Hearth reads no bonus keys.
 */
public final class StealthRules {
	private StealthRules() {
	}

	// ---------------------------------------------------------------- XP (§14.1)

	/** Sneaking within this many blocks of a hostile that isn't targeting you teaches. */
	public static final double NEAR_RANGE = 12;
	/** One XP every 4 s of it. */
	public static final int NEAR_TICKS = 80;
	public static final double NEAR_XP = 1;
	/** At most 15 a minute. */
	public static final double NEAR_CAP_PER_MINUTE = 15;
	/** A sneak attack's kill bonus: a quarter of the mob's max health. */
	public static final double KILL_BONUS = 0.25;
	/** Sneaking within 16 blocks of a Warden that isn't angry with you: 2 a second, at most 60 a minute. */
	public static final double WARDEN_RANGE = 16;
	public static final double WARDEN_XP_PER_SECOND = 2;
	public static final double WARDEN_CAP_PER_MINUTE = 60;

	/** A sneak attack's XP: 1 a point of damage, never more than the health the mob had left. */
	public static double sneakAttackXp(double dealt, double healthBefore) {
		return Math.max(0, Math.min(dealt, healthBefore));
	}

	public static double killBonus(double maxHealth) {
		return Math.max(0, maxHealth) * KILL_BONUS;
	}

	/**
	 * Whether a mob near a sneaking player teaches: a hostile not targeting them, not from a spawner, not leashed, not
	 * riding (a boat or a cart: caged), and able to reach them.
	 */
	public static boolean teaches(boolean hostile, boolean targetingYou, boolean spawner, boolean leashed, boolean riding, boolean reachable) {
		return hostile && !targetingYou && !spawner && !leashed && !riding && reachable;
	}

	/** XP per hour for a night hunter: minutes sneaking near unaware hostiles, sneak kills of an average hostile. */
	public static double hourly(double sneakNearMinutes, double sneakKills, double hostileHealth) {
		double near = Math.min(sneakNearMinutes * 60.0 / (NEAR_TICKS / 20.0) * NEAR_XP, sneakNearMinutes * NEAR_CAP_PER_MINUTE);
		return near + sneakKills * (sneakAttackXp(hostileHealth, hostileHealth) + killBonus(hostileHealth));
	}

	// ---------------------------------------------------------------- perk numbers

	/** Shadow's ranks: hostiles notice a sneaking player at 90 / 80 / 70 % of their range; Stealth counts 1 / 2 / 3 higher. */
	public static final double[] SHADOW_SIGHT = {0.9, 0.8, 0.7};
	public static final int[] SHADOW_LEVELS = {1, 2, 3};
	/** "Light 7 or less" (Cloak of Dusk, Nightstalker, Ghost). */
	public static final int DARK = 7;
	/** Skulker's cost: in light 12 or more hostiles notice you from 25 % further. */
	public static final int BRIGHT = 12;
	public static final double SKULKER_COST = 1.25;
	public static final double CLOAK_SIGHT = 0.75;
	public static final double CLOAK_WITNESS = 0.25;
	public static final int VANISH_SNEAK_TICKS = 40;
	public static final int VANISH_COOLDOWN = 600;
	public static final double VANISH_RANGE = 32;
	public static final float SILENT_FALL = 2;
	public static final double AMBUSHER = 0.30;
	public static final double HAMSTRING = 0.5;
	public static final int HAMSTRING_TICKS = 60;
	public static final int HAMSTRINGING_TICKS = 40;
	public static final double UNSEEN_BLADE_RANGE = 8;
	public static final int POISON_TICKS = 60;
	public static final double GHOST_RANGE = 4;
	public static final double SHADOWSTEP_RANGE = 8;
	public static final int SHADOWSTEP_COOLDOWN = 240;
	public static final double ASSASSIN = 3.0;
	public static final double ASSASSIN_BOSS = 1.5;
	public static final double COWARD = 0.5;
	public static final double COWARD_TAKEN = 0.15;
	public static final double QUIET_WAY_SIGHT = 0.10;
	public static final double QUIET_WAY_GIVE_UP = 1 / 3.0;
	/** Never Seen: a mob that lost you forgets you twice as fast. Under Its Nose: Darkness lasts half as long. */
	public static final double NEVER_SEEN_FORGET = 0.5;
	public static final double UNDER_ITS_NOSE = 0.5;
	/** "Not attacking": no hit in the last 3 s. */
	public static final int ATTACK_GRACE = 60;
	/** Patient: sneaking still this long readies the next hit. */
	public static final int PATIENT_TICKS = 40;
	public static final int SWIFT_EXIT_TICKS = 40;
	/** Night Hunter: a "sure crit" adds what vanilla's critical arrow adds at most (half the damage, plus 2). */
	public static double nightHunterCrit(double damage) {
		return damage + damage / 2 + 2;
	}

	/** Under Its Nose: within 8 blocks of a Warden that never noticed you, then away past 16. */
	public static final double WARDEN_PASS = 8;

	// Never Seen's tally (target 100): an unseen crime in a Hearth settlement is 5 (20 crimes); without Hearth an unnoticed kill is 2 (50 kills).
	public static final int NEVER_SEEN_CRIME = 5;
	public static final int NEVER_SEEN_KILL = 2;

	/** Fallbacks while the loaded Hearth reads no bonus keys (§14): levels Stealth counts higher. */
	public static final int FALLBACK_CLOAK = 2;
	public static final int FALLBACK_UNSEEN = 1;
	public static final int FALLBACK_GHOST = 5;
	public static final int FALLBACK_QUIET_WAY = 2;
	public static final int MAX_LEVEL = 20;

	// ---------------------------------------------------------------- what a character holds, as Hearth and the hooks read it

	/**
	 * Everything of Stealth's that reaches Hearth, and the moment's conditions.
	 *
	 * @param level         the Stealth level itself
	 * @param shadow        Shadow's rank (0–3)
	 * @param cloak         Cloak of Dusk
	 * @param unseen        the Unseen trait's total, a share (0.08 = 8 %)
	 * @param ghost         the Ghost mastery
	 * @param quietWay      the Teaching
	 * @param lightFingered the Light-fingered trait's total, a share
	 * @param silverTongue  the talent Silver Tongue, a share of market edge
	 * @param sneaking      sneaking now
	 * @param dark          light 7 or less where the player stands
	 * @param night         night in the overworld sense (dark outside)
	 * @param attacking     a hit within the last 3 s
	 */
	public record Holdings(int level, int shadow, boolean cloak, double unseen, boolean ghost, boolean quietWay, double lightFingered,
		double silverTongue, boolean sneaking, boolean dark, boolean night, boolean attacking) {
		public static final Holdings NONE = new Holdings(0, 0, false, 0, false, false, 0, 0, false, false, false, false);

		/** Ghost's state holds: sneaking in light 7 or less, not attacking. */
		public boolean ghostHolds() {
			return this.ghost && this.sneaking && this.dark && !this.attacking;
		}
	}

	/**
	 * The levels Stealth counts higher wherever Hearth reads it (Shadow always; the fallbacks only while Hearth has no
	 * bonus keys).
	 */
	public static int levelsAdded(Holdings h, boolean bonus) {
		int add = h.shadow() > 0 ? SHADOW_LEVELS[Math.min(h.shadow(), 3) - 1] : 0;
		if (!bonus) {
			add += h.cloak() && h.night() ? FALLBACK_CLOAK : 0;
			add += h.unseen() > 0 && h.night() ? FALLBACK_UNSEEN : 0;
			add += h.ghostHolds() ? FALLBACK_GHOST : 0;
			add += h.quietWay() ? FALLBACK_QUIET_WAY : 0;
		}
		return add;
	}

	/** The effective Stealth Hearth reads: the level and what adds to it, at most 20. */
	public static int effectiveLevel(Holdings h, boolean bonus) {
		return Math.min(MAX_LEVEL, Math.max(0, h.level()) + levelsAdded(h, bonus));
	}

	/** The bonus keys Stealth answers, and their values now (0 when none). Read only when Hearth reads keys. */
	public static Map<String, Double> bonuses(Holdings h) {
		Map<String, Double> out = new LinkedHashMap<>();
		double keep = 1;
		keep *= h.cloak() && h.dark() ? 1 - CLOAK_WITNESS : 1;
		keep *= 1 - Math.clamp(h.unseen(), 0, 1);
		keep *= h.quietWay() ? 1 - QUIET_WAY_SIGHT : 1;
		out.put(HearthKeys.STEALTH_SIGHT, 1 - keep);
		out.put(HearthKeys.STEALTH_GIVE_UP, h.quietWay() ? QUIET_WAY_GIVE_UP : 0);
		out.put(HearthKeys.STEALTH_UNSEEN_BEYOND, h.ghostHolds() ? GHOST_RANGE : 0);
		out.put(HearthKeys.PICKPOCKET_AWARENESS, Math.clamp(h.lightFingered(), 0, 1));
		return out;
	}

	/** One bonus key's value (0 for any key Stealth doesn't answer). */
	public static double bonus(Holdings h, String key) {
		return bonuses(h).getOrDefault(key, 0.0);
	}

	/** Light-fingered without bonus keys: animals notice you that much closer (a Mettle-only effect). */
	public static double animalsCloser(Holdings h, boolean bonus) {
		return bonus ? 0 : Math.clamp(h.lightFingered(), 0, 1);
	}

	/** Silver Tongue's share of market edge at Hearth (a price factor, always). */
	public static double priceEdge(Holdings h) {
		return Math.max(0, h.silverTongue());
	}

	// ---------------------------------------------------------------- how far hostiles notice a player

	/**
	 * The factor on a hostile's range for noticing the player (LivingEntity#getVisibilityPercent): Shadow and Shadowed
	 * while under sneaking's cover, Cloak of Dusk in the dark, Skulker's cost in bright light, Footpad's cover while
	 * climbing or swimming, and Ghost (nothing beyond 4 blocks).
	 *
	 * @param cover      sneaking, or Footpad's cover (climbing or swimming)
	 * @param footpadOnly the cover is Footpad's only (vanilla's sneaking factor isn't in the percent then, so it is added)
	 * @param shadowed   the Shadowed trait's total, a share
	 * @param skulkerCost Skulker's factor in bright light (1 without it; Hex 2 softens it, Cursed.down)
	 */
	public static double sight(int shadow, boolean cover, boolean footpadOnly, double shadowed, boolean cloak, double skulkerCost, int light,
		boolean ghostHolds, double distance) {
		double f = 1;
		if (cover) {
			f *= shadow > 0 ? SHADOW_SIGHT[Math.min(shadow, 3) - 1] : 1;
			f *= 1 - Math.clamp(shadowed, 0, 0.9);
			f *= footpadOnly ? 0.8 : 1;
		}
		if (cloak && light <= DARK) {
			f *= CLOAK_SIGHT;
		}
		if (skulkerCost > 1 && light >= BRIGHT) {
			f *= skulkerCost;
		}
		if (ghostHolds && distance > GHOST_RANGE) {
			return 0;
		}
		return f;
	}

	/**
	 * A sneak attack's damage factor from perks (not the cards' ceilings: traits are counted there). {@code coward} is
	 * Coward's Edge's share (0 without it; Hex 4 raises it, Cursed.up).
	 */
	public static double sneakAttack(boolean ambusher, double coward, boolean assassinFirst, boolean boss) {
		double m = 1 + (ambusher ? AMBUSHER : 0) + Math.max(0, coward);
		if (assassinFirst) {
			m *= boss ? ASSASSIN_BOSS : ASSASSIN;
		}
		return m;
	}

	/** How long a mob that lost sight of the player keeps hunting: Vanishing and Never Seen shorten it. */
	public static int unseenMemory(int ticks, double vanishing, boolean neverSeen) {
		double t = ticks * (1 - Math.clamp(vanishing, 0, 0.9)) * (neverSeen ? NEVER_SEEN_FORGET : 1);
		return Math.max(1, (int) Math.round(t));
	}

	/** Hushed: how far a listener still hears the player's vibrations. */
	public static double hushedRadius(double radius, double hushed) {
		return radius * (1 - Math.clamp(hushed, 0, 0.9));
	}

	/** Darkness and Blindness on the player: Lurking shortens both, Under Its Nose halves Darkness. */
	public static int darkDuration(int ticks, boolean darkness, double lurking, boolean underItsNose) {
		double t = ticks * (1 - Math.clamp(lurking, 0, 0.9));
		if (darkness && underItsNose) {
			t *= UNDER_ITS_NOSE;
		}
		return Math.max(1, (int) Math.round(t));
	}
}
