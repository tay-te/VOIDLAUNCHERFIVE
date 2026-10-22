package dev.voidmc.fray.motion.bake;

import dev.voidmc.fray.motion.Channel;
import dev.voidmc.fray.motion.Clip;
import dev.voidmc.fray.motion.ClipSampler;
import dev.voidmc.fray.motion.FirstPersonRig;
import dev.voidmc.fray.motion.HumanoidRig;
import dev.voidmc.fray.motion.HumanoidSolver;
import dev.voidmc.fray.motion.PartPose;
import dev.voidmc.fray.motion.PoseBuffer;
import dev.voidmc.fray.motion.Rig;
import java.util.List;
import java.util.Locale;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * The bake's looks checks, measured on the same solvers the client draws with (ANIMATION.md §6 "validation tests"):
 * <ul>
 * <li><b>ground</b>: the lowest point of the posed body (armour included: boxes grown by a pixel), sampled six times a
 * tick. A dodge or a plunge that dips below 0 clips into the floor. Fails the build when the clip lists
 * {@code "checks": ["ground"]} and goes lower than half a pixel.</li>
 * <li><b>fp_framing</b>: in first person, how much of the screen the arm and the blade cover (70° vertical field
 * of view, 16:9), through the {@link FirstPersonRig} {@code FirstPersonBody} draws with (the arm box cut at the near
 * plane, its outline clipped to the screen). A hand that reaches the camera counts as the whole screen. Fails at more
 * than {@link #FP_LIMIT} ({@link #FP_LIMIT_STRIKE} on the strike's ticks) when listed; the crosshair may be covered only on the strike's ticks (ACTIVE ± 1).</li>
 * <li><b>fp_match</b>: first and third person do the same move ({@link FirstPersonMatch}).</li>
 * <li><b>euler</b>: two keys that differ by more than 150° on one axis (per-component blending would unwind the long
 * way): a warning.</li>
 * </ul>
 */
public final class Checks {
	/** Pixels: what we tolerate below the ground (half a pixel). */
	public static final float GROUND_TOLERANCE = 0.5F / 16F;
	/** First-person screen coverage limit (arm and blade). */
	public static final float FP_LIMIT = 0.15F;
	/** On the strike's ticks (ACTIVE ± 1) the swing may fill more of the screen. */
	public static final float FP_LIMIT_STRIKE = 0.22F;

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
	 * First-person framing of a clip: the peak share of the screen the arms and the blade
	 * cover, whether an arm reaches the camera, and how many ticks outside the strike (ACTIVE ± 1) the arm or blade
	 * covers the crosshair.
	 */
	public record Framing(float maxCoverage, float atSeconds, boolean throughCamera, int crosshairTicks, int armEndTicks, boolean overLimit) {
	}

	/** First-person screen coverage over the clip (clips that draw first-person arms). */
	public static Framing firstPerson(Clip clip) {
		if (!clip.rig.humanoid || !drawsArms(clip)) {
			return new Framing(0, 0, false, 0, 0, false);
		}
		float max = 0, at = 0;
		boolean through = false, over = false;
		java.util.Set<Integer> cross = new java.util.TreeSet<>(), ends = new java.util.TreeSet<>();
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
			over |= cov[0] > (nearStrike(clip, tick) ? FP_LIMIT_STRIKE : FP_LIMIT);
			if (cov[2] > 0 && !nearStrike(clip, tick)) {
				cross.add(tick);
			}
			if (cov[3] > 0) {
				ends.add(tick);
			}
		}
		return new Framing(max, at, through, cross.size(), ends.size(), over);
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

	/** Fray draws its own first-person hands only for clips with first-person rig curves (else vanilla's hands). */
	public static boolean drawsArms(Clip clip) {
		return FirstPersonRig.animates(clip);
	}

	private static final float NEAR = 0.05F;

	private static float[] project(Vector3f v, float tanX, float tanY) {
		return new float[] {v.x / (-v.z * tanX), v.y / (-v.z * tanY)};
	}

	/** The convex hull (Andrew's monotone chain), counter-clockwise. */
	private static List<float[]> hull(List<float[]> pts) {
		List<float[]> p = new java.util.ArrayList<>(pts);
		p.sort((a, b) -> a[0] != b[0] ? Float.compare(a[0], b[0]) : Float.compare(a[1], b[1]));
		float[][] h = new float[p.size() * 2][];
		int k = 0;
		for (float[] q : p) {
			while (k >= 2 && cross(h[k - 2], h[k - 1], q) <= 0) {
				k--;
			}
			h[k++] = q;
		}
		for (int i = p.size() - 2, lo = k + 1; i >= 0; i--) {
			float[] q = p.get(i);
			while (k >= lo && cross(h[k - 2], h[k - 1], q) <= 0) {
				k--;
			}
			h[k++] = q;
		}
		List<float[]> out = new java.util.ArrayList<>();
		for (int i = 0; i < k - 1; i++) {
			out.add(h[i]);
		}
		return out;
	}

	private static float cross(float[] o, float[] a, float[] b) {
		return (a[0] - o[0]) * (b[1] - o[1]) - (a[1] - o[1]) * (b[0] - o[0]);
	}

	/** Sutherland-Hodgman against the screen square [-1, 1]². */
	private static List<float[]> clipToScreen(List<float[]> poly) {
		float[][] planes = {{1, 0, 1}, {-1, 0, 1}, {0, 1, 1}, {0, -1, 1}};
		List<float[]> out = poly;
		for (float[] pl : planes) {
			List<float[]> in = out;
			out = new java.util.ArrayList<>();
			for (int i = 0; i < in.size(); i++) {
				float[] a = in.get(i), b = in.get((i + 1) % in.size());
				float da = pl[0] * a[0] + pl[1] * a[1] + pl[2], db = pl[0] * b[0] + pl[1] * b[1] + pl[2];
				if (da >= 0) {
					out.add(a);
				}
				if ((da >= 0) != (db >= 0)) {
					float f = da / (da - db);
					out.add(new float[] {a[0] + (b[0] - a[0]) * f, a[1] + (b[1] - a[1]) * f});
				}
			}
			if (out.isEmpty()) {
				break;
			}
		}
		return out;
	}

	private static float polygonArea(List<float[]> poly) {
		float a = 0;
		for (int i = 0; i < poly.size(); i++) {
			float[] p0 = poly.get(i), p1 = poly.get((i + 1) % poly.size());
			a += p0[0] * p1[1] - p1[0] * p0[1];
		}
		return Math.abs(a) / 2;
	}

	private static boolean contains(List<float[]> poly, float x, float y) {
		boolean pos = true, neg = true;
		for (int i = 0; i < poly.size(); i++) {
			float[] p0 = poly.get(i), p1 = poly.get((i + 1) % poly.size());
			float cz = (p1[0] - p0[0]) * (y - p0[1]) - (p1[1] - p0[1]) * (x - p0[0]);
			pos &= cz >= 0;
			neg &= cz <= 0;
		}
		return pos || neg;
	}

	private static final float[][] BLADE = {{-0.35F, -0.35F}, {0.45F, 0.45F}};
	private static final float BLADE_HALF = 0.06F;

	/** {coverage 0..1, 1 if an arm crosses the near plane, 1 if the arm or blade covers the crosshair} at one moment. */
	static float[] coverage(Clip clip, float t) {
		FirstPersonRig.Pose pose = FirstPersonRig.sample(clip, clip.localTime(t), new FirstPersonRig.Pose());
		float tanY = (float) Math.tan(Math.toRadians(35)), tanX = tanY * 16 / 9F;
		float area = 0;
		boolean through = false, cross = false, endSeen = false;
		Vector3f v = new Vector3f();
		for (int side = 0; side < (pose.off > 0.5F ? 2 : 1); side++) {
			boolean left = side == 1;
			Matrix4f m = FirstPersonRig.arm(pose.hand(left), left, new Matrix4f());
			Rig.Cube c0 = Rig.HUMANOID.cubes[left ? HumanoidRig.LEFT_ARM : HumanoidRig.RIGHT_ARM].getFirst();
			// The arm box with its added sleeve (a raised hand).
			float ext = FirstPersonRig.armExtension(pose.hand(left), left) * FirstPersonRig.SEGMENT;
			Rig.Cube c = new Rig.Cube(c0.x(), c0.y() - ext, c0.z(), c0.dx(), c0.dy() + ext, c0.dz(), c0.u(), c0.v(), c0.inflate(), c0.mirror());
			// The arm box in front of the near plane (edges cut there), projected, its convex hull clipped to the screen.
			Vector3f[] corner = new Vector3f[8];
			for (int k = 0; k < 8; k++) {
				corner[k] = m.transformPosition(new Vector3f(((k & 1) == 0 ? c.x() : c.x() + c.dx()) / 16, ((k & 2) == 0 ? c.y() : c.y() + c.dy()) / 16,
					((k & 4) == 0 ? c.z() : c.z() + c.dz()) / 16));
			}
			List<float[]> pts = new java.util.ArrayList<>();
			for (int k = 0; k < 8; k++) {
				if (corner[k].z <= -NEAR) {
					pts.add(project(corner[k], tanX, tanY));
				}
				for (int bit = 1; bit < 8; bit <<= 1) {
					int o = k | bit;
					if (o == k) {
						continue;
					}
					Vector3f p0 = corner[k], p1 = corner[o];
					if ((p0.z <= -NEAR) != (p1.z <= -NEAR)) {
						float f = (-NEAR - p0.z) / (p1.z - p0.z);
						pts.add(project(new Vector3f(p0).lerp(p1, f), tanX, tanY));
					}
				}
			}
			// The arm's far end (the shoulder): on screen the arm would end in the air, so it must leave the screen.
			Vector3f end = m.transformPosition(new Vector3f(FirstPersonRig.GRIP_ARM_X / 16F * (left ? -1 : 1), c.y() / 16, 0));
			if (end.z < -NEAR) {
				float[] e = project(end, tanX, tanY);
				endSeen |= Math.abs(e[0]) < 0.98F && Math.abs(e[1]) < 0.98F;
			}
			// The hand end of the arm reaching the near plane is an arm in the face.
			Vector3f hand = m.transformPosition(new Vector3f(FirstPersonRig.GRIP_ARM_X / 16F * (left ? -1 : 1), FirstPersonRig.GRIP_ARM_Y / 16F, 0));
			through |= hand.z > -0.15F;
			if (pts.size() < 3) {
				continue;
			}
			List<float[]> hull = clipToScreen(hull(pts));
			if (hull.size() >= 3) {
				area += polygonArea(hull) / 4;
				cross |= contains(hull, 0, 0);
			}
		}
		// The blade in the main hand: a thin quad from grip to tip.
		Matrix4f item = FirstPersonRig.item(pose.right, false, new Matrix4f());
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
		return new float[] {through ? 1 : Math.min(1, area), through ? 1 : 0, cross ? 1 : 0, endSeen ? 1 : 0};
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
