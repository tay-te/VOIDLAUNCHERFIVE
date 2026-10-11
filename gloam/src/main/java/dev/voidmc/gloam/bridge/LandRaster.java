package dev.voidmc.gloam.bridge;

import java.util.HashMap;
import java.util.Map;

/**
 * The land's height and wetness on a coarse lattice, as the weather tiles need them for the rain shadow and the wet
 * lean of the fronts: a dense snapshot ({@link LandRaster}) over the area a rebuild covers, filled from a cache of
 * lattice points ({@link Lattice}) that outlives rebuilds, so the land is asked about each point once per world.
 *
 * <p>Snapshots are immutable and published to other threads (the camera's rain-shadow lean is read on the client
 * thread from the worker's last snapshot).
 *
 * @param originX world x of lattice column 0
 * @param originZ world z of lattice row 0
 * @param step    blocks between lattice points
 * @param n       lattice points along a side
 * @param alt     the ground's height (y) per point, row-major; NaN where the land is not known
 * @param humid   the land's wetness 0..1 per point; NaN where not known
 */
public record LandRaster(int originX, int originZ, int step, int n, float[] alt, float[] humid) {
	/** Asks about the land at one point: {@code out[0]} its height (y), {@code out[1]} its wetness 0..1; NaN for unknown. */
	@FunctionalInterface
	public interface Probe {
		void probe(int x, int z, float[] out);
	}

	/** The ground's height at world (x, z), bilinear between the lattice points round it; NaN off the raster or unknown. */
	public float altitude(double x, double z) {
		return this.bilinear(this.alt, x, z);
	}

	/** The land's wetness at world (x, z); NaN off the raster or unknown. */
	public float humidity(double x, double z) {
		return this.bilinear(this.humid, x, z);
	}

	private float bilinear(float[] v, double x, double z) {
		double u = (x - this.originX) / this.step, w = (z - this.originZ) / this.step;
		int i = (int) Math.floor(u), j = (int) Math.floor(w);
		if (i < 0 || j < 0 || i >= this.n - 1 || j >= this.n - 1) {
			return Float.NaN;
		}
		float tx = (float) (u - i), tz = (float) (w - j);
		float a = v[j * this.n + i], b = v[j * this.n + i + 1], c = v[(j + 1) * this.n + i], d = v[(j + 1) * this.n + i + 1];
		float top = a + (b - a) * tx, bottom = c + (d - c) * tx;
		return top + (bottom - top) * tz;
	}

	/**
	 * Lattice points kept across rebuilds, keyed by their lattice index. Worker-confined. Points whose height is unknown
	 * are not kept (VOID LOD's far land may know them later).
	 */
	public static final class Lattice {
		private static final int LIMIT = 80_000;
		final int step;
		private final Map<Long, long[]> known = new HashMap<>();
		private Probe of;
		/** Points asked of the probe so far (for the logs). */
		long asked;

		public Lattice(int step) {
			this.step = step;
		}

		/**
		 * A snapshot covering world {@code minX..maxX}, {@code minZ..maxZ} (widened to whole lattice steps) from
		 * {@code probe}. A different probe than last time (another world) forgets the kept points.
		 */
		public LandRaster raster(Probe probe, double minX, double minZ, double maxX, double maxZ) {
			if (probe != this.of || this.known.size() > LIMIT) {
				this.known.clear();
				this.of = probe;
			}
			int i0 = (int) Math.floor(minX / this.step), j0 = (int) Math.floor(minZ / this.step);
			int i1 = (int) Math.ceil(maxX / this.step), j1 = (int) Math.ceil(maxZ / this.step);
			int n = Math.max(i1 - i0, j1 - j0) + 1;
			float[] alt = new float[n * n], humid = new float[n * n];
			float[] out = new float[2];
			for (int j = 0; j < n; j++) {
				for (int i = 0; i < n; i++) {
					long key = (long) (i0 + i) << 32 | (j0 + j) & 0xFFFFFFFFL;
					long[] kept = this.known.get(key);
					float a, h;
					if (kept != null) {
						a = Float.intBitsToFloat((int) kept[0]);
						h = Float.intBitsToFloat((int) (kept[0] >>> 32));
					} else {
						out[0] = out[1] = Float.NaN;
						probe.probe((i0 + i) * this.step, (j0 + j) * this.step, out);
						this.asked++;
						a = out[0];
						h = out[1];
						if (!Float.isNaN(a)) {
							this.known.put(key, new long[]{Float.floatToIntBits(a) & 0xFFFFFFFFL | (long) Float.floatToIntBits(h) << 32});
						}
					}
					alt[j * n + i] = a;
					humid[j * n + i] = h;
				}
			}
			return new LandRaster(i0 * this.step, j0 * this.step, this.step, n, alt, humid);
		}

		public void forget() {
			this.known.clear();
			this.of = null;
		}
	}
}
