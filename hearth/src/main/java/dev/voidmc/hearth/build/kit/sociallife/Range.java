package dev.voidmc.hearth.build.kit.sociallife;

import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.kit.Body;
import dev.voidmc.hearth.build.kit.Chimneys;
import dev.voidmc.hearth.build.kit.Furniture;
import dev.voidmc.hearth.build.kit.Stairs;
import dev.voidmc.hearth.build.kit.civic.Grounds;
import dev.voidmc.hearth.build.kit.civic.Tradition;
import dev.voidmc.hearth.build.kit.light.Lighting;
import dev.voidmc.hearth.build.kit.trade.Floorplan;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * A charitable house of two storeys (an almshouse, an orphanage): a plain range across the front of the plot with its
 * ridge along the street, the common room below (the door off the middle, the stair along the back wall from the end
 * away from it) and the sleeping floor above. Draws the shell and the stair and hands the archetype both floors as
 * hand-furnished plans, the door, the landing and the stair foot kept.
 */
public final class Range {
	private Range() {
	}

	/** What a range drew: its body and shell, both floors' plans, the interior rectangle and the stair's end. */
	public record Built(Body body, Shell.Built shell, Floorplan ground, Floorplan upper, int base, int zF, int zM, int ix0, int iz0, int ix1, int iz1,
		boolean stairLeft, int[] heights) {
		/** The interior's middle row. */
		public int midZ() {
			return (this.iz0 + this.iz1) / 2;
		}
	}

	/**
	 * Builds the range over the front {@code depth} rows (after a forecourt) of the plot: two storeys of four where the
	 * height allows (an upper storey of three under a low ceiling where it does not); a masonry ground storey under timber
	 * where the culture builds so ({@code stoneBase}).
	 */
	public static Built build(Draft d, int depth, boolean stoneBase, double density) {
		return build(d, depth, stoneBase, density, -1);
	}

	/**
	 * {@link #build(Draft, int, boolean, double)} with the range's front wall in row {@code at} (an almshouse's range across
	 * the back of its court, the archetype laying the court out), or after the forecourt where {@code at} is negative or
	 * the house stands on posts.
	 */
	public static Built build(Draft d, int depth, boolean stoneBase, double density, int at) {
		int w = d.frontage();
		int dp = d.depth();
		Tradition t = Tradition.of(d);
		boolean raised = t.raised();
		boolean placed = at >= 0 && !raised;
		int md = placed ? Math.min(depth, dp - at) : Math.min(depth, dp - 2);
		int zF = raised ? Math.max(2, Math.min(dp - md, 1 + Grounds.stiltBase(d, 0, 1, w - 1, md))) : placed ? at : 2;
		int zM = Math.min(dp - 1, zF + md - 1);
		int base = raised ? Grounds.stiltBase(d, 0, zF, w - 1, zM) : 0;
		int[] heights = d.maxHeight() - base >= 10 ? new int[]{4, 4} : new int[]{4, 3};
		Body body = new Body(0, zF, w - 1, zM, base, heights, false, false);
		d.body(0, zF, w - 1, zM);
		d.eave(body.plateY());
		int doorCol = d.chance(0.5) ? w / 2 - 1 : w / 2 + 1;
		if (!placed) {
			Grounds.forecourt(d, 0, w - 1, zF - 1, !raised);
		}
		Shell.Built sh = Shell.build(d, body, Direction.Axis.X, Dirs.FRONT, doorCol, 1, density, stoneBase, t.masonry(), false);
		doorCol = sh.doorCol();
		int ix0 = 1;
		int ix1 = w - 2;
		int iz0 = zF + 1;
		int iz1 = zM - 1;

		// the stair along the back wall from the end away from the door, climbing toward it
		boolean stairLeft = doorCol > w / 2;
		Direction climb = stairLeft ? Dirs.RIGHT : Dirs.LEFT;
		int sx0 = stairLeft ? ix0 : ix1;
		int h0 = heights[0];
		Stairs.straight(d, sx0, base, iz1, climb, h0, 1);
		int fy = base + h0;
		try (var s = d.stage(StageKind.FLOORS)) {
			for (int k = 0; k < h0 - 1; k++) {
				d.set(sx0 + Dirs.dx(climb) * k, fy - 1, iz1, Blocks.AIR.defaultBlockState());
			}
		}
		Floorplan ground = new Floorplan(ix0, iz0, ix1, iz1, base, h0 - 1);
		ground.keep(sh.inside()[0], sh.inside()[1]);
		for (int k = 0; k < h0; k++) {
			ground.block(sx0 + Dirs.dx(climb) * k, iz1);
		}
		ground.keep(sx0 + Dirs.dx(climb) * h0, iz1);
		ground.keep(sx0, iz1 - 1);

		int[] up = body.rect(1);
		Floorplan upper = new Floorplan(up[0] + 1, up[1] + 1, up[2] - 1, up[3] - 1, fy, heights[1] - 1);
		upper.keep(sx0 + Dirs.dx(climb) * h0, iz1);
		for (int k = 0; k < h0 - 1; k++) {
			upper.block(sx0 + Dirs.dx(climb) * k, iz1);
		}
		upper.blockHoles(d);
		return new Built(body, sh, ground, upper, base, zF, zM, ix0, iz0, ix1, iz1, stairLeft, heights);
	}

	/**
	 * The kitchen hearth on the end wall of the common room across its middle (the end at {@code hx}, the fire facing
	 * {@code face}): the fire, its stack through the roof, sconces either side, a cauldron of water and a chest of the
	 * house's stores beside it. Answers whether it was built.
	 */
	public static boolean hearth(Draft d, Built r, Floorplan plan, int hx, Direction face, List<Object[]> stores) {
		int hz = r.midZ();
		int y = r.base();
		if (!plan.free(hx, hz) || !plan.inside(hx + Dirs.dx(face), hz) || !plan.take(hx, hz, face)) {
			return false;
		}
		Furniture.hearth(d, hx, y, hz, face, true);
		if (r.shell().roof() != null) {
			Chimneys.stack(d, hx - Dirs.dx(face), hz, y + 2, r.shell().roof(), r.shell().roof().ridgeY + 1);
		}
		Lighting.flank(d, hx + Dirs.dx(face), y + 2, hz, face.getOpposite(), 1);
		int bz = hz + 1;
		if (plan.free(hx, bz) && plan.take(hx, bz, face)) {
			d.inStage(StageKind.INTERIOR, () -> d.set(hx, y, bz, Blocks.WATER_CAULDRON.defaultBlockState().setValue(BlockStateProperties.LEVEL_CAULDRON, 3)));
		}
		int cz = hz - 1;
		if (plan.free(hx, cz) && plan.take(hx, cz, face)) {
			Furniture.chest(d, hx, y, cz, face, stores);
		}
		return true;
	}

	/**
	 * Long tables down the room between x0 and x1 (a trestle of {@code n} with benches either side), as many as fit up
	 * to {@code max}, a candle stand on each. Answers how many.
	 */
	public static int tables(Draft d, Floorplan plan, int x0, int x1, int n, int max) {
		int placed = 0;
		for (int z = plan.z0 + 1; z <= plan.z1 - 1 && placed < max; z++) {
			for (int x = x0 + 1; x + n <= x1 - 1; x++) {
				int[][] cells = new int[n * 3][];
				int k = 0;
				for (int i = 0; i < n; i++) {
					cells[k++] = new int[]{x + i, z};
					cells[k++] = new int[]{x + i, z - 1};
					cells[k++] = new int[]{x + i, z + 1};
				}
				boolean ok = true;
				for (int[] c : cells) {
					ok &= plan.free(c[0], c[1]);
				}
				int[] end = {x + n, z};
				if (!ok || !plan.free(end[0], end[1]) || !plan.take(cells, end)) {
					continue;
				}
				Venue.trestle(d, x, plan.y, z, Dirs.RIGHT, n);
				Venue.candles(d, x + n / 2, plan.y + 1, z, 2);
				placed++;
				z += 3;
				break;
			}
		}
		return placed;
	}

	/** A bench of two against a wall of the room wherever it fits (for a room too small for its tables). Answers whether. */
	public static boolean wallBench(Draft d, Floorplan plan) {
		for (Direction wall : Dirs.HORIZONTAL) {
			List<int[]> run = plan.along(wall);
			for (int i = 0; i + 1 < run.size(); i++) {
				int[] a = run.get(i);
				int[] b = run.get(i + 1);
				int[] fa = {a[0] - Dirs.dx(wall), a[1] - Dirs.dz(wall)};
				if (!plan.free(a[0], a[1]) || !plan.free(b[0], b[1]) || !plan.inside(fa[0], fa[1]) || !plan.take(new int[][]{a, b}, fa)) {
					continue;
				}
				Direction along = Dirs.of(b[0] - a[0], b[1] - a[1]);
				Venue.bench(d, a[0], plan.y, a[1], along, 2, wall);
				return true;
			}
		}
		return false;
	}
}
