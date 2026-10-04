package dev.voidmc.hearth.build.kit.age;

import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * Masonry weathering: the mossy and cracked members of vanilla's stone families, swapped in for the clean ones as a
 * building ages (cobble to mossy cobble, stone bricks to mossy or cracked, deepslate bricks and tiles to cracked, mud
 * bricks crumbling back to packed mud). Shapes and facings are kept, so a stair stays the same stair.
 */
public final class Weather {
	private static final Map<Block, Block> MOSS = new IdentityHashMap<>();
	private static final Map<Block, Block> CRACK = new IdentityHashMap<>();

	static {
		moss(Blocks.COBBLESTONE, Blocks.MOSSY_COBBLESTONE);
		moss(Blocks.COBBLESTONE_STAIRS, Blocks.MOSSY_COBBLESTONE_STAIRS);
		moss(Blocks.COBBLESTONE_SLAB, Blocks.MOSSY_COBBLESTONE_SLAB);
		moss(Blocks.COBBLESTONE_WALL, Blocks.MOSSY_COBBLESTONE_WALL);
		moss(Blocks.STONE_BRICKS, Blocks.MOSSY_STONE_BRICKS);
		moss(Blocks.STONE_BRICK_STAIRS, Blocks.MOSSY_STONE_BRICK_STAIRS);
		moss(Blocks.STONE_BRICK_SLAB, Blocks.MOSSY_STONE_BRICK_SLAB);
		moss(Blocks.STONE_BRICK_WALL, Blocks.MOSSY_STONE_BRICK_WALL);
		crack(Blocks.STONE_BRICKS, Blocks.CRACKED_STONE_BRICKS);
		crack(Blocks.DEEPSLATE_BRICKS, Blocks.CRACKED_DEEPSLATE_BRICKS);
		crack(Blocks.DEEPSLATE_TILES, Blocks.CRACKED_DEEPSLATE_TILES);
		crack(Blocks.POLISHED_BLACKSTONE_BRICKS, Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS);
		crack(Blocks.NETHER_BRICKS, Blocks.CRACKED_NETHER_BRICKS);
		crack(Blocks.MUD_BRICKS, Blocks.PACKED_MUD);
		crack(Blocks.POLISHED_DEEPSLATE, Blocks.COBBLED_DEEPSLATE);
	}

	private Weather() {
	}

	private static void moss(Block from, Block to) {
		MOSS.put(from, to);
	}

	private static void crack(Block from, Block to) {
		CRACK.put(from, to);
	}

	/** Whether a block has a weathered member at all. */
	public static boolean weathers(BlockState s) {
		return MOSS.containsKey(s.getBlock()) || CRACK.containsKey(s.getBlock());
	}

	/**
	 * The weathered state of a block: mossy where moss grows and {@code mossy} says so, else cracked (where the family has
	 * a cracked member), else mossy anyway; null when the block does not weather.
	 */
	public static @Nullable BlockState weathered(BlockState s, boolean mossy, boolean mossGrows) {
		Block m = mossGrows ? MOSS.get(s.getBlock()) : null;
		Block c = CRACK.get(s.getBlock());
		Block to = mossy && m != null ? m : c != null ? c : m;
		return to == null ? null : to.withPropertiesOf(s);
	}

	/** The block's masonry kind for rubble and wear: whether it is laid stone, brick, earth or tile rather than timber. */
	public static boolean masonry(BlockState s) {
		String n = name(s.getBlock());
		return n.contains("stone") || n.contains("brick") || n.contains("cobble") || n.contains("terracotta") || n.contains("deepslate")
			|| n.contains("tuff") || n.contains("andesite") || n.contains("diorite") || n.contains("granite") || n.contains("calcite")
			|| n.contains("mud") || n.contains("quartz") || n.contains("basalt") || n.contains("blackstone") || n.contains("prismarine")
			|| n.contains("concrete") || n.contains("tile") || n.contains("limestone") || n.contains("slate") || n.contains("sandstone")
			|| n.contains("clay") || n.contains("dripstone") || n.contains("mushroom_stem") || n.contains("bone_block");
	}

	/** Timber (planks, logs, boards, bamboo, wattle): rots and burns rather than crumbling. */
	public static boolean timber(BlockState s) {
		String n = name(s.getBlock());
		return n.contains("planks") || n.endsWith("_log") || n.endsWith("_wood") || n.contains("stripped") || n.contains("bamboo")
			|| n.endsWith("_stairs") && woodName(n) || n.endsWith("_slab") && woodName(n) || n.contains("fence") || n.contains("trapdoor")
			|| n.contains("_door") || n.contains("stem") && !n.contains("mushroom") || n.contains("hyphae");
	}

	/** Soft stuff that rots fastest: wool, hay and thatch, moss. */
	public static boolean soft(BlockState s) {
		String n = name(s.getBlock());
		return n.contains("wool") || n.contains("hay") || n.contains("carpet") || n.contains("thatch") || n.contains("moss") || n.contains("dried_kelp");
	}

	private static boolean woodName(String n) {
		for (String w : new String[]{"oak", "spruce", "birch", "jungle", "acacia", "dark_oak", "mangrove", "cherry", "pale_oak", "bamboo", "crimson",
			"warped"}) {
			if (n.startsWith(w + "_") || n.contains(":" + w + "_")) {
				return true;
			}
		}
		return false;
	}

	public static String name(Block b) {
		return net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(b).getPath();
	}
}
