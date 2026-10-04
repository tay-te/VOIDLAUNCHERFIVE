package dev.voidmc.hearth.sim.model;

import dev.voidmc.hearth.api.Goods;
import dev.voidmc.hearth.sim.SimConfig;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

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
	/** The resource economy's rules (territory, chains, spoilage, prosperity, caravans, stations, extraction). */
	public final SimConfig.Resources resources;
	/** Store room particular buildings add, by archetype, per goods ordinal. */
	private final Map<Identifier, int[]> storageBy = new HashMap<>();

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
		this.resources = config.resources();
		config.storage().byArchetype().forEach((id, room) -> {
			Identifier key = Identifier.tryParse(id);
			if (key == null) {
				return;
			}
			int[] add = new int[Goods.VALUES.length];
			room.forEach((what, n) -> {
				for (Goods g : group(what)) {
					add[g.ordinal()] += n;
				}
			});
			this.storageBy.put(key, add);
		});
	}

	/** What a building of this archetype adds to the stores, per goods ordinal, or null for the general allowance. */
	public int @Nullable [] storageFor(Identifier archetype) {
		return this.storageBy.get(archetype);
	}

	/** Goods named in the storage config: a goods name, or a group ({@code #all}, {@code #edible}, ...). */
	static List<Goods> group(String what) {
		if (!what.startsWith("#")) {
			for (Goods g : Goods.VALUES) {
				if (g.getSerializedName().equals(what)) {
					return List.of(g);
				}
			}
			return List.of();
		}
		return switch (what) {
			case "#all" -> List.of(Goods.VALUES);
			case "#edible" -> List.of(Goods.FOOD, Goods.GRAIN, Goods.FLOUR, Goods.MEAT, Goods.FISH, Goods.PRODUCE, Goods.SALT);
			case "#building" -> List.of(Goods.WOOD, Goods.LOGS, Goods.STONE, Goods.DRESSED_STONE, Goods.BRICK, Goods.GLASS, Goods.CLAY, Goods.SAND);
			case "#raw" -> java.util.Arrays.stream(Goods.VALUES).filter(g -> g.kind() == Goods.Kind.RAW).toList();
			case "#processed" -> java.util.Arrays.stream(Goods.VALUES).filter(g -> g.kind() == Goods.Kind.PROCESSED).toList();
			case "#finished" -> java.util.Arrays.stream(Goods.VALUES).filter(g -> g.kind() == Goods.Kind.FINISHED).toList();
			default -> List.of();
		};
	}

	public static SimRules defaults() {
		return new SimRules(SimConfig.DEFAULTS);
	}
}
