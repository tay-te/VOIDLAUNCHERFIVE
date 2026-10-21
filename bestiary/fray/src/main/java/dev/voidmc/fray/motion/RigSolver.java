package dev.voidmc.fray.motion;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Poses a custom rig (real hierarchy, real joints) from a {@link PoseBuffer}: each bone is
 * {@code lerp(rest, rest + offset, weight) + additive}, relative to its parent, exactly the fields vanilla's
 * {@code ModelPart} draws with. {@link #forward} chains them the way {@code ModelPart.translateAndRotate} does, so the
 * bake's hit volumes sit where the client draws the bones.
 *
 * <p>Common code, no allocation in {@link #solve}; not thread-safe (one instance per thread).
 */
public final class RigSolver {
	private final Quaternionf quat = new Quaternionf();

	/** Writes the posed local transforms into {@code out} (one {@link PartPose} per bone). {@code buf} may be null (rest). */
	public void solve(Rig rig, PoseBuffer buf, PartPose[] out) {
		PartPose[] rest = rig.rest;
		for (int b = 0; b < rest.length; b++) {
			PartPose p = out[b].set(rest[b]);
			if (buf == null || b >= buf.bones) {
				continue;
			}
			int o = b * PoseBuffer.STRIDE;
			float[] v = buf.v;
			float w = v[o + PoseBuffer.W_ROT];
			if (w > 0) {
				p.xRot += v[o] * w;
				p.yRot += v[o + 1] * w;
				p.zRot += v[o + 2] * w;
			}
			w = v[o + PoseBuffer.W_POS];
			if (w > 0) {
				p.x += v[o + 3] * w;
				p.y += v[o + 4] * w;
				p.z += v[o + 5] * w;
			}
			w = v[o + PoseBuffer.W_SCALE];
			if (w > 0) {
				p.xScale += v[o + 6] * w;
				p.yScale += v[o + 7] * w;
				p.zScale += v[o + 8] * w;
			}
			p.xRot += v[o + PoseBuffer.ADD_ROT];
			p.yRot += v[o + PoseBuffer.ADD_ROT + 1];
			p.zRot += v[o + PoseBuffer.ADD_ROT + 2];
			p.x += v[o + PoseBuffer.ADD_POS];
			p.y += v[o + PoseBuffer.ADD_POS + 1];
			p.z += v[o + PoseBuffer.ADD_POS + 2];
		}
	}

	/**
	 * Model-space matrices (blocks; vanilla's model space: Y down, front -Z) of every bone: the parent's matrix, then
	 * {@code translate(x/16, y/16, z/16)}, {@code rotationZYX(z, y, x)}, scale.
	 */
	public void forward(Rig rig, PartPose[] local, Matrix4f[] world) {
		for (int b = 0; b < local.length; b++) {
			PartPose p = local[b];
			Matrix4f m = world[b];
			if (rig.parent[b] >= 0) {
				m.set(world[rig.parent[b]]);
			} else {
				m.identity();
			}
			m.translate(p.x / 16F, p.y / 16F, p.z / 16F);
			if (p.xRot != 0 || p.yRot != 0 || p.zRot != 0) {
				m.rotate(this.quat.rotationZYX(p.zRot, p.yRot, p.xRot));
			}
			if (p.xScale != 1 || p.yScale != 1 || p.zScale != 1) {
				m.scale(p.xScale, p.yScale, p.zScale);
			}
		}
	}

	/** A point given in a bone's frame (model pixels) in model space (pixels), from that bone's {@link #forward} matrix. */
	public static Vector3f boneToModel(Matrix4f bone, float x, float y, float z, Vector3f out) {
		out.set(x / 16F, y / 16F, z / 16F);
		bone.transformPosition(out);
		return out.mul(16);
	}

	public static PartPose[] poses(int n) {
		PartPose[] out = new PartPose[n];
		for (int i = 0; i < n; i++) {
			out[i] = new PartPose();
		}
		return out;
	}

	public static Matrix4f[] matrices(int n) {
		Matrix4f[] out = new Matrix4f[n];
		for (int i = 0; i < n; i++) {
			out[i] = new Matrix4f();
		}
		return out;
	}
}
