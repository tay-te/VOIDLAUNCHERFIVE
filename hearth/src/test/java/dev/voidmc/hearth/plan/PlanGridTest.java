package dev.voidmc.hearth.plan;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.HearthServices;
import dev.voidmc.hearth.api.PlotView;
import dev.voidmc.hearth.build.BuildModule;
import dev.voidmc.hearth.plan.catalogue.Catalogue;
import dev.voidmc.hearth.plan.grid.Cell;
import dev.voidmc.hearth.plan.grid.Occupancy;
import dev.voidmc.hearth.plan.grid.PlotGeometry;
import dev.voidmc.hearth.plan.layout.LayoutChooser;
import dev.voidmc.hearth.plan.model.LayoutType;
import dev.voidmc.hearth.plan.model.PlanState;
import dev.voidmc.hearth.plan.model.QueuedProject;
import dev.voidmc.hearth.plan.testing.CatalogueDouble;
import dev.voidmc.hearth.plan.testing.MiniSim;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The occupancy grid the planner keeps between calls is only brought up to date where plots came and went
 * ({@code HearthPlanner.reconcile}, {@code remark}), never rebuilt whole in a grown town: it must always equal a rebuild
 * from scratch, or a city's grid drifts from its plots (land held and given back came back as free ground, hiding the
 * gaps under it). Grows walled towns on the real archetypes, with rebuilds that replace their old plots as the sim's
 * do, and compares the kept grid with a fresh one every few days.
 */
class PlanGridTest {
	@BeforeAll
	static void boot() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
		HearthServices.setBlueprints(BuildModule.standalone());
	}

	@AfterAll
	static void restore() {
		HearthServices.setBlueprints(new CatalogueDouble());
	}

	@Test
	void keptGridEqualsARebuild() {
		check(Culture.TEMPERATE, "hills", 5, LayoutType.WALLED);
		check(Culture.TEMPERATE, "river", 4, null);
	}

	private static void check(Culture culture, String terrain, long seed, LayoutType layout) {
		if (layout != null) {
			LayoutChooser.FORCED.set(layout);
		}
		MiniSim sim;
		try {
			sim = RealGrowthTest.grow(culture, terrain, seed, 600);
		} finally {
			LayoutChooser.FORCED.remove();
		}
		for (int day = 5; day <= 260; day += 5) {
			sim.run(5);
			PlanState state = sim.settlement.data(PlanModule.STATE);
			if (state.occupancy == null) {
				continue;
			}
			List<PlotView> plots = new ArrayList<>(sim.settlement.plots());
			plots.sort(Comparator.comparingInt(PlotView::id));
			PlanContext ctx = new PlanContext(sim.settlement, state, sim.terrain, RandomSource.create(0), Catalogue.current(), PlanConfig.DEFAULTS);
			// brought up to date as the next call would first (plots came and went since the last)
			HearthPlanner.reconcile(ctx);
			Occupancy fresh = HearthPlanner.fresh(ctx, plots);
			// a project queued without its marks (a wall waiting on its palisade) shows once it is handed out
			List<int[]> queued = new ArrayList<>();
			for (QueuedProject q : state.queue) {
				int[] b = PlotGeometry.bounds(q.spec());
				if (b != null) {
					queued.add(b);
				}
			}
			Set<Long> tiles = new HashSet<>();
			for (long k : fresh.tileKeys()) {
				tiles.add(k);
			}
			for (long k : state.occupancy.tileKeys()) {
				tiles.add(k);
			}
			int wrong = 0;
			String first = null;
			for (long k : tiles) {
				int tx = (int) (k >> 32);
				int tz = (int) k;
				for (int i = 0; i < 256; i++) {
					int x = tx * 16 + (i & 15);
					int z = tz * 16 + (i >> 4);
					byte a = fresh.get(x, z);
					byte b = state.occupancy.get(x, z);
					if (a != b && queued.stream().noneMatch(r -> x >= r[0] && x <= r[2] && z >= r[1] && z <= r[3])) {
						wrong++;
						if (first == null) {
							first = x + "," + z + " kept " + Cell.name(b) + ", rebuilt " + Cell.name(a);
						}
					}
				}
			}
			assertEquals(0, wrong, culture + " " + terrain + " day " + day + " (" + plots.size() + " plots): " + wrong + " columns differ, first " + first);
		}
	}
}
