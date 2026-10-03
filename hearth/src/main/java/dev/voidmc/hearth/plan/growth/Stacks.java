package dev.voidmc.hearth.plan.growth;

import dev.voidmc.hearth.api.ArchetypeInfo;
import dev.voidmc.hearth.api.ArchetypeInfo.SiteFlag;
import dev.voidmc.hearth.api.PlotSpec;
import dev.voidmc.hearth.api.PlotState;
import dev.voidmc.hearth.api.PlotView;
import dev.voidmc.hearth.plan.PlanContext;
import dev.voidmc.hearth.plan.grid.Cell;
import dev.voidmc.hearth.plan.model.QueuedProject;
import dev.voidmc.hearth.plan.road.Roads;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
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
 */
public final class Stacks {
	private static final int MAX_LEVEL = 3;

	private Stacks() {
	}

	static boolean plan(PlanContext ctx) {
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
}
