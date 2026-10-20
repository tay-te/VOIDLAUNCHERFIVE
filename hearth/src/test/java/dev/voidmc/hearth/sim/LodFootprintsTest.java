package dev.voidmc.hearth.sim;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.HearthServices;
import dev.voidmc.hearth.api.PlotSpec;
import dev.voidmc.hearth.api.testing.SyntheticTerrain;
import dev.voidmc.hearth.sim.atlas.Atlas;
import dev.voidmc.hearth.sim.atlas.RegionPlan;
import dev.voidmc.hearth.sim.model.SimRules;
import dev.voidmc.hearth.sim.settlement.SimPlot;
import dev.voidmc.hearth.sim.settlement.SimSettlement;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** VOID LOD's footprints: reads never compute, regions get ready in the background, and a restart reads them back. */
class LodFootprintsTest {
	private static Atlas atlas(long seed) {
		return atlas(seed, true);
	}

	private static Atlas atlas(long seed, boolean histories) {
		TestSupport.boot();
		SimConfig d = SimConfig.DEFAULTS;
		SimConfig.Atlas a = d.atlas();
		SimConfig.Atlas everywhere = new SimConfig.Atlas(histories, 1.0, a.siteMargin(), 160, a.ageSkew(), a.villageClearance(), a.structureClearance(),
			a.guardMargin(), a.treeReach(), a.adoptMargin(), a.planCache());
		SimRules rules = new SimRules(new SimConfig(d.dimensions(), false, d.embody(), d.clock(), d.applier(), everywhere, d.labour(), d.production(),
			d.population(), d.storage(), d.founding(), d.daughters(), d.resources()));
		return new Atlas(Level.OVERWORLD, new SyntheticTerrain(SyntheticTerrain.Kind.HILLS, seed), null, rules, null, b -> Culture.TEMPERATE);
	}

	/** A settled region (rx, rz). */
	private static int[] settled(Atlas atlas) {
		for (int rx = 0; rx < 6; rx++) {
			RegionPlan p = atlas.plan(rx, 1);
			for (RegionPlan.HistoricPlot hp : p.plots()) {
				if (finished(hp)) {
					return new int[]{rx, 1};
				}
			}
		}
		throw new AssertionError("no settled region");
	}

	private static boolean finished(RegionPlan.HistoricPlot hp) {
		return hp.completed() >= hp.stageCount() && !hp.road();
	}

	/** A column of the region that has a building drawn on it (the region must be ready). */
	private static int[] drawnColumn(LodFootprints f, Atlas atlas, int rx, int rz) {
		int[] out = new int[3];
		for (RegionPlan.HistoricPlot hp : atlas.plan(rx, rz).plots()) {
			if (!finished(hp)) {
				continue;
			}
			PlotSpec sp = hp.spec();
			for (int x = sp.origin().getX(); x < sp.origin().getX() + sp.sizeX(); x++) {
				for (int z = sp.origin().getZ(); z < sp.origin().getZ() + sp.sizeZ(); z++) {
					if (Atlas.region(x) == rx && Atlas.region(z) == rz && f.sample(x, z, out)) {
						return new int[]{x, z};
					}
				}
			}
		}
		throw new AssertionError("nothing drawn in region " + rx + "," + rz);
	}

	private static void await(LodFootprints f, int rx, int rz) throws InterruptedException {
		long until = System.currentTimeMillis() + 60_000;
		while (!f.ready(rx, rz)) {
			assertTrue(System.currentTimeMillis() < until, "region never got ready");
			f.version(rx, rz);
			Thread.sleep(5);
		}
	}

	@Test
	void readsNeverWaitAndAReadyRegionChangesVersion(@TempDir Path dir) throws InterruptedException {
		Atlas atlas = atlas(4242);
		int[] reg = settled(atlas);
		Path file = dir.resolve("lod.bin");
		LodFootprints f = new LodFootprints(atlas, atlas.probe(), file, () -> new long[0]);
		assertEquals(0, f.version(reg[0], reg[1]));
		int[] out = new int[3];
		long t0 = System.nanoTime();
		assertFalse(f.sample((reg[0] << 10) + 512, (reg[1] << 10) + 512, out), "nothing is drawn before the region is ready");
		assertTrue((System.nanoTime() - t0) / 1e6 < 50, "a read must not build the region");
		await(f, reg[0], reg[1]);
		assertNotEquals(0, f.version(reg[0], reg[1]));
		int[] col = drawnColumn(f, atlas, reg[0], reg[1]);
		int[] at = {reg[0], reg[1], col[0], col[1]};
		assertTrue(f.sample(at[2], at[3], out));
		assertEquals(0xFF, out[0] >>> 24, "a history's finished building is built (0xFF)");
		assertEquals(0xFF, out[1] >>> 24);
		int eave = out[2] >> 16;
		int ridge = (short) out[2];
		assertTrue(ridge >= eave, eave + " " + ridge);
		int[] chunk = new int[768];
		assertTrue(f.chunk(at[2] >> 4, at[3] >> 4, chunk));
		int i = ((at[3] & 15) << 4 | at[2] & 15) * 3;
		assertArrayEquals(out, new int[]{chunk[i], chunk[i + 1], chunk[i + 2]});

		// saved once idle; a restart (fresh atlas, nothing planned) draws the same without planning
		long until = System.currentTimeMillis() + 10_000;
		while (f.pending() > 0 || !Files.exists(file)) {
			assertTrue(System.currentTimeMillis() < until, "never saved");
			Thread.sleep(10);
		}
		Thread.sleep(50);
		Atlas again = atlas(4242);
		LodFootprints g = new LodFootprints(again, again.probe(), file, () -> new long[0]);
		g.sample(at[2], at[3], new int[3]);
		await(g, at[0], at[1]);
		int[] out2 = new int[3];
		assertTrue(g.sample(at[2], at[3], out2));
		assertArrayEquals(out, out2);
		assertEquals(0, again.plansComputed.sum(), "a saved region needs no plan");
	}

	@Test
	void anotherSeedIgnoresTheFile(@TempDir Path dir) throws Exception {
		Atlas atlas = atlas(4242);
		int[] at = settled(atlas);
		Path file = dir.resolve("lod.bin");
		LodFootprints f = new LodFootprints(atlas, atlas.probe(), file, () -> new long[0]);
		f.sample((at[0] << 10) + 512, (at[1] << 10) + 512, new int[3]);
		await(f, at[0], at[1]);
		long until = System.currentTimeMillis() + 10_000;
		while (f.pending() > 0 || !Files.exists(file)) {
			assertTrue(System.currentTimeMillis() < until);
			Thread.sleep(10);
		}
		Thread.sleep(50);
		Atlas other = atlas(777);
		LodFootprints g = new LodFootprints(other, other.probe(), file, () -> new long[0]);
		g.sample((at[0] << 10) + 512, (at[1] << 10) + 512, new int[3]);
		await(g, at[0], at[1]);
		assertTrue(other.plansComputed.sum() > 0, "another world plans its own");
	}

	private static boolean greyish(int c) {
		int r = c >> 16 & 255, g = c >> 8 & 255, b = c & 255;
		return Math.max(r, Math.max(g, b)) - Math.min(r, Math.min(g, b)) < 16;
	}

	/**
	 * A temperate town with the real catalogue: each building is drawn over the cells it covers (less than its plot), in
	 * its own colours (not grey; more than one roof colour in the town), and none of a history's buildings is flagged.
	 */
	@Test
	void historicBuildingsArePerBuildingAndInTheirOwnColours() throws InterruptedException {
		TestSupport.boot();
		var blueprints = HearthServices.blueprints();
		var planner = HearthServices.planner();
		TestSupport.useRealCatalogue();
		try {
			Atlas atlas = new Atlas(Level.OVERWORLD, new SyntheticTerrain(SyntheticTerrain.Kind.HILLS, 8675309L), null, TestSupport.rules(), null,
				b -> Culture.TEMPERATE);
			int rx = -99;
			int rz = 0;
			for (int x = -4; x < 4 && rx == -99; x++) {
				for (int z = -4; z < 4; z++) {
					if (atlas.plan(x, z).plots().stream().filter(LodFootprintsTest::finished).count() >= 8) {
						rx = x;
						rz = z;
						break;
					}
				}
			}
			assertNotEquals(-99, rx, "no town in 64 regions");
			LodFootprints f = new LodFootprints(atlas, atlas.probe());
			f.sample(rx << 10, rz << 10, new int[3]);
			await(f, rx, rz);
			int[] out = new int[3];
			long plotArea = 0;
			long drawn = 0;
			int smaller = 0;
			int plots = 0;
			int greyCols = 0;
			java.util.Set<Integer> roofs = new java.util.HashSet<>();
			java.util.Set<Integer> walls = new java.util.HashSet<>();
			for (RegionPlan.HistoricPlot hp : atlas.plan(rx, rz).plots()) {
				PlotSpec sp = hp.spec();
				if (!finished(hp) || Atlas.region(sp.origin().getX()) != rx || Atlas.region(sp.origin().getZ()) != rz
					|| Atlas.region(sp.origin().getX() + sp.sizeX() - 1) != rx || Atlas.region(sp.origin().getZ() + sp.sizeZ() - 1) != rz) {
					continue;
				}
				plots++;
				int area = sp.sizeX() * sp.sizeZ();
				int n = 0;
				for (int x = sp.origin().getX(); x < sp.origin().getX() + sp.sizeX(); x++) {
					for (int z = sp.origin().getZ(); z < sp.origin().getZ() + sp.sizeZ(); z++) {
						if (f.sample(x, z, out)) {
							n++;
							assertEquals(0xFF, out[0] >>> 24, "a history's building is built (0xFF), never unfinished");
							int eave = out[2] >> 16;
							int ridge = (short) out[2];
							assertTrue(ridge >= eave, eave + " " + ridge);
							walls.add(out[0] & 0xFFFFFF);
							roofs.add(out[1] & 0xFFFFFF);
							if (greyish(out[0]) && greyish(out[1])) {
								greyCols++;
							}
						}
					}
				}
				plotArea += area;
				drawn += n;
				if (n < area) {
					smaller++;
				}
			}
			System.out.printf("LOD footprints region %d,%d: %d plots, %d of %d plot cells drawn, %d plots drawn smaller than the plot, walls %s, roofs %s, %d grey columns%n",
				rx, rz, plots, drawn, plotArea, smaller, hex(walls), hex(roofs), greyCols);
			assertTrue(plots >= 4, "plots " + plots);
			assertTrue(drawn > 0, "nothing drawn");
			assertTrue(drawn < plotArea * 0.9, "footprints should be the buildings, not their plots: " + drawn + " of " + plotArea);
			assertTrue(smaller >= plots / 2, smaller + " of " + plots + " plots drawn smaller than the plot");
			assertTrue(greyCols < drawn / 4, greyCols + " of " + drawn + " columns grey in wall and roof");
			assertTrue(roofs.size() >= 2, "every roof one colour: " + hex(roofs));
			// a chunk read agrees with the column reads
			int[] chunk = new int[768];
			int[] col = drawnColumn(f, atlas, rx, rz);
			assertTrue(f.chunk(col[0] >> 4, col[1] >> 4, chunk));
			assertTrue(f.sample(col[0], col[1], out));
			int i = ((col[1] & 15) << 4 | col[0] & 15) * 3;
			assertArrayEquals(out, new int[]{chunk[i], chunk[i + 1], chunk[i + 2]});
		} finally {
			HearthServices.setBlueprints(blueprints);
			HearthServices.setPlanner(planner);
		}
	}

	private static String hex(java.util.Set<Integer> cs) {
		StringBuilder b = new StringBuilder();
		for (int c : new java.util.TreeSet<>(cs)) {
			b.append(String.format("#%06X ", c));
		}
		return b.toString().trim();
	}

	/** A live settlement: its unfinished plots are flagged planned, its finished ones are not, and a change bumps the version. */
	@Test
	void plannedPlotsAreFlaggedAndFinishedOnesAreNot() throws InterruptedException {
		SimSettlement s = TestSupport.founded("hills", 1234);
		Atlas atlas = atlas(1234, false);
		LodFootprints f = new LodFootprints(atlas, atlas.probe());
		f.refresh(s);
		java.util.Set<Long> regions = new java.util.HashSet<>();
		for (SimPlot p : s.plots()) {
			regions.add(Atlas.regionKey(Atlas.region(p.spec().origin().getX()), Atlas.region(p.spec().origin().getZ())));
		}
		for (long k : regions) {
			f.sample(net.minecraft.world.level.ChunkPos.getX(k) << 10, net.minecraft.world.level.ChunkPos.getZ(k) << 10, new int[3]);
			await(f, net.minecraft.world.level.ChunkPos.getX(k), net.minecraft.world.level.ChunkPos.getZ(k));
		}
		int[] c = census(f, s);
		System.out.printf("LOD footprints of a founded town: %d active plots (%d flagged columns), %d built (%d unflagged, %d flagged columns)%n", c[0],
			c[2], c[1], c[4], c[3]);
		assertTrue(c[0] > 0, "a founded town plans plots");
		assertTrue(c[2] > 0, "planned plots are drawn flagged");
		assertEquals(0, c[3], "finished plots are never flagged");

		// build until something finishes: its region's version changes and the building shows unflagged
		long[] before = new long[regions.size()];
		int j = 0;
		for (long k : regions) {
			before[j++] = f.version(net.minecraft.world.level.ChunkPos.getX(k), net.minecraft.world.level.ChunkPos.getZ(k));
		}
		int built = c[1];
		for (int d = 1; d <= 60 && built == c[1]; d++) {
			dev.voidmc.hearth.sim.settlement.SettlementClock.advance(s, d * dev.voidmc.hearth.sim.model.SimRules.DAY);
			built = (int) s.plots().stream().filter(SimPlot::isStanding).count();
		}
		assertTrue(built > c[1], "nothing finished in 60 days");
		f.refresh(s);
		for (long k : regions) {
			await(f, net.minecraft.world.level.ChunkPos.getX(k), net.minecraft.world.level.ChunkPos.getZ(k));
		}
		boolean bumped = false;
		j = 0;
		for (long k : regions) {
			bumped |= f.version(net.minecraft.world.level.ChunkPos.getX(k), net.minecraft.world.level.ChunkPos.getZ(k)) != before[j++];
		}
		assertTrue(bumped, "a finished building changes its region's version");
		c = census(f, s);
		System.out.printf("LOD footprints after building: %d active plots (%d flagged columns), %d built (%d unflagged, %d flagged columns)%n", c[0], c[2],
			c[1], c[4], c[3]);
		assertTrue(c[4] > 0, "finished plots are drawn unflagged");
		assertEquals(0, c[3], "finished plots are never flagged");
	}

	/** {active plots, standing plots, flagged columns of active plots, flagged columns of standing ones, unflagged columns of standing ones}. */
	private static int[] census(LodFootprints f, SimSettlement s) {
		int[] out = new int[3];
		int[] c = new int[5];
		for (SimPlot p : s.plots()) {
			if (dev.voidmc.hearth.sim.atlas.PlotColumns.isLine(p.spec())) {
				continue;
			}
			PlotSpec sp = p.spec();
			boolean planned = p.isActive();
			if (planned) {
				c[0]++;
			} else if (p.isStanding()) {
				c[1]++;
			}
			for (int x = sp.origin().getX(); x < sp.origin().getX() + sp.sizeX(); x++) {
				for (int z = sp.origin().getZ(); z < sp.origin().getZ() + sp.sizeZ(); z++) {
					if (!f.sample(x, z, out)) {
						continue;
					}
					int state = out[0] >>> 24;
					assertTrue(state == 0xFF || state == 0xFE, "state byte " + Integer.toHexString(state));
					boolean flag = state == 0xFE;
					if (planned) {
						assertEquals(0xFE, state, "an unfinished plot is 0xFE");
					}
					if (p.isStanding()) {
						assertEquals(0xFF, state, "a built column keeps 0xFF");
					}
					if (planned && flag) {
						c[2]++;
					} else if (p.isStanding()) {
						c[flag ? 3 : 4]++;
					}
				}
			}
		}
		return c;
	}
}
