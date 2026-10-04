package dev.voidmc.hearth.social;

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
import dev.voidmc.hearth.api.testing.SimpleSettlement;
import dev.voidmc.hearth.social.calendar.Agenda;
import dev.voidmc.hearth.social.calendar.Festival;
import dev.voidmc.hearth.social.calendar.SocialTime;
import dev.voidmc.hearth.social.calendar.TownCalendar;
import dev.voidmc.hearth.social.calendar.TownEvent;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TownCalendarTest {
	final SocialConfig cfg = SocialConfig.DEFAULTS;

	@Test
	void oneMarketDayAWeekAndTownsDiffer() {
		Set<Integer> weekdays = new HashSet<>();
		for (long id = 1; id <= 40; id++) {
			int markets = 0;
			for (long day = 0; day < 7; day++) {
				if (TownCalendar.isMarketDay(id, day, this.cfg.market(), SettlementTier.VILLAGE)) {
					markets++;
					weekdays.add((int) day);
				}
			}
			assertEquals(1, markets);
			long next = TownCalendar.nextMarketDay(id, 10, this.cfg.market());
			assertTrue(next >= 10 && next < 17);
			assertTrue(TownCalendar.isMarketDay(id, next, this.cfg.market(), SettlementTier.VILLAGE));
		}
		assertTrue(weekdays.size() >= 5, "towns keep different market days");
	}

	@Test
	void campsHoldNoMarket() {
		for (long day = 0; day < 14; day++) {
			assertFalse(TownCalendar.isMarketDay(3, day, this.cfg.market(), SettlementTier.CAMP));
		}
	}

	@Test
	void festivalsOfTheYear() {
		SocialConfig.Festivals f = this.cfg.festivals();
		long year = f.yearDays();
		assertEquals(Festival.MIDSUMMER, TownCalendar.festivalOn(year * 3 + f.midsummerDay(), Culture.ALPINE, -500, SettlementTier.VILLAGE, f));
		assertEquals(Festival.HARVEST, TownCalendar.festivalOn(f.harvestDay(), Culture.ALPINE, -500, SettlementTier.VILLAGE, f));
		assertEquals(Festival.CATTLE_DRIVE, TownCalendar.festivalOn(f.cultureDay(), Culture.ALPINE, -500, SettlementTier.VILLAGE, f));
		assertEquals(Festival.LANTERNS, TownCalendar.festivalOn(f.cultureDay(), Culture.KARST, -500, SettlementTier.VILLAGE, f));
		long founded = -500;
		long foundersDay = Math.floorMod(founded, year) + year;
		assertEquals(Festival.FOUNDERS, TownCalendar.festivalOn(foundersDay, Culture.ALPINE, founded, SettlementTier.VILLAGE, f));
		assertNull(TownCalendar.festivalOn(f.midsummerDay(), Culture.ALPINE, founded, SettlementTier.CAMP, f), "camps keep no feasts");
		TownCalendar.Upcoming up = TownCalendar.nextFestival(0, Culture.TEMPERATE, founded, SettlementTier.TOWN, f);
		assertNotNull(up);
		assertTrue(up.day() >= 0 && up.day() <= year);
	}

	@Test
	void nightFestivalsRunPastMidnightWithinTheirDay() {
		int[] h = TownCalendar.festivalHours(Festival.LANTERNS, this.cfg.festivals());
		long start = SocialTime.at(10, h[0]);
		long end = SocialTime.at(10, h[1]);
		assertTrue(end > start, "01:00 belongs to the sim day that began at sunrise");
		assertEquals(10, SocialTime.day(end));
	}

	@Test
	void timeOfDayRoundTrips() {
		for (int m = 0; m < 24 * 60; m += 7) {
			long t = SocialTime.at(5, m);
			assertEquals(m, SocialTime.minute(t));
		}
		assertEquals(0, SocialTime.offset(6 * 60));
		assertEquals(6000, SocialTime.offset(12 * 60));
	}

	@Test
	void agendaOfAMarketDay() {
		SimpleSettlement s = SocialTestKit.town(11, Culture.TEMPERATE);
		for (int i = 0; i < 30; i++) {
			s.addResident(SocialTestKit.resident("R" + i, i % 2 == 0, AgeStage.ADULT, JobType.FARMER, -200));
		}
		long market = TownCalendar.nextMarketDay(s.id(), 100, this.cfg.market());
		SocialLedger ledger = new SocialLedger();
		List<TownEvent> events = Agenda.of(s, ledger, market, this.cfg);
		assertTrue(events.stream().anyMatch(e -> e.kind() == TownEvent.Kind.MARKET));
		TownEvent m = events.stream().filter(e -> e.kind() == TownEvent.Kind.MARKET).findFirst().orElseThrow();
		assertTrue(m.activeAt(SocialTime.at(market, 12 * 60)));
		assertFalse(m.activeAt(SocialTime.at(market, 20 * 60)));
		// about a third come in the dinner hour, nobody at nine but the idle
		long noon = SocialTime.at(market, 12 * 60 + 10);
		int come = 0;
		for (Resident r : s.residents()) {
			if (Agenda.attends(m, r, noon, this.cfg)) {
				come++;
			}
			assertFalse(Agenda.attends(m, r, SocialTime.at(market, 9 * 60), this.cfg), "farmers are at work at nine");
		}
		assertTrue(come > 3 && come < 20, "a share comes: " + come);
		// and the same folk each time it is asked
		for (Resident r : s.residents()) {
			assertEquals(Agenda.attends(m, r, noon, this.cfg), Agenda.attends(m, r, noon + 5, this.cfg));
		}
	}

	@Test
	void weddingsAndFuneralsComeFromTheLedger() {
		SimpleSettlement s = SocialTestKit.town(12, Culture.NORTHERN);
		Resident a = SocialTestKit.resident("Ada", true, AgeStage.ADULT, JobType.BAKER, -100);
		Resident b = SocialTestKit.resident("Bo", false, AgeStage.ADULT, JobType.SMITH, -110);
		s.addResident(a);
		s.addResident(b);
		SocialLedger ledger = new SocialLedger();
		ledger.family.add(new SocialLedger.FamilyEvent(SocialLedger.FamilyEvent.Kind.WEDDING, 50, List.of(a.id, b.id), List.of("Ada", "Bo"), List.of(), ""));
		ledger.family.add(new SocialLedger.FamilyEvent(SocialLedger.FamilyEvent.Kind.FUNERAL, 50, List.of(SocialTestKit.id(999)), List.of("Old Tom"),
			List.of(a.id), "age"));
		List<TownEvent> events = Agenda.of(s, ledger, 50, this.cfg);
		TownEvent wedding = events.stream().filter(e -> e.kind() == TownEvent.Kind.WEDDING).findFirst().orElseThrow();
		TownEvent feast = events.stream().filter(e -> e.kind() == TownEvent.Kind.FEAST).findFirst().orElseThrow();
		TownEvent funeral = events.stream().filter(e -> e.kind() == TownEvent.Kind.FUNERAL).findFirst().orElseThrow();
		assertEquals(wedding.end(), feast.start());
		assertTrue(Agenda.attends(wedding, a, wedding.start(), this.cfg));
		assertTrue(Agenda.attends(funeral, a, funeral.start(), this.cfg), "kin always come");
		// at the funeral hour, the funeral comes first for the kin
		assertEquals(TownEvent.Kind.FUNERAL, Agenda.forResident(events, a, funeral.start(), this.cfg).kind());
		assertTrue(Agenda.of(s, ledger, 51, this.cfg).stream().noneMatch(e -> e.kind() == TownEvent.Kind.WEDDING));
	}
}
