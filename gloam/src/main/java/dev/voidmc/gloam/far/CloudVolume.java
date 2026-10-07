package dev.voidmc.gloam.far;

import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;
import java.util.stream.IntStream;
import org.lwjgl.system.MemoryUtil;

/**
 * The 3D noise the cloud deck is shaped with: a {@value #N}³ block of noise that tiles in every direction, so a cloud is
 * a volume with rounded billows on every side rather than a 2D pattern stood up in columns (which drew vertical streaks
 * down every heap). Mojang's GPU layer has no 3D textures, so the block is laid out as its {@value #N} slices in an
 * {@value #GRID} x {@value #GRID} grid of one 2D texture, each slice with a one-texel border copied from its opposite
 * edge so filtering inside a slice wraps; the shader blends the two slices around a point ({@code farclouds.glsl}).
 *
 * <p>The channels, as in the clouds of Horizon Zero Dawn: red a Perlin-Worley noise (soft gradient noise sharpened by
 * cellular noise into billows) for the shape of the heaps; green, blue and alpha cellular noise at three finer scales
 * for eroding their edges into cauliflower. Made once on a worker at client start, in well under a second.
 */
final class CloudVolume {
	static final int N = 64;
	static final int GRID = 8;
	static final int CELL = N + 2;
	static final int SIZE = GRID * CELL;

	private static CompletableFuture<ByteBuffer> pending;

	private CloudVolume() {
	}

	/** Starts making the texels, once. */
	static synchronized CompletableFuture<ByteBuffer> start() {
		if (pending == null) {
			pending = CompletableFuture.supplyAsync(CloudVolume::make);
		}
		return pending;
	}

	/** The texels, RGBA8, {@value #SIZE} square. */
	static ByteBuffer make() {
		int n3 = N * N * N;
		float[] shape = new float[n3], d1 = new float[n3], d2 = new float[n3], d3 = new float[n3];
		IntStream.range(0, N).parallel().forEach(z -> {
			for (int y = 0; y < N; y++) {
				for (int x = 0; x < N; x++) {
					int i = (z * N + y) * N + x;
					float px = (x + 0.5F) / N, py = (y + 0.5F) / N, pz = (z + 0.5F) / N;
					// Perlin-Worley: gradient noise, 3 octaves, pushed up where the cellular noise is high
					float perlin = 0, amp = 1, total = 0;
					for (int o = 0; o < 3; o++) {
						int f = 4 << o;
						perlin += amp * gradient(11 + o, f, px * f, py * f, pz * f);
						total += amp;
						amp *= 0.5F;
					}
					perlin = Math.clamp(perlin / total * 0.9F + 0.5F, 0, 1);
					float worley = 0.625F * cells(21, 4, px, py, pz) + 0.25F * cells(22, 8, px, py, pz) + 0.125F * cells(23, 16, px, py, pz);
					shape[i] = remap(perlin, worley - 1, 1, 0, 1);
					d1[i] = cells(31, 8, px, py, pz);
					d2[i] = cells(32, 16, px, py, pz);
					d3[i] = cells(33, 32, px, py, pz);
				}
			}
		});
		stretch(shape);
		ByteBuffer out = MemoryUtil.memAlloc(SIZE * SIZE * 4);
		for (int cy = 0; cy < SIZE; cy++) {
			for (int cx = 0; cx < SIZE; cx++) {
				int slice = (cy / CELL) * GRID + cx / CELL;
				// the border copies the opposite edge, so a slice's filtering wraps
				int x = Math.floorMod(cx % CELL - 1, N), y = Math.floorMod(cy % CELL - 1, N);
				int i = (slice * N + y) * N + x;
				out.put(unit(shape[i])).put(unit(d1[i])).put(unit(d2[i])).put(unit(d3[i]));
			}
		}
		return out.flip();
	}

	private static byte unit(float v) {
		return (byte) Math.round(Math.clamp(v, 0, 1) * 255);
	}

	private static float remap(float v, float lo, float hi, float to0, float to1) {
		return to0 + (v - lo) / (hi - lo) * (to1 - to0);
	}

	/** Stretches the values to span 0..1. */
	private static void stretch(float[] v) {
		float min = Float.MAX_VALUE, max = -Float.MAX_VALUE;
		for (float f : v) {
			min = Math.min(min, f);
			max = Math.max(max, f);
		}
		float s = 1 / Math.max(1e-6F, max - min);
		for (int i = 0; i < v.length; i++) {
			v[i] = (v[i] - min) * s;
		}
	}

	/** Inverted cellular noise at (x, y, z) in 0..1 with {@code f} cells across, wrapping: 1 at a cell's point, 0 between. */
	private static float cells(long seed, int f, float x, float y, float z) {
		float sx = x * f, sy = y * f, sz = z * f;
		int ix = (int) Math.floor(sx), iy = (int) Math.floor(sy), iz = (int) Math.floor(sz);
		float best = Float.MAX_VALUE;
		for (int dz = -1; dz <= 1; dz++) {
			for (int dy = -1; dy <= 1; dy++) {
				for (int dx = -1; dx <= 1; dx++) {
					int cx = ix + dx, cy = iy + dy, cz = iz + dz;
					long h = hash(seed, Math.floorMod(cx, f), Math.floorMod(cy, f), Math.floorMod(cz, f));
					float fx = cx + ((h & 0x3FF) / 1023.0F) - sx;
					float fy = cy + (((h >>> 10) & 0x3FF) / 1023.0F) - sy;
					float fz = cz + (((h >>> 20) & 0x3FF) / 1023.0F) - sz;
					best = Math.min(best, fx * fx + fy * fy + fz * fz);
				}
			}
		}
		return 1 - Math.min(1, (float) Math.sqrt(best));
	}

	/** Gradient noise at (x, y, z) on a lattice wrapping every {@code period} cells, about -1..1. */
	private static float gradient(long seed, int period, float x, float y, float z) {
		int x0 = (int) Math.floor(x), y0 = (int) Math.floor(y), z0 = (int) Math.floor(z);
		float fx = x - x0, fy = y - y0, fz = z - z0;
		float u = fade(fx), v = fade(fy), w = fade(fz);
		float result = 0;
		for (int c = 0; c < 8; c++) {
			int dx = c & 1, dy = (c >> 1) & 1, dz = c >> 2;
			long h = hash(seed, Math.floorMod(x0 + dx, period), Math.floorMod(y0 + dy, period), Math.floorMod(z0 + dz, period));
			// one of twelve edge directions
			float gx, gy, gz;
			switch ((int) Math.floorMod(h, 12L)) {
				case 0 -> { gx = 1; gy = 1; gz = 0; }
				case 1 -> { gx = -1; gy = 1; gz = 0; }
				case 2 -> { gx = 1; gy = -1; gz = 0; }
				case 3 -> { gx = -1; gy = -1; gz = 0; }
				case 4 -> { gx = 1; gy = 0; gz = 1; }
				case 5 -> { gx = -1; gy = 0; gz = 1; }
				case 6 -> { gx = 1; gy = 0; gz = -1; }
				case 7 -> { gx = -1; gy = 0; gz = -1; }
				case 8 -> { gx = 0; gy = 1; gz = 1; }
				case 9 -> { gx = 0; gy = -1; gz = 1; }
				case 10 -> { gx = 0; gy = 1; gz = -1; }
				default -> { gx = 0; gy = -1; gz = -1; }
			}
			float dot = gx * (fx - dx) + gy * (fy - dy) + gz * (fz - dz);
			float weight = (dx == 1 ? u : 1 - u) * (dy == 1 ? v : 1 - v) * (dz == 1 ? w : 1 - w);
			result += weight * dot;
		}
		return result;
	}

	private static long hash(long seed, int x, int y, int z) {
		long h = seed * 0x9E3779B97F4A7C15L ^ x * 0xBF58476D1CE4E5B9L ^ y * 0x94D049BB133111EBL ^ z * 0xD6E8FEB86659FD93L;
		h ^= h >>> 31;
		h *= 0xD6E8FEB86659FD93L;
		h ^= h >>> 29;
		h *= 0xBF58476D1CE4E5B9L;
		h ^= h >>> 32;
		return h & Long.MAX_VALUE;
	}

	private static float fade(float t) {
		return t * t * t * (t * (t * 6 - 15) + 10);
	}
}
