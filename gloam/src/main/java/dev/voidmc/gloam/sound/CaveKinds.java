package dev.voidmc.gloam.sound;

import java.util.Locale;

/**
 * Stable small ids for what {@code ExpanseUnderground} names with strings: the realm a point is in and the kind of
 * cavern water nearest it. The soundscape keys its pitches on them, and the bridge sends the same ids to the VOID
 * Welkin pack ({@code void_underground.z} is the realm, {@code .w} the water kind, and the U tile's high nibble the
 * water kind), so they must never be renumbered: new realms go on the end.
 */
public final class CaveKinds {
	public static final int REALM_NONE = 0, WILDS = 1, LUSH = 2, DRIPSTONE = 3, CRYSTAL = 4, EMBER = 5, MERE = 6, FROZEN = 7, ROOTS = 8,
		FOSSIL = 9, TIDAL = 10, AMBER = 11, BRIMSTONE = 12, SEA_REALM = 13;
	public static final int WATER_NONE = 0, RIVER = 1, SEA = 2, LAKE = 3, LAVA = 4;

	private static final String[] REALMS = {"none", "wilds", "lush", "dripstone", "crystal", "ember", "mere", "frozen", "roots", "fossil",
		"tidal", "amber", "brimstone", "sea"};
	private static final String[] WATERS = {"none", "river", "sea", "lake", "lava"};

	private CaveKinds() {
	}

	/** The id of an Expanse realm name ({@code wilds} .. {@code brimstone}, {@code sea}); unknown names are none. */
	public static int realm(String name) {
		return index(REALMS, name);
	}

	/** The id of an Expanse water kind ({@code river}, {@code sea}, {@code lake}, {@code lava}); unknown is none. */
	public static int water(String kind) {
		return index(WATERS, kind);
	}

	public static String realmName(int id) {
		return id >= 0 && id < REALMS.length ? REALMS[id] : "none";
	}

	public static String waterName(int id) {
		return id >= 0 && id < WATERS.length ? WATERS[id] : "none";
	}

	private static int index(String[] names, String name) {
		if (name == null) {
			return 0;
		}
		String key = name.toLowerCase(Locale.ROOT);
		for (int i = 0; i < names.length; i++) {
			if (names[i].equals(key)) {
				return i;
			}
		}
		return 0;
	}
}
