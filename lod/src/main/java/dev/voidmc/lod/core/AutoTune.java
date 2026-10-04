package dev.voidmc.lod.core;

import java.util.Arrays;

/**
 * Keeps what the LOD costs a frame under a budget: the GPU time of its draws, and the render-thread time it
 * takes to prepare them. While either is over its target it coarsens the detail budget a step, and only once
 * the detail is three times coarser than asked does it shorten the reach; while both have room it refines
 * again, step by step, back to the settings.
 *
 * <p>It is fed every frame and decides twice a second, and it is built not to hunt:
 * <ul>
 *   <li>A step down needs a second over the target, a step up three seconds under it and five since the last
 *       change, so the finer tiles have streamed in and shown what they cost before the next step.</li>
 *   <li>A step up is only taken when the cost it predicts lands under {@value #UP_MARGIN} of the target. A
 *       tile's cost goes with its cell count, the square of the detail budget, so each step is about half
 *       again the cost of the one below it, and the step up is never the one that goes over.</li>
 *   <li>A step up undone within 20 s bars that step for 30 s, twice as long each time it happens again, up to
 *       eight minutes: a view that sits right at the edge settles one step down instead of flickering.</li>
 *   <li>Prepare time is the median of the frames that streamed no tiles: uploads have their own cap, and the
 *       odd hitch is not the LOD's doing.</li>
 *   <li>While the game holds its frame cap (vsync, a frame limit), the LOD is not what holds it back, and
 *       nothing is coarsened, however long the GPU timer says the draws took on a GPU idling at low clocks.</li>
 * </ul>
 *
 * <p>Pure logic with no clock of its own: the caller passes the time, so tests can feed it any history.
 */
public final class AutoTune {
	/** The steps, from the settings as they are to the coarsest: how much coarser the detail budget, how much shorter the reach. */
	private static final double[] DETAIL = {1, 1.2, 1.45, 1.75, 2.1, 2.5, 3, 3, 3};
	private static final double[] REACH = {1, 1, 1, 1, 1, 1, 1, 0.75, 0.5};
	static final int STEPS = DETAIL.length;
	/** How long the measurements of one decision run. */
	static final long WINDOW_NANOS = 500_000_000L;
	/** Windows in a row over the target before a step down, and under it before a step up. */
	static final int DOWN_WINDOWS = 2;
	static final int UP_WINDOWS = 6;
	/** The least time since the last change before a step down, and before a step up. */
	static final long DOWN_SETTLE_NANOS = 1_000_000_000L;
	static final long UP_SETTLE_NANOS = 5_000_000_000L;
	/** A step up must be predicted to land under this fraction of the target; a step down aims under this one. */
	static final double UP_MARGIN = 0.8;
	static final double DOWN_AIM = 0.9;
	/** The most steps one decision goes down, however far over the target. */
	static final int MAX_DOWN = 3;
	/** A step down this soon after a step up undoes it, and bars that step for a while. */
	static final long REVERT_NANOS = 20_000_000_000L;
	static final long BACKOFF_NANOS = 30_000_000_000L;
	static final long MAX_BACKOFF_NANOS = 480_000_000_000L;
	/** Frames this close to the cap's frame time are holding it. */
	static final double HOLDING_CAP = 1.1;
	private static final int SAMPLES = 128;
	private static final int MIN_SAMPLES = 5;
	private static final int MIN_FRAMES = 4;

	private double gpuTarget = 1.5;
	private double prepareTarget = 0.5;
	private int step;
	private boolean started;
	private long windowStart;
	private int frames;
	private double gpuSum;
	private int gpuCount;
	private final float[] prepare = new float[SAMPLES];
	private final float[] sorted = new float[SAMPLES];
	private int prepareCount;
	private int over;
	private int under;
	private long changedNanos;
	private boolean wentUp;
	/** While a step up has failed: the best step allowed, until when, and how long the next failure bars it. */
	private int ceiling;
	private long ceilingUntil;
	private long backoff = BACKOFF_NANOS;

	/** The last decision's measurements, for the debug screen: GPU and prepare milliseconds (-1 if unmeasured), and the load on the budget. */
	public double gpu = -1;
	public double prepareMillis = -1;
	public double load;
	/** Whether the game was holding its frame cap at the last decision. */
	public boolean holding;

	/** The targets, in milliseconds a frame: the LOD's GPU time, and its render-thread time. */
	public void setTargets(double gpuMillis, double prepareMillis) {
		this.gpuTarget = gpuMillis;
		this.prepareTarget = prepareMillis;
	}

	/** Back to the settings as they are, forgetting all history: a new world, or settings the player just chose. */
	public void reset() {
		this.step = 0;
		this.started = false;
		this.over = 0;
		this.under = 0;
		this.wentUp = false;
		this.ceiling = 0;
		this.ceilingUntil = 0;
		this.backoff = BACKOFF_NANOS;
		this.gpu = -1;
		this.prepareMillis = -1;
		this.load = 0;
		this.holding = false;
		this.clearWindow(0);
	}

	public int step() {
		return this.step;
	}

	/** What the detail budgets are multiplied by: 1 at the settings, more is coarser. */
	public double detailScale() {
		return DETAIL[this.step];
	}

	/** What the reach is multiplied by: 1 at the settings. */
	public double reachScale() {
		return REACH[this.step];
	}

	/**
	 * One frame's measurements: the LOD's GPU time ({@code gpuMillis}, negative if it cannot be measured), its
	 * render-thread time, whether tiles were streaming in (that frame's prepare time then says little), and the
	 * frame time the game is capped at ({@code capMillis}, 0 if it is not). Returns true when the step changed.
	 */
	public boolean sample(long nanos, double gpuMillis, double prepareMillis, boolean streaming, double capMillis) {
		if (!this.started) {
			this.started = true;
			this.changedNanos = nanos;
			this.clearWindow(nanos);
		}
		this.frames++;
		if (gpuMillis >= 0) {
			this.gpuSum += gpuMillis;
			this.gpuCount++;
		}
		if (!streaming && prepareMillis >= 0) {
			this.prepare[this.prepareCount++ % SAMPLES] = (float) prepareMillis;
		}
		long elapsed = nanos - this.windowStart;
		if (elapsed < WINDOW_NANOS) {
			return false;
		}
		boolean changed = this.frames >= MIN_FRAMES && this.decide(nanos, elapsed, capMillis);
		this.clearWindow(nanos);
		return changed;
	}

	private boolean decide(long nanos, long elapsed, double capMillis) {
		this.gpu = this.gpuCount > 0 ? this.gpuSum / this.gpuCount : -1;
		int n = Math.min(this.prepareCount, SAMPLES);
		this.prepareMillis = n >= MIN_SAMPLES ? this.median(n) : -1;
		if (this.gpu < 0 && this.prepareMillis < 0) {
			// nothing measured (all frames streaming, no GPU timer): no evidence either way
			this.over = 0;
			this.under = 0;
			return false;
		}
		this.load = Math.max(this.gpu >= 0 ? this.gpu / this.gpuTarget : 0, this.prepareMillis >= 0 ? this.prepareMillis / this.prepareTarget : 0);
		this.holding = capMillis > 0 && elapsed / 1e6 / this.frames <= capMillis * HOLDING_CAP;
		if (this.load > 1 && !this.holding) {
			this.over++;
			this.under = 0;
		} else if (this.step > 0 && this.load * cost(this.step - 1) / cost(this.step) < UP_MARGIN) {
			this.under++;
			this.over = 0;
		} else {
			this.over = 0;
			this.under = 0;
		}
		long since = nanos - this.changedNanos;
		if (this.over >= DOWN_WINDOWS && this.step < STEPS - 1 && since >= DOWN_SETTLE_NANOS) {
			// as many steps as the cost model says bring it under the target, a few at most
			int to = this.step + 1;
			while (to < STEPS - 1 && to - this.step < MAX_DOWN && this.load * cost(to) / cost(this.step) > DOWN_AIM) {
				to++;
			}
			if (this.wentUp) {
				if (since < REVERT_NANOS) {
					// the last step up did not hold: bar it for a while, longer each time
					this.ceiling = this.step + 1;
					this.ceilingUntil = nanos + this.backoff;
					this.backoff = Math.min(this.backoff * 2, MAX_BACKOFF_NANOS);
				} else {
					this.backoff = BACKOFF_NANOS;
				}
			}
			this.change(to, false, nanos);
			return true;
		}
		boolean barred = this.step - 1 < this.ceiling && nanos < this.ceilingUntil;
		if (this.under >= UP_WINDOWS && this.step > 0 && since >= UP_SETTLE_NANOS && !barred) {
			this.change(this.step - 1, true, nanos);
			return true;
		}
		return false;
	}

	private void change(int to, boolean up, long nanos) {
		this.step = to;
		this.wentUp = up;
		this.changedNanos = nanos;
		this.over = 0;
		this.under = 0;
	}

	/** What a step costs relative to the settings: the cell count goes with the square of the detail, and the reach takes its share. */
	private static double cost(int step) {
		return REACH[step] / (DETAIL[step] * DETAIL[step]);
	}

	private double median(int n) {
		System.arraycopy(this.prepare, 0, this.sorted, 0, n);
		Arrays.sort(this.sorted, 0, n);
		return this.sorted[n / 2];
	}

	private void clearWindow(long nanos) {
		this.windowStart = nanos;
		this.frames = 0;
		this.gpuSum = 0;
		this.gpuCount = 0;
		this.prepareCount = 0;
	}
}
