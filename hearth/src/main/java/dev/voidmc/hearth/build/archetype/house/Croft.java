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
import dev.voidmc.hearth.build.kit.Body;
import dev.voidmc.hearth.build.kit.Chimneys;
import dev.voidmc.hearth.build.kit.Floors;
import dev.voidmc.hearth.build.kit.Furniture;
import dev.voidmc.hearth.build.kit.Garden;
import dev.voidmc.hearth.build.kit.Openings;
import dev.voidmc.hearth.build.kit.Roofs;
import dev.voidmc.hearth.build.kit.Rooms;
import dev.voidmc.hearth.build.kit.Side;
import dev.voidmc.hearth.build.kit.Stairs;
import dev.voidmc.hearth.build.kit.homestead.Adobe;
import dev.voidmc.hearth.build.kit.homestead.Bohemia;
import dev.voidmc.hearth.build.kit.homestead.Household;
import dev.voidmc.hearth.build.kit.homestead.Sheds;
import dev.voidmc.hearth.build.kit.homestead.Spots;
import dev.voidmc.hearth.build.kit.homestead.Yard;
import dev.voidmc.hearth.build.kit.light.Lighting;
import dev.voidmc.hearth.build.kit.station.Roofwork;
import dev.voidmc.hearth.build.kit.station.Verdure;
import dev.voidmc.hearth.build.kit.townhome.Partitions;
import dev.voidmc.hearth.build.kit.townhome.Traits;
import dev.voidmc.hearth.build.kit.trade.Props;
import dev.voidmc.hearth.build.style.Form;
import dev.voidmc.hearth.build.style.Habits.RoofForm;
import dev.voidmc.hearth.build.style.Habits.WallKind;
import dev.voidmc.hearth.build.style.Role;
import dev.voidmc.hearth.build.terrain.Groundwork;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.Half;

/**
 * {@code hearth:house/croft}: a peasant smallholding (docs/LIVING_WORLD.md §8.1): one long, low house with the family
 * and the beasts under the same roof. In the Bohemian way its gable stands to the lane, boarded over a little pent, with
 * the living room's two windows in it; along its yard side the door into the hall, where the hearth is and the ladder to
 * the loft; behind, through a partition, the byre of fieldstone with its wide door to the yard, stalls with their mangers
 * and the hay over them. The yard runs along the house from a gate in the wattle fence on the lane: the kailyard of
 * cabbages and roots fenced at its front, the dung heap by the byre door, the woodpile along the boundary, a trough, the
 * hens' house at the back. The moors and the north lay the long house along the lane instead (its door and the byre's
 * in the front wall, the kailyard before it); the dry lands build it of mud under a flat roof with the beasts' pen walled
 * beside it; raised lands lift the dwelling on piles and keep the beasts in a pen under a lean-to.
 *
 * <p>Inside, the living room holds the beds (three: the family's two and the old one's, or the children's), the table in
 * its corner with benches, the chest; the hall the fire, barrels and sacks.
 *
 * <p>Params:
 * <ul>
 *   <li>{@code station}, {@code household} (shared; a herder's croft keeps more beasts, a farmer's a basket of the harvest)</li>
 *   <li>{@code along} (boolean): lay the house along the lane (default by culture)</li>
 *   <li>{@code roof} (RoofForm name): gable or half_hip (default by seed)</li>
 *   <li>{@code variant} (int)</li>
 * </ul>
 */
public final class Croft implements Archetype {
	public static final ArchetypeInfo INFO = new ArchetypeInfo(Hearth.id("house/croft"), ArchetypeCategory.HOUSING, Set.of(), 3, 9, 12, 12, 16, 9, 12,
		SettlementTier.CAMP, Set.of(), Set.of(), List.of(Hearth.id("house/cottage")), 0, 1.0);

	@Override
	public ArchetypeInfo info() {
		return INFO;
	}

	/**
	 * The long house in its own coordinates: {@code u} runs along it from the dwelling's end ({@code u = 0}) to the
	 * byre's, {@code v} across it from the yard (or front) wall ({@code v = 0}).
	 */
	record Long(int x0, int z0, int x1, int z1, Direction.Axis axis, boolean flipU, boolean flipV) {
		int len() {
			return this.axis == Direction.Axis.Z ? this.z1 - this.z0 + 1 : this.x1 - this.x0 + 1;
		}

		int wid() {
			return this.axis == Direction.Axis.Z ? this.x1 - this.x0 + 1 : this.z1 - this.z0 + 1;
		}

		int x(int u, int v) {
			return this.axis == Direction.Axis.Z ? (this.flipV ? this.x1 - v : this.x0 + v) : (this.flipU ? this.x1 - u : this.x0 + u);
		}

		int z(int u, int v) {
			return this.axis == Direction.Axis.Z ? (this.flipU ? this.z1 - u : this.z0 + u) : (this.flipV ? this.z1 - v : this.z0 + v);
		}

		int u(int x, int z) {
			return this.axis == Direction.Axis.Z ? (this.flipU ? this.z1 - z : z - this.z0) : (this.flipU ? this.x1 - x : x - this.x0);
		}

		/** The outward direction of the v = 0 wall (toward the yard or the front). */
		Direction yard() {
			return this.axis == Direction.Axis.Z ? (this.flipV ? Dirs.RIGHT : Dirs.LEFT) : (this.flipV ? Dirs.BACK : Dirs.FRONT);
		}

		/** The direction of increasing u. */
		Direction along() {
			return this.axis == Direction.Axis.Z ? (this.flipU ? Dirs.FRONT : Dirs.BACK) : (this.flipU ? Dirs.LEFT : Dirs.RIGHT);
		}

		/** The rectangle {x0, z0, x1, z1} of the cells with u in [u0, u1] and v in [v0, v1]. */
		int[] rect(int u0, int u1, int v0, int v1) {
			int ax = x(u0, v0);
			int az = z(u0, v0);
			int bx = x(u1, v1);
			int bz = z(u1, v1);
			return new int[]{Math.min(ax, bx), Math.min(az, bz), Math.max(ax, bx), Math.max(az, bz)};
		}

		/** The wall of the body facing {@code out}. */
		Side side(Direction out) {
			return Side.of(this.x0, this.z0, this.x1, this.z1, out);
		}
	}

	@Override
	public void draw(Draft d) {
		raise(d);
		dev.voidmc.hearth.build.kit.townhome.Settle.finish(d);
	}

	/** The house as drawn, before its last walk-round ({@link dev.voidmc.hearth.build.kit.townhome.Settle}). */
	private void raise(Draft d) {
		Traits t = Traits.of(d);
		if (t.flat()) {
			flat(d, t);
			return;
		}
		pitched(d, t);
	}

	/** Cultures that lay the long house along the lane (the blackhouse, the north's long house). */
	private static boolean alongLane(Draft d) {
		Culture c = d.style().culture() == null ? Culture.TEMPERATE : d.style().culture();
		boolean dflt = c == Culture.MOORLAND || c == Culture.NORTHERN || c == Culture.TUNDRA || c == Culture.MARITIME;
		return d.param("along", dflt);
	}

	// ----------------------------------------------------------------------------------------------- pitched

	private static void pitched(Draft d, Traits t) {
		int w = d.frontage();
		int dp = d.depth();
		boolean along = alongLane(d);
		boolean raised = t.raised();
		Long L;
		int front;
		if (!along) {
			// gable to the lane: the house down one side of the plot, the yard down the other
			int bw = w >= 11 ? 7 : 6;
			boolean yardLeft = d.chance(0.5);
			front = dp >= 15 ? 2 : 1;
			int back = dp - front >= 14 ? 1 : 0;
			int x0 = yardLeft ? w - bw : 0;
			L = new Long(x0, front, x0 + bw - 1, dp - 1 - back, Direction.Axis.Z, false, !yardLeft);
		} else {
			// along the lane: the kailyard before it, a strip of yard behind
			int bd = dp >= 15 ? 7 : 6;
			int back = 2;
			int z1 = dp - 1 - back;
			front = z1 - bd + 1;
			boolean dwellLeft = d.chance(0.5);
			L = new Long(0, front, w - 1, z1, Direction.Axis.X, !dwellLeft, false);
		}
		int len = L.len();
		int wid = L.wid();
		// rooms along the house: living | hall | byre (the hall merged into the living room on a short house)
		boolean hall = len >= 13;
		// a living room five long inside where the house is short, the byre taking what is left (two to four)
		int p1 = hall ? 5 : len - 2 - Math.clamp(len - 8, 2, 4);
		int p2 = hall ? 8 : p1;
		// raised a step over the yard (the beasts' end and the yard's work want the floor near the ground), more over water
		boolean wet = d.site().anyWater(L.x0(), L.z0(), L.x1(), L.z1());
		int base = raised ? StationHomes.raisedBase(d, L.x0(), L.z0(), L.x1(), L.z1(), 1) : 0;
		Body body = new Body(L.x0(), L.z0(), L.x1(), L.z1(), base, new int[]{4}, false, false);
		d.body(L.x0(), L.z0(), L.x1(), L.z1());
		int plate = body.plateY();
		d.eave(plate);

		// ground
		if (raised) {
			Groundwork.stilts(d, L.x0(), L.z0(), L.x1(), L.z1(), base - 1, 2);
			Floors.boards(d, L.x0(), L.z0(), L.x1(), L.z1(), base - 1);
		} else {
			Groundwork.footings(d, L.x0(), L.z0(), L.x1(), L.z1(), base - 1);
			int[] dwell = L.rect(1, p2 - 1, 1, wid - 2);
			Floors.boards(d, dwell[0], dwell[1], dwell[2], dwell[3], base - 1);
			int[] byre = L.rect(p2 + 1, len - 2, 1, wid - 2);
			StationHomes.earthFloor(d, byre[0], byre[1], byre[2], byre[3], base - 1);
			Groundwork.apron(d, L.x0(), L.z0(), L.x1(), L.z1(), base, 2);
		}

		// walls: the dwelling in the culture's walling, the byre in fieldstone (logs in log lands), partitions between
		walls(d, body, L, p2, t);
		d.carve(L.x0() + 1, base, L.z0() + 1, L.x1() - 1, plate, L.z1() - 1);
		Mat inner = d.mat(Role.WALL);
		partition(d, L, p2, base, plate, t);
		if (hall) {
			partition(d, L, p1, base, plate, t);
		}

		// openings: the house door into the hall from the yard, the byre's double door, windows
		int doorU = hall ? p1 + 1 + (p2 - p1 - 1) / 2 : p1 - 1;
		Side yardWall = L.side(L.yard());
		int doorX = L.x(doorU, 0);
		int doorZ = L.z(doorU, 0);
		Openings.door(d, yardWall, yardWall.column(yardWall.index(L.axis() == Direction.Axis.Z ? doorZ : doorX)), base, 1,
			new Openings.DoorOpts(false, false, true));
		int byreU = p2 + 1 + Math.max(0, (len - 2 - p2 - 2) / 2);
		byreDoor(d, L, yardWall, byreU, base);
		windows(d, L, p1, p2, hall, base, plate, doorU);

		// the roof: long and steep, thatched or shingled, half-hipped as often as gabled
		RoofForm form = HouseBuilder.roofParam(d, d.pick(RoofForm.GABLE, RoofForm.HALF_HIP, RoofForm.HALF_HIP));
		if (t.eastern()) {
			form = RoofForm.HIP;
		}
		Mat thatch = d.mat(Role.THATCH);
		Mat cover = d.habits().roof().thatch() ? d.mat(Role.ROOF)
			: thatch.main(Form.FULL) == Blocks.HAY_BLOCK && thatch.has(Form.STAIRS) && d.chance(0.45) ? thatch : d.mat(Role.ROOF);
		Direction.Axis ridge = L.axis();
		int eaves = raised ? 2 : 1;
		double pitch = StationHomes.hutPitch(d, L.x0(), L.z0(), L.x1(), L.z1(), plate, form, ridge, 2.0, eaves, 1);
		Roofs.Roof roof = StationHomes.hutRoof(d, L.x0(), L.z0(), L.x1(), L.z1(), plate, form, ridge, pitch, eaves, cover, null, 0);
		Bohemia.carryRidge(d, roof);
		boolean straw = cover.main(Form.FULL) == Blocks.HAY_BLOCK;
		if (straw) {
			Roofwork.thick(d, roof, cover);
		}
		int[] r = {L.x0(), L.z0(), L.x1(), L.z1()};
		if (form != RoofForm.HIP) {
			Paint gable = t.walls() == WallKind.LOG ? d.mat(Role.WALL).log(ridge == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X)
				: t.masonry() && t.walls() != WallKind.PLASTER ? d.mat(Role.WALL).full() : d.mat(Role.GABLE).full();
			StationHomes.gableFill(d, roof, ridge, r, plate, gable);
			if (Bohemia.is(d)) {
				Bohemia.gables(d, roof, ridge, r, plate);
			}
			Roofwork.verges(d, roof, ridge, d.mat(Role.ROOF_TRIM));
			hayDoor(d, L, roof, plate);
		}
		Roofwork.brackets(d, roof, r, ridge, d.mat(Role.BEAM), form == RoofForm.HIP);

		// the hayloft over the byre, the hall's ladder up to it
		loft(d, L, p2, plate, roof);

		// the hearth in the hall (or the living room's back corner) with its chimney through the roof
		int hu = hall ? p1 + 1 : p1 - 1;
		int hv = wid - 2;
		int hx = L.x(hu, hv);
		int hz = L.z(hu, hv);
		Furniture.hearth(d, hx, base, hz, L.yard(), false);
		Chimneys.stack(d, hx, hz, base + 2, roof, Math.min(d.maxHeight() - 1, roof.ridgeY + 1));

		// rooms
		Rooms.Room living = interior(d, L, p1, p2, hall, base, hx, hz, doorU);
		sleepsThree(d, living, byre(d, L, p2, byreU, base, plate));

		// the yard, the kailyard, the lane
		if (!along) {
			yardAlong(d, L, front, doorU, byreU, base);
		} else {
			yardBefore(d, L, front, doorU, byreU, base);
		}
		Lighting.doorLamp(d, L.x(doorU + 1, -1), base + 2, L.z(doorU + 1, -1), L.yard().getOpposite());
		if (!raised) {
			Verdure.foot(d, L.x0(), L.z0(), L.x1(), L.z1(), 0.35);
		}
		Groundwork.clear(d, 0, 0, w - 1, dp - 1, roof.ridgeY + 3);
	}

	/**
	 * The long house's walls: the dwelling end (u up to the byre partition) in the culture's walling on a fieldstone
	 * plinth, timber posts at its corners and at the partition where the culture frames; the byre in rubble fieldstone
	 * with dressed corners (logs where the culture builds in logs, mud where in mud).
	 */
	private static void walls(Draft d, Body b, Long L, int p2, Traits t) {
		WallKind kind = t.walls();
		Mat wall = d.mat(Role.WALL);
		Mat frame = d.mat(Role.FRAME);
		Mat beam = d.mat(Role.BEAM);
		Mat found = d.mat(Role.FOUNDATION);
		Mat accent = d.mat(Role.WALL_ACCENT);
		Mat gable = d.mat(Role.GABLE);
		boolean framed = kind == WallKind.TIMBER_FRAME || kind == WallKind.PLASTER || kind == WallKind.PLANK || kind == WallKind.BAMBOO;
		boolean stoneByre = kind != WallKind.LOG && kind != WallKind.BAMBOO && kind != WallKind.ADOBE;
		int ya = b.baseY;
		int yg = b.plateY();
		for (Side s : b.sides(0)) {
			Direction.Axis axis = s.axis();
			try (var st = d.stage(StageKind.WALLS)) {
				for (int i = 0; i < s.length(); i++) {
					int x = s.x(i);
					int z = s.z(i);
					int u = L.u(x, z);
					boolean byre = u > p2;
					boolean corner = s.isCorner(i);
					boolean post = framed && (corner || u == p2);
					for (int y = ya; y <= yg; y++) {
						Paint p;
						if (byre && stoneByre) {
							// rubble fieldstone, the corners dressed course and course about, a beam at the head
							p = corner && ((y - ya) & 1) == 0 ? accent.full() : y == yg ? beam.log(axis) : found.full();
						} else if (kind == WallKind.LOG) {
							p = corner ? frame.log((y & 1) == 0 ? axis : StationHomes.other(axis)) : y == ya ? found.full() : wall.log(axis);
						} else if (y == ya) {
							p = found.full();
						} else if (post) {
							p = frame.log(Direction.Axis.Y);
						} else if (y == yg && framed) {
							p = beam.log(axis);
						} else if (kind == WallKind.PLANK) {
							p = gable.full();
						} else {
							p = wall.full();
						}
						d.set(x, y, z, p);
					}
				}
			}
		}
	}

	/** A partition across the house at u = {@code p}, from floor to plate, with a door in its middle. */
	private static void partition(Draft d, Long L, int p, int base, int plate, Traits t) {
		int wid = L.wid();
		int dv = (wid - 1) / 2;
		Mat m = t.walls() == WallKind.LOG ? d.mat(Role.WALL) : t.masonry() ? d.mat(Role.WALL) : d.mat(Role.GABLE);
		Direction.Axis acrossAxis = L.axis() == Direction.Axis.Z ? Direction.Axis.X : Direction.Axis.Z;
		try (var s = d.stage(StageKind.WALLS)) {
			for (int v = 1; v <= wid - 2; v++) {
				int x = L.x(p, v);
				int z = L.z(p, v);
				for (int y = base; y < plate; y++) {
					if (v == dv && y < base + 2) {
						continue;
					}
					d.set(x, y, z, t.walls() == WallKind.LOG ? m.log(acrossAxis) : m.full());
				}
				d.set(x, plate, z, d.mat(Role.BEAM).log(acrossAxis));
			}
		}
		Partitions.door(d, L.x(p, dv), base, L.z(p, dv), L.along());
	}

	/** The byre's wide door: two leaves in the yard wall, a beam over them. */
	private static void byreDoor(Draft d, Long L, Side wall, int u, int base) {
		Block door = d.mat(Role.DOOR).mainOr(Form.DOOR, Blocks.SPRUCE_DOOR);
		Direction out = L.yard();
		for (int k = 0; k < 2; k++) {
			int x = L.x(u + k, 0);
			int z = L.z(u + k, 0);
			DoorHingeSide hinge = k == 0 ? DoorHingeSide.RIGHT : DoorHingeSide.LEFT;
			if (L.flipU() ^ (out == Dirs.RIGHT || out == Dirs.BACK)) {
				hinge = k == 0 ? DoorHingeSide.LEFT : DoorHingeSide.RIGHT;
			}
			DoorHingeSide hg = hinge;
			d.inStage(StageKind.OPENINGS, () -> d.door(x, base, z, door, out, hg));
			d.set(x, base + 2, z, d.mat(Role.BEAM).log(L.along().getAxis()));
		}
		int ex = L.x(u, -1);
		int ez = L.z(u, -1);
		d.entrance(ex, base, ez);
	}

	/** The windows: two in the dwelling's end wall (the gable to the lane), one or two along the sides, a hatch in the byre. */
	private static void windows(Draft d, Long L, int p1, int p2, boolean hall, int base, int plate, int doorU) {
		int wid = L.wid();
		Side end = L.side(L.along().getOpposite());
		Side yardWall = L.side(L.yard());
		Side farWall = L.side(L.yard().getOpposite());
		var style = d.habits().windows().lattice() ? Openings.WindowStyle.LATTICE : Openings.WindowStyle.DRESSED;
		// the end wall: two windows, symmetric
		if (wid >= 6) {
			for (int i : new int[]{1, end.last() - 1}) {
				Openings.window(d, end, end.column(i), base + 1, 1, 2, style);
			}
		} else {
			Openings.window(d, end, end.column(end.middle()), base + 1, 1, 2, style);
		}
		// the living room's side windows, a hall window opposite the door
		int lu = Math.max(2, (p1 - 1) / 2 + 1);
		windowAt(d, yardWall, L, lu, 0, base + 1, style);
		windowAt(d, farWall, L, lu, wid - 1, base + 1, style);
		if (hall) {
			windowAt(d, farWall, L, doorU, wid - 1, base + 1, style);
		}
		// the byre: small hatches
		int bu = p2 + 2;
		if (bu < L.len() - 2) {
			int x = L.x(bu, wid - 1);
			int z = L.z(bu, wid - 1);
			if (Openings.isWallBlock(d, x, base + 2, z)) {
				Hovel.hatch(d, farWall, farWall.index(farWall.axis() == Direction.Axis.X ? x : z), base + 2);
			}
		}
	}

	private static void windowAt(Draft d, Side s, Long L, int u, int v, int y, Openings.WindowStyle style) {
		int x = L.x(u, v);
		int z = L.z(u, v);
		int col = s.axis() == Direction.Axis.X ? x : z;
		int i = s.index(col);
		if (i <= 0 || i >= s.last() || !Openings.isWallBlock(d, x, y, z) || !Openings.isWallBlock(d, x, y + 1, z)) {
			return;
		}
		Openings.window(d, s, col, y, 1, 2, style);
	}

	/** The hay door high in the byre's end gable, a bale showing in it. */
	private static void hayDoor(Draft d, Long L, Roofs.Roof roof, int plate) {
		int u = L.len() - 1;
		int v = (L.wid() - 1) / 2;
		int x = L.x(u, v);
		int z = L.z(u, v);
		if (!roof.covers(x, z) || roof.bottomY(x, z) - (plate + 1) < 2) {
			return;
		}
		Direction out = L.along();
		try (var s = d.stage(StageKind.OPENINGS)) {
			d.set(x, plate + 1, z, Blocks.HAY_BLOCK.defaultBlockState().trySetValue(BlockStateProperties.AXIS, out.getAxis()));
			d.set(x, plate + 2, z, d.mat(Role.SHUTTER).trapdoor(out, Half.TOP, true));
		}
	}

	/** Boards over the byre at the plate (the hayloft), bales stacked on them, a ladder up from the byre floor. */
	private static void loft(Draft d, Long L, int p2, int plate, Roofs.Roof roof) {
		int len = L.len();
		int wid = L.wid();
		if (len - 2 - p2 < 3) {
			return;
		}
		int[] lr = L.rect(p2 + 1, len - 2, 1, wid - 2);
		try (var s = d.stage(StageKind.FLOORS)) {
			for (int z = lr[1]; z <= lr[3]; z++) {
				for (int x = lr[0]; x <= lr[2]; x++) {
					d.set(x, plate, z, d.mat(Role.FLOOR).full());
				}
			}
		}
		// the ladder's hole by the partition on the far side
		int lu = p2 + 1;
		int lv = wid - 2;
		int lx = L.x(lu, lv);
		int lz = L.z(lu, lv);
		d.set(lx, plate, lz, Blocks.AIR.defaultBlockState());
		Stairs.ladder(d, lx, lz, plate - 3, plate, L.along().getOpposite());
		// hay stacked against the far end, under the roof
		long seed = Noise.hash(d.seed(), 0x4A11);
		try (var s = d.stage(StageKind.INTERIOR)) {
			for (int u = len - 3; u <= len - 2; u++) {
				for (int v = 1; v <= wid - 2; v++) {
					int x = L.x(u, v);
					int z = L.z(u, v);
					int under = roof.covers(x, z) ? roof.bottomY(x, z) : plate + 1;
					int h = Noise.unit(seed, x, 0, z) < 0.5 ? 2 : 1;
					for (int y = plate + 1; y <= plate + h && y < under; y++) {
						d.set(x, y, z, Blocks.HAY_BLOCK.defaultBlockState());
					}
				}
			}
		}
	}

	/**
	 * The dwelling's rooms: the living room at the end with the beds, the table and its benches; the hall with the hearth,
	 * barrels and sacks. The household's trade dresses them.
	 */
	private static Rooms.Room interior(Draft d, Long L, int p1, int p2, boolean hall, int base, int hx, int hz, int doorU) {
		int wid = L.wid();
		int dv = (wid - 1) / 2;
		int[] lr = L.rect(1, p1 - 1, 1, wid - 2);
		Rooms.Room living = new Rooms.Room(lr[0], lr[1], lr[2], lr[3], base, 3);
		// the door through the partition, the house door when it opens into this room
		int pu = hall ? p1 - 1 : p2 - 1;
		living.keep(L.x(pu, dv), L.z(pu, dv));
		if (!hall) {
			living.keep(L.x(doorU, 1), L.z(doorU, 1));
			living.block(hx, hz);
			living.keep(hx + Dirs.dx(L.yard()), hz + Dirs.dz(L.yard()));
		}
		int beds = dev.voidmc.hearth.build.kit.homestead.Beds.place(d, living, hall ? 3 : 3);
		if (beds < 3) {
			TownHouses.bedroom(d, living, 3 - beds);
		}
		Household.first(d, living, new int[]{hx, hz});
		Rooms.furnish(d, living, Rooms.Type.LIVING, 0, JobType.NONE);
		Lighting.room(d, living);
		Rooms.Room kitchen = living;
		if (hall) {
			int[] hr = L.rect(p1 + 1, p2 - 1, 1, wid - 2);
			kitchen = new Rooms.Room(hr[0], hr[1], hr[2], hr[3], base, 3);
			kitchen.keep(L.x(doorU, 1), L.z(doorU, 1));
			kitchen.keep(L.x(p1 + 1, dv), L.z(p1 + 1, dv));
			kitchen.keep(L.x(p2 - 1, dv), L.z(p2 - 1, dv));
			kitchen.block(hx, hz);
			kitchen.keep(hx + Dirs.dx(L.yard()), hz + Dirs.dz(L.yard()));
			Rooms.furnish(d, kitchen, Rooms.Type.KITCHEN, 0, JobType.NONE);
			Lighting.room(d, kitchen);
		}
		Household.dress(d, kitchen, living, new int[]{hx, hz});
		return living;
	}

	/**
	 * The byre: stalls along the far wall (fence partitions, a manger of hay at the head of each, the trough), straw on
	 * the floor, a lantern; the way from its door to the partition kept clear.
	 */
	private static Rooms.Room byre(Draft d, Long L, int p2, int byreU, int base, int plate) {
		int len = L.len();
		int wid = L.wid();
		int u0 = p2 + 1;
		int u1 = len - 2;
		if (u1 - u0 < 1) {
			return null;
		}
		int dv = (wid - 1) / 2;
		Mat fence = d.mat(Role.FENCE);
		long seed = Noise.hash(d.seed(), 0xB12E);
		try (var s = d.stage(StageKind.INTERIOR)) {
			for (int u = u0; u <= u1; u++) {
				int fv = wid - 2;
				int x = L.x(u, fv);
				int z = L.z(u, fv);
				if (!d.isEmpty(x, base, z)) {
					continue;
				}
				// the mangers along the far wall: hay and the trough
				boolean trough = (u - u0) % 3 == 1;
				if (trough) {
					d.set(x, base, z, Blocks.WATER_CAULDRON.defaultBlockState().setValue(BlockStateProperties.LEVEL_CAULDRON, 3));
				} else {
					d.set(x, base, z, Blocks.HAY_BLOCK.defaultBlockState());
				}
				// stall partitions every third cell, reaching in from the far wall
				if ((u - u0) % 3 == 2 && wid >= 6) {
					int px = L.x(u, fv - 1);
					int pz = L.z(u, fv - 1);
					if (d.isEmpty(px, base, pz) && u + 1 <= u1) {
						d.set(px, base, pz, fence.fence());
					}
				}
			}
			// straw on the floor, here and there
			for (int u = u0; u <= u1; u++) {
				for (int v = 1; v <= wid - 3; v++) {
					int x = L.x(u, v);
					int z = L.z(u, v);
					if (d.isEmpty(x, base, z) && Noise.unit(seed, x, 0, z) < 0.18 && v != dv && Math.abs(u - byreU) > 1) {
						d.set(x, base - 1, z, Blocks.HAY_BLOCK.defaultBlockState());
					}
				}
			}
		}
		int[] br = L.rect(u0, u1, 1, wid - 2);
		Rooms.Room room = new Rooms.Room(br[0], br[1], br[2], br[3], base, 3);
		room.keep(L.x(byreU, 1), L.z(byreU, 1));
		room.keep(L.x(u0, dv), L.z(u0, dv));
		Lighting.room(d, room);
		return room;
	}

	/** Three beds: where the living room could not take them all, the old one sleeps out in the byre by the beasts' warmth. */
	private static void sleepsThree(Draft d, Rooms.Room living, Rooms.Room byre) {
		int beds = TownHouses.bedsIn(d, living) + (byre == null ? 0 : TownHouses.bedsIn(d, byre));
		if (beds < INFO.beds() && byre != null) {
			dev.voidmc.hearth.build.kit.homestead.Beds.place(d, byre, INFO.beds() - beds);
		}
	}

	/**
	 * The yard down the house's side (the house's gable to the lane): a gate from the lane (roofed in Bohemia), the path
	 * to the door, the kailyard fenced at the lane end, the dung heap by the byre door, a woodpile along the boundary, a
	 * trough, the hens' house at the back; before the gable, flowers and a bench under its windows.
	 */
	private static void yardAlong(Draft d, Long L, int front, int doorU, int byreU, int base) {
		int w = d.frontage();
		int dp = d.depth();
		boolean left = L.yard() == Dirs.LEFT;
		int yx0 = left ? 0 : L.x1() + 1;
		int yx1 = left ? L.x0() - 1 : w - 1;
		if (yx1 < yx0) {
			return;
		}
		// the yard made level with the floor (a raised house's a step below it; water in it embanked and filled), the lane's
		// row too on level ground
		boolean wetYard = d.site().anyWater(yx0, 0, yx1, dp - 1);
		if (!wetYard) {
			Groundwork.level(d, yx0, base == 0 ? 0 : 1, yx1, dp - 1, base);
		} else if (yx1 - yx0 >= 1) {
			// water in the yard: the far part embanked and made up, the way along the house left to the steps
			int ex0 = left ? yx0 : yx0 + 1;
			int ex1 = left ? yx1 - 1 : yx1;
			Groundwork.level(d, ex0, 1, ex1, dp - 1, base, true);
		}
		if (base == 0 && !d.site().anyWater(0, 0, w - 1, front - 1)) {
			Groundwork.level(d, 0, 0, w - 1, front - 1, base);
		}
		if (base != 0 || wetYard || d.site().anyWater(0, 0, w - 1, front - 1)) {
			Groundwork.approach(d, L.x(doorU, -1), base, L.z(doorU, -1));
			Groundwork.approach(d, L.x(byreU, -1), base, L.z(byreU, -1));
		}
		// the path from the lane down the yard to the door
		int px = left ? L.x0() - 1 : L.x1() + 1;
		int doorZ = L.z(doorU, 0);
		Garden.path(d, Garden.line(px, 0, px, doorZ), base);
		// the kailyard: rows of roots and greens at the lane end of the yard, wattle round them
		int kw = yx1 - yx0;
		if (kw >= 2) {
			int kx0 = left ? yx0 : px + 1;
			int kx1 = left ? px - 1 : yx1;
			int kz0 = 1;
			int kz1 = Math.min(doorZ - 2, kz0 + 3);
			kailyard(d, kx0, kz0, kx1, kz1, base);
		}
		// the byre door's dung heap, a trough beside the way
		int bz = L.z(byreU, 0);
		int hx = left ? yx0 : yx1;
		if (kw >= 1 && Spots.standable(d, hx, base, bz + 2)) {
			Yard.dungHeap(d, hx, base, bz + 2, Dirs.BACK);
		}
		if (kw >= 2 && Spots.standable(d, hx, base, bz - 1)) {
			Props.trough(d, hx, base, bz - 1, Dirs.FRONT, 1);
		}
		// the woodpile along the boundary by the house door, the hens at the back
		if (kw >= 2 && Spots.standable(d, hx, base, doorZ - 1) && Spots.standable(d, hx, base, doorZ - 2)) {
			Garden.woodpile(d, hx, base, doorZ - 1, Dirs.FRONT, 2);
		}
		int cz = dp - 2;
		if (kw >= 2 && cz > bz + 3) {
			int cx = left ? yx0 : yx1 - 1;
			if (Spots.standable(d, cx, base, cz) && Spots.standable(d, cx + 1, base, cz)) {
				Sheds.coop(d, cx, base, cz + 1 < dp ? cz : cz, Dirs.FRONT);
			}
		}
		// the lane: a wattle fence with a gate into the yard, flowers and a bench before the gable
		List<int[]> line = new ArrayList<>();
		for (int x = 0; x < w; x++) {
			line.add(new int[]{x, 0});
		}
		Garden.fence(d, line, px, Dirs.FRONT);
		if (Bohemia.is(d)) {
			Bohemia.wattle(d, 0, 0, w - 1, 0);
		}
		int gz = L.z0() - 1;
		if (gz >= 1) {
			for (int x = L.x0() + 1; x <= L.x1() - 1; x++) {
				if (Spots.standable(d, x, base, gz) && !Spots.claimed(d, x, base, gz)) {
					Garden.flower(d, x, base, gz);
				}
			}
		} else if (Bohemia.is(d)) {
			Bohemia.bench(d, L.x0() + (L.x1() - L.x0()) / 2, L.z0(), base);
		}
		// a long side bench against the house under the living room's window
		int bu = 2;
		int bxx = L.x(bu, -1);
		int bzz = L.z(bu, -1);
		if (Spots.standable(d, bxx, base, bzz) && !Spots.claimed(d, bxx, base, bzz) && Math.abs(bxx - px) + Math.abs(bzz - bzz) > 0) {
			Yard.bench(d, bxx, base, bzz, L.yard().getOpposite());
		}
	}

	/**
	 * The yard before the house laid along the lane: the kailyard either side of the path to the door, the dung heap at
	 * the byre's end, a woodpile at the dwelling's.
	 */
	private static void yardBefore(Draft d, Long L, int front, int doorU, int byreU, int base) {
		int w = d.frontage();
		if (base == 0 && front >= 1 && !d.site().anyWater(0, 0, w - 1, front - 1)) {
			Groundwork.level(d, 0, 0, w - 1, front - 1, base);
		}
		int dx = L.x(doorU, -1);
		Groundwork.approach(d, dx, base, L.z0() - 1);
		Garden.path(d, Garden.line(dx, L.z0() - 1, dx, 0), base);
		int bx = L.x(byreU, -1);
		Garden.path(d, Garden.line(bx, L.z0() - 1, bx, Math.max(0, L.z0() - 2)), base);
		if (front >= 3) {
			int kz0 = 1;
			int kz1 = L.z0() - 2;
			int lo = Math.min(dx, bx);
			int hi = Math.max(dx, bx);
			if (hi - lo >= 4) {
				kailyard(d, lo + 2, kz0, hi - 2, kz1, base);
			}
			int far = L.flipU() ? 1 : w - 2;
			int near = L.flipU() ? w - 2 : 1;
			kailyard(d, Math.min(far, dx < bx ? 1 : bx + 2), kz0, Math.max(far, dx < bx ? dx - 2 : w - 2), kz1, base);
			if (near < 0) {
				return;
			}
		}
		int heapX = L.x(L.len() - 2, -1);
		if (Spots.standable(d, heapX, base, L.z0() - 1)) {
			Yard.dungHeap(d, heapX, base, L.z0() - 1, L.along().getOpposite());
		}
		List<int[]> line = new ArrayList<>();
		for (int x = 0; x < w; x++) {
			line.add(new int[]{x, 0});
		}
		Garden.fence(d, line, dx, Dirs.FRONT);
		// the woodpile along the back wall of the dwelling
		int wx = L.x(1, L.wid());
		int wz = L.z(1, L.wid());
		if (wz < d.depth() && Spots.standable(d, wx, base, wz)) {
			Garden.woodpile(d, wx, base, wz, L.along(), 3);
		}
	}

	/**
	 * A kailyard over a rectangle: rows of the culture's roots and greens on tilled ground with a water furrow down the
	 * middle where it is wide enough, a cabbage-like row of leaves now and then.
	 */
	static void kailyard(Draft d, int x0, int z0, int x1, int z1, int y) {
		if (x1 < x0 || z1 < z0) {
			return;
		}
		boolean ok = true;
		for (int z = z0; z <= z1 && ok; z++) {
			for (int x = x0; x <= x1 && ok; x++) {
				ok = Spots.standable(d, x, y, z) && !Spots.claimed(d, x, y, z) && Spots.level(d, x, z) == y && !d.site().isWater(x, z);
			}
		}
		if (!ok) {
			return;
		}
		int across = x1 - x0;
		if (across >= 2 && z1 - z0 >= 1) {
			Garden.kitchenRows(d, x0, z0, x1, z1, y, Dirs.FRONT);
		} else {
			Mat crops = d.mat(Role.CROPS);
			try (var s = d.stage(StageKind.EXTERIOR)) {
				for (int z = z0; z <= z1; z++) {
					for (int x = x0; x <= x1; x++) {
						d.set(x, y - 1, z, Blocks.FARMLAND.defaultBlockState().setValue(BlockStateProperties.MOISTURE, 7));
						Block crop = crops.block(Form.FULL, x, y, 0);
						d.set(x, y, z, crop instanceof CropBlock ? States.crop(crop, 4 + (int) (Noise.unit(d.seed(), x, y, z) * 4)) : crop.defaultBlockState());
					}
				}
			}
		}
	}

	// ----------------------------------------------------------------------------------------------- flat

	/**
	 * The dry lands' croft: a long mud house under a flat roof, the dwelling's two rooms and the beasts' room behind a
	 * partition, a walled pen beside it with a shade of poles over the trough, the kitchen garden by the lane.
	 */
	private static void flat(Draft d, Traits t) {
		int w = d.frontage();
		int dp = d.depth();
		int bw = 7;
		boolean penLeft = d.chance(0.5);
		// a short plot gives the house all its depth but the lane's row (the living room needs five rows for three beds)
		int front = dp >= 14 ? 2 : 1;
		int back = dp >= 14 ? 1 : 0;
		int x0 = penLeft ? w - bw : 0;
		Long L = new Long(x0, front, x0 + bw - 1, dp - 1 - back, Direction.Axis.Z, false, !penLeft);
		int len = L.len();
		int wid = L.wid();
		boolean hall = len >= 13;
		// a living room five long inside where the house is short, the byre taking what is left (two to four)
		int p1 = hall ? 5 : len - 2 - Math.clamp(len - 8, 2, 4);
		int p2 = hall ? 8 : p1;
		boolean wet = d.site().anyWater(L.x0(), L.z0(), L.x1(), L.z1());
		int base = wet ? StationHomes.raisedBase(d, L.x0(), L.z0(), L.x1(), L.z1(), front) : 0;
		Body body = new Body(L.x0(), L.z0(), L.x1(), L.z1(), base, new int[]{4}, false, false);
		d.body(L.x0(), L.z0(), L.x1(), L.z1());
		int plate = body.plateY();
		d.eave(plate);
		if (wet) {
			Groundwork.stilts(d, L.x0(), L.z0(), L.x1(), L.z1(), base - 1, 2);
			Floors.boards(d, L.x0(), L.z0(), L.x1(), L.z1(), base - 1);
		} else {
			Groundwork.footings(d, L.x0(), L.z0(), L.x1(), L.z1(), base - 1);
			StationHomes.earthFloor(d, L.x0() + 1, L.z0() + 1, L.x1() - 1, L.z1() - 1, base - 1);
		}
		StationHomes.lowWalls(d, body, 0);
		d.carve(L.x0() + 1, base, L.z0() + 1, L.x1() - 1, plate - 1, L.z1() - 1);
		partition(d, L, p2, base, plate, t);
		if (hall) {
			partition(d, L, p1, base, plate, t);
		}
		int doorU = hall ? p1 + 1 + (p2 - p1 - 1) / 2 : p1 - 1;
		Side yardWall = L.side(L.yard());
		Openings.door(d, yardWall, yardWall.column(yardWall.index(L.z(doorU, 0))), base, 1, new Openings.DoorOpts(false, false, true));
		int byreU = p2 + 1 + Math.max(0, (len - 2 - p2 - 2) / 2);
		byreDoor(d, L, yardWall, byreU, base);
		windows(d, L, p1, p2, hall, base, plate, doorU);
		int deck = Roofs.flat(d, L.x0(), L.z0(), L.x1(), L.z1(), plate, true);
		Adobe.parapet(d, L.x0(), L.z0(), L.x1(), L.z1(), deck, Adobe.Parapet.ROUNDED);
		int hu = hall ? p1 + 1 : p1 - 1;
		int hx = L.x(hu, wid - 2);
		int hz = L.z(hu, wid - 2);
		Furniture.hearth(d, hx, base, hz, L.yard(), false);
		HouseBuilder.chimneyFlat(d, hx, hz, deck);
		Rooms.Room living = interior(d, L, p1, p2, hall, base, hx, hz, doorU);
		sleepsThree(d, living, byre(d, L, p2, byreU, base, plate));
		// the pen: a mud wall round the yard beside the house, a shade over the trough
		if (!wet) {
			int yx0 = penLeft ? 0 : L.x1() + 1;
			int yx1 = penLeft ? L.x0() - 1 : w - 1;
			if (yx1 >= yx0) {
				Groundwork.level(d, yx0, 0, yx1, dp - 1, base);
				Groundwork.level(d, 0, 0, w - 1, front - 1, base);
				int px = penLeft ? L.x0() - 1 : L.x1() + 1;
				Garden.path(d, Garden.line(px, 0, px, L.z(doorU, 0)), base);
				if (yx1 - yx0 >= 2) {
					int sx0 = penLeft ? yx0 : px + 1;
					int sx1 = penLeft ? px - 1 : yx1;
					Adobe.ramada(d, sx0, L.z(byreU, 0) - 1, sx1, L.z(byreU, 0) + 1, base);
					kailyard(d, sx0, 1, sx1, Math.min(L.z(doorU, 0) - 2, 3), base);
				}
				List<int[]> line = new ArrayList<>();
				for (int x = 0; x < w; x++) {
					line.add(new int[]{x, 0});
				}
				Garden.fence(d, line, px, Dirs.FRONT);
			}
			Groundwork.approach(d, L.x(doorU, -1), base, L.z(doorU, -1));
		} else {
			Groundwork.approach(d, L.x(doorU, -1), base, L.z(doorU, -1));
		}
		Lighting.doorLamp(d, L.x(doorU + 1, -1), base + 2, L.z(doorU + 1, -1), L.yard().getOpposite());
		Groundwork.clear(d, 0, 0, w - 1, dp - 1, deck + 3);
	}

	/** Unused: the meeting place a croft has none of (kept so the POI import reads deliberate). */
	static PoiType none() {
		return PoiType.HEARTH;
	}
}
