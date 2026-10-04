package dev.voidmc.hearth.social;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.voidmc.hearth.api.AgeStage;
import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.Goods;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.api.Station;
import dev.voidmc.hearth.api.testing.SimpleSettlement;
import dev.voidmc.hearth.social.life.FamilyLife;
import dev.voidmc.hearth.social.life.Matchmaker;
import dev.voidmc.hearth.social.office.Office;
import dev.voidmc.hearth.social.office.Officials;
import dev.voidmc.hearth.social.office.Succession;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;

class FamilyAndOfficesTest {
	@Test
	void matchmakerPairsUnmarriedGrownFolkOfAnAge() {
		Resident a = SocialTestKit.resident("Anna", true, AgeStage.ADULT, JobType.BAKER, -100);
		Resident b = SocialTestKit.resident("Ben", false, AgeStage.ADULT, JobType.SMITH, -105);
		Resident c = SocialTestKit.resident("Carl", false, AgeStage.ADULT, JobType.FARMER, -300);
		Resident kid = SocialTestKit.resident("Kit", false, AgeStage.CHILD, JobType.NONE, -10);
		for (int seed = 0; seed < 50; seed++) {
			Matchmaker.Match m = Matchmaker.find(List.of(a, b, c, kid), Set.of(), false, 0, new SplittableRandom(seed));
			assertNotNull(m);
			assertNotEquals(m.a().female, m.b().female);
			assertTrue(m.a() != kid && m.b() != kid);
			if (m.a() == a || m.b() == a) {
				// Anna is matched with the man nearest her age
				assertTrue(m.a() == b || m.b() == b);
			}
		}
		assertNull(Matchmaker.find(List.of(a, b, c), Set.of(a.id), false, 0, new SplittableRandom(1)), "the only woman is promised");
	}

	@Test
	void kinDoNotMarry() {
		Resident mum = SocialTestKit.resident("Mum", true, AgeStage.ADULT, JobType.BAKER, -300);
		Resident son = SocialTestKit.resident("Son", false, AgeStage.ADULT, JobType.SMITH, -100);
		Resident daughter = SocialTestKit.resident("Dot", true, AgeStage.ADULT, JobType.FARMER, -90);
		SocialTestKit.parents(son, mum);
		SocialTestKit.parents(daughter, mum);
		assertTrue(FolkFacts.kin(mum, son));
		assertTrue(FolkFacts.kin(son, daughter), "siblings share a parent");
		assertNull(Matchmaker.find(List.of(mum, son, daughter), Set.of(), false, 0, new SplittableRandom(3)));
	}

	@Test
	void aWeddingIsAnnouncedThenHeld() {
		SimpleSettlement s = SocialTestKit.town(21, Culture.TEMPERATE);
		Resident a = SocialTestKit.resident("Anna", true, AgeStage.ADULT, JobType.BAKER, -100);
		Resident b = SocialTestKit.resident("Ben", false, AgeStage.ADULT, JobType.SMITH, -105);
		s.addResident(a);
		s.addResident(b);
		s.stockpile().add(Goods.FOOD, 100);
		SocialLedger ledger = new SocialLedger();
		SocialConfig.Family cfg = new SocialConfig.Family(true, 1.0, 3, 11 * 60, false, 0, 12, true, 1, 10 * 60, true, 0.4, 0.25);
		List<FamilyLife.Posting> posts = new ArrayList<>();
		FamilyLife.day(s, ledger, 10, cfg, posts::add);
		assertEquals(1, ledger.family.size());
		SocialLedger.FamilyEvent e = ledger.family.get(0);
		assertEquals(13, e.day());
		assertEquals("hearth.social.notice.banns", posts.get(0).key());
		// no second match while they are promised
		FamilyLife.day(s, ledger, 11, cfg, posts::add);
		assertEquals(1, ledger.family.size());
		FamilyLife.day(s, ledger, 13, cfg, posts::add);
		assertEquals(b.id, FolkFacts.spouse(a));
		assertEquals(a.id, FolkFacts.spouse(b));
		assertEquals(88, s.stockpile().get(Goods.FOOD), "the feast eats");
	}

	@Test
	void aDeathBringsAFuneralWithTheKin() {
		SimpleSettlement s = SocialTestKit.town(22, Culture.TEMPERATE);
		Resident old = SocialTestKit.resident("Old", false, AgeStage.ELDER, JobType.NONE, -600);
		Resident son = SocialTestKit.resident("Son", false, AgeStage.ADULT, JobType.SMITH, -300);
		SocialTestKit.parents(son, old);
		s.addResident(son);
		SocialLedger ledger = new SocialLedger();
		List<FamilyLife.Posting> posts = new ArrayList<>();
		FamilyLife.gone(s, ledger, old, "age", 40, SocialConfig.Family.DEFAULTS, posts::add);
		assertEquals("hearth.social.notice.died", posts.get(0).key());
		assertEquals("hearth.social.notice.funeral", posts.get(1).key());
		SocialLedger.FamilyEvent f = ledger.family.get(0);
		assertEquals(SocialLedger.FamilyEvent.Kind.FUNERAL, f.kind());
		assertEquals(41, f.day());
		assertTrue(f.family().contains(son.id));
		posts.clear();
		FamilyLife.gone(s, ledger, SocialTestKit.resident("Ida", true, AgeStage.ADULT, JobType.FARMER, -100), "left", 40, SocialConfig.Family.DEFAULTS,
			posts::add);
		assertEquals("hearth.social.notice.left", posts.get(0).key());
		assertEquals(1, ledger.family.size(), "no funeral for someone who moved away");
	}

	@Test
	void birthsAreAnnouncedWithTheParents() {
		SimpleSettlement s = SocialTestKit.town(23, Culture.TEMPERATE);
		Resident mum = SocialTestKit.resident("Mae", true, AgeStage.ADULT, JobType.BAKER, -100);
		Resident dad = SocialTestKit.resident("Dan", false, AgeStage.ADULT, JobType.SMITH, -100);
		s.addResident(mum);
		s.addResident(dad);
		Resident baby = SocialTestKit.resident("Bea", true, AgeStage.CHILD, JobType.NONE, 40);
		baby.extra("sim").putString("parent_a", mum.id.toString());
		baby.extra("sim").putString("parent_b", dad.id.toString());
		List<FamilyLife.Posting> posts = new ArrayList<>();
		FamilyLife.born(s, baby, 40, SocialConfig.Family.DEFAULTS, posts::add);
		assertEquals(List.of("Bea", "Mae", "Dan"), posts.get(0).args());
	}

	@Test
	void officialsAreAppointedAndKept() {
		SimpleSettlement s = SocialTestKit.town(31, Culture.TEMPERATE);
		// a town of 70: reeve, lord and steward; guards, priests and builders have their seniors
		for (int i = 0; i < 60; i++) {
			s.addResident(SocialTestKit.resident("F" + i, i % 2 == 0, AgeStage.ADULT, JobType.FARMER, -100 - i));
		}
		Resident merchant = SocialTestKit.resident("Mira", true, AgeStage.ADULT, JobType.MERCHANT, -400, "industrious", "sociable");
		Resident gent = SocialTestKit.resident("Gerold", false, AgeStage.ADULT, JobType.MERCHANT, -500);
		gent.station = Station.GENTRY;
		Resident scholar = SocialTestKit.resident("Sol", false, AgeStage.ADULT, JobType.SCHOLAR, -300, "industrious");
		Resident guard1 = SocialTestKit.resident("Gil", false, AgeStage.ADULT, JobType.GUARD, -200, "brave");
		Resident guard2 = SocialTestKit.resident("Gus", false, AgeStage.ADULT, JobType.GUARD, -150, "lazy");
		Resident priest = SocialTestKit.resident("Pia", true, AgeStage.ADULT, JobType.PRIEST, -250, "pious");
		Resident builder = SocialTestKit.resident("Bart", false, AgeStage.ADULT, JobType.BUILDER, -220);
		for (Resident r : List.of(merchant, gent, scholar, guard1, guard2, priest, builder)) {
			s.addResident(r);
		}
		var keep = SocialTestKit.built(s, "noble/keep", 40, 40);
		var hall = SocialTestKit.built(s, "civic/town_hall", 0, 40);
		SocialLedger ledger = new SocialLedger();
		List<Officials.Change> changes = Officials.update(s, ledger, SocialConfig.Offices.DEFAULTS, 0);
		assertEquals(gent.id, ledger.offices.get(Office.LORD.id()), "the gentleman holds the town");
		assertEquals(JobType.LORD, gent.job);
		assertEquals(keep.id(), gent.work);
		assertEquals(Station.LORD, gent.station);
		assertEquals(guard1.id, ledger.offices.get(Office.GUARD_CAPTAIN.id()), "the brave guard is captain");
		assertEquals(JobType.GUARD, guard1.job, "an honour keeps the trade");
		assertEquals(priest.id, ledger.offices.get(Office.PRIEST.id()));
		assertEquals(builder.id, ledger.offices.get(Office.MASTER_BUILDER.id()));
		Resident reeve = s.resident(ledger.offices.get(Office.REEVE.id()));
		assertNotNull(reeve);
		assertEquals(JobType.REEVE, reeve.job);
		assertEquals(hall.id(), reeve.work);
		assertEquals("reeve", FolkFacts.office(reeve).orElse(""));
		assertNotNull(ledger.offices.get(Office.STEWARD.id()));
		assertNull(ledger.offices.get(Office.JUDGE.id()), "no courthouse, no judge");
		assertTrue(changes.size() >= 6);

		// the sim hands out the day's jobs and knows nothing of offices: the post comes back
		reeve.job = JobType.FARMER;
		reeve.work = Resident.NO_PLOT;
		assertTrue(Officials.update(s, ledger, SocialConfig.Offices.DEFAULTS, 1).isEmpty(), "same holders");
		assertEquals(JobType.REEVE, reeve.job);

		// the lord dies; his grown son inherits
		Resident heir = SocialTestKit.resident("Hal", false, AgeStage.ADULT, JobType.FARMER, -200);
		SocialTestKit.parents(heir, gent);
		s.addResident(heir);
		s.removeResident(gent.id);
		List<Officials.Change> succession = Officials.update(s, ledger, SocialConfig.Offices.DEFAULTS, 2);
		assertEquals(heir.id, ledger.offices.get(Office.LORD.id()));
		assertTrue(succession.stream().anyMatch(c -> c.office() == Office.LORD && c.after() == heir));
	}

	@Test
	void honoursNeedTheTrade() {
		Resident farmer = SocialTestKit.resident("Fay", true, AgeStage.ADULT, JobType.FARMER, -100, "brave");
		assertFalse(Succession.eligible(Office.GUARD_CAPTAIN, farmer));
		Resident elder = SocialTestKit.resident("Eli", false, AgeStage.ELDER, JobType.NONE, -900);
		assertTrue(Succession.eligible(Office.REEVE, elder));
		assertFalse(Succession.eligible(Office.STEWARD, elder));
	}

	@Test
	void smallPlacesHaveFewOffices() {
		SimpleSettlement s = SocialTestKit.town(32, Culture.TEMPERATE);
		for (int i = 0; i < 8; i++) {
			s.addResident(SocialTestKit.resident("H" + i, i % 2 == 0, AgeStage.ADULT, JobType.FARMER, -100 - i));
		}
		SocialLedger ledger = new SocialLedger();
		Officials.update(s, ledger, SocialConfig.Offices.DEFAULTS, 0);
		assertTrue(ledger.offices.isEmpty(), "a hamlet of farmers has no reeve, lord or seniors: " + ledger.offices);
	}
}
