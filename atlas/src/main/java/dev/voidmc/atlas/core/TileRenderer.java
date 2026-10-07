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

		/** The block's id ("minecraft:grass_block"), for the tuned table; null if not known. */
		default String blockName(int block) {
			return null;
		}

		/** The biome's id ("minecraft:forest"), for the biome view; null if not known. */
		default String biomeName(int biome) {
			return null;
		}

		/** The biome's grass colour, 0xRRGGBB (plains' by default). */
		default int grass(int biome) {
			return MapArt.REF_GRASS;
		}

		/** The biome's foliage colour, 0xRRGGBB (plains' by default). */
		default int foliage(int biome) {
			return MapArt.REF_FOLIAGE;
		}
	}

	/** What a layer's images show: the land (terrain), its biomes, or the land by night. */
	public enum Variant {
		TERRAIN, BIOMES, NIGHT
	}

	public enum Mode {
		/** The surface: elevation bands, sky-lit. */
		SURFACE,
		/** A horizontal slice (underground, the Nether): floors under the slice, rock where there is no air. */
		CAVE,
		/**
		 * Land seen from afar (VOID LOD): the surface's look with the hillshade at half strength, 40% less saturated and
		 * 14% darker, its edge to unexplored land an 8 px checker.
		 */
		FAR,
		/** A party member's land: the surface's look at 50% saturation and 80% contrast, quieter than one's own. */
		SHARED
	}

	/** How far round a rectangle a mode reads (far land: its small gaps are filled from up to 2 chunks off, and its checker). */
	public static int margin(Mode mode) {
		return mode == Mode.FAR ? FAR_GAP + FAR_DISSOLVE + 1 : MARGIN;
	}

	/** The far layer's edge checker: 8 px cells. */
	public static final int FAR_EDGE = 8;
	/** A gap in far land this wide or less (2 chunks), with far land on both sides, is filled from its neighbours. */
	public static final int FAR_GAP = 32;
	/**
	 * Blocks a far sample stands for (1: the fine far layer; 32: the coarse store): its gap fill and dissolve are worked
	 * out in blocks, so they look the same at both.
	 */
	public int farSample = 1;
	private int[] gapFrom = new int[0];
	/** Far land's dissolve into unexplored land: this many blocks wide, an ordered dither in 4-block cells. */
	public static final int FAR_DISSOLVE = 32;
	private byte[] farDist = new byte[0];
	private static final int[] BAYER4 = {0, 8, 2, 10, 12, 4, 14, 6, 3, 11, 1, 9, 15, 7, 13, 5};

	private final Looks looks;
	private final Int2ObjectOpenHashMap<float[]> toned = new Int2ObjectOpenHashMap<>();
	private final Int2ObjectOpenHashMap<Material> materials = new Int2ObjectOpenHashMap<>();
	private final it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap waters = new it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap();

	// scratch grids for one rectangle, grown as needed
	boolean[] known = new boolean[0];
	short[] heights = new short[0];
	byte[] kinds = new byte[0];

	private static final byte LAND = 0;
	private static final byte WATER = 1;
	private static final byte ROCK = 2;
	private static final byte NONE = 3;
	/** Grid pixels kept round a drawn rectangle. */
	public static final int MARGIN = 3;
	/** Whether underground water and lava are drawn (the "Water & lava" layer); off, water shows its bed. */
	public boolean fluids = true;
	/** The next render's look: set by the layer before each render (the renderer is one per worker). */
	public Variant variant = Variant.TERRAIN;
	public MapArt.Style style = MapArt.Style.NATURAL;
	public boolean contours;
	/** In the biome view, the family kept bright (the others dimmed); null for none. */
	public MapArt.Family highlight;
	/** The new map art (section 9); off: the shipped palette (kept for comparison). */
	public boolean art = true;

	// grids the art needs as well
	int[] blocks = new int[0];
	int[] biomes = new int[0];
	byte[] depths = new byte[0];
	byte[] lights = new byte[0];
	private float[][] scratch = new float[12][0];
	/** The top of the world drawn (its highest block): the elevation lift spreads over its height (319 in vanilla). */
	public int worldTop = 319;
	private byte[] scratchBytes = new byte[0];
	/** The rectangle's region origin in blocks (for patterns that line up across regions). */
	int originX;
	int originZ;
	private final ArtPainter painter;

	public TileRenderer(Looks looks) {
		this.looks = looks;
		this.painter = new ArtPainter(looks);
	}

	float[] scratch(int i, int n) {
		if (this.scratch[i].length < n) {
			this.scratch[i] = new float[n];
		}
		return this.scratch[i];
	}

	byte[] scratchBytes(int n) {
		if (this.scratchBytes.length < n) {
			this.scratchBytes = new byte[n];
		}
		return this.scratchBytes;
	}

	/** Forgets cached colours (after the looks change: a resource reload). */
	public void clearCache() {
		this.toned.clear();
		this.materials.clear();
		this.waters.clear();
		this.painter.clear();
	}

	/**
	 * Draws the rectangle {@code [x0, x1) x [z0, z1)} (region-local pixels) of {@code around[4]} into {@code image}'s
	 * level 0, then rebuilds the mip levels over it. {@code around} is the 3 x 3 block of regions centred on the one
	 * drawn ({@code around[(dz + 1) * 3 + dx + 1]}), nulls where nothing is known: the edge of the rectangle needs its
	 * neighbours' heights for the hillshade and their explored edge for the dither.
	 */
	public void render(RegionRaw[] around, RegionImage image, int x0, int z0, int x1, int z1, Mode mode) {
		this.render(around, image, x0, z0, x1, z1, mode, Integer.MIN_VALUE);
	}

	/**
	 * As {@link #render(RegionRaw[], RegionImage, int, int, int, int, Mode)}; a cave slice ({@link Mode#CAVE}) seen from
	 * height {@code caveTop} has its floors stepped darker the further below that they lie.
	 */
	public void render(RegionRaw[] around, RegionImage image, int x0, int z0, int x1, int z1, Mode mode, int caveTop) {
		x0 = Math.max(0, x0);
		z0 = Math.max(0, z0);
		x1 = Math.min(Coords.REGION_SIZE, x1);
		z1 = Math.min(Coords.REGION_SIZE, z1);
		if (x0 >= x1 || z0 >= z1) {
			return;
		}
		int w = x1 - x0;
		int h = z1 - z0;
		// a grid over the rectangle and three pixels round it (the dither and the cave walls reach that far)
		int m = margin(mode);
		int gw = w + 2 * m;
		int gh = h + 2 * m;
		int gn = gw * gh;
		if (this.known.length < gn) {
			this.known = new boolean[gn];
			this.heights = new short[gn];
			this.kinds = new byte[gn];
			this.blocks = new int[gn];
			this.biomes = new int[gn];
			this.depths = new byte[gn];
			this.lights = new byte[gn];
		}
		this.originX = around[4] == null ? 0 : around[4].x << Coords.REGION_SHIFT;
		this.originZ = around[4] == null ? 0 : around[4].z << Coords.REGION_SHIFT;
		for (int gz = 0; gz < gh; gz++) {
			for (int gx = 0; gx < gw; gx++) {
				int lx = x0 + gx - m;
				int lz = z0 + gz - m;
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
				this.blocks[g] = r.block[i];
				this.biomes[g] = r.biome[i];
				this.depths[g] = r.depth[i];
				this.lights[g] = r.light[i];
				if (mode == Mode.CAVE && !this.fluids && this.kinds[g] == WATER) {
					// water hidden: its bed shows
					this.kinds[g] = LAND;
					this.heights[g] = (short) (r.height[i] - r.depth(i));
				}
			}
		}
		if (mode == Mode.FAR) {
			this.fillFarGaps(gw, gh);
			this.farDistances(gw, gh);
		}
		RegionRaw centre = around[4];
		int[] out = image.levelOrCreate(0);
		if (this.art && (mode != Mode.CAVE || this.variant == Variant.BIOMES)) {
			// the section 9 map art: the surface, far and shared land, and the biome view everywhere
			this.painter.paintSurface(this, mode, x0, z0, x1, z1, m, gw, gh, out, 0, 0);
			if (this.variant == Variant.NIGHT) {
				this.night(out, x0, z0, x1, z1, m, gw);
			}
			image.downsample(x0, z0, x1, z1);
			image.touch();
			return;
		}
		for (int z = z0; z < z1; z++) {
			for (int x = x0; x < x1; x++) {
				int g = (z - z0 + m) * gw + (x - x0 + m);
				int p = z * Coords.REGION_SIZE + x;
				if (!this.known[g]) {
					out[p] = 0;
					continue;
				}
				int coverage = mode == Mode.FAR ? this.farCoverage(g, gw, x, z) : Palette.coverage(this.distanceToUnknown(g, gw));
				if (coverage == 0) {
					out[p] = 0;
					continue;
				}
				int i = RegionRaw.index(x, z);
				int rgb;
				byte kind = this.kinds[g];
				if (kind == ROCK) {
					if (mode == Mode.CAVE) {
						// the wall: a rim, a step darker, then rock; rock further from seen air is not known at all
						int d = this.distanceToOpen(g, gw);
						if (d > 3) {
							out[p] = 0;
							continue;
						}
						rgb = d == 1 ? Palette.CAVE_RIM : d == 2 ? Palette.CAVE_WALL : Palette.ROCK;
					} else {
						rgb = Palette.ROCK;
					}
				} else if (kind == WATER) {
					boolean shore = this.isLand(g - 1) || this.isLand(g + 1) || this.isLand(g - gw) || this.isLand(g + gw);
					// from the grid (a far gap filled from its neighbour has its neighbour's)
					int depth = this.depths[g] & 0xFF;
					rgb = mode == Mode.CAVE ? Palette.caveWater(depth, shore, this.water(this.biomes[g]))
						: Palette.water(depth, shore, this.water(this.biomes[g]));
					if (mode == Mode.FAR || mode == Mode.SHARED) {
						rgb = quiet(rgb, mode);
					}
				} else {
					int block = this.blocks[g];
					int biome = this.biomes[g];
					Material material = this.material(block);
					float[] tone = this.tone(block, biome, material);
					int step = Palette.shadeStep(this.northWestDifference(g, gw));
					if (mode == Mode.CAVE) {
						if (this.kinds[g - 1] == ROCK || this.kinds[g - gw] == ROCK) {
							// the floor at the foot of a cave wall: in the wall's shadow
							step = Math.min(step, -1);
						}
						int below = caveTop == Integer.MIN_VALUE ? 0 : Math.max(0, caveTop - this.heights[g]);
						rgb = !this.fluids && material == Material.LAVA ? Palette.CAVE_WALL : Palette.caveFloor(tone, material, step, below);
					} else if (mode == Mode.FAR) {
						// half the hillshade, 40% less colour, 14% darker
						float L = (tone[0] + step * material.shade * 0.5F + Palette.band(this.heights[g]) * material.band) * 0.86F;
						rgb = Oklab.toRgb(Math.max(0.07F, Math.min(0.70F, L)), tone[1] * 0.6F, tone[2] * 0.6F);
					} else if (mode == Mode.SHARED) {
						rgb = quiet(Palette.land(tone, material, step, Palette.band(this.heights[g])), mode);
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

	/**
	 * Far land's small gaps (where VOID LOD sent nothing, up to {@value #FAR_GAP} blocks across with far land on both
	 * sides, across or along) take the nearest neighbour's column, so seams and missed tiles do not read as holes; wider
	 * gaps stay unexplored, behind the same checker edge as all far land's.
	 */
	private void fillFarGaps(int gw, int gh) {
		final int FAR_GAP = Math.max(1, TileRenderer.FAR_GAP / this.farSample);
		int gn = gw * gh;
		if (this.gapFrom.length < gn) {
			this.gapFrom = new int[gn];
		}
		int filled = 0;
		for (int gz = 0; gz < gh; gz++) {
			for (int gx = 0; gx < gw; gx++) {
				int g = gz * gw + gx;
				this.gapFrom[g] = -1;
				if (this.known[g]) {
					continue;
				}
				int l = 1;
				while (l <= FAR_GAP && gx - l >= 0 && !this.known[g - l]) {
					l++;
				}
				int r = 1;
				while (r <= FAR_GAP && gx + r < gw && !this.known[g + r]) {
					r++;
				}
				int best = -1;
				int bestD = Integer.MAX_VALUE;
				if (gx - l >= 0 && gx + r < gw && l <= FAR_GAP && r <= FAR_GAP && l + r - 1 <= FAR_GAP) {
					best = l <= r ? g - l : g + r;
					bestD = Math.min(l, r);
				}
				int u = 1;
				while (u <= FAR_GAP && gz - u >= 0 && !this.known[g - u * gw]) {
					u++;
				}
				int d = 1;
				while (d <= FAR_GAP && gz + d < gh && !this.known[g + d * gw]) {
					d++;
				}
				if (gz - u >= 0 && gz + d < gh && u <= FAR_GAP && d <= FAR_GAP && u + d - 1 <= FAR_GAP && Math.min(u, d) < bestD) {
					best = u <= d ? g - u * gw : g + d * gw;
				}
				if (best >= 0) {
					this.gapFrom[g] = best;
					filled++;
				}
			}
		}
		if (filled == 0) {
			return;
		}
		for (int g = 0; g < gn; g++) {
			int from = this.gapFrom[g];
			if (from >= 0) {
				this.known[g] = true;
				this.kinds[g] = this.kinds[from];
				this.heights[g] = this.heights[from];
				this.blocks[g] = this.blocks[from];
				this.biomes[g] = this.biomes[from];
				this.depths[g] = this.depths[from];
				this.lights[g] = this.lights[from];
			}
		}
	}

	/**
	 * How far each far column is from unexplored land (blocks, the eight ways, capped past {@link #FAR_DISSOLVE}): a
	 * two-pass distance transform over the grid, once a render.
	 */
	private void farDistances(int gw, int gh) {
		int gn = gw * gh;
		if (this.farDist.length < gn) {
			this.farDist = new byte[gn];
		}
		int cap = this.dissolve() + 1;
		byte[] d = this.farDist;
		for (int gz = 0; gz < gh; gz++) {
			for (int gx = 0; gx < gw; gx++) {
				int g = gz * gw + gx;
				if (!this.known[g]) {
					d[g] = 0;
					continue;
				}
				int v = cap;
				if (gx > 0) {
					v = Math.min(v, d[g - 1] + 1);
				}
				if (gz > 0) {
					v = Math.min(v, d[g - gw] + 1);
					if (gx > 0) {
						v = Math.min(v, d[g - gw - 1] + 1);
					}
					if (gx < gw - 1) {
						v = Math.min(v, d[g - gw + 1] + 1);
					}
				}
				d[g] = (byte) v;
			}
		}
		for (int gz = gh - 1; gz >= 0; gz--) {
			for (int gx = gw - 1; gx >= 0; gx--) {
				int g = gz * gw + gx;
				int v = d[g];
				if (v == 0) {
					continue;
				}
				if (gx < gw - 1) {
					v = Math.min(v, d[g + 1] + 1);
				}
				if (gz < gh - 1) {
					v = Math.min(v, d[g + gw] + 1);
					if (gx < gw - 1) {
						v = Math.min(v, d[g + gw + 1] + 1);
					}
					if (gx > 0) {
						v = Math.min(v, d[g + gw - 1] + 1);
					}
				}
				d[g] = (byte) v;
			}
		}
	}

	/**
	 * A far pixel's coverage: whole, but within {@value #FAR_DISSOLVE} blocks of unexplored land it dissolves into it, an
	 * ordered dither in 4-block cells thinning towards the edge: no hard edge at any zoom, a hole in VOID LOD's cover
	 * reads as land not seen yet.
	 */
	private int farCoverage(int g, int gw, int x, int z) {
		int d = this.farDist[g];
		int dissolve = this.dissolve();
		if (d > dissolve) {
			return 255;
		}
		// 4-block dither cells; a coarse sample is a cell of its own
		int shift = this.farSample >= 4 ? 0 : 2;
		int cell = BAYER4[((z >> shift) & 3) * 4 + ((x >> shift) & 3)];
		return d * 16 > cell * dissolve + dissolve / 2 ? 255 : 0;
	}

	/** The dissolve's width in samples: {@value #FAR_DISSOLVE} blocks, at least four samples. */
	private int dissolve() {
		return Math.max(4, FAR_DISSOLVE / this.farSample);
	}

	/** A colour made quieter: far land 40% less saturated and 14% darker; a party member's at 50% saturation, 80% contrast. */
	static int quiet(int rgb, Mode mode) {
		float[] lab = Oklab.fromRgb(rgb);
		if (mode == Mode.FAR) {
			return Oklab.toRgb(lab[0] * 0.86F, lab[1] * 0.6F, lab[2] * 0.6F);
		}
		return Oklab.toRgb(0.45F + (lab[0] - 0.45F) * 0.8F, lab[1] * 0.5F, lab[2] * 0.5F);
	}

	/** A pixel's coverage by the explored edge (far land: the 8 px checker). */
	int coverageFor(Mode mode, int g, int gw, int x, int z) {
		return mode == Mode.FAR ? this.farCoverage(g, gw, x, z) : Palette.coverage(this.distanceToUnknown(g, gw));
	}

	/** The shipped palette's tone of a block (the untuned blocks start from it). */
	float[] shippedTone(int block, int biome, Material material) {
		return this.tone(block, biome, material);
	}

	int waterColour(int biome) {
		return this.water(biome);
	}

	/** How far block light reaches (a level 15 source: 10.4 blocks). */
	private static final int GLOW_REACH = 11;

	/**
	 * The night look of what was just painted (Figma 395:20486): moonlit (L × 0.56 + 0.015, chroma 0.4, cooler), and
	 * the glow of the block light Atlas recorded in walked columns (torches 14, lanterns and glowstone 15, soul light
	 * 10, cooler), warming the day's colour back in. Sources up to {@value #GLOW_REACH} blocks round the rectangle are
	 * read from the regions round it.
	 */
	private void night(int[] out, int x0, int z0, int x1, int z1, int m, int gw) {
		int w = x1 - x0;
		int h = z1 - z0;
		float[] glow = this.scratch(0, w * h);
		java.util.Arrays.fill(glow, 0, w * h, 0);
		byte[] soul = this.scratchBytes(w * h);
		java.util.Arrays.fill(soul, 0, w * h, (byte) 0);
		// sources in the grid (the margin covers the near ones; light from beyond the margin is not seen)
		int gh = h + 2 * m;
		for (int gz = 0; gz < gh; gz++) {
			for (int gx = 0; gx < gw; gx++) {
				int g = gz * gw + gx;
				int lv = this.lights[g] & 0x0F;
				if (lv <= 3 || !this.known[g]) {
					continue;
				}
				boolean isSoul = (this.lights[g] & 0x10) != 0;
				int sx = gx - m;
				int sz = gz - m;
				for (int dz = -GLOW_REACH; dz <= GLOW_REACH; dz++) {
					int pz = sz + dz;
					if (pz < 0 || pz >= h) {
						continue;
					}
					for (int dx = -GLOW_REACH; dx <= GLOW_REACH; dx++) {
						int px = sx + dx;
						if (px < 0 || px >= w) {
							continue;
						}
						float gv = MapArt.glow(lv, Math.sqrt(dx * dx + dz * dz));
						int k = pz * w + px;
						if (gv > glow[k]) {
							glow[k] = gv;
							soul[k] = (byte) (isSoul ? 1 : 0);
						}
					}
				}
			}
		}
		for (int z = z0; z < z1; z++) {
			for (int x = x0; x < x1; x++) {
				int p = z * Coords.REGION_SIZE + x;
				int argb = out[p];
				if (argb >>> 24 == 0) {
					continue;
				}
				float[] day = Oklab.fromRgb(argb & 0xFFFFFF);
				float[] night = MapArt.moonlit(day);
				int k = (z - z0) * w + (x - x0);
				float[] lit = MapArt.lit(night, day, glow[k], soul[k] != 0);
				out[p] = argb & 0xFF000000 | Oklab.toRgb(Math.max(0.03F, lit[0]), lit[1], lit[2]);
			}
		}
	}

	/** Chebyshev distance from a wall pixel to the nearest open (floor or water) pixel, up to 4 ("far"). */
	private int distanceToOpen(int g, int gw) {
		for (int r = 1; r <= 3; r++) {
			for (int dz = -r; dz <= r; dz++) {
				for (int dx = -r; dx <= r; dx++) {
					if (Math.abs(dx) != r && Math.abs(dz) != r) {
						continue;
					}
					int o = g + dz * gw + dx;
					if (this.known[o] && this.kinds[o] != ROCK) {
						return r;
					}
				}
			}
		}
		return 4;
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

	Material material(int block) {
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
