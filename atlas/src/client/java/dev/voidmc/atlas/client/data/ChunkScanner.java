package dev.voidmc.atlas.client.data;

import dev.voidmc.atlas.core.Coords;
import dev.voidmc.atlas.core.NamePalette;
import dev.voidmc.atlas.core.RegionRaw;
import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.MapColor;

/**
 * Reads a chunk's columns into a raw region, on a worker thread. The client's chunks are only read, never written,
 * and a column read while the client thread changes it is read again when that change's own rescan comes.
 *
 * <p>The surface: each column is followed down from its highest block, past plants, torches and thin snow (which
 * only colours the ground), to the canopy, a fluid's surface, or the first solid block; under water, on down to the
 * bed, for the depth. A slice (underground, the Nether): from the top of the slice down to the first open space, and
 * from there down to its floor; a column with no open space in the slice is rock.
 *
 * <p>Not thread-safe: one per worker thread.
 */
public final class ChunkScanner {
	/** How far down a water column is followed to its bed. */
	private static final int MAX_DEPTH = 96;
	/** How far below a slice its floors are looked for. */
	private static final int FLOOR_REACH = 24;

	private final NamePalette blockNames;
	private final NamePalette biomeNames;
	private final Map<Block, Integer> blockIds = new IdentityHashMap<>();
	private final Map<Object, Integer> biomeIds = new IdentityHashMap<>();
	private int snowId = -1;

	public ChunkScanner(NamePalette blockNames, NamePalette biomeNames) {
		this.blockNames = blockNames;
		this.biomeNames = biomeNames;
	}

	private int blockId(Block block) {
		Integer id = this.blockIds.get(block);
		if (id == null) {
			id = this.blockNames.id(BuiltInRegistries.BLOCK.getKey(block).toString());
			this.blockIds.put(block, id);
		}
		return id;
	}

	private int biomeId(Holder<Biome> holder) {
		Integer id = this.biomeIds.get(holder);
		if (id == null) {
			id = holder.unwrapKey().map(k -> this.biomeNames.id(k.identifier().toString())).orElse(NamePalette.NONE);
			this.biomeIds.put(holder, id);
		}
		return id;
	}

	private static BlockState state(LevelChunkSection[] sections, int minY, int x, int y, int z) {
		int index = (y - minY) >> 4;
		if (index < 0 || index >= sections.length) {
			return Blocks.AIR.defaultBlockState();
		}
		return sections[index].getBlockState(x, y & 15, z);
	}

	private final Map<BlockState, Boolean> visible = new IdentityHashMap<>();

	/** Whether a block shows on the map at all (barriers, light blocks and structure voids do not). */
	private boolean visible(BlockState state) {
		Boolean v = this.visible.get(state);
		if (v == null) {
			try {
				v = state.getMapColor(null, net.minecraft.core.BlockPos.ZERO) != MapColor.NONE || !state.getFluidState().isEmpty();
			} catch (RuntimeException e) {
				v = true;
			}
			this.visible.put(state, v);
		}
		return v;
	}

	/** Reads the chunk's surface into {@code raw}; returns whether any column changed. */
	public boolean surface(LevelChunk chunk, RegionRaw raw) {
		LevelChunkSection[] sections = chunk.getSections();
		int minY = chunk.getMinY();
		int baseX = chunk.getPos().getMinBlockX();
		int baseZ = chunk.getPos().getMinBlockZ();
		boolean changed = false;
		if (this.snowId < 0) {
			this.snowId = this.blockId(Blocks.SNOW_BLOCK);
		}
		for (int z = 0; z < 16; z++) {
			for (int x = 0; x < 16; x++) {
				int top = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
				int lx = Coords.inRegion(baseX + x);
				int lz = Coords.inRegion(baseZ + z);
				if (top < minY) {
					// nothing at all (the End's void): explored, but empty
					changed |= raw.set(lx, lz, minY, NamePalette.NONE, 0, NamePalette.NONE, RegionRaw.KNOWN | RegionRaw.ROCK);
					continue;
				}
				int waterTop = Integer.MIN_VALUE;
				boolean snow = false;
				BlockState ground = null;
				int groundY = minY;
				int floor = Math.max(minY, top - 320);
				for (int y = top; y >= floor; y--) {
					BlockState state = state(sections, minY, x, y, z);
					if (state.isAir()) {
						continue;
					}
					FluidState fluid = state.getFluidState();
					if (!fluid.isEmpty() && !state.isSolid()) {
						if (fluid.is(FluidTags.LAVA)) {
							ground = Blocks.LAVA.defaultBlockState();
							groundY = y;
							break;
						}
						if (waterTop == Integer.MIN_VALUE) {
							waterTop = y;
						}
						if (waterTop - y > MAX_DEPTH) {
							ground = Blocks.WATER.defaultBlockState();
							groundY = y;
							break;
						}
						continue;
					}
					if (waterTop == Integer.MIN_VALUE && state.is(BlockTags.LEAVES)) {
						ground = state;
						groundY = y;
						break;
					}
					if (!state.isSolid() || !this.visible(state)) {
						if (state.getBlock() instanceof SnowLayerBlock) {
							snow = true;
						}
						continue;
					}
					ground = state;
					groundY = y;
					break;
				}
				int biomeY = waterTop != Integer.MIN_VALUE ? waterTop : groundY;
				int biome = this.biomeId(chunk.getNoiseBiome(QuartPos.fromBlock(baseX + x), QuartPos.fromBlock(biomeY), QuartPos.fromBlock(baseZ + z)));
				if (ground == null) {
					changed |= raw.set(lx, lz, minY, NamePalette.NONE, 0, biome, RegionRaw.KNOWN | RegionRaw.ROCK);
					continue;
				}
				int block = snow && waterTop == Integer.MIN_VALUE ? this.snowId : this.blockId(ground.getBlock());
				if (waterTop != Integer.MIN_VALUE) {
					changed |= raw.set(lx, lz, waterTop, block, Math.max(1, waterTop - groundY), biome, RegionRaw.KNOWN);
				} else {
					changed |= raw.set(lx, lz, groundY, block, 0, biome, RegionRaw.KNOWN);
				}
			}
		}
		return changed;
	}

	/**
	 * Reads the slice {@code [band * 16, band * 16 + 15]} of a chunk: each column's first open space from the top of
	 * the slice, and the floor under it. Returns whether any column changed.
	 */
	public boolean slice(LevelChunk chunk, RegionRaw raw, int band) {
		LevelChunkSection[] sections = chunk.getSections();
		int minY = chunk.getMinY();
		int maxY = minY + chunk.getHeight() - 1;
		int sliceTop = Math.min(maxY, band * 16 + 15);
		int sliceBottom = Math.max(minY, band * 16);
		int baseX = chunk.getPos().getMinBlockX();
		int baseZ = chunk.getPos().getMinBlockZ();
		boolean changed = false;
		for (int z = 0; z < 16; z++) {
			for (int x = 0; x < 16; x++) {
				int lx = Coords.inRegion(baseX + x);
				int lz = Coords.inRegion(baseZ + z);
				int open = Integer.MIN_VALUE;
				for (int y = sliceTop; y >= sliceBottom; y--) {
					BlockState state = state(sections, minY, x, y, z);
					if (state.isAir() || (!state.isSolid() && state.getFluidState().isEmpty())) {
						open = y;
						break;
					}
					if (!state.getFluidState().isEmpty() && !state.isSolid()) {
						open = y;
						break;
					}
				}
				int biome = this.biomeId(chunk.getNoiseBiome(QuartPos.fromBlock(baseX + x), QuartPos.fromBlock(Math.max(sliceBottom, minY)), QuartPos.fromBlock(baseZ + z)));
				if (open == Integer.MIN_VALUE) {
					changed |= raw.set(lx, lz, sliceTop, NamePalette.NONE, 0, biome, RegionRaw.KNOWN | RegionRaw.ROCK);
					continue;
				}
				int waterTop = Integer.MIN_VALUE;
				BlockState ground = null;
				int groundY = open;
				int bottom = Math.max(minY, sliceBottom - FLOOR_REACH);
				for (int y = open; y >= bottom; y--) {
					BlockState state = state(sections, minY, x, y, z);
					if (state.isAir()) {
						continue;
					}
					FluidState fluid = state.getFluidState();
					if (!fluid.isEmpty() && !state.isSolid()) {
						if (fluid.is(FluidTags.LAVA)) {
							ground = Blocks.LAVA.defaultBlockState();
							groundY = y;
							break;
						}
						if (waterTop == Integer.MIN_VALUE) {
							waterTop = y;
						}
						continue;
					}
					if (!state.isSolid() || !this.visible(state)) {
						continue;
					}
					ground = state;
					groundY = y;
					break;
				}
				if (ground == null) {
					// open all the way down past the slice: a shaft or a ravine, drawn as its deepest point
					changed |= raw.set(lx, lz, bottom, NamePalette.NONE, 0, biome, RegionRaw.KNOWN);
					continue;
				}
				int block = this.blockId(ground.getBlock());
				if (waterTop != Integer.MIN_VALUE) {
					changed |= raw.set(lx, lz, waterTop, block, Math.max(1, waterTop - groundY), biome, RegionRaw.KNOWN);
				} else {
					changed |= raw.set(lx, lz, groundY, block, 0, biome, RegionRaw.KNOWN);
				}
			}
		}
		return changed;
	}
}
