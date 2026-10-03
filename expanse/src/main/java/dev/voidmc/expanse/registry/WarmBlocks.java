package dev.voidmc.expanse.registry;

import dev.voidmc.expanse.Expanse;
import dev.voidmc.expanse.block.OcotilloBlock;
import dev.voidmc.expanse.block.SaguaroBlock;
import java.util.function.Function;
import net.fabricmc.fabric.api.item.v1.BlockTransformerHelper;
import net.fabricmc.fabric.api.registry.FlammableBlockRegistry;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ColorRGBA;
import net.minecraft.util.random.Weighted;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DryVegetationBlock;
import net.minecraft.world.level.block.FlowerBlock;
import net.minecraft.world.level.block.FlowerPotBlock;
import net.minecraft.world.level.block.SandBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.grower.TreeGrower;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.storage.loot.providers.number.ints.ContextIntProviders;

/**
 * The blocks of the warm and dry biomes (Olive Groves, Monsoon Forest, Ghost Gum Outback, Saguaro Flats,
 * Kapok Rainforest, Coral Coast): four woods, the soils and sands they stand on, and their plants.
 * Registered the way {@link ExpanseBlocks} registers its own, with assets from tools/assets_warm.py and
 * textures from tools/textures/warm.py.
 */
public final class WarmBlocks {
	// ------------------------------------------------------------------ trees (data/expanse/worldgen/feature/*.json)

	public static final ResourceKey<Feature> OLIVE_TREE = tree("olive");
	public static final ResourceKey<Feature> EUCALYPTUS_TREE = tree("eucalyptus");
	public static final ResourceKey<Feature> TEAK_TREE = tree("teak");
	public static final ResourceKey<Feature> FLAME_TREE = tree("flame_tree");
	public static final ResourceKey<Feature> KAPOK_TREE = tree("kapok");
	public static final ResourceKey<Feature> GIANT_KAPOK_TREE = tree("giant_kapok");

	static final TreeGrower OLIVE_GROWER = new TreeGrower("expanse:olive", WeightedList.of(OLIVE_TREE), WeightedList.of(), WeightedList.of(), null);
	static final TreeGrower EUCALYPTUS_GROWER = new TreeGrower("expanse:eucalyptus", WeightedList.of(EUCALYPTUS_TREE), WeightedList.of(),
		WeightedList.of(), null);
	// One teak sapling in four grows a flame-of-the-forest instead, as one wisteria in four blooms blue.
	static final TreeGrower TEAK_GROWER = new TreeGrower("expanse:teak",
		WeightedList.of(new Weighted<>(TEAK_TREE, 3), new Weighted<>(FLAME_TREE, 1)), WeightedList.of(), WeightedList.of(), null);
	// A single kapok sapling grows a young tree; four in a square grow an emergent giant, like spruce.
	static final TreeGrower KAPOK_GROWER = new TreeGrower("expanse:kapok", WeightedList.of(KAPOK_TREE), WeightedList.of(GIANT_KAPOK_TREE),
		WeightedList.of(), null);

	public static final WoodSet OLIVE = new WoodSet("olive", MapColor.SAND, MapColor.TERRACOTTA_LIGHT_GRAY,
		0xFF7F906C, 0, WoodSet.sapling(OLIVE_GROWER));
	public static final WoodSet EUCALYPTUS = new WoodSet("eucalyptus", MapColor.TERRACOTTA_RED, MapColor.QUARTZ,
		0xFF8DA295, 0, WoodSet.sapling(EUCALYPTUS_GROWER));
	public static final WoodSet TEAK = new WoodSet("teak", MapColor.TERRACOTTA_YELLOW, MapColor.TERRACOTTA_GRAY,
		0xFFA6AE4C, 0, WoodSet.sapling(TEAK_GROWER));
	public static final WoodSet KAPOK = new WoodSet("kapok", MapColor.TERRACOTTA_WHITE, MapColor.COLOR_LIGHT_GRAY,
		0xFF3F8C3A, 0, WoodSet.sapling(KAPOK_GROWER));
	/** Flame of the forest: the teak forest's orange-flowering tree (it grows from a teak sapling). */
	public static final Block FLAME_TREE_LEAVES = WoodSet.leaves("flame_tree_leaves", 0xFFE8661E, 0);

	// ------------------------------------------------------------------ ground

	/** Red earth: the iron-red soil of the outback and the terra rossa under the olive groves. */
	public static final Block RED_EARTH = ExpanseBlocks.registerWithItem("red_earth", Block::new,
		BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_ORANGE).strength(0.5F).sound(SoundType.GRAVEL));
	/** Coral sand: crushed shell and coral, near white with a blush of pink. */
	public static final Block CORAL_SAND = ExpanseBlocks.registerWithItem("coral_sand", p -> new SandBlock(new ColorRGBA(0xEFE3D6), p),
		BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_WHITE).instrument(NoteBlockInstrument.SNARE).strength(0.5F).sound(SoundType.SAND));

	// ------------------------------------------------------------------ plants

	public static final Block LAVENDER = flower("lavender", MobEffects.REGENERATION, 7.0F);
	public static final Block DESERT_PEA = flower("desert_pea", MobEffects.FIRE_RESISTANCE, 4.0F);
	public static final Block MOTH_ORCHID = flower("moth_orchid", MobEffects.SATURATION, 0.35F);
	public static final Block DESERT_MARIGOLD = flower("desert_marigold", MobEffects.SPEED, 6.0F);
	/** Spinifex: the spiky hummock grass of the outback. */
	public static final Block SPINIFEX = ExpanseBlocks.registerWithItem("spinifex", DryVegetationBlock::new,
		BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_YELLOW).replaceable().noCollision().instabreak().sound(SoundType.GRASS)
			.ignitedByLava().offsetType(BlockBehaviour.OffsetType.XZ).pushReaction(PushReaction.POPPED),
		p -> p.compostable(ContextIntProviders.COMPOSTABLE_LOW));
	public static final Block SAGUARO = ExpanseBlocks.registerWithItem("saguaro", SaguaroBlock::new,
		BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).forceSolidOff().strength(0.4F).sound(SoundType.WOOL).noOcclusion()
			.pushReaction(PushReaction.POPPED),
		p -> p.compostable(ContextIntProviders.COMPOSTABLE_MEDIUM));
	public static final Block OCOTILLO = ExpanseBlocks.registerWithItem("ocotillo", OcotilloBlock::new,
		BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_GREEN).noCollision().strength(0.2F).sound(SoundType.AZALEA)
			.noOcclusion().ignitedByLava().offsetType(BlockBehaviour.OffsetType.XZ).pushReaction(PushReaction.POPPED),
		p -> p.compostable(ContextIntProviders.COMPOSTABLE_LOW));

	public static final Block POTTED_LAVENDER = potted("lavender", LAVENDER);
	public static final Block POTTED_DESERT_PEA = potted("desert_pea", DESERT_PEA);
	public static final Block POTTED_MOTH_ORCHID = potted("moth_orchid", MOTH_ORCHID);
	public static final Block POTTED_DESERT_MARIGOLD = potted("desert_marigold", DESERT_MARIGOLD);

	private WarmBlocks() {
	}

	public static WoodSet[] woods() {
		return new WoodSet[]{OLIVE, EUCALYPTUS, TEAK, KAPOK};
	}

	/** Class loading registers the blocks; this adds what vanilla does for wood in code: stripping and fire. */
	public static void init() {
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
		fire.add(FLAME_TREE_LEAVES, 30, 60);
		for (Block b : new Block[]{LAVENDER, DESERT_PEA, MOTH_ORCHID, DESERT_MARIGOLD, OCOTILLO}) {
			fire.add(b, 60, 100);
		}
		// Spinifex is resinous and burns like tinder.
		fire.add(SPINIFEX, 100, 100);
	}

	// ------------------------------------------------------------------ helpers

	private static ResourceKey<Feature> tree(String name) {
		return ResourceKey.create(Registries.FEATURE, Expanse.id(name));
	}

	private static Block flower(String id, Holder<MobEffect> effect, float seconds) {
		Function<BlockBehaviour.Properties, Block> factory = p -> new FlowerBlock(effect, seconds, p);
		return ExpanseBlocks.registerWithItem(id, factory,
			BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).noCollision().instabreak().sound(SoundType.GRASS)
				.offsetType(BlockBehaviour.OffsetType.XZ).pushReaction(PushReaction.POPPED),
			p -> p.compostable(ContextIntProviders.COMPOSTABLE_MEDIUM));
	}

	private static Block potted(String id, Block plant) {
		return ExpanseBlocks.register("potted_" + id, p -> new FlowerPotBlock(plant, p), ExpanseBlocks.flowerPotProperties());
	}
}
