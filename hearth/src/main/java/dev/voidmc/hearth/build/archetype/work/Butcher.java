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
import dev.voidmc.hearth.build.draft.States;
import dev.voidmc.hearth.build.kit.Garden;
import dev.voidmc.hearth.build.kit.Openings;
import dev.voidmc.hearth.build.kit.Rooms;
import dev.voidmc.hearth.build.kit.Side;
import dev.voidmc.hearth.build.kit.craft.Craft;
import dev.voidmc.hearth.build.kit.craft.ShopHouse;
import dev.voidmc.hearth.build.kit.craft.Wares;
import dev.voidmc.hearth.build.kit.light.Lighting;
import dev.voidmc.hearth.build.kit.townhome.Proportions;
import dev.voidmc.hearth.build.kit.trade.Floorplan;
import dev.voidmc.hearth.build.kit.trade.Hall;
import dev.voidmc.hearth.build.kit.trade.Props;
import dev.voidmc.hearth.build.kit.trade.Shop;
import dev.voidmc.hearth.build.kit.trade.Signs;
import dev.voidmc.hearth.build.kit.trade.Yard;
import dev.voidmc.hearth.build.style.Form;
import dev.voidmc.hearth.build.style.Habits.RoofForm;
import dev.voidmc.hearth.build.style.Role;
import dev.voidmc.hearth.build.terrain.Groundwork;
import java.util.List;
import java.util.Set;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;

/**
 * {@code hearth:work/butcher}: the butcher's shop on the market street, in a row with its neighbours. To the street the
 * shop opens through a wide hatch: the stall board let down before it, the cuts set out on shelves in the opening, hooks
 * hanging under the oversailing upper floor (or a striped red and white awning where the front is flat). Inside, the
 * counter across the shop, the chopping blocks, shelves of meat up the walls, a brine tub and the salt barrel; the
 * salting loft and stores above under a steep roof. Behind, where the plot runs deep, the yard with the smokehouse: a
 * little masonry tower under a pyramid cap, its smoker glowing within and smoke going up from its stack; where the plot
 * is shallow the smoker stands against the back wall of the shop and its flue goes up through the roof.
 *
 * <p>Job sites (BUTCHER): the chopping block, the smoker. COUNTER where the butcher serves. Storage: the meat chest, the
 * salt barrel. Blocks only: no item frames, no animals.
 *
 * <p>Params: {@code party_left}, {@code party_right}, {@code storeys} (1–2), {@code roof}, {@code variant}.
 */
public final class Butcher implements Archetype {
	public static final ArchetypeInfo INFO = new ArchetypeInfo(Hearth.id("work/butcher"), ArchetypeCategory.WORKPLACE, Set.of(JobType.BUTCHER), 0,
		6, 9, 8, 11, 9, 14, SettlementTier.VILLAGE, Set.of(), Set.of(ArchetypeInfo.SiteFlag.ATTACHABLE), List.of(), 0, 1.0);

	private static final RoofForm[] FORMS = {RoofForm.GABLE, RoofForm.HALF_HIP, RoofForm.GAMBREL, RoofForm.PAGODA, RoofForm.FLAT};

	@Override
	public ArchetypeInfo info() {
		return INFO;
	}

	@Override
	public void draw(Draft d) {
		// two storeys in a row; standing alone on a narrow plot, no taller than half again its front (art review 22)
		int sh = Math.max(4, d.habits().walls().storeyHeight());
		int most = Proportions.storeys(d, d.frontage(), 2, sh, 2 + Proportions.rise(d, d.frontage()));
		ShopHouse s = ShopHouse.build(d, FORMS, most, 3, 6, 0.65, false);
		Hall hall = s.hall;
		boolean jettied = hall.body.outset(hall.storeys() - 1, Dirs.FRONT) > 0;
		// the sign by the door, or high on the oversailing floor where the jetty takes the low cell
		if (!Signs.bracket(d, s.front, s.signCell(), 3, "butcher", "Butcher")) {
			Side up = hall.side(Math.min(1, hall.storeys() - 1), Dirs.FRONT);
			int i = up.index(s.doorLeft ? s.door + 1 : s.door - 1);
			Signs.bracket(d, up, i, hall.floorY(1) + 1, "butcher", "Butcher");
		}
		if (s.winW > 0) {
			hatch(d, s, jettied);
		}
		// the yard and its smokehouse first, so the shop is furnished round the back door
		boolean yard = s.back >= 3 && s.backDoor() != Integer.MIN_VALUE && yard(d, s);
		interior(d, s, yard);
		upstairs(d, s);
		Yard.pave(d, 0, 0, s.w - 1, s.hz0 - 1, 0);
		Yard.connect(d, s.ex, s.door, s.hz0 - 1);
		if (hall.deck != Integer.MIN_VALUE) {
			hall.terrace(d.chance(0.5) ? Dirs.LEFT : Dirs.RIGHT, d.chance(0.7));
		}
		Groundwork.clear(d, 0, 0, s.w - 1, s.dp - 1, hall.top + 2);
	}

	/**
	 * The shop's hatch to the street: the window opening's lower row holds shelves of meat facing out, open above them,
	 * a lintel over; the stall board let down outside; hooks under the jetty or a red and white awning over it.
	 */
	private static void hatch(Draft d, ShopHouse s, boolean jettied) {
		Side f = s.front;
		Hall hall = s.hall;
		boolean masonry = Hall.masonry(d);
		Mat beam = d.mat(Role.BEAM);
		Mat accent = d.mat(Role.WALL_ACCENT);
		for (int c = s.win; c < s.win + s.winW; c++) {
			int i = f.index(c);
			int x = f.x(i);
			int z = f.z(i);
			// out with whatever the window left outside (its sill, a box, shutters)
			int ox = f.outX(i, 1);
			int oz = f.outZ(i, 1);
			for (int y = 0; y <= 2; y++) {
				if (d.get(ox, y, oz) != null) {
					d.remove(ox, y, oz);
				}
			}
			Wares.shelf(d, x, 1, z, Dirs.FRONT, Wares.meat(d, x, 1, z));
			d.inStage(StageKind.OPENINGS, () -> {
				d.set(x, 2, z, Blocks.AIR.defaultBlockState());
				d.set(x, 3, z, masonry ? accent.full().at(x, 3, z) : beam.log(f.axis()).at(x, 3, z));
			});
		}
		// the shutters folded back either side, the board let down before it
		Mat shutter = d.mat(Role.SHUTTER);
		for (int c : new int[]{s.win - 1, s.win + s.winW}) {
			if (!f.contains(c) || c == s.door) {
				continue;
			}
			int i = f.index(c);
			int ox = f.outX(i, 1);
			int oz = f.outZ(i, 1);
			if (d.isAbsent(ox, 1, oz) || d.get(ox, 1, oz) != null && !d.isSolid(ox, 1, oz)) {
				d.inStage(StageKind.OPENINGS, () -> d.set(ox, 1, oz, shutter.trapdoor(Dirs.FRONT, Half.BOTTOM, true)));
				d.inStage(StageKind.OPENINGS, () -> d.set(ox, 2, oz, shutter.trapdoor(Dirs.FRONT, Half.TOP, true)));
			}
		}
		Wares.stallBoard(d, f, s.win, s.winW, 0);
		if (jettied) {
			for (int c = s.win; c < s.win + s.winW; c += 2) {
				int i = f.index(c);
				Wares.hook(d, f.outX(i, 1), 2, f.outZ(i, 1), 0);
			}
		} else {
			awning(d, f, s.win - 1, s.win + s.winW, 3);
		}
	}

	/** The butcher's colours: a striped awning of red and white wool stairs. */
	private static void awning(Draft d, Side wall, int c0, int c1, int y) {
		Block red = Blocks.WOOL_STAIRS.pick(DyeColor.RED);
		Block white = Blocks.WOOL_STAIRS.pick(DyeColor.WHITE);
		try (var st = d.stage(StageKind.EXTERIOR)) {
			for (int c = c0; c <= c1; c++) {
				if (!wall.contains(c)) {
					continue;
				}
				int i = wall.index(c);
				int x = wall.outX(i, 1);
				int z = wall.outZ(i, 1);
				if (!d.allowed(x, y, z) || !d.isAbsent(x, y, z)) {
					continue;
				}
				d.set(x, y, z, States.stairs((c - c0) % 2 == 0 ? red : white, wall.out().getOpposite(), Half.BOTTOM));
			}
		}
	}

	/** The shop: counter, chopping block, shelves of meat, brine and salt, the meat chest; the smoker where there is no yard. */
	private static void interior(Draft d, ShopHouse s, boolean yard) {
		Hall hall = s.hall;
		int[] in = hall.inner(0);
		Floorplan fp = s.groundFloor();
		boolean left = s.doorLeft;
		// the smoker against the back wall, its flue up through the roof, where the plot leaves no room for a smokehouse
		if (!yard) {
			for (int[] c : fp.along(Dirs.BACK)) {
				if (fp.take(c[0], c[1], Dirs.FRONT)) {
					d.inStage(StageKind.INTERIOR, () -> d.set(c[0], 0, c[1], States.lit(States.facing(Blocks.SMOKER, Dirs.FRONT).getBlock(), true)
						.trySetValue(BlockStateProperties.HORIZONTAL_FACING, Dirs.FRONT)));
					d.poi(PoiType.JOB_SITE, c[0], 0, c[1] - 1, Dirs.BACK, JobType.BUTCHER);
					hall.chimney(c[0], c[1], 1);
					break;
				}
			}
		}
		// the counter across the shop two in from the hatch, the butcher behind it
		int cz = in[1] + 1;
		int cx0 = left ? s.door + 2 : in[0];
		int cx1 = left ? in[2] : s.door - 2;
		int len = 0;
		int start = Integer.MIN_VALUE;
		if (in[3] - in[1] >= 3) {
			for (int x = cx0; x <= cx1 && len < 3; x++) {
				if (fp.free(x, cz) && fp.free(x, cz + 1) && fp.take(new int[][]{{x, cz}}, null)) {
					if (start == Integer.MIN_VALUE) {
						start = x;
					}
					len++;
				} else if (len > 0) {
					break;
				}
			}
		}
		if (len >= 1) {
			int st = start;
			Shop.counter(d, st, 0, cz, Dirs.RIGHT, len, Dirs.BACK, null);
			fp.keep(st + (len - 1) / 2, cz + 1);
		}
		// the chopping block in the middle of the back of the shop, worked from the door's side
		boolean block = false;
		int bz = Math.min(in[3], cz + 2);
		for (int dx : new int[]{0, 1, -1, 2, -2}) {
			int bx = (in[0] + in[2]) / 2 + dx;
			Direction user = left ? Dirs.LEFT : Dirs.RIGHT;
			if (!block && fp.take(bx, bz, user)) {
				Mat log = d.mat(Role.FRAME);
				d.inStage(StageKind.INTERIOR, () -> d.set(bx, 0, bz, log.strippedLog(Direction.Axis.Y)));
				d.poi(PoiType.JOB_SITE, bx + Dirs.dx(user), 0, bz, user.getOpposite(), JobType.BUTCHER);
				block = true;
			}
		}
		if (!block) {
			// anywhere the floor stays connected, worked from whichever side is open
			outer:
			for (int z = in[3]; z >= in[1] + 1; z--) {
				for (int x = in[0]; x <= in[2]; x++) {
					for (Direction user : Dirs.HORIZONTAL) {
						if (fp.take(x, z, user)) {
							Mat log = d.mat(Role.FRAME);
							int bx = x;
							int bz2 = z;
							d.inStage(StageKind.INTERIOR, () -> d.set(bx, 0, bz2, log.strippedLog(Direction.Axis.Y)));
							d.poi(PoiType.JOB_SITE, x + Dirs.dx(user), 0, z + Dirs.dz(user), user.getOpposite(), JobType.BUTCHER);
							break outer;
						}
					}
				}
			}
		}
		// shelves of meat up the wall away from the door, two high; the brine tub and salt by them
		Direction wall = left ? Dirs.RIGHT : Dirs.LEFT;
		Direction into = wall.getOpposite();
		int shelves = 0;
		for (int[] c : fp.along(wall)) {
			if (c[1] <= in[1] + 1 || shelves >= 2) {
				continue;
			}
			if (fp.take(c[0], c[1], into)) {
				if (shelves == 0) {
					Props.store(d, c[0], 0, c[1], into, false, List.of(new Object[]{"minecraft:porkchop", 16}, new Object[]{"minecraft:beef", 12}));
				} else {
					d.inStage(StageKind.INTERIOR, () -> d.set(c[0], 0, c[1], Blocks.WATER_CAULDRON.defaultBlockState()
						.setValue(BlockStateProperties.LEVEL_CAULDRON, 3)));
				}
				if (d.isEmpty(c[0], 1, c[1]) && d.isSolid(c[0] + Dirs.dx(wall), 1, c[1])) {
					Wares.shelf(d, c[0], 1, c[1], into, Wares.meat(d, c[0], 1, c[1]));
				}
				if (hall.body.height(0) >= 4 && d.isEmpty(c[0], 2, c[1]) && d.isSolid(c[0] + Dirs.dx(wall), 2, c[1])) {
					Wares.shelf(d, c[0], 2, c[1], into, Wares.meat(d, c[0], 2, c[1]));
				}
				shelves++;
			}
		}
		List<Object[]> meat = List.of(new Object[]{"minecraft:porkchop", 24}, new Object[]{"minecraft:beef", 18}, new Object[]{"minecraft:mutton", 12},
			new Object[]{"minecraft:leather", 6});
		Props.storeAnywhere(d, fp, meat);
		Rooms.furnish(d, fp.room(), Rooms.Type.WORKSHOP, 0, JobType.NONE);
		Lighting.room(d, fp.room());
	}

	/** The salting loft and stores over the shop: barrels, a chest, hooks from the beams, light. */
	private static void upstairs(Draft d, ShopHouse s) {
		for (int k = 1; k < s.hall.storeys(); k++) {
			Floorplan up = s.upperFloor(k);
			if (up == null) {
				break;
			}
			int barrels = 0;
			for (int[] c : up.along(Dirs.BACK)) {
				if (barrels < 2 && up.take(c[0], c[1], null)) {
					d.inStage(StageKind.INTERIOR, () -> d.set(c[0], up.y, c[1], States.facing(Blocks.BARREL, Direction.UP)));
					barrels++;
				}
			}
			Rooms.furnish(d, up.room(), Rooms.Type.STORAGE, 0, JobType.NONE);
			Lighting.room(d, up.room());
		}
	}

	/**
	 * The back yard behind the back door: the smokehouse in the far corner (its door toward the yard), smoking wood
	 * stacked along the back, a chopping block, a lamp by the door, all laid so the way from the back door to the
	 * smokehouse stays open. Answers whether the smokehouse went in.
	 */
	private static boolean yard(Draft d, ShopHouse s) {
		int w = s.w;
		int z0 = s.hz1 + 1;
		int z1 = s.dp - 1;
		int bx = s.backDoor;
		Yard.trodden(d, 0, z0, w - 1, z1, 0);
		Floorplan yp = Craft.yardPlan(d, 0, z0, w - 1, z1);
		yp.keep(bx, z0);
		boolean houseLeft = bx > (w - 1) / 2;
		int sx0 = houseLeft ? 0 : w - 3;
		int sz0 = z1 - 2;
		Direction doorFace = houseLeft ? Dirs.RIGHT : Dirs.LEFT;
		int[][] foot = new int[9][];
		int n = 0;
		for (int z = sz0; z <= z1; z++) {
			for (int x = sx0; x <= sx0 + 2; x++) {
				foot[n++] = new int[]{x, z};
			}
		}
		int jx = sx0 + 1 + Dirs.dx(doorFace) * 2;
		int jz = sz0 + 1;
		if (sz0 < z0 || !yp.take(foot, new int[]{jx, jz})) {
			return false;
		}
		smokehouse(d, sx0, sz0, doorFace);
		// smoking wood along the back fence beyond the smokehouse's door, a chopping block by the back door
		Direction run = houseLeft ? Dirs.RIGHT : Dirs.LEFT;
		for (int k = 0; k < 2; k++) {
			int wx = jx + Dirs.dx(run) * (k + 1);
			if (wx >= 0 && wx < w && wx != bx && yp.take(wx, z1, null)) {
				Garden.woodpile(d, wx, 0, z1, run, 1);
			}
		}
		int tx = bx + (houseLeft ? -1 : 1);
		if (tx >= 0 && tx < w && yp.take(tx, z0 + 1, null)) {
			Props.choppingBlock(d, tx, 0, z0 + 1);
		}
		int lx = bx + (houseLeft ? 1 : -1);
		if (lx >= 0 && lx < w && d.isEmpty(lx, 2, z0) && d.isEmpty(lx, 3, z0)) {
			Lighting.doorLamp(d, lx, 2, z0, Dirs.FRONT);
		}
		return true;
	}

	/**
	 * The smokehouse: a three by three tower of the chimney's masonry, its door in the side {@code doorFace}, the smoker
	 * glowing in its one cell, a pyramid cap of the masonry's stairs and a short stack smoking on top. Registers the
	 * butcher's job site at its door.
	 */
	private static void smokehouse(Draft d, int x0, int z0, Direction doorFace) {
		Mat stone = d.mat(Role.CHIMNEY);
		int x1 = x0 + 2;
		int z1 = z0 + 2;
		int cx = x0 + 1;
		int cz = z0 + 1;
		int wallTop = 3;
		try (var s = d.stage(StageKind.FOUNDATION)) {
			for (int z = z0; z <= z1; z++) {
				for (int x = x0; x <= x1; x++) {
					int g = d.site().ground(x, z);
					for (int y = Math.min(-1, g - 1); y <= -1; y++) {
						d.set(x, y, z, stone.full());
					}
				}
			}
		}
		try (var s = d.stage(StageKind.WALLS)) {
			for (int[] c : Draft.ring(x0, z0, x1, z1)) {
				for (int y = 0; y <= wallTop; y++) {
					d.set(c[0], y, c[1], stone.full());
				}
			}
			d.carve(cx, 0, cz, cx, wallTop, cz);
		}
		// the cap: stairs round the head, the stack in the middle, smoke on top
		try (var s = d.stage(StageKind.ROOF)) {
			for (int[] c : Draft.ring(x0, z0, x1, z1)) {
				int ddx = Integer.signum(c[0] - cx);
				int ddz = Integer.signum(c[1] - cz);
				Direction toMid = ddx != 0 && ddz == 0 ? (ddx > 0 ? Dirs.LEFT : Dirs.RIGHT) : ddz != 0 && ddx == 0 ? (ddz > 0 ? Dirs.FRONT : Dirs.BACK) : null;
				if (toMid == null) {
					d.set(c[0], wallTop + 1, c[1], stone.slab(net.minecraft.world.level.block.state.properties.SlabType.BOTTOM));
				} else {
					d.set(c[0], wallTop + 1, c[1], stone.stairsOrSlab(toMid, Half.BOTTOM));
				}
			}
			int stackTop = Math.min(d.maxHeight() - 2, wallTop + 3);
			for (int y = wallTop + 1; y < stackTop; y++) {
				d.set(cx, y, cz, stone.full());
			}
			d.set(cx, stackTop, cz, States.lit(Blocks.CAMPFIRE, true).trySetValue(BlockStateProperties.SIGNAL_FIRE, false));
		}
		// the door and the smoker behind it
		int dx = cx + Dirs.dx(doorFace);
		int dz = cz + Dirs.dz(doorFace);
		Block door = d.mat(Role.DOOR).mainOr(Form.DOOR, Blocks.SPRUCE_DOOR);
		d.inStage(StageKind.OPENINGS, () -> d.door(dx, 0, dz, door, doorFace, net.minecraft.world.level.block.state.properties.DoorHingeSide.LEFT));
		d.inStage(StageKind.INTERIOR, () -> d.set(cx, 0, cz, States.lit(Blocks.SMOKER, true).trySetValue(BlockStateProperties.HORIZONTAL_FACING, doorFace)));
		int jx = dx + Dirs.dx(doorFace);
		int jz = dz + Dirs.dz(doorFace);
		d.poi(PoiType.JOB_SITE, jx, 0, jz, doorFace.getOpposite(), JobType.BUTCHER);
		if (Openings.isWallBlock(d, cx - Dirs.dx(doorFace), 2, cz - Dirs.dz(doorFace))) {
			// a smoke vent high in the back wall
			d.inStage(StageKind.OPENINGS, () -> d.set(cx - Dirs.dx(doorFace), 2, cz - Dirs.dz(doorFace), Blocks.IRON_BARS.defaultBlockState()));
		}
	}
}
