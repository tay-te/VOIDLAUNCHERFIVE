package dev.voidmc.lod.source;

import dev.voidmc.lod.core.ColumnSample;
import dev.voidmc.lod.core.Colors;
import dev.voidmc.lod.core.TerrainSource;

/**
 * VOID Hearth's towns stood on the land nobody has loaded ({@link Footprints}): up close (cells of 4 blocks or less) a
 * column under a finished building rises to its eaves in the building's wall colour, and its roof climbs a block for
 * every block in from the footprint's edge to the ridge, so a house has a pitched roof and a hall a long ridge; farther
 * out a cell takes on the share of it under buildings, a town becoming red and brown roofs on the horizon, lit at night.
 * Under the chunks the player has seen ({@code IngestSource}), which replace it as soon as they load.
 */
public final class HearthOverlay implements TerrainSource {
	/** What VOID Hearth says stands where ({@link HearthFootprints}). Thread-safe. */
	public interface Footprints {
		/** Bumped whenever anything in the 1024-block region (x >> 10, z >> 10) would draw differently. */
		long version(int regionX, int regionZ);

		/** A finished building's {wall colour, roof colour, eave << 16 | ridge & 0xFFFF} at column (x, z), if one stands there. */
		boolean sample(int x, int z, int[] out);

		/** Something changed (a region's version moved on): what was read before may be out of date. */
		default void changed() {
		}
	}

	/** How far in from a footprint's edge a roof is looked for: a ridge rises no higher than this above the eaves here. */
	private static final int ROOF_REACH = 8;
	/** The widest cells towns are drawn in (out to a few kilometres): past them a town is under a few pixels. */
	static final int MAX_CELL = 32;
	/** Points per side a far cell is looked at in, for the share of it under buildings. */
	private static final int FAR_POINTS = 4;

	private final TerrainSource generator;
	private final Footprints footprints;
	private final ThreadLocal<int[]> scratch = ThreadLocal.withInitial(() -> new int[3]);
	private final ThreadLocal<int[]> probe = ThreadLocal.withInitial(() -> new int[3]);

	public HearthOverlay(TerrainSource generator, Footprints footprints) {
		this.generator = generator;
		this.footprints = footprints;
	}

	public TerrainSource generator() {
		return this.generator;
	}

	public Footprints footprints() {
		return this.footprints;
	}

	@Override
	public long version() {
		return this.generator.version();
	}

	@Override
	public boolean partial() {
		return this.generator.partial();
	}

	@Override
	public boolean covers(int minX, int minZ, int maxX, int maxZ) {
		return this.generator.covers(minX, minZ, maxX, maxZ);
	}

	@Override
	public void beginTile(long key, int x0, int z0, int cell, int border, int size) {
		this.generator.beginTile(key, x0, z0, cell, border, size);
	}

	@Override
	public void endTile() {
		this.generator.endTile();
	}

	@Override
	public dev.voidmc.lod.core.Decorator decorator() {
		return this.generator.decorator();
	}

	@Override
	public boolean keepsAnswers() {
		return this.generator.keepsAnswers();
	}

	@Override
	public void sample(int x, int z, int cell, ColumnSample out) {
		this.generator.sample(x, z, cell, out);
		if (out.empty) {
			return;
		}
		if (cell > MAX_CELL) {
			// a town is a few pixels out there, and asking would have Hearth work out the history of every region in reach
			return;
		}
		int[] b = this.scratch.get();
		if (cell <= 4) {
			if (!this.footprints.sample(x, z, b)) {
				return;
			}
			int wall = b[0] & 0xFFFFFF, roof = b[1] & 0xFFFFFF, eave = b[2] >> 16, ridge = (short) b[2];
			int ground = (int) Math.ceil(out.ground);
			eave = Math.max(eave, ground + 2);
			ridge = Math.max(ridge, eave);
			// the roof rises a block for every block in from the footprint's edge, up to the ridge
			int in = this.inward(x, z, Math.min(ROOF_REACH, ridge - eave));
			out.ground = Math.min(ridge, eave + in) + 1;
			out.water = Integer.MIN_VALUE;
			out.top = roof;
			out.side = out.soil = out.rock = wall;
			out.soilDepth = Math.max(1, (int) out.ground - ground);
			out.bands = null;
			out.topMaterial = out.sideMaterial = out.soilMaterial = out.rockMaterial = 0;
			out.canopy = 0;
			out.cover = 0;
			out.bareWhenSteep = false;
			// windows lit at night
			out.light = Math.max(out.light, 10);
			return;
		}
		// farther out: the share of the cell under buildings, standing as high as their eaves
		int hits = 0, tallest = Integer.MIN_VALUE;
		long r = 0, g = 0, bl = 0;
		float step = cell / (float) FAR_POINTS;
		float start = x - cell / 2.0F + step / 2, startZ = z - cell / 2.0F + step / 2;
		for (int j = 0; j < FAR_POINTS; j++) {
			for (int i = 0; i < FAR_POINTS; i++) {
				if (!this.footprints.sample((int) Math.floor(start + i * step), (int) Math.floor(startZ + j * step), b)) {
					continue;
				}
				hits++;
				tallest = Math.max(tallest, b[2] >> 16);
				r += b[1] >> 16 & 255;
				g += b[1] >> 8 & 255;
				bl += b[1] & 255;
			}
		}
		if (hits == 0) {
			return;
		}
		float share = hits / (float) (FAR_POINTS * FAR_POINTS);
		int roof = (int) (r / hits) << 16 | (int) (g / hits) << 8 | (int) (bl / hits);
		out.top = Colors.mix(out.top, roof, Math.min(1, share * 1.5F));
		out.cover *= 1 - share;
		out.light = Math.max(out.light, Math.min(13, 4 + 30 * share));
		int ground = (int) Math.ceil(out.ground);
		if (share >= 0.3F && out.water == Integer.MIN_VALUE && tallest > ground) {
			out.ground = ground + (tallest - ground) * Math.min(1, share * 1.5F);
		}
	}

	/** How many blocks in from the footprint's edge column (x, z) is, up to {@code reach}: the nearest column outside it. */
	private int inward(int x, int z, int reach) {
		int[] b = this.probe.get();
		for (int d = 1; d <= reach; d++) {
			if (!this.footprints.sample(x + d, z, b) || !this.footprints.sample(x - d, z, b) || !this.footprints.sample(x, z + d, b)
				|| !this.footprints.sample(x, z - d, b)) {
				return d - 1;
			}
		}
		return reach;
	}
}
