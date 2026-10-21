package dev.voidmc.hearth.social.events;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.voidmc.hearth.api.AgeStage;
import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.api.SettlementTier;
import dev.voidmc.hearth.api.Station;
import dev.voidmc.hearth.api.testing.SimpleSettlement;
import dev.voidmc.hearth.law.justice.Trials;
import dev.voidmc.hearth.social.SocialConfig;
import dev.voidmc.hearth.social.SocialLedger;
import dev.voidmc.hearth.social.SocialModule;
import dev.voidmc.hearth.social.calendar.TownCalendar;
import dev.voidmc.hearth.social.life.FamilyLife;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

/**
 * The event rules (docs/LIVING_WORLD_2.md §3.8, {@code EventRulesTest}): deterministic schedules; frequency per 100
 * town-days by tier within its band (weddings 6–10 in a village, fires 0.3–0.8); trials only on court days and only with
 * a defendant; tax every 14 days only in domain towns; raids only where a band's strength exceeds the defence; at most
 * one major event and three minor a day.
 */
class EventRulesTest {
	static final Set<String> VILLAGE_PLACES = Set.of("chapel", "cemetery", "well", "barn", "storehouse", "town_hall", "pillory");

	static TownFacts facts(long id, SettlementTier tier, int pop, double prosperity, double timber, long seat, int threat, int defence, Set<String> places) {
		return new TownFacts(id, tier, pop, pop * 3 / 4, prosperity, Culture.TEMPERATE, timber, false, false, false, false, false, false, false, seat, threat,
			threat > 0 ? 77 : 0, defence, places, 0);
	}

	static TownFacts onDay(TownFacts f, long day) {
		int season = (int) (Math.floorMod(day, 96L) * 4 / 96);
		return f.on(f.courtDay(), false, Math.floorMod(day, 7L) == 3, Math.floorMod(day, 7L) == 5, false, false, season);
	}

	static List<Scheduled> plan(TownFacts f, long day, List<EventScheduler.Offer> offers, long lastTax, long quiet) {
		AtomicInteger ids = new AtomicInteger(1);
		return EventScheduler.plan(f, day, EventRules.all(), offers, lastTax, quiet, (k, d) -> {
			if (k == EventKind.FIRE) {
				d.plot = 3;
			}
			return true;
		}, ids::getAndIncrement);
	}

	@Test
	void everyKindHasItsRuleInTheData() {
		Map<EventKind, EventRule> rules = EventRules.builtin();
		for (EventKind k : EventKind.VALUES) {
			if (k != EventKind.SCENE) {
				assertNotNull(rules.get(k), "no rule for " + k.id());
			}
		}
		Map<String, EventRule> scenes = EventRules.builtinScenes();
		assertNotNull(scenes.get("oath"));
		assertNotNull(scenes.get("dubbing"));
		assertEquals(EventKind.SCENE, scenes.get("oath").kind());
	}

	@Test
	void theSameDayGivesTheSameEvents() {
		TownFacts f = facts(41, SettlementTier.TOWN, 90, 0.4, 0.7, 12, 6, 2, VILLAGE_PLACES);
		for (long day = 0; day < 400; day++) {
			TownFacts d = onDay(f, day);
			List<Scheduled> a = plan(d, day, List.of(), Long.MIN_VALUE, Long.MIN_VALUE);
			List<Scheduled> b = plan(d, day, List.of(), Long.MIN_VALUE, Long.MIN_VALUE);
			assertEquals(a.size(), b.size());
			for (int i = 0; i < a.size(); i++) {
				assertEquals(a.get(i).kind(), b.get(i).kind());
				assertEquals(a.get(i).start(), b.get(i).start());
				assertEquals(a.get(i).seed(), b.get(i).seed());
				assertEquals(a.get(i).day(), day);
				assertTrue(a.get(i).end() > a.get(i).start());
			}
		}
	}

	@Test
	void atMostOneMajorAndThreeMinorADay() {
		TownFacts f = facts(7, SettlementTier.CITY, 200, 0.2, 0.7, 3, 7, 1, Set.of("chapel", "well", "stage", "barn", "storehouse", "town_hall"));
		int days = 0;
		for (long day = 0; day < 3000; day++) {
			TownFacts d = onDay(f, day);
			List<EventScheduler.Offer> offers = new ArrayList<>();
			offers.add(EventScheduler.Offer.of(EventKind.WEDDING, List.of(new UUID(1, day)), List.of()));
			offers.add(EventScheduler.Offer.of(EventKind.FUNERAL, List.of(new UUID(2, day)), List.of()));
			List<Scheduled> out = plan(d, day, offers, Long.MIN_VALUE, Long.MIN_VALUE);
			long majors = out.stream().filter(e -> e.kind().major).count();
			long minors = out.size() - majors;
			assertTrue(majors <= 1, "day " + day + ": " + majors + " major events");
			assertTrue(minors <= 3, "day " + day + ": " + minors + " minor events");
			if (majors == 1) {
				days++;
				// the dead come first
				assertEquals(EventKind.FUNERAL, out.stream().filter(e -> e.kind().major).findFirst().orElseThrow().kind());
			}
		}
		assertEquals(3000, days);
	}

	@Test
	void firesInAVillageAreRare() {
		double total = 0;
		int towns = 40;
		for (int t = 0; t < towns; t++) {
			TownFacts f = facts(1000 + t, SettlementTier.VILLAGE, 35, 0.5, FactsReader.timber(Culture.TEMPERATE), 0, 0, 0, VILLAGE_PLACES);
			int fires = 0;
			for (long day = 0; day < 1000; day++) {
				for (Scheduled e : plan(onDay(f, day), day, List.of(), Long.MIN_VALUE, Long.MIN_VALUE)) {
					if (e.kind() == EventKind.FIRE) {
						fires++;
						assertEquals(3, e.plot(), "a fire burns the plot its filler chose");
					}
				}
			}
			total += fires / 10.0;
		}
		double per100 = total / towns;
		assertTrue(per100 >= 0.3 && per100 <= 0.8, "fires per 100 village-days: " + per100);
		// stone towns of the dry south burn less than timber towns of the north
		assertTrue(EventScheduler.factor(EventKind.FIRE, facts(1, SettlementTier.VILLAGE, 30, 0.5, FactsReader.timber(Culture.DESERT), 0, 0, 0, Set.of()))
			< EventScheduler.factor(EventKind.FIRE, facts(1, SettlementTier.VILLAGE, 30, 0.5, FactsReader.timber(Culture.NORTHERN), 0, 0, 0, Set.of())));
	}

	@Test
	void weddingsInAVillageComeSixToTenInAHundredDays() {
		SocialConfig cfg = SocialModule.config();
		double total = 0;
		int towns = 20;
		for (int t = 0; t < towns; t++) {
			SimpleSettlement s = new SimpleSettlement(500 + t, "Ashbrook", Culture.TEMPERATE, new BlockPos(0, 64, 0), -400);
			for (int i = 0; i < 50; i++) {
				s.addResident(new Resident(new UUID(500 + t, i), "Ann" + i, "Smith" + (i % 9), i % 2 == 0, AgeStage.ADULT, -300 - (i * 37 % 200), JobType.FARMER,
					Resident.NO_PLOT, Resident.NO_PLOT, i, new CompoundTag()));
			}
			assertEquals(SettlementTier.VILLAGE, s.tier());
			SocialLedger ledger = new SocialLedger();
			int weddings = 0;
			for (long day = 0; day < 100; day++) {
				FamilyLife.day(s, ledger, day, cfg.family(), p -> {
				});
				List<EventScheduler.Offer> offers = EventDirector.familyOffers(ledger, cfg, day);
				TownFacts f = facts(s.id(), s.tier(), s.population(), 0.5, 0.6, 0, 0, 0, VILLAGE_PLACES);
				for (Scheduled e : plan(onDay(f, day), day, offers, Long.MIN_VALUE, Long.MIN_VALUE)) {
					if (e.kind() == EventKind.WEDDING) {
						weddings++;
						assertEquals(2, e.principals().size());
					}
				}
			}
			total += weddings;
		}
		double per100 = total / towns;
		assertTrue(per100 >= 6 && per100 <= 10, "weddings per 100 village-days: " + per100);
	}

	@Test
	void trialsOnlyOnCourtDaysAndOnlyWithADefendant() {
		Map<UUID, Resident> people = new HashMap<>();
		List<Resident> town = new ArrayList<>();
		for (int i = 0; i < 40; i++) {
			Resident r = new Resident(new UUID(77, i), "Hob" + i, "Miller" + (i % 7), i % 2 == 0, AgeStage.ADULT, -300, JobType.FARMER, i % 12, Resident.NO_PLOT,
				i, new CompoundTag());
			r.station = i % 3 == 0 ? Station.POOR : Station.PEASANT;
			people.put(r.id, r);
			town.add(r);
		}
		for (double prosperity : new double[]{0.2, 0.7}) {
			List<Trials.Defendant> waiting = new ArrayList<>();
			int trials = 0;
			int crimes = 0;
			int days = 14 * 40;
			for (long day = 0; day < days; day++) {
				Set<UUID> w = new HashSet<>();
				for (Trials.Defendant d : waiting) {
					w.add(d.resident());
				}
				Trials.Defendant d = Trials.roll(77, day, town, prosperity, 0, 0, Set.of(), w, List.of());
				if (d != null) {
					waiting.add(d);
					crimes++;
				}
				boolean court = Math.floorMod(day, 7L) == 2;
				TownFacts f = facts(77, SettlementTier.VILLAGE, 40, prosperity, 0.5, 0, 0, 0, VILLAGE_PLACES).on(court, false, false, false, false, false, 0);
				EventScheduler.Offer offer = EventDirector.trialOffer(f, waiting, people::get, Set.of(), 9 * 60, "staged", day);
				if (!court || waiting.isEmpty()) {
					assertNull(offer, "a trial with no court or nobody to try, day " + day);
				}
				List<EventScheduler.Offer> offers = offer == null ? List.of() : List.of(offer);
				for (Scheduled e : plan(f, day, offers, Long.MIN_VALUE, Long.MIN_VALUE)) {
					if (e.kind() == EventKind.TRIAL) {
						assertTrue(court, "a trial off the court day");
						assertEquals(1, e.principals().size());
						trials++;
						waiting.removeIf(x -> e.principals().contains(x.resident()));
					}
				}
			}
			double perFortnight = crimes / (days / 14.0);
			if (prosperity < 0.5) {
				assertTrue(perFortnight >= 1 && perFortnight <= 2.5, "petty crimes a fortnight in a poor town: " + perFortnight);
				assertTrue(trials > 0);
			} else {
				assertEquals(0, crimes, "crime in a contented town");
				assertEquals(0, trials);
			}
		}
	}

	@Test
	void taxEveryFourteenDaysOnlyInDomainTowns() {
		TownFacts domain = facts(21, SettlementTier.VILLAGE, 40, 0.5, 0.5, 9, 0, 0, VILLAGE_PLACES);
		long last = Long.MIN_VALUE;
		List<Long> days = new ArrayList<>();
		for (long day = 0; day < 280; day++) {
			for (Scheduled e : plan(onDay(domain, day), day, List.of(), last, Long.MIN_VALUE)) {
				if (e.kind() == EventKind.TAX) {
					days.add(day);
					last = day;
				}
			}
		}
		assertEquals(20, days.size(), "tax days in 280: " + days);
		for (int i = 1; i < days.size(); i++) {
			assertEquals(14, days.get(i) - days.get(i - 1));
		}
		// a town outside any domain, and the seat itself, pay no bailiff
		for (long seat : new long[]{0, 21}) {
			TownFacts f = facts(21, SettlementTier.VILLAGE, 40, 0.5, 0.5, seat, 0, 0, VILLAGE_PLACES);
			for (long day = 0; day < 280; day++) {
				assertTrue(plan(onDay(f, day), day, List.of(), Long.MIN_VALUE, Long.MIN_VALUE).stream().noneMatch(e -> e.kind() == EventKind.TAX));
			}
		}
		// a bailiff kept away one day by a funeral comes the next
		TownFacts f = facts(21, SettlementTier.VILLAGE, 40, 0.5, 0.5, 9, 0, 0, VILLAGE_PLACES);
		long phase = days.get(0);
		long lastTax = phase;
		long due = phase + 14;
		List<Scheduled> funeralDay = plan(onDay(f, due), due, List.of(EventScheduler.Offer.of(EventKind.FUNERAL, List.of(), List.of())), lastTax,
			Long.MIN_VALUE);
		assertTrue(funeralDay.stream().noneMatch(e -> e.kind() == EventKind.TAX));
		assertTrue(plan(onDay(f, due + 1), due + 1, List.of(), lastTax, Long.MIN_VALUE).stream().anyMatch(e -> e.kind() == EventKind.TAX));
	}

	@Test
	void raidsOnlyWhereABandIsStrongerThanTheDefence() {
		TownFacts guarded = facts(31, SettlementTier.HAMLET, 12, 0.5, 0.5, 0, 4, 6, VILLAGE_PLACES);
		TownFacts lonely = facts(32, SettlementTier.HAMLET, 12, 0.5, 0.5, 0, 6, 0, VILLAGE_PLACES);
		int raidsGuarded = 0;
		int raidsLonely = 0;
		long quiet = Long.MIN_VALUE;
		for (long day = 0; day < 2000; day++) {
			raidsGuarded += (int) plan(onDay(guarded, day), day, List.of(), Long.MIN_VALUE, Long.MIN_VALUE).stream().filter(e -> e.kind() == EventKind.RAID)
				.count();
			for (Scheduled e : plan(onDay(lonely, day), day, List.of(), Long.MIN_VALUE, quiet)) {
				if (e.kind() == EventKind.RAID) {
					raidsLonely++;
					quiet = day + 6;
					// by night, from 02:00
					assertTrue(dev.voidmc.hearth.social.calendar.SocialTime.minute(e.start()) >= 120 && dev.voidmc.hearth.social.calendar.SocialTime.minute(
						e.start()) < 180);
				}
			}
		}
		assertEquals(0, raidsGuarded, "a band weaker than the town's defence never raids it");
		assertTrue(raidsLonely > 50 && raidsLonely < 400, "raids on a lonely hamlet in 2000 days: " + raidsLonely);
		// the defence: guards twice, walls four more, a keep six
		assertEquals(2 * 3 + 4 + 6, RaidRules.defence(3, true, true));
	}

	@Test
	void bandsWithinReachByRoad() {
		RaidRules.BandView near = new RaidRules.BandView(1, 5, 0.6, 10, 20, 600, 2000);
		RaidRules.BandView far = new RaidRules.BandView(2, 7, 0.9, 30, 40, 1000, 4000);
		java.util.function.ToDoubleBiFunction<Long, Long> road = (a, b) -> a == 30 && b == 10 ? 3000 : -1;
		assertEquals(near, RaidRules.threat(10, 50, 2, List.of(near, far), road));
		assertNull(RaidRules.threat(10, 50, 5, List.of(near), road), "a band no stronger than the defence");
		assertNull(RaidRules.threat(20, 50, 2, List.of(far), road), "a band not joined by road");
	}

	@Test
	void domainsGoToTheNearestSeatByRoadWithinReach() {
		List<Domains.Seat> seats = List.of(new Domains.Seat(1, new BlockPos(0, 64, 0)), new Domains.Seat(2, new BlockPos(1500, 64, 0)));
		java.util.function.ToDoubleBiFunction<Long, Long> road = (a, b) -> a == 1 ? 1700 : a == 2 ? 900 : -1;
		assertEquals(2, Domains.seatOf(9, new BlockPos(1000, 64, 0), seats, road, true));
		assertEquals(1, Domains.seatOf(1, new BlockPos(0, 64, 0), seats, road, true), "a seat is its own");
		assertEquals(0, Domains.seatOf(9, new BlockPos(9000, 64, 0), seats, (a, b) -> 2500, true), "beyond reach");
		// before the roads are built, the crow's flight a quarter longer
		assertEquals(1, Domains.seatOf(9, new BlockPos(300, 64, 0), seats, (a, b) -> -1, false));
	}

	@Test
	void fairsAndTournamentsKeepTheirOwnDays() {
		SocialConfig.Festivals cfg = SocialModule.config().festivals();
		int year = cfg.yearDays();
		for (long id = 1; id < 40; id++) {
			int fair = 0;
			int tourney = 0;
			for (long day = 0; day < year; day++) {
				boolean f = TownCalendar.isFairDay(id, day, Culture.TEMPERATE, -50, SettlementTier.TOWN, cfg);
				boolean t = TownCalendar.isTourneyDay(id, day, Culture.TEMPERATE, -50, SettlementTier.TOWN, cfg);
				if (f) {
					fair++;
					assertNull(TownCalendar.festivalOn(day, Culture.TEMPERATE, -50, SettlementTier.TOWN, cfg), "a fair on a feast day");
				}
				if (t) {
					tourney++;
					assertFalse(f, "the tournament on a fair day");
				}
			}
			assertEquals(2, fair, "two fair days a year");
			assertEquals(1, tourney, "one tournament a year");
		}
	}

	@Test
	void taxIsAShareAboveTheFloor() {
		assertEquals(0, Outcomes.taxOwed(1000, 20, 0.10, 60));
		assertEquals(80, Outcomes.taxOwed(2000, 20, 0.10, 60));
	}
}
