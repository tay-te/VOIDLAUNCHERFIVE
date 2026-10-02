package dev.voidmc.expanse.world;

import dev.voidmc.expanse.Expanse;
import dev.voidmc.expanse.registry.WarmBlocks;
import dev.voidmc.expanse.world.biome.ExpanseBiomes;
import dev.voidmc.expanse.world.feature.WarmFeatures;
import dev.voidmc.expanse.world.tree.WarmTreePlacers;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.npc.villager.VillagerType;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.GenerationStep;

/**
 * The warm and dry biomes: Olive Groves, Monsoon Forest, Ghost Gum Outback, Saguaro Flats, Kapok
 * Rainforest and Coral Coast. Their blocks, tree placers and feature types, the villagers who live in
 * them, and the little things ({@code micro/*}, see {@link dev.voidmc.expanse.world.feature.LittleThings})
 * scattered over them. Where they go is {@link dev.voidmc.expanse.world.biome.BiomePlacement}; the biomes
 * themselves are data, written by tools/worldgen_warm.py.
 */
public final class WarmLands {
	private WarmLands() {
	}

	public static void init() {
		WarmBlocks.init();
		WarmTreePlacers.init();
		WarmFeatures.init();
		villagers();
		littleThings();
	}

	/** Each takes the villager look of the vanilla biome it grew from (see ExpanseVillagers). */
	private static void villagers() {
		Map<ResourceKey<Biome>, ResourceKey<VillagerType>> byBiome = VillagerType.BY_BIOME;
		byBiome.put(ExpanseBiomes.OLIVE_GROVES, VillagerType.PLAINS);        // plains
		byBiome.put(ExpanseBiomes.MONSOON_FOREST, VillagerType.JUNGLE);      // sparse jungle
		byBiome.put(ExpanseBiomes.GHOST_GUM_OUTBACK, VillagerType.SAVANNA);  // savanna
		byBiome.put(ExpanseBiomes.SAGUARO_FLATS, VillagerType.DESERT);       // desert
		byBiome.put(ExpanseBiomes.KAPOK_RAINFOREST, VillagerType.JUNGLE);    // jungle
		byBiome.put(ExpanseBiomes.CORAL_COAST, VillagerType.JUNGLE);         // a warm beach, as Palm Coast
	}

	/**
	 * The little things of the regions these biomes resemble. Fabric appends additions to a step in the order
	 * they are registered, so the groups are added here in LittleThings' own order (grassland, savanna, desert,
	 * jungle, coast, the traces, the lost chest); every biome then lists them in the one relative order the
	 * feature sorter accepts.
	 */
	private static void littleThings() {
		Map<String, List<ResourceKey<Biome>>> groups = new LinkedHashMap<>();
		for (String g : new String[]{"grassland", "savanna", "desert", "jungle", "coast", "traces_lowland", "traces_warm", "traces_wet", "lost_chest"}) {
			groups.put(g, new ArrayList<>());
		}
		groups.get("grassland").add(ExpanseBiomes.OLIVE_GROVES);
		groups.get("savanna").add(ExpanseBiomes.GHOST_GUM_OUTBACK);
		groups.get("desert").add(ExpanseBiomes.SAGUARO_FLATS);
		groups.get("jungle").add(ExpanseBiomes.MONSOON_FOREST);
		groups.get("jungle").add(ExpanseBiomes.KAPOK_RAINFOREST);
		groups.get("coast").add(ExpanseBiomes.CORAL_COAST);
		groups.get("traces_lowland").add(ExpanseBiomes.OLIVE_GROVES);
		groups.get("traces_warm").add(ExpanseBiomes.MONSOON_FOREST);
		groups.get("traces_warm").add(ExpanseBiomes.GHOST_GUM_OUTBACK);
		groups.get("traces_warm").add(ExpanseBiomes.SAGUARO_FLATS);
		groups.get("traces_wet").add(ExpanseBiomes.KAPOK_RAINFOREST);
		groups.get("lost_chest").addAll(List.of(ExpanseBiomes.OLIVE_GROVES, ExpanseBiomes.MONSOON_FOREST, ExpanseBiomes.GHOST_GUM_OUTBACK,
			ExpanseBiomes.SAGUARO_FLATS, ExpanseBiomes.KAPOK_RAINFOREST, ExpanseBiomes.CORAL_COAST));
		groups.forEach((name, biomes) -> BiomeModifications.addFeature(BiomeSelectors.includeByKey(biomes),
			GenerationStep.Decoration.SURFACE_STRUCTURES, ResourceKey.create(Registries.PLACED_FEATURE, Expanse.id("micro/" + name))));
	}
}
