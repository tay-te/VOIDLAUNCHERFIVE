package dev.voidmc.expanse.world;

import dev.voidmc.expanse.registry.WarmBlocks;
import dev.voidmc.expanse.world.biome.ExpanseBiomes;
import dev.voidmc.expanse.world.feature.WarmFeatures;
import dev.voidmc.expanse.world.tree.WarmTreePlacers;
import java.util.Map;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.npc.villager.VillagerType;
import net.minecraft.world.level.biome.Biome;

/**
 * The warm and dry biomes: Olive Groves, Monsoon Forest, Ghost Gum Outback, Saguaro Flats, Kapok
 * Rainforest and Coral Coast. Their blocks, tree placers and feature types, and the villagers who live in
 * them. Where they go is {@link dev.voidmc.expanse.world.biome.BiomePlacement}; the biomes themselves are
 * data, written by tools/worldgen_warm.py. Their little things ({@code micro/*}) are registered with every
 * other region's in {@link dev.voidmc.expanse.world.feature.LittleThings}, in its one fixed order, so that
 * all biomes list them in the same relative order and the game's feature-order check finds no cycle.
 */
public final class WarmLands {
	private WarmLands() {
	}

	public static void init() {
		WarmBlocks.init();
		WarmTreePlacers.init();
		WarmFeatures.init();
		villagers();
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
}
