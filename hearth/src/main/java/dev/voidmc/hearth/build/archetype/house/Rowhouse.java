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
import dev.voidmc.hearth.build.kit.Body;
import dev.voidmc.hearth.build.kit.Facade;
import dev.voidmc.hearth.build.kit.Garden;
import dev.voidmc.hearth.build.kit.Openings;
import dev.voidmc.hearth.build.kit.Porches;
import dev.voidmc.hearth.build.kit.Roofs;
import dev.voidmc.hearth.build.kit.Rooms;
import dev.voidmc.hearth.build.kit.Side;
import dev.voidmc.hearth.build.kit.townhome.Fronts;
import dev.voidmc.hearth.build.kit.townhome.Life;
import dev.voidmc.hearth.build.kit.townhome.Projections;
import dev.voidmc.hearth.build.kit.townhome.StairCore;
import dev.voidmc.hearth.build.kit.townhome.Traits;
import dev.voidmc.hearth.build.style.Habits;
import dev.voidmc.hearth.build.style.Habits.RoofForm;
import dev.voidmc.hearth.build.style.Habits.WallKind;
import dev.voidmc.hearth.build.style.Role;
import dev.voidmc.hearth.build.terrain.Groundwork;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.properties.Half;

/**
 * {@code hearth:house/rowhouse}: the two-storey terraced house a village grows into when its houses start to share
 * walls. A narrow front on the street, sometimes behind a strip of front garden with a low wall or railing and a gate,
 * a door under a hood beside a bay window or the parlour's window, two bedroom windows over it, a roof storey with
 * dormers; behind, a lean-to scullery outshot and a yard with the washing out. In a row the party walls rise over the
 * roof with the chimney stacks on them in masonry towns; the end house turns its roof toward the side street. Flat-
 * roofed cultures give it a roof terrace (a pueblo steps its upper storey back), stilt cultures a double gallery.
 *
 * <p>Params:
 * <ul>
 *   <li>{@code storeys} (int, 2): full storeys under the roof (a third now and then on a deep plot by seed)</li>
 *   <li>{@code party_left}, {@code party_right}, {@code corner_left}, {@code corner_right} (boolean): as for the
 *   townhouse; read through {@code Draft.partyLocalLeft/Right}</li>
 *   <li>{@code garden} (boolean): a front garden strip behind a low wall (default by seed on deep plots)</li>
 *   <li>{@code outshot} (boolean): a lean-to scullery behind (default by seed on deep plots)</li>
 *   <li>{@code roof} (RoofForm name), {@code variant} (int)</li>
 * </ul>
 */
public final class Rowhouse implements Archetype {
	public static final ArchetypeInfo INFO = new ArchetypeInfo(Hearth.id("house/rowhouse"), ArchetypeCategory.HOUSING, Set.of(), 2,
		5, 7, 8, 12, 9, 14, SettlementTier.VILLAGE, Set.of(), Set.of(ArchetypeInfo.SiteFlag.ATTACHABLE),
		List.of(Hearth.id("house/townhouse"), Hearth.id("house/tenement")), 0, 1.0);

	private static final RoofForm[] FORMS = {RoofForm.GABLE, RoofForm.HALF_HIP, RoofForm.HIP, RoofForm.GAMBREL, RoofForm.PAGODA, RoofForm.FLAT,
		RoofForm.CROSS_GABLE, RoofForm.DOME, RoofForm.CONE, RoofForm.SALTBOX};

	@Override
	public ArchetypeInfo info() {
		return INFO;
	}

	@Override
	public void draw(Draft d) {
		int w = d.frontage();
		int dp = d.depth();
		Habits h = d.habits();
		Traits t = Traits.of(d);
		boolean partyL = d.partyLocalLeft();
		boolean partyR = d.partyLocalRight();
		boolean cornerL = TownHouses.cornerLocalLeft(d) && !partyL;
		boolean cornerR = TownHouses.cornerLocalRight(d) && !partyR;

		RoofForm form = HouseBuilder.roofParam(d, h.roof().pick(d.random(), FORMS));
		if (form == RoofForm.DOME || form == RoofForm.CONE) {
			form = form == RoofForm.DOME ? RoofForm.FLAT : RoofForm.HIP;
		}
		if (form == RoofForm.SALTBOX && (partyL || partyR)) {
			form = RoofForm.GABLE;
		}
		boolean flat = form == RoofForm.FLAT;
		boolean raised = t.raised() && dp >= 10;
		int sh = Math.max(4, h.walls().storeyHeight());
		int full = Math.clamp(d.param("storeys", 2) + (dp >= 11 && d.chance(0.15) ? 1 : 0), 2, 3);

		// a front strip (garden or stoop), the body, the yard behind
		int door = TownHouses.doorColumn(d, 0, w - 1, partyL, partyR);
		boolean awkward = TownHouses.misfit(d, door) > 0;
		boolean garden = !raised && dp >= 10 && d.param("garden", d.chance(0.45));
		int setback = raised ? 2 : garden ? 2 : dp >= 9 || awkward || t.pueblo() && flat ? 1 : 0;
		int rest = dp - setback;
		boolean outshot = !flat && rest >= 10 && d.param("outshot", d.chance(0.55));
		int back = outshot ? 3 : rest >= 9 ? 2 : rest >= 8 ? 1 : 0;
		int bz0 = setback;
		int bz1 = dp - 1 - back;
		Direction.Axis ridge = d.chance(h.roof().frontGable() * 0.8) ? Direction.Axis.Z : Direction.Axis.X;
		if (form == RoofForm.HIP || form == RoofForm.PAGODA) {
			ridge = partyL || partyR ? Direction.Axis.X : Direction.Axis.Z;
		}
		if (form == RoofForm.CROSS_GABLE || form == RoofForm.GAMBREL) {
			ridge = Direction.Axis.X;
		}
		Roofs.Opts opts = Roofs.Opts.of(d);
		if (t.eastern() && (partyL || partyR)) {
			form = RoofForm.HIP;
			opts = opts.withPitch(0.6);
		}
		// a corner house hips its roof toward the side street where the culture hips at all
		if ((cornerL || cornerR) && !flat && ridge == Direction.Axis.X && h.roof().weight(RoofForm.HIP) + h.roof().weight(RoofForm.HALF_HIP) > 0) {
			form = h.roof().weight(RoofForm.HALF_HIP) > h.roof().weight(RoofForm.HIP) ? RoofForm.HALF_HIP : RoofForm.HIP;
		}

		Body body = null;
		for (int pass = 0; pass < 6; pass++) {
			body = new Body(0, bz0, w - 1, bz1, 0, TownHouses.heights(full, sh, sh, !flat), partyL, partyR);
			if (t.pueblo() && flat && body.depth() >= 8 && w >= 7) {
				body.setback(full - 1, Dirs.FRONT, Math.min(3, body.depth() - 5));
			} else if (!flat && !raised && (h.walls().kind(1) == WallKind.TIMBER_FRAME) && d.chance(h.walls().jetty()) && bz0 >= 1) {
				body.jetty(1, Dirs.FRONT);
			}
			int top = TownHouses.ridge(d, body, form, ridge, opts) + (flat ? 1 : 2);
			if (top < d.maxHeight()) {
				break;
			}
			if (full > 2) {
				full--;
			} else if (opts.pitch() > 0.5) {
				opts = opts.withPitch(0.5);
			} else {
				form = RoofForm.FLAT;
				flat = true;
			}
		}
		d.body(0, bz0, w - 1, bz1);

		Facade facade = TownHouses.planWithDoor(d, body, door, 1, t.timber() && !t.eastern() && w >= 7 ? 2 : 1, 0.75);
		if ((t.walls() == WallKind.TIMBER_FRAME || t.walls() == WallKind.BAMBOO || t.walls() == WallKind.PLANK) && d.chance(0.75)) {
			facade = TownHouses.pairedPanels(facade);
		}
		Integer placed = HouseBuilder.doorColumn(facade);
		if (placed != null) {
			door = placed;
		}
		d.eave(body.plateY());

		Groundwork.footings(d, 0, bz0, w - 1, bz1, -1);
		boolean rusticate = t.walls() == WallKind.PLASTER && h.walls().kind(1) == WallKind.PLASTER && d.chance(0.25);
		TownHouses.shell(d, body, facade, rusticate);
		Openings.WindowStyle style = TownHouses.windowStyle(d, t);
		Set<Integer> skip = new HashSet<>();
		TownHouses.openings(d, body, facade, t, style, skip, true);
		Side front0 = body.side(0, Dirs.FRONT);
		Fronts.doorCase(d, front0, door, 1, 0, t);
		TownHouses.dress(d, body, t, flat, cornerL || cornerR, cornerL ? Dirs.LEFT : Dirs.RIGHT);

		// the front: a bay window beside the door, a balcony over it, or a double gallery on posts
		if (raised) {
			int[] floors = new int[full];
			for (int k = 0; k < full; k++) {
				floors[k] = body.floorY(k);
			}
			Projections.postedGalleries(d, front0, 0, front0.last(), floors, 2, body.floorY(full) - 1, t.lattice(), front0.index(door));
			for (int k = 1; k < full; k++) {
				TownHouses.galleryDoor(d, body.side(k, Dirs.FRONT), body.floorY(k), door);
			}
		} else if (t.eastern()) {
			Side s1 = body.side(1, Dirs.FRONT);
			if (d.chance(Math.max(0.5, h.balcony()))) {
				Projections.gallery(d, s1, 0, s1.last(), body.floorY(1), true, false);
				TownHouses.galleryDoor(d, s1, body.floorY(1), door);
			} else {
				Fronts.pent(d, front0, 0, front0.last(), body.floorY(1) - 1, 1);
			}
		} else {
			if (bz0 >= 1 && w >= 6 && d.chance(Math.max(0.3, h.windows().bay()))) {
				for (Facade.Opening o : facade.openings(Dirs.FRONT)) {
					int i = front0.index(o.at());
					if (o.kind() == Facade.Kind.WINDOW && i >= 2 && i + o.width() <= front0.last() - 1 && Math.abs(o.at() - door) >= 2) {
						Porches.bay(d, front0, o.at() - (o.width() == 1 ? 1 : 0), o.at() + o.width() - (o.width() == 1 ? 0 : 1), 0);
						break;
					}
				}
			}
			if (d.chance(h.balcony())) {
				Side s1 = body.side(1, Dirs.FRONT);
				int i = s1.index(door);
				Porches.balcony(d, s1, s1.column(Math.max(1, i - 1)), s1.column(Math.min(s1.last() - 1, i + 1)), body.floorY(1));
				TownHouses.galleryDoor(d, s1, body.floorY(1), door);
				if (d.chance(0.5)) {
					Life.airing(d, s1.outX(Math.max(1, i - 1), 1), body.floorY(1) + 1, s1.outZ(Math.max(1, i - 1), 1));
				}
			}
		}

		// roof
		int topK = body.storeys() - 1;
		int[] tr = body.topRect();
		Roofs.Roof roof = null;
		int deck = Integer.MIN_VALUE;
		if (flat) {
			if (t.pueblo() && body.outset(full - 1, Dirs.FRONT) != 0) {
				TownHouses.terraces(d, body, full);
			}
			deck = TownHouses.flat(d, body, t.walls() == WallKind.ADOBE && !t.pueblo() && d.chance(0.4));
		} else {
			boolean dormers = d.chance(Math.max(0.4, h.roof().dormers()));
			roof = TownHouses.pitched(d, body, form, ridge, opts, t, facade, dormers, t.stoneGables() && ridge == Direction.Axis.Z && d.chance(0.5));
		}

		boolean wellLeft = door > (w - 1) / 2;
		int[] hearth = TownHouses.chimney(d, body, roof, deck, wellLeft, Math.max(h.chimney(), t.masonry() && !t.flat() ? 0.6 : 0));
		boolean pueblo = t.pueblo() && flat && body.outset(full - 1, Dirs.FRONT) != 0;
		StairCore core = pueblo ? null : TownHouses.core(d, body, door, topK, roof);
		if (pueblo) {
			TownHouses.puebloLadders(d, body, full, deck, door);
		} else if (flat) {
			int[] exit = core.bulkhead(d, deck, d.maxHeight());
			if (exit != null) {
				Life.roofGarden(d, tr[0] + 1, tr[1] + 1, tr[2] - 1, tr[3] - 1, deck, exit[0], exit[2], false);
				int ly = deck + 2;
				if (tr[2] - tr[0] >= 4 && ly < d.maxHeight()) {
					Life.line(d, tr[0], tr[3] - 1, tr[2], tr[3] - 1, ly);
				}
			}
		}

		// the scullery outshot behind, its door through the back wall
		int[] backDoor = null;
		if (outshot && roof != null && core != null) {
			backDoor = outshot(d, body, core, hearth);
		}

		// rooms: kitchen and parlour below, bedrooms above, one under the roof
		for (int k = 0; k < body.storeys() && TownHouses.reached(core, k); k++) {
			int[] r = body.rect(k);
			boolean top = k == topK;
			Rooms.Room room = TownHouses.room(d, body, k, top ? roof : null);
			if (room == null) {
				continue;
			}
			if (core != null) {
				core.keep(room, k);
			}
			TownHouses.keepDoors(d, room, r, body.floorY(k));
			if (k == 0) {
				room.keep(door, r[1] + 1);
				if (backDoor != null) {
					room.keep(backDoor[0], backDoor[1]);
				}
				if (hearth != null) {
					room.block(hearth[0], hearth[1]);
					room.keep(hearth[0] + (hearth[0] == r[0] + 1 ? 1 : -1), hearth[1]);
				}
				Rooms.furnish(d, room, Rooms.Type.KITCHEN, 0, JobType.NONE);
				Rooms.furnish(d, room, Rooms.Type.LIVING, 0, JobType.NONE);
				dev.voidmc.hearth.build.kit.homestead.Household.dress(d, room, room, hearth);
			} else {
				TownHouses.bedroom(d, room, top && roof != null ? 1 : 2);
				if (core == null) {
					Rooms.furnish(d, room, Rooms.Type.KITCHEN, 0, JobType.NONE);
				}
			}
		}
		TownHouses.joists(d, body, core);

		// the street, the front garden, the yard
		if (garden) {
			frontGarden(d, body, door);
		}
		TownHouses.street(d, body, door, awkward, 0);
		yard(d, body, outshot);
		if (h.snow()) {
			Garden.snowBanks(d, 0, bz0, w - 1, bz1, 0, door);
		}
		int top = roof != null ? roof.ridgeY : deck + 4;
		Groundwork.clear(d, 0, 0, w - 1, dp - 1, top + 2);
		dev.voidmc.hearth.build.kit.refine.Voids.finish(d);
	}

	/**
	 * A lean-to scullery across part of the back: walls two high under a roof falling from under the back eave, a door
	 * through the back wall clear of the stair, a window, barrels and a chest inside. Returns the cell inside the house
	 * before its door, or null.
	 */
	private static int[] outshot(Draft d, Body b, StairCore core, int[] hearth) {
		int dp = d.depth();
		int[] r = b.rect(0);
		int z0 = r[3];
		int z1 = Math.min(dp - 2, z0 + 3);
		if (z1 - z0 < 2) {
			return null;
		}
		// on the side away from the stair well
		boolean wellLeft = core.x0() <= (r[0] + r[2]) / 2;
		int ow = Math.max(4, (r[2] - r[0] + 1) * 2 / 3);
		int ox0 = wellLeft ? r[2] - ow + 1 : r[0];
		int ox1 = wellLeft ? r[2] : r[0] + ow - 1;
		Mat wall = d.mat(Role.WALL);
		Mat roofM = d.mat(Role.ROOF);
		Groundwork.footings(d, ox0, z0 + 1, ox1, z1, -1);
		dev.voidmc.hearth.build.kit.Floors.boards(d, ox0 + 1, z0 + 1, ox1 - 1, z1 - 1, -1);
		int top = 2;
		try (var s = d.stage(StageKind.WALLS)) {
			for (int z = z0 + 1; z <= z1; z++) {
				for (int x = ox0; x <= ox1; x++) {
					boolean edge = x == ox0 || x == ox1 || z == z1;
					if (!edge) {
						continue;
					}
					for (int y = 0; y <= top; y++) {
						d.set(x, y, z, wall.full());
					}
				}
			}
		}
		// a lean-to roof of stairs falling to the back, slabs on its high edge against the house
		try (var s = d.stage(StageKind.ROOF)) {
			for (int x = ox0 - (ox0 > 0 ? 1 : 0); x <= ox1 + (ox1 < d.frontage() - 1 ? 1 : 0); x++) {
				for (int z = z0 + 1; z <= z1 + 1 && z < dp; z++) {
					int y = top + 1 + (z1 + 1 - z) / 2;
					boolean half = ((z1 + 1 - z) & 1) == 1;
					if (d.isAbsent(x, y, z)) {
						d.set(x, y, z, half ? roofM.slab(net.minecraft.world.level.block.state.properties.SlabType.TOP) : roofM.stairsOrSlab(Dirs.FRONT, Half.BOTTOM));
					}
				}
			}
		}
		d.carve(ox0 + 1, 0, z0 + 1, ox1 - 1, top, z1 - 1);
		int mid = (ox0 + ox1) / 2;
		d.inStage(StageKind.OPENINGS, () -> d.set(mid, 1, z1, d.mat(Role.GLASS).pane()));
		// the door through the back wall, clear of the well and the hearth
		for (int k = 0; k <= ox1 - ox0; k++) {
			int x = wellLeft ? ox1 - 1 - k : ox0 + 1 + k;
			if (x <= ox0 || x >= ox1 || x >= core.x0() - 1 && x <= core.x1() + 1) {
				continue;
			}
			if (hearth != null && Math.abs(x - hearth[0]) <= 1 && Math.abs(z0 - 1 - hearth[1]) <= 1) {
				continue;
			}
			if (!Openings.isWallBlock(d, x, 0, z0) || !Openings.isWallBlock(d, x, 1, z0)) {
				continue;
			}
			int dx = x;
			Fronts.plainDoor(d, dx, 0, z0, Dirs.BACK);
			Rooms.Room room = new Rooms.Room(ox0 + 1, z0 + 1, ox1 - 1, z1 - 1, 0, 2);
			room.keep(dx, z0 + 1);
			Rooms.furnish(d, room, Rooms.Type.STORAGE, 0, JobType.NONE);
			return new int[]{dx, z0 - 1};
		}
		return null;
	}

	/** A front garden strip behind a low wall or railing with a gate before the door, planted. */
	private static void frontGarden(Draft d, Body b, int door) {
		int z0 = b.rect(0)[1];
		if (d.site().anyWater(0, 0, d.frontage() - 1, z0 - 1) || d.site().maxGround(0, 0, d.frontage() - 1, z0 - 1)
			- d.site().minGround(0, 0, d.frontage() - 1, z0 - 1) > 1) {
			return;
		}
		Groundwork.level(d, 0, 0, d.frontage() - 1, z0 - 1, 0);
		List<int[]> beds = new ArrayList<>();
		for (int z = 1; z < z0; z++) {
			for (int x = 0; x < d.frontage(); x++) {
				if (x != door && d.isAbsent(x, 0, z)) {
					beds.add(new int[]{x, z});
				}
			}
		}
		Garden.path(d, List.of(new int[]{door, 0}, new int[]{door, 1}), 0);
		for (int[] c : beds) {
			Garden.garden(d, c[0], c[1], c[0], c[1], 0);
		}
		List<int[]> line = new ArrayList<>();
		for (int x = 0; x < d.frontage(); x++) {
			line.add(new int[]{x, 0});
		}
		Garden.fence(d, line, door, Dirs.FRONT);
	}

	/** The yard behind: levelled, a washing line across it, a water butt, the culture's garden along the back. */
	private static void yard(Draft d, Body b, boolean outshot) {
		int[] r = b.rect(0);
		int z0 = r[3] + 1;
		int z1 = d.depth() - 1;
		if (z1 < z0 || d.site().anyWater(0, z0, d.frontage() - 1, z1)) {
			return;
		}
		Groundwork.level(d, 0, z0, d.frontage() - 1, z1, 0);
		if (z1 - z0 >= 1 && !outshot) {
			for (int x = 1; x < d.frontage() - 1; x++) {
				if (d.isAbsent(x, 0, z1)) {
					Garden.garden(d, x, z1, x, z1, 0);
				}
			}
		}
		// a washing line from the back wall to a post at the yard's end
		int lx = outshot ? (r[0] + r[2]) / 2 : r[0] + 1;
		for (int x = r[0] + 1; x < r[2]; x++) {
			if (d.isAbsent(x, 0, z1) && d.isAbsent(x, 0, z0) && d.isAbsent(x, 1, z0) && z1 - z0 >= 2) {
				lx = x;
				break;
			}
		}
		if (z1 - z0 >= 2 && d.isAbsent(lx, 0, z1)) {
			Mat fence = d.mat(Role.FENCE);
			int px = lx;
			try (var s = d.stage(StageKind.EXTERIOR)) {
				d.set(px, 0, z1, fence.fence());
				d.set(px, 1, z1, fence.fence());
				d.set(px, 2, z1, fence.fence());
				d.set(px, 3, z1, fence.fence());
			}
			Life.line(d, px, r[3], px, z1, 3);
		}
		if (d.isAbsent(r[2], 0, z0) && !b.partyRight) {
			Garden.waterButt(d, r[2], 0, z0);
		}
	}
}
