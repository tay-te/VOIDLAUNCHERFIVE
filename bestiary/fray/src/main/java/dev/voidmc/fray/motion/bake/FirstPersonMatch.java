package dev.voidmc.fray.motion.bake;

import dev.voidmc.fray.motion.Clip;
import dev.voidmc.fray.motion.ClipSampler;
import dev.voidmc.fray.motion.FirstPersonRig;
import dev.voidmc.fray.motion.HumanoidRig;
import dev.voidmc.fray.motion.HumanoidSolver;
import dev.voidmc.fray.motion.PartPose;
import dev.voidmc.fray.motion.PoseBuffer;
import dev.voidmc.fray.motion.Rig;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Do first and third person do the same move? (The bake's {@code fp_match} check.) The sword is measured in both, each
 * relative to its own view: first person from the camera (the {@link FirstPersonRig}), third person from the animated
 * head's eye (the solved third-person pose). Points are compared in view angles (degrees from the crosshair in the
 * screen's directions: the azimuthal projection about the view axis), which stay continuous for a blade behind the
 * shoulder, where a screen projection isn't.
 *
 * <ul>
 * <li><b>travel</b>: the direction the tip moves on screen, per tick, where both move at least {@link #MOVING} a tick;</li>
 * <li><b>blade</b>: the blade's angle on screen (grip to tip), per tick, where both are seen at least {@link #SEEN} long;</li>
 * <li>both over the move itself: from first person's first authored pose after vanilla's held sword
 * ({@link #firstCompared}) to its last one before the return ({@link #lastCompared}), skipping ticks still at rest
 * ({@link #nearRest}). Leaving and regaining rest, each view starts from and returns to its own rest, and the two
 * rests differ (vanilla's first-person sword stands up at the lower right, the third-person guard points it ahead);</li>
 * <li><b>phase</b>: which side the tip is on at the end of the wind-up, the strike's direction across the screen (the
 * tip from the tick before ACTIVE to its end), and the side of the follow-through (two ticks after);</li>
 * <li><b>edge</b>: how far the blade's flat turns into its direction of travel over the ACTIVE ticks, half a tick at a
 * time (0: the edge leads, 90: the flat leads), in each view.</li>
 * </ul>
 */
public final class FirstPersonMatch {
	/** Disagreement limit for directions and blade angles (degrees). */
	public static final float LIMIT = 25;
	/** The sides (wind-up, follow-through) are directions from the crosshair: a looser limit. */
	public static final float SIDE_LIMIT = 45;
	/** Tip speed (degrees of view a tick) below which a tick's direction isn't compared. */
	public static final float MOVING = 10;
	/** Blade length on screen (degrees of view) below which its angle isn't compared. */
	public static final float SEEN = 8;
	/** The edge must lead within this on the strike ticks (degrees between the travel and the blade's plane). */
	public static final float EDGE_LIMIT = 35;
	/** The blade's mid point must move at least this far in half a tick for its edge to be judged (blocks). */
	public static final float EDGE_SPEED = 0.04F;

	private FirstPersonMatch() {
	}

	/** One view of the sword at one moment: grip, tip (view angles) and, in 3D view space, the blade's mid point and flat's normal. */
	public record Sample(float gyaw, float gpitch, float tyaw, float tpitch, Vector3f mid, Vector3f normal) {
		float bladeAngle() {
			return (float) Math.toDegrees(Math.atan2(this.tpitch - this.gpitch, this.tyaw - this.gyaw));
		}

		float bladeLength() {
			return (float) Math.hypot(this.tpitch - this.gpitch, this.tyaw - this.gyaw);
		}
	}

	public record Result(int ticks, float travelMax, int travelAt, int travelCompared, float bladeMax, int bladeAt, int bladeCompared, float windup,
		float strike, float follow, boolean strikeCrosses, float edgeFp, float edgeTp, List<String> problems, Sample[] fp, Sample[] tp) {
		public String line() {
			return String.format(Locale.ROOT, "fp/tp match: travel max %.0f° (t%d, %d ticks), blade max %.0f° (t%d, %d ticks), wind-up side %.0f°, strike %.0f°%s, follow-through side %.0f°, edge fp %.0f° tp %.0f°",
				this.travelMax, this.travelAt, this.travelCompared, this.bladeMax, this.bladeAt, this.bladeCompared, this.windup, this.strike, this.strikeCrosses ? "" : " (crosses the other way)",
				this.follow, this.edgeFp, this.edgeTp);
		}
	}

	/** Whether the clip can be compared: a player clip with first-person rig curves. */
	public static boolean applies(Clip clip) {
		return clip.rig.humanoid && FirstPersonRig.animates(clip);
	}

	public static Result run(Clip clip) {
		int n = clip.lengthTicks();
		int steps = n * 2;
		Sample[] fp = new Sample[steps + 1], tp = new Sample[steps + 1];
		for (int i = 0; i <= steps; i++) {
			float t = Math.min(clip.length, i / 40F);
			fp[i] = firstPerson(clip, t);
			tp[i] = thirdPerson(clip, t);
		}
		float travelMax = 0, bladeMax = 0;
		int travelAt = -1, bladeAt = -1, travelN = 0, bladeN = 0;
		float a = clip.marker("windup_end"), b = clip.marker("active_end");
		int first = firstCompared(clip), last = lastCompared(clip, n);
		for (int k = first; k <= last; k++) {
			int i = k * 2;
			if (nearRest(clip, k / 20F)) {
				continue;
			}
			// Travel spans the half ticks around k: only where both halves are inside the compared span.
			if (i - 1 >= 0 && i + 1 <= steps && k > first && k < last) {
				float[] vf = move(fp[i - 1], fp[i + 1]), vt = move(tp[i - 1], tp[i + 1]);
				if (len(vf) >= MOVING && len(vt) >= MOVING) {
					float d = angleBetween(vf, vt);
					travelN++;
					if (d > travelMax) {
						travelMax = d;
						travelAt = k;
					}
				}
			}
			if (fp[i].bladeLength() >= SEEN && tp[i].bladeLength() >= SEEN) {
				float d = Math.abs(wrap(fp[i].bladeAngle() - tp[i].bladeAngle()));
				bladeN++;
				if (d > bladeMax) {
					bladeMax = d;
					bladeAt = k;
				}
			}
		}
		List<String> problems = new ArrayList<>();
		if (travelMax > LIMIT) {
			problems.add(String.format(Locale.ROOT, "the tip travels %.0f° apart at tick %d", travelMax, travelAt));
		}
		if (bladeMax > LIMIT) {
			problems.add(String.format(Locale.ROOT, "the blade's screen angle differs by %.0f° at tick %d", bladeMax, bladeAt));
		}
		float windup = 0, strike = 0, follow = 0, edgeFp = 0, edgeTp = 0;
		boolean crosses = true;
		if (a >= 0 && b >= 0) {
			int as = Math.round(a * 20), ae = Math.round(b * 20);
			int w = Math.max(0, as - 1) * 2, s0 = Math.max(0, as - 1) * 2, s1 = Math.min(n, ae) * 2, f = Math.min(n, ae + 2) * 2;
			windup = sideDiff(fp[w], tp[w]);
			follow = sideDiff(fp[f], tp[f]);
			float[] sf = move(fp[s0], fp[s1]), st = move(tp[s0], tp[s1]);
			strike = angleBetween(sf, st);
			crosses = sameWay(sf, st);
			if (windup > SIDE_LIMIT) {
				problems.add(String.format(Locale.ROOT, "the wind-up is on another side (%.0f° apart)", windup));
			}
			if (follow > SIDE_LIMIT) {
				problems.add(String.format(Locale.ROOT, "the follow-through ends on another side (%.0f° apart)", follow));
			}
			if (strike > LIMIT || !crosses) {
				problems.add(String.format(Locale.ROOT, "the strike crosses the screen %s (%.0f° apart)", crosses ? "in another direction" : "the other way", strike));
			}
			// The edge over the cut itself: each ACTIVE tick to the next (half-tick steps), so the turn at the top of a
			// swing (the tick before the strike) doesn't count as travel.
			for (int i = as * 2; i < s1; i++) {
				edgeFp = Math.max(edgeFp, edge(fp[i], fp[i], fp[i + 1]));
				edgeTp = Math.max(edgeTp, edge(tp[i], tp[i], tp[i + 1]));
			}
			if (edgeFp > EDGE_LIMIT) {
				problems.add(String.format(Locale.ROOT, "first person leads with the flat (%.0f°)", edgeFp));
			}
			if (edgeTp > EDGE_LIMIT) {
				problems.add(String.format(Locale.ROOT, "third person leads with the flat (%.0f°)", edgeTp));
			}
		}
		return new Result(n, travelMax, travelAt, travelN, bladeMax, bladeAt, bladeN, windup, strike, follow, crosses, edgeFp, edgeTp, problems, fp, tp);
	}

	/** The first tick compared: when first person starts on vanilla's held sword, the first tick of its second key. */
	static int firstCompared(Clip clip) {
		dev.voidmc.fray.motion.Channel r = clip.fp.rotation()[HumanoidRig.ITEM_R], p = clip.fp.position()[HumanoidRig.ITEM_R];
		if (r == null || r.keys() < 2 || !identity(r, 0) || p != null && !identity(p, 0)) {
			return 0;
		}
		return (int) Math.ceil(r.times[1] * 20 - 1e-3F);
	}

	private static boolean identity(dev.voidmc.fray.motion.Channel c, int key) {
		return Math.abs(c.values[key * 3]) + Math.abs(c.values[key * 3 + 1]) + Math.abs(c.values[key * 3 + 2]) < 0.01F;
	}

	/**
	 * The last tick compared: when first person ends on vanilla's held sword, its last segment returns each view to its
	 * own rest (vanilla's first-person sword stands up at the lower right, the third-person guard points it ahead), which
	 * differ, so the ticks after the clip's second-to-last first-person key are left out.
	 */
	static int lastCompared(Clip clip, int n) {
		dev.voidmc.fray.motion.Channel r = clip.fp.rotation()[HumanoidRig.ITEM_R], p = clip.fp.position()[HumanoidRig.ITEM_R];
		if (r == null || r.keys() < 2) {
			return n;
		}
		int k = r.keys() - 1;
		boolean restEnd = Math.abs(r.values[k * 3]) + Math.abs(r.values[k * 3 + 1]) + Math.abs(r.values[k * 3 + 2]) < 0.01F
			&& (p == null || Math.abs(p.values[p.keys() * 3 - 3]) + Math.abs(p.values[p.keys() * 3 - 2]) + Math.abs(p.values[p.keys() * 3 - 1]) < 0.01F);
		return restEnd ? Math.min(n, (int) Math.floor(r.times[k - 1] * 20 + 1e-3F)) : n;
	}

	/** First person at or near vanilla's held sword (turned less than 30°, moved less than 3 px): framed by vanilla, not by the move. */
	static boolean nearRest(Clip clip, float t) {
		FirstPersonRig.Pose p = FirstPersonRig.sample(clip, clip.localTime(t), new FirstPersonRig.Pose());
		float angle = (float) Math.toDegrees(2 * Math.acos(Math.min(1, Math.abs(p.right.rot.w))));
		return angle < 30 && p.right.pos.length() * 16 < 3;
	}

	/** The tip's motion between two samples, view degrees (yaw, pitch). */
	private static float[] move(Sample a, Sample b) {
		return new float[] {b.tyaw - a.tyaw, b.tpitch - a.tpitch};
	}

	private static float len(float[] v) {
		return (float) Math.hypot(v[0], v[1]);
	}

	private static float angleBetween(float[] a, float[] b) {
		if (len(a) < 1e-4F || len(b) < 1e-4F) {
			return 0;
		}
		return Math.abs(wrap((float) Math.toDegrees(Math.atan2(a[1], a[0]) - Math.atan2(b[1], b[0]))));
	}

	/** Both cross the screen the same way: their dominant components agree in sign. */
	private static boolean sameWay(float[] a, float[] b) {
		for (int k = 0; k < 2; k++) {
			float big = Math.max(Math.abs(a[k]), Math.abs(b[k]));
			if (big > 0.4F * Math.max(len(a), len(b)) && Math.signum(a[k]) != Math.signum(b[k]) && Math.min(Math.abs(a[k]), Math.abs(b[k])) > 3) {
				return false;
			}
		}
		return true;
	}

	/** The tip's direction from the crosshair in each view, compared (0 when either is near the middle). */
	private static float sideDiff(Sample a, Sample b) {
		float[] pa = {a.tyaw, a.tpitch}, pb = {b.tyaw, b.tpitch};
		if (len(pa) < 10 || len(pb) < 10) {
			return 0;
		}
		return angleBetween(pa, pb);
	}

	/** Degrees between the blade mid point's travel and the blade's plane (0 the edge leads), or 0 when it hardly moves. */
	private static float edge(Sample prev, Sample at, Sample next) {
		Vector3f v = new Vector3f(next.mid).sub(prev.mid);
		// A blade turning round (the top of a swing) barely moves: its direction of travel isn't meaningful.
		if (v.length() < EDGE_SPEED) {
			return 0;
		}
		float s = Math.abs(v.normalize().dot(at.normal));
		return (float) Math.toDegrees(Math.asin(Math.min(1, s)));
	}

	private static float wrap(float d) {
		d %= 360;
		if (d > 180) {
			d -= 360;
		}
		if (d < -180) {
			d += 360;
		}
		return d;
	}

	// ------------------------------------------------------------------ the two views

	/** First person: the rig's sword, camera space. */
	public static Sample firstPerson(Clip clip, float t) {
		FirstPersonRig.Pose pose = FirstPersonRig.sample(clip, clip.localTime(t), new FirstPersonRig.Pose());
		Matrix4f item = FirstPersonRig.item(pose.right, false, new Matrix4f());
		return sample(item);
	}

	/** Third person: the solved pose's sword from the animated head's eye, in the same camera axes. */
	public static Sample thirdPerson(Clip clip, float t) {
		Matrix4f[] m = thirdPersonItem(clip, t);
		return sample(new Matrix4f(m[1]).mul(m[0]));
	}

	/**
	 * Third person: {item (model blocks from item space), camera (view from model blocks: the animated head's eye, the
	 * view axes)}. The root moves both alike, so it is left out.
	 */
	public static Matrix4f[] thirdPersonItem(Clip clip, float t) {
		PoseBuffer buf = new PoseBuffer(Rig.HUMANOID);
		ClipSampler.accumulate(clip, clip.localTime(t), 1, buf, new float[3]);
		return thirdPersonItem(buf);
	}

	/** {@link #thirdPersonItem(Clip, float)} for a sampled pose. */
	public static Matrix4f[] thirdPersonItem(PoseBuffer buf) {
		PartPose[] rest = HumanoidRig.restPose();
		PartPose[] parts = HumanoidRig.restPose();
		new HumanoidSolver().solve(parts, rest, buf);
		Matrix4f item = partMatrix(parts[HumanoidRig.RIGHT_FOREARM]).translate(-HumanoidRig.ELBOW_RIGHT_X / 16F, -HumanoidRig.ELBOW_Y / 16F, 0)
			.mul(itemBone(buf, false, new Matrix4f())).mul(FirstPersonRig.handToItem(false, new Matrix4f())).mul(FirstPersonRig.tpDisplay(false));
		float eye = HumanoidRig.MODEL_Y_OFFSET - 1.62F / HumanoidRig.PLAYER_SCALE;
		Matrix4f head = partMatrix(parts[HumanoidRig.HEAD]).translate(0, eye, 0);
		float s = HumanoidRig.PLAYER_SCALE;
		Matrix4f cam = new Matrix4f().scale(-s, -s, s).mul(head.invert());
		return new Matrix4f[] {item, cam};
	}

	private static Sample sample(Matrix4f itemToView) {
		Vector3f g = itemToView.transformPosition(new Vector3f(FirstPersonRig.GRIP_ITEM));
		Vector3f tip = itemToView.transformPosition(new Vector3f(FirstPersonRig.TIP_ITEM));
		Vector3f mid = new Vector3f(g).add(tip).mul(0.5F);
		Vector3f normal = itemToView.transformDirection(new Vector3f(0, 0, 1)).normalize();
		return new Sample(yaw(g), pitch(g), yaw(tip), pitch(tip), mid, normal);
	}

	/**
	 * View angles: the azimuthal equidistant projection about the view axis (degrees from the crosshair, in the screen's
	 * directions), which matches yaw and pitch on screen and stays continuous for a blade behind the head.
	 */
	static float yaw(Vector3f v) {
		return azimuthal(v)[0];
	}

	static float pitch(Vector3f v) {
		return azimuthal(v)[1];
	}

	private static float[] azimuthal(Vector3f v) {
		double len = v.length();
		if (len < 1e-6) {
			return new float[2];
		}
		double theta = Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, -v.z / len))));
		double side = Math.hypot(v.x, v.y);
		if (side < 1e-9) {
			return new float[2];
		}
		return new float[] {(float) (theta * v.x / side), (float) (theta * v.y / side)};
	}

	/** A part's own transform (vanilla's {@code ModelPart.translateAndRotate}, blocks). */
	public static Matrix4f partMatrix(PartPose p) {
		return new Matrix4f().translate(p.x / 16, p.y / 16, p.z / 16).rotate(new Quaternionf().rotationZYX(p.zRot, p.yRot, p.xRot))
			.scale(p.xScale, p.yScale, p.zScale);
	}

	/** The clip's item bone ({@code PoseApply.itemBone}): the item turned about the grip, in the arm's frame. */
	public static Matrix4f itemBone(PoseBuffer buf, boolean left, Matrix4f out) {
		out.identity();
		int o = (left ? HumanoidRig.ITEM_L : HumanoidRig.ITEM_R) * PoseBuffer.STRIDE;
		float[] v = buf.v;
		float wr = v[o + PoseBuffer.W_ROT], wp = v[o + PoseBuffer.W_POS];
		float rx = v[o] * wr + v[o + PoseBuffer.ADD_ROT], ry = v[o + 1] * wr + v[o + PoseBuffer.ADD_ROT + 1], rz = v[o + 2] * wr + v[o + PoseBuffer.ADD_ROT + 2];
		float px = v[o + 3] * wp + v[o + PoseBuffer.ADD_POS], py = v[o + 4] * wp + v[o + PoseBuffer.ADD_POS + 1], pz = v[o + 5] * wp + v[o + PoseBuffer.ADD_POS + 2];
		if (rx != 0 || ry != 0 || rz != 0 || px != 0 || py != 0 || pz != 0) {
			float gx = (left ? 1 : -1) / 16F, gy = 10 / 16F;
			out.translate(gx + px / 16F, gy + py / 16F, pz / 16F).rotate(new Quaternionf().rotationZYX(rz, ry, rx)).translate(-gx, -gy, 0);
		}
		return out;
	}

	// ------------------------------------------------------------------ a picture for iterating

	/**
	 * Both views' blades over the clip, side by side, in view angles (PNG): one line a tick from grip to tip (blue wind-up,
	 * red strike, green recovery), the tip's path, the screen's edges and the crosshair. {@code -Dfray.fp.plot=<dir>}.
	 */
	public static void plot(Clip clip, Result r, java.nio.file.Path file) throws java.io.IOException {
		int pw = 560, ph = 420;
		java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(pw * 2 + 30, ph + 70, java.awt.image.BufferedImage.TYPE_INT_RGB);
		java.awt.Graphics2D g = img.createGraphics();
		g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
		g.setColor(new java.awt.Color(24, 24, 28));
		g.fillRect(0, 0, img.getWidth(), img.getHeight());
		g.setColor(java.awt.Color.WHITE);
		g.drawString(clip.id + "   " + r.line(), 10, 16);
		g.drawString(r.problems().isEmpty() ? "match: ok" : "match: " + String.join("; ", r.problems()), 10, 32);
		float a = clip.marker("windup_end") * 20, b = clip.marker("active_end") * 20;
		for (int view = 0; view < 2; view++) {
			int ox = 10 + view * (pw + 10), oy = 44;
			Sample[] s = view == 0 ? r.fp() : r.tp();
			float range = 120;
			java.util.function.BiFunction<Float, Float, int[]> at = (yaw, pitch) -> new int[] {ox + (int) (pw / 2 + yaw / range * pw / 2),
				oy + (int) (ph / 2 - pitch / range * pw / 2)};
			g.setColor(new java.awt.Color(40, 40, 48));
			g.fillRect(ox, oy, pw, ph);
			float hy = 35, hx = (float) Math.toDegrees(Math.atan(Math.tan(Math.toRadians(35)) * 16 / 9));
			int[] p0 = at.apply(-hx, hy), p1 = at.apply(hx, -hy);
			g.setColor(new java.awt.Color(90, 90, 110));
			g.drawRect(p0[0], p0[1], p1[0] - p0[0], p1[1] - p0[1]);
			int[] c = at.apply(0F, 0F);
			g.drawLine(c[0] - 6, c[1], c[0] + 6, c[1]);
			g.drawLine(c[0], c[1] - 6, c[0], c[1] + 6);
			g.setColor(java.awt.Color.WHITE);
			g.drawString(view == 0 ? "first person (camera)" : "third person (from the head)", ox + 6, oy + 14);
			for (int i = 0; i < s.length; i += 2) {
				float k = i / 2F;
				java.awt.Color col = a >= 0 && k < a ? new java.awt.Color(90, 150, 255) : a >= 0 && k <= b ? new java.awt.Color(255, 80, 70) : new java.awt.Color(110, 220, 120);
				int[] gp = at.apply(s[i].gyaw(), s[i].gpitch()), tp = at.apply(s[i].tyaw(), s[i].tpitch());
				g.setColor(col);
				g.setStroke(new java.awt.BasicStroke(2));
				g.drawLine(gp[0], gp[1], tp[0], tp[1]);
				g.fillOval(gp[0] - 3, gp[1] - 3, 6, 6);
				g.drawString(String.valueOf(i / 2), tp[0] + 3, tp[1] - 2);
			}
			g.setStroke(new java.awt.BasicStroke(1));
			g.setColor(new java.awt.Color(255, 255, 255, 120));
			for (int i = 1; i < s.length; i++) {
				int[] q0 = at.apply(s[i - 1].tyaw(), s[i - 1].tpitch()), q1 = at.apply(s[i].tyaw(), s[i].tpitch());
				g.drawLine(q0[0], q0[1], q1[0], q1[1]);
			}
		}
		g.dispose();
		java.nio.file.Files.createDirectories(file.getParent());
		javax.imageio.ImageIO.write(img, "png", file.toFile());
	}
}
