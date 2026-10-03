package dev.voidmc.hearth.api;

/** A settlement's stores, in {@link Goods} units. Server thread only. */
public interface Stockpile {
	int get(Goods goods);

	/** Adds up to capacity; returns how much was added. */
	int add(Goods goods, int amount);

	/** Takes up to {@code amount}; returns how much was taken. */
	int take(Goods goods, int amount);

	/** How much of it the settlement can store (grows with storage buildings). */
	int capacity(Goods goods);
}
