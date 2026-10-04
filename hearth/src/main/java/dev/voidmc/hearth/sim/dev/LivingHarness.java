package dev.voidmc.hearth.sim.dev;

import dev.voidmc.hearth.api.Blueprint;
import dev.voidmc.hearth.api.Goods;
import dev.voidmc.hearth.api.PlotState;
import dev.voidmc.hearth.api.ResourceSite;
import dev.voidmc.hearth.api.SettlementEvents;
import dev.voidmc.hearth.sim.SimServer;
import dev.voidmc.hearth.sim.land.Lands;
import dev.voidmc.hearth.sim.land.SimSite;
import dev.voidmc.hearth.sim.settlement.SimPlot;
import dev.voidmc.hearth.sim.settlement.SimSettlement;
import dev.voidmc.hearth.sim.world.SimWorld;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The living world end to end on a dedicated server (integration of phase 3): {@code -Dhearth.dev.living=DAYS}, run after
 * the sim harness founded its towns ({@code found=N}). For each town it logs the survey (sites by kind), runs the town a
 * day at a time for {@code DAYS} days and logs what its sites gave (sites worked, raw and processed goods), what its
 * construction consumed of the processed goods (the bills of the stages finished), its prosperity and capacity; counts the
 * caravans that set out, arrived and were lost, the sites spent and found and the turns of fortune; then takes the last
 * town's land from it (every site worked out) and runs it on to show the decline (prosperity, folk leaving, houses left
 * empty), and asks the first town to send settlers to found a daughter hamlet at a resource it lacks. Logged as
 * {@code [hearth-living]}.
 */
final class LivingHarness {
	private static final Logger LOG = LoggerFactory.getLogger("hearth");
	private static final Goods[] PROCESSED = {Goods.WOOD, Goods.DRESSED_STONE, Goods.BRICK, Goods.GLASS, Goods.METAL, Goods.TOOLS, Goods.CANDLES,
		Goods.CLOTH, Goods.FLOUR, Goods.FOOD, Goods.FUEL};
	private static final Goods[] RAW = {Goods.LOGS, Goods.STONE, Goods.ORE, Goods.CLAY, Goods.SAND, Goods.GRAIN, Goods.WOOL, Goods.FISH, Goods.MEAT};

	private static final Map<String, AtomicInteger> EVENTS = new HashMap<>();
	private static boolean listening;

	private LivingHarness() {
	}

	private static void count(String what) {
		EVENTS.computeIfAbsent(what, k -> new AtomicInteger()).incrementAndGet();
	}

	private static void listen() {
		if (listening) {
			return;
		}
		listening = true;
		SettlementEvents.CARAVAN.register((w, from, to, kind, id, cargo) -> count("caravan " + kind.name().toLowerCase(Locale.ROOT)));
		SettlementEvents.SITE_DEPLETED.register((w, s, site) -> count("site depleted"));
		SettlementEvents.SITE_FOUND.register((w, s, site) -> count("site found"));
		SettlementEvents.PROSPERITY_CHANGED.register((w, s, rising, why) -> {
			count(rising ? "fortunes rose" : "fortunes declined");
			LOG.info("[hearth-living] {}: fortunes {} ({})", s.name(), rising ? "rising" : "declining", why);
		});
		SettlementEvents.PLOT_ABANDONED.register((w, s, p, why) -> count("plot abandoned"));
		SettlementEvents.BUILDING_COMPLETED.register((w, s, p) -> count("building completed"));
		SettlementEvents.CONSTRUCTION_STALLED.register((w, s, p, g, n) -> count("stalled for " + g.getSerializedName()));
	}

	static void run(MinecraftServer server, SimWorld w, List<SimSettlement> towns, int days) {
		if (towns.isEmpty()) {
			LOG.info("[hearth-living] no towns");
			return;
		}
		server.submit(LivingHarness::listen).join();
		for (SimSettlement s : towns) {
			LOG.info("[hearth-living] {}", server.submit(() -> survey(s)).join());
		}
		Map<Long, int[]> consumed = new HashMap<>();
		Map<Long, int[]> start = new HashMap<>();
		for (SimSettlement s : towns) {
			start.put(s.id(), server.submit(() -> s.stockpile().amounts().clone()).join());
			consumed.put(s.id(), new int[Goods.VALUES.length]);
		}
		SimServer sim = SimServer.current();
		long stepNanos = 0;
		int steps = 0;
		for (int d = 0; d < days; d++) {
			for (SimSettlement s : towns) {
				long n = server.submit(() -> {
					Map<Integer, Integer> before = stages(s);
					long t = w.simulate(s, 1);
					billOfFinished(w, s, before, consumed.get(s.id()));
					return t;
				}).join();
				stepNanos += n;
				steps++;
			}
		}
		LOG.info("[hearth-living] {} towns run {} days a day at a time: {} ms per settlement-day (incl. planner calls)", towns.size(), days,
			String.format(Locale.ROOT, "%.3f", stepNanos / 1e6 / Math.max(1, steps)));
		for (SimSettlement s : towns) {
			LOG.info("[hearth-living] {}", server.submit(() -> after(s, start.get(s.id()), consumed.get(s.id()), days)).join());
		}
		LOG.info("[hearth-living] caravans on the road now: {}; {}", server.submit(() -> w.caravanTrade().active().size()).join(),
			server.submit(() -> w.caravanTrade().describe()).join());
		LOG.info("[hearth-living] events over the run: {}", events());

		// the land runs out: every site of the last town worked out, and the town run on
		SimSettlement poor = towns.getLast();
		LOG.info("[hearth-living] decline: {} before: {}", poor.name(), server.submit(() -> fortunes(poor)).join());
		server.submit(() -> {
			for (SimSite site : List.copyOf(poor.land().sites())) {
				Lands.take(poor, site, site.remaining() + 1, 0, false, w);
			}
			return null;
		}).join();
		for (int d = 0; d < 40; d += 10) {
			server.submit(() -> w.simulate(poor, 10)).join();
			LOG.info("[hearth-living] decline: {} +{} days: {}", poor.name(), d + 10, server.submit(() -> fortunes(poor)).join());
		}

		// a daughter hamlet at a resource the parent lacks
		SimSettlement parent = towns.getFirst();
		int hamletsBefore = server.submit(() -> hamlets(w).size()).join();
		boolean asked = server.submit(() -> w.sendSettlers(parent)).join();
		LOG.info("[hearth-living] {} asked to send settlers out: {}", parent.name(), asked);
		for (int i = 0; i < 120; i++) {
			if (server.submit(() -> hamlets(w).size()).join() > hamletsBefore) {
				break;
			}
			DevHarness.sleep(1000);
		}
		for (String line : server.submit(() -> hamlets(w)).join()) {
			LOG.info("[hearth-living] {}", line);
		}
		LOG.info("[hearth-living] {}", server.submit(w::daughterStats).join());
		if (sim != null) {
			LOG.info("[hearth-living] sim tick work so far: {}", server.submit(() -> String.format(Locale.ROOT, "%.4f ms avg per server tick over %d ticks",
				sim.stats().tickNanos.sum() / 1e6 / Math.max(1, sim.ticks()), sim.ticks())).join());
		}
	}

	private static String survey(SimSettlement s) {
		Map<ResourceSite.Kind, Integer> kinds = new EnumMap<>(ResourceSite.Kind.class);
		Map<ResourceSite.Kind, Long> units = new EnumMap<>(ResourceSite.Kind.class);
		List<String> materials = new ArrayList<>();
		for (ResourceSite r : s.sites()) {
			kinds.merge(r.kind(), 1, Integer::sum);
			units.merge(r.kind(), (long) r.remaining(), Long::sum);
			if (r.material() != null && materials.size() < 6 && !materials.contains(r.material().toString())) {
				materials.add(r.material().toString());
			}
		}
		return String.format(Locale.ROOT, "%s (%s %s, %d souls): territory %d, %d sites %s, units %s, materials %s, prosperity %.2f, capacity %s",
			s.name(), s.culture().getSerializedName(), s.tier().getSerializedName(), s.population(), s.territoryRadius(), s.sites().size(), kinds, units,
			materials, s.prosperity(), s.carryingCapacity() == Integer.MAX_VALUE ? "-" : s.carryingCapacity());
	}

	private static Map<Integer, Integer> stages(SimSettlement s) {
		Map<Integer, Integer> m = new HashMap<>();
		for (SimPlot p : s.plots()) {
			if (p.state() == PlotState.PLANNED || p.state() == PlotState.BUILDING || p.state() == PlotState.BUILT) {
				m.put(p.id(), p.state() == PlotState.BUILT ? Integer.MAX_VALUE : p.stage());
			}
		}
		return m;
	}

	/** Adds the goods bills of the stages finished since {@code before} (what construction consumed). */
	private static void billOfFinished(SimWorld w, SimSettlement s, Map<Integer, Integer> before, int[] into) {
		for (SimPlot p : s.plots()) {
			Integer b = before.get(p.id());
			if (b == null || b == Integer.MAX_VALUE) {
				continue;
			}
			Blueprint bp;
			try {
				bp = w.blueprints().blueprint(p.spec(), w.terrain());
			} catch (RuntimeException e) {
				continue;
			}
			int now = p.state() == PlotState.BUILT ? bp.stageCount() : p.stage();
			for (int st = b; st < now && st < bp.stageCount(); st++) {
				int[] g = bp.goods(st);
				for (int i = 0; i < g.length && i < into.length; i++) {
					into[i] += g[i];
				}
			}
		}
	}

	private static String after(SimSettlement s, int[] start, int[] consumed, int days) {
		int[] now = s.stockpile().amounts();
		int worked = 0;
		for (SimSite site : s.land().sites()) {
			if (site.workedYesterday() > 0) {
				worked++;
			}
		}
		StringBuilder made = new StringBuilder();
		for (Goods g : PROCESSED) {
			int i = g.ordinal();
			// made = what is in store now - what was + what construction took
			int m = now[i] - start[i] + consumed[i];
			if (m != 0 || consumed[i] != 0) {
				made.append(g.getSerializedName()).append(" +").append(m).append(" (built with ").append(consumed[i]).append(") ");
			}
		}
		StringBuilder raw = new StringBuilder();
		for (Goods g : RAW) {
			int i = g.ordinal();
			if (now[i] != start[i] || consumed[i] != 0) {
				raw.append(g.getSerializedName()).append(' ').append(start[i]).append("->").append(now[i]).append(' ');
			}
		}
		return String.format(Locale.ROOT, "%s after %d days: %d souls (%s), %d of %d sites worked yesterday; processed: %s; raw: %s; %s", s.name(), days,
			s.population(), s.tier().getSerializedName(), worked, s.land().sites().size(), made.toString().trim(), raw.toString().trim(), fortunes(s));
	}

	private static String fortunes(SimSettlement s) {
		int empty = s.land().deserted().size();
		double fill = 0;
		int n = 0;
		for (SimSite site : s.land().sites()) {
			if (site.capacity() > 0) {
				fill += site.fill();
				n++;
			}
		}
		return String.format(Locale.ROOT, "prosperity %.2f, fortunes %+d (%s), capacity %s, %d souls, %d beds, %d houses left empty, sites %.0f%% full, "
				+ "treasury %d", s.prosperity(), s.fortunes(), s.fortunesWhy().isEmpty() ? "-" : s.fortunesWhy(),
			s.carryingCapacity() == Integer.MAX_VALUE ? "-" : s.carryingCapacity(), s.population(), s.beds(), empty, n == 0 ? 0 : fill / n * 100,
			s.treasury());
	}

	private static List<String> hamlets(SimWorld w) {
		List<String> out = new ArrayList<>();
		for (SimSettlement s : w.settlements()) {
			if (s.clockState().parent() != 0) {
				SimSettlement parent = w.settlement(s.clockState().parent());
				out.add(String.format(Locale.ROOT, "hamlet %s at %s, %d blocks from %s, %d souls, sites %d", s.name(), s.center().toShortString(),
					parent == null ? -1 : (int) Math.sqrt(parent.center().distSqr(s.center())), parent == null ? "?" : parent.name(), s.population(),
					s.sites().size()));
			}
		}
		return out;
	}

	private static String events() {
		StringBuilder b = new StringBuilder();
		EVENTS.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e -> b.append(e.getKey()).append(' ').append(e.getValue().get()).append(", "));
		return b.toString();
	}
}
