package dev.voidmc.hearth.sim.settlement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.AgeStage;
import dev.voidmc.hearth.api.Goods;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.PlotSpec;
import dev.voidmc.hearth.api.PlotState;
import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.api.Station;
import dev.voidmc.hearth.api.StandingService;
import dev.voidmc.hearth.api.HearthServices;
import dev.voidmc.hearth.sim.EventContext;
import dev.voidmc.hearth.sim.TestSupport;
import dev.voidmc.hearth.sim.land.SimSite;
import dev.voidmc.hearth.sim.model.SimRules;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/** Sites, carrying capacity, prosperity, boom and bust, stations and standing (docs/SIM.md §16). */
class FortunesTest {
	private static void ruin(SimSettlement s) {
		for (SimSite site : s.land().sites()) {
			site.forgive();
		}
		// the land is worked out and stays so; the stores are bare, the treasury empty
		for (SimSite site : s.land().sites()) {
			dev.voidmc.hearth.sim.land.Lands.take(s, site, site.capacity() * 2, 0, false, s.ctx());
		}
		for (Goods g : Goods.VALUES) {
			s.stockpile().take(g, 1_000_000);
		}
		s.withdraw(s.treasury());
	}

	@Test
	void sitesAnnounceThemselvesAndTheirExhaustion() {
		EventContext[] ctx = new EventContext[1];
		SimSettlement s = TestSupport.foundedWithEvents("river", 51, ctx);
		assertTrue(ctx[0].count("found ") >= 3, ctx[0].events.toString());
		SimSite site = s.land().sites().getFirst();
		dev.voidmc.hearth.sim.land.Lands.take(s, site, site.capacity() + 1, 0, false, s.ctx());
		dev.voidmc.hearth.sim.land.Lands.take(s, site, 10, 0, false, s.ctx());
		assertEquals(1, ctx[0].count("depleted "), "once per exhaustion");
		if (site.renewable()) {
			// it regrows; past half it may be announced again when it next runs out
			for (int d = 0; d < 400 && site.fill() <= 0.5; d++) {
				dev.voidmc.hearth.sim.land.Lands.daily(s, d, s.ctx());
			}
			assertTrue(site.fill() > 0.5, "regrew to " + site.fill());
		}
	}

	@Test
	void aWorkedOutTownDeclinesAndEmpties() {
		EventContext[] ctx = new EventContext[1];
		SimSettlement s = TestSupport.foundedWithEvents("flat", 52, ctx);
		for (int d = 1; d <= 150; d++) {
			SettlementClock.advance(s, d * SimRules.DAY);
		}
		int peak = s.population();
		int bedsBefore = s.beds();
		assertTrue(peak > 15, "grew to " + peak);
		ruin(s);
		for (int d = 151; d <= 260; d++) {
			ruin(s);
			SettlementClock.advance(s, d * SimRules.DAY);
		}
		assertEquals(-1, s.land().mood(), "declining, prosperity " + s.land().prosperity());
		assertTrue(ctx[0].count("declining: ") >= 1, ctx[0].events.stream().filter(e -> !e.startsWith("removed")).toList().toString());
		assertTrue(s.population() < peak * 0.75, "folk left: " + peak + " -> " + s.population());
		assertTrue(!s.land().deserted().isEmpty(), "houses were left standing empty");
		assertTrue(ctx[0].count("abandoned: left empty") >= 1);
		for (int id : s.land().deserted()) {
			assertEquals(PlotState.ABANDONED, s.plot(id).state());
		}
		assertTrue(s.beds() < bedsBefore, "empty houses' beds no longer count");
	}

	@Test
	void tradeAndCoinMakeABoomAndAFinerFinish() {
		EventContext[] ctx = new EventContext[1];
		SimSettlement s = TestSupport.foundedWithEvents("flat", 53, ctx);
		for (int d = 1; d <= 80; d++) {
			s.deposit(400);
			s.land().traded(600, 0);
			SettlementClock.advance(s, d * SimRules.DAY);
		}
		assertEquals(1, s.land().mood(), "prosperity " + s.land().prosperity());
		assertTrue(ctx[0].count("rising: ") >= 1);
		PlotSpec spec = new PlotSpec(Hearth.id("house/cottage"), Hearth.id("temperate"), 1, BlockPos.ZERO, 9, 9, Direction.SOUTH, 9, 2,
			new CompoundTag());
		assertEquals(3, Prosperity.finish(s, spec, s.ctx().rules()).tier(), "a boom town builds finer");
		PlotSpec road = new PlotSpec(Hearth.id("infra/road"), Hearth.id("temperate"), 1, BlockPos.ZERO, 9, 9, Direction.SOUTH, 9, 2, new CompoundTag());
		assertEquals(2, Prosperity.finish(s, road, s.ctx().rules()).tier(), "roads are left as planned");
	}

	@Test
	void growthStopsAtWhatTheLandFeeds() {
		SimSettlement s = TestSupport.founded("flat", 54);
		for (SimSite site : s.land().sites()) {
			dev.voidmc.hearth.sim.land.Lands.take(s, site, site.capacity() * 2, 0, false, s.ctx());
		}
		int cap = Prosperity.capacity(s, s.ctx().rules(), s.ctx().data());
		assertTrue(cap > 0 && cap < 1000, "capacity " + cap);
		s.land().setCapacity(s.population());
		assertEquals(0, SettlementClock.settlerFactor(s, s.ctx().rules()), 1e-9, "no settlers past the land's capacity");
		assertTrue(SettlementClock.growthFactor(s, s.ctx().rules()) < 0.5, "fewer births");
	}

	@Test
	void aHouseholdRisesAndFallsWithItsPurse() {
		SimSettlement s = TestSupport.founded("flat", 55);
		List<Resident> household = new ArrayList<>();
		for (Resident r : s.residents()) {
			if (r.age == AgeStage.ADULT && household.size() < 2) {
				r.job = JobType.FARMER;
				r.home = 0;
				r.station = Station.PEASANT;
				household.add(r);
			}
		}
		var cfg = s.ctx().rules().resources.stations();
		for (Resident r : household) {
			r.extra("economy").putLong("purse", 2000);
		}
		Stations.review(s, household, cfg, s.ctx().rules(), 0.5);
		assertEquals(Station.PEASANT, household.get(0).station, "one look is not enough");
		Stations.review(s, household, cfg, s.ctx().rules(), 0.5);
		assertEquals(Station.ARTISAN, household.get(0).station, "two looks agree: up a station");
		assertEquals(Station.ARTISAN, household.get(1).station, "the whole household moves");
		for (Resident r : household) {
			r.extra("economy").putLong("purse", 0);
		}
		Stations.review(s, household, cfg, s.ctx().rules(), 0.5);
		Stations.review(s, household, cfg, s.ctx().rules(), 0.5);
		assertEquals(Station.PEASANT, household.get(0).station, "ruin moves it down");
		// a calling stays whatever the purse
		household.get(1).job = JobType.PRIEST;
		Stations.review(s, household, cfg, s.ctx().rules(), 0.5);
		assertEquals(Station.CLERGY, household.get(1).station);
	}

	@Test
	void aStandingModuleIsTheAuthorityOnReputation() {
		SimSettlement s = TestSupport.founded("flat", 56);
		UUID player = UUID.randomUUID();
		s.addReputation(player, 7);
		assertEquals(7, s.reputation(player), "without a standing module, the sim keeps the number");
		FakeStanding fake = new FakeStanding(s);
		HearthServices.setStanding(fake);
		try {
			assertEquals(42, s.reputation(player));
			s.addReputation(player, -5);
			assertEquals(1, fake.deeds.size());
			assertEquals(SimSettlement.REPUTATION_DEED, fake.deeds.getFirst().kind());
			assertEquals(-5, fake.deeds.getFirst().weight());
			// the standing module reading the raw number from inside its own call gets it, not itself again
			assertEquals(7, fake.rawSeen);
		} finally {
			HearthServices.setStanding(null);
		}
	}

	private static final class FakeStanding implements StandingService {
		final SimSettlement s;
		final List<Deed> deeds = new ArrayList<>();
		int rawSeen;

		FakeStanding(SimSettlement s) {
			this.s = s;
		}

		@Override
		public int standing(long settlementId, UUID player) {
			this.rawSeen = this.s.reputation(player);
			return 42;
		}

		@Override
		public Title title(long settlementId, UUID player) {
			return Title.STRANGER;
		}

		@Override
		public void deed(Deed deed) {
			this.deeds.add(deed);
			// a module built on the raw numbers writes them back: that must not recurse
			this.s.addReputation(deed.player(), 0);
		}

		@Override
		public int opinion(UUID resident, UUID player) {
			return 0;
		}

		@Override
		public int fame(UUID player, BlockPos near) {
			return 0;
		}

		@Override
		public float charisma(ServerPlayer player) {
			return 0;
		}

		@Override
		public float menace(ServerPlayer player) {
			return 0;
		}

		@Override
		public @Nullable String company(UUID player) {
			return null;
		}
	}
}
