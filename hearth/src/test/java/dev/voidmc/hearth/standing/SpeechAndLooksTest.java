package dev.voidmc.hearth.standing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.Station;
import dev.voidmc.hearth.standing.appearance.Look;
import dev.voidmc.hearth.standing.appearance.PlayerState;
import dev.voidmc.hearth.standing.speech.SpeechKind;
import dev.voidmc.hearth.standing.speech.SpeechOdds;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SpeechAndLooksTest {
	private static SpeechOdds.Inputs who(Set<String> traits, Station station, JobType job, double charisma, double menace, double bribe) {
		return new SpeechOdds.Inputs(0, 0, charisma, menace, traits, 0, station, job, 0, bribe, 0);
	}

	private static double odds(SpeechKind kind, SpeechOdds.Inputs in) {
		return SpeechOdds.of(kind, in, 0.05, 0.95).chance();
	}

	@Test
	void traitsDecide() {
		SpeechOdds.Inputs plain = who(Set.of(), Station.PEASANT, JobType.FARMER, 0, 0.5, 1);
		SpeechOdds.Inputs greedy = who(Set.of("greedy"), Station.PEASANT, JobType.FARMER, 0, 0.5, 1);
		SpeechOdds.Inputs pious = who(Set.of("pious"), Station.PEASANT, JobType.FARMER, 0, 0.5, 1);
		SpeechOdds.Inputs brave = who(Set.of("brave"), Station.PEASANT, JobType.FARMER, 0, 0.5, 1);
		SpeechOdds.Inputs grumpy = who(Set.of("grumpy"), Station.PEASANT, JobType.FARMER, 0, 0.5, 1);
		assertTrue(odds(SpeechKind.BRIBE, greedy) > odds(SpeechKind.BRIBE, plain), "the greedy take bribes");
		assertTrue(odds(SpeechKind.BRIBE, pious) < odds(SpeechKind.BRIBE, plain));
		assertTrue(odds(SpeechKind.INTIMIDATE, brave) < odds(SpeechKind.INTIMIDATE, plain) - 0.2, "the brave shrug off threats");
		assertTrue(odds(SpeechKind.FLATTER, grumpy) < odds(SpeechKind.FLATTER, plain));
	}

	@Test
	void stationAndDutyDecide() {
		double peasant = odds(SpeechKind.INTIMIDATE, who(Set.of(), Station.PEASANT, JobType.FARMER, 0, 0.6, 0));
		double guard = odds(SpeechKind.INTIMIDATE, who(Set.of(), Station.OFFICIAL, JobType.GUARD, 0, 0.6, 0));
		double lord = odds(SpeechKind.PERSUADE, who(Set.of(), Station.LORD, JobType.LORD, 0, 0, 0));
		assertTrue(guard < peasant - 0.3, "a guard is not frightened like a farmer");
		assertTrue(lord < odds(SpeechKind.PERSUADE, who(Set.of(), Station.ARTISAN, JobType.SMITH, 0, 0, 0)));
		assertTrue(SpeechOdds.expectedBribe(Station.LORD) > 10 * SpeechOdds.expectedBribe(Station.POOR));
	}

	@Test
	void looksAndCoinAndRepetitionMatter() {
		double charming = odds(SpeechKind.PERSUADE, who(Set.of(), Station.ARTISAN, JobType.SMITH, 0.8, 0, 0));
		double repellent = odds(SpeechKind.PERSUADE, who(Set.of(), Station.ARTISAN, JobType.SMITH, -0.8, 0, 0));
		assertTrue(charming > repellent + 0.3);
		double half = odds(SpeechKind.BRIBE, who(Set.of(), Station.ARTISAN, JobType.SMITH, 0, 0, 0.5));
		double fair = odds(SpeechKind.BRIBE, who(Set.of(), Station.ARTISAN, JobType.SMITH, 0, 0, 1));
		double lavish = odds(SpeechKind.BRIBE, who(Set.of(), Station.ARTISAN, JobType.SMITH, 0, 0, 3));
		assertTrue(half < fair && fair < lavish);
		SpeechOdds.Inputs again = new SpeechOdds.Inputs(0, 0, 0, 0, Set.of(), 0, Station.ARTISAN, JobType.SMITH, 0, 0, 2);
		assertTrue(odds(SpeechKind.FLATTER, again) < odds(SpeechKind.FLATTER, who(Set.of(), Station.ARTISAN, JobType.SMITH, 0, 0, 0)) - 0.3);
		SpeechOdds.Odds o = SpeechOdds.of(SpeechKind.BRIBE, who(Set.of("greedy"), Station.POOR, JobType.HAULER, 0, 0, 10), 0.05, 0.95);
		assertEquals(0.95, o.chance(), 1e-9, "never certain");
		assertTrue(o.factors().stream().anyMatch(f -> f.key().equals("trait.greedy")), "the screen can say why");
	}

	@Test
	void appearance() {
		Look clean = new Look(1, true, false, false, 7, 4, 0, 0, 0);
		Look brute = new Look(0.1, false, true, true, 20, 0, 0, 0, 80);
		Look foreigner = new Look(0.6, false, false, false, 7, 0, 4, 0, 0);
		assertTrue(clean.charisma() > 0.5, "clean, bathed, local dress: " + clean.charisma());
		assertTrue(brute.charisma() < -0.7);
		assertTrue(brute.menace() > 0.9);
		assertTrue(clean.menace() < 0.2);
		assertTrue(foreigner.charisma() < new Look(0.6, false, false, false, 7, 0, 0, 4, 0).charisma(), "foreign dress less welcome than plain");
		assertTrue(brute.notes().containsAll(java.util.List.of("filthy", "bloodied", "armed", "heavy_armour", "violent")));
		assertFalse(clean.notes().contains("armour"));
	}

	@Test
	void bodyState() {
		long day = 24000;
		PlayerState s = PlayerState.FRESH.washedAt(0);
		assertEquals(1, s.cleanliness(0, 3 * day), 1e-9);
		assertEquals(0.5, s.cleanliness(36000, 3 * day), 1e-9);
		assertEquals(0, s.cleanliness(10 * day, 3 * day), 1e-9);
		s = s.bloodiedAt(1000);
		assertTrue(s.bloodied(2000, 6000));
		assertFalse(s.bloodied(8000, 6000), "blood dries off in time");
		assertFalse(s.washedAt(1500).bloodied(2000, 6000), "and washes off");
		assertTrue(s.bathedAt(3000).bathed(4000, 24000));
		assertEquals(1, s.bathedAt(3000).cleanliness(3000, 3 * day), 1e-9, "a bath washes");
	}
}
