package dev.voidmc.hearth.build.archetype.work;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.ArchetypeCategory;
import dev.voidmc.hearth.api.ArchetypeInfo;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.PoiType;
import dev.voidmc.hearth.api.SettlementTier;
import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.Archetype;
import dev.voidmc.hearth.build.draft.BlockEntities;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Mat;
import dev.voidmc.hearth.build.draft.Noise;
import dev.voidmc.hearth.build.draft.Paint;
import dev.voidmc.hearth.build.draft.States;
import dev.voidmc.hearth.build.kit.Body;
import dev.voidmc.hearth.build.kit.Facade;
import dev.voidmc.hearth.build.kit.Floors;
import dev.voidmc.hearth.build.kit.Furniture;
import dev.voidmc.hearth.build.kit.Garden;
import dev.voidmc.hearth.build.kit.Openings;
import dev.voidmc.hearth.build.kit.Roofs;
import dev.voidmc.hearth.build.kit.Side;
import dev.voidmc.hearth.build.kit.Walls;
import dev.voidmc.hearth.build.style.Form;
import dev.voidmc.hearth.build.style.Habits;
import dev.voidmc.hearth.build.style.Habits.RoofForm;
import dev.voidmc.hearth.build.style.Role;
import dev.voidmc.hearth.build.terrain.Groundwork;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * {@code hearth:work/builders_yard}: the first workplace every settlement raises, where its builders work. A fenced yard
 * behind a wide gate, an open-fronted workshop shed along the back with benches and the stone saw under its roof, a
 * little office with the plans on a lectern, stacks of logs on bearers, sawn planks and dressed stone, sawhorses with a
 * log across, scaffolding stacked ready, and a timber crane whose jib swings a load on a chain over the yard.
 *
 * <p>Job sites (BUILDER): the work bench, the stone saw, the sawhorses, the crane's foot. Storage: the shed's chests.
 *
 * <p>Params: none.
 */
public final class BuildersYard implements Archetype {
	public static final ArchetypeInfo INFO = new ArchetypeInfo(Hearth.id("work/builders_yard"), ArchetypeCategory.WORKPLACE, Set.of(JobType.BUILDER), 0,
		11, 17, 11, 17, 8, 14, SettlementTier.CAMP, Set.of(), Set.of(), List.of(), 1, 1.0);

	@Override
	public ArchetypeInfo info() {
		return INFO;
	}

	@Override
	public void draw(Draft d) {
		int w = d.frontage();
		int dp = d.depth();
		Habits h = d.habits();
		// the way in is the gate in the middle of the front, from the street
		d.entrance(w / 2, 0, -1);
		// the yard is levelled to the street
		Groundwork.level(d, 0, 0, w - 1, dp - 1, 0, true);
		yardSurface(d, w, dp);

		// the shed along the back: open to the yard, a lean-to roof sloping to the front, the office at one end
		int shedDepth = dp >= 14 ? 5 : 4;
		int sx0 = 1;
		int sx1 = w - 2;
		int sz0 = dp - 1 - shedDepth;
		int sz1 = dp - 2;
		boolean officeLeft = d.chance(0.5);
		int ox0 = officeLeft ? sx0 : sx1 - 3;
		int ox1 = ox0 + 3;
		int ax0 = officeLeft ? ox1 : sx0;
		int ax1 = officeLeft ? sx1 : ox0;
		shed(d, sx0, sz0, sx1, sz1, ax0, ax1);
		office(d, ox0, sz0, ox1, sz1);
		fence(d, w, dp);

		// the yard: timber on the left, sawn stock and stone on the right, the lane up the middle kept clear
		int laneX = w / 2;
		int logLen = Math.max(2, Math.min(4, sz0 - 5));
		logStack(d, 1, 2, Dirs.BACK, logLen);
		int horseZ = 2 + logLen + 1;
		if (horseZ + 1 < sz0 - 1) {
			sawhorses(d, 1, horseZ);
		}
		plankStack(d, w - 4, 2);
		if (6 < sz0 - 2) {
			stoneStack(d, w - 4, 5);
		}
		// the crane stands before the open end of the shed, its jib over the lane; scaffolding stacked by it
		int craneX = officeLeft ? sx1 - 1 : sx0 + 1;
		crane(d, craneX, sz0 - 1, laneX, h);
		int scafX = officeLeft ? craneX - 2 : craneX + 2;
		if (Math.abs(scafX - laneX) > 1 && Math.abs(scafX - (laneX - 1)) > 1) {
			scaffoldPile(d, scafX, sz0 - 1);
		}
		// a lamp post by the gate, a cart track up the middle
		Garden.lampPost(d, laneX + 2, 0, 1, 2, null);
		List<int[]> track = new ArrayList<>();
		for (int z = 1; z < sz0; z++) {
			track.add(new int[]{laneX, z});
			track.add(new int[]{laneX - 1, z});
		}
		Garden.path(d, track, 0);
		d.poi(PoiType.GATE, laneX, 0, 0, Dirs.BACK);
		Groundwork.clear(d, 0, 0, w - 1, dp - 1, 12);
	}

	private static void yardSurface(Draft d, int w, int dp) {
		try (var s = d.stage(StageKind.EXTERIOR)) {
			for (int z = 1; z < dp - 1; z++) {
				for (int x = 1; x < w - 1; x++) {
					double u = Noise.value(d.seed() ^ 0x7A2D, x / 2.5, 0, z / 2.5);
					Block b = u < 0.35 ? Blocks.COARSE_DIRT : u < 0.55 ? Blocks.DIRT_PATH : u < 0.7 ? Blocks.GRAVEL : Blocks.PACKED_MUD;
					if (d.site().isWater(x, z)) {
						continue;
					}
					d.set(x, -1, z, b.defaultBlockState());
				}
			}
		}
	}

	/** An open shed: posts along the front, a back wall, a lean-to roof, benches and the stone saw under it. */
	private static void shed(Draft d, int x0, int z0, int x1, int z1, int ax0, int ax1) {
		Mat frame = d.mat(Role.FRAME);
		Mat beam = d.mat(Role.BEAM);
		Mat wall = d.mat(Role.GABLE);
		Mat floor = d.mat(Role.PAVING);
		int h = 4;
		// floor of rammed stone, footings under the posts and back wall
		Groundwork.footings(d, x0, z0, x1, z1, -1);
		try (var s = d.stage(StageKind.FLOORS)) {
			for (int z = z0 + 1; z < z1; z++) {
				for (int x = x0 + 1; x < x1; x++) {
					d.set(x, -1, z, floor.full());
				}
			}
		}
		Side front = Side.of(x0, z0, x1, z1, Dirs.FRONT);
		Side back = Side.of(x0, z0, x1, z1, Dirs.BACK);
		try (var s = d.stage(StageKind.FRAME)) {
			for (int i = 0; i < front.length(); i += 3) {
				int ii = Math.min(i, front.last());
				for (int y = 0; y < h; y++) {
					d.set(front.x(ii), y, front.z(ii), frame.log(Direction.Axis.Y));
				}
			}
			for (int y = 0; y < h; y++) {
				d.set(front.x(front.last()), y, front.z(front.last()), frame.log(Direction.Axis.Y));
			}
			for (int i = 0; i < front.length(); i++) {
				d.set(front.x(i), h, front.z(i), beam.log(Direction.Axis.X));
			}
		}
		// back and end walls of boards to the roof
		try (var s = d.stage(StageKind.WALLS)) {
			for (Side side : new Side[]{back, Side.of(x0, z0, x1, z1, Dirs.LEFT), Side.of(x0, z0, x1, z1, Dirs.RIGHT)}) {
				for (int i = 0; i < side.length(); i++) {
					int x = side.x(i);
					int z = side.z(i);
					if (z == z0) {
						continue;
					}
					int top = h + 1 + (z - z0) / 2;
					for (int y = 0; y <= top; y++) {
						d.set(x, y, z, side.isCorner(i) ? frame.log(Direction.Axis.Y) : wall.full());
					}
				}
			}
		}
		d.carve(x0 + 1, 0, z0, x1 - 1, h - 1, z1 - 1);
		// the lean-to roof rising toward the back wall
		Roofs.Field shedRoof = Roofs.shed(x0 - 1, z0 - 1, x1 + 1, z1 + 1, Dirs.BACK, Roofs.Profile.straight(0.5));
		Roofs.raster(d, shedRoof, h, Math.max(0, x0 - 1), Math.max(0, z0 - 1), Math.min(d.frontage() - 1, x1 + 1), Math.min(d.depth() - 1, z1 + 1),
			d.mat(Role.ROOF), d.mat(Role.ROOF_TRIM), Habits.Ridge.SLAB);
		// under the roof, along the back wall of the open part: chests of stock, the work bench, the stone saw,
		// barrels, a grindstone, tools on a shelf, a lantern from the beam
		int row = z1 - 1;
		List<String> stations = new ArrayList<>(List.of("chest", "bench", "saw", "barrels", "grind", "chest2"));
		int room = ax1 - ax0 - 1;
		while (stations.size() > room) {
			stations.remove(stations.size() > 3 ? stations.size() - 2 : stations.size() - 1);
		}
		int x = ax0 + 1 + (room - stations.size()) / 2;
		for (String st : stations) {
			switch (st) {
				case "chest" -> Furniture.chest(d, x, 0, row, Dirs.FRONT, List.of(new Object[]{"minecraft:iron_axe", 1}, new Object[]{"minecraft:iron_shovel", 1},
					new Object[]{"minecraft:oak_planks", 32}, new Object[]{"minecraft:cobblestone", 32}));
				case "chest2" -> Furniture.chest(d, x, 0, row, Dirs.FRONT, List.of(new Object[]{"minecraft:stone_bricks", 48},
					new Object[]{"minecraft:glass_pane", 16}));
				case "bench" -> Furniture.workstation(d, JobType.BUILDER, x, 0, row, Dirs.FRONT);
				case "saw" -> Furniture.workstation(d, JobType.MASON, x, 0, row, Dirs.FRONT);
				case "grind" -> {
					int gx = x;
					d.inStage(StageKind.INTERIOR, () -> d.set(gx, 0, row, States.attached(Blocks.GRINDSTONE, AttachFace.FLOOR, Dirs.FRONT)));
					d.poi(PoiType.JOB_SITE, x, 0, row - 1, Dirs.BACK, JobType.BUILDER);
				}
				default -> {
					int bx = x;
					d.inStage(StageKind.INTERIOR, () -> {
						d.set(bx, 0, row, States.facing(Blocks.BARREL, Direction.UP));
						d.set(bx, 1, row, States.facing(Blocks.BARREL, Direction.UP));
					});
					Furniture.shelf(d, bx, 2, row, Dirs.FRONT, "tools");
				}
			}
			x++;
		}
		Furniture.hangingLantern(d, (ax0 + ax1) / 2, h, z0, 1);
	}

	/** The foreman's office: a closed little room with a window, a door, the plans on a lectern. */
	private static void office(Draft d, int x0, int z0, int x1, int z1) {
		Mat wall = d.mat(Role.WALL);
		Mat frame = d.mat(Role.FRAME);
		Body b = Body.single(x0, z0, x1, z1, 4);
		Facade f = Facade.plan(d, b, x0 + 1, 1, 1, 0.0);
		for (Side s : b.sides(0)) {
			Walls.side(d, b, 0, s, f, d.habits().walls().kind() == Habits.WallKind.LOG ? Habits.WallKind.LOG : Habits.WallKind.PLANK);
		}
		d.carve(x0 + 1, 0, z0 + 1, x1 - 1, 2, z1 - 1);
		Side front = b.side(0, Dirs.FRONT);
		Openings.door(d, front, x0 + 1, 0, 1, new Openings.DoorOpts(true, true, false));
		Openings.window(d, front, x0 + 2 <= x1 - 1 ? x0 + 2 : x1 - 1, 1, 1, 1, Openings.WindowStyle.PLAIN,
			new Openings.WindowOpts(false, true, true, false));
		try (var s = d.stage(StageKind.INTERIOR)) {
			d.set(x1 - 1, 0, z1 - 1, States.facing(Blocks.LECTERN, Dirs.LEFT), BlockEntities.lectern("Plans", "The Master Builder",
				List.of("The settlement's plans: what is to be built next, in what order, with what stone and timber.")));
		}
		d.poi(PoiType.LECTERN, x1 - 2, 0, z1 - 1, Dirs.RIGHT);
		Furniture.chair(d, x0 + 1, 0, z1 - 1, Dirs.BACK);
	}

	/** A fence (or wall) round the yard with a wide gate in the middle of the front. */
	private static void fence(Draft d, int w, int dp) {
		List<int[]> line = new ArrayList<>();
		for (int x = 0; x < w; x++) {
			line.add(new int[]{x, 0});
		}
		for (int z = 1; z < dp; z++) {
			line.add(new int[]{w - 1, z});
		}
		for (int x = w - 2; x >= 0; x--) {
			line.add(new int[]{x, dp - 1});
		}
		for (int z = dp - 2; z >= 1; z--) {
			line.add(new int[]{0, z});
		}
		int gate = w / 2;
		Mat fence = d.mat(Role.FENCE);
		try (var s = d.stage(StageKind.EXTERIOR)) {
			for (int[] c : line) {
				if (c[1] == 0 && (c[0] == gate || c[0] == gate - 1)) {
					d.set(c[0], 0, c[1], fence.has(Form.FENCE_GATE) ? fence.gate(Dirs.FRONT, true) : Paint.of(Blocks.AIR.defaultBlockState()));
					continue;
				}
				if (d.isAbsent(c[0], 0, c[1])) {
					d.set(c[0], 0, c[1], fence.has(Form.FENCE) ? fence.fence() : d.mat(Role.FOUNDATION).wall());
				}
			}
		}
	}

	/** Logs stacked on two bearers, lying across, pyramid-fashion. */
	private static void logStack(Draft d, int x, int z, Direction run, int length) {
		Mat log = d.mat(Role.FRAME);
		Mat bearer = d.mat(Role.BEAM);
		try (var s = d.stage(StageKind.EXTERIOR)) {
			for (int i = 0; i < length; i++) {
				int px = x;
				int pz = z + i;
				for (int k = 0; k < 3; k++) {
					d.set(px + k, 0, pz, log.log(Direction.Axis.X));
				}
				d.set(px, 1, pz, log.log(Direction.Axis.X));
				d.set(px + 1, 1, pz, log.log(Direction.Axis.X));
				if (i % 2 == 0) {
					d.set(px, 2, pz, log.log(Direction.Axis.X));
				}
			}
		}
		d.poi(PoiType.STORAGE, x + 3, 0, z, Dirs.LEFT);
	}

	/** Sawn planks stacked in a block, slabs on top, a few boards leaning. */
	private static void plankStack(Draft d, int x, int z) {
		Mat planks = d.mat(Role.FLOOR);
		try (var s = d.stage(StageKind.EXTERIOR)) {
			for (int dx = 0; dx < 2; dx++) {
				for (int dz = 0; dz < 2; dz++) {
					d.set(x + dx, 0, z + dz, planks.full());
					d.set(x + dx, 1, z + dz, (dx + dz) % 2 == 0 ? planks.full() : planks.slab(SlabType.BOTTOM));
				}
			}
			d.set(x + 2, 0, z, planks.trapdoor(Dirs.RIGHT, Half.BOTTOM, true).orElse(planks.slab(SlabType.BOTTOM)));
		}
	}

	/** Dressed stone stacked on a pallet. */
	private static void stoneStack(Draft d, int x, int z) {
		Mat stone = d.mat(Role.PLINTH);
		Mat pallet = d.mat(Role.FLOOR);
		try (var s = d.stage(StageKind.EXTERIOR)) {
			for (int dx = 0; dx < 2; dx++) {
				for (int dz = 0; dz < 2; dz++) {
					d.set(x + dx, 0, z + dz, pallet.slab(SlabType.BOTTOM));
				}
			}
			d.set(x, 1, z, stone.full());
			d.set(x + 1, 1, z, stone.full());
			d.set(x, 1, z + 1, stone.full());
			d.set(x, 2, z, stone.slab(SlabType.BOTTOM));
		}
		// the slab tops of the pallet hold the stones: use full blocks under them so nothing floats
		try (var s = d.stage(StageKind.EXTERIOR)) {
			d.set(x, 0, z, pallet.full());
			d.set(x + 1, 0, z, pallet.full());
			d.set(x, 0, z + 1, pallet.full());
		}
	}

	/** Two sawhorses with a log across them, a chopping block beside. */
	private static void sawhorses(Draft d, int x, int z) {
		Mat fence = d.mat(Role.FENCE);
		Mat log = d.mat(Role.FRAME);
		try (var s = d.stage(StageKind.EXTERIOR)) {
			d.set(x, 0, z, fence.fence());
			d.set(x + 2, 0, z, fence.fence());
			d.set(x, 1, z, log.log(Direction.Axis.X));
			d.set(x + 1, 1, z, log.log(Direction.Axis.X));
			d.set(x + 2, 1, z, log.log(Direction.Axis.X));
			d.set(x + 4, 0, z, log.strippedLog(Direction.Axis.Y));
		}
		d.poi(PoiType.JOB_SITE, x + 1, 0, z - 1, Dirs.BACK, JobType.BUILDER);
	}

	/** Scaffolding stacked ready to go up. */
	private static void scaffoldPile(Draft d, int x, int z) {
		try (var s = d.stage(StageKind.EXTERIOR)) {
			for (int k = 0; k < 3; k++) {
				d.set(x, k, z, Blocks.SCAFFOLDING.defaultBlockState().setValue(net.minecraft.world.level.block.ScaffoldingBlock.DISTANCE, 0)
					.setValue(net.minecraft.world.level.block.ScaffoldingBlock.BOTTOM, false));
			}
			d.set(x, 0, z - 1, Blocks.SCAFFOLDING.defaultBlockState().setValue(net.minecraft.world.level.block.ScaffoldingBlock.DISTANCE, 0)
				.setValue(net.minecraft.world.level.block.ScaffoldingBlock.BOTTOM, false));
		}
	}

	/**
	 * A timber crane: a mast braced on a square foot, a jib reaching out over the yard, a chain from the jib's end with a
	 * load hanging on it, and a windlass at the foot.
	 */
	private static void crane(Draft d, int x, int z, int toward, Habits h) {
		Mat mast = d.mat(Role.FRAME);
		Mat beam = d.mat(Role.BEAM);
		Mat fence = d.mat(Role.FENCE);
		int height = 9;
		Direction reach = toward > x ? Dirs.RIGHT : Dirs.LEFT;
		try (var s = d.stage(StageKind.FRAME)) {
			for (int y = -1; y < height; y++) {
				d.set(x, y, z, mast.log(Direction.Axis.Y));
			}
			// the foot: a stone pad and four stays
			for (Direction dir : Dirs.HORIZONTAL) {
				int fx = x + Dirs.dx(dir);
				int fz = z + Dirs.dz(dir);
				d.set(fx, -1, fz, d.mat(Role.FOUNDATION).full());
				d.set(fx, 0, fz, beam.stairsOrSlab(dir.getOpposite(), Half.BOTTOM));
			}
			// the jib
			int len = Math.max(3, Math.min(5, Math.abs(toward - x)));
			for (int i = 0; i <= len; i++) {
				int jx = x + Dirs.dx(reach) * i;
				d.set(jx, height, z, beam.log(reach.getAxis()));
			}
			d.set(x, height + 1, z, fence.fence());
			// a brace from the mast up to the jib
			d.set(x + Dirs.dx(reach), height - 1, z, beam.stairsOrSlab(reach.getOpposite(), Half.TOP));
			// the chain and its load
			int ex = x + Dirs.dx(reach) * len;
			for (int y = height - 1; y >= 4; y--) {
				d.set(ex, y, z, States.axis(Blocks.IRON_CHAIN, Direction.Axis.Y));
			}
			d.set(ex, 3, z, Blocks.HAY_BLOCK.defaultBlockState());
		}
		// the windlass
		try (var s = d.stage(StageKind.EXTERIOR)) {
			int wx = x - Dirs.dx(reach);
			d.set(wx, 0, z + 1 < d.depth() ? z : z, beam.log(Dirs.right(reach).getAxis()));
		}
		d.poi(PoiType.JOB_SITE, x - Dirs.dx(reach), 0, z - 1, Dirs.BACK, JobType.BUILDER);
	}
}
