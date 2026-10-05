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
 * The little things: stumps, rock piles, cold campfires, cairns, ruins, a lost chest. Small templates
 * ({@code expanse:template}, see {@link SurfaceTemplateFeature}) scattered over the land so that between
 * the structures there is always something a few blocks across to come upon, every chunk or two.
 *
 * <p>Each region has its own sets (data/expanse/worldgen/{feature,placed_feature}/micro/, written by
 * tools/structures/gen_micro.py): the natural things of its woods, rocks or shore, the traces people leave
 * there, the ruins of what they built, and last a rare lost chest nearly everywhere. They go in at
 * {@code surface_structures}, vanilla's step for its desert wells: after the structures (which they keep
 * clear of) and before the trees and grass, which then grow round them as they would round a rock.
 *
 * <p>Fabric appends each addition to the end of the step in registration order. Two biomes that list the
 * same placed features in different relative orders make the game's feature-order check fail on a cycle,
 * so every group, for every region (the warm lands' too), is added here, in this one table and in this one
 * fixed order; every biome then lists the ones it gets in the same order. gen_micro.py checks the names
 * here against the files it writes.
 */
public final class LittleThings {
	private LittleThings() {
	}

	public static void init() {
		// ---- natural things, one set per region
		add("oak_woods", Biomes.FOREST, Biomes.FLOWER_FOREST, Biomes.WINDSWEPT_FOREST, Biomes.DAPPLED_FOREST, ExpanseBiomes.WOODLAND_GLADES);
		add("birch_woods", Biomes.BIRCH_FOREST, Biomes.OLD_GROWTH_BIRCH_FOREST);
		add("dark_woods", Biomes.DARK_FOREST, ExpanseBiomes.ANCIENT_OAKWOOD);
		add("taiga", Biomes.TAIGA, Biomes.OLD_GROWTH_PINE_TAIGA, Biomes.OLD_GROWTH_SPRUCE_TAIGA, Biomes.SNOWY_TAIGA);
		add("redwood", ExpanseBiomes.REDWOOD_GIANTS);
		add("grassland", Biomes.PLAINS, Biomes.SUNFLOWER_PLAINS, Biomes.MEADOW);
		add("moor", ExpanseBiomes.HEATHER_MOOR, Biomes.WINDSWEPT_HILLS, Biomes.WINDSWEPT_GRAVELLY_HILLS);
		add("peaks", ExpanseBiomes.VERDANT_PEAKS, Biomes.STONY_PEAKS, ExpanseBiomes.DOLOMITE_PEAKS);
		// (the crystal, dunes, lumen and vale sets were the fantasy biomes'; they stay registered, in their place
		// in the order, but no biome takes them now that those biomes are natural ones)
		add("crystal");
		add("snow", Biomes.SNOWY_PLAINS, Biomes.GROVE, ExpanseBiomes.LICHEN_TUNDRA);
		add("savanna", Biomes.SAVANNA, Biomes.SAVANNA_PLATEAU, Biomes.WINDSWEPT_SAVANNA);
		add("steppe", ExpanseBiomes.AMBER_STEPPE);
		add("desert", Biomes.DESERT);
		add("badlands", Biomes.BADLANDS, Biomes.ERODED_BADLANDS, Biomes.WOODED_BADLANDS, ExpanseBiomes.RED_DUNES);
		add("dunes");
		add("swamp", Biomes.SWAMP);
		add("bayou", ExpanseBiomes.WILLOW_BAYOU);
		add("jungle", Biomes.JUNGLE, Biomes.SPARSE_JUNGLE, Biomes.BAMBOO_JUNGLE, ExpanseBiomes.CLOUD_FOREST);
		add("karst", ExpanseBiomes.JADE_KARST);
		add("lumen");
		add("vale");
		add("cherry", Biomes.CHERRY_GROVE);
		add("coast", Biomes.BEACH, ExpanseBiomes.PALM_COAST);
		add("pale", Biomes.PALE_GARDEN);
		add("maple", ExpanseBiomes.MAPLE_HIGHLANDS);
		add("aspen", ExpanseBiomes.ASPEN_PARKLAND);
		add("larch", ExpanseBiomes.LARCH_TAIGA);
		add("muskeg", ExpanseBiomes.BOREAL_MUSKEG);
		add("bluebell", ExpanseBiomes.BLUEBELL_WOODS);
		add("heath", ExpanseBiomes.PINE_HEATH);
		add("frost", Biomes.SNOWY_SLOPES, Biomes.FROZEN_PEAKS, Biomes.JAGGED_PEAKS);
		add("ice_spikes", Biomes.ICE_SPIKES);
		add("fjord", ExpanseBiomes.FJORDLANDS);
		add("tepui", ExpanseBiomes.TEPUI);
		add("outback", ExpanseBiomes.GHOST_GUM_OUTBACK);
		add("saguaro", ExpanseBiomes.SAGUARO_FLATS);
		add("canyons", ExpanseBiomes.PAINTED_CANYONS);
		add("salt", ExpanseBiomes.SALT_FLATS);
		add("volcanic", ExpanseBiomes.VOLCANIC_HIGHLANDS);
		add("mangrove", Biomes.MANGROVE_SWAMP);
		add("monsoon", ExpanseBiomes.MONSOON_FOREST);
		add("kapok", ExpanseBiomes.KAPOK_RAINFOREST);
		add("olive", ExpanseBiomes.OLIVE_GROVES);
		add("mushroom", Biomes.MUSHROOM_FIELDS);
		add("coral", ExpanseBiomes.CORAL_COAST);
		add("cold_shore", Biomes.STONY_SHORE, Biomes.SNOWY_BEACH);

		// ---- traces of people, one set per region, every four or five chunks
		add("traces_fields", Biomes.PLAINS, Biomes.SUNFLOWER_PLAINS, Biomes.MEADOW);
		add("traces_woods", Biomes.FOREST, Biomes.FLOWER_FOREST, Biomes.BIRCH_FOREST, Biomes.OLD_GROWTH_BIRCH_FOREST,
			Biomes.DAPPLED_FOREST, Biomes.DARK_FOREST, Biomes.WINDSWEPT_FOREST, ExpanseBiomes.BLUEBELL_WOODS,
			ExpanseBiomes.ANCIENT_OAKWOOD, ExpanseBiomes.WOODLAND_GLADES);
		add("traces_maple", ExpanseBiomes.MAPLE_HIGHLANDS);
		add("traces_moor", ExpanseBiomes.HEATHER_MOOR, Biomes.WINDSWEPT_HILLS, Biomes.WINDSWEPT_GRAVELLY_HILLS,
			ExpanseBiomes.PINE_HEATH);
		add("traces_vale");
		add("traces_cherry", Biomes.CHERRY_GROVE);
		add("traces_north", Biomes.TAIGA, Biomes.OLD_GROWTH_PINE_TAIGA, Biomes.OLD_GROWTH_SPRUCE_TAIGA, Biomes.SNOWY_TAIGA,
			ExpanseBiomes.LARCH_TAIGA);
		add("traces_redwood", ExpanseBiomes.REDWOOD_GIANTS);
		add("traces_snow", Biomes.SNOWY_PLAINS, Biomes.GROVE, ExpanseBiomes.LICHEN_TUNDRA, Biomes.ICE_SPIKES);
		add("traces_peaks", ExpanseBiomes.VERDANT_PEAKS, Biomes.STONY_PEAKS, ExpanseBiomes.DOLOMITE_PEAKS);
		add("traces_high", Biomes.SNOWY_SLOPES, Biomes.FROZEN_PEAKS, Biomes.JAGGED_PEAKS);
		add("traces_muskeg", ExpanseBiomes.BOREAL_MUSKEG);
		add("traces_fjord", ExpanseBiomes.FJORDLANDS);
		add("traces_cold_shore", Biomes.STONY_SHORE, Biomes.SNOWY_BEACH);
		add("traces_aspen", ExpanseBiomes.ASPEN_PARKLAND);
		add("traces_warm", Biomes.SAVANNA, Biomes.SAVANNA_PLATEAU, Biomes.WINDSWEPT_SAVANNA);
		add("traces_steppe", ExpanseBiomes.AMBER_STEPPE);
		add("traces_outback", ExpanseBiomes.GHOST_GUM_OUTBACK);
		add("traces_saguaro", ExpanseBiomes.SAGUARO_FLATS);
		add("traces_badlands", Biomes.BADLANDS, Biomes.ERODED_BADLANDS, Biomes.WOODED_BADLANDS, ExpanseBiomes.PAINTED_CANYONS);
		add("traces_desert", Biomes.DESERT, ExpanseBiomes.RED_DUNES);
		add("traces_salt", ExpanseBiomes.SALT_FLATS);
		add("traces_volcanic", ExpanseBiomes.VOLCANIC_HIGHLANDS);
		add("traces_olive", ExpanseBiomes.OLIVE_GROVES);
		add("traces_wet", Biomes.SWAMP, ExpanseBiomes.WILLOW_BAYOU);
		add("traces_mangrove", Biomes.MANGROVE_SWAMP);
		add("traces_jungle", Biomes.JUNGLE, Biomes.SPARSE_JUNGLE, Biomes.BAMBOO_JUNGLE, ExpanseBiomes.CLOUD_FOREST,
			ExpanseBiomes.KAPOK_RAINFOREST, ExpanseBiomes.MONSOON_FOREST, ExpanseBiomes.TEPUI);
		add("traces_karst", ExpanseBiomes.JADE_KARST);
		add("traces_coast", Biomes.BEACH, ExpanseBiomes.PALM_COAST, ExpanseBiomes.CORAL_COAST);
		add("traces_pale", Biomes.PALE_GARDEN);
		add("traces_lumen");

		// ---- ruins, one set per region, every ten to fourteen chunks
		add("ruins_fields", Biomes.PLAINS, Biomes.SUNFLOWER_PLAINS, Biomes.MEADOW, ExpanseBiomes.ASPEN_PARKLAND);
		add("ruins_woods", Biomes.FOREST, Biomes.FLOWER_FOREST, Biomes.BIRCH_FOREST, Biomes.OLD_GROWTH_BIRCH_FOREST,
			Biomes.DAPPLED_FOREST, Biomes.DARK_FOREST, Biomes.WINDSWEPT_FOREST, ExpanseBiomes.MAPLE_HIGHLANDS,
			ExpanseBiomes.BLUEBELL_WOODS, ExpanseBiomes.ANCIENT_OAKWOOD, ExpanseBiomes.WOODLAND_GLADES);
		add("ruins_moor", ExpanseBiomes.HEATHER_MOOR, Biomes.WINDSWEPT_HILLS, Biomes.WINDSWEPT_GRAVELLY_HILLS,
			ExpanseBiomes.PINE_HEATH);
		add("ruins_vale", Biomes.CHERRY_GROVE);
		add("ruins_pale", Biomes.PALE_GARDEN);
		add("ruins_north", Biomes.TAIGA, Biomes.OLD_GROWTH_PINE_TAIGA, Biomes.OLD_GROWTH_SPRUCE_TAIGA, Biomes.SNOWY_TAIGA,
			ExpanseBiomes.LARCH_TAIGA, ExpanseBiomes.REDWOOD_GIANTS, ExpanseBiomes.BOREAL_MUSKEG);
		add("ruins_snow", Biomes.SNOWY_PLAINS, Biomes.GROVE, ExpanseBiomes.LICHEN_TUNDRA);
		add("ruins_fjord", ExpanseBiomes.FJORDLANDS, Biomes.STONY_SHORE, Biomes.SNOWY_BEACH);
		add("ruins_peaks", ExpanseBiomes.VERDANT_PEAKS, Biomes.STONY_PEAKS, ExpanseBiomes.DOLOMITE_PEAKS);
		add("ruins_tepui", ExpanseBiomes.TEPUI);
		add("ruins_olive", ExpanseBiomes.OLIVE_GROVES);
		add("ruins_dry", Biomes.DESERT, ExpanseBiomes.SAGUARO_FLATS, ExpanseBiomes.RED_DUNES, Biomes.BADLANDS, Biomes.ERODED_BADLANDS,
			Biomes.WOODED_BADLANDS, ExpanseBiomes.PAINTED_CANYONS);
		add("ruins_savanna", Biomes.SAVANNA, Biomes.SAVANNA_PLATEAU, Biomes.WINDSWEPT_SAVANNA);
		add("ruins_steppe", ExpanseBiomes.AMBER_STEPPE);
		add("ruins_outback", ExpanseBiomes.GHOST_GUM_OUTBACK);
		add("ruins_jungle", Biomes.JUNGLE, Biomes.SPARSE_JUNGLE, Biomes.BAMBOO_JUNGLE, ExpanseBiomes.CLOUD_FOREST,
			ExpanseBiomes.KAPOK_RAINFOREST, ExpanseBiomes.MONSOON_FOREST);
		add("ruins_karst", ExpanseBiomes.JADE_KARST);
		add("ruins_volcanic", ExpanseBiomes.VOLCANIC_HIGHLANDS);
		add("ruins_salt", ExpanseBiomes.SALT_FLATS);
		add("ruins_wet", Biomes.SWAMP, Biomes.MANGROVE_SWAMP, ExpanseBiomes.WILLOW_BAYOU);
		add("ruins_coast", Biomes.BEACH, ExpanseBiomes.PALM_COAST, ExpanseBiomes.CORAL_COAST);

		// ---- the rare find, almost anywhere on land
		add("lost_chest", Biomes.FOREST, Biomes.FLOWER_FOREST, Biomes.WINDSWEPT_FOREST, Biomes.DAPPLED_FOREST, Biomes.BIRCH_FOREST,
			Biomes.OLD_GROWTH_BIRCH_FOREST, Biomes.DARK_FOREST, Biomes.PALE_GARDEN, Biomes.TAIGA, Biomes.OLD_GROWTH_PINE_TAIGA,
			Biomes.OLD_GROWTH_SPRUCE_TAIGA, Biomes.SNOWY_TAIGA, Biomes.PLAINS, Biomes.SUNFLOWER_PLAINS, Biomes.MEADOW,
			Biomes.WINDSWEPT_HILLS, Biomes.WINDSWEPT_GRAVELLY_HILLS, Biomes.SNOWY_PLAINS, Biomes.ICE_SPIKES, Biomes.GROVE,
			Biomes.SNOWY_SLOPES, Biomes.SAVANNA, Biomes.SAVANNA_PLATEAU, Biomes.WINDSWEPT_SAVANNA, Biomes.DESERT, Biomes.BADLANDS,
			Biomes.ERODED_BADLANDS, Biomes.WOODED_BADLANDS, Biomes.SWAMP, Biomes.MANGROVE_SWAMP, Biomes.JUNGLE,
			Biomes.SPARSE_JUNGLE, Biomes.BAMBOO_JUNGLE, Biomes.CHERRY_GROVE, Biomes.BEACH, Biomes.SNOWY_BEACH,
			Biomes.STONY_SHORE, Biomes.MUSHROOM_FIELDS,
			ExpanseBiomes.LICHEN_TUNDRA, ExpanseBiomes.HEATHER_MOOR, ExpanseBiomes.WOODLAND_GLADES, ExpanseBiomes.REDWOOD_GIANTS,
			ExpanseBiomes.ANCIENT_OAKWOOD, ExpanseBiomes.WILLOW_BAYOU, ExpanseBiomes.AMBER_STEPPE, ExpanseBiomes.RED_DUNES,
			ExpanseBiomes.JADE_KARST, ExpanseBiomes.CLOUD_FOREST, ExpanseBiomes.VERDANT_PEAKS, ExpanseBiomes.PALM_COAST,
			ExpanseBiomes.VOLCANIC_HIGHLANDS, ExpanseBiomes.PAINTED_CANYONS, ExpanseBiomes.SALT_FLATS, ExpanseBiomes.TEPUI,
			ExpanseBiomes.FJORDLANDS, ExpanseBiomes.MAPLE_HIGHLANDS, ExpanseBiomes.ASPEN_PARKLAND, ExpanseBiomes.LARCH_TAIGA,
			ExpanseBiomes.BOREAL_MUSKEG, ExpanseBiomes.BLUEBELL_WOODS, ExpanseBiomes.PINE_HEATH, ExpanseBiomes.OLIVE_GROVES,
			ExpanseBiomes.MONSOON_FOREST, ExpanseBiomes.GHOST_GUM_OUTBACK, ExpanseBiomes.SAGUARO_FLATS,
			ExpanseBiomes.KAPOK_RAINFOREST, ExpanseBiomes.CORAL_COAST);
	}

	@SafeVarargs
	private static void add(String name, ResourceKey<Biome>... biomes) {
		BiomeModifications.addFeature(BiomeSelectors.includeByKey(biomes), GenerationStep.Decoration.SURFACE_STRUCTURES,
			ResourceKey.create(Registries.PLACED_FEATURE, Expanse.id("micro/" + name)));
	}
}
