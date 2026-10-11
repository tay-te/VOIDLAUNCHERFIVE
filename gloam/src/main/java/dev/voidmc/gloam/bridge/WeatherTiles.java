package dev.voidmc.gloam.bridge;

import dev.voidmc.gloam.air.Air;
import dev.voidmc.gloam.air.Noise;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The weather tiles W0 (128-block cells, 32 km) and W1 (1024-block cells, 262 km): the fronts ({@link Air#front}),
 * leaned on by the land's wetness and its rain shadows ({@link RainShadow}), with the cloud cover, the rain and the
 * storm worked out from them exactly as the camera's air works them out, so the pack's clouds rain where the rain
 * particles fall and the rain is heard.
 *
 * <p>The tiles lie in the fronts' own frame, which drifts east with them ({@link Air#FRONT_DRIFT}, a fifth of it
 * south): a texel holds the same piece of weather as it moves, and between rebuilds the pack slides the picture along
 * by {@code void_weatherShift} alone. A cell (cx, cz) of tile size {@code s} covers the frame's
 * {@code cx*s .. (cx+1)*s}; at {@code seconds} it lies over world {@code x = frame x + FRONT_DRIFT * seconds}.
 *
 * <p>A rebuild (every {@value #REBUILD_TICKS} ticks for the fronts' slow change of shape, at once when vanilla's rain or
 * thunder has moved, the world changed or the camera jumped) works out both whole tiles on a worker, about 130,000
 * fronts, some 40-80 ms there, and the render thread writes them over {@value #SPREAD_FRAMES} frames. In between, as the
 * camera crosses a cell of the frame (walking, or the frame drifting under it: one W0 cell in 21 s) the window steps by
 * one cell and only the line that came in is worked out (256 fronts).
 */
public final class WeatherTiles {
	public static final int REBUILD_TICKS = 200;
	/** A move of more cells than this in one go rebuilds instead of stepping. */
	static final int JUMP = 12;
	/** How many frames a rebuild is spread over. */
	static final int SPREAD_FRAMES = 4;
	/** Lattice steps of the land raster for W0 and W1, in blocks. */
	static final int FINE_STEP = 512, COARSE_STEP = 4096;
	/** How far round the camera the rasters reach beyond the tiles: the upwind reach and a margin for steps. */
	private static final double FINE_MARGIN = RainShadow.UPWIND[2] + 2048, COARSE_MARGIN = RainShadow.UPWIND[2] + 8192;

	/**
	 * What a rebuild or a step is worked out from, gathered on the client thread.
	 *
	 * @param air            this world's air
	 * @param gameTime       the world's game ticks
	 * @param year           0..1
	 * @param vanillaRain    vanilla's own rain level, before Gloam makes it regional
	 * @param vanillaThunder vanilla's own thunder level (under its rain)
	 * @param showers        whether the fronts bring showers under a clear vanilla sky (the setting)
	 * @param windX          the wind's direction (where it blows toward), a unit vector
	 * @param downfall       the land's wetness at the camera, for places with no land data
	 * @param land           asks the land's height and wetness (Expanse, else VOID LOD's far land), or null
	 * @param reach          how far round the camera the strongest storm is looked for (the far view), in blocks
	 */
	public record Inputs(Air air, long gameTime, float year, float vanillaRain, float vanillaThunder, boolean showers, float windX, float windZ,
		double cameraX, double cameraZ, float downfall, LandRaster.Probe land, double reach) {
		double seconds() {
			return this.gameTime / 20.0;
		}
	}

	private final RollingWindow near = new RollingWindow(Contract.TILE, Contract.W0, -1);
	private final RollingWindow far = new RollingWindow(Contract.TILE, Contract.W1, -1);
	private final LandRaster.Lattice fineLattice = new LandRaster.Lattice(FINE_STEP);
	private final LandRaster.Lattice coarseLattice = new LandRaster.Lattice(COARSE_STEP);
	final StripQueue queue = new StripQueue();
	private final AtomicBoolean busy = new AtomicBoolean();

	// published by the worker
	private volatile LandRaster fine, coarse;
	private volatile float maxStorm;
	private volatile boolean ready;
	private volatile double lastMillis;

	// the client thread's record of what it asked for
	private long builtAt = Long.MIN_VALUE;
	private float builtRain = -1, builtThunder = -1;
	private long builtSeed;
	private boolean builtShowers;
	private int askedNearX, askedNearZ, askedFarX, askedFarZ;
	private int rebuilds, steps;

	/** The cell of the fronts' frame world x lies in at {@code seconds}, for cells {@code size} blocks across. */
	public static int frameCellX(double x, double seconds, int size) {
		return (int) Math.floor((x - Air.FRONT_DRIFT * seconds) / size);
	}

	public static int frameCellZ(double z, double seconds, int size) {
		return (int) Math.floor((z - Air.FRONT_DRIFT * 0.2 * seconds) / size);
	}

	/**
	 * Client thread, once a tick: starts a rebuild when one is due, or a step when the camera has crossed into another
	 * cell of the frame. One job at a time; a tick with the worker busy waits for the next.
	 */
	public void tick(Inputs in, Executor worker) {
		if (this.busy.get()) {
			return;
		}
		double s = in.seconds();
		int nx = frameCellX(in.cameraX, s, TileCodec.WEATHER_CELL), nz = frameCellZ(in.cameraZ, s, TileCodec.WEATHER_CELL);
		int fx = frameCellX(in.cameraX, s, TileCodec.FAR_CELL), fz = frameCellZ(in.cameraZ, s, TileCodec.FAR_CELL);
		boolean rebuild = this.builtAt == Long.MIN_VALUE || in.air.seed() != this.builtSeed || in.gameTime - this.builtAt >= REBUILD_TICKS
			|| in.gameTime < this.builtAt || Math.abs(in.vanillaRain - this.builtRain) > 0.08F || Math.abs(in.vanillaThunder - this.builtThunder) > 0.1F
			|| in.showers != this.builtShowers || Math.max(Math.abs(nx - this.askedNearX), Math.abs(nz - this.askedNearZ)) > JUMP;
		boolean step = nx != this.askedNearX || nz != this.askedNearZ || fx != this.askedFarX || fz != this.askedFarZ;
		if (!rebuild && !step) {
			return;
		}
		if (rebuild) {
			this.builtAt = in.gameTime;
			this.builtRain = in.vanillaRain;
			this.builtThunder = in.vanillaThunder;
			this.builtSeed = in.air.seed();
			this.builtShowers = in.showers;
			this.rebuilds++;
		} else {
			this.steps++;
		}
		this.askedNearX = nx;
		this.askedNearZ = nz;
		this.askedFarX = fx;
		this.askedFarZ = fz;
		this.busy.set(true);
		try {
			worker.execute(() -> {
				try {
					long t0 = System.nanoTime();
					this.queue.post(rebuild ? this.rebuild(in) : this.step(in));
					this.lastMillis = (System.nanoTime() - t0) / 1e6;
				} finally {
					this.busy.set(false);
				}
			});
		} catch (RuntimeException e) {
			this.busy.set(false);
			throw e;
		}
	}

	/** Worker: both tiles afresh round the camera, nearest rows first, W0's and W1's interleaved. */
	TileStrip.Batch rebuild(Inputs in) {
		double s = in.seconds();
		double drift = Air.FRONT_DRIFT * s;
		LandRaster fineLand = null, coarseLand = null;
		if (in.land != null) {
			double r = 128.0 * TileCodec.WEATHER_CELL + FINE_MARGIN;
			fineLand = this.fineLattice.raster(in.land, in.cameraX - r, in.cameraZ - r, in.cameraX + r, in.cameraZ + r);
			double R = 128.0 * TileCodec.FAR_CELL + COARSE_MARGIN;
			coarseLand = this.coarseLattice.raster(in.land, in.cameraX - R, in.cameraZ - R, in.cameraX + R, in.cameraZ + R);
		}
		this.fine = fineLand;
		this.coarse = coarseLand;
		List<Integer> nearRows = this.near.restart(frameCellX(in.cameraX, s, TileCodec.WEATHER_CELL), frameCellZ(in.cameraZ, s, TileCodec.WEATHER_CELL));
		List<Integer> farRows = this.far.restart(frameCellX(in.cameraX, s, TileCodec.FAR_CELL), frameCellZ(in.cameraZ, s, TileCodec.FAR_CELL));
		Cell nearCell = new Cell(in, fineLand, coarseLand, TileCodec.WEATHER_CELL);
		Cell farCell = new Cell(in, coarseLand, null, TileCodec.FAR_CELL);
		List<TileStrip> strips = new ArrayList<>(512);
		for (int i = 0; i < Contract.TILE; i++) {
			strips.add(this.near.row(nearRows.get(i), nearCell, 0));
			strips.add(this.far.row(farRows.get(i), farCell, 0));
		}
		// the strongest storm in the far view, for how high the pack lets the towers climb
		double camFx = in.cameraX - drift, camFz = in.cameraZ - Air.FRONT_DRIFT * 0.2 * s;
		float best = 0;
		for (int z = this.far.centreZ() - this.far.below; z <= this.far.centreZ() + this.far.above; z++) {
			for (int x = this.far.centreX() - this.far.below; x <= this.far.centreX() + this.far.above; x++) {
				double dx = (x + 0.5) * TileCodec.FAR_CELL - camFx, dz = (z + 0.5) * TileCodec.FAR_CELL - camFz;
				if (dx * dx + dz * dz <= in.reach * in.reach) {
					best = Math.max(best, farCell.storms[Math.floorMod(z, 256) * 256 + Math.floorMod(x, 256)]);
				}
			}
		}
		this.maxStorm = Noise.smoothstep(0.0F, 0.5F, best);
		this.ready = true;
		return new TileStrip.Batch(strips, true);
	}

	/** Worker: moves each window toward the camera's cell of the frame, one line a cell. */
	TileStrip.Batch step(Inputs in) {
		if (!this.near.have()) {
			return this.rebuild(in);
		}
		double s = in.seconds();
		int nx = frameCellX(in.cameraX, s, TileCodec.WEATHER_CELL), nz = frameCellZ(in.cameraZ, s, TileCodec.WEATHER_CELL);
		if (this.near.distance(nx, nz) > JUMP) {
			return this.rebuild(in);
		}
		List<TileStrip> strips = new ArrayList<>();
		LandRaster fineLand = this.fine, coarseLand = this.coarse;
		Cell nearCell = new Cell(in, fineLand, coarseLand, TileCodec.WEATHER_CELL);
		Cell farCell = new Cell(in, coarseLand, null, TileCodec.FAR_CELL);
		for (int line; (line = this.near.step(nx, nz)) != Integer.MIN_VALUE; ) {
			strips.add(this.near.line(this.near.lastColumn, line, nearCell, 0));
		}
		int fx = frameCellX(in.cameraX, s, TileCodec.FAR_CELL), fz = frameCellZ(in.cameraZ, s, TileCodec.FAR_CELL);
		for (int line; (line = this.far.step(fx, fz)) != Integer.MIN_VALUE; ) {
			strips.add(this.far.line(this.far.lastColumn, line, farCell, 0));
		}
		return new TileStrip.Batch(strips, true);
	}

	/** One tile's cells at one moment: the fronts with the land's lean, and what they bring. */
	static final class Cell implements RollingWindow.Cells {
		private final Inputs in;
		private final LandRaster land, wider;
		private final int size;
		private final double driftX, driftZ;
		private final float[] scratch = new float[RainShadow.UPWIND.length];
		/** The storm in each cell worked out, by its wrapped index (for the far view's strongest storm). */
		final float[] storms = new float[256 * 256];

		/**
		 * @param land  the land raster the cells read first
		 * @param wider a coarser raster for cells off {@code land} (W0's far edge), or null
		 */
		Cell(Inputs in, LandRaster land, LandRaster wider, int size) {
			this.in = in;
			this.land = land;
			this.wider = wider;
			this.size = size;
			double s = in.seconds();
			this.driftX = Air.FRONT_DRIFT * s;
			this.driftZ = Air.FRONT_DRIFT * 0.2 * s;
		}

		@Override
		public long cell(int cx, int cz) {
			double wx = (cx + 0.5) * this.size + this.driftX, wz = (cz + 0.5) * this.size + this.driftZ;
			LandRaster land = this.land;
			float humid = land == null ? Float.NaN : land.humidity(wx, wz);
			if (Float.isNaN(humid) && this.wider != null) {
				land = this.wider;
				humid = land.humidity(wx, wz);
			}
			if (Float.isNaN(humid)) {
				humid = this.in.downfall;
			}
			float raw = this.in.air.front(wx, wz, this.in.gameTime, this.in.year, humid);
			float lean = RainShadow.leanAt(land, wx, wz, this.in.windX, this.in.windZ, this.scratch);
			float front = RainShadow.front(raw, lean);
			float storm = Air.storm(front, this.in.year);
			this.storms[Math.floorMod(cz, 256) * 256 + Math.floorMod(cx, 256)] = storm;
			return encodeRest(front, storm, this.in) & 0xFFFFFFFFL;
		}
	}

	/** A W texel from a front with the land's lean in it, as the camera's air works out the same front. */
	static int encodeRest(float front, float storm, Inputs in) {
		float rain = Air.rain(in.vanillaRain, front, in.showers);
		float cover = Air.cloudCover(front, rain);
		float stormy = Math.max(storm, in.vanillaThunder * Noise.smoothstep(0.3F, 0.6F, front));
		return TileCodec.weather(front, cover, rain, stormy);
	}

	// --------------------------------------------------------------------------------------------- reads, any thread

	/**
	 * The land's lean on the front at world (x, z) for a wind blowing toward (windX, windZ), from the last rebuild's
	 * land: what {@code Atmosphere} adds to the camera's front so its rain agrees with the tiles. 0 before the first.
	 */
	public float leanAt(double x, double z, float windX, float windZ) {
		LandRaster fine = this.fine;
		// the fine raster where it reaches the place, else the coarse (0 is a lean like any other)
		LandRaster land = fine != null && !Float.isNaN(fine.altitude(x, z)) ? fine : this.coarse;
		return RainShadow.leanAt(land, x, z, windX, windZ);
	}

	/** {@code void_maxStorm}: the strongest storm in the far view, 0..1, as {@code FarAir} worked it out. */
	public float maxStorm() {
		return this.maxStorm;
	}

	/** Whether the tiles hold anything yet. */
	public boolean ready() {
		return this.ready;
	}

	/** Client thread: forgets everything (a new world). The worker's windows restart at the next rebuild. */
	public void reset() {
		this.builtAt = Long.MIN_VALUE;
		this.ready = false;
		this.maxStorm = 0;
		this.fine = null;
		this.coarse = null;
		this.queue.clear();
	}

	/** One line for the logs. */
	public String stats() {
		return String.format(java.util.Locale.ROOT, "weather tiles: %d rebuilds, %d steps, last job %.1f ms, max storm %.2f, land points asked %d + %d",
			this.rebuilds, this.steps, this.lastMillis, this.maxStorm, this.fineLattice.asked, this.coarseLattice.asked);
	}
}
