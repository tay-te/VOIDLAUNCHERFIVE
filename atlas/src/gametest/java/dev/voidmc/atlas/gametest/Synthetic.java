package dev.voidmc.atlas.gametest;

import dev.voidmc.atlas.client.AtlasClient;
import dev.voidmc.atlas.client.data.DimensionData;
import dev.voidmc.atlas.client.data.LayerData;
import dev.voidmc.atlas.client.data.MapSession;
import dev.voidmc.atlas.core.Coords;
import dev.voidmc.atlas.core.RegionRaw;

/**
 * Land made up for the performance test: a few hundred regions of plausible terrain (seas, beaches, plains, forests,
 * mountains with snow), written straight into a layer's raw regions on the workers and drawn like any other. The
 * map's drawing cost does not depend on where land came from, and this way the test can pan over far more tiles than
 * a flight could map in its time.
 */
final class Synthetic {
	private Synthetic() {
	}

	static void fill(AtlasClient atlas, String dimension, int rx0, int rz0, int n) {
		MapSession session = atlas.session();
		LayerData layer = session.dimension(dimension, false).layer(DimensionData.SURFACE);
		int water = session.blockNames.id("minecraft:sand");
		int grass = session.blockNames.id("minecraft:grass_block");
		int sand = session.blockNames.id("minecraft:sand");
		int stone = session.blockNames.id("minecraft:stone");
		int snow = session.blockNames.id("minecraft:snow_block");
		int leaves = session.blockNames.id("minecraft:oak_leaves");
		int spruce = session.blockNames.id("minecraft:spruce_leaves");
		int gravel = session.blockNames.id("minecraft:gravel");
		int plains = session.biomeNames.id("minecraft:plains");
		int forest = session.biomeNames.id("minecraft:forest");
		int ocean = session.biomeNames.id("minecraft:ocean");
		int peaks = session.biomeNames.id("minecraft:stony_peaks");
		int taiga = session.biomeNames.id("minecraft:taiga");
		for (int rz = rz0; rz < rz0 + n; rz++) {
			for (int rx = rx0; rx < rx0 + n; rx++) {
				int frx = rx;
				int frz = rz;
				session.workers.execute(() -> {
					RegionRaw raw = new RegionRaw(frx, frz);
					for (int lz = 0; lz < Coords.REGION_SIZE; lz++) {
						for (int lx = 0; lx < Coords.REGION_SIZE; lx++) {
							int x = (frx << Coords.REGION_SHIFT) + lx;
							int z = (frz << Coords.REGION_SHIFT) + lz;
							double h = height(x, z);
							int y = (int) Math.round(h);
							double wood = noise(x * 0.02 + 91, z * 0.02 - 17);
							if (y < 62) {
								raw.set(lx, lz, 62, y < 50 ? gravel : water, 62 - y, ocean, RegionRaw.KNOWN);
							} else if (y < 65) {
								raw.set(lx, lz, y, sand, 0, plains, RegionRaw.KNOWN);
							} else if (y > 150) {
								raw.set(lx, lz, y, snow, 0, peaks, RegionRaw.KNOWN);
							} else if (y > 112) {
								raw.set(lx, lz, y, stone, 0, peaks, RegionRaw.KNOWN);
							} else if (wood > 0.25) {
								int canopy = y + 5 + (int) (hash(x, z) * 4);
								raw.set(lx, lz, canopy, y > 90 ? spruce : leaves, 0, y > 90 ? taiga : forest, RegionRaw.KNOWN);
							} else {
								raw.set(lx, lz, y, grass, 0, plains, RegionRaw.KNOWN);
							}
						}
					}
					try {
						// drawn, written to disk and let go: the map is then read back like any big explored world
						layer.importRegion(raw);
					} catch (java.io.IOException e) {
						throw new java.io.UncheckedIOException(e);
					}
				});
			}
		}
	}

	/**
	 * Cave land for the underground map's performance test: a band of {@code n x n} regions of winding caverns (stone
	 * and deepslate floors stepping down, pools of water, a little lava, rock between), imported like any explored
	 * band.
	 */
	static void fillCaves(AtlasClient atlas, String dimension, int rx0, int rz0, int n, int band) {
		MapSession session = atlas.session();
		LayerData layer = session.dimension(dimension, false).under(band);
		int stone = session.blockNames.id("minecraft:stone");
		int deepslate = session.blockNames.id("minecraft:deepslate");
		int gravel = session.blockNames.id("minecraft:gravel");
		int tuff = session.blockNames.id("minecraft:tuff");
		int lava = session.blockNames.id("minecraft:lava");
		int dripstone = session.biomeNames.id("minecraft:dripstone_caves");
		int lush = session.biomeNames.id("minecraft:lush_caves");
		int top = DimensionData.bandTop(band);
		for (int rz = rz0; rz < rz0 + n; rz++) {
			for (int rx = rx0; rx < rx0 + n; rx++) {
				int frx = rx;
				int frz = rz;
				session.workers.execute(() -> {
					RegionRaw raw = new RegionRaw(frx, frz);
					for (int lz = 0; lz < Coords.REGION_SIZE; lz++) {
						for (int lx = 0; lx < Coords.REGION_SIZE; lx++) {
							int x = (frx << Coords.REGION_SHIFT) + lx;
							int z = (frz << Coords.REGION_SHIFT) + lz;
							double cave = Math.abs(noise(x * 0.012 + 7, z * 0.012 - 3)) + Math.abs(noise(x * 0.03 - 11, z * 0.03 + 5)) * 0.35;
							if (cave > 0.32) {
								// rock, seen as wall where it meets a cave, unknown deeper in
								if (cave < 0.36) {
									raw.set(lx, lz, top, 0, 0, 0, RegionRaw.KNOWN | RegionRaw.ROCK);
								}
								continue;
							}
							int floor = top - 2 - (int) (cave * 40) - (int) (hash(x >> 2, z >> 2) * 2);
							double pool = noise(x * 0.05 + 30, z * 0.05 + 30);
							int biome = noise(x * 0.004, z * 0.004) > 0 ? dripstone : lush;
							if (pool > 0.55) {
								raw.set(lx, lz, floor + 2, gravel, 3, biome, RegionRaw.KNOWN);
							} else if (pool < -0.7) {
								raw.set(lx, lz, floor, lava, 0, biome, RegionRaw.KNOWN);
							} else {
								raw.set(lx, lz, floor, floor < top - 12 ? deepslate : hash(x, z) > 0.8 ? tuff : stone, 0, biome, RegionRaw.KNOWN);
							}
						}
					}
					try {
						layer.importRegion(raw);
					} catch (java.io.IOException e) {
						throw new java.io.UncheckedIOException(e);
					}
				});
			}
		}
	}

	private static double height(int x, int z) {
		double h = 70;
		h += noise(x * 0.0009, z * 0.0009) * 70;
		h += noise(x * 0.004 + 13, z * 0.004 - 7) * 22;
		h += noise(x * 0.016 - 31, z * 0.016 + 5) * 6;
		double ridge = 1 - Math.abs(noise(x * 0.002 + 40, z * 0.002 + 40));
		h += Math.max(0, ridge - 0.55) * 160;
		return h;
	}

	private static double hash(int x, int z) {
		long h = x * 341873128712L + z * 132897987541L;
		h = (h ^ (h >>> 13)) * 1274126177L;
		return ((h ^ (h >>> 16)) & 0xFFFF) / 65535.0;
	}

	/** Smooth value noise, -1..1. */
	private static double noise(double x, double z) {
		int x0 = (int) Math.floor(x);
		int z0 = (int) Math.floor(z);
		double fx = x - x0;
		double fz = z - z0;
		double sx = fx * fx * (3 - 2 * fx);
		double sz = fz * fz * (3 - 2 * fz);
		double a = hash(x0, z0) * 2 - 1;
		double b = hash(x0 + 1, z0) * 2 - 1;
		double c = hash(x0, z0 + 1) * 2 - 1;
		double d = hash(x0 + 1, z0 + 1) * 2 - 1;
		return (a + (b - a) * sx) + ((c + (d - c) * sx) - (a + (b - a) * sx)) * sz;
	}
}
