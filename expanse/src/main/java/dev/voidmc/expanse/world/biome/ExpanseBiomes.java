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

	// --- landform biomes (world/terrain/Landform: placed by the land itself, see BiomePlacement.landforms) ---
	public static final ResourceKey<Biome> VOLCANIC_HIGHLANDS = key("volcanic_highlands");
	public static final ResourceKey<Biome> PAINTED_CANYONS = key("painted_canyons");
	public static final ResourceKey<Biome> SALT_FLATS = key("salt_flats");
	public static final ResourceKey<Biome> TEPUI = key("tepui");
	public static final ResourceKey<Biome> FJORDLANDS = key("fjordlands");
	/** By landform number; index 0 is no landform. */
	@SuppressWarnings("unchecked")
	public static final ResourceKey<Biome>[] LANDFORMS = new ResourceKey[]{null, VOLCANIC_HIGHLANDS, PAINTED_CANYONS, SALT_FLATS, TEPUI, FJORDLANDS};

	// --- cold & temperate biomes (BiomePlacement.remap; data from tools/worldgen_cold.py) ---
	public static final ResourceKey<Biome> MAPLE_HIGHLANDS = key("maple_highlands");
	public static final ResourceKey<Biome> ASPEN_PARKLAND = key("aspen_parkland");
	public static final ResourceKey<Biome> LARCH_TAIGA = key("larch_taiga");
	public static final ResourceKey<Biome> BOREAL_MUSKEG = key("boreal_muskeg");
	public static final ResourceKey<Biome> BLUEBELL_WOODS = key("bluebell_woods");
	public static final ResourceKey<Biome> PINE_HEATH = key("pine_heath");

	// --- warm & dry biomes (BiomePlacement.remap; data from tools/worldgen_warm.py) ---
	public static final ResourceKey<Biome> OLIVE_GROVES = key("olive_groves");
	public static final ResourceKey<Biome> MONSOON_FOREST = key("monsoon_forest");
	public static final ResourceKey<Biome> GHOST_GUM_OUTBACK = key("ghost_gum_outback");
	public static final ResourceKey<Biome> SAGUARO_FLATS = key("saguaro_flats");
	public static final ResourceKey<Biome> KAPOK_RAINFOREST = key("kapok_rainforest");
	public static final ResourceKey<Biome> CORAL_COAST = key("coral_coast");

	private ExpanseBiomes() {
	}

	private static ResourceKey<Biome> key(String name) {
		return ResourceKey.create(Registries.BIOME, Expanse.id(name));
	}
}
