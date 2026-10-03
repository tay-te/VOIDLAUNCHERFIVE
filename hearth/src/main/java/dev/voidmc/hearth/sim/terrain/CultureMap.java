package dev.voidmc.hearth.sim.terrain;

import dev.voidmc.hearth.api.Culture;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import net.fabricmc.fabric.api.tag.convention.v2.ConventionalBiomeTags;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;

/**
 * Which building tradition a settlement founded in a biome follows. Three layers, most specific first:
 * <ol>
 *   <li>every Expanse biome by id (Expanse biomes join their parent's vanilla tags, so tags alone would file Jade Karst
 *   under jungle);</li>
 *   <li>vanilla's biomes by id where tags cannot tell them apart (cherry grove, bamboo jungle, the windswept hills);</li>
 *   <li>tags, vanilla's and the {@code c:} conventional ones that modded biomes join, then plain climate (temperature and
 *   rain), so any biome from any mod gets a sensible culture without being named.</li>
 * </ol>
 * Thread-safe (the atlas asks on world generation threads); results are cached per biome holder.
 */
public final class CultureMap implements Function<Holder<Biome>, Culture> {
	private static final Map<Identifier, Culture> BY_ID = new HashMap<>();

	static {
		expanse("frostbloom_tundra", Culture.TUNDRA);
		expanse("heather_moor", Culture.MOORLAND);
		expanse("wisteria_vale", Culture.WISTERIA);
		expanse("redwood_giants", Culture.NORTHERN);
		expanse("lumen_grove", Culture.TEMPERATE);
		expanse("willow_bayou", Culture.BAYOU);
		expanse("amber_steppe", Culture.STEPPE);
		expanse("opal_dunes", Culture.DESERT);
		expanse("jade_karst", Culture.KARST);
		expanse("cloud_forest", Culture.TROPICAL);
		expanse("verdant_peaks", Culture.ALPINE);
		expanse("prismatic_peaks", Culture.ALPINE);
		expanse("palm_coast", Culture.TROPICAL);
		expanse("volcanic_highlands", Culture.MOORLAND);
		expanse("painted_canyons", Culture.MESA);
		expanse("salt_flats", Culture.DESERT);
		expanse("tepui", Culture.TROPICAL);
		expanse("fjordlands", Culture.MARITIME);
		expanse("maple_highlands", Culture.TEMPERATE);
		expanse("aspen_parkland", Culture.TEMPERATE);
		expanse("larch_taiga", Culture.NORTHERN);
		expanse("boreal_muskeg", Culture.BAYOU);
		expanse("bluebell_woods", Culture.TEMPERATE);
		expanse("pine_heath", Culture.NORTHERN);
		expanse("olive_groves", Culture.MEDITERRANEAN);
		expanse("monsoon_forest", Culture.KARST);
		expanse("ghost_gum_outback", Culture.STEPPE);
		expanse("saguaro_flats", Culture.DESERT);
		expanse("kapok_rainforest", Culture.TROPICAL);
		expanse("coral_coast", Culture.TROPICAL);
		// the caves never hold a settlement centre, but a probe may still read them under an overhang
		expanse("mossy_caves", Culture.TEMPERATE);
		expanse("dripstone_grottos", Culture.MOORLAND);
		expanse("echoing_depths", Culture.NORTHERN);
		expanse("sulfur_seeps", Culture.MESA);

		vanilla(Biomes.CHERRY_GROVE, Culture.WISTERIA);
		vanilla(Biomes.BAMBOO_JUNGLE, Culture.KARST);
		vanilla(Biomes.SPARSE_JUNGLE, Culture.TROPICAL);
		vanilla(Biomes.MEADOW, Culture.ALPINE);
		vanilla(Biomes.GROVE, Culture.ALPINE);
		vanilla(Biomes.SNOWY_SLOPES, Culture.ALPINE);
		vanilla(Biomes.STONY_PEAKS, Culture.ALPINE);
		vanilla(Biomes.JAGGED_PEAKS, Culture.ALPINE);
		vanilla(Biomes.FROZEN_PEAKS, Culture.TUNDRA);
		vanilla(Biomes.WINDSWEPT_HILLS, Culture.MOORLAND);
		vanilla(Biomes.WINDSWEPT_GRAVELLY_HILLS, Culture.MOORLAND);
		vanilla(Biomes.WINDSWEPT_FOREST, Culture.MOORLAND);
		vanilla(Biomes.WINDSWEPT_SAVANNA, Culture.STEPPE);
		vanilla(Biomes.SNOWY_PLAINS, Culture.TUNDRA);
		vanilla(Biomes.ICE_SPIKES, Culture.TUNDRA);
		vanilla(Biomes.SNOWY_TAIGA, Culture.NORTHERN);
		vanilla(Biomes.SNOWY_BEACH, Culture.TUNDRA);
		vanilla(Biomes.FROZEN_RIVER, Culture.TUNDRA);
		vanilla(Biomes.STONY_SHORE, Culture.MARITIME);
		vanilla(Biomes.MUSHROOM_FIELDS, Culture.MARITIME);
		vanilla(Biomes.SWAMP, Culture.BAYOU);
		vanilla(Biomes.MANGROVE_SWAMP, Culture.BAYOU);
		vanilla(Biomes.DESERT, Culture.DESERT);
		vanilla(Biomes.PALE_GARDEN, Culture.NORTHERN);
		vanilla(Biomes.DARK_FOREST, Culture.NORTHERN);
	}

	private final Map<Holder<Biome>, Culture> cache = new java.util.concurrent.ConcurrentHashMap<>();

	private static void expanse(String path, Culture culture) {
		BY_ID.put(Identifier.fromNamespaceAndPath("expanse", path), culture);
	}

	private static void vanilla(net.minecraft.resources.ResourceKey<Biome> key, Culture culture) {
		BY_ID.put(key.identifier(), culture);
	}

	@Override
	public Culture apply(Holder<Biome> biome) {
		return this.cache.computeIfAbsent(biome, CultureMap::classify);
	}

	/** The culture by id, then tags, then climate. */
	public static Culture classify(Holder<Biome> biome) {
		Culture byId = biome.unwrapKey().map(k -> BY_ID.get(k.identifier())).orElse(null);
		if (byId != null) {
			return byId;
		}
		float t = biome.value().getBaseTemperature();
		boolean rain = biome.value().hasPrecipitation();
		if (is(biome, BiomeTags.IS_BADLANDS, ConventionalBiomeTags.IS_BADLANDS)) {
			return Culture.MESA;
		}
		if (is(biome, null, ConventionalBiomeTags.IS_SWAMP)) {
			return Culture.BAYOU;
		}
		if (is(biome, null, ConventionalBiomeTags.IS_DESERT)) {
			return Culture.DESERT;
		}
		if (is(biome, BiomeTags.IS_JUNGLE, ConventionalBiomeTags.IS_JUNGLE)) {
			return Culture.TROPICAL;
		}
		if (is(biome, BiomeTags.IS_SAVANNA, ConventionalBiomeTags.IS_SAVANNA)) {
			return Culture.STEPPE;
		}
		if (is(biome, BiomeTags.IS_BEACH, ConventionalBiomeTags.IS_BEACH) || is(biome, BiomeTags.IS_OCEAN, ConventionalBiomeTags.IS_OCEAN)
			|| is(biome, null, ConventionalBiomeTags.IS_STONY_SHORES)) {
			return t >= 0.8F ? Culture.TROPICAL : t < 0.15F ? Culture.TUNDRA : Culture.MARITIME;
		}
		if (is(biome, null, ConventionalBiomeTags.IS_SNOWY_PLAINS) || t < 0.15F && !is(biome, BiomeTags.IS_TAIGA, ConventionalBiomeTags.IS_TAIGA)) {
			return Culture.TUNDRA;
		}
		if (is(biome, BiomeTags.IS_TAIGA, ConventionalBiomeTags.IS_TAIGA) || is(biome, null, ConventionalBiomeTags.IS_CONIFEROUS_TREE)) {
			return Culture.NORTHERN;
		}
		if (is(biome, BiomeTags.IS_MOUNTAIN, ConventionalBiomeTags.IS_MOUNTAIN)) {
			return t < 0.6F ? Culture.ALPINE : Culture.MEDITERRANEAN;
		}
		if (is(biome, null, ConventionalBiomeTags.IS_WINDSWEPT)) {
			return Culture.MOORLAND;
		}
		if (is(biome, BiomeTags.IS_RIVER, ConventionalBiomeTags.IS_RIVER)) {
			return t >= 1.0F ? Culture.TROPICAL : t < 0.3F ? Culture.NORTHERN : Culture.TEMPERATE;
		}
		// plain climate for whatever is left, modded biomes included
		if (t >= 1.5F) {
			return rain ? Culture.TROPICAL : Culture.DESERT;
		}
		if (t >= 1.0F) {
			return rain ? Culture.TROPICAL : Culture.STEPPE;
		}
		if (t >= 0.9F) {
			return Culture.MEDITERRANEAN;
		}
		if (t < 0.35F) {
			return Culture.NORTHERN;
		}
		if (t < 0.55F) {
			return Culture.ALPINE;
		}
		return Culture.TEMPERATE;
	}

	private static boolean is(Holder<Biome> biome, TagKey<Biome> vanilla, TagKey<Biome> conventional) {
		return vanilla != null && biome.is(vanilla) || conventional != null && biome.is(conventional);
	}
}
