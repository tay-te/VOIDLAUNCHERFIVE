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
import dev.voidmc.hearth.build.draft.Paint;
import dev.voidmc.hearth.build.kit.Body;
import dev.voidmc.hearth.build.kit.Floors;
import dev.voidmc.hearth.build.kit.Furniture;
import dev.voidmc.hearth.build.kit.Garden;
import dev.voidmc.hearth.build.kit.Openings;
import dev.voidmc.hearth.build.kit.Roofs;
import dev.voidmc.hearth.build.kit.Rooms;
import dev.voidmc.hearth.build.kit.Side;
import dev.voidmc.hearth.build.kit.homestead.Adobe;
import dev.voidmc.hearth.build.kit.homestead.Bohemia;
import dev.voidmc.hearth.build.kit.homestead.Household;
import dev.voidmc.hearth.build.kit.homestead.Round;
import dev.voidmc.hearth.build.kit.homestead.Spots;
import dev.voidmc.hearth.build.kit.homestead.Verandas;
import dev.voidmc.hearth.build.kit.homestead.Yard;
import dev.voidmc.hearth.build.kit.light.Lighting;
import dev.voidmc.hearth.build.kit.station.Fire;
import dev.voidmc.hearth.build.kit.station.Mending;
import dev.voidmc.hearth.build.kit.station.Roofwork;
import dev.voidmc.hearth.build.kit.station.Verdure;
import dev.voidmc.hearth.build.kit.townhome.Traits;
import dev.voidmc.hearth.build.style.Form;
import dev.voidmc.hearth.build.style.Habits;
import dev.voidmc.hearth.build.style.Habits.RoofForm;
import dev.voidmc.hearth.build.style.Habits.WallKind;
import dev.voidmc.hearth.build.style.Role;
import dev.voidmc.hearth.build.terrain.Groundwork;
import java.util.List;
import java.util.Set;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;

/**
 * {@code hearth:house/hovel}: the home of the poor (docs/LIVING_WORLD.md §8.1). One room under a big roof: walls on a
 * course of fieldstone, the daub between the posts mended with boards where it fell out; a thatch laid thick where the
 * culture thatches, else the culture's roofing, coming down low; a lean-to against one side under the same roof run on
 * over it as a catslide, firewood stacked dry under it and a chopping block at its mouth; a doorstep with a stool, a pot
 * of flowers and the water barrel; grass and ferns grown up round its foot. Inside: a fire pit on a hearthstone against
 * the back wall (the household's light, warmth and cooking), a bed either side of it, a chest, a barrel, the trade's one
 * tool where the household has a trade. Poor, but cared for.
 *
 * <p>The culture shapes it: flat-roofed lands build an adobe cell with a lower store against its side and a shade of
 * poles and fronds before the door over the cooking fire; round-house lands a round hut under a cone of thatch; raised
 * lands lift it on piles over a deck, its roof broad over low walls; the north may lay its roof in turf.
 *
 * <p>Params:
 * <ul>
 *   <li>{@code station}, {@code household} (shared, see {@link Household}): the household's trade dresses the room</li>
 *   <li>{@code lean_to} (boolean): a lean-to against a side (default true; needs a plot 7 wide)</li>
 *   <li>{@code roof} (RoofForm name): the main roof's form (gable, hip, half_hip; default by culture and seed)</li>
 *   <li>{@code variant} (int): the style variant</li>
 * </ul>
 */
public final class Hovel implements Archetype {
	public static final ArchetypeInfo INFO = new ArchetypeInfo(Hearth.id("house/hovel"), ArchetypeCategory.HOUSING, Set.of(), 2, 7, 9, 6, 8, 8, 9,
		SettlementTier.CAMP, Set.of(), Set.of(), List.of(Hearth.id("house/cottage")), 0, 0.8);

	/** How much of a hovel's wall is mended. */
	static final double MEND = 0.18;

	@Override
	public ArchetypeInfo info() {
		return INFO;
	}

	@Override
	public void draw(Draft d) {
		Traits t = Traits.of(d);
		Habits h = d.habits();
		if (t.flat()) {
			flat(d);
		} else if (h.plan().round() >= 0.5 && d.chance(0.75)) {
			round(d);
		} else {
			pitched(d, t);
		}
	}

	// ----------------------------------------------------------------------------------------------- pitched

	private static void pitched(Draft d, Traits t) {
		int w = d.frontage();
		int dp = d.depth();
		Habits h = d.habits();
		int bd = dp >= 8 ? 6 : 5;
		int front = dp - bd;
		// raised over water or by habit: on piles over a deck, no lean-to
		boolean raised = StationHomes.raised(d, 0, front, w - 1, dp - 1);
		boolean lean = !raised && d.param("lean_to", true) && w >= 7;
		int lw = lean ? Math.clamp(w - 5, 2, 3) : 0;
		int bw = raised ? Math.min(w - 2, 6) : Math.min(6, w - lw);
		boolean leanLeft = d.chance(0.5);
		int bx0 = lean ? (leanLeft ? w - bw : 0) : (w - bw) / 2;
		int bx1 = bx0 + bw - 1;
		int bz0 = front;
		int bz1 = dp - 1;
		int base = raised ? StationHomes.raisedBase(d, bx0, bz0, bx1, bz1, Math.max(1, front)) : 0;
		Body body = new Body(bx0, bz0, bx1, bz1, base, new int[]{raised ? 3 : 4}, false, false);
		boolean deck = raised && front >= 2;
		d.body(bx0, bz0, bx1, bz1);
		int plate = body.plateY();
		d.eave(plate);
		// the door in the middle of the front, a cell toward the lean-to on a wide room
		int door = bx0 + bw / 2 - (bw % 2 == 0 && (lean ? leanLeft : d.chance(0.5)) ? 1 : 0);

		// ground: piles over water or by habit, else footings on the ground
		if (raised) {
			Groundwork.stilts(d, bx0, bz0, bx1, bz1, base - 1, 2);
			Floors.boards(d, bx0, bz0, bx1, bz1, base - 1);
		} else {
			Groundwork.footings(d, bx0, bz0, bx1, bz1, base - 1);
			StationHomes.earthFloor(d, bx0 + 1, bz0 + 1, bx1 - 1, bz1 - 1, base - 1);
			Groundwork.apron(d, bx0, bz0, bx1, bz1, base, 2);
		}

		// walls, mended
		StationHomes.lowWalls(d, body, MEND);
		d.carve(bx0 + 1, base, bz0 + 1, bx1 - 1, plate, bz1 - 1);
		Side frontWall = body.side(0, Dirs.FRONT);
		Openings.door(d, frontWall, door, base, 1, new Openings.DoorOpts(false, false, true));
		// a window hole with its shutter on the side away from the lean-to, another in the front of a wide room
		Side sideWall = body.side(0, lean ? (leanLeft ? Dirs.RIGHT : Dirs.LEFT) : (d.chance(0.5) ? Dirs.LEFT : Dirs.RIGHT));
		hatch(d, sideWall, sideWall.middle(), base + 1);
		if (bw >= 6) {
			int other = door - bx0 <= bw / 2 ? bx1 - 1 : bx0 + 1;
			hatch(d, frontWall, frontWall.index(other), base + 1);
		}

		// the roof: a lean-to wants the ridge back from the street, so it sits under the eave and the roof runs on over it
		RoofForm form = HouseBuilder.roofParam(d, roofForm(d, t));
		Direction.Axis ridge = lean || d.chance(0.6) ? Direction.Axis.Z : Direction.Axis.X;
		boolean turf = t.walls() == WallKind.LOG && d.mat(Role.GROUND).main(Form.FULL) == Blocks.GRASS_BLOCK && d.chance(0.5);
		int[] r = body.rect(0);
		Roofs.Roof roof;
		if (turf) {
			roof = HouseBuilder.turfRoof(d, body, ridge);
		} else {
			int eaves = raised ? 2 : 1;
			Direction leanSide = lean ? (leanLeft ? Dirs.LEFT : Dirs.RIGHT) : null;
			double pitch = StationHomes.hutPitch(d, bx0, bz0, bx1, bz1, plate, form, ridge, 1.0, eaves, 1);
			Mat cover = cover(d, t);
			roof = StationHomes.hutRoof(d, bx0, bz0, bx1, bz1, plate, form, ridge, pitch, eaves, cover, leanSide, lw - 1);
			Bohemia.carryRidge(d, roof);
			boolean straw = cover.main(Form.FULL) == Blocks.HAY_BLOCK;
			if (straw) {
				Roofwork.thick(d, roof, cover);
			}
			if (form != RoofForm.HIP) {
				Paint boards = t.walls() == WallKind.STONE || t.walls() == WallKind.ADOBE || t.walls() == WallKind.BRICK ? d.mat(Role.WALL).full()
					: t.walls() == WallKind.LOG ? d.mat(Role.WALL).log(ridge == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X)
					: d.mat(Role.GABLE).full();
				StationHomes.gableFill(d, roof, ridge, r, plate, Mending.mended(d, boards, 7, MEND, Mending.patchesFor(d, t.walls())));
				if (Bohemia.is(d)) {
					Bohemia.gables(d, roof, ridge, r, plate);
				}
				Roofwork.verges(d, roof, ridge, d.mat(Role.ROOF_TRIM));
			}
			if (!straw && cover.palette().patches().isEmpty()) {
				Mending.roof(d, roof, 1, 0.1);
			}
			Roofwork.brackets(d, roof, r, ridge, d.mat(Role.BEAM), form == RoofForm.HIP);
		}
		// the room open to the rafters
		for (int z = bz0 + 1; z <= bz1 - 1; z++) {
			for (int x = bx0 + 1; x <= bx1 - 1; x++) {
				int under = roof.covers(x, z) ? roof.bottomY(x, z) : plate + 1;
				for (int y = plate; y < under; y++) {
					if (d.isAbsent(x, y, z)) {
						d.air(x, y, z);
					}
				}
			}
		}

		if (lean) {
			leanTo(d, body, leanLeft ? Dirs.LEFT : Dirs.RIGHT, roof);
		}

		// the doorstep (a deck on piles before a raised hovel) and the way to the street
		if (deck) {
			deck(d, body, door);
		} else if (raised) {
			Groundwork.approach(d, door, base, bz0 - 1);
		} else if (bz0 >= 1) {
			if (!d.site().anyWater(0, 0, w - 1, bz0 - 1)) {
				Groundwork.level(d, 0, 0, w - 1, bz0 - 1, 0);
			}
			Groundwork.approach(d, door, base, bz0 - 1);
			Garden.path(d, Garden.line(door, bz0 - 1, door, 0), 0);
		}

		// inside: the fire pit, the beds, the household's few things; the lamp at the door
		Rooms.Room room = new Rooms.Room(bx0 + 1, bz0 + 1, bx1 - 1, bz1 - 1, base, 2);
		StationHomes.fireAndBeds(d, room, door, 2, true);
		Household.first(d, room, new int[]{door, bz1 - 1});
		Rooms.furnish(d, room, Rooms.Type.STORAGE, 0, JobType.NONE);
		Household.dress(d, room, room, new int[]{door, bz1 - 1});
		doorLight(d, door, base, bz0 - 1, lean && leanLeft);

		// the doorstep's life, the green at the foot of the walls
		if (!raised) {
			doorstep(d, body, door, front);
			Verdure.foot(d, bx0, bz0, bx1, bz1, 0.55);
		}
		if (h.snow()) {
			Garden.snowBanks(d, bx0, bz0, bx1, bz1, base, door);
		}
		Groundwork.clear(d, 0, 0, w - 1, dp - 1, roof.ridgeY + 2);
	}

	/** The roof forms a hovel takes: a hip or a half-hip as often as a gable (a hip is the humblest roof). */
	private static RoofForm roofForm(Draft d, Traits t) {
		if (Bohemia.is(d)) {
			return d.pick(RoofForm.GABLE, RoofForm.HALF_HIP, RoofForm.HALF_HIP, RoofForm.HIP);
		}
		if (t.eastern()) {
			return RoofForm.HIP;
		}
		RoofForm f = d.habits().roof().pick(d.random(), RoofForm.GABLE, RoofForm.HIP, RoofForm.HALF_HIP);
		if (f == null) {
			return RoofForm.GABLE;
		}
		// a third of them hipped whatever the culture's habit, the poor man's roof
		return d.chance(0.33) ? RoofForm.HIP : f;
	}

	/**
	 * What the hovel is roofed with: thatch where the culture's thatch is straw (most of the time; the rest take the
	 * culture's own roofing, old and mended), else the roofing.
	 */
	private static Mat cover(Draft d, Traits t) {
		if (d.habits().roof().thatch()) {
			return d.mat(Role.ROOF);
		}
		Mat thatch = d.mat(Role.THATCH);
		boolean straw = thatch.main(Form.FULL) == Blocks.HAY_BLOCK && thatch.has(Form.STAIRS);
		return straw && d.chance(t.raised() || t.eastern() ? 0.85 : 0.6) ? thatch : d.mat(Role.ROOF);
	}

	/**
	 * A window of the poor: a hole in the wall with a board shutter standing half open in it (no glass), a rough sill of
	 * the frame's wood under it.
	 */
	static void hatch(Draft d, Side s, int i, int y) {
		if (i <= 0 || i >= s.last() || !Openings.isWallBlock(d, s.x(i), y, s.z(i))) {
			return;
		}
		Openings.window(d, s, s.column(i), y, 1, 1, Openings.WindowStyle.LATTICE, new Openings.WindowOpts(false, false, true, false));
	}

	/**
	 * A lean-to against the body's side wall {@code side}, under the main roof run on over it as a catslide: posts at
	 * its outer corners up to the roof, boards across its back end, the ground under it made level; firewood stacked
	 * along the house wall under it and a chopping block at its open front.
	 */
	private static void leanTo(Draft d, Body b, Direction side, Roofs.Roof roof) {
		int[] r = b.rect(0);
		boolean left = side == Dirs.LEFT;
		int wallX = left ? r[0] : r[2];
		int outX = left ? 0 : d.frontage() - 1;
		int nearX = wallX + (left ? -1 : 1);
		int z0 = r[1];
		int z1 = r[3];
		int n = Math.abs(outX - wallX);
		if (n < 2 || z1 - z0 < 2) {
			return;
		}
		int ground = b.baseY;
		// its floor: the ground made level with the room's
		for (int z = z0; z <= z1; z++) {
			for (int k = 1; k <= n; k++) {
				int x = wallX + (left ? -k : k);
				int g = d.site().ground(x, z);
				try (var s = d.stage(StageKind.EARTHWORK)) {
					for (int y = g - 1; y < ground - 1; y++) {
						d.set(x, y, z, d.mat(Role.FILL).full());
					}
					for (int y = ground; y < g; y++) {
						d.set(x, y, z, Blocks.AIR.defaultBlockState());
					}
					if (g != ground) {
						d.set(x, ground - 1, z, Blocks.COARSE_DIRT.defaultBlockState());
					}
				}
			}
		}
		Mat boards = d.mat(Role.DOOR);
		Mat post = d.mat(Role.FRAME);
		try (var s = d.stage(StageKind.FRAME)) {
			for (int z : new int[]{z0, z1}) {
				int top = roof.covers(outX, z) ? roof.bottomY(outX, z) : ground + 2;
				for (int y = ground; y < top; y++) {
					d.set(outX, y, z, post.log(Direction.Axis.Y));
				}
			}
		}
		try (var s = d.stage(StageKind.WALLS)) {
			for (int k = 1; k < n; k++) {
				int x = wallX + (left ? -k : k);
				int top = roof.covers(x, z1) ? roof.bottomY(x, z1) : ground + 2;
				for (int y = ground; y < top; y++) {
					d.set(x, y, z1, boards.full());
				}
			}
		}
		// firewood stacked along the wall under it, split ends out
		Mat log = d.mat(Role.BEAM);
		try (var s = d.stage(StageKind.EXTERIOR)) {
			for (int z = z0 + 1; z < z1; z++) {
				int top = roof.covers(nearX, z) ? roof.bottomY(nearX, z) - 1 : ground + 1;
				for (int y = ground; y <= Math.min(ground + 1, top); y++) {
					if (d.isEmpty(nearX, y, z)) {
						d.set(nearX, y, z, log.log(Direction.Axis.X));
					}
				}
			}
		}
		int cz = Math.max(0, z0 - 1);
		int cx = n >= 3 ? nearX + (left ? -1 : 1) : nearX;
		if (z0 - 1 >= 0 && Spots.standable(d, cx, ground, cz)) {
			Yard.choppingBlock(d, cx, ground, cz, side);
		}
	}

	/**
	 * The deck before a raised hovel: boards on piles across the front at the floor's level, a rail along its edge, the
	 * way down from its middle to the street.
	 */
	private static void deck(Draft d, Body b, int door) {
		int[] r = b.rect(0);
		int y = b.baseY;
		int z = r[1] - 1;
		if (z < 0) {
			Groundwork.approach(d, door, y, z);
			return;
		}
		for (int x = r[0]; x <= r[2]; x++) {
			Verandas.deck(d, x, z, y);
		}
		for (int x : new int[]{r[0], r[2]}) {
			Verandas.pile(d, x, z, y - 1);
		}
		Groundwork.approach(d, door, y, z);
		for (int x = r[0]; x <= r[2]; x++) {
			if (Math.abs(x - door) > 1 && d.isEmpty(x, y, z)) {
				Verandas.rail(d, x, z, y);
			}
		}
	}

	/**
	 * The lamp by the door, on the side away from the lean-to: hung from a bracket on the wall where the gable leaves
	 * room over it, else hung under the eave.
	 */
	static void doorLight(Draft d, int door, int y, int z, boolean leanLeft) {
		if (z < 0) {
			return;
		}
		for (int dx : leanLeft ? new int[]{1, -1} : new int[]{-1, 1}) {
			int x = door + dx;
			if (!d.isEmpty(x, y + 1, z) || !Openings.isWallBlock(d, x, y + 1, z + 1)) {
				continue;
			}
			if (d.isEmpty(x, y + 2, z) && Openings.isWallBlock(d, x, y + 2, z + 1)) {
				Lighting.doorLamp(d, x, y + 1, z, Dirs.BACK);
				return;
			}
			if (d.get(x, y + 2, z) != null && !d.isEmpty(x, y + 2, z)) {
				Lighting.chandelier(d, x, z, y + 2, y + 1);
				return;
			}
		}
	}

	/** Life at the door: a stool beside it against the wall, a pot of flowers, the water barrel at a front corner. */
	private static void doorstep(Draft d, Body b, int door, int front) {
		int[] r = b.rect(0);
		int y = b.baseY;
		int z = r[1] - 1;
		if (z < 0) {
			return;
		}
		for (int dx : new int[]{2, -2}) {
			int x = door + dx;
			if (x <= r[0] || x >= r[2] || !Spots.standable(d, x, y, z) || Spots.claimed(d, x, y, z)) {
				continue;
			}
			Furniture.chair(d, x, y, z, Dirs.BACK);
			int ox = door - dx / 2;
			if (front >= 2 && Spots.standable(d, ox, y, z) && !Spots.claimed(d, ox, y, z)) {
				Garden.pot(d, ox, y, z);
			}
			break;
		}
		for (int x : new int[]{r[0] - 1, r[2] + 1}) {
			if (Spots.standable(d, x, y, r[1]) && !Spots.claimed(d, x, y, r[1])) {
				d.inStage(StageKind.EXTERIOR, () -> d.set(x, y, r[1], Blocks.WATER_CAULDRON.defaultBlockState()
					.setValue(BlockStateProperties.LEVEL_CAULDRON, 3)));
				break;
			}
		}
	}

	// ----------------------------------------------------------------------------------------------- flat

	/**
	 * The dry lands' hovel: an adobe cell under a flat roof of poles and mud (a rounded coping, the poles' ends through
	 * the wall head), a lower store or a lean-to of poles and reeds against its side; a yard before it walled from the
	 * street, a shade of poles and reeds before the door over the cooking fire (over the doorway alone on a shallow
	 * plot); jars by the door.
	 */
	private static void flat(Draft d) {
		int w = d.frontage();
		int dp = d.depth();
		boolean storeLeft = d.chance(0.5);
		int bw = Math.min(6, w - 2);
		int bx0 = storeLeft ? w - bw : 0;
		int bx1 = bx0 + bw - 1;
		int bd = dp >= 8 ? 6 : 5;
		int bz0 = dp - bd;
		int bz1 = dp - 1;
		boolean raised = d.site().anyWater(0, bz0, w - 1, bz1);
		int base = raised ? StationHomes.raisedBase(d, bx0, bz0, bx1, bz1, bz0 + 1) : 0;
		Body body = new Body(bx0, bz0, bx1, bz1, base, new int[]{4}, false, false);
		d.body(bx0, bz0, bx1, bz1);
		int plate = body.plateY();
		d.eave(plate);
		int door = bx0 + bw / 2 - (bw % 2 == 0 && storeLeft ? 1 : 0);
		if (raised) {
			Groundwork.stilts(d, bx0, bz0, bx1, bz1, base - 1, 2);
			Floors.boards(d, bx0, bz0, bx1, bz1, base - 1);
		} else {
			Groundwork.footings(d, bx0, bz0, bx1, bz1, base - 1);
			StationHomes.earthFloor(d, bx0 + 1, bz0 + 1, bx1 - 1, bz1 - 1, base - 1);
			Groundwork.apron(d, bx0, bz0, bx1, bz1, base, 2);
		}
		StationHomes.lowWalls(d, body, 0);
		d.carve(bx0 + 1, base, bz0 + 1, bx1 - 1, plate - 1, bz1 - 1);
		Side frontWall = body.side(0, Dirs.FRONT);
		Openings.door(d, frontWall, door, base, 1, new Openings.DoorOpts(false, false, true));
		Side sideWall = body.side(0, storeLeft ? Dirs.RIGHT : Dirs.LEFT);
		hatch(d, sideWall, sideWall.middle(), base + 1);
		// the shade before the door first (the roof's poles find their ends round it)
		boolean yardFire = !raised && bz0 >= 2;
		if (yardFire) {
			Adobe.ramada(d, Math.max(0, door - 2), 0, Math.min(w - 1, door + 1), bz0 - 1, base);
		}
		int deck = Roofs.flat(d, bx0, bz0, bx1, bz1, plate, false);
		Adobe.parapet(d, bx0, bz0, bx1, bz1, deck - 1, Adobe.Parapet.ROUNDED);
		// the store: a lower cell against the side (the house wall its end), a door to the front; or a lean-to of poles and
		// reeds open to the yard
		if (!raised && w - bw >= 2) {
			if (d.chance(0.5)) {
				store(d, storeLeft ? 0 : bx1, bz0 + 1, storeLeft ? bx0 : w - 1, bz1, base, storeLeft);
			} else {
				Adobe.leanTo(d, storeLeft ? 0 : bx1 + 1, bz0 + 1, storeLeft ? bx0 - 1 : w - 1, bz1, base, storeLeft ? Dirs.RIGHT : Dirs.LEFT);
			}
		}
		// the yard before the door walled from the street, the gap at the door; a shade of reeds over the door where the
		// yard is too shallow for the fire's
		if (!raised && bz0 >= 2) {
			java.util.List<int[]> wall = new java.util.ArrayList<>();
			for (int x = 0; x < w; x++) {
				if (x != door) {
					wall.add(new int[]{x, 0});
				}
			}
			for (int z = 1; z < bz0; z++) {
				wall.add(new int[]{0, z});
				wall.add(new int[]{w - 1, z});
			}
			Adobe.yardWall(d, wall, base);
		}
		if (!raised && !yardFire) {
			Adobe.doorShade(d, door, bz0, base);
		}
		if (raised) {
			Groundwork.approach(d, door, base, bz0 - 1);
		} else if (bz0 >= 1) {
			Groundwork.level(d, 0, 0, w - 1, bz0 - 1, 0);
			Groundwork.approach(d, door, base, bz0 - 1);
			Adobe.threshold(d, door, base, bz0 - 1);
		}
		// inside: the beds and the stores; the cooking is done outside
		Rooms.Room room = new Rooms.Room(bx0 + 1, bz0 + 1, bx1 - 1, bz1 - 1, base, 3);
		StationHomes.fireAndBeds(d, room, door, 2, !yardFire);
		Household.first(d, room, null);
		Rooms.furnish(d, room, Rooms.Type.STORAGE, 0, JobType.NONE);
		Household.dress(d, room, room, null);
		Lighting.room(d, room);
		if (yardFire) {
			// the fire pit beside the shade
			int fx = storeLeft ? Math.min(w - 1, door + 2) : Math.max(0, door - 2);
			boolean lit = false;
			for (int[] c : new int[][]{{fx, 0}, {fx, bz0 - 1}, {storeLeft ? Math.max(0, door - 2) : Math.min(w - 1, door + 2), 0}}) {
				if (!lit) {
					lit = Fire.yard(d, c[0], Spots.level(d, c[0], c[1]), c[1], c[1] == 0 ? Dirs.BACK : Dirs.FRONT, storeLeft ? Dirs.RIGHT : Dirs.LEFT);
				}
			}
			if (!lit) {
				// no ground for it in the yard: the fire burns indoors after all, against the back wall
				int fz = bz1 - 1;
				for (int x = bx0 + 1; x <= bx1 - 1 && !lit; x++) {
					if (d.isEmpty(x, base, fz) && room.claim(x, fz)) {
						Fire.indoor(d, x, base, fz, Dirs.FRONT);
						lit = true;
					}
				}
			}
		}
		if (!raised && bz0 >= 1) {
			for (int x : new int[]{door - 1, door + 1}) {
				int z = bz0 - 1;
				if (Spots.standable(d, x, base, z) && !Spots.claimed(d, x, base, z)) {
					Adobe.jar(d, x, base, z);
					break;
				}
			}
		}
		Groundwork.clear(d, 0, 0, w - 1, dp - 1, deck + 3);
	}

	/**
	 * A low store of mud against the house's side over {@code [x0..x1] × [z0..z1]} (the house's own wall is one of its
	 * ends): walls three courses under a flat roof with a rounded coping, a door to the front, jars and a barrel inside.
	 */
	private static void store(Draft d, int x0, int z0, int x1, int z1, int y, boolean left) {
		Body body = new Body(x0, z0, x1, z1, y, new int[]{3}, false, false);
		Groundwork.footings(d, body.x0, z0, body.x1, z1, y - 1);
		StationHomes.earthFloor(d, body.x0 + 1, z0 + 1, body.x1 - 1, z1 - 1, y - 1);
		Paint wall = d.mat(Role.WALL).full();
		try (var s = d.stage(StageKind.WALLS)) {
			for (Side side : body.sides(0)) {
				for (int i = 0; i < side.length(); i++) {
					int x = side.x(i);
					int z = side.z(i);
					for (int yy = y; yy <= body.plateY(); yy++) {
						if (d.isAbsent(x, yy, z)) {
							d.set(x, yy, z, wall);
						}
					}
				}
			}
		}
		d.carve(body.x0 + 1, y, z0 + 1, body.x1 - 1, body.plateY() - 1, z1 - 1);
		int deck = Roofs.flat(d, body.x0, z0, body.x1, z1, body.plateY(), false);
		Adobe.parapet(d, body.x0, z0, body.x1, z1, deck - 1, Adobe.Parapet.ROUNDED);
		int mx = left ? body.x0 + 1 : body.x1 - 1;
		if (body.x1 - body.x0 >= 2) {
			d.inStage(StageKind.OPENINGS, () -> d.door(mx, y, z0, d.mat(Role.DOOR).mainOr(Form.DOOR, Blocks.SPRUCE_DOOR), Dirs.FRONT, DoorHingeSide.LEFT));
			d.entrance(mx, y, z0 - 1);
			Groundwork.approach(d, mx, y, z0 - 1);
			Rooms.Room room = new Rooms.Room(body.x0 + 1, z0 + 1, body.x1 - 1, z1 - 1, y, 2);
			room.keep(mx, z0 + 1);
			Rooms.furnish(d, room, Rooms.Type.STORAGE, 0, JobType.NONE);
		}
	}

	// ----------------------------------------------------------------------------------------------- round

	/**
	 * The round hovel of the round-house lands: a ring of wattle and daub under a cone of thatch, its fire against the
	 * back of the floor between the two beds; firewood stacked against its side.
	 */
	private static void round(Draft d) {
		int w = d.frontage();
		int dp = d.depth();
		double cx = (w & 1) == 1 ? (w - 1) / 2.0 : (w - 2) / 2.0 + (d.chance(0.5) ? 1 : 0);
		double rad = 2.0;
		// the ring's centre on a whole cell, its front wall a row back from the street (the doorstep), a row behind it
		// for the eave where the plot is deep enough
		double cz = Math.max(rad + 1, dp - 4);
		var ring = Round.ring(cx, cz, rad);
		int plate = 2;
		d.body(ring.x0(), ring.z0(), ring.x1(), ring.z1());
		d.eave(plate);
		for (int[] c : ring.disc()) {
			boolean edge = ring.isWall(c[0], c[1]);
			int g = d.site().ground(c[0], c[1]);
			try (var s = d.stage(StageKind.FOUNDATION)) {
				for (int y = Math.min(-1, g - 2); y <= -1; y++) {
					d.set(c[0], y, c[1], d.mat(Role.FOUNDATION).full());
				}
			}
			if (!edge) {
				try (var s = d.stage(StageKind.EARTHWORK)) {
					for (int y = 0; y < g; y++) {
						d.set(c[0], y, c[1], Blocks.AIR.defaultBlockState());
					}
				}
				StationHomes.earthFloor(d, c[0], c[1], c[0], c[1], -1);
			}
		}
		Round.walls(d, ring, 0, plate, Round.Pattern.NONE);
		int[] doorCell = ring.cellToward(Dirs.FRONT);
		int doorX = doorCell[0];
		int dz = doorCell[1];
		d.inStage(StageKind.OPENINGS, () -> d.door(doorX, 0, dz, d.mat(Role.DOOR).mainOr(Form.DOOR, Blocks.SPRUCE_DOOR), Dirs.FRONT, DoorHingeSide.LEFT));
		d.entrance(doorX, 0, dz - 1);
		d.poi(PoiType.DOOR, doorX, 0, dz - 1, Dirs.BACK);
		double pitch = 1.1;
		while (pitch > 0.6 && plate + 1 + pitch * (rad + 1.5) + 2 >= d.maxHeight()) {
			pitch -= 0.1;
		}
		Roofs.Roof roof = Round.cone(d, ring, plate, 1, pitch, d.mat(Role.THATCH), true);
		Rooms.Room room = Round.room(ring, 0, 2);
		StationHomes.fireAndBeds(d, room, doorX, dz + 1, 2, true);
		Rooms.furnish(d, room, Rooms.Type.STORAGE, 0, JobType.NONE);
		Household.dress(d, room, room, null);
		if (dz >= 1) {
			Groundwork.approach(d, doorX, 0, dz - 1);
			Garden.path(d, Garden.line(doorX, dz - 1, doorX, 0), 0);
		}
		doorLight(d, doorX, 0, dz - 1, false);
		// firewood against the side
		int sx = d.chance(0.5) ? ring.x0() - 1 : ring.x1() + 1;
		int szz = (int) Math.round(cz);
		if (sx >= 0 && sx < w && Spots.standable(d, sx, 0, szz)) {
			Garden.woodpile(d, sx, 0, szz - 1, Dirs.BACK, 3);
		}
		Verdure.foot(d, ring.x0(), ring.z0(), ring.x1(), ring.z1(), 0.4);
		Groundwork.clear(d, 0, 0, w - 1, dp - 1, Math.min(d.maxHeight() - 1, roof.ridgeY + 3));
	}
}
