package dev.voidmc.atlas.client.render;

import dev.voidmc.atlas.client.AtlasSettings;

/**
 * Day and night on the map, as the design has it: by night the land about 45% darker and a touch cooler, lit places
 * (villages, settlements) warm. Follows the world's clock (night from dusk, 12,500 ticks, to dawn, 23,500), or stays
 * day or night; any change eases in over 20 seconds. One state for the whole client, moved on once a frame.
 */
public final class MapLight {
	/** How long a change takes, in seconds. */
	public static final double EASE_SECONDS = 20;
	public static final long DUSK = 12_500;
	public static final long DAWN = 23_500;

	private static double night = Double.NaN;
	private static long last;

	private MapLight() {
	}

	/** Moves the light on towards what the setting and the time of day want (render thread, once a frame). */
	public static void update(AtlasSettings.MapLook look, long dayTime, boolean hasSky) {
		double target = !hasSky ? 0 : switch (look) {
			case DAY -> 0;
			case NIGHT -> 1;
			case FOLLOW -> isNight(dayTime) ? 1 : 0;
		};
		long now = System.nanoTime();
		if (Double.isNaN(night)) {
			night = target;
		} else {
			double dt = Math.min(1, (now - last) / 1e9);
			double step = dt / EASE_SECONDS;
			night += Math.max(-step, Math.min(step, target - night));
		}
		last = now;
	}

	/** Whether it is night at a time of day (ticks). */
	public static boolean isNight(long dayTime) {
		long t = Math.floorMod(dayTime, 24000L);
		return t >= DUSK && t < DAWN;
	}

	/** How far into night the map is, 0 (day) to 1 (night), eased. */
	public static double night() {
		return Double.isNaN(night) ? 0 : night * night * (3 - 2 * night);
	}

	/** The colour the land is multiplied by (0xRRGGBB): white by day, about 55% and cooler by night. */
	public static int tint() {
		double n = night();
		int r = (int) Math.round(255 * (1 - n * 0.47));
		int g = (int) Math.round(255 * (1 - n * 0.45));
		int b = (int) Math.round(255 * (1 - n * 0.38));
		return r << 16 | g << 8 | b;
	}
}
