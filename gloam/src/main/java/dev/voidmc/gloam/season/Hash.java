package dev.voidmc.gloam.season;

/** Small, fast integer hashes and the noise and cells made from them, for the colours' per-place variation. */
final class Hash {
	private Hash() {
	}

	/** A well-mixed 32 bits for the lattice point (x, z). */
	static int of(int seed, int x, int z) {
		int h = seed * 0x27D4EB2D ^ x * 0x165667B1 ^ z * 0x61C88647;
		h ^= h >>> 15;
		h *= 0x2C1B3C6D;
		h ^= h >>> 12;
		h *= 0x297A2D39;
		return h ^ h >>> 15;
	}

	/** A value 0..1 from 8 bits of {@code h}, the {@code which}-th byte. */
	static float unit(int h, int which) {
		return (h >>> (which * 8) & 0xFF) / 255.0F;
	}

	/** Smooth value noise, -1..1, one lattice cell per unit. */
	static float value(int seed, double x, double z) {
		int ix = (int) Math.floor(x), iz = (int) Math.floor(z);
		float fx = (float) (x - ix), fz = (float) (z - iz);
		fx = fx * fx * (3 - 2 * fx);
		fz = fz * fz * (3 - 2 * fz);
		float a = unit(of(seed, ix, iz), 0), b = unit(of(seed, ix + 1, iz), 0);
		float c = unit(of(seed, ix, iz + 1), 0), d = unit(of(seed, ix + 1, iz + 1), 0);
		float top = a + (b - a) * fx, bottom = c + (d - c) * fx;
		return (top + (bottom - top) * fz) * 2 - 1;
	}

	/**
	 * The cell of a jittered grid {@code size} blocks across that (x, z) lies nearest the middle of: one tree's crown, near
	 * enough, with uneven edges between neighbours rather than a grid's straight lines. Returns the cell's hash.
	 */
	static int cell(int seed, int x, int z, int size) {
		int cx = Math.floorDiv(x, size), cz = Math.floorDiv(z, size);
		float px = x + 0.5F, pz = z + 0.5F;
		float best = Float.MAX_VALUE;
		int id = 0;
		for (int dz = -1; dz <= 1; dz++) {
			for (int dx = -1; dx <= 1; dx++) {
				int h = of(seed, cx + dx, cz + dz);
				float fx = (cx + dx + unit(h, 2)) * size - px, fz = (cz + dz + unit(h, 3)) * size - pz;
				float d = fx * fx + fz * fz;
				if (d < best) {
					best = d;
					id = h;
				}
			}
		}
		return id;
	}
}
