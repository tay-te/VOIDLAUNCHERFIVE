package dev.voidmc.hearth.economy.coin;

import org.jspecify.annotations.Nullable;

/**
 * Change-making over the three coins, as plain arithmetic on counts indexed by {@link Coin#ordinal()}.
 *
 * <p>Paying works like a person at a stall: hand over exact coin if you can, largest first; if you cannot, add the
 * smallest coin you still have that covers the rest and take change. Because each coin is ten of the one below, the
 * largest-first rule finds an exact payment whenever one exists with the coins held, and the change is given back in
 * the fewest coins. Every route leaves the player with the same value; only the mix of coins differs.
 */
public final class Change {
	private Change() {
	}

	/** The fewest coins worth exactly {@code bits} (non-negative). */
	public static long[] fewest(long bits) {
		if (bits < 0) {
			throw new IllegalArgumentException("negative amount " + bits);
		}
		long[] counts = new long[Coin.VALUES.length];
		long rest = bits;
		for (Coin coin : Coin.DESCENDING) {
			counts[coin.ordinal()] = rest / coin.value;
			rest %= coin.value;
		}
		return counts;
	}

	public static long value(long[] counts) {
		long total = 0;
		for (Coin coin : Coin.VALUES) {
			total = Math.addExact(total, Math.multiplyExact(counts[coin.ordinal()], coin.value));
		}
		return total;
	}

	public static long coins(long[] counts) {
		long n = 0;
		for (long c : counts) {
			n += c;
		}
		return n;
	}

	/**
	 * Which of the {@code held} coins to hand over to pay {@code amount}, and the change due back; null if the coins held
	 * are not worth enough. {@code held} is not changed.
	 */
	public static @Nullable Payment plan(long[] held, long amount) {
		if (amount < 0) {
			throw new IllegalArgumentException("negative amount " + amount);
		}
		if (value(held) < amount) {
			return null;
		}
		long[] give = new long[Coin.VALUES.length];
		long rest = amount;
		for (Coin coin : Coin.DESCENDING) {
			long use = Math.min(held[coin.ordinal()], rest / coin.value);
			give[coin.ordinal()] = use;
			rest -= use * coin.value;
		}
		if (rest == 0) {
			return new Payment(give, 0);
		}
		// No exact payment: every coin small enough to fit is already in hand (or ran out), so the smallest coin left over
		// is bigger than what remains. Add it and take change; the greedy pass guarantees such a coin exists.
		for (Coin coin : Coin.VALUES) {
			if (give[coin.ordinal()] < held[coin.ordinal()]) {
				give[coin.ordinal()]++;
				return new Payment(give, coin.value - rest);
			}
		}
		throw new IllegalStateException("enough coin but no payment found for " + amount);
	}

	/**
	 * @param give   coins to take, by {@link Coin#ordinal()}
	 * @param change bits to hand back (in the fewest coins)
	 */
	public record Payment(long[] give, long change) {
	}
}
