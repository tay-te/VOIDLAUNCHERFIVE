package dev.voidmc.mettle.character.moment;

import java.util.Locale;

/**
 * What kind of progress moment happened (UI.md §6b). Level moments carry a skill and a level; perk moments carry the
 * perk. Plain Java (no game classes), so the queue logic is unit-tested without a bootstrap. The client gives each kind its own sound and toast; the overlay gets the lower-case name.
 */
public enum MomentKind {
	/** A skill gained a level. */
	LEVEL,
	/** A skill gained a level that opened a perk tier (TREES.md §1); stronger than {@link #LEVEL}. */
	TIER,
	/** The character level rose: one more attribute point. */
	CHARACTER,
	/** A root, path or cursed perk was drafted (a rank of it). */
	LEARNED,
	/** A mastery was chosen. */
	MASTERY,
	FEAT,
	SYNERGY,
	TEACHING;

	public String serializedName() {
		return name().toLowerCase(Locale.ROOT);
	}

	/** Feats, synergies and teachings: earned, never bought; drawn in champagne. */
	public boolean earned() {
		return this == FEAT || this == SYNERGY || this == TEACHING;
	}

	/** Moments that come from XP: they bring drafts and attribute points, so they wake the nudge. */
	public boolean levelUp() {
		return this == LEVEL || this == TIER || this == CHARACTER;
	}

	/** Moments the player caused by picking a card (usually from a screen that already shows the change). */
	public boolean bought() {
		return this == LEARNED || this == MASTERY;
	}

	/** Moments with the same sound group share a throttle; the higher {@link #soundPriority} wins a tie. */
	public String soundGroup() {
		return levelUp() ? "level" : bought() ? "learned" : "earned";
	}

	public int soundPriority() {
		return switch (this) {
			case CHARACTER, LEARNED, FEAT, SYNERGY, TEACHING -> 1;
			case LEVEL, MASTERY -> 2;
			case TIER -> 3;
		};
	}

	/** How long a sound group stays quiet after it played, unless something stronger comes. */
	public long soundIntervalMs() {
		return levelUp() ? 1500 : bought() ? 90 : 2500;
	}
}
