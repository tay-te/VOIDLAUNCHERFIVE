package dev.voidmc.hearth.economy.coin;

/**
 * The three coins, smallest first. Amounts everywhere in code are bits; a coin is just a way of carrying them.
 *
 * <p>Each denomination is ten times the one below, so change can always be made by breaking one coin into ten of the
 * next, and the greedy "largest coin that fits" rule gives the fewest coins. A fourth, larger coin was considered and
 * left out: a full stack of crowns is already 6,400 bits, more than a house costs, and the pouch carries hundreds of
 * coins in one slot, so another denomination would only add a name to learn.
 */
public enum Coin {
	/** Struck from copper: small change, a loaf of bread, a handful of cobble. */
	BIT("copper_bit", 1, 1.0F),
	/** Struck from iron: a day's wage, a good tool. */
	MARK("iron_mark", 10, 1.3F),
	/** Struck from gold: a horse, a season's rent, a quest well done. */
	CROWN("gold_crown", 100, 1.7F);

	/** Smallest first; index by {@link #ordinal()}. */
	public static final Coin[] VALUES = values();
	/** Largest first, for making change. */
	public static final Coin[] DESCENDING = {CROWN, MARK, BIT};

	/** The item's registry path under {@code hearth:}. */
	public final String id;
	/** Worth in bits. */
	public final long value;
	/** Pitch of the pickup clink: copper rings low and dull, iron bright, gold high and sweet. */
	public final float pitch;

	Coin(String id, long value, float pitch) {
		this.id = id;
		this.value = value;
		this.pitch = pitch;
	}

	/** {@code hearth.economy.coin.<name>.one|many}: "1 crown" / "3 crowns" in the formatted amount. */
	public String amountKey(long count) {
		return "hearth.economy.coin." + name().toLowerCase(java.util.Locale.ROOT) + (count == 1 ? ".one" : ".many");
	}
}
