package dev.voidmc.hearth.law.pocket;

import dev.voidmc.hearth.api.AgeStage;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.api.Station;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.nbt.CompoundTag;

/**
 * What a resident carries (docs/LIVING_WORLD.md §4): a purse that follows their station, the house key if they keep
 * the household's, and the small things of their trade. Deterministic per resident and pocket period, so the same
 * pockets are there however often a thief looks; what has been lifted stays gone until the pockets fill again
 * ({@code Resident.extra("law")}: {@code pocket_period}, {@code taken}). Pure apart from that record.
 */
public final class Pockets {
	public static final String KEY = "law";

	private Pockets() {
	}

	/**
	 * One thing in a pocket.
	 *
	 * @param item   the item's id (for {@link Kind#ITEM})
	 * @param count  how many
	 * @param bits   coin, in bits (for {@link Kind#COIN})
	 * @param weight how long it takes to lift, 1 (a coin) to 8 (a tool)
	 */
	public record PocketItem(Kind kind, String item, int count, long bits, int weight) {
		public static PocketItem coin(long bits) {
			return new PocketItem(Kind.COIN, "", 1, bits, 1);
		}

		public static PocketItem key() {
			return new PocketItem(Kind.KEY, "", 1, 0, 1);
		}

		public static PocketItem item(String id, int count, int weight) {
			return new PocketItem(Kind.ITEM, id, count, 0, weight);
		}
	}

	public enum Kind {
		COIN, KEY, ITEM
	}

	/**
	 * The resident's pockets for a period, before anything was lifted.
	 *
	 * @param keyHolder whether this resident keeps the household's (or the cells') key
	 */
	public static List<PocketItem> of(Resident resident, long period, boolean keyHolder, boolean withCoin) {
		Random random = new Random(resident.id.getMostSignificantBits() ^ resident.id.getLeastSignificantBits() * 31 ^ period * 0x9E3779B97F4A7C15L);
		List<PocketItem> out = new ArrayList<>();
		if (withCoin) {
			long[] purse = purse(resident.age == AgeStage.CHILD ? Station.POOR : resident.station);
			long bits = purse[0] + (purse[1] > purse[0] ? random.nextLong(purse[1] - purse[0] + 1) : 0);
			if (resident.age == AgeStage.CHILD) {
				bits = Math.max(1, bits / 4);
			}
			out.add(PocketItem.coin(bits));
		}
		if (keyHolder) {
			out.add(PocketItem.key());
		}
		if (resident.age == AgeStage.CHILD) {
			String[] trinkets = {"minecraft:flint", "minecraft:stick", "minecraft:feather", "minecraft:sweet_berries", "minecraft:string"};
			out.add(PocketItem.item(trinkets[random.nextInt(trinkets.length)], 1 + random.nextInt(2), 1));
			return out;
		}
		for (String[] trade : trade(resident.job)) {
			if (random.nextFloat() < Float.parseFloat(trade[3])) {
				int min = Integer.parseInt(trade[1]);
				int max = Integer.parseInt(trade[2]);
				out.add(PocketItem.item(trade[0], min + random.nextInt(max - min + 1), weight(trade[0])));
			}
		}
		if (random.nextFloat() < 0.5F) {
			String[] food = {"minecraft:bread", "minecraft:apple", "minecraft:cookie", "minecraft:baked_potato", "minecraft:dried_kelp"};
			out.add(PocketItem.item(food[random.nextInt(food.length)], 1 + random.nextInt(2), 2));
		}
		return out;
	}

	/** Coin carried by station: [least, most] bits. */
	static long[] purse(Station station) {
		return switch (station) {
			case POOR -> new long[]{0, 6};
			case PEASANT -> new long[]{2, 15};
			case CLERGY -> new long[]{2, 12};
			case ARTISAN -> new long[]{6, 30};
			case SCHOLAR -> new long[]{5, 22};
			case OFFICIAL -> new long[]{10, 40};
			case MERCHANT -> new long[]{20, 85};
			case GENTRY -> new long[]{40, 160};
			case LORD -> new long[]{100, 400};
		};
	}

	/** {id, min, max, chance} per trade. */
	private static String[][] trade(JobType job) {
		return switch (job) {
			case SMITH, SMELTER -> new String[][]{{"minecraft:iron_nugget", "2", "8", "0.8"}, {"minecraft:iron_ingot", "1", "2", "0.3"}};
			case FARMER -> new String[][]{{"minecraft:wheat_seeds", "3", "10", "0.8"}, {"minecraft:carrot", "1", "4", "0.4"}};
			case BAKER -> new String[][]{{"minecraft:bread", "1", "3", "0.9"}, {"minecraft:sugar", "1", "3", "0.3"}};
			case BUTCHER -> new String[][]{{"minecraft:porkchop", "1", "2", "0.7"}};
			case MERCHANT, TRADER -> new String[][]{{"minecraft:emerald", "1", "3", "0.6"}, {"minecraft:gold_nugget", "2", "6", "0.5"}};
			case MINTER -> new String[][]{{"minecraft:gold_nugget", "3", "9", "0.8"}, {"minecraft:copper_ingot", "1", "2", "0.4"}};
			case GUARD -> new String[][]{{"minecraft:arrow", "2", "6", "0.4"}, {"minecraft:bread", "1", "1", "0.4"}};
			case PRIEST -> new String[][]{{"minecraft:candle", "1", "2", "0.7"}, {"minecraft:paper", "1", "2", "0.3"}};
			case SCHOLAR, PAPERMAKER -> new String[][]{{"minecraft:paper", "1", "4", "0.8"}, {"minecraft:feather", "1", "2", "0.4"}};
			case MINER, QUARRIER -> new String[][]{{"minecraft:coal", "1", "4", "0.7"}, {"minecraft:raw_iron", "1", "2", "0.3"}};
			case FISHER -> new String[][]{{"minecraft:cod", "1", "2", "0.7"}, {"minecraft:string", "1", "3", "0.4"}};
			case HUNTER -> new String[][]{{"minecraft:rabbit_hide", "1", "2", "0.5"}, {"minecraft:arrow", "2", "5", "0.6"}};
			case TAILOR, DYER -> new String[][]{{"minecraft:string", "2", "6", "0.8"}, {"minecraft:white_dye", "1", "2", "0.3"}};
			case BREWER -> new String[][]{{"minecraft:glass_bottle", "1", "2", "0.6"}, {"minecraft:sugar", "1", "2", "0.4"}};
			case HEALER -> new String[][]{{"minecraft:glistering_melon_slice", "1", "1", "0.4"}, {"minecraft:sweet_berries", "2", "5", "0.6"}};
			case CHANDLER, BEEKEEPER -> new String[][]{{"minecraft:candle", "1", "3", "0.8"}, {"minecraft:honeycomb", "1", "2", "0.4"}};
			case FORESTER, CARPENTER, BOATWRIGHT -> new String[][]{{"minecraft:stick", "2", "6", "0.7"}, {"minecraft:oak_sapling", "1", "2", "0.3"}};
			case MASON, KILN_WORKER, GLASSMAKER -> new String[][]{{"minecraft:brick", "1", "3", "0.6"}, {"minecraft:flint", "1", "2", "0.3"}};
			case HERDER, TANNER -> new String[][]{{"minecraft:leather", "1", "2", "0.5"}, {"minecraft:wheat", "1", "3", "0.5"}};
			case SALTER -> new String[][]{{"minecraft:sugar", "1", "3", "0.5"}};
			case INNKEEPER -> new String[][]{{"minecraft:bread", "1", "2", "0.5"}, {"minecraft:glass_bottle", "1", "1", "0.4"}};
			case FENCE -> new String[][]{{"minecraft:gold_nugget", "2", "8", "0.7"}, {"minecraft:amethyst_shard", "1", "2", "0.4"}};
			case LORD, STEWARD -> new String[][]{{"minecraft:gold_ingot", "1", "2", "0.6"}, {"minecraft:emerald", "1", "2", "0.4"}};
			default -> new String[][]{{"minecraft:stick", "1", "2", "0.3"}};
		};
	}

	private static int weight(String id) {
		if (id.endsWith("_nugget") || id.endsWith("seeds") || id.contains("paper") || id.contains("string") || id.contains("feather")) {
			return 1;
		}
		if (id.endsWith("_ingot") || id.contains("emerald") || id.contains("raw_")) {
			return 3;
		}
		return 2;
	}

	/** The pocket period a day falls in (pockets fill again each period). */
	public static long period(long day, int refreshDays) {
		return refreshDays <= 0 ? day : Math.floorDiv(day, refreshDays);
	}

	/** Indices of what has already been lifted from this resident this period. */
	public static List<Integer> taken(Resident resident, long period) {
		CompoundTag tag = resident.extra.getCompoundOrEmpty(KEY);
		if (tag.getLongOr("pocket_period", Long.MIN_VALUE) != period) {
			return List.of();
		}
		int[] taken = tag.getIntArray("taken").orElse(new int[0]);
		List<Integer> out = new ArrayList<>(taken.length);
		for (int t : taken) {
			out.add(t);
		}
		return out;
	}

	/** Records that slot {@code index} was lifted this period. */
	public static void markTaken(Resident resident, long period, int index) {
		CompoundTag tag = resident.extra(KEY);
		List<Integer> taken = new ArrayList<>(taken(resident, period));
		if (!taken.contains(index)) {
			taken.add(index);
		}
		tag.putLong("pocket_period", period);
		tag.putIntArray("taken", taken.stream().mapToInt(Integer::intValue).toArray());
	}
}
