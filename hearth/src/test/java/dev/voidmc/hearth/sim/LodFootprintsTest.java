package dev.voidmc.hearth.sim;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.testing.SyntheticTerrain;
import dev.voidmc.hearth.sim.atlas.Atlas;
import dev.voidmc.hearth.sim.atlas.RegionPlan;
import dev.voidmc.hearth.sim.model.SimRules;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** VOID LOD's footprints: reads never compute, regions get ready in the background, and a restart reads them back. */
class LodFootprintsTest {
	private static Atlas atlas(long seed) {
		TestSupport.boot();
		SimConfig d = SimConfig.DEFAULTS;
		SimConfig.Atlas a = d.atlas();
		SimConfig.Atlas everywhere = new SimConfig.Atlas(true, 1.0, a.siteMargin(), 160, a.ageSkew(), a.villageClearance(), a.structureClearance(),
			a.guardMargin(), a.treeReach(), a.adoptMargin(), a.planCache());
		SimRules rules = new SimRules(new SimConfig(d.dimensions(), false, d.embody(), d.clock(), d.applier(), everywhere, d.labour(), d.production(),
			d.population(), d.storage(), d.founding(), d.daughters(), d.resources()));
		return new Atlas(Level.OVERWORLD, new SyntheticTerrain(SyntheticTerrain.Kind.HILLS, seed), null, rules, null, b -> Culture.TEMPERATE);
	}

	/** A settled region and a column of one of its finished buildings. */
	private static int[] settled(Atlas atlas) {
		for (int rx = 0; rx < 6; rx++) {
			RegionPlan p = atlas.plan(rx, 1);
			for (RegionPlan.HistoricPlot hp : p.plots()) {
				if (hp.completed() >= hp.stageCount() && !hp.road()) {
					return new int[]{rx, 1, hp.spec().origin().getX() + hp.spec().sizeX() / 2, hp.spec().origin().getZ() + hp.spec().sizeZ() / 2};
				}
			}
		}
		throw new AssertionError("no settled region");
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
		int[] at = settled(atlas);
		Path file = dir.resolve("lod.bin");
		LodFootprints f = new LodFootprints(atlas, atlas.probe(), file, () -> new long[0]);
		assertEquals(0, f.version(at[0], at[1]));
		int[] out = new int[3];
		long t0 = System.nanoTime();
		assertFalse(f.sample(at[2], at[3], out), "nothing is drawn before the region is ready");
		assertTrue((System.nanoTime() - t0) / 1e6 < 50, "a read must not build the region");
		await(f, at[0], at[1]);
		assertNotEquals(0, f.version(at[0], at[1]));
		assertTrue(f.sample(at[2], at[3], out));
		assertTrue((out[0] >>> 24) == 0xFF && (out[1] >>> 24) == 0xFF);
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
		f.sample(at[2], at[3], new int[3]);
		await(f, at[0], at[1]);
		long until = System.currentTimeMillis() + 10_000;
		while (f.pending() > 0 || !Files.exists(file)) {
			assertTrue(System.currentTimeMillis() < until);
			Thread.sleep(10);
		}
		Thread.sleep(50);
		Atlas other = atlas(777);
		LodFootprints g = new LodFootprints(other, other.probe(), file, () -> new long[0]);
		g.sample(at[2], at[3], new int[3]);
		await(g, at[0], at[1]);
		assertTrue(other.plansComputed.sum() > 0, "another world plans its own");
	}
}
