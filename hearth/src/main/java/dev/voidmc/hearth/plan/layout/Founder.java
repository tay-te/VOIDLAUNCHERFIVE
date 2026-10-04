package dev.voidmc.hearth.plan.layout;

import dev.voidmc.hearth.api.ArchetypeInfo;
import dev.voidmc.hearth.api.PlotSpec;
import dev.voidmc.hearth.plan.PlanContext;
import dev.voidmc.hearth.plan.catalogue.Archetypes;
import dev.voidmc.hearth.plan.grid.Cell;
import dev.voidmc.hearth.plan.growth.Build;
import dev.voidmc.hearth.plan.growth.Growth;
import dev.voidmc.hearth.plan.model.LayoutType;
import dev.voidmc.hearth.plan.model.QueuedProject;
import dev.voidmc.hearth.plan.model.Reservation;
import dev.voidmc.hearth.plan.model.RoadEdge;
import dev.voidmc.hearth.plan.model.RoadGraph;
import dev.voidmc.hearth.plan.model.RoadKind;
import dev.voidmc.hearth.plan.model.Site;
import dev.voidmc.hearth.plan.road.Polyline;
import dev.voidmc.hearth.plan.road.Roads;
import dev.voidmc.hearth.plan.road.Router;
import dev.voidmc.hearth.plan.terrain.Ground;
import dev.voidmc.hearth.plan.terrain.SiteSurvey;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import org.jspecify.annotations.Nullable;

/**
 * Lays a settlement out at its founding (PLANNING.md, "Layout types"): surveys the site, chooses the layout and
 * signature, holds the square open at the heart with its well, and plans the skeleton every later street grows from —
 * the main roads out of the square routed over the land, a ring or two, the stair street up the slope, the
 * switchbacks up the mountain, the boardwalk out over the water, the tracks into the farmland. Only the first
 * stretches are built at once; the rest is reserved, and built as the town grows along it. Then the first needs are
 * planned (the builders' yard, the first homes, a field) and handed back in build order.
 */
public final class Founder {
	private Founder() {
	}

	public static List<PlotSpec> found(PlanContext ctx, BlockPos center, @Nullable ResourceKey<Biome> biome) {
		var state = ctx.state;
		Ground g = ctx.ground;
		Site site = SiteSurvey.survey(g, center.getX(), center.getZ());
		LayoutChooser.Choice choice = LayoutChooser.choose(ctx.culture, site, biome, ctx.random);
		state.layout = choice.layout();
		state.signature = choice.signature();
		state.site = site;
		int hx = site.heartX();
		int hz = site.heartZ();
		if (state.layout == LayoutType.CLIFF && site.cliff()) {
			int[] foot = cliffFoot(g, hx, hz, site);
			hx = foot[0];
			hz = foot[1];
		}
		// a square needs ground it can stand on: off a cliff face or a crag, to the nearest easier ground
		if (g.slope(hx, hz, 3) > 0.8 || g.range(hx - 6, hz - 6, hx + 6, hz + 6, 2)[1] - g.range(hx - 6, hz - 6, hx + 6, hz + 6, 2)[0] > 10) {
			int[] easy = easierGround(g, hx, hz);
			hx = easy[0];
			hz = easy[1];
		}
		state.heartX = hx;
		state.heartZ = hz;
		state.heartY = g.surface(hx, hz);
		// the square stays on dry land: smaller where the water comes close
		int half = state.layout.plazaHalf;
		while (half > 3 && wet(g, hx, hz, half)) {
			half--;
		}
		state.plazaHalf = half;
		state.founded = true;
		// the square, held open from the start: a green round the well, a paved plaza later
		int h = state.plazaHalf;
		state.reservations.add(new Reservation("square", hx - h, hz - h, hx + h, hz + h, Cell.PLAZA));
		ctx.occ.markRect(hx - h, hz - h, hx + h, hz + h, Cell.PLAZA);
		RoadGraph.Node heart = state.graph.addNode(hx, state.heartY, hz, RoadGraph.NodeKind.HEART);
		squareEdge(ctx, heart.id());

		skeleton(ctx, heart.id());
		// the lots on the square first (the hall, the market hall, the court), then the side lanes of the first stretches,
		// planned ahead so the first houses keep their mouths open and the lanes go round the lots
		dev.voidmc.hearth.plan.growth.Lots.reserve(ctx);
		for (RoadEdge e : new ArrayList<>(state.graph.edges())) {
			if (e.built > 0) {
				dev.voidmc.hearth.plan.growth.Grid.planAhead(ctx, e, 0, e.built);
			}
		}
		heartFeature(ctx);
		// the first needs: the builders' yard, homes for whoever founded it, a field
		int rounds = 3 + ctx.population / 3;
		for (int i = 0; i < rounds; i++) {
			if (!Growth.step(ctx)) {
				break;
			}
		}
		List<PlotSpec> out = new ArrayList<>();
		for (QueuedProject q : state.queue) {
			out.add(q.spec());
		}
		state.queue.clear();
		return out;
	}

	private static boolean wet(Ground g, int hx, int hz, int half) {
		for (int z = hz - half; z <= hz + half; z++) {
			for (int x = hx - half; x <= hx + half; x++) {
				if (g.isWater(x, z)) {
					return true;
				}
			}
		}
		return false;
	}

	/** The square's edge as a road-network edge: plots front onto the square along it. Clockwise, one in from the edge. */
	public static void squareEdge(PlanContext ctx, int heart) {
		int h = ctx.state.plazaHalf - 1;
		int hx = ctx.heartX();
		int hz = ctx.heartZ();
		int y = ctx.state.heartY;
		int[] pts = {hx - h, y, hz - h, hx + h, y, hz - h, hx + h, y, hz + h, hx - h, y, hz + h, hx - h, y, hz - h};
		// split each side into runs of at most MAX_RUN so it obeys the polyline rule
		List<Integer> out = new ArrayList<>();
		for (int v = 0; v + 1 < 5; v++) {
			int ax = pts[v * 3];
			int az = pts[v * 3 + 2];
			int bx = pts[v * 3 + 3];
			int bz = pts[v * 3 + 5];
			int n = Math.max(Math.abs(bx - ax), Math.abs(bz - az));
			int steps = Math.max(1, (n + Polyline.MAX_RUN - 1) / Polyline.MAX_RUN);
			for (int s = 0; s < steps; s++) {
				out.add(ax + (bx - ax) * s / steps);
				out.add(y);
				out.add(az + (bz - az) * s / steps);
			}
		}
		out.add(pts[12]);
		out.add(y);
		out.add(pts[14]);
		Polyline line = new Polyline(out.stream().mapToInt(Integer::intValue).toArray());
		RoadEdge e = ctx.state.graph.addEdge(heart, heart, RoadEdge.Role.SQUARE, RoadKind.STREET, 1, line, new int[0]);
		e.built = e.length();
		ctx.state.squareEdge = e.id;
	}

	private static void heartFeature(PlanContext ctx) {
		ArchetypeInfo well = ctx.catalogue.info(Archetypes.WELL);
		if (well == null) {
			well = ctx.catalogue.info(Archetypes.BELL);
		}
		if (well == null) {
			return;
		}
		int s = well.minFrontage();
		int x0 = ctx.heartX() - s / 2;
		int z0 = ctx.heartZ() - s / 2;
		CompoundTag params = new CompoundTag();
		params.putBoolean("heart", true);
		params.putBoolean("on_square", true);
		params.putInt("street_y", ctx.state.heartY);
		PlotSpec spec = new PlotSpec(well.id(), ctx.style, ctx.nextSeed(), new BlockPos(x0, ctx.state.heartY, z0), s, s, Direction.SOUTH,
			well.maxHeight(), Build.finish(ctx, well, 0, null), params);
		Roads.issue(ctx, spec, "heart: the well the settlement gathers round");
	}

	/** Plans the layout's main roads and builds their first stretches. */
	private static void skeleton(PlanContext ctx, int heart) {
		var state = ctx.state;
		Site site = state.site;
		LayoutType layout = state.layout;
		double contour = site.contourAngle();
		// streets square to the compass on easy ground (plots are square to it too); along the contour on a real slope
		double jitter = (ctx.random.nextDouble() - 0.5) * 0.2;
		double base = site.slope() > 0.15 ? contour : snapCardinal(site.slope() > 0.04 ? contour : ctx.random.nextDouble() * Math.PI * 2) + jitter;
		int width = layout.armWidth;
		RoadKind kind = layout.armKind;
		switch (layout) {
			case CROSSROADS, WALLED -> {
				boolean walled = layout == LayoutType.WALLED;
				double len = walled ? 130 : 200;
				// a walled town builds its two main streets right through to where its gates will be
				int first = walled ? 44 : 30;
				arm(ctx, heart, base, len + 40 * ctx.random.nextDouble(), width, kind, RoadEdge.Role.ARM, first);
				arm(ctx, heart, base + Math.PI, len + 40 * ctx.random.nextDouble(), width, kind, RoadEdge.Role.ARM, first);
				boolean tee = layout == LayoutType.CROSSROADS && ctx.random.nextDouble() < 0.25;
				arm(ctx, heart, base + Math.PI / 2, len * 0.8, width, kind, RoadEdge.Role.ARM, walled ? 40 : 18);
				if (!tee) {
					arm(ctx, heart, base - Math.PI / 2, len * 0.8, width, kind, RoadEdge.Role.ARM, walled ? 40 : 18);
				}
				if (layout == LayoutType.WALLED) {
					dev.voidmc.hearth.plan.growth.Walls.reserveLine(ctx, heart);
				}
			}
			case LINEAR -> {
				double along = site.hasWater() && site.waterDistance() < 64 ? site.waterAxis() : base;
				if (Math.abs(along - snapCardinal(along)) < 0.35) {
					along = snapCardinal(along) + jitter;
				}
				arm(ctx, heart, along, 300, width, kind, RoadEdge.Role.ARM, 36);
				arm(ctx, heart, along + Math.PI, 300, width, kind, RoadEdge.Role.ARM, 36);
				if (site.hasWater() && site.waterDistance() < 64) {
					// a short street down to the water
					double down = StrictMath.atan2(site.waterZ() - ctx.heartZ(), site.waterX() - ctx.heartX());
					arm(ctx, heart, down, Math.max(20, site.waterDistance() + 4), 3, RoadKind.LANE, RoadEdge.Role.LANE, 200);
				} else {
					arm(ctx, heart, along + Math.PI / 2, 90, 3, RoadKind.LANE, RoadEdge.Role.ARM, 0);
				}
			}
			case HILLSIDE -> {
				arm(ctx, heart, contour, 190, width, kind, RoadEdge.Role.ARM, 26);
				arm(ctx, heart, contour + Math.PI, 190, width, kind, RoadEdge.Role.ARM, 26);
				stair(ctx, heart, site.uphillAngle(), 90, 30);
				stair(ctx, heart, site.uphillAngle() + Math.PI, 60, 0);
			}
			case MOUNTAIN -> {
				arm(ctx, heart, contour, 130, width, kind, RoadEdge.Role.ARM, 22);
				arm(ctx, heart, contour + Math.PI, 130, width, kind, RoadEdge.Role.ARM, 0);
				switchback(ctx, heart, site);
			}
			case CLIFF -> {
				double along = site.cliff() ? site.cliffAngle() + Math.PI / 2 : contour;
				arm(ctx, heart, along, 160, width, kind, RoadEdge.Role.ARM, 24);
				arm(ctx, heart, along + Math.PI, 160, width, kind, RoadEdge.Role.ARM, 24);
				double up = site.cliff() ? site.cliffAngle() + Math.PI : site.uphillAngle();
				RoadEdge climb = stair(ctx, heart, up, 70, 0);
				if (climb != null) {
					Roads.build(ctx, climb, climb.length(), "road: the stair street up the cliff");
					Polyline.Dense d = climb.dense();
					int top = climb.to;
					arm(ctx, top, along, 110, 3, RoadKind.LANE, RoadEdge.Role.ARM, 0);
					arm(ctx, top, along + Math.PI, 110, 3, RoadKind.LANE, RoadEdge.Role.ARM, 0);
				}
			}
			case STILT -> {
				double along = site.hasWater() ? site.waterAxis() : base;
				arm(ctx, heart, along, 160, 3, RoadKind.LANE, RoadEdge.Role.ARM, 20);
				arm(ctx, heart, along + Math.PI, 160, 3, RoadKind.LANE, RoadEdge.Role.ARM, 20);
				boardwalkFromShore(ctx, heart, site);
			}
			case RING -> {
				int spokes = 4 + ctx.random.nextInt(3);
				for (int i = 0; i < spokes; i++) {
					arm(ctx, heart, base + i * 2 * Math.PI / spokes, 150, width, kind, RoadEdge.Role.ARM, i < 2 ? 22 : 0);
				}
				int r = state.plazaHalf + 15;
				for (int ring = 0; ring < 3; ring++) {
					ring(ctx, heart, r, ring == 0);
					r += 24;
				}
			}
			case FARMSTEADS -> {
				arm(ctx, heart, base, 70, 3, RoadKind.LANE, RoadEdge.Role.ARM, 20);
				arm(ctx, heart, base + Math.PI, 70, 3, RoadKind.LANE, RoadEdge.Role.ARM, 20);
				int tracks = 3 + ctx.random.nextInt(3);
				for (int i = 0; i < tracks; i++) {
					double ang = base + Math.PI / 4 + i * 2 * Math.PI / tracks + (ctx.random.nextDouble() - 0.5) * 0.6;
					arm(ctx, heart, ang, (280 + 140 * ctx.random.nextDouble()) * ctx.config.sprawlBias(), 3, RoadKind.PATH, RoadEdge.Role.TRACK, 0);
				}
			}
		}
	}

	private static double snapCardinal(double a) {
		return Math.round(a / (Math.PI / 2)) * (Math.PI / 2);
	}

	/**
	 * Plans a road from a node toward a direction and distance, routed over the land; builds its first {@code build}
	 * blocks. Starts at the node (at the heart, on the square's edge).
	 */
	public static @Nullable RoadEdge arm(PlanContext ctx, int from, double angle, double length, int width, RoadKind kind, RoadEdge.Role role, int build) {
		RoadGraph.Node n = ctx.state.graph.node(from);
		Ground g = ctx.ground;
		int tx = n.x() + (int) Math.round(StrictMath.cos(angle) * length);
		int tz = n.z() + (int) Math.round(StrictMath.sin(angle) * length);
		// a target on dry land, not in a lake
		for (int k = 0; k < 20 && g.isWater(tx, tz); k++) {
			tx = n.x() + (int) Math.round(StrictMath.cos(angle) * length * (1 - 0.05 * (k + 1)));
			tz = n.z() + (int) Math.round(StrictMath.sin(angle) * length * (1 - 0.05 * (k + 1)));
		}
		boolean stairs = Roads.rules(ctx, role).stairs();
		Router.Params params = new Router.Params(length > 90 ? 4 : 2, 48, ctx.config.roadGrade() * 0.85,
			stairs ? ctx.config.stairGrade() * 0.8 : ctx.config.roadGrade() * 1.1, 9, role == RoadEdge.Role.TRACK ? 3 : 5, false, 600_000,
			role == RoadEdge.Role.TRACK ? 0.25 : ctx.state.layout.terraces ? 0.35 : 0.7);
		// from the heart, a main street leaves the middle of a side of the square at right angles and runs straight for
		// a while before the land is allowed to bend it
		int[] lead = null;
		int sx = n.x();
		int sz = n.z();
		if (n.kind() == RoadGraph.NodeKind.HEART && role != RoadEdge.Role.TRACK) {
			int h = Roads.cardinal(StrictMath.cos(angle), StrictMath.sin(angle));
			int dx = Roads.DX[h];
			int dz = Roads.DZ[h];
			// from the square's edge, but never so near the middle that the street's width reaches the well
			int half = Math.max(ctx.state.plazaHalf, width / 2 + 3);
			int run = (int) Math.min(36, length / 3);
			double limit = stairs ? ctx.config.stairGrade() * 0.8 : ctx.config.roadGrade();
			int k = 0;
			double prev = g.smooth(n.x() + dx * half, n.z() + dz * half);
			while (k < run) {
				int x = n.x() + dx * (half + k + 1);
				int z = n.z() + dz * (half + k + 1);
				double here = g.smooth(x, z);
				byte c = ctx.occ.get(x, z);
				if (g.isWater(x, z) || Math.abs(here - prev) > limit + 0.05 || !(c == Cell.FREE || c == Cell.PLANNED_ROAD)) {
					break;
				}
				prev = here;
				k++;
			}
			if (k >= 8) {
				lead = new int[]{n.x() + dx * half, n.z() + dz * half, n.x() + dx * (half + k), n.z() + dz * (half + k)};
				sx = lead[2];
				sz = lead[3];
			}
		}
		// farm tracks follow field edges: square to the compass, turning now and then
		if (role == RoadEdge.Role.TRACK) {
			params = params.cardinalOnly();
		}
		int[][] path = Router.route(g, ctx.occ, sx, sz, tx, tz, params);
		if (path == null && lead == null) {
			return null;
		}
		int[] xs;
		int[] zs;
		if (lead != null) {
			int[][] straight = Router.densify(new int[]{lead[0], lead[2]}, new int[]{lead[1], lead[3]});
			if (path == null) {
				xs = straight[0];
				zs = straight[1];
			} else {
				xs = Roads.concat(java.util.Arrays.copyOf(straight[0], straight[0].length - 1), path[0]);
				zs = Roads.concat(java.util.Arrays.copyOf(straight[1], straight[1].length - 1), path[1]);
			}
		} else {
			xs = path[0];
			zs = path[1];
			// begin on the square's edge rather than its middle
			int start = 0;
			if (n.kind() == RoadGraph.NodeKind.HEART) {
				Reservation sq = ctx.state.reservation("square");
				while (sq != null && start + 1 < xs.length && (sq.contains(xs[start + 1], zs[start + 1])
					|| Math.max(Math.abs(xs[start] - n.x()), Math.abs(zs[start] - n.z())) < width / 2 + 3)) {
					start++;
				}
			}
			if (start > 0) {
				xs = java.util.Arrays.copyOfRange(xs, start, xs.length);
				zs = java.util.Arrays.copyOfRange(zs, start, zs.length);
			}
		}
		RoadEdge e = Roads.plan(ctx, from, xs, zs, role, kind, width, 12);
		if (e != null && build > 0) {
			Roads.build(ctx, e, build, "road: the first stretch of the " + (role == RoadEdge.Role.TRACK ? "track" : "main street"));
		}
		return e;
	}

	/** A stair street straight up (or down) the slope from a node. */
	private static @Nullable RoadEdge stair(PlanContext ctx, int from, double angle, double length, int build) {
		RoadGraph.Node n = ctx.state.graph.node(from);
		// a stair street goes straight up the fall line from the middle of the square's side
		int heading = Roads.cardinal(StrictMath.cos(angle), StrictMath.sin(angle));
		boolean[] joined = new boolean[1];
		int[][] path = Roads.walk(ctx, n.x(), n.z(), heading, ctx.state.plazaHalf + 1, (int) length, Roads.Mode.STRAIGHT, 8, 1, joined);
		if (path[0].length < ctx.state.plazaHalf + 10) {
			return null;
		}
		int[] xs = path[0];
		int[] zs = path[1];
		int start = 0;
		Reservation sq = ctx.state.reservation("square");
		while (sq != null && start + 1 < xs.length && sq.contains(xs[start + 1], zs[start + 1])) {
			start++;
		}
		xs = java.util.Arrays.copyOfRange(xs, start, xs.length);
		zs = java.util.Arrays.copyOfRange(zs, start, zs.length);
		RoadEdge e = Roads.plan(ctx, from, xs, zs, RoadEdge.Role.STAIR, RoadKind.LANE, 3, 10);
		if (e != null && build > 0) {
			Roads.build(ctx, e, build, "road: the stair street up from the square");
		}
		return e;
	}

	/**
	 * A road up the mountain in hairpins: legs along the contour, each climbing about seven blocks at a gentle grade
	 * (offset uphill by the rise over the slope), turning back at each end. No stairs: carts go up it. Stops where a
	 * leg would leave the land it can grade.
	 */
	private static void switchback(PlanContext ctx, int from, Site site) {
		RoadGraph.Node n = ctx.state.graph.node(from);
		double slope = Math.max(0.12, site.slope());
		double ua = site.uphillAngle();
		double ux = StrictMath.cos(ua);
		double uz = StrictMath.sin(ua);
		double cx = -uz;
		double cz = ux;
		int legLen = 40;
		double climb = 7;
		double up = climb / slope;
		int legs = (int) Math.max(2, Math.min(7, (site.summitY() - ctx.state.heartY) / climb));
		int half = ctx.state.plazaHalf + 1;
		java.util.List<Integer> vx = new java.util.ArrayList<>();
		java.util.List<Integer> vz = new java.util.ArrayList<>();
		double px = n.x() + ux * half;
		double pz = n.z() + uz * half;
		vx.add((int) Math.round(px));
		vz.add((int) Math.round(pz));
		// the first leg starts off across the slope from the square's uphill side
		px += cx * legLen / 2.0 + ux * up / 2;
		pz += cz * legLen / 2.0 + uz * up / 2;
		vx.add((int) Math.round(px));
		vz.add((int) Math.round(pz));
		int dir = -1;
		for (int k = 0; k < legs; k++) {
			px += cx * legLen * dir + ux * up;
			pz += cz * legLen * dir + uz * up;
			vx.add((int) Math.round(px));
			vz.add((int) Math.round(pz));
			dir = -dir;
		}
		int[][] dense = Router.densify(vx.stream().mapToInt(Integer::intValue).toArray(), vz.stream().mapToInt(Integer::intValue).toArray());
		RoadEdge e = Roads.plan(ctx, from, dense[0], dense[1], RoadEdge.Role.SWITCHBACK, RoadKind.STREET, 3, 16);
		if (e != null) {
			Roads.build(ctx, e, 24, "road: the first hairpins up the mountain");
		}
	}

	/** A ring road round the heart at a radius, a closed loop starting where it crosses the first main road. */
	private static void ring(PlanContext ctx, int heart, int radius, boolean build) {
		RoadGraph.Node n = ctx.state.graph.node(heart);
		Ground g = ctx.ground;
		int points = Math.max(16, radius / 3);
		double start = 0;
		RoadEdge first = null;
		for (RoadEdge e : ctx.state.graph.edges()) {
			if (e.role == RoadEdge.Role.ARM) {
				first = e;
				break;
			}
		}
		if (first != null) {
			Polyline.Dense d = first.dense();
			int i = Math.min(d.length() - 1, Math.max(0, radius - ctx.state.plazaHalf));
			start = StrictMath.atan2(d.zs()[i] - n.z(), d.xs()[i] - n.x());
		}
		int[] vx = new int[points + 1];
		int[] vz = new int[points + 1];
		for (int i = 0; i <= points; i++) {
			double a = start + i * 2 * Math.PI / points;
			double r = radius;
			int x = n.x() + (int) Math.round(StrictMath.cos(a) * r);
			int z = n.z() + (int) Math.round(StrictMath.sin(a) * r);
			for (int k = 0; k < 6 && g.isWater(x, z); k++) {
				r -= 3;
				x = n.x() + (int) Math.round(StrictMath.cos(a) * r);
				z = n.z() + (int) Math.round(StrictMath.sin(a) * r);
			}
			vx[i] = x;
			vz[i] = z;
		}
		int[][] dense = Router.densify(vx, vz);
		int[] at = first == null ? null : nearestIndex(first, dense[0][0], dense[1][0]);
		int from = at == null ? heart : Roads.junction(ctx, first, at[0]);
		RoadEdge e = Roads.plan(ctx, from, dense[0], dense[1], RoadEdge.Role.RING, RoadKind.LANE, 3, 20);
		if (e != null && build) {
			Roads.build(ctx, e, 30, "road: the first stretch of the ring");
		}
	}

	private static int @Nullable [] nearestIndex(RoadEdge e, int x, int z) {
		Polyline.Dense d = e.dense();
		int i = d.nearest(x, z);
		return d.distance2(i, x, z) <= 9 ? new int[]{i} : null;
	}

	/** The stilt town's spine: a boardwalk straight out from the shore over the water. */
	private static void boardwalkFromShore(PlanContext ctx, int heart, Site site) {
		if (!site.hasWater()) {
			return;
		}
		RoadGraph.Node n = ctx.state.graph.node(heart);
		int heading = Roads.cardinal(site.waterX() - n.x(), site.waterZ() - n.z());
		boolean[] joined = new boolean[1];
		int[][] path = Roads.walk(ctx, n.x(), n.z(), heading, ctx.state.plazaHalf + 1, 60, Roads.Mode.WATER, 0, 1, joined);
		if (path[0].length < ctx.state.plazaHalf + 8) {
			return;
		}
		int start = ctx.state.plazaHalf;
		int[] xs = java.util.Arrays.copyOfRange(path[0], start, path[0].length);
		int[] zs = java.util.Arrays.copyOfRange(path[1], start, path[1].length);
		RoadEdge e = Roads.plan(ctx, heart, xs, zs, RoadEdge.Role.BOARDWALK, RoadKind.LANE, 3, 6);
		if (e != null) {
			Roads.build(ctx, e, e.length(), "road: the boardwalk out over the water");
		}
	}

	/** The nearest spot within 48 blocks where a square of thirteen would stand on fairly even, dry ground. */
	private static int[] easierGround(Ground g, int hx, int hz) {
		int[] best = {hx, hz};
		double bestScore = Double.MAX_VALUE;
		for (int r = 4; r <= 48; r += 4) {
			for (int a = 0; a < 24; a++) {
				double ang = a * Math.PI / 12;
				int x = hx + (int) Math.round(StrictMath.cos(ang) * r);
				int z = hz + (int) Math.round(StrictMath.sin(ang) * r);
				if (g.isWater(x, z)) {
					continue;
				}
				int[] range = g.range(x - 6, z - 6, x + 6, z + 6, 2);
				double score = (range[1] - range[0]) * 4 + r * 0.3;
				if (score < bestScore) {
					bestScore = score;
					best = new int[]{x, z};
				}
			}
		}
		return best;
	}

	/** The foot of the cliff near the heart: down the face until the ground levels out. */
	private static int[] cliffFoot(Ground g, int hx, int hz, Site site) {
		double a = site.cliffAngle();
		double dx = StrictMath.cos(a);
		double dz = StrictMath.sin(a);
		// find the face first (the steepest column along the downhill line), then walk off its foot
		int bestK = 0;
		double steepest = 0;
		for (int k = -40; k <= 40; k += 2) {
			int x = hx + (int) Math.round(dx * k);
			int z = hz + (int) Math.round(dz * k);
			double s = g.slope(x, z, 2);
			if (s > steepest) {
				steepest = s;
				bestK = k;
			}
		}
		int k = bestK;
		while (k < bestK + 40) {
			int x = hx + (int) Math.round(dx * k);
			int z = hz + (int) Math.round(dz * k);
			if (g.slope(x, z, 3) < 0.25 && !g.isWater(x, z)) {
				break;
			}
			k++;
		}
		k += 10;
		int x = hx + (int) Math.round(dx * k);
		int z = hz + (int) Math.round(dz * k);
		return g.isWater(x, z) ? new int[]{hx, hz} : new int[]{x, z};
	}
}
