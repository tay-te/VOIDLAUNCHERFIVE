package dev.voidmc.hearth.sim.model;

import dev.voidmc.hearth.sim.SimConfig;

/**
 * The model's numbers, read from {@link SimConfig} into flat arrays once so the clock touches no maps while it steps.
 */
public final class SimRules {
	public static final long HOUR = 1000;
	public static final long DAY = 24000;

	public final SimConfig config;
	/** {@code [job][goods]} units per worker per day. */
	public final double[][] production;
	/** Bits per worker per day, by job. */
	public final int[] coin;
	/** Bits per unit of goods, and the trade terms (see {@link SimConfig.Production}). */
	public final double[] prices;
	public final double exportShare;
	public final double importMarkup;
	public final int importPerDay;
	public final double foodAdult;
	public final double foodChild;
	public final double foodElder;
	public final SimConfig.Labour labour;
	public final SimConfig.Population population;
	public final SimConfig.Storage storage;
	public final long maxCatchUpTicks;

	public SimRules(SimConfig config) {
		this.config = config;
		this.production = config.production().table();
		this.coin = config.production().coin();
		this.prices = config.production().priceTable();
		this.exportShare = config.production().exportShare();
		this.importMarkup = config.production().importMarkup();
		this.importPerDay = config.production().importPerDay();
		this.foodAdult = config.production().foodAdult();
		this.foodChild = config.production().foodChild();
		this.foodElder = config.production().foodElder();
		this.labour = config.labour();
		this.population = config.population();
		this.storage = config.storage();
		this.maxCatchUpTicks = config.clock().maxCatchUpDays() * DAY;
	}

	public static SimRules defaults() {
		return new SimRules(SimConfig.DEFAULTS);
	}
}
