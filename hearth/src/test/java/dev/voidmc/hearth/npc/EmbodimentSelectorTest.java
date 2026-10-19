package dev.voidmc.hearth.npc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.voidmc.hearth.npc.embody.EmbodimentSelector;
import dev.voidmc.hearth.npc.embody.EmbodimentSelector.Candidate;
import dev.voidmc.hearth.npc.embody.EmbodimentSelector.Plan;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EmbodimentSelectorTest {
	private static UUID id(int n) {
		return new UUID(0, n);
	}

	@Test
	void spawnsWithinRadiusOnly() {
		List<Candidate> cs = List.of(new Candidate(id(1), 50, false, true), new Candidate(id(2), 100, false, true));
		Plan plan = EmbodimentSelector.plan(cs, 48, 400, 96, 112, 16);
		assertEquals(List.of(id(1)), plan.spawn());
		assertTrue(plan.fold().isEmpty());
	}

	@Test
	void hysteresisKeepsFolkBetweenTheRadii() {
		// At 100 blocks: an existing folk stays, a record does not appear.
		List<Candidate> cs = List.of(new Candidate(id(1), 100, true, true), new Candidate(id(2), 100, false, true));
		Plan plan = EmbodimentSelector.plan(cs, 48, 400, 96, 112, 16);
		assertTrue(plan.spawn().isEmpty());
		assertTrue(plan.fold().isEmpty());
		// Beyond 112 the folk goes.
		plan = EmbodimentSelector.plan(List.of(new Candidate(id(1), 113, true, true)), 48, 400, 96, 112, 16);
		assertEquals(List.of(id(1)), plan.fold());
	}

	@Test
	void notInTickingChunksMeansNoEntity() {
		List<Candidate> cs = List.of(new Candidate(id(1), 10, false, false), new Candidate(id(2), 10, true, false));
		Plan plan = EmbodimentSelector.plan(cs, 48, 400, 96, 112, 16);
		assertTrue(plan.spawn().isEmpty());
		assertEquals(List.of(id(2)), plan.fold());
	}

	@Test
	void capTakesTheNearest() {
		List<Candidate> cs = new ArrayList<>();
		for (int i = 0; i < 100; i++) {
			cs.add(new Candidate(id(i), 90 - i * 0.5, false, true));
		}
		Plan plan = EmbodimentSelector.plan(cs, 48, 400, 96, 112, 16);
		assertEquals(48, plan.spawn().size());
		// The nearest are the highest indices.
		assertTrue(plan.spawn().contains(id(99)));
		assertTrue(!plan.spawn().contains(id(0)));
		assertEquals(id(99), plan.spawn().get(0), "nearest first");
	}

	@Test
	void serverRoomLimitsNewEntities() {
		List<Candidate> cs = new ArrayList<>();
		for (int i = 0; i < 20; i++) {
			cs.add(new Candidate(id(i), i, false, true));
		}
		Plan plan = EmbodimentSelector.plan(cs, 48, 5, 96, 112, 16);
		assertEquals(List.of(id(0), id(1), id(2), id(3), id(4)), plan.spawn());
	}

	@Test
	void overCapFoldsTheFarthestButExistingFolkHaveAHeadStart() {
		List<Candidate> cs = new ArrayList<>();
		// Two embodied at 60, a newcomer at 50: with cap 2 and 16 blocks of stickiness, the embodied stay.
		cs.add(new Candidate(id(1), 60, true, true));
		cs.add(new Candidate(id(2), 60, true, true));
		cs.add(new Candidate(id(3), 50, false, true));
		Plan plan = EmbodimentSelector.plan(cs, 2, 400, 96, 112, 16);
		assertTrue(plan.spawn().isEmpty());
		assertTrue(plan.fold().isEmpty());
		// A newcomer much nearer displaces the farthest.
		cs.set(2, new Candidate(id(3), 10, false, true));
		cs.set(1, new Candidate(id(2), 90, true, true));
		plan = EmbodimentSelector.plan(cs, 2, 400, 96, 112, 16);
		assertEquals(List.of(id(3)), plan.spawn());
		assertEquals(List.of(id(2)), plan.fold());
	}

	@Test
	void noPlayersFoldsEveryone() {
		List<Candidate> cs = List.of(new Candidate(id(1), Double.POSITIVE_INFINITY, true, true),
			new Candidate(id(2), Double.POSITIVE_INFINITY, false, true));
		Plan plan = EmbodimentSelector.plan(cs, 48, 400, 96, 112, 16);
		assertEquals(List.of(id(1)), plan.fold());
		assertTrue(plan.spawn().isEmpty());
	}

	@Test
	void aTownIsNeverEmptyForAPlayerAtItsEdge() {
		// The player at the end of a long road out of town: nobody within the spawn radius, the houses 110-150 blocks off.
		List<Candidate> cs = new ArrayList<>();
		for (int i = 0; i < 20; i++) {
			cs.add(new Candidate(id(i), 110 + i * 2, false, true));
		}
		cs.add(new Candidate(id(99), 120, false, false));
		Plan plan = EmbodimentSelector.plan(cs, 48, 400, 96, 112, 16, 6, 192);
		assertEquals(List.of(id(0), id(1), id(2), id(3), id(4), id(5)), plan.spawn(), "the six nearest that can tick appear");
		// Once out they stay, though beyond the despawn radius.
		List<Candidate> next = new ArrayList<>();
		for (int i = 0; i < 20; i++) {
			next.add(new Candidate(id(i), 110 + i * 2, i < 6, true));
		}
		plan = EmbodimentSelector.plan(next, 48, 400, 96, 112, 16, 6, 192);
		assertTrue(plan.spawn().isEmpty(), plan.toString());
		assertTrue(plan.fold().isEmpty(), plan.toString());
		// Beyond the reach nobody is made to appear.
		plan = EmbodimentSelector.plan(List.of(new Candidate(id(1), 200, false, true)), 48, 400, 96, 112, 16, 6, 192);
		assertTrue(plan.spawn().isEmpty());
	}

	@Test
	void theFarFewGoWhenTheTownFillsIn() {
		// The player walked in: six near folk now, so the far ones kept for company fold back.
		List<Candidate> cs = new ArrayList<>();
		for (int i = 0; i < 6; i++) {
			cs.add(new Candidate(id(i), 20 + i, false, true));
			cs.add(new Candidate(id(50 + i), 150, true, true));
		}
		Plan plan = EmbodimentSelector.plan(cs, 48, 400, 96, 112, 16, 6, 192);
		assertEquals(6, plan.spawn().size());
		assertEquals(6, plan.fold().size());
	}
}
