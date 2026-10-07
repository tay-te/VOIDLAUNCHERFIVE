package dev.voidmc.atlas.core;

import java.util.Locale;

/**
 * The colours of terrain seen from afar (VOID LOD's far tiles, which come as colours, not blocks), carried through the
 * map's block palette as names like {@code atlas:far#5a7a3c}: quantised to 4 bits a channel (4,096 names at most), so the
 * palette stays small, and drawn by the tile renderer like any block. Never a real block id, so a far column can never
 * be taken for a walked one.
 */
public final class FarColour {
	public static final String PREFIX = "atlas:far#";

	private FarColour() {
	}

	/** The name for a colour (RGB; alpha ignored), quantised to 4 bits a channel. */
	public static String name(int rgb) {
		int r = (rgb >> 16 & 0xFF) >> 4;
		int g = (rgb >> 8 & 0xFF) >> 4;
		int b = (rgb & 0xFF) >> 4;
		return PREFIX + String.format(Locale.ROOT, "%x%x%x", r, g, b);
	}

	/** Whether a block name is a far colour. */
	public static boolean is(String name) {
		return name != null && name.startsWith(PREFIX);
	}

	/** The colour a far name stands for (RGB, each channel the middle of its 4-bit step), or -1 if it is not one. */
	public static int colour(String name) {
		if (!is(name) || name.length() != PREFIX.length() + 3) {
			return -1;
		}
		try {
			int r = Integer.parseInt(name.substring(PREFIX.length(), PREFIX.length() + 1), 16);
			int g = Integer.parseInt(name.substring(PREFIX.length() + 1, PREFIX.length() + 2), 16);
			int b = Integer.parseInt(name.substring(PREFIX.length() + 2), 16);
			return (r * 17) << 16 | (g * 17) << 8 | b * 17;
		} catch (NumberFormatException e) {
			return -1;
		}
	}
}
