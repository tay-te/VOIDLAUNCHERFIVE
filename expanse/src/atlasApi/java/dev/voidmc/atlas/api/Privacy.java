package dev.voidmc.atlas.api;

import java.util.Locale;

/**
 * Who a player shares their position and explored terrain with, and whose they accept: the same scope works both
 * ways, so turning sharing off also stops seeing others. Checked by {@code core.social.Roster#mayShare}.
 */
public enum Privacy {
	/** Friends, and anyone in the party. */
	FRIENDS,
	/** Only players in the same party. */
	PARTY,
	/** No one. */
	NOBODY;

	public static final Privacy DEFAULT = PARTY;

	/** The lower-case name used on the wire: {@code friends}, {@code party}, {@code nobody}. */
	public String id() {
		return name().toLowerCase(Locale.ROOT);
	}

	/** Reads {@link #id()} (any case); anything else is {@link #DEFAULT}. */
	public static Privacy parse(String text) {
		if (text != null) {
			for (Privacy p : values()) {
				if (p.id().equalsIgnoreCase(text.strip())) {
					return p;
				}
			}
		}
		return DEFAULT;
	}
}
