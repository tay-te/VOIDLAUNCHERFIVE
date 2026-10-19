package dev.voidmc.hearth.sim.settlement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.voidmc.hearth.api.ArchetypeInfo;
import dev.voidmc.hearth.api.PlotState;
import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.sim.TestSupport;
import dev.voidmc.hearth.sim.model.SimRules;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

/** An inn's beds are guest beds: nobody is housed in them, and they do not count as the town's housing. */
class GuestBedsTest {
	@Test
	void guestHousesAreKnown() {
		assertTrue(ArchetypeInfo.guestHouse(Identifier.fromNamespaceAndPath("hearth", "commerce/inn")));
		assertTrue(ArchetypeInfo.guestHouse(Identifier.fromNamespaceAndPath("hearth", "commerce/hostel")));
		assertTrue(ArchetypeInfo.guestHouse(Identifier.fromNamespaceAndPath("hearth", "commerce/caravanserai")));
		assertTrue(!ArchetypeInfo.guestHouse(Identifier.fromNamespaceAndPath("hearth", "house/cottage")));
	}

	@Test
	void nobodyLivesInAnInnAndItsBedsAreNotHousing() {
		SimSettlement s = TestSupport.founded("flat", 53);
		for (int d = 1; d <= 120; d++) {
			SettlementClock.advance(s, d * SimRules.DAY);
		}
		SimPlot house = null;
		for (SimPlot p : s.plots.values()) {
			if (p.state == PlotState.BUILT && p.beds > 0 && p.owner == null && !ArchetypeInfo.guestHouse(p.spec().archetype())) {
				house = p;
				break;
			}
		}
		assertTrue(house != null, "a house was built");
		s.recomputeDerived();
		int homesBefore = s.beds();
		// an inn as an old save left it: its guest beds counted, and a household living in them
		var o = house.spec();
		SimPlot inn = s.addPlot(new dev.voidmc.hearth.api.PlotSpec(Identifier.fromNamespaceAndPath("hearth", "commerce/inn"), o.style(), o.seed() + 1,
			o.origin().offset(64, 0, 64), o.sizeX(), o.sizeZ(), o.facing(), o.maxHeight(), o.tier(), new net.minecraft.nbt.CompoundTag()), Resident.NO_PLOT,
			"test inn");
		inn.state = PlotState.BUILT;
		inn.beds = 4;
		Resident lodger = s.residents.values().iterator().next();
		lodger.home = inn.id();
		s.recomputeDerived();
		assertEquals(0, inn.beds, "an inn's beds are guest beds, not homes");
		assertEquals(homesBefore, s.beds(), "the town's housing does not count them");
		SettlementClock.assignHomes(s);
		assertNotEquals(inn.id(), lodger.home, "the household is turned out of the guest beds");
		for (Resident r : s.residents.values()) {
			assertNotEquals(inn.id(), r.home, r.id + " lives in the inn");
		}
	}
}
