package dev.voidmc.gloam.far;

import dev.voidmc.gloam.air.Air;
import dev.voidmc.gloam.air.Noise;
import java.nio.ByteBuffer;
import java.util.Arrays;
import org.lwjgl.system.MemoryUtil;

/**
 * Rain curtains under the far showers and storms: grey veils from the cloud base to the ground, kilometres off, so
 * weather can be watched coming in across the land. The fronts are sampled on a grid of {@value #CELL}-block cells in
 * their own drifting frame, so a curtain belongs to its piece of weather and travels with it; a cell where it rains
 * hangs one curtain, as wide as the rain is heavy, turned to face the camera. Built every frame on the render thread
 * from the weather map's CPU copy (a few hundred lookups), back to front.
 */
final class FarRain {
	/** The grid the curtains hang on, in blocks. */
	static final int CELL = 1024;
	/** Bytes per vertex: position (3 floats), across/up/rain/storm (4 floats), width/height/seed/spare (4 floats). */
	static final int VERTEX_BYTES = 44;
	/** Rain lighter than this hangs no curtain: a drizzle seen kilometres off is nothing. */
	private static final float MIN_RAIN = 0.12F;

	private ByteBuffer vertices = MemoryUtil.memAlloc(VERTEX_BYTES * 4 * 256);
	private float[] order = new float[256 * 8];
	private int count;

	/** How many curtains the last build made. */
	int count() {
		return this.count;
	}

	/** The last build's vertices (4 per curtain), ready to upload. */
	ByteBuffer vertices() {
		return this.vertices;
	}

	/**
	 * Builds this frame's curtains around (camX, camY, camZ) out to {@code reach}: {@code ticks} the game time with the
	 * partial tick in it, {@code base} the cloud deck's base, {@code vanilla} vanilla's rain level.
	 */
	void build(WeatherMap.Picture map, FarLand land, double camX, double camY, double camZ, double ticks, double reach, float base,
		float vanilla, boolean showers, float year, float thunder, long seed) {
		this.count = 0;
		this.vertices.clear();
		if (map == null) {
			return;
		}
		double seconds = ticks / 20.0;
		double driftX = Air.FRONT_DRIFT * seconds, driftZ = Air.FRONT_DRIFT * 0.2 * seconds;
		double fx = camX - driftX, fz = camZ - driftZ;
		int r = (int) Math.ceil(reach / CELL) + 1;
		int c0x = (int) Math.floor(fx / CELL), c0z = (int) Math.floor(fz / CELL);
		double reach2 = reach * reach;
		int n = 0;
		for (int cz = c0z - r; cz <= c0z + r; cz++) {
			for (int cx = c0x - r; cx <= c0x + r; cx++) {
				// a place of its own in the cell, the same for as long as the cell lives
				float jx = hash(seed, cx, cz, 1), jz = hash(seed, cx, cz, 2);
				double px = (cx + 0.2 + 0.6 * jx) * CELL, pz = (cz + 0.2 + 0.6 * jz) * CELL;
				float front = map.at(px, pz);
				float rain = Air.rain(vanilla, front, showers);
				if (rain < MIN_RAIN) {
					continue;
				}
				double wx = px + driftX, wz = pz + driftZ;
				double dx = wx - camX, dz = wz - camZ;
				double d2 = dx * dx + dz * dz;
				if (d2 > reach2 || d2 < 250 * 250) {
					continue;
				}
				float storm = Math.max(Air.storm(front, year), thunder * Noise.smoothstep(0.3F, 0.6F, front));
				if (n * 8 + 8 > this.order.length) {
					this.order = Arrays.copyOf(this.order, this.order.length * 2);
				}
				float[] o = this.order;
				o[n * 8] = (float) d2;
				o[n * 8 + 1] = (float) dx;
				o[n * 8 + 2] = (float) dz;
				o[n * 8 + 3] = rain;
				o[n * 8 + 4] = storm;
				o[n * 8 + 5] = hash(seed, cx, cz, 3);
				o[n * 8 + 6] = land.groundAt(wx, wz, 63);
				o[n * 8 + 7] = 0;
				n++;
			}
		}
		this.sortFarFirst(n);
		for (int i = 0; i < n; i++) {
			float[] o = this.order;
			float dx = o[i * 8 + 1], dz = o[i * 8 + 2], rain = o[i * 8 + 3], storm = o[i * 8 + 4], s = o[i * 8 + 5], ground = o[i * 8 + 6];
			float width = 500 + 900 * rain + 400 * storm;
			float top = base - 35 * storm + 15;
			float bottom = Math.min(ground, top - 40) - 24;
			float height = top - bottom;
			// turned to face the camera across the ground
			float len = (float) Math.sqrt(dx * dx + dz * dz);
			float rx = -dz / len * width * 0.5F, rz = dx / len * width * 0.5F;
			float y0 = (float) (bottom - camY), y1 = (float) (top - camY);
			if (this.vertices.remaining() < VERTEX_BYTES * 4) {
				this.vertices = MemoryUtil.memRealloc(this.vertices, this.vertices.capacity() * 2);
			}
			this.vertex(dx - rx, y0, dz - rz, -1, 0, rain, storm, width, height, s);
			this.vertex(dx + rx, y0, dz + rz, 1, 0, rain, storm, width, height, s);
			this.vertex(dx + rx, y1, dz + rz, 1, 1, rain, storm, width, height, s);
			this.vertex(dx - rx, y1, dz - rz, -1, 1, rain, storm, width, height, s);
		}
		this.count = n;
		this.vertices.flip();
	}

	private void vertex(float x, float y, float z, float u, float v, float rain, float storm, float width, float height, float seed) {
		this.vertices.putFloat(x).putFloat(y).putFloat(z);
		this.vertices.putFloat(u).putFloat(v).putFloat(rain).putFloat(storm);
		this.vertices.putFloat(width).putFloat(height).putFloat(seed).putFloat(0);
	}

	/** Orders the gathered curtains farthest first (insertion sort: a few dozen, mostly in order from the last frame). */
	private void sortFarFirst(int n) {
		float[] o = this.order;
		float[] tmp = new float[8];
		for (int i = 1; i < n; i++) {
			System.arraycopy(o, i * 8, tmp, 0, 8);
			int j = i - 1;
			while (j >= 0 && o[j * 8] < tmp[0]) {
				System.arraycopy(o, j * 8, o, (j + 1) * 8, 8);
				j--;
			}
			System.arraycopy(tmp, 0, o, (j + 1) * 8, 8);
		}
	}

	private static float hash(long seed, int x, int z, int salt) {
		long h = seed ^ 0x9E3779B97F4A7C15L * salt;
		h ^= x * 0xBF58476D1CE4E5B9L;
		h = Long.rotateLeft(h, 27) * 0x94D049BB133111EBL;
		h ^= z * 0xD6E8FEB86659FD93L;
		h ^= h >>> 31;
		h *= 0xBF58476D1CE4E5B9L;
		h ^= h >>> 29;
		return (h >>> 40) * 0x1.0p-24F;
	}

	void close() {
		MemoryUtil.memFree(this.vertices);
	}
}
