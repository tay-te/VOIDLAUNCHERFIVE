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
		for (int rz = StructureSites.region(z0); rz <= StructureSites.region(z1 - 1); rz++) {
			for (int rx = StructureSites.region(x0); rx <= StructureSites.region(x1 - 1); rx++) {
				List<StructureSites.Box> region = this.sites.boxes(rx, rz);
				if (region == null) {
					continue;
				}
				for (StructureSites.Box b : region) {
					if (b.maxX() >= x0 && b.minX() < x1 && b.maxZ() >= z0 && b.minZ() < z1) {
						boxes.add(b);
					}
				}
			}
		}
		if (boxes.isEmpty()) {
			return;
		}
		int ground = (int) Math.ceil(out.ground);
		if (cell <= 4) {
			// the tallest building over this column
			StructureSites.Box top = null;
			for (StructureSites.Box b : boxes) {
				if (x >= b.minX() && x <= b.maxX() && z >= b.minZ() && z <= b.maxZ() && b.maxY() + 1 > ground + 1
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
			if (b.maxY() + 1 <= ground + 1) {
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
