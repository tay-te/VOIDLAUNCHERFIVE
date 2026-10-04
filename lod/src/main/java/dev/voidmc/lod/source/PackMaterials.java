package dev.voidmc.lod.source;

import java.util.Arrays;
import java.util.function.IntUnaryOperator;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;

/**
 * What kind of block each material id is, in the categories Distant Horizons gives shader packs (leaves, stone, wood,
 * dirt, grass, sand, snow, water, lava, glowing...): a pack's dh_terrain shades by them, waving nothing, letting light
 * through leaves, making lava and lamps glow. Worked out once per id, from the block state behind it.
 */
public final class PackMaterials implements IntUnaryOperator {
	// Iris's DH_BLOCK_* values
	private static final int UNKNOWN = 0, LEAVES = 1, STONE = 2, WOOD = 3, METAL = 4, DIRT = 5, LAVA = 6, DEEPSLATE = 7, SNOW = 8,
		SAND = 9, TERRACOTTA = 10, NETHER_STONE = 11, WATER = 12, GRASS = 13, ILLUMINATED = 15;

	private final Materials materials;
	private volatile byte[] known = new byte[0];

	public PackMaterials(Materials materials) {
		this.materials = materials;
	}

	@Override
	public int applyAsInt(int id) {
		byte[] known = this.known;
		if (id < known.length && known[id] != 0) {
			return known[id] - 1;
		}
		Materials.Face face = this.materials.face(id);
		int kind = face == null ? STONE : kind(face.state());
		synchronized (this) {
			byte[] grown = this.known;
			if (id >= grown.length) {
				grown = Arrays.copyOf(grown, Math.max(id + 1, grown.length * 2));
			}
			grown[id] = (byte) (kind + 1);
			this.known = grown;
		}
		return kind;
	}

	static int kind(BlockState state) {
		if (state.getFluidState().is(Fluids.WATER) || state.is(Blocks.WATER)) {
			return WATER;
		}
		if (state.getFluidState().is(Fluids.LAVA) || state.is(Blocks.LAVA)) {
			return LAVA;
		}
		if (state.getLightEmission() > 0) {
			return ILLUMINATED;
		}
		if (state.is(BlockTags.LEAVES)) {
			return LEAVES;
		}
		if (state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.MYCELIUM)) {
			return GRASS;
		}
		if (state.is(Blocks.SNOW) || state.is(Blocks.SNOW_BLOCK) || state.is(Blocks.POWDER_SNOW)) {
			return SNOW;
		}
		if (state.is(BlockTags.SAND)) {
			return SAND;
		}
		if (state.is(BlockTags.TERRACOTTA)) {
			return TERRACOTTA;
		}
		if (state.is(BlockTags.DIRT)) {
			return DIRT;
		}
		if (state.is(BlockTags.LOGS)) {
			return WOOD;
		}
		if (state.is(Blocks.NETHERRACK) || state.is(BlockTags.NYLIUM) || state.is(Blocks.SOUL_SAND) || state.is(Blocks.SOUL_SOIL)) {
			return NETHER_STONE;
		}
		String path = state.getBlock().builtInRegistryHolder().key().identifier().getPath();
		if (path.contains("deepslate")) {
			return DEEPSLATE;
		}
		if (path.contains("iron") || path.contains("copper") || path.contains("gold_block")) {
			return METAL;
		}
		return state.isAir() ? UNKNOWN : STONE;
	}
}
