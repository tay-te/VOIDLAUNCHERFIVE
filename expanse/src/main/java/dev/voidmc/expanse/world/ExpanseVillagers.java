package dev.voidmc.expanse.world;

import dev.voidmc.expanse.world.biome.ExpanseBiomes;
import java.util.Map;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.npc.villager.VillagerType;
import net.minecraft.world.level.biome.Biome;

/**
 * Which villagers live in the Expanse biomes.
 *
 * <p>A villager's look (its {@link VillagerType}) comes from the biome it stands in:
 * {@code Villager.finalizeSpawn} asks {@link VillagerType#byBiome}, and so do the children of two villagers
 * half the time, cured zombie villagers and spawn eggs. That is a plain map in 26.3, not data, and a biome
 * missing from it gets plains villagers. The village templates write the same type into each villager,
 * but, as vanilla's own do, without finalizing it, so this map decides.
 *
 * <p>Each Expanse biome takes the type of the vanilla biome it grew from (the analog its tags come from),
 * except where that analog says nothing useful: Verdant Peaks is green to the summit, so taiga rather than
 * the snow of jagged peaks; Prismatic Peaks is calcite and snow, so snow rather than stony peaks' plains;
 * Palm Coast, a warm beach under palms, gets jungle villagers rather than a beach's plains ones.
 * Fabric API for 26.3 has no VillagerTypeHelper; the map is made accessible by
 * fabric-transitive-access-wideners-v1 and is written once, at start-up, before any world exists.
 */
public final class ExpanseVillagers {
	private ExpanseVillagers() {
	}

	public static void init() {
		Map<ResourceKey<Biome>, ResourceKey<VillagerType>> byBiome = VillagerType.BY_BIOME;
		byBiome.put(ExpanseBiomes.FROSTBLOOM_TUNDRA, VillagerType.SNOW);    // snowy plains
		byBiome.put(ExpanseBiomes.HEATHER_MOOR, VillagerType.PLAINS);       // meadow
		byBiome.put(ExpanseBiomes.WISTERIA_VALE, VillagerType.PLAINS);      // forest
		byBiome.put(ExpanseBiomes.REDWOOD_GIANTS, VillagerType.TAIGA);      // old growth spruce taiga
		byBiome.put(ExpanseBiomes.LUMEN_GROVE, VillagerType.PLAINS);        // dark forest
		byBiome.put(ExpanseBiomes.WILLOW_BAYOU, VillagerType.SWAMP);        // swamp
		byBiome.put(ExpanseBiomes.AMBER_STEPPE, VillagerType.SAVANNA);      // savanna
		byBiome.put(ExpanseBiomes.OPAL_DUNES, VillagerType.DESERT);         // desert
		byBiome.put(ExpanseBiomes.JADE_KARST, VillagerType.JUNGLE);         // jungle
		byBiome.put(ExpanseBiomes.CLOUD_FOREST, VillagerType.JUNGLE);       // jungle
		byBiome.put(ExpanseBiomes.VERDANT_PEAKS, VillagerType.TAIGA);       // jagged peaks, but green
		byBiome.put(ExpanseBiomes.PRISMATIC_PEAKS, VillagerType.SNOW);      // stony peaks under calcite and snow
		byBiome.put(ExpanseBiomes.PALM_COAST, VillagerType.JUNGLE);         // a warm beach under palms
		// --- cold & temperate biomes ---
		byBiome.put(ExpanseBiomes.MAPLE_HIGHLANDS, VillagerType.PLAINS);    // dappled forest
		byBiome.put(ExpanseBiomes.ASPEN_PARKLAND, VillagerType.PLAINS);     // plains
		byBiome.put(ExpanseBiomes.LARCH_TAIGA, VillagerType.SNOW);          // snowy taiga
		byBiome.put(ExpanseBiomes.BOREAL_MUSKEG, VillagerType.TAIGA);       // taiga
		byBiome.put(ExpanseBiomes.BLUEBELL_WOODS, VillagerType.PLAINS);     // forest
		byBiome.put(ExpanseBiomes.PINE_HEATH, VillagerType.TAIGA);          // old growth pine taiga
		// --- end cold & temperate biomes ---
	}
}
