package dev.voidmc.fray.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.voidmc.fray.api.DamageKind;
import dev.voidmc.fray.api.GuardOutcome;
import dev.voidmc.fray.api.HitFlags;
import dev.voidmc.fray.api.TellColour;
import dev.voidmc.fray.combat.math.GuardMath;
import org.junit.jupiter.api.Test;

/** Parry and block windows at every tick boundary (COMBAT.md §7, §17). */
class GuardMathTest {
	@Test
	void shieldParryWindowIsTicksZeroToFive() {
		GuardMath.Spec s = GuardMath.shield(1.3F);
		int w = GuardMath.window(s.parryWindow(), 0, false);
		assertEquals(6, w);
		assertEquals(GuardOutcome.NONE, GuardMath.resolve(s, -1, w, false, true, HitFlags.MELEE), "before the press");
		for (int t = 0; t <= 5; t++) {
			assertEquals(GuardOutcome.PARRIED, GuardMath.resolve(s, t, w, true, true, HitFlags.MELEE), "tick " + t);
		}
		assertEquals(GuardOutcome.BLOCKED, GuardMath.resolve(s, 6, w, true, true, HitFlags.MELEE), "tick 6 held: block");
		assertEquals(GuardOutcome.NONE, GuardMath.resolve(s, 6, w, false, true, HitFlags.MELEE), "tick 6 released: nothing");
	}

	@Test
	void weaponParryWindowIsTicksZeroToFour() {
		GuardMath.Spec s = GuardMath.weapon();
		int w = GuardMath.window(s.parryWindow(), 0, false);
		assertEquals(5, w);
		assertEquals(GuardOutcome.PARRIED, GuardMath.resolve(s, 4, w, true, true, 0));
		assertEquals(GuardOutcome.BLOCKED, GuardMath.resolve(s, 5, w, true, true, 0));
	}

	@Test
	void blockStartsAtItsTick() {
		GuardMath.Spec s = GuardMath.weapon();
		// With no parry window (Winded), the block is up from tick 3.
		assertEquals(GuardOutcome.NONE, GuardMath.resolve(s, 2, 0, true, true, 0));
		assertEquals(GuardOutcome.BLOCKED, GuardMath.resolve(s, 3, 0, true, true, 0));
	}

	@Test
	void windowsBonusSpamAndCeiling() {
		assertEquals(7, GuardMath.window(5, 2, false), "Duelist +2 ticks");
		assertEquals(9, GuardMath.window(6, 10, false), "capped at 9");
		assertEquals(3, GuardMath.window(6, 0, true), "a press within 12 ticks of the last: half a window");
	}

	@Test
	void tellsChangeTheRules() {
		GuardMath.Spec shield = GuardMath.shield(1.3F), weapon = GuardMath.weapon();
		int red = TellColour.RED.hitFlags(), orange = TellColour.ORANGE.hitFlags(), purple = TellColour.PURPLE.hitFlags();
		assertEquals(GuardOutcome.NONE, GuardMath.resolve(shield, 1, 6, true, true, red), "red: unblockable");
		assertEquals(GuardOutcome.NONE, GuardMath.resolve(shield, 1, 6, true, true, purple), "purple: a grab");
		assertEquals(GuardOutcome.PARRIED, GuardMath.resolve(shield, 1, 6, true, true, orange), "orange parries with a shield");
		assertEquals(GuardOutcome.NONE, GuardMath.resolve(weapon, 1, 5, false, true, orange), "orange: no parry with a weapon");
		assertEquals(GuardOutcome.BLOCKED, GuardMath.resolve(weapon, 4, 5, true, true, orange), "orange: blocked by a weapon");
		assertEquals(1.5F * 10 * 1.6F, GuardMath.blockCost(weapon, 10, orange, 1), 1e-4, "orange blocks cost ×1.5");
	}

	@Test
	void arrowsAndAngles() {
		assertEquals(GuardOutcome.NONE, GuardMath.resolve(GuardMath.weapon(), 1, 5, true, true, HitFlags.PROJECTILE), "a weapon doesn't block arrows");
		assertEquals(GuardOutcome.PARRIED, GuardMath.resolve(GuardMath.shield(1.3F), 1, 6, true, true, HitFlags.PROJECTILE), "a shield deflects them");
		assertTrue(GuardMath.covers(GuardMath.shield(1), 89));
		assertFalse(GuardMath.covers(GuardMath.shield(1), 91), "the front half");
		assertTrue(GuardMath.covers(GuardMath.weapon(), 60));
		assertFalse(GuardMath.covers(GuardMath.weapon(), 61), "120° in front");
		assertEquals(GuardOutcome.NONE, GuardMath.resolve(GuardMath.weapon(), 1, 5, true, false, 0), "from behind");
	}

	@Test
	void blockedShares() {
		GuardMath.Spec shield = GuardMath.shield(1.0F), weapon = GuardMath.weapon();
		assertEquals(1.0F, shield.blocked(DamageKind.SLASH));
		assertEquals(0.5F, shield.blocked(DamageKind.FIRE));
		assertEquals(0.0F, shield.blocked(DamageKind.ARCANE));
		assertEquals(0.6F, weapon.blocked(DamageKind.BLUNT));
		assertEquals(0.0F, weapon.blocked(DamageKind.FROST));
		assertEquals(13, GuardMath.blockCost(GuardMath.shield(1.3F), 10, 0, 1), 1e-4, "wood shield stability 1.3");
	}
}
