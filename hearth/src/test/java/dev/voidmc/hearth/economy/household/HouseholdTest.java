package dev.voidmc.hearth.economy.household;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.serialization.JsonOps;
import dev.voidmc.hearth.api.AgeStage;
import dev.voidmc.hearth.api.Goods;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.PlotState;
import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.api.Station;
import dev.voidmc.hearth.npc.companion.PlaceConfig;
import dev.voidmc.hearth.sim.TestSupport;
import dev.voidmc.hearth.sim.model.SimRules;
import dev.voidmc.hearth.sim.settlement.ResidentClaims;
import dev.voidmc.hearth.sim.settlement.SettlementClock;
import dev.voidmc.hearth.sim.settlement.SimPlot;
import dev.voidmc.hearth.sim.settlement.SimSettlement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * A house with servants (docs/LIVING_WORLD_2.md §8.8): servants keep their home and work at the player's house through
 * thirty days of the sim's own assignment; the larder is filled from the town's food and charged to the owner's bank;
 * unpaid three days running, servants leave.
 */
class HouseholdTest {
	private static final PlaceConfig CFG = PlaceConfig.DEFAULTS;
	private static final UUID OWNER = new UUID(3, 3);

	@BeforeAll
	static void claims() {
		net.minecraft.SharedConstants.tryDetectVersion();
		net.minecraft.server.Bootstrap.bootStrap();
		ResidentClaims.register(Servants.CLAIMS);
	}

	private record Town(SimSettlement s, SimPlot house, long day) {
	}

	private static Town town() {
		SimSettlement s = TestSupport.founded("flat", 71);
		for (int d = 1; d <= 60; d++) {
			SettlementClock.advance(s, d * SimRules.DAY);
		}
		SimPlot house = null;
		for (dev.voidmc.hearth.api.PlotView pv : s.plots()) {
			SimPlot p = (SimPlot) pv;
			if (p.state() == PlotState.BUILT && p.beds() > 0 && p.spec().archetype().getPath().startsWith("house/")) {
				house = p;
			}
		}
		assertNotNull(house, "a house stands in the test town");
		s.setPlotOwner(house.id(), OWNER);
		return new Town(s, house, 60);
	}

	private static List<Resident> hands(SimSettlement s, int n) {
		List<Resident> out = new ArrayList<>();
		for (Resident r : s.residents()) {
			if (r.age == AgeStage.ADULT && out.size() < n) {
				r.station = Station.POOR;
				out.add(r);
			}
		}
		return out;
	}

	@Test
	void servantsKeepHomeAndWorkThroughThirtyDaysOfTheSimsAssignment() {
		Town t = town();
		SimSettlement s = t.s;
		HouseBook book = new HouseBook();
		List<Resident> servants = hands(s, 2);
		for (Resident r : servants) {
			assertTrue(Servants.willing(s, r));
			Servants.hire(s, book, r, t.house, t.day, CFG);
		}
		assertEquals(2, book.house(t.house.id()).servants.size());
		int checked = 0;
		for (int d = 61; d <= 90; d++) {
			SettlementClock.advance(s, d * SimRules.DAY);
			for (Resident r : servants) {
				Resident now = s.resident(r.id);
				if (now == null) {
					continue; // a death or a move in the town's own course: not the assignment's doing
				}
				assertEquals(JobType.SERVANT, now.job, "day " + d + ": still in service");
				assertEquals(t.house.id(), now.work, "day " + d + ": works at the house");
				assertEquals(t.house.id(), now.home, "day " + d + ": lives at the house");
				checked++;
			}
		}
		assertTrue(checked >= 30, "servants were there to check");
		// the deed sold back to the town: the claim lets them go to the sim's assignment again
		s.setPlotOwner(t.house.id(), null);
		assertEquals(Resident.NO_PLOT, Servants.keptHouse(s, servants.getFirst()));
	}

	@Test
	void theLarderIsFilledAndChargedAndTheUnpaidLeave() {
		Town t = town();
		SimSettlement s = t.s;
		HouseBook book = new HouseBook();
		List<Resident> servants = hands(s, 3);
		int slots = Servants.slots(s, t.house, CFG);
		assertTrue(slots >= 1 && slots <= CFG.household().servantsPerHouse(), "slots by the beds, at most the config's");
		for (Resident r : servants.subList(0, Math.min(slots, servants.size()))) {
			Servants.hire(s, book, r, t.house, t.day, CFG);
		}
		int hired = book.house(t.house.id()).servants.size();
		s.stockpile().add(Goods.FOOD, 2000);
		int foodBefore = s.stockpile().get(Goods.FOOD);
		long treasuryBefore = s.treasury();
		long[] charged = {0};
		List<String> why = new ArrayList<>();
		long[] purses = {0};
		List<Servants.Left> left = Servants.daily(s, book, t.day + 1, (p, bits, w) -> {
			assertEquals(OWNER, p);
			charged[0] += bits;
			why.add(w);
			return true;
		}, units -> 3L * units, (r, bits) -> purses[0] += bits, CFG);
		assertTrue(left.isEmpty());
		HouseBook.House h = book.house(t.house.id());
		assertEquals(CFG.household().larder(), h.pending, "the larder is bought up to its size");
		assertEquals(foodBefore - CFG.household().larder(), s.stockpile().get(Goods.FOOD), "from the town's own food");
		assertEquals(hired * Servants.wage(CFG) + 3L * CFG.household().larder(), charged[0], "wages and the larder, from the bank");
		assertEquals(treasuryBefore + 3L * CFG.household().larder(), s.treasury(), "the town is paid for its food");
		assertEquals(hired * Servants.wage(CFG), purses[0], "the servants are paid");
		assertTrue(why.contains("larder"));
		// a full larder is not bought again
		charged[0] = 0;
		Servants.daily(s, book, t.day + 2, (p, bits, w) -> {
			charged[0] += bits;
			return true;
		}, units -> 3L * units, (r, bits) -> {
		}, CFG);
		assertEquals(hired * Servants.wage(CFG), charged[0], "only the wages the next day");
		// the bank refuses: three days unpaid and they leave
		for (int d = 3; d <= 4; d++) {
			assertTrue(Servants.daily(s, book, t.day + d, (p, bits, w) -> false, units -> 3L, (r, bits) -> {
			}, CFG).isEmpty(), "they stay a while unpaid");
		}
		List<Servants.Left> gone = Servants.daily(s, book, t.day + 5, (p, bits, w) -> false, units -> 3L, (r, bits) -> {
		}, CFG);
		assertEquals(hired, gone.size(), "three days unpaid: they leave");
		for (Servants.Left l : gone) {
			Resident r = s.resident(l.servant());
			assertNull(ServantState.of(r));
			assertFalse(r.job == JobType.SERVANT);
			assertEquals("unpaid", l.why());
		}
		assertTrue(book.house(t.house.id()).servants.isEmpty());
	}

	@Test
	void aHouseSoldToAnotherPlayerIsServedForItsNewOwner() {
		Town t = town();
		SimSettlement s = t.s;
		HouseBook book = new HouseBook();
		Resident r = hands(s, 1).getFirst();
		Servants.hire(s, book, r, t.house, t.day, CFG);
		UUID buyer = new UUID(9, 9);
		s.setPlotOwner(t.house.id(), buyer);
		List<UUID> paidBy = new ArrayList<>();
		Servants.daily(s, book, t.day + 1, (p, bits, w) -> {
			paidBy.add(p);
			return true;
		}, units -> 1L, (x, bits) -> {
		}, CFG);
		assertEquals(buyer, ServantState.of(r).player(), "servants serve whoever holds the deed");
		assertTrue(paidBy.stream().allMatch(buyer::equals));
		// and the book saves
		var json = HouseBook.CODEC.encodeStart(JsonOps.INSTANCE, book).getOrThrow();
		HouseBook back = HouseBook.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
		assertEquals(book.house(t.house.id()).servants, back.house(t.house.id()).servants);
		assertEquals(buyer, back.house(t.house.id()).owner);
	}
}
