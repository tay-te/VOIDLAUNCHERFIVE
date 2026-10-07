package dev.voidmc.lod.source;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunction;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunctions;
import net.minecraft.world.level.levelgen.densityfunction.DensitySampler;
import net.minecraft.world.level.levelgen.densityfunction.DensityVolume;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;
import net.minecraft.world.level.levelgen.densityfunction.ScopedDensityBuffer;
import net.minecraft.world.level.levelgen.densityfunction.op.InterpolatedFunction;

/**
 * The ground exactly as the chunks will have it, block by block. A noise generator does not lay its terrain at the
 * height its model gives a column: it samples its density at the corners of cells (4 blocks across, 8 high for
 * Expanse), interpolates between them, and fills every block where that is positive. Between corners the ground is a
 * blend of theirs, plus whatever 3D noise the density carries, so it strays a block or more from the model's height,
 * in bands that follow the contours. This samples the generator's own interpolated density (its seeded sampler, from
 * the world's noise router) at those corners near the model's height, interpolates as the chunk does, and finds the
 * first block of air from above: the tiles next to the chunks, and the trees stood on them, then meet the chunks block
 * for block. Caves that only open at the surface by their fine noise, and the ground a structure levels, are left out.
 */
public final class NoiseSurface {
	/**
	 * Blocks sampled above and below the model's height first: its 3D noise moves the ground under four blocks. Where
	 * the ground is not within that, the corners are sampled further, up to {@link #FAR}.
	 */
	private static final int REACH = 5;
	private static final int FAR = 24;
	/** Columns worked out, and the time they took, for the statistics. */
	static final java.util.concurrent.atomic.AtomicLong COLUMNS = new java.util.concurrent.atomic.AtomicLong();
	static final java.util.concurrent.atomic.AtomicLong NANOS = new java.util.concurrent.atomic.AtomicLong();
	private static final int CACHED = 16384;

	private final DensitySampler sampler;
	private final int cellXz;
	private final int cellY;
	private final ThreadLocal<Corners> corners = ThreadLocal.withInitial(Corners::new);
	/** Per thread, the ground last found: where to look first in a world with no model height to go by. */
	private final ThreadLocal<int[]> last = ThreadLocal.withInitial(() -> new int[]{64});

	/**
	 * The first air above the ground in column (x, z) of a world with no model height to start from (vanilla's): looked
	 * for near the last ground found (the column beside it, usually), then further out, then through the whole world
	 * height; {@link Integer#MIN_VALUE} if there is none (the void).
	 */
	int firstAir(int x, int z, int minY, int maxY) {
		int[] last = this.last.get();
		long t0 = System.nanoTime();
		int air = Integer.MIN_VALUE;
		for (int reach : new int[]{REACH + 3, 48, 160}) {
			air = this.firstAir(x, z, (float) last[0], reach);
			if (air != Integer.MIN_VALUE) {
				break;
			}
		}
		if (air == Integer.MIN_VALUE) {
			float middle = (minY + maxY) / 2.0F;
			air = this.firstAir(x, z, middle, (int) Math.ceil((maxY - minY) / 2.0F) + this.cellY);
		}
		if (air != Integer.MIN_VALUE) {
			last[0] = air;
		}
		NANOS.addAndGet(System.nanoTime() - t0);
		COLUMNS.incrementAndGet();
		return air;
	}

	private NoiseSurface(DensitySampler sampler, int cellXz, int cellY) {
		this.sampler = sampler;
		this.cellXz = cellXz;
		this.cellY = cellY;
	}

	/** For the world whose noise is {@code randomState}'s, or null if its final density is not interpolated. */
	public static NoiseSurface of(RandomState randomState) {
		InterpolatedFunction interpolated = find(((dev.voidmc.lod.mixin.RandomStateAccessor) (Object) randomState).voidLod$router().finalDensity(), 0);
		if (interpolated == null) {
			return null;
		}
		return new NoiseSurface(randomState.getSampler(interpolated.input()), interpolated.cellSizeXz(), interpolated.cellSizeY());
	}

	private static InterpolatedFunction find(DensityFunction f, int depth) {
		if (depth > 64 || f == null) {
			return null;
		}
		if (f instanceof InterpolatedFunction i) {
			return i;
		}
		if (f instanceof DensityFunctions.HolderHolder(var holder)) {
			return find(holder.value(), depth + 1);
		}
		InterpolatedFunction[] found = new InterpolatedFunction[1];
		f.rewriteChildren(child -> {
			if (found[0] == null) {
				found[0] = find(child, depth + 1);
			}
			return child;
		});
		return found[0];
	}

	String stats() {
		long n = COLUMNS.get();
		return String.format(java.util.Locale.ROOT, ", ground block by block: %d columns (%.1f us each)", n, n == 0 ? 0 : NANOS.get() / 1e3 / n);
	}

	/** One column of cell corners: the density at layers {@code lo}, {@code lo + 1}... (in cells). */
	private static final class Column {
		int lo;
		float[] values = new float[0];
	}

	/** Per thread: its sampler context and the corner columns it has sampled. */
	private static final class Corners {
		final SamplerContext context = SamplerContext.builder().enableCaches().build();
		final Map<Long, Column> columns = new HashMap<>();
	}

	/**
	 * The first block of air above the ground in column (x, z), whose model height is {@code height}; or
	 * {@link Integer#MIN_VALUE} if the ground is not within reach of it (a cave opening, a structure).
	 */
	public int firstAir(int x, int z, float height) {
		long t0 = System.nanoTime();
		int air = this.firstAir(x, z, height, REACH);
		if (air == Integer.MIN_VALUE) {
			air = this.firstAir(x, z, height, FAR);
		}
		NANOS.addAndGet(System.nanoTime() - t0);
		COLUMNS.incrementAndGet();
		return air;
	}

	private int firstAir(int x, int z, float height, int reach) {
		Corners c = this.corners.get();
		if (c.columns.size() > CACHED) {
			c.columns.clear();
		}
		int xc = Math.floorDiv(x, this.cellXz) * this.cellXz;
		int zc = Math.floorDiv(z, this.cellXz) * this.cellXz;
		int lo = Math.floorDiv((int) Math.floor(height) - reach, this.cellY);
		int hi = Math.floorDiv((int) Math.ceil(height) + reach, this.cellY) + 1;
		Column c00 = this.column(c, xc, zc, lo, hi);
		// on a corner itself (far tiles sample on the lattice) the other three corners weigh nothing
		boolean onCorner = x == xc && z == zc;
		Column c10 = onCorner ? c00 : this.column(c, xc + this.cellXz, zc, lo, hi);
		Column c01 = onCorner ? c00 : this.column(c, xc, zc + this.cellXz, lo, hi);
		Column c11 = onCorner ? c00 : this.column(c, xc + this.cellXz, zc + this.cellXz, lo, hi);
		float fx = (x - xc) / (float) this.cellXz;
		float fz = (z - zc) / (float) this.cellXz;
		if (layer(c00, c10, c01, c11, hi, fx, fz) > 0) {
			// solid at the top of the reach: the ground is higher than the model says, beyond it
			return Integer.MIN_VALUE;
		}
		float above = layer(c00, c10, c01, c11, hi, fx, fz);
		for (int l = hi - 1; l >= lo; l--) {
			float here = layer(c00, c10, c01, c11, l, fx, fz);
			if (here > 0) {
				// the crossing is in this cell: the highest block in it the interpolation calls solid
				for (int dy = this.cellY - 1; dy >= 0; dy--) {
					float t = dy / (float) this.cellY;
					if (here + t * (above - here) > 0) {
						return l * this.cellY + dy + 1;
					}
				}
				return l * this.cellY + 1;
			}
			above = here;
		}
		return Integer.MIN_VALUE;
	}

	/** The density at layer {@code l} interpolated across the cell, as the chunk interpolates it. */
	private static float layer(Column c00, Column c10, Column c01, Column c11, int l, float fx, float fz) {
		float a = c00.values[l - c00.lo], b = c10.values[l - c10.lo];
		float d = c01.values[l - c01.lo], e = c11.values[l - c11.lo];
		float near = a + fx * (b - a);
		float far = d + fx * (e - d);
		return near + fz * (far - near);
	}

	/** The corner column at (x, z), sampled at least over layers lo..hi. */
	private Column column(Corners c, int x, int z, int lo, int hi) {
		long key = (long) x << 32 ^ z & 0xFFFFFFFFL;
		Column column = c.columns.get(key);
		if (column == null) {
			column = new Column();
			column.lo = lo;
			column.values = this.sample(c, x, z, lo, hi - lo + 1);
			c.columns.put(key, column);
			return column;
		}
		int have = column.lo + column.values.length - 1;
		if (lo >= column.lo && hi <= have) {
			return column;
		}
		// widen it: keep what was sampled, sample the layers it lacks
		int nlo = Math.min(lo, column.lo), nhi = Math.max(hi, have);
		float[] values = new float[nhi - nlo + 1];
		if (nlo < column.lo) {
			System.arraycopy(this.sample(c, x, z, nlo, column.lo - nlo), 0, values, 0, column.lo - nlo);
		}
		System.arraycopy(column.values, 0, values, column.lo - nlo, column.values.length);
		if (nhi > have) {
			System.arraycopy(this.sample(c, x, z, have + 1, nhi - have), 0, values, have + 1 - nlo, nhi - have);
		}
		column.lo = nlo;
		column.values = values;
		return column;
	}

	private float[] sample(Corners c, int x, int z, int lo, int layers) {
		DensityVolume volume = new DensityVolume(1, layers, 1, x, lo * this.cellY, z, this.cellXz, this.cellY, this.cellXz);
		float[] out = new float[layers];
		try (ScopedDensityBuffer buffer = this.sampler.bind(c.context).sampleVolume(volume)) {
			for (int y = 0; y < layers; y++) {
				out[y] = buffer.get(volume.indexUnchecked(0, y, 0));
			}
		}
		return out;
	}
}
