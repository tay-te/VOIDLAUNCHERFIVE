package dev.voidmc.hub.ui;

/**
 * Where the VOID screen's own time goes, frame by frame: Ultralight's update and render, the copy
 * out of its surface, the texture upload, and the JS bridge. Always on (a handful of nanoTime
 * calls); read by the perf game test and by the F3-style log line.
 *
 * <p>Render thread only.
 */
public final class HubStats {

	public static final HubStats INSTANCE = new HubStats();

	/**
	 * One frame of the VOID screen. Times in nanoseconds.
	 *
	 * <p>Render-thread cost: {@code postNs} (handing messages to the UI thread), {@code waitNs}
	 * (only in sync mode, or once on open), {@code copyNs} (dirty rect out of the surface) and
	 * {@code uploadNs} (into the texture). UI-thread work since the previous frame, which does not
	 * block the game: {@code updateNs}, {@code renderNs}, {@code scriptNs}.
	 */
	public static final class Frame {
		public long postNs;
		public long waitNs;
		public long copyNs;
		public long uploadNs;
		public long updateNs;
		public long renderNs;
		public long scriptNs;
		public int uiPasses;
		public boolean busy;
		public long bytes;
		public int rects;
		public long endNs;
		/** The Atlas map widget's draw on the Map page (render thread; Atlas's cost, not the hub's). */
		public long mapNs;
		/** A screen's own per-frame page sync beyond layout and state (Mettle's state on the Classes page), render thread. */
		public long extraNs;
		/** Cycles the UI thread itself ran for this frame (ThreadCpu), -1 when unknown: {@link #uiCpuNs}. */
		public long uiCycles = -1;

		/**
		 * The CPU Ultralight's UI thread itself spent for this frame (its update, script and paint), ns, -1 when unknown.
		 * Unlike {@link #uiNs} (wall clock) it does not grow when other programs keep the thread waiting for a core.
		 */
		public long uiCpuNs() {
			return ThreadCpu.ns(this.uiCycles);
		}

		/** Time this frame spent on the game's render thread because of the VOID screen. */
		public long totalNs() {
			return this.postNs + this.waitNs + this.copyNs + this.uploadNs + this.extraNs;
		}

		/** Ultralight's own work (UI thread) attributed to this frame. */
		public long uiNs() {
			return this.updateNs + this.renderNs + this.scriptNs;
		}

		void reset() {
			this.postNs = 0;
			this.waitNs = 0;
			this.copyNs = 0;
			this.uploadNs = 0;
			this.updateNs = 0;
			this.renderNs = 0;
			this.scriptNs = 0;
			this.uiPasses = 0;
			this.busy = false;
			this.bytes = 0;
			this.rects = 0;
			this.mapNs = 0;
			this.extraNs = 0;
			this.uiCycles = -1;
		}

		Frame copy() {
			Frame f = new Frame();
			f.postNs = this.postNs;
			f.waitNs = this.waitNs;
			f.copyNs = this.copyNs;
			f.uploadNs = this.uploadNs;
			f.updateNs = this.updateNs;
			f.renderNs = this.renderNs;
			f.scriptNs = this.scriptNs;
			f.uiPasses = this.uiPasses;
			f.busy = this.busy;
			f.bytes = this.bytes;
			f.rects = this.rects;
			f.endNs = this.endNs;
			f.mapNs = this.mapNs;
			f.extraNs = this.extraNs;
			f.uiCycles = this.uiCycles;
			return f;
		}
	}

	private static final int CAPACITY = 1 << 16;
	private final Frame[] ring = new Frame[CAPACITY];
	private long frames;
	private Frame current = new Frame();

	// One-off costs.
	public volatile long loadNativesNs;
	public volatile long createRendererNs;
	public volatile long createViewNs;
	public volatile long pageLoadNs;
	public volatile long initTotalNs;
	public volatile long lastResizeNs;
	/** nanoTime when a VOID screen last ran init(); and when its first frame finished extracting. */
	public volatile long lastScreenInitNs;
	public volatile long lastScreenFirstFrameNs;
	public volatile long fullUploads;
	public volatile long dirtyUploads;
	public volatile long idleFrames;

	private HubStats() {
		for (int i = 0; i < CAPACITY; i++) {
			this.ring[i] = new Frame();
		}
	}

	Frame begin() {
		this.current = this.ring[(int) (this.frames & (CAPACITY - 1))];
		this.current.reset();
		return this.current;
	}

	void end() {
		this.current.endNs = System.nanoTime();
		this.frames++;
	}

	Frame current() {
		return this.current;
	}

	/** Adds the map widget's draw time to the frame being recorded. */
	public void noteMap(long ns) {
		this.current.mapNs += ns;
	}

	/** The frame being recorded's post time so far (to keep a measured section's posts out of extraNs). */
	public long currentPostNs() {
		return this.current.postNs;
	}

	/** Adds a screen's own page-sync time (HubScreen.pushExtra) to the frame being recorded. */
	public void noteExtra(long ns) {
		this.current.extraNs += ns;
	}

	/** Frames recorded so far (monotonic). */
	public long frameCount() {
		return this.frames;
	}

	/** Copies of the frames recorded since {@code fromFrame} (at most the ring's capacity). */
	public Frame[] since(long fromFrame) {
		long from = Math.max(fromFrame, this.frames - CAPACITY);
		int n = (int) Math.max(0, this.frames - from);
		Frame[] out = new Frame[n];
		for (int i = 0; i < n; i++) {
			out[i] = this.ring[(int) ((from + i) & (CAPACITY - 1))].copy();
		}
		return out;
	}
}
