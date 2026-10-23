package dev.voidmc.fray.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.voidmc.fray.api.HitFlags;
import dev.voidmc.fray.combat.math.DamageMath;
import org.junit.jupiter.api.Test;

/** The pipeline's maths, stage by stage (CONTRACTS.md §4.2, §7.6; COMBAT.md §10.2, §13). */
class DamageMathTest {
	private static final float E = 1e-4F;

	@Test
	void stage2Pools() {
		// (base + add) × (1 + Σ increased) × Π more
		assertEquals((10 + 2) * 1.3F * 1.5F, DamageMath.pools(10, 2, 0.3F, 1.5F), E);
		assertEquals(0, DamageMath.pools(5, -10, 0, 1), E, "never negative");
		assertEquals(10, DamageMath.pools(10, 0, 0, 1), E, "neutral pools change nothing");
	}

	@Test
	void stage2Ceiling() {
		assertEquals(26, DamageMath.capAttacker(10, 40, DamageMath.ATTACKER_CAP), E);
		assertEquals(20, DamageMath.capAttacker(10, 20, DamageMath.ATTACKER_CAP), E);
	}

	@Test
	void stage3TakesTheLargestNotTheProduct() {
		int flags = HitFlags.RIPOSTE | HitFlags.BACKSTAB | HitFlags.PLUNGE;
		assertEquals(2.0F, DamageMath.situational(flags, 1, 1.5F, true, 1.5F, 1.5F, 3), E);
		assertEquals(2.5F, DamageMath.situational(HitFlags.EXECUTE, 1, 1.5F, false, 1.5F, 1.5F, 3), E);
		assertEquals(1.5F, DamageMath.situational(HitFlags.PLUNGE, 1, 1.5F, false, 1.5F, 1.5F, 3), E);
		assertEquals(2.0F, DamageMath.situational(HitFlags.PLUNGE, 1, 1.5F, false, 1.5F, 2.0F, 3), E, "Overhead Blow makes the plunge ×2");
		assertEquals(1.0F, DamageMath.situational(0, 1, 1.5F, false, 1.5F, 1.5F, 3), E);
		assertEquals(1.5F, DamageMath.situational(0, 1, 1.5F, true, 1.5F, 1.5F, 3), E, "a rolled crit");
	}

	@Test
	void stage3WeakPointOnTopAndCapped() {
		assertEquals(3.0F, DamageMath.situational(HitFlags.RIPOSTE | HitFlags.WEAK_POINT, 1, 1.5F, false, 1.5F, 1.5F, 3), E, "2.0 × 1.5 = 3.0");
		assertEquals(3.0F, DamageMath.situational(HitFlags.EXECUTE | HitFlags.WEAK_POINT, 1, 1.5F, false, 1.5F, 1.5F, 3), E, "2.5 × 1.5 capped at 3.0");
		assertEquals(3.5F, DamageMath.situational(HitFlags.EXECUTE | HitFlags.WEAK_POINT, 1, 1.5F, false, 1.5F, 1.5F, 3.5F), E, "a Mastery's 3.5 cap");
		assertEquals(2.25F, DamageMath.situational(HitFlags.WEAK_POINT, 1, 1.5F, true, 1.5F, 1.5F, 3), E, "crit × weak point");
	}

	@Test
	void chargedRisesFromHeavyToFamilyMax() {
		assertEquals(1.6F, DamageMath.charged(0, 2.4F), E);
		assertEquals(2.0F, DamageMath.charged(0.5F, 2.4F), E);
		assertEquals(2.4F, DamageMath.charged(1, 2.4F), E);
		assertEquals(2.4F, DamageMath.charged(3, 2.4F), E, "clamped");
		assertEquals(2.4F, DamageMath.situational(HitFlags.CHARGED, DamageMath.charged(1, 2.4F), 1.5F, true, 1.5F, 1.5F, 3), E, "charged beats a crit");
	}

	@Test
	void stage5ArmourIsVanillasFormula() {
		// Vanilla: damage × (1 − clamp(armour − damage / (2 + toughness / 4), armour × 0.2, 20) / 25)
		assertEquals(10 * (1 - (20 - 10 / 2F) / 25F), DamageMath.armour(10, 20, 0), E);
		assertEquals(42, DamageMath.armour(50, 20, 0), E, "armour floor: 20 % of the armour");
		assertEquals(10, DamageMath.armour(10, 0, 0), E, "no armour");
		assertTrue(DamageMath.armour(10, 20, 8) < DamageMath.armour(10, 20, 0), "toughness helps");
		assertTrue(DamageMath.armour(10, 20, 8 * 2) < DamageMath.armour(10, 20, 8 * 0.5F), "pierce counts toughness double, blunt half");
	}

	@Test
	void stage5Resistances() {
		assertEquals(5, DamageMath.resist(10, 0.5F), E);
		assertEquals(1, DamageMath.resist(10, 0.95F), E, "0.9 cap: a 10 % floor");
		assertEquals(20, DamageMath.resist(10, -1), E, "-1 doubles");
		assertEquals(20, DamageMath.resist(10, -3), E, "clamped at -1");
	}

	@Test
	void incomingCeiling() {
		assertEquals(2.5F, DamageMath.floor(10, 1), E, "never below 25 % of the raw hit");
		assertEquals(6, DamageMath.floor(10, 6), E);
	}

	@Test
	void poiseByAction() {
		assertEquals(10, DamageMath.poise(10, 1, HitFlags.LIGHT), E);
		assertEquals(15, DamageMath.poise(10, 1, HitFlags.HEAVY), E);
		assertEquals(20, DamageMath.poise(10, 1, HitFlags.CHARGED | HitFlags.HEAVY), E);
		assertEquals(18, DamageMath.poise(10, 1.8F, HitFlags.LIGHT), E, "a mace's poise factor");
	}

	@Test
	void referenceSwordNumbers() {
		// COMBAT.md §13 early: light 6 → heavy ≈ 10 (×1.6), full charge ≈ 14 (×2.4), riposte 12 (×2.0).
		float light = 6;
		assertEquals(9.6F, light * DamageMath.HEAVY, E);
		assertEquals(14.4F, light * DamageMath.charged(1, 2.4F), E);
		assertEquals(12, light * DamageMath.situational(HitFlags.RIPOSTE, 1, 1.5F, false, 1.5F, 1.5F, 3), E);
		assertEquals(light * 0.6F, light * DamageMath.PVP_SCALE, E);
	}

	/** The weak-point test's geometry: the shortest distance between a swing's line and a hurt capsule's axis. */
	@Test
	void segmentDistance() {
		net.minecraft.world.phys.Vec3 o = net.minecraft.world.phys.Vec3.ZERO;
		assertEquals(1.0, DamageMath.segmentDistance(o, new net.minecraft.world.phys.Vec3(0, 0, 4), new net.minecraft.world.phys.Vec3(-1, 1, 2),
			new net.minecraft.world.phys.Vec3(1, 1, 2)), 1e-9, "crossing lines a block apart");
		assertEquals(0.0, DamageMath.segmentDistance(o, new net.minecraft.world.phys.Vec3(0, 0, 4), new net.minecraft.world.phys.Vec3(-1, 0, 2),
			new net.minecraft.world.phys.Vec3(1, 0, 2)), 1e-9, "touching");
		assertEquals(2.0, DamageMath.segmentDistance(o, new net.minecraft.world.phys.Vec3(0, 0, 4), new net.minecraft.world.phys.Vec3(0, 0, 6),
			new net.minecraft.world.phys.Vec3(0, 0, 8)), 1e-9, "beyond the swing's reach");
		assertEquals(Math.sqrt(2), DamageMath.segmentDistance(o, o, new net.minecraft.world.phys.Vec3(1, 1, 0), new net.minecraft.world.phys.Vec3(1, 1, 0)), 1e-9,
			"two points");
	}
}
