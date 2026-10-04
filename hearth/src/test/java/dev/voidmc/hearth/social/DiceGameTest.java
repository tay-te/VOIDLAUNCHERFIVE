package dev.voidmc.hearth.social;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.voidmc.hearth.social.dice.DiceAi;
import dev.voidmc.hearth.social.dice.DiceGame;
import dev.voidmc.hearth.social.dice.DicePersonality;
import dev.voidmc.hearth.social.dice.Farkle;
import java.util.List;
import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;

class DiceGameTest {
	@Test
	void bustOddsMatchTheRules() {
		// 1s, 5s, triples and runs (three pairs do not score here, so six dice bust 1440 times in 46656)
		assertEquals(2.0 / 3.0, DiceAi.bustChance(1), 1e-9);
		assertEquals(16.0 / 36.0, DiceAi.bustChance(2), 1e-9);
		assertEquals(60.0 / 216.0, DiceAi.bustChance(3), 1e-9);
		assertEquals(1440.0 / 46656.0, DiceAi.bustChance(6), 1e-9);
	}

	@Test
	void aiAlwaysPicksAValidScoringSet() {
		SplittableRandom r = new SplittableRandom(7);
		for (int i = 0; i < 20_000; i++) {
			int n = 1 + r.nextInt(6);
			int[] free = new int[n];
			for (int j = 0; j < n; j++) {
				free[j] = 1 + r.nextInt(6);
			}
			if (!Farkle.scores(free)) {
				continue;
			}
			DicePersonality p = DicePersonality.values()[r.nextInt(DicePersonality.values().length)];
			DiceAi.Decision d = DiceAi.decide(free, 6 - n, r.nextInt(1000), r.nextInt(2000), r.nextInt(2000), 2000, p, r);
			assertTrue(d.keep() != 0);
			assertTrue(Farkle.scoreMask(free, d.keep()) > 0, "invalid keep");
		}
	}

	@Test
	void aiBanksWhenThatWins() {
		DiceAi.Decision d = DiceAi.decide(new int[] {1, 2, 3, 3, 6, 6}, 0, 0, 1950, 0, 2000, DicePersonality.RECKLESS, new SplittableRandom(1));
		assertTrue(d.bank());
	}

	@Test
	void aiRollsSixDiceAndBanksOneLeftOnABigTurn() {
		DiceAi.Decision roll = DiceAi.decide(new int[] {1, 2, 3, 4, 6, 6}, 0, 0, 0, 0, 2000, DicePersonality.CAREFUL, new SplittableRandom(1));
		assertFalse(roll.bank(), "one die set aside, five to roll: press on");
		DiceAi.Decision bank = DiceAi.decide(new int[] {5, 3}, 4, 600, 0, 0, 2000, DicePersonality.BOLD, new SplittableRandom(1));
		assertTrue(bank.bank(), "600 on the turn and one die left: bank");
	}

	@Test
	void careFolkBankSmallerTurnsThanRecklessFolk() {
		double careful = averageBank(DicePersonality.CAREFUL);
		double reckless = averageBank(DicePersonality.RECKLESS);
		assertTrue(careful < reckless, careful + " vs " + reckless);
	}

	/** Average points a folk banks per banked turn, playing against itself. */
	private static double averageBank(DicePersonality p) {
		long total = 0;
		int banks = 0;
		for (int seed = 0; seed < 200; seed++) {
			DiceGame g = new DiceGame(new SplittableRandom(seed), p, 2000, 0, 0);
			g.begin();
			int guard = 0;
			while (g.phase() != DiceGame.Phase.OVER && guard++ < 5000) {
				switch (g.phase()) {
					case PLAYER_ROLLED -> playerMove(g);
					case PLAYER_BUST -> g.passDice();
					case FOLK_TURN -> g.folkStep();
					default -> {
					}
				}
				for (DiceGame.Event e : g.drainEvents()) {
					if (e.kind() == DiceGame.Event.Kind.BANKED && e.seat() == DiceGame.FOLK) {
						total += e.value();
						banks++;
					}
				}
			}
		}
		return (double) total / Math.max(1, banks);
	}

	/** A simple player: keeps every scoring die it can, banks at 350 or with two dice or fewer. */
	private static void playerMove(DiceGame g) {
		int scoring = g.scoringMask();
		int[] dice = g.dice();
		int pick = 0;
		for (int i = 0; i < Farkle.DICE; i++) {
			if ((scoring >> i & 1) != 0) {
				pick |= 1 << i;
			}
		}
		// every useful die may not make a valid set together; fall back to singles
		if (Farkle.scoreMask(dice, pick) <= 0) {
			pick = 0;
			for (int i = 0; i < Farkle.DICE; i++) {
				if (!g.kept(i) && (dice[i] == 1 || dice[i] == 5)) {
					pick |= 1 << i;
					break;
				}
			}
		}
		if (pick == 0 || Farkle.scoreMask(dice, pick) <= 0) {
			// a triple without singles: pick the triple
			for (int sub = 1; sub < 64; sub++) {
				if ((sub & g.keptMask()) == 0 && Farkle.scoreMask(dice, sub) > 0) {
					pick = sub;
					break;
				}
			}
		}
		for (int i = 0; i < Farkle.DICE; i++) {
			if ((pick >> i & 1) != 0) {
				g.toggle(i);
			}
		}
		int left = Farkle.DICE - Integer.bitCount(g.keptMask() | pick);
		if (g.turnPoints() + g.selectionScore() >= 350 || left > 0 && left <= 2) {
			assertTrue(g.bank());
		} else {
			assertTrue(g.rollOn());
		}
	}

	@Test
	void gamesFinishWithAWinnerAtTheTarget() {
		for (int seed = 0; seed < 300; seed++) {
			DiceGame g = new DiceGame(new SplittableRandom(seed * 31L), DicePersonality.SHREWD, 1500, 0, 0);
			assertEquals(DiceGame.Phase.STAKE, g.phase());
			g.begin();
			int guard = 0;
			while (g.phase() != DiceGame.Phase.OVER && guard++ < 10_000) {
				switch (g.phase()) {
					case PLAYER_ROLLED -> playerMove(g);
					case PLAYER_BUST -> g.passDice();
					case FOLK_TURN -> g.folkStep();
					default -> {
					}
				}
				g.drainEvents();
			}
			assertEquals(DiceGame.Phase.OVER, g.phase());
			int w = g.winner();
			assertTrue(w == DiceGame.PLAYER || w == DiceGame.FOLK);
			assertTrue(g.score(w) >= 1500);
			assertTrue(g.score(1 - w) < 1500);
		}
	}

	@Test
	void hotDiceRollAllSixAgain() {
		// find a seed whose opening roll is a full run, then set it all aside
		for (int seed = 0; seed < 200_000; seed++) {
			DiceGame g = new DiceGame(new SplittableRandom(seed), DicePersonality.SHREWD, 2000, 0, 0);
			g.begin();
			if (Farkle.score(g.dice()) != 1500) {
				continue;
			}
			for (int i = 0; i < 6; i++) {
				g.toggle(i);
			}
			assertEquals(1500, g.selectionScore());
			assertTrue(g.rollOn());
			List<DiceGame.Event> events = g.drainEvents();
			assertTrue(events.stream().anyMatch(e -> e.kind() == DiceGame.Event.Kind.HOT_DICE));
			assertEquals(0, g.keptMask(), "all six back in hand");
			assertEquals(0b111111, g.rolledMask());
			return;
		}
		throw new AssertionError("no opening run in 200000 seeds");
	}

	@Test
	void invalidPicksCannotBeRolledOrBanked() {
		for (int seed = 0; seed < 1000; seed++) {
			DiceGame g = new DiceGame(new SplittableRandom(seed), DicePersonality.SHREWD, 2000, 0, 0);
			g.begin();
			if (g.phase() != DiceGame.Phase.PLAYER_ROLLED) {
				continue;
			}
			int[] d = g.dice();
			for (int i = 0; i < 6; i++) {
				if (d[i] != 1 && d[i] != 5 && Farkle.scoreMask(d, 1 << i) < 0) {
					g.toggle(i);
					assertFalse(g.canRollOn());
					assertFalse(g.rollOn());
					assertFalse(g.bank());
					return;
				}
			}
		}
	}

	@Test
	void loadedDiceAreCaughtSoonerOrLater() {
		int caught = 0;
		for (int seed = 0; seed < 200; seed++) {
			DiceGame g = new DiceGame(new SplittableRandom(seed), DicePersonality.SOUR, 4000, 0.45, 0.03);
			g.setLoaded(true);
			g.begin();
			int guard = 0;
			while (g.phase() != DiceGame.Phase.OVER && guard++ < 10_000) {
				switch (g.phase()) {
					case PLAYER_ROLLED -> playerMove(g);
					case PLAYER_BUST -> g.passDice();
					case FOLK_TURN -> g.folkStep();
					default -> {
					}
				}
				g.drainEvents();
			}
			if (g.cheatCaught()) {
				caught++;
				assertEquals(DiceGame.FOLK, g.winner());
			}
		}
		assertTrue(caught > 120, "a sour folk catches most cheats over a long game: " + caught);
	}

	@Test
	void loadedDiceRollMoreOnesAndFives() {
		SplittableRandom r = new SplittableRandom(3);
		int honest = 0;
		int loaded = 0;
		for (int i = 0; i < 4000; i++) {
			DiceGame h = new DiceGame(new SplittableRandom(r.nextLong()), DicePersonality.BOLD, 2000, 0.45, -1);
			h.begin();
			DiceGame l = new DiceGame(new SplittableRandom(r.nextLong()), DicePersonality.BOLD, 2000, 0.45, -1);
			l.setLoaded(true);
			l.begin();
			for (int f : h.dice()) {
				honest += f == 1 || f == 5 ? 1 : 0;
			}
			for (int f : l.dice()) {
				loaded += f == 1 || f == 5 ? 1 : 0;
			}
		}
		assertTrue(loaded > honest * 1.2, honest + " vs " + loaded);
	}

	@Test
	void forfeitHandsTheGameToTheFolk() {
		DiceGame g = new DiceGame(new SplittableRandom(1), DicePersonality.SHREWD, 2000, 0, 0);
		g.begin();
		g.forfeit();
		assertEquals(DiceGame.Phase.OVER, g.phase());
		assertEquals(DiceGame.FOLK, g.winner());
	}
}
