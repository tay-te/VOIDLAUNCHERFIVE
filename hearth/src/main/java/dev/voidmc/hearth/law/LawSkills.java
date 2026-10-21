package dev.voidmc.hearth.law;

import dev.voidmc.hearth.api.HearthServices;
import dev.voidmc.hearth.api.PlayerSkills;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.api.StandingEvents;
import dev.voidmc.hearth.law.justice.PlayerLaw;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

/**
 * The law's reading of a character's skills ({@link PlayerSkills}, a character mod's; neutral without one) and the XP it
 * reports back. The law keeps its own thief's practice ({@link PlayerLaw}); against a character mod's Thievery the
 * better of the two counts, so without one every minigame plays exactly as before. Server thread only.
 */
public final class LawSkills {
	private LawSkills() {
	}

	/** Lockpicking skill, 0–20: the law's practice or the character's Thievery, whichever is better. */
	public static int lockpick(ServerPlayer player) {
		return better(PlayerLaw.of(player).lockpickSkill(), level(player, PlayerSkills.THIEVERY));
	}

	/** Pickpocketing skill, 0–20, as {@link #lockpick}. */
	public static int pickpocket(ServerPlayer player) {
		return better(PlayerLaw.of(player).pickpocketSkill(), level(player, PlayerSkills.THIEVERY));
	}

	/** Stealth, 0–20 (a character mod's; 0 without one): shortens how far folk see and hear a sneaking player. */
	public static int stealth(ServerPlayer player) {
		return level(player, PlayerSkills.STEALTH);
	}

	/** The pure rule: the better of the law's practice skill and a character's level, within 0–20. */
	public static int better(int practice, int level) {
		return Math.clamp(Math.max(practice, level), 0, 20);
	}

	/**
	 * A law speech check's odds (persuading a guard, bribing or threatening a witness) with the character's Speech and
	 * Presence added: +1 % per level of Speech and +1 % per point of Presence above 3, kept within the law's 5–92 %.
	 * Speech 0 and Presence 3 leave the odds untouched.
	 */
	public static double speech(ServerPlayer player, double odds) {
		PlayerSkills skills = HearthServices.playerSkills();
		return speech(odds, skills.level(player, PlayerSkills.SPEECH), skills.attribute(player, PlayerSkills.PRESENCE));
	}

	/** The pure rule of {@link #speech(ServerPlayer, double)}. */
	public static double speech(double odds, int speech, int presence) {
		double bonus = 0.01 * Math.clamp(speech, 0, 20) + 0.01 * (Math.clamp(presence, 1, 20) - 3);
		if (bonus == 0) {
			return odds;
		}
		return Math.clamp(odds + bonus, 0.05, 0.92);
	}

	/** The most a character mod's named bonus may add to one law check (Hearth's clamp). */
	public static final double MAX_SPEECH_BONUS = 0.15;

	/**
	 * {@link #speech(ServerPlayer, double)} for a check of one kind ({@code persuade}, {@code bribe} or {@code intimidate}:
	 * a threat): a character mod's {@code hearth:speech/<kind>} odds are added too (0–{@link #MAX_SPEECH_BONUS}), still
	 * within the law's 5–92 %.
	 */
	public static double speech(ServerPlayer player, String kind, double odds) {
		PlayerSkills skills = HearthServices.playerSkills();
		double p = speech(odds, skills.level(player, PlayerSkills.SPEECH), skills.attribute(player, PlayerSkills.PRESENCE));
		return withBonus(p, PlayerSkills.bonus(skills, player, PlayerSkills.BONUS_SPEECH + kind, 0, MAX_SPEECH_BONUS));
	}

	/** The pure rule: odds plus a named bonus (0–{@link #MAX_SPEECH_BONUS}), within 5–92 %; a bonus of 0 leaves them. */
	public static double withBonus(double odds, double bonus) {
		double b = Math.clamp(Double.isNaN(bonus) ? 0 : bonus, 0, MAX_SPEECH_BONUS);
		return b == 0 ? odds : Math.clamp(odds + b, 0.05, 0.92);
	}

	/** The speech kind a law check's XP reason reads as ({@code bribe_witness} is a bribe, {@code threaten_witness} a threat). */
	public static String kindOf(String what) {
		return what.startsWith("bribe") ? "bribe" : what.startsWith("threaten") ? "intimidate" : "persuade";
	}

	// ---- being seen (docs/requests/mettle-speech-stealth.md §12)

	/** The most a bonus may take off how far a sneak is seen, as a share. */
	public static final double MAX_SIGHT_BONUS = 0.5;
	/** {@code hearth:stealth/unseen_beyond} is held within these blocks when on. */
	public static final double MIN_UNSEEN = 2;
	public static final double MAX_UNSEEN = 32;
	/** The most a bonus may take off a guard's search after losing the player. */
	public static final double MAX_GIVE_UP_BONUS = 0.75;
	/** The most a bonus may take off how fast a mark notices a pickpocket. */
	public static final double MAX_AWARENESS_BONUS = 0.5;

	/** A character mod's share off how far a sneaking player is seen ({@code hearth:stealth/sight}), 0–0.5. */
	public static double sightBonus(ServerPlayer player) {
		return PlayerSkills.bonus(HearthServices.playerSkills(), player, PlayerSkills.BONUS_STEALTH_SIGHT, 0, MAX_SIGHT_BONUS);
	}

	/** Blocks beyond which a sneaking player is not seen at all ({@code hearth:stealth/unseen_beyond}), 0 when off, else 2–32. */
	public static double unseenBeyond(ServerPlayer player) {
		return unseenBeyond(PlayerSkills.bonus(HearthServices.playerSkills(), player, PlayerSkills.BONUS_STEALTH_UNSEEN_BEYOND, 0, MAX_UNSEEN));
	}

	/** The pure rule of {@link #unseenBeyond(ServerPlayer)}. */
	public static double unseenBeyond(double blocks) {
		return Double.isNaN(blocks) || blocks <= 0 ? 0 : Math.clamp(blocks, MIN_UNSEEN, MAX_UNSEEN);
	}

	/** How long a guard searches for a player they lost, in ticks: {@code base} less a character's {@code hearth:stealth/give_up} share. */
	public static int searchTicks(ServerPlayer player, int base) {
		return searchTicks(base, PlayerSkills.bonus(HearthServices.playerSkills(), player, PlayerSkills.BONUS_STEALTH_GIVE_UP, 0, MAX_GIVE_UP_BONUS));
	}

	/** The pure rule of {@link #searchTicks(ServerPlayer, int)}. */
	public static int searchTicks(int base, double share) {
		double s = Math.clamp(Double.isNaN(share) ? 0 : share, 0, MAX_GIVE_UP_BONUS);
		return s == 0 ? base : (int) Math.round(base * (1 - s));
	}

	/** A mark's awareness rate for this thief: less a character's {@code hearth:pickpocket/awareness} share. */
	public static double awareness(ServerPlayer player, double rate) {
		return awareness(rate, PlayerSkills.bonus(HearthServices.playerSkills(), player, PlayerSkills.BONUS_PICKPOCKET_AWARENESS, 0, MAX_AWARENESS_BONUS));
	}

	/** The pure rule of {@link #awareness(ServerPlayer, double)}. */
	public static double awareness(double rate, double share) {
		double s = Math.clamp(Double.isNaN(share) ? 0 : share, 0, MAX_AWARENESS_BONUS);
		return s == 0 ? rate : rate * (1 - s);
	}

	// ---- a second word (hearth:speech/retry)

	/**
	 * Uses one of the player's retries for today ({@code hearth:speech/retry}, 0–3 a sim day, one budget shared with
	 * conversations' {@link dev.voidmc.hearth.standing.speech.SpeechRetries}): a failed word with a guard taken back, so
	 * it may be tried again at once. False without one left (or without a skills mod).
	 */
	public static boolean retry(ServerPlayer player, long day) {
		return dev.voidmc.hearth.standing.speech.SpeechRetries.take(player, day);
	}

	/** Reports XP to the character mod (a no-op without one). {@code reason} under {@code hearth:law/}. */
	public static void award(ServerPlayer player, String skill, float xp, String reason) {
		if (xp > 0) {
			HearthServices.playerSkills().award(player, skill, xp, "hearth:law/" + reason);
		}
	}

	/** Speech XP for a law check passed (a guard talked round, a witness hushed): 20, or 7 for a failed one. */
	public static void speechCheck(ServerPlayer player, boolean passed, String what) {
		award(player, PlayerSkills.SPEECH, passed ? 20.0F : 7.0F, what);
	}

	/**
	 * {@link #speechCheck(ServerPlayer, boolean, String)}, and {@link StandingEvents#LAW_CHECK} with where it happened and
	 * the odds it was rolled at.
	 */
	public static void speechCheck(ServerPlayer player, @Nullable Settlement settlement, boolean passed, String what, double odds) {
		speechCheck(player, passed, what);
		StandingEvents.LAW_CHECK.invoker().onSpeechCheck(player, settlement, what, passed, StandingEvents.difficulty(odds));
	}

	/** Thievery XP for a picked lock: 10 a tier (a very hard lock is worth 50). */
	public static float lockXp(int tier) {
		return 10.0F * Math.clamp(tier, 1, 5);
	}

	/** Thievery XP for something lifted from a pocket: 6, plus one per 5 bits it is worth, at most 30. */
	public static float pocketXp(long value) {
		return (float) Math.min(30.0, 6.0 + Math.max(0, value) / 5.0);
	}

	/** Stealth XP for a crime nobody saw: 5, plus one per 10 bits of what was taken, at most 25. */
	public static float unseenXp(long value) {
		return (float) Math.min(25.0, 5.0 + Math.max(0, value) / 10.0);
	}

	private static int level(ServerPlayer player, String skill) {
		return HearthServices.playerSkills().level(player, skill);
	}
}
