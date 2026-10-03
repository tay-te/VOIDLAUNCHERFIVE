package dev.voidmc.hearth.economy.bank;

import com.mojang.serialization.Codec;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;

/**
 * The ledger of coin players keep on deposit: one per world, so a deposit made at one settlement's bank is honoured at
 * every other's. Plain data; {@link DepositStore} keeps it with the world.
 */
public final class Deposits {
	public static final Codec<Deposits> CODEC = Codec.unboundedMap(UUIDUtil.STRING_CODEC, Codec.LONG).xmap(Deposits::new, d -> Map.copyOf(d.balances));

	final Map<UUID, Long> balances;

	public Deposits() {
		this(Map.of());
	}

	Deposits(Map<UUID, Long> balances) {
		this.balances = new HashMap<>(balances);
	}

	public long balance(UUID player) {
		return this.balances.getOrDefault(player, 0L);
	}

	/** Adds (or with a negative delta takes) bits: false, and no change, if it would go below zero or overflow. */
	public boolean add(UUID player, long delta) {
		long next;
		try {
			next = Math.addExact(balance(player), delta);
		} catch (ArithmeticException e) {
			return false;
		}
		if (next < 0) {
			return false;
		}
		if (next == 0) {
			this.balances.remove(player);
		} else {
			this.balances.put(player, next);
		}
		return true;
	}

	public Map<UUID, Long> accounts() {
		return Map.copyOf(this.balances);
	}

	Deposits copy() {
		return new Deposits(this.balances);
	}
}
