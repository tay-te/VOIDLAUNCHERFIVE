package dev.voidmc.mettle.compat;

import java.util.List;

/**
 * Hearth's bonus keys (request 12, shipped by Hearth at ae4da055 as {@code PlayerSkills.BONUS_*}), read through
 * {@code PlayerSkills.bonus(player, key)}. Hearth keeps every clamp of its own (its ARCHITECTURE.md §11). These are the
 * same strings, for code outside {@code compat.hearth} that may not name a Hearth type; {@code HearthKeysTest} holds them
 * equal to Hearth's constants.
 */
public final class HearthKeys {
	/** Speech levels added only for reading a merchant's limit band (Hearth: 0–10). */
	public static final String HAGGLE_BAND = "hearth:haggle/band";
	/** A share added to a merchant's patience (Hearth: 0–0.5). */
	public static final String HAGGLE_PATIENCE = "hearth:haggle/patience";
	/** 1: a merchant walking away is never sour. */
	public static final String HAGGLE_NEVER_SOUR = "hearth:haggle/never_sour";
	/** Odds added to a speech check of one kind: append {@code persuade}, {@code intimidate}, {@code bribe} or {@code flatter} (Hearth: 0–0.15). */
	public static final String SPEECH = "hearth:speech/";
	/** Failed checks a sim day taken back (Hearth: 0–3; one budget for talk and law). */
	public static final String SPEECH_RETRY = "hearth:speech/retry";
	/** A share added to positive standing changes (Hearth: 0–0.5). */
	public static final String STANDING_GAIN = "hearth:standing/gain";
	/** A share off how far witnesses and Detection see a sneaking player (Hearth: 0–0.5). */
	public static final String STEALTH_SIGHT = "hearth:stealth/sight";
	/** Blocks beyond which a sneaking player is not seen at all; 0 = off (Hearth: 2–32 when on). */
	public static final String STEALTH_UNSEEN_BEYOND = "hearth:stealth/unseen_beyond";
	/** A share off a guard's search timer after losing the player (Hearth: 0–0.75). */
	public static final String STEALTH_GIVE_UP = "hearth:stealth/give_up";
	/** A share off how fast the pickpocket's mark notices (Hearth: 0–0.5). */
	public static final String PICKPOCKET_AWARENESS = "hearth:pickpocket/awareness";
	/** 1: folk greet the player as a hero (flavour only). */
	public static final String GREET_HERO = "hearth:greet/hero";

	/** The kinds of {@link #SPEECH} (Hearth's {@code SpeechKind}; the law's checks read the same three of them). */
	public static final List<String> SPEECH_KINDS = List.of("persuade", "intimidate", "bribe", "flatter");

	/** Every key Hearth reads, the speech kinds spelled out. */
	public static final List<String> ALL = List.of(HAGGLE_BAND, HAGGLE_PATIENCE, HAGGLE_NEVER_SOUR, SPEECH + "persuade", SPEECH + "intimidate",
		SPEECH + "bribe", SPEECH + "flatter", SPEECH_RETRY, STANDING_GAIN, STEALTH_SIGHT, STEALTH_UNSEEN_BEYOND, STEALTH_GIVE_UP,
		PICKPOCKET_AWARENESS, GREET_HERO);

	private HearthKeys() {
	}
}
