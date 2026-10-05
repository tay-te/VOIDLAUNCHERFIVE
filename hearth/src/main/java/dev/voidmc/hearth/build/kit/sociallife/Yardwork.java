package dev.voidmc.hearth.build.kit.sociallife;

import dev.voidmc.hearth.api.PoiType;
import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Mat;
import dev.voidmc.hearth.build.draft.Noise;
import dev.voidmc.hearth.build.draft.Paint;
import dev.voidmc.hearth.build.draft.States;
import dev.voidmc.hearth.build.style.Form;
import dev.voidmc.hearth.build.style.Role;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * The small things round a house of the social-life family: the wayside cross for pilgrims, the hitching rail and
 * trough where travellers tie their beasts, the herb beds of a hospital, the swing on a frame in an orphanage's yard,
 * and the plank partition with its door that divides a room.
 */
public final class Yardwork {
	private Yardwork() {
	}

	/** A wayside cross at (x, y, z): a cross on a stone with candles before it, roofed with a little board in the timber cultures. */
	public static void wayside(Draft d, int x, int y, int z) {
		Mat stone = d.mat(Role.FOUNDATION);
		Graves.Manner m = Graves.of(d);
		try (var s = d.stage(StageKind.EXTERIOR)) {
			d.set(x, y - 1, z, stone.full());
			d.set(x, y, z, stone.full());
			boolean timber = m == Graves.Manner.BOHEMIAN || m == Graves.Manner.ALPINE || m == Graves.Manner.NORTHERN;
			var shaft = timber ? Blocks.DARK_OAK_FENCE.defaultBlockState() : Blocks.STONE_BRICK_WALL.defaultBlockState();
			d.set(x, y + 1, z, shaft);
			d.set(x, y + 2, z, shaft);
			d.set(x, y + 3, z, shaft);
			if (x - 1 >= 0 && d.isEmpty(x - 1, y + 2, z)) {
				d.set(x - 1, y + 2, z, shaft);
			}
			if (x + 1 < d.frontage() && d.isEmpty(x + 1, y + 2, z)) {
				d.set(x + 1, y + 2, z, shaft);
			}
			if (timber && y + 4 < d.maxHeight()) {
				d.set(x, y + 4, z, d.mat(Role.ROOF).slab(SlabType.BOTTOM));
			}
		}
		Graves.candles(d, x, y + 1, z + 1 < d.depth() && Venue.standable(d, x, y, z + 1) ? z + 1 : z, 2);
	}

	/**
	 * A hitching rail from (x, y, z) along {@code run}: {@code n} posts of the fence timber with the rail between and a
	 * trough of water at the end, the beasts standing on the {@code toBeasts} side. Registers a HITCHING_POST at each
	 * second post where the beast stands. Answers whether it was built (every cell free ground).
	 */
	public static boolean hitchingRail(Draft d, int x, int y, int z, Direction run, int n, Direction toBeasts) {
		for (int k = 0; k <= n; k++) {
			int cx = x + Dirs.dx(run) * k;
			int cz = z + Dirs.dz(run) * k;
			int bx = cx + Dirs.dx(toBeasts);
			int bz = cz + Dirs.dz(toBeasts);
			if (cx < 0 || cz < 0 || cx >= d.frontage() || cz >= d.depth() || !Venue.standable(d, cx, y, cz)) {
				return false;
			}
			if (k % 2 == 0 && !(bx >= 0 && bz >= 0 && bx < d.frontage() && bz < d.depth() && Venue.standable(d, bx, y, bz))) {
				return false;
			}
		}
		Mat post = d.mat(Role.FENCE).has(Form.FENCE) ? d.mat(Role.FENCE) : d.mat(Role.FRAME);
		try (var s = d.stage(StageKind.EXTERIOR)) {
			for (int k = 0; k < n; k++) {
				d.set(x + Dirs.dx(run) * k, y, z + Dirs.dz(run) * k, post.fence());
			}
			d.set(x + Dirs.dx(run) * n, y, z + Dirs.dz(run) * n, Blocks.WATER_CAULDRON.defaultBlockState().setValue(BlockStateProperties.LEVEL_CAULDRON, 3));
		}
		for (int k = 0; k < n; k += 2) {
			d.poi(PoiType.HITCHING_POST, x + Dirs.dx(run) * k + Dirs.dx(toBeasts), y, z + Dirs.dz(run) * k + Dirs.dz(toBeasts), toBeasts.getOpposite());
		}
		return true;
	}

	/**
	 * Herb beds over [x0..x1] × [z0..z1] at standing level y: raised boxes edged with the frame timber in rows a cell
	 * apart, planted with low herbs and flowers (the infirmary's physic garden), the walks between of the path.
	 */
	public static void herbBeds(Draft d, int x0, int z0, int x1, int z1, int y, Direction rows) {
		Mat edge = d.mat(Role.FRAME);
		Block[] herbs = {Blocks.FERN, Blocks.SWEET_BERRY_BUSH, Blocks.LILY_OF_THE_VALLEY, Blocks.CORNFLOWER, Blocks.OXEYE_DAISY, Blocks.AZURE_BLUET,
			Blocks.SHORT_GRASS};
		boolean alongX = rows.getAxis() == Direction.Axis.X;
		try (var s = d.stage(StageKind.EXTERIOR)) {
			for (int z = z0; z <= z1; z++) {
				for (int x = x0; x <= x1; x++) {
					if (!Venue.standable(d, x, y, z)) {
						continue;
					}
					int across = alongX ? z - z0 : x - x0;
					if (across % 3 == 2) {
						d.set(x, y - 1, z, d.mat(Role.PATH).full());
						continue;
					}
					d.set(x, y - 1, z, Blocks.ROOTED_DIRT.defaultBlockState());
					d.set(x, y - 2, z, Blocks.DIRT.defaultBlockState());
					Block b = herbs[(int) (Noise.unit(d.seed(), x, 7, z) * herbs.length)];
					if (b == Blocks.SWEET_BERRY_BUSH) {
						d.set(x, y, z, Blocks.SWEET_BERRY_BUSH.defaultBlockState().setValue(BlockStateProperties.AGE_3, 2));
					} else {
						d.set(x, y, z, b.defaultBlockState());
					}
					// the boards that edge the bed, where it ends on a walk
					if ((alongX ? x == x0 || x == x1 : z == z0 || z == z1) && edge.has(Form.TRAPDOOR)) {
						d.set(x, y, z, edge.trapdoor(alongX ? (x == x0 ? Dirs.RIGHT : Dirs.LEFT) : (z == z0 ? Dirs.BACK : Dirs.FRONT), net.minecraft.world.level.block.state.properties.Half.BOTTOM, true));
					}
				}
			}
		}
	}

	/**
	 * A swing on a frame for the children: two posts of the frame timber three high with a beam across, chains down to a
	 * slab seat, at (x, y, z) with the beam along {@code along}. Needs the three cells along and the cell either side of
	 * the seat free. Answers whether it was built.
	 */
	public static boolean swing(Draft d, int x, int y, int z, Direction along) {
		Direction across = Dirs.right(along);
		for (int k = -1; k <= 1; k++) {
			for (int j = -1; j <= 1; j++) {
				int cx = x + Dirs.dx(along) * k + Dirs.dx(across) * j;
				int cz = z + Dirs.dz(along) * k + Dirs.dz(across) * j;
				if (cx < 0 || cz < 0 || cx >= d.frontage() || cz >= d.depth() || !Venue.standable(d, cx, y, cz) || y + 4 >= d.maxHeight()) {
					return false;
				}
			}
		}
		Mat frame = d.mat(Role.FRAME);
		try (var s = d.stage(StageKind.EXTERIOR)) {
			for (int k : new int[]{-1, 1}) {
				int px = x + Dirs.dx(along) * k;
				int pz = z + Dirs.dz(along) * k;
				for (int yy = y; yy <= y + 2; yy++) {
					d.set(px, yy, pz, frame.fence());
				}
				d.set(px, y + 3, pz, frame.log(along.getAxis()));
			}
			d.set(x, y + 3, z, frame.log(along.getAxis()));
			d.set(x, y + 2, z, States.axis(Blocks.IRON_CHAIN, Direction.Axis.Y));
			d.set(x, y + 1, z, States.axis(Blocks.IRON_CHAIN, Direction.Axis.Y));
			d.set(x, y, z, d.mat(Role.FURNITURE).slab(SlabType.TOP));
		}
		d.poi(PoiType.SEAT, x, y, z, across);
		return true;
	}

	/**
	 * A partition across a room at x = {@code px} from z0 to z1, {@code rows} high from y, of the gable's material, with a
	 * door at {@code doorZ} opening toward {@code opens}.
	 */
	public static void partition(Draft d, int px, int z0, int z1, int y, int rows, int doorZ, Direction opens) {
		Mat wall = d.mat(Role.GABLE);
		try (var s = d.stage(StageKind.WALLS)) {
			for (int z = z0; z <= z1; z++) {
				for (int yy = y; yy < y + rows; yy++) {
					d.set(px, yy, z, z == doorZ && yy < y + 2 ? Paint.of(Blocks.AIR.defaultBlockState()) : wall.full());
				}
			}
		}
		d.inStage(StageKind.OPENINGS, () -> d.door(px, y, doorZ, d.mat(Role.DOOR).mainOr(Form.DOOR, Blocks.SPRUCE_DOOR), opens, DoorHingeSide.LEFT));
	}

	/** A partition across a room at z = {@code pz} from x0 to x1, like {@link #partition}, its door at {@code doorX}. */
	public static void partitionAcross(Draft d, int pz, int x0, int x1, int y, int rows, int doorX, Direction opens) {
		Mat wall = d.mat(Role.GABLE);
		try (var s = d.stage(StageKind.WALLS)) {
			for (int x = x0; x <= x1; x++) {
				for (int yy = y; yy < y + rows; yy++) {
					d.set(x, yy, pz, x == doorX && yy < y + 2 ? Paint.of(Blocks.AIR.defaultBlockState()) : wall.full());
				}
			}
		}
		d.inStage(StageKind.OPENINGS, () -> d.door(doorX, y, pz, d.mat(Role.DOOR).mainOr(Form.DOOR, Blocks.SPRUCE_DOOR), opens, DoorHingeSide.LEFT));
	}

	/**
	 * A bell-cote on the ridge of a roof running along x, at column x: two posts of the frame timber either side of the
	 * ridge's top, a beam across them with the bell hung under it, a cap of the roofing; the poor house's call to prayer
	 * and to meals. Answers whether it fitted under the plot's height.
	 */
	public static boolean bellcote(Draft d, dev.voidmc.hearth.build.kit.Roofs.Roof roof, int x) {
		if (roof == null || x - 1 < roof.x0 || x + 1 > roof.x1) {
			return false;
		}
		int top = Integer.MIN_VALUE;
		int rz = 0;
		// the ridge as drawn: the highest block over the roof's span in this column
		for (int z = roof.z0; z <= roof.z1; z++) {
			int y = d.maxHeight() - 1;
			while (y > roof.y0 && d.isEmpty(x, y, z)) {
				y--;
			}
			if (y > top && !d.isEmpty(x, y, z)) {
				top = y;
				rz = z;
			}
		}
		if (top == Integer.MIN_VALUE || top + 3 >= d.maxHeight()) {
			return false;
		}
		Mat frame = d.mat(Role.FRAME);
		Mat cap = d.mat(Role.ROOF);
		try (var s = d.stage(StageKind.ROOF)) {
			for (int k : new int[]{-1, 1}) {
				d.set(x + k, top + 1, rz, frame.fence());
			}
			for (int k = -1; k <= 1; k++) {
				d.set(x + k, top + 2, rz, frame.log(Direction.Axis.X));
			}
			d.set(x, top + 1, rz, Blocks.BELL.defaultBlockState().setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.BELL_ATTACHMENT,
				net.minecraft.world.level.block.state.properties.BellAttachType.CEILING).setValue(
				net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING, net.minecraft.core.Direction.SOUTH));
			// a little gable of the roofing over it, its slopes the main roof's
			for (int k = -1; k <= 1; k++) {
				if (cap.has(Form.STAIRS)) {
					d.set(x + k, top + 2, rz - 1, cap.stairs(net.minecraft.core.Direction.SOUTH, net.minecraft.world.level.block.state.properties.Half.BOTTOM));
					d.set(x + k, top + 2, rz + 1, cap.stairs(net.minecraft.core.Direction.NORTH, net.minecraft.world.level.block.state.properties.Half.BOTTOM));
				}
				d.set(x + k, top + 3, rz, cap.has(Form.SLAB) ? cap.slab(SlabType.BOTTOM) : frame.fence());
			}
		}
		return true;
	}
}
