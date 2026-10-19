package dev.voidmc.hearth.standing.logic;

import dev.voidmc.hearth.api.StandingService.Title;
import dev.voidmc.hearth.standing.StandingConfig;

/** Which title a standing earns (docs/LIVING_WORLD.md §3): Outlaw … Hero, with Burgher for owners of property. */
public final class Titles {
	private Titles() {
	}

	/**
	 * @param standing    the player's standing in the town, −100…100
	 * @param owner       whether the player owns a plot in the town
	 * @param outlawByLaw whether the law has declared the player an outlaw there
	 */
	public static Title of(int standing, boolean owner, boolean outlawByLaw, StandingConfig.Titles t) {
		if (outlawByLaw || standing <= t.outlaw()) {
			return Title.OUTLAW;
		}
		if (standing >= t.hero()) {
			return Title.HERO;
		}
		if (owner && standing >= t.burgherMin()) {
			return Title.BURGHER;
		}
		if (standing >= t.friend()) {
			return Title.FRIEND;
		}
		if (standing >= t.known()) {
			return Title.KNOWN;
		}
		if (standing <= t.distrusted()) {
			return Title.DISTRUSTED;
		}
		return Title.STRANGER;
	}

	/** The next title up the standing ladder and the standing it is earned at (Burgher, which property earns, is left out). */
	public record Next(Title title, int at) {
	}

	/** The next title a higher standing earns, or null at the top (a hero). */
	public static @org.jspecify.annotations.Nullable Next next(int standing, StandingConfig.Titles t) {
		if (standing <= t.outlaw()) {
			return new Next(Title.DISTRUSTED, t.outlaw() + 1);
		}
		if (standing <= t.distrusted()) {
			return new Next(Title.STRANGER, t.distrusted() + 1);
		}
		if (standing < t.known()) {
			return new Next(Title.KNOWN, t.known());
		}
		if (standing < t.friend()) {
			return new Next(Title.FRIEND, t.friend());
		}
		if (standing < t.hero()) {
			return new Next(Title.HERO, t.hero());
		}
		return null;
	}

	/** Whether the title means the town thinks ill of the player. */
	public static boolean bad(Title title) {
		return title == Title.OUTLAW || title == Title.DISTRUSTED;
	}

	/** An ARGB colour for the title in screens and the HUD. */
	public static int colour(Title title) {
		return switch (title) {
			case OUTLAW -> 0xFFD83A3A;
			case DISTRUSTED -> 0xFFE08A3C;
			case STRANGER -> 0xFFC8C0B0;
			case KNOWN -> 0xFFE8E0C8;
			case FRIEND -> 0xFF8EE08E;
			case BURGHER -> 0xFF8EC8F0;
			case HERO -> 0xFFFFD86B;
		};
	}
}
