package dev.voidmc.hearth.gametest.render;

/**
 * Patches for the previews' ground cover (coarse dirt in the steppe grass, red sand and coarse dirt on the mesa, sand,
 * gravel and mud at the water): smooth value noise over a 5-block lattice with a finer octave and a little per-block
 * grain, so the land reads as drifts and patches, not the diagonal stripes a linear hash of x and z draws across a
 * whole town (ART_REVIEW_2.md item 30).
 */
final class GroundNoise {
	private GroundNoise() {
	}

	/** A patch index in 0 .. n-1 for a column, spread roughly evenly, changing smoothly over a few blocks. */
	static int patch(int x, int z, int n) {
		double v = 0.6 * smooth(x, z, 5, 0x9E3779B9L) + 0.3 * smooth(x, z, 2, 0x7F4A7C15L) + 0.1 * hash(x, z, 0x2545F491L);
		// the sum crowds round the middle: stretch it back out before cutting it into n bands
		double s = Math.max(0, Math.min(0.999, (v - 0.5) * 1.9 + 0.5));
		return (int) (s * n);
	}

	private static double smooth(int x, int z, int cell, long salt) {
		int i = Math.floorDiv(x, cell);
		int j = Math.floorDiv(z, cell);
		double fx = (x - i * cell + 0.5) / cell;
		double fz = (z - j * cell + 0.5) / cell;
		fx = fx * fx * (3 - 2 * fx);
		fz = fz * fz * (3 - 2 * fz);
		double a = hash(i, j, salt);
		double b = hash(i + 1, j, salt);
		double c = hash(i, j + 1, salt);
		double d = hash(i + 1, j + 1, salt);
		return (a + (b - a) * fx) * (1 - fz) + (c + (d - c) * fx) * fz;
	}

	private static double hash(int x, int z, long salt) {
		long h = x * 0x632BE59BD9B4E019L ^ z * 0x9E3779B97F4A7C15L ^ salt;
		h ^= h >>> 31;
		h *= 0xBF58476D1CE4E5B9L;
		h ^= h >>> 29;
		return (h >>> 11) * 0x1.0p-53;
	}
}
