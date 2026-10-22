package dev.voidmc.fray.motion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.voidmc.fray.motion.bake.FirstPersonMatch;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

/** Orientation sampling on quaternions, and the first-person rig: rest is vanilla's held item, the item is rigid in the hand. */
class FirstPersonRigTest {
	private static float angle(Quaternionf a, Quaternionf b) {
		return (float) Math.toDegrees(2 * Math.acos(Math.min(1, Math.abs(a.dot(b)))));
	}

	@Test
	void slerpTurnsAboutOneAxisTheShortWay() {
		// Two keys 120° apart about a tilted axis, written as Euler angles whose per-component lerp wanders off the axis.
		Quaternionf q0 = new Quaternionf(), q1 = new Quaternionf().fromAxisAngleDeg(new Vector3f(1, 1, 0).normalize(), 120);
		org.joml.Vector3f e = new org.joml.Matrix3f().set(q1).getEulerAnglesZYX(new Vector3f());
		Channel c = new Channel(new float[] {0, 1}, new float[] {0, 0, 0, (float) Math.toDegrees(e.x), (float) Math.toDegrees(e.y), (float) Math.toDegrees(e.z)},
			new Interp[] {Interp.LINEAR, Interp.LINEAR}, true);
		for (int i = 0; i <= 10; i++) {
			float t = i / 10F;
			Quaternionf q = c.sampleQuat(t, new Quaternionf());
			// On the great circle: the angle from each key adds up to the whole turn, at a constant rate.
			assertEquals(120 * t, angle(q0, q), 0.05);
			assertEquals(120, angle(q0, q) + angle(q, q1), 0.05);
			// sample() gives Euler angles of the same orientation.
			float[] out = new float[3];
			c.sample(t, out, 0);
			assertEquals(0, angle(q, Channel.euler(out[0], out[1], out[2], new Quaternionf())), 0.2);
		}
	}

	@Test
	void cubicPassesThroughItsKeys() {
		float[] v = {0, 0, 0, 40, 10, 0, 80, -20, 30, 90, 0, 60};
		Channel c = new Channel(new float[] {0, 1, 2, 3}, v, new Interp[] {Interp.CATMULLROM, Interp.CATMULLROM, Interp.CATMULLROM, Interp.CATMULLROM}, true);
		for (int k = 0; k < 4; k++) {
			Quaternionf q = c.sampleQuat(k, new Quaternionf());
			assertEquals(0, angle(q, Channel.euler(v[k * 3], v[k * 3 + 1], v[k * 3 + 2], new Quaternionf())), 0.2);
		}
		// Smooth: no step between neighbouring samples bigger than the turn's pace allows.
		Quaternionf prev = c.sampleQuat(0, new Quaternionf());
		for (int i = 1; i <= 300; i++) {
			Quaternionf q = c.sampleQuat(i / 100F, new Quaternionf());
			assertTrue(angle(prev, q) < 2, "jump at " + i);
			prev = q;
		}
	}

	@Test
	void restIsVanillasHeldItemAndTheItemIsRigidInTheHand() {
		FirstPersonRig.Hand rest = new FirstPersonRig.Hand();
		Matrix4f item = FirstPersonRig.item(rest, false, new Matrix4f());
		assertTrue(item.equals(FirstPersonRig.restItem(false), 1e-6F), "rest is vanilla's first-person item");
		// Turn and move the hand: the grip follows the hand exactly, and the blade keeps its place in it.
		FirstPersonRig.Hand h = new FirstPersonRig.Hand();
		h.rot.rotationXYZ(0.4F, -1.1F, 0.7F);
		h.pos.set(-0.3F, 0.2F, 0.1F);
		Matrix4f moved = FirstPersonRig.item(h, false, new Matrix4f());
		Vector3f grip = moved.transformPosition(new Vector3f(FirstPersonRig.GRIP_ITEM));
		assertEquals(0, grip.distance(h.grip(false, new Vector3f())), 1e-5);
		float len0 = item.transformPosition(new Vector3f(FirstPersonRig.TIP_ITEM)).distance(item.transformPosition(new Vector3f(FirstPersonRig.GRIP_ITEM)));
		float len1 = moved.transformPosition(new Vector3f(FirstPersonRig.TIP_ITEM)).distance(grip);
		assertEquals(len0, len1, 1e-5);
		// The forearm's hand is at the grip.
		Matrix4f arm = FirstPersonRig.arm(h, false, new Matrix4f());
		Vector3f hand = arm.transformPosition(new Vector3f(FirstPersonRig.GRIP_ARM_X / 16F, FirstPersonRig.GRIP_ARM_Y / 16F, 0));
		assertEquals(0, hand.distance(grip), 1e-5);
	}

	@Test
	void matchCheckPassesTheClipAndCatchesAMirroredFirstPerson() throws Exception {
		String src = java.nio.file.Files.readString(java.nio.file.Path.of("../src/main/motion/bestiary/player/sword/light_1.motion.json"));
		Clip clip = ClipJson.parse(Identifier.parse("bestiary:player/sword/light_1"), src);
		assertTrue(FirstPersonMatch.applies(clip));
		assertTrue(FirstPersonMatch.run(clip).problems().isEmpty(), FirstPersonMatch.run(clip).problems().toString());
		// First person mirrored left to right (the cut now crosses the other way): the check must catch it.
		com.google.gson.JsonObject o = com.google.gson.JsonParser.parseString(src).getAsJsonObject();
		com.google.gson.JsonObject item = o.getAsJsonObject("fp").getAsJsonObject("bones").getAsJsonObject("item_r");
		for (String ch : new String[] {"rotation", "position"}) {
			for (com.google.gson.JsonElement k : item.getAsJsonArray(ch)) {
				com.google.gson.JsonArray v = k.getAsJsonArray().get(1).getAsJsonArray();
				for (int i : ch.equals("rotation") ? new int[] {1, 2} : new int[] {0}) {
					v.set(i, new com.google.gson.JsonPrimitive(-v.get(i).getAsFloat()));
				}
			}
		}
		Clip mirrored = ClipJson.parse(Identifier.parse("bestiary:player/sword/light_1"), o.toString());
		assertTrue(!FirstPersonMatch.run(mirrored).problems().isEmpty(), "a mirrored first person passes the match check");
	}
}
