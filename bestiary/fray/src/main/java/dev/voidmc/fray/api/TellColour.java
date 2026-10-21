package dev.voidmc.fray.api;

import java.util.Locale;

/**
 * The tell language (COMBAT.md §7, BESTIARY.md §4) with the shapes the HUD draws for colour-blind players (H9).
 *
 * @since 0.1.0 (2026-10-21)
 */
public enum TellColour {
	/** Parry or block. Circle. */
	WHITE(0xFFF2F2EE),
	/** A heavy: block (stamina ×1.5), parry only with a shield. Square. */
	ORANGE(0xFFFFA24C),
	/** Unblockable: dodge. Diamond. */
	RED(0xFFFF4D4D),
	/** A grab or a spell: dodge or interrupt. Triangle. */
	PURPLE(0xFFB07CFF);

	public final int colour;

	TellColour(int colour) {
		this.colour = colour;
	}

	public String id() {
		return this.name().toLowerCase(Locale.ROOT);
	}

	/** The hit flags a strike with this tell carries. */
	public int hitFlags() {
		return switch (this) {
			case WHITE -> 0;
			case ORANGE -> HitFlags.HEAVY_TELL;
			case RED -> HitFlags.UNBLOCKABLE;
			case PURPLE -> HitFlags.GRAB | HitFlags.UNBLOCKABLE;
		};
	}

	public static TellColour byId(String id) {
		for (TellColour c : values()) {
			if (c.id().equals(id)) {
				return c;
			}
		}
		return WHITE;
	}
}
