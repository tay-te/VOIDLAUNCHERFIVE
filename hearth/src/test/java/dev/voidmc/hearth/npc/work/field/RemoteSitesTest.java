package dev.voidmc.hearth.npc.work.field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.AgeStage;
import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.Goods;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.PlotSpec;
import dev.voidmc.hearth.api.PlotState;
import dev.voidmc.hearth.api.PlotView;
import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.api.ResourceSite;
import dev.voidmc.hearth.api.testing.SimpleSettlement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Remote work sites (docs/LIVING_WORLD_2.md §6.2, §6.8): the sites a town's plots and territory give, who works at each,
 * and where a worker is on the track at the shift's change.
 */
class RemoteSitesTest {
	@BeforeAll
	static void bootstrap() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	/** A town at the origin with a surveyed territory. */
	static final class Town extends SimpleSettlement {
		final List<ResourceSite> sites = new ArrayList<>();
		int residents;

		Town() {
			super(5, "Hollin", Culture.TEMPERATE, new BlockPos(0, 64, 0), 0);
		}

		@Override
		public List<ResourceSite> sites() {
			return this.sites;
		}

		@Override
		public int territoryRadius() {
			return 384;
		}

		PlotView plot(Identifier archetype, int x, int z, int size, Direction facing) {
			SimplePlot p = addPlot(new PlotSpec(archetype, Hearth.id("temperate"), 1, new BlockPos(x, 64, z), size, size, facing, 10, 1, new CompoundTag()));
			p.state = PlotState.BUILT;
			return p;
		}

		Resident worker(JobType job, int work) {
			Resident r = new Resident(new UUID(77, this.residents++), "W" + this.residents, "Test", false, AgeStage.ADULT, -5000, job, Resident.NO_PLOT,
				work, 0, new CompoundTag());
			addResident(r);
			return r;
		}

		ResourceSite meadow(int id, int x, int z) {
			ResourceSite s = new ResourceSite(id, ResourceSite.Kind.PASTURE, new BlockPos(x, 70, z), new BoundingBox(x - 20, 60, z - 20, x + 20, 80, z + 20),
				400, 400, 2, new float[Goods.VALUES.length], null, PlotView.NONE);
			this.sites.add(s);
			return s;
		}
	}

	@Test
	void aPastureWithHerdersGetsTheNearestFarMeadowAndItsHerdersWorkThere() {
		Town t = new Town();
		PlotView pasture = t.plot(RemoteSites.PASTURE, 20, 0, 16, Direction.SOUTH);
		Resident a = t.worker(JobType.HERDER, pasture.id());
		Resident b = t.worker(JobType.HERDER, pasture.id());
		Resident farmer = t.worker(JobType.FARMER, pasture.id());
		// a paddock by the heart (not driven to), one under a plot, the far meadow, one farther still, one past the reach
		t.meadow(1, 10, 10);
		t.meadow(2, 26, 6);
		ResourceSite far = t.meadow(3, 200, 40);
		t.meadow(4, 260, 60);
		t.meadow(5, 700, 0);

		RemoteSites.Plan plan = RemoteSites.plan(t, FieldWorkConfig.Sites.DEFAULTS);
		assertEquals(1, plan.sites().size(), plan.toString());
		RemoteSites.Site site = plan.sites().get(0);
		assertEquals(RemoteSites.Kind.PASTURE, site.kind());
		assertEquals(far.id(), site.resource(), "the nearest meadow out of town");
		assertEquals(far.center(), site.work());
		assertTrue(site.remote());
		assertNotNull(site.from(), "a drove sets out from the pen's gate");
		assertEquals(pasture.id(), site.plot());
		assertEquals(site, plan.of(a.id));
		assertEquals(site, plan.of(b.id));
		assertNull(plan.of(farmer.id), "only the herders drive the flock");
		assertEquals(List.of(a.id, b.id).stream().sorted().toList(), plan.workersAt(0));
		assertTrue(plan.anyRemote());
	}

	@Test
	void eachMeadowIsTakenOnceAndAPastureWithoutHerdersHasNone() {
		Town t = new Town();
		PlotView p1 = t.plot(RemoteSites.PASTURE, 60, 0, 16, Direction.SOUTH);
		PlotView p2 = t.plot(RemoteSites.PASTURE, 60, 40, 16, Direction.SOUTH);
		PlotView idle = t.plot(RemoteSites.PASTURE, -60, 0, 16, Direction.SOUTH);
		t.worker(JobType.HERDER, p1.id());
		t.worker(JobType.HERDER, p2.id());
		t.meadow(1, 200, 0);
		RemoteSites.Plan plan = RemoteSites.plan(t, FieldWorkConfig.Sites.DEFAULTS);
		assertEquals(1, plan.sites().size(), "one meadow, one flock driven to it");
		assertEquals(p1.id(), plan.sites().get(0).plot(), "the first pasture (by plot id) takes it");
		for (RemoteSites.Site s : plan.sites()) {
			assertFalse(s.plot() == idle.id(), "no herders, no drove");
		}
	}

	@Test
	void theGreatMinesPitheadAndTheKilnAreSitesOfTheirPlots() {
		Town t = new Town();
		PlotView pithead = t.plot(RemoteSites.GREAT_PITHEAD, 300, 300, 25, Direction.NORTH);
		PlotView kiln = t.plot(RemoteSites.CHARCOAL_KILN, 40, -40, 12, Direction.SOUTH);
		Resident miner = t.worker(JobType.MINER, pithead.id());
		Resident burner = t.worker(JobType.CHARCOAL_BURNER, kiln.id());
		RemoteSites.Plan plan = RemoteSites.plan(t, FieldWorkConfig.Sites.DEFAULTS);
		RemoteSites.Site mine = plan.of(miner.id);
		assertNotNull(mine);
		assertEquals(RemoteSites.Kind.PITHEAD, mine.kind());
		assertEquals(new BlockPos(312, 64, 312), mine.work(), "the pithead is its plot's middle (the shaft)");
		assertNull(mine.from(), "miners set out from their own doors");
		assertTrue(mine.window()[0] == RemoteSites.SHIFT_START && mine.window()[1] == RemoteSites.SHIFT_END);
		RemoteSites.Site clamp = plan.of(burner.id);
		assertNotNull(clamp);
		assertEquals(RemoteSites.Kind.CLAMP, clamp.kind());
		assertNull(clamp.window(), "a burner keeps the trade's own hours");
		// a plot not yet built is no site
		Town u = new Town();
		SimpleSettlement.SimplePlot planned = (SimpleSettlement.SimplePlot) u.plot(RemoteSites.GREAT_PITHEAD, 300, 300, 25, Direction.NORTH);
		planned.state = PlotState.BUILDING;
		u.worker(JobType.MINER, planned.id());
		assertTrue(RemoteSites.plan(u, FieldWorkConfig.Sites.DEFAULTS).sites().isEmpty());
	}

	@Test
	void theDroveIsOnTheTrackAtTheShiftsChangeAndOnTheMeadowBetween() {
		int travel = Commute.minutes(200, FieldWorkConfig.Sites.DEFAULTS.walkSpeed());
		assertTrue(travel >= 60 && travel <= 180, "two hundred blocks at a flock's pace: " + travel + " minutes");
		int start = RemoteSites.DROVE_START;
		int end = RemoteSites.DROVE_END;
		assertEquals(Commute.Phase.NONE, Commute.at(start - 1, start, end, travel).phase(), "in the pen before 07:00");
		Commute.Where early = Commute.at(start + 10, start, end, travel);
		assertEquals(Commute.Phase.OUT, early.phase());
		assertTrue(early.fraction() > 0 && early.fraction() < 0.5, "a little way out at 07:10");
		assertEquals(Commute.Phase.AT, Commute.at(10 * 60, start, end, travel).phase(), "on the meadow at 10:00");
		Commute.Where late = Commute.at(end - 5, start, end, travel);
		assertEquals(Commute.Phase.BACK, late.phase());
		assertTrue(late.fraction() < 0.2, "nearly home at 17:55");
		assertEquals(Commute.Phase.NONE, Commute.at(20 * 60, start, end, travel).phase(), "the pen full at 20:00");
		// a window too short for the way both ways is split down the middle
		assertEquals(Commute.Phase.OUT, Commute.at(100, 100, 140, 60).phase());
		assertEquals(Commute.Phase.BACK, Commute.at(125, 100, 140, 60).phase());
	}

	@Test
	void aWorkerOnTheWayIsOnTheStraightTrackBetweenHomeAndTheSite() {
		BlockPos home = new BlockPos(0, 64, 0);
		BlockPos site = new BlockPos(200, 90, 100);
		BlockPos half = Commute.along(home, site, 0.5);
		assertEquals(new BlockPos(100, 77, 50), half);
		assertEquals(home, Commute.along(home, site, -1));
		assertEquals(site, Commute.along(home, site, 2));
		// the miners strung out on the track at 06:30
		int travel = Commute.minutes(Commute.distance(home, site), 0.1);
		Commute.Where w = Commute.at(6 * 60 + 30, RemoteSites.SHIFT_START, RemoteSites.SHIFT_END, travel);
		assertEquals(Commute.Phase.OUT, w.phase());
		BlockPos on = Commute.along(home, site, w.fraction());
		assertTrue(RemoteEmbodiment.segmentDistance(on, home, site) < 1.5, "on the track");
		assertTrue(Commute.distance(on, home) > 10 && Commute.distance(on, site) > 10, "neither at home nor at the pithead");
	}

	@Test
	void aRemoteSiteIsNearAPlayerOnItsMeadowOrOnItsTrack() {
		RemoteSites.Site s = new RemoteSites.Site(RemoteSites.Kind.PASTURE, new BlockPos(300, 70, 0), new BlockPos(20, 64, 0), 12, 1, 3, true);
		assertTrue(RemoteEmbodiment.nearSite(new BlockPos(330, 70, 40), s, 112), "beside the meadow");
		assertTrue(RemoteEmbodiment.nearSite(new BlockPos(150, 70, 60), s, 112), "beside the track out");
		assertFalse(RemoteEmbodiment.nearSite(new BlockPos(150, 70, 400), s, 112), "far off both");
		assertEquals(0, RemoteEmbodiment.segmentDistance(new BlockPos(100, 0, 0), new BlockPos(0, 0, 0), new BlockPos(200, 0, 0)), 1e-9);
		assertEquals(30, RemoteEmbodiment.segmentDistance(new BlockPos(-30, 0, 0), new BlockPos(0, 0, 0), new BlockPos(200, 0, 0)), 1e-9);
	}
}
