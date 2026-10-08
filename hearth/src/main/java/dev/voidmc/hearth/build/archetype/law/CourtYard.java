package dev.voidmc.hearth.build.archetype.law;

import dev.voidmc.hearth.api.PoiType;
import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Paint;
import dev.voidmc.hearth.build.kit.Body;
import dev.voidmc.hearth.build.kit.Furniture;
import dev.voidmc.hearth.build.kit.Openings;
import dev.voidmc.hearth.build.kit.Side;
import dev.voidmc.hearth.build.kit.civic.Pueblo;
import dev.voidmc.hearth.build.kit.civic.Adobe;
import dev.voidmc.hearth.build.kit.civic.Arches;
import dev.voidmc.hearth.build.kit.civic.Halls;
import dev.voidmc.hearth.build.kit.civic.Signs;
import dev.voidmc.hearth.build.kit.civic.Tradition;
import dev.voidmc.hearth.build.kit.landmark.Stone;
import dev.voidmc.hearth.build.kit.landscape.Paving;
import dev.voidmc.hearth.build.kit.lawhouse.Courtroom;
import dev.voidmc.hearth.build.kit.lawhouse.Fenestration;
import dev.voidmc.hearth.build.kit.lawhouse.Masonry;
import dev.voidmc.hearth.build.kit.light.Lighting;
import dev.voidmc.hearth.build.style.Role;
import dev.voidmc.hearth.build.terrain.Groundwork;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * The court in a courtyard of {@link Courthouse} (desert, mesa): a walled compound entered from the street through a
 * tall iwan whose pointed arch rises over the crested wall; a paved court with a basin in the middle and a palm, shaded
 * along both sides by arcades of pointed arches under flat roofs, where petitioners wait on benches against the wall; and
 * at the back of the court the qadi's hall, its own iwan on the court, latticed screens either side, a flat roof with a
 * crested parapet and a dome lifted on a drum over the middle. Inside, the qadi's dais at the back with the high seat,
 * divans along the walls, the dock and the bar.
 */
final class CourtYard {
	private CourtYard() {
	}

	/**
	 * A pueblo gateway in wall s at column cx: three wide and three high from the ground, a lintel of the beam's logs over
	 * it with their ends run through the wall either side, the wall above it carried up as it was (up to {@code top});
	 * registered as a way in (an entrance and its DOOR) before it.
	 */
	static void gateway(Draft d, Side s, int cx, int top) {
		int i = s.index(cx);
		var beam = d.mat(Role.BEAM);
		try (var st = d.stage(StageKind.WALLS)) {
			for (int k = -1; k <= 1; k++) {
				int ii = i + k;
				if (ii < 1 || ii > s.last() - 1) {
					continue;
				}
				for (int y = 0; y <= 2; y++) {
					d.set(s.x(ii), y, s.z(ii), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
				}
			}
			for (int k = -2; k <= 2; k++) {
				int ii = i + k;
				if (ii >= 0 && ii <= s.last() && 3 <= top) {
					d.set(s.x(ii), 3, s.z(ii), beam.log(s.along().getAxis()));
				}
			}
		}
		// the way in: an open gateway is the building's door
		int ex = s.outX(i, 1);
		int ez = s.outZ(i, 1);
		d.entrance(ex, 0, ez);
		d.poi(dev.voidmc.hearth.api.PoiType.DOOR, ex, 0, ez, s.out().getOpposite());
	}

	static void draw(Draft d) {
		int w = d.frontage();
		int dp = d.depth();
		int maxH = d.maxHeight();
		int cx = (w - 1) / 2;
		Stone wall = Masonry.walling(d);
		Stone dress = Stone.dressing(d);
		Stone plinth = Masonry.plinth(d);
		// the compound from row 2 (the street's apron and the iwan's projection before it), the hall at its back
		int fz = 2;
		int hd = Math.clamp(dp / 2, 7, 9);
		int zb = dp - hd;
		int aw = w >= 15 ? 4 : 3;
		int wallTop = 4;
		Groundwork.level(d, 0, 0, w - 1, dp - 1, 0, true);
		Groundwork.footings(d, 0, fz, w - 1, dp - 1, -1);
		// --- the court: paving in the culture's pattern round a basin
		Paving.lay(d, 0, 0, w - 1, dp - 1, 0, Paving.paint(d, Paving.pattern(d), cx, (fz + zb) / 2.0, 2, false));
		// --- the hall at the back
		Body hall = new Body(0, zb, w - 1, dp - 1, 0, new int[]{6}, false, false);
		d.body(0, zb, w - 1, dp - 1);
		Masonry.storey(d, hall, 0, wall, dress, plinth, false);
		int plate = hall.plateY();
		d.eave(plate);
		// --- the compound's front wall and its iwan, the main way in
		Side front = Side.of(0, fz, w - 1, dp - 1, Dirs.FRONT);
		try (var st = d.stage(StageKind.WALLS)) {
			for (int i = 0; i < front.length(); i++) {
				for (int y = 0; y <= wallTop; y++) {
					d.set(front.x(i), y, front.z(i), y == 0 ? plinth.full() : i == 0 || i == front.last() ? dress.full() : wall.full());
				}
			}
		}
		// the mesa's court is a pueblo plaza's: a plain gateway under a lintel of vigas, no crested wall, no iwan
		boolean pueblo = Pueblo.is(d);
		if (pueblo) {
			gateway(d, front, cx, wallTop);
		} else {
			merlons(d, front, wallTop + 1, dress);
			Halls.iwan(d, front, cx, 0, wallTop + 3, 2);
		}
		// --- the arcades down both sides of the court, their flat roofs on the compound's side walls
		for (boolean left : new boolean[]{true, false}) {
			int ax0 = left ? 0 : w - aw;
			int ax1 = left ? aw - 1 : w - 1;
			// the outer wall
			Side outer = Side.of(ax0, fz, ax1, zb, left ? Dirs.LEFT : Dirs.RIGHT);
			try (var st = d.stage(StageKind.WALLS)) {
				for (int i = 0; i < outer.length(); i++) {
					for (int y = 0; y < wallTop; y++) {
						d.set(outer.x(i), y, outer.z(i), y == 0 ? plinth.full() : wall.full());
					}
				}
			}
			Side line = Side.of(ax0, fz, ax1, zb, left ? Dirs.RIGHT : Dirs.LEFT);
			int[] piers = Arches.piers(0, line.last(), 3);
			// (the mesa's portales: vigas on posts, not pointed arches)
			Arches.Kind ak = pueblo ? Arches.Kind.BRACED : Arches.Kind.POINTED;
			Arches.arcade(d, line, piers, 0, wallTop - 1, ak, pueblo ? Arches.pierPaint(d, ak) : dress.full(), pueblo ? null : wall.full());
			Adobe.roof(d, ax0, fz, ax1, zb, wallTop - 1, true, false);
			// benches against the outer wall, lanterns under the roof
			int bx = left ? ax0 + 1 : ax1 - 1;
			for (int z = fz + 2; z <= zb - 2; z += 2) {
				if (d.isEmpty(bx, 0, z)) {
					Furniture.chair(d, bx, 0, z, left ? Dirs.LEFT : Dirs.RIGHT);
				}
			}
			int lx = (ax0 + ax1) / 2;
			for (int z = fz + 1; z <= zb - 1; z += 3) {
				if (d.isEmpty(lx, 1, z) && d.isEmpty(lx, 2, z)) {
					Halls.lanternUnder(d, lx, 0, z, 0);
				}
			}
		}
		// --- the court: the basin, a palm or two, lamps
		int bz = (fz + zb) / 2;
		basin(d, cx, bz, dress);
		int px0 = aw + 1;
		int px1 = w - aw - 2;
		if (px1 - px0 >= 4 && zb - fz >= 6 && !pueblo) {
			Adobe.palm(d, px0, 0, zb - 2, Math.min(5, maxH - 3));
			if (d.chance(0.6)) {
				Adobe.palm(d, px1, 0, fz + 2, Math.min(5, maxH - 3));
			}
		}
		// --- the hall's own iwan on the court, screens either side, the roof, the dome
		Side hallFront = hall.side(0, Dirs.FRONT);
		if (pueblo) {
			gateway(d, hallFront, cx, plate);
		} else {
			Halls.iwan(d, hallFront, cx, 0, plate + 3, 2);
		}
		for (int off : new int[]{-4, 4, -6, 6}) {
			int x = cx + off;
			int i = hallFront.index(x);
			if (i >= 2 && i <= hallFront.last() - 2 && x > aw && x < w - 1 - aw) {
				Fenestration.framed(d, hallFront, i, 1, 1, 2, dress, false, false, false);
				if (!pueblo) {
					Adobe.screen(d, hallFront, i, 1);
				}
			}
		}
		for (Side s : new Side[]{hall.side(0, Dirs.LEFT), hall.side(0, Dirs.RIGHT), hall.side(0, Dirs.BACK)}) {
			for (int i : Masonry.bays(s.length(), 4)) {
				Fenestration.framed(d, s, i, 1, 2, 2, dress, false, false, false);
			}
		}
		int deck = Adobe.roof(d, 0, zb, w - 1, dp - 1, plate, true, true);
		int skyline = deck + 3;
		int hcz = (zb + dp - 1) / 2;
		skyline = Math.max(skyline, Adobe.dome(d, cx, hcz, deck, 2, maxH - 1));
		skyline = Math.max(skyline, plate + 4);
		// --- the court room in the hall
		Set<Long> keep = new HashSet<>();
		keep.add(Courtroom.key(cx, zb + 1));
		Courtroom.furnish(d, 1, zb + 1, w - 2, dp - 2, 0, plate, keep, new HashSet<>(), Courtroom.Seating.SIDES);
		// --- outside: the notice board by the iwan, braziers at the corners of the apron
		for (int off : new int[]{-4, 4, -5, 5}) {
			int nx = cx + off;
			if (nx > 0 && nx < w - 1 && d.isEmpty(nx, 0, 1) && d.isEmpty(nx, 1, 1)) {
				Signs.noticeBoard(d, nx, 0, 1, Dirs.FRONT);
				break;
			}
		}
		for (int x : new int[]{0, w - 1}) {
			if (d.isEmpty(x, 0, 1)) {
				Lighting.post(d, x, 0, 1);
			}
		}
		// lamps in the court's open corners, so nobody stands in the dark
		for (int[] c : new int[][]{{aw, fz + 1}, {w - 1 - aw, fz + 1}, {aw, zb - 1}, {w - 1 - aw, zb - 1}}) {
			if (d.isEmpty(c[0], 0, c[1]) && d.isEmpty(c[0], 1, c[1]) && d.isEmpty(c[0], 2, c[1])) {
				Lighting.post(d, c[0], 0, c[1]);
			}
		}
		// the mesa's pueblo: a stepped room over the flat roof, a ladder up to its terrace
		int crown = pueblo ? Pueblo.crown(d) : Integer.MIN_VALUE;
		dev.voidmc.hearth.build.kit.civic.Halls.finish(d);
		Groundwork.clear(d, 0, 0, w - 1, dp - 1, Math.max(Math.min(maxH - 1, skyline + 2), crown + 2));
	}

	/** Merlons along the top of a wall: a block every other cell over the coping course at y. */
	static void merlons(Draft d, Side s, int y, Stone dress) {
		try (var st = d.stage(StageKind.WALLS)) {
			for (int i = 0; i < s.length(); i++) {
				if (!d.allowed(s.x(i), y, s.z(i))) {
					continue;
				}
				if ((i & 1) == 0) {
					d.set(s.x(i), y, s.z(i), dress.full());
					if (d.allowed(s.x(i), y + 1, s.z(i))) {
						d.set(s.x(i), y + 1, s.z(i), dress.slabOrNothing(SlabType.BOTTOM));
					}
				} else {
					d.set(s.x(i), y, s.z(i), dress.slabOrNothing(SlabType.BOTTOM));
				}
			}
		}
	}

	/** A basin: a rim of the dressing round a cell of water, a jet of water rising from a stone in its middle. */
	static void basin(Draft d, int cx, int cz, Stone dress) {
		try (var st = d.stage(StageKind.EXTERIOR)) {
			for (int dz = -1; dz <= 1; dz++) {
				for (int dx = -1; dx <= 1; dx++) {
					if (dx == 0 && dz == 0) {
						d.set(cx, -1, cz, dress.full());
						d.set(cx, 0, cz, Blocks.WATER.defaultBlockState());
					} else {
						d.set(cx + dx, 0, cz + dz, (dx == 0 || dz == 0) ? dress.stairsOrSlab(dx < 0 ? Dirs.RIGHT : dx > 0 ? Dirs.LEFT
							: dz < 0 ? Dirs.BACK : Dirs.FRONT, Half.TOP) : dress.full());
					}
				}
			}
		}
		d.poi(PoiType.MEETING_POINT, cx, 0, cz - 2, Dirs.BACK);
	}

	/** Paint helper. */
	static Paint air() {
		return Paint.of(Blocks.AIR.defaultBlockState());
	}

	/** Whether to draw this plan. */
	static boolean suits(Tradition t) {
		return t == Tradition.ADOBE;
	}
}
