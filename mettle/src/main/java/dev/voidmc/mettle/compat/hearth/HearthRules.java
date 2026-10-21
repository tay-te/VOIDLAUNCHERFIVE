package dev.voidmc.mettle.compat.hearth;

import dev.voidmc.mettle.core.Curves;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The numbers Mettle gives Hearth (INTEGRATION.md §1, BALANCE.md §9), as plain functions with no Hearth or game types so
 * JUnit can hold them still. Skills are Mettle skill paths ({@code "blades"}); tiers, quest types and jobs are Hearth's
 * serialized names ({@code "city"}, {@code "bounty"}, {@code "smith"}).
 */
public final class HearthRules {
	private HearthRules() {
	}

	// ------------------------------------------------------------------ checks

	public static final double CHECK_MIN = 0.05;
	public static final double CHECK_MAX = 0.95;

	/**
	 * A plain skill check's odds: 50 % + 5 % × (level + attribute ÷ 2 − difficulty), within 5–95 % (CHARACTER.md §7; the
	 * rule Hearth's {@code PlayerSkills.odds} documents). So a check is even when the difficulty matches the skill level
	 * plus half the governing attribute: Speech 5 with Presence 10 meets a difficulty-10 persuasion at 50 %.
	 */
	public static double checkOdds(int level, int attribute, int difficulty) {
		return Math.clamp(0.5 + 0.05 * (level + attribute / 2.0 - difficulty), CHECK_MIN, CHECK_MAX);
	}

	// ------------------------------------------------------------------ prices

	/** Better prices per point of Presence above 3 (worse below). BALANCE.md §4. */
	public static final double PRESENCE_PER_POINT = 0.005;
	/** Better prices per Speech level: 10 % at Speech 20. */
	public static final double SPEECH_PER_LEVEL = 0.005;
	/** The Merchant's Coin Sense, at a Hearth market. */
	public static final double COIN_SENSE = 0.05;
	/** The edge never goes past these, whatever comes later (Hearth clamps the product of all mods to 0.5–1.5 too). */
	public static final double EDGE_MIN = -0.10;
	public static final double EDGE_MAX = 0.25;

	/**
	 * The player's edge at a market: the share by which they buy cheaper and sell dearer. Presence 3, Speech 0 and no
	 * Coin Sense is exactly 0 (prices untouched); Presence 20 and Speech 20 with Coin Sense is 23.5 %.
	 */
	public static double priceEdge(int presence, int speech, boolean coinSense) {
		double edge = PRESENCE_PER_POINT * (presence - 3) + SPEECH_PER_LEVEL * speech + (coinSense ? COIN_SENSE : 0);
		return Math.clamp(edge, EDGE_MIN, EDGE_MAX);
	}

	/** The {@code PriceModifier} factor: below 1 when the player buys (cheaper), above 1 when they sell (dearer). */
	public static double priceFactor(double edge, boolean playerBuys) {
		return playerBuys ? 1 - edge : 1 + edge;
	}

	// ------------------------------------------------------------------ business

	/** Speech XP per crown (100 bits) that changes hands at a market counter, for the first crowns of a sim day... */
	public static final double SPEECH_XP_PER_CROWN = 5;
	/** ...up to this many crowns of business a day; beyond it a crown is worth only {@link #SPEECH_XP_PER_CROWN_LATE}. */
	public static final double FULL_RATE_CROWNS_PER_DAY = 20;
	public static final double SPEECH_XP_PER_CROWN_LATE = 1;
	public static final long BITS_PER_CROWN = 100;

	/**
	 * Speech XP for a trade of {@code crowns}, when {@code crownsToday} were already traded this sim day. Buying and
	 * selling the same goods back and forth loses the market's spread every time, and past 20 crowns a day it pays a fifth.
	 */
	public static double tradeXp(double crownsToday, double crowns) {
		if (crowns <= 0) {
			return 0;
		}
		double full = Math.clamp(FULL_RATE_CROWNS_PER_DAY - Math.max(0, crownsToday), 0, crowns);
		return full * SPEECH_XP_PER_CROWN + (crowns - full) * SPEECH_XP_PER_CROWN_LATE;
	}

	// ------------------------------------------------------------------ quests

	/** Stands for "the combat skill the player used most on the quest" (Blades, Heavy Weapons or Marksmanship). */
	public static final String COMBAT = "combat";

	/** What a completed quest is worth: skill XP (base, before favoured and Wits) to one skill or to {@link #COMBAT}. */
	public record QuestXp(String skill, double xp) {
	}

	public static final double BOUNTY_XP = 120;
	public static final double TRAVEL_XP = 100;
	public static final double SUPPLY_XP = 80;
	public static final double EXPLORATION_XP = 150;
	public static final double GATHERING_XP = 60;
	public static final double PERSONAL_XP = 80;
	public static final double DISASTER_XP = 100;
	public static final double OFFICIAL_XP = 80;
	public static final double CRIME_XP = 100;

	/** Words in a supply or contract template's path that mean the goods came out of the ground. */
	private static final List<String> MINED = List.of("stone", "ore", "clay", "sand", "salt", "adobe", "glass", "fuel", "kiln", "smelter", "quarry", "survey", "mine", "blast");
	/** ...and that mean the goods came off an anvil. */
	private static final List<String> SMITHED = List.of("tool", "metal", "smithy", "guild");

	/**
	 * The XP a completed quest grants (INTEGRATION.md §1.1), from its type and its template's path
	 * ({@code bounty/wolves}, {@code supply/ore}, {@code official/guard/night_patrol}). Character XP comes with it:
	 * character XP is all skill XP.
	 */
	public static QuestXp questXp(String type, String templatePath) {
		String path = templatePath.toLowerCase(Locale.ROOT);
		return switch (type) {
			case "bounty", "caravan", "law" -> new QuestXp(COMBAT, BOUNTY_XP);
			case "courier", "escort" -> new QuestXp("athletics", TRAVEL_XP);
			case "supply", "contract" -> new QuestXp(goodsSkill(path), SUPPLY_XP);
			case "exploration", "scouting" -> new QuestXp("survival", EXPLORATION_XP);
			case "gathering" -> new QuestXp("survival", GATHERING_XP);
			case "personal" -> new QuestXp("speech", PERSONAL_XP);
			case "disaster" -> path.contains("wolves") ? new QuestXp(COMBAT, DISASTER_XP)
				: has(path, MINED) ? new QuestXp("mining", DISASTER_XP) : new QuestXp("survival", DISASTER_XP);
			case "official" -> path.contains("/guard/") || path.contains("caravan") ? new QuestXp(COMBAT, OFFICIAL_XP)
				: path.contains("/builder/") ? new QuestXp(goodsSkill(path), OFFICIAL_XP) : new QuestXp("speech", OFFICIAL_XP);
			case "crime" -> new QuestXp("stealth", CRIME_XP);
			default -> new QuestXp("survival", GATHERING_XP);
		};
	}

	/** Mining for stone, ore, clay and fuel; Smithing for tools and metal; Survival for food, cloth, wood and the rest. */
	static String goodsSkill(String path) {
		if (has(path, SMITHED)) {
			return "smithing";
		}
		return has(path, MINED) ? "mining" : "survival";
	}

	private static boolean has(String path, List<String> words) {
		for (String w : words) {
			if (path.contains(w)) {
				return true;
			}
		}
		return false;
	}

	// ------------------------------------------------------------------ trainers

	/** The highest level a trainer of a settlement this size teaches to: camp 4 · hamlet 6 · village 10 · town 15 · city 20. */
	public static int trainerCap(String tier) {
		return switch (tier) {
			case "camp" -> 4;
			case "hamlet" -> 6;
			case "village" -> 10;
			case "town" -> 15;
			case "city" -> 20;
			default -> 4;
		};
	}

	/** A lesson is worth this share of the XP between the player's level and the next. */
	public static final double LESSON_SHARE = 0.4;

	/** A lesson's XP (base, before favoured and Wits) for a player at {@code level}; 0 at the skill cap. */
	public static double lessonXp(int level) {
		if (level >= Curves.SKILL_CAP) {
			return 0;
		}
		return Math.round(LESSON_SHARE * (Curves.skillXpFor(level + 1) - Curves.skillXpFor(level)));
	}

	/**
	 * A lesson's fee in bits, for a player at {@code level} (so learning towards {@code level + 1}): 2.5 × next² + 15,
	 * to the nearest 5. Blades 2 → 3 costs 40 bits, 9 → 10 about 2½ crowns, 19 → 20 about 10 crowns.
	 */
	public static long lessonFee(int level) {
		int next = Math.min(level, Curves.SKILL_CAP - 1) + 1;
		double fee = 2.5 * next * next + 15;
		return Math.round(fee / 5.0) * 5;
	}

	/** Whether a trainer of this tier can still teach a player at {@code level}. */
	public static boolean canTeach(String tier, int level) {
		return level < trainerCap(tier) && level < Curves.SKILL_CAP;
	}

	/** A city master's Teaching (the skill's earned perk, TREES.md): its fee, and the skill level it asks first. */
	public static final long TEACHING_FEE = 1000;
	public static final int TEACHING_MIN_LEVEL = 6;
	/** Only a city's master teaches the Teaching. */
	public static final String TEACHING_TIER = "city";

	/**
	 * Who trains what, by Hearth job: the first skill is the trade's own. A guard teaches the sword and the shield; a hunter
	 * the bow and the wild; a fence what fences know. Wave-2 skills that Hearth already reads (Speech, Thievery, Stealth)
	 * are trained too: their levels move prices, checks, locks and witnesses even before their trees ship.
	 */
	public static final Map<String, List<String>> TRAINERS = Map.ofEntries(
		Map.entry("miner", List.of("mining")),
		Map.entry("quarrier", List.of("mining")),
		Map.entry("smith", List.of("smithing")),
		Map.entry("guard", List.of("blades", "defense")),
		Map.entry("forester", List.of("heavy_weapons")),
		Map.entry("hunter", List.of("marksmanship", "survival")),
		Map.entry("courier", List.of("athletics")),
		Map.entry("hauler", List.of("athletics")),
		Map.entry("healer", List.of("survival")),
		Map.entry("merchant", List.of("speech")),
		Map.entry("trader", List.of("speech")),
		Map.entry("fence", List.of("thievery", "stealth")));

	/**
	 * Whose Teaching a city master of this job gives (TREES.md: "a city's master miner", "a city's guard"...). The guard
	 * captain TREES.md names for Defense is a guard to Hearth.
	 */
	public static final Map<String, String> TEACHERS = Map.of(
		"mining", "miner",
		"blades", "guard",
		"heavy_weapons", "forester",
		"marksmanship", "hunter",
		"defense", "guard",
		"athletics", "courier",
		"survival", "healer",
		"smithing", "smith",
		"stealth", "fence");

	/** Skills a resident of this job trains; empty for most folk. */
	public static List<String> trains(String job) {
		return TRAINERS.getOrDefault(job, List.of());
	}

	/** Whether a resident of this job, in a settlement of this tier, teaches the skill's Teaching. */
	public static boolean teaches(String job, String tier, String skill) {
		return TEACHING_TIER.equals(tier) && job.equals(TEACHERS.get(skill));
	}

	/** The skills Hearth reads by name ({@code PlayerSkills.SPEECH} ...): known even though their trees are unreleased. */
	public static final Set<String> HEARTH_SKILLS = Set.of("speech", "thievery", "stealth");
}
