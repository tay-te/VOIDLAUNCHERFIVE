package dev.voidmc.hearth.plan.grid;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

/**
 * Which block columns of a settlement are taken, and by what ({@link Cell} codes): a sparse map of 16×16 byte tiles,
 * about 256 bytes per tile touched, so a city of 500 × 500 blocks costs a quarter of a megabyte. The planner rebuilds it
 * from the settlement's plots and its own reservations rather than saving it, so it can never disagree with them.
 */
public final class Occupancy {
	private final Long2ObjectOpenHashMap<byte[]> tiles = new Long2ObjectOpenHashMap<>();
	private long lastKey = Long.MIN_VALUE;
	private byte[] last;

	public byte get(int x, int z) {
		byte[] t = tile(x, z, false);
		return t == null ? Cell.FREE : t[((z & 15) << 4) | (x & 15)];
	}

	/** Sets a column unconditionally. */
	public void set(int x, int z, byte code) {
		byte[] t = tile(x, z, code != Cell.FREE);
		if (t != null) {
			t[((z & 15) << 4) | (x & 15)] = code;
		}
	}

	/** Sets a column only if the code it holds ranks lower (see {@link Cell#rank}); returns whether it did. */
	public boolean mark(int x, int z, byte code) {
		byte old = get(x, z);
		if (Cell.rank(old) < Cell.rank(code) || old == Cell.FREE) {
			set(x, z, code);
			return true;
		}
		return false;
	}

	/** Marks a rectangle, inclusive bounds. */
	public void markRect(int x0, int z0, int x1, int z1, byte code) {
		for (int z = z0; z <= z1; z++) {
			for (int x = x0; x <= x1; x++) {
				mark(x, z, code);
			}
		}
	}

	/** Clears every column of a rectangle that holds {@code code}. */
	public void clearRect(int x0, int z0, int x1, int z1, byte code) {
		for (int z = z0; z <= z1; z++) {
			for (int x = x0; x <= x1; x++) {
				if (get(x, z) == code) {
					set(x, z, Cell.FREE);
				}
			}
		}
	}

	/** Whether every column of the rectangle (inclusive) is free. */
	public boolean isFree(int x0, int z0, int x1, int z1) {
		for (int z = z0; z <= z1; z++) {
			for (int x = x0; x <= x1; x++) {
				if (get(x, z) != Cell.FREE) {
					return false;
				}
			}
		}
		return true;
	}

	/** Whether any column of the rectangle (inclusive) holds {@code code}. */
	public boolean any(int x0, int z0, int x1, int z1, byte code) {
		for (int z = z0; z <= z1; z++) {
			for (int x = x0; x <= x1; x++) {
				if (get(x, z) == code) {
					return true;
				}
			}
		}
		return false;
	}

	public void clear() {
		this.tiles.clear();
		this.lastKey = Long.MIN_VALUE;
		this.last = null;
	}

	public int tileCount() {
		return this.tiles.size();
	}

	private byte[] tile(int x, int z, boolean create) {
		long key = ((long) (x >> 4) << 32) ^ ((z >> 4) & 0xFFFFFFFFL);
		if (key == this.lastKey && this.last != null) {
			return this.last;
		}
		byte[] t = this.tiles.get(key);
		if (t == null) {
			if (!create) {
				return null;
			}
			t = new byte[256];
			this.tiles.put(key, t);
		}
		this.lastKey = key;
		this.last = t;
		return t;
	}
}
