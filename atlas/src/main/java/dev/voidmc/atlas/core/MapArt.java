package dev.voidmc.atlas.core;

import dev.voidmc.atlas.core.names.BiomeFamily;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The map art of section 9 (Figma 395:19899, "Atlas — biome view and map art"): the hand-tuned surface blocks, the
 * biome fills and their families, the water steps, and the styles. Pure tables and formulas; {@link TileRenderer}
 * draws with them.
 */
public final class MapArt {
	private MapArt() {
	}

	/** The map styles: Natural (default), Flat (no relief shading, water in 3 steps), Topographic (contours, quiet). */
	public enum Style {
		NATURAL("Natural"), FLAT("Flat"), TOPOGRAPHIC("Topographic");

		public final String label;

		Style(String label) {
			this.label = label;
		}
	}

	// --- blocks --------------------------------------------------------------------------------------------------

	/** The 33 commonest surface blocks, hand-tuned, at flat light (plus cobblestone, wheat, ripe wheat and moss). */
	private static final Map<String, Integer> BLOCKS = Map.ofEntries(
		Map.entry("grass_block", 0x455331), Map.entry("oak_leaves", 0x2C4025), Map.entry("birch_leaves", 0x424B2C),
		Map.entry("spruce_leaves", 0x20392B), Map.entry("dark_oak_leaves", 0x1E311A), Map.entry("jungle_leaves", 0x284426),
		Map.entry("acacia_leaves", 0x4F5029), Map.entry("mangrove_leaves", 0x303D20), Map.entry("cherry_leaves", 0x9B7382),
		Map.entry("sand", 0x9C9177), Map.entry("red_sand", 0x8B6145), Map.entry("sandstone", 0x95886E), Map.entry("terracotta", 0x755141),
		Map.entry("orange_terracotta", 0x865B3D), Map.entry("white_terracotta", 0xA08E85), Map.entry("yellow_terracotta", 0x92754C),
		Map.entry("red_terracotta", 0x6E423B), Map.entry("brown_terracotta", 0x493629), Map.entry("light_gray_terracotta", 0x776863),
		Map.entry("stone", 0x696C6F), Map.entry("calcite", 0xA5A5A0), Map.entry("gravel", 0x68625E), Map.entry("snow", 0xB0B9BE),
		Map.entry("snow_block", 0xB0B9BE), Map.entry("powder_snow", 0xB0B9BE), Map.entry("packed_ice", 0x8394A7), Map.entry("ice", 0x7C96AC),
		Map.entry("dirt", 0x624A36), Map.entry("coarse_dirt", 0x5B4534), Map.entry("podzol", 0x4E3820), Map.entry("mud", 0x333237),
		Map.entry("clay", 0x7A8189), Map.entry("dirt_path", 0x897452), Map.entry("farmland", 0x472F1E), Map.entry("oak_planks", 0x907554),
		Map.entry("cobblestone", 0x626365), Map.entry("wheat", 0x5E6A37), Map.entry("moss_block", 0x4D5C32), Map.entry("moss_carpet", 0x4D5C32));
	/** Ripe wheat (age 7), when the scan can tell. */
	public static final int RIPE_WHEAT = 0xA68F60;
	/** What grass and leaf colours in the table are measured against: plains. */
	public static final int REF_GRASS = 0x91BD59;
	public static final int REF_FOLIAGE = 0x77AB2F;
	public static final float CHROMA_CAP = 0.075F;
	public static final float LAND_MIN_L = 0.14F;
	public static final float LAND_MAX_L = 0.86F;
	/** Blocks not in the table: the shipped tone, lifted into the new range. */
	public static final float UNTUNED_LIFT = 0.12F;

	/** A tuned block's colour (0xRRGGBB) by its id ("minecraft:grass_block"), or -1. */
	public static int tuned(String blockId) {
		if (blockId == null) {
			return -1;
		}
		int colon = blockId.indexOf(':');
		if (colon > 0 && !blockId.startsWith("minecraft")) {
			return -1;
		}
		Integer c = BLOCKS.get(colon < 0 ? blockId : blockId.substring(colon + 1));
		return c == null ? -1 : c;
	}

	/**
	 * A grass or leaf colour in a biome (OKLab): the tuned colour plus 35% of the biome tint's a and b and 25% of its
	 * lightness, each measured against plains; chroma capped.
	 */
	public static float[] tinted(int tunedRgb, int biomeTintRgb, boolean foliage) {
		float[] base = Oklab.fromRgb(tunedRgb);
		float[] tint = Oklab.fromRgb(biomeTintRgb);
		float[] ref = Oklab.fromRgb(foliage ? REF_FOLIAGE : REF_GRASS);
		float[] out = {base[0] + (tint[0] - ref[0]) * 0.25F, base[1] + (tint[1] - ref[1]) * 0.35F, base[2] + (tint[2] - ref[2]) * 0.35F};
		capChroma(out, CHROMA_CAP);
		return out;
	}

	/** How much a block's relief shows (snow 1.3, stone 1.15, terracotta 1.1, sand 0.85, paths 0.8, farmland 0.6). */
	public static float shadeFactor(String blockId, Material m) {
		String path = blockId == null ? "" : blockId.substring(blockId.indexOf(':') + 1);
		if (path.equals("dirt_path")) {
			return 0.8F;
		}
		if (path.equals("farmland")) {
			return 0.6F;
		}
		return switch (m) {
			case SNOW -> 1.3F;
			case STONE, GRAVEL -> 1.15F;
			case TERRACOTTA -> 1.1F;
			case SAND -> 0.85F;
			default -> 1;
		};
	}

	// --- biomes --------------------------------------------------------------------------------------------------

	/** The biome view's families, in the legend's order. */
	public enum Family {
		OCEANS("Oceans"), RIVERS("Rivers"), BEACHES("Beaches"), PLAINS("Plains"), FORESTS("Forests"), TAIGA("Taiga"), SNOWY("Snowy"),
		MOUNTAINS("Mountains"), DESERTS("Deserts"), BADLANDS("Badlands"), JUNGLES("Jungles"), SWAMPS("Swamps"), RARE("Rare"), CAVES("Caves"),
		NETHER("Nether"), OTHER("Other");

		public final String label;

		Family(String label) {
			this.label = label;
		}
	}

	private static final Map<String, Integer> FILLS = new HashMap<>();
	private static final Map<String, Family> FAMILIES = new HashMap<>();

	private static void fill(Family f, Object... pairs) {
		for (int i = 0; i < pairs.length; i += 2) {
			FILLS.put((String) pairs[i], (Integer) pairs[i + 1]);
			FAMILIES.put((String) pairs[i], f);
		}
	}

	static {
		fill(Family.OCEANS, "ocean", 0x2D3F52, "deep_ocean", 0x213244, "lukewarm_ocean", 0x2A4752, "deep_lukewarm_ocean", 0x1E3946, "cold_ocean",
			0x2F3B4D, "frozen_ocean", 0x4A5763, "warm_ocean", 0x2F5056, "deep_cold_ocean", 0x283445, "deep_frozen_ocean", 0x404D59);
		fill(Family.RIVERS, "river", 0x385B67, "frozen_river", 0x61717B);
		fill(Family.BEACHES, "beach", 0x8F856E, "snowy_beach", 0x92999F, "stony_shore", 0x66696D);
		fill(Family.PLAINS, "plains", 0x6F7653, "sunflower_plains", 0x787B55, "meadow", 0x6D8169);
		fill(Family.FORESTS, "forest", 0x475C42, "flower_forest", 0x586648, "birch_forest", 0x636E51, "dark_forest", 0x314634,
			"old_growth_birch_forest", 0x5D6A4D, "windswept_forest", 0x4A5B4B);
		fill(Family.TAIGA, "taiga", 0x3A5349, "old_growth_pine_taiga", 0x2F493F, "old_growth_spruce_taiga", 0x2F493F);
		fill(Family.SNOWY, "snowy_plains", 0xA4ACB1, "snowy_taiga", 0x798A8B, "snowy_slopes", 0xAFB5BA, "grove", 0x728582, "ice_spikes", 0x98A7B4);
		fill(Family.MOUNTAINS, "windswept_hills", 0x5F6D61, "stony_peaks", 0x7D8085, "jagged_peaks", 0x989FA6, "frozen_peaks", 0xA1ADB5,
			"windswept_gravelly_hills", 0x6A6E6B);
		fill(Family.DESERTS, "desert", 0x998A6D, "savanna", 0x7E7855, "savanna_plateau", 0x787352, "windswept_savanna", 0x76735A);
		fill(Family.BADLANDS, "badlands", 0x805943, "eroded_badlands", 0x89664E, "wooded_badlands", 0x68543C);
		fill(Family.JUNGLES, "jungle", 0x365942, "sparse_jungle", 0x49664D, "bamboo_jungle", 0x476041);
		fill(Family.SWAMPS, "swamp", 0x474A34, "mangrove_swamp", 0x3A4430);
		fill(Family.RARE, "mushroom_fields", 0x715C6A, "cherry_grove", 0x9D7B87, "pale_garden", 0x888E89);
		fill(Family.CAVES, "lush_caves", 0x536B4D, "dripstone_caves", 0x735F4F, "deep_dark", 0x1F3135);
		fill(Family.NETHER, "nether_wastes", 0x5E3E3B, "crimson_forest", 0x582F33, "warped_forest", 0x274947, "soul_sand_valley", 0x51453A,
			"basalt_deltas", 0x414246);
		fill(Family.OTHER, "the_end", 0x55554C, "end_highlands", 0x5C5B50, "end_midlands", 0x58574D, "small_end_islands", 0x505047,
			"end_barrens", 0x4E4D45, "the_void", 0x1A1B1D);
	}

	/** A vanilla biome's fill (0xRRGGBB) by id, or -1 for any other. */
	public static int vanillaFill(String biomeId) {
		if (biomeId == null || !biomeId.startsWith("minecraft:")) {
			return -1;
		}
		Integer c = FILLS.get(biomeId.substring("minecraft:".length()));
		return c == null ? -1 : c;
	}

	/** A biome's family in the biome view (vanilla by the table, any other by its id's keywords). */
	public static Family family(String biomeId) {
		if (biomeId == null) {
			return Family.OTHER;
		}
		if (biomeId.startsWith("minecraft:")) {
			Family f = FAMILIES.get(biomeId.substring("minecraft:".length()));
			if (f != null) {
				return f;
			}
		}
		return switch (BiomeFamily.of(biomeId)) {
			case OCEAN -> Family.OCEANS;
			case RIVER -> Family.RIVERS;
			case BEACH -> Family.BEACHES;
			case PLAINS, MEADOW -> Family.PLAINS;
			case FOREST, BIRCH_FOREST, DARK_FOREST -> Family.FORESTS;
			case TAIGA -> Family.TAIGA;
			case SNOWY -> Family.SNOWY;
			case MOUNTAIN, PEAKS -> Family.MOUNTAINS;
			case DESERT, SAVANNA -> Family.DESERTS;
			case BADLANDS -> Family.BADLANDS;
			case JUNGLE -> Family.JUNGLES;
			case SWAMP, MANGROVE -> Family.SWAMPS;
			case MUSHROOM, CHERRY, PALE -> Family.RARE;
			case CAVE -> Family.CAVES;
			case NETHER -> Family.NETHER;
			default -> Family.OTHER;
		};
	}

	private static List<float[]> vanillaLabs;

	/**
	 * A modded biome's fill from its own grass and foliage: mix = 60% grass + 40% foliage (OKLab); L = 0.47 + (mix L −
	 * 0.62) × 0.6 within 0.40–0.60; chroma = min(0.050, mix chroma × 0.38) on the mix's hue; stepped 0.03 L away from
	 * a vanilla fill it would be within ΔE 0.025 of.
	 */
	public static int moddedFill(int grassRgb, int foliageRgb) {
		float[] g = Oklab.fromRgb(grassRgb);
		float[] f = Oklab.fromRgb(foliageRgb);
		float[] mix = {g[0] * 0.6F + f[0] * 0.4F, g[1] * 0.6F + f[1] * 0.4F, g[2] * 0.6F + f[2] * 0.4F};
		float L = Math.max(0.40F, Math.min(0.60F, 0.47F + (mix[0] - 0.62F) * 0.6F));
		float c = (float) Math.hypot(mix[1], mix[2]);
		float target = Math.min(0.050F, c * 0.38F);
		float k = c < 1e-6F ? 0 : target / c;
		float[] out = {L, mix[1] * k, mix[2] * k};
		List<float[]> labs = vanillaLabs;
		if (labs == null) {
			labs = new java.util.ArrayList<>();
			for (int v : FILLS.values()) {
				labs.add(Oklab.fromRgb(v));
			}
			vanillaLabs = labs;
		}
		for (float[] v : labs) {
			double de = Math.sqrt((v[0] - out[0]) * (v[0] - out[0]) + (v[1] - out[1]) * (v[1] - out[1]) + (v[2] - out[2]) * (v[2] - out[2]));
			if (de < 0.025) {
				out[0] += out[0] >= v[0] ? 0.03F : -0.03F;
				break;
			}
		}
		return Oklab.toRgb(out);
	}

	/** The well biome borders are mixed toward, and the dark ink on light fills. */
	public static final int WELL = 0x0E0F11;
	/** Borders: the fill mixed this far toward the well. */
	public static final float BORDER_MIX = 0.45F;
	/** Fills lighter than this take the dark ink for their names. */
	public static final float LIGHT_FILL = 0.58F;

	/** A fill dimmed when another family is highlighted: L 0.22 + (L − 0.22) × 0.45, chroma × 0.3. */
	public static float[] dimmed(float[] lab) {
		return new float[]{0.22F + (lab[0] - 0.22F) * 0.45F, lab[1] * 0.3F, lab[2] * 0.3F};
	}

	/** The climate words for the biome hover. */
	public static String temperatureWord(float t) {
		return t < 0.15F ? "frozen" : t < 0.5F ? "cold" : t <= 0.95F ? "mild" : "hot";
	}

	public static String rainfallWord(float r) {
		return r < 0.3F ? "dry" : r <= 0.7F ? "moderate" : "wet";
	}

	/** "minecraft:dark_forest" to "Dark Forest". */
	public static String biomeLabel(String biomeId) {
		String path = biomeId == null ? "" : biomeId.substring(biomeId.indexOf(':') + 1);
		StringBuilder b = new StringBuilder();
		for (String w : path.replace('/', '_').split("_")) {
			if (!w.isEmpty()) {
				b.append(b.isEmpty() ? "" : " ").append(Character.toUpperCase(w.charAt(0))).append(w.substring(1).toLowerCase(Locale.ROOT));
			}
		}
		return b.toString();
	}

	// --- water ---------------------------------------------------------------------------------------------------

	/** Sea steps by depth 1, 2–3, 4–6, 7–11, 12–19, 20–31, 32+. */
	public static final int[] SEA = {0x3F5A64, 0x344F5C, 0x294251, 0x213747, 0x1B2D3D, 0x152433, 0x111D2A};
	/** River steps (river biomes and 1–2 wide channels), never deeper than the third. */
	public static final int[] RIVER = {0x3F6167, 0x35575F, 0x2C4D56};
	public static final int FOAM_SEA = 0x5D7075;
	public static final int FOAM_RIVER = 0x556B6D;
	/** Biome water colour shows this much in a and b; water chroma is capped. */
	public static final float WATER_TINT = 0.25F;
	public static final float WATER_CHROMA_CAP = 0.055F;
	public static final int WATER_REF = 0x3F76E4;

	public static int seaStep(int depth) {
		if (depth <= 1) {
			return 0;
		}
		if (depth <= 3) {
			return 1;
		}
		if (depth <= 6) {
			return 2;
		}
		if (depth <= 11) {
			return 3;
		}
		if (depth <= 19) {
			return 4;
		}
		if (depth <= 31) {
			return 5;
		}
		return 6;
	}

	/** Flat style: water in three steps (shallow, middle, deep). */
	public static int flatStep(int depth) {
		return depth <= 3 ? 0 : depth <= 11 ? 3 : 5;
	}

	/** A water colour tinted toward the biome's water (25% in a and b), chroma capped. OKLab. */
	public static float[] water(int stepRgb, int biomeWaterRgb) {
		float[] base = Oklab.fromRgb(stepRgb);
		float[] biome = Oklab.fromRgb(biomeWaterRgb);
		float[] ref = Oklab.fromRgb(WATER_REF);
		float[] out = {base[0], base[1] + (biome[1] - ref[1]) * WATER_TINT, base[2] + (biome[2] - ref[2]) * WATER_TINT};
		capChroma(out, WATER_CHROMA_CAP);
		return out;
	}

	// --- night ---------------------------------------------------------------------------------------------------

	/** The moonlit map: L × 0.56 + 0.015; a × 0.40 − 0.008; b × 0.40 − 0.028. */
	public static float[] moonlit(float[] day) {
		return new float[]{day[0] * 0.56F + 0.015F, day[1] * 0.40F - 0.008F, day[2] * 0.40F - 0.028F};
	}

	/** Block light's glow at a distance from a source of a light level: clamp((level − 1.15 d − 3) / 10, 0, 1)^1.3. */
	public static float glow(int level, double distance) {
		double g = (level - 1.15 * distance - 3) / 10;
		return g <= 0 ? 0 : (float) Math.pow(Math.min(1, g), 1.3);
	}

	/** A night pixel lit by block light {@code g}: L toward day × 0.95 + 0.03 by 0.75 g; a, b toward day × 0.5 + warm. */
	public static float[] lit(float[] night, float[] day, float g, boolean soul) {
		if (g <= 0) {
			return night;
		}
		// warm: hue 70°, chroma 0.075 (soul light: cooler, hue 200°)
		double hue = Math.toRadians(soul ? 200 : 70);
		float wa = (float) (Math.cos(hue) * 0.075);
		float wb = (float) (Math.sin(hue) * 0.075);
		float L = night[0] + (day[0] * 0.95F + 0.03F - night[0]) * 0.75F * g;
		float a = night[1] + (day[1] * 0.5F + wa - night[1]) * g;
		float b = night[2] + (day[2] * 0.5F + wb - night[2]) * g;
		return new float[]{L, a, b};
	}

	static void capChroma(float[] lab, float max) {
		float c = (float) Math.hypot(lab[1], lab[2]);
		if (c > max) {
			float k = max / c;
			lab[1] *= k;
			lab[2] *= k;
		}
	}

	/** The art's version, for images drawn with another (they are drawn again). */
	public static final int VERSION = 9_004;
}
