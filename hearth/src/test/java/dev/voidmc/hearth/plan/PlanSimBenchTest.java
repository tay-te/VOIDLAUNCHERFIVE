package dev.voidmc.hearth.plan;

import dev.voidmc.hearth.api.HearthServices;
import dev.voidmc.hearth.api.PlotSpec;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.api.SettlementPlanner;
import dev.voidmc.hearth.api.TerrainProbe;
import dev.voidmc.hearth.sim.TestSupport;
import dev.voidmc.hearth.sim.model.SimRules;
import dev.voidmc.hearth.sim.settlement.SettlementClock;
import dev.voidmc.hearth.sim.settlement.SimSettlement;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.util.RandomSource;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/**
 * A bench, not a test: with {@code HEARTH_PLAN_SIMBENCH=1} it grows a town with the real sim (residents of every
 * station, prosperity, chains, the clock asking as it does live) over the build module's archetypes, hour by hour, and
 * prints what {@code planner.next} costs per spell as the town becomes a city. Closer to a live server than
 * {@link PlanBenchTest}'s MiniSim. {@code HEARTH_PLAN_SIMBENCH_DAYS} (default 600), {@code _TERRAIN} (hills), {@code _SEED}.
 */
class PlanSimBenchTest {
	@Test
	void bench() throws InterruptedException {
		if (System.getenv("HEARTH_PLAN_SIMBENCH") == null) {
			return;
		}
		TestSupport.useRealCatalogue();
		SettlementPlanner real = HearthServices.planner();
		LongArrayList times = new LongArrayList();
		// calls that answered nothing because they ran out of time, apart (requests/w7-tour.md §1)
		LongArrayList outOfTime = new LongArrayList();
		long[] stepWorst = new long[1];
		HearthServices.setPlanner(new SettlementPlanner() {
			@Override
			public List<PlotSpec> found(FoundingContext ctx) {
				return real.found(ctx);
			}

			@Override
			public @Nullable GrowthProject next(Settlement settlement, TerrainProbe terrain, RandomSource random) {
				long t0 = System.nanoTime();
				GrowthProject p = real.next(settlement, terrain, random);
				long dt = System.nanoTime() - t0;
				times.add(dt);
				String why = p == null ? PlanStats.last(settlement.id()) : null;
				if (why != null && why.contains("out of time")) {
					outOfTime.add(dt);
				}
				return p;
			}
		});
		try {
			int days = Integer.parseInt(System.getenv().getOrDefault("HEARTH_PLAN_SIMBENCH_DAYS", "600"));
			String terrain = System.getenv().getOrDefault("HEARTH_PLAN_SIMBENCH_TERRAIN", "hills");
			long seed = Long.parseLong(System.getenv().getOrDefault("HEARTH_PLAN_SIMBENCH_SEED", "1"));
			// a live call's time limit (live_time_limit_ms is 10): 0 runs every call to the end, as a history does
			HearthPlanner.DEV_DEADLINE_MS = Double.parseDouble(System.getenv().getOrDefault("HEARTH_PLAN_SIMBENCH_DEADLINE", "0"));
			PlanStats.reset();
			PlanStats.everyCall = true;
			SimSettlement s = TestSupport.founded(terrain, seed);
			long t0 = System.nanoTime();
			int from = 0;
			// the second half's calls (the town grown) are profiled together
			PlanBenchTest.Sampler sampler = null;
			for (int d = 1; d <= days; d++) {
				if (d == days / 2 && System.getenv("HEARTH_PLAN_SIMBENCH_PROFILE") != null) {
					sampler = new PlanBenchTest.Sampler(Thread.currentThread());
					sampler.start();
				}
				for (int h = 1; h <= 24; h++) {
					long s0 = System.nanoTime();
					SettlementClock.advance(s, (d - 1) * SimRules.DAY + h * (SimRules.DAY / 24));
					stepWorst[0] = Math.max(stepWorst[0], System.nanoTime() - s0);
				}
				if (d % Math.max(1, days / 12) == 0) {
					System.out.println(String.format("sim %s %d day %d: %d souls, %d plots, prosperity %.2f, %s", terrain, seed, d, s.population(),
						s.plots().size(), s.land().prosperity(), stats(new LongArrayList(times.subList(from, times.size())))));
					from = times.size();
				}
			}
			if (sampler != null) {
				sampler.halt();
				sampler.print();
			}
			System.out.println(String.format("sim %s %d: %d days in %.1f s, all calls %s", terrain, seed, days, (System.nanoTime() - t0) / 1e9, stats(times)));
			System.out.println(String.format("sim %s %d: out of time %s; worst hour step %.2f ms", terrain, seed, stats(outOfTime), stepWorst[0] / 1e6));
			for (String line : PlanStats.lines()) {
				System.out.println("sim " + terrain + " " + seed + ": " + line);
			}
		} finally {
			HearthServices.setPlanner(real);
			HearthPlanner.DEV_DEADLINE_MS = 0;
			PlanStats.everyCall = false;
		}
	}

	private static String stats(LongArrayList nanos) {
		if (nanos.isEmpty()) {
			return "no calls";
		}
		long[] t = nanos.toLongArray();
		Arrays.sort(t);
		double sum = 0;
		for (long v : t) {
			sum += v;
		}
		int over15 = 0;
		int over25 = 0;
		for (long v : t) {
			over15 += v > 15_000_000L ? 1 : 0;
			over25 += v > 25_000_000L ? 1 : 0;
		}
		return String.format("%d calls: median %.2f ms, p90 %.2f ms, max %.2f ms, mean %.2f ms, %d over 15 ms, %d over 25 ms", t.length,
			t[t.length / 2] / 1e6, t[t.length * 9 / 10] / 1e6, t[t.length - 1] / 1e6, sum / t.length / 1e6, over15, over25);
	}
}
