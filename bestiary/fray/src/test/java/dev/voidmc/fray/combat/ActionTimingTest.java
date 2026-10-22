package dev.voidmc.fray.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.voidmc.fray.combat.action.ActionDef;
import dev.voidmc.fray.combat.action.Moveset;
import dev.voidmc.fray.combat.action.Movesets;
import dev.voidmc.fray.combat.action.WeaponFamily;
import dev.voidmc.fray.combat.math.HoldMath;
import dev.voidmc.fray.combat.math.Phases;
import dev.voidmc.fray.combat.math.PoiseMeter;
import org.junit.jupiter.api.Test;

/** Phases, windows, hold timing, movesets and poise (COMBAT.md §3, §4, §6, §9). */
class ActionTimingTest {
	@Test
	void phasesAndWindows() {
		Phases sword = Movesets.of(WeaponFamily.SWORD).light(0).phases();
		assertEquals(new Phases(4, 2, 6), sword);
		assertEquals(Phases.Phase.WINDUP, sword.at(3));
		assertEquals(Phases.Phase.ACTIVE, sword.at(4));
		assertEquals(Phases.Phase.ACTIVE, sword.at(5));
		assertEquals(Phases.Phase.RECOVERY, sword.at(6));
		assertEquals(Phases.Phase.DONE, sword.at(12));
		assertFalse(sword.chainOpen(5));
		assertTrue(sword.chainOpen(6), "the chain window opens at the end of ACTIVE");
		assertTrue(sword.dodgeCancel(6));
		assertFalse(sword.guardCancel(8));
		assertTrue(sword.guardCancel(9), "guard cancels the last 3 ticks");
		Phases heavy = Movesets.of(WeaponFamily.SWORD).heavy().phases();
		assertTrue(heavy.feint(9));
		assertFalse(heavy.feint(10), "feint: windup ticks 0 to windup - 3");
	}

	@Test
	void actionSpeedShortensWindupAndRecovery() {
		Phases p = new Phases(4, 2, 10).speed(1.25);
		assertEquals(new Phases(3, 2, 8), p);
		assertEquals(new Phases(4, 2, 10), new Phases(4, 2, 10).speed(1));
	}

	@Test
	void holdTiming() {
		assertEquals(HoldMath.Kind.LIGHT, HoldMath.classify(5));
		assertEquals(HoldMath.Kind.HEAVY, HoldMath.classify(6));
		assertEquals(HoldMath.Kind.HEAVY, HoldMath.classify(15));
		assertEquals(HoldMath.Kind.CHARGED, HoldMath.classify(16));
		assertEquals(0, HoldMath.charge(16, 20, 1));
		assertEquals(0.5F, HoldMath.charge(26, 20, 1), 1e-4);
		assertEquals(1, HoldMath.charge(36, 20, 1), 1e-4);
		assertEquals(1, HoldMath.charge(31, 20, 1.5), 1e-4, "Mettle's charge speed ×1.5");
		assertFalse(HoldMath.autoRelease(75, 20, 1));
		assertTrue(HoldMath.autoRelease(76, 20, 1), "full at 36, released 40 ticks later");
		assertEquals(23, HoldMath.chargedCost(18, 28, 0.5F), 1e-4);
	}

	@Test
	void swordStringIsAboutVanillaPace() {
		// COMBAT.md §4: a three-hit sword string takes about 38 ticks for 3.3 hits of damage.
		Moveset s = Movesets.of(WeaponFamily.SWORD);
		int ticks = 0;
		float dmg = 0;
		for (int i = 0; i < 3; i++) {
			ActionDef d = s.light(i);
			ticks += d.phases().total();
			dmg += d.multiplier();
		}
		assertEquals(42, ticks);
		assertEquals(3.3F, dmg, 1e-4);
	}

	@Test
	void movesetTable() {
		Moveset mace = Movesets.of(WeaponFamily.MACE);
		assertEquals(new Phases(16, 3, 16), mace.heavy().phases());
		assertEquals(2, mace.lights().size());
		assertEquals(1.8F, mace.poiseFactor());
		Moveset spear = Movesets.of(WeaponFamily.SPEAR);
		assertEquals(120, spear.light(2).arc(), "the spear's finisher sweeps");
		assertEquals(4.0F, spear.light(0).reach());
		assertEquals(12, Movesets.BACKSTEP.stamina());
		assertEquals(22, Movesets.DODGE_COST[1]);
		assertEquals(new Phases(1, 7, 4), Movesets.ROLL.phases(), "i-frames ticks 1-7, recovery the last 4");
		assertTrue(Movesets.canonical().startsWith("fray-combat-1"));
	}

	@Test
	void poiseBreaksRefillsAndHyperArmour() {
		PoiseMeter p = new PoiseMeter(20);
		assertFalse(p.hit(15, 0, 0));
		assertTrue(p.hit(6, 0, 1), "at 0: staggered");
		assertEquals(20, p.value, "and refilled");
		assertFalse(p.hit(30, 20, 2), "hyper-armour absorbs 20");
		assertEquals(10, p.value, 1e-4);
		p.tick(61, 60);
		assertEquals(10, p.value, 1e-4);
		p.tick(62, 60);
		assertEquals(20, p.value, "refills after 60 ticks without poise damage");
		PoiseMeter player = new PoiseMeter(10);
		assertTrue(player.flinches(10, 0), "a hit at least the remaining poise flinches");
		assertFalse(player.flinches(25, 20));
	}
}
