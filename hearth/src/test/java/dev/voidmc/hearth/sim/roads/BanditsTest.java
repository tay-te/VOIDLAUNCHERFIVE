package dev.voidmc.hearth.sim.roads;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.voidmc.hearth.api.TravelService.TravelKind;
import dev.voidmc.hearth.sim.model.SimRules;
import dev.voidmc.hearth.sim.settlement.SimSettlement;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;

/** The danger of the roads and the bands on them (docs/LIVING_WORLD_2.md §1.8). */
class BanditsTest {
	@Test
	void dangerRisesWithLonelinessAndFallsWithPatrols() {
		double len = 3000;
		assertEquals(0, Bandits.danger(100, len, 0.5F, false, false), "near a town nobody robs anyone");
		assertEquals(0, Bandits.danger(len - 300, len, 0.5F, false, false), "near the other town either");
		float middle = Bandits.danger(len / 2, len, 0.5F, false, false);
		float earlier = Bandits.danger(800, len, 0.5F, false, false);
		assertTrue(middle > earlier && earlier > 0, "lonelier toward the middle: " + earlier + " < " + middle);
		assertTrue(Bandits.danger(len / 2, len, 1F, false, false) > middle, "woods and gorges are worse");
		assertTrue(Bandits.danger(len / 2, len, 0.5F, true, false) < middle * 0.5, "a patrol lately makes it safer");
		assertTrue(Bandits.danger(len / 2, len, 0.5F, false, true) < middle * 0.5, "a band broken there lately too");
		assertEquals(0, Bandits.peak(900, 1F, false, false), "a short road between neighbours is never lonely");
		assertTrue(Bandits.peak(4000, 0.6F, false, false) >= Bandits.DANGEROUS, "a long one through the woods is");
	}

	@Test
	void robberyOddsFallWithGuardsAndRiseWithTheBand() {
		assertTrue(Bandits.robOdds(5, 0) > Bandits.robOdds(5, 2));
		assertTrue(Bandits.robOdds(5, 2) > Bandits.robOdds(5, 4));
		assertTrue(Bandits.robOdds(7, 2) > Bandits.robOdds(3, 2));
		assertTrue(Bandits.robOdds(3, 2) < 0.5, "a small band against a guarded caravan mostly fails");
		assertTrue(Bandits.robOdds(7, 0) > 0.8, "a big band against lone travellers mostly wins");
		// a caravan's risk at its departure: nothing on short, patrolled roads; real on a long road with a band
		assertEquals(0, Bandits.caravanRisk(new float[]{0F, 0F}, new boolean[]{false, false}, 2), 1e-9);
		double banded = Bandits.caravanRisk(new float[]{0.8F}, new boolean[]{true}, 2);
		double unbanded = Bandits.caravanRisk(new float[]{0.8F}, new boolean[]{false}, 2);
		assertTrue(banded > unbanded && unbanded > 0, unbanded + " < " + banded);
		assertTrue(Bandits.caravanRisk(new float[]{0.8F}, new boolean[]{true}, 4) < banded, "more guards, less risk");
	}

	@Test
	void aBrokenBandFreesItsRoad() {
		Object[] s = TrafficTest.setup();
		Traffic t = (Traffic) s[0];
		Way way = (Way) s[4];
		Band band = t.newBand(way, RandomSource.create(3), 0);
		t.book().bands.add(band.withStrength(2, 0.6));
		float before = t.danger(way.id(), band.along());
		assertTrue(before >= 0.6F, "a band makes its stretch dangerous: " + before);
		t.hurtBand(band.id(), 1);
		assertNull(t.band(band.id()), "below two it breaks");
		assertTrue(t.danger(way.id(), band.along()) < before * 0.5F, "and its road is quiet for a while");
		// a camp off the road, in reach of it
		double[] on = RoadWorld.nearestOn(way, band.camp().getX(), band.camp().getZ());
		assertNotNull(on);
		double off = Math.sqrt(on[0]);
		assertTrue(off >= 30 && off <= 130, "camp " + Math.round(off) + " blocks off the road");
	}

	@Test
	void theBooksDecideAWaylayingAndItsOutcomeStands() {
		Object[] s = TrafficTest.setup();
		Traffic t = (Traffic) s[0];
		TrafficTest.World w = (TrafficTest.World) s[1];
		SimSettlement a = (SimSettlement) s[2];
		SimSettlement b = (SimSettlement) s[3];
		Way way = (Way) s[4];
		Band band = new Band(9_000_001, way.id(), t.newBand(way, RandomSource.create(5), 0).camp(), way.length() / 2, 7, 1.0, new int[0], 0, 0);
		t.book().bands.add(band);
		boolean waylaid = false;
		for (int day = 0; day < 40 && !waylaid; day++) {
			w.clock = day * SimRules.DAY;
			a.setSimTick(w.clock);
			long id = t.send(TravelKind.PEDLAR, a, b, List.of(), w.clock + 100, 0, false, 0, 0, new CompoundTag());
			// the next day's roll
			w.clock = (day + 1) * SimRules.DAY - 1;
			t.tick();
			w.clock = (day + 1) * SimRules.DAY;
			t.tick();
			Journey j = t.journey(id);
			if (j != null && !j.outcome().isEmpty()) {
				waylaid = true;
				assertEquals(band.id(), j.band());
				long at = j.waylay();
				// at its tick the outcome stands in the books
				w.clock = at;
				t.tick();
				Journey after = t.journey(id);
				if (after != null) {
					assertTrue(after.outcome().isEmpty(), "done with");
				}
				Band changed = t.band(band.id());
				assertTrue(changed == null || changed.coin() > 0 || changed.morale() < band.morale(), "the band robbed it or was seen off");
			}
			t.recall(id);
		}
		assertTrue(waylaid, "a bold band on a lonely road waylays somebody within forty days");
		assertFalse(t.book().waylaid == 0);
	}
}
