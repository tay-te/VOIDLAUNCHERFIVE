package dev.voidmc.expanse.world.biome;

import dev.voidmc.expanse.Expanse;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import org.jspecify.annotations.Nullable;

/**
 * The biomes of the world underground (data from tools/worldgen_deep.py).
 *
 * <p>Under the Earth terrain the caves between the realms take the places vanilla's cave biomes have in
 * the overworld's biome source ({@link #remap}, from {@link EarthBiomeSource}): lush caves become Mossgrown
 * Caves, dripstone caves Dripstone Grottos, the deep dark the Echoing Depths and sulfur caves the Sulfur
 * Seeps. They are ours, with vanilla's ores, geodes and dungeons and nothing else; what grows in them is
 * placed by {@link dev.voidmc.expanse.world.terrain.CavernLife}. (The deep dark's ancient cities and its
 * warden go with it.) Other worlds keep vanilla's caves and their biomes.
 *
 * <p>The realms' biomes are not in the source: the Earth generator puts them wherever a realm is, by its
 * character ({@link #REALMS}, indexed by {@link dev.voidmc.expanse.world.terrain.CavernModel} theme).
 */
public final class UndergroundBiomes {
	public static final ResourceKey<Biome> MOSSY_CAVES = key("mossy_caves");
	public static final ResourceKey<Biome> DRIPSTONE_GROTTOS = key("dripstone_grottos");
	public static final ResourceKey<Biome> ECHOING_DEPTHS = key("echoing_depths");
	public static final ResourceKey<Biome> SULFUR_SEEPS = key("sulfur_seeps");

	/** The realms' biomes, by CavernModel theme. */
	public static final String[] REALMS = {"realm_wilds", "realm_lush", "realm_dripstone", "realm_crystal", "realm_ember", "realm_mere",
		"realm_frozen", "realm_roots", "realm_fossil", "realm_tidal", "realm_amber", "realm_brimstone"};

	private UndergroundBiomes() {
	}

	/** Ours in place of a vanilla cave biome, or null if {@code b} is not one. */
	static @Nullable ResourceKey<Biome> remap(ResourceKey<Biome> b) {
		if (b == Biomes.LUSH_CAVES) {
			return MOSSY_CAVES;
		}
		if (b == Biomes.DRIPSTONE_CAVES) {
			return DRIPSTONE_GROTTOS;
		}
		if (b == Biomes.DEEP_DARK) {
			return ECHOING_DEPTHS;
		}
		if (b == Biomes.SULFUR_CAVES) {
			return SULFUR_SEEPS;
		}
		return null;
	}

	private static ResourceKey<Biome> key(String name) {
		return ResourceKey.create(Registries.BIOME, Expanse.id(name));
	}
}
