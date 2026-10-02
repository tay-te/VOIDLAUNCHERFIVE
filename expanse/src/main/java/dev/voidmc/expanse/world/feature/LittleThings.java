package dev.voidmc.expanse.world.feature;

import dev.voidmc.expanse.Expanse;
import dev.voidmc.expanse.world.biome.ExpanseBiomes;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.levelgen.GenerationStep;

/**
 * The little things: stumps, rock piles, cold campfires, cairns, a lost chest. Small templates
 * ({@code expanse:template}, see {@link SurfaceTemplateFeature}) scattered over the land so that between
 * the structures there is always something a few blocks across to come upon, every chunk or two.
 *
 * <p>Each region has its own set (data/expanse/worldgen/{feature,placed_feature}/micro/, written by
 * tools/structures/gen_micro.py): the natural things of its woods or rocks, then the traces people leave
 * on the lowlands, the north or the dry country, and last a rare lost chest nearly everywhere. They go in
 * at {@code surface_structures}, vanilla's step for its desert wells: after the structures (which they
 * keep clear of) and before the trees and grass, which then grow round them as they would round a rock.
 *
 * <p>Fabric appends each addition to the end of the step in registration order, so the placements are
 * added here in one fixed order and every biome lists the ones it gets in that same order: the game's
 * feature-order check sees no cycle. gen_micro.py checks the names here against the files it writes.
 */
public final class LittleThings {
	private LittleThings() {
	}

	public static void init() {
		// ---- natural things, one set per region
		add("oak_woods", Biomes.FOREST, Biomes.FLOWER_FOREST, Biomes.WINDSWEPT_FOREST, Biomes.DAPPLED_FOREST,
			ExpanseBiomes.MAPLE_HIGHLANDS, ExpanseBiomes.BLUEBELL_WOODS);   // + cold & temperate
		add("birch_woods", Biomes.BIRCH_FOREST, Biomes.OLD_GROWTH_BIRCH_FOREST);
		add("dark_woods", Biomes.DARK_FOREST);
		add("taiga", Biomes.TAIGA, Biomes.OLD_GROWTH_PINE_TAIGA, Biomes.OLD_GROWTH_SPRUCE_TAIGA, Biomes.SNOWY_TAIGA,
			ExpanseBiomes.LARCH_TAIGA, ExpanseBiomes.BOREAL_MUSKEG, ExpanseBiomes.PINE_HEATH);   // + cold & temperate
		add("redwood", ExpanseBiomes.REDWOOD_GIANTS);
		add("grassland", Biomes.PLAINS, Biomes.SUNFLOWER_PLAINS, Biomes.MEADOW, ExpanseBiomes.ASPEN_PARKLAND);   // + cold & temperate
		add("moor", ExpanseBiomes.HEATHER_MOOR, Biomes.WINDSWEPT_HILLS, Biomes.WINDSWEPT_GRAVELLY_HILLS);
		add("peaks", ExpanseBiomes.VERDANT_PEAKS, Biomes.STONY_PEAKS);
		add("crystal", ExpanseBiomes.PRISMATIC_PEAKS);
		add("snow", Biomes.SNOWY_PLAINS, Biomes.GROVE, ExpanseBiomes.FROSTBLOOM_TUNDRA);
		add("savanna", Biomes.SAVANNA, Biomes.SAVANNA_PLATEAU, Biomes.WINDSWEPT_SAVANNA);
		add("steppe", ExpanseBiomes.AMBER_STEPPE);
		add("desert", Biomes.DESERT);
		add("badlands", Biomes.BADLANDS, Biomes.ERODED_BADLANDS, Biomes.WOODED_BADLANDS);
		add("dunes", ExpanseBiomes.OPAL_DUNES);
		add("swamp", Biomes.SWAMP, Biomes.MANGROVE_SWAMP);
		add("bayou", ExpanseBiomes.WILLOW_BAYOU);
		add("jungle", Biomes.JUNGLE, Biomes.SPARSE_JUNGLE, Biomes.BAMBOO_JUNGLE, ExpanseBiomes.CLOUD_FOREST);
		add("karst", ExpanseBiomes.JADE_KARST);
		add("lumen", ExpanseBiomes.LUMEN_GROVE);
		add("vale", ExpanseBiomes.WISTERIA_VALE);
		add("cherry", Biomes.CHERRY_GROVE);
		add("coast", Biomes.BEACH, ExpanseBiomes.PALM_COAST);

		// ---- traces of people passing through, rarer
		add("traces_lowland", Biomes.PLAINS, Biomes.SUNFLOWER_PLAINS, Biomes.MEADOW, Biomes.FOREST, Biomes.FLOWER_FOREST,
			Biomes.BIRCH_FOREST, Biomes.OLD_GROWTH_BIRCH_FOREST, Biomes.DAPPLED_FOREST, Biomes.CHERRY_GROVE,
			ExpanseBiomes.HEATHER_MOOR, ExpanseBiomes.WISTERIA_VALE,
			ExpanseBiomes.MAPLE_HIGHLANDS, ExpanseBiomes.BLUEBELL_WOODS, ExpanseBiomes.ASPEN_PARKLAND);   // + cold & temperate
		add("traces_north", Biomes.TAIGA, Biomes.OLD_GROWTH_PINE_TAIGA, Biomes.OLD_GROWTH_SPRUCE_TAIGA, Biomes.SNOWY_TAIGA,
			Biomes.SNOWY_PLAINS, Biomes.GROVE, Biomes.WINDSWEPT_HILLS, Biomes.WINDSWEPT_GRAVELLY_HILLS, Biomes.WINDSWEPT_FOREST,
			ExpanseBiomes.REDWOOD_GIANTS, ExpanseBiomes.FROSTBLOOM_TUNDRA, ExpanseBiomes.VERDANT_PEAKS,
			ExpanseBiomes.LARCH_TAIGA, ExpanseBiomes.BOREAL_MUSKEG, ExpanseBiomes.PINE_HEATH);   // + cold & temperate
		add("traces_warm", Biomes.SAVANNA, Biomes.SAVANNA_PLATEAU, Biomes.WINDSWEPT_SAVANNA, ExpanseBiomes.AMBER_STEPPE);
		add("traces_wet", Biomes.SWAMP, ExpanseBiomes.WILLOW_BAYOU, Biomes.JUNGLE, Biomes.SPARSE_JUNGLE,
			ExpanseBiomes.JADE_KARST, ExpanseBiomes.CLOUD_FOREST);

		// ---- the rare find, almost anywhere on land
		add("lost_chest", Biomes.FOREST, Biomes.FLOWER_FOREST, Biomes.WINDSWEPT_FOREST, Biomes.DAPPLED_FOREST, Biomes.BIRCH_FOREST,
			Biomes.OLD_GROWTH_BIRCH_FOREST, Biomes.DARK_FOREST, Biomes.TAIGA, Biomes.OLD_GROWTH_PINE_TAIGA,
			Biomes.OLD_GROWTH_SPRUCE_TAIGA, Biomes.SNOWY_TAIGA, Biomes.PLAINS, Biomes.SUNFLOWER_PLAINS, Biomes.MEADOW,
			Biomes.WINDSWEPT_HILLS, Biomes.WINDSWEPT_GRAVELLY_HILLS, Biomes.SNOWY_PLAINS, Biomes.GROVE, Biomes.SAVANNA,
			Biomes.SAVANNA_PLATEAU, Biomes.WINDSWEPT_SAVANNA, Biomes.DESERT, Biomes.BADLANDS, Biomes.WOODED_BADLANDS, Biomes.SWAMP,
			Biomes.JUNGLE, Biomes.SPARSE_JUNGLE, Biomes.BAMBOO_JUNGLE, Biomes.CHERRY_GROVE, Biomes.BEACH,
			ExpanseBiomes.FROSTBLOOM_TUNDRA, ExpanseBiomes.HEATHER_MOOR, ExpanseBiomes.WISTERIA_VALE, ExpanseBiomes.REDWOOD_GIANTS,
			ExpanseBiomes.LUMEN_GROVE, ExpanseBiomes.WILLOW_BAYOU, ExpanseBiomes.AMBER_STEPPE, ExpanseBiomes.OPAL_DUNES,
			ExpanseBiomes.JADE_KARST, ExpanseBiomes.CLOUD_FOREST, ExpanseBiomes.VERDANT_PEAKS, ExpanseBiomes.PALM_COAST,
			ExpanseBiomes.MAPLE_HIGHLANDS, ExpanseBiomes.ASPEN_PARKLAND, ExpanseBiomes.LARCH_TAIGA,   // + cold & temperate
			ExpanseBiomes.BOREAL_MUSKEG, ExpanseBiomes.BLUEBELL_WOODS, ExpanseBiomes.PINE_HEATH);
	}

	@SafeVarargs
	private static void add(String name, ResourceKey<Biome>... biomes) {
		BiomeModifications.addFeature(BiomeSelectors.includeByKey(biomes), GenerationStep.Decoration.SURFACE_STRUCTURES,
			ResourceKey.create(Registries.PLACED_FEATURE, Expanse.id("micro/" + name)));
	}
}
