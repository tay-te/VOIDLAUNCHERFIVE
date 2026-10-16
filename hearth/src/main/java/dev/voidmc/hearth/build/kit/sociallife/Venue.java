package dev.voidmc.hearth.build.kit.sociallife;

import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.PoiType;
import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Mat;
import dev.voidmc.hearth.build.draft.Noise;
import dev.voidmc.hearth.build.draft.States;
import dev.voidmc.hearth.build.kit.civic.Signs;
import dev.voidmc.hearth.build.kit.light.Lighting;
import dev.voidmc.hearth.build.style.Form;
import dev.voidmc.hearth.build.style.Role;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * Where folk gather to be entertained: a stage for the musicians and players (a boarded platform with a moulded lip, a
 * step up at one end, the culture's cloth hung behind it, a drum and a stool), the dice tables of the social module
 * (green baize on a dark frame, a stool at each end), and benches. Every piece registers what the social module looks
 * for: {@code STAGE} and the entertainer's job site on a stage, {@code GAMING_TABLE} at a dice table, {@code SEAT}s.
 */
public final class Venue {
	private Venue() {
	}

	/**
	 * A stage over {@code [x0..x1] × [z0..z1]} on the floor at standing level {@code y} (the boards at y, the players
	 * stand at y + 1), its front toward {@code toAudience}: the front edge in upside-down stairs (a moulded lip), a step
	 * up at the front's left end, wall banners in the culture's cloth on the wall behind where there is one, a drum (a
	 * note block, a candle on it) and a stool at the back corners. Registers {@code STAGE} at the middle of the front row
	 * and the entertainer's job site beside it. Answers the standing cell of the STAGE point.
	 */
	public static int[] stage(Draft d, int x0, int z0, int x1, int z1, int y, Direction toAudience, boolean dress) {
		int ax = Math.min(x0, x1);
		int bx = Math.max(x0, x1);
		int az = Math.min(z0, z1);
		int bz = Math.max(z0, z1);
		Mat boards = d.mat(Role.FLOOR);
		Mat wood = d.mat(Role.FURNITURE);
		Direction back = toAudience.getOpposite();
		try (var s = d.stage(StageKind.INTERIOR)) {
			for (int z = az; z <= bz; z++) {
				for (int x = ax; x <= bx; x++) {
					boolean front = isEdge(x, z, ax, az, bx, bz, toAudience);
					d.set(x, y, z, front ? boards.stairsOrSlab(back, Half.TOP) : boards.full());
				}
			}
		}
		// the step up at the front's left end (seen from the audience), on the floor in front of the lip
		Direction along = Dirs.right(back);
		int[] step = stepCell(ax, az, bx, bz, toAudience);
		int sx = step[0];
		int sz = step[1];
		if (d.isEmpty(sx, y, sz) && floor(d, sx, y, sz)) {
			d.inStage(StageKind.INTERIOR, () -> d.set(sx, y, sz, wood.stairsOrSlab(back, Half.BOTTOM)));
		}
		// the players' places: the middle of the front row and the cell behind it
		int cx = (ax + bx) / 2;
		int cz = (az + bz) / 2;
		int[] spot = frontMiddle(ax, az, bx, bz, toAudience);
		d.poi(PoiType.STAGE, spot[0], y + 1, spot[1], toAudience);
		int jx = spot[0] - Dirs.dx(toAudience);
		int jz = spot[1] - Dirs.dz(toAudience);
		if (jx < ax || jx > bx || jz < az || jz > bz) {
			jx = spot[0] + Dirs.dx(along);
			jz = spot[1] + Dirs.dz(along);
		}
		if (jx >= ax && jx <= bx && jz >= az && jz <= bz) {
			d.poi(PoiType.JOB_SITE, jx, y + 1, jz, toAudience, JobType.ENTERTAINER);
		} else {
			d.poi(PoiType.JOB_SITE, spot[0], y + 1, spot[1], toAudience, JobType.ENTERTAINER);
		}
		if (!dress) {
			return new int[]{spot[0], y + 1, spot[1]};
		}
		// a drum and a stool at the back corners, where they leave the front free
		int[] bl = backCorner(ax, az, bx, bz, toAudience, true);
		int[] br = backCorner(ax, az, bx, bz, toAudience, false);
		boolean deep = toAudience.getAxis() == Direction.Axis.Z ? bz - az >= 1 : bx - ax >= 1;
		boolean wide = toAudience.getAxis() == Direction.Axis.Z ? bx - ax >= 2 : bz - az >= 2;
		if (deep && wide) {
			try (var s = d.stage(StageKind.INTERIOR)) {
				if (d.isEmpty(bl[0], y + 1, bl[1]) && d.isEmpty(bl[0], y + 2, bl[1])) {
					d.set(bl[0], y + 1, bl[1], Blocks.NOTE_BLOCK.defaultBlockState().trySetValue(
						net.minecraft.world.level.block.state.properties.BlockStateProperties.NOTEBLOCK_INSTRUMENT, NoteBlockInstrument.BASEDRUM));
					// a candle stood on the drum (nobody climbs onto it)
					d.set(bl[0], y + 2, bl[1], Blocks.CANDLE.defaultBlockState().setValue(
						net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT, true));
				}
				if (d.isEmpty(br[0], y + 1, br[1])) {
					d.set(br[0], y + 1, br[1], wood.slab(SlabType.BOTTOM));
				}
			}
			if (d.isEmpty(br[0], y + 2, br[1])) {
				d.poi(PoiType.SEAT, br[0], y + 1, br[1], toAudience);
			}
		}
		// the cloth behind: a banner on the wall above each back cell, in turn the culture's colours
		List<String> colours = Signs.clothColours(d);
		int k = 0;
		for (int[] c : backRow(ax, az, bx, bz, toAudience)) {
			int wx = c[0] + Dirs.dx(back);
			int wz = c[1] + Dirs.dz(back);
			int by = y + 3;
			if (d.isSolid(wx, by, wz) && d.isEmpty(c[0], by, c[1]) && d.isEmpty(c[0], by - 1, c[1])) {
				String colour = colours.isEmpty() ? "red" : colours.get(k % colours.size());
				banner(d, c[0], by, c[1], toAudience, colour, List.<String[]>of(new String[]{"minecraft:border", colours.size() > 1
					? colours.get((k + 1) % colours.size()) : "white"}), StageKind.INTERIOR);
			}
			k++;
		}
		return new int[]{spot[0], y + 1, spot[1]};
	}

	/**
	 * A dice table: two cells from (x, y, z) along {@code run}, each a dark post under a green baize top, a stool (a slab)
	 * at either end where there is room. Registers {@code GAMING_TABLE} at the first cell and a SEAT at each stool.
	 */
	public static void gamingTable(Draft d, int x, int y, int z, Direction run) {
		Mat wood = d.mat(Role.FURNITURE);
		Block post = wood.has(Form.FENCE) ? null : Blocks.DARK_OAK_FENCE;
		int bx = x + Dirs.dx(run);
		int bz = z + Dirs.dz(run);
		try (var s = d.stage(StageKind.INTERIOR)) {
			for (int[] c : new int[][]{{x, z}, {bx, bz}}) {
				d.set(c[0], y, c[1], post != null ? States.of(post) : States.of(Blocks.DARK_OAK_FENCE));
				d.set(c[0], y + 1, c[1], Blocks.CARPET.pick(net.minecraft.world.item.DyeColor.GREEN).defaultBlockState());
			}
		}
		d.poi(PoiType.GAMING_TABLE, x, y, z, Dirs.right(run));
		for (int[] e : new int[][]{{x - Dirs.dx(run), z - Dirs.dz(run), 1}, {bx + Dirs.dx(run), bz + Dirs.dz(run), 0}}) {
			if (standable(d, e[0], y, e[1])) {
				d.inStage(StageKind.INTERIOR, () -> d.set(e[0], y, e[1], wood.slab(SlabType.BOTTOM)));
				d.poi(PoiType.SEAT, e[0], y, e[1], e[2] == 1 ? run : run.getOpposite());
			}
		}
	}

	/** The cells a dice table and its stools take, from (x, z) along {@code run}: stool, table, table, stool. */
	public static int[][] gamingCells(int x, int z, Direction run) {
		int dx = Dirs.dx(run);
		int dz = Dirs.dz(run);
		return new int[][]{{x - dx, z - dz}, {x, z}, {x + dx, z + dz}, {x + 2 * dx, z + 2 * dz}};
	}

	/**
	 * A bench of {@code n} seats from (x, y, z) along {@code run}, the sitters' backs toward {@code back} (stairs of the
	 * furniture wood), each a SEAT.
	 */
	public static void bench(Draft d, int x, int y, int z, Direction run, int n, Direction back) {
		Mat wood = d.mat(Role.FURNITURE);
		for (int i = 0; i < n; i++) {
			int px = x + Dirs.dx(run) * i;
			int pz = z + Dirs.dz(run) * i;
			if (!d.isEmpty(px, y, pz)) {
				continue;
			}
			d.inStage(StageKind.INTERIOR, () -> d.set(px, y, pz, wood.stairsOrSlab(back, Half.BOTTOM)));
			d.poi(PoiType.SEAT, px, y, pz, back.getOpposite());
		}
	}

	/**
	 * A trestle table and its benches: a slab-topped board {@code n} long from (x, y, z) along {@code run} on two
	 * trestles, a bench of stairs along each long side where the floor is free. Registers a TABLE and the seats.
	 */
	public static void trestle(Draft d, int x, int y, int z, Direction run, int n) {
		Mat wood = d.mat(Role.FURNITURE);
		Direction side = Dirs.right(run);
		try (var s = d.stage(StageKind.INTERIOR)) {
			for (int i = 0; i < n; i++) {
				int px = x + Dirs.dx(run) * i;
				int pz = z + Dirs.dz(run) * i;
				d.set(px, y, pz, i == 0 || i == n - 1 ? wood.fence() : wood.slab(SlabType.TOP));
				if (i == 0 || i == n - 1) {
					d.set(px, y + 1, pz, wood.plate());
				}
			}
		}
		d.poi(PoiType.TABLE, x + Dirs.dx(run) * (n / 2), y, z + Dirs.dz(run) * (n / 2), side);
		for (Direction sd : new Direction[]{side, side.getOpposite()}) {
			for (int i = 0; i < n; i++) {
				int px = x + Dirs.dx(run) * i + Dirs.dx(sd);
				int pz = z + Dirs.dz(run) * i + Dirs.dz(sd);
				if (standable(d, px, y, pz)) {
					d.inStage(StageKind.INTERIOR, () -> d.set(px, y, pz, wood.stairsOrSlab(sd, Half.BOTTOM)));
					d.poi(PoiType.SEAT, px, y, pz, sd.getOpposite());
				}
			}
		}
	}

	/**
	 * A wall banner at (x, y, z) hung on the block behind it (opposite {@code facing}) in the given stage (inside a room
	 * it is an interior fitting).
	 */
	public static void banner(Draft d, int x, int y, int z, Direction facing, String colour, List<String[]> patterns, StageKind stage) {
		Block banner = States.block("minecraft:" + colour + "_wall_banner", States.block("minecraft:white_wall_banner", Blocks.AIR));
		if (banner == Blocks.AIR) {
			return;
		}
		var st = banner.defaultBlockState().trySetValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING, facing);
		d.inStage(stage, () -> d.set(x, y, z, st, dev.voidmc.hearth.build.draft.BlockEntities.banner(patterns)));
	}

	/** Something to stand on under (x, y, z): a drawn solid block, or the natural ground left as it is at that level. */
	public static boolean floor(Draft d, int x, int y, int z) {
		return d.isSolid(x, y - 1, z) || d.isAbsent(x, y - 1, z) && d.site().ground(x, z) == y && !d.site().isWater(x, z);
	}

	/** A free cell one can stand in: nothing drawn at feet or head, something to stand on. */
	public static boolean standable(Draft d, int x, int y, int z) {
		return d.isEmpty(x, y, z) && d.isEmpty(x, y + 1, z) && floor(d, x, y, z);
	}

	/** Candles (lit) on a table top or a sill: a thin wrapper so the venue's own fittings read together. */
	public static void candles(Draft d, int x, int y, int z, int n) {
		if (d.isEmpty(x, y, z)) {
			Lighting.candles(d, x, y, z, n);
		}
	}

	// ------------------------------------------------------------------------------------------- placing on a floor

	/**
	 * Dice tables placed on a hand-furnished floor wherever they fit (stool, table, table, stool along either axis, a cell
	 * beside to stand in), the most central spots first, at least a cell apart. Answers how many were placed.
	 */
	public static int placeGaming(Draft d, dev.voidmc.hearth.build.kit.trade.Floorplan plan, int y, int want) {
		int placed = 0;
		double cx = (plan.x0 + plan.x1) / 2.0;
		double cz = (plan.z0 + plan.z1) / 2.0;
		java.util.List<int[]> spots = new java.util.ArrayList<>();
		for (int z = plan.z0; z <= plan.z1; z++) {
			for (int x = plan.x0; x <= plan.x1; x++) {
				spots.add(new int[]{x, z, 0});
				spots.add(new int[]{x, z, 1});
			}
		}
		spots.sort(java.util.Comparator.comparingDouble(c -> Math.abs(c[0] + (c[2] == 0 ? 0.5 : 0) - cx) + Math.abs(c[1] + (c[2] == 1 ? 0.5 : 0) - cz)
			+ Noise.unit(d.seed() ^ 0x6A3E, c[0], c[2], c[1]) * 0.3));
		for (int[] c : spots) {
			if (placed >= want) {
				break;
			}
			Direction run = c[2] == 0 ? Dirs.RIGHT : Dirs.BACK;
			int[][] cells = gamingCells(c[0], c[1], run);
			boolean ok = true;
			for (int[] k : cells) {
				ok &= plan.free(k[0], k[1]);
			}
			if (!ok) {
				continue;
			}
			Direction side = Dirs.right(run);
			int[] stand = null;
			for (int[] st : new int[][]{{c[0] + Dirs.dx(side), c[1] + Dirs.dz(side)}, {c[0] - Dirs.dx(side), c[1] - Dirs.dz(side)},
				{c[0] + Dirs.dx(run) + Dirs.dx(side), c[1] + Dirs.dz(run) + Dirs.dz(side)}}) {
				if (plan.free(st[0], st[1])) {
					stand = st;
					break;
				}
			}
			if (stand == null || !plan.take(cells, stand)) {
				continue;
			}
			gamingTable(d, c[0], y, c[1], run);
			placed++;
			// keep the ring round it free of another table
			for (int[] k : cells) {
				for (Direction dir : Dirs.HORIZONTAL) {
					int nx = k[0] + Dirs.dx(dir);
					int nz = k[1] + Dirs.dz(dir);
					if (plan.free(nx, nz)) {
						plan.keepArea(nx, nz, nx, nz);
					}
				}
			}
		}
		return placed;
	}

	// ------------------------------------------------------------------------------------------- geometry

	static boolean isEdge(int x, int z, int ax, int az, int bx, int bz, Direction side) {
		return switch (side) {
			case SOUTH -> z == az;
			case NORTH -> z == bz;
			case WEST -> x == ax;
			default -> x == bx;
		};
	}

	/** The floor cell of a stage's step up: in front of the front's left end (seen from the audience). */
	public static int[] stepCell(int x0, int z0, int x1, int z1, Direction toAudience) {
		int[] fl = frontCorner(Math.min(x0, x1), Math.min(z0, z1), Math.max(x0, x1), Math.max(z0, z1), toAudience, true);
		return new int[]{fl[0] + Dirs.dx(toAudience), fl[1] + Dirs.dz(toAudience)};
	}

	/** The front row's middle cell (front = the edge toward {@code toAudience}). */
	static int[] frontMiddle(int ax, int az, int bx, int bz, Direction toAudience) {
		return switch (toAudience) {
			case SOUTH -> new int[]{(ax + bx) / 2, az};
			case NORTH -> new int[]{(ax + bx + 1) / 2, bz};
			case WEST -> new int[]{ax, (az + bz + 1) / 2};
			default -> new int[]{bx, (az + bz) / 2};
		};
	}

	/** A front corner; {@code left} is the left end as the audience sees it. */
	static int[] frontCorner(int ax, int az, int bx, int bz, Direction toAudience, boolean left) {
		// the audience looks back toward the stage: their left is the stage's right
		return switch (toAudience) {
			case SOUTH -> new int[]{left ? bx : ax, az};
			case NORTH -> new int[]{left ? ax : bx, bz};
			case WEST -> new int[]{ax, left ? bz : az};
			default -> new int[]{bx, left ? az : bz};
		};
	}

	static int[] backCorner(int ax, int az, int bx, int bz, Direction toAudience, boolean left) {
		int[] f = frontCorner(ax, az, bx, bz, toAudience, left);
		return switch (toAudience) {
			case SOUTH -> new int[]{f[0], bz};
			case NORTH -> new int[]{f[0], az};
			case WEST -> new int[]{bx, f[1]};
			default -> new int[]{ax, f[1]};
		};
	}

	static List<int[]> backRow(int ax, int az, int bx, int bz, Direction toAudience) {
		java.util.ArrayList<int[]> out = new java.util.ArrayList<>();
		switch (toAudience) {
			case SOUTH -> {
				for (int x = ax; x <= bx; x++) {
					out.add(new int[]{x, bz});
				}
			}
			case NORTH -> {
				for (int x = ax; x <= bx; x++) {
					out.add(new int[]{x, az});
				}
			}
			case WEST -> {
				for (int z = az; z <= bz; z++) {
					out.add(new int[]{bx, z});
				}
			}
			default -> {
				for (int z = az; z <= bz; z++) {
					out.add(new int[]{ax, z});
				}
			}
		}
		return out;
	}
}
