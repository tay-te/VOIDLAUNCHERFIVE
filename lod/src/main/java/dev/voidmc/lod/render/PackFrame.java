package dev.voidmc.lod.render;

import java.util.Arrays;

/**
 * This frame's tiles for a shader pack's Distant Horizons programs, as plain GL handles and counts: per tile the vertex
 * buffer it lives in, where its vertices start, its land and its water as index ranges, and where it is relative to the
 * camera. Filled by {@link LodRenderer} while it prepares the frame, drawn by the pack renderer (compat.iris), which is
 * the only part that knows Iris.
 */
public final class PackFrame {
	public int count;
	public int[] heap = new int[256];
	public int[] baseVertex = new int[256];
	public int[] landIndices = new int[256];
	/**
	 * Per tile, the runs of its land that can face the camera (up to three: first index and count each, in
	 * {@link #landRuns}) and how many there are; the shadow pass draws the whole land, {@link #landIndices}.
	 */
	public int[] landRuns = new int[256 * 6];
	public int[] runs = new int[256];
	public int[] waterFirst = new int[256];
	public int[] waterIndices = new int[256];
	/** x, y, z per tile: its corner (y: the vertical offset its vertices carry) relative to the camera, in blocks. */
	public float[] offset = new float[256 * 3];
	/** How close each tile comes to the camera across the ground, in blocks: the shadow pass leaves out the tiles past its map. */
	public float[] near = new float[256];
	/** The shared quad index buffer: its GL handle, the GL type of its indices and their size in bytes. */
	public int indexBuffer;
	public int indexType;
	public int indexBytes;
	/** How far vanilla's chunks reach: the pack's programs fade the LOD in toward it. */
	public float clip;

	/** Replaces the last tile's land with only the runs given next (call before the first {@link #addRun}). */
	void clearRuns() {
		this.runs[this.count - 1] = 0;
	}

	void addRun(int first, int count) {
		int i = this.count - 1;
		int r = this.runs[i];
		if (r == 3) {
			// a fourth run: merge it into the third, drawing what lies between
			int end = first + count;
			this.landRuns[i * 6 + 5] = end - this.landRuns[i * 6 + 4];
			return;
		}
		this.landRuns[i * 6 + r * 2] = first;
		this.landRuns[i * 6 + r * 2 + 1] = count;
		this.runs[i] = r + 1;
	}

	void clear() {
		this.count = 0;
	}

	void add(int heap, int baseVertex, int landIndices, int waterFirst, int waterIndices, double x, double y, double z, double near) {
		if (this.count == this.heap.length) {
			int n = this.count * 2;
			this.heap = Arrays.copyOf(this.heap, n);
			this.baseVertex = Arrays.copyOf(this.baseVertex, n);
			this.landIndices = Arrays.copyOf(this.landIndices, n);
			this.landRuns = Arrays.copyOf(this.landRuns, n * 6);
			this.runs = Arrays.copyOf(this.runs, n);
			this.waterFirst = Arrays.copyOf(this.waterFirst, n);
			this.waterIndices = Arrays.copyOf(this.waterIndices, n);
			this.offset = Arrays.copyOf(this.offset, n * 3);
			this.near = Arrays.copyOf(this.near, n);
		}
		int i = this.count++;
		this.heap[i] = heap;
		this.baseVertex[i] = baseVertex;
		this.landIndices[i] = landIndices;
		// the whole land unless runs are given (addRun)
		this.runs[i] = 1;
		this.landRuns[i * 6] = 0;
		this.landRuns[i * 6 + 1] = landIndices;
		this.waterFirst[i] = waterFirst;
		this.waterIndices[i] = waterIndices;
		this.offset[i * 3] = (float) x;
		this.offset[i * 3 + 1] = (float) y;
		this.offset[i * 3 + 2] = (float) z;
		this.near[i] = (float) near;
	}
}
