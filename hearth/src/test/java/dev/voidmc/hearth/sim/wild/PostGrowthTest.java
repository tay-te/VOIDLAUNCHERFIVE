package dev.voidmc.hearth.sim.wild;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.voidmc.hearth.api.AgeStage;
import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.PlotSpec;
import dev.voidmc.hearth.api.PlotState;
import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.api.SettlementKind;
import dev.voidmc.hearth.api.TerrainProbe;
import dev.voidmc.hearth.api.testing.SyntheticTerrain;
import dev.voidmc.hearth.build.BuildModule;
import dev.voidmc.hearth.plan.wild.PostLayouts;
import dev.voidmc.hearth.sim.TestSupport;
import dev.voidmc.hearth.sim.model.SimRules;
import dev.voidmc.hearth.sim.settlement.SettlementClock;
import dev.voidmc.hearth.sim.settlement.SimPlot;
import dev.voidmc.hearth.sim.settlement.SimSettlement;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

/** A post never grows past its programme and keeps its beds filled (docs/LIVING_WORLD_2.md §7.8); the nomads move. */
class PostGrowthTest {
	static {
		TestSupport.boot();
	}

	static final BlueprintsHolder BP = new BlueprintsHolder();

	static final class BlueprintsHolder {
		final dev.voidmc.hearth.api.BlueprintService service = BuildModule.standalone();
	}

	static SimSettlement post(PostType type, TerrainProbe t, long id) {
		PostSite site = dev.voidmc.hearth.plan.wild.PostPlannerTestSites.site(type, t);
		return PostHistory.run(site, id, "Test " + type.getSerializedName(), type == PostType.NOMAD_CAMP ? Culture.STEPPE : Culture.TEMPERATE,
			Level.OVERWORLD, SimRules.defaults(), t, BP.service);
	}

	/** Runs the post's books {@code days} sim days, with its day's business. */
	static void run(SimSettlement s, int days) {
		for (int i = 0; i < days; i++) {
			long target = s.simTick() + SimRules.DAY;
			while (s.simTick() < target) {
				if (SettlementClock.advance(s, target) <= 0) {
					break;
				}
			}
			PostRules.daily(s, Math.floorDiv(s.simTick(), SimRules.DAY));
		}
	}

	static Set<String> programme(SimSettlement s) {
		Set<String> out = new HashSet<>();
		for (SimPlot p : s.plots()) {
			if (p.state() != PlotState.DEMOLISHING) {
				out.add(p.spec().archetype() + "@" + p.spec().origin().getX() + "," + p.spec().origin().getZ());
			}
		}
		return out;
	}

	@Test
	void postsAreBuiltWholeAndPeopled() {
		for (PostType type : new PostType[]{PostType.INN, PostType.TOLL_GATE, PostType.HERMITAGE, PostType.HOSPICE, PostType.FARMSTEAD}) {
			TerrainProbe t = dev.voidmc.hearth.plan.wild.PostPlannerTestSites.terrain(type);
			SimSettlement s = post(type, t, 100 + type.ordinal());
			assertEquals(SettlementKind.POST, s.kind(), type + " is a post");
			assertEquals(type.getSerializedName(), s.postType());
			for (SimPlot p : s.plots()) {
				assertEquals(PlotState.BUILT, p.state(), type + ": " + p.spec().archetype() + " not built");
			}
			assertTrue(s.population() >= type.minFolk, type + " has " + s.population() + " souls");
			assertTrue(s.beds() >= s.population(), type + ": " + s.population() + " souls in " + s.beds() + " beds");
		}
	}

	@Test
	void aPostNeverGrowsPastItsProgrammeAndRefillsItsBeds() {
		for (PostType type : new PostType[]{PostType.INN, PostType.MONASTERY, PostType.HUNTERS_CAMP}) {
			TerrainProbe t = dev.voidmc.hearth.plan.wild.PostPlannerTestSites.terrain(type);
			SimSettlement s = post(type, t, 200 + type.ordinal());
			Set<String> before = programme(s);
			int beds = s.beds();
			run(s, 100);
			// a few leave (gone to town, died): the empty beds fill again from the land round about
			List<Resident> people = s.residentList();
			int gone = 0;
			for (Resident r : people) {
				if (gone < 2 && r.age == AgeStage.ADULT && people.size() - gone > 1) {
					s.removeResident(r.id);
					gone++;
				}
			}
			int low = s.population();
			run(s, 200);
			assertEquals(before, programme(s), type + " grew past its programme in 300 days");
			assertEquals(beds, s.beds(), type + "'s beds changed");
			assertTrue(s.population() <= s.beds() + 1, type + ": " + s.population() + " souls in " + s.beds() + " beds");
			assertTrue(s.population() > low || s.population() >= s.beds(), type + ": its empty beds were not filled (" + low + " -> " + s.population()
				+ " of " + s.beds() + ")");
			assertTrue(s.clockState().daughters() == 0, type + " founded a daughter");
		}
	}

	@Test
	void theTollKeeperKeepsTheGateAndTheHermitHisHut() {
		dev.voidmc.hearth.sim.settlement.ResidentClaims.register(PostRules.JOBS);
		TerrainProbe t = dev.voidmc.hearth.plan.wild.PostPlannerTestSites.terrain(PostType.TOLL_GATE);
		SimSettlement toll = post(PostType.TOLL_GATE, t, 300);
		run(toll, 2);
		assertEquals(1, toll.residentList().stream().filter(r -> r.job == JobType.TOLLKEEPER).count(), "one toll keeper");
		SimSettlement hermit = post(PostType.HERMITAGE, dev.voidmc.hearth.plan.wild.PostPlannerTestSites.terrain(PostType.HERMITAGE), 301);
		run(hermit, 2);
		assertTrue(hermit.residentList().stream().anyMatch(r -> r.job == JobType.HERMIT), "the hermit");
	}

	@Test
	void theNomadsMoveBetweenTheirGroundsEveryFortnight() {
		TerrainProbe t = SyntheticTerrain.named("flat", 9);
		SimSettlement camp = post(PostType.NOMAD_CAMP, t, 400);
		PostData d = PostData.of(camp);
		assertEquals(0, d.ground);
		Set<String> ground0 = programme(camp);
		run(camp, PostRules.CAMP_DAYS + 1);
		assertEquals(1, d.ground, "moved after a fortnight");
		Set<String> ground1 = programme(camp);
		assertTrue(java.util.Collections.disjoint(ground0, ground1), "pitched on the other ground");
		for (SimPlot p : camp.plots()) {
			if (p.state() != PlotState.DEMOLISHING) {
				assertEquals(PlotState.BUILT, p.state(), "felt and poles: up at once");
			}
		}
		assertTrue(camp.beds() >= 6, "its yurts' beds count: " + camp.beds());
		run(camp, PostRules.CAMP_DAYS + 1);
		assertEquals(0, d.ground, "and back");
		assertEquals(ground0, programme(camp), "on the first ground's places again");
		// the record does not pile up: what was struck on a ground is replaced when the camp comes back to it
		int records = camp.plots().size();
		run(camp, 2 * (PostRules.CAMP_DAYS + 1));
		assertTrue(camp.plots().size() <= records, camp.plots().size() + " plots after two more moves, " + records + " before");
	}

	@Test
	void aPostsStepIsCheap() {
		TerrainProbe t = dev.voidmc.hearth.plan.wild.PostPlannerTestSites.terrain(PostType.INN);
		SimSettlement s = post(PostType.INN, t, 500);
		run(s, 10);
		int interval = WildConfig.Wild.DEFAULTS.stepInterval();
		// warmed first (a server steps its posts for hours): a cold step measures the JIT
		for (int i = 0; i < 3000; i++) {
			long target = s.simTick() + interval;
			while (s.simTick() < target) {
				if (SettlementClock.advance(s, target) <= 0) {
					break;
				}
			}
		}
		long total = 0;
		int steps = 400;
		long[] each = new long[steps];
		for (int i = 0; i < steps; i++) {
			long target = s.simTick() + interval;
			long t0 = System.nanoTime();
			while (s.simTick() < target) {
				if (SettlementClock.advance(s, target) <= 0) {
					break;
				}
			}
			each[i] = System.nanoTime() - t0;
			total += each[i];
		}
		java.util.Arrays.sort(each);
		double avgUs = total / 1e3 / steps;
		System.out.printf(java.util.Locale.ROOT, "post step (%d ticks): avg %.1f us, p50 %.1f us, p90 %.1f us; 600 posts: %.4f ms a tick%n", interval,
			avgUs, each[steps / 2] / 1e3, each[steps * 9 / 10] / 1e3, avgUs * 600 / interval / 1e3);
		// the budget is 10 us a step, a day's business once in four steps; the JIT and a test machine give leeway
		assertTrue(each[steps / 2] / 1e3 < 200, "a post's step p50 " + each[steps / 2] / 1e3 + " us");
		PlotSpec any = PostLayouts.layOut(PostData.of(s).site, Culture.TEMPERATE, t).get(0);
		assertTrue(any != null);
		assertEquals(Direction.SOUTH, PostData.of(s).site.facing());
	}
}
