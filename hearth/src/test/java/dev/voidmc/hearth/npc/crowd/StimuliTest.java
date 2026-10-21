package dev.voidmc.hearth.npc.crowd;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.voidmc.hearth.api.StandingService.Title;
import java.util.List;
import org.junit.jupiter.api.Test;

/** What the crowd notices about a player (docs/LIVING_WORLD_2.md §5.3, §5.8). */
class StimuliTest {
	private static List<Stimulus> of(PlayerFacts f) {
		return Stimuli.of(f).stream().map(Stimuli.Sensed::stimulus).toList();
	}

	@Test
	void aPlainStrangerAtNoonIsNothingToLookAt() {
		assertTrue(Stimuli.of(PlayerFacts.PLAIN).isEmpty());
		assertEquals(0, Stimuli.top(Stimuli.of(PlayerFacts.PLAIN)));
	}

	@Test
	void aDrawnBladeIsSeenAndAFightOutranksIt() {
		assertEquals(List.of(Stimulus.BLADE), of(PlayerFacts.PLAIN.withWeapon(1)));
		// fighting with the blade out: a fight, not a blade
		assertEquals(List.of(Stimulus.FIGHT), of(PlayerFacts.PLAIN.withWeapon(400).withCombat(20)));
		// a fight five seconds old is still a fight, one older is over
		assertTrue(of(PlayerFacts.PLAIN.withCombat(Stimuli.FIGHT_TICKS)).contains(Stimulus.FIGHT));
		assertFalse(of(PlayerFacts.PLAIN.withCombat(Stimuli.FIGHT_TICKS + 1)).contains(Stimulus.FIGHT));
		// held long, or by someone fearsome, a blade draws more notice, never as much as a fight
		int brief = Stimuli.of(PlayerFacts.PLAIN.withWeapon(1)).get(0).salience();
		int long_ = Stimuli.of(PlayerFacts.PLAIN.withWeapon(Stimuli.DRAWN_TOO_LONG).withMenace(1)).get(0).salience();
		assertTrue(long_ > brief);
		assertTrue(long_ < Stimulus.FIGHT.salience());
	}

	@Test
	void titlesAreGreetedShunnedOrMutteredAt() {
		assertEquals(List.of(Stimulus.HERO), of(PlayerFacts.PLAIN.withTitle(Title.HERO)));
		assertEquals(List.of(Stimulus.DISTRUSTED), of(PlayerFacts.PLAIN.withTitle(Title.DISTRUSTED)));
		assertEquals(List.of(Stimulus.FRIEND), of(PlayerFacts.PLAIN.withTitle(Title.FRIEND)));
		assertEquals(List.of(Stimulus.FRIEND), of(PlayerFacts.PLAIN.withTitle(Title.BURGHER)));
		assertEquals(List.of(Stimulus.OUTLAW), of(PlayerFacts.PLAIN.withTitle(Title.OUTLAW)));
		// the law's word makes an outlaw whatever the title says, and nobody hails an outlaw
		assertEquals(List.of(Stimulus.OUTLAW), of(PlayerFacts.PLAIN.withTitle(Title.HERO).withOutlaw(true)));
		assertTrue(of(PlayerFacts.PLAIN.withTitle(Title.STRANGER)).isEmpty());
	}

	@Test
	void armourBloodAndProwling() {
		assertTrue(of(PlayerFacts.PLAIN.withArmour(7)).isEmpty(), "leather clothes are not armour");
		assertTrue(of(PlayerFacts.PLAIN.withArmour(14)).isEmpty(), "chain is not remarked on");
		assertEquals(List.of(Stimulus.ARMOUR), of(PlayerFacts.PLAIN.withArmour(20)));
		assertEquals(List.of(Stimulus.BLOODIED), of(PlayerFacts.PLAIN.withHealth(0.4F)));
		assertTrue(of(PlayerFacts.PLAIN.withHealth(0.7F)).isEmpty(), "a scratch long healed");
		assertTrue(of(PlayerFacts.PLAIN.withHealth(0.7F).withCombat(300)).contains(Stimulus.BLOODIED), "fresh from a fight");
		assertEquals(List.of(Stimulus.PROWL), of(PlayerFacts.PLAIN.withSneaking(true, false)));
		assertEquals(List.of(Stimulus.PROWL), of(PlayerFacts.PLAIN.withSneaking(false, true)));
	}

	@Test
	void mostSalientFirst() {
		PlayerFacts all = PlayerFacts.PLAIN.withOutlaw(true).withBody(true).withWeapon(10).withArmour(20).withHealth(0.3F).withSneaking(true, true);
		List<Stimulus> got = of(all);
		assertEquals(List.of(Stimulus.OUTLAW, Stimulus.BODY, Stimulus.BLADE, Stimulus.BLOODIED, Stimulus.ARMOUR, Stimulus.PROWL), got);
		List<Stimuli.Sensed> sensed = Stimuli.of(all);
		for (int i = 1; i < sensed.size(); i++) {
			assertTrue(sensed.get(i - 1).salience() >= sensed.get(i).salience());
		}
		assertEquals(Stimulus.OUTLAW.salience(), Stimuli.top(sensed));
	}

	@Test
	void reactionsByRole() {
		assertEquals(Reaction.SCATTER, Reaction.of(Stimulus.BLADE, Role.MOTHER, 5));
		assertEquals(null, Reaction.of(Stimulus.BLADE, Role.FOLK, 12), "too far to step back from");
		assertEquals(Reaction.REMARK, Reaction.of(Stimulus.BLADE, Role.GUARD, 14), "a guard comes over");
		assertEquals(null, Reaction.of(Stimulus.OUTLAW, Role.GUARD, 4), "guards attack outlaws themselves");
		assertEquals(Reaction.SHUN, Reaction.of(Stimulus.OUTLAW, Role.CHILD, 10));
		assertEquals(Reaction.GAWK, Reaction.of(Stimulus.FIGHT, Role.ELDER, 10));
		assertEquals(Reaction.HAIL, Reaction.of(Stimulus.HERO, Role.CHILD, 10));
		assertEquals(Reaction.REMARK, Reaction.of(Stimulus.ARMOUR, Role.MERCHANT, 6));
		assertEquals(null, Reaction.of(Stimulus.ARMOUR, Role.FOLK, 6), "only guards and merchants remark on armour");
		assertEquals(Reaction.MUTTER, Reaction.of(Stimulus.DISTRUSTED, Role.FOLK, 6));
		// the priorities sit in the design's 200–239 band, a scatter above a gawk above a hail
		for (Reaction r : Reaction.VALUES) {
			assertTrue(r.priority() >= 200 && r.priority() < 240, r.name());
		}
		assertTrue(Reaction.SCATTER.priority() > Reaction.GAWK.priority());
		assertTrue(Reaction.GAWK.priority() > Reaction.HAIL.priority());
		assertTrue(Reaction.SHUN.priority() > Reaction.GAWK.priority());
	}
}
