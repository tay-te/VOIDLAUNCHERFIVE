package dev.voidmc.atlas.core.under;

import dev.voidmc.atlas.core.detect.BlockSource;
import dev.voidmc.atlas.core.detect.Sig;
import java.util.Arrays;

/**
 * The underground map's honesty: what the player has <em>been near and could see</em>, never what is merely loaded.
 *
 * <p>The model is a <b>connected-air reveal</b>: from the player's feet and head, a breadth-first walk through open
 * blocks (air, and water, which can be seen through) inside a sphere of {@link #RADIUS} blocks ({@link #NETHER_RADIUS}
 * in the Nether's big caverns). Every open block reached is revealed, and every solid block that bounds one is
 * revealed as wall. A cave behind a wall, however close, is never reached and stays hidden; a cave round a corner of
 * a passage the player stands in is revealed, as a torch-lit look round would. Lava is seen (its surface bounds the
 * walk) but not walked through. Run as the player moves underground, on a worker, a few milliseconds each.
 *
 * <p>The walk's result is per column and per {@value #BAND}-block band: the highest open block seen in that band (the slice's
 * view starts there and looks down to the floor), or that only wall was seen.
 */
public final class Reveal {
	public static final int RADIUS = 24;
	public static final int NETHER_RADIUS = 32;
	/** At most this many open blocks are walked (a huge cavern is revealed in part, from where the player stands). */
	public static final int MAX_OPEN = 60_000;
	public static final int BAND = 8;
	/** In {@link Result#top}: nothing open seen in that band of that column. */
	public static final short NOTHING = Short.MIN_VALUE;

	/**
	 * The walk's result: for each column of the square round the player ({@code size} on a side, {@code x0, z0} its
	 * north-west corner) and each band from {@code band0} ({@code bands} of them): the highest open block seen, or
	 * {@link #NOTHING}; and whether wall was seen there.
	 */
	public static final class Result {
		public final int x0;
		public final int z0;
		public final int size;
		public final int band0;
		public final int bands;
		public final short[] top;
		public final boolean[] wall;
		public int opened;

		Result(int x0, int z0, int size, int band0, int bands) {
			this.x0 = x0;
			this.z0 = z0;
			this.size = size;
			this.band0 = band0;
			this.bands = bands;
			this.top = new short[size * size * bands];
			this.wall = new boolean[size * size * bands];
			Arrays.fill(this.top, NOTHING);
		}

		public int index(int dx, int dz, int b) {
			return (dz * this.size + dx) * this.bands + b;
		}

		/** The highest open block seen in band {@code band} at world column (x, z), or {@link #NOTHING}. */
		public int topAt(int x, int z, int band) {
			int dx = x - this.x0;
			int dz = z - this.z0;
			int b = band - this.band0;
			if (dx < 0 || dz < 0 || dx >= this.size || dz >= this.size || b < 0 || b >= this.bands) {
				return NOTHING;
			}
			return this.top[this.index(dx, dz, b)];
		}

		public boolean wallAt(int x, int z, int band) {
			int dx = x - this.x0;
			int dz = z - this.z0;
			int b = band - this.band0;
			if (dx < 0 || dz < 0 || dx >= this.size || dz >= this.size || b < 0 || b >= this.bands) {
				return false;
			}
			return this.wall[this.index(dx, dz, b)];
		}
	}

	private int[] stamp = new int[0];
	private int generation;
	private int[] queue = new int[4096];

	/** Walks the open blocks round (px, py, pz). Not thread-safe: one {@code Reveal} per worker. */
	public Result walk(BlockSource w, int px, int py, int pz, int radius) {
		int side = radius * 2 + 1;
		int volume = side * side * side;
		if (this.stamp.length < volume) {
			this.stamp = new int[volume];
			this.generation = 0;
		}
		if (++this.generation == Integer.MAX_VALUE) {
			Arrays.fill(this.stamp, 0);
			this.generation = 1;
		}
		int gen = this.generation;
		int band0 = Math.floorDiv(py - radius, BAND);
		int band1 = Math.floorDiv(py + radius, BAND);
		Result r = new Result(px - radius, pz - radius, side, band0, band1 - band0 + 1);
		int r2 = radius * radius;
		int head = 0;
		int tail = 0;
		for (int dy = 0; dy <= 1; dy++) {
			int code = w.code(px, py + dy, pz);
			if (passable(code)) {
				int cell = cell(radius, side, 0, dy, 0);
				if (this.stamp[cell] != gen) {
					this.stamp[cell] = gen;
					tail = this.push(tail, cell);
				}
			}
		}
		int[] q;
		while (head < tail && r.opened < MAX_OPEN) {
			q = this.queue;
			int cell = q[head++];
			int dx = cell / (side * side) - radius;
			int dz = (cell / side) % side - radius;
			int dy = cell % side - radius;
			int x = px + dx;
			int y = py + dy;
			int z = pz + dz;
			r.opened++;
			int b = Math.floorDiv(y, BAND) - band0;
			int i = r.index(dx + radius, dz + radius, b);
			if (y > r.top[i]) {
				r.top[i] = (short) y;
			}
			for (int d = 0; d < 6; d++) {
				int nx = dx + (d == 0 ? 1 : d == 1 ? -1 : 0);
				int ny = dy + (d == 2 ? 1 : d == 3 ? -1 : 0);
				int nz = dz + (d == 4 ? 1 : d == 5 ? -1 : 0);
				if (nx * nx + ny * ny + nz * nz > r2) {
					continue;
				}
				int n = cell(radius, side, nx, ny, nz);
				if (this.stamp[n] == gen) {
					continue;
				}
				this.stamp[n] = gen;
				int code = w.code(px + nx, py + ny, pz + nz);
				if (code < 0) {
					// not loaded: nothing is known there
					continue;
				}
				if (passable(code)) {
					tail = this.push(tail, n);
				} else {
					// the wall (or the lava, or the floor) that bounds what is open
					int nb = Math.floorDiv(py + ny, BAND) - band0;
					r.wall[r.index(nx + radius, nz + radius, nb)] = true;
				}
			}
		}
		return r;
	}

	private static int cell(int radius, int side, int dx, int dy, int dz) {
		return ((dx + radius) * side + (dz + radius)) * side + (dy + radius);
	}

	private int push(int tail, int cell) {
		if (tail == this.queue.length) {
			this.queue = Arrays.copyOf(this.queue, this.queue.length * 2);
		}
		this.queue[tail] = cell;
		return tail + 1;
	}

	/** What the walk goes through: open space and water (which can be seen through). */
	static boolean passable(int code) {
		return code >= 0 && ((code & 3) == Sig.OPEN || (code & 3) == Sig.WATER);
	}
}
