package dev.voidmc.hub.gametest;

import net.minecraft.client.Minecraft;

/**
 * Render-thread hooks called by the test mixins: frame timing, and the mouse sweeper that drives
 * the pointer across the slots every frame (as fast as a frantic player, faster than ticks).
 */
public final class FrameHooks {

	private FrameHooks() {
	}

	/** Pointer path for the sweep, in window px; null when not sweeping. */
	public static volatile double[][] sweepPath;
	/** Path points visited per second. */
	public static volatile double sweepRate = 30.0;
	private static long sweepStart;
	public static volatile long sweepMoves;

	public static void frameStart() {
		double[][] path = sweepPath;
		if (path != null && path.length > 0) {
			long now = System.nanoTime();
			if (sweepStart == 0) {
				sweepStart = now;
			}
			double t = (now - sweepStart) / 1e9 * sweepRate;
			int i = (int) t % path.length;
			int j = (i + 1) % path.length;
			double f = t - Math.floor(t);
			double x = path[i][0] + (path[j][0] - path[i][0]) * f;
			double y = path[i][1] + (path[j][1] - path[i][1]) * f;
			Minecraft mc = Minecraft.getInstance();
			double dx = x - mc.mouseHandler.xpos();
			double dy = y - mc.mouseHandler.ypos();
			mc.mouseHandler.onMove(mc.getWindow().handle(), x, y, dx, dy);
			sweepMoves++;
		} else {
			sweepStart = 0;
		}
		FrameRecorder.INSTANCE.onRenderStart();
	}

	public static void beforeSubmit() {
		FrameRecorder.INSTANCE.onBeforeSubmit();
	}

	public static void frameEnd() {
		FrameRecorder.INSTANCE.onFrameEnd();
	}

	public static void tick() {
		FrameRecorder.INSTANCE.onTick();
	}
}
