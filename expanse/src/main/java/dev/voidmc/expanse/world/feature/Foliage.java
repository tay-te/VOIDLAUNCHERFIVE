package dev.voidmc.expanse.world.feature;

import dev.voidmc.expanse.Expanse;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.ModificationPhase;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

/**
 * Richer ground cover for the vanilla overworld biomes where vanilla's is thin, and the plants of the
 * water's edge wherever a river runs (the Earth terrain's rivers cross every biome, not just the river
 * biome): undergrowth and leaf litter in the forests and taigas, wildflower drifts on the plains, dry
 * grass and dead bushes on the savannas and badlands, spruce shrubs in the snow, and reeds, cattails,
 * sugar cane, lily pads, clay and seagrass along the banks.
 *
 * <p>The placed features are data, under {@code data/expanse/worldgen/placed_feature/vanilla/}, written by
 * {@code tools/gen_worldgen.py}; this only says which biomes get which. They are their own placed
 * features, never the ones the Expanse biomes list, and they are added by one modification in the order
 * of {@link #ADDITIONS}, so every biome lists them in the same relative order and the feature sorter
 * never finds two biomes that disagree.
 *
 * <p>Biomes are named one by one rather than by tag, since the Expanse biomes join vanilla's tags (a
 * Wisteria Vale is {@code #is_forest}) and have their own ground cover already.
 */
public final class Foliage {
	private Foliage() {
	}

	private record Addition(ResourceKey<PlacedFeature> feature, GenerationStep.Decoration step, Set<ResourceKey<Biome>> biomes) {
	}

	// ------------------------------------------------------------------ the land

	private static final Set<ResourceKey<Biome>> BROADLEAF_FORESTS = Set.of(Biomes.FOREST, Biomes.FLOWER_FOREST, Biomes.BIRCH_FOREST,
		Biomes.OLD_GROWTH_BIRCH_FOREST, Biomes.WINDSWEPT_FOREST);
	private static final Set<ResourceKey<Biome>> DARK_FORESTS = Set.of(Biomes.DARK_FOREST);
	private static final Set<ResourceKey<Biome>> TAIGAS = Set.of(Biomes.TAIGA, Biomes.SNOWY_TAIGA, Biomes.OLD_GROWTH_PINE_TAIGA,
		Biomes.OLD_GROWTH_SPRUCE_TAIGA);
	private static final Set<ResourceKey<Biome>> GRASSLANDS = Set.of(Biomes.PLAINS, Biomes.SUNFLOWER_PLAINS, Biomes.MEADOW);
	private static final Set<ResourceKey<Biome>> SAVANNAS = Set.of(Biomes.SAVANNA, Biomes.SAVANNA_PLATEAU, Biomes.WINDSWEPT_SAVANNA);
	private static final Set<ResourceKey<Biome>> BADLANDS = Set.of(Biomes.BADLANDS, Biomes.WOODED_BADLANDS, Biomes.ERODED_BADLANDS);
	private static final Set<ResourceKey<Biome>> SNOWFIELDS = Set.of(Biomes.SNOWY_PLAINS, Biomes.SNOWY_TAIGA);

	// ------------------------------------------------------------------ the water's edge, by climate

	private static final Set<ResourceKey<Biome>> TEMPERATE_BANKS = Set.of(Biomes.PLAINS, Biomes.SUNFLOWER_PLAINS, Biomes.MEADOW,
		Biomes.FOREST, Biomes.FLOWER_FOREST, Biomes.BIRCH_FOREST, Biomes.OLD_GROWTH_BIRCH_FOREST, Biomes.DARK_FOREST, Biomes.DAPPLED_FOREST,
		Biomes.CHERRY_GROVE, Biomes.TAIGA, Biomes.OLD_GROWTH_PINE_TAIGA, Biomes.OLD_GROWTH_SPRUCE_TAIGA, Biomes.WINDSWEPT_HILLS,
		Biomes.WINDSWEPT_GRAVELLY_HILLS, Biomes.WINDSWEPT_FOREST, Biomes.RIVER, Biomes.SWAMP);
	private static final Set<ResourceKey<Biome>> WARM_BANKS = Set.of(Biomes.SAVANNA, Biomes.SAVANNA_PLATEAU, Biomes.WINDSWEPT_SAVANNA,
		Biomes.JUNGLE, Biomes.SPARSE_JUNGLE, Biomes.BAMBOO_JUNGLE);
	private static final Set<ResourceKey<Biome>> ARID_BANKS = Set.of(Biomes.DESERT, Biomes.BADLANDS, Biomes.WOODED_BADLANDS,
		Biomes.ERODED_BADLANDS);
	/** Where rivers freeze over: clay on the banks, and nothing that would stand in the ice. */
	private static final Set<ResourceKey<Biome>> COLD_BANKS = Set.of(Biomes.SNOWY_PLAINS, Biomes.SNOWY_TAIGA, Biomes.ICE_SPIKES,
		Biomes.FROZEN_RIVER, Biomes.GROVE, Biomes.SNOWY_SLOPES);
	/** Lily pads: the temperate and jungle waters, but not the swamp (which has its own) or the windswept rock. */
	private static final Set<ResourceKey<Biome>> CALM_WATERS = Set.of(Biomes.PLAINS, Biomes.SUNFLOWER_PLAINS, Biomes.MEADOW,
		Biomes.FOREST, Biomes.FLOWER_FOREST, Biomes.BIRCH_FOREST, Biomes.OLD_GROWTH_BIRCH_FOREST, Biomes.DARK_FOREST, Biomes.DAPPLED_FOREST,
		Biomes.CHERRY_GROVE, Biomes.TAIGA, Biomes.OLD_GROWTH_PINE_TAIGA, Biomes.OLD_GROWTH_SPRUCE_TAIGA, Biomes.WINDSWEPT_FOREST,
		Biomes.RIVER, Biomes.JUNGLE, Biomes.SPARSE_JUNGLE, Biomes.BAMBOO_JUNGLE);
	/** Ferns and moss on the wet banks of wooded country. */
	private static final Set<ResourceKey<Biome>> WOODED_BANKS = Set.of(Biomes.FOREST, Biomes.FLOWER_FOREST, Biomes.BIRCH_FOREST,
		Biomes.OLD_GROWTH_BIRCH_FOREST, Biomes.DARK_FOREST, Biomes.DAPPLED_FOREST, Biomes.WINDSWEPT_FOREST, Biomes.TAIGA, Biomes.SNOWY_TAIGA,
		Biomes.OLD_GROWTH_PINE_TAIGA, Biomes.OLD_GROWTH_SPRUCE_TAIGA, Biomes.JUNGLE, Biomes.SPARSE_JUNGLE, Biomes.BAMBOO_JUNGLE, Biomes.SWAMP);
	/** Seagrass on the riverbed: every unfrozen bank but the river and swamp biomes, which have vanilla's. */
	private static final Set<ResourceKey<Biome>> RIVERBEDS = union(minus(TEMPERATE_BANKS, Set.of(Biomes.RIVER, Biomes.SWAMP)), WARM_BANKS, ARID_BANKS);
	private static final Set<ResourceKey<Biome>> ALL_BANKS = union(TEMPERATE_BANKS, WARM_BANKS, ARID_BANKS, COLD_BANKS);

	private static final GenerationStep.Decoration VEGETATION = GenerationStep.Decoration.VEGETAL_DECORATION;

	/** In the order every biome lists them. Append, don't reorder. */
	private static final List<Addition> ADDITIONS = List.of(
		add("river_clay", GenerationStep.Decoration.UNDERGROUND_ORES, ALL_BANKS),
		add("forest_undergrowth", VEGETATION, union(BROADLEAF_FORESTS, DARK_FORESTS)),
		add("forest_leaf_litter", VEGETATION, BROADLEAF_FORESTS),
		add("forest_mushrooms", VEGETATION, DARK_FORESTS),
		add("taiga_undergrowth", VEGETATION, TAIGAS),
		add("plains_wildflowers", VEGETATION, GRASSLANDS),
		add("savanna_scrub", VEGETATION, SAVANNAS),
		add("badlands_scrub", VEGETATION, BADLANDS),
		add("snowy_shrubs", VEGETATION, SNOWFIELDS),
		add("river_reeds_temperate", VEGETATION, TEMPERATE_BANKS),
		add("river_reeds_warm", VEGETATION, WARM_BANKS),
		add("river_reeds_arid", VEGETATION, ARID_BANKS),
		add("river_lily_pads", VEGETATION, CALM_WATERS),
		add("river_wet_bank", VEGETATION, WOODED_BANKS),
		add("river_seagrass", VEGETATION, RIVERBEDS));

	public static void init() {
		Set<ResourceKey<Biome>> touched = new HashSet<>();
		ADDITIONS.forEach(a -> touched.addAll(a.biomes()));
		BiomeModifications.create(Expanse.id("foliage")).add(ModificationPhase.ADDITIONS, selection -> touched.contains(selection.getBiomeKey()),
			(selection, context) -> {
				for (Addition a : ADDITIONS) {
					if (a.biomes().contains(selection.getBiomeKey())) {
						context.getGenerationSettings().addFeature(a.step(), a.feature());
					}
				}
			});
	}

	private static Addition add(String name, GenerationStep.Decoration step, Set<ResourceKey<Biome>> biomes) {
		return new Addition(ResourceKey.create(Registries.PLACED_FEATURE, Expanse.id("vanilla/" + name)), step, biomes);
	}

	@SafeVarargs
	private static Set<ResourceKey<Biome>> union(Set<ResourceKey<Biome>>... sets) {
		Set<ResourceKey<Biome>> all = new HashSet<>();
		for (Set<ResourceKey<Biome>> s : sets) {
			all.addAll(s);
		}
		return Set.copyOf(all);
	}

	private static Set<ResourceKey<Biome>> minus(Set<ResourceKey<Biome>> from, Set<ResourceKey<Biome>> taken) {
		Set<ResourceKey<Biome>> rest = new HashSet<>(from);
		rest.removeAll(taken);
		return Set.copyOf(rest);
	}
}
