package dev.voidmc.gloam.far;

import dev.voidmc.gloam.air.Air;
import dev.voidmc.gloam.compat.Expanse;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.lwjgl.system.MemoryUtil;

/**
 * The weather fronts over the whole far view, as a picture: {@link Air#front} worked out on a grid of {@value #SIZE} x
 * {@value #SIZE} texels, {@value #TEXEL} blocks apart (about 49 km across), for the cloud deck, the rain curtains and
 * the cloud shadows to read on the GPU.
 *
 * <p>The grid lies in the fronts' own frame, which drifts east with them ({@link Air#FRONT_DRIFT}): a texel holds the
 * same piece of weather as it moves, so the shaders slide the picture along continuously and only the fronts' slow
 * change of shape (hours) waits for the next picture. A new picture is worked out on a worker every few seconds, or
 * when the camera has moved far enough from the old one's middle; each takes some 20 ms there.
 */
final class WeatherMap {
	static final int SIZE = 256;
	static final float TEXEL = 192.0F;
	/** How far the camera may wander from a picture's middle before a new one is centred on it, in blocks. */
	private static final float RECENTRE = 4000.0F;
	/** How often the picture is redone for the fronts' change of shape, in game ticks. */
	private static final long REFRESH_TICKS = 200;

	private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "VOID Gloam weather map");
		t.setDaemon(true);
		t.setPriority(Thread.MIN_PRIORITY);
		return t;
	});

	/** One finished picture: its corner in the fronts' frame, the fronts (0..1) row by row, and what it was made for. */
	record Picture(double originX, double originZ, float[] fronts, long gameTime, long seed) {
		/** The front at (fx, fz) in the fronts' frame, interpolated as the GPU does; 0 off the picture. */
		float at(double fx, double fz) {
			double u = (fx - this.originX) / TEXEL - 0.5, v = (fz - this.originZ) / TEXEL - 0.5;
			int x0 = (int) Math.floor(u), z0 = (int) Math.floor(v);
			if (x0 < 0 || z0 < 0 || x0 >= SIZE - 1 || z0 >= SIZE - 1) {
				return 0;
			}
			float tx = (float) (u - x0), tz = (float) (v - z0);
			float a = this.fronts[z0 * SIZE + x0], b = this.fronts[z0 * SIZE + x0 + 1];
			float c = this.fronts[(z0 + 1) * SIZE + x0], d = this.fronts[(z0 + 1) * SIZE + x0 + 1];
			return (a + (b - a) * tx) + ((c + (d - c) * tx) - (a + (b - a) * tx)) * tz;
		}
	}

	/** How far apart the land's wetness is read from Expanse's countries, in blocks: rain shadows are mountain-sized. */
	private static final int WET_STEP = 768;

	/** The land's wetness on a {@value #WET_STEP}-block lattice in the world, kept across pictures (worker only). */
	private final Map<Long, Float> wetness = new ConcurrentHashMap<>();
	private Expanse.Sampler wetnessOf;

	private volatile Picture ready;
	private Picture current;
	private boolean working;
	/** Set when {@link #current} changed and the GPU's copy is out of date. */
	private boolean dirty;

	/** The fronts' frame at {@code gameTime}: where world (x, z) lies in it. */
	static double frameX(double x, long gameTime) {
		return x - Air.FRONT_DRIFT * (gameTime / 20.0);
	}

	static double frameZ(double z, long gameTime) {
		return z - Air.FRONT_DRIFT * 0.2 * (gameTime / 20.0);
	}

	/**
	 * Called once a frame on the render thread: takes a finished picture, and starts the next when the old one is due
	 * or the camera has wandered from its middle. The land's wetness leans on the fronts (wet land cloudier, a rain
	 * shadow drier): each place's own from Expanse's countries ({@code country}), else {@code downfall}, the land's
	 * wetness at the camera, everywhere.
	 */
	void update(Air air, double cameraX, double cameraZ, long gameTime, float year, float downfall, Expanse.Sampler country) {
		Picture done = this.ready;
		if (done != null && done != this.current) {
			this.current = done;
			this.dirty = true;
			this.working = false;
		}
		if (this.working) {
			return;
		}
		double fx = frameX(cameraX, gameTime), fz = frameZ(cameraZ, gameTime);
		Picture c = this.current;
		double half = SIZE * TEXEL * 0.5;
		boolean due = c == null || c.seed != air.seed() || gameTime - c.gameTime > REFRESH_TICKS || gameTime < c.gameTime
			|| Math.abs(fx - (c.originX + half)) > RECENTRE || Math.abs(fz - (c.originZ + half)) > RECENTRE;
		if (!due) {
			return;
		}
		this.working = true;
		// snapped to whole texels, so a new picture's texels fall where the old one's did
		double ox = Math.floor((fx - half) / TEXEL) * TEXEL, oz = Math.floor((fz - half) / TEXEL) * TEXEL;
		WORKER.execute(() -> {
			try {
				this.ready = this.make(air, ox, oz, gameTime, year, downfall, country);
			} catch (RuntimeException e) {
				this.working = false;
				throw e;
			}
		});
	}

	private Picture make(Air air, double ox, double oz, long gameTime, float year, float downfall, Expanse.Sampler country) {
		if (country != this.wetnessOf || this.wetness.size() > 40_000) {
			this.wetness.clear();
			this.wetnessOf = country;
		}
		float[] fronts = new float[SIZE * SIZE];
		double driftX = Air.FRONT_DRIFT * (gameTime / 20.0), driftZ = Air.FRONT_DRIFT * 0.2 * (gameTime / 20.0);
		for (int z = 0; z < SIZE; z++) {
			for (int x = 0; x < SIZE; x++) {
				// Air.front takes world coordinates at a time: the texel's place in the frame, back in the world now
				double wx = ox + (x + 0.5) * TEXEL + driftX, wz = oz + (z + 0.5) * TEXEL + driftZ;
				float wet = country == null ? downfall : this.wetnessAt(country, wx, wz);
				fronts[z * SIZE + x] = air.front(wx, wz, gameTime, year, wet);
			}
		}
		return new Picture(ox, oz, fronts, gameTime, air.seed());
	}

	/** The land's wetness at world (x, z), between the lattice points round it (each read from Expanse once). */
	private float wetnessAt(Expanse.Sampler country, double x, double z) {
		double u = x / WET_STEP, v = z / WET_STEP;
		int x0 = (int) Math.floor(u), z0 = (int) Math.floor(v);
		float tx = (float) (u - x0), tz = (float) (v - z0);
		float a = this.lattice(country, x0, z0), b = this.lattice(country, x0 + 1, z0);
		float c = this.lattice(country, x0, z0 + 1), d = this.lattice(country, x0 + 1, z0 + 1);
		return (a + (b - a) * tx) + ((c + (d - c) * tx) - (a + (b - a) * tx)) * tz;
	}

	private float lattice(Expanse.Sampler country, int i, int j) {
		return this.wetness.computeIfAbsent(((long) i << 32) | (j & 0xFFFFFFFFL), k -> country.at(i * WET_STEP, j * WET_STEP).downfall());
	}

	/** The picture in use (null until the first is done). */
	Picture current() {
		return this.current;
	}

	/** If the picture changed since the last call, its texels as RGBA8 (the front in red) for the GPU; else null. */
	ByteBuffer takeUpload() {
		if (!this.dirty || this.current == null) {
			return null;
		}
		this.dirty = false;
		ByteBuffer out = MemoryUtil.memAlloc(SIZE * SIZE * 4);
		for (float f : this.current.fronts) {
			byte v = (byte) Math.round(Math.clamp(f, 0, 1) * 255);
			out.put(v).put(v).put(v).put((byte) 255);
		}
		return out.flip();
	}

	/** Forgets everything (a new level). */
	void reset() {
		this.current = null;
		this.ready = null;
		this.working = false;
		this.dirty = false;
	}
}
