package dev.voidmc.hearth.build.kit.craft;

import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Paint;
import dev.voidmc.hearth.build.kit.trade.Hall;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A rail along every edge a worker walks over a drop: the staging round a hull, a charging gallery, a hayloft's well,
 * a landing on a trestle. Wherever a cell one can stand on at a level has beside it a fall of four blocks or more, a
 * fence of the culture's wood goes on the edge: on a joist end laid out from the deck (so the rail stands on the deck's
 * edge, never in the air), or, where the drop is the plot's own edge, on the cell itself. Ladders, stairs, hoist doors
 * and anything else standing in the edge cell close it already; cells listed as gaps (a hoist's landing, a gangway) are
 * left open.
 */
public final class Rails {
	private Rails() {
	}

	/**
	 * Rails the edges of everything one can stand on at level y over the box [x0..x1] x [z0..z1]. Returns how many rails
	 * were set.
	 */
	public static int guard(Draft d, int x0, int z0, int x1, int z1, int y, List<int[]> gaps) {
		return guard(d, x0, z0, x1, z1, y, gaps, true);
	}

	/** {@link #guard}; {@code street}: also toward the street (the row before the plot). */
	private static int guard(Draft d, int x0, int z0, int x1, int z1, int y, List<int[]> gaps, boolean street) {
		Paint rail = Hall.woodFence(d).fence();
		int n = 0;
		try (var s = d.stage(StageKind.EXTERIOR)) {
			for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) {
				for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) {
					if (!standable(d, x, y, z) || gap(gaps, x, z)) {
						continue;
					}
					for (Direction dir : Dirs.HORIZONTAL) {
						int nx = x + Dirs.dx(dir);
						int nz = z + Dirs.dz(dir);
						if (!street && nz < 0 || gap(gaps, nx, nz) || !d.isEmpty(nx, y, nz) || !d.isEmpty(nx, y + 1, nz) || drop(d, nx, y, nz) < 4) {
							continue;
						}
						if (!d.allowed(nx, y, nz) || !d.allowed(nx, y - 1, nz)) {
							// the plot ends here: the rail goes on the edge cell itself
							d.set(x, y, z, rail);
							n++;
							break;
						}
						BlockState deck = d.get(x, y - 1, z);
						if (deck != null && deck.isCollisionShapeFullBlock(net.minecraft.world.level.EmptyBlockGetter.INSTANCE, net.minecraft.core.BlockPos.ZERO)) {
							d.set(nx, y - 1, nz, deck);
						} else {
							d.set(nx, y - 1, nz, Hall.woodFence(d).full());
						}
						d.set(nx, y, nz, rail);
						n++;
					}
				}
			}
		}
		return n;
	}

	/**
	 * Rails the yard's edges: everything one can stand on at the ground's levels (from three below the floor to two over
	 * it) beside a fall of four or more, inside the plot or over its edge where the land drops away below a levelled yard
	 * (never toward the street). Run when everything else is drawn. Returns how many rails were set.
	 */
	public static int yard(Draft d) {
		int n = 0;
		for (int y = -3; y <= 2; y++) {
			n += guard(d, 0, 0, d.frontage() - 1, d.depth() - 1, y, List.of(), false);
		}
		// a little higher (a mound's or a bank's top) only at the plot's sides and back, where the land falls away
		for (int y = 3; y <= 4; y++) {
			for (int[] r : new int[][]{{0, 0, 0, d.depth() - 1}, {d.frontage() - 1, 0, d.frontage() - 1, d.depth() - 1}, {0, d.depth() - 1, d.frontage() - 1,
				d.depth() - 1}}) {
				n += guard(d, r[0], r[1], r[2], r[3], y, List.of(), false);
			}
		}
		return n;
	}

	/**
	 * Rails the stairwell of a U-stair ({@link Hall#switchback}'s result {@code well}, from storey k up to k + 1) on the
	 * floor above: round its opening, so nobody steps off the landing's floor into it; the stair's head stays open.
	 */
	public static int well(Draft d, Hall h, int k, int[] well) {
		if (well == null || k + 1 >= h.storeys()) {
			return 0;
		}
		return guard(d, well[4] - 1, well[5] - 1, well[6] + 1, well[7] + 1, h.floorY(k + 1), List.of());
	}

	/** Someone can stand at (x, y, z): something under it, and the cell and the one over it empty. */
	public static boolean standable(Draft d, int x, int y, int z) {
		BlockState under = d.get(x, y - 1, z);
		return under != null && !under.isAir() && !(under.getBlock() instanceof net.minecraft.world.level.block.FenceBlock)
			&& !(under.getBlock() instanceof net.minecraft.world.level.block.WallBlock) && d.isEmpty(x, y, z) && d.isEmpty(x, y + 1, z)
			&& !under.getCollisionShape(net.minecraft.world.level.EmptyBlockGetter.INSTANCE, net.minecraft.core.BlockPos.ZERO).isEmpty();
	}

	/** How far one stepping off into (x, y, z) falls: empty cells under it down to something solid or the ground. */
	public static int drop(Draft d, int x, int y, int z) {
		// (the site knows the ground a few blocks round the plot too)
		int g = d.site().ground(x, z);
		int n = 0;
		for (int yy = y - 1; yy >= y - 12; yy--) {
			BlockState s = d.get(x, yy, z);
			if (s == null ? yy < g : !s.isAir()) {
				break;
			}
			n++;
		}
		return n;
	}

	private static boolean gap(List<int[]> gaps, int x, int z) {
		for (int[] g : gaps) {
			if (g[0] == x && g[1] == z) {
				return true;
			}
		}
		return false;
	}
}
