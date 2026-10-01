package dev.voidmc.expanse.world.biome;

import dev.voidmc.expanse.Expanse;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;

/** The biomes are data (data/expanse/worldgen/biome); these are their names, for placing them. */
public final class ExpanseBiomes {
	public static final ResourceKey<Biome> FROSTBLOOM_TUNDRA = key("frostbloom_tundra");
	public static final ResourceKey<Biome> HEATHER_MOOR = key("heather_moor");
	public static final ResourceKey<Biome> WISTERIA_VALE = key("wisteria_vale");
	public static final ResourceKey<Biome> REDWOOD_GIANTS = key("redwood_giants");
	public static final ResourceKey<Biome> LUMEN_GROVE = key("lumen_grove");
	public static final ResourceKey<Biome> WILLOW_BAYOU = key("willow_bayou");
	public static final ResourceKey<Biome> AMBER_STEPPE = key("amber_steppe");
	public static final ResourceKey<Biome> OPAL_DUNES = key("opal_dunes");
	public static final ResourceKey<Biome> JADE_KARST = key("jade_karst");
	public static final ResourceKey<Biome> CLOUD_FOREST = key("cloud_forest");
	public static final ResourceKey<Biome> VERDANT_PEAKS = key("verdant_peaks");
	public static final ResourceKey<Biome> PRISMATIC_PEAKS = key("prismatic_peaks");
	public static final ResourceKey<Biome> PALM_COAST = key("palm_coast");

	private ExpanseBiomes() {
	}

	private static ResourceKey<Biome> key(String name) {
		return ResourceKey.create(Registries.BIOME, Expanse.id(name));
	}
}
