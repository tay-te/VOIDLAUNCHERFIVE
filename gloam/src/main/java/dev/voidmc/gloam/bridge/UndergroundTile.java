package dev.voidmc.gloam.bridge;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The underground tile U: 4-block cells, 128 x 128 round the eye (512 m), each the cave the eye's height runs through
 * there: how open it is, its floor and its roof, the nearest cavern water, and the life in it (glowworms, drip, a great
 * river). The pack lets its underground fog reach as far as the cave is open, pools mist over the great rivers, and
 * gives the glowworm caves their glow.
 *
 * <p>It is a horizontal slice at one height, the eye's rounded to 4 blocks ({@code void_ugCentre.y}, the
 * <em>base</em>), and floor and roof are stored against it. The eye may wander {@value #REBASE} blocks up or down before
 * the slice is moved: then every cell is re-encoded against the new base at once, from the floors and roofs already
 * known (so the tile never holds bytes against two bases), and the openness is sampled again, nearest rows first. The
 * window moves after {@value #STEP_CELLS} cells (16 m) of walking, bringing in that many lines.
 *
 * <p>Jobs run every {@value #JOB_TICKS} ticks while the eye is underground ({@code void_underground.x > 0}): on the
 * worker with Expanse's caverns (pure from the seed, so any thread), else on the client thread from the loaded blocks
 * ({@link ClientCaves}, a smaller budget). Each job's lines go to the render thread as one batch, written in one frame.
 */
public final class UndergroundTile {
	public static final int JOB_TICKS = 10;
	/** How many cells the eye may walk from the window's centre before it moves. */
	static final int STEP_CELLS = 4;
	/** How far (blocks) the eye may leave the base before the slice moves to it. */
	static final int REBASE = 6;
	/** A move of more cells than this in one go starts afresh. */
	static final int JUMP = 32;
	private static final int SIZE = TileCodec.UG_SIZE;

	/** Asks about the caves at one point. */
	public interface CaveSource {
		/** Fills {@code out} for the point (x, y, z); returns false where nothing is known (an unloaded chunk). */
		boolean at(int x, int y, int z, Sample out);
	}

	/** One point underground, in the tile's terms. */
	public static final class Sample {
		/** Blocks inside open space (0 in the rock). */
		public float space;
		/** The floor under the point and the roof over it (y); the point's own y in the rock. */
		public int floor, ceiling;
		/** The nearest cavern water ({@link TileCodec#WATER_NONE} ...) and how far across it is (blocks). */
		public int water;
		public float waterDistance = 1000;
		/** The realm ({@link TileCodec#REALMS}). */
		public int realm;
		/** How much the river here is a great one, 0..1. */
		public float greatRiver;
		public boolean glowworms, bats, drip;

		void clear(int y) {
			this.space = 0;
			this.floor = this.ceiling = y;
			this.water = TileCodec.WATER_NONE;
			this.waterDistance = 1000;
			this.realm = 0;
			this.greatRiver = 0;
			this.glowworms = this.bats = this.drip = false;
		}

		/** U's flags for the sample. */
		int flags() {
			return TileCodec.UG_PRESENT | (this.glowworms ? TileCodec.UG_GLOWWORMS : 0) | (this.drip ? TileCodec.UG_DRIP : 0)
				| (this.greatRiver >= 0.25F && this.water == TileCodec.WATER_RIVER && this.waterDistance < 48 ? TileCodec.UG_RIVER : 0);
		}
	}

	/** What the eye's own sample says, for {@code void_underground} and {@code void_cave}. */
	public record Eye(float openness, int realm, int water, boolean glowworms, boolean bats, boolean drip, float river) {
		public static final Eye NONE = new Eye(0, 0, TileCodec.WATER_NONE, false, false, false, 0);
	}

	/**
	 * What a job works from.
	 *
	 * @param world  what the caves belong to (the client level): another starts afresh
	 * @param source the caves (Expanse's, or the loaded blocks')
	 * @param budget how many cells this job may sample
	 */
	public record Inputs(Object world, CaveSource source, double eyeX, double eyeY, double eyeZ, int budget) {
	}

	private final RollingWindow window = new RollingWindow(SIZE, Contract.U, -1);
	private final int[] cellX = new int[SIZE * SIZE], cellZ = new int[SIZE * SIZE];
	private final boolean[] filled = new boolean[SIZE * SIZE];
	private final int[] texel = new int[SIZE * SIZE];
	private final int[] floor = new int[SIZE * SIZE], ceiling = new int[SIZE * SIZE];
	private final ArrayDeque<Integer> refill = new ArrayDeque<>();
	private final Sample sample = new Sample();
	private Object world;
	private CaveSource source;
	private int base;
	final StripQueue queue = new StripQueue();
	private final AtomicBoolean busy = new AtomicBoolean();
	private volatile Eye eye = Eye.NONE;
	private volatile boolean ready;
	private volatile long samples, jobs, rebases, nanos;

	/** Client thread: starts a job (on {@code worker}, which may be {@code Runnable::run}) unless one is running. */
	public void schedule(Inputs in, Executor worker) {
		if (in.source == null || !this.busy.compareAndSet(false, true)) {
			return;
		}
		try {
			worker.execute(() -> {
				try {
					long t0 = System.nanoTime();
					this.queue.post(this.job(in));
					this.nanos += System.nanoTime() - t0;
				} finally {
					this.busy.set(false);
				}
			});
		} catch (RuntimeException e) {
			this.busy.set(false);
			throw e;
		}
	}

	/** One job: restart, re-base, move, and sample as many queued rows as the budget allows. */
	TileStrip.Batch job(Inputs in) {
		this.jobs++;
		List<TileStrip> strips = new ArrayList<>();
		int cx = Contract.cell(in.eyeX, TileCodec.UG_CELL), cz = Contract.cell(in.eyeZ, TileCodec.UG_CELL);
		int wantBase = Math.round((float) in.eyeY / 4.0F) * 4;
		RollingWindow.Cells cells = this::cell;
		if (in.world != this.world || in.source != this.source || !this.window.have() || this.window.distance(cx, cz) > JUMP) {
			this.world = in.world;
			this.source = in.source;
			java.util.Arrays.fill(this.filled, false);
			this.base = wantBase;
			this.refill.clear();
			this.refill.addAll(this.window.restart(cx, cz));
			strips.add(this.window.clear(this.base));
		} else if (Math.abs(in.eyeY - this.base) > REBASE) {
			// every cell against the new base at once, then the openness again, nearest first
			this.base = wantBase;
			this.rebases++;
			for (int i = 0; i < this.filled.length; i++) {
				if (this.filled[i]) {
					int t = this.texel[i];
					this.texel[i] = TileCodec.underground(TileCodec.space(TileCodec.r(t)), this.floor[i], this.ceiling[i], this.base, TileCodec.a(t) >>> 4,
						TileCodec.a(t) & 15);
				}
			}
			List<Integer> rows = this.window.restart(this.window.centreX(), this.window.centreZ());
			for (int z : rows) {
				strips.add(this.window.row(z, this::encoded, this.base));
			}
			this.refill.clear();
			this.refill.addAll(rows);
		}
		if (this.window.distance(cx, cz) >= STEP_CELLS) {
			for (int line; (line = this.window.step(cx, cz)) != Integer.MIN_VALUE; ) {
				strips.add(this.window.line(this.window.lastColumn, line, cells, this.base));
			}
		}
		int budget = in.budget;
		while (!this.refill.isEmpty() && budget > 0) {
			int z = this.refill.poll();
			if (z < this.window.centreZ() - this.window.below || z > this.window.centreZ() + this.window.above) {
				continue;
			}
			strips.add(this.window.row(z, cells, this.base));
			budget -= SIZE;
		}
		// the eye's own
		Sample s = this.sample;
		s.clear((int) Math.floor(in.eyeY));
		if (this.source.at((int) Math.floor(in.eyeX), (int) Math.floor(in.eyeY), (int) Math.floor(in.eyeZ), s)) {
			float river = s.water == TileCodec.WATER_RIVER ? s.greatRiver * (1 - dev.voidmc.gloam.air.Noise.smoothstep(0.0F, 64.0F, s.waterDistance)) : 0;
			this.eye = new Eye(Math.min(1.0F, s.space / (255 * TileCodec.SPACE_STEP)), s.realm, s.water, s.glowworms, s.bats, s.drip, river);
		}
		if (!strips.isEmpty()) {
			this.ready = true;
		}
		return new TileStrip.Batch(strips, false);
	}

	/** Samples one cell (at its middle, at the base height) and keeps it. */
	private long cell(int cx, int cz) {
		int i = slot(cx, cz);
		Sample s = this.sample;
		s.clear(this.base);
		this.samples++;
		if (!this.source.at(cx * TileCodec.UG_CELL + 2, this.base, cz * TileCodec.UG_CELL + 2, s)) {
			this.filled[i] = false;
			return 0;
		}
		this.cellX[i] = cx;
		this.cellZ[i] = cz;
		this.filled[i] = true;
		this.floor[i] = s.floor;
		this.ceiling[i] = s.ceiling;
		// the water nibble names water at the cell itself (within 4 blocks, lava within 8), as the pack's U tile v1 reads
		// it: the low fog band, the cavern water's colour and the mist key on it; farther water is void_underground.w's
		int water = s.waterDistance <= (s.water == TileCodec.WATER_LAVA ? 8 : 4) ? s.water : TileCodec.WATER_NONE;
		this.texel[i] = TileCodec.underground(s.space, s.floor, s.ceiling, this.base, water, s.flags());
		return this.texel[i] & 0xFFFFFFFFL;
	}

	/** A kept cell's texel without sampling (0 for one not held). */
	private long encoded(int cx, int cz) {
		int i = slot(cx, cz);
		return this.filled[i] && this.cellX[i] == cx && this.cellZ[i] == cz ? this.texel[i] & 0xFFFFFFFFL : 0;
	}

	private static int slot(int cx, int cz) {
		return (cz & (SIZE - 1)) * SIZE + (cx & (SIZE - 1));
	}

	/** The eye's own sample, as of the last job. */
	public Eye eye() {
		return this.eye;
	}

	public boolean ready() {
		return this.ready;
	}

	/** Client thread: forgets the world. */
	public void reset() {
		this.eye = Eye.NONE;
		this.ready = false;
		this.queue.clear();
	}

	public String stats() {
		return String.format(java.util.Locale.ROOT, "underground tile: %d jobs, %d samples (%.1f us each), %d re-bases, base y %d", this.jobs, this.samples,
			this.samples == 0 ? 0 : this.nanos / 1e3 / this.samples, this.rebases, this.base);
	}
}
