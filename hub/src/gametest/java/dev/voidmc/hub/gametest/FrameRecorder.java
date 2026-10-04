package dev.voidmc.hub.gametest;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.commands.GpuQueryPool;
import java.util.OptionalLong;

/**
 * Per-frame timing, recorded on the render thread by {@code MinecraftFrameMixin}:
 *
 * <ul>
 *   <li><b>interval</b> — end of one frame to the end of the next: what the player's FPS is.</li>
 *   <li><b>render</b> — CPU time inside {@code Minecraft.renderFrame}, extract to present: the
 *       frame's own cost, without the client-tick work (and without the game-test lock-step wait
 *       that tick frames carry under the client game test).</li>
 *   <li><b>tick</b> — whether a client tick ran in that frame.</li>
 *   <li><b>gpu</b> — GPU time between two timestamps written at the start of renderFrame and just
 *       before its submit, read back a few frames later (both backends implement timestamps).</li>
 * </ul>
 */
public final class FrameRecorder {

	public static final FrameRecorder INSTANCE = new FrameRecorder();

	private static final int CAP = 1 << 18;
	private static final int GPU_RING = 32;

	private final long[] interval = new long[CAP];
	private final long[] render = new long[CAP];
	private final long[] gpu = new long[CAP];
	private final boolean[] tick = new boolean[CAP];
	private int n;
	private volatile boolean recording;

	private long lastFrameEnd;
	private long renderStart;
	private boolean tickThisFrame;

	private GpuQueryPool pool;
	private final int[] ringFrame = new int[GPU_RING];
	private int ringSlot;
	private boolean gpuBroken;

	/** Hook: a frame listener for open-latency measurement (render thread). */
	public volatile Runnable afterFrame;

	public void start() {
		this.n = 0;
		this.lastFrameEnd = 0;
		java.util.Arrays.fill(this.gpu, -1L);
		java.util.Arrays.fill(this.ringFrame, -1);
		this.recording = true;
	}

	public void stop() {
		this.recording = false;
	}

	public int count() {
		return this.n;
	}

	public long[] intervals() {
		return java.util.Arrays.copyOf(this.interval, this.n);
	}

	public long[] renders() {
		return java.util.Arrays.copyOf(this.render, this.n);
	}

	public long[] gpus() {
		return java.util.Arrays.copyOf(this.gpu, this.n);
	}

	public boolean[] ticks() {
		return java.util.Arrays.copyOf(this.tick, this.n);
	}

	// ---- hooks (render thread) --------------------------------------------------------------------

	void onTick() {
		this.tickThisFrame = true;
	}

	void onRenderStart() {
		this.renderStart = System.nanoTime();
		if (this.recording && !this.gpuBroken) {
			try {
				if (this.pool == null) {
					this.pool = RenderSystem.getDevice().createTimestampQueryPool(GPU_RING * 2);
				}
				this.collectGpu();
				int slot = this.ringSlot;
				if (this.ringFrame[slot] >= 0) {
					// Still pending after a full ring: give up on that sample, reuse the slot.
					this.ringFrame[slot] = -1;
				}
				RenderSystem.getDevice().createCommandEncoder().writeTimestamp(this.pool, slot * 2);
				this.ringFrame[slot] = this.n;
			} catch (Throwable t) {
				this.gpuBroken = true;
			}
		}
	}

	void onBeforeSubmit() {
		if (this.recording && !this.gpuBroken && this.pool != null) {
			try {
				RenderSystem.getDevice().createCommandEncoder().writeTimestamp(this.pool, this.ringSlot * 2 + 1);
			} catch (Throwable t) {
				this.gpuBroken = true;
			}
			this.ringSlot = (this.ringSlot + 1) % GPU_RING;
		}
	}

	void onFrameEnd() {
		long now = System.nanoTime();
		if (this.recording && this.lastFrameEnd != 0 && this.n < CAP) {
			this.interval[this.n] = now - this.lastFrameEnd;
			this.render[this.n] = now - this.renderStart;
			this.tick[this.n] = this.tickThisFrame;
			this.n++;
		}
		this.lastFrameEnd = now;
		this.tickThisFrame = false;
		Runnable r = this.afterFrame;
		if (r != null) {
			r.run();
		}
	}

	private void collectGpu() {
		for (int s = 0; s < GPU_RING; s++) {
			int frame = this.ringFrame[s];
			if (frame < 0 || s == this.ringSlot) {
				continue;
			}
			OptionalLong[] v = this.pool.getValues(s * 2, 2);
			if (v[0].isPresent() && v[1].isPresent()) {
				double period = RenderSystem.getDevice().getDeviceInfo().timestampPeriod();
				// The frame index the sample was taken in is the index the *next* onFrameEnd fills.
				if (frame < CAP) {
					this.gpu[frame] = (long) ((v[1].getAsLong() - v[0].getAsLong()) * period);
				}
				this.ringFrame[s] = -1;
			}
		}
	}
}
