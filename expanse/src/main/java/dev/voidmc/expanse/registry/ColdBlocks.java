package dev.voidmc.expanse.registry;

import dev.voidmc.expanse.Expanse;
import dev.voidmc.expanse.world.feature.ColdFeatures;
import net.fabricmc.fabric.api.item.v1.BlockTransformerHelper;
import net.fabricmc.fabric.api.registry.FlammableBlockRegistry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.random.Weighted;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.DoubleHighBlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.CarpetBlock;
import net.minecraft.world.level.block.FlowerBedBlock;
import net.minecraft.world.level.block.FlowerPotBlock;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.TallFlowerBlock;
import net.minecraft.world.level.block.grower.TreeGrower;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.storage.loot.providers.number.ints.ContextIntProviders;

/**
 * The blocks of the cold and temperate biomes (Maple Highlands, Aspen Parkland, Larch Taiga, Boreal Muskeg,
 * Bluebell Woods, Pine Heath): four wood sets, two more leaves, the larch's sapling, the bog's peat and
 * sphagnum, and the plants of the woods, bogs and heaths.
 *
 * <p>Registered the way {@link ExpanseBlocks} registers its own (same helpers, same vanilla classes), and the
 * assets, loot, recipes and tags come from tools/assets_cold.py, the textures from tools/textures/cold.py.
 * The trees the saplings grow are the same features the world generator places (tools/worldgen_cold.py).
 */
public final class ColdBlocks {
	// ------------------------------------------------------------------ the trees saplings grow

	private static final ResourceKey<Feature> MAPLE_TREE = tree("maple");
	private static final ResourceKey<Feature> ORANGE_MAPLE_TREE = tree("orange_maple");
	private static final ResourceKey<Feature> MOTTLED_MAPLE_TREE = tree("mottled_maple");
	private static final ResourceKey<Feature> ASPEN_TREE = tree("aspen");
	private static final ResourceKey<Feature> ASPEN_BEES_TREE = tree("aspen_bees");
	private static final ResourceKey<Feature> BEECH_TREE = tree("beech");
	private static final ResourceKey<Feature> BEECH_BEES_TREE = tree("beech_bees");
	private static final ResourceKey<Feature> SCOTS_PINE_TREE = tree("scots_pine");
	private static final ResourceKey<Feature> YOUNG_PINE_TREE = tree("young_pine");
	private static final ResourceKey<Feature> LARCH_TREE = tree("larch");

	// A maple sapling turns out scarlet, flame orange, or (one in five) both at once.
	public static final TreeGrower MAPLE_GROWER = new TreeGrower("expanse:maple",
		WeightedList.of(new Weighted<>(MAPLE_TREE, 2), new Weighted<>(ORANGE_MAPLE_TREE, 2), new Weighted<>(MOTTLED_MAPLE_TREE, 1)),
		WeightedList.of(), WeightedList.of(), null);
	// Aspens and beeches near flowers get a hive, as oak and birch do.
	public static final TreeGrower ASPEN_GROWER = new TreeGrower("expanse:aspen",
		WeightedList.of(ASPEN_TREE), WeightedList.of(), WeightedList.of(ASPEN_BEES_TREE), null);
	public static final TreeGrower BEECH_GROWER = new TreeGrower("expanse:beech",
		WeightedList.of(BEECH_TREE), WeightedList.of(), WeightedList.of(BEECH_BEES_TREE), null);
	public static final TreeGrower PINE_GROWER = new TreeGrower("expanse:pine",
		WeightedList.of(new Weighted<>(SCOTS_PINE_TREE, 3), new Weighted<>(YOUNG_PINE_TREE, 1)), WeightedList.of(), WeightedList.of(), null);
	public static final TreeGrower LARCH_GROWER = new TreeGrower("expanse:larch",
		WeightedList.of(LARCH_TREE), WeightedList.of(), WeightedList.of(), null);

	// ------------------------------------------------------------------ woods

	public static final WoodSet MAPLE = new WoodSet("maple", MapColor.TERRACOTTA_WHITE, MapColor.TERRACOTTA_GRAY,
		0xFFB8301C, 0, WoodSet.sapling(MAPLE_GROWER));
	public static final Block ORANGE_MAPLE_LEAVES = WoodSet.leaves("orange_maple_leaves", 0xFFD9761E, 0);
	public static final WoodSet ASPEN = new WoodSet("aspen", MapColor.SAND, MapColor.QUARTZ,
		0xFFD8C232, 0, WoodSet.sapling(ASPEN_GROWER));
	public static final WoodSet BEECH = new WoodSet("beech", MapColor.TERRACOTTA_PINK, MapColor.STONE,
		0xFF4D912D, 0, WoodSet.sapling(BEECH_GROWER));
	public static final WoodSet PINE = new WoodSet("pine", MapColor.WOOD, MapColor.TERRACOTTA_ORANGE,
		0xFF2F5E4C, 0, WoodSet.sapling(PINE_GROWER));
	// The larch is a conifer that turns gold and drops its needles: spruce-built, with leaves of its own.
	public static final Block LARCH_LEAVES = WoodSet.leaves("larch_leaves", 0xFFD6A52E, 0);
	public static final Block LARCH_SAPLING = ExpanseBlocks.registerWithItem("larch_sapling", p -> new SaplingBlock(LARCH_GROWER, p),
		BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).noCollision().randomTicks().instabreak().sound(SoundType.GRASS)
			.pushReaction(PushReaction.POPPED),
		p -> p.compostable(ContextIntProviders.COMPOSTABLE_LOW).cookingFuel(ContextIntProviders.COOKING_TIME_DRY_PLANTS));
	public static final Block POTTED_LARCH_SAPLING = ExpanseBlocks.register("potted_larch_sapling",
		p -> new FlowerPotBlock(LARCH_SAPLING, p), ExpanseBlocks.flowerPotProperties());

	// ------------------------------------------------------------------ the bog

	/** Cut from the muskeg: soil for anything that grows in dirt, and it burns like coal. */
	public static final Block PEAT = ExpanseBlocks.registerWithItem("peat", Block::new,
		BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_BROWN).strength(0.5F).sound(SoundType.ROOTED_DIRT),
		p -> p.cookingFuel(ContextIntProviders.COOKING_TIME_COAL));
	public static final Block SPHAGNUM_MOSS = ExpanseBlocks.registerWithItem("sphagnum_moss", Block::new,
		BlockBehaviour.Properties.of().ignitedByLava().mapColor(MapColor.TERRACOTTA_RED).strength(0.1F).sound(SoundType.MOSS)
			.pushReaction(PushReaction.POPPED),
		p -> p.compostable(ContextIntProviders.COMPOSTABLE_MEDIUM));
	public static final Block SPHAGNUM_MOSS_CARPET = ExpanseBlocks.registerWithItem("sphagnum_moss_carpet", CarpetBlock::new,
		BlockBehaviour.Properties.of().ignitedByLava().mapColor(MapColor.TERRACOTTA_RED).strength(0.1F).sound(SoundType.MOSS_CARPET)
			.pushReaction(PushReaction.POPPED),
		p -> p.compostable(ContextIntProviders.COMPOSTABLE_LOW));
	public static final Block COTTON_GRASS = ExpanseBlocks.registerWithItem("cotton_grass", BushBlock::new, plant(MapColor.SNOW),
		p -> p.compostable(ContextIntProviders.COMPOSTABLE_LOW));
	public static final Block CRANBERRY_BUSH = ExpanseBlocks.registerWithItem("cranberry_bush", p -> new BushBlock(p, 8),
		BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).noCollision().instabreak().sound(SoundType.SWEET_BERRY_BUSH)
			.offsetType(BlockBehaviour.OffsetType.XZ).ignitedByLava().pushReaction(PushReaction.POPPED),
		p -> p.compostable(ContextIntProviders.COMPOSTABLE_MEDIUM));
	public static final Item CRANBERRIES = ExpanseItems.register("cranberries", Item::new, new Item.Properties()
		.food(new FoodProperties.Builder().nutrition(2).saturationModifier(0.1F).build()).compostable(ContextIntProviders.COMPOSTABLE_LOW));

	// ------------------------------------------------------------------ woods, heaths and clearings

	public static final Block BLUEBELLS = ExpanseBlocks.registerWithItem("bluebells", p -> new FlowerBedBlock(p, 3),
		BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLUE).noCollision().sound(SoundType.PINK_PETALS).pushReaction(PushReaction.POPPED),
		p -> p.compostable(ContextIntProviders.COMPOSTABLE_LOW));
	public static final Block FIREWEED = tallFlower("fireweed", MapColor.COLOR_MAGENTA);
	public static final Block REINDEER_LICHEN = ExpanseBlocks.registerWithItem("reindeer_lichen", p -> new BushBlock(p, 5),
		plant(MapColor.TERRACOTTA_WHITE).sound(SoundType.MOSS_CARPET), p -> p.compostable(ContextIntProviders.COMPOSTABLE_LOW));

	private ColdBlocks() {
	}

	public static void init() {
		// Class loading does the registering; this also gives the woods vanilla's wood behaviour and registers
		// the bog's features.
		FlammableBlockRegistry fire = FlammableBlockRegistry.getDefaultInstance();
		for (WoodSet wood : woods()) {
			BlockTransformerHelper.registerStripping(wood.log, wood.strippedLog);
			BlockTransformerHelper.registerStripping(wood.wood, wood.strippedWood);
			for (Block b : new Block[]{wood.log, wood.wood, wood.strippedLog, wood.strippedWood}) {
				fire.add(b, 5, 5);
			}
			for (Block b : new Block[]{wood.planks, wood.stairs, wood.slab, wood.fence, wood.fenceGate}) {
				fire.add(b, 5, 20);
			}
			fire.add(wood.leaves, 30, 60);
		}
		fire.add(ORANGE_MAPLE_LEAVES, 30, 60);
		fire.add(LARCH_LEAVES, 30, 60);
		for (Block b : new Block[]{COTTON_GRASS, CRANBERRY_BUSH, BLUEBELLS, FIREWEED, REINDEER_LICHEN}) {
			fire.add(b, 60, 100);
		}
		fire.add(SPHAGNUM_MOSS_CARPET, 60, 20);
		fire.add(SPHAGNUM_MOSS, 30, 20);
		fire.add(PEAT, 5, 5);
		ColdFeatures.init();
	}

	public static WoodSet[] woods() {
		return new WoodSet[]{MAPLE, ASPEN, BEECH, PINE};
	}

	private static BlockBehaviour.Properties plant(MapColor colour) {
		return BlockBehaviour.Properties.of().mapColor(colour).replaceable().noCollision().instabreak().sound(SoundType.GRASS)
			.offsetType(BlockBehaviour.OffsetType.XZ).ignitedByLava().pushReaction(PushReaction.POPPED);
	}

	private static Block tallFlower(String id, MapColor colour) {
		Block block = ExpanseBlocks.register(id, TallFlowerBlock::new,
			BlockBehaviour.Properties.of().mapColor(colour).noCollision().instabreak().sound(SoundType.GRASS)
				.offsetType(BlockBehaviour.OffsetType.XZ).ignitedByLava().pushReaction(PushReaction.POPPED));
		ExpanseItems.registerBlockItem(id, block, (b, p) -> new DoubleHighBlockItem(b, p.compostable(ContextIntProviders.COMPOSTABLE_MEDIUM)));
		return block;
	}

	private static ResourceKey<Feature> tree(String name) {
		return ResourceKey.create(Registries.FEATURE, Expanse.id(name));
	}
}
