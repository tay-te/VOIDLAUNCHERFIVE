package dev.voidmc.hearth.sim.wild;

import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.TerrainProbe;
import dev.voidmc.hearth.sim.atlas.Atlas;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;

/**
 * The site overlay of posts (docs/LIVING_WORLD_2.md §7.2): where, in one 1024 × 1024 atlas region, the small places in the
 * wild stand. A pure function of the seed, the region and the land (the rough terrain probe, the biomes, the atlas's
 * sites and the historic roads), with its own random stream, so no town's history changes and any thread gets the same
 * answer.
 *
 * <ul>
 *   <li>Road posts from the network (§1): a <b>toll gate</b> at the land end of a big river's bridge, or at the middle
 *   of a king's road of 950–1 200 blocks (where one town's land meets the other's); a <b>caravan stop</b> at the middle
 *   of a king's road of 1 300 and more; a <b>wayside inn</b> at the middle of any road of 1 200 and more (the network
 *   joins places within 2 400 blocks, so that is as far from a roof as a road goes).</li>
 *   <li>Land posts by Expanse's own site rules: a <b>hospice</b> on a pass (a saddle rising 12+ along one axis at ±48
 *   and falling 8+ across it, y 110 and up; a road over it preferred), a <b>monastery</b> on a summit or knoll (standing
 *   12+ over its ring at 56, y 150 and up), a <b>hunters' camp</b> on a wood's edge 300–900 from a town, a
 *   <b>hermitage</b> 600 and more from roads and towns in deep wood or on broken ground, a <b>harbour</b> on a coast or a
 *   big lake 600 from towns, a <b>farmstead</b> on upland pasture or moor, a <b>nomad camp</b> on steppe or dunes.</li>
 * </ul>
 * At most {@code per_region} posts, each 300 + the town's reach from every town (a toll gate 150), 200 from each other (100 from the region's
 * edges, so posts in neighbouring regions never crowd), on dry land clear of structures.
 */
public final class PostSites {
	/** Posts keep this far from their region's edges (so two in neighbouring regions stand {@link #SPACING} apart). */
	public static final int EDGE = 100;
	public static final int SPACING = 200;
	/** From a town's reach ({@link Land#towns}). */
	public static final int TOWN_CLEARANCE = 300;
	/** A toll gate's, from a town's reach. */
	public static final int TOLL_CLEARANCE = 150;
	private static final int GRID = 48;
	/** Bridge columns in a row that make a big river's bridge (a toll at its land end). */
	static final int BRIDGE_RUN = 6;
	/** A road this long (blocks along it) has an inn at its middle; a king's road a caravan stop from {@link #CARAVAN_ROAD}. */
	static final int INN_ROAD = 1200;
	static final int CARAVAN_ROAD = 1300;
	/** A king's road this long, but shorter than an inn's, has a toll at its middle (where one town's land meets the other's). */
	static final int BORDER_ROAD = 950;
	private static final long SALT = 0x5057_5317_A11DL;

	private PostSites() {
	}

	/** What the siting reads of the world. Any thread; deterministic. */
	public interface Land {
		TerrainProbe probe();

		/** The biome's id path at a column ({@code dark_forest}), or "" where there is none (synthetic land). */
		String biome(int x, int z);

		Culture culture(int x, int z);

		/** Towns whose land may reach into the region (sites of it and its eight neighbours): {@code {x, z, reach}}. */
		List<int[]> towns(int rx, int rz);

		/** The historic roads whose way crosses the region. */
		List<RoadLine> roads(int rx, int rz);

		/** Whether a column is clear of structures (villages, temples) to keep off. */
		boolean clear(int x, int z);
	}

	/**
	 * A road's way: its dense centreline (one column a step) and what each column is ({@code Route.GRADE/FORD/BRIDGE}).
	 *
	 * @param kings a king's road (between towns) rather than a track
	 */
	public record RoadLine(long id, boolean kings, int[] xs, int[] ys, int[] zs, byte[] types) {
		public int size() {
			return this.xs.length;
		}
	}

	/** A candidate before the choice. */
	record Candidate(PostType type, int x, int z, double score, Direction facing, int[] road, int gx, int gz) {
	}

	/** The chance a region with a fitting spot has a post of each type (rolled in this order, whatever the land). */
	static double chance(PostType t) {
		return switch (t) {
			case HOSPICE -> 1.0;
			case TOLL_GATE -> 0.8;
			case CARAVAN_STOP -> 0.7;
			case INN -> 0.85;
			case MONASTERY -> 0.55;
			case HARBOUR -> 0.5;
			case NOMAD_CAMP -> 0.6;
			case FARMSTEAD -> 0.5;
			case HUNTERS_CAMP -> 0.5;
			case HERMITAGE -> 0.4;
		};
	}

	/** The order types are chosen in: what the land demands first, the commonest last. */
	static final PostType[] PRIORITY = {PostType.HOSPICE, PostType.TOLL_GATE, PostType.CARAVAN_STOP, PostType.INN, PostType.MONASTERY,
		PostType.HARBOUR, PostType.NOMAD_CAMP, PostType.FARMSTEAD, PostType.HUNTERS_CAMP, PostType.HERMITAGE};

	/** The region's posts, in index order. */
	public static List<PostSite> region(Land land, WildConfig.Wild config, int rx, int rz) {
		if (!config.enabled() || config.perRegion() <= 0) {
			return List.of();
		}
		TerrainProbe t = land.probe();
		RandomSource random = RandomSource.create(mix(t.seed(), rx, rz));
		boolean[] rolled = new boolean[PostType.values().length];
		for (PostType type : PRIORITY) {
			rolled[type.ordinal()] = random.nextDouble() < chance(type);
		}
		long ageSeed = random.nextLong();
		List<int[]> towns = land.towns(rx, rz);
		List<RoadLine> roads = land.roads(rx, rz);
		List<Candidate> all = new ArrayList<>();
		roadCandidates(land, roads, rx, rz, all);
		landCandidates(land, roads, towns, rx, rz, all);
		all.sort(Comparator.comparingDouble(Candidate::score).reversed().thenComparingInt(Candidate::x).thenComparingInt(Candidate::z));
		List<Candidate> chosen = new ArrayList<>();
		int max = Math.min(4, config.perRegion());
		for (PostType type : PRIORITY) {
			if (!rolled[type.ordinal()]) {
				continue;
			}
			// two hospices where a region holds two passes far apart; one of every other kind
			int want = type == PostType.HOSPICE ? 2 : 1;
			for (Candidate c : all) {
				if (want <= 0 || chosen.size() >= max) {
					break;
				}
				if (c.type != type || !fits(c, chosen, towns, land)) {
					continue;
				}
				chosen.add(c);
				want--;
			}
		}
		List<PostSite> out = new ArrayList<>(chosen.size());
		RandomSource ages = RandomSource.create(ageSeed);
		for (int i = 0; i < chosen.size(); i++) {
			Candidate c = chosen.get(i);
			int age = 40 + ages.nextInt(400);
			long seed = ages.nextLong();
			out.add(new PostSite(PostSite.key(rx, rz, i), c.type, c.x, t.groundY(c.x, c.z), c.z, c.facing, age, seed, c.road, c.gx, c.gz, c.score));
		}
		return out;
	}

	private static long mix(long seed, int rx, int rz) {
		long h = seed ^ SALT;
		h ^= rx * 0x9E3779B97F4A7C15L;
		h = Long.rotateLeft(h, 31) ^ rz * 0xC2B2AE3D27D4EB4FL;
		h = (h ^ (h >>> 33)) * 0xFF51AFD7ED558CCDL;
		return h ^ (h >>> 29);
	}

	/** Spacing, towns, structures. */
	private static boolean fits(Candidate c, List<Candidate> chosen, List<int[]> towns, Land land) {
		int need = c.type == PostType.HOSPICE ? 400 : SPACING;
		for (Candidate o : chosen) {
			int s = o.type == PostType.HOSPICE && c.type == PostType.HOSPICE ? 400 : need;
			if (dist(o.x, o.z, c.x, c.z) < s + Math.max(c.type.radius, o.type.radius)) {
				return false;
			}
		}
		// a toll gate keeps the lord's road where one town's land meets the next's: nearer than the rest may stand
		double far = c.type == PostType.HERMITAGE || c.type == PostType.HARBOUR ? 600 : c.type == PostType.TOLL_GATE ? TOLL_CLEARANCE : TOWN_CLEARANCE;
		for (int[] tn : towns) {
			double d = dist(tn[0], tn[1], c.x, c.z) - tn[2];
			if (d < far + c.type.radius) {
				return false;
			}
			if (c.type == PostType.HUNTERS_CAMP && d > 900) {
				// (a camp within 900 of some town; checked separately below)
				continue;
			}
		}
		if (c.type == PostType.HUNTERS_CAMP) {
			boolean near = false;
			for (int[] tn : towns) {
				double d = dist(tn[0], tn[1], c.x, c.z) - tn[2];
				near |= d >= TOWN_CLEARANCE && d <= 900;
			}
			if (!near) {
				return false;
			}
		}
		// clear of structures at its middle and corners
		int r = c.type.radius;
		return land.clear(c.x, c.z) && land.clear(c.x - r, c.z - r) && land.clear(c.x + r, c.z + r) && land.clear(c.x - r, c.z + r)
			&& land.clear(c.x + r, c.z - r);
	}

	static double dist(int ax, int az, int bx, int bz) {
		return Math.hypot(ax - bx, az - bz);
	}

	private static boolean inside(int rx, int rz, int x, int z, int pad) {
		int x0 = (rx << Atlas.REGION_BITS) + EDGE + pad;
		int z0 = (rz << Atlas.REGION_BITS) + EDGE + pad;
		int x1 = (rx << Atlas.REGION_BITS) + Atlas.REGION_SIZE - 1 - EDGE - pad;
		int z1 = (rz << Atlas.REGION_BITS) + Atlas.REGION_SIZE - 1 - EDGE - pad;
		return x >= x0 && x <= x1 && z >= z0 && z <= z1;
	}

	private static boolean dry(TerrainProbe t, int x, int z) {
		return !t.isWater(x, z) && t.groundY(x, z) > t.seaLevel();
	}

	// ---------------------------------------------------------------- along the roads

	private static void roadCandidates(Land land, List<RoadLine> roads, int rx, int rz, List<Candidate> out) {
		TerrainProbe t = land.probe();
		for (RoadLine r : roads) {
			int n = r.size();
			if (n < 16) {
				continue;
			}
			double[] along = new double[n];
			for (int i = 1; i < n; i++) {
				along[i] = along[i - 1] + Math.hypot(r.xs[i] - r.xs[i - 1], r.zs[i] - r.zs[i - 1]);
			}
			double total = along[n - 1];
			// a toll gate at the land end of every long bridge (a big river)
			int run = 0;
			for (int i = 0; i <= n; i++) {
				boolean bridge = i < n && r.types[i] == 2;
				if (bridge) {
					run++;
					continue;
				}
				if (run >= BRIDGE_RUN) {
					// on the graded road a little way off the bridge's land end, either end, where the road runs level
					// between level banks (the gate's piers stand on the ground, its passage on the road)
					boolean placed = false;
					for (int dir : new int[]{1, -1}) {
						int start = dir > 0 ? i : i - run - 1;
						for (int k = 6; k <= 30 && !placed; k += 2) {
							int end = start + dir * k;
							if (end < 6 || end >= n - 6 || along[end] < 200 || total - along[end] < 200) {
								continue;
							}
							int x = r.xs[end];
							int z = r.zs[end];
							if (!inside(rx, rz, x, z, PostType.TOLL_GATE.radius) || !dry(t, x, z) || !levelGate(t, r, end)) {
								continue;
							}
							out.add(new Candidate(PostType.TOLL_GATE, x, z, 50 + run - k / 4.0, axisFacing(r, end), slice(r, end), 0, 0));
							placed = true;
						}
						if (placed) {
							break;
						}
					}
				}
				run = 0;
			}
			// the middle of a long road: a caravan stop on a king's road, an inn on any
			if (total >= INN_ROAD) {
				for (PostType type : r.kings && total >= CARAVAN_ROAD ? new PostType[]{PostType.CARAVAN_STOP, PostType.INN} : new PostType[]{PostType.INN}) {
					Candidate best = null;
					for (int k = -200; k <= 200; k += 8) {
						int i = indexAt(along, total / 2 + k);
						if (i < 4 || i >= n - 4 || r.types[i] != 0) {
							continue;
						}
						for (int side : new int[]{-1, 1}) {
							Candidate c = beside(t, r, i, side, type, rx, rz, Math.abs(k));
							if (c != null && (best == null || c.score > best.score)) {
								best = c;
							}
						}
					}
					if (best != null) {
						out.add(best);
					}
				}
			} else if (r.kings && total >= BORDER_ROAD) {
				// a shorter king's road between two lords' towns: the toll where one's land gives way to the other's, about
				// its middle (on graded road, not a bridge or ford)
				Candidate best = null;
				for (int k = -120; k <= 120; k += 6) {
					int i = indexAt(along, total / 2 + k);
					if (i < 4 || i >= n - 4 || r.types[i] != 0) {
						continue;
					}
					int x = r.xs[i];
					int z = r.zs[i];
					if (!inside(rx, rz, x, z, PostType.TOLL_GATE.radius) || !dry(t, x, z)) {
						continue;
					}
					// level along the road through the gate
					int a = Math.max(0, i - 6);
					int b = Math.min(n - 1, i + 6);
					int rise = Math.abs(r.ys[b] - r.ys[a]);
					double score = 30 - rise * 3 - Math.abs(k) / 30.0;
					if (rise <= 3 && levelGate(t, r, i) && (best == null || score > best.score)) {
						best = new Candidate(PostType.TOLL_GATE, x, z, score, axisFacing(r, i), slice(r, i), 0, 0);
					}
				}
				if (best != null) {
					out.add(best);
				}
			}
		}
	}

	/**
	 * Whether a toll gate fits astride the road at column {@code i}: graded road for 6 columns either way rising at most 2,
	 * and the ground under its piers (5 to 6 across either side) within 3 of the road's level, so the gate stands on the
	 * land rather than half sunk in a bank or up on stilts.
	 */
	static boolean levelGate(TerrainProbe t, RoadLine r, int i) {
		int a = Math.max(0, i - 6);
		int b = Math.min(r.size() - 1, i + 6);
		for (int j = a; j <= b; j++) {
			if (r.types[j] != 0) {
				return false;
			}
		}
		if (Math.abs(r.ys[b] - r.ys[a]) > 2) {
			return false;
		}
		double dx = r.xs[b] - r.xs[a];
		double dz = r.zs[b] - r.zs[a];
		double len = Math.max(1, Math.hypot(dx, dz));
		int y = r.ys[i];
		for (int side : new int[]{-1, 1}) {
			for (int off : new int[]{4, 6}) {
				for (int along : new int[]{-3, 0, 3}) {
					int x = (int) Math.round(r.xs[i] + side * -dz / len * off + dx / len * along);
					int z = (int) Math.round(r.zs[i] + side * dx / len * off + dz / len * along);
					if (t.isWater(x, z) || Math.abs(t.groundY(x, z) - y) > 3) {
						return false;
					}
				}
			}
		}
		return true;
	}

	/** The index of the column {@code d} blocks along a road. */
	private static int indexAt(double[] along, double d) {
		int lo = 0;
		int hi = along.length - 1;
		while (lo < hi) {
			int m = (lo + hi) >>> 1;
			if (along[m] < d) {
				lo = m + 1;
			} else {
				hi = m;
			}
		}
		return lo;
	}

	/**
	 * A post standing beside the road at column {@code i}, on the {@code side} (left −1, right +1 of the way the road
	 * runs): its centre {@code radius·0.6 + 6} off the centreline, facing the road; scored for level ground, water near
	 * and nearness to the middle.
	 */
	private static Candidate beside(TerrainProbe t, RoadLine r, int i, int side, PostType type, int rx, int rz, int offMiddle) {
		int a = Math.max(0, i - 4);
		int b = Math.min(r.size() - 1, i + 4);
		double dx = r.xs[b] - r.xs[a];
		double dz = r.zs[b] - r.zs[a];
		double len = Math.max(1, Math.hypot(dx, dz));
		// left of the way: (dz, -dx)
		double nx = side * -dz / len;
		double nz = side * dx / len;
		int off = (int) Math.round(type.radius * 0.6) + 6;
		int x = (int) Math.round(r.xs[i] + nx * off);
		int z = (int) Math.round(r.zs[i] + nz * off);
		if (!inside(rx, rz, x, z, type.radius) || !dry(t, x, z)) {
			return null;
		}
		int g = t.groundY(x, z);
		int roadY = r.ys[i];
		double rough = 0;
		int span = type.radius - 6;
		for (int[] o : new int[][]{{span, 0}, {-span, 0}, {0, span}, {0, -span}, {span, span}, {-span, -span}, {span, -span}, {-span, span}}) {
			int sx = x + o[0];
			int sz = z + o[1];
			if (t.isWater(sx, sz)) {
				return null;
			}
			rough += Math.abs(t.groundY(sx, sz) - g);
		}
		if (rough > 8 * 5 || Math.abs(g - roadY) > 6) {
			return null;
		}
		boolean water = false;
		for (int[] o : new int[][]{{48, 0}, {-48, 0}, {0, 48}, {0, -48}}) {
			water |= t.isWater(x + o[0], z + o[1]);
		}
		Direction facing = Direction.getApproximateNearest(-nx, 0, -nz);
		double score = 40 - rough - Math.abs(g - roadY) * 2 + (water ? 6 : 0) - offMiddle / 40.0;
		return new Candidate(type, x, z, score, facing, slice(r, i), 0, 0);
	}

	/** The road's way, every other column, 40 columns either side of {@code i}: {@code x, y, z} triples. */
	static int[] slice(RoadLine r, int i) {
		int a = Math.max(0, i - 40);
		int b = Math.min(r.size() - 1, i + 40);
		int[] out = new int[((b - a) / 2 + 1) * 3];
		int k = 0;
		for (int j = a; j <= b; j += 2) {
			out[k++] = r.xs[j];
			out[k++] = r.ys[j];
			out[k++] = r.zs[j];
		}
		return java.util.Arrays.copyOf(out, k);
	}

	/** The cardinal direction the road runs at column {@code i}. */
	private static Direction axisFacing(RoadLine r, int i) {
		int a = Math.max(0, i - 3);
		int b = Math.min(r.size() - 1, i + 3);
		return Direction.getApproximateNearest(r.xs[b] - r.xs[a], 0, r.zs[b] - r.zs[a]);
	}

	// ---------------------------------------------------------------- by the land

	private static void landCandidates(Land land, List<RoadLine> roads, List<int[]> towns, int rx, int rz, List<Candidate> out) {
		TerrainProbe t = land.probe();
		int x0 = (rx << Atlas.REGION_BITS) + EDGE;
		int z0 = (rz << Atlas.REGION_BITS) + EDGE;
		int span = Atlas.REGION_SIZE - 2 * EDGE;
		int sea = t.seaLevel();
		Candidate[] best = new Candidate[PostType.values().length];
		for (int gx = 0; gx * GRID <= span; gx++) {
			for (int gz = 0; gz * GRID <= span; gz++) {
				int x = x0 + gx * GRID;
				int z = z0 + gz * GRID;
				if (t.isWater(x, z)) {
					continue;
				}
				int h = t.groundY(x, z);
				if (h <= sea) {
					continue;
				}
				// a pass: refine round the grid point as Expanse does (±48, every 16)
				if (h >= 100 && saddleish(t, x, z, h)) {
					Candidate c = bestPass(t, roads, x, z, rx, rz);
					keep(best, c);
				}
				if (h >= 145) {
					keep(best, monastery(t, x, z, h, rx, rz));
				}
				String biome = land.biome(x, z);
				Culture culture = land.culture(x, z);
				keep(best, farmstead(t, x, z, h, biome, culture, rx, rz));
				keep(best, nomads(t, x, z, h, culture, rx, rz));
				keep(best, harbour(t, x, z, h, rx, rz));
				keep(best, woodEdge(land, x, z, h, biome, rx, rz));
				keep(best, hermitage(land, roads, towns, x, z, h, biome, rx, rz));
			}
		}
		for (Candidate c : best) {
			if (c != null) {
				out.add(c);
			}
		}
		// a second pass far from the best one (two refuges on a long range)
		Candidate first = best[PostType.HOSPICE.ordinal()];
		if (first != null) {
			Candidate second = null;
			for (int gx = 0; gx * GRID <= span; gx += 2) {
				for (int gz = 0; gz * GRID <= span; gz += 2) {
					int x = x0 + gx * GRID;
					int z = z0 + gz * GRID;
					if (dist(x, z, first.x, first.z) < 460 || t.isWater(x, z) || t.groundY(x, z) < 100 || !saddleish(t, x, z, t.groundY(x, z))) {
						continue;
					}
					Candidate c = bestPass(t, roads, x, z, rx, rz);
					if (c != null && (second == null || c.score > second.score)) {
						second = c;
					}
				}
			}
			if (second != null) {
				out.add(second);
			}
		}
	}

	private static void keep(Candidate[] best, Candidate c) {
		if (c != null && (best[c.type.ordinal()] == null || c.score > best[c.type.ordinal()].score)) {
			best[c.type.ordinal()] = c;
		}
	}

	/** A cheap first look: along one axis the land rises 6+ at ±64 and across it falls 4+ (the refinement decides). */
	private static boolean saddleish(TerrainProbe t, int x, int z, int h) {
		for (int ax = 0; ax < 2; ax++) {
			int a = t.groundY(x + (ax == 0 ? 64 : 0), z + (ax == 0 ? 0 : 64));
			int b = t.groundY(x - (ax == 0 ? 64 : 0), z - (ax == 0 ? 0 : 64));
			if (Math.min(a, b) - h < 6) {
				continue;
			}
			int p = t.groundY(x + (ax == 0 ? 0 : 64), z + (ax == 0 ? 64 : 0));
			int q = t.groundY(x - (ax == 0 ? 0 : 64), z - (ax == 0 ? 64 : 0));
			if (h - Math.max(p, q) >= 4) {
				return true;
			}
		}
		return false;
	}

	/** The best pass score round a point (±48 every 16), with a bonus for a road over it. */
	private static Candidate bestPass(TerrainProbe t, List<RoadLine> roads, int x, int z, int rx, int rz) {
		Candidate best = null;
		for (int dx = -48; dx <= 48; dx += 16) {
			for (int dz = -48; dz <= 48; dz += 16) {
				int px = x + dx;
				int pz = z + dz;
				if (!inside(rx, rz, px, pz, PostType.HOSPICE.radius)) {
					continue;
				}
				int[] axis = new int[1];
				float s = pass(t, px, pz, axis);
				if (s <= 0) {
					continue;
				}
				int[] near = nearestRoad(roads, px, pz, 96);
				double score = s + (near != null ? 30 : 0);
				// the refuge's gate faces down the pass (the low axis), toward the road where there is one
				Direction facing = axis[0] == 0 ? Direction.SOUTH : Direction.EAST;
				if (near != null) {
					facing = Direction.getApproximateNearest(near[0] - px, 0, near[1] - pz);
				}
				if (best == null || score > best.score) {
					best = new Candidate(PostType.HOSPICE, px, pz, score, facing, near == null ? new int[0] : sliceNear(roads, px, pz), 0, 0);
				}
			}
		}
		return best;
	}

	/**
	 * Expanse's pass rule ({@code SitedJigsawStructure.siteScore}, "pass"): y 110 and up, level for 6 round, and along one
	 * axis the land rises 12+ at both ±48 while across it falls 8+ at both. Returns up + down (0 when no pass); {@code axis}
	 * gets the rising axis (0: x rises, so the pass runs along z).
	 */
	public static float pass(TerrainProbe t, int x, int z, int[] axis) {
		float h = t.groundY(x, z);
		if (h < 110 || t.isWater(x, z)) {
			return 0;
		}
		for (int[] o : new int[][]{{6, 0}, {-6, 0}, {0, 6}, {0, -6}}) {
			if (Math.abs(t.groundY(x + o[0], z + o[1]) - h) > 4) {
				return 0;
			}
		}
		float best = 0;
		for (int ax = 0; ax < 2; ax++) {
			float a = t.groundY(x + (ax == 0 ? 48 : 0), z + (ax == 0 ? 0 : 48));
			float b = t.groundY(x - (ax == 0 ? 48 : 0), z - (ax == 0 ? 0 : 48));
			float p = t.groundY(x + (ax == 0 ? 0 : 48), z + (ax == 0 ? 48 : 0));
			float q = t.groundY(x - (ax == 0 ? 0 : 48), z - (ax == 0 ? 48 : 0));
			float up = Math.min(a, b) - h;
			float down = h - Math.max(p, q);
			if (up >= 12 && down >= 8 && up + down > best) {
				best = up + down;
				axis[0] = ax;
			}
		}
		return best;
	}

	/** The nearest road column within {@code within} ({@code x, z}), or null. */
	private static int[] nearestRoad(List<RoadLine> roads, int x, int z, int within) {
		int[] best = null;
		long bd = (long) within * within;
		for (RoadLine r : roads) {
			for (int i = 0; i < r.size(); i += 3) {
				long dx = r.xs[i] - x;
				long dz = r.zs[i] - z;
				long d = dx * dx + dz * dz;
				if (d < bd) {
					bd = d;
					best = new int[]{r.xs[i], r.zs[i]};
				}
			}
		}
		return best;
	}

	private static int[] sliceNear(List<RoadLine> roads, int x, int z) {
		RoadLine br = null;
		int bi = -1;
		long bd = Long.MAX_VALUE;
		for (RoadLine r : roads) {
			for (int i = 0; i < r.size(); i++) {
				long dx = r.xs[i] - x;
				long dz = r.zs[i] - z;
				long d = dx * dx + dz * dz;
				if (d < bd) {
					bd = d;
					br = r;
					bi = i;
				}
			}
		}
		return br == null ? new int[0] : slice(br, bi);
	}

	/** Distance to the nearest road column (sampled), or a large number. */
	private static double roadDistance(List<RoadLine> roads, int x, int z) {
		double best = 1e9;
		for (RoadLine r : roads) {
			for (int i = 0; i < r.size(); i += 4) {
				best = Math.min(best, dist(r.xs[i], r.zs[i], x, z));
			}
		}
		return best;
	}

	/**
	 * Expanse's knoll and summit rules: nothing higher within 16, and standing over the ring round it (a summit 25+ at 48,
	 * a knoll 12+ at 56), with nothing on the ring more than 2 over it. y 150 and up for a monastery.
	 */
	private static Candidate monastery(TerrainProbe t, int x, int z, int h, int rx, int rz) {
		if (!inside(rx, rz, x, z, PostType.MONASTERY.radius)) {
			return null;
		}
		float prom = prominence(t, x, z, h);
		if (prom <= 0) {
			return null;
		}
		// the gate faces the gentlest way down (where the path comes up)
		Direction facing = gentlest(t, x, z, h, 40);
		return new Candidate(PostType.MONASTERY, x, z, prom, facing, new int[0], 0, 0);
	}

	/** A knoll's or summit's prominence over its ring (0 if it is neither). */
	public static float prominence(TerrainProbe t, int x, int z, int h) {
		for (int[] o : new int[][]{{16, 0}, {-16, 0}, {0, 16}, {0, -16}}) {
			if (t.groundY(x + o[0], z + o[1]) > h + 1) {
				return 0;
			}
		}
		float best = 0;
		for (int r : new int[]{48, 56}) {
			float sum = 0;
			float max = -Float.MAX_VALUE;
			for (int k = 0; k < 8; k++) {
				double a = k * Math.PI / 4;
				float g = t.groundY(x + (int) Math.round(r * Math.cos(a)), z + (int) Math.round(r * Math.sin(a)));
				sum += g;
				max = Math.max(max, g);
			}
			float prom = h - sum / 8;
			if (max <= h + 2 && prom >= (r == 48 ? 25 : 12)) {
				best = Math.max(best, prom);
			}
		}
		return best;
	}

	/** The cardinal direction the land falls least steeply toward from (x, z), at {@code r}. */
	private static Direction gentlest(TerrainProbe t, int x, int z, int h, int r) {
		Direction best = Direction.SOUTH;
		int bd = Integer.MAX_VALUE;
		for (Direction d : new Direction[]{Direction.SOUTH, Direction.EAST, Direction.NORTH, Direction.WEST}) {
			int g = t.groundY(x + d.getStepX() * r, z + d.getStepZ() * r);
			int drop = Math.abs(h - g) + (t.isWater(x + d.getStepX() * r, z + d.getStepZ() * r) ? 50 : 0);
			if (drop < bd) {
				bd = drop;
				best = d;
			}
		}
		return best;
	}

	/** How uneven the ground is at {@code r} round (x, z): the largest difference from the middle. */
	private static int unevenness(TerrainProbe t, int x, int z, int h, int r) {
		int m = 0;
		for (int[] o : new int[][]{{r, 0}, {-r, 0}, {0, r}, {0, -r}, {r, r}, {-r, -r}, {r, -r}, {-r, r}}) {
			if (t.isWater(x + o[0], z + o[1])) {
				return 99;
			}
			m = Math.max(m, Math.abs(t.groundY(x + o[0], z + o[1]) - h));
		}
		return m;
	}

	static boolean wooded(String biome) {
		return biome.contains("forest") || biome.contains("taiga") || biome.contains("jungle") || biome.contains("wood") || biome.contains("grove")
			|| biome.contains("redwood") || biome.contains("pine") || biome.contains("birch") || biome.contains("bamboo");
	}

	private static boolean snowy(String biome) {
		return biome.contains("snow") || biome.contains("frozen") || biome.contains("ice") || biome.contains("peaks");
	}

	/** Upland pasture, moor or alpine meadow: a herder family's farm on open, gentle ground. */
	private static Candidate farmstead(TerrainProbe t, int x, int z, int h, String biome, Culture c, int rx, int rz) {
		boolean upland = c == Culture.MOORLAND || c == Culture.ALPINE || h >= t.seaLevel() + 40 && !wooded(biome);
		if (!upland || wooded(biome) || snowy(biome) || !inside(rx, rz, x, z, PostType.FARMSTEAD.radius)) {
			return null;
		}
		int un = unevenness(t, x, z, h, 20);
		if (un > 6) {
			return null;
		}
		double score = 20 - un * 3 + (c == Culture.MOORLAND || c == Culture.ALPINE ? 8 : 0);
		return new Candidate(PostType.FARMSTEAD, x, z, score, gentlest(t, x, z, h, 30), new int[0], 0, 0);
	}

	/** Steppe, dunes or savanna, level for two camping grounds 80 apart. */
	private static Candidate nomads(TerrainProbe t, int x, int z, int h, Culture c, int rx, int rz) {
		if (c != Culture.STEPPE && c != Culture.DESERT || !inside(rx, rz, x, z, PostType.NOMAD_CAMP.radius + 40)) {
			return null;
		}
		Candidate best = null;
		for (int ax = 0; ax < 2; ax++) {
			int ox = ax == 0 ? 40 : 0;
			int oz = ax == 0 ? 0 : 40;
			int ha = t.groundY(x - ox, z - oz);
			int hb = t.groundY(x + ox, z + oz);
			int ua = unevenness(t, x - ox, z - oz, ha, 18);
			int ub = unevenness(t, x + ox, z + oz, hb, 18);
			if (ua > 4 || ub > 4) {
				continue;
			}
			double score = 24 - (ua + ub) * 2;
			if (best == null || score > best.score) {
				best = new Candidate(PostType.NOMAD_CAMP, x, z, score, ax == 0 ? Direction.SOUTH : Direction.EAST, new int[0], ox * 2, oz * 2);
			}
		}
		return best;
	}

	/** Dry ground by the sea or a big lake: water within 24 one way, and still water 56 out that way. */
	private static Candidate harbour(TerrainProbe t, int x, int z, int h, int rx, int rz) {
		if (h > t.seaLevel() + 8 || !inside(rx, rz, x, z, PostType.HARBOUR.radius)) {
			return null;
		}
		Direction best = null;
		double score = 0;
		for (Direction d : new Direction[]{Direction.SOUTH, Direction.EAST, Direction.NORTH, Direction.WEST}) {
			int near = -1;
			for (int k = 8; k <= 24; k += 4) {
				if (t.isWater(x + d.getStepX() * k, z + d.getStepZ() * k)) {
					near = k;
					break;
				}
			}
			if (near < 0) {
				continue;
			}
			// big water: wet far out and to either side
			int far = near + 32;
			Direction side = d.getClockWise();
			if (!t.isWater(x + d.getStepX() * far, z + d.getStepZ() * far)
				|| !t.isWater(x + d.getStepX() * far + side.getStepX() * 24, z + d.getStepZ() * far + side.getStepZ() * 24)
				|| !t.isWater(x + d.getStepX() * far - side.getStepX() * 24, z + d.getStepZ() * far - side.getStepZ() * 24)) {
				continue;
			}
			// room on land behind for the cottages
			int back = unevenness(t, x - d.getStepX() * 12, z - d.getStepZ() * 12, h, 10);
			if (back > 5) {
				continue;
			}
			double s = 20 - near / 4.0 - back * 2;
			if (s > score) {
				score = s;
				best = d;
			}
		}
		return best == null ? null : new Candidate(PostType.HARBOUR, x, z, score, best, new int[0], 0, 0);
	}

	/** Where a wood gives onto open land: open ground here, wood on one side and open land on the other. */
	private static Candidate woodEdge(Land land, int x, int z, int h, String biome, int rx, int rz) {
		TerrainProbe t = land.probe();
		if (biome.isEmpty() || wooded(biome) || !inside(rx, rz, x, z, PostType.HUNTERS_CAMP.radius)) {
			return null;
		}
		Direction wood = null;
		int woods = 0;
		for (Direction d : new Direction[]{Direction.SOUTH, Direction.EAST, Direction.NORTH, Direction.WEST}) {
			if (wooded(land.biome(x + d.getStepX() * 40, z + d.getStepZ() * 40))) {
				woods++;
				wood = d;
			}
		}
		if (woods == 0 || woods == 4 || unevenness(t, x, z, h, 12) > 5) {
			return null;
		}
		// the lodge's back to the trees, its front to the open land
		return new Candidate(PostType.HUNTERS_CAMP, x, z, 10 + woods, wood.getOpposite(), new int[0], 0, 0);
	}

	/** Remote ground: deep wood or broken, rocky land, 600 from roads and towns. */
	private static Candidate hermitage(Land land, List<RoadLine> roads, List<int[]> towns, int x, int z, int h, String biome, int rx, int rz) {
		TerrainProbe t = land.probe();
		if (!inside(rx, rz, x, z, PostType.HERMITAGE.radius)) {
			return null;
		}
		boolean wood = wooded(biome);
		int un = unevenness(t, x, z, h, 16);
		boolean crag = un >= 8 && un < 99 && unevenness(t, x, z, h, 4) <= 2;
		if (!wood && !crag) {
			return null;
		}
		if (roadDistance(roads, x, z) < 600) {
			return null;
		}
		double townDist = 1e9;
		for (int[] tn : towns) {
			townDist = Math.min(townDist, dist(tn[0], tn[1], x, z) - tn[2]);
		}
		if (townDist < 600) {
			return null;
		}
		double score = (crag ? 10 : 6) + Math.min(10, townDist / 200);
		return new Candidate(PostType.HERMITAGE, x, z, score, gentlest(t, x, z, h, 12), new int[0], 0, 0);
	}
}
