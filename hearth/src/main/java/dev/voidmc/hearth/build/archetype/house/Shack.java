package dev.voidmc.hearth.build.archetype.house;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.ArchetypeCategory;
import dev.voidmc.hearth.api.ArchetypeInfo;
import dev.voidmc.hearth.api.JobType;
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
import dev.voidmc.hearth.build.kit.Stairs;
import dev.voidmc.hearth.build.kit.homestead.Adobe;
import dev.voidmc.hearth.build.kit.homestead.Household;
import dev.voidmc.hearth.build.kit.homestead.Spots;
import dev.voidmc.hearth.build.kit.light.Lighting;
import dev.voidmc.hearth.build.kit.station.Fire;
import dev.voidmc.hearth.build.kit.station.Mending;
import dev.voidmc.hearth.build.kit.townhome.Life;
import dev.voidmc.hearth.build.kit.townhome.Proportions;
import dev.voidmc.hearth.build.kit.townhome.Traits;
import dev.voidmc.hearth.build.kit.trade.Props;
import dev.voidmc.hearth.build.style.Form;
import dev.voidmc.hearth.build.style.Habits;
import dev.voidmc.hearth.build.style.Habits.WallKind;
import dev.voidmc.hearth.build.style.Role;
import dev.voidmc.hearth.build.terrain.Groundwork;
import java.util.List;
import java.util.Set;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * {@code hearth:house/shack}: where the poor of a town live (docs/LIVING_WORLD.md §8.1), a narrow board shack squeezed
 * onto a scrap of land in a back lane, sharing its side walls with the next one. Board-and-batten walls on a rubble
 * footing, mended with odd planks; a sleeping loft over the room, oversailing the lane on brackets in timber towns; a
 * lean-to roof of boards high over the lane and falling to the back (or a narrow gable to the lane), patched; a thin
 * flue of rubble up its side from the fire inside; a door and a shuttered hatch to the lane, crates and a barrel at the
 * door, the washing hung out of the loft. Inside, below: the fire on its hearthstone under the flue, a bed, a table, a
 * chest; above, up a ladder: the children's bed under the roof. Low plots (out of the town's core) get one storey with
 * both beds by the fire. The dry lands build it of mud with a flat roof and a cloth awning; raised lands on short piles.
 *
 * <p>Params:
 * <ul>
 *   <li>{@code party_left}, {@code party_right} (boolean): shared side walls (read with {@code Draft.partyLocalLeft/Right})</li>
 *   <li>{@code station}, {@code household} (shared)</li>
 *   <li>{@code storeys} (int): 1 or 2 (default: 2 where the plot's height allows)</li>
 *   <li>{@code variant} (int)</li>
 * </ul>
 */
public final class Shack implements Archetype {
	public static final ArchetypeInfo INFO = new ArchetypeInfo(Hearth.id("house/shack"), ArchetypeCategory.HOUSING, Set.of(), 2, 5, 7, 5, 8, 8, 11,
		SettlementTier.CAMP, Set.of(), Set.of(ArchetypeInfo.SiteFlag.ATTACHABLE), List.of(Hearth.id("house/rowhouse")), 0, 0.8);

	@Override
	public ArchetypeInfo info() {
		return INFO;
	}

	@Override
	public void draw(Draft d) {
		raise(d);
		dev.voidmc.hearth.build.kit.townhome.Settle.finish(d);
	}

	/** The house as drawn, before its last walk-round ({@link dev.voidmc.hearth.build.kit.townhome.Settle}). */
	private void raise(Draft d) {
		int w = d.frontage();
		int dp = d.depth();
		Traits t = Traits.of(d);
		Habits h = d.habits();
		boolean pl = d.partyLocalLeft();
		boolean pr = d.partyLocalRight();
		boolean flat = t.flat();
		int front = dp >= 7 ? 1 : 0;
		int back = dp >= 8 ? 1 : 0;
		int bz0 = front;
		int bz1 = dp - 1 - back;
		boolean raised = StationHomes.raised(d, 0, bz0, w - 1, bz1);
		// raised only where a step in front can reach the floor
		int base = raised && front > 0 ? StationHomes.raisedBase(d, 0, bz0, w - 1, bz1, front) : 0;
		// two storeys where the plot is tall enough for the loft and its roof
		int roofRoom = flat ? 2 : 3;
		int loft = flat ? 3 : 2;
		// (standing alone, only where the loft leaves it no taller than half again its front: art review 22)
		boolean two = d.param("storeys", 2) >= 2 && base + 4 + loft + roofRoom < d.maxHeight()
			&& (!Proportions.detached(d) || base + 4 + loft + roofRoom <= Proportions.tallest(w));
		int[] heights = two ? new int[]{4, loft} : new int[]{4};
		Body body = new Body(0, bz0, w - 1, bz1, base, heights, pl, pr);
		// the loft oversails the lane (timber towns, a lane in front at least three below it)
		boolean jetty = two && !flat && !raised && t.timber() && d.chance(0.6);
		if (jetty) {
			body.jetty(1, Dirs.FRONT);
		}
		d.body(0, bz0, w - 1, bz1);
		int plate = body.plateY();
		d.eave(plate);
		// the door in the middle of a narrow front (the room is laid out round it), toward a free side on a wide one
		int door = w <= 5 ? w / 2 : TownHouses.doorColumn(d, 2, w - 3, pl, pr);
		boolean flueLeft = door > (w - 1) / 2 || door == (w - 1) / 2 && d.chance(0.5);

		// ground
		if (raised) {
			Groundwork.stilts(d, 0, bz0, w - 1, bz1, base - 1, 2);
			Floors.boards(d, 0, bz0, w - 1, bz1, base - 1);
		} else {
			Groundwork.footings(d, 0, bz0, w - 1, bz1, base - 1);
			Floors.boards(d, 1, bz0 + 1, w - 2, bz1 - 1, base - 1);
		}

		// walls: boards nailed up on a rubble footing (mud where the land builds in mud), mended
		walls(d, body, t);
		if (two) {
			Floors.boards(d, body.rect(1)[0], body.rect(1)[1], body.rect(1)[2], body.rect(1)[3], body.floorY(1) - 1);
		}
		for (int k = 0; k < body.storeys(); k++) {
			int[] rk = body.rect(k);
			d.carve(rk[0] + 1, body.floorY(k), rk[1] + 1, rk[2] - 1, body.floorY(k + 1) - 2, rk[3] - 1);
		}
		if (jetty) {
			brackets(d, body);
		}
		Side f0 = body.side(0, Dirs.FRONT);
		Openings.door(d, f0, door, base, 1, new Openings.DoorOpts(false, false, true));
		int hatchCol = door <= (w - 1) / 2 ? w - 2 : 1;
		if (Math.abs(hatchCol - door) >= 2) {
			Hovel.hatch(d, f0, f0.index(hatchCol), base + 1);
		}
		if (two) {
			Side f1 = body.side(1, Dirs.FRONT);
			Hovel.hatch(d, f1, f1.index(door <= (w - 1) / 2 ? door + 1 : door - 1), body.floorY(1) + 1);
		}
		// a hatch in the back wall where it is free
		Side back0 = body.side(0, Dirs.BACK);
		Hovel.hatch(d, back0, back0.middle(), base + 1);

		// the roof
		int[] tr = body.topRect();
		Roofs.Roof roof = null;
		int top;
		if (flat) {
			int deck = Roofs.flat(d, tr[0], tr[1], tr[2], tr[3], plate, false);
			Adobe.parapet(d, tr[0], tr[1], tr[2], tr[3], deck - 1, Adobe.Parapet.ROUNDED);
			top = deck + 2;
			// a shade of poles and reed mats over the door, a jar beside it, a sill stone at the step, where a row is left
			// before the door (art review 2 item 7)
			if (bz0 >= 1 && !raised) {
				Adobe.doorShade(d, door, bz0, base);
				for (int x : new int[]{door - 2, door + 2}) {
					if (x >= 0 && x < w) {
						Adobe.jar(d, x, base, bz0 - 1);
					}
				}
				Adobe.threshold(d, door, base, bz0 - 1);
			}
		} else {
			roof = roof(d, body, t);
			Paint boards = StationHomes.boardAndBatten(d, d.mat(Role.DOOR), d.mat(Role.FRAME), Direction.Axis.X);
			StationHomes.fillUnder(d, roof, tr, plate, boards);
			Mending.roof(d, roof, 3, 0.18);
			// the loft open up under the roof
			int k = body.storeys() - 1;
			for (int z = tr[1] + 1; z <= tr[3] - 1; z++) {
				for (int x = tr[0] + 1; x <= tr[2] - 1; x++) {
					int under = roof.covers(x, z) ? roof.bottomY(x, z) : plate + 1;
					for (int y = plate; y < under; y++) {
						if (d.isAbsent(x, y, z)) {
							d.air(x, y, z);
						}
					}
				}
			}
			top = roof.ridgeY + 2;
		}

		// the fire inside under its flue, the beds, the loft's ladder
		int[] r0 = body.rect(0);
		int ix0 = r0[0] + 1;
		int ix1 = r0[2] - 1;
		int iz0 = r0[1] + 1;
		int iz1 = r0[3] - 1;
		Rooms.Room down = new Rooms.Room(ix0, iz0, ix1, iz1, base, 3);
		// the fire against the back wall behind the door, a bed beside it (both beds by the fire in a one-room shack), the
		// loft's ladder in the back corner on the other side
		int lx = door - ix0 <= ix1 - door ? ix1 : ix0;
		if (two) {
			lx = door + 1 <= ix1 ? door + 1 : door - 1;
			down.keep(lx, iz1);
		}
		int fx = door;
		int fz = iz1;
		int bedsDown = two ? 1 : 2;
		StationHomes.fireAndBeds(d, down, door, iz0, bedsDown, true);
		if (two) {
			Stairs.ladder(d, lx, iz1, base, body.floorY(1) - 1, Dirs.FRONT);
			d.set(lx, body.floorY(1) - 1, iz1, Blocks.AIR.defaultBlockState());
		}
		flue(d, fx, fz, base, roof, top, Dirs.BACK);
		Household.first(d, down, new int[]{fx, fz});
		Rooms.furnish(d, down, Rooms.Type.KITCHEN, 0, JobType.NONE);
		Household.dress(d, down, down, new int[]{fx, fz});
		StationHomes.roomToTurn(d, down);
		StationHomes.windows(d, r0[0], r0[1], r0[2], r0[3], base + 1);
		Lighting.room(d, down);
		if (two) {
			int[] r1 = body.rect(1);
			Rooms.Room up = new Rooms.Room(r1[0] + 1, r1[1] + 1, r1[2] - 1, r1[3] - 1, body.floorY(1), 2);
			up.keep(lx, iz1);
			if (up.inside(lx, iz1 - 1)) {
				up.keep(lx, iz1 - 1);
			}
			up.block(fx, fz);
			int got = dev.voidmc.hearth.build.kit.homestead.Beds.place(d, up, 1);
			if (got < 1) {
				TownHouses.bedroom(d, up, 1);
			}
			Rooms.furnish(d, up, Rooms.Type.STORAGE, 0, JobType.NONE);
			StationHomes.roomToTurn(d, up);
			Lighting.room(d, up);
		}

		// the lane: the doorstep, crates and a barrel at the door, the washing out of the loft
		if (raised || front == 0) {
			Groundwork.approach(d, door, base, bz0 - 1);
		} else {
			Groundwork.approach(d, door, base, bz0 - 1);
			lane(d, body, door, front);
		}
		Hovel.doorLight(d, door, base, bz0 - 1, !flueLeft);
		if (flat && !raised && front >= 1) {
			dev.voidmc.hearth.build.kit.trade.Pentice.awning(d, f0, 0, f0.last(), 1, base, base + 2, true, f0.index(door));
		}
		if (back >= 1) {
			yard(d, body, flueLeft);
		}
		if (h.snow()) {
			Garden.snowBanks(d, 0, bz0, w - 1, bz1, base, door);
		}
		Groundwork.clear(d, 0, 0, w - 1, dp - 1, top);
	}

	/**
	 * Board walls storey by storey: corner posts of the frame's timber, board-and-batten between them on a course of
	 * rubble, mended with odd planks; party walls plain boards. The dry lands' shacks are mud, the culture's own walling
	 * (logs, stone, bamboo) where it is not timber.
	 */
	private static void walls(Draft d, Body b, Traits t) {
		WallKind kind = t.walls();
		boolean boarded = kind == WallKind.TIMBER_FRAME || kind == WallKind.PLASTER || kind == WallKind.PLANK || kind == WallKind.BRICK
			|| kind == WallKind.STONE;
		if (!boarded) {
			StationHomes.lowWalls(d, singleStorey(b, 0), 0.12);
			if (b.storeys() > 1) {
				StationHomes.lowWalls(d, singleStorey(b, 1), 0.12);
			}
			return;
		}
		Mat frame = d.mat(Role.FRAME);
		Mat found = d.mat(Role.FOUNDATION);
		Mat beam = d.mat(Role.BEAM);
		Mat boards = d.mat(Role.DOOR);
		Paint[] patches = {d.mat(Role.FLOOR).full(), d.mat(Role.FENCE).full()};
		for (int k = 0; k < b.storeys(); k++) {
			int ya = k == 0 ? b.baseY : b.floorY(k) - 1;
			int yg = b.floorY(k + 1) - 1;
			for (Side s : b.sides(k)) {
				boolean party = b.isParty(s.out());
				Paint face = party ? boards.full() : Mending.mended(d, StationHomes.boardAndBatten(d, boards, frame, s.axis()), 20 + k * 4L + s.out().ordinal(), 0.16, patches);
				try (var st = d.stage(StageKind.WALLS)) {
					for (int i = 0; i < s.length(); i++) {
						int x = s.x(i);
						int z = s.z(i);
						for (int y = ya; y <= yg; y++) {
							Paint p;
							if (k == 0 && y == ya) {
								p = found.full();
							} else if (s.isCorner(i)) {
								p = frame.log(Direction.Axis.Y);
							} else if (y == yg || k > 0 && y == ya) {
								p = beam.log(s.axis());
							} else {
								p = face;
							}
							d.set(x, y, z, p);
						}
					}
				}
			}
		}
	}

	/** One storey of a body as a body of its own (for the shared low walls). */
	private static Body singleStorey(Body b, int k) {
		int[] r = b.rect(k);
		int y = k == 0 ? b.baseY : b.floorY(k) - 1;
		int h = k == 0 ? b.height(0) : b.height(k) + 1;
		return new Body(r[0], r[1], r[2], r[3], y, new int[]{h}, b.partyLeft, b.partyRight);
	}

	/** Upside-down stairs of the beam's timber under the oversailing loft, at each end and every other cell. */
	private static void brackets(Draft d, Body b) {
		Side s = b.side(1, Dirs.FRONT);
		int y = b.floorY(1) - 2;
		Mat beam = d.mat(Role.BEAM);
		try (var st = d.stage(StageKind.FRAME)) {
			for (int i = 0; i < s.length(); i += 2) {
				int x = s.x(i);
				int z = s.z(i);
				if (dev.voidmc.hearth.build.kit.townhome.Fronts.allowed(d, x, y, z) && d.isAbsent(x, y, z) && y > d.site().ground(x, z) + 1) {
					d.set(x, y, z, beam.stairsOrSlab(Dirs.BACK, Half.TOP));
				}
			}
		}
	}

	/**
	 * The shack's roof over its top storey: a lean-to of boards high over the lane and falling to the back (most), or a
	 * narrow gable to the lane, its eaves only over the lane and the yard, never onto the neighbours.
	 */
	private static Roofs.Roof roof(Draft d, Body b, Traits t) {
		int[] tr = b.topRect();
		int plate = b.plateY();
		// (a lean-to over a single storey would leave no room for its beds' heads: a gable there)
		Mat cover = t.thatch() ? d.mat(Role.ROOF) : d.chance(0.5) ? d.mat(Role.ROOF) : d.mat(Role.DOOR);
		boolean gable = d.chance(0.7) || t.eastern();
		int x0 = b.partyLeft ? tr[0] : Math.max(0, tr[0]);
		int x1 = b.partyRight ? tr[2] : Math.min(d.frontage() - 1, tr[2]);
		int z0 = tr[1] - 1;
		int z1 = Math.min(d.depth() - 1, tr[3] + 1);
		Roofs.Field f;
		int y0;
		if (gable) {
			double pitch = 1.0;
			Roofs.Profile p = dd -> 2 + pitch * dd;
			f = Roofs.gable(tr[0] - 1, z0, tr[2] + 1, z1, Direction.Axis.Z, p);
			y0 = plate - 1;
		} else {
			// a lean-to falling to the lane from a high back wall
			Roofs.Profile p = dd -> 2 + 0.5 * dd;
			f = Roofs.shed(x0, z0, x1, z1, Dirs.BACK, p);
			y0 = plate - 1;
		}
		int rx0 = gable && !b.partyLeft ? Math.max(0, tr[0] - 1) : x0;
		int rx1 = gable && !b.partyRight ? Math.min(d.frontage() - 1, tr[2] + 1) : x1;
		try (var s = d.stage(StageKind.ROOF)) {
			Mat trim = cover;
			Roofs.Roof roof = Roofs.raster(d, f, y0, rx0, Math.max(-1, z0), rx1, z1, cover, trim, Habits.Ridge.SLAB);
			dev.voidmc.hearth.build.kit.homestead.Bohemia.carryRidge(d, roof);
			return roof;
		}
	}

	/**
	 * The flue: a thin stack of rubble walling from over the fire up through the loft and the roof (a wall block, so it
	 * reads as a stovepipe), a campfire smoking on top.
	 */
	private static void flue(Draft d, int x, int z, int y, Roofs.Roof roof, int top, Direction wall) {
		Mat stone = d.mat(Role.CHIMNEY);
		int surface = roof != null && roof.covers(x, z) ? roof.surfaceY(x, z) : top - 2;
		int end = Math.min(d.maxHeight() - 2, surface + 2);
		try (var s = d.stage(StageKind.ROOF)) {
			for (int yy = y + 2; yy < end; yy++) {
				d.set(x, yy, z, yy <= surface ? stone.full() : stone.has(Form.WALL) ? stone.wall() : stone.full());
			}
			d.set(x, end, z, dev.voidmc.hearth.build.draft.States.lit(Blocks.CAMPFIRE, true)
				.trySetValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.SIGNAL_FIRE, false));
		}
	}

	/** The doorstep row: crates and a barrel by the door, a washing line from the loft to a pole where there is room. */
	private static void lane(Draft d, Body b, int door, int front) {
		int[] r = b.rect(0);
		int z = r[1] - 1;
		int y = b.baseY;
		for (int dx : new int[]{1, -1, 2, -2}) {
			int x = door + dx;
			if (x < 0 || x >= d.frontage() || !Spots.standable(d, x, y, z) || Spots.claimed(d, x, y, z) || !d.isEmpty(x, y + 1, z)) {
				continue;
			}
			if (Math.abs(dx) == 1) {
				continue;
			}
			Props.crates(d, x, y, z, Dirs.RIGHT, 1);
			break;
		}
		for (int x : new int[]{0, d.frontage() - 1}) {
			if (Math.abs(x - door) >= 2 && Spots.standable(d, x, y, z) && !Spots.claimed(d, x, y, z)) {
				Furniture.barrel(d, x, y, z, net.minecraft.core.Direction.UP);
				break;
			}
		}
	}

	/** The scrap of yard behind: a privy-like store of boards or a woodpile, a water butt, the washing line. */
	private static void yard(Draft d, Body b, boolean flueLeft) {
		int[] r = b.rect(0);
		int z = r[3] + 1;
		int y = b.baseY;
		if (z >= d.depth() || d.site().isWater(0, z)) {
			return;
		}
		Groundwork.level(d, 0, z, d.frontage() - 1, z, y);
		int x = flueLeft ? d.frontage() - 2 : 1;
		if (Spots.standable(d, x, y, z)) {
			Garden.woodpile(d, x, y, z, flueLeft ? Dirs.LEFT : Dirs.RIGHT, Math.min(2, d.frontage() - 3));
		}
		int bx = flueLeft ? 1 : d.frontage() - 2;
		if (Spots.standable(d, bx, y, z) && !Spots.claimed(d, bx, y, z)) {
			Garden.waterButt(d, bx, y, z);
		}
	}

	/** Unused: the life of the lane (a sheet out of the loft), kept for the street previews. */
	static void sheet(Draft d, Body b) {
		if (b.storeys() < 2) {
			return;
		}
		Side s = b.side(1, Dirs.FRONT);
		Life.sheet(d, s, s.middle(), b.floorY(1) + 1);
	}

	/** Unused helper (a slab of the boards for a shelf). */
	static Paint shelf(Draft d) {
		return d.mat(Role.DOOR).slab(SlabType.TOP);
	}
}
