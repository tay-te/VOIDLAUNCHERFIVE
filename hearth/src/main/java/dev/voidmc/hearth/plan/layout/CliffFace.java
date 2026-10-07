package dev.voidmc.hearth.plan.layout;

import dev.voidmc.hearth.api.PlotSpec;
import dev.voidmc.hearth.plan.PlanContext;
import dev.voidmc.hearth.plan.catalogue.Archetypes;
import dev.voidmc.hearth.plan.grid.Cell;
import dev.voidmc.hearth.plan.model.LayoutType;
import dev.voidmc.hearth.plan.model.Reservation;
import dev.voidmc.hearth.plan.model.RoadEdge;
import dev.voidmc.hearth.plan.model.RoadGraph;
import dev.voidmc.hearth.plan.model.RoadKind;
import dev.voidmc.hearth.plan.model.Site;
import dev.voidmc.hearth.plan.road.Polyline;
import dev.voidmc.hearth.plan.road.Roads;
import dev.voidmc.hearth.plan.road.Specs;
import dev.voidmc.hearth.plan.terrain.Ground;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import org.jspecify.annotations.Nullable;

/**
 * The cliff quarter of a cliff town (ART_REVIEW.md item 29): the face behind the square is the town's, not a bare wall
 * above it. Terraces are cut into the face course by course, a cliff dwelling on each, every course set back a
 * dwelling's height behind the one below so the face climbs at one block in one, a staircase of flat roofs; the front
 * of each roof is the open terrace of the course above, its back carries that course's forecourt. Stair streets climb
 * the face beside the columns of dwellings at the same one in one, so each course's terrace meets a flight at its own
 * level, and the terraces of a row of columns run on as one walkway along the face. At the head of the main flight, on
 * the crest, a lookout.
 *
 * <p>Everything is laid out in the face's frame, from the heart: {@code u} runs out from the rock toward the plain
 * ({@link #out}), {@code t} along the face (its clockwise turn, the dwellings' own right hand). The main street runs
 * along the foot at {@code u = 0} through the square; the base course fronts it at {@code u = -2} (the square's own
 * column, behind the square, at {@code -h - 1}), its forecourt on the flat at the foot, its rooms dug into the toe.
 *
 * <pre>
 *   t   -52 ......... -32 -30 -28 ........ -8 -6 -4 .. 4  6  8 ........ 28 30 32 ......... 52
 *       [ 3 columns     ] |  [ 3 columns     ] |  [sq.]  |  [ 3 columns    ] |  [ 3 columns     ]
 *                       climb               climb      climb              climb
 * </pre>
 *
 * The inner group (the square's column and the three either side) climbs first, then the outer. The layout is a pure
 * function of the natural ground, the heart and the site's cliff direction, so every planning call finds the same
 * quarter; the founding reserves its ground ({@code cliff_face/*}) so ordinary plots keep off the face, and plans the
 * climbs as stair streets with explicit treads (the face is too steep for a profile graded from the land).
 */
public final class CliffFace {
	/** Prefix of the quarter's reservations. */
	public static final String TAG = "cliff_face/";
	/** The lookout's ground at the head of the main climb. */
	public static final String TOP = TAG + "top";
	/** Depth of a face dwelling (the archetype's deepest: room for a forecourt over the roof below and rooms behind). */
	public static final int DEPTH = 9;
	/** Most courses a face carries (36 blocks of dwellings); a higher face keeps its bare rock above. */
	public static final int MAX_COURSES = 6;
	/** Width of a climb. */
	public static final int CLIMB_WIDTH = 3;
	/** The climbs' centre lines along the face; the first two flank the square and are built at the founding. */
	static final int[] CLIMBS = {6, -6, 30, -30};
	/** Columns as {first t, frontage, group}; the square's own column first. */
	static final int[][] COLUMNS = {
		{-4, 9, 0}, {8, 7, 0}, {-14, 7, 0}, {15, 7, 0}, {-21, 7, 0}, {22, 7, 0}, {-28, 7, 0},
		{32, 7, 1}, {-38, 7, 1}, {39, 7, 1}, {-45, 7, 1}, {46, 7, 1}, {-52, 7, 1}};

	private CliffFace() {
	}

	/** {@link #neighbours} for a side that meets a climb. */
	public static final int CLIMB = -2;

	public static int columns() {
		return COLUMNS.length;
	}

	public static int group(int c) {
		return COLUMNS[c][2];
	}

	public static int columnStart(int c) {
		return COLUMNS[c][0];
	}

	public static int columnFrontage(int c) {
		return COLUMNS[c][1];
	}

	/**
	 * What stands either side of a column: {-t side, +t side}, each another column's index, {@link #CLIMB} for a climb
	 * whose landings meet its terraces, or -1. The square's column meets no landings (it stands further back).
	 */
	public static int[] neighbours(int c) {
		int[] out = {-1, -1};
		int[] edge = {COLUMNS[c][0] - 1, COLUMNS[c][0] + COLUMNS[c][1]};
		for (int side = 0; side < 2; side++) {
			int t = edge[side];
			for (int o = 0; o < COLUMNS.length; o++) {
				if (o != c && t >= COLUMNS[o][0] && t < COLUMNS[o][0] + COLUMNS[o][1]) {
					out[side] = o;
				}
			}
			for (int climb : CLIMBS) {
				if (c != 0 && Math.abs(t - climb) <= CLIMB_WIDTH / 2) {
					out[side] = CLIMB;
				}
			}
		}
		return out;
	}

	/** The centre line of the climb beside a column on a side (0: -t, 1: +t). */
	public static int climbBeside(int c, int side) {
		return side == 0 ? COLUMNS[c][0] - 1 - CLIMB_WIDTH / 2 : COLUMNS[c][0] + COLUMNS[c][1] + CLIMB_WIDTH / 2;
	}

	/**
	 * The face as the planner lays it out: the heart, which way is out, the base course's level (the square's), the rise
	 * of a course and how many courses the face carries.
	 */
	public record Face(int hx, int hz, int y0, Direction out, int course, int courses, int plazaHalf) {
		public Direction along() {
			return this.out.getClockWise();
		}

		public int x(int t, int u) {
			return this.hx + this.along().getStepX() * t + this.out.getStepX() * u;
		}

		public int z(int t, int u) {
			return this.hz + this.along().getStepZ() * t + this.out.getStepZ() * u;
		}

		public int t(int x, int z) {
			return (x - this.hx) * this.along().getStepX() + (z - this.hz) * this.along().getStepZ();
		}

		public int u(int x, int z) {
			return (x - this.hx) * this.out.getStepX() + (z - this.hz) * this.out.getStepZ();
		}

		/** The world rectangle {x0, z0, x1, z1} of a span of the face's frame. */
		public int[] rect(int t0, int t1, int u0, int u1) {
			int ax = x(t0, u0);
			int az = z(t0, u0);
			int bx = x(t1, u1);
			int bz = z(t1, u1);
			return new int[]{Math.min(ax, bx), Math.min(az, bz), Math.max(ax, bx), Math.max(az, bz)};
		}

		/** How far each course stands behind the one below: a course's height, keeping two rows of it on the roof below. */
		public int set() {
			return Math.max(2, Math.min(this.course, DEPTH - 3));
		}

		/** The front row ({@code u}) of a column's base course: behind the square for its own column, at the street otherwise. */
		public int front(int column) {
			return column == 0 ? -this.plazaHalf - 1 : -2;
		}

		/** The deepest row a column reaches with every course stacked. */
		public int back(int column) {
			return front(column) - (this.courses - 1) * set() - (DEPTH - 1);
		}
	}

	/** The direction from the rock out over the plain, from the site's cliff angle (the downhill way across the face). */
	static Direction outward(Site site) {
		double a = site.cliffAngle();
		double dx = StrictMath.cos(a);
		double dz = StrictMath.sin(a);
		if (Math.abs(dx) >= Math.abs(dz)) {
			return dx >= 0 ? Direction.EAST : Direction.WEST;
		}
		return dz >= 0 ? Direction.SOUTH : Direction.NORTH;
	}

	/**
	 * Where a cliff town's heart goes so its quarter fits: on the flat at the foot, four rows out from the toe of the
	 * face (the square's back edge on the toe, the main street at {@code u = 0}). Null when the land near the site's
	 * heart has no face worth terracing (two courses at least).
	 */
	public static int @Nullable [] heart(Ground g, Site site, int plazaHalf) {
		Direction out = outward(site);
		int ox = out.getStepX();
		int oz = out.getStepZ();
		int sx = site.heartX();
		int sz = site.heartZ();
		// the face: the steepest drop along the line out through the site's heart
		int faceK = 0;
		int steepest = 0;
		for (int k = -48; k <= 48; k++) {
			int drop = g.ground(sx + ox * (k - 2), sz + oz * (k - 2)) - g.ground(sx + ox * (k + 2), sz + oz * (k + 2));
			if (drop > steepest) {
				steepest = drop;
				faceK = k;
			}
		}
		if (steepest < 8) {
			return null;
		}
		int foot = Integer.MAX_VALUE;
		int crest = Integer.MIN_VALUE;
		for (int k = 0; k <= 20; k++) {
			foot = Math.min(foot, g.ground(sx + ox * (faceK + k), sz + oz * (faceK + k)));
			crest = Math.max(crest, g.ground(sx + ox * (faceK - k), sz + oz * (faceK - k)));
		}
		if (crest - foot < 12) {
			return null;
		}
		// the toe along a stretch of the face: where the land, walking out from the rock, comes down to the foot and stays
		// there; the square takes the toe of the stretch round the heart that stands furthest out, so it is on the flat
		Direction along = out.getClockWise();
		int[] toes = new int[9];
		int n = 0;
		for (int t = -8; t <= 8; t += 2) {
			int bx = sx + along.getStepX() * t;
			int bz = sz + along.getStepZ() * t;
			int toe = toe(g, bx, bz, ox, oz, faceK - 16, faceK + 30, foot);
			if (toe != Integer.MIN_VALUE) {
				toes[n++] = toe;
			}
		}
		if (n < 5) {
			return null;
		}
		int[] sorted = Arrays.copyOf(toes, n);
		Arrays.sort(sorted);
		int toe = sorted[n - 2];
		int k = toe + plazaHalf;
		int hx = sx + ox * k;
		int hz = sz + oz * k;
		if (g.isWater(hx, hz)) {
			return null;
		}
		return new int[]{hx, hz};
	}

	/** The first step out from {@code k0} where the ground is down to the foot and stays down two more, or MIN_VALUE. */
	private static int toe(Ground g, int bx, int bz, int ox, int oz, int k0, int k1, int foot) {
		for (int k = k0; k <= k1; k++) {
			boolean down = true;
			for (int j = 0; j < 3 && down; j++) {
				down = g.ground(bx + ox * (k + j), bz + oz * (k + j)) <= foot + 1 && !g.isWater(bx + ox * (k + j), bz + oz * (k + j));
			}
			if (down) {
				return k;
			}
		}
		return Integer.MIN_VALUE;
	}

	/**
	 * The quarter of a founded cliff town, or null (another layout, no face behind the square, or a town founded before
	 * the quarter existed). Pure: read from the heart, the site's cliff direction and the natural ground.
	 */
	public static @Nullable Face of(PlanContext ctx) {
		var state = ctx.state;
		if (state.layout != LayoutType.CLIFF || state.site == null || state.reservation(TAG + "0") == null || course(ctx) <= 0) {
			return null;
		}
		return survey(ctx.ground, state.site, state.heartX, state.heartY, state.heartZ, state.plazaHalf, course(ctx));
	}

	/** A course's rise: the height of a cliff dwelling, whose roof deck the course above stands on (0: no such house). */
	static int course(PlanContext ctx) {
		var info = ctx.catalogue.info(Archetypes.CLIFF_DWELLING);
		return info == null ? 0 : info.maxHeight();
	}

	/** The face from a heart: how many courses of {@code course} blocks fit between the square's level and the crest. */
	static @Nullable Face survey(Ground g, Site site, int hx, int hy, int hz, int plazaHalf, int course) {
		Direction out = outward(site);
		Face probe = new Face(hx, hz, hy, out, course, 1, plazaHalf);
		// the toe must be where the founding put it: the square's back edge on it, the face rising behind
		int rise = g.ground(probe.x(0, -plazaHalf - 4), probe.z(0, -plazaHalf - 4)) - hy;
		if (rise < 3) {
			return null;
		}
		// the crest: the highest ground over the stretch the inner group stands on, as deep as the courses could reach
		int crest = Integer.MIN_VALUE;
		for (int t = -24; t <= 24; t += 4) {
			for (int u = -2; u >= -2 - MAX_COURSES * course - DEPTH; u--) {
				crest = Math.max(crest, g.ground(probe.x(t, u), probe.z(t, u)));
			}
		}
		int courses = Math.min(MAX_COURSES, (crest - hy + course / 2) / course);
		if (courses < 2) {
			return null;
		}
		return new Face(hx, hz, hy, out, course, courses, plazaHalf);
	}

	/**
	 * Lays the quarter out at the founding, after the main street along the foot: reserves the columns' ground and the
	 * lookout's, and plans and builds the four climbs. Returns the main climb (the one whose
	 * head the lookout stands at), or null if the land has no quarter after all (the caller falls back to the plain
	 * cliff skeleton).
	 */
	static @Nullable RoadEdge found(PlanContext ctx, int heart) {
		var state = ctx.state;
		if (course(ctx) <= 0) {
			return null;
		}
		Face f = survey(ctx.ground, state.site, state.heartX, state.heartY, state.heartZ, state.plazaHalf, course(ctx));
		if (f == null) {
			return null;
		}
		// the columns' ground, group by group, from the street back to the deepest course
		List<Reservation> held = new ArrayList<>();
		for (int g = 0; g < 2; g++) {
			for (int side : new int[]{1, -1}) {
				int t0 = Integer.MAX_VALUE;
				int t1 = Integer.MIN_VALUE;
				for (int c = 1; c < COLUMNS.length; c++) {
					if (COLUMNS[c][2] == g && Integer.signum(COLUMNS[c][0]) == side) {
						t0 = Math.min(t0, COLUMNS[c][0]);
						t1 = Math.max(t1, COLUMNS[c][0] + COLUMNS[c][1] - 1);
					}
				}
				int[] r = f.rect(t0, t1, f.back(1), f.front(1));
				held.add(new Reservation(TAG + (1 + g * 2 + (side > 0 ? 0 : 1)), r[0], r[1], r[2], r[3], Cell.RESERVED));
			}
		}
		int[] sq = f.rect(COLUMNS[0][0], COLUMNS[0][0] + COLUMNS[0][1] - 1, f.back(0), f.front(0));
		held.add(0, new Reservation(TAG + "0", sq[0], sq[1], sq[2], sq[3], Cell.RESERVED));
		for (Reservation r : held) {
			if (!vacant(ctx, r, false)) {
				return null;
			}
		}
		for (Reservation r : held) {
			state.reservations.add(r);
			ctx.occ.markRect(r.x0(), r.z0(), r.x1(), r.z1(), r.code());
		}
		RoadEdge main = null;
		for (int i = 0; i < CLIMBS.length; i++) {
			// the street along the foot built on as far as the climb, so no flight starts off a street not yet laid
			int[] at = junction(ctx, f, CLIMBS[i]);
			if (at != null && ctx.state.graph.edge(at[0]).built <= at[1] + 2) {
				Roads.build(ctx, ctx.state.graph.edge(at[0]), at[1] + 3, "road: the street along the foot of the cliff");
			}
			RoadEdge e = climb(ctx, f, CLIMBS[i]);
			if (e == null) {
				continue;
			}
			// every climb is built at once, as the face's own (left to the town's road growth, the outer pair would be
			// laid as ordinary stair streets, walled up to the old crest)
			issue(ctx, f, e);
			if (i < 2) {
				if (main == null || e.line().points()[e.line().points().length - 2] > main.line().points()[main.line().points().length - 2]) {
					main = e;
				}
			}
		}
		if (main != null) {
			// the lookout's ground on the crest at the head of the main climb, in line with the flight and facing the town,
			// its door at the head of the flight (the road's end); it takes precedence over the top course of a column it
			// overlaps
			int[] p = main.line().points();
			int ex = p[p.length - 3];
			int ez = p[p.length - 1];
			int te = f.t(ex, ez);
			int ue = f.u(ex, ez);
			int[] r = f.rect(te - 3, te + 3, ue - 8, ue - 2);
			Reservation top = new Reservation(TOP, r[0], r[1], r[2], r[3], Cell.RESERVED);
			if (vacant(ctx, top, true)) {
				state.reservations.add(top);
				ctx.occ.markRect(r[0], r[1], r[2], r[3], Cell.RESERVED);
			}
		}
		return main;
	}

	/** Whether a rectangle is free for the quarter ({@code overFace}: the quarter's own columns count as free). */
	private static boolean vacant(PlanContext ctx, Reservation r, boolean overFace) {
		for (int z = r.z0(); z <= r.z1(); z++) {
			for (int x = r.x0(); x <= r.x1(); x++) {
				byte c = ctx.occ.get(x, z);
				boolean face = overFace && c == Cell.RESERVED && columnGround(ctx, x, z);
				if (!(Cell.isBuildable(c) || c == Cell.PLANNED_ROAD || face) || ctx.ground.isWater(x, z)) {
					return false;
				}
			}
		}
		return true;
	}

	/** Whether a column is held by the quarter's columns and nothing else. */
	private static boolean columnGround(PlanContext ctx, int x, int z) {
		boolean face = false;
		for (Reservation r : ctx.state.reservations) {
			if (r.contains(x, z)) {
				if (!r.tag().startsWith(TAG)) {
					return false;
				}
				face = true;
			}
		}
		return face;
	}

	/**
	 * Plans a climb at {@code t = c}: off the main street at the foot (a junction on it), straight into the face at one
	 * block in one, up to where its treads come out on the crest (or the courses end). The treads are the planner's
	 * own, not graded from the land: course {@code k}'s terrace front, at {@code u = -2 - k·set}, is met at its level.
	 */
	static @Nullable RoadEdge climb(PlanContext ctx, Face f, int c) {
		int[] at = junction(ctx, f, c);
		if (at == null) {
			return null;
		}
		RoadEdge arm = ctx.state.graph.edge(at[0]);
		int y0 = arm.dense().ys()[at[1]];
		Ground g = ctx.ground;
		List<int[]> pts = new ArrayList<>();
		int top = y0 + f.courses() * f.course() + 2;
		for (int u = 0; u >= -2 - f.courses() * f.set() - 6; u--) {
			int y = u >= -2 ? y0 : Math.min(top, y0 + (-2 - u) * f.course() / f.set());
			int x = f.x(c, u);
			int z = f.z(c, u);
			if (g.isWater(x, z)) {
				break;
			}
			if (!pts.isEmpty()) {
				// never more than a block up per block forward (a course climbs as far as it steps back)
				y = Math.min(y, pts.get(pts.size() - 1)[1] + 1);
			}
			pts.add(new int[]{x, y, z});
			// out on the crest: the treads have reached the land above the courses; two more on the level and it is done
			if (y >= y0 + (f.courses() - 1) * f.course() && g.ground(x, z) <= y) {
				for (int k = 1; k <= 2; k++) {
					pts.add(new int[]{f.x(c, u - k), y, f.z(c, u - k)});
				}
				break;
			}
		}
		if (pts.size() < 10) {
			return null;
		}
		for (int[] p : pts) {
			byte cell = ctx.occ.get(p[0], p[2]);
			if (Cell.isSolid(cell) && cell != Cell.RESERVED || cell == Cell.BUILDING) {
				return null;
			}
		}
		int n = pts.size();
		int[] xs = new int[n];
		int[] ys = new int[n];
		int[] zs = new int[n];
		for (int i = 0; i < n; i++) {
			xs[i] = pts.get(i)[0];
			ys[i] = pts.get(i)[1];
			zs[i] = pts.get(i)[2];
		}
		int from = Roads.junction(ctx, arm, at[1]);
		RoadGraph g2 = ctx.state.graph;
		RoadGraph.Node end = g2.addNode(xs[n - 1], ys[n - 1], zs[n - 1], RoadGraph.NodeKind.END);
		Polyline line = Polyline.fromDense(xs, ys, zs, 0, n - 1);
		RoadEdge e = g2.addEdge(from, end.id(), RoadEdge.Role.STAIR, RoadKind.LANE, CLIMB_WIDTH, line, new int[]{0, RoadEdge.STAIRS, 0});
		Roads.reserve(ctx, e);
		return e;
	}

	/** The main street's edge and dense index where it passes {@code (t = c, u = 0)}, or null. */
	private static int @Nullable [] junction(PlanContext ctx, Face f, int c) {
		int x = f.x(c, 0);
		int z = f.z(c, 0);
		for (RoadEdge e : ctx.state.graph.edges()) {
			if (e.role != RoadEdge.Role.ARM) {
				continue;
			}
			Polyline.Dense d = e.dense();
			for (int i = 0; i < d.length(); i++) {
				if (d.xs()[i] == x && d.zs()[i] == z) {
					return new int[]{e.id, i};
				}
			}
		}
		return null;
	}

	/** The climb at {@code t = c}, if planned. */
	public static @Nullable RoadEdge climbAt(PlanContext ctx, Face f, int c) {
		int x = f.x(c, -3);
		int z = f.z(c, -3);
		for (RoadEdge e : ctx.state.graph.edges()) {
			if (e.role != RoadEdge.Role.STAIR || e.width != CLIMB_WIDTH) {
				continue;
			}
			Polyline.Dense d = e.dense();
			for (int i = 0; i < Math.min(d.length(), 6); i++) {
				if (d.xs()[i] == x && d.zs()[i] == z) {
					return e;
				}
			}
		}
		return null;
	}

	/**
	 * Builds a climb whole, as stair plots that know the face: their side walls rise no more than a parapet over the
	 * treads (the courses beside are cut later, and a wall up to the old crest would stand above their roofs as a fin),
	 * and they leave gaps where each course's terrace meets the flight.
	 */
	public static void issue(PlanContext ctx, Face f, RoadEdge e) {
		if (e.built >= e.length()) {
			return;
		}
		Polyline.Dense d = e.dense();
		int c = f.t(d.xs()[0], d.zs()[0]);
		List<Integer> gaps = new ArrayList<>();
		for (int k = 1; k < f.courses(); k++) {
			for (int r = 0; r < 2; r++) {
				int u = -2 - k * f.set() - r;
				for (int side : new int[]{-1, 1}) {
					if (Math.abs(c) < 10 && Integer.signum(c) != side) {
						// beside the square's own column, set further back: its terraces do not meet the flight
						continue;
					}
					int tt = c + side * (CLIMB_WIDTH / 2);
					gaps.add(f.x(tt, u));
					gaps.add(f.y0() + k * f.course());
					gaps.add(f.z(tt, u));
				}
			}
		}
		int maxLen = Math.max(8, ctx.config.roadPlotLength());
		for (int a = 0; a < d.length() - 1; a += maxLen) {
			int b = Math.min(d.length() - 1, a + maxLen);
			int[] pts = Polyline.fromDense(d.xs(), d.ys(), d.zs(), a, b).points();
			PlotSpec s = Specs.road(ctx, Archetypes.STAIRS, pts, e.width, e.kind);
			CompoundTag params = s.params();
			params.putInt("cap", 2);
			params.putIntArray("gaps", gaps.stream().mapToInt(Integer::intValue).toArray());
			params.putBoolean("climb", true);
			Roads.issue(ctx, new PlotSpec(s.archetype(), s.style(), s.seed(), s.origin(), s.sizeX(), s.sizeZ(), s.facing(), s.maxHeight(), s.tier(),
				params), "road: a stair street up the cliff face, past each course of dwellings");
		}
		e.built = e.length();
	}
}
