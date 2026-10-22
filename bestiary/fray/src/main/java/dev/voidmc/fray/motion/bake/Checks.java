package dev.voidmc.fray.motion.bake;

import dev.voidmc.fray.motion.Channel;
import dev.voidmc.fray.motion.Clip;
import dev.voidmc.fray.motion.ClipSampler;
import dev.voidmc.fray.motion.HumanoidRig;
import dev.voidmc.fray.motion.HumanoidSolver;
import dev.voidmc.fray.motion.PartPose;
import dev.voidmc.fray.motion.PoseBuffer;
import dev.voidmc.fray.motion.Rig;
import java.util.List;
import java.util.Locale;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * The bake's looks checks, measured on the same solvers the client draws with (ANIMATION.md §6 "validation tests"):
 * <ul>
 * <li><b>ground</b>: the lowest point of the posed body (armour included: boxes grown by a pixel), sampled six times a
 * tick. A dodge or a plunge that dips below 0 clips into the floor. Fails the build when the clip lists
 * {@code "checks": ["ground"]} and goes lower than half a pixel.</li>
 * <li><b>fp_framing</b>: in first person, how much of the screen the arms cover (70° vertical field of view, 16:9),
 * through the same transform {@code FirstPersonBody} uses. An arm that reaches the camera's near plane counts as the
 * whole screen. Fails at more than 45 % when listed.</li>
 * <li><b>euler</b>: two keys that differ by more than 150° on one axis (per-component blending would unwind the long
 * way): a warning.</li>
 * </ul>
 */
public final class Checks {
	/** Pixels: what we tolerate below the ground (half a pixel). */
	public static final float GROUND_TOLERANCE = 0.5F / 16F;
	public static final float FP_LIMIT = 0.45F;
	private static final float EYE_HEIGHT = 1.62F;

	private Checks() {
	}

	public record Ground(float lowest, float atSeconds) {
	}

	/** The lowest point of the clip over its length, in blocks above the ground, and when. */
	public static Ground ground(Clip clip) {
		float low = Float.MAX_VALUE, at = 0;
		int steps = Math.max(1, clip.lengthTicks() * 6);
		Posed posed = new Posed(clip.rig, clip.modelScale);
		for (int i = 0; i <= steps; i++) {
			float t = clip.length * i / steps;
			float y = posed.at(clip, t).lowest(0);
			if (y < low) {
				low = y;
				at = t;
			}
		}
		return new Ground(low, at);
	}

	public record Framing(float maxCoverage, float atSeconds, boolean throughCamera) {
	}

	/** First-person screen coverage of the arms over the clip (clips with an {@code fp} block). */
	public static Framing firstPerson(Clip clip) {
		if (clip.fp == null || !clip.rig.humanoid || clip.fp.hideArms() || !drawsArms(clip.fp)) {
			return new Framing(0, 0, false);
		}
		float max = 0, at = 0;
		boolean through = false;
		int steps = Math.max(1, clip.lengthTicks() * 3);
		for (int i = 0; i <= steps; i++) {
			float t = clip.length * i / steps;
			float[] cov = coverage(clip, t);
			if (cov[0] > max) {
				max = cov[0];
				at = t;
			}
			through |= cov[1] > 0;
		}
		return new Framing(max, at, through);
	}

	/** Fray draws its own first-person arms only for clips with first-person bone curves or a framing (else vanilla's hands). */
	public static boolean drawsArms(Clip.FirstPerson fp) {
		return !fp.hideArms() && !"fray:default".equals(fp.framing());
	}

	/** {coverage 0..1, 1 if an arm crosses the near plane} at one moment. */
	static float[] coverage(Clip clip, float t) {
		PoseBuffer buf = new PoseBuffer(Rig.HUMANOID);
		float lt = clip.localTime(t);
		ClipSampler.accumulate(clip, lt, 1, buf, new float[3]);
		Clip.FirstPerson fp = clip.fp;
		// First person is the third-person pose seen from the eye (FirstPersonFrame): no curves of its own.
		PartPose[] rest = HumanoidRig.restPose();
		PartPose[] parts = HumanoidRig.restPose();
		HumanoidSolver solver = new HumanoidSolver();
		solver.solve(parts, rest, buf);
		Matrix4f view = FirstPersonFrame.cameraFromModel(parts, buf, fp, solver, new Matrix4f());
		float tanY = (float) Math.tan(Math.toRadians(35)), tanX = tanY * 16 / 9F;
		float area = 0;
		boolean through = false;
		boolean left = FirstPersonFrame.leftArm(parts, fp) > 0.5F;
		for (int arm : left ? new int[] {HumanoidRig.RIGHT_ARM, HumanoidRig.LEFT_ARM} : new int[] {HumanoidRig.RIGHT_ARM}) {
			if (fp.hideArms()) {
				continue;
			}
			PartPose p = parts[arm];
			Matrix4f m = new Matrix4f(view).translate(p.x / 16, p.y / 16, p.z / 16).rotate(new Quaternionf().rotationZYX(p.zRot, p.yRot, p.xRot));
			Rig.Cube c = Rig.HUMANOID.cubes[arm].getFirst();
			float minX = 1, maxX = -1, minY = 1, maxY = -1;
			Vector3f v = new Vector3f();
			for (int k = 0; k < 8; k++) {
				v.set(((k & 1) == 0 ? c.x() : c.x() + c.dx()) / 16, ((k & 2) == 0 ? c.y() : c.y() + c.dy()) / 16, ((k & 4) == 0 ? c.z() : c.z() + c.dz()) / 16);
				m.transformPosition(v);
				if (v.z > -0.05F) {
					through = true;
					continue;
				}
				float nx = v.x / (-v.z * tanX), ny = v.y / (-v.z * tanY);
				minX = Math.min(minX, nx);
				maxX = Math.max(maxX, nx);
				minY = Math.min(minY, ny);
				maxY = Math.max(maxY, ny);
			}
			minX = Math.max(-1, minX);
			maxX = Math.min(1, maxX);
			minY = Math.max(-1, minY);
			maxY = Math.min(1, maxY);
			if (maxX > minX && maxY > minY) {
				area += (maxX - minX) * (maxY - minY) / 4;
			}
		}
		return new float[] {through ? 1 : Math.min(1, area), through ? 1 : 0};
	}

	/** Keys more than 150° apart on one axis, as warnings. */
	public static void euler(Clip clip, List<String> warnings) {
		for (int b = 0; b < clip.bones(); b++) {
			Channel c = clip.rotation[b];
			if (c == null) {
				continue;
			}
			for (int i = 1; i < c.keys(); i++) {
				if (c.interps[i] == dev.voidmc.fray.motion.Interp.STEP) {
					continue;
				}
				for (int k = 0; k < 3; k++) {
					float d = Math.abs(c.values[i * 3 + k] - c.values[(i - 1) * 3 + k]);
					if (d > 150) {
						warnings.add(String.format(Locale.ROOT, "%s: %s rotation jumps %.0f degrees on axis %d between %.2f s and %.2f s (blends unwind the long way)",
							clip.id, clip.rig.name(b), d, k, c.times[i - 1], c.times[i]));
					}
				}
			}
		}
	}
}
