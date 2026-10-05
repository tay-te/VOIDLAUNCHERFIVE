package dev.voidmc.atlas.core.waypoints;

import java.util.Locale;

/**
 * Turns the many ways other mods and players name a dimension into the game's own id. The vanilla three have
 * nicknames ({@code nether}, {@code DIM-1}, {@code -1}, {@code the end}); anything else that already looks like an
 * id ({@code namespace:path}) is passed through in lower case.
 */
public final class DimensionIds {
	public static final String OVERWORLD = "minecraft:overworld";
	public static final String NETHER = "minecraft:the_nether";
	public static final String END = "minecraft:the_end";

	private DimensionIds() {
	}

	/**
	 * The canonical id for a dimension name, or null when the name is blank or not recognised. Numbers are the old
	 * dimension numbers: 0 is the overworld, -1 the nether and 1 the end; other numbers are unknown and give null.
	 */
	public static String normalise(String raw) {
		if (raw == null) {
			return null;
		}
		String s = raw.strip().toLowerCase(Locale.ROOT);
		if (s.isEmpty()) {
			return null;
		}
		if (s.startsWith("dim") && s.length() > 3 && (s.charAt(3) == '-' || Character.isDigit(s.charAt(3)))) {
			s = s.substring(3);
		}
		switch (s) {
			case "0", "overworld", "minecraft:overworld", "the_overworld", "the overworld", "ow", "surface":
				return OVERWORLD;
			case "-1", "nether", "the_nether", "the nether", "minecraft:the_nether", "minecraft:nether", "hell":
				return NETHER;
			case "1", "end", "the_end", "the end", "minecraft:the_end", "minecraft:end":
				return END;
			default:
				break;
		}
		int colon = s.indexOf(':');
		if (colon > 0 && colon < s.length() - 1 && s.indexOf(':', colon + 1) < 0 && s.matches("[a-z0-9_.\\-]+:[a-z0-9_.\\-/]+")) {
			return s;
		}
		return null;
	}

	/** The id to use when a name is not recognised: the name normalised, or the overworld. */
	public static String orOverworld(String raw) {
		String id = normalise(raw);
		return id == null ? OVERWORLD : id;
	}
}
