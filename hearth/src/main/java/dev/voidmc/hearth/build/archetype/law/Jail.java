package dev.voidmc.hearth.build.archetype.law;

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
import dev.voidmc.hearth.build.draft.States;
import dev.voidmc.hearth.build.kit.Body;
import dev.voidmc.hearth.build.kit.Furniture;
import dev.voidmc.hearth.build.kit.Garden;
import dev.voidmc.hearth.build.kit.Openings;
import dev.voidmc.hearth.build.kit.Rooms;
import dev.voidmc.hearth.build.kit.Side;
import dev.voidmc.hearth.build.kit.Stairs;
import dev.voidmc.hearth.build.kit.civic.Pueblo;
import dev.voidmc.hearth.build.kit.civic.Grounds;
import dev.voidmc.hearth.build.kit.civic.Halls;
import dev.voidmc.hearth.build.kit.civic.Signs;
import dev.voidmc.hearth.build.kit.civic.Storeys;
import dev.voidmc.hearth.build.kit.civic.Towers;
import dev.voidmc.hearth.build.kit.civic.Tradition;
import dev.voidmc.hearth.build.kit.landmark.Details;
import dev.voidmc.hearth.build.kit.landmark.Stone;
import dev.voidmc.hearth.build.kit.lawhouse.Cells;
import dev.voidmc.hearth.build.kit.lawhouse.Fenestration;
import dev.voidmc.hearth.build.kit.lawhouse.Masonry;
import dev.voidmc.hearth.build.kit.light.Lighting;
import dev.voidmc.hearth.build.style.Form;
import dev.voidmc.hearth.build.style.Habits.RoofForm;
import dev.voidmc.hearth.build.style.Role;
import dev.voidmc.hearth.build.terrain.Groundwork;
import java.util.List;
import java.util.Set;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.Half;

/**
 * {@code hearth:law/jail}: the town's jail, a strong house of two storeys. Its ground storey is bare dressed stone (the
 * plinth's) whatever the town builds in, quoined and nearly blind: barred slits to the cells, two barred windows either
 * side of the door; the door itself deep in a round-headed portal of the dressing with an iron grille in its head,
 * lanterns either side and the jail's sign hanging over the street. The upper storey, where the jailer and his turnkey
 * live, wears the town's walls and ordinary windows. A heavy hipped roof (or, in the masonry and southern traditions, a
 * flat roof behind battlements) with the jailer's chimney.
 *
 * <p>Inside: the guard room across the front (the jailer's desk with the register, the key rack, the grindstone and the
 * rack of arms, a table, the chest of confiscated goods) with the stair up along one wall; behind it the cells side by
 * side behind iron bars, each with its iron door (picked, never opened by hand), straw, a bucket and a chain, lanterns
 * hung before the bars. Upstairs the jailer's room and the turnkey's bunk, a hearth.
 *
 * <p>POIs: CELL (one per cell, inside the bars), JOB_SITE (JAILER at the desk, GUARD at the grindstone), GUARD_POST by the
 * door, STORAGE, BED, SEAT, TABLE, LECTERN, DOOR.
 *
 * <p>Params: {@code variant} (int).
 */
public final class Jail implements Archetype {
	public static final ArchetypeInfo INFO = new ArchetypeInfo(Hearth.id("law/jail"), ArchetypeCategory.LAW, Set.of(JobType.JAILER, JobType.GUARD), 2,
		9, 13, 10, 13, 12, 16, SettlementTier.TOWN, Set.of(), Set.of(), List.of(), 1, 1.0);

	@Override
	public ArchetypeInfo info() {
		return INFO;
	}

	@Override
	public void draw(Draft d) {
		int w = d.frontage();
		int dp = d.depth();
		int maxH = d.maxHeight();
		Tradition t = Tradition.of(d);
		int x0 = w >= 12 ? 1 : 0;
		int x1 = w - 1 - x0;
		int cx = (x0 + x1) / 2;
		int z0 = 2;
		int z1 = dp - 1;
		int base = Grounds.raisedBase(d, x0, x1, z0);
		int h0 = 5;
		int h1 = 4;
		Body body = new Body(x0, z0, x1, z1, base, new int[]{h0, h1}, false, false);
		// the mesa's lock-up steps its jailer's storey back from the front, the strong storey's roof a terrace before it
		boolean pueblo = Pueblo.is(d);
		if (pueblo) {
			body.setback(1, Dirs.FRONT, 2);
		}
		d.body(x0, z0, x1, z1);
		int plate = body.plateY();
		d.eave(plate);
		int fy = body.floorY(1);
		Stone dress = Stone.dressing(d);
		Stone wall = Masonry.walling(d);
		Stone plinth = Masonry.plinth(d);
		Stone rusticated = Stone.stony(d, Role.PLINTH) ? Stone.of(d, Role.PLINTH) : Stone.masonry(d);

		// --- the shell: a stone ground storey, the town's walls above
		Storeys.ground(d, body, false);
		Grounds.forecourt(d, 0, w - 1, z0 - 1, true);
		Masonry.storey(d, body, 0, rusticated, dress, plinth, true);
		Masonry.storey(d, body, 1, wall, dress, plinth, false);
		try (var st = d.stage(StageKind.WALLS)) {
			for (Side s : body.sides(0)) {
				Details.course(d, s, 0, s.last(), fy - 1, dress);
			}
		}

		// --- the plan: the stair up one side wall, the guard room across the front, the cells behind
		boolean stairLeft = d.chance(0.5);
		int ix0 = x0 + 1;
		int ix1 = x1 - 1;
		int iz0 = z0 + 1;
		int iz1 = z1 - 1;
		int sx = stairLeft ? ix0 : ix1;
		int gd = dp >= 12 ? 3 : 2;
		int barZ = iz0 + gd;
		int doorX = cx;
		Side front = body.side(0, Dirs.FRONT);
		Side up = body.side(1, Dirs.FRONT);

		// the door in its portal, the grille over it, lanterns either side, the sign
		try (var st = d.stage(StageKind.OPENINGS)) {
			Details.arch(d, front, front.index(doorX), 1, base, 3, dress, dev.voidmc.hearth.build.draft.Paint.of(Blocks.IRON_BARS.defaultBlockState()));
		}
		Openings.door(d, front, doorX, base, 1, new Openings.DoorOpts(false, false, true));
		for (int side : new int[]{-1, 1}) {
			int lx = doorX + side;
			if (z0 - 1 >= 0 && d.isEmpty(lx, base + 1, z0 - 1)) {
				Lighting.doorLamp(d, lx, base + 1, z0 - 1, Dirs.BACK);
			}
		}
		int signX = doorX + (stairLeft ? 2 : -2);
		if (z0 - 1 >= 0 && signX > x0 && signX < x1 && d.isEmpty(signX, base + 3, z0 - 1)) {
			Signs.hangingSign(d, signX, base + 3, z0 - 1, stairLeft ? Dirs.RIGHT : Dirs.LEFT,
				List.of(new String[]{"", ""}, new String[]{"hearth.build.law.jail", "Jail"}, new String[]{"", ""}));
		}
		d.poi(PoiType.GUARD_POST, doorX + (stairLeft ? -1 : 1), base, z0 - 1, Dirs.FRONT);
		// barred windows either side of the door, slits to the cells in the back and side walls
		for (int off : new int[]{-3, 3}) {
			int x = doorX + off;
			int i = front.index(x);
			if (i >= 1 && i <= front.last() - 1 && Math.abs(x - sx) >= 1 && x != signX) {
				Fenestration.framed(d, front, i, 1, base + 1, 2, dress, false, true, false);
			}
		}
		Side back = body.side(0, Dirs.BACK);
		for (int i = 2; i <= back.last() - 2; i += 2) {
			Fenestration.barred(d, back, i, base + 3, 1, dress);
		}
		// the upper storey's windows, plain
		for (Side s : body.sides(1)) {
			for (int i : Masonry.bays(s.length(), 3)) {
				Openings.window(d, s, s.column(i), fy + 1, 1, 2, Openings.WindowStyle.DRESSED);
			}
		}

		// --- the roof: hipped and heavy, or a flat roof behind battlements in the masonry and southern traditions
		boolean flat = t == Tradition.STONE && d.chance(0.5) || t == Tradition.MEDITERRANEAN || t == Tradition.ADOBE;
		Storeys.Top top;
		if (flat) {
			top = Storeys.roof(d, body, RoofForm.FLAT, Direction.Axis.X, 1.0, 1);
			int[] tr = body.topRect();
			Towers.battlements(d, tr[0], tr[1], tr[2], tr[3], top.deck());
			if (pueblo) {
				// the terrace over the strong storey, a door out to it from the jailer's storey
				dev.voidmc.hearth.build.kit.Roofs.terrace(d, body.rect(0), body.rect(1), fy - 1);
				Openings.door(d, up, cx, fy, 1, new Openings.DoorOpts(false, false, false));
			}
		} else {
			RoofForm form = t == Tradition.KARST ? RoofForm.PAGODA : RoofForm.HIP;
			top = Storeys.roof(d, body, form, Direction.Axis.X, 1.0, 2);
		}
		int skyline = top.ridge();

		// --- the guard room
		Stairs.straight(d, sx, base, iz0, Dirs.BACK, h0, 1);
		try (var st = d.stage(StageKind.FLOORS)) {
			for (int i = h0 - 3; i < h0; i++) {
				d.set(sx, fy - 1, iz0 + i, Blocks.AIR.defaultBlockState());
			}
			d.set(sx, fy - 1, iz0 + h0 - 1, d.mat(Role.FURNITURE).stairs(Dirs.BACK, Half.BOTTOM));
		}
		// a wall between the stair and the cells
		int sideWallX = stairLeft ? sx + 1 : sx - 1;
		try (var st = d.stage(StageKind.WALLS)) {
			for (int z = barZ; z <= iz1; z++) {
				for (int y = base; y < fy - 1; y++) {
					d.set(sideWallX, y, z, rusticated.full());
				}
			}
		}
		int gx0 = stairLeft ? sx + 1 : ix0;
		int gx1 = stairLeft ? ix1 : sx - 1;
		Rooms.Room guard = new Rooms.Room(gx0, iz0, gx1, barZ - 1, base, h0 - 1);
		guard.keep(doorX, iz0);
		// the stair is climbed from beside its first step
		guard.keep(sx + (stairLeft ? 1 : -1), iz0);
		for (int x = gx0; x <= gx1; x++) {
			guard.keepArea(x, barZ - 1, x, barZ - 1);
		}
		// the cells behind their bars
		int cx0 = stairLeft ? sideWallX + 1 : ix0;
		int cx1 = stairLeft ? ix1 : sideWallX - 1;
		List<int[]> cells = Cells.along(d, cx0, cx1, barZ, iz1, base, h0 - 1, rusticated, dress);
		// lanterns hung before the bars, over the way along them
		for (int[] c : cells) {
			Lighting.chandelier(d, c[0], barZ - 1, fy - 1, fy - 2);
		}
		for (int[] c : cells) {
			Lighting.room(d, Math.max(cx0, c[0] - 1), barZ + 1, Math.min(cx1, c[0] + 1), iz1, base);
		}
		int gsX = stairLeft ? gx1 : gx0;
		if (guard.claim(gsX, iz0)) {
			Furniture.workstation(d, JobType.GUARD, gsX, base, iz0, stairLeft ? Dirs.LEFT : Dirs.RIGHT);
			guard.keep(gsX + (stairLeft ? -1 : 1), iz0);
		}
		// the jailer's desk with the register (his job site), the key rack, the grindstone, the chest of confiscated goods
		int gsFront = gsX + (stairLeft ? -1 : 1);
		int deskX = Integer.MIN_VALUE;
		for (int off : new int[]{2, -2, 3, -3, 1, -1, 4, -4}) {
			int x = doorX + (stairLeft ? off : -off);
			if (guard.inside(x, iz0) && x != gsFront && x != sx && guard.claim(x, iz0)) {
				deskX = x;
				break;
			}
		}
		if (deskX != Integer.MIN_VALUE) {
			int dx = deskX;
			d.inStage(StageKind.INTERIOR, () -> d.set(dx, base, iz0, States.facing(Blocks.LECTERN, Dirs.BACK),
				BlockEntities.lectern("Register of Prisoners", "The Jailer", List.of("Taken in, and let go, by the day."))));
			d.poi(PoiType.JOB_SITE, dx, base, iz0 + 1, Dirs.FRONT, JobType.JAILER);
			d.poi(PoiType.LECTERN, dx, base, iz0 + 1, Dirs.FRONT);
			guard.keep(dx, iz0 + 1);
			// the key rack on the wall over the desk
			var shelf = d.mat(Role.FURNITURE).main(Form.SHELF);
			if (shelf != null && Openings.isWallBlock(d, dx, base + 2, z0)) {
				d.inStage(StageKind.INTERIOR, () -> d.set(dx, base + 2, iz0, States.facing(shelf, Dirs.BACK), BlockEntities.shelf(List.of(
					new Object[]{"minecraft:tripwire_hook", 1, 0}, new Object[]{"minecraft:tripwire_hook", 1, 1}, new Object[]{"minecraft:tripwire_hook", 1, 2}))));
			}
		} else {
			// no free place on the front wall: the jailer works at the bars, by the first cell
			int[] first = cells.get(0);
			d.poi(PoiType.JOB_SITE, first[0], base, barZ - 1, Dirs.BACK, JobType.JAILER);
		}
		// the chest of confiscated goods against the front wall (or, with no room there, upstairs)
		boolean stored = false;
		for (int off : new int[]{-2, 2, -3, 3, -1, 1, -4, 4}) {
			int x = doorX + (stairLeft ? off : -off);
			if (guard.inside(x, iz0) && x != sx && x != gsFront && guard.claim(x, iz0)) {
				Furniture.chest(d, x, base, iz0, Dirs.BACK, List.of(new Object[]{"minecraft:iron_sword", 1}, new Object[]{"minecraft:leather_boots", 1},
					new Object[]{"minecraft:emerald", 2}));
				stored = true;
				break;
			}
		}
		if (gd >= 3) {
			int tx = stairLeft ? gx1 - 1 : gx0 + 1;
			if (guard.claim(tx, iz0 + 1)) {
				Furniture.table(d, tx, base, iz0 + 1);
				if (guard.claim(tx + (stairLeft ? -1 : 1), iz0 + 1)) {
					Furniture.chair(d, tx + (stairLeft ? -1 : 1), base, iz0 + 1, stairLeft ? Dirs.LEFT : Dirs.RIGHT);
				}
			}
		}
		Rooms.furnish(d, dev.voidmc.hearth.build.kit.civic.Halls.keepOffWells(d, guard), Rooms.Type.LIVING, 0, JobType.NONE);
		Lighting.room(d, guard);

		// --- upstairs: the jailer's room and the turnkey's bunk, a hearth with its chimney
		int[] ur = body.rect(1);
		Rooms.Room quarters = new Rooms.Room(ur[0] + 1, ur[1] + 1, ur[2] - 1, ur[3] - 1, fy, h1 - 1);
		quarters.keep(sx, iz0 + h0);
		quarters.keepArea(sx, iz0, sx, iz0 + h0 - 1);
		quarters.keepArea(sx + (stairLeft ? 1 : -1), iz0 + h0 - 3, sx + (stairLeft ? 1 : -1), iz0 + h0 - 1);
		if (pueblo) {
			// the way in from the terrace door
			quarters.keepArea(cx, ur[1] + 1, cx, ur[1] + 2);
		}
		int hx = stairLeft ? ur[2] - 1 : ur[0] + 1;
		int hz = (ur[1] + ur[3]) / 2;
		if (top.roof() != null && quarters.claim(hx, hz)) {
			Furniture.hearth(d, hx, fy, hz, stairLeft ? Dirs.LEFT : Dirs.RIGHT, false);
			skyline = Math.max(skyline, dev.voidmc.hearth.build.kit.Chimneys.stack(d, stairLeft ? ur[2] : ur[0], hz, fy + 1, top.roof(), top.roof().ridgeY));
		}
		if (!stored) {
			int qx = stairLeft ? ur[2] - 1 : ur[0] + 1;
			for (int z = ur[3] - 1; z > ur[1]; z--) {
				if (quarters.claim(qx, z)) {
					Furniture.chest(d, qx, fy, z, stairLeft ? Dirs.LEFT : Dirs.RIGHT, List.of(new Object[]{"minecraft:iron_sword", 1},
						new Object[]{"minecraft:emerald", 2}));
					break;
				}
			}
		}
		Rooms.furnish(d, dev.voidmc.hearth.build.kit.civic.Halls.keepOffWells(d, quarters), Rooms.Type.BEDROOM, 2, JobType.NONE);
		Lighting.room(d, quarters);

		Grounds.flight(d, doorX - 1, doorX + 1, z0 - 1, base, base);
		Grounds.approach(d, doorX, base, z0 - 1);
		Halls.lamp(d, 0, 0, 0, 2);
		if (d.habits().snow()) {
			Garden.snowBanks(d, x0, z0, x1, z1, base, doorX);
		}
		// the mesa's pueblo: a stepped room over the flat roof, a ladder up to its terrace
		int crown = pueblo ? Pueblo.crown(d, body.topRect()) : Integer.MIN_VALUE;
		dev.voidmc.hearth.build.kit.civic.Halls.finish(d);
		Groundwork.clear(d, 0, 0, w - 1, dp - 1, Math.max(Math.min(maxH - 1, skyline + 2), crown + 2));
	}
}
