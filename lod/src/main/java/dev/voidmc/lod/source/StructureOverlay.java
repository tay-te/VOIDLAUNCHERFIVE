package dev.voidmc.lod.source;

import dev.voidmc.lod.core.ColumnSample;
import dev.voidmc.lod.core.TerrainSource;
import java.util.ArrayList;
import java.util.List;

/**
 * The world's structures stood on the generator's land ({@link StructureSites}), for land nobody has loaded: up close
 * (cells of 4 blocks or less) a cell inside a building's box rises to its roof, walled and roofed in the structure's
 * colours and lit at night; farther out a cell takes on the share of it the buildings cover, a settlement becoming a
 * lit cluster on the horizon. Under the chunks the player has seen ({@code IngestSource}), which replace it as soon as
 * they load.
 */
public final class StructureOverlay implements TerrainSource {
	private final TerrainSource generator;
	private final StructureSites sites;
	private final ThreadLocal<List<StructureSites.Box>> found = ThreadLocal.withInitial(ArrayList::new);
	/** Per worker, the boxes reaching into the tile it is building, gathered once ({@link #beginTile}), and its bounds. */
	private final ThreadLocal<Tile> tiles = ThreadLocal.withInitial(Tile::new);

	private static final class Tile {
		final List<StructureSites.Box> boxes = new ArrayList<>();
		boolean active;
		int minX, minZ, maxX, maxZ;
		/**
		 * The boxes binned on a {@value #BINS} x {@value #BINS} grid over the tile, so a sample tests only the few near it:
		 * a far tile spans many regions, thousands of pieces. Bin {@code b}'s boxes are {@code binned[first[b]..first[b + 1])}.
		 */
		int binSize = 1;
		final int[] first = new int[BINS * BINS + 1];
		int[] binned = new int[64];
		/** Per box, the last sample that looked at it: a box in several bins a sample spans counts once. */
		int[] seen = new int[64];
		int sample;

		void bin() {
			int n = this.boxes.size();
			this.binSize = Math.max(1, Math.ceilDiv(Math.max(this.maxX - this.minX, this.maxZ - this.minZ), BINS));
			java.util.Arrays.fill(this.first, 0);
			if (this.seen.length < n) {
				this.seen = new int[Math.max(n, this.seen.length * 2)];
			}
			java.util.Arrays.fill(this.seen, 0, n, 0);
			this.sample = 0;
			// count per bin, then lay them out
			for (int pass = 0; pass < 2; pass++) {
				int total = 0;
				if (pass == 1) {
					for (int b = 0; b <= BINS * BINS; b++) {
						int c = this.first[b];
						this.first[b] = total;
						total += c;
					}
					if (this.binned.length < total) {
						this.binned = new int[Math.max(total, this.binned.length * 2)];
					}
				}
				int[] fill = pass == 1 ? this.first.clone() : null;
				for (int i = 0; i < n; i++) {
					StructureSites.Box b = this.boxes.get(i);
					int bx0 = this.bin(b.minX() - this.minX), bx1 = this.bin(b.maxX() - this.minX);
					int bz0 = this.bin(b.minZ() - this.minZ), bz1 = this.bin(b.maxZ() - this.minZ);
					for (int bz = bz0; bz <= bz1; bz++) {
						for (int bx = bx0; bx <= bx1; bx++) {
							int at = bx + bz * BINS;
							if (pass == 0) {
								this.first[at]++;
							} else {
								this.binned[fill[at]++] = i;
							}
						}
					}
				}
			}
		}

		int bin(int offset) {
			return Math.max(0, Math.min(BINS - 1, Math.floorDiv(offset, this.binSize)));
		}
	}

	private static final int BINS = 16;
	/**
	 * Pieces whose top is this far under the sea are never seen from the surface (mineshafts, strongholds, ancient cities):
	 * left out of a tile's boxes.
	 */
	private static final int BURIED = 32;

	public StructureOverlay(TerrainSource generator, StructureSites sites) {
		this.generator = generator;
		this.sites = sites;
	}

	public TerrainSource generator() {
		return this.generator;
	}

	public StructureSites sites() {
		return this.sites;
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
		// every sample's square lies in here: from half a cell before the first sample to half a cell past the last
		Tile t = this.tiles.get();
		int c = Math.max(1, cell);
		t.minX = x0 - border * c - c;
		t.minZ = z0 - border * c - c;
		t.maxX = x0 + (size - border) * c + c;
		t.maxZ = z0 + (size - border) * c + c;
		t.boxes.clear();
		this.gather(t.minX, t.minZ, t.maxX, t.maxZ, t.boxes);
		int buried = this.sites.seaLevel() - BURIED;
		t.boxes.removeIf(b -> b.maxY() < buried);
		t.bin();
		t.active = true;
	}

	@Override
	public void endTile() {
		this.generator.endTile();
		this.tiles.get().active = false;
	}

	/** The boxes reaching into the block square [x0, x1) x [z0, z1), into {@code out}. */
	private void gather(int x0, int z0, int x1, int z1, List<StructureSites.Box> out) {
		for (int rz = StructureSites.region(z0); rz <= StructureSites.region(z1 - 1); rz++) {
			for (int rx = StructureSites.region(x0); rx <= StructureSites.region(x1 - 1); rx++) {
				List<StructureSites.Box> region = this.sites.boxes(rx, rz);
				if (region == null) {
					continue;
				}
				for (StructureSites.Box b : region) {
					if (b.maxX() >= x0 && b.minX() < x1 && b.maxZ() >= z0 && b.minZ() < z1) {
						out.add(b);
					}
				}
			}
		}
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
		// the square this sample stands for
		int half = Math.max(1, cell) / 2;
		int x0 = x - half, z0 = z - half, x1 = x0 + Math.max(1, cell), z1 = z0 + Math.max(1, cell);
		List<StructureSites.Box> boxes = this.found.get();
		boxes.clear();
		Tile t = this.tiles.get();
		if (t.active && x0 >= t.minX && z0 >= t.minZ && x1 <= t.maxX && z1 <= t.maxZ) {
			// the tile's own boxes, gathered once: most tiles have none
			if (t.boxes.isEmpty()) {
				return;
			}
			int stamp = ++t.sample;
			int bx0 = t.bin(x0 - t.minX), bx1 = t.bin(x1 - 1 - t.minX), bz0 = t.bin(z0 - t.minZ), bz1 = t.bin(z1 - 1 - t.minZ);
			for (int bz = bz0; bz <= bz1; bz++) {
				for (int bx = bx0; bx <= bx1; bx++) {
					int at = bx + bz * BINS;
					for (int k = t.first[at], end = t.first[at + 1]; k < end; k++) {
						int i = t.binned[k];
						if (t.seen[i] == stamp) {
							continue;
						}
						t.seen[i] = stamp;
						StructureSites.Box b = t.boxes.get(i);
						if (b.maxX() >= x0 && b.minX() < x1 && b.maxZ() >= z0 && b.minZ() < z1) {
							boxes.add(b);
						}
					}
				}
			}
		} else {
			this.gather(x0, z0, x1, z1, boxes);
		}
		if (boxes.isEmpty()) {
			return;
		}
		int ground = (int) Math.ceil(out.ground);
		// under water a piece is hidden (ruins, shipwrecks): only what rises above the surface stands
		int floor = out.water != Integer.MIN_VALUE && out.water > ground ? out.water : ground;
		if (cell <= 4) {
			// the tallest building over this column
			StructureSites.Box top = null;
			for (StructureSites.Box b : boxes) {
				if (x >= b.minX() && x <= b.maxX() && z >= b.minZ() && z <= b.maxZ() && b.maxY() + 1 > floor + 1
					&& (top == null || b.maxY() > top.maxY())) {
					top = b;
				}
			}
			if (top == null) {
				return;
			}
			int look = top.look();
			// walls to about half the piece's height, then a roof rising a block for every block in from its edge: hipped
			// and gabled outlines, not crates
			int base = Math.max(top.minY(), ground);
			int wall = base + Math.max(3, (top.maxY() + 1 - base) / 2);
			int edge = Math.min(Math.min(x - top.minX(), top.maxX() - x), Math.min(z - top.minZ(), top.maxZ() - z));
			out.ground = Math.min(top.maxY() + 1, wall + edge);
			out.water = Integer.MIN_VALUE;
			out.top = StructureSites.Looks.ROOF[look];
			out.side = out.soil = out.rock = StructureSites.Looks.WALL[look];
			out.soilDepth = Math.max(1, (int) out.ground - Math.max(top.minY(), ground - 1));
			out.bands = null;
			out.topMaterial = out.sideMaterial = out.soilMaterial = out.rockMaterial = 0;
			out.canopy = 0;
			out.cover = 0;
			if (StructureSites.Looks.LIT[look]) {
				out.light = Math.max(out.light, 11);
			}
			return;
		}
		// farther out: the share of the square under buildings, standing as high as they do
		float area = (float) (x1 - x0) * (z1 - z0);
		float covered = 0;
		int tallest = ground;
		int look = 0;
		boolean lit = false;
		for (StructureSites.Box b : boxes) {
			if (b.maxY() + 1 <= floor + 1) {
				continue;
			}
			int w = Math.min(b.maxX() + 1, x1) - Math.max(b.minX(), x0);
			int d = Math.min(b.maxZ() + 1, z1) - Math.max(b.minZ(), z0);
			if (w <= 0 || d <= 0) {
				continue;
			}
			covered += (float) w * d;
			if (b.maxY() + 1 > tallest) {
				tallest = b.maxY() + 1;
				look = b.look();
			}
			lit |= StructureSites.Looks.LIT[b.look()];
		}
		float share = Math.min(1, covered / area);
		if (share <= 0.01F) {
			return;
		}
		if (lit) {
			// a lit town glows by how much of the cell it fills, brighter than its share: windows show from afar
			out.light = Math.max(out.light, Math.min(13, 4 + 30 * share));
		}
		out.top = mix(out.top, StructureSites.Looks.ROOF[look], Math.min(1, share * 1.5F));
		out.cover *= 1 - share;
		if (share >= 0.3F && out.water == Integer.MIN_VALUE) {
			out.ground = ground + (tallest - ground) * Math.min(1, share * 1.5F);
		}
	}

	private static int mix(int a, int b, float t) {
		int r = Math.round(((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
		int g = Math.round(((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
		int bl = Math.round((a & 255) * (1 - t) + (b & 255) * t);
		return r << 16 | g << 8 | bl;
	}
}
