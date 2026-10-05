package dev.voidmc.atlas.core.waypoints;

import dev.voidmc.atlas.api.Waypoint;
import dev.voidmc.atlas.core.Oklab;

/**
 * Picks the nearest of Atlas's eight muted waypoint colours ({@link Waypoint#COLOURS}) for a colour from somewhere
 * else, such as another map mod's bright chat colours. A plain distance would pull most bright colours onto the pale
 * ink-white or the grey, so the match is by hue: greys, black and white go to Ink or Stone by lightness, and anything
 * with colour goes to the coloured entry whose hue is closest in Oklab.
 */
public final class AtlasColours {
	/** Minecraft's sixteen chat colours, in their index order (black, dark blue, ... white). */
	public static final int[] CHAT_COLOURS = {
		0x000000, 0x0000AA, 0x00AA00, 0x00AAAA, 0xAA0000, 0xAA00AA, 0xFFAA00, 0xAAAAAA,
		0x555555, 0x5555FF, 0x55FF55, 0x55FFFF, 0xFF5555, 0xFF55FF, 0xFFFF55, 0xFFFFFF
	};
	public static final int INK = 0;
	public static final int STONE = 7;
	/** Below this Oklab chroma a colour counts as grey. */
	private static final float GREY_CHROMA = 0.04f;
	/** Ink is chosen over Stone for greys at least this light. */
	private static final float LIGHT_GREY = 0.8f;

	private AtlasColours() {
	}

	/** The nearest Atlas colour index for an RGB colour ({@code 0xRRGGBB}; any alpha is ignored). */
	public static int nearest(int rgb) {
		float[] lab = Oklab.fromRgb(rgb & 0xFFFFFF);
		if (Oklab.chroma(lab) < GREY_CHROMA) {
			return lab[0] >= LIGHT_GREY ? INK : STONE;
		}
		double hue = Math.atan2(lab[2], lab[1]);
		int best = STONE;
		double bestDiff = Double.MAX_VALUE;
		for (int i = 0; i < Waypoint.COLOURS.length; i++) {
			if (i == INK || i == STONE) {
				continue;
			}
			float[] p = Oklab.fromRgb(Waypoint.COLOURS[i]);
			double diff = Math.abs(Math.IEEEremainder(hue - Math.atan2(p[2], p[1]), 2 * Math.PI));
			if (diff < bestDiff) {
				bestDiff = diff;
				best = i;
			}
		}
		return best;
	}

	/** The nearest Atlas colour index for one of Minecraft's sixteen chat colours; out-of-range indices wrap. */
	public static int fromChatColour(int index) {
		return nearest(CHAT_COLOURS[Math.floorMod(index, CHAT_COLOURS.length)]);
	}
}
