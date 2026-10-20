package dev.voidmc.hearth.sim.roads;

import java.util.Arrays;

/**
 * A road's way across the land, worked out by {@link RoadRouter}: its dense centreline (one column per step, eight-way
 * neighbours, the infrastructure params rule), the walking level of every column (a bridge's deck over its water), and
 * what each column is — graded road, a ford, or a bridge (for whose columns {@code decks} keeps the top of the water
 * spanned).
 *
 * @param states the search states the router expanded (for the cost reports)
 */
public record Route(int[] xs, int[] ys, int[] zs, byte[] types, int[] decks, int states) {
	public static final byte GRADE = 0;
	public static final byte FORD = 1;
	public static final byte BRIDGE = 2;

	public int size() {
		return this.xs.length;
	}

	/** Horizontal length in blocks (diagonal steps count √2). */
	public double length() {
		double len = 0;
		for (int i = 1; i < this.xs.length; i++) {
			len += Math.hypot(this.xs[i] - this.xs[i - 1], this.zs[i] - this.zs[i - 1]);
		}
		return len;
	}

	/** Packed {@code x, y, z} triples every {@code step} columns (and the last), for the api's {@code RoadView}. */
	public int[] points(int step) {
		int n = this.xs.length;
		int count = (n - 1) / step + 1 + ((n - 1) % step == 0 ? 0 : 1);
		int[] out = new int[count * 3];
		int k = 0;
		for (int i = 0; i < n; i += step) {
			out[k++] = this.xs[i];
			out[k++] = this.ys[i];
			out[k++] = this.zs[i];
		}
		if ((n - 1) % step != 0) {
			out[k++] = this.xs[n - 1];
			out[k++] = this.ys[n - 1];
			out[k++] = this.zs[n - 1];
		}
		return Arrays.copyOf(out, k);
	}

	/** The same road walked the other way. */
	public Route reversed() {
		int n = this.xs.length;
		int[] x = new int[n];
		int[] y = new int[n];
		int[] z = new int[n];
		byte[] t = new byte[n];
		int[] d = new int[n];
		for (int i = 0; i < n; i++) {
			x[i] = this.xs[n - 1 - i];
			y[i] = this.ys[n - 1 - i];
			z[i] = this.zs[n - 1 - i];
			t[i] = this.types[n - 1 - i];
			d[i] = this.decks[n - 1 - i];
		}
		return new Route(x, y, z, t, d, this.states);
	}

	// ---------------------------------------------------------------- compact storage (live routes)

	/** Delta-encoded: first column absolute, then per column dx, dz (each -1..1) and dy packed, with the run types. */
	public int[] encode() {
		int n = this.xs.length;
		int[] out = new int[4 + n * 2];
		out[0] = n;
		out[1] = n == 0 ? 0 : this.xs[0];
		out[2] = n == 0 ? 0 : this.ys[0];
		out[3] = n == 0 ? 0 : this.zs[0];
		for (int i = 0; i < n; i++) {
			int dx = i == 0 ? 0 : this.xs[i] - this.xs[i - 1];
			int dz = i == 0 ? 0 : this.zs[i] - this.zs[i - 1];
			int dy = i == 0 ? 0 : this.ys[i] - this.ys[i - 1];
			out[4 + i * 2] = (dx + 1) | (dz + 1) << 2 | this.types[i] << 4 | (dy & 0xFFFF) << 8;
			out[5 + i * 2] = this.decks[i];
		}
		return out;
	}

	public static Route decode(int[] in) {
		int n = in.length < 4 ? 0 : in[0];
		int[] x = new int[n];
		int[] y = new int[n];
		int[] z = new int[n];
		byte[] t = new byte[n];
		int[] d = new int[n];
		for (int i = 0; i < n; i++) {
			int p = in[4 + i * 2];
			int dx = (p & 3) - 1;
			int dz = (p >> 2 & 3) - 1;
			int dy = (short) (p >>> 8 & 0xFFFF);
			x[i] = i == 0 ? in[1] : x[i - 1] + dx;
			z[i] = i == 0 ? in[3] : z[i - 1] + dz;
			y[i] = i == 0 ? in[2] : y[i - 1] + dy;
			t[i] = (byte) (p >> 4 & 0xF);
			d[i] = in[5 + i * 2];
		}
		return new Route(x, y, z, t, d, 0);
	}
}
