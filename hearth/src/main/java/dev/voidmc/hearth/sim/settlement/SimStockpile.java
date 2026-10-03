package dev.voidmc.hearth.sim.settlement;

import com.mojang.serialization.Codec;
import dev.voidmc.hearth.api.Goods;
import dev.voidmc.hearth.api.Stockpile;
import java.util.Arrays;
import java.util.stream.IntStream;

/**
 * A settlement's stores. Capacity is the same for every kind of goods and is set by the settlement from its storage
 * buildings ({@link SimSettlement#recomputeDerived}). Changes mark the owner dirty so the economy module can sell to a
 * town without knowing about persistence.
 */
public final class SimStockpile implements Stockpile {
	/** Amounts only; capacity is derived. */
	public static final Codec<SimStockpile> CODEC = Codec.INT_STREAM.xmap(s -> new SimStockpile(s.toArray()), p -> IntStream.of(p.amounts));

	private final int[] amounts = new int[Goods.VALUES.length];
	private int capacity = Integer.MAX_VALUE;
	private Runnable onChange = () -> {
	};

	public SimStockpile() {
	}

	private SimStockpile(int[] values) {
		System.arraycopy(values, 0, this.amounts, 0, Math.min(values.length, this.amounts.length));
	}

	void bind(Runnable onChange) {
		this.onChange = onChange;
	}

	void setCapacity(int capacity) {
		this.capacity = Math.max(0, capacity);
	}

	@Override
	public int get(Goods goods) {
		return this.amounts[goods.ordinal()];
	}

	@Override
	public int add(Goods goods, int amount) {
		int added = Math.max(0, Math.min(amount, this.capacity - this.amounts[goods.ordinal()]));
		if (added > 0) {
			this.amounts[goods.ordinal()] += added;
			this.onChange.run();
		}
		return added;
	}

	@Override
	public int take(Goods goods, int amount) {
		int taken = Math.max(0, Math.min(amount, this.amounts[goods.ordinal()]));
		if (taken > 0) {
			this.amounts[goods.ordinal()] -= taken;
			this.onChange.run();
		}
		return taken;
	}

	@Override
	public int capacity(Goods goods) {
		return this.capacity;
	}

	/** Whether every amount in {@code need} (by ordinal, each clamped to capacity) is in store. */
	public boolean has(int[] need) {
		for (int g = 0; g < need.length && g < this.amounts.length; g++) {
			if (this.amounts[g] < Math.min(need[g], this.capacity)) {
				return false;
			}
		}
		return true;
	}

	/** The goods most short of {@code need}, or -1 if nothing is. */
	public int worstShortfall(int[] need) {
		int worst = -1;
		int missing = 0;
		for (int g = 0; g < need.length && g < this.amounts.length; g++) {
			int m = Math.min(need[g], this.capacity) - this.amounts[g];
			if (m > missing) {
				missing = m;
				worst = g;
			}
		}
		return worst;
	}

	public int shortfall(int[] need, int g) {
		return Math.max(0, Math.min(need[g], this.capacity) - this.amounts[g]);
	}

	/** Takes everything in {@code need} (clamped to capacity: what a store cannot hold, carts bring straight to site). */
	public void takeAll(int[] need) {
		for (int g = 0; g < need.length && g < this.amounts.length; g++) {
			this.amounts[g] -= Math.min(this.amounts[g], Math.min(need[g], this.capacity));
		}
		this.onChange.run();
	}

	/** Adds without the change hook (the clock marks its settlement dirty once per step). */
	int addQuiet(int g, int amount) {
		int added = Math.max(0, Math.min(amount, this.capacity - this.amounts[g]));
		this.amounts[g] += added;
		return added;
	}

	int takeQuiet(int g, int amount) {
		int taken = Math.max(0, Math.min(amount, this.amounts[g]));
		this.amounts[g] -= taken;
		return taken;
	}

	public int[] snapshot() {
		return this.amounts.clone();
	}

	@Override
	public String toString() {
		return Arrays.toString(this.amounts);
	}
}
