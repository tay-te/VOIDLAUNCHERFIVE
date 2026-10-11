package dev.voidmc.gloam.bridge;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * A square window of cells round a centre that moves one cell at a time, for a wrapped tile: the window is
 * {@code centre - (size/2 - 1) .. centre + size/2} on both axes ({@code -127 .. +128} for a whole tile, as
 * {@link LandTiles} has it, {@code -63 .. +64} for U's 128 x 128), and a step brings in exactly one column or row, the
 * one that falls on the texels the leaving one held. Pure; the cells come from a {@link Cells} function.
 */
final class RollingWindow {
	/** Works out one cell: tile A's texel in the low 32 bits, tile B's in the high 32. */
	@FunctionalInterface
	interface Cells {
		long cell(int cx, int cz);
	}

	final int size, below, above;
	final int tileA, tileB;
	private boolean have;
	private int centreX, centreZ;

	/**
	 * @param size  cells along a side: 256, or 128 for U
	 * @param tileA the tile the low texel goes into
	 * @param tileB the tile the high texel goes into, or -1
	 */
	RollingWindow(int size, int tileA, int tileB) {
		this.size = size;
		this.below = size / 2 - 1;
		this.above = size / 2;
		this.tileA = tileA;
		this.tileB = tileB;
	}

	boolean have() {
		return this.have;
	}

	int centreX() {
		return this.centreX;
	}

	int centreZ() {
		return this.centreZ;
	}

	void forget() {
		this.have = false;
	}

	/** Whether cell (cx, cz) is inside the window. */
	boolean contains(int cx, int cz) {
		return this.have && cx >= this.centreX - this.below && cx <= this.centreX + this.above && cz >= this.centreZ - this.below
			&& cz <= this.centreZ + this.above;
	}

	/** How far (cells, Chebyshev) the target is from the centre; {@link Integer#MAX_VALUE} without a window. */
	int distance(int tx, int tz) {
		return this.have ? Math.max(Math.abs(tx - this.centreX), Math.abs(tz - this.centreZ)) : Integer.MAX_VALUE;
	}

	/**
	 * Centres the window on (cx, cz) afresh: returns the window's rows (world cell z), nearest the centre first. The
	 * caller writes a clear, then those rows.
	 */
	List<Integer> restart(int cx, int cz) {
		this.have = true;
		this.centreX = cx;
		this.centreZ = cz;
		List<Integer> rows = new ArrayList<>(this.size);
		for (int z = cz - this.below; z <= cz + this.above; z++) {
			rows.add(z);
		}
		rows.sort(Comparator.comparingInt(z -> Math.abs(z - cz)));
		return rows;
	}

	/**
	 * Moves the centre one cell toward (tx, tz), x first: returns the line that came in as {@code column ? x : z} with
	 * {@link #lastColumn} telling which, or {@link Integer#MIN_VALUE} when the centre is already there.
	 */
	int step(int tx, int tz) {
		if (tx != this.centreX) {
			int dx = Integer.signum(tx - this.centreX);
			this.centreX += dx;
			this.lastColumn = true;
			return dx > 0 ? this.centreX + this.above : this.centreX - this.below;
		}
		if (tz != this.centreZ) {
			int dz = Integer.signum(tz - this.centreZ);
			this.centreZ += dz;
			this.lastColumn = false;
			return dz > 0 ? this.centreZ + this.above : this.centreZ - this.below;
		}
		return Integer.MIN_VALUE;
	}

	/** Whether the last {@link #step} brought in a column. */
	boolean lastColumn;

	/** Row {@code z} of the window, worked out by {@code cells}. */
	TileStrip row(int z, Cells cells, int base) {
		return this.line(false, z, cells, base);
	}

	/** Column {@code x} of the window, worked out by {@code cells}. */
	TileStrip column(int x, Cells cells, int base) {
		return this.line(true, x, cells, base);
	}

	TileStrip line(boolean column, int at, Cells cells, int base) {
		int from = (column ? this.centreZ : this.centreX) - this.below;
		int[] a = new int[this.size];
		int[] b = this.tileB >= 0 ? new int[this.size] : null;
		for (int i = 0; i < this.size; i++) {
			long c = column ? cells.cell(at, from + i) : cells.cell(from + i, at);
			a[i] = (int) c;
			if (b != null) {
				b[i] = (int) (c >>> 32);
			}
		}
		return new TileStrip(this.tileA, this.tileB, false, column, at, from, a, b, this.centreX, this.centreZ, base);
	}

	/** A clear of the window's tiles, carrying its centre. */
	TileStrip clear(int base) {
		return TileStrip.clear(this.tileA, this.tileB, this.centreX, this.centreZ, base);
	}
}
