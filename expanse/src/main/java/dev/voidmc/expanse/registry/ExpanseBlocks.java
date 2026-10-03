package dev.voidmc.expanse.registry;

import dev.voidmc.expanse.Expanse;
import dev.voidmc.expanse.block.FrostbloomBlock;
import dev.voidmc.expanse.block.PalmSaplingBlock;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ColorRGBA;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.AmethystBlock;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.FlowerBlock;
import net.minecraft.world.level.block.FlowerPotBlock;
import net.minecraft.world.level.block.HangingMossBlock;
import net.minecraft.world.level.block.SandBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.storage.loot.providers.number.ints.ContextIntProviders;

public final class ExpanseBlocks {
	// ------------------------------------------------------------------ trees
	// Leaf particle colours are ARGB; they are the falling leaves, so they match each texture's mid-tone.

	public static final WoodSet REDWOOD = new WoodSet("redwood", MapColor.TERRACOTTA_RED, MapColor.TERRACOTTA_BROWN,
		0xFF3F6B34, 0, WoodSet.sapling(ExpanseTrees.REDWOOD_GROWER));
	public static final WoodSet WILLOW = new WoodSet("willow", MapColor.TERRACOTTA_LIGHT_GREEN, MapColor.TERRACOTTA_GRAY,
		0xFF8DB05A, 0, WoodSet.sapling(ExpanseTrees.WILLOW_GROWER));
	public static final WoodSet PALM = new WoodSet("palm", MapColor.SAND, MapColor.TERRACOTTA_LIGHT_GRAY,
		0xFF5E9E3A, 0, p -> new PalmSaplingBlock(ExpanseTrees.PALM_GROWER, p));
	public static final WoodSet LUMEN = new WoodSet("lumen", MapColor.COLOR_LIGHT_BLUE, MapColor.QUARTZ,
		0xFF5FE6D2, 9, WoodSet.sapling(ExpanseTrees.LUMEN_GROWER));
	public static final WoodSet BAOBAB = new WoodSet("baobab", MapColor.TERRACOTTA_PINK, MapColor.TERRACOTTA_LIGHT_GRAY,
		0xFF7E9440, 0, WoodSet.sapling(ExpanseTrees.BAOBAB_GROWER));
	public static final WoodSet WISTERIA = new WoodSet("wisteria", MapColor.TERRACOTTA_PURPLE, MapColor.TERRACOTTA_GRAY,
		0xFFB487E0, 0, WoodSet.sapling(ExpanseTrees.WISTERIA_GROWER));
	public static final Block AZURE_WISTERIA_LEAVES = WoodSet.leaves("azure_wisteria_leaves", 0xFF86A9EE, 0);

	// ------------------------------------------------------------------ stone

	public static final Block LIMESTONE = registerWithItem("limestone", Block::new,
		BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_WHITE).instrument(NoteBlockInstrument.BASEDRUM)
			.requiresCorrectToolForDrops().strength(1.25F, 6.0F).sound(SoundType.TUFF));
	public static final Block LIMESTONE_STAIRS = stairs("limestone_stairs", LIMESTONE);
	public static final Block LIMESTONE_SLAB = slab("limestone_slab", LIMESTONE);
	public static final Block LIMESTONE_WALL = wall("limestone_wall", LIMESTONE);
	public static final Block POLISHED_LIMESTONE = copy("polished_limestone", LIMESTONE);
	public static final Block LIMESTONE_BRICKS = copy("limestone_bricks", LIMESTONE);
	public static final Block LIMESTONE_BRICK_STAIRS = stairs("limestone_brick_stairs", LIMESTONE_BRICKS);
	public static final Block LIMESTONE_BRICK_SLAB = slab("limestone_brick_slab", LIMESTONE_BRICKS);
	public static final Block LIMESTONE_BRICK_WALL = wall("limestone_brick_wall", LIMESTONE_BRICKS);
	public static final Block CHISELED_LIMESTONE = copy("chiseled_limestone", LIMESTONE);
	public static final Block MOSSY_LIMESTONE = copy("mossy_limestone", LIMESTONE);

	public static final Block OPAL_SAND = registerWithItem("opal_sand", p -> new SandBlock(new ColorRGBA(0xE9C6D6), p),
		BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PINK).instrument(NoteBlockInstrument.SNARE).strength(0.5F).sound(SoundType.SAND));
	public static final Block OPAL_SANDSTONE = registerWithItem("opal_sandstone", Block::new,
		BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PINK).instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops().strength(0.8F));
	public static final Block OPAL_SANDSTONE_STAIRS = stairs("opal_sandstone_stairs", OPAL_SANDSTONE);
	public static final Block OPAL_SANDSTONE_SLAB = slab("opal_sandstone_slab", OPAL_SANDSTONE);
	public static final Block OPAL_SANDSTONE_WALL = wall("opal_sandstone_wall", OPAL_SANDSTONE);
	public static final Block SMOOTH_OPAL_SANDSTONE = copy("smooth_opal_sandstone", OPAL_SANDSTONE);
	public static final Block CUT_OPAL_SANDSTONE = copy("cut_opal_sandstone", OPAL_SANDSTONE);
	public static final Block CHISELED_OPAL_SANDSTONE = copy("chiseled_opal_sandstone", OPAL_SANDSTONE);

	public static final Block PRISMITE_BLOCK = registerWithItem("prismite_block", AmethystBlock::new,
		BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_LIGHT_BLUE).strength(1.5F).sound(SoundType.AMETHYST)
			.requiresCorrectToolForDrops().lightLevel(s -> 6).emissiveRendering(s -> true));
	public static final Block PRISMITE_CLUSTER = registerWithItem("prismite_cluster", p -> new AmethystClusterBlock(7.0F, 10.0F, p),
		BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_LIGHT_BLUE).forceSolidOn().noOcclusion().sound(SoundType.AMETHYST_CLUSTER)
			.strength(1.5F).lightLevel(s -> 8).emissiveRendering(s -> true).pushReaction(PushReaction.POPPED));

	// ------------------------------------------------------------------ plants

	public static final Block HEATHER = flower("heather", p -> new FlowerBlock(MobEffects.REGENERATION, 6.0F, p), 0);
	public static final Block EDELWEISS = flower("edelweiss", p -> new FlowerBlock(MobEffects.RESISTANCE, 5.0F, p), 0);
	public static final Block FROSTBLOOM = flower("frostbloom", p -> new FrostbloomBlock(MobEffects.FIRE_RESISTANCE, 6.0F, p), 7);
	public static final Block GLOWCAP = flower("glowcap", p -> new FlowerBlock(MobEffects.NIGHT_VISION, 8.0F, p), 10);
	public static final Block CATTAIL = registerWithItem("cattail", DoublePlantBlock::new,
		BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).replaceable().noCollision().instabreak().sound(SoundType.GRASS)
			.offsetType(BlockBehaviour.OffsetType.XZ).ignitedByLava().pushReaction(PushReaction.POPPED),
		p -> p.compostable(ContextIntProviders.COMPOSTABLE_MEDIUM));
	public static final Block SPANISH_MOSS = registerWithItem("spanish_moss", HangingMossBlock::new,
		BlockBehaviour.Properties.of().ignitedByLava().mapColor(MapColor.COLOR_LIGHT_GREEN).noCollision().sound(SoundType.MOSS_CARPET)
			.pushReaction(PushReaction.POPPED),
		p -> p.compostable(ContextIntProviders.COMPOSTABLE_LOW));

	// Wisteria racemes, hung under the canopy by HangingCascadeDecorator.
	public static final Block WISTERIA_BLOSSOMS = blossoms("wisteria_blossoms", MapColor.COLOR_PURPLE);
	public static final Block AZURE_WISTERIA_BLOSSOMS = blossoms("azure_wisteria_blossoms", MapColor.COLOR_LIGHT_BLUE);

	public static final Block POTTED_HEATHER = potted("heather", HEATHER, 0);
	public static final Block POTTED_EDELWEISS = potted("edelweiss", EDELWEISS, 0);
	public static final Block POTTED_FROSTBLOOM = potted("frostbloom", FROSTBLOOM, 7);
	public static final Block POTTED_GLOWCAP = potted("glowcap", GLOWCAP, 10);

	private ExpanseBlocks() {
	}

	public static void init() {
		// Class loading does the registering; this just makes sure it happens before the registries freeze.
	}

	public static WoodSet[] woods() {
		return new WoodSet[]{REDWOOD, WILLOW, PALM, LUMEN, BAOBAB, WISTERIA};
	}

	// ------------------------------------------------------------------ helpers

	static Block register(String id, Function<BlockBehaviour.Properties, Block> factory, BlockBehaviour.Properties properties) {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, Expanse.id(id));
		return Registry.register(BuiltInRegistries.BLOCK, key, factory.apply(properties.setId(key)));
	}

	static Block registerWithItem(String id, Function<BlockBehaviour.Properties, Block> factory, BlockBehaviour.Properties properties) {
		return registerWithItem(id, factory, properties, UnaryOperator.identity());
	}

	static Block registerWithItem(
		String id, Function<BlockBehaviour.Properties, Block> factory, BlockBehaviour.Properties properties, UnaryOperator<Item.Properties> item
	) {
		Block block = register(id, factory, properties);
		ExpanseItems.registerBlockItem(id, block, item);
		return block;
	}

	private static Block copy(String id, Block base) {
		return registerWithItem(id, Block::new, BlockBehaviour.Properties.ofLegacyCopy(base));
	}

	private static Block stairs(String id, Block base) {
		return registerWithItem(id, p -> new StairBlock(base.defaultBlockState(), p), BlockBehaviour.Properties.ofLegacyCopy(base));
	}

	private static Block slab(String id, Block base) {
		return registerWithItem(id, SlabBlock::new, BlockBehaviour.Properties.ofLegacyCopy(base));
	}

	private static Block wall(String id, Block base) {
		return registerWithItem(id, WallBlock::new, BlockBehaviour.Properties.ofLegacyCopy(base).forceSolidOn());
	}

	private static Block flower(String id, Function<BlockBehaviour.Properties, Block> factory, int light) {
		BlockBehaviour.Properties properties = BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).noCollision().instabreak()
			.sound(SoundType.GRASS).offsetType(BlockBehaviour.OffsetType.XZ).pushReaction(PushReaction.POPPED);
		if (light > 0) {
			properties = properties.lightLevel(s -> light).emissiveRendering(s -> true);
		}
		return registerWithItem(id, factory, properties, p -> p.compostable(ContextIntProviders.COMPOSTABLE_MEDIUM));
	}

	private static Block blossoms(String id, MapColor colour) {
		return registerWithItem(id, HangingMossBlock::new,
			BlockBehaviour.Properties.of().ignitedByLava().mapColor(colour).noCollision().instabreak().sound(SoundType.CHERRY_LEAVES)
				.pushReaction(PushReaction.POPPED),
			p -> p.compostable(ContextIntProviders.COMPOSTABLE_LOW));
	}

	private static Block potted(String id, Block plant, int light) {
		BlockBehaviour.Properties properties = flowerPotProperties();
		if (light > 0) {
			properties = properties.lightLevel(s -> light);
		}
		return register("potted_" + id, p -> new FlowerPotBlock(plant, p), properties);
	}

	static BlockBehaviour.Properties flowerPotProperties() {
		return BlockBehaviour.Properties.of().instabreak().noOcclusion().pushReaction(PushReaction.POPPED);
	}
}
