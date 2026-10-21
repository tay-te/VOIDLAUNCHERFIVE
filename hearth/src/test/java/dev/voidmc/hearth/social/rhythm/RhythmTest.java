package dev.voidmc.hearth.social.rhythm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.serialization.Codec;
import dev.voidmc.hearth.api.AgeStage;
import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.HearthServices;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.api.SettlementCalendar;
import dev.voidmc.hearth.api.testing.SimpleSettlement;
import dev.voidmc.hearth.npc.routine.Schedule;
import dev.voidmc.hearth.npc.routine.ScheduleOverrides;
import dev.voidmc.hearth.social.calendar.TownCalendar;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Who goes to the service, who washes, who drinks; the offering; and what the abstract side costs (§2.4, §2.8). */
class RhythmTest {
	private static int next;
	private SettlementCalendar before;

	@BeforeEach
	void calendar() {
		this.before = HearthServices.calendar();
		HearthServices.setCalendar(RhythmCalendar.INSTANCE);
	}

	@AfterEach
	void restore() {
		HearthServices.setCalendar(this.before);
	}

	static Resident resident(AgeStage age, JobType job, int home, String... traits) {
		Resident r = new Resident(new UUID(0xA2L, ++next), "Ann" + next, "Smith", next % 2 == 0, age, -100, job, home, Resident.NO_PLOT, next,
			new CompoundTag());
		if (traits.length > 0) {
			r.extra("npc").store("traits", Codec.STRING.listOf(), List.of(traits));
		}
		return r;
	}

	static void child(Resident child, Resident... parents) {
		child.extra("npc").store("parents", UUIDUtil.CODEC.listOf(), java.util.Arrays.stream(parents).map(p -> p.id).toList());
	}

	/** A village of {@code households} households: two adults and a child each, an elder in every third, a priest. */
	static SimpleSettlement village(long id, int households) {
		SimpleSettlement s = new SimpleSettlement(id, "Ashbrook", Culture.TEMPERATE, new BlockPos(0, 64, 0), -400);
		for (int h = 0; h < households; h++) {
			Resident a = resident(AgeStage.ADULT, JobType.FARMER, h);
			Resident b = resident(AgeStage.ADULT, h % 4 == 0 ? JobType.SMITH : JobType.NONE, h);
			Resident c = resident(AgeStage.CHILD, JobType.NONE, h);
			child(c, a, b);
			s.addResident(a);
			s.addResident(b);
			s.addResident(c);
			if (h % 3 == 0) {
				s.addResident(resident(AgeStage.ELDER, JobType.NONE, h));
			}
		}
		s.addResident(resident(AgeStage.ADULT, JobType.PRIEST, households));
		s.addResident(resident(AgeStage.ADULT, JobType.GUARD, households + 1));
		s.addResident(resident(AgeStage.ADULT, JobType.INNKEEPER, households + 2));
		return s;
	}

	@Test
	void theCongregation() {
		SimpleSettlement s = village(11, 40);
		int adults = 0;
		int going = 0;
		Set<UUID> at = Service.attending(s.residents(), 21, 0.6);
		for (Resident r : s.residents()) {
			switch (r.job) {
				case PRIEST -> assertTrue(at.contains(r.id), "the priest says it");
				case GUARD, INNKEEPER -> assertFalse(at.contains(r.id), "the watch and the inn keep their posts");
				default -> {
				}
			}
			if (r.age == AgeStage.ELDER) {
				assertTrue(at.contains(r.id), "the elders always come");
			}
			if (r.age == AgeStage.ADULT && r.job != JobType.PRIEST && r.job != JobType.GUARD && r.job != JobType.INNKEEPER) {
				adults++;
				if (at.contains(r.id)) {
					going++;
				}
			}
			if (r.age == AgeStage.CHILD) {
				boolean parentGoes = false;
				for (UUID p : dev.voidmc.hearth.social.FolkFacts.parents(r)) {
					parentGoes |= at.contains(p);
				}
				assertEquals(parentGoes, at.contains(r.id), "children come with their parents");
			}
		}
		double share = going / (double) adults;
		assertTrue(share > 0.45 && share < 0.75, "about three adults in five: " + share);
		// the pious always come
		Resident pious = resident(AgeStage.ADULT, JobType.FARMER, 0, "pious");
		assertTrue(Service.goes(pious, 21, 0.0));
		// the same folk all week, others the next
		assertEquals(at, Service.attending(s.residents(), 22, 0.6));
		assertFalse(at.equals(Service.attending(s.residents(), 28, 0.6)));
	}

	@Test
	void thePartsOfTheService() {
		RhythmData.Service svc = RhythmData.DEFAULT.service();
		assertEquals(Service.Part.NONE, Service.part(svc, svc.start() - Service.GATHER - 1));
		assertEquals(Service.Part.GATHER, Service.part(svc, svc.start() - 5));
		assertEquals(Service.Part.SERMON, Service.part(svc, svc.start() + 2));
		int chants = 0;
		for (int m = svc.start(); m < svc.end(); m++) {
			if (Service.part(svc, m) == Service.Part.CHANT && Service.part(svc, m - 1) != Service.Part.CHANT) {
				chants++;
			}
		}
		assertEquals(2, chants, "two chants");
		assertEquals(Service.Part.PORCH, Service.part(svc, svc.end() + 3));
		assertEquals(Service.Part.NONE, Service.part(svc, svc.end() + Service.PORCH));
	}

	@Test
	void washDayOneFromEachOfAQuarterOfHouseholds() {
		SimpleSettlement s = village(12, 20);
		Map<UUID, Integer> w = WashBehaviour.pick(s.residents(), 9, 0.25, 3);
		assertEquals(5, w.size(), "a quarter of twenty households");
		Set<Integer> homes = new java.util.HashSet<>();
		for (Resident r : s.residents()) {
			if (w.containsKey(r.id)) {
				assertEquals(AgeStage.ADULT, r.age);
				assertTrue(homes.add(r.home), "one washer a household");
				assertFalse(WashBehaviour.KEPT.contains(r.job));
			}
		}
		assertEquals(3, WashBehaviour.pick(village(13, 4).residents(), 9, 0.25, 3).size(), "three at least where there are three");
		assertEquals(w, WashBehaviour.pick(s.residents(), 9, 0.25, 3), "the same washers all day");
	}

	@Test
	void tavernNightDrinkersFitTheSeats() {
		SimpleSettlement s = village(14, 40);
		Set<UUID> d = RhythmSchedule.pickDrinkers(s.residents(), 10, 5, 0.22);
		assertTrue(d.size() <= 10 && d.size() >= 4, "drinkers " + d.size());
		for (Resident r : s.residents()) {
			if (d.contains(r.id)) {
				assertEquals(AgeStage.ADULT, r.age);
				assertFalse(RhythmSchedule.NOT_DRINKING.contains(r.job));
			}
		}
		assertTrue(RhythmSchedule.pickDrinkers(s.residents(), 0, 5, 0.22).isEmpty(), "no tavern, no drinkers");
		assertFalse(d.equals(RhythmSchedule.pickDrinkers(s.residents(), 10, 6, 0.22)), "drawn afresh each night");
	}

	@Test
	void theOfferingIsPaidOnceARestDay() {
		SimpleSettlement s = village(15, 30);
		long rest = -1;
		long other = -1;
		for (long day = 50; day < 57; day++) {
			if (RhythmCalendar.INSTANCE.restDay(s, day)) {
				rest = day;
			} else if (other < 0) {
				other = day;
			}
		}
		assertTrue(rest > 0);
		long t0 = s.treasury();
		assertFalse(RhythmDirector.offering(s, other, RhythmConfig.DEFAULTS), "no offering on a working day");
		assertEquals(t0, s.treasury());
		assertTrue(RhythmDirector.offering(s, rest, RhythmConfig.DEFAULTS));
		long paid = s.treasury() - t0;
		assertTrue(paid > 20 && paid < 120, "a bit a head: " + paid);
		assertFalse(RhythmDirector.offering(s, rest, RhythmConfig.DEFAULTS), "once a rest day");
		assertEquals(t0 + paid, s.treasury());
	}

	@Test
	void withoutAPriestAMonkElseAnElderLeads() {
		SimpleSettlement s = village(21, 3);
		Resident priest = s.residents().stream().filter(r -> r.job == JobType.PRIEST).findFirst().orElseThrow();
		assertEquals(priest.id, Service.officiant(s).id);
		priest.job = JobType.NONE;
		Resident elder = Service.officiant(s);
		assertEquals(AgeStage.ELDER, elder.age, "an elder says the words");
		Resident pious = resident(AgeStage.ELDER, JobType.NONE, 9, "pious");
		s.addResident(pious);
		assertEquals(pious.id, Service.officiant(s).id, "a pious elder first");
		Resident monk = resident(AgeStage.ADULT, JobType.MONK, 10);
		s.addResident(monk);
		assertEquals(monk.id, Service.officiant(s).id);
	}

	@Test
	void oneMusicianATavernANight() {
		TavernNight.PLAYING.clear();
		Resident shy = resident(AgeStage.ADULT, JobType.NONE, 1);
		Resident keen = resident(AgeStage.ADULT, JobType.NONE, 2, "cheerful");
		Resident other = resident(AgeStage.ADULT, JobType.NONE, 3, "sociable");
		assertTrue(TavernNight.claimTune(7, 40, keen, true));
		assertFalse(TavernNight.claimTune(7, 40, other, true), "the instrument is taken");
		assertTrue(TavernNight.claimTune(7, 40, keen, true), "and stays with its player");
		TavernNight.releaseTune(7, keen.id);
		assertTrue(TavernNight.claimTune(7, 40, other, true), "put down, another takes it up");
		assertTrue(TavernNight.claimTune(7, 41, keen, true), "a new night");
		TavernNight.PLAYING.clear();
		boolean shyPlays = TavernNight.claimTune(8, 40, shy, false);
		assertEquals(Math.floorMod(shy.id.getLeastSignificantBits(), 2L) != 0, shyPlays);
		TavernNight.PLAYING.clear();
	}

	@Test
	void theLeaderIsTheBestOfThoseHere() {
		SimpleSettlement s = village(22, 6);
		long day = 0;
		while (!RhythmCalendar.INSTANCE.restDay(s, day)) {
			day++;
		}
		Service.clear();
		Service.Day d = Service.day(s, day);
		Resident priest = s.residents().stream().filter(r -> r.job == JobType.PRIEST).findFirst().orElseThrow();
		assertEquals(priest.id, d.leaders().get(0));
		assertEquals(priest.id, Service.leader(s, d), "nobody here: the priest, as written");
		UUID second = d.leaders().get(1);
		s.setResidentEmbodied(second, true);
		assertEquals(second, Service.leader(s, d), "the priest out of reach: the next best who is here");
		s.setResidentEmbodied(priest.id, true);
		assertEquals(priest.id, Service.leader(s, d));
		assertFalse(d.seatOf().containsKey(priest.id), "the one who leads has no pew");
		Service.clear();
	}

	@Test
	void forcedDaysHoldForTheirDayAndTheNext() {
		SimpleSettlement s = village(16, 3);
		long day = 300;
		boolean was = RhythmCalendar.INSTANCE.restDay(s, day);
		RhythmCalendar.force(s.id(), day, RhythmCalendar.Forced.REST);
		assertTrue(RhythmCalendar.INSTANCE.restDay(s, day));
		assertFalse(RhythmCalendar.INSTANCE.marketDay(s, day));
		assertTrue(RhythmCalendar.INSTANCE.restDay(s, day + 1), "and the next, should the town's day turn over early");
		assertEquals(TownCalendar.isRestDay(s.id(), day + 2, 7, RhythmData.of(s.culture()).restWeekday()), RhythmCalendar.INSTANCE.restDay(s, day + 2));
		RhythmCalendar.force(s.id(), day, null);
		assertEquals(was, RhythmCalendar.INSTANCE.restDay(s, day));
		RhythmCalendar.clearForced();
	}

	/** The abstract side's cost (§2.4: ≤ 2 µs a town a day): the offering, and a day's windows for a resident. */
	@Test
	void abstractCost() {
		List<SimpleSettlement> towns = new ArrayList<>();
		for (int i = 0; i < 200; i++) {
			towns.add(village(1000 + i, 12 + i % 30));
		}
		RhythmConfig cfg = RhythmConfig.DEFAULTS;
		// warm up
		for (int d = 0; d < 50; d++) {
			for (SimpleSettlement s : towns) {
				RhythmDirector.offering(s, d, cfg);
			}
		}
		long t0 = System.nanoTime();
		int days = 70;
		for (int d = 50; d < 50 + days; d++) {
			for (SimpleSettlement s : towns) {
				RhythmDirector.offering(s, d, cfg);
			}
		}
		double perTownDay = (System.nanoTime() - t0) / 1e3 / (towns.size() * (double) days);
		RhythmData data = RhythmData.of(Culture.TEMPERATE);
		Schedule base = Schedule.of(JobType.SMITH, AgeStage.ADULT, java.util.EnumSet.noneOf(dev.voidmc.hearth.npc.person.Trait.class), Culture.TEMPERATE, 5, 3);
		long t1 = System.nanoTime();
		int n = 20000;
		for (int i = 0; i < n; i++) {
			ScheduleOverrides.overlay(base, RhythmSchedule.windows(JobType.SMITH, AgeStage.ADULT, List.of(), data, i % 7 == 0, i % 5 == 0, false, i, i / 7));
		}
		double perResidentDay = (System.nanoTime() - t1) / 1e3 / n;
		System.out.printf(java.util.Locale.ROOT, "rhythm abstract cost: offering %.2f us a town a day (avg over the week); a resident's day of windows %.2f us%n",
			perTownDay, perResidentDay);
		assertTrue(perTownDay < 50, "offering " + perTownDay + " us");
	}
}
