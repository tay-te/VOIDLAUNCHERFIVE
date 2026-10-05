package dev.voidmc.atlas.core.names;

import java.util.Locale;
import java.util.Map;

/**
 * A coarse family of biomes, used to pick fitting words for place names ("Fells" for hills, "Mire" for a swamp).
 * Vanilla biomes are mapped by hand; any other id (from any mod) is classified by the keywords in its path, so
 * {@code expanse:karst_spires} lands in {@link #MOUNTAIN} and {@code terralith:sakura_valley} in {@link #CHERRY}.
 */
public enum BiomeFamily {
	OCEAN,
	RIVER,
	BEACH,
	PLAINS,
	MEADOW,
	FOREST,
	BIRCH_FOREST,
	DARK_FOREST,
	TAIGA,
	SNOWY,
	MOUNTAIN,
	PEAKS,
	DESERT,
	BADLANDS,
	SAVANNA,
	JUNGLE,
	SWAMP,
	MANGROVE,
	MUSHROOM,
	CHERRY,
	PALE,
	CAVE,
	NETHER,
	END,
	OTHER;

	/** Every vanilla biome path, by hand, so the keyword rules never have to guess for them. */
	private static final Map<String, BiomeFamily> VANILLA = Map.ofEntries(
		Map.entry("ocean", OCEAN),
		Map.entry("deep_ocean", OCEAN),
		Map.entry("warm_ocean", OCEAN),
		Map.entry("lukewarm_ocean", OCEAN),
		Map.entry("deep_lukewarm_ocean", OCEAN),
		Map.entry("cold_ocean", OCEAN),
		Map.entry("deep_cold_ocean", OCEAN),
		Map.entry("frozen_ocean", OCEAN),
		Map.entry("deep_frozen_ocean", OCEAN),
		Map.entry("river", RIVER),
		Map.entry("frozen_river", RIVER),
		Map.entry("beach", BEACH),
		Map.entry("snowy_beach", BEACH),
		Map.entry("stony_shore", BEACH),
		Map.entry("plains", PLAINS),
		Map.entry("sunflower_plains", PLAINS),
		Map.entry("meadow", MEADOW),
		Map.entry("forest", FOREST),
		Map.entry("flower_forest", FOREST),
		Map.entry("birch_forest", BIRCH_FOREST),
		Map.entry("old_growth_birch_forest", BIRCH_FOREST),
		Map.entry("dark_forest", DARK_FOREST),
		Map.entry("pale_garden", PALE),
		Map.entry("taiga", TAIGA),
		Map.entry("old_growth_pine_taiga", TAIGA),
		Map.entry("old_growth_spruce_taiga", TAIGA),
		Map.entry("snowy_taiga", SNOWY),
		Map.entry("snowy_plains", SNOWY),
		Map.entry("ice_spikes", SNOWY),
		Map.entry("grove", SNOWY),
		Map.entry("snowy_slopes", SNOWY),
		Map.entry("windswept_hills", MOUNTAIN),
		Map.entry("windswept_gravelly_hills", MOUNTAIN),
		Map.entry("windswept_forest", MOUNTAIN),
		Map.entry("windswept_savanna", MOUNTAIN),
		Map.entry("jagged_peaks", PEAKS),
		Map.entry("frozen_peaks", PEAKS),
		Map.entry("stony_peaks", PEAKS),
		Map.entry("desert", DESERT),
		Map.entry("badlands", BADLANDS),
		Map.entry("eroded_badlands", BADLANDS),
		Map.entry("wooded_badlands", BADLANDS),
		Map.entry("savanna", SAVANNA),
		Map.entry("savanna_plateau", SAVANNA),
		Map.entry("jungle", JUNGLE),
		Map.entry("sparse_jungle", JUNGLE),
		Map.entry("bamboo_jungle", JUNGLE),
		Map.entry("swamp", SWAMP),
		Map.entry("mangrove_swamp", MANGROVE),
		Map.entry("mushroom_fields", MUSHROOM),
		Map.entry("cherry_grove", CHERRY),
		Map.entry("dripstone_caves", CAVE),
		Map.entry("lush_caves", CAVE),
		Map.entry("deep_dark", CAVE),
		Map.entry("nether_wastes", NETHER),
		Map.entry("crimson_forest", NETHER),
		Map.entry("warped_forest", NETHER),
		Map.entry("soul_sand_valley", NETHER),
		Map.entry("basalt_deltas", NETHER),
		Map.entry("the_end", END),
		Map.entry("end_highlands", END),
		Map.entry("end_midlands", END),
		Map.entry("end_barrens", END),
		Map.entry("small_end_islands", END),
		Map.entry("the_void", OTHER)
	);

	/**
	 * Classifies a biome registry id such as {@code minecraft:snowy_taiga} or {@code expanse:karst_spires}. Vanilla
	 * ids are looked up by hand; anything else falls back to keywords in the path, checked in an order that keeps
	 * the more telling word in charge (a snowy beach is a beach, a frozen river a river, a snowy taiga snowy).
	 *
	 * @param biomeId a registry id, with or without a namespace; {@code null} or blank gives {@link #OTHER}
	 * @return the family, never {@code null}
	 */
	public static BiomeFamily of(String biomeId) {
		if (biomeId == null || biomeId.isBlank()) {
			return OTHER;
		}
		String id = biomeId.trim().toLowerCase(Locale.ROOT);
		int colon = id.indexOf(':');
		String namespace = colon >= 0 ? id.substring(0, colon) : "minecraft";
		String path = colon >= 0 ? id.substring(colon + 1) : id;
		if (namespace.equals("minecraft")) {
			BiomeFamily exact = VANILLA.get(path);
			if (exact != null) {
				return exact;
			}
		}
		return byKeywords(path);
	}

	/** True for open water: {@link #OCEAN} and {@link #RIVER}. */
	public boolean isWater() {
		return this == OCEAN || this == RIVER;
	}

	private static BiomeFamily byKeywords(String path) {
		String[] tokens = path.split("[^a-z0-9]+");
		if (has(path, "mushroom")) {
			return MUSHROOM;
		}
		if (has(path, "mangrove")) {
			return MANGROVE;
		}
		if (has(path, "ocean") || token(tokens, "sea", "seas")) {
			return OCEAN;
		}
		if (has(path, "river", "stream")) {
			return RIVER;
		}
		if (has(path, "beach", "shore", "coast")) {
			return BEACH;
		}
		if (has(path, "nether", "crimson", "warped", "soul_sand", "basalt")) {
			return NETHER;
		}
		if (token(tokens, "end") || path.startsWith("the_end")) {
			return END;
		}
		if (has(path, "cave", "cavern", "deep_dark", "dripstone")) {
			return CAVE;
		}
		if (has(path, "cherry", "blossom", "sakura")) {
			return CHERRY;
		}
		if (token(tokens, "pale")) {
			return PALE;
		}
		if (has(path, "peak", "summit", "alps")) {
			return PEAKS;
		}
		if (has(path, "windswept")) {
			return MOUNTAIN;
		}
		if (has(path, "snow", "frozen", "tundra", "glacier", "frost") || token(tokens, "ice", "icy")) {
			return SNOWY;
		}
		if (has(path, "mountain", "hill", "highland", "cliff", "karst", "spire", "crag")) {
			return MOUNTAIN;
		}
		if (has(path, "desert", "dune")) {
			return DESERT;
		}
		if (has(path, "badland", "mesa", "canyon")) {
			return BADLANDS;
		}
		if (has(path, "savanna", "steppe")) {
			return SAVANNA;
		}
		if (has(path, "jungle", "rainforest")) {
			return JUNGLE;
		}
		if (has(path, "swamp", "marsh", "wetland") || token(tokens, "bog", "fen", "fens")) {
			return SWAMP;
		}
		if (has(path, "birch")) {
			return BIRCH_FOREST;
		}
		if (has(path, "dark")) {
			return DARK_FOREST;
		}
		if (has(path, "pine", "spruce", "taiga", "boreal")) {
			return TAIGA;
		}
		if (has(path, "forest", "wood", "grove", "glade")) {
			return FOREST;
		}
		if (has(path, "meadow", "prairie", "grassland", "field")) {
			return MEADOW;
		}
		if (has(path, "plain", "moor", "heath")) {
			return PLAINS;
		}
		return OTHER;
	}

	private static boolean has(String path, String... words) {
		for (String word : words) {
			if (path.contains(word)) {
				return true;
			}
		}
		return false;
	}

	private static boolean token(String[] tokens, String... words) {
		for (String token : tokens) {
			for (String word : words) {
				if (token.equals(word)) {
					return true;
				}
			}
		}
		return false;
	}
}
