package dev.voidmc.expanse.client.atlas;

import java.util.Locale;

/**
 * The names of an Expanse world's lands, lakes, rivers and landmarks, as the VOID Atlas map writes them: made up from
 * the world's seed and the place's position, so a place is called the same every session and on every client.
 *
 * <p>The words follow the country. The temperate heartland is Bohemia, its names Czech as German clerks wrote them
 * down (Brezov Forest, Lake Rybnitz, the Olsava); the cold north is Norse (Kaldvik Fell); dry and tropical country
 * has a southern tongue (the Sabaran Sands). What a place is called follows what it is: a forest region is a Forest,
 * Wood or Woods, a moor a Moor or Heath, a big lake a Lake and a small one a Pond.
 */
public final class ExpanseNames {
	/** What a region's land is, from its biome: decides the word after the name and the tongue of the name. */
	enum Land {
		WOOD, PINE, FIELDS, ORCHARD, HILLS, MOUNTAINS, MARSH, BAYOU, MOOR, STEPPE, SAVANNA, OUTBACK, DESERT, SALT, BADLANDS,
		JUNGLE, CLOUD_FOREST, SNOW, FJORD, COAST, SEA, DEEP_SEA, RIVER, CAVES, VOLCANIC, KARST, TEPUI, MUSHROOM, OTHER
	}

	enum Tongue {
		BOHEMIAN, NORSE, SOUTHERN
	}

	private ExpanseNames() {
	}

	/**
	 * The name of a place Atlas is about to label, or null to leave it to Atlas.
	 *
	 * @param kind     Atlas's kind: {@code atlas:region}, {@code atlas:lake}, {@code atlas:river}, {@code atlas:peak},
	 *                 {@code atlas:waterfall}, {@code atlas:cave_mouth} or {@code atlas:ravine}
	 * @param biome    the biome there, like {@code expanse:heather_moor}
	 * @param area     its size in square blocks as far as it is explored
	 * @param seedHash Atlas's hash of the world's seed
	 */
	public static String name(String kind, String biome, int x, int z, long area, long seedHash) {
		Land land = land(biome);
		long h = mix(seedHash ^ kind.hashCode() * 0x9E3779B97F4A7C15L, x, z);
		Tongue tongue = tongue(land);
		return switch (kind) {
			case "atlas:region" -> region(land, tongue, area, h);
			case "atlas:lake" -> area >= 60_000 ? "Lake " + word(tongue, h) : pick(h >>> 40, "%s Pond", "%s Mere", "%s Pond", "%s Tarn")
				.formatted(word(tongue, h));
			case "atlas:river" -> river(tongue, h);
			case "atlas:peak" -> (land == Land.MOUNTAINS || land == Land.SNOW || land == Land.VOLCANIC || land == Land.FJORD
				? pick(h >>> 40, "Mount %s", "%s Peak", "%s Crag", "%s Horn")
				: pick(h >>> 40, "%s Hill", "%s Crag", "%s Knoll", "%s Tor")).formatted(word(tongue, h));
			case "atlas:waterfall" -> pick(h >>> 40, "%s Falls", "%s Force", "%s Falls").formatted(word(tongue, h));
			case "atlas:cave_mouth" -> pick(h >>> 40, "%s Cave", "%s Hole", "%s Grotto", "%s Cave").formatted(word(tongue, h));
			case "atlas:ravine" -> pick(h >>> 40, "%s Gorge", "%s Ravine", "%s Cleft").formatted(word(tongue, h));
			default -> null;
		};
	}

	private static String region(Land land, Tongue tongue, long area, long h) {
		String w = word(tongue, h);
		long p = h >>> 40;
		String form = switch (land) {
			case WOOD -> pick(p, "%s Forest", "%s Wood", "%s Woods", "%s Forest");
			case PINE -> pick(p, "%s Forest", "%s Pinewood", "%s Firs");
			case FIELDS -> pick(p, "%s Fields", "%s Meadows", "%s Vale", "%s Lea");
			case ORCHARD -> pick(p, "%s Groves", "%s Orchards");
			case HILLS -> pick(p, "%s Hills", "%s Uplands", "%s Heights");
			case MOUNTAINS -> pick(p, "%s Mountains", "%s Range", "%s Peaks");
			case MARSH -> pick(p, "%s Marsh", "%s Fen", "%s Bog");
			case BAYOU -> pick(p, "%s Bayou", "%s Swamp");
			case MOOR -> pick(p, "%s Moor", "%s Heath");
			case STEPPE -> pick(p, "%s Steppe", "%s Plain");
			case SAVANNA -> pick(p, "%s Savanna", "%s Grassland");
			case OUTBACK -> pick(p, "%s Outback", "%s Scrub");
			case DESERT -> pick(p, "%s Desert", "%s Sands", "%s Waste");
			case SALT -> pick(p, "%s Salt Flats", "%s Pan");
			case BADLANDS -> pick(p, "%s Badlands", "%s Canyons");
			case JUNGLE -> pick(p, "%s Jungle", "%s Rainforest");
			case CLOUD_FOREST -> "%s Cloud Forest";
			case SNOW -> pick(p, "%s Tundra", "%s Snows", "%s Barrens");
			case FJORD -> pick(p, "%s Fjords", "%s Sound");
			case COAST -> pick(p, "%s Coast", "%s Strand", "%s Shore");
			case SEA -> area >= 400_000 ? "%s Sea" : pick(p, "%s Bay", "%s Sound");
			case DEEP_SEA -> pick(p, "%s Deep", "%s Sea");
			case RIVER -> null;
			case CAVES -> pick(p, "%s Caves", "%s Grottoes", "%s Deeps");
			case VOLCANIC -> pick(p, "%s Fells", "%s Ashlands", "%s Volcanoes");
			case KARST -> pick(p, "%s Spires", "%s Karst");
			case TEPUI -> pick(p, "%s Tepui", "%s Tablelands");
			case MUSHROOM -> pick(p, "%s Isle", "%s Fields");
			case OTHER -> pick(p, "%s Lands", "%s Country");
		};
		return form == null ? river(tongue, h) : form.formatted(w);
	}

	/** A river's name: one word, as rivers are named (the Vltava, the Sazava). */
	private static String river(Tongue tongue, long h) {
		String root = pick(h, roots(tongue));
		String end = switch (tongue) {
			case BOHEMIAN -> pick(h >>> 20, "ava", "ka", "nice", "a", "ina", "ava");
			case NORSE -> pick(h >>> 20, "elv", "a", "aa", "elv");
			case SOUTHERN -> pick(h >>> 20, "a", "ane", "ir", "os");
		};
		return join(root, end);
	}

	/** A place name: a root and an ending in the country's tongue (Skalitz, Talmberg, Kaldvik, Sabaran). */
	static String word(Tongue tongue, long h) {
		String root = pick(h, roots(tongue));
		String end = switch (tongue) {
			case BOHEMIAN -> pick(h >>> 20, "ov", "ovice", "itz", "any", "in", "etz", "ec", "enice", "berg", "hof", "au", "ek", "ow", "itz", "ov");
			case NORSE -> pick(h >>> 20, "heim", "vik", "dal", "gard", "stad", "by", "fell", "mark", "ness", "lund", "holm");
			case SOUTHERN -> pick(h >>> 20, "ara", "esh", "una", "an", "ir", "ida", "ar", "oma", "ul", "eia", "an");
		};
		return join(root, end);
	}

	private static String[] roots(Tongue tongue) {
		return switch (tongue) {
			case BOHEMIAN -> BOHEMIAN;
			case NORSE -> NORSE;
			case SOUTHERN -> SOUTHERN;
		};
	}

	private static final String[] BOHEMIAN = {"Brez", "Dub", "Hol", "Lip", "Kamen", "Ryb", "Zlat", "Ols", "Sed", "Javor", "Hrab", "Vrb", "Kost",
		"Lhot", "Mal", "Bor", "Strak", "Petr", "Zbor", "Chlum", "Les", "Skal", "Tal", "Rad", "Vys", "Mrak", "Vod", "Krasn", "Sobot", "Ledet",
		"Mer", "Uzh", "Trosk", "Prib", "Bechyn", "Tyn", "Hrus", "Jedl", "Kuten", "Neu", "Rat", "Sas", "Vran", "Zhel", "Semin", "Mokr", "Studen",
		"Hlub", "Bystr", "Cern", "Bel", "Smrk", "Lub", "Dobr", "Rozm"};
	private static final String[] NORSE = {"Skjold", "Hrafn", "Ulf", "Kald", "Fros", "Is", "Bjor", "Vind", "Myr", "Hvit", "Sval", "Grim", "Ask",
		"Tor", "Hal", "Sten", "Fjall", "Run", "Ravn", "Elg", "Sno", "Brim", "Djup", "Hamar", "Lang", "Kvit", "Mork", "Natt", "Rim"};
	private static final String[] SOUTHERN = {"Alm", "Kas", "Tar", "Sur", "Mer", "Zar", "Qad", "Sal", "Or", "Cam", "Bas", "Fal", "Ost", "Dor",
		"Mir", "Ash", "Kar", "Sab", "Ser", "Lev", "Nah", "Tam", "Har", "Zeph", "Ib", "Kal", "Mur", "Sar", "Ten", "Az"};

	/** The root and ending joined, without a doubled letter where they meet (Kal + ar, not Kalaar). */
	private static String join(String root, String end) {
		char last = Character.toLowerCase(root.charAt(root.length() - 1));
		if (last == end.charAt(0)) {
			end = end.substring(1);
		}
		boolean vowelEnd = "aeiouy".indexOf(last) >= 0;
		if (vowelEnd && !end.isEmpty() && "aeiouy".indexOf(end.charAt(0)) >= 0) {
			end = end.substring(1);
		}
		return root + end;
	}

	static Land land(String biome) {
		String id = biome == null ? "" : biome.toLowerCase(Locale.ROOT);
		String path = id.substring(id.indexOf(':') + 1);
		if (id.startsWith("expanse:")) {
			Land land = switch (path) {
				case "amber_steppe" -> Land.STEPPE;
				case "ancient_oakwood", "aspen_parkland", "bluebell_woods", "woodland_glades" -> Land.WOOD;
				case "boreal_muskeg" -> Land.MARSH;
				case "cloud_forest" -> Land.CLOUD_FOREST;
				case "coral_coast", "palm_coast" -> Land.COAST;
				case "dolomite_peaks", "verdant_peaks" -> Land.MOUNTAINS;
				case "fjordlands" -> Land.FJORD;
				case "ghost_gum_outback" -> Land.OUTBACK;
				case "heather_moor", "pine_heath" -> Land.MOOR;
				case "jade_karst" -> Land.KARST;
				case "kapok_rainforest", "monsoon_forest" -> Land.JUNGLE;
				case "larch_taiga", "redwood_giants" -> Land.PINE;
				case "lichen_tundra" -> Land.SNOW;
				case "maple_highlands" -> Land.HILLS;
				case "olive_groves" -> Land.ORCHARD;
				case "painted_canyons" -> Land.BADLANDS;
				case "red_dunes", "saguaro_flats" -> Land.DESERT;
				case "salt_flats" -> Land.SALT;
				case "sulfur_seeps", "volcanic_highlands" -> Land.VOLCANIC;
				case "tepui" -> Land.TEPUI;
				case "willow_bayou" -> Land.BAYOU;
				default -> null;
			};
			if (land != null) {
				return land;
			}
			if (path.startsWith("realm_") || path.contains("cave") || path.contains("grotto") || path.contains("depths")) {
				return Land.CAVES;
			}
		}
		// vanilla's biomes (and anyone else's) by the words in their ids; the order matters (snowy_taiga is a pinewood)
		if (path.contains("deep") && path.contains("ocean")) {
			return Land.DEEP_SEA;
		}
		if (path.contains("ocean") || path.contains("sea")) {
			return Land.SEA;
		}
		if (path.contains("river")) {
			return Land.RIVER;
		}
		if (path.contains("beach") || path.contains("shore") || path.contains("coast")) {
			return Land.COAST;
		}
		if (path.contains("mangrove")) {
			return Land.BAYOU;
		}
		if (path.contains("swamp") || path.contains("marsh") || path.contains("bog")) {
			return Land.MARSH;
		}
		if (path.contains("cave") || path.contains("deep_dark") || path.contains("dripstone")) {
			return Land.CAVES;
		}
		if (path.contains("jungle") || path.contains("bamboo")) {
			return Land.JUNGLE;
		}
		if (path.contains("taiga") || path.equals("grove")) {
			return Land.PINE;
		}
		if (path.contains("peaks") || path.contains("slopes")) {
			return Land.MOUNTAINS;
		}
		if (path.contains("snowy") || path.contains("frozen") || path.contains("ice")) {
			return Land.SNOW;
		}
		if (path.contains("badlands")) {
			return Land.BADLANDS;
		}
		if (path.contains("desert")) {
			return Land.DESERT;
		}
		if (path.contains("savanna")) {
			return Land.SAVANNA;
		}
		if (path.contains("windswept") || path.contains("hills")) {
			return Land.HILLS;
		}
		if (path.contains("forest") || path.contains("wood") || path.contains("birch") || path.contains("cherry") || path.contains("grove")) {
			return Land.WOOD;
		}
		if (path.contains("plains") || path.contains("meadow") || path.contains("field")) {
			return Land.FIELDS;
		}
		if (path.contains("mushroom")) {
			return Land.MUSHROOM;
		}
		return Land.OTHER;
	}

	static Tongue tongue(Land land) {
		return switch (land) {
			case SNOW, FJORD -> Tongue.NORSE;
			case DESERT, SALT, BADLANDS, SAVANNA, OUTBACK, STEPPE, JUNGLE, CLOUD_FOREST, KARST, TEPUI, ORCHARD -> Tongue.SOUTHERN;
			default -> Tongue.BOHEMIAN;
		};
	}

	@SafeVarargs
	private static <T> T pick(long h, T... options) {
		return options[(int) Long.remainderUnsigned(h, options.length)];
	}

	/** A well-mixed hash of the seed and a position (SplitMix64's finaliser over a simple combine). */
	static long mix(long seed, int x, int z) {
		long h = seed ^ (x * 0x9E3779B97F4A7C15L) ^ (z * 0xC2B2AE3D27D4EB4FL);
		h = (h ^ h >>> 30) * 0xBF58476D1CE4E5B9L;
		h = (h ^ h >>> 27) * 0x94D049BB133111EBL;
		return h ^ h >>> 31;
	}
}
