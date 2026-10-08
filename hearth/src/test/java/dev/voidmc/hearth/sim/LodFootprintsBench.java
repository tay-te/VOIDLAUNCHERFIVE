package dev.voidmc.hearth.sim;

import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.HearthServices;
import dev.voidmc.hearth.api.PlotSpec;
import dev.voidmc.hearth.api.Silhouette;
import dev.voidmc.hearth.api.testing.SyntheticTerrain;
import dev.voidmc.hearth.sim.atlas.Atlas;
import dev.voidmc.hearth.sim.atlas.RegionPlan;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;

/**
 * What VOID LOD's footprints cost over 256 regions (an 8 km view) of synthetic hills with the real catalogue and planner:
 * reads while regions are not ready, the background build cold (plans and silhouettes), with the plans already made, and
 * after a restart (from the file); then reads on ready regions. Prints BENCH lines. Run with
 * {@code HEARTH_BENCH=1 ./gradlew test --tests '*LodFootprintsBench*'} (minutes: it plans every region).
 */
@EnabledIfEnvironmentVariable(named = "HEARTH_BENCH", matches = ".+")
class LodFootprintsBench {
	private static final int R = 8;

	private static double drain(LodFootprints f, String label) throws InterruptedException {
		long t0 = System.nanoTime();
		double maxRead = 0;
		List<Double> reads = new ArrayList<>();
		int[] out = new int[3];
		for (int rx = -R; rx < R; rx++) {
			for (int rz = -R; rz < R; rz++) {
				long a = System.nanoTime();
				f.sample((rx << 10) + 512, (rz << 10) + 512, out);
				double ms = (System.nanoTime() - a) / 1e6;
				reads.add(ms);
				maxRead = Math.max(maxRead, ms);
			}
		}
		// poll versions as LOD does until every region is ready
		while (true) {
			boolean all = true;
			for (int rx = -R; rx < R; rx++) {
				for (int rz = -R; rz < R; rz++) {
					long a = System.nanoTime();
					f.version(rx, rz);
					double ms = (System.nanoTime() - a) / 1e6;
					reads.add(ms);
					maxRead = Math.max(maxRead, ms);
					all &= f.ready(rx, rz);
				}
			}
			if (all) {
				break;
			}
			Thread.sleep(200);
		}
		double wall = (System.nanoTime() - t0) / 1e6;
		reads.sort(null);
		long slow = reads.stream().filter(ms -> ms > 0.05).count();
		System.out.printf("BENCH   reads while building: %d, median %.5f ms, p99.9 %.4f ms, %d over 0.05 ms%n", reads.size(), reads.get(reads.size() / 2),
			reads.get((int) (reads.size() * 0.999)), slow);
		System.out.printf("BENCH %s: %d regions ready in %.0f ms wall (%d threads); build %.2f ms/region avg (%.0f ms CPU in all), %d compiles, %d dropped; max read while building %.3f ms%n",
			label, f.built.get(), wall, LodFootprints.THREADS, f.buildNanos.get() / 1e6 / Math.max(1, f.built.get()), f.buildNanos.get() / 1e6,
			f.compiles.get(), f.dropped.get(), maxRead);
		return wall;
	}

	@Test
	void bench(@TempDir Path dir) throws Exception {
		TestSupport.useRealCatalogue();
		SyntheticTerrain t = new SyntheticTerrain(SyntheticTerrain.Kind.HILLS, 8675309L);
		Atlas atlas = new Atlas(Level.OVERWORLD, t, null, TestSupport.rules(), null, b -> Culture.TEMPERATE);
		Path file = dir.resolve("lod.bin");

		// 1. cold: nothing planned, nothing compiled
		LodFootprints cold = new LodFootprints(atlas, t, file, () -> new long[0]);
		drain(cold, "cold (plans + silhouettes)");
		System.out.printf("BENCH   of which atlas plans: %d computed, %.0f ms; %d kept in the atlas cache%n", atlas.plansComputed.sum(), atlas.planNanos.sum() / 1e6,
			atlas.cachedPlans());
		while (cold.pending() > 0) {
			Thread.sleep(50);
		}
		Thread.sleep(500);
		System.out.printf("BENCH   file %d bytes%n", Files.size(file));

		// 2. plans already made (as world generation leaves them), blueprints cold, no file
		long p0 = System.nanoTime();
		for (int rx = -R; rx < R; rx++) {
			for (int rz = -R; rz < R; rz++) {
				atlas.plan(rx, rz);
			}
		}
		System.out.printf("BENCH atlas plans on one thread (for comparison): %.1f ms/region; %n", (System.nanoTime() - p0) / 1e6 / 256);
		TestSupport.useRealCatalogue();
		LodFootprints planned = new LodFootprints(atlas, t, null, () -> new long[0]);
		drain(planned, "plans cached, typical silhouettes");

		// the old way for the same regions: an exact compile per finished plot, cold; and how far typical is from exact
		TestSupport.useRealCatalogue();
		List<PlotSpec> specs = new ArrayList<>();
		for (int rx = -R; rx < R; rx++) {
			for (int rz = -R; rz < R; rz++) {
				for (RegionPlan.HistoricPlot hp : atlas.plan(rx, rz).plots()) {
					if (hp.completed() >= hp.stageCount() && !hp.road()) {
						specs.add(hp.spec());
					}
				}
			}
		}
		Map<String, Silhouette> firstOfKind = new HashMap<>();
		Map<String, PlotSpec> firstSpec = new HashMap<>();
		long c0 = System.nanoTime();
		int n = 0;
		double eaveErr = 0, ridgeErr = 0, colErr = 0;
		int exactSame = 0;
		for (int i = 0; i < specs.size(); i += 4) {
			PlotSpec spec = specs.get(i);
			Silhouette exact = HearthServices.blueprints().blueprint(spec, t).silhouette();
			n++;
			String kind = LodFootprints.kind(spec);
			Silhouette first = firstOfKind.putIfAbsent(kind, exact);
			firstSpec.putIfAbsent(kind, spec);
			if (first != null) {
				PlotSpec fs = firstSpec.get(kind);
				int eaveRel = first.eaveY() - fs.floorY();
				int rise = first.ridgeY() - first.eaveY();
				int span = Math.min(fs.sizeX(), fs.sizeZ());
				if (rise <= span) {
					rise = Math.round(rise * Math.min(spec.sizeX(), spec.sizeZ()) / (float) span);
				}
				int eave = spec.floorY() + eaveRel;
				eaveErr += Math.abs(eave - exact.eaveY());
				ridgeErr += Math.abs(eave + rise - exact.ridgeY());
				colErr += dist(first.wallColor(), exact.wallColor()) + dist(first.roofColor(), exact.roofColor());
				exactSame++;
			}
		}
		double exactMs = (System.nanoTime() - c0) / 1e6;
		System.out.printf("BENCH old way: %d finished plots in 256 regions; exact compile cold %.1f ms/plot (sampled %d) = ~%.0f ms/region%n",
			specs.size(), exactMs / n, n, exactMs / n * specs.size() / 256);
		System.out.printf("BENCH typical vs exact (%d plots): eave off %.2f blocks, ridge off %.2f blocks, colour off %.1f /255 per channel avg%n",
			exactSame, eaveErr / exactSame, ridgeErr / exactSame, colErr / exactSame / 6);

		// 3. restart: a fresh atlas (nothing planned) reading the file
		Atlas fresh = new Atlas(Level.OVERWORLD, t, null, TestSupport.rules(), null, b -> Culture.TEMPERATE);
		LodFootprints restart = new LodFootprints(fresh, t, file, () -> new long[0]);
		drain(restart, "restart (from file)");
		System.out.printf("BENCH   atlas plans on restart: %d%n", fresh.plansComputed.sum());

		// 4. reads on ready regions
		int[] out = new int[3];
		int hits = 0;
		long r0 = System.nanoTime();
		for (int i = 0; i < 2_000_000; i++) {
			int x = Math.floorMod(i * 7919, R * 2048) - R * 1024;
			int z = Math.floorMod(i * 104729, R * 2048) - R * 1024;
			if (restart.sample(x, z, out)) {
				hits++;
			}
		}
		long r1 = System.nanoTime();
		int[] chunk = new int[768];
		int chits = 0;
		for (int i = 0; i < 200_000; i++) {
			int cx = Math.floorMod(i * 7919, R * 128) - R * 64;
			int cz = Math.floorMod(i * 104729, R * 128) - R * 64;
			if (restart.chunk(cx, cz, chunk)) {
				chits++;
			}
		}
		long r2 = System.nanoTime();
		long v0 = System.nanoTime();
		for (int i = 0; i < 200_000; i++) {
			restart.version(Math.floorMod(i, 2 * R) - R, Math.floorMod(i / 16, 2 * R) - R);
		}
		long v1 = System.nanoTime();
		System.out.printf("BENCH reads on ready regions: sample %.5f ms (%d hits of 2M), chunk %.5f ms (%d hits of 200k), version %.5f ms%n",
			(r1 - r0) / 1e6 / 2_000_000, hits, (r2 - r1) / 1e6 / 200_000, chits, (v1 - v0) / 1e6 / 200_000);
	}

	private static int dist(int a, int b) {
		return Math.abs((a >> 16 & 255) - (b >> 16 & 255)) + Math.abs((a >> 8 & 255) - (b >> 8 & 255)) + Math.abs((a & 255) - (b & 255));
	}
}
