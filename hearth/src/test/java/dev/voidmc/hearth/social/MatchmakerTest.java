package dev.voidmc.hearth.social;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.voidmc.hearth.api.AgeStage;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.social.life.Matchmaker;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** The weddings prefer sweethearts (docs/LIVING_WORLD_2.md §4.1, §4.8): of all the singles, a courting pair is wed first. */
class MatchmakerTest {
	@AfterEach
	void forget() {
		Matchmaker.sweethearts(r -> null);
	}

	@Test
	void sweetheartsMarriedFirst() {
		Resident a = SocialTestKit.resident("Ann", true, AgeStage.ADULT, JobType.FARMER, -100);
		Resident b = SocialTestKit.resident("Bob", false, AgeStage.ADULT, JobType.FARMER, -101);
		Resident c = SocialTestKit.resident("Cid", false, AgeStage.ADULT, JobType.FARMER, -400); // far older than Ann
		Resident d = SocialTestKit.resident("Dot", true, AgeStage.ADULT, JobType.FARMER, -399); // and Cid's match by age
		Map<UUID, UUID> courting = new HashMap<>();
		courting.put(a.id, c.id);
		courting.put(c.id, a.id);
		Matchmaker.sweethearts(r -> courting.get(r.id));
		for (long seed = 0; seed < 40; seed++) {
			Matchmaker.Match m = Matchmaker.find(List.of(a, b, c, d), Set.of(), false, 0, new SplittableRandom(seed));
			assertNotNull(m);
			assertEquals(Set.of(a.id, c.id), Set.of(m.a().id, m.b().id), "the sweethearts, not the pair nearest in age");
		}
	}

	@Test
	void promisedOrOneSidedIsNoCourtship() {
		Resident a = SocialTestKit.resident("Eve", true, AgeStage.ADULT, JobType.FARMER, -100);
		Resident b = SocialTestKit.resident("Fil", false, AgeStage.ADULT, JobType.FARMER, -101);
		Resident c = SocialTestKit.resident("Gus", false, AgeStage.ADULT, JobType.FARMER, -400);
		// Eve fancies Gus, Gus nobody: no courtship, so the nearest in age as before
		Matchmaker.sweethearts(r -> r == a ? c.id : null);
		Matchmaker.Match m = Matchmaker.find(List.of(a, b, c), Set.of(), false, 0, new SplittableRandom(3));
		assertEquals(Set.of(a.id, b.id), Set.of(m.a().id, m.b().id));
		// a promised sweetheart is not wed twice
		Matchmaker.sweethearts(r -> r == a ? c.id : r == c ? a.id : null);
		Matchmaker.Match n = Matchmaker.find(List.of(a, b, c), Set.of(c.id), false, 0, new SplittableRandom(3));
		assertTrue(n == null || !Set.of(n.a().id, n.b().id).contains(c.id));
	}
}
