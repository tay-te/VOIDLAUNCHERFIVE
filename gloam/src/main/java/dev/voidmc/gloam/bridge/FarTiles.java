package dev.voidmc.gloam.bridge;

import dev.voidmc.gloam.air.Air;
import dev.voidmc.gloam.air.Noise;
import dev.voidmc.gloam.compat.Expanse;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The far land tiles F0 and F1: 1024-block cells, 256 x 256 round the camera (262 km, VOID LOD's whole reach), world
 * anchored and wrapped like the near tiles ({@link LandTiles}), so crossing a kilometre brings in one line of 256 cells.
 *
 * <p>Two sources, each optional, merged per cell:
 * <ul>
 *   <li>Expanse's countries ({@link Expanse.RawSampler}, singleplayer on Expanse's Earth): humidity, temperature, the
 *       haze hint, the ground's height, water and the local lowness, from the seed, so the whole reach fills in seconds
 *       whether or not the land has been seen. Five samples a cell within {@value #DETAIL_REACH} cells of the camera, one
 *       beyond.</li>
 *   <li>VOID LOD's far land ({@link FarSource}, Gloam's {@code FarLand}): the real ground, water and biomes of what LOD
 *       has drawn, filled as its tiles arrive (on any server). Where it knows a cell well it gives the ground and the
 *       water; Expanse still gives the climate.</li>
 * </ul>
 *
 * <p>F0's height is the cell's <em>valley floor</em>: the lower quartile of its ground (LOD's 128-block cells, or the
 * second lowest of Expanse's five samples), so the pack's far mist slab ({@code floor + 4 + 26 mist}) settles into the
 * cell's hollows and along its rivers instead of half-way up its hills. F0's lowness is valley-scale: how far the cell's
 * mean ground lies under its eight neighbours' ({@code smoothstep(0, 48)} blocks), or Expanse's local lowness if more.
 *
 * <p>The work happens on a worker, at most {@value #SAMPLE_BUDGET} Expanse samples a job, nearest rows first; finished
 * lines go to the render thread through {@link #queue}. A rolling re-encode (one row every {@value #REFRESH_TICKS} ticks,
 * no sampling) carries the slow parts that are not the land's own: how wet the ground is, and the snow by the season.
 */
public final class FarTiles {
	/** Cells within this distance of the camera's get five Expanse samples, the rest one. */
	static final int DETAIL_REACH = 16;
	/** A move of more cells than this in one go starts the tiles afresh. */
	static final int JUMP = 64;
	/** Expanse samples per worker job. */
	static final int SAMPLE_BUDGET = 1536;
	/** Ticks between rolling re-encodes of a row. */
	static final int REFRESH_TICKS = 3;
	private static final int[][] DETAIL = {{512, 512}, {256, 256}, {768, 256}, {256, 768}, {768, 768}};
	private static final int[][] ONE = {{512, 512}};

	/** VOID LOD's far land as Gloam keeps it ({@code FarLand}), from any thread. */
	public interface FarSource {
		/**
		 * Sums up the known land in the square {@code minX .. minX + size}, {@code minZ .. minZ + size} into {@code out};
		 * returns whether anything there is known.
		 */
		boolean summary(int minX, int minZ, int size, Summary out);

		/** Hands over (and forgets) the far cells ({@link TileCodec#FAR_CELL}) new land arrived in since the last call. */
		void drainArrivals(java.util.function.LongConsumer cells);
	}

	/** What {@link FarSource#summary} found. */
	public static final class Summary {
		/** How many of LOD's cells inside were known. */
		public int known;
		/** The lower quartile and the mean of their ground (y). */
		public float floor, mean;
		/** The share of them that is water, 0..1. */
		public float water;
		/** Their biomes' mean base temperature (vanilla's scale) and downfall. */
		public float temperature, downfall;
	}

	/**
	 * What a job works from, gathered on the client thread.
	 *
	 * @param world    what the land belongs to (the client level): another one starts the tiles afresh
	 * @param country  Expanse's countries, or null
	 * @param far      VOID LOD's far land, or null
	 * @param wetness  how wet the ground is at the camera (the far land is taken as wet: one regional number)
	 * @param cold     the season's cold 0..1 ({@code SeasonColours.cold}), 0 with the seasons off
	 * @param summer   the season's warmth above 0 ({@code max(0, SeasonColours.warmth)}), 0 with the seasons off
	 * @param seaLevel the level's sea level
	 * @param tick     the client's tick counter, for the rolling refresh
	 */
	public record Inputs(Object world, Expanse.RawSampler country, FarSource far, double cameraX, double cameraZ, float wetness, float cold, float summer,
		int seaLevel, long tick) {
	}

	/** One cell's land, as the two sources gave it. */
	static final class Cell {
		int x, z;
		boolean filled;
		// Expanse
		boolean e;
		float floorE, meanE, waterE, nearE, tempE, humidE, lowE;
		int kindE;
		// VOID LOD
		int knownF;
		float floorF, meanF, waterF, tempF, humidF;
	}

	private final RollingWindow window = new RollingWindow(Contract.TILE, Contract.F0, Contract.F1);
	/** The window's cells by their wrapped index; made on the first job (some 5 MB), so a game without a pack pays nothing. */
	private Cell[] cells;
	private final ArrayDeque<Integer> refill = new ArrayDeque<>();
	private final BitSet dirtyRows = new BitSet(256), dirtyColumns = new BitSet(256);
	private final Summary summary = new Summary();
	private Object world;
	private Expanse.RawSampler country;
	private FarSource far;
	private int refreshRow;
	private long refreshedAt = Long.MIN_VALUE;
	final StripQueue queue = new StripQueue();
	private final AtomicBoolean busy = new AtomicBoolean();
	private volatile boolean pending, ready;
	private volatile long samples, jobs, nanos;
	private int askedX = Integer.MIN_VALUE, askedZ = Integer.MIN_VALUE;
	private long scheduledAt = Long.MIN_VALUE;


	/**
	 * Client thread, once a tick: starts a job when there is something to do (the camera crossed a cell, rows still to
	 * fill, LOD's land arrived, or the rolling refresh is due). One job at a time.
	 */
	public void tick(Inputs in, Executor worker) {
		if (this.busy.get() || in.country == null && in.far == null) {
			return;
		}
		int tx = Contract.cell(in.cameraX, TileCodec.FAR_CELL), tz = Contract.cell(in.cameraZ, TileCodec.FAR_CELL);
		boolean moved = tx != this.askedX || tz != this.askedZ;
		if (!moved && !this.pending && in.tick - this.scheduledAt < REFRESH_TICKS) {
			return;
		}
		this.askedX = tx;
		this.askedZ = tz;
		this.scheduledAt = in.tick;
		this.busy.set(true);
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

	/** Worker: one job's worth of work, as a batch of lines. */
	TileStrip.Batch job(Inputs in) {
		this.jobs++;
		if (this.cells == null) {
			this.cells = new Cell[256 * 256];
			for (int i = 0; i < this.cells.length; i++) {
				this.cells[i] = new Cell();
			}
		}
		List<TileStrip> strips = new ArrayList<>();
		int tx = Contract.cell(in.cameraX, TileCodec.FAR_CELL), tz = Contract.cell(in.cameraZ, TileCodec.FAR_CELL);
		boolean otherWorld = in.world != this.world || in.country != this.country || in.far != this.far;
		if (otherWorld || !this.window.have() || this.window.distance(tx, tz) > JUMP) {
			if (otherWorld) {
				// the same cells in another world are other land
				for (Cell c : this.cells) {
					c.filled = false;
				}
			}
			this.world = in.world;
			this.country = in.country;
			this.far = in.far;
			this.refill.clear();
			this.refill.addAll(this.window.restart(tx, tz));
			this.dirtyRows.clear();
			this.dirtyColumns.clear();
			strips.add(this.window.clear(0));
		}
		int budget = SAMPLE_BUDGET;
		boolean worked = false;
		while (!this.refill.isEmpty() && budget > 0) {
			int z = this.refill.poll();
			budget -= this.fillLine(in, false, z, tx, tz);
			this.markRows(z);
			worked = true;
		}
		while (this.refill.isEmpty() && budget > 0) {
			int line = this.window.step(tx, tz);
			if (line == Integer.MIN_VALUE) {
				break;
			}
			boolean column = this.window.lastColumn;
			budget -= this.fillLine(in, column, line, tx, tz);
			if (column) {
				this.dirtyColumns.set((line - 1) & 255);
				this.dirtyColumns.set(line & 255);
				this.dirtyColumns.set((line + 1) & 255);
			} else {
				this.markRows(line);
			}
			worked = true;
		}
		if (in.far != null) {
			in.far.drainArrivals(key -> {
				int cx = (int) (key >> 32), cz = (int) key;
				if (this.window.contains(cx, cz)) {
					Cell c = this.cells[index(cx, cz)];
					if (c.filled && c.x == cx && c.z == cz) {
						this.readFar(in.far, c);
						this.markRows(cz);
					}
				}
			});
		}
		if (!worked && this.dirtyRows.isEmpty() && in.tick - this.refreshedAt >= REFRESH_TICKS) {
			this.refreshedAt = in.tick;
			this.refreshRow = (this.refreshRow + 1) & 255;
			this.dirtyRows.set((this.window.centreZ() - this.window.below + this.refreshRow) & 255);
		}
		this.emit(in, strips);
		this.pending = !this.refill.isEmpty() || this.window.distance(tx, tz) > 0;
		if (!strips.isEmpty()) {
			this.ready = true;
		}
		return new TileStrip.Batch(strips, true);
	}

	private void markRows(int z) {
		this.dirtyRows.set((z - 1) & 255);
		this.dirtyRows.set(z & 255);
		this.dirtyRows.set((z + 1) & 255);
	}

	/** Samples every cell of a line of the window that is not yet held; returns the Expanse samples taken. */
	private int fillLine(Inputs in, boolean column, int at, int camX, int camZ) {
		int from = (column ? this.window.centreZ() : this.window.centreX()) - this.window.below;
		int taken = 0;
		for (int i = 0; i < this.window.size; i++) {
			int cx = column ? at : from + i, cz = column ? from + i : at;
			Cell c = this.cells[index(cx, cz)];
			if (c.filled && c.x == cx && c.z == cz) {
				continue;
			}
			taken += this.fill(in, c, cx, cz, Math.max(Math.abs(cx - camX), Math.abs(cz - camZ)) <= DETAIL_REACH);
		}
		this.samples += taken;
		return taken;
	}

	/** Works out one cell from both sources. Returns the Expanse samples taken. */
	int fill(Inputs in, Cell c, int cx, int cz, boolean detail) {
		c.x = cx;
		c.z = cz;
		c.filled = true;
		c.e = false;
		c.knownF = 0;
		int taken = 0;
		if (in.country != null) {
			int[][] at = detail ? DETAIL : ONE;
			float[] alts = new float[at.length];
			float sumAlt = 0, water = 0, near = 0, temp = 0, humid = 0, low = 0;
			int bx = cx * TileCodec.FAR_CELL, bz = cz * TileCodec.FAR_CELL;
			String hint = null;
			for (int k = 0; k < at.length; k++) {
				// near the camera the full model (rivers, lakes, the local lowness), past it the far-view model, which
				// never builds a full plate (the chunk generator's are kept)
				Expanse.Raw r = detail ? in.country.at(bx + at[k][0], bz + at[k][1]) : in.country.far(bx + at[k][0], bz + at[k][1]);
				// the sea's surface, not its bed, as the near tiles have it
				float alt = r.coastDistance() < 0 ? Math.max(r.altitude(), in.seaLevel) : r.altitude();
				alts[k] = alt;
				sumAlt += alt;
				water += r.waterDistance() <= 0 || r.coastDistance() < 0 ? 1 : 0;
				near += nearness(r.waterDistance(), r.coastDistance());
				temp += r.temperature();
				humid += r.humidity();
				low += Noise.smoothstep(0.0F, 24.0F, r.lowness());
				if (k == 0) {
					hint = r.haze();
				}
			}
			taken = at.length;
			java.util.Arrays.sort(alts);
			c.floorE = alts[Math.min(alts.length - 1, 1)];
			c.meanE = sumAlt / at.length;
			c.waterE = water / at.length;
			c.nearE = near / at.length;
			c.tempE = Math.clamp(temp / at.length, -1, 1);
			c.humidE = Math.clamp(humid / at.length, 0, 1);
			c.lowE = low / at.length;
			c.kindE = Contract.hazeKind(hint);
			c.e = true;
		}
		if (in.far != null) {
			this.readFar(in.far, c);
		}
		return taken;
	}

	/** Takes in what VOID LOD's far land knows of a cell now. */
	private void readFar(FarSource far, Cell c) {
		Summary s = this.summary;
		s.known = 0;
		if (!far.summary(c.x * TileCodec.FAR_CELL, c.z * TileCodec.FAR_CELL, TileCodec.FAR_CELL, s) || s.known == 0) {
			c.knownF = 0;
			return;
		}
		c.knownF = s.known;
		c.floorF = s.floor;
		c.meanF = s.mean;
		c.waterF = s.water;
		c.tempF = TileCodec.temperature(s.temperature);
		c.humidF = s.downfall;
	}

	/** How near open water a sample is, 0..1, as {@code Expanse.water} has it. */
	static float nearness(float waterDistance, float coastDistance) {
		return Math.max(1 - Noise.smoothstep(0.0F, 96.0F, waterDistance), 1 - Noise.smoothstep(0.0F, 96.0F, coastDistance));
	}

	/** Encodes the dirty lines of the window into strips. */
	private void emit(Inputs in, List<TileStrip> strips) {
		RollingWindow.Cells encode = (cx, cz) -> this.encode(in, cx, cz);
		int z0 = this.window.centreZ() - this.window.below, x0 = this.window.centreX() - this.window.below;
		for (int i = this.dirtyRows.nextSetBit(0); i >= 0; i = this.dirtyRows.nextSetBit(i + 1)) {
			// the window's row that falls on wrapped line i
			int z = z0 + Math.floorMod(i - z0, 256);
			strips.add(this.window.row(z, encode, 0));
		}
		for (int i = this.dirtyColumns.nextSetBit(0); i >= 0; i = this.dirtyColumns.nextSetBit(i + 1)) {
			int x = x0 + Math.floorMod(i - x0, 256);
			strips.add(this.window.column(x, encode, 0));
		}
		this.dirtyRows.clear();
		this.dirtyColumns.clear();
	}

	/** One cell's F0 (low) and F1 (high) texels; 0 for a cell nothing is known of. */
	long encode(Inputs in, int cx, int cz) {
		Cell c = this.held(cx, cz);
		if (c == null || !c.e && c.knownF == 0) {
			return 0;
		}
		boolean lod = c.knownF >= 4 || !c.e;
		float floor = lod ? c.floorF : c.floorE;
		float mean = lod ? c.meanF : c.meanE;
		float water = c.e && c.knownF > 0 ? Math.max(c.waterE, c.waterF) : c.e ? c.waterE : c.waterF;
		float temp = c.e ? c.tempE : c.tempF;
		float humid = c.e ? c.humidE : c.humidF;
		// valley-scale lowness: how far the cell lies under its neighbours
		float ring = 0;
		int n = 0;
		for (int dz = -1; dz <= 1; dz++) {
			for (int dx = -1; dx <= 1; dx++) {
				if (dx == 0 && dz == 0) {
					continue;
				}
				Cell o = this.held(cx + dx, cz + dz);
				if (o != null && (o.e || o.knownF > 0)) {
					ring += o.knownF >= 4 || !o.e ? o.meanF : o.meanE;
					n++;
				}
			}
		}
		float lowness = n == 0 ? 0 : Noise.smoothstep(0.0F, 48.0F, ring / n - mean);
		if (c.e) {
			lowness = Math.max(lowness, 0.6F * c.lowE);
		}
		float near = c.e ? Math.max(water, 0.6F * c.nearE) : water;
		float potential = Air.mistPotential(lowness, near, humid, in.wetness, TileCodec.dry(temp, humid));
		int kind = c.e && c.kindE > 0 ? c.kindE : TileCodec.hazeKind(temp, humid, water, lowness, mean - in.seaLevel, false);
		float snowline = SeasonSnow.height(TileCodec.biomeTemperature(temp), in.seaLevel, in.cold, in.summer);
		int flags = Contract.CELL_PRESENT | (c.e ? Contract.CELL_EXPANSE : 0) | (mean > snowline && water < 0.5F ? Contract.CELL_SNOW : 0)
			| (water >= 0.5F ? Contract.CELL_WATER : 0);
		int f0 = TileCodec.f0(floor, lowness, water);
		int f1 = TileCodec.f1(potential, temp, humid, kind, flags);
		return f0 & 0xFFFFFFFFL | (long) f1 << 32;
	}

	/** The cell held for (cx, cz), or null if its slot holds another or nothing. */
	private Cell held(int cx, int cz) {
		Cell c = this.cells[index(cx, cz)];
		return c.filled && c.x == cx && c.z == cz ? c : null;
	}

	static int index(int cx, int cz) {
		return (cz & 255) * 256 + (cx & 255);
	}

	/** Whether the tiles hold anything yet. */
	public boolean ready() {
		return this.ready;
	}

	/** Client thread: forgets the world. The worker starts over on its next job (the sources change identity). */
	public void reset() {
		this.askedX = Integer.MIN_VALUE;
		this.ready = false;
		this.queue.clear();
	}

	public String stats() {
		return String.format(java.util.Locale.ROOT, "far tiles: %d jobs, %d Expanse samples, %.1f ms on the worker in all (%.1f us a sample), %s", this.jobs,
			this.samples, this.nanos / 1e6, this.samples == 0 ? 0 : this.nanos / 1e3 / this.samples, this.pending ? "filling" : "full");
	}
}
