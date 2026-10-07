package dev.voidmc.hearth.plan.growth;

import dev.voidmc.hearth.api.ArchetypeInfo;
import dev.voidmc.hearth.api.ArchetypeInfo.SiteFlag;
import dev.voidmc.hearth.api.PlotSpec;
import dev.voidmc.hearth.api.PlotState;
import dev.voidmc.hearth.api.PlotView;
import dev.voidmc.hearth.api.SettlementTier;
import dev.voidmc.hearth.api.Station;
import dev.voidmc.hearth.plan.PlanContext;
import dev.voidmc.hearth.plan.catalogue.Archetypes;
import dev.voidmc.hearth.plan.grid.Cell;
import dev.voidmc.hearth.plan.layout.CliffFace;
import dev.voidmc.hearth.plan.model.QueuedProject;
import dev.voidmc.hearth.plan.model.Reservation;
import dev.voidmc.hearth.plan.model.RoadEdge;
import dev.voidmc.hearth.plan.road.Polyline;
import dev.voidmc.hearth.plan.road.Roads;
import dev.voidmc.hearth.plan.society.Stations;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import org.jspecify.annotations.Nullable;

/**
 * Stacked dwellings (pueblos, karst cliff towns, oasis roof terraces): a STACKABLE building raised on the roof of a
 * built STACKABLE one, set back by half its depth so its front half stands on the roof below (a roof terrace before
 * its door) and its back half on the slope behind. Up to three levels. The stacked plot's floor is the lower plot's
 * {@code floorY + maxHeight}; params {@code stacked_on} names the plot below, {@code level} the storey of the stack.
 *
 * <p>In a cliff town with a face quarter ({@link CliffFace}) the stacks are the quarter's: columns of cliff dwellings
 * at the foot, their rooms dug into the toe, each course set back a course's height behind the one below (one block in
 * one, the climbs' own grade) and stacked up to the crest, the inner group (behind the square) before the outer. Face
 * plots carry {@code face_column} and {@code level}, and {@code open_left}/{@code open_right} (the dwelling's own
 * sides, as party walls are named) where its terrace runs on into a neighbour's or onto a climb's landing.
 */
public final class Stacks {
	private static final int MAX_LEVEL = 3;

	private Stacks() {
	}

	static boolean plan(PlanContext ctx) {
		CliffFace.Face face = CliffFace.of(ctx);
		if (face != null) {
			// a cliff town's face is built up by its households (Housing), not on the plain; here only its lookout
			return lookout(ctx, face, placed(ctx));
		}
		Set<Integer> under = new HashSet<>();
		List<PlotSpec> specs = new ArrayList<>();
		for (PlotView p : ctx.settlement.plots()) {
			specs.add(p.spec());
		}
		for (QueuedProject q : ctx.state.queue) {
			specs.add(q.spec());
		}
		for (PlotSpec s : specs) {
			int on = s.params().getIntOr("stacked_on", -1);
			if (on >= 0) {
				under.add(on);
			}
		}
		List<PlotView> plots = new ArrayList<>(ctx.settlement.plots());
		plots.sort(Comparator.comparingInt(PlotView::id));
		for (PlotView p : plots) {
			if (p.state() != PlotState.BUILT || under.contains(p.id())) {
				continue;
			}
			ArchetypeInfo info = ctx.info(p.spec().archetype());
			if (info == null || !info.flags().contains(SiteFlag.STACKABLE)) {
				continue;
			}
			int level = p.spec().params().getIntOr("level", 0);
			if (level + 1 >= MAX_LEVEL) {
				continue;
			}
			PlotSpec top = stackOn(ctx, p.id(), p.spec(), info, level + 1);
			if (top != null) {
				Roads.issue(ctx, top, "stacking: a dwelling raised on the roof of the one below (level " + (level + 2) + ")");
				return true;
			}
		}
		return false;
	}

	private static @Nullable PlotSpec stackOn(PlanContext ctx, int id, PlotSpec below, ArchetypeInfo info, int level) {
		Direction f = below.facing();
		int depth = below.depth();
		int set = Math.max(2, depth / 2);
		int x0 = below.origin().getX() - f.getStepX() * set;
		int z0 = below.origin().getZ() - f.getStepZ() * set;
		int x1 = x0 + below.sizeX() - 1;
		int z1 = z0 + below.sizeZ() - 1;
		int floor = below.floorY() + below.maxHeight();
		// the back half must be open ground no higher than the new floor (the slope behind), never water
		for (int z = z0; z <= z1; z++) {
			for (int x = x0; x <= x1; x++) {
				boolean overBelow = x >= below.origin().getX() && x < below.origin().getX() + below.sizeX() && z >= below.origin().getZ()
					&& z < below.origin().getZ() + below.sizeZ();
				if (overBelow) {
					continue;
				}
				if (ctx.occ.get(x, z) != Cell.FREE && ctx.occ.get(x, z) != Cell.SETBACK || ctx.ground.isWater(x, z)) {
					return null;
				}
				int g = ctx.ground.ground(x, z);
				// behind: open ground no higher than the new floor, or rock the dwelling is cut into (cliff towns)
				boolean intoRock = Parcels.cutsIn(ctx) && g <= floor + below.maxHeight() + 12;
				if (g > floor + 1 && !intoRock || g < floor - ctx.config.terraceMaxHeight()) {
					return null;
				}
			}
		}
		CompoundTag params = new CompoundTag();
		params.putInt("stacked_on", id);
		params.putInt("level", level);
		params.putBoolean("stacked", true);
		params.putInt("street_y", below.floorY() + below.maxHeight());
		int height = Math.max(info.minHeight(), Math.min(info.maxHeight(), info.minHeight() + 1));
		return new PlotSpec(info.id(), ctx.style, ctx.nextSeed(), new BlockPos(x0, floor, z0), below.sizeX(), below.sizeZ(), f, height,
			below.tier(), params);
	}

	// ------------------------------------------------------------------------------------------------ the face quarter

	/** A face plot the quarter already has: its spec, its id once a plot (or -1 while queued), whether it is built. */
	private record Placed(PlotSpec spec, int id, boolean built) {
	}

	/**
	 * Plans the next dwelling of a cliff town's face quarter, if it has one with room left: the lowest course missing in
	 * the inner group (the column under it built), else in the outer. {@code home} is the household's params (station,
	 * household) when it is built for one. True if a dwelling was queued.
	 */
	public static boolean face(PlanContext ctx, @Nullable CompoundTag home, @Nullable String reason) {
		CliffFace.Face f = CliffFace.of(ctx);
		if (f == null) {
			return false;
		}
		ArchetypeInfo info = ctx.catalogue.info(Archetypes.CLIFF_DWELLING);
		if (info == null) {
			return false;
		}
		int columns = CliffFace.columns();
		Map<Integer, Placed[]> have = placed(ctx);
		for (int group = 0; group < 2; group++) {
			// the outer group waits for the town to reach a village and for the inner one to have climbed
			if (group == 1 && (ctx.tier.ordinal() < SettlementTier.VILLAGE.ordinal() || !climbed(have, f, 0, Math.max(2, f.courses() - 1)))) {
				break;
			}
			for (int level = 0; level < f.courses(); level++) {
				for (int c = 0; c < columns; c++) {
					if (CliffFace.group(c) != group) {
						continue;
					}
					Placed[] col = have.computeIfAbsent(c, k -> new Placed[CliffFace.MAX_COURSES]);
					if (col[level] != null) {
						continue;
					}
					PlotSpec spec;
					if (level == 0) {
						spec = base(ctx, f, info, c, home);
					} else {
						Placed below = col[level - 1];
						if (below == null || !below.built()) {
							continue;
						}
						spec = course(ctx, f, info, c, level, below, have, home);
					}
					if (spec != null) {
						if (group == 1) {
							climbs(ctx, f);
						}
						String why = reason != null ? reason : level == 0 ? "stacking: a dwelling dug into the foot of the cliff"
							: "stacking: a dwelling cut into the cliff on the roof of the one below (course " + (level + 1) + ")";
						Roads.issue(ctx, spec, why);
						ctx.buildings++;
						return true;
					}
				}
			}
		}
		return false;
	}

	/** The face dwellings the quarter has, by column and course. */
	private static Map<Integer, Placed[]> placed(PlanContext ctx) {
		Map<Integer, Placed[]> have = new HashMap<>();
		for (PlotView p : ctx.settlement.plots()) {
			collect(have, p.spec(), p.id(), p.state() == PlotState.BUILT);
		}
		for (QueuedProject q : ctx.state.queue) {
			collect(have, q.spec(), -1, false);
		}
		return have;
	}

	private static void collect(Map<Integer, Placed[]> have, PlotSpec s, int id, boolean built) {
		int c = s.paramInt("face_column", -1);
		if (c < 0) {
			return;
		}
		int level = s.paramInt("level", 0);
		if (level < CliffFace.MAX_COURSES) {
			have.computeIfAbsent(c, k -> new Placed[CliffFace.MAX_COURSES])[level] = new Placed(s, id, built);
		}
	}

	/** Whether every column of a group that stood its base course has reached {@code levels} courses. */
	private static boolean climbed(Map<Integer, Placed[]> have, CliffFace.Face f, int group, int levels) {
		int any = 0;
		for (int c = 0; c < CliffFace.columns(); c++) {
			Placed[] col = have.get(c);
			if (CliffFace.group(c) != group || col == null || col[0] == null) {
				continue;
			}
			any++;
			for (int k = 0; k < Math.min(levels, f.courses()); k++) {
				if (col[k] == null) {
					return false;
				}
			}
		}
		return any > 0;
	}

	/**
	 * A column's base course: its front on the main street (the square for the square's own column), its forecourt on
	 * the flat at the foot, its rooms dug into the toe. Null where the column's ground is not free or not near the
	 * street's level at the front.
	 */
	private static @Nullable PlotSpec base(PlanContext ctx, CliffFace.Face f, ArchetypeInfo info, int c, @Nullable CompoundTag home) {
		int t0 = CliffFace.columnStart(c);
		int t1 = t0 + CliffFace.columnFrontage(c) - 1;
		int front = f.front(c);
		int[] r = f.rect(t0, t1, front - CliffFace.DEPTH + 1, front);
		if (!open(ctx, r, true)) {
			return null;
		}
		// the door's street: the square, or the main street (built on as far as the column if it has not reached it)
		int tm = t0 + CliffFace.columnFrontage(c) / 2;
		int ex = f.x(tm, front + 1);
		int ez = f.z(tm, front + 1);
		int streetY;
		if (c == 0) {
			streetY = ctx.state.heartY;
		} else {
			RoadEdge arm = null;
			int index = -1;
			for (RoadEdge e : ctx.state.graph.edges()) {
				if (e.role != RoadEdge.Role.ARM) {
					continue;
				}
				Polyline.Dense d = e.dense();
				int i = d.nearest(f.x(tm, 0), f.z(tm, 0));
				if (d.xs()[i] == f.x(tm, 0) && d.zs()[i] == f.z(tm, 0)) {
					arm = e;
					index = i;
					break;
				}
			}
			if (arm == null) {
				return null;
			}
			if (!Cell.isAccess(ctx.occ.get(ex, ez))) {
				if (!ctx.roadsMayGrow()) {
					return null;
				}
				Roads.build(ctx, arm, index + CliffFace.columnFrontage(c), "road: the street along the foot of the cliff");
				if (!Cell.isAccess(ctx.occ.get(ex, ez))) {
					return null;
				}
			}
			streetY = arm.dense().ys()[index];
		}
		// the forecourt is levelled at the street's level: a little cut into the toe, little fill
		for (int u = front; u > front - 3; u--) {
			for (int t = t0; t <= t1; t++) {
				int g = ctx.ground.ground(f.x(t, u), f.z(t, u));
				if (g > streetY + 5 || g < streetY - 3) {
					return null;
				}
			}
		}
		CompoundTag params = home == null ? new CompoundTag() : home.copy();
		params.putInt("face_column", c);
		params.putInt("level", 0);
		params.putBoolean("cut_into_slope", true);
		params.putInt("street_y", streetY);
		params.putString("zone", Build.zoneName(ctx.zone(ex, ez)));
		return new PlotSpec(info.id(), ctx.style, ctx.nextSeed(), new BlockPos(r[0], streetY, r[1]), r[2] - r[0] + 1, r[3] - r[1] + 1, f.out(),
			f.course(), finish(ctx, info, params), params);
	}

	/**
	 * Course {@code level} of a column: on the roof of the course below, set back a course's height, its forecourt on
	 * that roof's back rows and its rooms cut into the rock behind.
	 */
	private static @Nullable PlotSpec course(PlanContext ctx, CliffFace.Face f, ArchetypeInfo info, int c, int level, Placed below,
		Map<Integer, Placed[]> have, @Nullable CompoundTag home) {
		PlotSpec b = below.spec();
		int set = f.set();
		int x0 = b.origin().getX() - f.out().getStepX() * set;
		int z0 = b.origin().getZ() - f.out().getStepZ() * set;
		int[] r = {x0, z0, x0 + b.sizeX() - 1, z0 + b.sizeZ() - 1};
		int floor = b.floorY() + b.maxHeight();
		// behind the roof below: the quarter's ground, cut into the rock, or ground a little below the floor (where the
		// face steps back, footings down to it); never water
		for (int z = r[1]; z <= r[3]; z++) {
			for (int x = r[0]; x <= r[2]; x++) {
				boolean overBelow = x >= b.origin().getX() && x < b.origin().getX() + b.sizeX() && z >= b.origin().getZ()
					&& z < b.origin().getZ() + b.sizeZ();
				if (overBelow) {
					continue;
				}
				byte cell = ctx.occ.get(x, z);
				if (!(Cell.isBuildable(cell) || cell == Cell.RESERVED && faceGround(ctx, x, z)) || ctx.ground.isWater(x, z)) {
					return null;
				}
				if (ctx.ground.ground(x, z) < floor - ctx.config.terraceMaxHeight()) {
					return null;
				}
			}
		}
		CompoundTag params = home == null ? new CompoundTag() : home.copy();
		params.putInt("face_column", c);
		params.putInt("level", level);
		params.putInt("stacked_on", below.id());
		params.putBoolean("stacked", true);
		params.putInt("set", set);
		params.putInt("street_y", floor);
		params.putString("zone", Build.zoneName(Math.min(2, ctx.zone(r[0], r[1]))));
		// the terrace runs on into a neighbour's whose course below stands (or onto a climb's landing beside it)
		int[] nb = CliffFace.neighbours(c);
		for (int side = 0; side < 2; side++) {
			int n = nb[side];
			boolean open = n == CliffFace.CLIMB ? CliffFace.climbAt(ctx, f, CliffFace.climbBeside(c, side)) != null
				: n >= 0 && have.get(n) != null && have.get(n)[level - 1] != null;
			if (open) {
				// side 0 is -t, the dwelling's own left (its right hand is the face's +t)
				params.putBoolean(side == 0 ? "open_left" : "open_right", true);
			}
		}
		return new PlotSpec(info.id(), ctx.style, ctx.nextSeed(), new BlockPos(r[0], floor, r[1]), b.sizeX(), b.sizeZ(), f.out(), f.course(),
			b.tier(), params);
	}

	/**
	 * The lookout on the crest at the head of the main climb, once the town is a village and the inner group has climbed
	 * halfway: a watchtower facing down over the roofs.
	 */
	private static boolean lookout(PlanContext ctx, CliffFace.Face f, Map<Integer, Placed[]> have) {
		Reservation top = ctx.state.reservation(CliffFace.TOP);
		if (top == null || ctx.tier.ordinal() < SettlementTier.VILLAGE.ordinal() || !climbed(have, f, 0, Math.max(2, f.courses() / 2))) {
			return false;
		}
		ArchetypeInfo tower = ctx.catalogue.resolve(Archetypes.WATCHTOWER, ctx.culture, ctx.tier);
		if (tower == null) {
			return false;
		}
		for (int z = top.z0(); z <= top.z1(); z++) {
			for (int x = top.x0(); x <= top.x1(); x++) {
				if (ctx.occ.get(x, z) != Cell.RESERVED) {
					return false;
				}
			}
		}
		int[] mid = {(top.x0() + top.x1()) / 2, (top.z0() + top.z1()) / 2};
		int tm = f.t(mid[0], mid[1]);
		int uf = Math.max(f.u(top.x0(), top.z0()), f.u(top.x1(), top.z1()));
		// the way it is reached: the head of the climb or the lane along the crest, a few steps in front of its door
		int[] at = null;
		for (int k = 1; k <= 4 && at == null; k++) {
			at = Roads.edgeAt(ctx, f.x(tm, uf + k), f.z(tm, uf + k));
		}
		if (at == null) {
			return false;
		}
		RoadEdge e = ctx.state.graph.edge(at[0]);
		int floor = e.dense().ys()[at[1]];
		int lo = Integer.MAX_VALUE;
		int hi = Integer.MIN_VALUE;
		for (int z = top.z0(); z <= top.z1(); z++) {
			for (int x = top.x0(); x <= top.x1(); x++) {
				lo = Math.min(lo, ctx.ground.ground(x, z));
				hi = Math.max(hi, ctx.ground.ground(x, z));
			}
		}
		if (hi - floor > ctx.config.terraceMaxHeight() || floor - lo > ctx.config.terraceMaxHeight()) {
			return false;
		}
		CompoundTag params = new CompoundTag();
		params.putInt("street_y", floor);
		params.putBoolean("lookout", true);
		if (hi - lo > ctx.config.slopeTolerance()) {
			params.putBoolean("terraced", true);
			if (ctx.catalogue.has(Archetypes.TERRACE)) {
				Roads.issue(ctx, Build.terrace(ctx, top.x0(), top.z0(), top.x1(), top.z1(), floor, f.out()), "terrace: level ground for the lookout");
			}
		}
		ctx.state.removeReservation(CliffFace.TOP);
		PlotSpec spec = new PlotSpec(tower.id(), ctx.style, ctx.nextSeed(), new BlockPos(top.x0(), floor, top.z0()), top.x1() - top.x0() + 1,
			top.z1() - top.z0() + 1, f.out(), Build.height(ctx, tower, 1), Build.finish(ctx, tower, 1, null), params);
		ctx.occ.markRect(top.x0(), top.z0(), top.x1(), top.z1(), Cell.FREE);
		Roads.issue(ctx, spec, "landmark: a lookout on the crest at the head of the stair streets");
		ctx.buildings++;
		return true;
	}

	/** Builds the outer climbs (planned at the founding) once the outer group starts. */
	private static void climbs(PlanContext ctx, CliffFace.Face f) {
		for (int c : new int[]{30, -30}) {
			RoadEdge e = CliffFace.climbAt(ctx, f, c);
			if (e != null) {
				CliffFace.issue(ctx, f, e);
			}
		}
	}

	/** Whether a reserved column is the face quarter's own (and held for nothing else: a castle hill, a lot). */
	private static boolean faceGround(PlanContext ctx, int x, int z) {
		boolean face = false;
		for (Reservation r : ctx.state.reservations) {
			if (r.contains(x, z)) {
				if (!r.tag().startsWith(CliffFace.TAG) || r.tag().equals(CliffFace.TOP)) {
					return false;
				}
				face = true;
			}
		}
		return face;
	}

	/** Whether a rectangle {x0, z0, x1, z1} is free for a face dwelling (the quarter's reserved ground counts as free). */
	private static boolean open(PlanContext ctx, int[] r, boolean dry) {
		for (int z = r[1]; z <= r[3]; z++) {
			for (int x = r[0]; x <= r[2]; x++) {
				byte cell = ctx.occ.get(x, z);
				if (!(Cell.isVacant(cell) || cell == Cell.RESERVED && faceGround(ctx, x, z)) || dry && ctx.ground.isWater(x, z)) {
					return false;
				}
			}
		}
		return true;
	}

	private static int finish(PlanContext ctx, ArchetypeInfo info, CompoundTag params) {
		int finish = Build.finish(ctx, info, 1, null);
		Station station = Build.station(params);
		return station == null ? finish : Math.max(0, Math.min(4, finish + Stations.finish(station)));
	}
}
