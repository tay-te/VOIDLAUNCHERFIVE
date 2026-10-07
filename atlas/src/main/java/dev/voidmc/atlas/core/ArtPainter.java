package dev.voidmc.atlas.core;

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;

/**
 * The surface's map art (Figma 395:19899): hand-tuned block colours, a hillshade lit from the north-west on the
 * smoothed ground (not the canopy), slope and elevation, roofs with ridges and shadows, tree crowns lit from the
 * north-west with a shadow, contours, water by depth with river steps and foam, the three styles, and the biome view
 * (flat fills with a faint hillshade, borders, a highlighted family) for the surface, the underground and the Nether.
 * One per {@link TileRenderer} (it caches colours by block and biome), so one per worker thread.
 */
final class ArtPainter {
	private static final double LIGHT_ALT = Math.toRadians(40);
	private static final double LIGHT_AZ = Math.toRadians(315);
	/** The light's direction (x east, y up, z south), towards the light: from the north-west, 40° up. */
	private static final double LX = Math.cos(LIGHT_ALT) * Math.sin(LIGHT_AZ);
	private static final double LY = Math.sin(LIGHT_ALT);
	private static final double LZ = -Math.cos(LIGHT_ALT) * Math.cos(LIGHT_AZ);
	private static final float WELL_L;
	private static final float[] WELL_LAB = Oklab.fromRgb(MapArt.WELL);

	static {
		WELL_L = WELL_LAB[0];
	}

	private final TileRenderer.Looks looks;
	private final Int2ObjectOpenHashMap<float[]> base = new Int2ObjectOpenHashMap<>();
	private final Int2ObjectOpenHashMap<float[]> fills = new Int2ObjectOpenHashMap<>();
	private final Int2ObjectOpenHashMap<MapArt.Family> families = new Int2ObjectOpenHashMap<>();
	private final Int2IntOpenHashMap flags = new Int2IntOpenHashMap();
	private final Int2ObjectOpenHashMap<float[]> waterTints = new Int2ObjectOpenHashMap<>();

	private static final int F_LEAF = 1, F_ROOF = 2, F_RIVER_BIOME = 4;

	ArtPainter(TileRenderer.Looks looks) {
		this.looks = looks;
	}

	void clear() {
		this.base.clear();
		this.fills.clear();
		this.families.clear();
		this.flags.clear();
		this.waterTints.clear();
	}

	/** A block's colour in a biome (OKLab), as the table and the style have it. */
	private float[] base(int block, int biome, Material m, MapArt.Style style, float[] shippedTone) {
		int key = block << 16 | (biome & 0xFFFF);
		float[] lab = this.base.get(key);
		if (lab == null) {
			String name = this.looks.blockName(block);
			int tuned = MapArt.tuned(name);
			if (tuned >= 0) {
				if (m == Material.GRASS || m == Material.FOLIAGE && !"minecraft:cherry_leaves".equals(name) && !"minecraft:azalea_leaves".equals(name)) {
					boolean foliage = m == Material.FOLIAGE;
					lab = MapArt.tinted(tuned, foliage ? this.looks.foliage(biome) : this.looks.grass(biome), foliage);
				} else {
					lab = Oklab.fromRgb(tuned);
				}
			} else {
				// not in the table: the shipped tone, lifted into the new range
				lab = new float[]{shippedTone[0] + MapArt.UNTUNED_LIFT, shippedTone[1], shippedTone[2]};
			}
			MapArt.capChroma(lab, MapArt.CHROMA_CAP);
			if (style == MapArt.Style.TOPOGRAPHIC) {
				lab = new float[]{lab[0], lab[1] * 0.35F, lab[2] * 0.35F};
			}
			this.base.put(key, lab);
		}
		return lab;
	}

	private int flags(int block, Material m) {
		int f = this.flags.getOrDefault(block, -1);
		if (f < 0) {
			f = 0;
			if (m == Material.FOLIAGE) {
				f |= F_LEAF;
			}
			String name = this.looks.blockName(block);
			String path = name == null ? "" : name.substring(name.indexOf(':') + 1);
			if (m == Material.BUILT || m == Material.WOOD && (path.contains("plank") || path.contains("stairs") || path.contains("slab")
				|| path.contains("roof") || path.contains("shingle") || path.contains("thatch") || path.contains("hay"))) {
				f |= F_ROOF;
			}
			this.flags.put(block, f);
		}
		return f;
	}

	float[] fill(int biome) {
		float[] lab = this.fills.get(biome);
		if (lab == null) {
			String id = this.looks.biomeName(biome);
			int rgb = MapArt.vanillaFill(id);
			if (rgb < 0) {
				rgb = MapArt.moddedFill(this.looks.grass(biome), this.looks.foliage(biome));
			}
			lab = Oklab.fromRgb(rgb);
			this.fills.put(biome, lab);
		}
		return lab;
	}

	MapArt.Family family(int biome) {
		MapArt.Family f = this.families.get(biome);
		if (f == null) {
			f = MapArt.family(this.looks.biomeName(biome));
			this.families.put(biome, f);
		}
		return f;
	}

	private boolean riverBiome(int biome) {
		String id = this.looks.biomeName(biome);
		return id != null && (id.endsWith(":river") || id.endsWith(":frozen_river") || id.contains("river"));
	}

	private float[] waterLab(int stepRgb, int biome, int waterRgb) {
		int key = stepRgb * 31 + biome;
		float[] lab = this.waterTints.get(key);
		if (lab == null) {
			lab = MapArt.water(stepRgb, waterRgb);
			this.waterTints.put(key, lab);
		}
		return lab;
	}

	/**
	 * Paints the surface art over the rectangle: the grids are those {@link TileRenderer} built, {@code gw} wide with
	 * a margin of {@code m}. Writes ARGB into {@code out} (coverage in alpha from {@code coverage}).
	 */
	void paintSurface(TileRenderer r, TileRenderer.Mode mode, int x0, int z0, int x1, int z1, int m, int gw, int gh, int[] out, int ox, int oz) {
		boolean[] known = r.known;
		short[] heights = r.heights;
		byte[] kinds = r.kinds;
		int[] blocks = r.blocks;
		int[] biomes = r.biomes;
		byte[] depths = r.depths;
		int gn = gw * gh;
		MapArt.Style style = r.style;
		boolean biomeLook = r.variant == TileRenderer.Variant.BIOMES;
		// the ground, without the canopy: a leaf column takes the ground round it
		float[] ground = r.scratch(0, gn);
		byte[] leaf = r.scratchBytes(gn);
		for (int g = 0; g < gn; g++) {
			leaf[g] = 0;
			if (known[g] && kinds[g] == 0) {
				Material mat = r.material(blocks[g]);
				if ((this.flags(blocks[g], mat) & F_LEAF) != 0) {
					leaf[g] = 1;
				}
			}
		}
		for (int gz = 0; gz < gh; gz++) {
			for (int gx = 0; gx < gw; gx++) {
				int g = gz * gw + gx;
				if (!known[g]) {
					ground[g] = Float.NaN;
					continue;
				}
				if (leaf[g] == 0) {
					ground[g] = kinds[g] == 1 ? heights[g] - Math.min(depths[g] & 0xFF, 1) : heights[g];
					continue;
				}
				double sum = 0;
				int n = 0;
				for (int dz = -2; dz <= 2; dz++) {
					for (int dx = -2; dx <= 2; dx++) {
						int ax = gx + dx, az = gz + dz;
						if (ax < 0 || az < 0 || ax >= gw || az >= gh) {
							continue;
						}
						int a = az * gw + ax;
						if (known[a] && leaf[a] == 0 && kinds[a] != 2) {
							sum += heights[a];
							n++;
						}
					}
				}
				ground[g] = n > 0 ? (float) (sum / n) : heights[g] - 5;
			}
		}
		// smoothed: 3 x 3 for the terrain's light, 5 x 5 for the biome view's faint one
		float[] smooth = r.scratch(1, gn);
		boolean far = mode == TileRenderer.Mode.FAR;
		// far land's light from its own smoothed heights (a chunk-sized sample, or a coarse one, is not a slope)
		box(ground, smooth, gw, gh, biomeLook ? 2 : far ? (r.farSample > 1 ? 1 : 6) : 1);
		float perStep = biomeLook ? 0.004F : style == MapArt.Style.TOPOGRAPHIC ? 0.008F : 0.011F;
		if (mode == TileRenderer.Mode.FAR) {
			perStep *= 0.5F;
		}
		boolean relief = biomeLook || style != MapArt.Style.FLAT;
		float[] baseL = r.scratch(2, gn);
		float[] baseA = r.scratch(3, gn);
		float[] baseB = r.scratch(4, gn);
		float[] calmL = r.scratch(5, gn);
		float[] calmA = r.scratch(6, gn);
		float[] calmB = r.scratch(7, gn);
		if (!biomeLook) {
			for (int g = 0; g < gn; g++) {
				if (!known[g] || kinds[g] != 0) {
					baseL[g] = Float.NaN;
					continue;
				}
				Material mat = r.material(blocks[g]);
				float[] b = this.base(blocks[g], biomes[g], mat, style, r.shippedTone(blocks[g], biomes[g], mat));
				if (far) {
					// far land: towards its biome's own fill (not grey or beige), quieter; one calm colour an area
					float[] fill = this.fill(biomes[g]);
					baseL[g] = b[0] * 0.75F + fill[0] * 0.25F;
					baseA[g] = (b[1] * 0.35F + fill[1] * 0.65F) * 0.8F;
					baseB[g] = (b[2] * 0.35F + fill[2] * 0.65F) * 0.8F;
				} else {
					baseL[g] = b[0];
					baseA[g] = b[1];
					baseB[g] = b[2];
				}
			}
			if (far) {
				blurMasked(r, baseL, baseA, baseB, calmL, calmA, calmB, gw, gh, r.farSample > 1 ? 1 : 16);
			}
		}
		boolean contours = r.contours && !biomeLook;
		MapArt.Family highlight = r.highlight;
		float[] lab = new float[3];
		for (int z = z0; z < z1; z++) {
			for (int x = x0; x < x1; x++) {
				int g = (z - z0 + m) * gw + (x - x0 + m);
				int p = (z - oz) * Coords.REGION_SIZE + (x - ox);
				if (!known[g]) {
					out[p] = 0;
					continue;
				}
				int coverage = r.coverageFor(mode, g, gw, x, z);
				if (coverage == 0) {
					out[p] = 0;
					continue;
				}
				byte kind = kinds[g];
				int block = blocks[g];
				int biome = biomes[g];
				if (kind == 2) {
					out[p] = coverage << 24 | Palette.ROCK;
					continue;
				}
				// the light on the smoothed ground
				float step = 0;
				float slope = 0;
				double grade = 0;
				if (relief && !Float.isNaN(smooth[g])) {
					float e = neighbour(smooth, g + 1, smooth[g]);
					float w = neighbour(smooth, g - 1, smooth[g]);
					float s = neighbour(smooth, g + gw, smooth[g]);
					float n = neighbour(smooth, g - gw, smooth[g]);
					double gx = (e - w) / 2.0;
					double gz = (s - n) / 2.0;
					double len = Math.sqrt(gx * gx + 1 + gz * gz);
					double lambert = (-gx * LX + LY - gz * LZ) / len;
					// continuous, not in steps: a long even slope is one even shade, never bands
					step = (float) Math.max(-6, Math.min(6, (lambert - LY) / 0.035));
					grade = Math.hypot(gx, gz);
					slope = (float) Math.min(grade / 1.5, 1);
				}
				if (biomeLook) {
					float[] fill = this.fill(biome);
					MapArt.Family fam = this.family(biome);
					if (mode == TileRenderer.Mode.CAVE) {
						// underground and Nether biome view
						boolean lava = kind == 0 && r.material(block) == Material.LAVA;
						if (fam == MapArt.Family.CAVES || fam == MapArt.Family.NETHER) {
							lab[0] = lava ? fill[0] * 0.7F : fill[0];
							lab[1] = fill[1];
							lab[2] = fill[2];
						} else {
							// other floors: the biome above, quieter (half the lightness range, 35% of the colour)
							lab[0] = 0.30F + (fill[0] - 0.30F) * 0.5F;
							lab[1] = fill[1] * 0.35F;
							lab[2] = fill[2] * 0.35F;
						}
					} else {
						lab[0] = fill[0];
						lab[1] = fill[1];
						lab[2] = fill[2];
					}
					if (highlight != null && fam != highlight) {
						float[] d = MapArt.dimmed(lab);
						lab[0] = d[0];
						lab[1] = d[1];
						lab[2] = d[2];
					}
					lab[0] += step * perStep;
					// the border: where the biome differs from the east or south neighbour
					int ge = g + 1, gs = g + gw;
					if (known[ge] && biomes[ge] != biome || known[gs] && biomes[gs] != biome) {
						lab[0] += (WELL_L - lab[0]) * MapArt.BORDER_MIX;
						lab[1] += (WELL_LAB[1] - lab[1]) * MapArt.BORDER_MIX;
						lab[2] += (WELL_LAB[2] - lab[2]) * MapArt.BORDER_MIX;
					}
					out[p] = coverage << 24 | Oklab.toRgb(lab[0], lab[1], lab[2]);
					continue;
				}
				if (kind == 1) {
					// water: by depth, the river's own steps, foam where it touches land
					boolean river = this.riverBiome(biome) || this.channel(kinds, known, g, gw);
					int depth = depths[g] & 0xFF;
					boolean shore = isLand(kinds, known, g - 1) || isLand(kinds, known, g + 1) || isLand(kinds, known, g - gw)
						|| isLand(kinds, known, g + gw);
					int rgb;
					if (shore) {
						rgb = river ? MapArt.FOAM_RIVER : MapArt.FOAM_SEA;
					} else if (river) {
						rgb = MapArt.RIVER[Math.min(2, style == MapArt.Style.FLAT ? (depth <= 3 ? 0 : 2) : MapArt.seaStep(depth))];
					} else {
						rgb = MapArt.SEA[style == MapArt.Style.FLAT ? MapArt.flatStep(depth) : MapArt.seaStep(depth)];
					}
					float[] w = this.waterLab(rgb, biome, r.waterColour(biome));
					lab[0] = w[0];
					lab[1] = w[1];
					lab[2] = w[2];
					if (style == MapArt.Style.TOPOGRAPHIC) {
						lab[1] *= 0.35F;
						lab[2] *= 0.35F;
					}
				} else {
					Material mat = r.material(block);
					int from = grade > 1.2 && !far ? majority(blocks, baseL, gw, gh, g, 2) : g;
					if (far && !Float.isNaN(calmL[g])) {
						// far land: the calmed colour of its area
						lab[0] = calmL[g];
						lab[1] = calmA[g];
						lab[2] = calmB[g];
					} else if (from != g) {
						// a steep face (snow and rock striped by its steps): the block most of its 5 x 5 shows, so a
						// snowfield reads as one, its edges still crisp
						lab[0] = baseL[from];
						lab[1] = baseA[from];
						lab[2] = baseB[from];
					} else {
						lab[0] = baseL[g];
						lab[1] = baseA[g];
						lab[2] = baseB[g];
					}
					if (mat == Material.LAVA) {
						out[p] = coverage << 24 | Oklab.toRgb(lab[0], lab[1], lab[2]);
						continue;
					}
					int fl = this.flags(block, mat);
					float h = heights[g];
					float gh0 = Float.isNaN(ground[g]) ? h : ground[g];
					if (relief) {
						lab[0] += step * perStep * MapArt.shadeFactor(this.looks.blockName(block), mat);
						lab[0] -= 0.012F * slope;
						if (style != MapArt.Style.FLAT) {
							if ((fl & F_ROOF) != 0) {
								// a roof's ridge: lit or shaded by the height difference to the north-west
								int nw = g - gw - 1;
								if (known[nw]) {
									float dh = h - heights[nw];
									lab[0] += dh > 0 ? 0.035F : dh < 0 ? -0.035F : 0;
								}
							} else if (shadowFrom(r, known, heights, blocks, F_ROOF, g, gw, h)) {
								lab[0] -= 0.04F;
							}
							if ((fl & F_LEAF) != 0) {
								// a tree crown: lit on its north-west, darker on its outer ring, each tree a little its own
								int nw = g - gw - 1;
								float lit = !known[nw] || leaf[nw] == 0 || heights[nw] < h ? 1 : heights[nw] == h ? 0.5F : 0;
								lab[0] += 0.030F * lit;
								if (leaf[g - 1] == 0 || leaf[g + 1] == 0 || leaf[g - gw] == 0 || leaf[g + gw] == 0) {
									lab[0] -= 0.018F;
								}
								int wx = x + ox * 0, wz = z;
								int t = treeHash(r.originX + x >> 2, r.originZ + z >> 2);
								lab[0] += ((t & 3) - 1.5F) / 1.5F * 0.01F;
							} else if (shadowFrom(r, known, heights, blocks, F_LEAF, g, gw, h)) {
								lab[0] -= 0.035F;
							}
						}
					}
					// elevation: a little lighter in eight steps up to two thirds of the way from the sea to 64 below the
					// world's top (+0.032 there): every 16 blocks to y 191 in vanilla, as it always was; every 43 in a
					// world 704 high, so tall mountains keep lightening all the way up
					if (gh0 > Palette.SEA_LEVEL) {
						float stepBlocks = Math.max(16, (r.worldTop - 64 - Palette.SEA_LEVEL) * 2 / 3F / 8F);
						lab[0] += Math.min(0.032F, 0.004F * (float) Math.floor((gh0 - Palette.SEA_LEVEL) / stepBlocks));
					}
					if (contours) {
						int gi = (int) Math.floor(gh0);
						int ge = g + 1, gs = g + gw;
						int he = known[ge] && kinds[ge] == 0 ? (int) Math.floor(Float.isNaN(ground[ge]) ? heights[ge] : ground[ge]) : gi;
						int hs = known[gs] && kinds[gs] == 0 ? (int) Math.floor(Float.isNaN(ground[gs]) ? heights[gs] : ground[gs]) : gi;
						if (Math.floorDiv(gi, 64) != Math.floorDiv(he, 64) || Math.floorDiv(gi, 64) != Math.floorDiv(hs, 64)) {
							lab[0] -= 0.09F;
						} else if (Math.floorDiv(gi, 16) != Math.floorDiv(he, 16) || Math.floorDiv(gi, 16) != Math.floorDiv(hs, 16)) {
							// a minor line fades where its neighbours crowd it: full at a grade of 1 (16 blocks apart, 4 px or
							// more at 1:4), gone by a grade of 2; the 64-block lines always stay
							float keep = (float) Math.max(0, Math.min(1, 2 - grade));
							lab[0] -= 0.045F * keep;
						}
					}
					lab[0] = Math.max(MapArt.LAND_MIN_L, Math.min(MapArt.LAND_MAX_L, lab[0]));
				}
				if (mode == TileRenderer.Mode.FAR) {
					// land was already calmed towards its biome; water quietened here
					lab[0] *= 0.88F;
					if (kind == 1) {
						lab[1] *= 0.85F;
						lab[2] *= 0.85F;
					}
				} else if (mode == TileRenderer.Mode.SHARED) {
					lab[0] = 0.45F + (lab[0] - 0.45F) * 0.8F;
					lab[1] *= 0.5F;
					lab[2] *= 0.5F;
				}
				out[p] = coverage << 24 | Oklab.toRgb(lab[0], lab[1], lab[2]);
			}
		}
	}

	/** Whether a pixel lies in the shadow (1–2 px to its south-east) of a taller roof or crown. */
	private boolean shadowFrom(TileRenderer r, boolean[] known, short[] heights, int[] blocks, int flag, int g, int gw, float h) {
		for (int d = 1; d <= 2; d++) {
			int a = g - d * gw - d;
			if (a < 0 || !known[a]) {
				return false;
			}
			if ((this.flags(blocks[a], r.material(blocks[a])) & flag) != 0 && heights[a] > h + 1) {
				return true;
			}
		}
		return false;
	}

	/** A narrow channel: water with land within two pixels on both sides, across either way. */
	private boolean channel(byte[] kinds, boolean[] known, int g, int gw) {
		boolean we = (isLand(kinds, known, g - 1) || isLand(kinds, known, g - 2)) && (isLand(kinds, known, g + 1) || isLand(kinds, known, g + 2));
		boolean ns = (isLand(kinds, known, g - gw) || isLand(kinds, known, g - 2 * gw)) && (isLand(kinds, known, g + gw) || isLand(kinds, known, g + 2 * gw));
		return we || ns;
	}

	private static boolean isLand(byte[] kinds, boolean[] known, int g) {
		return g >= 0 && g < known.length && known[g] && kinds[g] == 0;
	}

	private static float neighbour(float[] a, int g, float self) {
		if (g < 0 || g >= a.length) {
			return self;
		}
		float v = a[g];
		return Float.isNaN(v) ? self : v;
	}

	/** The cell round {@code g} (radius {@code k}) whose block most land there shares; {@code g} when it is a tie or its own. */
	private static int majority(int[] blocks, float[] baseL, int w, int h, int g, int k) {
		int gx = g % w;
		int gz = g / w;
		int bestBlock = blocks[g];
		int bestCount = 0;
		int bestCell = g;
		int ownCount = 0;
		for (int dz = -k; dz <= k; dz++) {
			for (int dx = -k; dx <= k; dx++) {
				int ax = gx + dx, az = gz + dz;
				if (ax < 0 || az < 0 || ax >= w || az >= h) {
					continue;
				}
				int a = az * w + ax;
				if (Float.isNaN(baseL[a])) {
					continue;
				}
				int block = blocks[a];
				if (block == blocks[g]) {
					ownCount++;
					continue;
				}
				int c = 0;
				for (int ez = -k; ez <= k; ez++) {
					for (int ex = -k; ex <= k; ex++) {
						int bx = gx + ex, bz = gz + ez;
						if (bx >= 0 && bz >= 0 && bx < w && bz < h && blocks[bz * w + bx] == block && !Float.isNaN(baseL[bz * w + bx])) {
							c++;
						}
					}
				}
				if (c > bestCount) {
					bestCount = c;
					bestBlock = block;
					bestCell = a;
				}
			}
		}
		return bestCount > ownCount + 2 ? bestCell : g;
	}

	/**
	 * A box blur of radius {@code k} of three channels over the cells whose first channel is a number (land), each
	 * averaged only with its own kind; separable, with running sums (any radius, one pass a channel a way).
	 */
	private static void blurMasked(TileRenderer r, float[] l, float[] a, float[] b, float[] ol, float[] oa, float[] ob, int w, int h, int k) {
		int n = w * h;
		float[] tl = r.scratch(8, n);
		float[] ta = r.scratch(9, n);
		float[] tb = r.scratch(10, n);
		float[] tc = r.scratch(11, n);
		// across
		for (int z = 0; z < h; z++) {
			float sl = 0, sa = 0, sb = 0, c = 0;
			int row = z * w;
			for (int x = -k; x < w; x++) {
				int add = x + k;
				if (add < w && !Float.isNaN(l[row + add])) {
					sl += l[row + add];
					sa += a[row + add];
					sb += b[row + add];
					c++;
				}
				int sub = x - k - 1;
				if (sub >= 0 && !Float.isNaN(l[row + sub])) {
					sl -= l[row + sub];
					sa -= a[row + sub];
					sb -= b[row + sub];
					c--;
				}
				if (x >= 0) {
					tl[row + x] = sl;
					ta[row + x] = sa;
					tb[row + x] = sb;
					tc[row + x] = c;
				}
			}
		}
		// down
		for (int x = 0; x < w; x++) {
			float sl = 0, sa = 0, sb = 0, c = 0;
			for (int z = -k; z < h; z++) {
				int add = z + k;
				if (add < h) {
					int g = add * w + x;
					sl += tl[g];
					sa += ta[g];
					sb += tb[g];
					c += tc[g];
				}
				int sub = z - k - 1;
				if (sub >= 0) {
					int g = sub * w + x;
					sl -= tl[g];
					sa -= ta[g];
					sb -= tb[g];
					c -= tc[g];
				}
				if (z >= 0) {
					int g = z * w + x;
					if (Float.isNaN(l[g]) || c < 0.5F) {
						ol[g] = Float.NaN;
					} else {
						ol[g] = sl / c;
						oa[g] = sa / c;
						ob[g] = sb / c;
					}
				}
			}
		}
	}

	/** A box blur of radius {@code k} over the known (non-NaN) cells. */
	private static void box(float[] in, float[] out, int w, int h, int k) {
		for (int z = 0; z < h; z++) {
			for (int x = 0; x < w; x++) {
				int g = z * w + x;
				if (Float.isNaN(in[g])) {
					out[g] = Float.NaN;
					continue;
				}
				double sum = 0;
				int n = 0;
				for (int dz = -k; dz <= k; dz++) {
					int az = z + dz;
					if (az < 0 || az >= h) {
						continue;
					}
					for (int dx = -k; dx <= k; dx++) {
						int ax = x + dx;
						if (ax < 0 || ax >= w) {
							continue;
						}
						float v = in[az * w + ax];
						if (!Float.isNaN(v)) {
							sum += v;
							n++;
						}
					}
				}
				out[g] = (float) (sum / n);
			}
		}
	}

	private static int treeHash(int x, int z) {
		int h = x * 73856093 ^ z * 19349663;
		h ^= h >>> 13;
		h *= 0x5bd1e995;
		return h ^ h >>> 15;
	}
}
