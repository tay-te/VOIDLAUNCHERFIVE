package dev.voidmc.mettle.character.skill.stealth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.voidmc.mettle.character.skill.athletics.AthleticsRules;
import dev.voidmc.mettle.character.skill.stealth.StealthRules.Holdings;
import dev.voidmc.mettle.compat.HearthKeys;
import java.util.Random;
import org.junit.jupiter.api.Test;

/** Stealth's rules (docs/BUILD_FEEL.md §14.1): XP, how far hostiles notice you, perk numbers, the effective level and keys. */
class StealthRulesTest {
	// ---------------------------------------------------------------- XP

	@Test
	void sneakingNearTeachesOnlyUnawareReachableHostiles() {
		assertTrue(StealthRules.teaches(true, false, false, false, false, true));
		assertFalse(StealthRules.teaches(false, false, false, false, false, true), "not a hostile");
		assertFalse(StealthRules.teaches(true, true, false, false, false, true), "it is targeting you");
		assertFalse(StealthRules.teaches(true, false, true, false, false, true), "a spawner's mob");
		assertFalse(StealthRules.teaches(true, false, false, true, false, true), "leashed");
		assertFalse(StealthRules.teaches(true, false, false, false, true, true), "in a boat or a cart");
		assertFalse(StealthRules.teaches(true, false, false, false, false, false), "can't reach you (caged)");
	}

	@Test
	void sneakingNearPaysOneEveryFourSecondsAtMostFifteenAMinute() {
		assertEquals(80, StealthRules.NEAR_TICKS);
		assertEquals(1.0, StealthRules.NEAR_XP);
		AthleticsRules.Meter m = new AthleticsRules.Meter(StealthRules.NEAR_CAP_PER_MINUTE);
		double paid = 0;
		// Twice as often as the rule allows, for a minute: the cap holds at 15.
		for (int t = 0; t < 1200; t += 40) {
			paid += m.take(StealthRules.NEAR_XP, t);
		}
		assertEquals(15, paid, 1e-9);
		AthleticsRules.Meter w = new AthleticsRules.Meter(StealthRules.WARDEN_CAP_PER_MINUTE);
		double warden = 0;
		for (int t = 0; t < 1200; t += 10) {
			warden += w.take(StealthRules.WARDEN_XP_PER_SECOND, t);
		}
		assertEquals(60, warden, 1e-9, "Warden XP: at most 60 a minute");
	}

	@Test
	void sneakAttackXpIsTheDamageCappedAtHealthPlusAQuarterOnTheKill() {
		assertEquals(6, StealthRules.sneakAttackXp(6, 20));
		assertEquals(4, StealthRules.sneakAttackXp(30, 4), "never more than the health left");
		assertEquals(0, StealthRules.sneakAttackXp(-1, 4));
		assertEquals(5, StealthRules.killBonus(20));
		assertEquals(0, StealthRules.killBonus(-3));
	}

	@Test
	void aNightHunterLandsNearSixHundredToSevenHundredFiftyAnHour() {
		double h = StealthRules.hourly(18, 15, 19.5);
		assertTrue(h >= 600 && h <= 750, "XP per hour " + h);
		// Sneaking near hostiles alone never pays past 15 a minute.
		assertEquals(60 * 15, StealthRules.hourly(60, 0, 19.5), 1e-9);
	}

	// ---------------------------------------------------------------- being noticed

	@Test
	void shadowShadowedCloakSkulkerAndGhost() {
		assertEquals(1.0, StealthRules.sight(0, true, false, 0, false, 1, 10, false, 10), 1e-9, "nothing held");
		assertEquals(0.9, StealthRules.sight(1, true, false, 0, false, 1, 10, false, 10), 1e-9);
		assertEquals(0.7, StealthRules.sight(3, true, false, 0, false, 1, 10, false, 10), 1e-9);
		assertEquals(1.0, StealthRules.sight(3, false, false, 0, false, 1, 10, false, 10), 1e-9, "Shadow only under sneaking's cover");
		assertEquals(0.7 * 0.9, StealthRules.sight(3, true, false, 0.10, false, 1, 10, false, 10), 1e-9, "Shadowed 10 %");
		assertEquals(0.75, StealthRules.sight(0, false, false, 0, true, 1, 7, false, 10), 1e-9, "Cloak of Dusk at light 7");
		assertEquals(1.0, StealthRules.sight(0, false, false, 0, true, 1, 8, false, 10), 1e-9, "not at light 8");
		assertEquals(1.25, StealthRules.sight(0, false, false, 0, false, 1.25, 12, false, 10), 1e-9, "Skulker in light 12");
		assertEquals(1.0, StealthRules.sight(0, false, false, 0, false, 1.25, 11, false, 10), 1e-9);
		assertEquals(0, StealthRules.sight(0, true, false, 0, false, 1, 3, true, 4.5), 1e-9, "Ghost: unseen beyond 4");
		assertEquals(1.0, StealthRules.sight(0, true, false, 0, false, 1, 3, true, 3.5), 1e-9, "Ghost: seen within 4");
		assertEquals(0.8, StealthRules.sight(0, true, true, 0, false, 1, 10, false, 10), 1e-9, "Footpad's cover is sneaking's 0.8");
	}

	// ---------------------------------------------------------------- perks

	@Test
	void sneakAttackPerks() {
		assertEquals(1.0, StealthRules.sneakAttack(false, 0, false, false));
		assertEquals(1.3, StealthRules.sneakAttack(true, 0, false, false), 1e-9);
		assertEquals(1.8, StealthRules.sneakAttack(true, 0.5, false, false), 1e-9);
		assertEquals(3.0, StealthRules.sneakAttack(false, 0, true, false), 1e-9, "Assassin: triple");
		assertEquals(1.5, StealthRules.sneakAttack(false, 0, true, true), 1e-9, "a boss: half again");
		assertEquals(StealthRules.nightHunterCrit(6), 6 + 3 + 2, 1e-9, "Night Hunter: vanilla's best crit");
	}

	@Test
	void givingUpDarknessAndHush() {
		assertEquals(60, StealthRules.unseenMemory(60, 0, false));
		assertEquals(45, StealthRules.unseenMemory(60, 0.25, false));
		assertEquals(30, StealthRules.unseenMemory(60, 0, true), "Never Seen: twice as fast");
		assertEquals(23, StealthRules.unseenMemory(60, 0.25, true));
		assertEquals(1, StealthRules.unseenMemory(1, 0.9, true), "never below a tick");
		assertEquals(100, StealthRules.darkDuration(100, true, 0, false));
		assertEquals(80, StealthRules.darkDuration(100, false, 0.2, true), "Blindness: Lurking only");
		assertEquals(40, StealthRules.darkDuration(100, true, 0.2, true), "Darkness: Lurking and Under Its Nose");
		assertEquals(6, StealthRules.hushedRadius(8, 0.25), 1e-9);
	}

	// ---------------------------------------------------------------- what Hearth reads

	static Holdings with(int level, int shadow, boolean cloak, double unseen, boolean ghost, boolean quietWay, double lightFingered, boolean sneaking,
		boolean dark, boolean night, boolean attacking) {
		return new Holdings(level, shadow, cloak, unseen, ghost, quietWay, lightFingered, 0, sneaking, dark, night, attacking);
	}

	@Test
	void theEffectiveLevelNeverPassesTwentyAndCountsOnlyTheListedSources() {
		Random r = new Random(7);
		for (int i = 0; i < 20_000; i++) {
			Holdings h = with(r.nextInt(21), r.nextInt(4), r.nextBoolean(), r.nextInt(3) * 0.06, r.nextBoolean(), r.nextBoolean(), r.nextDouble() * 0.12,
				r.nextBoolean(), r.nextBoolean(), r.nextBoolean(), r.nextBoolean());
			for (boolean bonus : new boolean[] {true, false}) {
				int eff = StealthRules.effectiveLevel(h, bonus);
				assertTrue(eff <= 20 && eff >= h.level(), "effective " + eff + " from " + h);
				int expect = h.level() + (h.shadow() > 0 ? h.shadow() : 0);
				if (!bonus) {
					expect += (h.cloak() && h.night() ? 2 : 0) + (h.unseen() > 0 && h.night() ? 1 : 0) + (h.ghostHolds() ? 5 : 0) + (h.quietWay() ? 2 : 0);
				}
				assertEquals(Math.min(20, expect), eff, h + " bonus " + bonus);
			}
		}
		// Light-fingered and Silver Tongue never touch the level.
		assertEquals(5, StealthRules.effectiveLevel(new Holdings(5, 0, false, 0, false, false, 0.12, 0.03, true, true, true, false), false));
	}

	@Test
	void bonusKeys() {
		Holdings none = Holdings.NONE;
		assertEquals(0, StealthRules.bonus(none, "hearth:haggle/band"), "a key Stealth doesn't answer");
		assertEquals(0, StealthRules.bonus(none, "nonsense"));
		for (String k : new String[] {HearthKeys.STEALTH_SIGHT, HearthKeys.STEALTH_GIVE_UP, HearthKeys.STEALTH_UNSEEN_BEYOND, HearthKeys.PICKPOCKET_AWARENESS}) {
			assertEquals(0, StealthRules.bonus(none, k), k + " with nothing held");
		}
		Holdings ghost = with(10, 0, false, 0, true, false, 0, true, true, true, false);
		assertEquals(4, StealthRules.bonus(ghost, HearthKeys.STEALTH_UNSEEN_BEYOND), "Ghost holds: 4");
		assertEquals(0, StealthRules.bonus(with(10, 0, false, 0, true, false, 0, true, false, true, false), HearthKeys.STEALTH_UNSEEN_BEYOND), "in the light: 0");
		assertEquals(0, StealthRules.bonus(with(10, 0, false, 0, true, false, 0, false, true, true, false), HearthKeys.STEALTH_UNSEEN_BEYOND), "not sneaking: 0");
		assertEquals(0, StealthRules.bonus(with(10, 0, false, 0, true, false, 0, true, true, true, true), HearthKeys.STEALTH_UNSEEN_BEYOND), "attacking: 0");
		Random r = new Random(3);
		for (int i = 0; i < 5000; i++) {
			Holdings h = with(10, 0, r.nextBoolean(), r.nextDouble() * 0.12, r.nextBoolean(), r.nextBoolean(), r.nextDouble() * 0.12, r.nextBoolean(), r.nextBoolean(),
				r.nextBoolean(), r.nextBoolean());
			double beyond = StealthRules.bonus(h, HearthKeys.STEALTH_UNSEEN_BEYOND);
			assertTrue(beyond == 0 || beyond == 4, "unseen_beyond is 4 or 0: " + beyond);
			double sight = StealthRules.bonus(h, HearthKeys.STEALTH_SIGHT);
			assertTrue(sight >= 0 && sight < 1, "a share: " + sight);
		}
		Holdings all = with(10, 0, true, 0.12, false, true, 0, true, true, true, false);
		assertEquals(1 - 0.75 * 0.88 * 0.9, StealthRules.bonus(all, HearthKeys.STEALTH_SIGHT), 1e-9, "Cloak, Unseen and the Quiet Way together");
		assertEquals(1 / 3.0, StealthRules.bonus(all, HearthKeys.STEALTH_GIVE_UP), 1e-9);
		assertEquals(0.1, StealthRules.bonus(with(1, 0, false, 0, false, false, 0.1, false, false, false, false), HearthKeys.PICKPOCKET_AWARENESS), 1e-9);
	}

	@Test
	void fallbacksAreOffWheneverHearthReadsKeys() {
		Holdings h = with(10, 0, true, 0.1, true, true, 0.1, true, true, true, false);
		assertEquals(10, StealthRules.effectiveLevel(h, true), "with bonus keys: no fallback levels");
		assertEquals(10 + 2 + 1 + 5 + 2, StealthRules.effectiveLevel(h, false));
		assertEquals(0, StealthRules.animalsCloser(h, true));
		assertEquals(0.1, StealthRules.animalsCloser(h, false), 1e-9);
	}
}
