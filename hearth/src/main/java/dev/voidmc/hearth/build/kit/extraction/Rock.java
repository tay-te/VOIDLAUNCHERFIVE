package dev.voidmc.hearth.build.kit.extraction;

import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Noise;
import dev.voidmc.hearth.build.draft.States;
import java.util.Locale;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * The rock an excavation is cut in, as the survey named it (the {@code rock} param: the host rock's block id, e.g.
 * {@code minecraft:andesite}, {@code minecraft:calcite} for the karst's limestone, {@code minecraft:cobbled_deepslate}
 * below y 0), else the rock the culture's land is made of. The working never <i>places</i> its own rock in the faces —
 * the world's rock is the face — but its stair treads, the blocks waiting on the stacks, the crane's load, the spoil and
 * the dry-stone of a portal are cut from it.
 *
 * @param natural the rock as it lies (the face; the blanks on the stacks)
 * @param rough   the rock broken (rubble walls, spoil)
 * @param dressed the rock cut square (finished ashlar on the pallets)
 * @param stairs  treads cut into the rock (a flight between benches)
 * @param slab    a half block of it
 * @param spoil   what the waste tip is made of, commonest first
 * @param kind    a short name ("stone", "sandstone", "limestone"...)
 */
public record Rock(Block natural, Block rough, Block dressed, Block stairs, Block slab, Block[] spoil, String kind) {

	/** The rock of a draft: its {@code rock} param, else its culture's land. */
	public static Rock of(Draft d) {
		String id = d.param("rock", "");
		if (!id.isEmpty()) {
			return named(id);
		}
		Culture c = d.style().culture() == null ? Culture.TEMPERATE : d.style().culture();
		return switch (c) {
			case DESERT -> named("minecraft:sandstone");
			case MESA -> named("minecraft:red_sandstone");
			case KARST -> named("minecraft:calcite");
			case BAYOU -> named("minecraft:packed_mud");
			case ALPINE, NORTHERN -> named(Noise.unit(d.seed(), 3, 1, 4) < 0.5 ? "minecraft:andesite" : "minecraft:cobblestone");
			case MOORLAND, MARITIME -> named(Noise.unit(d.seed(), 3, 1, 4) < 0.5 ? "minecraft:granite" : "minecraft:cobblestone");
			case TROPICAL -> named("minecraft:cobblestone");
			default -> named(Noise.unit(d.seed(), 3, 1, 4) < 0.25 ? "minecraft:andesite" : "minecraft:cobblestone");
		};
	}

	/** The rock for a block id (any namespace; unknown rock reads as stone). */
	public static Rock named(String id) {
		String p = id.substring(id.indexOf(':') + 1).toLowerCase(Locale.ROOT);
		Block gravel = Blocks.GRAVEL;
		Block coarse = Blocks.COARSE_DIRT;
		if (p.equals("clay")) {
			// the bank's clay under its overburden: the spoil is the topsoil and the stones thrown out of it
			return new Rock(Blocks.CLAY, Blocks.CLAY, Blocks.BRICKS, Blocks.MUD_BRICK_STAIRS, Blocks.MUD_BRICK_SLAB,
				new Block[]{Blocks.DIRT, coarse, Blocks.DIRT, gravel, Blocks.ROOTED_DIRT}, "clay");
		}
		if (p.equals("sand") || p.equals("red_sand")) {
			boolean red = p.startsWith("red");
			return new Rock(red ? Blocks.RED_SAND : Blocks.SAND, red ? Blocks.RED_SAND : Blocks.SAND, red ? Blocks.CUT_RED_SANDSTONE : Blocks.CUT_SANDSTONE,
				red ? Blocks.RED_SANDSTONE_STAIRS : Blocks.SANDSTONE_STAIRS, red ? Blocks.RED_SANDSTONE_SLAB : Blocks.SANDSTONE_SLAB,
				new Block[]{Blocks.DIRT, coarse, gravel, Blocks.DIRT, red ? Blocks.RED_SAND : Blocks.SAND}, red ? "red sand" : "sand");
		}
		if (p.contains("red_sandstone") || p.contains("terracotta")) {
			return new Rock(Blocks.RED_SANDSTONE, Blocks.RED_SANDSTONE, Blocks.CUT_RED_SANDSTONE, Blocks.RED_SANDSTONE_STAIRS, Blocks.RED_SANDSTONE_SLAB,
				new Block[]{Blocks.RED_SAND, Blocks.RED_SAND, coarse, Blocks.RED_SANDSTONE, Blocks.TERRACOTTA}, "red sandstone");
		}
		if (p.contains("sandstone")) {
			return new Rock(Blocks.SANDSTONE, Blocks.SANDSTONE, Blocks.CUT_SANDSTONE, Blocks.SANDSTONE_STAIRS, Blocks.SANDSTONE_SLAB,
				new Block[]{Blocks.SAND, Blocks.SAND, gravel, Blocks.SANDSTONE, coarse}, "sandstone");
		}
		if (p.contains("limestone") || p.contains("calcite")) {
			Block lime = States.block("expanse:limestone", Blocks.CALCITE);
			return new Rock(lime, lime, States.block("expanse:limestone_bricks", Blocks.STONE_BRICKS),
				States.block("expanse:limestone_stairs", Blocks.DIORITE_STAIRS), States.block("expanse:limestone_slab", Blocks.DIORITE_SLAB),
				new Block[]{gravel, Blocks.CALCITE, coarse, gravel, Blocks.DIORITE}, "limestone");
		}
		if (p.contains("deepslate")) {
			return new Rock(Blocks.DEEPSLATE, Blocks.COBBLED_DEEPSLATE, Blocks.DEEPSLATE_BRICKS, Blocks.COBBLED_DEEPSLATE_STAIRS, Blocks.COBBLED_DEEPSLATE_SLAB,
				new Block[]{gravel, Blocks.COBBLED_DEEPSLATE, gravel, Blocks.TUFF, coarse}, "deepslate");
		}
		if (p.contains("tuff")) {
			return new Rock(Blocks.TUFF, Blocks.TUFF, Blocks.TUFF_BRICKS, Blocks.TUFF_STAIRS, Blocks.TUFF_SLAB,
				new Block[]{gravel, Blocks.TUFF, coarse, gravel, Blocks.ANDESITE}, "tuff");
		}
		if (p.contains("blackstone") || p.contains("basalt")) {
			return new Rock(Blocks.BLACKSTONE, Blocks.BLACKSTONE, Blocks.POLISHED_BLACKSTONE_BRICKS, Blocks.BLACKSTONE_STAIRS, Blocks.BLACKSTONE_SLAB,
				new Block[]{gravel, Blocks.BLACKSTONE, Blocks.TUFF, gravel, coarse}, "blackstone");
		}
		if (p.contains("mud")) {
			return new Rock(Blocks.PACKED_MUD, Blocks.PACKED_MUD, Blocks.MUD_BRICKS, Blocks.MUD_BRICK_STAIRS, Blocks.MUD_BRICK_SLAB,
				new Block[]{Blocks.MUD, coarse, Blocks.DIRT, Blocks.PACKED_MUD, gravel}, "mud");
		}
		if (p.contains("granite")) {
			return new Rock(Blocks.GRANITE, Blocks.GRANITE, Blocks.POLISHED_GRANITE, Blocks.GRANITE_STAIRS, Blocks.GRANITE_SLAB,
				new Block[]{gravel, Blocks.GRANITE, Blocks.COBBLESTONE, Blocks.GRANITE, coarse}, "granite");
		}
		if (p.contains("diorite")) {
			return new Rock(Blocks.DIORITE, Blocks.DIORITE, Blocks.POLISHED_DIORITE, Blocks.DIORITE_STAIRS, Blocks.DIORITE_SLAB,
				new Block[]{gravel, Blocks.DIORITE, Blocks.COBBLESTONE, Blocks.DIORITE, coarse}, "diorite");
		}
		if (p.contains("andesite")) {
			return new Rock(Blocks.ANDESITE, Blocks.ANDESITE, Blocks.POLISHED_ANDESITE, Blocks.ANDESITE_STAIRS, Blocks.ANDESITE_SLAB,
				new Block[]{gravel, Blocks.ANDESITE, Blocks.COBBLESTONE, Blocks.ANDESITE, coarse}, "andesite");
		}
		return new Rock(Blocks.STONE, Blocks.COBBLESTONE, Blocks.STONE_BRICKS, Blocks.STONE_STAIRS, Blocks.COBBLESTONE_SLAB,
			new Block[]{gravel, Blocks.COBBLESTONE, Blocks.COBBLESTONE, Blocks.ANDESITE, coarse}, "stone");
	}

	/** A spoil block for a spot, mixed in patches (gravel and broken rock, the odd lump of earth). */
	public Block spoilAt(long seed, int x, int y, int z) {
		double u = Noise.value(seed ^ 0x5B011, x / 2.2, y / 1.6, z / 2.2);
		double v = Noise.unit(seed ^ 0x5B012, x, y, z);
		int i = u < 0.3 ? 0 : u < 0.55 ? 1 : u < 0.72 ? 2 : u < 0.88 ? 3 : 4;
		if (v < 0.12) {
			i = (i + 1) % this.spoil.length;
		}
		return this.spoil[i];
	}

	/** Whether the rock is soft enough to dig with a shovel (sand, mud): its faces stand back further. */
	public boolean soft() {
		return this.natural == Blocks.PACKED_MUD;
	}
}
