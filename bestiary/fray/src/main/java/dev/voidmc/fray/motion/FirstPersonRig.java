package dev.voidmc.fray.motion;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

/**
 * The first-person rig (ANIMATION.md §7.4): what action games draw in first person, a hand and its held item anchored to
 * the camera, the arm running off the edge of the screen as vanilla's first-person hand does.
 *
 * <p>Camera space throughout: blocks, x right, y up, the view along -z, the eye at the origin (the frame vanilla's
 * {@code FirstPersonHandsAndItemsRenderer} draws the held item in, after its sway).
 *
 * <ul>
 * <li>The rest pose is <b>vanilla's first-person held item</b> ({@code applyItemArmTransform}'s
 * {@code (±0.56, -0.52, -0.72)} and the item model's {@code firstperson_*hand} display transform), so a clip that starts
 * and ends at rest starts and ends exactly on vanilla's picture.</li>
 * <li>A clip moves each hand by its {@code fp} curves on {@code item_r} / {@code item_l}: a rigid transform of the
 * hand and its item, rotation (degrees, ZYX, the camera's axes) about the hand's grip and an offset in pixels. Rotations
 * are interpolated on quaternions ({@link Channel#sampleQuat}). The item never turns in the hand: the hand carries it.</li>
 * <li>The arm is vanilla's first-person arm (the long arm box vanilla draws for the empty hand, its sleeve as the skin
 * says), its hand at the grip, pointing back at vanilla's shoulder off screen at the bottom right ({@link #arm}); a hand
 * raised high gets more sleeve along the same line ({@link #armExtension}), so the arm always runs off the edge of the
 * screen. Its roll follows the hand.</li>
 * </ul>
 * Common code: the client draws with it ({@code FirstPersonBody}), the bake measures it ({@code Checks},
 * {@code FirstPersonMatch}).
 */
public final class FirstPersonRig {
	/** Vanilla's {@code applyItemArmTransform} for the right hand (no equip offset). */
	public static final float ARM_X = 0.56F, ARM_Y = -0.52F, ARM_Z = -0.72F;
	/** The hand (the grip) in the arm part's frame, model pixels: the fist's end, where {@code item_r} turns the item. */
	public static final float GRIP_ARM_X = -1, GRIP_ARM_Y = 10;
	/** A sword in item space (the item model centred, blocks): the grip in the fist, the tip, the pommel. */
	public static final Vector3f GRIP_ITEM = new Vector3f(), TIP_ITEM = new Vector3f(0.44F, 0.44F, 0), POMMEL_ITEM = new Vector3f(-0.44F, -0.44F, 0);
	/**
	 * Vanilla's first-person arm (the empty hand, {@code renderPlayerArm} at rest): the arm part's frame in camera space,
	 * per side, and its shoulder (the top of the arm box), off screen at the bottom right. The rig's arm is this arm,
	 * its hand moved to the grip, pointing back at the shoulder.
	 */
	private static final Matrix4f VANILLA_ARM_R = vanillaArm(1), VANILLA_ARM_L = vanillaArm(-1);
	public static final Vector3f SHOULDER_R = VANILLA_ARM_R.transformPosition(new Vector3f(GRIP_ARM_X / 16F, -2 / 16F, 0));
	private static final float TAN_Y = (float) Math.tan(Math.toRadians(35)), TAN_X = TAN_Y * 16 / 9F;
	private static final Matrix4f FP_DISPLAY_R = display(new float[] {0, -90, 25}, new float[] {1.13F, 3.2F, 1.13F}, 0.68F);
	private static final Matrix4f FP_DISPLAY_L = display(new float[] {0, 90, -25}, new float[] {1.13F, 3.2F, 1.13F}, 0.68F);
	private static final Matrix4f TP_DISPLAY_R = display(new float[] {0, -90, 55}, new float[] {0, 4, 0.5F}, 0.85F);
	private static final Matrix4f TP_DISPLAY_L = display(new float[] {0, 90, -55}, new float[] {0, 4, 0.5F}, 0.85F);
	/** Rest: camera space from item space, per hand, and the rest grip. */
	private static final Matrix4f REST_ITEM_R = new Matrix4f().translate(ARM_X, ARM_Y, ARM_Z).mul(FP_DISPLAY_R);
	private static final Matrix4f REST_ITEM_L = new Matrix4f().translate(-ARM_X, ARM_Y, ARM_Z).mul(FP_DISPLAY_L);
	private static final Vector3f REST_GRIP_R = new Vector3f(), REST_GRIP_L = new Vector3f();

	static {
		// The grip in item space: the point third person turns the item about (the fist, GRIP_ARM in the arm's frame),
		// through ItemInHandLayer's hand-to-item transform and the third-person display, onto the item's plane.
		Matrix4f tp = handToItem(false, new Matrix4f()).mul(TP_DISPLAY_R);
		tp.invert().transformPosition(GRIP_ARM_X / 16F, GRIP_ARM_Y / 16F, 0, GRIP_ITEM);
		GRIP_ITEM.z = 0;
		REST_ITEM_R.transformPosition(GRIP_ITEM, REST_GRIP_R);
		REST_GRIP_L.set(-REST_GRIP_R.x, REST_GRIP_R.y, REST_GRIP_R.z);
	}

	private FirstPersonRig() {
	}

	/** A model's {@code display} transform as vanilla's {@code ItemTransform.apply} builds it (translation in pixels). */
	public static Matrix4f display(float[] rotDeg, float[] translationPx, float scale) {
		return new Matrix4f().translate(translationPx[0] / 16F, translationPx[1] / 16F, translationPx[2] / 16F)
			.rotate(new Quaternionf().rotationXYZ((float) Math.toRadians(rotDeg[0]), (float) Math.toRadians(rotDeg[1]), (float) Math.toRadians(rotDeg[2])))
			.scale(scale);
	}

	/** {@code ItemInHandLayer}'s transform from the arm's frame (after {@code translateToHand}) to the item's display frame. */
	public static Matrix4f handToItem(boolean left, Matrix4f out) {
		return out.identity().rotateX((float) Math.toRadians(-90)).rotateY((float) Math.toRadians(180)).translate((left ? -1 : 1) / 16F, 2 / 16F, -10 / 16F);
	}

	/** The handheld item's third-person display transform (item space to the display frame). */
	public static Matrix4f tpDisplay(boolean left) {
		return new Matrix4f(left ? TP_DISPLAY_L : TP_DISPLAY_R);
	}

	/** Vanilla's first-person held item: camera space from item space, at rest. */
	public static Matrix4f restItem(boolean left) {
		return new Matrix4f(left ? REST_ITEM_L : REST_ITEM_R);
	}

	/** The rest grip in camera space. */
	public static Vector3f restGrip(boolean left, Vector3f out) {
		return out.set(left ? REST_GRIP_L : REST_GRIP_R);
	}

	/** {@code FirstPersonHandsAndItemsRenderer.renderPlayerArm} at rest (no swing, no equip offset), then the arm part's pose. */
	private static Matrix4f vanillaArm(float f) {
		return new Matrix4f().translate(f * 0.64F, -0.6F, -0.72F).rotateY((float) Math.toRadians(f * 45)).translate(f * -1.0F, 3.6F, 3.5F)
			.rotateZ((float) Math.toRadians(f * 120)).rotateX((float) Math.toRadians(200)).rotateY((float) Math.toRadians(f * -135)).translate(f * 5.6F, 0, 0)
			.translate(-f * 5 / 16F, 2 / 16F, 0);
	}

	/** One hand: its rigid offset from rest (rotation about the rest grip, then a translation, camera space). */
	public static final class Hand {
		public final Quaternionf rot = new Quaternionf();
		public final Vector3f pos = new Vector3f();

		public Hand set(Hand o) {
			this.rot.set(o.rot);
			this.pos.set(o.pos);
			return this;
		}

		/** {@code a} toward {@code b} by {@code w} (slerp, lerp); {@code a} null is rest. */
		public Hand blend(@Nullable Hand a, Hand b, float w) {
			Quaternionf ra = a == null ? new Quaternionf() : a.rot;
			Vector3f pa = a == null ? new Vector3f() : a.pos;
			Quaternionf rb = new Quaternionf(b.rot);
			if (ra.dot(rb) < 0) {
				rb.mul(-1);
			}
			ra.slerp(rb, w, this.rot);
			pa.lerp(b.pos, w, this.pos);
			return this;
		}

		/** Camera space from the rest item's camera space: about the rest grip, then moved. */
		public Matrix4f matrix(boolean left, Matrix4f out) {
			Vector3f g = restGrip(left, new Vector3f());
			return out.identity().translate(g.x + this.pos.x, g.y + this.pos.y, g.z + this.pos.z).rotate(this.rot).translate(-g.x, -g.y, -g.z);
		}

		/** The grip, camera space. */
		public Vector3f grip(boolean left, Vector3f out) {
			return restGrip(left, out).add(this.pos);
		}
	}

	/** A frame of the rig: both hands, and how much of the off hand shows. */
	public static final class Pose {
		public final Hand right = new Hand(), left = new Hand();
		/** 0..1: the off hand's arm (drawn for clips with {@code "arms": "both"}). */
		public float off;

		public Pose set(Pose o) {
			this.right.set(o.right);
			this.left.set(o.left);
			this.off = o.off;
			return this;
		}

		public Pose rest() {
			this.right.rot.identity();
			this.right.pos.zero();
			this.left.rot.identity();
			this.left.pos.zero();
			this.off = 0;
			return this;
		}

		/** {@code a} toward {@code b} by {@code w}; {@code a} null is rest (vanilla's held item). */
		public Pose blend(@Nullable Pose a, Pose b, float w) {
			this.right.blend(a == null ? null : a.right, b.right, w);
			this.left.blend(a == null ? null : a.left, b.left, w);
			this.off = (a == null ? 0 : a.off) + (b.off - (a == null ? 0 : a.off)) * w;
			return this;
		}

		public Hand hand(boolean left) {
			return left ? this.left : this.right;
		}
	}

	/** Whether a clip has first-person rig curves (else first person shows vanilla's hands). */
	public static boolean animates(Clip clip) {
		Clip.FirstPerson fp = clip.fp;
		if (fp == null || fp.hideArms() || "fray:default".equals(fp.framing()) || fp.rotation() == null) {
			return false;
		}
		return fp.rotation()[HumanoidRig.ITEM_R] != null || fp.position()[HumanoidRig.ITEM_R] != null;
	}

	/** The rig at {@code seconds} into the clip (already wrapped by its loop mode). */
	public static Pose sample(Clip clip, float seconds, Pose out) {
		out.rest();
		Clip.FirstPerson fp = clip.fp;
		if (fp == null || fp.rotation() == null) {
			return out;
		}
		hand(fp, HumanoidRig.ITEM_R, seconds, out.right);
		hand(fp, HumanoidRig.ITEM_L, seconds, out.left);
		out.off = fp.bothArms() ? 1 : 0;
		return out;
	}

	private static void hand(Clip.FirstPerson fp, int bone, float t, Hand out) {
		Channel r = fp.rotation()[bone], p = fp.position()[bone];
		if (r != null) {
			r.sampleQuat(t, out.rot);
		}
		if (p != null) {
			float[] v = new float[3];
			p.sample(t, v, 0);
			out.pos.set(v[0] / 16F, v[1] / 16F, v[2] / 16F);
		}
	}

	/**
	 * Where the client submits the first-person item state (whose own display transform then applies): the hand's
	 * transform on vanilla's {@code applyItemArmTransform} position.
	 */
	public static Matrix4f itemAnchor(Hand hand, boolean left, Matrix4f out) {
		return hand.matrix(left, out).translate(left ? -ARM_X : ARM_X, ARM_Y, ARM_Z);
	}

	/** The held item: camera space from item space (vanilla's first-person display included). */
	public static Matrix4f item(Hand hand, boolean left, Matrix4f out) {
		return hand.matrix(left, out).mul(left ? REST_ITEM_L : REST_ITEM_R);
	}

	/**
	 * The arm part's frame in camera space (model pixels / 16, as vanilla's first-person hand draws it): vanilla's
	 * first-person arm with its hand moved to the grip, pointing back at vanilla's shoulder, off screen at the bottom
	 * right. Its roll follows the hand. When the arm box would end on screen (a hand raised high), {@link #armExtension}
	 * says how much sleeve to add along the same line, so the arm always runs off the edge.
	 */
	public static Matrix4f arm(Hand hand, boolean left, Matrix4f out) {
		Matrix4f vanilla = left ? VANILLA_ARM_L : VANILLA_ARM_R;
		float gx = (left ? -GRIP_ARM_X : GRIP_ARM_X) / 16F, gy = GRIP_ARM_Y / 16F;
		Vector3f vGrip = vanilla.transformPosition(new Vector3f(gx, gy, 0));
		Vector3f vTop = vanilla.transformPosition(new Vector3f(gx, -2 / 16F, 0));
		Vector3f dv = new Vector3f(vTop).sub(vGrip).normalize();
		Quaternionf vRot = vanilla.getUnnormalizedRotation(new Quaternionf()).normalize();
		Vector3f grip = hand.grip(left, new Vector3f());
		Vector3f shoulder = new Vector3f(SHOULDER_R);
		if (left) {
			shoulder.x = -shoulder.x;
		}
		Vector3f d = new Vector3f(shoulder).sub(grip);
		if (d.lengthSquared() < 1e-6F) {
			d.set(dv);
		}
		d.normalize();
		Vector3f rigid = hand.rot.transform(new Vector3f(dv));
		Quaternionf q = new Quaternionf().rotationTo(rigid, d).mul(hand.rot).mul(vRot);
		return out.identity().translate(grip).rotate(q).translate(-gx, -gy, 0);
	}

	/** One sleeve segment: the arm box's upper half (the elbow cut), in pixels. */
	public static final float SEGMENT = 6;

	/**
	 * How many sleeve segments to add above the arm box ({@link #SEGMENT} px each, along the arm) so its far end leaves
	 * the screen: 0 for a hand near vanilla's place, more for a hand raised high (at most 4).
	 */
	public static int armExtension(Hand hand, boolean left) {
		Matrix4f m = arm(hand, left, new Matrix4f());
		float gx = (left ? -GRIP_ARM_X : GRIP_ARM_X) / 16F;
		for (int n = 0; n <= 4; n++) {
			if (!onScreen(m.transformPosition(new Vector3f(gx, (-2 - n * SEGMENT) / 16F, 0)))) {
				return n;
			}
		}
		return 4;
	}

	/** A point inside the view (a small margin outside the screen counts as inside). */
	private static boolean onScreen(Vector3f p) {
		if (p.z > -0.05F) {
			return false;
		}
		return Math.abs(p.x / -p.z) < TAN_X * 1.05F && Math.abs(p.y / -p.z) < TAN_Y * 1.05F;
	}
}
