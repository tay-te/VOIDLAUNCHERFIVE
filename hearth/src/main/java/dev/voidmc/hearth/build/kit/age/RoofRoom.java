package dev.voidmc.hearth.build.kit.age;

import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Mat;
import dev.voidmc.hearth.build.kit.Rooms;
import dev.voidmc.hearth.build.kit.Side;
import dev.voidmc.hearth.build.kit.Stairs;
import dev.voidmc.hearth.build.style.Form;
import dev.voidmc.hearth.build.style.Role;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * A room built on a flat roof: how households in the flat-roofed cultures grow, up onto the roof they already sleep and
 * dry things on. A box at a back corner of the roof terrace in the house's own walling, its door onto the terrace, a
 * window, a flat roof with a coping, a bed; a ladder up through the deck where the house had no way onto its roof.
 */
public final class RoofRoom {
	private RoofRoom() {
	}

	public static boolean add(HouseScan h, long seed) {
		Draft d = h.d;
		if (!h.flat) {
			return false;
		}
		// the deck: the lowest roof course over the body's inside
		int deck = Integer.MAX_VALUE;
		for (int x = h.x0 + 1; x < h.x1; x++) {
			for (int z = h.z0 + 1; z < h.z1; z++) {
				for (int y = h.eave - 1; y <= h.top; y++) {
					Draft.Cell c = d.cell(x, y, z);
					if (c != null && c.stage() == StageKind.ROOF && HouseScan.full(c.state())) {
						deck = Math.min(deck, y);
						break;
					}
				}
			}
		}
		if (deck == Integer.MAX_VALUE) {
			return false;
		}
		int stand = deck + 1;
		if (stand + 4 >= d.maxHeight()) {
			return false;
		}
		int iw = h.x1 - h.x0 - 1;
		int idp = h.z1 - h.z0 - 1;
		// its walls rise from the house's own at a back corner (the parapet there becomes the room's wall)
		int sx = iw >= 8 ? 5 : 4;
		int sz = idp >= 7 ? 5 : idp >= 4 ? 4 : 3;
		if (iw - (sx - 1) < 2 || idp - (sz - 1) < 1) {
			return false;
		}
		// a back corner first, a front one where the back is taken (by the chimney, the hatch)
		for (int attempt = 0; attempt < 4; attempt++) {
			boolean right = ((seed & 1) == 0) == (attempt % 2 == 0);
			boolean atBack = attempt < 2;
			int bx0 = right ? h.x1 - sx + 1 : h.x0;
			int bx1 = bx0 + sx - 1;
			int bz0 = atBack ? h.z1 - sz + 1 : h.z0;
			int bz1 = bz0 + sz - 1;
			if (clear(h, bx0, bz0, bx1, bz1, deck, stand, atBack)) {
				build(h, bx0, bz0, bx1, bz1, deck, stand, right, atBack);
				return true;
			}
		}
		return false;
	}

	private static boolean clear(HouseScan h, int x0, int z0, int x1, int z1, int deck, int stand, boolean atBack) {
		Draft d = h.d;
		for (int x = x0; x <= x1; x++) {
			for (int z = z0; z <= z1; z++) {
				boolean onWall = Storey.ring(h, x, z);
				if (!onWall && (!d.isSolid(x, deck, z) || h.onRoute(x, stand, z))) {
					return false;
				}
				for (int y = stand; y <= stand + 4; y++) {
					Draft.Cell c = d.cell(x, y, z);
					if (!movable(c) && !(onWall && c.stage() == StageKind.ROOF && y <= stand + 2)) {
						return false;
					}
				}
			}
		}
		// standing room on the terrace in front of it
		int fz = atBack ? z0 - 1 : z1 + 1;
		// (in front of the door: the cell toward the middle of the roof)
		int doorX = x0 == h.x0 ? x1 - 1 : x0 + 1;
		for (int x = doorX; x <= doorX; x++) {
			if (Storey.ring(h, x, fz)) {
				return false;
			}
			if (!movable(d.cell(x, stand, fz)) || !movable(d.cell(x, stand + 1, fz)) || !d.isSolid(x, deck, fz)) {
				return false;
			}
		}
		return true;
	}

	private static void build(HouseScan h, int x0, int z0, int x1, int z1, int deck, int stand, boolean right, boolean atBack) {
		Draft d = h.d;
		Mat wall = d.mat(Role.WALL);
		Mat roof = d.mat(Role.ROOF);
		Mat coping = d.mat(Role.WALL_ACCENT);
		int top = stand + 3;
		// the pots and benches that stood there are moved off
		for (int x = x0; x <= x1; x++) {
			for (int z = z0 - 1; z <= z1 + 1; z++) {
				boolean frontRow = z == (atBack ? z0 - 1 : z1 + 1);
				if (z < z0 && !frontRow || z > z1 && !frontRow) {
					continue;
				}
				for (int y = stand; y <= top + 1; y++) {
					if (!d.isEmpty(x, y, z) && !(frontRow && Storey.ring(h, x, z))) {
						d.remove(x, y, z);
					}
				}
			}
		}
		final int fx0 = x0;
		final int fx1 = x1;
		final int fz0 = z0;
		final int fz1 = z1;
		d.retainPois(p -> !(p.x() >= fx0 && p.x() <= fx1 && p.z() >= fz0 - 1 && p.z() <= fz1 && p.y() >= stand && p.y() <= top));
		try (var sc = d.stage(StageKind.WALLS)) {
			for (int[] c : Draft.ring(x0, z0, x1, z1)) {
				for (int y = stand; y < top; y++) {
					d.set(c[0], y, c[1], wall.full());
				}
			}
		}
		try (var sc = d.stage(StageKind.ROOF)) {
			for (int x = x0; x <= x1; x++) {
				for (int z = z0; z <= z1; z++) {
					d.set(x, top, z, roof.full());
				}
			}
			for (int[] c : Draft.ring(x0, z0, x1, z1)) {
				d.set(c[0], top + 1, c[1], coping.has(Form.SLAB) ? coping.slab(SlabType.BOTTOM) : wall.slab(SlabType.BOTTOM));
			}
		}
		// the door onto the terrace (in the front face, toward the middle of the roof), a window in the side
		net.minecraft.core.Direction toward = atBack ? Dirs.FRONT : Dirs.BACK;
		Side front = Side.of(x0, z0, x1, z1, toward);
		// the door's cell toward the middle of the roof
		int di;
		di = Math.abs(front.x(1) - (right ? x0 : x1)) < Math.abs(front.x(front.last() - 1) - (right ? x0 : x1)) ? 1 : front.last() - 1;
		int dx = front.x(di);
		int dz = front.z(di);
		d.inStage(StageKind.OPENINGS, () -> d.door(dx, stand, dz, d.mat(Role.DOOR).mainOr(Form.DOOR, Blocks.SPRUCE_DOOR), toward, DoorHingeSide.LEFT));
		Side side = Side.of(x0, z0, x1, z1, right ? Dirs.LEFT : Dirs.RIGHT);
		int wx = side.x(side.middle());
		int wz = side.z(side.middle());
		d.inStage(StageKind.OPENINGS, () -> d.set(wx, stand + 1, wz, d.mat(Role.GLASS).pane()));
		Rooms.Room room = new Rooms.Room(x0 + 1, z0 + 1, x1 - 1, z1 - 1, stand, 2);
		room.keep(dx, dz - Dirs.dz(toward));
		Rooms.furnish(d, room, Rooms.Type.BEDROOM, 1, dev.voidmc.hearth.api.JobType.NONE);
		// the way up, where the house had none
		if (!reachable(h, deck)) {
			int last = h.floors.get(h.floors.size() - 1);
			int[] lad = Storey.ladderSpot(h, last, deck);
			if (lad != null) {
				Stairs.ladder(d, lad[0], lad[1], last, deck, Dirs.HORIZONTAL[lad[2]]);
			}
		}
	}

	/** Roof-garden things that can be moved off for the room: plants, pots, benches (nothing built). */
	static boolean movable(Draft.Cell c) {
		return c == null || c.state().isAir() || !HouseScan.full(c.state()) && (c.stage() == StageKind.EXTERIOR || c.stage() == StageKind.INTERIOR
			|| c.stage() == StageKind.CLEAR);
	}

	/** Whether something already leads onto the roof: a ladder or stair reaching the deck, or a hatch in it. */
	static boolean reachable(HouseScan h, int deck) {
		Draft d = h.d;
		for (int x = h.x0; x <= h.x1; x++) {
			for (int z = h.z0 - 1; z <= h.z1 + 1; z++) {
				for (int y = deck - 1; y <= deck + 1; y++) {
					var s = d.get(x, y, z);
					if (s != null && (s.getBlock() instanceof LadderBlock || s.getBlock() instanceof StairBlock && d.cell(x, y, z).stage() != StageKind.ROOF)) {
						return true;
					}
				}
			}
		}
		return false;
	}
}
