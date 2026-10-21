package dev.voidmc.fray.motion;

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

	public Channel(float[] times, float[] values, Interp[] interps) {
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
	}

	public int keys() {
		return this.times.length;
	}

	/** Samples at {@code t} seconds into {@code out[off..off+2]}. No allocation. */
	public void sample(float t, float[] out, int off) {
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

	/** Vanilla's {@code Mth.catmullrom}, reproduced so the server (and the bake) need no client class. */
	public static float catmullrom(float a, float p0, float p1, float p2, float p3) {
		return 0.5F * (2.0F * p1 + (p2 - p0) * a + (2.0F * p0 - 5.0F * p1 + 4.0F * p2 - p3) * a * a + (3.0F * p1 - p0 - 3.0F * p2 + p3) * a * a * a);
	}
}
