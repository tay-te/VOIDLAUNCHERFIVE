package dev.voidmc.lod.core;

/**
 * The terrain the world really has where the client has seen it, and the generator's everywhere else: answers
 * from the chunks ingested into a {@link ColumnStore} (builds, dug-out land, villages, everything changed since the
 * world was generated) and falls back to a generator source where nothing has been seen. On a server, where there
 * is no generator, the unseen land is simply empty.
 *
 * <p>A cell of {@code c} blocks stands for the square {@code c} wide centred on its sample (corners for the smooth
 * tiles, centres for the blocky ones), which is always four aligned squares half as wide: a column or a few added
 * up, a chunk's own summary, or a cell of the store's pyramid. So a sample costs a handful of lookups at any level,
 * and every tile and every level agrees on what a square of land averages to. Where only part of a cell has been
 * seen, the seen part and the generator's answer blend by how much of the cell each covers, so the edge of the
 * explored land fades into the generated land instead of stepping.
 */
public final class IngestSource implements TerrainSource {
	/** Cells this wide or narrower are drawn as the chunks look up close: a crown is leaf blocks, not a canopy statistic. */
	static final int EXACT = 2;

	private final ColumnStore store;
	private final TerrainSource fallback;
	private final ThreadLocal<Reader> readers = ThreadLocal.withInitial(Reader::new);

	/** Per worker: the accumulator, a sample for the fallback, and the last region looked at. */
	private static final class Reader {
		final ColumnSummary sum = new ColumnSummary();
		final ColumnSample other = new ColumnSample();
		long key = Long.MIN_VALUE;
		ColumnStore.Region region;
	}

	/** @param fallback the generator's terrain for land nobody has seen, or null for none (a server's world) */
	public IngestSource(ColumnStore store, TerrainSource fallback) {
		this.store = store;
		this.fallback = fallback;
	}

	public ColumnStore store() {
		return this.store;
	}

	public TerrainSource fallback() {
		return this.fallback;
	}

	@Override
	public long version() {
		return this.fallback != null ? this.fallback.version() : 0;
	}

	@Override
	public boolean covers(int minX, int minZ, int maxX, int maxZ) {
		return this.fallback != null || this.store.any(minX, minZ, maxX, maxZ);
	}

	@Override
	public void sample(int x, int z, int cell, ColumnSample out) {
		Reader r = this.readers.get();
		ColumnSummary sum = r.sum;
		sum.clear();
		int total;
		if (cell <= 1) {
			this.add(r, x, z, 1, true);
			total = 1;
		} else {
			int h = cell >> 1;
			boolean exact = cell <= EXACT;
			this.add(r, x - h, z - h, h, exact);
			this.add(r, x, z - h, h, exact);
			this.add(r, x - h, z, h, exact);
			this.add(r, x, z, h, exact);
			total = cell * cell;
		}
		int known = sum.known();
		int none = sum.none();
		if (known + none == 0) {
			if (this.fallback != null) {
				this.fallback.sample(x, z, cell, out);
			} else {
				out.empty = true;
			}
			return;
		}
		if (none > known) {
			// mostly the void: a sky island's edge, a hole to the bottom of the world
			out.empty = true;
			return;
		}
		sum.write(out);
		if (known + none < total && this.fallback != null) {
			ColumnSample other = r.other;
			other.clear();
			this.fallback.sample(x, z, cell, other);
			blend(out, other, (known + none) / (float) total);
		}
	}

	/** Adds the {@code size}-block square at {@code (x, z)} (aligned to its size) to the reader's sum. */
	private void add(Reader r, int x, int z, int size, boolean exact) {
		int rx = x >> (ColumnStore.REGION_SHIFT + 4);
		int rz = z >> (ColumnStore.REGION_SHIFT + 4);
		long key = ColumnStore.key(rx, rz);
		if (key != r.key) {
			r.key = key;
			r.region = this.store.region(rx, rz);
		}
		ColumnStore.Region region = r.region;
		if (region == null) {
			return;
		}
		if (size >= 16) {
			int[] summary = ColumnStore.summary(region, x, z, size);
			if (summary != null) {
				r.sum.merge(summary);
			}
			return;
		}
		ChunkColumns c = region.chunks.get(ColumnStore.index(x >> 4, z >> 4));
		if (c == null) {
			return;
		}
		int x0 = x & 15;
		int z0 = z & 15;
		for (int j = 0; j < size; j++) {
			for (int i = 0; i < size; i++) {
				int at = x0 + i + (z0 + j) * 16;
				int packed = c.columns[at];
				r.sum.add(packed, packed == ChunkColumns.VOID ? null : c.palette[ChunkColumns.look(packed)], exact, c.light[at]);
			}
		}
	}

	/**
	 * Blends the generator's answer into a cell only {@code seen} of which the chunks have shown: heights and colours
	 * by share, and what kind of cell it is (land or water, its materials, its strata) by whichever covers more.
	 */
	static void blend(ColumnSample out, ColumnSample generated, float seen) {
		boolean wet = out.water != Integer.MIN_VALUE && out.water > out.ground;
		boolean otherWet = generated.water != Integer.MIN_VALUE && generated.water > generated.ground;
		if (wet == otherWet) {
			out.ground = generated.ground + (out.ground - generated.ground) * seen;
			if (wet) {
				out.water = Math.round(generated.water + (out.water - generated.water) * seen);
				out.waterColor = Colors.mix(generated.waterColor, out.waterColor, seen);
			}
		} else if (seen < 0.5F) {
			out.ground = generated.ground;
			out.water = generated.water;
			out.waterColor = generated.waterColor;
			out.lava = generated.lava;
		}
		if (seen < 0.5F) {
			out.bands = generated.bands;
			out.bandHeight = generated.bandHeight;
			out.bandOffset = generated.bandOffset;
			out.soilDepth = generated.soilDepth;
			out.topMaterial = generated.topMaterial;
			out.sideMaterial = generated.sideMaterial;
			out.soilMaterial = generated.soilMaterial;
			out.rockMaterial = generated.rockMaterial;
			out.leavesMaterial = generated.leavesMaterial;
		}
		// the generator knows of no light: the seen share of the cell carries what the chunks showed
		out.light = generated.light + (out.light - generated.light) * seen;
		out.top = Colors.mix(generated.top, out.top, seen);
		out.side = Colors.mix(generated.side, out.side, seen);
		out.soil = Colors.mix(generated.soil, out.soil, seen);
		out.rock = Colors.mix(generated.rock, out.rock, seen);
		// trees: how tall and what kind from both where both have them, the share of ground they cover by share
		if (out.cover > 0 && generated.cover > 0) {
			out.canopy = generated.canopy + (out.canopy - generated.canopy) * seen;
			out.leaves = Colors.mix(generated.leaves, out.leaves, seen);
			out.conifer = generated.conifer + (out.conifer - generated.conifer) * seen;
			out.crown = generated.crown + (out.crown - generated.crown) * seen;
		} else if (generated.cover > 0) {
			out.canopy = generated.canopy;
			out.leaves = generated.leaves;
			out.conifer = generated.conifer;
			out.crown = generated.crown;
			out.leavesMaterial = generated.leavesMaterial;
		}
		out.cover = generated.cover + (out.cover - generated.cover) * seen;
	}
}
