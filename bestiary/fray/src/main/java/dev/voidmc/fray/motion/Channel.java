package dev.voidmc.fray.motion;

import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * One animated property of one bone (rotation, position or scale): keyframes of three values each. Sampled with
 * vanilla's rules ({@code net.minecraft.client.animation.KeyframeAnimation}): before the first key the first value,
 * after the last key the last value, and the segment that arrives at a key uses that key's interpolation (so a
 * {@link Interp#STEP} key holds the previous value until its time, then jumps). Catmull-Rom uses the neighbouring keys
 * with clamped indices, as vanilla's {@code Interpolations.CATMULLROM} does.
 *
 * <p>Common code: the client samples it every frame, the bake samples the same channels to make the server timeline.
 */
public final class Channel {
	public final float[] times;
	/** Three values per key. */
	public final float[] values;
	public final Interp[] interps;
	/**
	 * A rotation channel sampled as an orientation (the clip's {@code "slerp": true}): keys are Euler degrees (vanilla's
	 * ZYX order), but between keys the rotation is interpolated on quaternions (slerp for linear keys, a cubic Bezier on
	 * the quaternion sphere with Catmull-Rom tangents for catmullrom keys), so a part turns about one axis the shortest
	 * way instead of each Euler axis on its own. {@link #sample} still returns Euler degrees.
	 */
	public final boolean slerp;
	private Quaternionf @org.jspecify.annotations.Nullable [] quats;

	public Channel(float[] times, float[] values, Interp[] interps) {
		this(times, values, interps, false);
	}

	public Channel(float[] times, float[] values, Interp[] interps, boolean slerp) {
		if (values.length != times.length * 3 || interps.length != times.length || times.length == 0) {
			throw new IllegalArgumentException("a channel needs 1+ keys of 3 values each");
		}
		for (int i = 1; i < times.length; i++) {
			if (times[i] < times[i - 1]) {
				throw new IllegalArgumentException("keyframes out of order at " + times[i]);
			}
		}
		this.times = times;
		this.values = values;
		this.interps = interps;
		this.slerp = slerp;
	}

	/** The same keys, sampled as an orientation (or not). */
	public Channel withSlerp(boolean on) {
		return on == this.slerp ? this : new Channel(this.times, this.values, this.interps, on);
	}

	public int keys() {
		return this.times.length;
	}

	/** Samples at {@code t} seconds into {@code out[off..off+2]}. No allocation (Euler-interpolated channels). */
	public void sample(float t, float[] out, int off) {
		if (this.slerp) {
			this.sampleSlerp(t, out, off);
			return;
		}
		this.sampleEuler(t, out, off);
	}

	/** Per-component interpolation (vanilla's rule), whatever the channel's mode. */
	public void sampleEuler(float t, float[] out, int off) {
		float[] ts = this.times;
		int n = ts.length;
		if (n == 1 || t <= ts[0]) {
			out[off] = this.values[0];
			out[off + 1] = this.values[1];
			out[off + 2] = this.values[2];
			return;
		}
		if (t >= ts[n - 1]) {
			int b = (n - 1) * 3;
			out[off] = this.values[b];
			out[off + 1] = this.values[b + 1];
			out[off + 2] = this.values[b + 2];
			return;
		}
		// Clips have a handful of keys per channel: a linear scan beats a search and needs no cursor state.
		int cur = 0;
		while (cur + 1 < n && ts[cur + 1] <= t) {
			cur++;
		}
		int tgt = cur + 1;
		float span = ts[tgt] - ts[cur];
		float a = span <= 0 ? 1 : (t - ts[cur]) / span;
		int c3 = cur * 3;
		int t3 = tgt * 3;
		switch (this.interps[tgt]) {
			case STEP -> {
				out[off] = this.values[c3];
				out[off + 1] = this.values[c3 + 1];
				out[off + 2] = this.values[c3 + 2];
			}
			case LINEAR -> {
				out[off] = this.values[c3] + (this.values[t3] - this.values[c3]) * a;
				out[off + 1] = this.values[c3 + 1] + (this.values[t3 + 1] - this.values[c3 + 1]) * a;
				out[off + 2] = this.values[c3 + 2] + (this.values[t3 + 2] - this.values[c3 + 2]) * a;
			}
			case CATMULLROM -> {
				int p0 = Math.max(0, cur - 1) * 3;
				int p3 = Math.min(n - 1, tgt + 1) * 3;
				for (int k = 0; k < 3; k++) {
					out[off + k] = catmullrom(a, this.values[p0 + k], this.values[c3 + k], this.values[t3 + k], this.values[p3 + k]);
				}
			}
		}
	}

	// ------------------------------------------------------------------ orientation sampling

	private static final float DEG = (float) (Math.PI / 180.0);

	/** A key's Euler degrees as a quaternion (vanilla's {@code rotationZYX(z, y, x)}). */
	public static Quaternionf euler(float xDeg, float yDeg, float zDeg, Quaternionf out) {
		return out.rotationZYX(zDeg * DEG, yDeg * DEG, xDeg * DEG);
	}

	private Quaternionf[] quats() {
		Quaternionf[] q = this.quats;
		if (q == null) {
			int n = this.times.length;
			q = new Quaternionf[n];
			for (int i = 0; i < n; i++) {
				q[i] = euler(this.values[i * 3], this.values[i * 3 + 1], this.values[i * 3 + 2], new Quaternionf());
				// Keep neighbours on one hemisphere so every segment takes the short way.
				if (i > 0 && q[i].dot(q[i - 1]) < 0) {
					q[i].mul(-1);
				}
			}
			this.quats = q;
		}
		return q;
	}

	/**
	 * The orientation at {@code t}: slerp for linear keys, a cubic (De Casteljau of slerps, Catmull-Rom tangents taken on
	 * the sphere) for catmullrom keys, a hold for step keys. Allocates a little (the bake, first person, the sampler).
	 */
	public Quaternionf sampleQuat(float t, Quaternionf out) {
		Quaternionf[] q = this.quats();
		float[] ts = this.times;
		int n = ts.length;
		if (n == 1 || t <= ts[0]) {
			return out.set(q[0]);
		}
		if (t >= ts[n - 1]) {
			return out.set(q[n - 1]);
		}
		int cur = 0;
		while (cur + 1 < n && ts[cur + 1] <= t) {
			cur++;
		}
		int tgt = cur + 1;
		float span = ts[tgt] - ts[cur];
		float a = span <= 0 ? 1 : (t - ts[cur]) / span;
		switch (this.interps[tgt]) {
			case STEP -> {
				return out.set(q[cur]);
			}
			case LINEAR -> {
				return q[cur].slerp(q[tgt], a, out);
			}
			default -> {
				Quaternionf q1 = q[cur], q2 = q[tgt];
				Quaternionf q0 = q[Math.max(0, cur - 1)], q3 = q[Math.min(n - 1, tgt + 1)];
				// Catmull-Rom tangents in each key's own frame, (next - previous) / 2 on the sphere (Shoemake); the clamped
				// end keys give half the segment, as the per-component Catmull-Rom does.
				Vector3f t1 = logDiff(q1, q2, new Vector3f()).sub(logDiff(q1, q0, new Vector3f())).mul(0.5F);
				Vector3f t2 = logDiff(q2, q3, new Vector3f()).sub(logDiff(q2, q1, new Vector3f())).mul(0.5F);
				Quaternionf b1 = new Quaternionf(q1).mul(exp(t1.mul(1 / 3F), new Quaternionf()));
				Quaternionf b2 = new Quaternionf(q2).mul(exp(t2.mul(-1 / 3F), new Quaternionf()));
				Quaternionf p01 = new Quaternionf(q1).slerp(b1, a), p12 = new Quaternionf(b1).slerp(b2, a), p23 = new Quaternionf(b2).slerp(q2, a);
				Quaternionf p012 = p01.slerp(p12, a), p123 = p12.slerp(p23, a);
				return p012.slerp(p123, a, out).normalize();
			}
		}
	}

	/** {@code log(a^-1 b)}: the rotation from a to b in a's frame, as axis × angle (radians). */
	private static Vector3f logDiff(Quaternionf a, Quaternionf b, Vector3f out) {
		Quaternionf d = new Quaternionf(a).conjugate().mul(b);
		if (d.w < 0) {
			d.mul(-1);
		}
		float s = (float) Math.sqrt(d.x * d.x + d.y * d.y + d.z * d.z);
		if (s < 1e-7F) {
			return out.set(0);
		}
		float angle = 2 * (float) Math.atan2(s, d.w);
		return out.set(d.x, d.y, d.z).mul(angle / s);
	}

	/** The rotation by an axis × angle vector. */
	private static Quaternionf exp(Vector3f v, Quaternionf out) {
		float angle = v.length();
		if (angle < 1e-7F) {
			return out.identity();
		}
		return out.fromAxisAngleRad(v.x / angle, v.y / angle, v.z / angle, angle);
	}

	private void sampleSlerp(float t, float[] out, int off) {
		Quaternionf q = this.sampleQuat(t, new Quaternionf());
		// The Euler reading closest to the per-component one (the authored intent), so blends and additive layers that
		// work on Euler values see no sudden jumps.
		this.sampleEuler(t, out, off);
		toEulerNear(q, out, off);
	}

	/**
	 * Writes {@code q} as ZYX Euler degrees into {@code out[off..off+2]}, choosing among the equivalent readings
	 * (the two solutions, plus whole turns) the one closest to what {@code out} holds on entry.
	 */
	public static void toEulerNear(Quaternionf q, float[] out, int off) {
		Vector3f e = new Matrix3f().set(q).getEulerAnglesZYX(new Vector3f());
		float rx = out[off], ry = out[off + 1], rz = out[off + 2];
		float ax = e.x / DEG, ay = e.y / DEG, az = e.z / DEG;
		float bx = ax + 180, by = 180 - ay, bz = az + 180;
		ax = near(ax, rx);
		ay = near(ay, ry);
		az = near(az, rz);
		bx = near(bx, rx);
		by = near(by, ry);
		bz = near(bz, rz);
		float da = Math.abs(ax - rx) + Math.abs(ay - ry) + Math.abs(az - rz);
		float db = Math.abs(bx - rx) + Math.abs(by - ry) + Math.abs(bz - rz);
		if (db < da) {
			ax = bx;
			ay = by;
			az = bz;
		}
		out[off] = ax;
		out[off + 1] = ay;
		out[off + 2] = az;
	}

	private static float near(float v, float ref) {
		return v + 360 * Math.round((ref - v) / 360);
	}

	/** Vanilla's {@code Mth.catmullrom}, reproduced so the server (and the bake) need no client class. */
	public static float catmullrom(float a, float p0, float p1, float p2, float p3) {
		return 0.5F * (2.0F * p1 + (p2 - p0) * a + (2.0F * p0 - 5.0F * p1 + 4.0F * p2 - p3) * a * a + (3.0F * p1 - p0 - 3.0F * p2 + p3) * a * a * a);
	}
}
