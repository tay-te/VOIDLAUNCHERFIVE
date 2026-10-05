package dev.voidmc.atlas.core;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;

/**
 * Draws raw regions ({@link RegionRaw}) into images ({@link RegionImage}) with the {@link Palette}: tone, hillshade,
 * elevation bands, water depth and shoreline, and the dithered edge of the explored land. Pure: it knows nothing of
 * the game, only a {@link Looks} that says what each block and biome id looks like.
 *
 * <p>Not thread-safe (it caches toned colours): use one per worker thread.
 */
public final class TileRenderer {
	/** What blocks and biomes look like, by their ids in the world's palettes. */
	public interface Looks {
		/** The block's material, for the palette. */
		Material material(int block);

		/** The block's own colour (its texture's, tinted for the biome), 0xRRGGBB. */
		int colour(int block, int biome);

		/** The biome's water colour, 0xRRGGBB. */
		int water(int biome);
	}

	public enum Mode {
		/** The surface: elevation bands, sky-lit. */
		SURFACE,
		/** A horizontal slice (underground, the Nether): floors under the slice, rock where there is no air. */
		CAVE
	}

	private final Looks looks;
	private final Int2ObjectOpenHashMap<float[]> toned = new Int2ObjectOpenHashMap<>();
	private final Int2ObjectOpenHashMap<Material> materials = new Int2ObjectOpenHashMap<>();
	private final it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap waters = new it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap();

	// scratch grids for one rectangle, grown as needed
	private boolean[] known = new boolean[0];
	private short[] heights = new short[0];
	private byte[] kinds = new byte[0];

	private static final byte LAND = 0;
	private static final byte WATER = 1;
	private static final byte ROCK = 2;
	private static final byte NONE = 3;

	public TileRenderer(Looks looks) {
		this.looks = looks;
	}

	/** Forgets cached colours (after the looks change: a resource reload). */
	public void clearCache() {
		this.toned.clear();
		this.materials.clear();
		this.waters.clear();
	}

	/**
	 * Draws the rectangle {@code [x0, x1) x [z0, z1)} (region-local pixels) of {@code around[4]} into {@code image}'s
	 * level 0, then rebuilds the mip levels over it. {@code around} is the 3 x 3 block of regions centred on the one
	 * drawn ({@code around[(dz + 1) * 3 + dx + 1]}), nulls where nothing is known: the edge of the rectangle needs its
	 * neighbours' heights for the hillshade and their explored edge for the dither.
	 */
	public void render(RegionRaw[] around, RegionImage image, int x0, int z0, int x1, int z1, Mode mode) {
		x0 = Math.max(0, x0);
		z0 = Math.max(0, z0);
		x1 = Math.min(Coords.REGION_SIZE, x1);
		z1 = Math.min(Coords.REGION_SIZE, z1);
		if (x0 >= x1 || z0 >= z1) {
			return;
		}
		int w = x1 - x0;
		int h = z1 - z0;
		// a grid over the rectangle and two pixels round it
		int gw = w + 4;
		int gh = h + 4;
		int gn = gw * gh;
		if (this.known.length < gn) {
			this.known = new boolean[gn];
			this.heights = new short[gn];
			this.kinds = new byte[gn];
		}
		for (int gz = 0; gz < gh; gz++) {
			for (int gx = 0; gx < gw; gx++) {
				int lx = x0 + gx - 2;
				int lz = z0 + gz - 2;
				RegionRaw r = regionAt(around, lx, lz);
				int g = gz * gw + gx;
				if (r == null) {
					this.known[g] = false;
					this.kinds[g] = NONE;
					continue;
				}
				int i = RegionRaw.index(lx & Coords.REGION_MASK, lz & Coords.REGION_MASK);
				boolean k = r.known(i);
				this.known[g] = k;
				if (!k) {
					this.kinds[g] = NONE;
				} else if ((r.flags[i] & RegionRaw.ROCK) != 0) {
					this.kinds[g] = ROCK;
				} else {
					this.kinds[g] = r.depth(i) > 0 ? WATER : LAND;
				}
				this.heights[g] = r.height[i];
			}
		}
		RegionRaw centre = around[4];
		int[] out = image.levelOrCreate(0);
		for (int z = z0; z < z1; z++) {
			for (int x = x0; x < x1; x++) {
				int g = (z - z0 + 2) * gw + (x - x0 + 2);
				int p = z * Coords.REGION_SIZE + x;
				if (!this.known[g]) {
					out[p] = 0;
					continue;
				}
				int coverage = Palette.coverage(this.distanceToUnknown(g, gw));
				int i = RegionRaw.index(x, z);
				int rgb;
				byte kind = this.kinds[g];
				if (kind == ROCK) {
					rgb = Palette.ROCK;
				} else if (kind == WATER) {
					boolean shore = this.isLand(g - 1) || this.isLand(g + 1) || this.isLand(g - gw) || this.isLand(g + gw);
					rgb = Palette.water(centre.depth(i), shore, this.water(centre.biome[i]));
				} else {
					int block = centre.block[i];
					int biome = centre.biome[i];
					Material material = this.material(block);
					float[] tone = this.tone(block, biome, material);
					int step = Palette.shadeStep(this.northWestDifference(g, gw));
					if (mode == Mode.CAVE) {
						if (this.kinds[g - 1] == ROCK || this.kinds[g - gw] == ROCK) {
							// the floor at the foot of a cave wall: in the wall's shadow
							step = Math.min(step, -1);
						}
						rgb = Palette.caveFloor(tone, material, step);
					} else {
						rgb = Palette.land(tone, material, step, Palette.band(this.heights[g]));
					}
				}
				out[p] = coverage << 24 | rgb;
			}
		}
		image.downsample(x0, z0, x1, z1);
		image.touch();
	}

	private boolean isLand(int g) {
		return this.known[g] && this.kinds[g] == LAND;
	}

	/** This pixel's height less its north-west neighbour's (the light comes from there); west or north if that is unknown. */
	private int northWestDifference(int g, int gw) {
		int h = this.heights[g];
		int nw = g - gw - 1;
		if (this.known[nw] && this.kinds[nw] != ROCK) {
			return h - this.heights[nw];
		}
		int w = g - 1;
		if (this.known[w] && this.kinds[w] != ROCK) {
			return h - this.heights[w];
		}
		int n = g - gw;
		if (this.known[n] && this.kinds[n] != ROCK) {
			return h - this.heights[n];
		}
		return 0;
	}

	/** Chebyshev distance to the nearest unknown pixel, up to 3 ("far"). */
	private int distanceToUnknown(int g, int gw) {
		for (int dz = -1; dz <= 1; dz++) {
			for (int dx = -1; dx <= 1; dx++) {
				if (!this.known[g + dz * gw + dx]) {
					return 1;
				}
			}
		}
		for (int dz = -2; dz <= 2; dz++) {
			for (int dx = -2; dx <= 2; dx++) {
				if ((Math.abs(dx) == 2 || Math.abs(dz) == 2) && !this.known[g + dz * gw + dx]) {
					return 2;
				}
			}
		}
		return 3;
	}

	private static RegionRaw regionAt(RegionRaw[] around, int lx, int lz) {
		int rx = (lx >> Coords.REGION_SHIFT) + 1;
		int rz = (lz >> Coords.REGION_SHIFT) + 1;
		if (rx < 0 || rx > 2 || rz < 0 || rz > 2) {
			return null;
		}
		return around[rz * 3 + rx];
	}

	private Material material(int block) {
		Material m = this.materials.get(block);
		if (m == null) {
			m = this.looks.material(block);
			this.materials.put(block, m);
		}
		return m;
	}

	private float[] tone(int block, int biome, Material material) {
		int key = block << 16 | (biome & 0xFFFF);
		float[] t = this.toned.get(key);
		if (t == null) {
			t = Palette.tone(material, this.looks.colour(block, biome));
			this.toned.put(key, t);
		}
		return t;
	}

	private int water(int biome) {
		int c = this.waters.getOrDefault(biome, -1);
		if (c == -1) {
			c = this.looks.water(biome) & 0xFFFFFF;
			this.waters.put(biome, c);
		}
		return c;
	}
}
