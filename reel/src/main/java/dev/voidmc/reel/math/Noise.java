package dev.voidmc.reel.math;

/**
 * Smooth one-dimensional noise for handheld shake: two octaves of value noise with quintic fades, in -1..1.
 * Seeded and stateless, so the same shot shakes the same way on every render.
 */
public final class Noise {
	private final long seed;

	public Noise(long seed) {
		this.seed = seed;
	}

	public double at(double x) {
		return octave(x) * 0.7 + octave(x * 2.13 + 17.3) * 0.3;
	}

	private double octave(double x) {
		long i = (long) Math.floor(x);
		double f = x - i;
		double fade = f * f * f * (f * (f * 6 - 15) + 10);
		return lattice(i) + (lattice(i + 1) - lattice(i)) * fade;
	}

	private double lattice(long i) {
		long h = i * 0x9E3779B97F4A7C15L + this.seed * 0xBF58476D1CE4E5B9L;
		h ^= h >>> 31;
		h *= 0x94D049BB133111EBL;
		h ^= h >>> 29;
		return ((h >>> 11) * 0x1.0p-53) * 2 - 1;
	}
}
