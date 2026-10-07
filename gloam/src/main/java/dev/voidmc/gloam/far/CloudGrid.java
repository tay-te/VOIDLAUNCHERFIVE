package dev.voidmc.gloam.far;

import dev.voidmc.gloam.air.Air;
import dev.voidmc.gloam.air.Noise;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import org.lwjgl.system.MemoryUtil;

/**
 * Minecraft's clouds laid out by Gloam's weather: vanilla's cells (12 x 12 blocks, the deck 4 high) and vanilla's
 * flat-shaded boxes, but placed from the fronts ({@link Air#front}) instead of {@code clouds.png}. A clear sky has a few
 * small puffs, a fuller sky bigger merged shapes, an overcast a near-continuous slab with a few holes; showers and storms
 * stack thicker, darker clouds into stepped towers, a storm's spreading at its top into an overhanging anvil.
 *
 * <p>The cells lie in the fronts' own drifting frame, so the clouds travel with their weather without the mesh being
 * rebuilt. Out to the far air's reach they get coarser in rings, each twice as far and with cells twice as wide as the
 * last (12, 24, 48 ... blocks), so the deck reaches VOID LOD's horizon in a few hundred thousand faces.
 *
 * <p>The mesh is vanilla's own format (three bytes a face: the cell's x and z halved, the face's direction and flags,
 * {@code CloudRenderer.encodeFace}), in groups that each share one height, cell size and tone, so vanilla's cloud
 * pipelines draw it unchanged, and a shader pack's clouds program, which Iris swaps in for them, draws it too. Built on a
 * worker when the camera has moved a little through the frame, the fronts' picture has changed, or the weather has.
 */
final class CloudGrid {
	/** Vanilla's cell size and deck height. */
	static final int CELL = 12;
	static final int DECK = 4;
	/** The height of each tower layer above the deck, and how many there can be. */
	static final int LAYER = 20;
	static final int LAYERS = 32;
	/** How far each ring reaches in cells of its own size. */
	private static final int RING_CELLS = 128;
	/** Tones: plain, showery (greyer), stormy (dark). */
	static final int TONES = 3;
	/** The shadows' picture, texels across. */
	static final int SHADE = 512;

	private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "VOID Gloam clouds");
		t.setDaemon(true);
		t.setPriority(Thread.MIN_PRIORITY);
		return t;
	});

	/**
	 * One draw: the faces from {@code first} (in faces) on, {@code count} of them, of cells {@code size} blocks wide whose
	 * relative cell (0, 0) is frame cell ({@code originX}, {@code originZ}) of that size, at {@code y} blocks above the
	 * deck's base, {@code height} high, in tone {@code tone}.
	 */
	record Group(int first, int count, int size, int originX, int originZ, int y, int height, int tone) {
	}

	/**
	 * A finished mesh, in the frame it was built in. {@code shade}: the sunlight under the clouds, {@value #SHADE}
	 * square, its corner at frame ({@code shadeX}, {@code shadeZ}) and {@code shadeSize} blocks across.
	 */
	record Mesh(ByteBuffer faces, List<Group> groups, int maxGroup, boolean fast, ByteBuffer shade, double shadeX, double shadeZ, double shadeSize) {
		void free() {
			MemoryUtil.memFree(this.faces);
			if (this.shade != null) {
				MemoryUtil.memFree(this.shade);
			}
		}
	}

	/** What a build is made from. */
	record Input(WeatherMap.Picture map, long seed, float year, float vanilla, boolean showers, float thunder, double frameX, double frameZ,
		double cameraHeight, double base, double near, double reach, boolean fast) {
	}

	private final AtomicReference<Mesh> ready = new AtomicReference<>();
	private volatile boolean working;
	private Input last;

	/** The mesh finished since the last call, or null; the caller owns (and frees) it. */
	Mesh take() {
		return this.ready.getAndSet(null);
	}

	/** Starts a new build if the clouds the camera sees would differ from the last one's. */
	void update(Input in) {
		if (this.working || in.map == null) {
			return;
		}
		Input l = this.last;
		boolean due = l == null || l.map != in.map || l.fast != in.fast || Math.abs(l.vanilla - in.vanilla) > 0.04 || Math.abs(l.thunder - in.thunder) > 0.04
			|| Math.abs(l.frameX - in.frameX) > 2 * CELL || Math.abs(l.frameZ - in.frameZ) > 2 * CELL || Math.abs(l.base - in.base) > 0.5
			|| Math.abs(l.reach - in.reach) > 256 || layerOf(l) != layerOf(in);
		if (!due) {
			return;
		}
		this.last = in;
		this.working = true;
		WORKER.execute(() -> {
			try {
				Mesh old = this.ready.getAndSet(build(in));
				if (old != null) {
					old.free();
				}
			} finally {
				this.working = false;
			}
		});
	}

	/** Which of the deck's planes the camera is between (the faces it sees above and below change there). */
	private static int layerOf(Input in) {
		double above = in.cameraHeight - in.base;
		return above < 0 ? -1 : above < DECK ? 0 : 1 + (int) ((above - DECK) / LAYER);
	}

	void reset() {
		Mesh m = this.ready.getAndSet(null);
		if (m != null) {
			m.free();
		}
		this.last = null;
	}

	// ------------------------------------------------------------------------------------------------- the clouds

	/**
	 * The layers of cloud over frame cell (i, j) of size {@code size}: bit 0 the deck, bit k the k-th tower layer; the
	 * tone in bits 60..61.
	 */
	static long cell(Input in, int i, int j, int size) {
		double fx = (i + 0.5) * size, fz = (j + 0.5) * size;
		float front = in.map.at(fx, fz);
		float rain = Air.rain(in.vanilla, front, in.showers);
		// a few small puffs even under a clear front: a summer sky is rarely empty
		float cover = Math.max(Air.cloudCover(front, rain), 0.1F);
		float storm = Math.max(Air.storm(front, in.year), in.thunder * Noise.smoothstep(0.3F, 0.6F, front));
		float showers = Noise.smoothstep(0.72F, 0.94F, front);
		float n = puffs(in.seed, fx, fz, size);
		// the cover decides how much of the noise is cloud, all but a few holes of it under an overcast
		float threshold = (1 - cover) * 0.96F + 0.025F;
		if (n <= threshold) {
			return 0;
		}
		long layers = 1;
		float inside = Math.min(1, (n - threshold) / Math.max(0.05F, 1 - threshold));
		if (rain > 0.3F && inside > 0.15F) {
			layers |= 2;
		}
		// towers: a smooth swell inside the cloud, stepped into layers, as tall as the shower or storm is strong
		float tower = Math.max(storm, showers * 0.6F);
		if (tower > 0.02F) {
			float swell = stretch(Noise.fbm(in.seed + 41, fx / 560.0, fz / 560.0, 0.5, size >= 192 ? 1 : 2), 2.4F);
			int top = (int) (Math.min(1, inside * 2.5F) * (0.35F + 0.65F * swell) * tower * (LAYERS - 3));
			for (int k = 1; k <= top; k++) {
				layers |= 1L << k;
			}
			if (storm > 0.4F) {
				// the anvil: the top two layers spread wider than the tower, overhanging the cloud beside it
				int anvil = (int) (storm * (LAYERS - 3));
				if (inside > 0.12F && swell > 0.25F) {
					layers |= 3L << Math.max(1, anvil - 1);
				}
			}
		}
		int tone = storm > 0.45F ? 2 : rain > 0.25F || showers > 0.5F ? 1 : 0;
		return layers | (long) tone << 60;
	}

	/** Puffs a few cells across in fields kilometres wide; octaves finer than two cells left out. */
	private static float puffs(long seed, double x, double z, int size) {
		double sum = 0, total = 0, amplitude = 1;
		double wavelength = 1400;
		for (int o = 0; o < 4; o++) {
			if (wavelength < size * 2.2) {
				break;
			}
			sum += amplitude * Noise.value(seed + 31 + o, x / wavelength, z / wavelength, 0.5);
			total += amplitude;
			amplitude *= 0.62;
			wavelength /= 3.1;
		}
		return stretch((float) (sum / Math.max(total, 1e-6)), 2.6F);
	}

	private static float stretch(float v, float by) {
		return Math.clamp((v - 0.5F) * by + 0.5F, 0, 1);
	}

	// ----------------------------------------------------------------------------------------------- the build

	static Mesh build(Input in) {
		List<int[]> facesByGroup = new ArrayList<>();
		List<int[]> keys = new ArrayList<>();
		java.util.Map<Long, Integer> index = new java.util.HashMap<>();
		double camAbove = in.cameraHeight - in.base;
		int size = CELL;
		double inner = 0, outer = Math.max(in.near, 512);
		int n = 2 * RING_CELLS + 3;
		long[] grid = new long[n * n];
		for (int level = 0; inner < in.reach && level < 6; level++) {
			int ox = (int) Math.floor(in.frameX / size), oz = (int) Math.floor(in.frameZ / size);
			double r2o = Math.min(outer, in.reach) * Math.min(outer, in.reach), r2i = inner * inner;
			java.util.Arrays.fill(grid, 0);
			for (int z = 0; z < n; z++) {
				for (int x = 0; x < n; x++) {
					int i = ox + x - RING_CELLS - 1, j = oz + z - RING_CELLS - 1;
					double dx = (i + 0.5) * size - in.frameX, dz = (j + 0.5) * size - in.frameZ;
					double d2 = dx * dx + dz * dz;
					if (d2 >= r2o || d2 < r2i) {
						continue;
					}
					grid[z * n + x] = cell(in, i, j, size);
				}
			}
			for (int z = 1; z < n - 1; z++) {
				for (int x = 1; x < n - 1; x++) {
					long c = grid[z * n + x];
					if (c == 0) {
						continue;
					}
					int tone = (int) (c >>> 60) & 3;
					long layers = c & ~(3L << 60);
					int rx = x - RING_CELLS - 1, rz = z - RING_CELLS - 1;
					// the cell's planes from the camera, in the frame: which of its faces the camera can see
					double minX = (ox + rx) * (double) size - in.frameX, maxX = minX + size;
					double minZ = (oz + rz) * (double) size - in.frameZ, maxZ = minZ + size;
					long north = grid[(z - 1) * n + x], south = grid[(z + 1) * n + x], west = grid[z * n + x - 1], east = grid[z * n + x + 1];
					for (int k = 0; k < LAYERS && layers >>> k != 0; k++) {
						long bit = 1L << k;
						if ((layers & bit) == 0) {
							continue;
						}
						int y = k == 0 ? 0 : DECK + (k - 1) * LAYER, h = k == 0 ? DECK : LAYER;
						int[] faces = group(facesByGroup, keys, index, level, size, ox, oz, k, y, h, tone);
						if (in.fast) {
							if (k == 0) {
								add(facesByGroup, keys, faces, rx, rz, 0, 32);
							}
							continue;
						}
						if ((layers & (bit << 1)) == 0 && camAbove > y + h) {
							add(facesByGroup, keys, faces, rx, rz, 1, 0);
						}
						if ((k == 0 || (layers & (bit >>> 1)) == 0) && camAbove < y) {
							add(facesByGroup, keys, faces, rx, rz, 0, 0);
						}
						if ((north & bit) == 0 && minZ > 0) {
							add(facesByGroup, keys, faces, rx, rz, 2, 0);
						}
						if ((south & bit) == 0 && maxZ < 0) {
							add(facesByGroup, keys, faces, rx, rz, 3, 0);
						}
						if ((west & bit) == 0 && minX > 0) {
							add(facesByGroup, keys, faces, rx, rz, 4, 0);
						}
						if ((east & bit) == 0 && maxX < 0) {
							add(facesByGroup, keys, faces, rx, rz, 5, 0);
						}
					}
				}
			}
			inner = outer;
			outer *= 2;
			size *= 2;
		}
		// one buffer, the groups one after another
		int total = 0, max = 0;
		for (int g = 0; g < keys.size(); g++) {
			total += facesByGroup.get(g)[0];
			max = Math.max(max, facesByGroup.get(g)[0]);
		}
		ByteBuffer out = MemoryUtil.memAlloc(Math.max(3, total * 3));
		List<Group> groups = new ArrayList<>();
		int first = 0;
		for (int g = 0; g < keys.size(); g++) {
			int[] f = facesByGroup.get(g);
			int count = f[0];
			if (count == 0) {
				continue;
			}
			for (int q = 0; q < count; q++) {
				int packed = f[1 + q];
				out.put((byte) (packed >> 16)).put((byte) (packed >> 8)).put((byte) packed);
			}
			int[] k = keys.get(g);
			groups.add(new Group(first, count, k[0], k[1], k[2], k[3], k[4], k[5]));
			first += count;
		}
		out.flip();
		// the shadows' picture, from the coarse cells
		double shadeSize = 2 * Math.min(in.reach, 16384);
		double texel = shadeSize / SHADE;
		double sx = Math.floor((in.frameX - shadeSize / 2) / texel) * texel, sz = Math.floor((in.frameZ - shadeSize / 2) / texel) * texel;
		ByteBuffer shade = MemoryUtil.memAlloc(SHADE * SHADE * 4);
		int shadeCell = 48;
		for (int y = 0; y < SHADE; y++) {
			for (int x = 0; x < SHADE; x++) {
				double fx = sx + (x + 0.5) * texel, fz = sz + (y + 0.5) * texel;
				long c = cell(in, (int) Math.floor(fx / shadeCell), (int) Math.floor(fz / shadeCell), shadeCell);
				long layers = c & ~(3L << 60);
				// in a cloud's shadow about half as bright as the sunlit field beside it, darker under a tall one
				float light = layers == 0 ? 1.0F : 0.6F - 0.15F * Math.min(1, Long.bitCount(layers) / 8.0F);
				byte v = (byte) Math.round(light * 0.5F * 255);
				shade.put(v).put(v).put(v).put((byte) 255);
			}
		}
		shade.flip();
		return new Mesh(out, groups, max, in.fast, shade, sx, sz, shadeSize);
	}

	private static int[] group(List<int[]> faces, List<int[]> keys, java.util.Map<Long, Integer> index, int level, int size, int ox, int oz, int k,
		int y, int h, int tone) {
		long key = ((long) level << 16) | ((long) k << 4) | tone;
		Integer at = index.get(key);
		if (at == null) {
			at = keys.size();
			index.put(key, at);
			keys.add(new int[]{size, ox, oz, y, h, tone, at});
			faces.add(new int[1 + 256]);
		}
		return keys.get(at);
	}

	/** Appends a face in vanilla's encoding (x and z halved, then direction, flags and the halves' lost bits). */
	private static void add(List<int[]> faces, List<int[]> keys, int[] key, int x, int z, int direction, int flags) {
		int g = key[6];
		int[] f = faces.get(g);
		int count = f[0];
		if (count + 1 >= f.length) {
			f = java.util.Arrays.copyOf(f, f.length * 2);
			faces.set(g, f);
		}
		int dirAndFlags = direction | flags | (x & 1) << 7 | (z & 1) << 6;
		f[1 + count] = ((x >> 1) & 255) << 16 | ((z >> 1) & 255) << 8 | (dirAndFlags & 255);
		f[0] = count + 1;
	}
}
