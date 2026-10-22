package dev.voidmc.fray.motion;

import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Turns a {@link PoseBuffer} into part poses for the humanoid rig: the one piece of pose maths shared by the client
 * (which copies the result onto vanilla's {@code ModelPart}s) and the server bake (which places locators with it), so
 * what the player sees and where the server's hit volume is can't disagree.
 *
 * <ol>
 * <li>each real part: {@code lerp(under, rest + offset, weight)} per channel;</li>
 * <li>the virtual {@code torso}: vanilla's arms and head aren't children of the body, so the torso rotation turns the
 * body, head and both arms about the hips: {@code p' = R(p - hip) + hip}, {@code rot' = R * rot};</li>
 * <li>{@code root} is a transform of the whole model ({@link #rootTransform}), applied by the renderer after
 * {@code setupRotations}.</li>
 * </ol>
 * Not thread-safe: one solver per thread (the client's render thread, the bake, the server thread).
 */
public final class HumanoidSolver {
	private final Matrix3f torso = new Matrix3f();
	private final Matrix3f part = new Matrix3f();
	private final Vector3f tmp = new Vector3f();
	private final Quaternionf quat = new Quaternionf();
	private static final int[] CARRIED = {HumanoidRig.HEAD, HumanoidRig.BODY, HumanoidRig.RIGHT_ARM, HumanoidRig.LEFT_ARM};

	/** {@code parts} hold the pose underneath (vanilla's, or the rest pose on the server) and receive the result. */
	public void solve(PartPose[] parts, PartPose[] rest, PoseBuffer buf) {
		this.solveParts(parts, rest, buf);
		if (parts.length > HumanoidRig.LEFT_FOREARM) {
			this.forearm(parts, rest, buf, HumanoidRig.RIGHT_ARM, HumanoidRig.RIGHT_FOREARM);
			this.forearm(parts, rest, buf, HumanoidRig.LEFT_ARM, HumanoidRig.LEFT_FOREARM);
		}
	}

	/**
	 * The forearm's local rotation (relative to its arm, radians, x/y/z) from the buffer: rest + clip × weight + the
	 * additive layers. Zero (a straight elbow) without a clip.
	 */
	public static void forearmLocal(PoseBuffer buf, PartPose rest, int bone, float[] out) {
		float[] v = buf.v;
		int o = bone * PoseBuffer.STRIDE;
		if (o + PoseBuffer.ADD_ROT + 2 >= v.length) {
			out[0] = out[1] = out[2] = 0;
			return;
		}
		float w = v[o + PoseBuffer.W_ROT];
		out[0] = rest.xRot + v[o] * w + v[o + PoseBuffer.ADD_ROT];
		out[1] = rest.yRot + v[o + 1] * w + v[o + PoseBuffer.ADD_ROT + 1];
		out[2] = rest.zRot + v[o + 2] * w + v[o + PoseBuffer.ADD_ROT + 2];
	}

	private final float[] local = new float[3];
	private final Quaternionf qa = new Quaternionf(), ql = new Quaternionf();

	/** The forearm in model space: hinged at the elbow (rest position, in the arm's frame), turned by its local rotation. */
	private void forearm(PartPose[] parts, PartPose[] rest, PoseBuffer buf, int armBone, int foreBone) {
		PartPose arm = parts[armBone];
		PartPose fo = parts[foreBone];
		PartPose r = rest[foreBone];
		forearmLocal(buf, r, foreBone, this.local);
		this.qa.rotationZYX(arm.zRot, arm.yRot, arm.xRot);
		this.tmp.set(r.x * arm.xScale, r.y * arm.yScale, r.z * arm.zScale);
		this.qa.transform(this.tmp);
		fo.x = arm.x + this.tmp.x;
		fo.y = arm.y + this.tmp.y;
		fo.z = arm.z + this.tmp.z;
		this.ql.rotationZYX(this.local[2], this.local[1], this.local[0]);
		this.part.set(this.qa.mul(this.ql));
		this.part.getEulerAnglesZYX(this.tmp);
		fo.xRot = this.tmp.x;
		fo.yRot = this.tmp.y;
		fo.zRot = this.tmp.z;
		fo.xScale = arm.xScale;
		fo.yScale = arm.yScale;
		fo.zScale = arm.zScale;
	}

	private void solveParts(PartPose[] parts, PartPose[] rest, PoseBuffer buf) {
		float[] v = buf.v;
		for (int b = HumanoidRig.FIRST_PART; b <= HumanoidRig.LAST_PART; b++) {
			int o = b * PoseBuffer.STRIDE;
			PartPose p = parts[b];
			PartPose r = rest[b];
			float w = v[o + PoseBuffer.W_ROT];
			if (w > 0) {
				p.xRot += (r.xRot + v[o] - p.xRot) * w;
				p.yRot += (r.yRot + v[o + 1] - p.yRot) * w;
				p.zRot += (r.zRot + v[o + 2] - p.zRot) * w;
			}
			w = v[o + PoseBuffer.W_POS];
			if (w > 0) {
				p.x += (r.x + v[o + 3] - p.x) * w;
				p.y += (r.y + v[o + 4] - p.y) * w;
				p.z += (r.z + v[o + 5] - p.z) * w;
			}
			w = v[o + PoseBuffer.W_SCALE];
			if (w > 0) {
				p.xScale += (r.xScale + v[o + 6] - p.xScale) * w;
				p.yScale += (r.yScale + v[o + 7] - p.yScale) * w;
				p.zScale += (r.zScale + v[o + 8] - p.zScale) * w;
			}
			// Additive layers (hit reactions, breathing) on top of everything else.
			p.xRot += v[o + PoseBuffer.ADD_ROT];
			p.yRot += v[o + PoseBuffer.ADD_ROT + 1];
			p.zRot += v[o + PoseBuffer.ADD_ROT + 2];
			p.x += v[o + PoseBuffer.ADD_POS];
			p.y += v[o + PoseBuffer.ADD_POS + 1];
			p.z += v[o + PoseBuffer.ADD_POS + 2];
		}
		int t = HumanoidRig.TORSO * PoseBuffer.STRIDE;
		float wr = v[t + PoseBuffer.W_ROT];
		float wp = v[t + PoseBuffer.W_POS];
		float ax = v[t + PoseBuffer.ADD_ROT], ay = v[t + PoseBuffer.ADD_ROT + 1], az = v[t + PoseBuffer.ADD_ROT + 2];
		float px = v[t + PoseBuffer.ADD_POS], py = v[t + PoseBuffer.ADD_POS + 1], pz = v[t + PoseBuffer.ADD_POS + 2];
		boolean rotates = wr > 0 || ax != 0 || ay != 0 || az != 0;
		if (!rotates && wp <= 0 && px == 0 && py == 0 && pz == 0) {
			return;
		}
		// The torso has nothing underneath (it is virtual): lerp(0, offset, weight), plus the additive layers.
		this.torso.rotationZYX(v[t + 2] * wr + az, v[t + 1] * wr + ay, v[t] * wr + ax);
		float dx = v[t + 3] * wp + px, dy = v[t + 4] * wp + py, dz = v[t + 5] * wp + pz;
		wr = rotates ? 1 : 0;
		for (int b : CARRIED) {
			PartPose p = parts[b];
			this.tmp.set(p.x - HumanoidRig.HIP_X, p.y - HumanoidRig.HIP_Y, p.z - HumanoidRig.HIP_Z);
			this.torso.transform(this.tmp);
			p.x = this.tmp.x + HumanoidRig.HIP_X + dx;
			p.y = this.tmp.y + HumanoidRig.HIP_Y + dy;
			p.z = this.tmp.z + HumanoidRig.HIP_Z + dz;
			if (wr > 0) {
				this.part.rotationZYX(p.zRot, p.yRot, p.xRot);
				this.torso.mul(this.part, this.part);
				this.part.getEulerAnglesZYX(this.tmp);
				p.xRot = this.tmp.x;
				p.yRot = this.tmp.y;
				p.zRot = this.tmp.z;
			}
		}
	}

	/**
	 * The root bone as a transform of model space in blocks (the frame right after vanilla's
	 * {@code translate(0, -1.501, 0)}): rotation about {@link HumanoidRig#ROOT_PIVOT_Y}, then the root's offset. Returns
	 * false (and leaves {@code out} alone) when the root carries no weight.
	 */
	public boolean rootTransform(PoseBuffer buf, Matrix4f out) {
		int o = HumanoidRig.ROOT * PoseBuffer.STRIDE;
		float[] v = buf.v;
		float wr = v[o + PoseBuffer.W_ROT];
		float wp = v[o + PoseBuffer.W_POS];
		float ax = v[o + PoseBuffer.ADD_ROT], ay = v[o + PoseBuffer.ADD_ROT + 1], az = v[o + PoseBuffer.ADD_ROT + 2];
		float ux = v[o + PoseBuffer.ADD_POS], uy = v[o + PoseBuffer.ADD_POS + 1], uz = v[o + PoseBuffer.ADD_POS + 2];
		if (wr <= 0 && wp <= 0 && ax == 0 && ay == 0 && az == 0 && ux == 0 && uy == 0 && uz == 0) {
			return false;
		}
		float px = HumanoidRig.ROOT_PIVOT_X / 16, py = HumanoidRig.ROOT_PIVOT_Y / 16, pz = HumanoidRig.ROOT_PIVOT_Z / 16;
		out.identity()
			.translate((v[o + 3] * wp + ux) / 16, (v[o + 4] * wp + uy) / 16, (v[o + 5] * wp + uz) / 16)
			.translate(px, py, pz)
			.rotate(this.quat.rotationZYX(v[o + 2] * wr + az, v[o + 1] * wr + ay, v[o] * wr + ax))
			.translate(-px, -py, -pz);
		return true;
	}

	/**
	 * Where a point given in a bone's frame (model pixels) ends up in model space, in pixels: vanilla's
	 * {@code ModelPart.translateAndRotate} (translate, rotate ZYX, scale) applied to the point.
	 */
	public static Vector3f partToModel(PartPose p, float x, float y, float z, Vector3f out) {
		out.set(x * p.xScale, y * p.yScale, z * p.zScale);
		new Quaternionf().rotationZYX(p.zRot, p.yRot, p.xRot).transform(out);
		return out.add(p.x, p.y, p.z);
	}

	/**
	 * Model space (pixels, Y down, front -Z) to entity-local blocks (Y up from the feet, facing +Z, +X the entity's left),
	 * through vanilla's {@code scale(-1,-1,1)}, the renderer's model scale and {@code translate(0,-1.501,0)} with a body
	 * yaw of zero. {@code root} (model-space blocks) may be null.
	 */
	public static Vector3f modelToEntity(Vector3f modelPx, Matrix4f root, float modelScale, Vector3f out) {
		out.set(modelPx).div(16);
		if (root != null) {
			root.transformPosition(out);
		}
		return out.set(modelScale * out.x, modelScale * (HumanoidRig.MODEL_Y_OFFSET - out.y), -modelScale * out.z);
	}
}
