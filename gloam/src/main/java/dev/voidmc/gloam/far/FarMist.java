package dev.voidmc.gloam.far;

import dev.voidmc.gloam.air.Air;
import dev.voidmc.gloam.air.Climate;
import dev.voidmc.gloam.air.Noise;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import org.lwjgl.system.MemoryUtil;

/**
 * The mist lying in the far valleys, as a mesh: the top of the mist over every {@link FarLand} cell where it lies, at
 * the height cold air pools to there (the lowest ground nearby plus {@link Air#mistDepth}), each vertex carrying how
 * deep the mist is over the ground under it and how thick it is. The mist itself is {@link Air#mist} for each far
 * place's own climate: its biome's warmth and wetness, how far below the land around it lies, how near water; so it
 * gathers in the valleys and over the rivers and leaves the ridges and hills standing out of it.
 *
 * <p>Rebuilt on a worker every couple of seconds (the mist changes over minutes); a build reads some 65,000 cells for a
 * 16 km view and takes a few milliseconds.
 */
final class FarMist {
	/** Bytes per vertex: x, y, z as floats from the anchor, then depth / 48 and thickness as unsigned bytes. */
	static final int VERTEX_BYTES = 16;
	private static final long REBUILD_NANOS = 2_000_000_000L;

	private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "VOID Gloam far mist");
		t.setDaemon(true);
		t.setPriority(Thread.MIN_PRIORITY);
		return t;
	});

	/** A finished mesh: quads, anchored at (anchorX, anchorZ) (y is world y). The vertices are freed once uploaded. */
	record Mesh(ByteBuffer vertices, int quads, int anchorX, int anchorZ) {
	}

	private final AtomicReference<Mesh> ready = new AtomicReference<>();
	private volatile boolean working;
	private long lastStart;
	private double lastX = Double.NaN, lastZ;
	private long lastClock;

	/** The mesh finished since the last call, or null; the caller owns (and frees) its vertices. */
	Mesh take() {
		return this.ready.getAndSet(null);
	}

	/** Starts a new build if one is due: every couple of seconds, at once when the camera jumps or the clock is set. */
	void update(FarLand land, Air air, double x, double z, double reach, long clock, float wetness) {
		if (this.working || !land.known()) {
			return;
		}
		long now = System.nanoTime();
		boolean jumped = Double.isNaN(this.lastX) || Math.abs(x - this.lastX) > 256 || Math.abs(z - this.lastZ) > 256
			|| Math.abs(clock - this.lastClock) > 600;
		if (!jumped && now - this.lastStart < REBUILD_NANOS) {
			return;
		}
		this.lastStart = now;
		this.lastX = x;
		this.lastZ = z;
		this.lastClock = clock;
		this.working = true;
		WORKER.execute(() -> {
			try {
				Mesh old = this.ready.getAndSet(build(land, air, x, z, reach, clock, wetness));
				if (old != null) {
					MemoryUtil.memFree(old.vertices());
				}
			} finally {
				this.working = false;
			}
		});
	}

	void reset() {
		Mesh m = this.ready.getAndSet(null);
		if (m != null) {
			MemoryUtil.memFree(m.vertices());
		}
		this.lastX = Double.NaN;
	}

	static Mesh build(FarLand land, Air air, double camX, double camZ, double reach, long clock, float wetness) {
		int cell = FarLand.CELL;
		int r = (int) Math.ceil(reach / cell) + 1;
		// the window of cells, with a margin for the neighbourhoods read around each
		int margin = 4;
		int n = 2 * (r + margin) + 1;
		int ox = (int) Math.floor(camX / cell) - r - margin, oz = (int) Math.floor(camZ / cell) - r - margin;
		float[] h = new float[n * n];
		float[] water = new float[n * n];
		float[] temp = new float[n * n];
		float[] wet = new float[n * n];
		boolean any = false;
		for (int z = 0; z < n; z++) {
			for (int x = 0; x < n; x++) {
				int cx = ox + x, cz = oz + z;
				FarLand.Region region = land.region(cx, cz);
				int i = z * n + x;
				int at = Math.floorMod(cz, FarLand.REGION) * FarLand.REGION + Math.floorMod(cx, FarLand.REGION);
				if (region == null || region.height[at] == FarLand.UNKNOWN) {
					h[i] = Float.NaN;
					continue;
				}
				any = true;
				h[i] = region.height[at];
				water[i] = (region.water[at] & 255) / 255.0F;
				temp[i] = (region.temperature[at] & 255) / 64.0F - 1;
				wet[i] = (region.downfall[at] & 255) / 255.0F;
			}
		}
		if (!any) {
			return new Mesh(MemoryUtil.memAlloc(0), 0, (int) camX, (int) camZ);
		}
		// the land around each cell, three cells (384 blocks) each way: its mean, to tell a valley from a ridge
		float[] mean = boxMean(h, n, 3);
		// the lowest ground two cells each way: the floor cold air pools on
		float[] floor = boxMin(h, n, 2);
		// near water: any of the cell and its neighbours
		float[] near = boxMax(water, n, 1);

		float year = air.year(clock);
		float[] top = new float[n * n];
		float[] thick = new float[n * n];
		for (int i = 0; i < n * n; i++) {
			if (Float.isNaN(h[i]) || Float.isNaN(mean[i]) || Float.isNaN(floor[i])) {
				top[i] = Float.NaN;
				continue;
			}
			float lowness = Noise.smoothstep(0.0F, 40.0F, mean[i] - h[i]);
			Climate climate = new Climate(temp[i], wet[i], lowness, Math.max(water[i], near[i] * 0.6F));
			float m = Air.mist(clock, year, climate, wetness);
			thick[i] = m;
			top[i] = floor[i] + Air.mistDepth(m);
		}

		int anchorX = (int) Math.floor(camX), anchorZ = (int) Math.floor(camZ);
		ByteBuffer out = MemoryUtil.memAlloc(VERTEX_BYTES * 4 * 4096);
		int quads = 0;
		double r2 = (reach + cell) * (reach + cell);
		int[] corners = new int[4];
		for (int z = margin; z < n - margin - 1; z++) {
			for (int x = margin; x < n - margin - 1; x++) {
				corners[0] = z * n + x;
				corners[1] = (z + 1) * n + x;
				corners[2] = (z + 1) * n + x + 1;
				corners[3] = z * n + x + 1;
				boolean show = false, complete = true;
				for (int c : corners) {
					if (Float.isNaN(top[c])) {
						complete = false;
						break;
					}
					if (thick[c] > 0.01F && top[c] > h[c] + 0.5F) {
						show = true;
					}
				}
				if (!complete || !show) {
					continue;
				}
				double wx = (ox + x + 0.5) * cell, wz = (oz + z + 0.5) * cell;
				double dx = wx - camX, dz = wz - camZ;
				if (dx * dx + dz * dz > r2) {
					continue;
				}
				if (out.remaining() < VERTEX_BYTES * 4) {
					out = MemoryUtil.memRealloc(out, out.capacity() * 2);
				}
				for (int k = 0; k < 4; k++) {
					int c = corners[k];
					int cx = c % n, cz = c / n;
					out.putFloat((float) ((ox + cx + 0.5) * cell - anchorX));
					out.putFloat(top[c]);
					out.putFloat((float) ((oz + cz + 0.5) * cell - anchorZ));
					out.put((byte) Math.round(Math.clamp((top[c] - h[c]) / 48.0F, 0, 1) * 255));
					out.put((byte) Math.round(Math.clamp(thick[c], 0, 1) * 255));
					out.put((byte) 0).put((byte) 0);
				}
				quads++;
			}
		}
		return new Mesh(out.flip(), quads, anchorX, anchorZ);
	}

	/** The mean of the known values within {@code k} cells each way (NaN where none is known). */
	private static float[] boxMean(float[] v, int n, int k) {
		float[] sum = new float[n * n];
		int[] count = new int[n * n];
		// along x, then along z
		for (int z = 0; z < n; z++) {
			for (int x = 0; x < n; x++) {
				float s = 0;
				int c = 0;
				for (int d = -k; d <= k; d++) {
					int xx = x + d;
					if (xx >= 0 && xx < n && !Float.isNaN(v[z * n + xx])) {
						s += v[z * n + xx];
						c++;
					}
				}
				sum[z * n + x] = s;
				count[z * n + x] = c;
			}
		}
		float[] out = new float[n * n];
		for (int z = 0; z < n; z++) {
			for (int x = 0; x < n; x++) {
				float s = 0;
				int c = 0;
				for (int d = -k; d <= k; d++) {
					int zz = z + d;
					if (zz >= 0 && zz < n) {
						s += sum[zz * n + x];
						c += count[zz * n + x];
					}
				}
				out[z * n + x] = c == 0 ? Float.NaN : s / c;
			}
		}
		return out;
	}

	private static float[] boxMin(float[] v, int n, int k) {
		return boxExtreme(v, n, k, true);
	}

	private static float[] boxMax(float[] v, int n, int k) {
		return boxExtreme(v, n, k, false);
	}

	/** The least (or greatest) known value within {@code k} cells each way (NaN where none is known). */
	private static float[] boxExtreme(float[] v, int n, int k, boolean min) {
		float[] pass = new float[n * n];
		float[] out = new float[n * n];
		for (int z = 0; z < n; z++) {
			for (int x = 0; x < n; x++) {
				float e = Float.NaN;
				for (int d = -k; d <= k; d++) {
					int xx = x + d;
					if (xx >= 0 && xx < n) {
						e = pick(e, v[z * n + xx], min);
					}
				}
				pass[z * n + x] = e;
			}
		}
		for (int z = 0; z < n; z++) {
			for (int x = 0; x < n; x++) {
				float e = Float.NaN;
				for (int d = -k; d <= k; d++) {
					int zz = z + d;
					if (zz >= 0 && zz < n) {
						e = pick(e, pass[zz * n + x], min);
					}
				}
				out[z * n + x] = e;
			}
		}
		return out;
	}

	private static float pick(float a, float b, boolean min) {
		if (Float.isNaN(b)) {
			return a;
		}
		if (Float.isNaN(a)) {
			return b;
		}
		return min ? Math.min(a, b) : Math.max(a, b);
	}
}
