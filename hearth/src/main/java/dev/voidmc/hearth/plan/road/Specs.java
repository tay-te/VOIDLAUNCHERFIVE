package dev.voidmc.hearth.plan.road;

import dev.voidmc.hearth.api.PlotSpec;
import dev.voidmc.hearth.plan.PlanContext;
import dev.voidmc.hearth.plan.catalogue.Archetypes;
import dev.voidmc.hearth.plan.model.RoadKind;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;

/**
 * Makes the plot specs of linear things (roads, stairs, bridges, docks, walkways, walls) to the infrastructure params
 * contract: the polyline in {@code points}, the {@code width}, the {@code kind}; the footprint is the corridor's bounding
 * box, {@code origin.y} the lowest walking level, {@code facing} the cardinal direction nearest the way from the first
 * point to the last.
 */
public final class Specs {
	private Specs() {
	}

	public static PlotSpec road(PlanContext ctx, Identifier archetype, int[] points, int width, RoadKind kind) {
		return road(ctx, archetype, points, width, kind, 0);
	}

	/** A road with street lamps every {@code lamps} blocks along its verge ({@code lamps} param, left out when 0). */
	public static PlotSpec road(PlanContext ctx, Identifier archetype, int[] points, int width, RoadKind kind, int lamps) {
		CompoundTag p = new CompoundTag();
		p.putIntArray("points", points);
		p.putInt("width", width);
		p.putString("kind", kind.getSerializedName());
		if (lamps > 0) {
			p.putInt("lamps", lamps);
		}
		return linear(ctx, archetype, points, width, p, minY(points), maxY(points) - minY(points) + 5);
	}

	public static PlotSpec bridge(PlanContext ctx, int[] points, int width, int deckY, int waterY, boolean boardwalk, RoadKind kind) {
		CompoundTag p = new CompoundTag();
		p.putIntArray("points", points);
		p.putInt("width", width);
		p.putInt("deck_y", deckY);
		p.putInt("water_y", waterY);
		p.putString("kind", boardwalk ? "boardwalk" : "span");
		p.putString("road_kind", kind.getSerializedName());
		int low = Math.min(minY(points), waterY);
		return linear(ctx, Archetypes.BRIDGE, points, width, p, low, Math.max(8, deckY - low + 8));
	}

	public static PlotSpec dock(PlanContext ctx, int[] points, int width) {
		CompoundTag p = new CompoundTag();
		p.putIntArray("points", points);
		p.putInt("width", width);
		return linear(ctx, Archetypes.DOCK, points, width, p, minY(points) - 1, 6);
	}

	public static PlotSpec walkway(PlanContext ctx, int[] points, int width, int deckY, int streetY) {
		CompoundTag p = new CompoundTag();
		p.putIntArray("points", points);
		p.putInt("width", width);
		p.putInt("deck_y", deckY);
		p.putInt("street_y", streetY);
		return linear(ctx, Archetypes.WALKWAY, points, width, p, streetY, deckY - streetY + 5);
	}

	public static PlotSpec wall(PlanContext ctx, Identifier archetype, int[] points, int width, int height) {
		CompoundTag p = new CompoundTag();
		p.putIntArray("points", points);
		p.putInt("width", width);
		p.putInt("height", height);
		return linear(ctx, archetype, points, width, p, minY(points), maxY(points) - minY(points) + height + 3);
	}

	private static PlotSpec linear(PlanContext ctx, Identifier archetype, int[] points, int width, CompoundTag params, int originY, int height) {
		int[] box = {Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
		Polyline.corridor(points, width, (x, z) -> {
			box[0] = Math.min(box[0], x);
			box[1] = Math.min(box[1], z);
			box[2] = Math.max(box[2], x);
			box[3] = Math.max(box[3], z);
		});
		int n = points.length / 3;
		int dx = points[(n - 1) * 3] - points[0];
		int dz = points[(n - 1) * 3 + 2] - points[2];
		Direction facing = Math.abs(dx) >= Math.abs(dz) ? (dx >= 0 ? Direction.EAST : Direction.WEST) : (dz >= 0 ? Direction.SOUTH : Direction.NORTH);
		return new PlotSpec(archetype, ctx.style, ctx.nextSeed(), new BlockPos(box[0], originY, box[1]), box[2] - box[0] + 1, box[3] - box[1] + 1,
			facing, Math.max(1, height), ctx.state.lastTier.ordinal(), params);
	}

	static int minY(int[] points) {
		int m = Integer.MAX_VALUE;
		for (int i = 1; i < points.length; i += 3) {
			m = Math.min(m, points[i]);
		}
		return m;
	}

	static int maxY(int[] points) {
		int m = Integer.MIN_VALUE;
		for (int i = 1; i < points.length; i += 3) {
			m = Math.max(m, points[i]);
		}
		return m;
	}
}
