package dev.voidmc.fray.motion.bake;

import dev.voidmc.fray.motion.Clip;
import dev.voidmc.fray.motion.HumanoidRig;
import dev.voidmc.fray.motion.HumanoidSolver;
import dev.voidmc.fray.motion.PartPose;
import dev.voidmc.fray.motion.PoseBuffer;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

/**
 * First person is derived from the third-person pose (shared by the client's {@code FirstPersonBody} and the bake's
 * framing check, so the check measures what is drawn). The clip's full third-person pose (root, torso, arms, item bones)
 * is solved exactly as the third-person renderer solves it; the camera sits at the posed head's eye and looks along the
 * player's view (the body's facing: the clip's head turns don't swing the camera). Each arm is drawn in that camera's
 * frame, so first person shows the third-person sword where it is. The clip's {@code fp} block only adds a constant
 * framing nudge ({@code offset}, {@code pitch}) so the arm stays on screen at the extremes; first-person bone curves are
 * no longer read (the format still parses them).
 */
public final class FirstPersonFrame {
	/** Standing eye height (blocks) over the player's model scale gives the eye in model space (blocks, Y down). */
	public static final float EYE_HEIGHT = 1.62F;
	/** The left arm joins first person as it rises: fully drawn above this pitch (radians, arm forward at -pi/2). */
	private static final float LEFT_FROM = (float) Math.toRadians(-25), LEFT_FULL = (float) Math.toRadians(-45);

	private FirstPersonFrame() {
	}

	/** The eye in model space (blocks, Y down) at rest: above the neck pivot. */
	public static float eyeModelY() {
		return HumanoidRig.MODEL_Y_OFFSET - EYE_HEIGHT / HumanoidRig.PLAYER_SCALE;
	}

	/**
	 * Camera space from model space (blocks) for the solved {@code parts} of {@code buf}: {@code offset}, {@code pitch},
	 * vanilla's model flip and scale, the posed eye at the origin, the clip's root transform. A part is then drawn with
	 * its own {@code translate(p / 16)} and ZYX rotation.
	 */
	public static Matrix4f cameraFromModel(PartPose[] parts, PoseBuffer buf, Clip.@Nullable FirstPerson fp, HumanoidSolver solver, Matrix4f out) {
		Matrix4f root = new Matrix4f();
		boolean hasRoot = solver.rootTransform(buf, root);
		PartPose head = parts[HumanoidRig.HEAD];
		Vector3f eye = new Vector3f(0, eyeModelY(), 0);
		new Quaternionf().rotationZYX(head.zRot, head.yRot, head.xRot).transform(eye);
		eye.add(head.x / 16, head.y / 16, head.z / 16);
		if (hasRoot) {
			root.transformPosition(eye);
		}
		float s = HumanoidRig.PLAYER_SCALE;
		out.identity();
		if (fp != null) {
			out.translate(fp.offset()[0], fp.offset()[1], fp.offset()[2]).rotateX((float) Math.toRadians(fp.pitch()));
		}
		out.scale(-s, -s, s).translate(-eye.x, -eye.y, -eye.z);
		if (hasRoot) {
			out.mul(root);
		}
		return out;
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
