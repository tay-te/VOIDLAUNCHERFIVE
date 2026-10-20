package dev.voidmc.hearth.plan.resource;

import dev.voidmc.hearth.api.ArchetypeInfo;
import dev.voidmc.hearth.api.PlotSpec;
import dev.voidmc.hearth.api.PlotState;
import dev.voidmc.hearth.api.PlotView;
import dev.voidmc.hearth.api.ResourceSite;
import dev.voidmc.hearth.api.UndergroundProbe;
import dev.voidmc.hearth.plan.PlanContext;
import dev.voidmc.hearth.plan.PlanModule;
import dev.voidmc.hearth.plan.growth.Build;
import dev.voidmc.hearth.plan.growth.Growth;
import dev.voidmc.hearth.plan.model.QueuedProject;
import dev.voidmc.hearth.plan.model.RoadEdge;
import dev.voidmc.hearth.plan.model.RoadKind;
import dev.voidmc.hearth.plan.road.Roads;
import dev.voidmc.hearth.sim.land.GreatMines;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import org.jspecify.annotations.Nullable;

/**
 * The Great Mine reopened by the planner (docs/LIVING_WORLD_2.md §6.2): a town whose survey found one of Expanse's great
 * mines in its territory builds a working pithead ({@code hearth:extract/great_pithead}) on the shaft itself — the plot's
 * middle on the shaft's centre, its floor one over the deck Expanse left the shaft at — and a cart track up to it. The
 * pithead's galleries open off the old levels (their floors are params), cut by the miners as any excavation. One a town;
 * ordinary mines and shafts never take the Great Mine's site ({@link Extraction}).
 */
public final class GreatMinePlan {
	/** The pithead's plot: 25 blocks square round the shaft (the claimed square, ±12). */
	public static final int SIZE = 25;
	/** Levels a pithead opens galleries from, at most (the highest first). */
	static final int MAX_LEVELS = 6;
	/** The pithead's height over its floor (the headframe's sheave). */
	static final int HEIGHT = 22;

	private GreatMinePlan() {
	}

	/** The town's Great Mine site, if its territory has one and no pithead stands or is queued on it. */
	public static @Nullable ResourceSite site(PlanContext ctx) {
		if (!ctx.config.living().extraction() || !GreatMines.enabled()) {
			return null;
		}
		for (PlotView p : ctx.settlement.plots()) {
			if (p.spec().archetype().equals(GreatMines.PITHEAD) && p.state() != PlotState.DEMOLISHING) {
				return null;
			}
		}
		for (QueuedProject q : ctx.state.queue) {
			if (q.spec().archetype().equals(GreatMines.PITHEAD)) {
				return null;
			}
		}
		for (ResourceSite s : ctx.settlement.sites()) {
			if (GreatMines.isGreat(s) && s.remaining() > 0) {
				return s;
			}
		}
		return null;
	}

	/** Whether the town would reopen a Great Mine now (the program's condition). */
	public static boolean available(PlanContext ctx) {
		return site(ctx) != null;
	}

	/**
	 * Plans the pithead on the shaft, or first the track up to it if no road comes near: true if anything was queued.
	 * The program entry's custom placement.
	 */
	public static boolean place(PlanContext ctx, ArchetypeInfo info) {
		ResourceSite site = site(ctx);
		if (site == null) {
			Growth.why("no great mine in the territory");
			return false;
		}
		UndergroundProbe u = PlanModule.underground(ctx.ground.probe());
		GreatMines.Mine mine = GreatMines.at(GreatMines.of(u), site);
		BlockPos shaft = mine != null ? mine.pithead() : site.center();
		int half = SIZE / 2;
		int x0 = shaft.getX() - half;
		int z0 = shaft.getZ() - half;
		if (!ctx.occ.isFree(x0, z0, x0 + SIZE - 1, z0 + SIZE - 1)) {
			Growth.why("the pithead's ground is taken");
			return false;
		}
		// the front toward the town, where the track comes up
		Direction facing = Direction.getApproximateNearest(ctx.heartX() - shaft.getX(), 0, ctx.heartZ() - shaft.getZ());
		if (facing.getAxis() == Direction.Axis.Y) {
			facing = Direction.SOUTH;
		}
		int[] gate = front(shaft, facing, half);
		boolean far = ctx.distanceToHeart(gate[0], gate[1]) > ctx.config.living().haulMaxDistance();
		if (!far && !Extraction.accessNear(ctx, gate[0], gate[1], 14)) {
			// the track up to the pithead first (with steps where the mountain is too steep for carts)
			if (Roads.haul(ctx, gate[0], gate[1], RoadEdge.Role.HAUL, RoadKind.PATH, 3, false, "road: a cart track up to the great mine") != null) {
				return true;
			}
			ctx.routed = false;
			if (Roads.haul(ctx, gate[0], gate[1], RoadEdge.Role.HAUL, RoadKind.PATH, 3, true, "road: a track with steps up to the great mine") != null) {
				return true;
			}
			// no way could be found: the pithead goes up all the same, its folk walk the mountain
		}
		CompoundTag params = new CompoundTag();
		params.putInt("site", site.id());
		params.putString("kind", site.kind().getSerializedName());
		List<Integer> levels = mine == null ? List.of() : mine.levels();
		int floorY = shaft.getY() + 1;
		int n = Math.max(0, Math.min(MAX_LEVELS, levels.size() - 1));
		int[] rel = new int[n];
		for (int i = 0; i < n; i++) {
			// (the lowest level is the great drift into the realm: no galleries are opened from it)
			rel[i] = levels.get(i) - floorY;
		}
		params.put("levels", new IntArrayTag(rel));
		if (mine != null) {
			params.putInt("shaft_bottom", mine.shaftBottom() - floorY);
		}
		params.putInt("street_y", floorY);
		PlotSpec spec = new PlotSpec(GreatMines.PITHEAD, ctx.style, ctx.nextSeed(), new BlockPos(x0, floorY, z0), SIZE, SIZE, facing, HEIGHT,
			Math.max(1, Math.min(3, ctx.tier.ordinal())), params);
		Roads.issue(ctx, spec, "resources: the great mine reopened (site " + site.id() + ")");
		return true;
	}

	/** The middle of the plot's front edge, a block out: where the track arrives. */
	static int[] front(BlockPos shaft, Direction facing, int half) {
		return new int[]{shaft.getX() + facing.getStepX() * (half + 1), shaft.getZ() + facing.getStepZ() * (half + 1)};
	}
}
