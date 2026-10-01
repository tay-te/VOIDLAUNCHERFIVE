package dev.voidmc.expanse.world.terrain;

import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.synth.SimplexNoise;

/**
 * The tectonic plates: one per cell of a jittered {@value #SPACING}-block grid, the world divided
 * between them by distance (a Voronoi diagram), measured after a gentle domain warp so the borders
 * wander instead of running straight.
 *
 * <p>Each plate is continental or oceanic, and drifts in its own direction. Whether two neighbouring
 * plates move toward each other (converge) or apart decides what their border becomes: a mountain
 * range, a coastal range, an island arc or open sea. Continental plates cluster, following a slow noise,
 * so land comes in continents of several plates with oceans between them.
 */
final class PlateMap {
	static final int SPACING = 4096;
	private static final double JITTER = 0.38;
	private static final double WARP = 520.0;
	private static final double WARP_FINE = 110.0;

	record Plate(long key, int gx, int gz, double cx, double cz, boolean continental, double driftX, double driftZ, long seed) {
	}

	/** Where a point lies: its plate, and how far it is from the border with each kind of neighbour. */
	static final class Hit {
		Plate own;
		/** Distance (blocks, in warped space) to the nearest border of any kind, and the plate across it. */
		double border;
		Plate across;
		/** Distance to the nearest border with an oceanic plate (infinite if there is none nearby). */
		double oceanBorder;
		Plate oceanAcross;
		/** Unit vector from this plate's centre toward {@link #across}'s, for reflecting across the border. */
		double normalX;
		double normalZ;
	}

	private final long seed;
	private final SimplexNoise warpX;
	private final SimplexNoise warpZ;
	private final SimplexNoise warpFineX;
	private final SimplexNoise warpFineZ;
	private final SimplexNoise land;
	private final ConcurrentHashMap<Long, Plate> plates = new ConcurrentHashMap<>();

	PlateMap(long seed) {
		this.seed = seed;
		RandomSource random = RandomSource.create(seed ^ 0x504C_4154_4553L);
		this.warpX = new SimplexNoise(random);
		this.warpZ = new SimplexNoise(random);
		this.warpFineX = new SimplexNoise(random);
		this.warpFineZ = new SimplexNoise(random);
		this.land = new SimplexNoise(random);
	}

	Plate plate(int gx, int gz) {
		// Scrambled (bijectively) so the keys hash evenly: Long.hashCode of the plain packed pair is
		// gx ^ gz, which puts every plate on a diagonal in the same bucket.
		long key = scramble((long) gx << 32 | gz & 0xFFFF_FFFFL);
		Plate p = this.plates.get(key);
		if (p == null) {
			p = this.makePlate(gx, gz, key);
			Plate raced = this.plates.putIfAbsent(key, p);
			if (raced != null) {
				p = raced;
			}
		}
		return p;
	}

	private Plate makePlate(int gx, int gz, long key) {
		long h = mix(this.seed, gx, gz);
		double jx = unit(h) - 0.5;
		double jz = unit(h >>> 21) - 0.5;
		double cx = (gx + 0.5 + jx * 2 * JITTER) * SPACING;
		double cz = (gz + 0.5 + jz * 2 * JITTER) * SPACING;
		// Continents: a slow noise picks the land half of the world, a per-plate roll breaks it up.
		double landness = this.land.get(cx / 17000.0, cz / 17000.0) * 0.9 + (unit(h >>> 42) - 0.5) * 0.7;
		boolean continental = landness > -0.08 || gx == 0 && gz == 0 || gx == -1 && gz == -1;
		double angle = unit(mix(h, 7, 11)) * Math.PI * 2;
		return new Plate(key, gx, gz, cx, cz, continental, Math.cos(angle), Math.sin(angle), mix(h, gx * 31L, gz * 17L));
	}

	double warpedX(double x, double z) {
		return x + WARP * this.warpX.get(x / 4200.0, z / 4200.0) + WARP_FINE * this.warpFineX.get(x / 1000.0, z / 1000.0);
	}

	double warpedZ(double x, double z) {
		return z + WARP * this.warpZ.get(x / 4200.0, z / 4200.0) + WARP_FINE * this.warpFineZ.get(x / 1000.0, z / 1000.0);
	}

	/** The plates round the last cell looked up, per thread: neighbouring lookups nearly always share them. */
	private static final class Neighbourhood {
		int gx = Integer.MIN_VALUE;
		int gz;
		final Plate[] plates = new Plate[25];
	}

	private final ThreadLocal<Neighbourhood> neighbourhood = ThreadLocal.withInitial(Neighbourhood::new);

	/** The plate at (x, z) and its distances to the borders round it. */
	void locate(double x, double z, Hit out) {
		double wx = this.warpedX(x, z);
		double wz = this.warpedZ(x, z);
		int gx = Mth.floor(wx / SPACING);
		int gz = Mth.floor(wz / SPACING);
		Neighbourhood hood = this.neighbourhood.get();
		Plate[] near = hood.plates;
		if (hood.gx != gx || hood.gz != gz) {
			int c = 0;
			for (int dx = -2; dx <= 2; dx++) {
				for (int dz = -2; dz <= 2; dz++) {
					near[c++] = this.plate(gx + dx, gz + dz);
				}
			}
			hood.gx = gx;
			hood.gz = gz;
		}
		int n = near.length;
		Plate best = null;
		double bestD = Double.MAX_VALUE;
		for (Plate p : near) {
			double d = sq(p.cx - wx) + sq(p.cz - wz);
			if (d < bestD) {
				bestD = d;
				best = p;
			}
		}
		out.own = best;
		out.border = Double.MAX_VALUE;
		out.oceanBorder = Double.MAX_VALUE;
		out.across = null;
		out.oceanAcross = null;
		for (int i = 0; i < n; i++) {
			Plate p = near[i];
			if (p == best) {
				continue;
			}
			// Signed distance to the perpendicular bisector of the two centres: that is the Voronoi edge.
			double nx = p.cx - best.cx;
			double nz = p.cz - best.cz;
			double len = Math.sqrt(nx * nx + nz * nz);
			nx /= len;
			nz /= len;
			double mx = (p.cx + best.cx) * 0.5;
			double mz = (p.cz + best.cz) * 0.5;
			double d = -((wx - mx) * nx + (wz - mz) * nz);
			if (d < out.border) {
				out.border = d;
				out.across = p;
				out.normalX = nx;
				out.normalZ = nz;
			}
			if (!p.continental && d < out.oceanBorder) {
				out.oceanBorder = d;
				out.oceanAcross = p;
			}
		}
	}

	/** How hard two plates push together across their border: 1 head-on, 0 sliding past, -1 pulling apart. */
	static double convergence(Plate a, Plate b) {
		double nx = b.cx - a.cx;
		double nz = b.cz - a.cz;
		double len = Math.sqrt(nx * nx + nz * nz);
		return ((a.driftX - b.driftX) * nx + (a.driftZ - b.driftZ) * nz) / len * 0.5;
	}

	/** A bijective 64-bit mix (MurmurHash3's finaliser): distinct inputs stay distinct. */
	static long scramble(long h) {
		h ^= h >>> 33;
		h *= 0xFF51_AFD7_ED55_8CCDL;
		h ^= h >>> 33;
		h *= 0xC4CE_B9FE_1A85_EC53L;
		return h ^ h >>> 33;
	}

	static long mix(long seed, long a, long b) {
		long h = seed ^ a * 0x9E37_79B9_7F4A_7C15L ^ b * 0xC2B2_AE3D_27D4_EB4FL;
		h ^= h >>> 33;
		h *= 0xFF51_AFD7_ED55_8CCDL;
		h ^= h >>> 33;
		h *= 0xC4CE_B9FE_1A85_EC53L;
		return h ^ h >>> 33;
	}

	/** The low 21 bits of h as a number in [0, 1). */
	static double unit(long h) {
		return (h & 0x1F_FFFF) / (double) 0x20_0000;
	}

	private static double sq(double v) {
		return v * v;
	}
}
