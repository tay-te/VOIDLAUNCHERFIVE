package dev.voidmc.fray.motion.bake;

import dev.voidmc.fray.motion.Clip;
import dev.voidmc.fray.motion.ClipSampler;
import dev.voidmc.fray.motion.HumanoidRig;
import dev.voidmc.fray.motion.HumanoidSolver;
import dev.voidmc.fray.motion.PartPose;
import dev.voidmc.fray.motion.PoseBuffer;
import dev.voidmc.fray.motion.Rig;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

/**
 * First person, derived from the third-person pose (shared by the client's {@code FirstPersonBody} and the bake's framing
 * check, so the check measures what is drawn).
 *
 * <ol>
 * <li>The clip's third-person pose is solved exactly as the third-person renderer solves it (torso and item bones
 * included), so first person follows the same direction, path and timing.</li>
 * <li>The camera is the player's real eye at rest, looking along the view: the clip's root, torso lean and head turns
 * never move the viewpoint; only the arms and the item move relative to it.</li>
 * <li>The swing is calmed: each arm (and the item relative to its hand) is blended from a calm ready framing toward the
 * derived pose by the swing factor (the clip's {@code fp.swing} × the client's {@code fpSwingScale}, 0.6 by default), so
 * the move reads the same, smaller.</li>
 * <li>Fast parts are eased: the pose is a centred average over ±{@link #SMOOTH_TICKS} ticks of the clip, so a strike
 * spreads over a few frames while its middle (the hit tick) stays where it was.</li>
 * </ol>
 * The clip's {@code fp} block adds only a constant framing nudge ({@code offset}, {@code pitch}); first-person bone
 * curves are no longer read.
 */
public final class FirstPersonFrame {
	/** Standing eye height (blocks) over the player's model scale gives the eye in model space (blocks, Y down). */
	public static final float EYE_HEIGHT = 1.62F;
	/** The client's default swing scale (option {@code fpSwingScale}). */
	public static final float DEFAULT_SWING = 0.6F;
	/** Half-width of the easing window, ticks. */
	public static final float SMOOTH_TICKS = 1.5F;
	private static final float[] SMOOTH_AT = {-1.5F, -0.75F, 0, 0.75F, 1.5F};
	private static final float[] SMOOTH_W = {1, 2, 3, 2, 1};
	/** The left arm joins first person as it rises: fully drawn above this pitch (radians, arm forward at -pi/2). */
	private static final float LEFT_FROM = (float) Math.toRadians(-25), LEFT_FULL = (float) Math.toRadians(-45);
	/** The calm ready framing: the arm a little forward and low, the sword up and forward (about vanilla's held sword). */
	private static final PartPose READY_R = new PartPose(), READY_L = new PartPose();

	static {
		READY_R.setPos(-5, 2, 0);
		READY_R.xRot = (float) Math.toRadians(-52);
		READY_R.yRot = (float) Math.toRadians(-6);
		READY_L.setPos(5, 2, 0);
		READY_L.xRot = (float) Math.toRadians(-40);
		READY_L.yRot = (float) Math.toRadians(8);
	}

	private FirstPersonFrame() {
	}

	/** The result for one frame: camera-space matrices for each arm (the part's own frame) and its item (before display). */
	public static final class Frame {
		public final Matrix4f rightArm = new Matrix4f(), rightItem = new Matrix4f(), leftArm = new Matrix4f(), leftItem = new Matrix4f();
		/** How much of the left arm is drawn (0..1). */
		public float left;
	}

	/** The eye in model space (blocks, Y down) at rest: above the neck pivot. */
	public static float eyeModelY() {
		return HumanoidRig.MODEL_Y_OFFSET - EYE_HEIGHT / HumanoidRig.PLAYER_SCALE;
	}

	/** Camera space from model space (blocks): the framing nudge, vanilla's model flip and scale, the rest eye at the origin. */
	public static Matrix4f cameraFromModel(Clip.@Nullable FirstPerson fp, Matrix4f out) {
		float s = HumanoidRig.PLAYER_SCALE;
		out.identity();
		if (fp != null) {
			out.translate(fp.offset()[0], fp.offset()[1], fp.offset()[2]).rotateX((float) Math.toRadians(fp.pitch()));
		}
		return out.scale(-s, -s, s).translate(0, -eyeModelY(), 0);
	}

	/** The swing factor for a clip: its {@code fp.swing} × the client's scale. */
	public static float swing(Clip.FirstPerson fp, float clientScale) {
		return Math.max(0, Math.min(1.5F, fp.swing() * clientScale));
	}

	/**
	 * One first-person frame from a pose buffer (the animator's blended layers at this moment; no easing): solve, frame
	 * from the rest eye, calm toward the ready framing by {@code swing}.
	 */
	public static Frame derive(PoseBuffer buf, Clip.FirstPerson fp, float swing, HumanoidSolver solver, Frame out) {
		PartPose[] rest = HumanoidRig.restPose();
		PartPose[] parts = HumanoidRig.restPose();
		solver.solve(parts, rest, buf);
		Matrix4f cam = cameraFromModel(fp, new Matrix4f());
		arm(cam, parts[HumanoidRig.RIGHT_ARM], READY_R, buf, false, swing, out.rightArm, out.rightItem);
		arm(cam, parts[HumanoidRig.LEFT_ARM], READY_L, buf, true, swing, out.leftArm, out.leftItem);
		out.left = leftArm(parts, fp);
		return out;
	}

	/**
	 * The eased frame of a clip at {@code elapsed} ticks: the centred average of {@link #derive} over the easing window
	 * (clamped to the clip). Used while one clip plays alone; crossfades use {@link #derive} on the blended buffer.
	 */
	public static Frame clip(Clip clip, double elapsed, Clip.FirstPerson fp, float swing, HumanoidSolver solver, Frame out) {
		Frame f = new Frame();
		PoseBuffer buf = new PoseBuffer(Rig.HUMANOID);
		float[] scratch = new float[3];
		float sum = 0;
		float len = clip.length * 20;
		for (int i = 0; i < SMOOTH_AT.length; i++) {
			double t = Math.max(0, Math.min(len, elapsed + SMOOTH_AT[i]));
			buf.clear();
			ClipSampler.accumulate(clip, clip.localTime((float) (t / 20.0)), 1, buf, scratch);
			derive(buf, fp, swing, solver, f);
			float w = SMOOTH_W[i];
			if (sum == 0) {
				out.rightArm.set(f.rightArm);
				out.rightItem.set(f.rightItem);
				out.leftArm.set(f.leftArm);
				out.leftItem.set(f.leftItem);
				out.left = f.left;
			} else {
				float k = w / (sum + w);
				out.rightArm.set(blend(out.rightArm, f.rightArm, k));
				out.rightItem.set(blend(out.rightItem, f.rightItem, k));
				out.leftArm.set(blend(out.leftArm, f.leftArm, k));
				out.leftItem.set(blend(out.leftItem, f.leftItem, k));
				out.left += (f.left - out.left) * k;
			}
			sum += w;
		}
		return out;
	}

	private static void arm(Matrix4f cam, PartPose part, PartPose ready, PoseBuffer buf, boolean left, float swing, Matrix4f armOut, Matrix4f itemOut) {
		Matrix4f derived = new Matrix4f(cam).mul(partMatrix(part));
		Matrix4f calm = new Matrix4f(cam).mul(partMatrix(ready));
		armOut.set(blend(calm, derived, swing));
		// The item relative to the hand, calmed the same way, so the grip never leaves the hand.
		Matrix4f chain = itemChain(buf, left, new Matrix4f());
		Matrix4f chainRest = itemChain(null, left, new Matrix4f());
		itemOut.set(armOut).mul(blend(chainRest, chain, swing));
	}

	/** A part's own transform (vanilla's {@code ModelPart.translateAndRotate}, blocks). */
	public static Matrix4f partMatrix(PartPose p) {
		return new Matrix4f().translate(p.x / 16, p.y / 16, p.z / 16).rotate(new Quaternionf().rotationZYX(p.zRot, p.yRot, p.xRot))
			.scale(p.xScale, p.yScale, p.zScale);
	}

	/** {@code ItemInHandLayer}'s hand-to-item transform in the arm's frame, with the clip's item bone (none if null). */
	public static Matrix4f itemChain(@Nullable PoseBuffer buf, boolean left, Matrix4f out) {
		out.identity();
		if (buf != null) {
			int o = (left ? HumanoidRig.ITEM_L : HumanoidRig.ITEM_R) * PoseBuffer.STRIDE;
			float[] v = buf.v;
			float wr = v[o + PoseBuffer.W_ROT], wp = v[o + PoseBuffer.W_POS];
			float rx = v[o] * wr + v[o + PoseBuffer.ADD_ROT], ry = v[o + 1] * wr + v[o + PoseBuffer.ADD_ROT + 1], rz = v[o + 2] * wr + v[o + PoseBuffer.ADD_ROT + 2];
			float px = v[o + 3] * wp + v[o + PoseBuffer.ADD_POS], py = v[o + 4] * wp + v[o + PoseBuffer.ADD_POS + 1], pz = v[o + 5] * wp + v[o + PoseBuffer.ADD_POS + 2];
			if (rx != 0 || ry != 0 || rz != 0 || px != 0 || py != 0 || pz != 0) {
				float gx = (left ? 1 : -1) / 16F, gy = 10 / 16F;
				out.translate(gx + px / 16F, gy + py / 16F, pz / 16F).rotate(new Quaternionf().rotationZYX(rz, ry, rx)).translate(-gx, -gy, 0);
			}
		}
		return out.rotateX((float) Math.toRadians(-90)).rotateY((float) Math.toRadians(180)).translate((left ? -1 : 1) / 16F, 2 / 16F, -10 / 16F);
	}

	/** Translation, rotation and scale of two transforms blended (rotation by slerp); {@code w} 0 is {@code a}. */
	public static Matrix4f blend(Matrix4f a, Matrix4f b, float w) {
		Vector3f ta = a.getTranslation(new Vector3f()), tb = b.getTranslation(new Vector3f());
		Vector3f sa = a.getScale(new Vector3f()), sb = b.getScale(new Vector3f());
		Quaternionf qa = a.getUnnormalizedRotation(new Quaternionf()).normalize(), qb = b.getUnnormalizedRotation(new Quaternionf()).normalize();
		return new Matrix4f().translationRotateScale(ta.lerp(tb, w), qa.slerp(qb, w), sa.lerp(sb, w));
	}

	/** How much of the left arm first person shows (0..1): when the clip says both arms, or as the arm rises. */
	public static float leftArm(PartPose[] parts, Clip.FirstPerson fp) {
		if (fp.bothArms()) {
			return 1;
		}
		float x = parts[HumanoidRig.LEFT_ARM].xRot;
		float k = (x - LEFT_FROM) / (LEFT_FULL - LEFT_FROM);
		k = Math.max(0, Math.min(1, k));
		return k * k * (3 - 2 * k);
	}
}
