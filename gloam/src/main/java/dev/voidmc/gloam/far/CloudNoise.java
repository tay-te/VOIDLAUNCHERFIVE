package dev.voidmc.gloam.far;

import java.nio.ByteBuffer;
import org.lwjgl.system.MemoryUtil;

/**
 * The noise the far air is shaped with on the GPU: a {@value #SIZE} x {@value #SIZE} texture that tiles, four
 * independent fractal noises in its four channels, each a few octaves of gradient noise so it has soft large shapes
 * with ragged edges. The shaders sample it at several scales (kilometres for the cloud masses, tens of blocks for their
 * edges and the rain's streaks), which costs a texture read where hashing noise on the GPU would cost dozens of
 * operations. Made once, in about 10 ms.
 */
final class CloudNoise {
	static final int SIZE = 256;

	private CloudNoise() {
	}

	/** The texels, RGBA8. */
	static ByteBuffer make() {
		float[][] channels = {
			fbm(1, 4, 5),
			fbm(2, 8, 5),
			fbm(3, 8, 4),
			fbm(4, 16, 4),
		};
		ByteBuffer out = MemoryUtil.memAlloc(SIZE * SIZE * 4);
		for (int i = 0; i < SIZE * SIZE; i++) {
			for (float[] c : channels) {
				out.put((byte) Math.round(Math.clamp(c[i], 0, 1) * 255));
			}
		}
		return out.flip();
	}

	/**
	 * Fractal gradient noise over the tile: {@code octaves} layers, the first with {@code cells} lattice cells across
	 * the tile, each next twice as fine and a little over half as strong; stretched so it spans most of 0..1.
	 */
	private static float[] fbm(long seed, int cells, int octaves) {
		float[] out = new float[SIZE * SIZE];
		float amplitude = 1, total = 0;
		for (int o = 0; o < octaves; o++) {
			int n = cells << o;
			for (int y = 0; y < SIZE; y++) {
				for (int x = 0; x < SIZE; x++) {
					out[y * SIZE + x] += amplitude * gradient(seed * 131 + o, n, x * n / (float) SIZE, y * n / (float) SIZE);
				}
			}
			total += amplitude;
			amplitude *= 0.55F;
		}
		float min = Float.MAX_VALUE, max = -Float.MAX_VALUE;
		for (float v : out) {
			min = Math.min(min, v);
			max = Math.max(max, v);
		}
		// to 0..1 by the range actually reached, so every channel uses its whole span
		float scale = 1 / Math.max(1e-6F, max - min);
		for (int i = 0; i < out.length; i++) {
			out[i] = (out[i] - min) * scale;
		}
		return out;
	}

	/** Gradient (Perlin) noise at (x, y) on a lattice that wraps every {@code period} cells, about -0.7..0.7. */
	private static float gradient(long seed, int period, float x, float y) {
		int x0 = (int) Math.floor(x), y0 = (int) Math.floor(y);
		float fx = x - x0, fy = y - y0;
		float u = fade(fx), v = fade(fy);
		float a = dot(seed, Math.floorMod(x0, period), Math.floorMod(y0, period), fx, fy);
		float b = dot(seed, Math.floorMod(x0 + 1, period), Math.floorMod(y0, period), fx - 1, fy);
		float c = dot(seed, Math.floorMod(x0, period), Math.floorMod(y0 + 1, period), fx, fy - 1);
		float d = dot(seed, Math.floorMod(x0 + 1, period), Math.floorMod(y0 + 1, period), fx - 1, fy - 1);
		return (a + (b - a) * u) + ((c + (d - c) * u) - (a + (b - a) * u)) * v;
	}

	private static float dot(long seed, int ix, int iy, float dx, float dy) {
		long h = seed * 0x9E3779B97F4A7C15L ^ ix * 0xBF58476D1CE4E5B9L ^ iy * 0x94D049BB133111EBL;
		h ^= h >>> 31;
		h *= 0xD6E8FEB86659FD93L;
		h ^= h >>> 29;
		double angle = (h >>> 11) * 0x1.0p-53 * Math.PI * 2;
		return (float) (Math.cos(angle) * dx + Math.sin(angle) * dy);
	}

	private static float fade(float t) {
		return t * t * t * (t * (t * 6 - 15) + 10);
	}
}
