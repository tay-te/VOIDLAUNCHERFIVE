package dev.voidmc.hearth.quest.gen;

import dev.voidmc.hearth.api.Goods;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.quest.model.SettlementQuests.StalledNeed;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * What each workshop works (docs/LIVING_WORLD.md §7.5), as the quest module reads it: a smelter wants ore and charcoal,
 * a chandler wax, a sawyer logs. When a town's stores run low of a workshop's inputs, that workshop posts a standing
 * contract on the board. Read from the residents' trades and the stockpile only, once per board refresh.
 */
public final class Chains {
	private static final Map<JobType, List<Goods>> INPUTS = new EnumMap<>(JobType.class);
	private static final Map<JobType, String> WORKSHOP = new EnumMap<>(JobType.class);

	static {
		chain(JobType.SMELTER, "smelter", Goods.ORE, Goods.FUEL);
		chain(JobType.SMITH, "smithy", Goods.METAL, Goods.FUEL);
		chain(JobType.CHARCOAL_BURNER, "charcoal kiln", Goods.LOGS);
		chain(JobType.CARPENTER, "sawmill", Goods.LOGS);
		chain(JobType.MASON, "mason's yard", Goods.STONE);
		chain(JobType.KILN_WORKER, "brick kiln", Goods.CLAY, Goods.FUEL);
		chain(JobType.GLASSMAKER, "glassworks", Goods.SAND, Goods.FUEL);
		chain(JobType.BAKER, "bakery", Goods.FLOUR, Goods.FUEL);
		chain(JobType.BREWER, "brewhouse", Goods.GRAIN);
		chain(JobType.BUTCHER, "butcher's", Goods.MEAT, Goods.SALT);
		chain(JobType.TANNER, "tannery", Goods.SALT);
		chain(JobType.TAILOR, "tailor's", Goods.WOOL, Goods.DYE);
		chain(JobType.DYER, "dye works", Goods.PRODUCE);
		chain(JobType.CHANDLER, "chandlery", Goods.WAX);
		chain(JobType.PAPERMAKER, "paper mill", Goods.WOOD);
		chain(JobType.SCHOLAR, "scriptorium", Goods.PAPER, Goods.LEATHER, Goods.CANDLES);
		chain(JobType.BOATWRIGHT, "boatyard", Goods.WOOD, Goods.CLOTH);
		chain(JobType.INNKEEPER, "inn", Goods.FOOD, Goods.CANDLES);
		chain(JobType.HEALER, "infirmary", Goods.PRODUCE, Goods.CLOTH);
		chain(JobType.PRIEST, "chapel", Goods.CANDLES);
	}

	private Chains() {
	}

	/** The goods a trade works, or an empty list for trades that gather or serve. */
	public static List<Goods> inputs(JobType job) {
		return INPUTS.getOrDefault(job, List.of());
	}

	/** What the workshop of a trade is called in quest text ("smelter", "chandlery"). */
	public static String workshop(JobType job) {
		return WORKSHOP.getOrDefault(job, job.getSerializedName().replace('_', ' '));
	}

	/** Every trade that works inputs. */
	public static java.util.Set<JobType> workshops() {
		return INPUTS.keySet();
	}

	/**
	 * The workshops of a town short of an input, most pressing first: each worker wants {@code perWorker} units of each of
	 * its inputs on hand; a workshop is short when the stores hold less than half of what its workers want.
	 */
	public static List<StalledNeed> shortages(Settlement s, int perWorker, long day) {
		Map<JobType, Integer> workers = new EnumMap<>(JobType.class);
		Map<JobType, Integer> workPlot = new EnumMap<>(JobType.class);
		for (Resident r : s.residents()) {
			if (INPUTS.containsKey(r.job)) {
				workers.merge(r.job, 1, Integer::sum);
				if (r.work >= 0) {
					workPlot.putIfAbsent(r.job, r.work);
				}
			}
		}
		Map<Goods, Integer> wanted = new EnumMap<>(Goods.class);
		Map<Goods, JobType> by = new EnumMap<>(Goods.class);
		for (Map.Entry<JobType, Integer> e : workers.entrySet()) {
			for (Goods g : INPUTS.get(e.getKey())) {
				int want = perWorker * e.getValue();
				// a good wanted by several workshops is asked for once, by the one that wants most
				if (want > wanted.getOrDefault(g, 0)) {
					wanted.put(g, want);
					by.put(g, e.getKey());
				}
			}
		}
		List<StalledNeed> out = new ArrayList<>();
		List<Double> pressure = new ArrayList<>();
		for (Map.Entry<Goods, Integer> e : wanted.entrySet()) {
			int stock = s.stockpile().get(e.getKey());
			int want = e.getValue();
			if (stock * 2 < want) {
				JobType job = by.get(e.getKey());
				out.add(new StalledNeed(workPlot.getOrDefault(job, -1), e.getKey(), want - stock, day, workshop(job), job.getSerializedName()));
				pressure.add(stock / (double) want);
			}
		}
		List<Integer> order = new ArrayList<>();
		for (int i = 0; i < out.size(); i++) {
			order.add(i);
		}
		order.sort(Comparator.comparingDouble((Integer i) -> pressure.get(i)).thenComparing(i -> out.get(i).goods().ordinal()));
		List<StalledNeed> sorted = new ArrayList<>(out.size());
		for (int i : order) {
			sorted.add(out.get(i));
		}
		return sorted;
	}

	private static void chain(JobType job, String workshop, Goods... inputs) {
		INPUTS.put(job, List.of(inputs));
		WORKSHOP.put(job, workshop);
	}
}
