package dev.voidmc.hearth.build.archetype.work;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.ArchetypeCategory;
import dev.voidmc.hearth.api.ArchetypeInfo;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.PoiType;
import dev.voidmc.hearth.api.SettlementTier;
import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.Archetype;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Mat;
import dev.voidmc.hearth.build.draft.Paint;
import dev.voidmc.hearth.build.draft.States;
import dev.voidmc.hearth.build.kit.Floors;
import dev.voidmc.hearth.build.kit.Garden;
import dev.voidmc.hearth.build.kit.trade.Floorplan;
import dev.voidmc.hearth.build.kit.Roofs;
import dev.voidmc.hearth.build.kit.Stairs;
import dev.voidmc.hearth.build.kit.trade.Hall;
import dev.voidmc.hearth.build.kit.trade.Mills;
import dev.voidmc.hearth.build.kit.trade.Props;
import dev.voidmc.hearth.build.kit.trade.Yard;
import dev.voidmc.hearth.build.style.Form;
import dev.voidmc.hearth.build.style.Habits;
import dev.voidmc.hearth.build.style.Habits.RoofForm;
import dev.voidmc.hearth.build.style.Role;
import dev.voidmc.hearth.build.terrain.Groundwork;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * {@code hearth:work/windmill}: the corn mill on the rise, a landmark seen from every road. By culture:
 * <ul>
 *   <li><b>tower mill</b>: a round tower tapering as it climbs, of the culture's masonry (whitewashed in the south,
 *   rubble on the moor, ochre on the steppe), a reefing stage round its middle, a turning cap on top — a boat-shaped
 *   wooden cap in the north and west, a cone of thatch or tile in the south — and four great sails turning before it
 *   (the Mediterranean mill's eight spars with their triangles of cloth); a smock mill's timber upper body where the
 *   culture builds in wood;</li>
 *   <li><b>post mill</b> (northern, tundra, alpine): the whole wooden body of the mill raised on a great post on its
 *   trestle, its stair running down behind to the ground, the sails on its breast.</li>
 * </ul>
 * Inside the tower, sacks of grain and flour, the millstones, a stair turning up through the floors.
 *
 * <p>Job sites: BAKER at the millstones (the miller), FARMER where the grain comes in. Storage: the flour chest.
 *
 * <p>Params: {@code kind} ({@code tower}, {@code post}), {@code sails} ({@code cross}, {@code saltire}, {@code star}),
 * {@code variant}.
 */
public final class Windmill implements Archetype {
	public static final ArchetypeInfo INFO = new ArchetypeInfo(Hearth.id("work/windmill"), ArchetypeCategory.WORKPLACE, Set.of(JobType.FARMER, JobType.BAKER),
		0, 13, 17, 11, 15, 16, 26, SettlementTier.VILLAGE, Set.of(), Set.of(ArchetypeInfo.SiteFlag.VERTICAL, ArchetypeInfo.SiteFlag.OUTSKIRTS), List.of(), 2,
		1.0);

	@Override
	public ArchetypeInfo info() {
		return INFO;
	}

	@Override
	public void draw(Draft d) {
		Habits h = d.habits();
		boolean northern = h.walls().kind() == Habits.WallKind.LOG || h.snow() || h.walls().upper() == Habits.WallKind.PLANK;
		String kind = d.param("kind", northern && d.chance(0.65) && d.depth() >= 9 ? "post" : "tower");
		if (kind.equals("post")) {
			post(d);
		} else {
			tower(d);
		}
	}

	/** Which sails: the Mediterranean star where roofs are low and walls white, otherwise crosses (now and then turned). */
	private static Mills.SailPlan plan(Draft d) {
		String p = d.param("sails", "");
		if (!p.isEmpty()) {
			try {
				return Mills.SailPlan.valueOf(p.toUpperCase(java.util.Locale.ROOT));
			} catch (IllegalArgumentException e) {
				// seeded below
			}
		}
		Habits h = d.habits();
		boolean south = h.roof().pitch() <= 0.6 || Hall.flatCulture(d);
		if (south && d.chance(0.7)) {
			return Mills.SailPlan.STAR;
		}
		return d.chance(0.3) ? Mills.SailPlan.SALTIRE : Mills.SailPlan.CROSS;
	}

	private static Paint cloth(Draft d) {
		Block white = Blocks.WOOL.pick(DyeColor.WHITE);
		Habits h = d.habits();
		if (h.walls().kind() == Habits.WallKind.ADOBE || h.roof().pitch() <= 0.6) {
			return Paint.of(white);
		}
		// canvas in the north: off-white, weathered
		return (x, y, z) -> ((x * 31 + y * 17 + z) & 3) == 0 ? Blocks.WOOL.pick(DyeColor.LIGHT_GRAY).defaultBlockState() : white.defaultBlockState();
	}

	// ------------------------------------------------------------------------------------------------ tower mill

	private static void tower(Draft d) {
		int w = d.frontage();
		int dp = d.depth();
		Habits h = d.habits();
		int max = d.maxHeight();
		double r = w >= 13 && dp >= 13 ? 3.4 : 3.0;
		// centred on a block, so the stair turns in the middle with a ring of floor round it
		int icx = (w - 1) / 2;
		double cx = icx;
		int icz = (int) Math.round(Math.max(r + 2.6, Math.min(dp - 1.6 - r, (dp - 1) / 2.0 + 1)));
		double cz = icz;
		// the sails as long as the plot is wide, the tower as tall as the sails and the cap allow
		int arm = Math.min(7, Math.min(icx, w - 1 - icx));
		int yt = Math.min(16, Math.min(max - 8, max - 3 - arm));
		if (yt < arm) {
			arm = Math.max(3, yt);
		}
		yt = Math.max(arm, yt);
		Yard.entrance(d);
		Groundwork.level(d, 0, 0, w - 1, dp - 1, 0, true);
		// materials: masonry; a smock mill's boards and corner posts above a stone base where the culture builds in timber
		Habits.WallKind wk = h.walls().kind();
		boolean timber = wk == Habits.WallKind.TIMBER_FRAME || wk == Habits.WallKind.LOG || wk == Habits.WallKind.PLANK || wk == Habits.WallKind.BAMBOO;
		Mat skirt = d.mat(Role.PLINTH);
		Mat lower = timber ? d.mat(Role.PLINTH) : d.mat(Role.WALL);
		Mat upper = timber ? d.mat(Role.GABLE) : d.mat(Role.WALL);
		Mat post = d.mat(Role.FRAME);
		Mat band = d.mat(Role.WALL_ACCENT);
		int split = timber ? Math.max(3, yt / 3) : Math.max(3, yt / 2);
		d.body((int) Math.floor(cx - r), (int) Math.floor(cz - r), (int) Math.ceil(cx + r), (int) Math.ceil(cz + r));
		d.eave(yt);
		List<int[]> ring = Draft.ellipseCells(cx, cz, r, r, false);
		List<int[]> disc = Draft.ellipseCells(cx, cz, r, r, true);
		List<int[]> skirtRing = Draft.ellipseCells(cx, cz, r + 0.7, r + 0.7, false);
		try (var s = d.stage(StageKind.FOUNDATION)) {
			for (int[] c : Draft.ellipseCells(cx, cz, r + 0.7, r + 0.7, true)) {
				int g = d.site().ground(c[0], c[1]);
				for (int y = Math.min(-1, g - 2); y <= -1; y++) {
					d.set(c[0], y, c[1], d.mat(Role.FOUNDATION).full());
				}
			}
		}
		// a battered skirt two courses high round the foot, the tower above, a band where the body changes
		try (var s = d.stage(StageKind.WALLS)) {
			for (int[] c : skirtRing) {
				if (!contains(disc, c)) {
					d.set(c[0], 0, c[1], skirt.full());
					d.set(c[0], 1, c[1], skirt.stairsOrSlab(Dirs.nearest(cx - c[0], cz - c[1]).getOpposite(), Half.BOTTOM));
				}
			}
			for (int y = 0; y <= yt; y++) {
				for (int[] c : ring) {
					Paint p;
					if (y == split) {
						p = band.full();
					} else if (y > split && timber && isTurn(ring, c)) {
						p = post.log(Direction.Axis.Y);
					} else {
						p = y < split ? lower.full() : upper.full();
					}
					d.set(c[0], y, c[1], p);
				}
			}
		}
		for (int[] c : disc) {
			if (!contains(ring, c)) {
				d.carve(c[0], 0, c[1], c[0], yt, c[1]);
			}
		}
		// floors every four blocks, a stair turning up through them
		List<Integer> floors = new ArrayList<>();
		for (int y = 4; y <= yt - 2; y += 4) {
			floors.add(y);
		}
		Mat floorMat = d.mat(Role.FLOOR);
		List<int[]> inner = Draft.ellipseCells(cx, cz, r - 1, r - 1, true);
		try (var s = d.stage(StageKind.FLOORS)) {
			for (int[] c : inner) {
				if (contains(ring, c)) {
					continue;
				}
				d.set(c[0], -1, c[1], floorMat.full());
				for (int fy : floors) {
					d.set(c[0], fy - 1, c[1], floorMat.full());
				}
			}
		}
		if (!floors.isEmpty()) {
			Stairs.spiral(d, icx, icz, 0, floors.get(floors.size() - 1), 4);
		}
		// the door at the front of the base, windows up the tower
		int dz = Integer.MAX_VALUE;
		for (int[] c : ring) {
			if (c[0] == icx) {
				dz = Math.min(dz, c[1]);
			}
		}
		int fdz = dz;
		d.inStage(StageKind.OPENINGS, () -> d.door(icx, 0, fdz, d.mat(Role.DOOR).mainOr(Form.DOOR, Blocks.SPRUCE_DOOR), Dirs.FRONT, DoorHingeSide.LEFT));
		// the skirt opens before the door
		d.inStage(StageKind.EXTERIOR, () -> {
			for (int[] c : skirtRing) {
				if (c[0] == icx && c[1] < fdz) {
					d.set(c[0], 0, c[1], Blocks.AIR.defaultBlockState());
					d.set(c[0], 1, c[1], Blocks.AIR.defaultBlockState());
					d.set(c[0], -1, c[1], d.mat(Role.PAVING).full());
				}
			}
		});
		int ez = fdz - 1;
		for (int[] c : skirtRing) {
			if (c[0] == icx && c[1] < fdz) {
				ez = Math.min(ez, c[1]);
			}
		}
		d.poi(PoiType.DOOR, icx, 0, fdz - 1, Dirs.BACK);
		d.entrance(icx, 0, fdz - 1);
		windows(d, ring, icx, icz, yt);
		if (yt >= 10) {
			stage(d, cx, cz, r, split);
		}
		// the cap: a curb at the top, a cone or a boat-shaped gable over it, the windshaft and the sails
		int capTop = cap(d, cx, cz, r, yt, h);
		int hubY = yt + 2;
		int sz = (int) Math.floor(cz - r) - 2;
		sz = Math.max(0, sz);
		Mat shaft = d.mat(Role.BEAM);
		try (var s = d.stage(StageKind.FRAME)) {
			for (int z = sz; z <= icz; z++) {
				d.set(icx, hubY, z, shaft.log(Direction.Axis.Z));
			}
		}
		int len = Math.min(arm, Math.min(hubY - 2, max - 1 - hubY));
		Mills.SailPlan sp = plan(d);
		Mat stock = Hall.woodFence(d);
		Paint lattice = stock.has(Form.FENCE) ? stock.fence() : stock.full();
		Mills.sails(d, icx, hubY, sz, Direction.Axis.Z, len, 2, sp, d.mat(Role.FRAME), cloth(d), lattice, 0.0);
		interior(d, cx, cz, r, icx, icz, fdz);
		yard(d, w, dp, icx, ez);
		Groundwork.clear(d, 0, 0, w - 1, dp - 1, Math.max(capTop, hubY + len) + 1);
	}

	/** Small windows up the tower, turning round it as the stair climbs. */
	private static void windows(Draft d, List<int[]> ring, int icx, int icz, int yt) {
		Mat glass = d.mat(Role.GLASS);
		int[][] dirs = {{-1, 0}, {0, 1}, {1, 0}, {0, -1}};
		int k = 0;
		for (int y = 2; y < yt - 1; y += 2) {
			int[] dir = dirs[k % 4];
			k++;
			if (dir[1] < 0 && y < 4) {
				continue;
			}
			int[] best = null;
			for (int[] c : ring) {
				boolean onAxis = dir[0] != 0 ? c[1] == icz && Integer.signum(c[0] - icx) == dir[0] : c[0] == icx && Integer.signum(c[1] - icz) == dir[1];
				if (onAxis && (best == null || Math.abs(c[0] - icx) + Math.abs(c[1] - icz) > Math.abs(best[0] - icx) + Math.abs(best[1] - icz))) {
					best = c;
				}
			}
			if (best == null) {
				continue;
			}
			int[] b = best;
			int yy = y;
			d.inStage(StageKind.OPENINGS, () -> d.set(b[0], yy, b[1], glass.pane()));
		}
	}

	/** A reefing stage: a ring of boards round the tower at the band, its rail of posts and a brace now and then. */
	private static void stage(Draft d, double cx, double cz, double r, int ys) {
		List<int[]> wall = Draft.ellipseCells(cx, cz, r, r, true);
		List<int[]> deck = Draft.ellipseCells(cx, cz, r + 1, r + 1, false);
		Mat boards = d.mat(Role.FLOOR);
		Mat beam = d.mat(Role.BEAM);
		Mat rail = d.mat(Role.RAILING).has(Form.FENCE) ? d.mat(Role.RAILING) : Hall.woodFence(d);
		try (var s = d.stage(StageKind.EXTERIOR)) {
			for (int[] c : deck) {
				if (contains(wall, c) || !d.allowed(c[0], ys + 1, c[1])) {
					continue;
				}
				d.set(c[0], ys, c[1], boards.slab(SlabType.TOP));
				d.set(c[0], ys + 1, c[1], rail.fence());
			}
			for (int[] c : deck) {
				boolean axis = Math.abs(c[0] - Math.round(cx)) < 0.6 || Math.abs(c[1] - Math.round(cz)) < 0.6;
				if (axis && !contains(wall, c) && d.allowed(c[0], ys - 1, c[1]) && d.isAbsent(c[0], ys - 1, c[1])) {
					Direction toward = Dirs.nearest(cx - c[0], cz - c[1]);
					d.set(c[0], ys - 1, c[1], beam.stairsOrSlab(toward.getOpposite(), Half.TOP));
				}
			}
		}
	}

	/**
	 * The cap on the tower: a curb of beams round the top, then (by culture) a cone of thatch or tile, or a boat-shaped
	 * gable running front to back. Returns the cap's top.
	 */
	private static int cap(Draft d, double cx, double cz, double r, int yt, Habits h) {
		Mat curb = d.mat(Role.BEAM);
		Mat roof = d.mat(Role.ROOF);
		try (var s = d.stage(StageKind.FRAME)) {
			for (int[] c : Draft.ellipseCells(cx, cz, r, r, true)) {
				d.set(c[0], yt + 1, c[1], curb.full());
			}
		}
		boolean cone = h.roof().pitch() <= 0.6 || Hall.flatCulture(d) || h.roof().weight(RoofForm.CONE) > 0 || h.roof().weight(RoofForm.PAGODA) > 1
			|| h.roof().thatch();
		int x0 = (int) Math.floor(cx - r) - 1;
		int x1 = (int) Math.ceil(cx + r) + 1;
		int z0 = (int) Math.floor(cz - r) - 1;
		int z1 = (int) Math.ceil(cz + r) + 1;
		Roofs.Roof rf;
		if (cone) {
			rf = Roofs.raster(d, Roofs.cone(cx, cz, r + 1, 1.3), yt + 1, Math.max(0, x0), Math.max(0, z0), Math.min(d.frontage() - 1, x1),
				Math.min(d.depth() - 1, z1), roof, d.mat(Role.ROOF_TRIM), Habits.Ridge.FULL);
			dev.voidmc.hearth.build.kit.trade.Shed.solidify(d, rf, roof);
			int ax = (int) Math.round(cx);
			int az = (int) Math.round(cz);
			if (rf.ridgeY + 1 < d.maxHeight()) {
				d.inStage(StageKind.ROOF, () -> d.set(ax, rf.ridgeY + 1, az, Hall.woodFence(d).fence()));
			}
		} else {
			// a boat-shaped cap: a gable front to back over the curb, its gable to the sails boarded
			Roofs.Field f = Roofs.gable(x0 + 1, z0, x1 - 1, z1, Direction.Axis.Z, Roofs.Profile.straight(1.0));
			rf = Roofs.raster(d, f, yt + 1, Math.max(0, x0 + 1), Math.max(0, z0), Math.min(d.frontage() - 1, x1 - 1), Math.min(d.depth() - 1, z1), roof,
				d.mat(Role.ROOF_TRIM), h.roof().ridge());
			Mat gable = d.mat(Role.GABLE);
			for (int zz : new int[]{Math.max(0, z0 + 1), Math.min(d.depth() - 1, z1 - 1)}) {
				for (int x = x0 + 1; x <= x1 - 1; x++) {
					if (!rf.covers(x, zz)) {
						continue;
					}
					for (int y = yt + 2; y < rf.bottomY(x, zz); y++) {
						d.set(x, y, zz, gable.full());
					}
				}
			}
		}
		return rf.ridgeY + 1;
	}

	/** The ground floor: the miller's stones, the grain bin, the flour chest and sacks round the stair's foot. */
	private static void interior(Draft d, double cx, double cz, double r, int icx, int icz, int fdz) {
		List<int[]> ring = Draft.ellipseCells(cx, cz, r, r, false);
		List<int[]> disc = Draft.ellipseCells(cx, cz, r, r, true);
		int x0 = (int) Math.floor(cx - r);
		int z0 = (int) Math.floor(cz - r);
		int x1 = (int) Math.ceil(cx + r);
		int z1 = (int) Math.ceil(cz + r);
		Floorplan fp = new Floorplan(x0, z0, x1, z1, 0, 3);
		for (int z = z0; z <= z1; z++) {
			for (int x = x0; x <= x1; x++) {
				int[] c = {x, z};
				if (!contains(disc, c) || contains(ring, c) || Math.abs(x - icx) <= 1 && Math.abs(z - icz) <= 1) {
					fp.block(x, z);
				}
			}
		}
		fp.keep(icx, fdz + 1);
		// the stair's first step is entered from the cell beside it
		for (int[] c : new int[][]{{icx - 2, icz + 1}, {icx + 2, icz + 1}, {icx, icz + 2}, {icx - 2, icz}}) {
			if (fp.open(c[0], c[1])) {
				fp.keep(c[0], c[1]);
				break;
			}
		}
		String[] wants = {"stones", "grain", "chest", "sacks", "sacks"};
		boolean chest = false;
		int k = 0;
		List<int[]> cells = new ArrayList<>();
		for (int[] c : disc) {
			if (fp.free(c[0], c[1])) {
				cells.add(c);
			}
		}
		cells.sort(java.util.Comparator.comparingInt((int[] c) -> -c[1]).thenComparingInt(c -> c[0]));
		for (int[] c : cells) {
			if (k >= wants.length) {
				break;
			}
			Direction user = null;
			if (!wants[k].equals("sacks")) {
				for (Direction dir : Dirs.HORIZONTAL) {
					int ux = c[0] + Dirs.dx(dir);
					int uz = c[1] + Dirs.dz(dir);
					if (fp.open(ux, uz) && fp.take(c[0], c[1], dir)) {
						user = dir;
						break;
					}
				}
				if (user == null) {
					continue;
				}
			} else if (!fp.take(c[0], c[1], null)) {
				continue;
			}
			switch (wants[k]) {
				case "stones" -> Props.grindstone(d, c[0], 0, c[1], user, JobType.BAKER);
				case "grain" -> Props.station(d, Blocks.COMPOSTER, c[0], 0, c[1], user, JobType.FARMER);
				case "chest" -> {
					Props.store(d, c[0], 0, c[1], user, true, List.of(new Object[]{"minecraft:wheat", 48}, new Object[]{"minecraft:bread", 8}));
					chest = true;
				}
				default -> Props.sacks(d, c[0], 0, c[1], Dirs.RIGHT, 1, true);
			}
			k++;
		}
		if (k < 2) {
			// too cramped inside: the stones and the grain bin by the door outside
			Props.grindstone(d, icx + 2, 0, fdz - 2, Dirs.BACK, JobType.BAKER);
			Props.station(d, Blocks.COMPOSTER, icx - 2, 0, fdz - 2, Dirs.BACK, JobType.FARMER);
		}
		if (!chest) {
			// no room for the flour chest inside: a barrel by the door
			for (int[] c : new int[][]{{icx + 2, fdz - 1}, {icx - 2, fdz - 1}, {icx + 1, fdz - 2}, {icx - 1, fdz - 2}}) {
				int ux = c[0] + (c[0] > icx ? -1 : 1);
				if (c[1] >= 0 && d.isEmpty(c[0], 0, c[1]) && d.isEmpty(c[0], 1, c[1]) && d.isEmpty(ux, 0, c[1]) && d.isEmpty(ux, 1, c[1])) {
					Props.store(d, c[0], 0, c[1], c[0] > icx ? Dirs.LEFT : Dirs.RIGHT, false, List.of(new Object[]{"minecraft:wheat", 48},
						new Object[]{"minecraft:bread", 8}));
					break;
				}
			}
		}
	}

	private static void yard(Draft d, int w, int dp, int icx, int fdz) {
		Yard.trodden(d, 0, 0, w - 1, dp - 1, 0);
		Yard.pave(d, icx, 0, icx, fdz - 1, 0);
		int sx = icx + 2;
		if (sx + 1 < w && d.isEmpty(sx, 0, fdz - 1)) {
			Props.sacks(d, sx, 0, fdz - 1, Dirs.RIGHT, Math.min(2, w - 1 - sx), true);
		}
		int cx = icx - 3;
		if (cx - 1 >= 0 && fdz >= 3 && d.isEmpty(cx, 0, 1) && d.isEmpty(cx - 1, 0, 1) && d.isEmpty(cx + 1, 0, 1) && d.isEmpty(cx, 0, 0)) {
			Props.cart(d, cx, 0, 1, Dirs.FRONT, Blocks.HAY_BLOCK.defaultBlockState());
		}
		List<int[]> gaps = new ArrayList<>();
		gaps.add(new int[]{icx, 0});
		Yard.enclose(d, 0, 0, w - 1, dp - 1, gaps);
	}

	// ------------------------------------------------------------------------------------------------ post mill

	private static void post(Draft d) {
		int w = d.frontage();
		int dp = d.depth();
		int max = d.maxHeight();
		int bw = w >= 11 ? 5 : 5;
		int bx0 = (w - bw) / 2;
		int bx1 = bx0 + bw - 1;
		int bz0 = 1;
		int bz1 = bz0 + 4;
		int yb = Math.min(4, dp - 1 - bz1);
		yb = Math.max(2, yb);
		int cx = (bx0 + bx1) / 2;
		int cz = (bz0 + bz1) / 2;
		Yard.entrance(d);
		Groundwork.level(d, 0, 0, w - 1, dp - 1, 0, true);
		// the trestle: cross-trees on stone piers, the great post, quarter bars
		Mat log = d.mat(Role.FRAME);
		Mat beam = d.mat(Role.BEAM);
		Mat pier = d.mat(Role.FOUNDATION);
		try (var s = d.stage(StageKind.FOUNDATION)) {
			for (int k = -2; k <= 2; k++) {
				if (k == 0) {
					continue;
				}
				d.set(cx + k, 0, cz, beam.log(Direction.Axis.X));
				d.set(cx, 0, cz + k, beam.log(Direction.Axis.Z));
			}
			for (int[] p : new int[][]{{cx - 2, cz}, {cx + 2, cz}, {cx, cz - 2}, {cx, cz + 2}}) {
				d.set(p[0], -1, p[1], pier.full());
			}
			for (int y = -1; y < yb - 1; y++) {
				d.set(cx, y, cz, log.log(Direction.Axis.Y));
			}
			// quarter bars rising from the cross-tree ends to the post
			for (Direction dir : Dirs.HORIZONTAL) {
				d.set(cx + Dirs.dx(dir), 1, cz + Dirs.dz(dir), beam.stairsOrSlab(dir, Half.TOP));
				if (yb >= 4) {
					d.set(cx + Dirs.dx(dir), 2, cz + Dirs.dz(dir), beam.stairsOrSlab(dir, Half.TOP));
				}
			}
		}
		// the body: a boarded box on the post, a gable front to back
		Hall hall = Hall.of(d, bx0, bz0, bx1, bz1, yb, new int[]{4, 2}, false, false);
		hall.kind = Habits.WallKind.PLANK;
		hall.facade(-1, 1, 1, 0.3);
		Floors.boards(d, bx0, bz0, bx1, bz1, yb - 1);
		// floor beams under the box, on the post's crown
		try (var s = d.stage(StageKind.FRAME)) {
			for (int x = bx0; x <= bx1; x++) {
				d.set(x, yb - 2, cz, beam.log(Direction.Axis.X));
			}
		}
		hall.stilted = true;
		d.body(bx0, bz0, bx1, bz1);
		hall.shell().openings(false);
		hall.roof(RoofForm.GABLE, Direction.Axis.Z, 1, null);
		// the back door and the stair down behind to the ground
		int[] in = hall.inner(0);
		hall.door(0, Dirs.BACK, cx, 1, false, true);
		Stairs.straight(d, cx, 0, bz1 + yb, Dirs.FRONT, yb, 1);
		// the windshaft through the breast, the sails before it
		int hubY = yb + 3;
		int sz = bz0 - 1;
		Mat shaft = d.mat(Role.BEAM);
		d.inStage(StageKind.FRAME, () -> {
			d.set(cx, hubY, sz, shaft.log(Direction.Axis.Z));
			d.set(cx, hubY, bz0, shaft.log(Direction.Axis.Z));
		});
		int len = Math.min(Math.min(cx, w - 1 - cx), Math.min(hubY - 2, max - 1 - hubY));
		Mat stock = Hall.woodFence(d);
		Mills.sails(d, cx, hubY, sz, Direction.Axis.Z, len, 2, d.chance(0.3) ? Mills.SailPlan.SALTIRE : Mills.SailPlan.CROSS, d.mat(Role.FRAME), cloth(d),
			stock.fence(), 0.0);
		// inside the box: the stones, sacks, the flour chest
		int y = yb;
		Props.grindstone(d, in[0], y, in[3] - 1, Dirs.RIGHT, JobType.BAKER);
		Props.store(d, in[2], y, in[1], Dirs.LEFT, true, List.of(new Object[]{"minecraft:wheat", 32}, new Object[]{"minecraft:bread", 6}));
		Props.sacks(d, in[0], y, in[1], Dirs.BACK, 1, true);
		// on the ground: the grain bin where the carts come in, sacks
		int gx = cx + 3 < w ? cx + 3 : cx - 3;
		Props.station(d, Blocks.COMPOSTER, gx, 0, cz + 2, gx > cx ? Dirs.LEFT : Dirs.RIGHT, JobType.FARMER);
		Props.sacks(d, gx, 0, cz + 3, Dirs.BACK, 1, false);
		Yard.trodden(d, 0, 0, w - 1, dp - 1, 0);
		List<int[]> gaps = new ArrayList<>();
		gaps.add(new int[]{cx, 0});
		Yard.enclose(d, 0, 0, w - 1, dp - 1, gaps);
		if (d.habits().snow()) {
			Garden.snowBanks(d, bx0, bz0, bx1, bz1, 0, cx);
		}
		Groundwork.clear(d, 0, 0, w - 1, dp - 1, Math.max(hall.top, hubY + len) + 1);
	}

	private static boolean containsCell(List<int[]> cells, int[] c) {
		return contains(cells, c);
	}

	private static boolean contains(List<int[]> cells, int[] c) {
		for (int[] e : cells) {
			if (e[0] == c[0] && e[1] == c[1]) {
				return true;
			}
		}
		return false;
	}

	/** A ring cell where the ring changes direction: an octagon's corner post. */
	private static boolean isTurn(List<int[]> ring, int[] c) {
		boolean e = contains(ring, new int[]{c[0] + 1, c[1]});
		boolean wv = contains(ring, new int[]{c[0] - 1, c[1]});
		boolean n = contains(ring, new int[]{c[0], c[1] + 1});
		boolean s = contains(ring, new int[]{c[0], c[1] - 1});
		return !(e && wv && !n && !s) && !(n && s && !e && !wv);
	}
}
