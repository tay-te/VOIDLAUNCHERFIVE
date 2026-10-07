package dev.voidmc.gloam.far;

import dev.voidmc.gloam.Gloam;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;

/**
 * The far land as the air needs it: on a grid of {@value #CELL}-block cells, each cell's ground height, whether it is
 * water, and its biome's warmth and wetness. Filled from the far tiles VOID LOD draws (through its terrain listener, on
 * its thread), so the far mist settles where the far land is low and wet, and rain curtains reach down to the ground
 * they fall on. Without VOID LOD it stays empty and the far air does without it.
 *
 * <p>Kept in regions of {@value #REGION} x {@value #REGION} cells (4 km); regions far from the camera are let go.
 */
public final class FarLand {
	/** Cell size in blocks. */
	public static final int CELL = 128;
	static final int REGION = 32;
	private static final int REGION_BLOCKS = CELL * REGION;
	/** A height no land has: the cell is not known. */
	static final short UNKNOWN = Short.MIN_VALUE;

	/** One region's cells, row-major (x fastest). Written by the listener's thread, read by the mist's worker. */
	static final class Region {
		final short[] height = new short[REGION * REGION];
		/** The finest LOD cell size each cell was last filled from (a coarser tile does not overwrite a finer one). */
		final byte[] detail = new byte[REGION * REGION];
		final byte[] water = new byte[REGION * REGION];
		/** The biome's temperature, (t + 1) * 64, and downfall * 255. */
		final byte[] temperature = new byte[REGION * REGION];
		final byte[] downfall = new byte[REGION * REGION];
		/**
		 * What Expanse's countries say of each cell (filled by the mist's worker, not the listener): whether it has been
		 * asked (1), its lowness and nearness to water as 0..255; its wetness then stands in the downfall above.
		 */
		final byte[] country = new byte[REGION * REGION];
		final byte[] lowness = new byte[REGION * REGION];
		final byte[] nearWater = new byte[REGION * REGION];

		Region() {
			java.util.Arrays.fill(this.height, UNKNOWN);
			java.util.Arrays.fill(this.detail, Byte.MAX_VALUE);
		}
	}

	private final Map<Long, Region> regions = new ConcurrentHashMap<>();
	private final Map<ResourceKey<Biome>, float[]> climates = new ConcurrentHashMap<>();
	private volatile ResourceKey<Level> dimension;
	private volatile boolean listening;

	private static long key(int rx, int rz) {
		return ((long) rx << 32) | (rz & 0xFFFFFFFFL);
	}

	/** Starts listening to VOID LOD's far tiles, if it is here. */
	void listen() {
		if (this.listening) {
			return;
		}
		this.listening = true;
		try {
			Listener.start(this);
		} catch (LinkageError e) {
			Gloam.LOGGER.warn("VOID Gloam: VOID LOD's terrain API is not what Gloam expects; the far air goes without the far land", e);
		}
	}

	/** Whether any far land is known. */
	public boolean known() {
		return !this.regions.isEmpty();
	}

	/** Forgets everything (a new level). */
	void reset() {
		this.regions.clear();
		this.dimension = null;
	}

	/** Lets go of regions further than {@code reach} blocks from (x, z). */
	void trim(double x, double z, double reach) {
		double r = reach + REGION_BLOCKS;
		this.regions.keySet().removeIf(k -> {
			double cx = ((int) (k >> 32) + 0.5) * REGION_BLOCKS, cz = ((int) (long) k + 0.5) * REGION_BLOCKS;
			return Math.abs(cx - x) > r || Math.abs(cz - z) > r;
		});
	}

	/** The region holding cell (cx, cz), or null if nothing is known there. */
	Region region(int cx, int cz) {
		return this.regions.get(key(Math.floorDiv(cx, REGION), Math.floorDiv(cz, REGION)));
	}

	/** The ground height of cell (cx, cz), or {@link #UNKNOWN}. */
	short height(int cx, int cz) {
		Region r = this.region(cx, cz);
		return r == null ? UNKNOWN : r.height[Math.floorMod(cz, REGION) * REGION + Math.floorMod(cx, REGION)];
	}

	/** The ground height at block (x, z) from the nearest known cell, or {@code fallback}. */
	public float groundAt(double x, double z, float fallback) {
		short h = this.height((int) Math.floor(x / CELL), (int) Math.floor(z / CELL));
		return h == UNKNOWN ? fallback : h;
	}

	/** Takes in one far tile: its heights, top blocks and biomes, averaged into the cells it covers. */
	void accept(ResourceKey<Level> dimension, int minX, int minZ, int cell, int size, short[] heights, boolean[] water, ResourceKey<Biome>[] biomes) {
		if (this.dimension != dimension) {
			if (this.dimension != null) {
				this.regions.clear();
			}
			this.dimension = dimension;
		}
		int detail = Math.min(126, Integer.numberOfTrailingZeros(Math.max(1, cell)));
		int span = cell * size;
		int c0x = Math.floorDiv(minX, CELL), c0z = Math.floorDiv(minZ, CELL);
		int c1x = Math.floorDiv(minX + span - 1, CELL), c1z = Math.floorDiv(minZ + span - 1, CELL);
		for (int cz = c0z; cz <= c1z; cz++) {
			for (int cx = c0x; cx <= c1x; cx++) {
				// the tile's samples inside this cell (at least the one nearest its middle, for a coarse tile)
				int x0 = Math.max(0, Math.floorDiv(cx * CELL - minX, cell)), x1 = Math.min(size - 1, Math.floorDiv(cx * CELL + CELL - 1 - minX, cell));
				int z0 = Math.max(0, Math.floorDiv(cz * CELL - minZ, cell)), z1 = Math.min(size - 1, Math.floorDiv(cz * CELL + CELL - 1 - minZ, cell));
				if (x1 < x0 || z1 < z0) {
					continue;
				}
				// at most 8 x 8 of them: enough for an average
				int stepX = Math.max(1, (x1 - x0 + 1) / 8), stepZ = Math.max(1, (z1 - z0 + 1) / 8);
				float sum = 0, temp = 0, wet = 0;
				int n = 0, waters = 0, climates = 0;
				for (int z = z0; z <= z1; z += stepZ) {
					for (int x = x0; x <= x1; x += stepX) {
						int i = z * size + x;
						short h = heights[i];
						if (h == Short.MIN_VALUE) {
							continue;
						}
						sum += h;
						n++;
						if (water[i]) {
							waters++;
						}
						ResourceKey<Biome> biome = biomes[i];
						float[] c = biome == null ? null : this.climate(biome);
						if (c != null) {
							temp += c[0];
							wet += c[1];
							climates++;
						}
					}
				}
				if (n == 0) {
					continue;
				}
				int rx = Math.floorDiv(cx, REGION), rz = Math.floorDiv(cz, REGION);
				Region r = this.regions.computeIfAbsent(key(rx, rz), k -> new Region());
				int at = Math.floorMod(cz, REGION) * REGION + Math.floorMod(cx, REGION);
				if (r.height[at] != UNKNOWN && r.detail[at] < detail) {
					continue;
				}
				r.height[at] = (short) Math.round(sum / n);
				r.detail[at] = (byte) detail;
				r.water[at] = (byte) Math.round(255.0F * waters / n);
				// asked of Expanse again with the new land (its wetness takes the downfall's place once more)
				r.country[at] = 0;
				if (climates > 0) {
					r.temperature[at] = (byte) Math.round(Math.clamp((temp / climates + 1) * 64, 0, 255));
					r.downfall[at] = (byte) Math.round(Math.clamp(wet / climates, 0, 1) * 255);
				} else {
					r.temperature[at] = (byte) Math.round((0.7F + 1) * 64);
					r.downfall[at] = (byte) Math.round(0.6F * 255);
				}
			}
		}
	}

	/** The biome's base temperature and downfall, looked up once per biome in the client's registry. */
	private float[] climate(ResourceKey<Biome> key) {
		float[] c = this.climates.get(key);
		if (c != null) {
			return c;
		}
		var level = Minecraft.getInstance().level;
		if (level == null) {
			return null;
		}
		Holder<Biome> holder = level.registryAccess().lookupOrThrow(Registries.BIOME).get(key).orElse(null);
		if (holder == null) {
			return null;
		}
		Biome biome = holder.value();
		c = new float[]{biome.getBaseTemperature(), biome.climateSettings.downfall()};
		this.climates.put(key, c);
		return c;
	}

	/** VOID LOD's side, in a class of its own, so the JVM only resolves it once VOID LOD is known to be there. */
	private static final class Listener {
		static void start(FarLand land) {
			dev.voidmc.lod.api.LodApi.addVisibleTerrainListener(tile -> {
				int n = tile.size() * tile.size();
				boolean[] water = new boolean[n];
				@SuppressWarnings("unchecked")
				ResourceKey<Biome>[] biomes = new ResourceKey[n];
				BlockState[] blocks = tile.blockPalette();
				Map<BlockState, Boolean> wet = new HashMap<>();
				for (int i = 0; i < n; i++) {
					int b = tile.blocks()[i];
					if (b >= 0 && b < blocks.length) {
						water[i] = wet.computeIfAbsent(blocks[b], s -> s.getFluidState().is(Fluids.WATER) || s.getFluidState().is(Fluids.FLOWING_WATER));
					}
					int bi = tile.biomes()[i];
					if (bi >= 0 && bi < tile.biomePalette().length) {
						biomes[i] = tile.biomePalette()[bi];
					}
				}
				land.accept(tile.dimension(), tile.minX(), tile.minZ(), tile.cell(), tile.size(), tile.heights(), water, biomes);
			});
		}
	}
}
