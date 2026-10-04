package dev.voidmc.hearth.law.justice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.voidmc.hearth.api.LawService.Crime;
import dev.voidmc.hearth.api.LawService.Offence;
import dev.voidmc.hearth.law.LawConfig;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

class JusticeRulesTest {
	private static final UUID THIEF = UUID.fromString("00000000-0000-0000-0000-00000000beef");
	private static final LawConfig.Fines FINES = LawConfig.Fines.DEFAULTS;

	private static Crime crime(Offence o, long value) {
		return new Crime(o, THIEF, 1, BlockPos.ZERO, null, value, true, 0);
	}

	@Test
	void finesFollowTheOffenceAndWhatWasTaken() {
		assertEquals(FINES.theft() + Math.round(40 * FINES.valueMultiplier()), Offences.fine(crime(Offence.THEFT, 40), FINES));
		assertEquals(FINES.trespass(), Offences.fine(crime(Offence.TRESPASS, 999), FINES));
		assertEquals(FINES.murder(), Offences.fine(crime(Offence.MURDER, 0), FINES));
		// vandalism is priced by the detector (blocks × per block); a bare report is five blocks
		assertEquals(12, Offences.fine(crime(Offence.VANDALISM, 12), FINES));
		assertEquals(FINES.vandalismPerBlock() * 5, Offences.fine(crime(Offence.VANDALISM, 0), FINES));
		LawConfig.Fines half = new LawConfig.Fines(20, 2.0, 30, 25, 15, 30, 3, 60, 1500, 300, 200, 80, 0.5);
		assertEquals(Math.round(FINES.murder() * 0.5), Offences.fine(crime(Offence.MURDER, 0), half));
	}

	@Test
	void severityOrdersTheOffences() {
		assertTrue(Offences.severity(Offence.MURDER) > Offences.severity(Offence.ROBBERY));
		assertTrue(Offences.severity(Offence.ROBBERY) > Offences.severity(Offence.ASSAULT));
		assertTrue(Offences.severity(Offence.ASSAULT) > Offences.severity(Offence.THEFT));
		assertTrue(Offences.severity(Offence.THEFT) > Offences.severity(Offence.TRESPASS));
		assertTrue(Offences.standingWeight(crime(Offence.MURDER, 0)) <= -100);
		assertTrue(Offences.standingWeight(crime(Offence.THEFT, 500)) < Offences.standingWeight(crime(Offence.THEFT, 1)));
		assertEquals("law:theft", Offences.deedKind(Offence.THEFT));
	}

	@Test
	void sentencesAreClampedAndMurderGetsTheLongest() {
		LawConfig.Justice j = LawConfig.Justice.DEFAULTS;
		assertEquals(j.jailMinMinutes(), Offences.jailMinutes(1, false, j));
		assertEquals(5, Offences.jailMinutes(45, false, j));
		assertEquals(j.jailMaxMinutes(), Offences.jailMinutes(100_000, false, j));
		assertEquals(j.jailMaxMinutes(), Offences.jailMinutes(1, true, j));
	}

	@Test
	void thePilloryIsForLesserCrimes() {
		LawConfig.Justice j = LawConfig.Justice.DEFAULTS;
		Warrant small = new Warrant(THIEF, 0);
		small.charge(Offence.THEFT, 30, 0);
		assertTrue(Offences.pilloryEligible(small, j));
		Warrant violent = new Warrant(THIEF, 0);
		violent.charge(Offence.ASSAULT, 30, 0);
		assertFalse(Offences.pilloryEligible(violent, j));
		Warrant big = new Warrant(THIEF, 0);
		big.charge(Offence.THEFT, j.pilloryMaxBounty() + 1, 0);
		assertFalse(Offences.pilloryEligible(big, j));
	}

	@Test
	void warrantsGrowShrinkAndClear() {
		Warrant w = new Warrant(THIEF, 3);
		assertFalse(w.active());
		w.charge(Offence.THEFT, 40, 3);
		w.charge(Offence.ASSAULT, 60, 4);
		assertTrue(w.active());
		assertEquals(100, w.bounty());
		assertEquals(Offence.ASSAULT, w.worst());
		assertEquals(4, w.lastDay());
		w.setPersuadeTried(true);
		w.charge(Offence.TRESPASS, 15, 5);
		assertFalse(w.persuadeTried(), "a fresh crime is a fresh chance to talk");
		w.grow(0.1, 5, 5);
		assertEquals(127, w.bounty());
		w.reduce(27, 6);
		assertEquals(100, w.bounty());
		w.reduce(500, 6);
		assertFalse(w.active());
		assertTrue(w.offences().isEmpty());
	}

	@Test
	void murderMarksTheWarrantAndWordFromAfarIsAbsorbed() {
		Warrant home = new Warrant(THIEF, 1);
		home.charge(Offence.MURDER, 1500, 1);
		assertTrue(home.murder());
		home.setOutlaw(true);
		Warrant there = new Warrant(THIEF, 3);
		there.absorb(home, 750, 3);
		assertEquals(750, there.bounty());
		assertTrue(there.murder());
		assertTrue(there.outlaw());
		assertEquals(Offence.MURDER, there.worst());
	}

	@Test
	void speechChecksMoveTheRightWayAndStayInBounds() {
		SpeechCheck.Listener plain = SpeechCheck.Listener.PLAIN;
		assertTrue(SpeechCheck.persuade(80, 50, 0.8F, 1, plain) > SpeechCheck.persuade(-80, -50, -0.8F, 1, plain));
		assertTrue(SpeechCheck.persuade(0, 0, 0, 1, plain) > SpeechCheck.persuade(0, 0, 0, 6, plain));
		SpeechCheck.Listener greedy = new SpeechCheck.Listener(false, false, true, false, false, false);
		SpeechCheck.Listener generous = new SpeechCheck.Listener(false, false, false, true, false, false);
		assertTrue(SpeechCheck.bribe(50, 100, 3, greedy) > SpeechCheck.bribe(50, 100, 3, generous));
		assertTrue(SpeechCheck.bribe(100, 100, 3, plain) > SpeechCheck.bribe(10, 100, 3, plain));
		SpeechCheck.Listener brave = new SpeechCheck.Listener(true, false, false, false, false, false);
		SpeechCheck.Listener cautious = new SpeechCheck.Listener(false, true, false, false, false, false);
		assertTrue(SpeechCheck.threaten(0.8F, cautious) > SpeechCheck.threaten(0.8F, brave));
		for (int st = -100; st <= 100; st += 50) {
			double p = SpeechCheck.persuade(st, st, st / 100.0F, 10, plain);
			assertTrue(p >= 0.05 && p <= 0.92);
		}
		assertEquals(62, SpeechCheck.percent(0.62));
		assertEquals(27, SpeechCheck.guardBribe(45));
	}

	@Test
	void skillGrowsWithPractice() {
		assertEquals(0, PlayerLaw.skill(0));
		assertEquals(2, PlayerLaw.skill(1));
		assertEquals(20, PlayerLaw.skill(100));
		assertEquals(20, PlayerLaw.skill(10_000));
	}

	@Test
	void anEscapeOwesTheUnservedPart() {
		PlayerLaw.Sentence s = new PlayerLaw.Sentence(1, BlockPos.ZERO, 1200, false, BlockPos.ZERO, 100, 4800);
		assertEquals(25, s.unserved());
		assertEquals(0, s.served(5000).ticksLeft());
	}
}
