package dev.voidmc.mettle.compat;

/**
 * What the loaded VOID Hearth can hear from Mettle, for code outside {@code compat.hearth} (no Hearth types here). Set
 * once by {@code HearthCompat.init}; without Hearth both stay false.
 *
 * <p>{@link #bonus()}: Hearth's {@code PlayerSkills} declares {@code bonus(player, key)} (request 12 of
 * hearth/docs/requests/mettle-speech-stealth.md). Until it does, every perk that reaches Hearth through a bonus key uses
 * its documented fallback instead (docs/BUILD_FEEL.md §14), and never both: see {@code character.skill.stealth.HearthChannels}.
 */
public final class HearthLink {
	private static volatile boolean hearth;
	private static volatile boolean bonus;

	private HearthLink() {
	}

	/** Called once by HearthCompat when Hearth is loaded. */
	public static void set(boolean hearthLoaded, boolean bonusDeclared) {
		hearth = hearthLoaded;
		bonus = hearthLoaded && bonusDeclared;
	}

	public static boolean hearth() {
		return hearth;
	}

	/** Whether the loaded Hearth reads bonus keys. False without Hearth. */
	public static boolean bonus() {
		return bonus;
	}
}
