package dev.voidmc.hearth.social;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.voidmc.hearth.social.dice.Farkle;
import org.junit.jupiter.api.Test;

class FarkleTest {
	@Test
	void singles() {
		assertEquals(100, Farkle.score(1));
		assertEquals(50, Farkle.score(5));
		assertEquals(150, Farkle.score(1, 5));
		assertEquals(250, Farkle.score(1, 1, 5));
		assertEquals(0, Farkle.score());
	}

	@Test
	void ofAKindDoubles() {
		assertEquals(1000, Farkle.score(1, 1, 1));
		assertEquals(200, Farkle.score(2, 2, 2));
		assertEquals(400, Farkle.score(2, 2, 2, 2));
		assertEquals(800, Farkle.score(2, 2, 2, 2, 2));
		assertEquals(1600, Farkle.score(2, 2, 2, 2, 2, 2));
		assertEquals(4800, Farkle.score(6, 6, 6, 6, 6, 6));
		assertEquals(8000, Farkle.score(1, 1, 1, 1, 1, 1));
		// two triples score as two triples
		assertEquals(500, Farkle.score(2, 2, 2, 3, 3, 3));
	}

	@Test
	void runs() {
		assertEquals(500, Farkle.score(1, 2, 3, 4, 5));
		assertEquals(750, Farkle.score(2, 3, 4, 5, 6));
		assertEquals(1500, Farkle.score(6, 5, 4, 3, 2, 1));
		// a run plus a spare scoring die
		assertEquals(600, Farkle.score(1, 2, 3, 4, 5, 1));
		assertEquals(800, Farkle.score(2, 3, 4, 5, 6, 5));
	}

	@Test
	void everyPickedDieMustScore() {
		assertEquals(Farkle.INVALID, Farkle.score(2));
		assertEquals(Farkle.INVALID, Farkle.score(1, 2));
		assertEquals(Farkle.INVALID, Farkle.score(3, 3, 4));
		assertEquals(Farkle.INVALID, Farkle.score(7));
		assertEquals(1050, Farkle.score(1, 1, 1, 5));
	}

	@Test
	void bestScoreAndBusts() {
		assertEquals(0, Farkle.bestScore(2, 3, 4, 6, 6, 2));
		assertFalse(Farkle.scores(2, 3, 4, 6, 6, 2));
		assertEquals(100, Farkle.bestScore(1, 2, 2, 3, 4, 6));
		assertEquals(1500, Farkle.bestScore(1, 2, 3, 4, 5, 6));
		assertTrue(Farkle.scores(5));
		assertFalse(Farkle.scores(3));
	}

	@Test
	void scoringMaskMarksUsefulDice() {
		assertEquals(0b111111, Farkle.scoringMask(new int[] {1, 2, 3, 4, 5, 6}));
		assertEquals(0, Farkle.scoringMask(new int[] {2, 2, 3, 4, 6, 6}));
		// the 1 and the three 4s, not the 2 or the 6
		assertEquals(0b011101, Farkle.scoringMask(new int[] {1, 2, 4, 4, 4, 6}));
	}

	@Test
	void ofAKindValues() {
		assertEquals(1000, Farkle.ofAKind(1, 3));
		assertEquals(600, Farkle.ofAKind(6, 3));
		assertEquals(2400, Farkle.ofAKind(6, 5));
	}
}
