package dev.voidmc.hearth.build.archetype.civic;

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
import dev.voidmc.hearth.build.draft.States;
import dev.voidmc.hearth.build.kit.Body;
import dev.voidmc.hearth.build.kit.Facade;
import dev.voidmc.hearth.build.kit.Garden;
import dev.voidmc.hearth.build.kit.Openings;
import dev.voidmc.hearth.build.kit.Rooms;
import dev.voidmc.hearth.build.kit.Side;
import dev.voidmc.hearth.build.kit.Stairs;
import dev.voidmc.hearth.build.kit.civic.Grounds;
import dev.voidmc.hearth.build.kit.civic.Halls;
import dev.voidmc.hearth.build.kit.civic.Signs;
import dev.voidmc.hearth.build.kit.civic.Storeys;
import dev.voidmc.hearth.build.kit.civic.Towers;
import dev.voidmc.hearth.build.kit.civic.Tradition;
import dev.voidmc.hearth.build.style.Form;
import dev.voidmc.hearth.build.style.Habits;
import dev.voidmc.hearth.build.style.Habits.RoofForm;
import dev.voidmc.hearth.build.style.Role;
import dev.voidmc.hearth.build.terrain.Groundwork;
import java.util.List;
import java.util.Set;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * {@code hearth:civic/guardhouse}: the watch's post. A stout two-storey house on a stone base: the guard room below
 * (the grindstone, racks of swords, crossbows and shields, helmets on the shelves, a table for cards, the stair), the
 * barracks above with their beds and lockers. Stone and southern traditions crown it with a walkable roof behind
 * battlements and a stair up to it; the others jetty the upper floor under a pitched roof and set a sentry box by the
 * door. A training dummy and a butt of arrows stand in its little yard; the town's banner hangs over the door.
 *
 * <p>POIs: the GUARD's JOB_SITE at the grindstone, GUARD_POSTs (by the door, on the roof), BEDs, STORAGE, SEAT, DOOR.
 *
 * <p>Params: {@code variant} (int).
 */
public final class Guardhouse implements Archetype {
	public static final ArchetypeInfo INFO = new ArchetypeInfo(Hearth.id("civic/guardhouse"), ArchetypeCategory.CIVIC, Set.of(JobType.GUARD), 2,
		7, 11, 7, 11, 8, 14, SettlementTier.VILLAGE, Set.of(), Set.of(), List.of(), 4, 1.0);

	@Override
	public ArchetypeInfo info() {
		return INFO;
	}

	@Override
	public void draw(Draft d) {
		int w = d.frontage();
		int dp = d.depth();
		int tier = d.tier();
		Habits h = d.habits();
		Tradition t = Tradition.of(d);
		boolean raised = t.raised();
		boolean flat = t.masonry() || t == Tradition.ADOBE;
		boolean yard = w >= 10 && !raised;
		int x0 = yard ? 0 : 0;
		int x1 = yard ? w - 4 : w - 1;
		int cx = (x0 + x1) / 2;
		int zF = raised ? 3 : 1;
		int z0 = zF;
		int z1 = dp - 1;
		int base = raised ? Grounds.stiltBase(d, x0, z0, x1, z1) : 0;
		Body body = new Body(x0, z0, x1, z1, base, new int[]{4, 4}, false, false);
		if (!flat && !raised && d.chance(0.6)) {
			body.jetty(1, Dirs.FRONT);
		}
		d.body(x0, z0, x1, z1);
		int plate = body.plateY();
		d.eave(plate);
		int fy = body.floorY(1);

		Storeys.ground(d, body, raised);
		Grounds.forecourt(d, 0, w - 1, z0 - 1, false);
		Facade fac = Facade.plan(d, body, cx, 1, 1, 0.5);
		Storeys.walls(d, body, fac, 0, 1, Storeys.stoneBase(d, 2), true);
		if (!Halls.masonry(d) && !raised) {
			Storeys.stoneStorey(d, body, 0);
			d.carve(x0 + 1, base, z0 + 1, x1 - 1, fy - 2, z1 - 1);
		}
		// slit windows below, ordinary ones above
		Storeys.windows(d, body, fac, 1, 1, 2);
		for (Side s : body.sides(0)) {
			for (Facade.Opening o : fac.openings(s.out())) {
				if (o.kind() == Facade.Kind.WINDOW && s.contains(o.at()) && !s.isCorner(s.index(o.at()))) {
					int x = s.x(s.index(o.at()));
					int z = s.z(s.index(o.at()));
					d.inStage(StageKind.OPENINGS, () -> d.set(x, base + 1, z, Blocks.IRON_BARS.defaultBlockState()));
				}
			}
		}
		Side front = body.side(0, Dirs.FRONT);
		Integer door = fac.openings(Dirs.FRONT).stream().filter(o -> o.kind() == Facade.Kind.DOOR).map(Facade.Opening::at).findFirst().orElse(cx);
		Openings.door(d, front, door, base, 1, new Openings.DoorOpts(true, true, true));
		d.poi(PoiType.GUARD_POST, door + (door > x0 + 1 ? -1 : 1), base, z0 - 1, Dirs.FRONT);
		Signs.Arms arms = Signs.arms(d);
		if (Openings.isWallBlock(d, door, fy + 1, body.rect(1)[1]) && body.rect(1)[1] - 1 >= -1) {
			int bz = body.rect(1)[1] - 1;
			if (d.isEmpty(door, fy + 1, bz) && (bz >= 0 || fy + 1 >= 3)) {
				Signs.wallBanner(d, door, fy + 1, bz, Dirs.FRONT, arms.field(), arms.layers());
			}
		}

		// --- the roof: battlements on a walkable deck, or the culture's pitched roof
		int skyline;
		int ix0 = x0 + 1;
		int ix1 = x1 - 1;
		int iz0 = z0 + 1;
		int iz1 = z1 - 1;
		boolean stairLeft = door > cx;
		int sx = stairLeft ? ix0 : ix1;
		int rise = 4;
		boolean deep = iz1 - iz0 + 1 >= rise + 1;
		Storeys.Top top;
		if (flat) {
			top = Storeys.roof(d, body, RoofForm.FLAT, Direction.Axis.X, 1.0, 1);
			Towers.battlements(d, x0, z0, x1, z1, top.deck());
			skyline = top.deck() + 2;
			// the stair from the barracks to the roof, against the other wall, through a hatch
			int rx = stairLeft ? ix1 : ix0;
			if (deep && iz1 - iz0 >= 4) {
				Stairs.straight(d, rx, fy, iz0, Dirs.BACK, 4, 1);
				for (int i = 1; i < 4; i++) {
					d.set(rx, plate, iz0 + i, Blocks.AIR.defaultBlockState());
				}
				d.set(rx, plate, iz0 + 3, d.mat(Role.FURNITURE).stairs(Dirs.BACK, Half.BOTTOM));
				d.poi(PoiType.GUARD_POST, ix0, top.deck(), z0 + 1, Dirs.FRONT);
				d.poi(PoiType.GUARD_POST, ix1, top.deck(), z0 + 1, Dirs.FRONT);
				d.poi(PoiType.VIEWPOINT, cx, top.deck(), z1 - 1, Dirs.BACK);
			}
		} else {
			RoofForm form = t == Tradition.KARST ? RoofForm.PAGODA : h.roof().pick(d.random(), RoofForm.GABLE, RoofForm.HIP, RoofForm.HALF_HIP);
			top = Storeys.roof(d, body, form, Direction.Axis.X, Math.max(1.0, h.roof().pitch()), 1);
			skyline = top.ridge();
			// a sentry box beside the door: two posts and a hood
			int bx = door + (door > x0 + 1 ? -2 : 2);
			if (!raised && z0 >= 1 && bx >= 0 && bx < w) {
				sentryBox(d, bx, base, z0 - 1);
			}
		}

		// --- the guard room: the stair up (along a side wall if the room is deep enough, else along the back)
		Direction sdir = deep ? Dirs.BACK : stairLeft ? Dirs.RIGHT : Dirs.LEFT;
		int s0x = deep ? sx : stairLeft ? ix0 : ix1;
		int s0z = deep ? iz0 : iz1;
		Stairs.straight(d, s0x, base, s0z, sdir, rise, 1);
		for (int i = 1; i < rise; i++) {
			d.set(s0x + Dirs.dx(sdir) * i, fy - 1, s0z + Dirs.dz(sdir) * i, Blocks.AIR.defaultBlockState());
		}
		d.set(s0x + Dirs.dx(sdir) * (rise - 1), fy - 1, s0z + Dirs.dz(sdir) * (rise - 1), d.mat(Role.FURNITURE).stairs(sdir, Half.BOTTOM));
		int landX = s0x + Dirs.dx(sdir) * rise;
		int landZ = s0z + Dirs.dz(sdir) * rise;
		int endX = s0x + Dirs.dx(sdir) * (rise - 1);
		int endZ = s0z + Dirs.dz(sdir) * (rise - 1);
		Rooms.Room room = new Rooms.Room(ix0, iz0, ix1, iz1, base, 3);
		room.keep(door, iz0);
		room.keepArea(s0x, s0z, endX, endZ);
		if (deep) {
			room.keep(sx + (stairLeft ? 1 : -1), iz0);
		} else {
			room.keep(s0x, iz1 - 1);
		}
		// the grindstone, chests and barrels where the room allows; racks of arms set into the side walls
		Rooms.furnish(d, room, Rooms.Type.WORKSHOP, 0, JobType.GUARD);
		List<List<Object[]>> racks = List.of(
			List.of(new Object[]{"minecraft:iron_sword", 1, 0}, new Object[]{"minecraft:crossbow", 1, 1}, new Object[]{"minecraft:shield", 1, 2}),
			List.of(new Object[]{"minecraft:iron_helmet", 1, 0}, new Object[]{"minecraft:iron_chestplate", 1, 1}, new Object[]{"minecraft:iron_helmet", 1, 2}),
			List.of(new Object[]{"minecraft:bow", 1, 0}, new Object[]{"minecraft:arrow", 16, 1}, new Object[]{"minecraft:iron_spear", 1, 2}));
		int ri = 0;
		for (Side s : new Side[]{body.side(0, Dirs.LEFT), body.side(0, Dirs.RIGHT)}) {
			for (int i2 = 1; i2 < s.last() && ri < racks.size(); i2++) {
				int x = s.x(i2);
				int z = s.z(i2);
				if (Openings.isWallBlock(d, x, base + 1, z) && Openings.isWallBlock(d, x, base + 2, z)) {
					rack(d, x, base + 1, z, s.out().getOpposite(), racks.get(ri++));
				}
			}
		}
		Rooms.furnish(d, room, Rooms.Type.LIVING, 0, JobType.NONE);
		if (room.width() >= 3 && room.length() >= 3) {
			Halls.lanternUnder(d, cx, base, (iz0 + iz1) / 2, 0);
		}
		dev.voidmc.hearth.build.kit.Floors.joists(d, ix0, iz0, ix1, iz1, fy - 1, Direction.Axis.X, 2, base);

		// --- the barracks upstairs
		int[] up = body.rect(1);
		Rooms.Room bunks = new Rooms.Room(up[0] + 1, up[1] + 1, up[2] - 1, up[3] - 1, fy, 3);
		bunks.keep(landX, landZ);
		bunks.keepArea(s0x, s0z, endX, endZ);
		if (flat && deep && iz1 - iz0 >= 4) {
			int rx = stairLeft ? ix1 : ix0;
			bunks.keepArea(rx, iz0, rx, iz0 + 3);
			bunks.keep(rx, iz0 + 4 <= up[3] - 1 ? iz0 + 4 : iz0 - 1);
			// the roof stair's foot: from in front of it, or (against the front wall) from beside its first step
			if (iz0 - 1 >= up[1] + 1) {
				bunks.keep(rx, iz0 - 1);
			} else {
				bunks.keep(rx + (rx == ix0 ? 1 : -1), iz0);
			}
		}
		Rooms.furnish(d, bunks, Rooms.Type.BEDROOM, (up[2] - up[0] - 1) * (up[3] - up[1] - 1) >= 20 ? 3 : 2, JobType.NONE);

		// --- the yard: a training dummy, a target, a butt of arrows
		if (yard) {
			int yx0 = x1 + 1;
			int yx1 = w - 1;
			Groundwork.level(d, yx0, 1, yx1, dp - 1, 0);
			int dz = Math.min(dp - 2, 4);
			if (d.isEmpty(yx0 + 1, 0, dz) && Groundwork.surface(d, yx0 + 1, dz) == 0) {
				try (var s = d.stage(StageKind.EXTERIOR)) {
					d.set(yx0 + 1, 0, dz, Halls.post(d));
					d.set(yx0 + 1, 1, dz, Blocks.HAY_BLOCK.defaultBlockState());
					d.set(yx0 + 1, 2, dz, States.facing(Blocks.CARVED_PUMPKIN, Dirs.FRONT));
				}
			}
			int tz = dp - 2;
			if (d.isEmpty(yx1 - 1, 0, tz) && Groundwork.surface(d, yx1 - 1, tz) == 0) {
				try (var s = d.stage(StageKind.EXTERIOR)) {
					d.set(yx1 - 1, 0, tz, Halls.post(d));
					d.set(yx1 - 1, 1, tz, Blocks.TARGET.defaultBlockState());
				}
			}
			java.util.List<int[]> fence = new java.util.ArrayList<>();
			for (int z = 1; z <= dp - 1; z++) {
				fence.add(new int[]{yx1, z});
			}
			Garden.fence(d, fence, -1, Dirs.FRONT);
			if (!d.site().isWater(yx0, 1) && Groundwork.surface(d, yx0, 1) == 0 && d.isEmpty(yx0, 0, 1)) {
				Garden.barrels(d, yx0, 0, 1, Dirs.RIGHT, 1);
			}
		}
		Grounds.approach(d, door, base, z0 - 1);
		if (h.snow()) {
			Garden.snowBanks(d, x0, z0, x1, z1, base, door);
		}
		Groundwork.clear(d, 0, 0, w - 1, dp - 1, skyline + 2);
	}

	/** A rack of arms: a shelf on the wall at (x, y, z) facing {@code facing} into the room, with its items shown. */
	static void rack(Draft d, int x, int y, int z, Direction facing, List<Object[]> items) {
		var shelf = d.mat(Role.FURNITURE).main(Form.SHELF);
		if (shelf == null) {
			return;
		}
		d.inStage(StageKind.INTERIOR, () -> d.set(x, y, z, States.facing(shelf, facing), BlockEntities.shelf(items)));
	}

	/** A sentry box: a little booth of two posts under a slab hood beside a door, its standing place a guard post. */
	static void sentryBox(Draft d, int x, int y, int z) {
		if (!d.isEmpty(x, y, z) || !d.isEmpty(x, y + 1, z) || Math.abs(Groundwork.surface(d, x, z) - y) > 0) {
			return;
		}
		Mat frame = d.mat(Role.FRAME);
		Mat roof = d.mat(Role.ROOF);
		try (var s = d.stage(StageKind.EXTERIOR)) {
			for (int dx : new int[]{-1, 1}) {
				int px = x + dx;
				if (px < 0 || px >= d.frontage() || !d.isEmpty(px, y, z)) {
					continue;
				}
				d.set(px, y, z, frame.log(Direction.Axis.Y));
				d.set(px, y + 1, z, frame.log(Direction.Axis.Y));
				d.set(px, y + 2, z, roof.stairsOrSlab(dx < 0 ? Dirs.RIGHT : Dirs.LEFT, Half.BOTTOM));
			}
			d.set(x, y + 2, z, roof.slab(SlabType.TOP));
		}
		d.poi(PoiType.GUARD_POST, x, y, z, Dirs.FRONT);
	}
}
