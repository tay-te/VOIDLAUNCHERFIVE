package dev.voidmc.fray.motion;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

/**
 * The first-person rig (ANIMATION.md §7.4): what action games draw in first person, a hand and its held item anchored to
 * the camera, with a forearm coming in from the edge of the screen. Never the upper arm or the shoulder.
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
 * <li>The forearm is the lower half of the player's arm (the elbow cut, {@code Elbows}: hand, wrist, forearm, closed by a
 * cuff; never the upper arm or the shoulder), at the first-person item's proportions, its hand at the grip, pointing
 * down and right ({@link #FOREARM}), so it comes in from the bottom right, seen from the side. Its roll follows the
 * hand.</li>
 * </ul>
 * Common code: the client draws with it ({@code FirstPersonBody}), the bake measures it ({@code Checks},
 * {@code FirstPersonMatch}).
 */
public final class FirstPersonRig {
	/** Vanilla's {@code applyItemArmTransform} for the right hand (no equip offset). */
	public static final float ARM_X = 0.56F, ARM_Y = -0.52F, ARM_Z = -0.72F;
	/**
	 * The forearm's scale: the player's model scale times the first-person item's size against the third-person one
	 * (0.68 / 0.85), so hand and sword keep third person's proportions.
	 */
	public static final float SCALE = HumanoidRig.PLAYER_SCALE * 0.68F / 0.85F;
	/** The hand (the grip) in the arm part's frame, model pixels: the fist's end, where {@code item_r} turns the item. */
	public static final float GRIP_ARM_X = -1, GRIP_ARM_Y = 10;
	/** A sword in item space (the item model centred, blocks): the grip in the fist, the tip, the pommel. */
	public static final Vector3f GRIP_ITEM = new Vector3f(), TIP_ITEM = new Vector3f(0.44F, 0.44F, 0), POMMEL_ITEM = new Vector3f(-0.44F, -0.44F, 0);
	/**
	 * Where the forearm points from the hand (camera space, unit): down and right across the screen, a little toward the
	 * camera, so it comes in from the bottom right and is seen from the side, never end-on.
	 */
	public static final Vector3f FOREARM = new Vector3f(0.55F, -0.75F, 0.36F).normalize();
	/** Where the arm points from the grip at rest. */
	private static final Vector3f REST_FOREARM = new Vector3f();
	/** The forearm's roll about its own axis at rest (degrees): the thumb side toward the blade. */
	private static final float REST_ROLL = 35;

	private static final Matrix4f FP_DISPLAY_R = display(new float[] {0, -90, 25}, new float[] {1.13F, 3.2F, 1.13F}, 0.68F);
	private static final Matrix4f FP_DISPLAY_L = display(new float[] {0, 90, -25}, new float[] {1.13F, 3.2F, 1.13F}, 0.68F);
	private static final Matrix4f TP_DISPLAY_R = display(new float[] {0, -90, 55}, new float[] {0, 4, 0.5F}, 0.85F);
	private static final Matrix4f TP_DISPLAY_L = display(new float[] {0, 90, -55}, new float[] {0, 4, 0.5F}, 0.85F);
	/** Rest: camera space from item space, per hand, and the rest grip. */
	private static final Matrix4f REST_ITEM_R = new Matrix4f().translate(ARM_X, ARM_Y, ARM_Z).mul(FP_DISPLAY_R);
	private static final Matrix4f REST_ITEM_L = new Matrix4f().translate(-ARM_X, ARM_Y, ARM_Z).mul(FP_DISPLAY_L);
	private static final Vector3f REST_GRIP_R = new Vector3f(), REST_GRIP_L = new Vector3f();
	/** The arm's rest rotation in camera space (applied after vanilla's model flip and scale), per side. */
	private static final Quaternionf REST_ARM_R = new Quaternionf(), REST_ARM_L = new Quaternionf();

	static {
		// The grip in item space: the point third person turns the item about (the fist, GRIP_ARM in the arm's frame),
		// through ItemInHandLayer's hand-to-item transform and the third-person display, onto the item's plane.
		Matrix4f tp = handToItem(false, new Matrix4f()).mul(TP_DISPLAY_R);
		tp.invert().transformPosition(GRIP_ARM_X / 16F, GRIP_ARM_Y / 16F, 0, GRIP_ITEM);
		GRIP_ITEM.z = 0;
		REST_ITEM_R.transformPosition(GRIP_ITEM, REST_GRIP_R);
		REST_GRIP_L.set(-REST_GRIP_R.x, REST_GRIP_R.y, REST_GRIP_R.z);
		REST_FOREARM.set(FOREARM);
		// The arm points from the hand (+y down the arm in model space, flipped up) along FOREARM, rolled.
		REST_ARM_R.rotationTo(0, 1, 0, REST_FOREARM.x, REST_FOREARM.y, REST_FOREARM.z).rotateY((float) Math.toRadians(REST_ROLL));
		mirror(REST_ARM_R, REST_ARM_L);
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

	private static void mirror(Quaternionf in, Quaternionf out) {
		// Mirroring across x: a rotation (x, y, z, w) becomes (x, -y, -z, w).
		out.set(in.x, -in.y, -in.z, in.w);
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
	 * The arm part's frame in camera space (model pixels / 16, vanilla's model flip and the player's scale included): the
	 * hand at the grip, the forearm pointing from it along {@link #FOREARM}, rolled with the hand.
	 */
	public static Matrix4f arm(Hand hand, boolean left, Matrix4f out) {
		Vector3f rest = new Vector3f(REST_FOREARM);
		if (left) {
			rest.x = -rest.x;
		}
		Vector3f grip = hand.grip(left, new Vector3f());
		// Where the hand's turn would point the forearm, and where it stays (down and right): the smallest turn between,
		// so the forearm keeps its direction and rolls with the hand.
		Vector3f rigid = hand.rot.transform(new Vector3f(rest));
		Quaternionf q = new Quaternionf().rotationTo(rigid, rest).mul(hand.rot).mul(left ? REST_ARM_L : REST_ARM_R);
		float gx = (left ? -GRIP_ARM_X : GRIP_ARM_X) / 16F, gy = GRIP_ARM_Y / 16F;
		return out.identity().translate(grip).rotate(q).scale(-SCALE, -SCALE, SCALE).translate(-gx, -gy, 0);
	}
}
