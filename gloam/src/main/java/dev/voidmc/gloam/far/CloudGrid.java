package dev.voidmc.gloam.far;

import dev.voidmc.gloam.air.Air;
import dev.voidmc.gloam.air.Noise;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import org.lwjgl.system.MemoryUtil;

/**
 * Minecraft's clouds laid out by Gloam's weather: vanilla's cells (12 x 12 blocks, a deck 4 high) and vanilla's
 * flat-shaded boxes, but placed from the fronts ({@link Air#front}) instead of {@code clouds.png}, in a stack of layers
 * fitted to the world's height ({@link Layers}): on a tall world a low grey stratus layer over the lowlands, the main
 * cumulus deck in the middle and a thin high deck near the top, so a far mountain range stands between them; on a
 * vanilla-height world one deck near vanilla's cloud height. A clear sky has scattered puffs about as many as vanilla's,
 * a fuller sky bigger merged shapes, an overcast a near-continuous slab with a few holes; showers and storms stack
 * darker clouds into stepped towers from the low base up into the high deck, a storm spreading at its top into an
 * overhanging anvil, wide enough to stand out as a landmark kilometres off.
 *
 * <p>The cells lie in the fronts' own drifting frame, so the clouds travel with their weather without the mesh being
 * rebuilt. Out to the far air's reach they get coarser in rings, each twice as far and with cells twice as wide as the
 * last (12, 24, 48 ... blocks), so the clouds reach VOID LOD's horizon in some tens of thousands of faces.
 *
 * <p>The mesh is vanilla's own format (three bytes a face: the cell's x and z halved, the face's direction and flags,
 * {@code CloudRenderer.encodeFace}), in groups that each share one height, cell size, tone and kind of face (top,
 * bottom, the sides along x and the side toward and away from the low sun), so vanilla's cloud pipelines draw it
 * unchanged, a shader pack's clouds program, which Iris swaps in for them, draws it too, and without a pack each kind of
 * face can take the low sun's colour. Built on a worker when the camera has moved a little through the frame, the
 * fronts' picture has changed, or the weather has.
 */
final class CloudGrid {
	/** Vanilla's cell size and deck height. */
	static final int CELL = 12;
	static final int DECK = 4;
	/** How far each ring reaches in cells of its own size. */
	private static final int RING_CELLS = 128;
	/** The shadows' picture, texels across. */
	static final int SHADE = 512;
	/** The kinds of slab in the stack. */
	static final int LOW = 0, MAIN = 1, HIGH = 2, TOWER = 3;
	/** The kinds of face, for the low sun's colours: bottom, top, the sides along z, the west side, the east side. */
	static final int FACE_BOTTOM = 0, FACE_TOP = 1, FACE_NS = 2, FACE_WEST = 3, FACE_EAST = 4;
	/** Bottoms and tops on the west and east of the camera, so the low sun can light those on its side of the sky. */
	static final int FACE_BOTTOM_EAST = 5, FACE_TOP_EAST = 6;

	private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "VOID Gloam clouds");
		t.setDaemon(true);
		t.setPriority(Thread.MIN_PRIORITY);
		return t;
	});

	/**
	 * The world's cloud layers, in world y: the low stratus deck (or -1 without one), the main deck, the high deck (or
	 * -1), and how high the towers reach.
	 */
	record Layers(int low, int main, int high, int towerTop) {
		/** The stack for a world from its build height and vanilla's cloud height, or one deck {@code lift} above it. */
		static Layers of(float cloudHeight, int maxY, double lift) {
			int vanilla = Math.round(cloudHeight);
			if (lift >= 0) {
				int main = vanilla + (int) Math.round(lift);
				return new Layers(-1, main, -1, main + 600);
			}
			if (maxY - vanilla < 160) {
				// a vanilla-height world: vanilla's one deck, towers up to the top of the world
				return new Layers(-1, vanilla, -1, Math.max(vanilla + 100, maxY - 8));
			}
			int low = vanilla + 16;
			if (maxY - vanilla < 300) {
				// a somewhat taller world: the low stratus, and the main deck near the top, over the highest ground
				int main = maxY - 40;
				return new Layers(low, main, -1, maxY - 8);
			}
			// a tall world: the low stratus, the main deck in the middle, a thin high deck near the top, so the highest
			// ranges stand between the main and the high deck
			int high = maxY - 20;
			int main = low + Math.round((high - low) * 0.45F);
			return new Layers(low, main, high, high);
		}

		/** The lowest cloud there can be: the base everything is placed from. */
		int base() {
			return this.low >= 0 ? this.low : this.main;
		}
	}

	/**
	 * The slabs of the stack, bottom up, touching one another: the decks 4 high, the gaps between them in tower layers of
	 * about 20 blocks. {@code y} from {@link Layers#base}.
	 */
	record Slabs(int[] y, int[] height, int[] kind, int main, int low, int high) {
		static Slabs of(Layers layers) {
			List<int[]> s = new ArrayList<>();
			int base = layers.base();
			int[] decks = layers.high >= 0 ? new int[]{layers.low, layers.main, layers.high}
				: layers.low >= 0 ? new int[]{layers.low, layers.main} : new int[]{layers.main};
			int[] kinds = layers.high >= 0 ? new int[]{LOW, MAIN, HIGH} : layers.low >= 0 ? new int[]{LOW, MAIN} : new int[]{MAIN};
			for (int d = 0; d < decks.length; d++) {
				s.add(new int[]{decks[d] - base, DECK, kinds[d]});
				int from = decks[d] + DECK, to = d + 1 < decks.length ? decks[d + 1] : layers.towerTop;
				int span = to - from;
				if (span > 0) {
					int n = Math.max(1, Math.round(span / 20.0F));
					for (int k = 0; k < n; k++) {
						int y0 = from + span * k / n, y1 = from + span * (k + 1) / n;
						s.add(new int[]{y0 - base, y1 - y0, TOWER});
					}
				}
			}
			int count = Math.min(56, s.size());
			int[] y = new int[count], h = new int[count], k = new int[count];
			int main = 0, low = -1, high = -1;
			for (int i = 0; i < count; i++) {
				y[i] = s.get(i)[0];
				h[i] = s.get(i)[1];
				k[i] = s.get(i)[2];
				if (k[i] == MAIN) {
					main = i;
				} else if (k[i] == LOW) {
					low = i;
				} else if (k[i] == HIGH) {
					high = i;
				}
			}
			return new Slabs(y, h, k, main, low, high);
		}

		int count() {
			return this.y.length;
		}
	}

	/**
	 * One draw: the faces from {@code first} (in faces) on, {@code count} of them, of cells {@code size} blocks wide whose
	 * relative cell (0, 0) is frame cell ({@code originX}, {@code originZ}) of that size, at {@code y} blocks above the
	 * stack's base, {@code height} high, in tone {@code tone}, all of kind of face {@code face}.
	 */
	record Group(int first, int count, int size, int originX, int originZ, int y, int height, int tone, int face) {
	}

	/**
	 * A finished mesh, in the frame it was built in. {@code shade}: the sunlight under the clouds, {@value #SHADE}
	 * square, its corner at frame ({@code shadeX}, {@code shadeZ}) and {@code shadeSize} blocks across.
	 */
	record Mesh(ByteBuffer faces, List<Group> groups, int maxGroup, boolean fast, ByteBuffer shade, double shadeX, double shadeZ, double shadeSize,
		Layers layers) {
		void free() {
			MemoryUtil.memFree(this.faces);
			if (this.shade != null) {
				MemoryUtil.memFree(this.shade);
			}
		}
	}

	/**
	 * What a build is made from: the fronts, the weather, the camera in the fronts' frame (and the drift from that frame
	 * to the world now, to find the far land under a cell), the layers, how far the finest cells and all of them reach.
	 */
	record Input(WeatherMap.Picture map, long seed, float year, float vanilla, boolean showers, float thunder, double frameX, double frameZ,
		double driftX, double driftZ, double cameraHeight, Layers layers, FarLand land, double near, double reach, boolean fast) {
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
			|| Math.abs(l.frameX - in.frameX) > 2 * CELL || Math.abs(l.frameZ - in.frameZ) > 2 * CELL || !l.layers.equals(in.layers)
			|| Math.abs(l.reach - in.reach) > 256 || slabOf(l) != slabOf(in);
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

	/** Which slab the camera is in or under (the faces it sees above and below change there). */
	private static int slabOf(Input in) {
		Slabs s = Slabs.of(in.layers);
		double above = in.cameraHeight - in.layers.base();
		int k = 0;
		while (k < s.count() && above >= s.y()[k]) {
			k++;
		}
		return k;
	}

	void reset() {
		Mesh m = this.ready.getAndSet(null);
		if (m != null) {
			m.free();
		}
		this.last = null;
	}

	// ------------------------------------------------------------------------------------------------- the clouds

	/** The slabs of cloud over frame cell (i, j) of size {@code size}, a bit each; the tone in bits 60..61. */
	static long cell(Input in, Slabs slabs, int i, int j, int size) {
		double fx = (i + 0.5) * size, fz = (j + 0.5) * size;
		float front = in.map.at(fx, fz);
		float rain = Air.rain(in.vanilla, front, in.showers);
		float storm = Math.max(Air.storm(front, in.year), in.thunder * Noise.smoothstep(0.3F, 0.6F, front));
		// a storm's footprint wider than its core, so a thunderhead stands out kilometres off
		float wideStorm = Math.max(storm, Noise.smoothstep(0.79F, 0.93F, front));
		float showers = Noise.smoothstep(0.72F, 0.94F, front);
		long mask = 0;
		// the main deck: scattered puffs about as many as vanilla's under a clear front, all but a few holes of it under
		// an overcast
		float cover = Math.max(Air.cloudCover(front, rain), 0.34F);
		float n = puffs(in.seed, fx, fz, size, 1400, 31);
		float threshold = (1 - cover) * 0.92F + 0.04F;
		boolean main = n > threshold || wideStorm > 0.5F;
		if (main) {
			mask |= 1L << slabs.main();
		}
		// the low stratus: wide grey sheets over the lowlands under wet fronts, breaking up over high ground
		if (slabs.low() >= 0) {
			int lowY = in.layers.low();
			float ground = in.land == null ? 0 : in.land.groundAt(fx + in.driftX, fz + in.driftZ, Float.NaN);
			float lowland = Float.isNaN(ground) ? 0.6F : 1 - Noise.smoothstep(lowY - 90, lowY - 20, ground);
			float wet = Math.max(rain, Noise.smoothstep(0.45F, 0.82F, front));
			float stratus = Math.min(0.95F, (0.1F + 0.9F * wet) * lowland);
			if (stratus > 0.05F && puffs(in.seed + 7, fx, fz, size, 2600, 51) > 1 - stratus * 0.9F) {
				mask |= 1L << slabs.low();
			}
		}
		// the high deck: thin wide sheets, more ahead of a front
		if (slabs.high() >= 0) {
			float sheets = 0.06F + 0.6F * Noise.smoothstep(0.35F, 0.8F, front);
			if (puffs(in.seed + 13, fx, fz, size, 4200, 71) > 1 - sheets) {
				mask |= 1L << slabs.high();
			}
		}
		// towers: a smooth swell inside the cloud, stepped into layers, as tall as the shower or storm is strong, from
		// the lowest deck up into the high one
		float tower = Math.max(wideStorm, showers * 0.55F);
		if (tower > 0.03F) {
			float swell = stretch(Noise.fbm(in.seed + 41, fx / 1800.0, fz / 1800.0, 0.5, size >= 384 ? 1 : 2), 2.2F);
			float strength = tower * (0.35F + 0.65F * swell);
			int bottom = slabs.low() >= 0 ? slabs.low() : slabs.main();
			int count = slabs.count();
			if (strength > 0.18F && (main || wideStorm > 0.4F)) {
				int top = bottom + (int) ((count - 1 - bottom) * Math.min(1, (strength - 0.18F) / 0.6F));
				for (int k = bottom; k <= top; k++) {
					mask |= 1L << k;
				}
			}
			if (wideStorm > 0.55F && strength > 0.1F) {
				// the anvil: the top two slabs spread wider than the tower under them, overhanging the cloud beside it
				mask |= 3L << (count - 2);
			}
		}
		if (mask == 0) {
			return 0;
		}
		int tone = wideStorm > 0.45F ? 2 : rain > 0.25F || showers > 0.5F ? 1 : 0;
		return mask | (long) tone << 60;
	}

	/** Puffs a few cells across in fields kilometres wide; octaves finer than two cells left out. */
	private static float puffs(long seed, double x, double z, int size, double wavelength, int salt) {
		double sum = 0, total = 0, amplitude = 1;
		for (int o = 0; o < 4; o++) {
			if (wavelength < size * 2.2) {
				break;
			}
			sum += amplitude * Noise.value(seed + salt + o, x / wavelength, z / wavelength, 0.5);
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
		Slabs slabs = Slabs.of(in.layers);
		Faces faces = new Faces();
		double camAbove = in.cameraHeight - in.layers.base();
		int size = CELL;
		double inner = 0, outer = Math.max(in.near, 512);
		int n = 2 * RING_CELLS + 3;
		long[] grid = new long[n * n];
		long allSlabs = (1L << slabs.count()) - 1;
		for (int level = 0; inner < in.reach && level < 6; level++) {
			int ox = (int) Math.floor(in.frameX / size), oz = (int) Math.floor(in.frameZ / size);
			double r2o = Math.min(outer, in.reach) * Math.min(outer, in.reach), r2i = inner * inner;
			Arrays.fill(grid, 0);
			for (int z = 0; z < n; z++) {
				for (int x = 0; x < n; x++) {
					int i = ox + x - RING_CELLS - 1, j = oz + z - RING_CELLS - 1;
					double dx = (i + 0.5) * size - in.frameX, dz = (j + 0.5) * size - in.frameZ;
					double d2 = dx * dx + dz * dz;
					if (d2 >= r2o || d2 < r2i) {
						continue;
					}
					grid[z * n + x] = cell(in, slabs, i, j, size);
				}
			}
			for (int z = 1; z < n - 1; z++) {
				for (int x = 1; x < n - 1; x++) {
					long c = grid[z * n + x];
					if (c == 0) {
						continue;
					}
					int tone = (int) (c >>> 60) & 3;
					long mask = c & allSlabs;
					int rx = x - RING_CELLS - 1, rz = z - RING_CELLS - 1;
					// the cell's planes from the camera, in the frame: which of its faces the camera can see
					double minX = (ox + rx) * (double) size - in.frameX, maxX = minX + size;
					double minZ = (oz + rz) * (double) size - in.frameZ, maxZ = minZ + size;
					long north = grid[(z - 1) * n + x], south = grid[(z + 1) * n + x], west = grid[z * n + x - 1], east = grid[z * n + x + 1];
					for (int k = 0; k < slabs.count(); k++) {
						long bit = 1L << k;
						if ((mask & bit) == 0) {
							continue;
						}
						int y = slabs.y()[k], h = slabs.height()[k];
						if (in.fast) {
							if (slabs.kind()[k] != TOWER) {
								faces.add(level, size, ox, oz, y, h, tone, FACE_BOTTOM, rx, rz, 0, 32);
							}
							continue;
						}
						boolean eastward = minX + maxX > 0;
						if ((mask & (bit << 1)) == 0 && camAbove > y + h) {
							faces.add(level, size, ox, oz, y, h, tone, eastward ? FACE_TOP_EAST : FACE_TOP, rx, rz, 1, 0);
						}
						if ((k == 0 || (mask & (bit >>> 1)) == 0) && camAbove < y) {
							faces.add(level, size, ox, oz, y, h, tone, eastward ? FACE_BOTTOM_EAST : FACE_BOTTOM, rx, rz, 0, 0);
						}
						if ((north & bit) == 0 && minZ > 0) {
							faces.add(level, size, ox, oz, y, h, tone, FACE_NS, rx, rz, 2, 0);
						}
						if ((south & bit) == 0 && maxZ < 0) {
							faces.add(level, size, ox, oz, y, h, tone, FACE_NS, rx, rz, 3, 0);
						}
						if ((west & bit) == 0 && minX > 0) {
							faces.add(level, size, ox, oz, y, h, tone, FACE_WEST, rx, rz, 4, 0);
						}
						if ((east & bit) == 0 && maxX < 0) {
							faces.add(level, size, ox, oz, y, h, tone, FACE_EAST, rx, rz, 5, 0);
						}
					}
				}
			}
			inner = outer;
			outer *= 2;
			size *= 2;
		}
		Mesh partial = faces.mesh(in.fast, in.layers);
		// the shadows' picture, from coarse cells of the main deck and the towers
		double shadeSize = 2 * Math.min(in.reach, 16384);
		double texel = shadeSize / SHADE;
		double sx = Math.floor((in.frameX - shadeSize / 2) / texel) * texel, sz = Math.floor((in.frameZ - shadeSize / 2) / texel) * texel;
		ByteBuffer shade = MemoryUtil.memAlloc(SHADE * SHADE * 4);
		int shadeCell = 48;
		for (int y = 0; y < SHADE; y++) {
			for (int x = 0; x < SHADE; x++) {
				double fx = sx + (x + 0.5) * texel, fz = sz + (y + 0.5) * texel;
				long mask = cell(in, slabs, (int) Math.floor(fx / shadeCell), (int) Math.floor(fz / shadeCell), shadeCell) & allSlabs;
				// in a cloud's shadow about half as bright as the sunlit field beside it, darker under a tall one
				float light = mask == 0 ? 1.0F : 0.62F - 0.17F * Math.min(1, Long.bitCount(mask) / 10.0F);
				byte v = (byte) Math.round(light * 0.5F * 255);
				shade.put(v).put(v).put(v).put((byte) 255);
			}
		}
		shade.flip();
		return new Mesh(partial.faces(), partial.groups(), partial.maxGroup(), in.fast, shade, sx, sz, shadeSize, in.layers);
	}

	/** The faces gathered by group as they are made, then laid out one group after another. */
	private static final class Faces {
		private final List<int[]> keys = new ArrayList<>();
		private final List<int[]> lists = new ArrayList<>();
		private final Map<Long, Integer> index = new HashMap<>();

		/** Appends a face in vanilla's encoding (x and z halved, then direction, flags and the halves' lost bits). */
		void add(int level, int size, int ox, int oz, int y, int h, int tone, int face, int x, int z, int direction, int flags) {
			long key = ((long) level << 24) | ((long) y << 8) | ((long) tone << 4) | face;
			Integer at = this.index.get(key);
			if (at == null) {
				at = this.keys.size();
				this.index.put(key, at);
				this.keys.add(new int[]{size, ox, oz, y, h, tone, face});
				this.lists.add(new int[1 + 256]);
			}
			int[] f = this.lists.get(at);
			int count = f[0];
			if (count + 1 >= f.length) {
				f = Arrays.copyOf(f, f.length * 2);
				this.lists.set(at, f);
			}
			int dirAndFlags = direction | flags | (x & 1) << 7 | (z & 1) << 6;
			f[1 + count] = ((x >> 1) & 255) << 16 | ((z >> 1) & 255) << 8 | (dirAndFlags & 255);
			f[0] = count + 1;
		}

		Mesh mesh(boolean fast, Layers layers) {
			int total = 0, max = 0;
			for (int[] f : this.lists) {
				total += f[0];
				max = Math.max(max, f[0]);
			}
			ByteBuffer out = MemoryUtil.memAlloc(Math.max(3, total * 3));
			List<Group> groups = new ArrayList<>();
			int first = 0;
			for (int g = 0; g < this.keys.size(); g++) {
				int[] f = this.lists.get(g);
				int count = f[0];
				if (count == 0) {
					continue;
				}
				for (int q = 0; q < count; q++) {
					int packed = f[1 + q];
					out.put((byte) (packed >> 16)).put((byte) (packed >> 8)).put((byte) packed);
				}
				int[] k = this.keys.get(g);
				groups.add(new Group(first, count, k[0], k[1], k[2], k[3], k[4], k[5], k[6]));
				first += count;
			}
			return new Mesh(out.flip(), groups, max, fast, null, 0, 0, 0, layers);
		}
	}
}
