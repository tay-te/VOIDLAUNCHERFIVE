package dev.voidmc.expanse.registry;

import dev.voidmc.expanse.Expanse;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.random.Weighted;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.level.block.grower.TreeGrower;
import net.minecraft.world.level.levelgen.feature.Feature;

/**
 * The tree features saplings grow into. The features themselves are data
 * (data/expanse/worldgen/feature/*.json); these are only their names, so a sapling planted by a
 * player grows the exact tree the world generator places.
 */
public final class ExpanseTrees {
	public static final ResourceKey<Feature> REDWOOD = key("redwood");
	public static final ResourceKey<Feature> GIANT_REDWOOD = key("giant_redwood");
	public static final ResourceKey<Feature> WILLOW = key("willow");
	public static final ResourceKey<Feature> PALM = key("palm");
	public static final ResourceKey<Feature> LUMEN = key("lumen");
	public static final ResourceKey<Feature> BAOBAB = key("baobab");
	public static final ResourceKey<Feature> WISTERIA = key("wisteria");
	public static final ResourceKey<Feature> AZURE_WISTERIA = key("azure_wisteria");

	// A single redwood sapling grows a young redwood; four in a square grow a giant, like spruce.
	public static final TreeGrower REDWOOD_GROWER = new TreeGrower("expanse:redwood",
		WeightedList.of(REDWOOD), WeightedList.of(GIANT_REDWOOD), WeightedList.of(), null);
	public static final TreeGrower WILLOW_GROWER = simple("willow", WILLOW);
	public static final TreeGrower PALM_GROWER = simple("palm", PALM);
	public static final TreeGrower LUMEN_GROWER = simple("lumen", LUMEN);
	public static final TreeGrower BAOBAB_GROWER = simple("baobab", BAOBAB);
	// One wisteria in four blooms blue.
	public static final TreeGrower WISTERIA_GROWER = new TreeGrower("expanse:wisteria",
		WeightedList.of(new Weighted<>(WISTERIA, 3), new Weighted<>(AZURE_WISTERIA, 1)), WeightedList.of(), WeightedList.of(), null);

	private ExpanseTrees() {
	}

	private static TreeGrower simple(String name, ResourceKey<Feature> tree) {
		return new TreeGrower("expanse:" + name, WeightedList.of(tree), WeightedList.of(), WeightedList.of(), null);
	}

	private static ResourceKey<Feature> key(String name) {
		return ResourceKey.create(Registries.FEATURE, Expanse.id(name));
	}
}
