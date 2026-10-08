package dev.voidmc.hearth.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.voidmc.hearth.economy.haggle.Temper;
import dev.voidmc.hearth.economy.price.MarketPricer;
import dev.voidmc.hearth.economy.price.PriceModifiers;
import dev.voidmc.hearth.law.LawSkills;
import dev.voidmc.hearth.law.stealth.Detection;
import dev.voidmc.hearth.standing.speech.SpeechCheck;
import dev.voidmc.hearth.standing.speech.SpeechKind;
import dev.voidmc.hearth.standing.speech.SpeechOdds;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * The Mettle slots (docs/requests/mettle.md): without a skills mod ({@link PlayerSkills#NONE}) every Hearth check has
 * exactly the odds it had before; a skills mod's numbers move them.
 */
class PlayerSkillsTest {
	/** A skills mod with fixed numbers that records what Hearth awards. */
	private static final class Fake implements PlayerSkills {
		final Map<String, Integer> levels;
		final int presence;
		final List<String> awarded = new ArrayList<>();

		Fake(Map<String, Integer> levels, int presence) {
			this.levels = levels;
			this.presence = presence;
		}

		@Override
		public int level(ServerPlayer player, String skill) {
			return this.levels.getOrDefault(skill, 0);
		}

		@Override
		public int attribute(ServerPlayer player, String attribute) {
			return PRESENCE.equals(attribute) ? this.presence : UNKNOWN_ATTRIBUTE;
		}

		@Override
		public boolean hasPerk(ServerPlayer player, Identifier perk) {
			return false;
		}

		@Override
		public Check check(ServerPlayer player, String skill, int difficulty) {
			return new Check(PlayerSkills.odds(level(player, skill), 3, difficulty), 0.5);
		}

		@Override
		public void award(ServerPlayer player, String skill, float xp, String reason) {
			this.awarded.add(skill + " " + xp + " " + reason);
		}
	}

	@AfterEach
	void reset() {
		HearthServices.setPlayerSkills(null);
		PriceModifiers.clear();
	}

	@Test
	void noneIsNeutralAndTheDefault() {
		assertSame(PlayerSkills.NONE, HearthServices.playerSkills());
		assertEquals(0, PlayerSkills.NONE.level(null, PlayerSkills.SPEECH));
		assertEquals(3, PlayerSkills.NONE.attribute(null, PlayerSkills.PRESENCE));
		assertFalse(PlayerSkills.NONE.hasPerk(null, Identifier.fromNamespaceAndPath("mettle", "speech/silver_tongue")));
		PlayerSkills.NONE.award(null, PlayerSkills.SPEECH, 10, "hearth:test");
		HearthServices.setPlayerSkills(new Fake(Map.of(), 3));
		HearthServices.setPlayerSkills(null);
		assertSame(PlayerSkills.NONE, HearthServices.playerSkills(), "null puts NONE back");
	}

	@Test
	void plainCheckOddsFollowMettlesRule() {
		assertEquals(0.5 + 0.05 * (0 + 1.5 - 10), PlayerSkills.odds(0, 3, 10), 1e-9);
		assertEquals(0.95, PlayerSkills.odds(20, 20, 1), 1e-9);
		assertEquals(0.05, PlayerSkills.odds(0, 1, 25), 1e-9);
		assertTrue(new PlayerSkills.Check(0.6, 0.59).passed());
		assertFalse(new PlayerSkills.Check(0.6, 0.6).passed());
	}

	// ---------------------------------------------------------------- speech (standing module)

	private static SpeechOdds.Inputs in(int speech, int presence) {
		return new SpeechOdds.Inputs(10, 5, 0.2, 0.3, Set.of("cheerful"), 0, Station.ARTISAN, JobType.SMITH, 0.2, 1.0, 0, speech, presence);
	}

	private static SpeechOdds.Inputs before() {
		return new SpeechOdds.Inputs(10, 5, 0.2, 0.3, Set.of("cheerful"), 0, Station.ARTISAN, JobType.SMITH, 0.2, 1.0, 0);
	}

	@Test
	void neutralSkillsLeaveSpeechOddsUnchanged() {
		for (SpeechKind kind : SpeechKind.values()) {
			SpeechOdds.Odds neutral = SpeechOdds.of(kind, in(0, 3), 0.05, 0.95);
			SpeechOdds.Odds old = SpeechOdds.of(kind, before(), 0.05, 0.95);
			assertEquals(old.chance(), neutral.chance(), 0.0, kind.name());
			assertEquals(old.factors(), neutral.factors(), kind.name());
			assertTrue(neutral.factors().stream().noneMatch(f -> f.key().equals("speech") || f.key().equals("presence")));
		}
		// the plain persuade base: nothing else moves it
		SpeechOdds.Inputs bare = new SpeechOdds.Inputs(0, 0, 0, 0, Set.of(), 0, Station.ARTISAN, JobType.SMITH, 0, 0, 0);
		assertEquals(0.40, SpeechOdds.of(SpeechKind.PERSUADE, bare, 0.05, 0.95).chance(), 1e-9);
	}

	@Test
	void skillsShiftSpeechOdds() {
		for (SpeechKind kind : SpeechKind.values()) {
			double neutral = SpeechOdds.of(kind, in(0, 3), 0.05, 0.95).chance();
			double skilled = SpeechOdds.of(kind, in(10, 8), 0.05, 0.95).chance();
			assertTrue(skilled > neutral, kind + ": " + skilled + " > " + neutral);
		}
		assertEquals(0.30, SpeechOdds.of(SpeechKind.PERSUADE, in(20, 3), 0, 1).chance() - SpeechOdds.of(SpeechKind.PERSUADE, in(0, 3), 0, 1).chance(),
			1e-9, "Speech 20 is worth 30 % to persuade");
		assertTrue(SpeechOdds.of(SpeechKind.PERSUADE, in(0, 1), 0, 1).chance() < SpeechOdds.of(SpeechKind.PERSUADE, in(0, 3), 0, 1).chance(),
			"a poor presence costs a little");
		assertTrue(SpeechCheck.xp(true, 0.5) > SpeechCheck.xp(true, 0) && SpeechCheck.xp(false, 0) > 0 && SpeechCheck.xp(false, 0) < SpeechCheck.xp(true, 0));
	}

	// ---------------------------------------------------------------- law: guards and witnesses, thieving, being seen

	@Test
	void lawSpeechNeutralUnlessSkilled() {
		for (double p : new double[]{0.05, 0.31, 0.5, 0.92}) {
			assertEquals(p, LawSkills.speech(p, 0, 3), 0.0);
		}
		assertTrue(LawSkills.speech(0.4, 10, 8) > 0.4);
		assertEquals(0.92, LawSkills.speech(0.9, 20, 20), 1e-9);
		// through the service: NONE leaves it, a skills mod moves it
		assertEquals(0.37, LawSkills.speech((ServerPlayer) null, 0.37), 0.0);
		HearthServices.setPlayerSkills(new Fake(Map.of(PlayerSkills.SPEECH, 10), 6));
		assertEquals(0.37 + 0.10 + 0.03, LawSkills.speech((ServerPlayer) null, 0.37), 1e-9);
	}

	@Test
	void thieveryAndStealth() {
		assertEquals(7, LawSkills.better(7, 0), "without Thievery the law's own practice counts");
		assertEquals(12, LawSkills.better(7, 12));
		assertEquals(20, LawSkills.better(3, 40));
		assertEquals(0, LawSkills.stealth(null));
		Fake fake = new Fake(Map.of(PlayerSkills.STEALTH, 15), 3);
		HearthServices.setPlayerSkills(fake);
		assertEquals(15, LawSkills.stealth(null));
		LawSkills.award(null, PlayerSkills.THIEVERY, LawSkills.lockXp(3), "lockpick");
		LawSkills.award(null, PlayerSkills.THIEVERY, 0, "nothing");
		assertEquals(List.of("mettle:thievery 30.0 hearth:law/lockpick"), fake.awarded);
		assertTrue(LawSkills.pocketXp(1000) == 30 && LawSkills.unseenXp(0) == 5);
	}

	@Test
	void stealthNeutralAtZeroAndHidesASneak() {
		for (boolean sneak : new boolean[]{false, true}) {
			Detection.Stance old = new Detection.Stance(7, sneak, false, true, 6, false);
			Detection.Stance zero = new Detection.Stance(7, sneak, false, true, 6, false, 0);
			assertEquals(old.bodyNoise(), zero.bodyNoise(), 0.0);
			assertEquals(Detection.sightRange(16, 1, old, Detection.Observer.ORDINARY), Detection.sightRange(16, 1, zero, Detection.Observer.ORDINARY), 0.0);
		}
		Detection.Stance plain = new Detection.Stance(7, true, false, true, 0, false, 0);
		Detection.Stance skilled = new Detection.Stance(7, true, false, true, 0, false, 20);
		assertTrue(skilled.bodyNoise() < plain.bodyNoise());
		assertEquals(0.7 * Detection.sightRange(16, 1, plain, Detection.Observer.ORDINARY), Detection.sightRange(16, 1, skilled, Detection.Observer.ORDINARY), 1e-9);
		Detection.Stance walking = new Detection.Stance(7, false, false, true, 0, false, 20);
		assertEquals(Detection.sightRange(16, 1, new Detection.Stance(7, false, false, true, 0, false), Detection.Observer.ORDINARY),
			Detection.sightRange(16, 1, walking, Detection.Observer.ORDINARY), 0.0, "stealth helps only a sneak");
	}

	// ---------------------------------------------------------------- haggling and prices

	@Test
	void haggleNeutralAtSpeechZero() {
		assertEquals(0.17, Temper.skilled(0.17, 0), 0.0);
		assertEquals(1.0, Temper.skilledPatience(0), 0.0);
		assertTrue(Temper.skilled(0.17, 10) > 0.17);
		assertEquals(0.6, Temper.skilled(0.55, 20), 1e-9);
		assertEquals(0, Temper.haggleXp(100, 100), 0.0);
		assertEquals(10, Temper.haggleXp(100, 90), 1e-6);
		assertEquals(40, Temper.haggleXp(1000, 100), 1e-6);
	}

	@Test
	void priceModifiersClampAndKeepTheSpread() {
		assertEquals(1.0, PriceModifiers.combine(1.0), 0.0);
		assertEquals(0.5, PriceModifiers.combine(0.2), 0.0);
		assertEquals(1.5, PriceModifiers.combine(3.0), 0.0);
		MarketPricer.Quote q = new MarketPricer.Quote(Goods.values()[0], 1, 10, 11, 8, 4, 44, 32);
		assertSame(q, q.adjusted(1.0, 1.0), "no modifiers: the quote as it was");
		MarketPricer.Quote cheap = q.adjusted(0.9, 1.1);
		assertEquals(9.9, cheap.buyUnit(), 1e-9);
		assertEquals(8.8, cheap.sellUnit(), 1e-9);
		assertEquals(40, cheap.lotBuy());
		assertEquals(35, cheap.lotSell());
		MarketPricer.Quote crossed = q.adjusted(0.5, 1.5);
		assertTrue(crossed.sellUnit() <= crossed.buyUnit() * 0.95 + 1e-9, "the town never pays what it charges");
	}

	@Test
	void dialogueOptionKeepsItsOldShape() {
		DialogueProvider.Option o = new DialogueProvider.Option("x:y", net.minecraft.network.chat.Component.literal("Hi"), 5);
		assertFalse(o.locked());
		assertEquals(null, o.hint());
		assertTrue(DialogueProvider.TRAINING_FIRST == 300 && DialogueProvider.TRAINING_LAST == 399);
	}
}
