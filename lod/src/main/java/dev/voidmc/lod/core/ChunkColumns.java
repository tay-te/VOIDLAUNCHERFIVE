package dev.voidmc.lod.core;

import java.util.Arrays;

/**
 * What the LOD keeps of one loaded chunk: its 256 columns, each packed into an int, and the few kinds of
 * {@link Surface} they are made of. Immutable once made, so worker threads read it while the client thread puts
 * a newer one in its place.
 *
 * <p>A column packs, low bits first: the ground's height (12 bits, biased, 0 for a column with nothing in it at
 * all), the water standing on it (8 bits, blocks deep, 0 for none), the leaves over it (6 bits, how far their top
 * stands above the ground, 0 for none), and its surface (6 bits, an index into the palette). About 1.1 KB a
 * chunk in memory, whatever is built on it.
 */
public final class ChunkColumns {
	/** The packed column with nothing in it from the build limit down: the void under a sky island. */
	public static final int VOID = 0;
	public static final int MAX_DEPTH = 255;
	public static final int MAX_CANOPY = 63;
	public static final int MAX_PALETTE = 64;
	private static final int BIAS = 2048;

	final int[] columns;
	final Surface[] palette;
	/** The whole chunk as a statistic: the 16-block cell of the far field, and what the regions' cells sum. */
	final int[] summary;

	/** {@code columns} are 256 packed columns, x fastest; the array is the chunk's own from here on. */
	public ChunkColumns(int[] columns, Surface[] palette) {
		this.columns = columns;
		this.palette = palette;
		ColumnSummary sum = new ColumnSummary();
		for (int packed : columns) {
			sum.add(packed, packed == VOID ? null : palette[look(packed)], false);
		}
		this.summary = sum.freeze();
	}

	/**
	 * A column: the ground's height (solid below this y), how deep the water on it is, how far the top of the
	 * leaves over it stands above the ground, and which of the chunk's surfaces it is made of.
	 */
	public static int pack(int ground, int depth, int canopy, int look) {
		int g = Math.max(1 - BIAS, Math.min(BIAS - 1, ground)) + BIAS;
		return g | Math.max(0, Math.min(MAX_DEPTH, depth)) << 12 | Math.max(0, Math.min(MAX_CANOPY, canopy)) << 20 | look << 26;
	}

	public static int ground(int packed) {
		return (packed & 0xFFF) - BIAS;
	}

	public static int depth(int packed) {
		return (packed >>> 12) & 0xFF;
	}

	public static int canopy(int packed) {
		return (packed >>> 20) & 0x3F;
	}

	public static int look(int packed) {
		return packed >>> 26;
	}

	/** The packed column at {@code (x, z)} within the chunk. */
	public int column(int x, int z) {
		return this.columns[x + z * 16];
	}

	public Surface surface(int packed) {
		return this.palette[look(packed)];
	}

	public int[] columns() {
		return this.columns;
	}

	public Surface[] palette() {
		return this.palette;
	}

	/**
	 * Where this differs from {@code old} in anything a far view shows: the bounds of the columns that changed,
	 * inclusive, packed {@code x0 | z0 << 4 | x1 << 8 | z1 << 12}, and above that ({@code << 16}) how many columns
	 * changed. -1 if none did: only the soil or rock under some columns changed (grass dying under a block placed on
	 * it), which shows a block at a time in a wall at most.
	 */
	public long changedSince(ChunkColumns old) {
		int x0 = 16, z0 = 16, x1 = -1, z1 = -1, n = 0;
		for (int z = 0; z < 16; z++) {
			for (int x = 0; x < 16; x++) {
				int a = this.columns[x + z * 16];
				int b = old.columns[x + z * 16];
				// height, water and leaves first, then what the surfaces look like from above
				if ((a & 0x3FFFFFF) == (b & 0x3FFFFFF) && (a == VOID || seenAlike(this.palette[look(a)], old.palette[look(b)]))) {
					continue;
				}
				n++;
				x0 = Math.min(x0, x);
				z0 = Math.min(z0, z);
				x1 = Math.max(x1, x);
				z1 = Math.max(z1, z);
			}
		}
		return x1 < 0 ? -1 : x0 | z0 << 4 | x1 << 8 | z1 << 12 | (long) n << 16;
	}

	/** Whether two surfaces look the same from afar: their tops and sides, their leaves and their water, not what is under them. */
	static boolean seenAlike(Surface a, Surface b) {
		return a == b || a.top() == b.top() && a.side() == b.side() && a.leaves() == b.leaves() && a.waterColor() == b.waterColor()
			&& a.lava() == b.lava() && a.conifer() == b.conifer() && a.topMaterial() == b.topMaterial() && a.sideMaterial() == b.sideMaterial()
			&& a.leavesMaterial() == b.leavesMaterial();
	}

	@Override
	public boolean equals(Object o) {
		return o instanceof ChunkColumns c && Arrays.equals(this.columns, c.columns) && Arrays.equals(this.palette, c.palette);
	}

	@Override
	public int hashCode() {
		return Arrays.hashCode(this.columns);
	}
}
