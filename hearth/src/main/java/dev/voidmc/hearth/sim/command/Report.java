package dev.voidmc.hearth.sim.command;

import dev.voidmc.hearth.api.PlotState;
import dev.voidmc.hearth.sim.SimServer;
import dev.voidmc.hearth.sim.settlement.SettlementClock.PlannerTimings;
import dev.voidmc.hearth.sim.settlement.SimSettlement;
import dev.voidmc.hearth.sim.terrain.GeneratorProbe;
import dev.voidmc.hearth.sim.world.SimWorld;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Per-subsystem timings and settlement figures, for {@code /hearth sim stats} and the dev harness log. */
public final class Report {
	private Report() {
	}

	private static double ms(long nanos, long n) {
		return n == 0 ? 0 : nanos / 1e6 / n;
	}

	public static List<String> lines(SimServer server, SimWorld w) {
		List<String> out = new ArrayList<>();
		SimServer.Stats st = server.stats();
		long steps = st.steps.sum();
		out.add(String.format(Locale.ROOT, "clock: %d steps, %.4f ms avg, %.3f ms max, %.1f sim days total, %d planner calls; sim tick work %.3f ms avg, %.1f ms max, %d ticks over 5 ms",
			steps, ms(st.stepNanos.sum(), steps), st.maxStepNanos.get() / 1e6, st.simulatedTicks.sum() / 24000.0, st.plannerCalls.sum(),
			ms(st.tickNanos.sum(), Math.max(1, server.ticks())), st.maxTickNanos.get() / 1e6, st.slowTicks.sum()));
		long live = PlannerTimings.LIVE_CALLS.sum();
		var b = PlannerTimings.LIVE_BUCKETS;
		long hist = PlannerTimings.HISTORY_CALLS.sum();
		double[] pc = PlannerTimings.percentiles();
		out.add(String.format(Locale.ROOT, "planner.next: %d live calls, %.3f ms avg, median %.2f, p90 %.2f, %.1f ms max (<1 ms %d, <5 ms %d, <20 ms %d, more %d); %d history calls, %.3f ms avg",
			live, ms(PlannerTimings.LIVE_NANOS.sum(), live), pc[0], pc[1],
			PlannerTimings.LIVE_MAX.get() / 1e6, b[0].sum(), b[1].sum(), b[2].sum(), b[3].sum(), hist,
			ms(PlannerTimings.HISTORY_NANOS.sum(), hist)));
		// why the live calls answered nothing, and where an overrunning call's time went (requests/w6-plan.md §1)
		out.addAll(dev.voidmc.hearth.plan.PlanStats.lines());
		var a = w.atlas();
		long plans = a.plansComputed.sum();
		long settled = a.plansSettled.sum();
		out.add(String.format(Locale.ROOT, "atlas: %d regions planned (%d settled), %.1f ms avg per region, %.1f ms avg per history, %d cached", plans, settled,
			ms(a.planNanos.sum(), plans), ms(a.historyNanos.sum(), settled), a.cachedPlans()));
		long probes = GeneratorProbe.SAMPLES.sum();
		out.add(String.format(Locale.ROOT, "probe (%s): %d columns, %.2f µs avg", w.probe().describe(), probes,
			probes == 0 ? 0 : GeneratorProbe.NANOS.sum() / 1e3 / probes));
		long chunks = st.featureChunks.sum();
		out.add(String.format(Locale.ROOT, "worldgen: %d chunks with history, %d blocks, %.3f ms avg per chunk; guard kept %d features off plots", chunks,
			st.featureBlocks.sum(), ms(st.featureNanos.sum(), chunks), st.guardSkips.sum()));
		var ap = w.applier();
		out.add(String.format(Locale.ROOT, "applier: %d chunks, %d blocks, %d cleared for rebuilds, %d refused (built over), %.1f ms total, %.1f ms max in a tick, %d queued",
			ap.chunksApplied.sum(), ap.blocksPlaced.sum(), ap.blocksCleared.sum(), ap.conflicts.sum(), ap.nanos.sum() / 1e6, ap.maxTickNanos.get() / 1e6, ap.pending()));
		out.add("slowest parts of a tick: " + server.slowestParts());
		out.add("applier: slowest chunk pass " + ap.slowest + "; " + ap.failures.sum() + " passes failed, " + ap.deferred.sum() + " waited for blueprints");
		long adoptions = st.adoptions.sum();
		out.add(String.format(Locale.ROOT, "adoption: %d, %.2f ms avg; saves: %d settlements written, %.1f ms on the server thread", adoptions,
			ms(st.adoptNanos.sum(), adoptions), st.saved.sum(), st.saveNanos.sum() / 1e6));
		int pop = 0;
		int plots = 0;
		int built = 0;
		for (SimSettlement s : w.settlements()) {
			pop += s.population();
			plots += s.plots().size();
			built += s.plotCounts().getOrDefault(PlotState.BUILT, 0);
		}
		out.add(String.format(Locale.ROOT, "world: %d live settlements, %d souls, %d plots (%d built), %d embodied, %d indexed chunks", w.settlements().size(),
			pop, plots, built, server.embodiedCount(w), w.spatial().indexedChunks()));
		int sites = 0;
		int rising = 0;
		int declining = 0;
		int deserted = 0;
		double prosperity = 0;
		for (SimSettlement s : w.settlements()) {
			sites += s.land().sites().size();
			rising += s.land().mood() > 0 ? 1 : 0;
			declining += s.land().mood() < 0 ? 1 : 0;
			deserted += s.land().deserted().size();
			prosperity += s.land().prosperity();
		}
		out.add(String.format(Locale.ROOT, "land: %d resource sites, prosperity %.2f avg (%d rising, %d declining), %d houses left empty; %s; underground: %s",
			sites, w.settlements().isEmpty() ? 0 : prosperity / w.settlements().size(), rising, declining, deserted, w.landStats(),
			w.underground() == null ? "none" : "Expanse caverns"));
		return out;
	}
}
