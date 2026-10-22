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
	/** First-person screen coverage limit (arms and blade, the default swing scale). */
	public static final float FP_LIMIT = 0.15F;
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

	/**
	 * First-person framing of a clip at the default swing scale: the peak share of the screen the arms and the blade
	 * cover, whether an arm reaches the camera, and how many ticks outside the strike (ACTIVE ± 1) the arm or blade
	 * covers the crosshair.
	 */
	public record Framing(float maxCoverage, float atSeconds, boolean throughCamera, int crosshairTicks) {
	}

	/** First-person screen coverage over the clip (clips that draw first-person arms). */
	public static Framing firstPerson(Clip clip) {
		if (clip.fp == null || !clip.rig.humanoid || clip.fp.hideArms() || !drawsArms(clip.fp)) {
			return new Framing(0, 0, false, 0);
		}
		float max = 0, at = 0;
		boolean through = false;
		java.util.Set<Integer> cross = new java.util.TreeSet<>();
		int steps = Math.max(1, clip.lengthTicks() * 3);
		for (int i = 0; i <= steps; i++) {
			float t = clip.length * i / steps;
			float[] cov = coverage(clip, t);
			if (cov[0] > max) {
				max = cov[0];
				at = t;
			}
			through |= cov[1] > 0;
			int tick = Math.round(t * 20);
			if (cov[2] > 0 && !nearStrike(clip, tick)) {
				cross.add(tick);
			}
		}
		return new Framing(max, at, through, cross.size());
	}

	private static boolean nearStrike(Clip clip, int tick) {
		float a = clip.marker("windup_end"), b = clip.marker("active_end");
		return a >= 0 && b >= 0 && tick >= Math.round(a * 20) - 1 && tick <= Math.round(b * 20) + 1;
	}

	/**
	 * Hand reach: how far the right hand (the bottom of the arm, where the grip is) is from the chest centre, blocks at the
	 * player's scale, sampled six times a tick on the solved third-person pose (torso included; the root moves both).
	 *
	 * @param max        the furthest anywhere
	 * @param maxOutside the furthest outside the strike (ACTIVE ± 1 tick; the whole clip when it has no strike)
	 * @param mean       the average over the clip
	 * @param ticksOver  ticks outside the strike with the hand further than {@link #REACH_LIMIT}
	 */
	public record Reach(float max, float maxOutside, float mean, int ticksOver) {
	}

	/** Hands further than this from the chest outside the strike are reported (blocks). */
	public static final float REACH_LIMIT = 0.65F;

	public static Reach reach(Clip clip) {
		PoseBuffer buf = new PoseBuffer(Rig.HUMANOID);
		HumanoidSolver solver = new HumanoidSolver();
		float[] scratch = new float[3];
		float max = 0, outside = 0, sum = 0;
		int n = 0;
		java.util.Set<Integer> over = new java.util.TreeSet<>();
		int steps = Math.max(1, clip.lengthTicks() * 6);
		Vector3f hand = new Vector3f(), chest = new Vector3f();
		for (int i = 0; i <= steps; i++) {
			float t = clip.length * i / steps;
			buf.clear();
			ClipSampler.accumulate(clip, clip.localTime(t), 1, buf, scratch);
			PartPose[] rest = HumanoidRig.restPose();
			PartPose[] parts = HumanoidRig.restPose();
			solver.solve(parts, rest, buf);
			HumanoidSolver.partToModel(parts[HumanoidRig.RIGHT_FOREARM], 0, HumanoidRig.HAND_Y, 0, hand);
			HumanoidSolver.partToModel(parts[HumanoidRig.BODY], 0, 4, 0, chest);
			float d = hand.distance(chest) / 16 * HumanoidRig.PLAYER_SCALE;
			max = Math.max(max, d);
			sum += d;
			n++;
			int tick = Math.round(t * 20);
			if (!nearStrike(clip, tick)) {
				outside = Math.max(outside, d);
				if (d > REACH_LIMIT) {
					over.add(tick);
				}
			}
		}
		return new Reach(max, outside, n == 0 ? 0 : sum / n, over.size());
	}

	/** Fray draws its own first-person arms only for clips with a framing (else vanilla's hands). */
	public static boolean drawsArms(Clip.FirstPerson fp) {
		return !fp.hideArms() && !"fray:default".equals(fp.framing());
	}

	/** A sword in the hand (vanilla's handheld third-person transform), as a blade from grip to tip, model blocks. */
	private static final Matrix4f SWORD_R = new Matrix4f().translate(0, 4 / 16F, 0.5F / 16F)
		.rotate(new Quaternionf().rotationXYZ(0, (float) Math.toRadians(-90), (float) Math.toRadians(55))).scale(0.85F);
	private static final float[][] BLADE = {{-0.35F, -0.35F}, {0.45F, 0.45F}};
	private static final float BLADE_HALF = 0.06F;

	/** {coverage 0..1, 1 if an arm crosses the near plane, 1 if the arm or blade covers the crosshair} at one moment. */
	static float[] coverage(Clip clip, float t) {
		Clip.FirstPerson fp = clip.fp;
		FirstPersonFrame.Frame f = FirstPersonFrame.clip(clip, t * 20, fp, FirstPersonFrame.swing(fp, Float.parseFloat(System.getProperty("fray.fp.swing", String.valueOf(FirstPersonFrame.DEFAULT_SWING)))), new HumanoidSolver(),
			new FirstPersonFrame.Frame());
		float tanY = (float) Math.tan(Math.toRadians(35)), tanX = tanY * 16 / 9F;
		float area = 0;
		boolean through = false, cross = false;
		Vector3f v = new Vector3f();
		int[] arms = f.left > 0.5F ? new int[] {HumanoidRig.RIGHT_ARM, HumanoidRig.RIGHT_FOREARM, HumanoidRig.LEFT_ARM, HumanoidRig.LEFT_FOREARM}
			: new int[] {HumanoidRig.RIGHT_ARM, HumanoidRig.RIGHT_FOREARM};
		for (int arm : arms) {
			Matrix4f m = arm == HumanoidRig.RIGHT_ARM ? f.rightArm : arm == HumanoidRig.RIGHT_FOREARM ? f.rightFore : arm == HumanoidRig.LEFT_ARM ? f.leftArm : f.leftFore;
			Rig.Cube c = Rig.HUMANOID.cubes[arm].getFirst();
			float minX = 1, maxX = -1, minY = 1, maxY = -1;
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
			cross |= minX < 0 && maxX > 0 && minY < 0 && maxY > 0;
			minX = Math.max(-1, minX);
			maxX = Math.min(1, maxX);
			minY = Math.max(-1, minY);
			maxY = Math.min(1, maxY);
			if (maxX > minX && maxY > minY) {
				area += (maxX - minX) * (maxY - minY) / 4;
			}
		}
		// The blade in the right hand: a thin quad from grip to tip.
		Matrix4f item = new Matrix4f(f.rightItem).mul(SWORD_R);
		float[][] q = new float[4][];
		float d = BLADE_HALF / (float) Math.sqrt(2);
		float[][] corners = {{BLADE[0][0] + d, BLADE[0][1] - d}, {BLADE[1][0] + d, BLADE[1][1] - d}, {BLADE[1][0] - d, BLADE[1][1] + d}, {BLADE[0][0] - d, BLADE[0][1] + d}};
		boolean bladeOk = true;
		for (int k = 0; k < 4; k++) {
			v.set(corners[k][0], corners[k][1], 0);
			item.transformPosition(v);
			if (v.z > -0.05F) {
				bladeOk = false;
				break;
			}
			q[k] = new float[] {Math.max(-1.2F, Math.min(1.2F, v.x / (-v.z * tanX))), Math.max(-1.2F, Math.min(1.2F, v.y / (-v.z * tanY)))};
		}
		if (bladeOk) {
			float a2 = 0;
			for (int k = 0; k < 4; k++) {
				float[] p0 = q[k], p1 = q[(k + 1) % 4];
				a2 += p0[0] * p1[1] - p1[0] * p0[1];
			}
			area += Math.abs(a2) / 2 / 4;
			// The crosshair: the origin inside the quad (same side of every edge).
			boolean pos = true, neg = true;
			for (int k = 0; k < 4; k++) {
				float[] p0 = q[k], p1 = q[(k + 1) % 4];
				float cz = (p1[0] - p0[0]) * (0 - p0[1]) - (p1[1] - p0[1]) * (0 - p0[0]);
				pos &= cz >= 0;
				neg &= cz <= 0;
			}
			cross |= pos || neg;
		}
		return new float[] {through ? 1 : Math.min(1, area), through ? 1 : 0, cross ? 1 : 0};
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
