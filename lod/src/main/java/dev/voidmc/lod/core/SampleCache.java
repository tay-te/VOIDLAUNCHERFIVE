package dev.voidmc.lod.core;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;

/**
 * The edges of recently sampled tiles, so a tile at the same level next to one of them copies the columns they
 * share instead of asking the terrain source again. Neighbouring tiles sample the same world positions along
 * their common edge, and each one's border is the other's inside: a smooth tile shares five of its 69 columns
 * with each neighbour, a blocky tile two of its 66. Sampling is nearly all of a build, and the source is asked
 * about exactly the same position with the same cell size, so the copy is exactly what it would have answered.
 *
 * <p>Only the same level: a parent's corners stand where every other corner of its children does, but it
 * sampled them with twice the cell size, and sources (and the natural variation) leave out the detail under a
 * cell, so its answer there is not the child's.
 *
 * <p>What is kept is strips: each edge of a tile, as deep as its neighbour on that side shares. An edge facing a
 * neighbour the tile could copy from is not kept (that neighbour is built), and an edge is let go once the
 * neighbour it faces has taken it, so the space goes to the edges still waiting for their neighbour. Shared by
 * the workers of one engine behind one lock, held only while copying: tens of microseconds, against a build of
 * tens of milliseconds. Strips are recycled oldest first, so a full cache allocates nothing. The builders sharing
 * it must agree on natural variation, which the copies include.
 */
public final class SampleCache {
	/** Edges remembered, each about 30 KB for a smooth tile: 11 MB in all. */
	static final int STRIPS = 384;
	/** A tile's edges: west (low x), east, north (low z) and south. */
	private static final int WEST = 0, EAST = 1, NORTH = 2, SOUTH = 3;

	private static final class Strip {
		final Samples samples;
		boolean live;
		long key;
		int side;
		TerrainSource source;
		long version;
		boolean corners;
		/** The tile's samples per row and border, and how many columns (or rows) of its edge are kept. */
		int size;
		int border;
		int width;

		Strip(int capacity) {
			this.samples = new Samples(false, capacity);
		}

		/** Where sample ({@code i}, {@code j}) of the tile is kept in this strip. */
		int index(int i, int j) {
			int far = this.size - this.border - this.width;
			return switch (this.side) {
				case WEST -> (i + this.border) + (j + this.border) * this.width;
				case EAST -> (i - far) + (j + this.border) * this.width;
				case NORTH -> (i + this.border) + (j + this.border) * this.size;
				default -> (i + this.border) + (j - far) * this.size;
			};
		}
	}

	/** Made as they are first needed, so a cache that is never used costs nothing. */
	private final Strip[] strips = new Strip[STRIPS];
	/** Per side, the strip holding each tile's edge on that side. */
	private final Long2IntOpenHashMap[] slots = new Long2IntOpenHashMap[4];
	private int next;

	public SampleCache() {
		for (int s = 0; s < 4; s++) {
			this.slots[s] = new Long2IntOpenHashMap();
			this.slots[s].defaultReturnValue(-1);
		}
	}

	/**
	 * Copies into {@code samples} (laid out for tile {@code key}, not yet sampled) every sample the remembered
	 * edges of its neighbours share with it, marking them known. Returns the sides of the tile whose neighbour
	 * was there to copy from, as bits.
	 */
	synchronized int fill(Samples samples, TerrainSource source, long key, boolean corners) {
		int level = TileKey.level(key);
		int tx = TileKey.tx(key);
		int tz = TileKey.tz(key);
		int b = samples.border;
		int last = samples.size - b - 1;
		int reused = 0;
		int found = 0;
		for (int dz = -1; dz <= 1; dz++) {
			for (int dx = -1; dx <= 1; dx++) {
				if (dx == 0 && dz == 0) {
					continue;
				}
				long near = TileKey.of(level, tx + dx, tz + dz);
				// the neighbour's edge facing this tile; for a corner, its west or east edge, else its north or south one
				Strip strip = this.strip(near, dx > 0 ? WEST : dx < 0 ? EAST : dz > 0 ? NORTH : SOUTH, source, corners, samples);
				if (strip == null && dx != 0 && dz != 0) {
					strip = this.strip(near, dz > 0 ? NORTH : SOUTH, source, corners, samples);
				}
				if (strip == null) {
					continue;
				}
				// the shared columns, in this tile's coordinates: all of them along the neighbour's side, or its corner
				int i0 = dx > 0 ? TileKey.TILE - b : -b;
				int i1 = dx < 0 ? last - TileKey.TILE : last;
				int j0 = dz > 0 ? TileKey.TILE - b : -b;
				int j1 = dz < 0 ? last - TileKey.TILE : last;
				for (int j = j0; j <= j1; j++) {
					for (int i = i0; i <= i1; i++) {
						if (samples.reuse(samples.at(i, j), strip.samples, strip.index(i - dx * TileKey.TILE, j - dz * TileKey.TILE))) {
							reused++;
						}
					}
				}
				if (dx == 0 || dz == 0) {
					// taken by the one tile it faces: let it go
					found |= 1 << (dx > 0 ? EAST : dx < 0 ? WEST : dz > 0 ? SOUTH : NORTH);
					this.drop(strip);
				}
			}
		}
		TileBuilder.SAMPLES_REUSED.addAndGet(reused);
		return found;
	}

	/**
	 * Remembers the edges of tile {@code key}, just sampled into {@code samples}, for its neighbours: those on the
	 * sides not in {@code found}, whose neighbours are already built.
	 */
	synchronized void store(Samples samples, TerrainSource source, long key, boolean corners, int found) {
		int b = samples.border;
		int width = samples.size - TileKey.TILE;
		int last = samples.size - b - 1;
		int far = last - width + 1;
		for (int side = 0; side < 4; side++) {
			if ((found & 1 << side) != 0) {
				continue;
			}
			int slot = this.slots[side].get(key);
			if (slot < 0) {
				slot = this.next;
				this.next = (this.next + 1) % STRIPS;
				if (this.strips[slot] == null) {
					int size = TileKey.TILE + 1 + 2 * Samples.MAX_BORDER;
					this.strips[slot] = new Strip((size - TileKey.TILE) * size);
				}
				this.drop(this.strips[slot]);
				this.slots[side].put(key, slot);
			}
			Strip s = this.strips[slot];
			s.live = true;
			s.key = key;
			s.side = side;
			s.source = source;
			s.version = source.version();
			s.corners = corners;
			s.size = samples.size;
			s.border = b;
			s.width = width;
			int i0 = side == EAST ? far : -b;
			int i1 = side == WEST ? -b + width - 1 : last;
			int j0 = side == SOUTH ? far : -b;
			int j1 = side == NORTH ? -b + width - 1 : last;
			for (int j = j0; j <= j1; j++) {
				for (int i = i0; i <= i1; i++) {
					s.samples.copy(s.index(i, j), samples, samples.at(i, j));
				}
			}
		}
	}

	/** Forgets every edge whose samples reach into the given block area, where the source's answer has changed. */
	synchronized void forget(int minX, int minZ, int maxX, int maxZ) {
		for (Strip s : this.strips) {
			if (s == null || !s.live) {
				continue;
			}
			int level = TileKey.level(s.key);
			int reach = s.border * TileKey.cell(level);
			int x0 = TileKey.minX(s.key) - reach;
			int z0 = TileKey.minZ(s.key) - reach;
			int x1 = TileKey.minX(s.key) + TileKey.span(level) + reach;
			int z1 = TileKey.minZ(s.key) + TileKey.span(level) + reach;
			if (x0 <= maxX && minX <= x1 && z0 <= maxZ && minZ <= z1) {
				this.drop(s);
			}
		}
	}

	synchronized void clear() {
		for (Strip s : this.strips) {
			if (s != null) {
				this.drop(s);
			}
		}
	}

	private Strip strip(long key, int side, TerrainSource source, boolean corners, Samples samples) {
		int slot = this.slots[side].get(key);
		if (slot < 0) {
			return null;
		}
		Strip s = this.strips[slot];
		boolean same = s.source == source && s.version == source.version() && s.corners == corners && s.size == samples.size
			&& s.border == samples.border;
		return same ? s : null;
	}

	private void drop(Strip s) {
		if (s.live) {
			this.slots[s.side].remove(s.key);
			s.live = false;
			s.source = null;
		}
	}
}
