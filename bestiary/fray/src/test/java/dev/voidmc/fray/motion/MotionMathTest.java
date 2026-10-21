package dev.voidmc.fray.motion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;
import org.junit.jupiter.api.Test;

/** Sampling and blending (ANIMATION.md §7.2): the maths the client runs every frame and the bake runs every tick. */
class MotionMathTest {
	private static Channel ch(float[] t, float[] v, Interp... in) {
		return new Channel(t, v, in);
	}

	@Test
	void linearStepAndClamp() {
		Channel c = ch(new float[] {0, 1, 2}, new float[] {0, 0, 0, 10, 20, 30, 50, 50, 50}, Interp.LINEAR, Interp.LINEAR, Interp.STEP);
		float[] out = new float[3];
		c.sample(-1, out, 0);
		assertEquals(0, out[0], 1e-6);
		c.sample(0.5F, out, 0);
		assertEquals(5, out[0], 1e-5);
		assertEquals(15, out[2], 1e-5);
		// The segment into a STEP key holds the previous value until the key's time (vanilla's pre/post targets).
		c.sample(1.99F, out, 0);
		assertEquals(10, out[0], 1e-5);
		c.sample(2, out, 0);
		assertEquals(50, out[0], 1e-5);
		c.sample(9, out, 0);
		assertEquals(50, out[1], 1e-5);
	}

	@Test
	void catmullromMatchesVanillaFormulaAndHitsKeys() {
		Channel c = ch(new float[] {0, 1, 2, 3}, new float[] {0, 0, 0, 10, 0, 0, 0, 0, 0, 5, 0, 0}, Interp.CATMULLROM, Interp.CATMULLROM, Interp.CATMULLROM,
			Interp.CATMULLROM);
		float[] out = new float[3];
		c.sample(1, out, 0);
		assertEquals(10, out[0], 1e-5);
		c.sample(1.25F, out, 0);
		// Mth.catmullrom(0.25, p0=0, p1=10, p2=0, p3=5)
		float a = 0.25F;
		float want = 0.5F * (2 * 10 + (0 - 0) * a + (2 * 0 - 5 * 10 + 4 * 0 - 5) * a * a + (3 * 10 - 0 - 3 * 0 + 5) * a * a * a);
		assertEquals(want, out[0], 1e-5);
	}

	@Test
	void layersFoldIntoOneBuffer() {
		Random r = new Random(7);
		for (int i = 0; i < 200; i++) {
			float under = r.nextFloat() * 4 - 2, a = r.nextFloat() * 4 - 2, b = r.nextFloat() * 4 - 2;
			float w1 = r.nextFloat(), w2 = r.nextFloat();
			PoseBuffer buf = new PoseBuffer();
			buf.combine(HumanoidRig.HEAD, PoseBuffer.ROT, PoseBuffer.W_ROT, a, 0, 0, w1);
			buf.combine(HumanoidRig.HEAD, PoseBuffer.ROT, PoseBuffer.W_ROT, b, 0, 0, w2);
			float w = buf.weight(HumanoidRig.HEAD, PoseBuffer.W_ROT);
			float applied = under + (buf.v[HumanoidRig.HEAD * PoseBuffer.STRIDE] - under) * w;
			float l1 = under + (a - under) * w1;
			float expected = l1 + (b - l1) * w2;
			assertEquals(expected, applied, 1e-4, "w1 " + w1 + " w2 " + w2);
		}
	}

	@Test
	void torsoCarriesArmsAboutTheHips() {
		PoseBuffer buf = new PoseBuffer();
		// 90 degrees of twist about Y (radians in the buffer).
		buf.combine(HumanoidRig.TORSO, PoseBuffer.ROT, PoseBuffer.W_ROT, 0, (float) Math.PI / 2, 0, 1);
		PartPose[] rest = HumanoidRig.restPose();
		PartPose[] parts = HumanoidRig.restPose();
		new HumanoidSolver().solve(parts, rest, buf);
		// Right shoulder (-5, 2, 0): relative to the hips (0, 12, 0) it is (-5, -10, 0); Ry(90) sends x to -z.
		assertEquals(0, parts[HumanoidRig.RIGHT_ARM].x, 1e-4);
		assertEquals(2, parts[HumanoidRig.RIGHT_ARM].y, 1e-4);
		assertEquals(5, parts[HumanoidRig.RIGHT_ARM].z, 1e-4);
		assertEquals(Math.PI / 2, parts[HumanoidRig.BODY].yRot, 1e-4);
		// Legs stay put.
		assertEquals(-1.9F, parts[HumanoidRig.RIGHT_LEG].x, 1e-6);
		assertEquals(0, parts[HumanoidRig.RIGHT_LEG].yRot, 1e-6);
	}

	@Test
	void forwardArmPointsForwardInEntitySpace() {
		PartPose arm = HumanoidRig.restPose()[HumanoidRig.RIGHT_ARM];
		arm.xRot = (float) -Math.PI / 2;
		org.joml.Vector3f p = HumanoidSolver.partToModel(arm, 0, 10, 0, new org.joml.Vector3f());
		HumanoidSolver.modelToEntity(p, null, 1, p);
		// The hand is in front (+Z entity-local), on the right (-X), at shoulder height (1.501 - 2/16).
		assertTrue(p.z > 0.6, "z " + p.z);
		assertTrue(p.x < 0, "x " + p.x);
		assertEquals(1.501 - 2 / 16.0, p.y, 1e-3);
	}
}
