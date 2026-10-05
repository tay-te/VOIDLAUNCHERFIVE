package dev.voidmc.hearth.build.kit.noble;

import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.PoiType;
import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Mat;
import dev.voidmc.hearth.build.draft.Noise;
import dev.voidmc.hearth.build.draft.States;
import dev.voidmc.hearth.build.kit.Furniture;
import dev.voidmc.hearth.build.kit.Garden;
import dev.voidmc.hearth.build.kit.Openings;
import dev.voidmc.hearth.build.kit.Roofs;
import dev.voidmc.hearth.build.kit.light.Lighting;
import dev.voidmc.hearth.build.style.Form;
import dev.voidmc.hearth.build.style.Habits;
import dev.voidmc.hearth.build.style.Role;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * The courts of a lord's seat: their level (cut and filled from the ground under them), their paving of setts worn to
 * earth along the ways, and what stands in them: a well under its little roof, a stable shed against the wall with its
 * stalls, hay and hitching posts, lamp posts, a woodpile, carts' barrels.
 */
public final class Court {
	private Court() {
	}

	/** The level a court is made at: the middle of the ground under it, kept between {@code lo} and {@code hi}. */
	public static int level(Draft d, int x0, int z0, int x1, int z1, int lo, int hi) {
		List<Integer> gs = new ArrayList<>();
		for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z += 2) {
			for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x += 2) {
				int g = d.site().ground(x, z);
				int wv = d.site().water(x, z);
				gs.add(wv != dev.voidmc.hearth.build.terrain.Site.NONE ? Math.max(g, wv) : g);
			}
		}
		if (gs.isEmpty()) {
			return Math.clamp(0, lo, hi);
		}
		int[] a = gs.stream().mapToInt(Integer::intValue).toArray();
		Arrays.sort(a);
		return Math.clamp(a[a.length / 2], lo, hi);
	}

	/** Paves a court at standing level y: setts of the paving, worn to the path's earth and gravel in patches. */
	public static void pave(Draft d, Dress m, int x0, int z0, int x1, int z1, int y) {
		Mat path = d.mat(Role.PATH);
		try (var s = d.stage(StageKind.EXTERIOR)) {
			for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) {
				for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) {
					if (!Mason.inPlot(d, x, z)) {
						continue;
					}
					double n = Noise.value(d.seed() ^ 0xC0B, x * 0.22, 0, z * 0.22);
					BlockState st = n > 0.18 ? path.full().at(x, y - 1, z) : m.paving.full().at(x, y - 1, z);
					if (st != null && st.getBlock() == Blocks.DIRT_PATH && d.isSolid(x, y, z)) {
						st = m.paving.full().at(x, y - 1, z);
					}
					d.set(x, y - 1, z, st);
				}
			}
		}
	}

	/**
	 * Levels and paves the ward an {@link Outline} encloses inside its curtain ({@code thick} deep) at standing level y,
	 * run by run along each row, so the made ground stops at the wall's foot and the field outside keeps its own slope.
	 */
	public static void fill(Draft d, Dress m, Outline o, int thick, int y) {
		for (int z = Math.max(0, o.z0); z <= Math.min(d.depth() - 1, o.z1); z++) {
			int run = Integer.MIN_VALUE;
			for (int x = o.x0; x <= o.x1 + 1; x++) {
				boolean in = x <= o.x1 && Mason.inPlot(d, x, z) && o.ward(x, z, thick);
				if (in && run == Integer.MIN_VALUE) {
					run = x;
				} else if (!in && run != Integer.MIN_VALUE) {
					dev.voidmc.hearth.build.terrain.Groundwork.level(d, run, z, x - 1, z, y, true);
					pave(d, m, run, z, x - 1, z, y);
					run = Integer.MIN_VALUE;
				}
			}
		}
	}

	/**
	 * A stable shed against the wall toward {@code wall} over (x0..x1, z0..z1) at standing level y: posts on the open
	 * side, a lean-to roof falling away from the wall, stalls parted by fences with hay and a trough, hitching posts in
	 * front (HITCHING_POST), a chest of tack (STORAGE).
	 */
	public static void stable(Draft d, Dress m, Manner man, int x0, int z0, int x1, int z1, int y, Direction wall) {
		int ax = Math.min(x0, x1);
		int bx = Math.max(x0, x1);
		int az = Math.min(z0, z1);
		int bz = Math.max(z0, z1);
		if (bx - ax < 2 || bz - az < 2) {
			return;
		}
		Direction open = wall.getOpposite();
		boolean alongZ = wall.getAxis() == Direction.Axis.X;
		Mat frame = d.mat(Role.FRAME);
		Mat fence = d.mat(Role.FENCE);
		// posts along the open side
		int ox = open == Dirs.RIGHT ? bx : open == Dirs.LEFT ? ax : -1;
		int oz = open == Dirs.BACK ? bz : open == Dirs.FRONT ? az : -1;
		try (var s = d.stage(StageKind.FRAME)) {
			if (alongZ) {
				for (int z = az; z <= bz; z += Math.max(1, (bz - az) / 2)) {
					for (int yy = y; yy < y + 3; yy++) {
						d.set(ox, yy, z, Mason.post(frame));
					}
				}
			} else {
				for (int x = ax; x <= bx; x += Math.max(1, (bx - ax) / 2)) {
					for (int yy = y; yy < y + 3; yy++) {
						d.set(x, yy, oz, Mason.post(frame));
					}
				}
			}
		}
		// the lean-to roof, rising toward the wall
		Roofs.Field f = Roofs.shed(ax - (alongZ ? 0 : 0), az, bx, bz, wall, Roofs.Profile.straight(0.5));
		try (var s = d.stage(StageKind.ROOF)) {
			Roofs.raster(d, f, y + 3, ax, az, bx, bz, d.mat(Role.ROOF), d.mat(Role.ROOF_TRIM), Habits.Ridge.SLAB);
		}
		// stalls: fences across from the wall every two cells, hay at the back, a trough
		try (var s = d.stage(StageKind.INTERIOR)) {
			if (alongZ) {
				int wx = wall == Dirs.RIGHT ? bx : ax;
				for (int z = az + 2; z < bz; z += 2) {
					int x = wx + Dirs.dx(open);
					d.set(x, y, z, fence.fence());
				}
				for (int z = az; z <= bz; z++) {
					if ((z - az) % 2 == 1) {
						d.set(wx, y, z, Blocks.HAY_BLOCK.defaultBlockState());
					}
				}
			} else {
				int wz = wall == Dirs.BACK ? bz : az;
				for (int x = ax + 2; x < bx; x += 2) {
					d.set(x, y, wz + Dirs.dz(open), fence.fence());
				}
				for (int x = ax; x <= bx; x++) {
					if ((x - ax) % 2 == 1) {
						d.set(x, y, wz, Blocks.HAY_BLOCK.defaultBlockState());
					}
				}
			}
		}
		// hitching posts outside the open side
		int hx = alongZ ? ox + Dirs.dx(open) : (ax + bx) / 2;
		int hz = alongZ ? (az + bz) / 2 : oz + Dirs.dz(open);
		if (Mason.inPlot(d, hx, hz) && d.isEmpty(hx, y, hz)) {
			try (var s = d.stage(StageKind.EXTERIOR)) {
				d.set(hx, y, hz, fence.fence());
			}
			d.poi(PoiType.HITCHING_POST, hx + Dirs.dx(open), y, hz + Dirs.dz(open), open.getOpposite());
		}
		int cx = alongZ ? (wall == Dirs.RIGHT ? bx : ax) : ax;
		int cz = alongZ ? az : (wall == Dirs.BACK ? bz : az);
		if (d.isEmpty(cx, y, cz)) {
			Furniture.chest(d, cx, y, cz, open, List.of(new Object[]{"minecraft:saddle", 1}, new Object[]{"minecraft:lead", 2},
				new Object[]{"minecraft:wheat", 8}));
		}
	}

	/**
	 * A well at (x, z), its water at the court's level: a curb of the dressing round it, two posts carrying a little roof
	 * and the winch, a bucket chain, a lantern. MEETING_POINT beside it.
	 */
	public static void well(Draft d, Dress m, int x, int z, int y) {
		if (!Mason.inPlot(d, x - 1, z - 1) || !Mason.inPlot(d, x + 1, z + 1)) {
			return;
		}
		try (var s = d.stage(StageKind.EARTHWORK)) {
			for (int yy = y - 4; yy < y - 1; yy++) {
				d.set(x, yy, z, Blocks.WATER.defaultBlockState());
			}
			d.set(x, y - 1, z, Blocks.WATER.defaultBlockState());
		}
		try (var s = d.stage(StageKind.EXTERIOR)) {
			for (int dz = -1; dz <= 1; dz++) {
				for (int dx = -1; dx <= 1; dx++) {
					if (dx == 0 && dz == 0) {
						continue;
					}
					for (int yy = y - 4; yy < y; yy++) {
						d.set(x + dx, yy, z + dz, m.footing.full());
					}
					d.set(x + dx, y, z + dz, m.dress.has(Form.SLAB) ? m.dress.slab(SlabType.BOTTOM) : m.dress.full());
				}
			}
			Mat frame = d.mat(Role.FRAME);
			for (int dx : new int[]{-1, 1}) {
				d.set(x + dx, y, z, Mason.post(frame));
				d.set(x + dx, y + 1, z, Mason.post(frame));
				d.set(x + dx, y + 2, z, Mason.post(frame));
			}
			Mat roof = d.mat(Role.ROOF);
			for (int dx = -1; dx <= 1; dx++) {
				d.set(x + dx, y + 3, z - 1, roof.stairsOrSlab(Dirs.BACK, Half.BOTTOM));
				d.set(x + dx, y + 3, z + 1, roof.stairsOrSlab(Dirs.FRONT, Half.BOTTOM));
				d.set(x + dx, y + 3, z, d.mat(Role.ROOF_TRIM).has(Form.SLAB) ? d.mat(Role.ROOF_TRIM).slab(SlabType.BOTTOM) : roof.full());
			}
			d.set(x, y + 2, z, Mason.beam(frame, Direction.Axis.X));
			d.set(x, y + 1, z, States.lantern(Blocks.LANTERN, true));
		}
		d.poi(PoiType.MEETING_POINT, x, y, z - 2, Dirs.BACK);
	}

	/** Lamp posts round the edge of a court where they block no way, about every nine blocks. */
	public static void lamps(Draft d, int x0, int z0, int x1, int z1, int y) {
		int ax = Math.min(x0, x1);
		int bx = Math.max(x0, x1);
		int az = Math.min(z0, z1);
		int bz = Math.max(z0, z1);
		for (int z = az + 1; z <= bz - 1; z += 8) {
			for (int x = ax + 1; x <= bx - 1; x += 8) {
				if (open(d, x, y, z)) {
					Lighting.post(d, x, y, z);
				}
			}
		}
	}

	/** {@link #lamps} over the ward of an {@link Outline} (inside its curtain, {@code thick} deep), never outside it. */
	public static void lamps(Draft d, Outline o, int thick, int y) {
		for (int z = o.z0 + 1; z <= o.z1 - 1; z += 8) {
			for (int x = o.x0 + 1; x <= o.x1 - 1; x += 8) {
				// the nearest ward cell along the row, so a lamp is not lost to a corner the outline cuts
				for (int off : new int[]{0, 2, -2, 4, -4}) {
					int px = x + off;
					if (o.ward(px, z, thick + 1) && open(d, px, y, z)) {
						Lighting.post(d, px, y, z);
						break;
					}
				}
			}
		}
	}

	private static boolean open(Draft d, int x, int y, int z) {
		for (int dz = -1; dz <= 1; dz++) {
			for (int dx = -1; dx <= 1; dx++) {
				if (!d.isEmpty(x + dx, y, z + dz) || !d.isEmpty(x + dx, y + 1, z + dz)) {
					return false;
				}
			}
		}
		return d.isSolid(x, y - 1, z);
	}

	/** A woodpile against something along {@code run}. */
	public static void woodpile(Draft d, int x, int y, int z, Direction run, int n) {
		Garden.woodpile(d, x, y, z, run, n);
	}

	/** The job site of a stable master: hay and a trough by the stalls. */
	public static void stablemaster(Draft d, int x, int y, int z, Direction facing) {
		try (var s = d.stage(StageKind.INTERIOR)) {
			d.set(x, y, z, Blocks.WATER_CAULDRON.defaultBlockState().setValue(BlockStateProperties.LEVEL_CAULDRON, 3));
		}
		d.poi(PoiType.JOB_SITE, x + Dirs.dx(facing), y, z + Dirs.dz(facing), facing.getOpposite(), JobType.STABLEMASTER);
	}

	/**
	 * A lean-to range against a curtain toward {@code wall}: walls of the seat's walling on the three open sides of
	 * (x0..x1, z0..z1) (the side toward the wall stands against the curtain's face), standing level y, {@code eave} rows
	 * of wall at the front, a shed roof rising from the front's eave to the curtain, a door in the middle of the front
	 * (and a second for a long one), small windows. Returns the room inside for the caller to furnish; {@code door[0..1]}
	 * receives the cell outside the main door.
	 */
	public static dev.voidmc.hearth.build.kit.Rooms.Room leanTo(Draft d, Dress m, Manner man, int x0, int z0, int x1, int z1, int y, Direction wall,
		int eave, int[] door) {
		int ax = Math.min(x0, x1);
		int bx = Math.max(x0, x1);
		int az = Math.min(z0, z1);
		int bz = Math.max(z0, z1);
		Direction front = wall.getOpposite();
		java.util.function.BiPredicate<Integer, Integer> isWall = (x, z) -> {
			boolean edgeX = x == ax || x == bx;
			boolean edgeZ = z == az || z == bz;
			boolean wallRow = wall == Dirs.RIGHT ? x == bx : wall == Dirs.LEFT ? x == ax : wall == Dirs.BACK ? z == bz : z == az;
			boolean onWallRowOnly = wallRow && !(wall.getAxis() == Direction.Axis.X ? edgeZ : edgeX);
			return (edgeX || edgeZ) && !onWallRowOnly;
		};
		for (int z = az; z <= bz; z++) {
			for (int x = ax; x <= bx; x++) {
				Mason.footing(d, m, x, z, y - 2);
			}
		}
		try (var s = d.stage(StageKind.WALLS)) {
			for (int z = az; z <= bz; z++) {
				for (int x = ax; x <= bx; x++) {
					boolean w = isWall.test(x, z);
					boolean corner = (x == ax || x == bx) && (z == az || z == bz);
					for (int yy = y - 1; yy < y + eave + 3; yy++) {
						if (w) {
							if (yy < y + eave) {
								d.set(x, yy, z, yy <= y - 1 ? m.plinth.full() : corner ? m.dress.full() : m.wall.full());
							}
						} else if (yy >= y && yy < y + eave + 3) {
							d.set(x, yy, z, Blocks.AIR.defaultBlockState());
						}
					}
				}
			}
		}
		try (var s = d.stage(StageKind.FLOORS)) {
			for (int z = az; z <= bz; z++) {
				for (int x = ax; x <= bx; x++) {
					if (!isWall.test(x, z)) {
						d.set(x, y - 1, z, m.paving.full());
					}
				}
			}
		}
		// the shed roof rising to the curtain: half a block a cell, from the front's eave
		Roofs.Field f = Roofs.shed(ax - (wall == Dirs.RIGHT || front == Dirs.LEFT ? 1 : 0) + (wall == Dirs.LEFT ? 0 : 0), az, bx, bz, wall, Roofs.Profile.straight(0.5));
		int ex0 = front == Dirs.LEFT ? ax - 1 : ax;
		int ex1 = front == Dirs.RIGHT ? bx + 1 : bx;
		int ez0 = front == Dirs.FRONT ? az - 1 : az;
		int ez1 = front == Dirs.BACK ? bz + 1 : bz;
		ex0 = Math.max(0, ex0);
		ez0 = Math.max(0, ez0);
		ex1 = Math.min(d.frontage() - 1, ex1);
		ez1 = Math.min(d.depth() - 1, ez1);
		f = Roofs.shed(ex0, ez0, ex1, ez1, wall, Roofs.Profile.straight(0.5));
		Roofs.Roof roof;
		try (var s = d.stage(StageKind.ROOF)) {
			roof = Roofs.raster(d, f, y + eave, ex0, ez0, ex1, ez1, d.mat(Role.ROOF), d.mat(Role.ROOF_TRIM), Habits.Ridge.SLAB);
		}
		// the side walls carried up under the roof
		try (var s = d.stage(StageKind.WALLS)) {
			for (int z = az; z <= bz; z++) {
				for (int x = ax; x <= bx; x++) {
					if (!isWall.test(x, z) || !roof.covers(x, z)) {
						continue;
					}
					for (int yy = y + eave; yy < roof.bottomY(x, z); yy++) {
						d.set(x, yy, z, m.wall.full());
					}
				}
			}
		}
		// doors and windows in the front
		dev.voidmc.hearth.build.kit.Side fs = dev.voidmc.hearth.build.kit.Side.of(ax, az, bx, bz, front);
		int mid = fs.middle();
		int[] doors = fs.length() >= 12 ? new int[]{fs.length() / 3, fs.length() * 2 / 3} : new int[]{mid};
		var doorBlock = d.mat(Role.DOOR).mainOr(Form.DOOR, Blocks.SPRUCE_DOOR);
		for (int k = 0; k < doors.length; k++) {
			int i = doors[k];
			int dx = fs.x(i);
			int dz = fs.z(i);
			d.inStage(StageKind.OPENINGS, () -> d.door(dx, y, dz, doorBlock, front, net.minecraft.world.level.block.state.properties.DoorHingeSide.LEFT));
			try (var s = d.stage(StageKind.WALLS)) {
				d.set(dx, y + 2, dz, m.dress.full());
			}
			if (k == 0 && door != null && door.length >= 2) {
				door[0] = fs.outX(i, 1);
				door[1] = fs.outZ(i, 1);
			}
			int lx = fs.outX(i + 1, 1);
			int lz = fs.outZ(i + 1, 1);
			if (Mason.inPlot(d, lx, lz) && eave >= 3) {
				Lighting.doorLamp(d, lx, y + 2, lz, front.getOpposite());
			}
		}
		for (int i = 2; i < fs.length() - 2; i += 3) {
			boolean nearDoor = false;
			for (int dc : doors) {
				nearDoor |= Math.abs(dc - i) <= 1;
			}
			if (!nearDoor && eave >= 3 && Openings.isWallBlock(d, fs.x(i), y + 1, fs.z(i))) {
				Range.window(d, m, man.window() == Manner.Window.LANCET ? Manner.Window.MULLION : man.window(), fs, i, y + 1, 1);
			}
		}
		var room = new dev.voidmc.hearth.build.kit.Rooms.Room(
			wall == Dirs.LEFT ? ax : ax + 1, wall == Dirs.FRONT ? az : az + 1,
			wall == Dirs.RIGHT ? bx : bx - 1, wall == Dirs.BACK ? bz : bz - 1, y, 3);
		for (int i : doors) {
			room.keep(fs.x(i) - Dirs.dx(front), fs.z(i) - Dirs.dz(front));
		}
		return room;
	}
}
