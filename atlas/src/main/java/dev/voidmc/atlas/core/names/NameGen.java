package dev.voidmc.atlas.core.names;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Deterministic place names for the map: "Larkspur Meadow", "Greytooth Range", "Lake Ashmere", "The Amber Wastes",
 * "Silverrun", "Ashfall Peak".
 *
 * <p>A name is a pure function of four things: the world's seed hash, the kind of {@link Feature}, the
 * {@link BiomeFamily} it sits in and a caller-chosen {@code id} (typically a hash of the feature's position). The
 * same four inputs give the same name on every machine and in every session, without anything being stored; a
 * different id or a different world gives a different name with high probability. Nothing here uses
 * {@link java.util.Random} or a hash of mutable state: the inputs are mixed with a SplitMix64 finaliser and every
 * choice is drawn from that stream.
 *
 * <p>Names are built from curated word lists so they always read well: a two-part compound such as Lark+spur or
 * Grey+tooth (with awkward joins, tripled letters and doubled sounds rejected and redrawn), followed or preceded by
 * a word that suits the feature and, for regions, the biome. They are at most {@link #MAX_LENGTH} characters, use
 * only letters and spaces, title-case every word and never repeat a word.
 *
 * <p>These are only the defaults: other mods can name their own places through Atlas's region-name providers,
 * which take precedence over this generator.
 */
public final class NameGen {
	/** The kinds of place that can be named. */
	public enum Feature {
		REGION,
		LAKE,
		RIVER,
		PEAK,
		WATERFALL,
		CAVE,
		RAVINE
	}

	/** The longest name this generator returns, in characters. */
	public static final int MAX_LENGTH = 24;

	private static final int MAX_COMPOUND = 14;
	private static final int ATTEMPTS = 256;
	private static final long GAMMA = 0x9E3779B97F4A7C15L;
	private static final Pattern SHAPE = Pattern.compile("^(The )?[A-Z][a-z']+( [A-Z][a-z']+)*$");

	private static final String[] PREFIXES = {
		"Lark", "Grey", "Ash", "Thorn", "Mist", "Raven", "Silver", "Elder", "Fern", "Heather",
		"Wren", "Stone", "Frost", "Amber", "Bramble", "Hollow", "Willow", "Moss", "Cinder", "Dun",
		"Bell", "Rook", "Hazel", "Briar", "Gold", "Cold", "Black", "White", "Red", "Sorrel",
		"Kestrel", "Rush", "Sedge", "Oak", "Rowan", "Yarrow", "Tansy", "Marl", "Flint", "Cobble",
		"Alder", "Apple", "Aspen", "Barley", "Beech", "Birch", "Bittern", "Blythe", "Bracken", "Bright",
		"Brindle", "Broom", "Calder", "Clover", "Corn", "Crane", "Crow", "Daw", "Dove", "Elm",
		"Ember", "Fallow", "Fair", "Falcon", "Finch", "Fox", "Gorse", "Hare", "Hart", "Hawk",
		"Hay", "Heron", "Holly", "Iron", "Ivy", "Juniper", "Kite", "Linden", "Long", "Mallow",
		"Maple", "Merrow", "Nettle", "Night", "Oat", "Osprey", "Otter", "Owl", "Pebble", "Pike",
		"Plover", "Poppy", "Rain", "Reed", "Rime", "Rose", "Saffron", "Sage", "Salt", "Shale",
		"Sloe", "Snipe", "Sparrow", "Starling", "Still", "Storm", "Sun", "Swallow", "Swan", "Teal",
		"Thistle", "Thrush", "Tern", "Umber", "Wheat", "Whin", "Wind", "Winter", "Wolf", "Yew",
		"Dusk", "Dawn", "Copper", "Slate", "Chalk", "Moor", "Cress", "Hind", "Pine", "Rye",
		"Cloud", "Wych", "Lin", "Brook", "Glen", "Elk", "Badger", "Fennel", "Gull", "Lapwing"
	};

	private static final String[] SUFFIXES = {
		"spur", "tooth", "mere", "wood", "fell", "holm", "wick", "combe", "ridge", "dale",
		"burn", "run", "brook", "field", "moor", "shaw", "crest", "stead", "ford", "howe",
		"beck", "garth", "ley", "thwaite", "ness", "hope", "scar", "tor", "wold", "worth",
		"well", "bridge", "hurst", "hythe", "by", "thorpe", "ham", "ton", "stone", "bank",
		"gate", "hill", "fold", "haven", "mouth", "mead", "side", "cliff", "croft", "low",
		"stow", "den", "bourne", "knoll", "cote", "watch", "rest", "fall", "barrow", "march",
		"glen", "rise", "mark", "keld", "wyn", "hold", "more", "thorn", "dell", "mire",
		"hallow", "lyn", "wake", "vale", "shade", "hollow", "bury", "grove", "gill", "holt",
		"lea", "crag", "fen"
	};

	/** Suffixes that make a compound read as running water on its own ("Silverrun", "The Ashbrook"). */
	private static final String[] WATER_SUFFIXES = {"run", "burn", "brook", "beck", "bourne"};

	private static final String[] ADJECTIVES = {
		"Amber", "Quiet", "Silent", "Grey", "Pale", "Sunken", "Old", "Wild", "Long", "High",
		"Low", "Golden", "Hollow", "Bleak", "Far", "Gentle", "Shrouded", "Whispering", "Broken", "Sundered",
		"Northern", "Southern", "Eastern", "Western", "Misty", "Lonely", "Hidden", "Endless", "Forgotten", "Fallow",
		"Ashen", "Verdant", "Emerald", "Iron", "Copper", "Silver", "Shattered", "Hallowed", "Drowsy", "Restless",
		"Lost", "Still", "Weeping", "Windswept", "Russet", "Tangled", "Distant", "Ancient", "Fading", "Dim",
		"Deep", "Cold", "Bitter", "Sleeping", "Scarlet"
	};

	private static final Map<BiomeFamily, String[]> REGION_WORDS = new EnumMap<>(BiomeFamily.class);

	static {
		REGION_WORDS.put(BiomeFamily.PLAINS, new String[]{"Meadow", "Fields", "Downs", "Lea", "Plain"});
		REGION_WORDS.put(BiomeFamily.MEADOW, new String[]{"Meadow", "Lea"});
		REGION_WORDS.put(BiomeFamily.FOREST, new String[]{"Wood", "Forest", "Weald", "Holt"});
		REGION_WORDS.put(BiomeFamily.BIRCH_FOREST, new String[]{"Wood", "Birchwood", "Weald"});
		REGION_WORDS.put(BiomeFamily.DARK_FOREST, new String[]{"Darkwood", "Forest", "Thicket"});
		REGION_WORDS.put(BiomeFamily.PALE, new String[]{"Pale", "Hollows"});
		REGION_WORDS.put(BiomeFamily.TAIGA, new String[]{"Pinewood", "Forest", "Weald"});
		REGION_WORDS.put(BiomeFamily.SNOWY, new String[]{"Tundra", "Snowfield", "Wastes", "Reach"});
		REGION_WORDS.put(BiomeFamily.MOUNTAIN, new String[]{"Hills", "Heights", "Fells", "Highlands"});
		REGION_WORDS.put(BiomeFamily.PEAKS, new String[]{"Range", "Peaks", "Spires", "Crags"});
		REGION_WORDS.put(BiomeFamily.DESERT, new String[]{"Wastes", "Dunes", "Sands", "Expanse"});
		REGION_WORDS.put(BiomeFamily.BADLANDS, new String[]{"Badlands", "Mesas", "Canyons", "Bluffs"});
		REGION_WORDS.put(BiomeFamily.SAVANNA, new String[]{"Steppe", "Plains", "Savanna", "Reach"});
		REGION_WORDS.put(BiomeFamily.JUNGLE, new String[]{"Jungle", "Wilds", "Tangle"});
		REGION_WORDS.put(BiomeFamily.SWAMP, new String[]{"Marsh", "Fen", "Mire", "Bog"});
		REGION_WORDS.put(BiomeFamily.MANGROVE, new String[]{"Mangroves", "Fen"});
		REGION_WORDS.put(BiomeFamily.MUSHROOM, new String[]{"Isles", "Fields"});
		REGION_WORDS.put(BiomeFamily.CHERRY, new String[]{"Grove", "Vale"});
		REGION_WORDS.put(BiomeFamily.CAVE, new String[]{"Deeps", "Hollows"});
		REGION_WORDS.put(BiomeFamily.NETHER, new String[]{"Wastes", "Reach", "Barrens"});
		REGION_WORDS.put(BiomeFamily.END, new String[]{"Barrens", "Isles"});
		REGION_WORDS.put(BiomeFamily.OCEAN, new String[]{"Sea", "Deep", "Waters"});
		REGION_WORDS.put(BiomeFamily.RIVER, new String[]{"Shore", "Strand", "Coast"});
		REGION_WORDS.put(BiomeFamily.BEACH, new String[]{"Shore", "Strand", "Coast"});
		REGION_WORDS.put(BiomeFamily.OTHER, new String[]{"Lands", "Reach", "Vale"});
	}

	private static final String[] LAKE_WORDS = {"Mere", "Water", "Tarn"};
	private static final String[] PEAK_WORDS = {"Peak", "Crag", "Tor"};
	private static final String[] WATERFALL_WORDS = {"Falls", "Force", "Cascade"};
	private static final String[] CAVE_WORDS = {"Cave", "Hollow", "Grotto"};
	private static final String[] RAVINE_WORDS = {"Gorge", "Ravine", "Chasm"};

	/** Unfortunate letter runs a compound must never contain, whatever the join. */
	private static final String[] BLOCKED = {
		"dung", "cum", "piss", "shit", "fag", "rape", "nazi", "anal", "cock", "slut", "wank", "arse", "porn",
		"fuk", "fuck", "nig", "twat", "cunt", "tit", "bum", "poo", "sex", "jiz", "hollywood", "flintstone", "brooklyn"
	};

	private NameGen() {
	}

	/**
	 * The name of one place. Deterministic: the same arguments always give the same name.
	 *
	 * @param worldSeedHash a hash identifying the world, for example from {@link #seedHash(String, long)}
	 * @param feature the kind of place
	 * @param biome the biome family the place sits in; {@code null} is treated as {@link BiomeFamily#OTHER}
	 * @param id the place's own id within the world, typically a hash of its position
	 * @return a title-cased name of at most {@link #MAX_LENGTH} characters, never empty
	 */
	public static String name(long worldSeedHash, Feature feature, BiomeFamily biome, long id) {
		Objects.requireNonNull(feature, "feature");
		BiomeFamily family = biome == null ? BiomeFamily.OTHER : biome;
		long h = mix(worldSeedHash ^ 0x6A09E667F3BCC909L);
		h = mix(h ^ (feature.ordinal() + 1L) * GAMMA);
		h = mix(h ^ (family.ordinal() + 1L) * 0xC2B2AE3D27D4EB4FL);
		h = mix(h ^ mix(id + 0x632BE59BD9B4E019L));
		Bits bits = new Bits(h);
		for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
			String name = compose(bits, feature, family);
			if (name != null) {
				return name;
			}
		}
		return fallback(feature);
	}

	/**
	 * A stable 64-bit mix of a string key and a seed, for callers that only have, say, a server address and the
	 * world seed. The same key and seed always give the same hash.
	 *
	 * @param worldKey any string identifying the world; {@code null} is treated as empty
	 * @param seed the world seed, or any other number that tells worlds with the same key apart
	 * @return the hash to pass to {@link #name(long, Feature, BiomeFamily, long)}
	 */
	public static long seedHash(String worldKey, long seed) {
		String key = worldKey == null ? "" : worldKey;
		long h = 0xCBF29CE484222325L;
		for (int i = 0; i < key.length(); i++) {
			h = (h ^ key.charAt(i)) * 0x100000001B3L;
		}
		h = mix(h ^ key.length());
		return mix(h ^ mix(seed + GAMMA));
	}

	private static String compose(Bits bits, Feature feature, BiomeFamily family) {
		switch (feature) {
			case REGION -> {
				String word = pick(bits, REGION_WORDS.get(family));
				if (bits.nextInt(6) == 0) {
					String adjective = pick(bits, ADJECTIVES);
					return accept("The " + adjective + " " + word, adjective, word);
				}
				String[] compound = compound(bits, SUFFIXES);
				return compound == null ? null : accept(compound[0] + " " + word, compound[1], compound[2], word);
			}
			case LAKE -> {
				String[] compound = compound(bits, SUFFIXES);
				if (compound == null) {
					return null;
				}
				int form = bits.nextInt(4);
				if (form == 0) {
					return accept("Lake " + compound[0], "Lake", compound[1], compound[2]);
				}
				String word = LAKE_WORDS[form - 1];
				return accept(compound[0] + " " + word, compound[1], compound[2], word);
			}
			case RIVER -> {
				int form = bits.nextInt(4);
				String[] compound = compound(bits, form < 2 ? SUFFIXES : WATER_SUFFIXES);
				if (compound == null) {
					return null;
				}
				return switch (form) {
					case 0, 1 -> accept(compound[0] + " River", compound[1], compound[2], "River");
					case 2 -> accept("The " + compound[0], compound[1], compound[2]);
					default -> accept(compound[0], compound[1], compound[2]);
				};
			}
			case PEAK -> {
				String[] compound = compound(bits, SUFFIXES);
				if (compound == null) {
					return null;
				}
				int form = bits.nextInt(4);
				if (form == 0) {
					return accept("Mount " + compound[0], "Mount", compound[1], compound[2]);
				}
				String word = PEAK_WORDS[form - 1];
				return accept(compound[0] + " " + word, compound[1], compound[2], word);
			}
			case WATERFALL -> {
				return suffixed(bits, WATERFALL_WORDS);
			}
			case CAVE -> {
				return suffixed(bits, CAVE_WORDS);
			}
			case RAVINE -> {
				return suffixed(bits, RAVINE_WORDS);
			}
			default -> throw new IllegalStateException("unknown feature " + feature);
		}
	}

	private static String suffixed(Bits bits, String[] words) {
		String[] compound = compound(bits, SUFFIXES);
		if (compound == null) {
			return null;
		}
		String word = pick(bits, words);
		return accept(compound[0] + " " + word, compound[1], compound[2], word);
	}

	/** A compound such as "Larkspur", as {word, prefix, suffix}, or {@code null} if this draw joins badly. */
	private static String[] compound(Bits bits, String[] suffixes) {
		String prefix = pick(bits, PREFIXES);
		String suffix = pick(bits, suffixes);
		String p = prefix.toLowerCase(Locale.ROOT);
		if (!joinsWell(p, suffix)) {
			return null;
		}
		return new String[]{prefix + suffix, prefix, suffix};
	}

	private static boolean joinsWell(String prefix, String suffix) {
		String word = prefix + suffix;
		if (word.length() > MAX_COMPOUND) {
			return false;
		}
		// one part echoing the other: "Barleyley", "Stoneton", "Rookbrook"
		if (prefix.contains(suffix) || suffix.contains(prefix)) {
			return false;
		}
		char last = prefix.charAt(prefix.length() - 1);
		char first = suffix.charAt(0);
		// a doubled letter across the join reads badly ("Ashholm", "Golddale"), except r ("Silverrun")
		if (last == first && last != 'r') {
			return false;
		}
		// hissing into hissing: "Ashshaw", "Birchside", "Cresssedge"
		if (endsSibilant(prefix) && (first == 's' || suffix.startsWith("ch") || first == 'z')) {
			return false;
		}
		// two digraphs back to back: "Birchthwaite", "Ashthorpe"
		if ((prefix.endsWith("sh") || prefix.endsWith("ch") || prefix.endsWith("th") || prefix.endsWith("gh"))
			&& (suffix.startsWith("sh") || suffix.startsWith("ch") || suffix.startsWith("th") || suffix.startsWith("wh"))) {
			return false;
		}
		if (longestConsonantRun(word) >= 5 || hasTripledLetter(word)) {
			return false;
		}
		for (String blocked : BLOCKED) {
			if (word.contains(blocked)) {
				return false;
			}
		}
		return true;
	}

	private static boolean endsSibilant(String prefix) {
		return prefix.endsWith("s") || prefix.endsWith("sh") || prefix.endsWith("ch") || prefix.endsWith("x")
			|| prefix.endsWith("z") || prefix.endsWith("ss");
	}

	private static int longestConsonantRun(String word) {
		int longest = 0;
		int run = 0;
		for (int i = 0; i < word.length(); i++) {
			char c = word.charAt(i);
			if ("aeiouy".indexOf(c) >= 0) {
				run = 0;
			} else {
				run++;
				longest = Math.max(longest, run);
			}
		}
		return longest;
	}

	private static boolean hasTripledLetter(String text) {
		String lower = text.toLowerCase(Locale.ROOT);
		for (int i = 2; i < lower.length(); i++) {
			char c = lower.charAt(i);
			if (Character.isLetter(c) && c == lower.charAt(i - 1) && c == lower.charAt(i - 2)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * The name if it passes every rule, else {@code null}. The parts are the words and word pieces it was built from;
	 * no part may echo another ("Ashfell Fells", "The Wild Wilds", "Lake Ashmere" is fine).
	 */
	private static String accept(String name, String... parts) {
		if (name.isEmpty() || name.length() > MAX_LENGTH || hasTripledLetter(name) || !SHAPE.matcher(name).matches()) {
			return null;
		}
		String[] stems = new String[parts.length];
		for (int i = 0; i < parts.length; i++) {
			stems[i] = stem(parts[i]);
		}
		for (int i = 0; i < stems.length; i++) {
			for (int j = i + 1; j < stems.length; j++) {
				if (stems[i].contains(stems[j]) || stems[j].contains(stems[i])) {
					return null;
				}
			}
		}
		// and no whole word may hide another, even across a compound's join
		String[] words = name.toLowerCase(Locale.ROOT).split(" ");
		for (int i = 0; i < words.length; i++) {
			for (int j = 0; j < words.length; j++) {
				if (i != j && !words[j].equals("the") && stem(words[i]).contains(stem(words[j]))) {
					return null;
				}
			}
		}
		return name;
	}

	/** Lower case, without a plural s: "Fells" and "fell" share the stem "fell". */
	private static String stem(String word) {
		String lower = word.toLowerCase(Locale.ROOT);
		if (lower.length() > 3 && lower.endsWith("s") && !lower.endsWith("ss")) {
			return lower.substring(0, lower.length() - 1);
		}
		return lower;
	}

	/** A name that always passes, for the vanishingly unlikely case that every draw was rejected. */
	private static String fallback(Feature feature) {
		return switch (feature) {
			case REGION -> "Ashby Lands";
			case LAKE -> "Lake Ashby";
			case RIVER -> "Ashby River";
			case PEAK -> "Ashby Peak";
			case WATERFALL -> "Ashby Falls";
			case CAVE -> "Ashby Cave";
			case RAVINE -> "Ashby Gorge";
		};
	}

	private static String pick(Bits bits, String[] words) {
		return words[bits.nextInt(words.length)];
	}

	/** The SplitMix64 finaliser: a strong bijective mix of 64 bits. */
	private static long mix(long z) {
		z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
		z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
		return z ^ (z >>> 31);
	}

	/** A SplitMix64 stream: every choice a name makes is drawn from here, in a fixed order. */
	private static final class Bits {
		private long state;

		Bits(long seed) {
			this.state = seed;
		}

		long next() {
			this.state += GAMMA;
			return mix(this.state);
		}

		int nextInt(int bound) {
			return (int) ((next() >>> 1) % bound);
		}
	}
}
