package dev.voidmc.hearth.build.archetype.house;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.ArchetypeCategory;
import dev.voidmc.hearth.api.ArchetypeInfo;
import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.PoiType;
import dev.voidmc.hearth.api.SettlementTier;
import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.Archetype;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Mat;
import dev.voidmc.hearth.build.draft.Noise;
import dev.voidmc.hearth.build.draft.Paint;
import dev.voidmc.hearth.build.draft.States;
import dev.voidmc.hearth.build.kit.Furniture;
import dev.voidmc.hearth.build.kit.Garden;
import dev.voidmc.hearth.build.kit.Openings;
import dev.voidmc.hearth.build.kit.Roofs;
import dev.voidmc.hearth.build.kit.Side;
import dev.voidmc.hearth.build.kit.homestead.Spots;
import dev.voidmc.hearth.build.kit.homestead.Turf;
import dev.voidmc.hearth.build.kit.homestead.Yard;
import dev.voidmc.hearth.build.style.Form;
import dev.voidmc.hearth.build.style.Habits;
import dev.voidmc.hearth.build.style.Habits.WallKind;
import dev.voidmc.hearth.build.style.Role;
import dev.voidmc.hearth.build.terrain.Groundwork;
import java.util.List;
import java.util.Set;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * {@code hearth:house/longhouse}: the long hall of the north, where a household lives round one fire. Its gable faces
 * the street behind a little gabled entry porch; its long walls bow out a block amidships like a ship's sides while the
 * ridge runs level, so the roof's planes curve; the roof is shingle, or turf where the culture banks its houses (grass
 * and moss with a wooden eave board), or thatch; carved horns cross over each gable's peak. Peeled logs notched at the
 * corners in log country, rubble stone with hipped ends in the moorland (a blackhouse). Inside, two rows of posts carry
 * tie beams under the open roof; a stone-kerbed fire pit burns down the middle under a louvred smoke hole in the ridge;
 * box beds line the side bays between the posts; at the far end the householder's high seat looks down the hall over a
 * table, with chests and banners; by the door barrels, a loom and the woodstore. Outside, the woodpile runs along a wall,
 * snow drifts against them in the tundra.
 *
 * <p>Params (all optional):
 * <ul>
 *   <li>{@code roof} (string: shingle, turf, thatch, hip): the covering and ends (default by culture and seed)</li>
 *   <li>{@code bow} (boolean): the long walls bow out amidships (default when the plot is wide enough)</li>
 * </ul>
 */
public final class Longhouse implements Archetype {
	public static final ArchetypeInfo INFO = new ArchetypeInfo(Hearth.id("house/longhouse"), ArchetypeCategory.HOUSING, Set.of(), 3,
		7, 9, 13, 21, 9, 13, SettlementTier.CAMP, Set.of(Culture.NORTHERN, Culture.TUNDRA, Culture.MOORLAND), Set.of(), List.of(), 0, 1.4);

	@Override
	public ArchetypeInfo info() {
		return INFO;
	}

	/** How the roof is covered and its ends made. */
	enum Cover {
		SHINGLE, TURF, THATCH
	}

	/** The hall's plan: its core walls, the bowed rows, levels. */
	record Hall(int x0, int z0, int x1, int z1, int bz0, int bz1, int y, int plate) {
		int left(int z) {
			return z >= this.bz0 && z <= this.bz1 ? this.x0 - 1 : this.x0;
		}

		int right(int z) {
			return z >= this.bz0 && z <= this.bz1 ? this.x1 + 1 : this.x1;
		}

		boolean bowed() {
			return this.bz1 >= this.bz0;
		}

		int midX() {
			return (this.x0 + this.x1) / 2;
		}
	}

	@Override
	public void draw(Draft d) {
		int w = d.frontage();
		int dp = d.depth();
		Habits hb = d.habits();
		boolean logs = hb.walls().kind() == WallKind.LOG;
		boolean stone = !logs && (hb.walls().kind() == WallKind.STONE || hb.walls().kind() == WallKind.PLASTER);
		// the core is 7 wide (9 on a 9-wide plot that will not bow), bowing out a block amidships when there is room
		boolean bow = d.param("bow", w >= 9);
		int bw = bow ? 7 : Math.min(w, 9);
		if (bw % 2 == 0) {
			bw--;
		}
		int x0 = (w - bw) / 2;
		int x1 = x0 + bw - 1;
		int porch = 2;
		int z0 = porch;
		int z1 = dp - 1;
		int len = z1 - z0 + 1;
		int bz0 = bow && x0 >= 1 ? z0 + Math.max(2, len / 4) : 1;
		int bz1 = bow && x0 >= 1 ? z1 - Math.max(2, len / 4) : 0;
		int wallH = hb.snow() ? 3 : 4;
		int plate = wallH - 1;
		Hall h = new Hall(x0, z0, x1, z1, bz0, bz1, 0, plate);
		d.body(x0, z0, x1, z1);
		d.eave(plate);
		Cover cover = cover(d, stone);
		boolean hipped = stone && !d.param("roof", "").equals("shingle") && d.chance(0.6) || d.param("roof", "").equals("hip");
		int door = h.midX();

		// ground: footings under the walls, the floor made up, the porch and yard levelled to the street
		ground(d, h);
		walls(d, h, logs, stone);
		Roofs.Roof roof = roof(d, h, cover, hipped);
		// gables: the triangles filled, carved horns over the peaks
		gables(d, h, roof, logs, stone, hipped);
		openings(d, h, roof, door, stone);
		porch(d, h, door, cover);
		Groundwork.approach(d, door, 0, 0);
		louver(d, h, roof);
		interior(d, h, roof, door);
		outside(d, h, door, logs);
		Groundwork.clear(d, 0, 0, w - 1, dp - 1, Math.min(d.maxHeight() - 1, roof.ridgeY + 3));
	}

	private static Cover cover(Draft d, boolean stone) {
		String p = d.param("roof", "");
		if (p.equals("turf")) {
			return Cover.TURF;
		}
		if (p.equals("thatch")) {
			return Cover.THATCH;
		}
		if (p.equals("shingle")) {
			return Cover.SHINGLE;
		}
		if (d.habits().roof().thatch()) {
			return Cover.THATCH;
		}
		boolean grassy = d.mat(Role.GROUND).main(Form.FULL) == Blocks.GRASS_BLOCK;
		return grassy && d.chance(stone ? 0.35 : 0.55) ? Cover.TURF : Cover.SHINGLE;
	}

	/** Footings under the bowed outline, the floor boarded, the ground in front levelled to the street. */
	private static void ground(Draft d, Hall h) {
		Groundwork.footings(d, h.x0, h.z0, h.x1, h.z1, h.y - 1);
		if (h.bowed()) {
			Groundwork.footings(d, h.x0 - 1, h.bz0, h.x1 + 1, h.bz1, h.y - 1);
		}
		Mat floor = d.mat(Role.FLOOR);
		try (var s = d.stage(StageKind.FLOORS)) {
			for (int z = h.z0 + 1; z < h.z1; z++) {
				for (int x = h.left(z) + 1; x < h.right(z); x++) {
					d.set(x, h.y - 1, z, floor.full());
				}
			}
		}
		if (!d.site().anyWater(0, 0, d.frontage() - 1, h.z0 - 1)) {
			Groundwork.level(d, 0, 0, d.frontage() - 1, h.z0 - 1, 0);
		}
		Groundwork.apron(d, h.x0 - 1, h.z0, h.x1 + 1, h.z1, h.y, 2);
	}

	/** The walls: peeled logs with bark corners notched past each other, or rubble stone with dressed corners. */
	private static void walls(Draft d, Hall h, boolean logs, boolean stone) {
		Mat wall = d.mat(Role.WALL);
		Mat frame = d.mat(Role.FRAME);
		Mat plinth = d.mat(Role.PLINTH);
		Mat accent = d.mat(Role.WALL_ACCENT);
		Mat gable = d.mat(Role.GABLE);
		try (var s = d.stage(StageKind.WALLS)) {
			for (int z = h.z0; z <= h.z1; z++) {
				int l = h.left(z);
				int r = h.right(z);
				boolean end = z == h.z0 || z == h.z1;
				for (int x = l; x <= r; x++) {
					boolean side = x == l || x == r;
					// where the wall steps out to the bow, the corner cell holds it closed
					boolean step = h.bowed() && (z == h.bz0 || z == h.bz1) && (x == h.x0 || x == h.x1);
					if (!end && !side && !step) {
						continue;
					}
					Direction.Axis along = end ? Direction.Axis.X : Direction.Axis.Z;
					boolean corner = end && side;
					for (int y = h.y; y <= h.plate; y++) {
						Paint p;
						if (logs) {
							if (corner || step) {
								p = frame.log(((y - h.y) & 1) == 0 ? along : other(along));
							} else if (y == h.y && d.habits().walls().plinth() > 0) {
								p = plinth.full();
							} else {
								// peeled logs: the bark taken off the wall logs, left on the corner posts
								p = wall.strippedLog(along).orElse(wall.log(along));
							}
						} else if (stone) {
							p = corner && ((y - h.y) & 1) == 0 ? accent.full() : wall.full();
						} else {
							p = corner ? frame.log(Direction.Axis.Y) : y == h.y ? plinth.full() : gable.full();
						}
						d.set(x, y, z, p);
					}
				}
			}
		}
		// the log ends run past the corners on alternate courses
		if (logs) {
			try (var s = d.stage(StageKind.WALLS)) {
				for (int[] c : new int[][]{{h.x0, h.z0, -1, -1}, {h.x1, h.z0, 1, -1}, {h.x0, h.z1, -1, 1}, {h.x1, h.z1, 1, 1}}) {
					for (int y = h.y + 1; y <= h.plate; y += 2) {
						int ox = c[0] + c[2];
						int oz = c[1] + c[3];
						if (ox >= 0 && ox < d.frontage() && d.isEmpty(ox, y, c[1])) {
							d.set(ox, y, c[1], frame.log(Direction.Axis.X));
						}
						if (oz >= 0 && oz < d.depth() && d.isEmpty(c[0], y - 1, oz) && y - 1 > h.y) {
							d.set(c[0], y - 1, oz, frame.log(Direction.Axis.Z));
						}
					}
				}
			}
		}
		for (int z = h.z0 + 1; z < h.z1; z++) {
			d.carve(h.left(z) + 1, h.y, z, h.right(z) - 1, h.plate, z);
		}
	}

	private static Direction.Axis other(Direction.Axis a) {
		return a == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X;
	}

	/**
	 * The roof: a level ridge over walls that bow, so each plane curves; hipped ends for a blackhouse. Shingle in the
	 * culture's roofing, turf of grass and moss with a wooden eave board, or the culture's thatch.
	 */
	private static Roofs.Roof roof(Draft d, Hall h, Cover cover, boolean hipped) {
		Roofs.Opts o = Roofs.Opts.of(d);
		double pitch = Math.max(0.9, o.pitch());
		int e = 1;
		int g = hipped ? 1 : 1;
		double cx = (h.x0 + h.x1) / 2.0;
		double coreHalf = (h.x1 - h.x0) / 2.0 + e;
		double ridgeH = 1 + pitch * coreHalf;
		int rz0 = Math.max(-1, h.z0 - g);
		int rz1 = Math.min(d.depth() - 1, h.z1 + g);
		int zNominal0 = h.z0 - g;
		int zNominal1 = h.z1 + g;
		Roofs.Field field = (x, z) -> {
			if (z < zNominal0 || z > zNominal1) {
				return Double.NaN;
			}
			int zz = Math.clamp(z, h.z0, h.z1);
			double left = h.left(zz) - e;
			double right = h.right(zz) + e;
			if (x < left || x > right) {
				return Double.NaN;
			}
			double half = (right - left) / 2.0;
			double dist = Math.min(x - left, right - x);
			double v = 1 + (ridgeH - 1) * dist / half;
			if (hipped) {
				double de = Math.min(z - zNominal0, zNominal1 - z);
				v = Math.min(v, 1 + pitch * de);
			}
			return v;
		};
		double hWall = 1 + (ridgeH - 1) * e / coreHalf;
		int tWall = Math.max(1, (int) Math.round(hWall * 2));
		int y0 = h.plate + 1 - ((tWall + 1) / 2 - 1);
		int rx0 = Math.max(0, h.x0 - (h.bowed() ? 2 : 1));
		int rx1 = Math.min(d.frontage() - 1, h.x1 + (h.bowed() ? 2 : 1));
		Roofs.Roof roof;
		try (var s = d.stage(StageKind.ROOF)) {
			switch (cover) {
				case TURF -> roof = Turf.raster(d, field, y0, rx0, rz0, rx1, rz1);
				case THATCH -> roof = Roofs.raster(d, field, y0, rx0, rz0, rx1, rz1, d.mat(Role.THATCH), d.mat(Role.THATCH), Habits.Ridge.FULL);
				default -> roof = Roofs.raster(d, field, y0, rx0, rz0, rx1, rz1, d.mat(Role.ROOF), d.mat(Role.ROOF_TRIM), d.habits().roof().ridge());
			}
		}
		// the hall open to the rafters
		for (int z = h.z0 + 1; z < h.z1; z++) {
			for (int x = h.left(z) + 1; x < h.right(z); x++) {
				int under = roof.bottomY(x, z);
				if (under != Integer.MIN_VALUE && under - 1 > h.plate) {
					d.carve(x, h.plate + 1, z, x, under - 1, z);
				}
			}
		}
		// the walls carried up to the roof's underside along the eaves
		Mat wall = d.mat(Role.WALL);
		boolean logs = d.habits().walls().kind() == WallKind.LOG;
		try (var s = d.stage(StageKind.WALLS)) {
			for (int z = h.z0 + 1; z < h.z1; z++) {
				for (int x : new int[]{h.left(z), h.right(z)}) {
					for (int y = h.plate + 1; y < roof.bottomY(x, z); y++) {
						d.set(x, y, z, logs ? wall.strippedLog(Direction.Axis.Z).orElse(wall.log(Direction.Axis.Z)) : wall.full());
					}
				}
			}
		}
		return roof;
	}

	/** The gable triangles filled (boards between a king post in timber, the wall's stone in stone), horns crossed over the peaks. */
	private static void gables(Draft d, Hall h, Roofs.Roof roof, boolean logs, boolean stone, boolean hipped) {
		Mat gable = d.mat(Role.GABLE);
		Mat wall = d.mat(Role.WALL);
		Mat frame = d.mat(Role.FRAME);
		Mat beam = d.mat(Role.BEAM);
		int mid = h.midX();
		for (int z : new int[]{h.z0, h.z1}) {
			try (var s = d.stage(StageKind.WALLS)) {
				for (int x = h.x0; x <= h.x1; x++) {
					int under = roof.bottomY(x, z);
					for (int y = h.plate + 1; y < under; y++) {
						Paint p;
						if (stone) {
							p = wall.full();
						} else if (x == mid) {
							p = frame.log(Direction.Axis.Y);
						} else if (y == h.plate + 1) {
							p = beam.log(Direction.Axis.X);
						} else {
							p = gable.full();
						}
						d.set(x, y, z, p);
					}
				}
			}
			if (hipped) {
				continue;
			}
			// the horns: barge boards crossing above the ridge at the gable's outer edge
			int gz = z == h.z0 ? Math.max(-1, z - 1) : Math.min(d.depth() - 1, z + 1);
			int ry = roof.surfaceY(mid, gz);
			if (ry == Integer.MIN_VALUE || ry + 2 >= d.maxHeight() || gz < 0 && ry + 1 < 3) {
				continue;
			}
			// each rake's barge board runs on past the ridge, so the two cross over the peak like horns
			try (var s = d.stage(StageKind.ROOF)) {
				int reach = ry + 2 < d.maxHeight() - 1 ? 2 : 1;
				for (int k = 1; k <= reach; k++) {
					d.set(mid - k, ry + k, gz, beam.stairsOrSlab(Dirs.LEFT, Half.BOTTOM));
					d.set(mid + k, ry + k, gz, beam.stairsOrSlab(Dirs.RIGHT, Half.BOTTOM));
				}
			}
		}
	}

	/** The door in the front gable, small windows along the long walls, a high window in the back gable. */
	private static void openings(Draft d, Hall h, Roofs.Roof roof, int door, boolean stone) {
		Side front = Side.of(h.x0, h.z0, h.x1, h.z1, Dirs.FRONT);
		Openings.door(d, front, door, h.y, 1, new Openings.DoorOpts(false, false, true));
		// small windows: one every four blocks along each long wall, at sitting height, shuttered
		for (int z = h.z0 + 3; z < h.z1 - 1; z += 4) {
			for (int side : new int[]{-1, 1}) {
				int x = side < 0 ? h.left(z) : h.right(z);
				Side s = side < 0 ? new Side(Dirs.LEFT, x, z, 1) : new Side(Dirs.RIGHT, x, z, 1);
				if (Openings.isWallBlock(d, x, h.y + 1, z) && Openings.isWallBlock(d, x, h.y + 1, z - 1) && Openings.isWallBlock(d, x, h.y + 1, z + 1)) {
					Openings.window(d, s, z, h.y + 1, 1, 1, stone ? Openings.WindowStyle.PLAIN : Openings.WindowStyle.DRESSED);
				}
			}
		}
		Side back = Side.of(h.x0, h.z0, h.x1, h.z1, Dirs.BACK);
		int bi = back.middle();
		if (roof.bottomY(back.x(bi), back.z(bi)) > h.plate + 3) {
			Openings.window(d, back, back.column(bi), h.plate + 2, 1, 1, Openings.WindowStyle.PLAIN, new Openings.WindowOpts(false, false, true, false));
		}
	}

	/** A gabled entry porch before the door: posts, a bench either side, its own little roof. */
	private static void porch(Draft d, Hall h, int door, Cover cover) {
		int px0 = door - 1;
		int px1 = door + 1;
		int pz0 = 0;
		int pz1 = h.z0 - 1;
		if (pz1 < pz0 || h.y + 4 >= d.maxHeight()) {
			return;
		}
		Mat frame = d.mat(Role.FRAME);
		Mat paving = d.mat(Role.PAVING);
		try (var s = d.stage(StageKind.FOUNDATION)) {
			for (int z = pz0; z <= pz1; z++) {
				for (int x = px0; x <= px1; x++) {
					int g = d.site().ground(x, z);
					for (int y = Math.min(g - 1, h.y - 1); y < h.y - 1; y++) {
						d.set(x, y, z, d.mat(Role.FOUNDATION).full());
					}
					d.set(x, h.y - 1, z, paving.full());
				}
			}
		}
		try (var s = d.stage(StageKind.FRAME)) {
			for (int x : new int[]{px0, px1}) {
				for (int y = h.y; y < h.y + 3; y++) {
					d.set(x, y, pz0, frame.log(Direction.Axis.Y));
				}
				d.set(x, h.y + 2, pz1, frame.log(Direction.Axis.Y));
			}
		}
		// benches between the posts along the porch's sides
		if (pz1 > pz0) {
			Furniture.chair(d, px0, h.y, pz1, Dirs.LEFT);
		}
		Roofs.Opts o = Roofs.Opts.of(d).withEaves(1, 0).withPitch(1.0);
		Roofs.Roof pr;
		try (var s = d.stage(StageKind.ROOF)) {
			Roofs.Field f = Roofs.gable(px0 - 1, pz0, px1 + 1, pz1, Direction.Axis.Z, Roofs.Profile.straight(1.0));
			pr = switch (cover) {
				case TURF -> Turf.raster(d, f, h.y + 3, Math.max(0, px0 - 1), pz0, Math.min(d.frontage() - 1, px1 + 1), pz1);
				case THATCH -> Roofs.raster(d, f, h.y + 3, Math.max(0, px0 - 1), pz0, Math.min(d.frontage() - 1, px1 + 1), pz1, d.mat(Role.THATCH),
					d.mat(Role.THATCH), Habits.Ridge.FULL);
				default -> Roofs.raster(d, f, h.y + 3, Math.max(0, px0 - 1), pz0, Math.min(d.frontage() - 1, px1 + 1), pz1, d.mat(Role.ROOF),
					d.mat(Role.ROOF_TRIM), d.habits().roof().ridge());
			};
		}
		// the porch's gable over its mouth
		try (var s = d.stage(StageKind.WALLS)) {
			for (int x = px0; x <= px1; x++) {
				for (int y = h.y + 3; y < pr.bottomY(x, pz0); y++) {
					d.set(x, y, pz0, x == door ? frame.log(Direction.Axis.Y) : d.mat(Role.GABLE).full());
				}
			}
		}
		for (int z = pz0; z <= pz1; z++) {
			for (int y = h.y; y < h.y + 3; y++) {
				if (d.isAbsent(door, y, z)) {
					d.air(door, y, z);
				}
			}
		}
		int ceil = pr.bottomY(door, pz1);
		if (ceil != Integer.MIN_VALUE && ceil - 1 > h.y + 1) {
			Furniture.hangingLantern(d, door, ceil, pz1, Math.max(0, ceil - h.y - 3));
		}
	}

	/** A louvred smoke hole over the fire: a raised cap on posts astride the ridge amidships. */
	private static void louver(Draft d, Hall h, Roofs.Roof roof) {
		int mid = h.midX();
		int z = (h.z0 + h.z1) / 2;
		int ry = roof.surfaceY(mid, z);
		if (ry + 3 >= d.maxHeight()) {
			return;
		}
		Mat frame = d.mat(Role.FRAME);
		Mat roofMat = d.mat(Role.ROOF);
		try (var s = d.stage(StageKind.ROOF)) {
			// the opening through the ridge, a cap above it on two posts
			d.set(mid, ry, z, Blocks.AIR.defaultBlockState());
			d.set(mid, ry, z + 1, frame.fence());
			d.set(mid, ry, z - 1, frame.fence());
			d.set(mid, ry + 1, z - 1, roofMat.slab(SlabType.BOTTOM).orElse(roofMat.full()));
			d.set(mid, ry + 1, z, roofMat.slab(SlabType.BOTTOM).orElse(roofMat.full()));
			d.set(mid, ry + 1, z + 1, roofMat.slab(SlabType.BOTTOM).orElse(roofMat.full()));
		}
	}

	/**
	 * Inside: two rows of posts carrying tie beams, the fire pit down the middle, box beds in the side bays, the high
	 * seat and table at the far end, the door end's stores and loom.
	 */
	private static void interior(Draft d, Hall h, Roofs.Roof roof, int door) {
		Mat frame = d.mat(Role.FRAME);
		Mat beam = d.mat(Role.BEAM);
		Mat stone = d.mat(Role.CHIMNEY);
		int mid = h.midX();
		int iz0 = h.z0 + 1;
		int iz1 = h.z1 - 1;
		int bw = h.x1 - h.x0 + 1;
		// the aisle posts: two cells in from each wall, every three blocks, up to a tie beam at the plate
		int pl = h.x0 + 2;
		int pr = h.x1 - 2;
		java.util.List<Integer> postRows = new java.util.ArrayList<>();
		for (int z = iz0 + 2; z <= iz1 - 2; z += 3) {
			postRows.add(z);
		}
		try (var s = d.stage(StageKind.FRAME)) {
			for (int z : postRows) {
				for (int x : new int[]{pl, pr}) {
					for (int y = h.y; y <= h.plate; y++) {
						d.set(x, y, z, frame.log(Direction.Axis.Y));
					}
				}
				for (int x = h.left(z) + 1; x < h.right(z); x++) {
					if (roof.bottomY(x, z) > h.plate + 1) {
						d.set(x, h.plate + 1, z, beam.log(Direction.Axis.X));
					}
				}
			}
		}
		// the fire pit amidships: a kerb of stone round a trench of embers and a fire
		int fz0 = (iz0 + iz1) / 2 - 1;
		int fz1 = fz0 + 2;
		try (var s = d.stage(StageKind.INTERIOR)) {
			for (int z = fz0; z <= fz1; z++) {
				d.set(mid, h.y - 1, z, stone.full());
				boolean fire = z == fz0 + 1;
				if (fire) {
					d.set(mid, h.y, z, States.lit(Blocks.CAMPFIRE, true).trySetValue(BlockStateProperties.SIGNAL_FIRE, false));
				} else {
					d.set(mid, h.y, z, stone.slab(SlabType.BOTTOM).orElse(stone.full()));
				}
			}
		}
		d.poi(PoiType.HEARTH, mid, h.y, fz0 - 1, Dirs.BACK);
		d.poi(PoiType.HEARTH, mid, h.y, fz1 + 1, Dirs.FRONT);
		// the far end: the high seat looking down the hall, a table before it, banners either side
		int sz = iz1;
		if (d.isEmpty(mid, h.y, sz)) {
			Furniture.chair(d, mid, h.y, sz, Dirs.BACK);
			d.poi(PoiType.SEAT, mid, h.y, sz, Dirs.FRONT);
		}
		if (iz1 - 2 > fz1 + 1) {
			Furniture.longTable(d, mid - 1, h.y, sz - 2, Dirs.RIGHT, 3);
			for (int x = mid - 1; x <= mid + 1; x += 2) {
				if (d.isEmpty(x, h.y, sz - 3) && Noise.unit(d.seed() ^ 0x5EA, x, 0, sz) < 0.8) {
					Furniture.chair(d, x, h.y, sz - 3, Dirs.FRONT);
				}
			}
		}
		// box beds in the side bays, lengthwise along the walls, between the posts
		int beds = 0;
		for (int side : new int[]{-1, 1}) {
			for (int z = iz0 + 2; z + 1 <= iz1 - 2; z++) {
				int x = side < 0 ? h.left(z) + 1 : h.right(z) - 1;
				int xn = side < 0 ? h.left(z + 1) + 1 : h.right(z + 1) - 1;
				if (x != xn || !d.isEmpty(x, h.y, z) || !d.isEmpty(x, h.y, z + 1) || postRows.contains(z) && Math.abs(x - (side < 0 ? pl : pr)) == 0) {
					continue;
				}
				// a bed whose head is against the post row in front, foot toward the back
				Block bed = d.mat(Role.CLOTH).block(Form.BED, x, h.y, z);
				if (!bed.defaultBlockState().hasProperty(BlockStateProperties.BED_PART)) {
					bed = Blocks.BED.pick(net.minecraft.world.item.DyeColor.RED);
				}
				Block b = bed;
				int bz = z;
				d.inStage(StageKind.INTERIOR, () -> d.bed(x, h.y, bz + 1, b, Dirs.FRONT));
				beds++;
				// a chest at its foot, then a gap
				int fx = x + (side < 0 ? 1 : -1);
				if (z + 2 <= iz1 - 2 && d.isEmpty(x, h.y, z + 2) && Spots.standable(d, fx, h.y, z + 2)) {
					if (Noise.unit(d.seed() ^ 0xBED, x, 0, z) < 0.5) {
						Furniture.chest(d, x, h.y, z + 2, side < 0 ? Dirs.RIGHT : Dirs.LEFT, List.of(new Object[]{"minecraft:white_wool", 2},
							new Object[]{"minecraft:bread", 3}));
					} else {
						Furniture.barrel(d, x, h.y, z + 2, Direction.UP);
					}
				}
				z += 3;
			}
		}
		Block banner = d.mat(Role.CLOTH).main(Form.WALL_BANNER);
		if (banner != null) {
			try (var s = d.stage(StageKind.INTERIOR)) {
				for (int x : new int[]{mid - 2, mid + 2}) {
					if (x > h.x0 && x < h.x1 && d.isEmpty(x, h.y + 2, iz1)) {
						d.set(x, h.y + 2, iz1, banner.defaultBlockState().trySetValue(BlockStateProperties.HORIZONTAL_FACING, Dirs.FRONT));
					}
				}
			}
		}
		// the door end: barrels, a loom, a chest of tools, the woodstore against the front wall
		try (var s = d.stage(StageKind.INTERIOR)) {
			int ez = iz0;
			for (int x = h.x0 + 1; x <= h.x1 - 1; x++) {
				if (Math.abs(x - door) <= 1 || !d.isEmpty(x, h.y, ez)) {
					continue;
				}
				double u = Noise.unit(d.seed() ^ 0xD00, x, 0, ez);
				if (u < 0.3) {
					d.set(x, h.y, ez, States.facing(Blocks.BARREL, Direction.UP));
				} else if (u < 0.5) {
					d.set(x, h.y, ez, States.facing(Blocks.LOOM, Dirs.BACK));
				} else if (u < 0.75) {
					d.set(x, h.y, ez, d.mat(Role.BEAM).log(Direction.Axis.X));
					d.set(x, h.y + 1, ez, d.mat(Role.BEAM).log(Direction.Axis.X));
				}
			}
		}
		if (d.isEmpty(h.x1 - 1, h.y, iz0 + 1) && Spots.standable(d, h.x1 - 2, h.y, iz0 + 1) && bw >= 7) {
			Furniture.chest(d, h.x1 - 1, h.y, iz0 + 1, Dirs.LEFT, List.of(new Object[]{"minecraft:iron_axe", 1}, new Object[]{"minecraft:cod", 4},
				new Object[]{"minecraft:bread", 4}));
		}
		// lanterns from the tie beams down the nave
		for (int z : postRows) {
			if (Math.abs(z - (fz0 + 1)) > 1 && d.isEmpty(mid, h.plate, z)) {
				Furniture.hangingLantern(d, mid, h.plate + 1, z, 0);
			}
		}
	}

	/** Outside: the woodpile along a long wall under the eaves, a chopping block, snow drifts in the tundra. */
	private static void outside(Draft d, Hall h, int door, boolean logs) {
		int len = Math.min(5, h.z1 - h.z0 - 4);
		boolean left = d.chance(0.5);
		int wx = left ? h.x0 - 1 : h.x1 + 1;
		if (h.bowed()) {
			wx = left ? h.x0 - 2 : h.x1 + 2;
		}
		if (len >= 2 && wx >= 0 && wx < d.frontage() && d.chance(Math.max(0.6, d.habits().yard().woodpile()))) {
			int wz = h.z0 + 2;
			int y = Spots.level(d, wx, wz);
			boolean flat = true;
			for (int k = 0; k < len; k++) {
				flat &= Spots.level(d, wx, wz + k) == y && Spots.free(d, wx, y, wz + k);
			}
			if (flat) {
				Garden.woodpile(d, wx, y, wz, Dirs.BACK, len);
				int cz = wz + len;
				if (cz < h.z1 && Spots.level(d, wx, cz) == y && Spots.free(d, wx, y, cz)) {
					Yard.choppingBlock(d, wx, y, cz, Dirs.BACK);
				}
			}
		}
		if (d.habits().snow()) {
			Garden.snowBanks(d, h.x0, h.z0, h.x1, h.z1, h.y, door);
		}
	}
}
