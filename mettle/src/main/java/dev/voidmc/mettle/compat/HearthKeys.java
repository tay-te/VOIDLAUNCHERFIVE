package dev.voidmc.mettle.compat;

/**
 * Hearth's bonus keys (request 12 of hearth/docs/requests/mettle-speech-stealth.md), read through
 * {@code PlayerSkills.bonus(player, key)}. Hearth keeps every clamp of its own.
 */
public final class HearthKeys {
	/** A share off how far witnesses and Detection see a sneaking player. */
	public static final String STEALTH_SIGHT = "hearth:stealth/sight";
	/** A share off a guard's search timer after losing the player. */
	public static final String STEALTH_GIVE_UP = "hearth:stealth/give_up";
	/** Blocks beyond which a sneaking player is not seen at all; 0 = off. */
	public static final String STEALTH_UNSEEN_BEYOND = "hearth:stealth/unseen_beyond";
	/** A share off how fast the pickpocket's mark notices. */
	public static final String PICKPOCKET_AWARENESS = "hearth:pickpocket/awareness";

	private HearthKeys() {
	}
}
