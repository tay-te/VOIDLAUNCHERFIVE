package dev.voidmc.mettle.character.skill.speech;

import dev.voidmc.mettle.compat.HearthKeys;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Speech's numbers and rules free of the game (docs/BUILD_FEEL.md §14.2), so they can be tested: XP, what reaches VOID
 * Hearth through which channel (the effective Speech and Presence, a bonus key, a price factor) and the fallbacks while
 * the loaded Hearth reads no bonus keys. Rule (a), one channel per effect: {@code compat.HearthChannels} lists every
 * effect, and {@code HearthChannelsTest} holds each one to it.
 */
public final class SpeechRules {
	private SpeechRules() {
	}

	public static final int MAX_LEVEL = 20;
	public static final int MAX_ATTRIBUTE = 20;

	// ---------------------------------------------------------------- XP without Hearth (§14.2)

	/** A villager trade: 3, and 1 per emerald that changed hands (tapers like any source). */
	public static final double VILLAGER_TRADE_XP = 3;
	public static final double PER_EMERALD_XP = 1;
	/** A zombie villager cured. */
	public static final double CURE_XP = 60;
	/** Hero of the Village (a raid won). */
	public static final double HERO_XP = 100;
	/** A piglin barter. */
	public static final double BARTER_XP = 1;

	public static double villagerTradeXp(int emeralds) {
		return VILLAGER_TRADE_XP + PER_EMERALD_XP * Math.max(0, emeralds);
	}

	// ---------------------------------------------------------------- perk numbers

	/** Haggler's ranks: market edge +2 / 4 / 6 %; villagers' trades of 4 or more emeralds cost 1 / 2 / 3 less. */
	public static final double[] HAGGLER_EDGE = {0.02, 0.04, 0.06};
	public static final int[] HAGGLER_VILLAGER = {1, 2, 3};
	public static final int HAGGLER_VILLAGER_MIN_COST = 4;
	/** Glib's ranks: Speech counts 1 / 2 / 3 higher; without Hearth, villagers' gossip grows 25 / 50 / 75 % faster. */
	public static final int[] GLIB_LEVELS = {1, 2, 3};
	public static final double[] GLIB_GOSSIP = {0.25, 0.50, 0.75};
	public static final double PROVISIONER = 0.10;
	public static final int SHREWD_EYE_BAND = 5;
	public static final double CHARMER = 0.25;
	public static final double BARTERER_CHANCE = 0.25;
	public static final int COMMANDING_VOICE_PRESENCE = 2;
	public static final double COMMANDING_VOICE_ALLY_XP = 0.05;
	public static final double MERCHANT_PRINCE_EDGE = 0.05;
	/** Hero's Welcome: Hero of the Village after a raid you helped win lasts 3 in-game days. */
	public static final int HEROS_WELCOME_TICKS = 3 * 24000;
	public static final int VOICE_SPEECH = 4;
	public static final int VOICE_PRESENCE = 2;
	public static final int HONEYED_LIES_SPEECH = 4;
	public static final double HONEYED_LIES_COST = 0.05;
	public static final int DEMAGOGUE_PRESENCE = 4;
	/** Demagogue's price: skill XP from everything but Speech × 0.8. */
	public static final double DEMAGOGUE_XP = 0.8;
	public static final int MANNERS_PRESENCE = 2;
	public static final double MARKET_REGULAR = 0.25;
	public static final double CARAVAN_CARRY = 0.10;
	public static final double TAVERN_KEEPER = 0.20;
	public static final double ALLY_RANGE = 8;

	/** Fallbacks while the loaded Hearth reads no bonus keys (§14). */
	public static final double FALLBACK_SHREWD_EYE_EDGE = 0.02;
	public static final int FALLBACK_CHARMER_PRESENCE = 1;
	public static final int FALLBACK_SECOND_WORD_SPEECH = 1;
	public static final double FALLBACK_NEVER_SOUR_EDGE = 0.02;

	// ---------------------------------------------------------------- feats (one tally each)

	/** Golden Mouth: 50 Hearth speech checks won. */
	public static final long GOLDEN_MOUTH_TARGET = 50;
	public static final int GOLDEN_MOUTH_WON = 1;
	/** Before Hearth fires its check events: 50 checks tried, counted from award reasons. */
	public static final int GOLDEN_MOUTH_TRIED = 1;
	/** Without Hearth: 5 zombie villagers cured. */
	public static final int GOLDEN_MOUTH_CURE = 10;
	/** Market Regular: 100 struck haggles, or 200 villager trades without Hearth. */
	public static final long MARKET_REGULAR_TARGET = 200;
	public static final int MARKET_REGULAR_HAGGLE = 2;
	public static final int MARKET_REGULAR_VILLAGER = 1;

	/**
	 * Golden Mouth: a failed check gives full XP. Hearth gives a third of a talk check's XP on a failure (15–45 by
	 * difficulty) and 7 of a law check's 20; this is what to add on top of what a failure already gave.
	 */
	public static double goldenMouthTopUp(double failedXp, boolean law) {
		if (failedXp <= 0) {
			return 0;
		}
		return law ? failedXp * (20.0 / 7.0 - 1) : failedXp * 2;
	}

	// ---------------------------------------------------------------- what a character holds, as Hearth reads it

	/**
	 * Everything of Speech's that reaches Hearth.
	 *
	 * @param level            the Speech level itself
	 * @param presence         Presence as the character has it (background and points)
	 * @param haggler          Haggler's rank (0–3)
	 * @param glib             Glib's rank (0–3)
	 * @param haggling         the Haggling trait's total, a share (0.02 = 2 %)
	 * @param persuasive       the Persuasive trait's total, in levels
	 * @param shrewd           the Shrewd trait's total, in levels
	 * @param patient          the Patient trait's total, a share
	 * @param smooth           the Smooth trait's total, a share
	 * @param imposing         the Imposing trait's total, a share
	 * @param charming         the Charming trait's total, a share
	 * @param shrewdEye        Shrewd Eye
	 * @param charmer          Charmer
	 * @param commandingVoice  Commanding Voice
	 * @param secondWord       Second Word
	 * @param merchantPrince   the Merchant Prince mastery
	 * @param herosWelcome     the Hero's Welcome mastery
	 * @param voice            the Voice of the Realm mastery
	 * @param honeyedLies      Honeyed Lies
	 * @param honeyedCost      Honeyed Lies' cost to the edge (0.05; Hex 2 softens it)
	 * @param demagogue        Demagogue
	 * @param manners          The Courtier's Manners (the Teaching)
	 * @param provisioner      Provisioner
	 * @param provisioning     the Provisioning trait's total, a share
	 * @param appraising       the Appraising trait's total, a share
	 * @param tavernKeeper     the Tavern-Keeper synergy
	 */
	public record Holdings(int level, int presence, int haggler, int glib, double haggling, double persuasive, double shrewd, double patient,
		double smooth, double imposing, double charming, boolean shrewdEye, boolean charmer, boolean commandingVoice, boolean secondWord,
		boolean merchantPrince, boolean herosWelcome, boolean voice, boolean honeyedLies, double honeyedCost, boolean demagogue, boolean manners,
		boolean provisioner, double provisioning, double appraising, boolean tavernKeeper) {
		public static final Holdings NONE = new Holdings(0, 3, 0, 0, 0, 0, 0, 0, 0, 0, 0, false, false, false, false, false, false, false, false, 0, false,
			false, false, 0, 0, false);
	}

	private static int rank(int[] ranks, int rank) {
		return rank > 0 ? ranks[Math.min(rank, ranks.length) - 1] : 0;
	}

	/** Levels Speech counts higher wherever Hearth reads it (the fallback only while Hearth reads no keys). */
	public static int speechAdded(Holdings h, boolean bonus) {
		int add = rank(GLIB_LEVELS, h.glib()) + (int) Math.floor(Math.max(0, h.persuasive()) + 1e-9);
		add += h.voice() ? VOICE_SPEECH : 0;
		add += h.honeyedLies() ? HONEYED_LIES_SPEECH : 0;
		add += !bonus && h.secondWord() ? FALLBACK_SECOND_WORD_SPEECH : 0;
		return add;
	}

	/** The Speech Hearth reads: the level and what adds to it, at most 20. */
	public static int effectiveSpeech(Holdings h, boolean bonus) {
		return Math.min(MAX_LEVEL, Math.max(0, h.level()) + speechAdded(h, bonus));
	}

	/** Points Presence counts higher wherever Hearth reads it (the fallback only while Hearth reads no keys). */
	public static int presenceAdded(Holdings h, boolean bonus) {
		int add = h.commandingVoice() ? COMMANDING_VOICE_PRESENCE : 0;
		add += h.voice() ? VOICE_PRESENCE : 0;
		add += h.demagogue() ? DEMAGOGUE_PRESENCE : 0;
		add += h.manners() ? MANNERS_PRESENCE : 0;
		add += !bonus && h.charmer() ? FALLBACK_CHARMER_PRESENCE : 0;
		return add;
	}

	/** The Presence Hearth reads: the attribute and what adds to it, at most 20 (never below the attribute). */
	public static int effectivePresence(Holdings h, boolean bonus) {
		return Math.max(h.presence(), Math.min(MAX_ATTRIBUTE, h.presence() + presenceAdded(h, bonus)));
	}

	/** The bonus keys Speech answers, and their values now (0 when none). Read only when Hearth reads keys. */
	public static Map<String, Double> bonuses(Holdings h) {
		Map<String, Double> out = new LinkedHashMap<>();
		out.put(HearthKeys.HAGGLE_BAND, (h.shrewdEye() ? SHREWD_EYE_BAND : 0) + Math.floor(Math.max(0, h.shrewd()) + 1e-9));
		out.put(HearthKeys.HAGGLE_PATIENCE, Math.max(0, h.patient()));
		out.put(HearthKeys.HAGGLE_NEVER_SOUR, h.merchantPrince() ? 1.0 : 0.0);
		out.put(HearthKeys.SPEECH + "persuade", Math.max(0, h.smooth()));
		out.put(HearthKeys.SPEECH + "flatter", Math.max(0, h.smooth()));
		out.put(HearthKeys.SPEECH + "intimidate", Math.max(0, h.imposing()));
		out.put(HearthKeys.SPEECH + "bribe", 0.0);
		out.put(HearthKeys.SPEECH_RETRY, h.secondWord() ? 1.0 : 0.0);
		out.put(HearthKeys.STANDING_GAIN, (h.charmer() ? CHARMER : 0) + Math.max(0, h.charming()));
		out.put(HearthKeys.GREET_HERO, h.herosWelcome() ? 1.0 : 0.0);
		return out;
	}

	/** One bonus key's value (0 for any key Speech doesn't answer). */
	public static double bonus(Holdings h, String key) {
		return bonuses(h).getOrDefault(key, 0.0);
	}

	/**
	 * Speech's share of the market edge at Hearth (the price channel, {@code MettlePrices}): Haggler, Haggling, Merchant
	 * Prince, Honeyed Lies' cost; and while Hearth reads no keys the fallbacks of Shrewd Eye and Merchant Prince's never-sour.
	 */
	public static double edge(Holdings h, boolean bonus) {
		double e = (h.haggler() > 0 ? HAGGLER_EDGE[Math.min(h.haggler(), 3) - 1] : 0) + Math.max(0, h.haggling());
		e += h.merchantPrince() ? MERCHANT_PRINCE_EDGE : 0;
		e -= h.honeyedLies() ? Math.max(0, h.honeyedCost()) : 0;
		if (!bonus) {
			e += h.shrewdEye() ? FALLBACK_SHREWD_EYE_EDGE : 0;
			e += h.merchantPrince() ? FALLBACK_NEVER_SOUR_EDGE : 0;
		}
		return e;
	}

	/**
	 * Speech's per-kind price factor at a Hearth market, on top of the edge: Provisioner (food, drink, potions both ways),
	 * Provisioning (food and drink bought), Appraising (tools, weapons, armour sold), Tavern-Keeper (food you cooked, sold).
	 */
	public static double kindFactor(Holdings h, boolean food, boolean drinkOrPotion, boolean gear, boolean cookedByYou, boolean playerBuys) {
		boolean provisioner = h.provisioner();
		double provisioning = h.provisioning();
		double appraising = h.appraising();
		boolean tavernKeeper = h.tavernKeeper();
		double f = 1;
		boolean provisions = food || drinkOrPotion;
		if (playerBuys) {
			f *= provisioner && provisions ? 1 - PROVISIONER : 1;
			f *= provisions ? 1 - Math.clamp(provisioning, 0, 0.5) : 1;
		} else {
			f *= provisioner && provisions ? 1 + PROVISIONER : 1;
			f *= gear ? 1 + Math.clamp(appraising, 0, 0.5) : 1;
			f *= tavernKeeper && food && cookedByYou ? 1 + TAVERN_KEEPER : 1;
		}
		return f;
	}

	// ---------------------------------------------------------------- Mettle-only numbers

	/** Haggler without Hearth: what a villager offer of {@code cost} emeralds comes down by. */
	public static int villagerCut(int haggler, int cost) {
		return cost >= HAGGLER_VILLAGER_MIN_COST ? rank(HAGGLER_VILLAGER, haggler) : 0;
	}

	/** Glib without Hearth: the share by which villagers' trading gossip grows faster. */
	public static double gossip(int glib) {
		return glib > 0 ? GLIB_GOSSIP[Math.min(glib, 3) - 1] : 0;
	}

	/** Hero of the Village's length: Hero's Welcome makes it 3 days (if longer), Heroic X longer on top. */
	public static int heroTicks(int ticks, boolean herosWelcome, double heroic) {
		double t = herosWelcome ? Math.max(ticks, HEROS_WELCOME_TICKS) : ticks;
		return (int) Math.min(Integer.MAX_VALUE, Math.round(t * (1 + Math.max(0, heroic))));
	}

	/**
	 * The XP multiplier Speech's perks put on a skill: Gregarious (Speech XP), Demagogue's price (every other skill;
	 * {@code demagogue} is its factor, 1 without it) and allies' Rallying and Commanding Voice within 8 blocks.
	 */
	public static double xp(boolean speech, double gregarious, double demagogue, double allies) {
		double m = 1 + Math.max(0, allies);
		if (speech) {
			m *= 1 + Math.max(0, gregarious);
		} else {
			m *= demagogue > 0 ? demagogue : 1;
		}
		return m;
	}

	/** Coins' weight factor: Ledger-Keeper makes them weightless, Coinwise lighter. */
	public static float coinWeight(boolean ledgerKeeper, double coinwise) {
		return ledgerKeeper ? 0f : (float) (1 - Math.clamp(coinwise, 0, 1));
	}
}
