package dev.voidmc.bestiary;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.voidmc.bestiary.ai.math.Curves;
import dev.voidmc.bestiary.ai.math.Morale;
import dev.voidmc.bestiary.ai.math.SenseMath;
import dev.voidmc.bestiary.ai.math.TokenPool;
import dev.voidmc.bestiary.ai.math.Utility;
import dev.voidmc.bestiary.danger.DangerMath;
import java.util.Random;
import org.junit.jupiter.api.Test;

/** BESTIARY.md §14 "Tactics (JUnit)": scoring curves, tokens never let more than N attack one player, morale; plus senses and danger. */
class AiMathTest {
	private static final float E = 1e-4F;

	@Test
	void curves() {
		assertEquals(0, Curves.eval(Curves.Curve.LINEAR, 0, 0, 10), E);
		assertEquals(0.5F, Curves.eval(Curves.Curve.LINEAR, 5, 0, 10), E);
		assertEquals(1, Curves.eval(Curves.Curve.LINEAR, 20, 0, 10), E);
		assertEquals(0.5F, Curves.eval(Curves.Curve.INVERSE, 5, 0, 10), E);
		// A band is 1 inside, eases out over a quarter of its width (at least half a block).
		assertEquals(1, Curves.eval(Curves.Curve.BAND, 1.5F, 0, 2.5F), E);
		assertEquals(0.6F, Curves.eval(Curves.Curve.BAND, 2.75F, 0, 2.5F), 0.01F);
		assertEquals(0, Curves.eval(Curves.Curve.BAND, 4, 0, 2.5F), E);
		assertEquals(1, Curves.eval(Curves.Curve.BOOL, 1, 1, 0.3F), E);
		assertEquals(0.3F, Curves.eval(Curves.Curve.BOOL, 0, 1, 0.3F), E);
		assertEquals(1, Curves.eval(Curves.Curve.BELL, 4, 4, 2), E);
		assertEquals(0.5F, Curves.eval(Curves.Curve.BELL, 6, 4, 2), E);
		assertEquals(1, Curves.eval(Curves.Curve.STEP, 3, 2, 0), E);
		assertEquals(0, Curves.eval(Curves.Curve.STEP, 1, 2, 0), E);
	}

	@Test
	void utilityScoresAndPicks() {
		// weight × Π values, noise ±15 %.
		assertEquals(0.5F, Utility.score(1, new float[] {1, 0.5F}, 2, 0.5F), E);
		assertEquals(0.5F * 1.15F, Utility.score(1, new float[] {1, 0.5F}, 2, 1F), E);
		assertEquals(0.5F * 0.85F, Utility.score(1, new float[] {1, 0.5F}, 2, 0F), E);
		assertEquals(0, Utility.score(2, new float[] {0.8F, 0}, 2, 0.5F), E);
		// The best wins; the second only when within 10 % and the coin says so.
		float[] s = {0.2F, 1.0F, 0.95F, 0.5F};
		assertEquals(1, Utility.pick(s, 4, 0.9F));
		assertEquals(2, Utility.pick(s, 4, 0.1F));
		float[] far = {0.2F, 1.0F, 0.8F};
		assertEquals(1, Utility.pick(far, 3, 0.1F));
		assertEquals(-1, Utility.pick(new float[] {0, 0}, 2, 0.1F));
		assertEquals(1, Utility.repetition(0), E);
		assertEquals(0.4F, Utility.repetition(5), E);
	}

	@Test
	void tokensNeverLetMoreThanNAttack() {
		Random r = new Random(7);
		for (int capacity = 1; capacity <= 4; capacity++) {
			TokenPool pool = new TokenPool();
			int[] held = new int[40];
			for (long now = 0; now < 4000; now++) {
				int c = r.nextInt(40);
				if (held[c] > 0 && r.nextFloat() < 0.3F) {
					pool.release(c);
					held[c] = 0;
				} else if (pool.acquire(c, c % 7 == 0 ? 2 : 1, capacity, now, 100)) {
					held[c] = 1;
				}
				assertTrue(pool.used() <= capacity, "used " + pool.used() + " of " + capacity);
			}
		}
		// Elites take two; renewal keeps the cost; expiry frees a token held by a creature that vanished.
		TokenPool p = new TokenPool();
		assertTrue(p.acquire(1, 2, 3, 0, 100));
		assertFalse(p.acquire(2, 2, 3, 0, 100));
		assertTrue(p.acquire(3, 1, 3, 0, 100));
		assertTrue(p.acquire(1, 2, 3, 50, 100));
		assertEquals(3, p.used());
		assertTrue(p.acquire(2, 2, 3, 200, 100), "tokens expire after their ttl");
		// Capacity by danger and difficulty (§5, §6.3).
		assertEquals(2, TokenPool.meleeCapacity(1, 0));
		assertEquals(2, TokenPool.meleeCapacity(2.4F, 0));
		assertEquals(3, TokenPool.meleeCapacity(3, 0));
		assertEquals(4, TokenPool.meleeCapacity(5, 0));
		assertEquals(1, TokenPool.meleeCapacity(1, -1));
		assertEquals(5, TokenPool.meleeCapacity(5, 1));
		assertEquals(2, TokenPool.rangedCapacity(0));
	}

	@Test
	void morale() {
		// Undead never flee.
		assertFalse(Morale.flees(false, false, false, 1, 0.01F, 0.2F, 0.3F));
		// A beast alone flees under 20 % health.
		assertTrue(Morale.flees(true, false, false, 1, 0.15F, 0.2F, 0.3F));
		assertFalse(Morale.flees(true, false, false, 1, 0.25F, 0.2F, 0.3F));
		// A pack flees when its leader dies or below 30 % of its number.
		assertTrue(Morale.flees(true, true, true, 1, 1, 0.2F, 0.3F));
		assertTrue(Morale.flees(true, true, false, 0.25F, 1, 0.2F, 0.3F));
		assertFalse(Morale.flees(true, true, false, 0.75F, 1, 0.2F, 0.3F));
	}

	@Test
	void senses() {
		// Day, lit: full range. Night without dark sight: the night range. Light 4 or less halves it; sneaking ×0.6; invisible ×0.1.
		assertEquals(24, SenseMath.sightRange(24, 16, false, false, 15, false, false, 1), E);
		assertEquals(16, SenseMath.sightRange(24, 16, true, false, 15, false, false, 1), E);
		assertEquals(24, SenseMath.sightRange(24, 16, true, true, 0, false, false, 1), E);
		assertEquals(12, SenseMath.sightRange(24, 16, false, false, 4, false, false, 1), E);
		assertEquals(24 * 0.6F, SenseMath.sightRange(24, 16, false, false, 15, true, false, 1), E);
		assertEquals(2.4F, SenseMath.sightRange(24, 16, false, false, 15, false, true, 1), E);
		assertEquals(12, SenseMath.sightRange(24, 16, false, false, 15, false, false, 0.5F), E);
		// A 120° cone facing south (yaw 0 faces +Z).
		assertTrue(SenseMath.inCone(0, 0, 5, 120));
		assertTrue(SenseMath.inCone(0, 2, 5, 120));
		assertFalse(SenseMath.inCone(0, 0, -5, 120));
		assertFalse(SenseMath.inCone(0, 5, 0.5, 120));
		assertTrue(SenseMath.inCone(90, -5, 0, 120), "yaw 90 faces -X");
		// Footsteps: sprinting 10, walking 4, sneaking 1, still 0.
		assertEquals(10, SenseMath.footsteps(true, true, false, 1), E);
		assertEquals(4, SenseMath.footsteps(true, false, false, 1), E);
		assertEquals(1, SenseMath.footsteps(true, false, true, 1), E);
		assertEquals(0, SenseMath.footsteps(false, true, false, 1), E);
		assertTrue(SenseMath.hears(16, 15, 1));
		assertFalse(SenseMath.hears(16, 17, 1));
	}

	@Test
	void danger() {
		double[] rings = {2000, 5000, 10000};
		assertEquals(1, DangerMath.distance(0, rings, 600), E);
		assertEquals(1, DangerMath.distance(1600, rings, 600), E);
		assertEquals(1.5F, DangerMath.distance(2000, rings, 600), E);
		assertEquals(2, DangerMath.distance(3000, rings, 600), E);
		assertEquals(4, DangerMath.distance(20000, rings, 600), E);
		assertEquals(0, DangerMath.depth(10, 50, 12, 44), E);
		assertEquals(0.5F, DangerMath.depth(30, 50, 12, 44), E);
		assertEquals(1, DangerMath.depth(30, 30, 12, 44), E);
		// §6.2 columns, interpolated.
		assertEquals(1, DangerMath.health(1), E);
		assertEquals(2.2F, DangerMath.health(3), E);
		assertEquals((2.2F + 3.2F) / 2, DangerMath.health(3.5F), E);
		assertEquals(3.6F, DangerMath.damage(5), E);
		assertEquals(0.02F, DangerMath.eliteChance(2), E);
		assertEquals(0, DangerMath.eliteChance(1), E);
		assertEquals(3, DangerMath.affixes(5, 0), E);
		assertEquals(2, DangerMath.affixes(4, 0.9F), E);
		assertEquals(1, DangerMath.affixes(2, 0.9F), E);
		// §6.3 difficulty.
		assertEquals(0.6F, DangerMath.difficultyDamage(1), E);
		assertEquals(1.15F, DangerMath.difficultyHealth(3), E);
		assertEquals(2, DangerMath.difficultyTokens(3, true));
		assertEquals(2.2F, DangerMath.partyHealth(3), E);
	}
}
