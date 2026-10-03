package dev.voidmc.hearth.build.kit.trade;

import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Mat;
import dev.voidmc.hearth.build.draft.Noise;
import dev.voidmc.hearth.build.kit.Garden;
import dev.voidmc.hearth.build.style.Form;
import dev.voidmc.hearth.build.style.Habits;
import dev.voidmc.hearth.build.style.Role;
import dev.voidmc.hearth.build.terrain.Groundwork;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Working ground: yards trodden to packed earth, gravel and straw in patches (sand and sandstone in the desert,
 * limestone chips in the karst), a paved apron where carts stand, and the enclosure round a yard (fence, dry-stone or
 * mud wall, hedge, by the culture) with its gates left open to the street.
 */
public final class Yard {
	private Yard() {
	}

	/**
	 * Registers the main entrance on the street row where the street meets the floor: the middle of the frontage, where
	 * the planner levels the floor to the street (of the two middle columns, the one whose street stands nearest the
	 * floor; then any column that meets it, nearest the middle first). Returns its column.
	 */
	public static int entrance(Draft d) {
		int w = d.frontage();
		int best = w / 2;
		int bestMiss = Math.abs(street(d, best));
		if (bestMiss > 0 && Math.abs(street(d, (w - 1) / 2)) < bestMiss) {
			best = (w - 1) / 2;
			bestMiss = Math.abs(street(d, best));
		}
		// neither middle column meets the floor: the nearest column that does, else the nearest miss
		for (int k = 1; k < w && bestMiss > 0; k++) {
			for (int x : new int[]{w / 2 - k, w / 2 + k}) {
				if (x < 0 || x >= w) {
					continue;
				}
				int miss = Math.abs(street(d, x));
				if (miss < bestMiss) {
					bestMiss = miss;
					best = x;
				}
			}
		}
		d.entrance(best, 0, -1);
		return best;
	}

	/**
	 * Keeps the way from the street to a door open: along the front row from the street entrance's column {@code ex} to
	 * the door's column, then in to the cell before the door at depth {@code doorZ} (standing level 0). Earthwork that
	 * stands in the way (a dyke against high water, a retaining course) is taken out and the way floored with paving
	 * where it would be water or air.
	 */
	public static void connect(Draft d, int ex, int doorX, int doorZ) {
		Mat paving = d.mat(Role.PAVING);
		List<int[]> cells = new ArrayList<>();
		for (int x = Math.min(ex, doorX); x <= Math.max(ex, doorX); x++) {
			cells.add(new int[]{x, 0});
		}
		for (int z = 1; z <= doorZ; z++) {
			cells.add(new int[]{doorX, z});
		}
		try (var s = d.stage(StageKind.EXTERIOR)) {
			for (int[] c : cells) {
				if (!Shed.Walls2.inPlot(d, c[0], c[1])) {
					continue;
				}
				for (int y = 0; y <= 1; y++) {
					Draft.Cell cell = d.cell(c[0], y, c[1]);
					if (cell != null && !cell.state().isAir() && (cell.stage() == StageKind.EARTHWORK || cell.stage() == StageKind.FOUNDATION)) {
						d.set(c[0], y, c[1], Blocks.AIR.defaultBlockState());
					}
				}
				BlockState below = d.get(c[0], -1, c[1]);
				boolean open = below == null ? d.site().ground(c[0], c[1]) < 0 || d.site().isWater(c[0], c[1]) : below.isAir() || below.is(Blocks.WATER);
				if (open && d.isEmpty(c[0], 0, c[1])) {
					d.set(c[0], -1, c[1], paving.full());
					int g = d.site().ground(c[0], c[1]);
					for (int y = g - 1; y < -1; y++) {
						if (d.isEmpty(c[0], y, c[1]) || d.get(c[0], y, c[1]) != null && d.get(c[0], y, c[1]).is(Blocks.WATER)) {
							d.set(c[0], y, c[1], d.mat(Role.FOUNDATION).full());
						}
					}
				}
			}
		}
	}

	/** The street's standing level in front of column x, relative to the floor (on the water where it is wet). */
	public static int street(Draft d, int x) {
		int wy = d.site().water(x, -1);
		return Math.max(d.site().ground(x, -1), wy == dev.voidmc.hearth.build.terrain.Site.NONE ? Integer.MIN_VALUE : wy);
	}

	/** The ground blocks a worked yard is trodden into, by culture: earth, gravel and mud, or sand and stone. */
	public static Block[] trodden(Draft d) {
		Habits.GardenKind g = d.habits().yard().garden();
		Block ground = d.mat(Role.GROUND).mainOr(Form.FULL, Blocks.GRASS_BLOCK);
		if (ground == Blocks.SAND) {
			return new Block[]{Blocks.SAND, Blocks.SANDSTONE, Blocks.SMOOTH_SANDSTONE, Blocks.SAND};
		}
		if (ground == Blocks.RED_SAND) {
			return new Block[]{Blocks.RED_SAND, Blocks.COARSE_DIRT, Blocks.PACKED_MUD, Blocks.RED_SAND};
		}
		if (g == Habits.GardenKind.ZEN) {
			return new Block[]{Blocks.GRAVEL, Blocks.COARSE_DIRT, Blocks.DIRT_PATH, Blocks.GRAVEL};
		}
		if (d.habits().snow()) {
			return new Block[]{Blocks.COARSE_DIRT, Blocks.GRAVEL, Blocks.SNOW_BLOCK, Blocks.DIRT_PATH};
		}
		return new Block[]{Blocks.COARSE_DIRT, Blocks.DIRT_PATH, Blocks.GRAVEL, Blocks.PACKED_MUD};
	}

	/**
	 * A trodden yard surface over a rectangle at standing level y: the ground row (y - 1) laid in patches of the
	 * culture's trodden ground wherever it is ground-like (natural, levelled or filled), never over water or anything
	 * built.
	 */
	public static void trodden(Draft d, int x0, int z0, int x1, int z1, int y) {
		Block[] mix = trodden(d);
		try (var s = d.stage(StageKind.EXTERIOR)) {
			for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) {
				for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) {
					if (!Shed.Walls2.inPlot(d, x, z) || d.site().isWater(x, z) && !d.drained(x, z)) {
						continue;
					}
					if (!d.isEmpty(x, y, z) || !Garden.groundLike(d, x, y - 1, z) && !(d.isAbsent(x, y - 1, z) && d.site().ground(x, z) == y)) {
						continue;
					}
					double u = Noise.value(d.seed() ^ 0x7A2D, x / 2.5, 0, z / 2.5);
					int i = u < 0.35 ? 0 : u < 0.55 ? 1 : u < 0.72 ? 2 : 3;
					d.set(x, y - 1, z, mix[i].defaultBlockState());
				}
			}
		}
	}

	/** Paving of the style at row y - 1 over cells (a cart apron, a threshold, a work floor), over ground only. */
	public static void pave(Draft d, int x0, int z0, int x1, int z1, int y) {
		Mat paving = d.mat(Role.PAVING);
		try (var s = d.stage(StageKind.EXTERIOR)) {
			for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) {
				for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) {
					if (!Shed.Walls2.inPlot(d, x, z) || !d.isEmpty(x, y, z)) {
						continue;
					}
					BlockState below = d.get(x, y - 1, z);
					if (below != null && !below.isAir() && !Garden.groundLike(d, x, y - 1, z)) {
						continue;
					}
					d.set(x, y - 1, z, paving.full());
				}
			}
		}
	}

	/**
	 * The yard levelled to standing level y (water inside embanked and filled) — the usual footing for a workplace whose
	 * floor meets the street.
	 */
	public static void level(Draft d, int x0, int z0, int x1, int z1, int y) {
		Groundwork.level(d, x0, z0, x1, z1, y, true);
	}

	/**
	 * Encloses a rectangle (fence, wall or hedge by the culture's habit; a wall for walled cultures), following the
	 * ground, leaving open every cell listed in {@code gaps} (each {x, z}) and every cell already built on. Sides
	 * listed in {@code skip} are left open (where a building stands).
	 */
	public static void enclose(Draft d, int x0, int z0, int x1, int z1, List<int[]> gaps, Direction... skip) {
		List<int[]> line = new ArrayList<>();
		for (int[] c : Draft.ring(x0, z0, x1, z1)) {
			boolean open = false;
			for (int[] g : gaps) {
				open |= g[0] == c[0] && g[1] == c[1];
			}
			for (Direction s : skip) {
				open |= s == Dirs.FRONT && c[1] == Math.min(z0, z1) || s == Dirs.BACK && c[1] == Math.max(z0, z1)
					|| s == Dirs.LEFT && c[0] == Math.min(x0, x1) || s == Dirs.RIGHT && c[0] == Math.max(x0, x1);
			}
			if (!open) {
				line.add(c);
			}
		}
		Garden.fence(d, line, -1, Dirs.FRONT);
	}

	/** Whether the culture bounds its yards with walls (desert, mesa, mediterranean, moorland, maritime). */
	public static boolean walled(Draft d) {
		return d.habits().yard().fence() == Habits.FenceKind.WALL;
	}
}
