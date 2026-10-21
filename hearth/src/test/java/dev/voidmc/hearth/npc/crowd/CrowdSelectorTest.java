package dev.voidmc.hearth.npc.crowd;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.voidmc.hearth.api.StandingService.Title;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Who reacts to whom: caps, cooldowns, salience order (docs/LIVING_WORLD_2.md §5.3, §5.8). */
class CrowdSelectorTest {
	private static final long COOLDOWN = 1200;

	private static List<CrowdSelector.Candidate> folk(int n, Role role) {
		List<CrowdSelector.Candidate> out = new ArrayList<>();
		for (int i = 0; i < n; i++) {
			out.add(new CrowdSelector.Candidate(new UUID(7, i + role.ordinal() * 1000L), role, 1 + i * 0.5));
		}
		return out;
	}

	private static CrowdSelector.PlayerInput player(UUID id, PlayerFacts f, List<CrowdSelector.Candidate> near) {
		return new CrowdSelector.PlayerInput(id, Stimuli.of(f), near);
	}

	@Test
	void atMostTwelveReactToOnePlayer() {
		UUID p = new UUID(1, 1);
		List<CrowdSelector.Assignment> a = CrowdSelector.select(List.of(player(p, PlayerFacts.PLAIN.withCombat(5), folk(24, Role.FOLK))), Map.of(),
			new CrowdSelector.Cooldowns(), 0, COOLDOWN, 12, true);
		assertEquals(12, a.size());
		assertTrue(a.stream().allMatch(x -> x.reaction() == Reaction.GAWK && x.stimulus() == Stimulus.FIGHT));
		// the nearest are taken first, and each has its place in the ring
		for (int i = 0; i < a.size(); i++) {
			assertEquals(i, a.get(i).rank());
		}
		// the cap shrinks with the server's load
		assertEquals(3, CrowdSelector.select(List.of(player(p, PlayerFacts.PLAIN.withCombat(5), folk(24, Role.FOLK))), Map.of(),
			new CrowdSelector.Cooldowns(), 0, COOLDOWN, 3, true).size());
	}

	@Test
	void oneReactionAFolkAndOneRemarkAStimulus() {
		UUID p = new UUID(1, 1);
		List<CrowdSelector.Candidate> near = new ArrayList<>(folk(3, Role.GUARD));
		near.addAll(folk(5, Role.FOLK));
		List<CrowdSelector.Assignment> a = CrowdSelector.select(List.of(player(p, PlayerFacts.PLAIN.withWeapon(10).withArmour(20), near)), Map.of(),
			new CrowdSelector.Cooldowns(), 0, COOLDOWN, 12, true);
		Set<UUID> seen = new HashSet<>();
		for (CrowdSelector.Assignment x : a) {
			assertTrue(seen.add(x.folk()), "one reaction a folk");
		}
		// one guard remarks on the blade, one on the armour; the rest of the folk step back
		assertEquals(1, a.stream().filter(x -> x.stimulus() == Stimulus.BLADE && x.reaction() == Reaction.REMARK).count());
		assertEquals(1, a.stream().filter(x -> x.stimulus() == Stimulus.ARMOUR).count());
		assertEquals(5, a.stream().filter(x -> x.reaction() == Reaction.SCATTER).count());
	}

	@Test
	void cooldownsKeepFolkFromReactingAgainButRefreshKeepsThemGoing() {
		UUID p = new UUID(1, 1);
		List<CrowdSelector.Candidate> near = folk(4, Role.FOLK);
		CrowdSelector.Cooldowns cd = new CrowdSelector.Cooldowns();
		PlayerFacts blade = PlayerFacts.PLAIN.withWeapon(10);
		assertEquals(4, CrowdSelector.select(List.of(player(p, blade, near)), Map.of(), cd, 0, COOLDOWN, 12, true).size());
		// the scatter is over (nothing ongoing): nobody reacts to the same blade again inside the cooldown...
		assertTrue(CrowdSelector.select(List.of(player(p, blade, near)), Map.of(), cd, 600, COOLDOWN, 12, true).isEmpty());
		// ...and after it, they do
		assertEquals(4, CrowdSelector.select(List.of(player(p, blade, near)), Map.of(), cd, COOLDOWN + 1, COOLDOWN, 12, true).size());
		// a ring round a fight that goes on is refreshed every pass, cooldown or not
		CrowdSelector.Cooldowns cd2 = new CrowdSelector.Cooldowns();
		PlayerFacts fight = PlayerFacts.PLAIN.withCombat(1);
		List<CrowdSelector.Assignment> first = CrowdSelector.select(List.of(player(p, fight, near)), Map.of(), cd2, 0, COOLDOWN, 12, true);
		Map<UUID, CrowdSelector.Ongoing> ongoing = new HashMap<>();
		for (CrowdSelector.Assignment x : first) {
			ongoing.put(x.folk(), new CrowdSelector.Ongoing(p, x.stimulus(), x.reaction()));
		}
		List<CrowdSelector.Assignment> again = CrowdSelector.select(List.of(player(p, fight, near)), ongoing, cd2, 10, COOLDOWN, 12, true);
		assertEquals(4, again.size());
		assertTrue(again.stream().allMatch(CrowdSelector.Assignment::refresh));
		// a slow server starts nothing new, but what is going on goes on
		assertTrue(CrowdSelector.select(List.of(player(p, fight, folk(4, Role.ELDER))), Map.of(), new CrowdSelector.Cooldowns(), 0, COOLDOWN, 12, false).isEmpty());
		assertEquals(4, CrowdSelector.select(List.of(player(p, fight, near)), ongoing, cd2, 20, COOLDOWN, 12, false).size());
	}

	@Test
	void theMostSalientPlayerFirst() {
		UUID hero = new UUID(1, 1);
		UUID outlaw = new UUID(1, 2);
		List<CrowdSelector.Candidate> near = folk(6, Role.FOLK);
		// the hero came first in the list, but the outlaw draws the crowd
		List<CrowdSelector.Assignment> a = CrowdSelector.select(List.of(player(hero, PlayerFacts.PLAIN.withTitle(Title.HERO), near),
			player(outlaw, PlayerFacts.PLAIN.withOutlaw(true), near)), Map.of(), new CrowdSelector.Cooldowns(), 0, COOLDOWN, 12, true);
		assertEquals(6, a.size());
		assertTrue(a.stream().allMatch(x -> x.player().equals(outlaw) && x.reaction() == Reaction.SHUN), "an outlaw among honest players: the outlaw is shunned");
		// two heroes: folk near both are shared out between them, no folk twice
		UUID hero2 = new UUID(1, 3);
		List<CrowdSelector.Assignment> b = CrowdSelector.select(List.of(player(hero, PlayerFacts.PLAIN.withTitle(Title.HERO), folk(10, Role.FOLK)),
			player(hero2, PlayerFacts.PLAIN.withTitle(Title.HERO), folk(10, Role.FOLK))), Map.of(), new CrowdSelector.Cooldowns(), 0, COOLDOWN, 12, true);
		assertEquals(6, b.stream().filter(x -> x.player().equals(hero)).count(), "six hail a hero at most");
		assertEquals(4, b.stream().filter(x -> x.player().equals(hero2)).count(), "the rest hail the other");
	}

	@Test
	void aBusierReactionIsNotInterrupted() {
		UUID p = new UUID(1, 1);
		List<CrowdSelector.Candidate> near = folk(2, Role.FOLK);
		Map<UUID, CrowdSelector.Ongoing> ongoing = Map.of(near.get(0).folk(), new CrowdSelector.Ongoing(new UUID(9, 9), Stimulus.OUTLAW, Reaction.SHUN));
		List<CrowdSelector.Assignment> a = CrowdSelector.select(List.of(player(p, PlayerFacts.PLAIN.withTitle(Title.HERO), near)), ongoing,
			new CrowdSelector.Cooldowns(), 0, COOLDOWN, 12, true);
		assertEquals(1, a.size());
		assertFalse(a.get(0).folk().equals(near.get(0).folk()), "running from an outlaw, nobody stops to wave");
	}

	@Test
	void cooldownsPrune() {
		CrowdSelector.Cooldowns cd = new CrowdSelector.Cooldowns();
		cd.mark(new UUID(0, 1), Stimulus.BLADE, 100);
		cd.mark(new UUID(0, 2), Stimulus.HERO, 1000);
		cd.prune(500);
		assertEquals(1, cd.size());
		assertTrue(cd.ready(new UUID(0, 1), Stimulus.BLADE, 500));
		assertFalse(cd.ready(new UUID(0, 2), Stimulus.HERO, 500));
	}

	/**
	 * The budget (§5.4: ≤ 20 µs a player a pass): stimuli and selection for 50 players with 24 folk each, timed. The
	 * spatial query is the npc module's and is not in here; the director's own timer covers it in game.
	 */
	@Test
	void selectionIsCheap() {
		List<CrowdSelector.PlayerInput> players = new ArrayList<>();
		for (int i = 0; i < 50; i++) {
			PlayerFacts f = switch (i % 5) {
				case 0 -> PlayerFacts.PLAIN.withWeapon(10);
				case 1 -> PlayerFacts.PLAIN.withCombat(3);
				case 2 -> PlayerFacts.PLAIN.withTitle(Title.HERO);
				case 3 -> PlayerFacts.PLAIN.withArmour(20).withTitle(Title.FRIEND);
				default -> PlayerFacts.PLAIN.withOutlaw(true);
			};
			List<CrowdSelector.Candidate> near = new ArrayList<>();
			for (int k = 0; k < 24; k++) {
				near.add(new CrowdSelector.Candidate(new UUID(i, k), Role.VALUES[k % Role.VALUES.length], 1 + k * 0.6));
			}
			players.add(new CrowdSelector.PlayerInput(new UUID(99, i), Stimuli.of(f), near));
		}
		CrowdSelector.Cooldowns cd = new CrowdSelector.Cooldowns();
		for (int w = 0; w < 2000; w++) {
			CrowdSelector.select(players, Map.of(), cd, w * 10L, 1, 12, true);
		}
		int rounds = 2000;
		long t0 = System.nanoTime();
		for (int r = 0; r < rounds; r++) {
			for (CrowdSelector.PlayerInput p : players) {
				Stimuli.of(PlayerFacts.PLAIN.withWeapon(r));
			}
			CrowdSelector.select(players, Map.of(), cd, 100_000L + r * 10L, 1, 12, true);
		}
		double usPerPlayer = (System.nanoTime() - t0) / 1000.0 / rounds / players.size();
		System.out.printf("crowd selection: %.2f us a player a pass (50 players, 24 folk each)%n", usPerPlayer);
		assertTrue(usPerPlayer < 20, "over budget: " + usPerPlayer + " us");
	}
}
