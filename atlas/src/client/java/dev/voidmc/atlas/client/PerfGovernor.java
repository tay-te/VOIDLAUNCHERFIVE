package dev.voidmc.atlas.client;

/**
 * How hard Atlas works this frame (the Performance setting: Auto, Quality, Light). In Auto it watches the frame time
 * (lightly smoothed) and goes Light once it has stayed over {@value #SLOW_FRAME_MS} ms for five seconds; it goes back
 * to Quality only after thirty seconds under {@value #FAST_FRAME_MS} ms, so it never flickers between them. Light
 * means: map tiles uploaded on one frame in four (a quarter of the redraw rate) and within 0.5 ms, the minimap's markers
 * looked up ten times a second, far sight and party tiles paused, the nearest 8 beams only, no radar, no rim dots or
 * edge markers; and the quiet note "Light mode on: this PC is busy". Render thread.
 */
public final class PerfGovernor {
	static final double SLOW_FRAME_MS = 20;
	static final double FAST_FRAME_MS = 14;
	private static final long SLOW_NANOS = 5_000_000_000L;
	private static final long CALM_NANOS = 30_000_000_000L;

	private static double frameMs = 8;
	private static double atlasMs = 0.2;
	private static volatile boolean light;
	private static long slowSince;
	private static long calmSince;
	private static long lightSince;
	private static int frames;

	private PerfGovernor() {
	}

	/** A frame went by: its time and what Atlas spent of it (render thread). */
	public static void frame(double frameMillis, double atlasMillis, AtlasSettings.Performance mode) {
		frame(frameMillis, atlasMillis, mode, System.nanoTime());
	}

	static void frame(double frameMillis, double atlasMillis, AtlasSettings.Performance mode, long now) {
		frames++;
		// smoothed over a few frames: one hitch is not a struggling PC
		frameMs += (Math.min(frameMillis, 200) - frameMs) * 0.1;
		atlasMs += (atlasMillis - atlasMs) * 0.02;
		boolean was = light;
		switch (mode) {
			case QUALITY -> light = false;
			case LIGHT -> light = true;
			case AUTO -> {
				if (!light) {
					if (frameMs <= SLOW_FRAME_MS) {
						slowSince = 0;
					} else if (slowSince == 0) {
						slowSince = now;
					} else if (now - slowSince >= SLOW_NANOS) {
						light = true;
						calmSince = 0;
					}
				} else {
					if (frameMs >= FAST_FRAME_MS) {
						calmSince = 0;
					} else if (calmSince == 0) {
						calmSince = now;
					} else if (now - calmSince >= CALM_NANOS) {
						light = false;
						slowSince = 0;
					}
				}
			}
		}
		if (light && !was) {
			lightSince = now;
		}
	}

	/** How long Light has been on (ms), or 0. */
	public static long lightMillis() {
		return light ? (System.nanoTime() - lightSince) / 1_000_000L : 0;
	}

	/** Whether map tiles are uploaded this frame: every frame, or one in four while Light. */
	public static boolean uploadThisFrame() {
		return !light || frames % 4 == 0;
	}

	/** Whether far sight and party tiles wait (Light). */
	public static boolean paused() {
		return light;
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
		slowSince = 0;
		lightSince = 0;
	}
}
