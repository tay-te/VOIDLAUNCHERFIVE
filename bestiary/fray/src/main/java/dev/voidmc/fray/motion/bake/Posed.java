package dev.voidmc.fray.motion.bake;

import dev.voidmc.fray.motion.Clip;
import dev.voidmc.fray.motion.ClipSampler;
import dev.voidmc.fray.motion.HumanoidRig;
import dev.voidmc.fray.motion.HumanoidSolver;
import dev.voidmc.fray.motion.PartPose;
import dev.voidmc.fray.motion.PoseBuffer;
import dev.voidmc.fray.motion.Rig;
import dev.voidmc.fray.motion.RigSolver;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * A clip posed at one moment, for the bake: where any point of any bone is, in entity-local blocks (Y up from the feet,
 * facing +Z, +X the entity's left), computed with the client's own solvers ({@link HumanoidSolver} over the humanoid's
 * rest pose, {@link RigSolver} for custom rigs). The clip is taken at full weight, as the bake assumes through ACTIVE.
 */
public final class Posed {
	private final Rig rig;
	private final float modelScale;
	private final PartPose[] parts;
	private final Matrix4f[] world;
	private final Matrix4f root = new Matrix4f();
	private boolean hasRoot;

	public Posed(Rig rig, float modelScale) {
		this.rig = rig;
		this.modelScale = modelScale;
		this.parts = rig.restPose();
		this.world = RigSolver.matrices(rig.bones());
	}

	/** Poses the clip at {@code seconds} (wrapped by its loop mode) over the rest pose. */
	public Posed at(Clip clip, float seconds) {
		PoseBuffer buf = new PoseBuffer(this.rig);
		ClipSampler.accumulate(clip, clip.localTime(seconds), 1, buf, new float[3]);
		return this.with(buf);
	}

	public Posed with(PoseBuffer buf) {
		if (this.rig.humanoid) {
			PartPose[] rest = this.rig.restPose();
			for (int i = 0; i < this.parts.length; i++) {
				this.parts[i].set(rest[i]);
			}
			new HumanoidSolver().solve(this.parts, rest, buf);
			this.hasRoot = new HumanoidSolver().rootTransform(buf, this.root);
		} else {
			RigSolver s = new RigSolver();
			s.solve(this.rig, buf, this.parts);
			s.forward(this.rig, this.parts, this.world);
		}
		return this;
	}

	/** The posed local transform of a bone (humanoid: the part's pose in model space; custom: relative to its parent). */
	public PartPose part(int bone) {
		return this.parts[bone];
	}

	/** A point in a bone's frame (model pixels) → entity-local blocks. */
	public Vector3f point(int bone, float x, float y, float z, Vector3f out) {
		if (this.rig.humanoid) {
			HumanoidSolver.partToModel(this.parts[bone], x, y, z, out);
			return HumanoidSolver.modelToEntity(out, this.hasRoot ? this.root : null, this.modelScale, out);
		}
		RigSolver.boneToModel(this.world[bone], x, y, z, out);
		return HumanoidSolver.modelToEntity(out, null, this.modelScale, out);
	}

	/**
	 * The lowest point of the posed geometry, in entity-local blocks (0 is the ground), with every box grown by
	 * {@code inflate} pixels (1 covers vanilla armour's outer layer).
	 */
	public float lowest(float inflate) {
		float min = Float.MAX_VALUE;
		Vector3f p = new Vector3f();
		for (int b = 0; b < this.rig.bones(); b++) {
			if (this.rig.humanoid && (b < HumanoidRig.FIRST_PART || b > HumanoidRig.LAST_PART)) {
				continue;
			}
			for (Rig.Cube c : this.rig.cubes[b]) {
				float g = c.inflate() + inflate;
				for (int k = 0; k < 8; k++) {
					float x = (k & 1) == 0 ? c.x() - g : c.x() + c.dx() + g;
					float y = (k & 2) == 0 ? c.y() - g : c.y() + c.dy() + g;
					float z = (k & 4) == 0 ? c.z() - g : c.z() + c.dz() + g;
					this.point(b, x, y, z, p);
					min = Math.min(min, p.y);
				}
			}
		}
		return min;
	}
}
