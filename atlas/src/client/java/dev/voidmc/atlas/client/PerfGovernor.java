package dev.voidmc.atlas.client;

/**
 * How hard Atlas works this frame (the Performance setting: Auto, Quality, Light). In Auto it watches the frame time
 * and Atlas's own cost per frame (smoothed over about two seconds) and goes Light when the game struggles (under ~40
 * fps) or Atlas costs over 1.5 ms a frame; it goes back to Quality only after ten seconds of comfort (over ~70 fps and
 * under 0.8 ms), so it never flickers between them. Light means: tile uploads limited to 0.5 ms a frame, the nearest
 * 8 beams only, no radar, and a simpler minimap (no rim dots, no edge markers, its tiles asked for at a third of the
 * rate). Render thread.
 */
public final class PerfGovernor {
	private static final double SLOW_FRAME_MS = 25;
	private static final double ATLAS_HEAVY_MS = 1.5;
	private static final double FAST_FRAME_MS = 14;
	private static final double ATLAS_LIGHT_MS = 0.8;
	private static final long CALM_NANOS = 10_000_000_000L;

	private static double frameMs = 8;
	private static double atlasMs = 0.2;
	private static boolean light;
	private static long calmSince;

	private PerfGovernor() {
	}

	/** A frame went by: its time and what Atlas spent of it (render thread). */
	public static void frame(double frameMillis, double atlasMillis, AtlasSettings.Performance mode) {
		double k = 0.02;
		frameMs += (Math.min(frameMillis, 200) - frameMs) * k;
		atlasMs += (atlasMillis - atlasMs) * k;
		switch (mode) {
			case QUALITY -> light = false;
			case LIGHT -> light = true;
			case AUTO -> {
				long now = System.nanoTime();
				if (!light && (frameMs > SLOW_FRAME_MS || atlasMs > ATLAS_HEAVY_MS)) {
					light = true;
					calmSince = 0;
				} else if (light) {
					boolean calm = frameMs < FAST_FRAME_MS && atlasMs < ATLAS_LIGHT_MS;
					if (!calm) {
						calmSince = 0;
					} else if (calmSince == 0) {
						calmSince = now;
					} else if (now - calmSince > CALM_NANOS) {
						light = false;
					}
				}
			}
		}
	}

	/** Whether Atlas works light now. */
	public static boolean light() {
		return light;
	}

	/** The tile upload budget a frame, in nanoseconds. */
	public static long uploadNanos() {
		return light ? 500_000L : 1_200_000L;
	}

	/** The most beams drawn. */
	public static int beams(int quality) {
		return light ? Math.min(8, quality) : quality;
	}

	/** The smoothed frame time and Atlas cost (ms), for the settings' readout and the tests. */
	public static double frameMs() {
		return frameMs;
	}

	public static double atlasMs() {
		return atlasMs;
	}

	/** For tests: back to the start. */
	static void reset() {
		frameMs = 8;
		atlasMs = 0.2;
		light = false;
		calmSince = 0;
	}
}
