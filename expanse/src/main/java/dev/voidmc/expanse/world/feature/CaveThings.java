package dev.voidmc.expanse.world.feature;

import dev.voidmc.expanse.Expanse;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.GenerationStep;

/**
 * The little things underground: a miner's bedroll and lantern, a candle on a cairn at a fork, a timber
 * prop, an ore cart left on a stub of rail, a lost explorer's bones and journal, a frozen camp under the
 * snow country, a forager's hut under the giant mushrooms of a realm. Small templates
 * ({@code expanse:cave_template}, see {@link CaveTemplateFeature}) set on cave floors so that the passages,
 * chambers, tunnels and realms are a place people have been, not only rock.
 *
 * <p>Each group is one placed feature (data/expanse/worldgen/{feature,placed_feature}/cave/, written by
 * tools/structures/gen_caves.py) with its own biomes, read where the thing would stand: the caves at large
 * ({@code #minecraft:is_overworld}), the caves under one kind of country (cold, dry, lush...) or one cave
 * biome, and each realm's own biome. The realms' biomes are not in the biome source, so no vanilla biome
 * filter would pass them; that is why the feature checks the biome itself and every group is added to
 * every overworld biome: it runs once in each chunk, wherever that chunk's caves are.
 *
 * <p>They go in at {@code underground_structures}, with vanilla's fossils and dungeons, before the ores and
 * before the caves are decorated ({@code CavernLife} works on natural ground only, so it grows round them).
 * Fabric appends additions to a step in registration order, and every biome gets these in the one order
 * below. gen_caves.py checks the names here against the files it writes.
 */
public final class CaveThings {
	private CaveThings() {
	}

	public static void init() {
		// ---- the caves at large, and the caves under each kind of country
		add("caves");
		add("caves_rare");
		add("caves_wall");
		add("caves_cold");
		add("caves_dry");
		add("caves_lush");
		add("caves_drip");
		add("caves_echo");
		add("caves_sulfur");
		// ---- the realms: traces of the explorers in all of them, then each realm's own
		add("realms");
		add("realm_wilds");
		add("realm_lush");
		add("realm_dripstone");
		add("realm_crystal");
		add("realm_ember");
		add("realm_mere");
		add("realm_frozen");
		add("realm_roots");
		add("realm_fossil");
		add("realm_tidal");
		add("realm_amber");
		add("realm_brimstone");
	}

	private static void add(String name) {
		BiomeModifications.addFeature(BiomeSelectors.foundInOverworld(), GenerationStep.Decoration.UNDERGROUND_STRUCTURES,
			ResourceKey.create(Registries.PLACED_FEATURE, Expanse.id("cave/" + name)));
	}
}
