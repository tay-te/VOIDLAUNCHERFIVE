package dev.voidmc.hub.gametest;

import dev.voidmc.hub.model.Json;
import java.util.Arrays;

/** A distribution of durations in milliseconds: avg, p50/p95/p99, max. */
public record Dist(int n, double avg, double p50, double p95, double p99, double max) {

	public static final Dist EMPTY = new Dist(0, 0, 0, 0, 0, 0);

	public static Dist ofNanos(long[] ns) {
		long[] v = Arrays.stream(ns).filter(x -> x >= 0).toArray();
		if (v.length == 0) {
			return EMPTY;
		}
		double[] ms = new double[v.length];
		for (int i = 0; i < v.length; i++) {
			ms[i] = v[i] / 1e6;
		}
		return of(ms);
	}

	public static Dist of(double[] ms) {
		if (ms.length == 0) {
			return EMPTY;
		}
		double[] s = ms.clone();
		Arrays.sort(s);
		double sum = 0;
		for (double d : s) {
			sum += d;
		}
		return new Dist(s.length, sum / s.length, pct(s, 0.50), pct(s, 0.95), pct(s, 0.99), s[s.length - 1]);
	}

	/** Nearest-rank percentile of a sorted array. */
	static double pct(double[] sorted, double q) {
		int idx = (int) Math.ceil(q * sorted.length) - 1;
		return sorted[Math.max(0, Math.min(sorted.length - 1, idx))];
	}

	public int countOver(double[] ms, double limit) {
		int c = 0;
		for (double d : ms) {
			if (d > limit) {
				c++;
			}
		}
		return c;
	}

	public String json() {
		return Json.object().put("n", this.n).put("avg", r(this.avg)).put("p50", r(this.p50)).put("p95", r(this.p95))
			.put("p99", r(this.p99)).put("max", r(this.max)).end();
	}

	public static double r(double v) {
		return Math.round(v * 1000.0) / 1000.0;
	}

	public String row() {
		return String.format(java.util.Locale.ROOT, "%.3f | %.3f | %.3f | %.3f | %.2f", this.avg, this.p50, this.p95, this.p99, this.max);
	}
}
