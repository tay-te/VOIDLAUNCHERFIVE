package dev.voidmc.hearth.economy.coin;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * A model of the coin a player carries: loose coin stacks in inventory slots and coin pouches. Built from a snapshot of
 * the inventory, changed here as plain numbers, then written back by {@link PlayerPurse} — so a charge is decided
 * completely before a single item moves, which is what makes it all-or-nothing.
 *
 * <p>Pure Java (no game classes) so the change-making can be tested exhaustively.
 */
public final class Purse {
	private final List<Slot> slots = new ArrayList<>();
	private final List<Pouch> pouches = new ArrayList<>();
	private final int stackSize;

	public Purse(int stackSize) {
		this.stackSize = stackSize;
	}

	/** An inventory slot that holds coin or is empty (free for coin). Slots holding anything else are left out. */
	public Slot addSlot(int index, @Nullable Coin coin, int count) {
		Slot slot = new Slot(index, coin, coin == null ? 0 : count);
		this.slots.add(slot);
		return slot;
	}

	public Pouch addPouch(int index, long[] counts, int capacity) {
		Pouch pouch = new Pouch(index, counts.clone(), capacity);
		this.pouches.add(pouch);
		return pouch;
	}

	public List<Slot> slots() {
		return this.slots;
	}

	public List<Pouch> pouches() {
		return this.pouches;
	}

	/** Coins held of each kind, loose and pouched. */
	public long[] counts() {
		long[] counts = new long[Coin.VALUES.length];
		for (Slot s : this.slots) {
			if (s.coin != null) {
				counts[s.coin.ordinal()] += s.count;
			}
		}
		for (Pouch p : this.pouches) {
			for (Coin c : Coin.VALUES) {
				counts[c.ordinal()] += p.counts[c.ordinal()];
			}
		}
		return counts;
	}

	public long balance() {
		return Change.value(counts());
	}

	/**
	 * Takes {@code bits}, making change. Returns null (and changes nothing) if the purse is not worth enough; otherwise the
	 * coins that did not fit back in as change, to be dropped at the player's feet (usually all zero).
	 */
	public long @Nullable [] charge(long bits) {
		Change.Payment payment = Change.plan(counts(), bits);
		if (payment == null) {
			return null;
		}
		for (Coin coin : Coin.VALUES) {
			take(coin, payment.give()[coin.ordinal()]);
		}
		return add(payment.change());
	}

	/** Puts {@code bits} in, in the fewest coins; returns what did not fit (by coin). */
	public long[] add(long bits) {
		return addCoins(Change.fewest(bits));
	}

	/**
	 * Puts these coins in: pouches first (that is what a pouch is for), then onto stacks of the same coin, then into empty
	 * slots. Returns what did not fit.
	 */
	public long[] addCoins(long[] coins) {
		long[] rest = coins.clone();
		for (Coin coin : Coin.DESCENDING) {
			int i = coin.ordinal();
			for (Pouch p : this.pouches) {
				if (rest[i] == 0) {
					break;
				}
				long put = Math.min(rest[i], p.space());
				p.counts[i] += put;
				rest[i] -= put;
			}
			for (Slot s : this.slots) {
				if (rest[i] == 0) {
					break;
				}
				if (s.coin == coin && s.count < this.stackSize) {
					long put = Math.min(rest[i], this.stackSize - s.count);
					s.count += (int) put;
					rest[i] -= put;
				}
			}
			for (Slot s : this.slots) {
				if (rest[i] == 0) {
					break;
				}
				if (s.coin == null) {
					long put = Math.min(rest[i], this.stackSize);
					s.coin = coin;
					s.count = (int) put;
					rest[i] -= put;
				}
			}
		}
		return rest;
	}

	/** Removes {@code n} coins of a kind: the smallest loose stacks first (tidying the inventory), then pouches. */
	private void take(Coin coin, long n) {
		long rest = n;
		List<Slot> stacks = new ArrayList<>();
		for (Slot s : this.slots) {
			if (s.coin == coin) {
				stacks.add(s);
			}
		}
		stacks.sort(Comparator.comparingInt(s -> s.count));
		for (Slot s : stacks) {
			if (rest == 0) {
				return;
			}
			long t = Math.min(rest, s.count);
			s.count -= (int) t;
			rest -= t;
			if (s.count == 0) {
				s.coin = null;
			}
		}
		for (Pouch p : this.pouches) {
			if (rest == 0) {
				return;
			}
			long t = Math.min(rest, p.counts[coin.ordinal()]);
			p.counts[coin.ordinal()] -= t;
			rest -= t;
		}
		if (rest != 0) {
			throw new IllegalStateException("purse short of " + rest + " " + coin);
		}
	}

	/** An inventory slot: empty ({@code coin == null}) or a stack of one coin. */
	public static final class Slot {
		public final int index;
		private final @Nullable Coin originalCoin;
		private final int originalCount;
		@Nullable Coin coin;
		int count;

		Slot(int index, @Nullable Coin coin, int count) {
			this.index = index;
			this.originalCoin = coin;
			this.originalCount = count;
			this.coin = coin;
			this.count = count;
		}

		public @Nullable Coin coin() {
			return this.coin;
		}

		public int count() {
			return this.count;
		}

		public boolean changed() {
			return this.coin != this.originalCoin || this.count != this.originalCount;
		}
	}

	/** A coin pouch in an inventory slot. */
	public static final class Pouch {
		public final int index;
		private final long[] original;
		final long[] counts;
		final int capacity;

		Pouch(int index, long[] counts, int capacity) {
			this.index = index;
			this.original = counts.clone();
			this.counts = counts;
			this.capacity = capacity;
		}

		public long[] counts() {
			return this.counts.clone();
		}

		public long space() {
			return Math.max(0, this.capacity - Change.coins(this.counts));
		}

		public boolean changed() {
			return !java.util.Arrays.equals(this.original, this.counts);
		}
	}
}
