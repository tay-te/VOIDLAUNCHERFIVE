package dev.voidmc.hearth.social.events;

import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.voidmc.hearth.api.AgeStage;
import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.api.SettlementTier;
import dev.voidmc.hearth.api.Station;
import dev.voidmc.hearth.law.justice.Trials;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

/**
 * The abstract cost of the town events (docs/LIVING_WORLD_2.md §3.4: ≤ 20 µs per town per sim day; O(1) per event at
 * its time): 200 towns of 60 souls planned for a year — the day's pass over the residents (what {@code FactsReader} and
 * the crime roll do), the plan, and every event's outcome — measured per town-day.
 */
class EventsBenchTest {
	@Test
	void aTownDayCostsLessThanTwentyMicroseconds() {
		int towns = 200;
		List<List<Resident>> people = new ArrayList<>();
		for (int t = 0; t < towns; t++) {
			List<Resident> rs = new ArrayList<>();
			for (int i = 0; i < 60; i++) {
				Resident r = new Resident(new UUID(t, i), "Hob" + i, "Miller", i % 2 == 0, i % 5 == 0 ? AgeStage.CHILD : AgeStage.ADULT, -300, i % 9 == 0
					? JobType.GUARD : JobType.FARMER, i % 15, Resident.NO_PLOT, i, new CompoundTag());
				r.station = i % 3 == 0 ? Station.POOR : Station.PEASANT;
				rs.add(r);
			}
			people.add(rs);
		}
		Set<String> places = EventRulesTest.VILLAGE_PLACES;
		long[] sink = {0};
		Runnable year = () -> {
			for (int t = 0; t < towns; t++) {
				List<Resident> rs = people.get(t);
				long last = Long.MIN_VALUE;
				for (long day = 0; day < 365; day++) {
					int adults = 0;
					int guards = 0;
					for (Resident r : rs) {
						if (r.age != AgeStage.CHILD) {
							adults++;
						}
						if (r.job == JobType.GUARD) {
							guards++;
						}
					}
					TownFacts f = new TownFacts(t, SettlementTier.TOWN, rs.size(), adults, 0.3 + (t % 5) * 0.1, Culture.TEMPERATE, 0.6, t % 4 == 0,
						Math.floorMod(day, 7L) == 2, false, Math.floorMod(day, 7L) == 3, false, false, false, t % 3 == 0 ? 0 : 999, t % 7, 3,
						RaidRules.defence(guards, false, false), places, (int) (day % 96 * 4 / 96));
					Trials.Defendant d = Trials.roll(t, day, rs, f.prosperity(), 0, 0, Set.of(), new HashSet<>(), List.of());
					List<EventScheduler.Offer> offers = new ArrayList<>();
					if (d != null) {
						EventScheduler.Offer o = EventDirector.trialOffer(f.on(true, false, false, false, false, false, 0), List.of(d), id -> rs.get(0), Set.of(),
							600, "staged", day);
						if (o != null) {
							offers.add(o);
						}
					}
					AtomicInteger ids = new AtomicInteger(1);
					for (Scheduled e : EventScheduler.plan(f, day, EventRules.all(), offers, last, Long.MIN_VALUE, (k, dr) -> true, ids::getAndIncrement)) {
						if (e.kind() == EventKind.TAX) {
							last = day;
						}
						Outcome o = Outcomes.resolve(e, EventRules.all().get(e.kind()), Interventions.NONE);
						sink[0] += o.coin() + o.goodsLost();
					}
				}
			}
		};
		year.run(); // warm up
		long t0 = System.nanoTime();
		year.run();
		double perTownDay = (System.nanoTime() - t0) / 1e3 / (towns * 365.0);
		System.out.println(String.format(Locale.ROOT, "events bench: %.2f µs per town-day (200 towns × 365 days), sink %d", perTownDay, sink[0]));
		assertTrue(perTownDay < 20, perTownDay + " µs per town-day");
	}
}
