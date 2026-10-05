package dev.voidmc.atlas.core.detect;

import java.util.Locale;

/**
 * Block signals: the few dozen kinds of block whose presence says a structure stands here (a bell, crying obsidian,
 * reinforced deepslate...), named by block id so the logic needs no game. A block's <em>code</em> packs its signal with
 * what it is to stand in or see through ({@link #OPEN}, {@link #SOLID}, {@link #WATER}, {@link #LAVA}):
 * {@code signal << 2 | base}.
 *
 * <p>Classification is by id alone and is done once per block state (the client caches it), so the scan itself only
 * compares small ints.
 */
public final class Sig {
	// bases
	public static final int OPEN = 0;
	public static final int SOLID = 1;
	public static final int WATER = 2;
	public static final int LAVA = 3;
	/** A block not loaded (outside the chunks the client holds). */
	public static final int UNKNOWN = -1;

	public static final int NONE = 0;
	public static final int BELL = 1;
	public static final int BED = 2;
	// job sites, one signal each, so a window can count how many kinds there are
	public static final int COMPOSTER = 3;
	public static final int LECTERN = 4;
	public static final int SMOKER = 5;
	public static final int BLAST_FURNACE = 6;
	public static final int BARREL = 7;
	public static final int CARTOGRAPHY_TABLE = 8;
	public static final int FLETCHING_TABLE = 9;
	public static final int GRINDSTONE = 10;
	public static final int LOOM = 11;
	public static final int SMITHING_TABLE = 12;
	public static final int STONECUTTER = 13;
	public static final int CAULDRON = 14;
	public static final int BREWING_STAND = 15;
	public static final int CRYING_OBSIDIAN = 16;
	public static final int OBSIDIAN = 17;
	public static final int SPAWNER = 18;
	public static final int MOSSY_COBBLE = 19;
	public static final int COBBLE = 20;
	public static final int COBWEB = 21;
	public static final int RAIL = 22;
	public static final int WOOD_FENCE = 23;
	public static final int PLANKS = 24;
	public static final int DEEPSLATE_TILES = 25;
	public static final int DEEPSLATE_BRICKS = 26;
	public static final int SCULK = 27;
	public static final int SCULK_SENSOR = 28;
	public static final int SCULK_SHRIEKER = 29;
	public static final int REINFORCED_DEEPSLATE = 30;
	public static final int PRISMARINE = 31;
	public static final int DARK_PRISMARINE = 32;
	public static final int SEA_LANTERN = 33;
	public static final int END_PORTAL_FRAME = 34;
	public static final int STONE_BRICKS = 35;
	public static final int MOSSY_STONE_BRICKS = 36;
	public static final int CRACKED_STONE_BRICKS = 37;
	public static final int CHISELED_STONE_BRICKS = 38;
	public static final int INFESTED_BRICKS = 39;
	public static final int IRON_BARS = 40;
	public static final int BOOKSHELF = 41;
	public static final int BUDDING_AMETHYST = 42;
	public static final int AMETHYST_BLOCK = 43;
	public static final int AMETHYST_CLUSTER = 44;
	public static final int CALCITE = 45;
	public static final int SMOOTH_BASALT = 46;
	public static final int SUSPICIOUS_GRAVEL = 47;
	public static final int SUSPICIOUS_SAND = 48;
	public static final int TERRACOTTA = 49;
	public static final int MUD_BRICKS = 50;
	public static final int TUFF_BRICKS = 51;
	public static final int COPPER_BUILT = 52;
	public static final int TRIAL_SPAWNER = 53;
	public static final int VAULT = 54;
	public static final int SANDSTONE = 55;
	public static final int CHISELED_SANDSTONE = 56;
	public static final int ORANGE_TERRACOTTA = 57;
	public static final int BLUE_TERRACOTTA = 58;
	public static final int TNT = 59;
	public static final int STONE_PRESSURE_PLATE = 60;
	public static final int DISPENSER = 61;
	public static final int TRIPWIRE_HOOK = 62;
	public static final int STICKY_PISTON = 63;
	public static final int DARK_OAK_LOG = 64;
	public static final int DARK_OAK_PLANKS = 65;
	public static final int BIRCH_PLANKS = 66;
	public static final int WHITE_BANNER = 67;
	public static final int WHITE_CARPET = 68;
	public static final int SNOW_BLOCK = 69;
	public static final int ICE = 70;
	public static final int FURNACE = 71;
	public static final int CHEST = 72;
	public static final int SHIP_WOOD = 73;
	public static final int NETHER_BRICKS = 74;
	public static final int NETHER_BRICK_FENCE = 75;
	public static final int NETHER_WART = 76;
	public static final int BLACKSTONE_BRICKS = 77;
	public static final int GILDED_BLACKSTONE = 78;
	public static final int PURPUR = 79;
	public static final int MAGMA = 80;
	public static final int GOLD_BLOCK = 81;
	/** Natural ground: stone and its kin, dirt, sand, gravel, ores, netherrack... (what a cave is cut into). */
	public static final int EARTH = 82;
	public static final int COUNT = 83;

	public static final int FIRST_JOB = COMPOSTER;
	public static final int LAST_JOB = BREWING_STAND;

	/**
	 * Signals worth a block-by-block look in a section that has them: their position matters (a bell is the middle of a
	 * village, a spawner the middle of a dungeon). Everything else is only counted, from the sections' palettes.
	 */
	public static final long ANCHORS = bits(BELL, CRYING_OBSIDIAN, SPAWNER, END_PORTAL_FRAME, TRIAL_SPAWNER, VAULT, BUDDING_AMETHYST, AMETHYST_CLUSTER,
		SUSPICIOUS_GRAVEL, SUSPICIOUS_SAND, BLUE_TERRACOTTA, TNT, DISPENSER, TRIPWIRE_HOOK, STICKY_PISTON, WHITE_BANNER, FURNACE, CHEST);
	/** {@link #ANCHORS} past bit 63: the white banner, the furnace and the chest. */
	public static final long ANCHORS_HIGH = bitsHigh(WHITE_BANNER, FURNACE, CHEST);

	/**
	 * The signals the palette counts are taken for (the counted rules and the village tallies), and the anchors: a
	 * section with none of them (stone, dirt, ores, water...) is not counted at all.
	 */
	private static final boolean[] COUNTED = new boolean[COUNT];

	static {
		int[] counted = {BED, PRISMARINE, DARK_PRISMARINE, SEA_LANTERN, DARK_OAK_PLANKS, COBBLE, BIRCH_PLANKS, DARK_OAK_LOG, REINFORCED_DEEPSLATE,
			DEEPSLATE_TILES, DEEPSLATE_BRICKS, SCULK, SCULK_SHRIEKER, SCULK_SENSOR, RAIL, WOOD_FENCE, PLANKS, COBWEB, STONE_BRICKS, MOSSY_STONE_BRICKS,
			CRACKED_STONE_BRICKS, INFESTED_BRICKS, BOOKSHELF, IRON_BARS, TUFF_BRICKS, COPPER_BUILT, NETHER_BRICKS, NETHER_BRICK_FENCE, NETHER_WART,
			BLACKSTONE_BRICKS, GILDED_BLACKSTONE, PURPUR};
		for (int s : counted) {
			COUNTED[s] = true;
		}
		for (int s = FIRST_JOB; s <= LAST_JOB; s++) {
			COUNTED[s] = true;
		}
		for (int s = 0; s < COUNT; s++) {
			COUNTED[s] |= isAnchor(s);
		}
	}

	/** Whether a section holding this signal is counted (see {@link #COUNTED}). */
	public static boolean counted(int signal) {
		return signal > 0 && signal < COUNT && COUNTED[signal];
	}

	private static final String[] NAMES = new String[COUNT];

	static {
		String[] n = {"none", "bell", "bed", "composter", "lectern", "smoker", "blast_furnace", "barrel", "cartography_table", "fletching_table",
			"grindstone", "loom", "smithing_table", "stonecutter", "cauldron", "brewing_stand", "crying_obsidian", "obsidian", "spawner",
			"mossy_cobble", "cobble", "cobweb", "rail", "wood_fence", "planks", "deepslate_tiles", "deepslate_bricks", "sculk", "sculk_sensor",
			"sculk_shrieker", "reinforced_deepslate", "prismarine", "dark_prismarine", "sea_lantern", "end_portal_frame", "stone_bricks",
			"mossy_stone_bricks", "cracked_stone_bricks", "chiseled_stone_bricks", "infested_bricks", "iron_bars", "bookshelf", "budding_amethyst",
			"amethyst_block", "amethyst_cluster", "calcite", "smooth_basalt", "suspicious_gravel", "suspicious_sand", "terracotta", "mud_bricks",
			"tuff_bricks", "copper_built", "trial_spawner", "vault", "sandstone", "chiseled_sandstone", "orange_terracotta", "blue_terracotta", "tnt",
			"stone_pressure_plate", "dispenser", "tripwire_hook", "sticky_piston", "dark_oak_log", "dark_oak_planks", "birch_planks", "white_banner",
			"white_carpet", "snow_block", "ice", "furnace", "chest", "ship_wood", "nether_bricks", "nether_brick_fence", "nether_wart",
			"blackstone_bricks", "gilded_blackstone", "purpur", "magma", "gold_block", "earth"};
		System.arraycopy(n, 0, NAMES, 0, COUNT);
	}

	private Sig() {
	}

	public static String name(int signal) {
		return signal >= 0 && signal < COUNT ? NAMES[signal] : "?";
	}

	public static int code(int signal, int base) {
		return signal << 2 | base;
	}

	public static int signal(int code) {
		return code < 0 ? NONE : code >>> 2;
	}

	public static int base(int code) {
		return code < 0 ? UNKNOWN : code & 3;
	}

	public static boolean open(int code) {
		return code >= 0 && (code & 3) == OPEN;
	}

	public static boolean solid(int code) {
		return code >= 0 && (code & 3) == SOLID;
	}

	public static boolean water(int code) {
		return code >= 0 && (code & 3) == WATER;
	}

	public static boolean isAnchor(int signal) {
		return signal < 64 ? (ANCHORS >>> signal & 1) != 0 : (ANCHORS_HIGH >>> (signal - 64) & 1) != 0;
	}

	public static boolean isJobSite(int signal) {
		return signal >= FIRST_JOB && signal <= LAST_JOB;
	}

	private static long bits(int... signals) {
		long b = 0;
		for (int s : signals) {
			if (s < 64) {
				b |= 1L << s;
			}
		}
		return b;
	}

	private static long bitsHigh(int... signals) {
		long b = 0;
		for (int s : signals) {
			if (s >= 64) {
				b |= 1L << (s - 64);
			}
		}
		return b;
	}

	private static final java.util.Set<String> EARTH_IDS = java.util.Set.of("stone", "deepslate", "granite", "diorite", "andesite", "tuff", "dirt",
		"coarse_dirt", "rooted_dirt", "grass_block", "podzol", "mycelium", "gravel", "sand", "red_sand", "clay", "calcite", "dripstone_block",
		"moss_block", "mud", "snow_block", "powder_snow", "packed_ice", "netherrack", "basalt", "blackstone", "soul_sand", "soul_soil", "end_stone",
		"cobbled_deepslate", "infested_stone", "infested_deepslate", "raw_iron_block", "raw_copper_block", "smooth_basalt", "magma_block");

	private static final String[] SHIP_WOODS = {"oak", "spruce", "birch", "jungle", "acacia", "dark_oak"};

	/**
	 * The signal of a block, by its registry id ({@code minecraft:bell}); {@link #NONE} for everything that says nothing.
	 * Only vanilla's blocks carry signals: a mod's look-alike blocks never make a structure appear.
	 */
	public static int of(String id) {
		if (!id.startsWith("minecraft:")) {
			return NONE;
		}
		String p = id.substring(10).toLowerCase(Locale.ROOT);
		switch (p) {
			case "bell": return BELL;
			case "composter": return COMPOSTER;
			case "lectern": return LECTERN;
			case "smoker": return SMOKER;
			case "blast_furnace": return BLAST_FURNACE;
			case "barrel": return BARREL;
			case "cartography_table": return CARTOGRAPHY_TABLE;
			case "fletching_table": return FLETCHING_TABLE;
			case "grindstone": return GRINDSTONE;
			case "loom": return LOOM;
			case "smithing_table": return SMITHING_TABLE;
			case "stonecutter": return STONECUTTER;
			case "cauldron", "water_cauldron", "lava_cauldron", "powder_snow_cauldron": return CAULDRON;
			case "brewing_stand": return BREWING_STAND;
			case "crying_obsidian": return CRYING_OBSIDIAN;
			case "obsidian": return OBSIDIAN;
			case "spawner": return SPAWNER;
			case "mossy_cobblestone", "mossy_cobblestone_stairs", "mossy_cobblestone_slab", "mossy_cobblestone_wall": return MOSSY_COBBLE;
			case "cobblestone", "cobblestone_stairs", "cobblestone_slab", "cobblestone_wall": return COBBLE;
			case "cobweb": return COBWEB;
			case "rail", "powered_rail", "detector_rail", "activator_rail": return RAIL;
			case "deepslate_tiles", "cracked_deepslate_tiles", "deepslate_tile_stairs", "deepslate_tile_slab", "deepslate_tile_wall": return DEEPSLATE_TILES;
			case "deepslate_bricks", "cracked_deepslate_bricks", "deepslate_brick_stairs", "deepslate_brick_slab", "deepslate_brick_wall",
				"chiseled_deepslate", "polished_deepslate", "polished_deepslate_stairs", "polished_deepslate_slab", "polished_deepslate_wall":
				return DEEPSLATE_BRICKS;
			case "sculk", "sculk_vein", "sculk_catalyst": return SCULK;
			case "sculk_sensor", "calibrated_sculk_sensor": return SCULK_SENSOR;
			case "sculk_shrieker": return SCULK_SHRIEKER;
			case "reinforced_deepslate": return REINFORCED_DEEPSLATE;
			case "prismarine", "prismarine_bricks", "prismarine_stairs", "prismarine_slab", "prismarine_wall", "prismarine_brick_stairs",
				"prismarine_brick_slab": return PRISMARINE;
			case "dark_prismarine", "dark_prismarine_stairs", "dark_prismarine_slab": return DARK_PRISMARINE;
			case "sea_lantern": return SEA_LANTERN;
			case "end_portal_frame": return END_PORTAL_FRAME;
			case "stone_bricks", "stone_brick_stairs", "stone_brick_slab", "stone_brick_wall": return STONE_BRICKS;
			case "mossy_stone_bricks", "mossy_stone_brick_stairs", "mossy_stone_brick_slab", "mossy_stone_brick_wall": return MOSSY_STONE_BRICKS;
			case "cracked_stone_bricks": return CRACKED_STONE_BRICKS;
			case "chiseled_stone_bricks": return CHISELED_STONE_BRICKS;
			case "infested_stone_bricks", "infested_mossy_stone_bricks", "infested_cracked_stone_bricks", "infested_chiseled_stone_bricks":
				return INFESTED_BRICKS;
			case "iron_bars": return IRON_BARS;
			case "bookshelf": return BOOKSHELF;
			case "budding_amethyst": return BUDDING_AMETHYST;
			case "amethyst_block": return AMETHYST_BLOCK;
			case "amethyst_cluster", "large_amethyst_bud", "medium_amethyst_bud", "small_amethyst_bud": return AMETHYST_CLUSTER;
			case "calcite": return CALCITE;
			case "smooth_basalt": return SMOOTH_BASALT;
			case "suspicious_gravel": return SUSPICIOUS_GRAVEL;
			case "suspicious_sand": return SUSPICIOUS_SAND;
			case "orange_terracotta": return ORANGE_TERRACOTTA;
			case "blue_terracotta": return BLUE_TERRACOTTA;
			case "mud_bricks", "mud_brick_stairs", "mud_brick_slab", "mud_brick_wall", "packed_mud": return MUD_BRICKS;
			case "tuff_bricks", "tuff_brick_stairs", "tuff_brick_slab", "tuff_brick_wall", "chiseled_tuff", "chiseled_tuff_bricks", "polished_tuff",
				"polished_tuff_stairs", "polished_tuff_slab", "polished_tuff_wall": return TUFF_BRICKS;
			case "trial_spawner": return TRIAL_SPAWNER;
			case "vault": return VAULT;
			case "sandstone", "cut_sandstone", "smooth_sandstone", "sandstone_stairs", "sandstone_slab", "sandstone_wall", "cut_sandstone_slab",
				"smooth_sandstone_stairs", "smooth_sandstone_slab": return SANDSTONE;
			case "chiseled_sandstone": return CHISELED_SANDSTONE;
			case "tnt": return TNT;
			case "stone_pressure_plate": return STONE_PRESSURE_PLATE;
			case "dispenser": return DISPENSER;
			case "tripwire_hook": return TRIPWIRE_HOOK;
			case "sticky_piston": return STICKY_PISTON;
			case "dark_oak_log", "dark_oak_wood", "stripped_dark_oak_log", "stripped_dark_oak_wood": return DARK_OAK_LOG;
			case "dark_oak_planks": return DARK_OAK_PLANKS;
			case "birch_planks": return BIRCH_PLANKS;
			case "white_banner", "white_wall_banner": return WHITE_BANNER;
			case "white_carpet": return WHITE_CARPET;
			case "snow_block": return SNOW_BLOCK;
			case "ice", "packed_ice", "blue_ice": return ICE;
			case "furnace": return FURNACE;
			case "chest", "trapped_chest": return CHEST;
			case "nether_bricks", "cracked_nether_bricks", "chiseled_nether_bricks", "nether_brick_stairs", "nether_brick_slab", "nether_brick_wall":
				return NETHER_BRICKS;
			case "nether_brick_fence": return NETHER_BRICK_FENCE;
			case "nether_wart": return NETHER_WART;
			case "polished_blackstone_bricks", "cracked_polished_blackstone_bricks", "polished_blackstone_brick_stairs",
				"polished_blackstone_brick_slab", "polished_blackstone_brick_wall", "chiseled_polished_blackstone": return BLACKSTONE_BRICKS;
			case "gilded_blackstone": return GILDED_BLACKSTONE;
			case "purpur_block", "purpur_pillar", "purpur_stairs", "purpur_slab": return PURPUR;
			case "magma_block": return MAGMA;
			case "gold_block": return GOLD_BLOCK;
			default:
				break;
		}
		if (p.endsWith("_bed")) {
			return BED;
		}
		if (p.endsWith("_ore") || EARTH_IDS.contains(p)) {
			return EARTH;
		}
		if (p.endsWith("_glazed_terracotta") || p.equals("terracotta") || p.endsWith("_terracotta")) {
			return TERRACOTTA;
		}
		if (p.contains("copper") && !p.contains("ore") && !p.equals("raw_copper_block") && !p.contains("torch") && !p.contains("lantern")
			&& !p.contains("chain") && !p.contains("bars")) {
			return COPPER_BUILT;
		}
		for (String wood : SHIP_WOODS) {
			if (p.equals(wood + "_planks")) {
				return PLANKS;
			}
			if (p.equals(wood + "_fence")) {
				return WOOD_FENCE;
			}
			if (p.equals(wood + "_log") || p.equals(wood + "_wood") || p.equals("stripped_" + wood + "_log") || p.equals("stripped_" + wood + "_wood")
				|| p.equals(wood + "_trapdoor") || p.equals(wood + "_stairs") || p.equals(wood + "_slab")) {
				return SHIP_WOOD;
			}
		}
		return NONE;
	}
}
