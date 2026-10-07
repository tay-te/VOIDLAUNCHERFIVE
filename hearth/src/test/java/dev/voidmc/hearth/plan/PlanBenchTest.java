package dev.voidmc.hearth.plan;

import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.HearthServices;
import dev.voidmc.hearth.api.PlotSpec;
import dev.voidmc.hearth.api.PlotView;
import dev.voidmc.hearth.build.BuildModule;
import dev.voidmc.hearth.plan.layout.LayoutChooser;
import dev.voidmc.hearth.plan.model.LayoutType;
import dev.voidmc.hearth.plan.model.PlanState;
import dev.voidmc.hearth.plan.model.QueuedProject;
import dev.voidmc.hearth.plan.testing.CatalogueDouble;
import dev.voidmc.hearth.plan.testing.MiniSim;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.Test;

/**
 * A bench, not a test: with {@code HEARTH_PLAN_BENCH=1} it grows cities of eight or nine hundred plots on the build
 * module's real archetypes (as the live tour's largest towns, requests/w5-tour.md §6) and prints what {@code next}
 * costs as they grow: median, p90 and worst call per spell, a sampled profile of the last spell (inclusive share per
 * planner method), and a fingerprint of every plot and queued project, so a change meant to make the planner cheaper
 * can show it plans exactly the same towns. {@code HEARTH_PLAN_BENCH_DAYS} (default 420), {@code _CASES} (a comma list
 * of {@code culture:terrain:seed:layout[:prosperous]}, the last building a grade up as a prosperous town does), {@code _PROFILE=0} to skip the sampler.
 */
class PlanBenchTest {
	@Test
	void bench() throws Exception {
		if (System.getenv("HEARTH_PLAN_BENCH") == null) {
			return;
		}
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
		HearthServices.setBlueprints(BuildModule.standalone());
		try {
			int days = Integer.parseInt(System.getenv().getOrDefault("HEARTH_PLAN_BENCH_DAYS", "420"));
			String cases = System.getenv().getOrDefault("HEARTH_PLAN_BENCH_CASES", "temperate:hills:1:-,temperate:hills:5:walled,mediterranean:slope:3:-,temperate:coast:2:-,temperate:river:4:-");
			boolean profile = !"0".equals(System.getenv("HEARTH_PLAN_BENCH_PROFILE"));
			for (String c : cases.split(",")) {
				String[] a = c.split(":");
				run(Culture.valueOf(a[0].toUpperCase()), a[1], Long.parseLong(a[2]), a[3].equals("-") ? null : LayoutType.valueOf(a[3].toUpperCase()), days,
					profile, a.length > 4 && a[4].equals("prosperous"));
			}
		} finally {
			HearthServices.setBlueprints(new CatalogueDouble());
		}
	}

	private static void run(Culture culture, String terrain, long seed, LayoutType layout, int days, boolean profile, boolean prosperous)
		throws Exception {
		if (layout != null) {
			LayoutChooser.FORCED.set(layout);
		}
		MiniSim sim;
		long t0 = System.nanoTime();
		try {
			sim = RealGrowthTest.grow(culture, terrain, seed, 600);
		} finally {
			LayoutChooser.FORCED.remove();
		}
		if (prosperous) {
			sim.prosperous();
		}
		String name = culture + " " + terrain + " " + seed + (layout != null ? " " + layout : "") + (prosperous ? " prosperous" : "");
		int spell = 60;
		// the city's calls (from the day it passes about five hundred plots) are profiled together
		Sampler sampler = null;
		int cityFrom = -1;
		for (int d = 0; d < days; d += spell) {
			int from = sim.callNanos.size();
			if (profile && sampler == null && sim.settlement.plots().size() >= 500) {
				sampler = new Sampler(Thread.currentThread());
				sampler.start();
			}
			if (cityFrom < 0 && sim.settlement.plots().size() >= 500) {
				cityFrom = from;
			}
			sim.run(Math.min(spell, days - d));
			LongArrayList times = new LongArrayList(sim.callNanos.subList(from, sim.callNanos.size()));
			System.out.println(name + " day " + sim.day() + ": " + sim.settlement.population() + " souls, " + sim.settlement.plots().size() + " plots, "
				+ stats(times));
		}
		if (sampler != null) {
			sampler.halt();
			sampler.print();
		}
		if (cityFrom >= 0) {
			System.out.println(name + " city calls (500+ plots): " + stats(new LongArrayList(sim.callNanos.subList(cityFrom, sim.callNanos.size()))));
		}
		System.out.println(String.format("%s: grown in %.1f s, all calls %s, fingerprint %016x", name, (System.nanoTime() - t0) / 1e9, stats(sim.callNanos),
			fingerprint(sim)));
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
		return String.format("%d calls: median %.2f ms, p90 %.2f ms, max %.2f ms, mean %.2f ms", t.length, t[t.length / 2] / 1e6,
			t[t.length * 9 / 10] / 1e6, t[t.length - 1] / 1e6, sum / t.length / 1e6);
	}

	/** Every plot (in id order) and queued project, as a hash: two runs that plan the same town print the same value. */
	static long fingerprint(MiniSim sim) {
		List<PlotView> plots = new ArrayList<>(sim.settlement.plots());
		plots.sort(Comparator.comparingInt(PlotView::id));
		long h = 1125899906842597L;
		for (PlotView p : plots) {
			h = h * 31 + spec(p.spec()) + p.id() * 17L + p.replaces();
		}
		PlanState st = sim.settlement.data(PlanModule.STATE);
		for (QueuedProject q : st.queue) {
			h = h * 31 + spec(q.spec());
		}
		h = h * 31 + st.graph.edges().size() * 131L + st.graph.townLength();
		return h;
	}

	private static long spec(PlotSpec s) {
		return s.archetype().hashCode() * 31L + s.origin().hashCode() * 7L + s.sizeX() * 131L + s.sizeZ() * 137L + s.facing().ordinal()
			+ s.floorY() * 1013L + s.seed() + s.params().toString().hashCode() * 3L;
	}

	/** Samples a thread's stack every millisecond and counts, per planner method, the samples it was on the stack in. */
	private static final class Sampler extends Thread {
		private final Thread target;
		private final Map<String, Integer> inclusive = new HashMap<>();
		private final Map<String, Integer> self = new HashMap<>();
		private volatile boolean running = true;
		private int samples;

		Sampler(Thread target) {
			this.target = target;
			setDaemon(true);
		}

		@Override
		public void run() {
			while (this.running) {
				StackTraceElement[] st = this.target.getStackTrace();
				int top = -1;
				for (int i = 0; i < st.length; i++) {
					if (st[i].getClassName().equals("dev.voidmc.hearth.plan.HearthPlanner") && st[i].getMethodName().equals("next")) {
						top = i;
						break;
					}
				}
				if (top >= 0) {
					this.samples++;
					Set<String> seen = new HashSet<>();
					for (int i = 0; i <= top; i++) {
						String k = st[i].getClassName().substring(st[i].getClassName().lastIndexOf('.') + 1) + "." + st[i].getMethodName();
						if (i == 0) {
							this.self.merge(k, 1, Integer::sum);
						}
						if (seen.add(k)) {
							this.inclusive.merge(k, 1, Integer::sum);
						}
					}
				}
				java.util.concurrent.locks.LockSupport.parkNanos(300_000);
			}
		}

		void halt() throws InterruptedException {
			this.running = false;
			join();
		}

		void print() {
			System.out.println("  profile: " + this.samples + " samples in next");
			print("inclusive", this.inclusive, 60);
			print("self", this.self, 20);
		}

		private void print(String what, Map<String, Integer> m, int n) {
			m.entrySet().stream().sorted(Map.Entry.<String, Integer>comparingByValue().reversed()).limit(n)
				.forEach(e -> System.out.println(String.format("    %s %5.1f%% %s", what, 100.0 * e.getValue() / Math.max(1, this.samples), e.getKey())));
		}
	}
}
