package dev.voidmc.atlas.core;

/**
 * Small synthetic worlds for the palette's golden images, drawn with fixed block colours (the vanilla textures'
 * averages, tinted for plains), so the tests need no game.
 */
final class Scenes {
	static final int GRASS = 1, SAND = 2, STONE = 3, SNOW = 4, LEAVES = 5, GRAVEL = 6, DIRT = 7, SPRUCE = 8;
	static final int PLAINS = 1, TAIGA = 2;

	/** Fixed looks: material and colour per block id, a plain water colour. */
	static final TileRenderer.Looks LOOKS = new TileRenderer.Looks() {
		@Override
		public Material material(int block) {
			return switch (block) {
				case GRASS -> Material.GRASS;
				case SAND -> Material.SAND;
				case STONE -> Material.STONE;
				case SNOW -> Material.SNOW;
				case LEAVES, SPRUCE -> Material.FOLIAGE;
				case GRAVEL -> Material.GRAVEL;
				case DIRT -> Material.DIRT;
				default -> Material.OTHER;
			};
		}

		@Override
		public int colour(int block, int biome) {
			return switch (block) {
				case GRASS -> biome == TAIGA ? 0x5E8A4E : 0x6E9C47;
				case SAND -> 0xDBD3A0;
				case STONE -> 0x7E7E7E;
				case SNOW -> 0xF0FAFA;
				case LEAVES -> 0x4C7A2E;
				case SPRUCE -> 0x3B5A3B;
				case GRAVEL -> 0x837E7C;
				case DIRT -> 0x866043;
				default -> 0x808080;
			};
		}

		@Override
		public int water(int biome) {
			return 0x3F76E4;
		}
	};

	private Scenes() {
	}

	static int hash(int x, int z) {
		int h = x * 73856093 ^ z * 19349663;
		h ^= h >>> 13;
		h *= 0x5bd1e995;
		return (h ^ (h >>> 15)) & 0x7FFFFFFF;
	}

	/**
	 * An island in a sea: deep water round it, a beach, grassland, a forest with uneven crowns, a stony mountain with a
	 * snow cap and a river; outside a circle nothing is known (the dithered edge).
	 */
	static RegionRaw island(int size) {
		RegionRaw raw = new RegionRaw(0, 0);
		double c = size / 2.0;
		for (int z = 0; z < size; z++) {
			for (int x = 0; x < size; x++) {
				double dx = x - c, dz = z - c;
				double r = Math.hypot(dx, dz);
				if (r > size * 0.47) {
					continue;
				}
				double land = 1 - r / (size * 0.34);
				int ground;
				if (land <= 0) {
					ground = (int) Math.round(62 + land * 60);
				} else {
					ground = 62 + (int) Math.round(Math.pow(land, 1.6) * 110 + 2);
				}
				// a river cutting across from the west
				boolean river = Math.abs(dz - Math.sin(x * 0.18) * 3 - 6) < 1.6 && x < c - 4 && land > 0;
				if (river) {
					raw.set(x, z, 62, GRAVEL, 2, PLAINS, RegionRaw.KNOWN);
				} else if (ground < 62) {
					raw.set(x, z, 62, ground < 50 ? GRAVEL : SAND, 62 - ground, PLAINS, RegionRaw.KNOWN);
				} else if (ground <= 64) {
					raw.set(x, z, ground, SAND, 0, PLAINS, RegionRaw.KNOWN);
				} else if (ground > 150) {
					raw.set(x, z, ground, SNOW, 0, PLAINS, RegionRaw.KNOWN);
				} else if (ground > 112) {
					raw.set(x, z, ground, STONE, 0, PLAINS, RegionRaw.KNOWN);
				} else if (dx > 4 && dz > -2 && ground < 100) {
					// the forest: crowns of uneven height
					int crown = ground + 4 + hash(x / 2, z / 2) % 4;
					raw.set(x, z, crown, LEAVES, 0, PLAINS, RegionRaw.KNOWN);
				} else {
					raw.set(x, z, ground, GRASS, 0, PLAINS, RegionRaw.KNOWN);
				}
			}
		}
		return raw;
	}

	/** Four stone ramps rising to the south-east at 0, 1, 2 and 4 blocks per pixel: the hillshade's steps. */
	static RegionRaw ramps(int size) {
		RegionRaw raw = new RegionRaw(0, 0);
		int[] slopes = {0, 1, 2, 4};
		for (int z = 0; z < size; z++) {
			for (int x = 0; x < size; x++) {
				int band = x * 4 / size;
				int h = 70 + slopes[band] * ((x % (size / 4)) + z) / 2;
				raw.set(x, z, h, band == 3 ? GRASS : STONE, 0, PLAINS, RegionRaw.KNOWN);
			}
		}
		return raw;
	}

	/** A cave slice: rock with a winding gallery and a pool, floors at varying depth. */
	static RegionRaw caves(int size) {
		RegionRaw raw = new RegionRaw(0, 0);
		for (int z = 0; z < size; z++) {
			for (int x = 0; x < size; x++) {
				double t = Math.sin(x * 0.12) * 10 + Math.cos(z * 0.09) * 8;
				boolean open = Math.abs(z - size / 2.0 - t) < 6 + (hash(x / 3, z / 3) % 3) || Math.hypot(x - size * 0.7, z - size * 0.3) < 9;
				if (!open) {
					raw.set(x, z, 30, 0, 0, PLAINS, RegionRaw.KNOWN | RegionRaw.ROCK);
				} else if (Math.hypot(x - size * 0.7, z - size * 0.3) < 6) {
					raw.set(x, z, 20, GRAVEL, 3, PLAINS, RegionRaw.KNOWN);
				} else {
					raw.set(x, z, 14 + (int) Math.round(Math.sin(x * 0.3) * 2), (x + z) % 7 == 0 ? GRAVEL : STONE, 0, PLAINS, RegionRaw.KNOWN);
				}
			}
		}
		return raw;
	}

	/** Draws a scene's top-left {@code size} square. */
	static int[] draw(RegionRaw raw, int size, TileRenderer.Mode mode) {
		RegionImage image = new RegionImage(0, 0, Palette.VERSION);
		RegionRaw[] around = new RegionRaw[9];
		around[4] = raw;
		new TileRenderer(LOOKS).render(around, image, 0, 0, size, size, mode);
		int[] full = image.level(0);
		int[] out = new int[size * size];
		for (int z = 0; z < size; z++) {
			System.arraycopy(full, z * Coords.REGION_SIZE, out, z * size, size);
		}
		return out;
	}
}
