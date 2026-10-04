package dev.voidmc.hearth.build.archetype.house;

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
import dev.voidmc.hearth.build.kit.Body;
import dev.voidmc.hearth.build.kit.Facade;
import dev.voidmc.hearth.build.kit.Furniture;
import dev.voidmc.hearth.build.kit.Garden;
import dev.voidmc.hearth.build.kit.Openings;
import dev.voidmc.hearth.build.kit.Porches;
import dev.voidmc.hearth.build.kit.Roofs;
import dev.voidmc.hearth.build.kit.Rooms;
import dev.voidmc.hearth.build.kit.Side;
import dev.voidmc.hearth.build.style.Form;
import dev.voidmc.hearth.build.style.Habits;
import dev.voidmc.hearth.build.style.Habits.RoofForm;
import dev.voidmc.hearth.build.style.Role;
import dev.voidmc.hearth.build.terrain.Groundwork;
import dev.voidmc.hearth.build.terrain.Site;
import java.util.List;
import java.util.Set;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;

/**
 * {@code hearth:house/hut}: a one-room dwelling for camps and hamlets, the first house a settlement raises. In round
 * cultures (steppe, tropical) a round hut of plaster and posts under a cone roof; in flat-roofed ones an adobe cell with
 * a roof terrace; on stilts where the culture builds over water; elsewhere a small gabled hut with a lean-to log store.
 * Inside: a bed, a hearth, a chest, a work bench and a lantern.
 *
 * <p>Params: none.
 */
public final class Hut implements Archetype {
	public static final ArchetypeInfo INFO = new ArchetypeInfo(Hearth.id("house/hut"), ArchetypeCategory.HOUSING, Set.of(), 1,
		5, 7, 5, 7, 5, 10, SettlementTier.CAMP, Set.of(), Set.of(), List.of(Hearth.id("house/cottage")), 0, 1.0);

	@Override
	public ArchetypeInfo info() {
		return INFO;
	}

	@Override
	public void draw(Draft d) {
		Habits h = d.habits();
		boolean round = d.chance(h.plan().round()) && d.frontage() >= 5 && d.depth() >= 5;
		if (round) {
			round(d);
			return;
		}
		RoofForm form = HouseBuilder.roofParam(d, h.roof().pick(d.random(), RoofForm.GABLE, RoofForm.HIP, RoofForm.HALF_HIP, RoofForm.FLAT, RoofForm.PAGODA,
			RoofForm.SHED));
		square(d, form);
		// log walls peeled, the bark left on the corners (a northern hut reads warm, not dark)
		dev.voidmc.hearth.build.kit.homestead.Logs.peelAll(d, d.maxHeight() - 1);
	}

	/** A square hut: one room under a gable, hip, flat or curved roof. */
	private static void square(Draft d, RoofForm form) {
		int w = d.frontage();
		int dp = d.depth();
		Habits h = d.habits();
		boolean flat = form == RoofForm.FLAT;
		boolean water = d.site().anyWater(0, 0, w - 1, dp - 1);
		boolean stilted = h.plan().stilts() == Habits.Stilts.ALWAYS || water;
		// a stilted hut wants a porch in front; on a shallow plot it gets a stair along its front instead
		int front = dp >= 7 ? 2 : dp >= 6 || stilted ? 1 : 0;
		int bx0 = w >= 7 ? 1 : 0;
		int bx1 = w >= 7 ? w - 2 : w - 1;
		int bz0 = front;
		int bz1 = dp - 1;
		int base = 0;
		if (stilted) {
			for (int z = bz0; z <= bz1; z++) {
				for (int x = bx0; x <= bx1; x++) {
					int wy = d.site().water(x, z);
					base = Math.max(base, Math.max(d.site().ground(x, z) + Math.max(1, h.plan().stiltHeight()) + 1, wy == Site.NONE ? 0 : wy + 2));
				}
			}
			base = Math.min(base, front + 1);
		}
		Body body = new Body(bx0, bz0, bx1, bz1, base, new int[]{Math.max(4, h.walls().storeyHeight())}, false, false);
		d.body(bx0, bz0, bx1, bz1);
		Facade facade = Facade.plan(d, body, (bx0 + bx1) / 2, 1, 1, 0.5);
		Integer placed = HouseBuilder.doorColumn(facade);
		int door = placed != null ? placed : (bx0 + bx1) / 2;
		int plate = body.plateY();
		d.eave(plate);
		HouseBuilder.ground(d, body, front, stilted);
		HouseBuilder.shell(d, body, facade);
		dev.voidmc.hearth.build.kit.homestead.Logs.sill(d, bx0, bz0, bx1, bz1, base);
		HouseBuilder.openings(d, body, facade, !stilted);
		if (stilted && front >= 1) {
			if (front >= 2) {
				Side fw = body.side(0, Dirs.FRONT);
				Porches.porch(d, fw, bx0, bx1, 1, base, 3, door);
			}
			Groundwork.approach(d, door, base, bz0 - 1);
		}
		Roofs.Roof roof = null;
		int topY;
		if (flat) {
			topY = Roofs.flat(d, bx0, bz0, bx1, bz1, plate, true);
			// a ladder up the back wall to the roof
			if (bz1 + 1 < dp) {
				dev.voidmc.hearth.build.kit.Stairs.ladder(d, (bx0 + bx1) / 2, bz1 + 1, base, topY, Dirs.BACK);
			}
			Garden.pot(d, bx0 + 1, topY, bz0 + 1);
			flatDress(d, bx0, bz0, bx1, bz1, base, topY, door, stilted);
			topY += 2;
		} else {
			Direction.Axis ridge = bx1 - bx0 >= bz1 - bz0 ? Direction.Axis.X : Direction.Axis.Z;
			roof = Roofs.build(d, bx0, bz0, bx1, bz1, plate, form, ridge, Roofs.Opts.of(d), false, false).roof();
			topY = roof.ridgeY;
		}
		furnish(d, bx0 + 1, bz0 + 1, bx1 - 1, bz1 - 1, base, door, roof, h);
		// a woodpile or a log store against the side, a path to the door
		if (bx0 >= 1 && d.chance(h.yard().woodpile())) {
			Garden.woodpile(d, bx0 - 1, 0, bz1 - 1, Dirs.FRONT, Math.min(3, bz1 - bz0));
		}
		if (front >= 1 && !stilted) {
			Garden.path(d, Garden.line(door, bz0 - 1, door, 0), 0);
			if (front >= 2 && d.chance(0.6)) {
				Garden.garden(d, door + 1, 0, Math.min(w - 1, door + 2), bz0 - 1, 0);
			}
		}
		if (h.snow()) {
			Garden.snowBanks(d, bx0, bz0, bx1, bz1, base, door);
		}
		Groundwork.clear(d, 0, 0, w - 1, dp - 1, topY + 2);
	}

	/**
	 * What keeps a flat-roofed hut from being a box: the parapet's corners raised a course and capped (the soft massing of
	 * mud building), a shade of poles and slabs before the door, a bread oven against a side wall, pots at the door.
	 */
	private static void flatDress(Draft d, int x0, int z0, int x1, int z1, int base, int deck, int door, boolean stilted) {
		Mat wall = d.mat(Role.WALL);
		Mat accent = d.mat(Role.WALL_ACCENT);
		if (deck + 2 < d.maxHeight()) {
			try (var s = d.stage(StageKind.ROOF)) {
				for (int[] c : new int[][]{{x0, z0}, {x1, z0}, {x0, z1}, {x1, z1}}) {
					d.set(c[0], deck + 1, c[1], wall.full());
					d.set(c[0], deck + 2, c[1], accent.has(Form.SLAB) ? accent.slab(net.minecraft.world.level.block.state.properties.SlabType.BOTTOM)
						: wall.slab(net.minecraft.world.level.block.state.properties.SlabType.BOTTOM));
				}
			}
		}
		if (stilted) {
			return;
		}
		// the shade: two poles before the door, a roof of slabs back to the wall
		Mat pole = d.mat(Role.FRAME);
		int zf = z0 - 2;
		if (zf >= 0 && door - 1 > x0 - 1 && door + 1 < x1 + 1 && base + 3 < d.maxHeight()) {
			boolean clear = true;
			for (int x = door - 1; x <= door + 1; x++) {
				for (int z = zf; z < z0; z++) {
					clear &= dev.voidmc.hearth.build.kit.homestead.Spots.standable(d, x, base, z) && d.isAbsent(x, base, z);
				}
			}
			if (clear) {
				try (var s = d.stage(StageKind.FRAME)) {
					for (int x : new int[]{door - 1, door + 1}) {
						d.set(x, base, zf, pole.log(Direction.Axis.Y));
						d.set(x, base + 1, zf, pole.log(Direction.Axis.Y));
						d.set(x, base + 2, zf, pole.log(Direction.Axis.Y));
					}
				}
				try (var s = d.stage(StageKind.ROOF)) {
					for (int x = door - 1; x <= door + 1; x++) {
						for (int z = zf; z < z0; z++) {
							d.set(x, base + 3, z, pole.slab(net.minecraft.world.level.block.state.properties.SlabType.BOTTOM).orElse(pole.full()));
						}
					}
				}
			}
		}
		// a bread oven against the side wall where there is room
		Mat clay = d.mat(Role.CHIMNEY);
		for (int x : new int[]{x0 - 1, x1 + 1}) {
			int z = (z0 + z1) / 2;
			if (x >= 0 && x < d.frontage() && dev.voidmc.hearth.build.kit.homestead.Spots.standable(d, x, base, z) && d.isAbsent(x, base, z)
				&& dev.voidmc.hearth.build.kit.homestead.Spots.standable(d, x, base, z + 1) && d.isAbsent(x, base, z + 1) && z + 1 < z1) {
				try (var s = d.stage(StageKind.EXTERIOR)) {
					d.set(x, base, z, States.facing(Blocks.SMOKER, x < x0 ? Dirs.LEFT : Dirs.RIGHT));
					d.set(x, base, z + 1, clay.full());
					d.set(x, base + 1, z, clay.slab(net.minecraft.world.level.block.state.properties.SlabType.BOTTOM).orElse(clay.full()));
					d.set(x, base + 1, z + 1, clay.stairsOrSlab(Dirs.FRONT, net.minecraft.world.level.block.state.properties.Half.BOTTOM));
				}
				break;
			}
		}
		// pots either side of the door
		for (int x : new int[]{door - 1, door + 1}) {
			int z = z0 - 1;
			if (z >= 0 && dev.voidmc.hearth.build.kit.homestead.Spots.standable(d, x, base, z) && d.isAbsent(x, base, z)) {
				d.inStage(StageKind.EXTERIOR, () -> d.set(x, base, z, States.of(Blocks.DECORATED_POT)));
			}
		}
	}

	/** One room: a bed, a hearth, a chest, a bench, a lantern. */
	private static void furnish(Draft d, int x0, int z0, int x1, int z1, int y, int door, Roofs.Roof roof, Habits h) {
		Rooms.Room room = new Rooms.Room(x0, z0, x1, z1, y, 3);
		room.keep(door, z0);
		Rooms.furnish(d, room, Rooms.Type.BEDROOM, 1, JobType.NONE);
		Rooms.furnish(d, room, Rooms.Type.STORAGE, 0, JobType.NONE);
	}

	/**
	 * A round hut: a ring wall on a plinth with peeled posts at the quarters and a band of the culture's colour under a
	 * cone of thatch or tile with a pot on its point; a door to the street; inside a bed, a fire and a chest.
	 */
	private static void round(Draft d) {
		int w = d.frontage();
		int dp = d.depth();
		// an odd ring (a whole-cell centre, the round one), half a block to one side on an even frontage; its cone
		// overhangs where the plot has room for the eave
		double cx = (w & 1) == 1 ? (w - 1) / 2.0 : (w - 2) / 2.0 + (d.chance(0.5) ? 1 : 0);
		int spare = (int) Math.min(cx, w - 1 - cx);
		double r = 2.0;
		int eaves = Math.clamp(spare - 2, 0, 1);
		double cz = dp - 1 - r - eaves;
		if (cz - r - 1 < 0) {
			cz = r + 1;
		}
		dev.voidmc.hearth.build.kit.homestead.Round.Ring ring = dev.voidmc.hearth.build.kit.homestead.Round.ring(cx, cz, r);
		int base = 0;
		int plate = 2;
		d.body(ring.x0(), ring.z0(), ring.x1(), ring.z1());
		d.eave(plate);
		Mat floor = d.mat(Role.FLOOR);
		for (int[] c : ring.disc()) {
			boolean edge = ring.isWall(c[0], c[1]);
			int g = d.site().ground(c[0], c[1]);
			try (var s = d.stage(StageKind.FOUNDATION)) {
				for (int y = Math.min(-1, g - 2); y <= -1; y++) {
					d.set(c[0], y, c[1], edge ? d.mat(Role.FOUNDATION).full() : floor.full());
				}
			}
			if (!edge) {
				try (var s = d.stage(StageKind.EARTHWORK)) {
					for (int y = 0; y < g; y++) {
						d.set(c[0], y, c[1], Blocks.AIR.defaultBlockState());
					}
				}
			}
		}
		boolean painted = d.style().culture() == dev.voidmc.hearth.api.Culture.STEPPE;
		dev.voidmc.hearth.build.kit.homestead.Round.walls(d, ring, base, plate, painted
			? d.pick(dev.voidmc.hearth.build.kit.homestead.Round.Pattern.TRIANGLES, dev.voidmc.hearth.build.kit.homestead.Round.Pattern.STEPS)
			: dev.voidmc.hearth.build.kit.homestead.Round.Pattern.NONE);
		int[] door = ring.cellToward(Dirs.FRONT);
		int doorX = door[0];
		int dz = door[1];
		d.inStage(StageKind.OPENINGS, () -> d.door(doorX, 0, dz, d.mat(Role.DOOR).mainOr(Form.DOOR, Blocks.SPRUCE_DOOR), Dirs.FRONT, DoorHingeSide.LEFT));
		d.entrance(doorX, 0, dz - 1);
		d.poi(PoiType.DOOR, doorX, 0, dz - 1, Dirs.BACK);
		for (Direction dir : new Direction[]{Dirs.LEFT, Dirs.RIGHT, Dirs.BACK}) {
			int[] c = ring.cellToward(dir);
			if (c != null) {
				d.inStage(StageKind.OPENINGS, () -> d.set(c[0], 1, c[1], d.habits().windows().lattice()
					? d.mat(Role.SHUTTER).trapdoor(dir.getOpposite(), net.minecraft.world.level.block.state.properties.Half.BOTTOM, true) : d.mat(Role.GLASS).pane()));
			}
		}
		double pitch = 1.1;
		while (pitch > 0.6 && plate + 1 + pitch * (r + 1.5) + 2 >= d.maxHeight()) {
			pitch -= 0.1;
		}
		Mat cover = d.habits().roof().thatch() || d.chance(0.3) ? d.mat(Role.THATCH) : d.mat(Role.ROOF);
		Roofs.Roof roof = dev.voidmc.hearth.build.kit.homestead.Round.cone(d, ring, plate, eaves, pitch, cover, true);
		Rooms.Room room = dev.voidmc.hearth.build.kit.homestead.Round.room(ring, 0, 2);
		room.keep(doorX, dz + 1);
		if (dev.voidmc.hearth.build.kit.homestead.Beds.place(d, room, 1) == 0) {
			Rooms.furnish(d, room, Rooms.Type.BEDROOM, 1, JobType.NONE);
		}
		dev.voidmc.hearth.build.kit.homestead.Round.fire(d, ring, room, 0);
		Rooms.furnish(d, room, Rooms.Type.STORAGE, 0, JobType.NONE);
		if (dz >= 1) {
			Groundwork.approach(d, doorX, 0, dz - 1);
			Garden.path(d, Garden.line(doorX, dz - 1, doorX, 0), 0);
		}
		Groundwork.clear(d, 0, 0, w - 1, dp - 1, Math.min(d.maxHeight() - 1, roof.ridgeY + 3));
	}

	private static boolean contains(List<int[]> cells, int[] c) {
		for (int[] e : cells) {
			if (e[0] == c[0] && e[1] == c[1]) {
				return true;
			}
		}
		return false;
	}

	/** A ring cell where the ring changes direction (not a straight run): posts stand there. */
	private static boolean isTurn(List<int[]> ring, int[] c) {
		boolean e = contains(ring, new int[]{c[0] + 1, c[1]});
		boolean wv = contains(ring, new int[]{c[0] - 1, c[1]});
		boolean n = contains(ring, new int[]{c[0], c[1] + 1});
		boolean s = contains(ring, new int[]{c[0], c[1] - 1});
		return !(e && wv && !n && !s) && !(n && s && !e && !wv);
	}
}
