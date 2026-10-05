package dev.voidmc.atlas.core.landmarks;

import dev.voidmc.atlas.core.Coords;
import dev.voidmc.atlas.core.RegionRaw;
import dev.voidmc.atlas.core.names.BiomeFamily;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Reads a region's map (its surface raw region and the eight round it) for what is worth a name. Pure and
 * single-threaded: run on a worker, a few milliseconds a region.
 *
 * <ul>
 *   <li><b>Peaks</b>: on a grid of 4-block cells (the highest ground in each, trees discounted), every local maximum at
 *   least {@value #PEAK_MIN_HEIGHT} blocks above the sea is flooded downhill from, always taking the highest ground
 *   next to what is flooded, until higher ground is reached: the lowest ground crossed on the way is the col, and the
 *   peak's height above it is its <em>prominence</em> (topographic prominence, approximately). A summit with at least
 *   {@value #PEAK_MIN_PROMINENCE} blocks of it is a peak. Unexplored land stops the flood: the prominence found is
 *   then a lower bound, so a peak is never claimed on land not seen.</li>
 *   <li><b>Waterfalls</b>: water standing at least {@value #FALL_MIN_DROP} blocks above the water next to it: the
 *   top of a fall.</li>
 *   <li><b>Ravines</b>: ground at least {@value #RAVINE_MIN_DEPTH} blocks below the land {@value #RAVINE_SPAN} blocks
 *   away on both sides, in a long, narrow run.</li>
 *   <li><b>Patches</b>: {@value RegionFeatures#CELL}-block cells by biome, lake water and river water, joined into
 *   connected patches, each with the cell deepest inside it for its label.</li>
 * </ul>
 */
public final class RegionAnalysis {
	/** What the analysis needs to know of the world's block and biome ids. */
	public interface Lookup {
		boolean leaves(int block);

		BiomeFamily family(int biome);
	}

	public static final int SEA = 63;
	/** How deep inside an area a cell on the region's border counts as, in 8-block cells (the area goes on beyond). */
	static final int BORDER_DEPTH = 12;
	public static final int PEAK_MIN_HEIGHT = 24;
	public static final int PEAK_MIN_PROMINENCE = 30;
	public static final int FALL_MIN_DROP = 8;
	public static final int RAVINE_MIN_DEPTH = 14;
	public static final int RAVINE_SPAN = 6;
	private static final int PEAK_CELL = 4;
	private static final int G = 3 * Coords.REGION_SIZE / PEAK_CELL;
	private static final int PEAK_FLOOD_BUDGET = 60_000;
	private static final short UNKNOWN = Short.MIN_VALUE;

	private RegionAnalysis() {
	}

	public static RegionFeatures analyse(RegionRaw[] around, Lookup lookup) {
		RegionRaw centre = around[4];
		if (centre == null) {
			throw new IllegalArgumentException("no region to analyse");
		}
		List<RegionFeatures.Point> points = new ArrayList<>();
		peaks(around, lookup, points);
		waterfalls(around, points);
		ravines(around, lookup, points);
		return patches(centre, lookup, points);
	}

	// --- columns ---------------------------------------------------------------------------------------------------

	/** The raw region holding centre-local column (lx, lz), which may lie in a neighbour. */
	private static RegionRaw at(RegionRaw[] around, int lx, int lz) {
		int rx = (lx >> Coords.REGION_SHIFT) + 1;
		int rz = (lz >> Coords.REGION_SHIFT) + 1;
		if (rx < 0 || rx > 2 || rz < 0 || rz > 2) {
			return null;
		}
		return around[rz * 3 + rx];
	}

	/** A land column's ground height (not water, not leaves, known), or {@link Integer#MIN_VALUE}. */
	private static int ground(RegionRaw[] around, Lookup lookup, int lx, int lz) {
		RegionRaw r = at(around, lx, lz);
		if (r == null) {
			return Integer.MIN_VALUE;
		}
		int i = RegionRaw.index(lx & Coords.REGION_MASK, lz & Coords.REGION_MASK);
		if (!r.known(i) || (r.flags[i] & RegionRaw.ROCK) != 0 || r.depth(i) > 0 || lookup.leaves(r.block[i])) {
			return Integer.MIN_VALUE;
		}
		return r.height[i];
	}

	// --- peaks -----------------------------------------------------------------------------------------------------

	private static void peaks(RegionRaw[] around, Lookup lookup, List<RegionFeatures.Point> out) {
		short[] g = new short[G * G];
		Arrays.fill(g, UNKNOWN);
		int per = Coords.REGION_SIZE / PEAK_CELL;
		for (int r = 0; r < 9; r++) {
			RegionRaw raw = around[r];
			if (raw == null) {
				continue;
			}
			int ox = (r % 3) * per;
			int oz = (r / 3) * per;
			for (int lz = 0; lz < Coords.REGION_SIZE; lz++) {
				int row = (oz + lz / PEAK_CELL) * G + ox;
				for (int lx = 0; lx < Coords.REGION_SIZE; lx++) {
					int i = RegionRaw.index(lx, lz);
					if (!raw.known(i) || (raw.flags[i] & RegionRaw.ROCK) != 0) {
						continue;
					}
					int h = raw.height[i];
					if (raw.depth(i) == 0 && lookup.leaves(raw.block[i])) {
						// the canopy: the ground is somewhere under it
						h -= 5;
					}
					int c = row + lx / PEAK_CELL;
					if (h > g[c]) {
						g[c] = (short) h;
					}
				}
			}
		}
		List<int[]> found = new ArrayList<>();
		int lo = per;
		int hi = 2 * per;
		int reach = 6;
		for (int cz = lo; cz < hi; cz++) {
			for (int cx = lo; cx < hi; cx++) {
				int c = cz * G + cx;
				int h = g[c];
				if (h == UNKNOWN || h < SEA + PEAK_MIN_HEIGHT) {
					continue;
				}
				boolean max = true;
				for (int dz = -reach; dz <= reach && max; dz++) {
					for (int dx = -reach; dx <= reach; dx++) {
						if (dx == 0 && dz == 0) {
							continue;
						}
						int o = (cz + dz) * G + cx + dx;
						int oh = g[o];
						if (oh > h || oh == h && o < c) {
							max = false;
							break;
						}
					}
				}
				if (!max) {
					continue;
				}
				int prominence = prominence(g, c);
				if (prominence >= PEAK_MIN_PROMINENCE) {
					found.add(new int[]{cx, cz, h, prominence});
				}
			}
		}
		// the higher of two summits close together
		found.sort((a, b) -> Integer.compare(b[2], a[2]));
		List<int[]> kept = new ArrayList<>();
		for (int[] p : found) {
			boolean close = false;
			for (int[] k : kept) {
				if (Math.hypot(k[0] - p[0], k[1] - p[1]) * PEAK_CELL < 64) {
					close = true;
					break;
				}
			}
			if (!close) {
				kept.add(p);
			}
		}
		RegionRaw centre = around[4];
		int baseX = (centre.x << Coords.REGION_SHIFT) - Coords.REGION_SIZE;
		int baseZ = (centre.z << Coords.REGION_SHIFT) - Coords.REGION_SIZE;
		for (int[] p : kept) {
			// the summit's column inside the cell, for its biome
			int x = baseX + p[0] * PEAK_CELL + PEAK_CELL / 2;
			int z = baseZ + p[1] * PEAK_CELL + PEAK_CELL / 2;
			int i = RegionRaw.index(Coords.inRegion(x), Coords.inRegion(z));
			out.add(new RegionFeatures.Point(RegionFeatures.PointKind.PEAK, x, p[2], z, p[3], 0, centre.biome[i]));
		}
	}

	/**
	 * The prominence of the summit at cell {@code c}: flooded downhill, highest ground first, until higher ground is
	 * reached; the lowest ground crossed is the col. Stopped by unexplored land or the edge of what is loaded (then
	 * what was found is a lower bound).
	 */
	static int prominence(short[] g, int c) {
		int peak = g[c];
		long[] heap = new long[1024];
		int size = 0;
		java.util.BitSet seen = new java.util.BitSet(g.length);
		seen.set(c);
		heap[size++] = key(peak, c);
		int col = peak;
		int pops = 0;
		while (size > 0 && pops < PEAK_FLOOD_BUDGET) {
			long top = heap[0];
			heap[0] = heap[--size];
			siftDown(heap, size, 0);
			int h = (int) (top >> 20) - 32768;
			int i = (int) (top & 0xFFFFF);
			pops++;
			if (h > peak) {
				return peak - col;
			}
			col = Math.min(col, h);
			int x = i % G;
			int z = i / G;
			for (int d = 0; d < 4; d++) {
				int nx = x + (d == 0 ? 1 : d == 1 ? -1 : 0);
				int nz = z + (d == 2 ? 1 : d == 3 ? -1 : 0);
				if (nx < 0 || nz < 0 || nx >= G || nz >= G) {
					return peak - col;
				}
				int n = nz * G + nx;
				if (seen.get(n)) {
					continue;
				}
				seen.set(n);
				if (g[n] == UNKNOWN) {
					// unexplored: it may hold higher ground, so stop here
					return peak - col;
				}
				if (size == heap.length) {
					heap = Arrays.copyOf(heap, size * 2);
				}
				heap[size] = key(g[n], n);
				siftUp(heap, size);
				size++;
			}
		}
		return peak - col;
	}

	private static long key(int h, int i) {
		return ((long) (h + 32768) << 20) | i;
	}

	private static void siftUp(long[] heap, int i) {
		long v = heap[i];
		while (i > 0) {
			int p = (i - 1) >> 1;
			if (heap[p] >= v) {
				break;
			}
			heap[i] = heap[p];
			i = p;
		}
		heap[i] = v;
	}

	private static void siftDown(long[] heap, int size, int i) {
		if (size == 0) {
			return;
		}
		long v = heap[i];
		while (true) {
			int l = 2 * i + 1;
			if (l >= size) {
				break;
			}
			int r = l + 1;
			int m = r < size && heap[r] > heap[l] ? r : l;
			if (heap[m] <= v) {
				break;
			}
			heap[i] = heap[m];
			i = m;
		}
		heap[i] = v;
	}

	// --- waterfalls ------------------------------------------------------------------------------------------------

	private static void waterfalls(RegionRaw[] around, List<RegionFeatures.Point> out) {
		RegionRaw r = around[4];
		int baseX = r.x << Coords.REGION_SHIFT;
		int baseZ = r.z << Coords.REGION_SHIFT;
		List<int[]> found = new ArrayList<>();
		int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
		for (int lz = 0; lz < Coords.REGION_SIZE; lz++) {
			for (int lx = 0; lx < Coords.REGION_SIZE; lx++) {
				int i = RegionRaw.index(lx, lz);
				if (!r.known(i) || r.depth(i) == 0 || (r.flags[i] & RegionRaw.ROCK) != 0) {
					continue;
				}
				int h = r.height[i];
				int drop = 0;
				for (int[] d : dirs) {
					RegionRaw n = at(around, lx + d[0], lz + d[1]);
					if (n == null) {
						continue;
					}
					int j = RegionRaw.index((lx + d[0]) & Coords.REGION_MASK, (lz + d[1]) & Coords.REGION_MASK);
					if (n.known(j) && n.depth(j) > 0 && (n.flags[j] & RegionRaw.ROCK) == 0) {
						drop = Math.max(drop, h - n.height[j]);
					}
				}
				if (drop < FALL_MIN_DROP) {
					continue;
				}
				boolean merged = false;
				for (int[] f : found) {
					if (Math.abs(f[0] - lx) <= 16 && Math.abs(f[1] - lz) <= 16) {
						if (drop > f[3]) {
							f[0] = lx;
							f[1] = lz;
							f[2] = h;
							f[3] = drop;
						}
						merged = true;
						break;
					}
				}
				if (!merged && found.size() < 32) {
					found.add(new int[]{lx, lz, h, drop, r.biome[i]});
				}
			}
		}
		for (int[] f : found) {
			out.add(new RegionFeatures.Point(RegionFeatures.PointKind.WATERFALL, baseX + f[0], f[2], baseZ + f[1], f[3], 0, f[4]));
		}
	}

	// --- ravines ---------------------------------------------------------------------------------------------------

	private static void ravines(RegionRaw[] around, Lookup lookup, List<RegionFeatures.Point> out) {
		RegionRaw r = around[4];
		int step = 2;
		int n = Coords.REGION_SIZE / step;
		int[] depth = new int[n * n];
		for (int sz = 0; sz < n; sz++) {
			for (int sx = 0; sx < n; sx++) {
				int lx = sx * step;
				int lz = sz * step;
				int h = ground(around, lookup, lx, lz);
				if (h == Integer.MIN_VALUE) {
					continue;
				}
				int best = 0;
				int e = ground(around, lookup, lx + RAVINE_SPAN, lz);
				int w = ground(around, lookup, lx - RAVINE_SPAN, lz);
				if (e != Integer.MIN_VALUE && w != Integer.MIN_VALUE) {
					best = Math.max(best, Math.min(e, w) - h);
				}
				int s = ground(around, lookup, lx, lz + RAVINE_SPAN);
				int nn = ground(around, lookup, lx, lz - RAVINE_SPAN);
				if (s != Integer.MIN_VALUE && nn != Integer.MIN_VALUE) {
					best = Math.max(best, Math.min(s, nn) - h);
				}
				if (best >= RAVINE_MIN_DEPTH) {
					depth[sz * n + sx] = best;
				}
			}
		}
		// long, narrow runs of such ground
		int[] stack = new int[n * n];
		boolean[] seen = new boolean[n * n];
		int baseX = r.x << Coords.REGION_SHIFT;
		int baseZ = r.z << Coords.REGION_SHIFT;
		int count = 0;
		for (int start = 0; start < n * n && count < 16; start++) {
			if (depth[start] == 0 || seen[start]) {
				continue;
			}
			int top = 0;
			stack[top++] = start;
			seen[start] = true;
			int cells = 0;
			int minX = n, maxX = 0, minZ = n, maxZ = 0;
			int deepest = start;
			while (top > 0) {
				int c = stack[--top];
				cells++;
				int cx = c % n;
				int cz = c / n;
				minX = Math.min(minX, cx);
				maxX = Math.max(maxX, cx);
				minZ = Math.min(minZ, cz);
				maxZ = Math.max(maxZ, cz);
				if (depth[c] > depth[deepest]) {
					deepest = c;
				}
				for (int dz = -1; dz <= 1; dz++) {
					for (int dx = -1; dx <= 1; dx++) {
						int nx = cx + dx;
						int nz = cz + dz;
						if (nx < 0 || nz < 0 || nx >= n || nz >= n) {
							continue;
						}
						int o = nz * n + nx;
						if (depth[o] > 0 && !seen[o]) {
							seen[o] = true;
							stack[top++] = o;
						}
					}
				}
			}
			int length = Math.max(maxX - minX, maxZ - minZ) * step;
			int breadth = Math.min(maxX - minX, maxZ - minZ) * step;
			// long and narrow; a wide bowl is a valley or a crater, not a ravine
			if (length >= 24 && cells >= 12 && cells * step * step <= length * 14 && breadth <= Math.max(16, length / 2)) {
				int lx = (deepest % n) * step;
				int lz = (deepest / n) * step;
				int h = ground(around, lookup, lx, lz);
				int i = RegionRaw.index(lx, lz);
				out.add(new RegionFeatures.Point(RegionFeatures.PointKind.RAVINE, baseX + lx, h, baseZ + lz, depth[deepest], length, r.biome[i]));
				count++;
			}
		}
	}

	// --- patches ---------------------------------------------------------------------------------------------------

	private static RegionFeatures patches(RegionRaw r, Lookup lookup, List<RegionFeatures.Point> points) {
		int n = RegionFeatures.CELLS;
		int cs = RegionFeatures.CELL;
		int[] biome = new int[n * n];
		byte[] kind = new byte[n * n]; // 0 unknown, 1 land, 2 lake water, 3 river water, 4 sea water
		int[] ids = new int[8];
		int[] counts = new int[8];
		for (int cz = 0; cz < n; cz++) {
			for (int cx = 0; cx < n; cx++) {
				int known = 0;
				int wet = 0;
				int distinct = 0;
				for (int z = cz * cs; z < cz * cs + cs; z++) {
					for (int x = cx * cs; x < cx * cs + cs; x++) {
						int i = RegionRaw.index(x, z);
						if (!r.known(i) || (r.flags[i] & RegionRaw.ROCK) != 0) {
							continue;
						}
						known++;
						if (r.depth(i) > 0) {
							wet++;
						}
						int b = r.biome[i];
						int k = 0;
						while (k < distinct && ids[k] != b) {
							k++;
						}
						if (k == distinct && distinct < ids.length) {
							ids[distinct] = b;
							counts[distinct] = 0;
							distinct++;
						}
						if (k < distinct) {
							counts[k]++;
						}
					}
				}
				int c = cz * n + cx;
				if (known < cs * cs / 4) {
					continue;
				}
				int best = 0;
				for (int k = 1; k < distinct; k++) {
					if (counts[k] > counts[best]) {
						best = k;
					}
				}
				biome[c] = ids[best];
				BiomeFamily family = lookup.family(ids[best]);
				if (wet * 2 >= known) {
					kind[c] = switch (family) {
						case RIVER -> 3;
						case OCEAN, BEACH -> 4;
						default -> 2;
					};
				} else {
					kind[c] = 1;
				}
			}
		}
		// land patches: runs of one biome (rivers and beaches are not regions)
		byte[] landCells = new byte[n * n];
		Arrays.fill(landCells, (byte) RegionFeatures.NONE);
		List<RegionFeatures.Patch> land = label(n, landCells, c -> {
			if (kind[c] == 0) {
				return -1;
			}
			BiomeFamily f = lookup.family(biome[c]);
			return f == BiomeFamily.RIVER || f == BiomeFamily.BEACH ? -1 : biome[c];
		}, biome, c -> RegionFeatures.PatchKind.BIOME);
		byte[] waterCells = new byte[n * n];
		Arrays.fill(waterCells, (byte) RegionFeatures.NONE);
		List<RegionFeatures.Patch> water = label(n, waterCells, c -> kind[c] == 2 ? 2 : kind[c] == 3 ? 3 : -1, biome,
			c -> kind[c] == 3 ? RegionFeatures.PatchKind.RIVER : RegionFeatures.PatchKind.LAKE);
		return new RegionFeatures(r.x, r.z, points, landCells, land, waterCells, water);
	}

	/**
	 * Connected patches (4-neighbours) of cells with the same class ({@code classOf}, -1 for none), labelled into
	 * {@code cells}; at most 254 patches (the rest are left unlabelled).
	 */
	private static List<RegionFeatures.Patch> label(int n, byte[] cells, java.util.function.IntUnaryOperator classOf, int[] biome,
		java.util.function.IntFunction<RegionFeatures.PatchKind> kindOf) {
		List<RegionFeatures.Patch> patches = new ArrayList<>();
		int[] cls = new int[n * n];
		for (int c = 0; c < n * n; c++) {
			cls[c] = classOf.applyAsInt(c);
		}
		int[] stack = new int[n * n];
		int[] members = new int[n * n];
		int[] dist = new int[n * n];
		for (int start = 0; start < n * n; start++) {
			if (cls[start] < 0 || (cells[start] & 0xFF) != RegionFeatures.NONE) {
				continue;
			}
			if (patches.size() >= RegionFeatures.NONE - 1) {
				break;
			}
			int id = patches.size();
			int top = 0;
			int count = 0;
			stack[top++] = start;
			cells[start] = (byte) id;
			long sx = 0;
			long sz = 0;
			while (top > 0) {
				int c = stack[--top];
				members[count++] = c;
				int cx = c % n;
				int cz = c / n;
				sx += cx;
				sz += cz;
				int[] ns = {cx + 1 < n ? c + 1 : -1, cx > 0 ? c - 1 : -1, cz + 1 < n ? c + n : -1, cz > 0 ? c - n : -1};
				for (int o : ns) {
					if (o >= 0 && cls[o] == cls[start] && (cells[o] & 0xFF) == RegionFeatures.NONE) {
						cells[o] = (byte) id;
						stack[top++] = o;
					}
				}
			}
			// the label cell: the one deepest inside the patch, its pole of inaccessibility on the 8-block grid (city-block
			// distance to its edge, by a breadth-first walk in from every edge cell). The region's own border is not an
			// edge — the land goes on into the next region — so a cell on it counts as BORDER_DEPTH cells deep: an area
			// across a region corner is named in its middle, not pushed away from the corner.
			int label = members[0];
			int labelDepth = 0;
			int head = 0;
			int tail = 0;
			for (int k = 0; k < count; k++) {
				int c = members[k];
				dist[c] = 0;
				int cx = c % n;
				int cz = c / n;
				if (cx > 0 && (cells[c - 1] & 0xFF) != id || cx < n - 1 && (cells[c + 1] & 0xFF) != id
					|| cz > 0 && (cells[c - n] & 0xFF) != id || cz < n - 1 && (cells[c + n] & 0xFF) != id) {
					dist[c] = 1;
					stack[tail++] = c;
				}
			}
			for (int pass = 0; pass < 2; pass++) {
				if (pass == 1) {
					// then the region's border, as far in as it is not already nearer a real edge
					head = 0;
					tail = 0;
					for (int k = 0; k < count; k++) {
						int c = members[k];
						int cx = c % n;
						int cz = c / n;
						if ((cx == 0 || cz == 0 || cx == n - 1 || cz == n - 1) && (dist[c] == 0 || dist[c] > BORDER_DEPTH)) {
							dist[c] = BORDER_DEPTH;
							stack[tail++] = c;
						}
					}
				}
				while (head < tail) {
					int c = stack[head++];
					int cx = c % n;
					int cz = c / n;
					int[] ns = {cx + 1 < n ? c + 1 : -1, cx > 0 ? c - 1 : -1, cz + 1 < n ? c + n : -1, cz > 0 ? c - n : -1};
					for (int o : ns) {
						if (o >= 0 && (cells[o] & 0xFF) == id && (dist[o] == 0 || dist[o] > dist[c] + 1)) {
							dist[o] = dist[c] + 1;
							stack[tail++] = o;
						}
					}
				}
			}
			double mx = (double) sx / count;
			double mz = (double) sz / count;
			double bestTie = Double.MAX_VALUE;
			for (int k = 0; k < count; k++) {
				int c = members[k];
				double tie = Math.hypot(c % n - mx, c / n - mz);
				if (dist[c] > labelDepth || dist[c] == labelDepth && tie < bestTie) {
					labelDepth = dist[c];
					label = c;
					bestTie = tie;
				}
			}
			patches.add(new RegionFeatures.Patch(kindOf.apply(start), biome[start], count, sx, sz, label, labelDepth));
		}
		return patches;
	}
}
