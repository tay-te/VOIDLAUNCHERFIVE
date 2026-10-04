package dev.voidmc.hearth.build.kit.homestead;

import dev.voidmc.hearth.api.PoiType;
import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Mat;
import dev.voidmc.hearth.build.draft.Noise;
import dev.voidmc.hearth.build.draft.States;
import dev.voidmc.hearth.build.style.Form;
import dev.voidmc.hearth.build.style.Habits;
import dev.voidmc.hearth.build.style.Role;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * The furniture of a farmyard: a water trough, a draw-well, a chopping block, a mounting block, a dung heap. Each sits on
 * the ground at the standing level it is given and keeps its water in.
 */
public final class Yard {
	private Yard() {
	}

	/** Stone in walled cultures, wood elsewhere: what troughs and well-heads are made of. */
	private static Mat trim(Draft d) {
		return d.habits().yard().fence() == Habits.FenceKind.WALL ? d.mat(Role.FOUNDATION) : d.mat(Role.FURNITURE);
	}

	/**
	 * A water trough {@code len} long along {@code run} from (x, z), standing on the ground at level y: water held between
	 * a rim of slabs on its long sides and a stair at each end.
	 */
	public static void trough(Draft d, int x, int y, int z, Direction run, int len) {
		Mat rim = trim(d);
		Direction across = Dirs.right(run);
		try (var s = d.stage(StageKind.EXTERIOR)) {
			for (int i = 0; i < len; i++) {
				int px = x + Dirs.dx(run) * i;
				int pz = z + Dirs.dz(run) * i;
				solidUnder(d, px, y, pz);
				d.set(px, y, pz, Blocks.WATER.defaultBlockState());
				for (int side : new int[]{-1, 1}) {
					int rx = px + Dirs.dx(across) * side;
					int rz = pz + Dirs.dz(across) * side;
					if (inPlot(d, rx, rz)) {
						solidUnder(d, rx, y, rz);
						d.set(rx, y, rz, rim.has(Form.SLAB) ? rim.slab(SlabType.BOTTOM) : rim.full());
					}
				}
			}
			for (int end : new int[]{-1, len}) {
				int ex = x + Dirs.dx(run) * end;
				int ez = z + Dirs.dz(run) * end;
				if (inPlot(d, ex, ez)) {
					solidUnder(d, ex, y, ez);
					Direction face = end < 0 ? run : run.getOpposite();
					d.set(ex, y, ez, rim.stairsOrSlab(face.getOpposite(), Half.BOTTOM));
				}
			}
		}
	}

	private static void solidUnder(Draft d, int x, int y, int z) {
		var below = d.get(x, y - 1, z);
		if (below == null && y - 1 >= d.site().ground(x, z) || below != null && !d.isSolid(x, y - 1, z)) {
			d.set(x, y - 1, z, d.mat(Role.PAVING).full());
		}
	}

	private static boolean inPlot(Draft d, int x, int z) {
		return x >= 0 && z >= 0 && x < d.frontage() && z < d.depth();
	}

	/**
	 * A draw-well over a 3 × 3 square centred on (cx, cz): a shaft of water two deep inside a stone kerb, two posts and a
	 * crossbar with the bucket's chain, a little roof of slabs. Registers a meeting point beside it.
	 */
	public static void well(Draft d, int cx, int y, int cz, Direction front) {
		Mat stone = d.mat(Role.FOUNDATION);
		Mat post = d.mat(Role.FRAME);
		Mat roof = d.mat(Role.ROOF);
		Direction across = Dirs.right(front);
		try (var s = d.stage(StageKind.EARTHWORK)) {
			for (int k = 1; k <= 3; k++) {
				d.set(cx, y - k, cz, k == 3 ? Blocks.MUD.defaultBlockState() : Blocks.WATER.defaultBlockState());
			}
		}
		try (var s = d.stage(StageKind.FOUNDATION)) {
			for (int dz = -1; dz <= 1; dz++) {
				for (int dx = -1; dx <= 1; dx++) {
					if (dx == 0 && dz == 0) {
						continue;
					}
					int x = cx + dx;
					int z = cz + dz;
					int g = d.site().ground(x, z);
					for (int yy = Math.min(g - 1, y - 3); yy < y; yy++) {
						d.set(x, yy, z, stone.full());
					}
					d.set(x, y, z, stone.has(Form.WALL) && (dx == 0 || dz == 0) ? stone.wall() : stone.full());
				}
			}
		}
		try (var s = d.stage(StageKind.FRAME)) {
			for (int side : new int[]{-1, 1}) {
				int px = cx + Dirs.dx(across) * side;
				int pz = cz + Dirs.dz(across) * side;
				d.set(px, y, pz, stone.full());
				d.set(px, y + 1, pz, post.fence().orElse(post.log(Direction.Axis.Y)));
				d.set(px, y + 2, pz, post.fence().orElse(post.log(Direction.Axis.Y)));
				d.set(px, y + 3, pz, post.log(across.getAxis()));
			}
			d.set(cx, y + 3, cz, post.log(across.getAxis()));
			d.set(cx, y + 2, cz, States.axis(Blocks.IRON_CHAIN, Direction.Axis.Y));
		}
		try (var s = d.stage(StageKind.ROOF)) {
			for (int k = -1; k <= 1; k++) {
				for (int side = -1; side <= 1; side++) {
					int x = cx + Dirs.dx(across) * side + Dirs.dx(front) * k;
					int z = cz + Dirs.dz(across) * side + Dirs.dz(front) * k;
					if (!inPlot(d, x, z)) {
						continue;
					}
					if (k == 0) {
						d.setIfAbsent(x, y + 4, z, roof.slab(SlabType.BOTTOM));
					} else {
						d.setIfAbsent(x, y + 3, z, roof.stairsOrSlab(k > 0 ? front.getOpposite() : front, Half.BOTTOM));
					}
				}
			}
		}
		d.poi(PoiType.MEETING_POINT, cx + Dirs.dx(front) * 2, y, cz + Dirs.dz(front) * 2, front.getOpposite());
	}

	/** A chopping block (an upright stripped log) with a few split logs lying by it. */
	public static void choppingBlock(Draft d, int x, int y, int z, Direction toward) {
		Mat log = d.mat(Role.BEAM);
		try (var s = d.stage(StageKind.EXTERIOR)) {
			d.set(x, y, z, log.strippedLog(Direction.Axis.Y).orElse(log.log(Direction.Axis.Y)));
			int lx = x + Dirs.dx(toward);
			int lz = z + Dirs.dz(toward);
			if (inPlot(d, lx, lz) && d.isAbsent(lx, y, lz)) {
				d.set(lx, y, lz, log.slab(SlabType.BOTTOM).orElse(log.log(Dirs.right(toward).getAxis())));
			}
		}
	}

	/** A dung heap: coarse earth and a composter or two, against a wall. */
	public static void dungHeap(Draft d, int x, int y, int z, Direction run) {
		try (var s = d.stage(StageKind.EXTERIOR)) {
			for (int i = 0; i < 2; i++) {
				int px = x + Dirs.dx(run) * i;
				int pz = z + Dirs.dz(run) * i;
				d.set(px, y - 1, pz, Blocks.COARSE_DIRT.defaultBlockState());
				if (d.isAbsent(px, y, pz)) {
					d.set(px, y, pz, i == 0 ? Blocks.COMPOSTER.defaultBlockState().setValue(
						net.minecraft.world.level.block.state.properties.BlockStateProperties.LEVEL_COMPOSTER, 7) : Blocks.PODZOL.defaultBlockState());
				}
			}
		}
	}

	/** A mounting block or a bench by a door: steps of stone (or a bench of joinery). */
	public static void bench(Draft d, int x, int y, int z, Direction back) {
		try (var s = d.stage(StageKind.EXTERIOR)) {
			d.set(x, y, z, d.mat(Role.FURNITURE).stairsOrSlab(back, Half.BOTTOM));
		}
		d.poi(PoiType.SEAT, x, y, z, back.getOpposite());
	}

	/** Whether a noise roll at a cell falls under p (deterministic per building). */
	public static boolean roll(Draft d, int x, int z, long salt, double p) {
		return Noise.unit(d.seed() ^ salt, x, 0, z) < p;
	}
}
