package dev.voidmc.hearth.build.archetype.civic;

import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Mat;
import dev.voidmc.hearth.build.draft.Paint;
import dev.voidmc.hearth.build.kit.Roofs;
import dev.voidmc.hearth.build.kit.civic.Grounds;
import dev.voidmc.hearth.build.kit.civic.Sacred;
import dev.voidmc.hearth.build.kit.civic.Towers;
import dev.voidmc.hearth.build.style.Habits;
import dev.voidmc.hearth.build.style.Role;
import dev.voidmc.hearth.build.terrain.Groundwork;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.Half;

/**
 * The round chapel of the steppe: a drum of ochre plaster between timber posts under a broad cone, in towns a second,
 * smaller drum of windows rising through it under its own cone; a gabled porch on posts to the street; inside, pews in
 * rows facing the altar at the back of the round; graves in the ground round it.
 */
final class ChapelRotunda {
	private ChapelRotunda() {
	}

	static void draw(Draft d) {
		int w = d.frontage();
		int dp = d.depth();
		int tier = d.tier();
		int maxH = d.maxHeight();
		double r = w >= 12 ? 5 : w >= 10 ? 4 : 3;
		double cx = (w - 1) / 2.0;
		int zPorch = 1;
		double cz = zPorch + 2 + r;
		int hc = tier <= 2 ? 6 : 7;
		int base = Grounds.raisedBase(d, (int) (cx - r), (int) (cx + r), (int) (cz - r));
		int top = base + hc - 1;
		Mat wall = d.mat(Role.WALL);
		Mat frame = d.mat(Role.FRAME);
		Mat plinth = d.mat(Role.PLINTH);
		Mat found = d.mat(Role.FOUNDATION);
		List<int[]> ring = Draft.ellipseCells(cx, cz, r, r, false);
		List<int[]> disc = Draft.ellipseCells(cx, cz, r, r, true);
		Set<Long> ringSet = new HashSet<>();
		for (int[] c : ring) {
			ringSet.add(key(c[0], c[1]));
		}
		int x0 = (int) Math.floor(cx - r);
		int x1 = (int) Math.ceil(cx + r);
		int z0 = (int) Math.floor(cz - r);
		int z1 = (int) Math.ceil(cz + r);
		d.body(x0, z0, x1, z1);
		d.eave(top);
		// footings and floor
		for (int[] c : disc) {
			boolean edge = ringSet.contains(key(c[0], c[1]));
			int g = d.site().ground(c[0], c[1]);
			try (var s = d.stage(StageKind.FOUNDATION)) {
				for (int y = Math.min(base - 1, g - 2); y <= base - 1; y++) {
					d.set(c[0], y, c[1], edge || y < base - 1 ? found.full() : d.mat(Role.PAVING).full());
				}
			}
			if (!edge && g > base) {
				try (var s = d.stage(StageKind.EARTHWORK)) {
					for (int y = base; y < g; y++) {
						d.set(c[0], y, c[1], Blocks.AIR.defaultBlockState());
					}
				}
			}
		}
		Grounds.forecourt(d, 0, w - 1, z0 - 1, true);
		// the drum
		for (int[] c : ring) {
			boolean post = isTurn(ringSet, c);
			try (var s = d.stage(StageKind.WALLS)) {
				for (int y = base; y <= top; y++) {
					Paint p = y == base ? plinth.full() : post ? frame.log(Direction.Axis.Y) : y == top ? d.mat(Role.WALL_ACCENT).full() : wall.full();
					d.set(c[0], y, c[1], p);
				}
			}
		}
		for (int[] c : disc) {
			if (!ringSet.contains(key(c[0], c[1]))) {
				d.carve(c[0], base, c[1], c[0], top + 8, c[1]);
			}
		}
		// windows in the drum between the posts, the door at the front
		int doorX = (int) Math.round(cx);
		int doorZ = z1;
		for (int[] c : ring) {
			if (c[0] == doorX && c[1] < doorZ) {
				doorZ = c[1];
			}
		}
		for (int[] c : ring) {
			if (isTurn(ringSet, c) || c[1] == doorZ && Math.abs(c[0] - doorX) <= 1) {
				continue;
			}
			double a = Math.atan2(c[1] - cz, c[0] - cx);
			int k = (int) Math.round((a + Math.PI) / (Math.PI / 4));
			if (k % 2 == 0) {
				int fx = c[0];
				int fz = c[1];
				d.inStage(StageKind.OPENINGS, () -> {
					d.set(fx, base + 2, fz, d.mat(Role.GLASS).pane());
					d.set(fx, base + 3, fz, d.mat(Role.CLOTH).pane());
				});
			}
		}
		int dz = doorZ;
		d.inStage(StageKind.OPENINGS, () -> d.door(doorX, base, dz, d.mat(Role.DOOR).mainOr(dev.voidmc.hearth.build.style.Form.DOOR, Blocks.SPRUCE_DOOR),
			Dirs.FRONT, DoorHingeSide.LEFT));
		d.entrance(doorX, base, dz - 1);
		d.poi(dev.voidmc.hearth.api.PoiType.DOOR, doorX, base, dz - 1, Dirs.BACK);
		// the cone: broad over the drum; in towns a clerestory drum and a second cone
		Roofs.Field cone = Roofs.cone(cx, cz, r + 1, 1.0);
		Roofs.Roof roof = Roofs.raster(d, cone, top, Math.max(0, x0 - 1), Math.max(0, z0 - 1), Math.min(w - 1, x1 + 1), Math.min(dp - 1, z1 + 1),
			d.mat(Role.ROOF), d.mat(Role.ROOF_TRIM), Habits.Ridge.FULL);
		int peak = roof.ridgeY;
		if (tier >= 3 && r >= 4) {
			double r2 = r - 2;
			int dtop = top + 3;
			for (int[] c : Draft.ellipseCells(cx, cz, r2, r2, false)) {
				try (var s = d.stage(StageKind.WALLS)) {
					for (int y = top + 1; y <= dtop; y++) {
						d.set(c[0], y, c[1], y == dtop - 1 && !isTurn(ringSet, c) ? d.mat(Role.GLASS).pane() : wall.full());
					}
				}
			}
			for (int[] c : Draft.ellipseCells(cx, cz, r2, r2, true)) {
				if (d.isEmpty(c[0], top + 1, c[1])) {
					d.clearBox(c[0], top + 1, c[1], c[0], dtop, c[1]);
				}
			}
			Roofs.Field cone2 = Roofs.cone(cx, cz, r2 + 1, 1.2);
			Roofs.Roof r2r = Roofs.raster(d, cone2, dtop, (int) Math.floor(cx - r2 - 1), (int) Math.floor(cz - r2 - 1), (int) Math.ceil(cx + r2 + 1),
				(int) Math.ceil(cz + r2 + 1), d.mat(Role.ROOF), d.mat(Role.ROOF_TRIM), Habits.Ridge.FULL);
			peak = r2r.ridgeY;
		}
		int topY = Towers.finial(d, (int) Math.round(cx), peak + 1, (int) Math.round(cz), maxH, false);

		// a gabled porch on posts before the door
		int px = doorX;
		try (var s = d.stage(StageKind.FRAME)) {
			for (int z = zPorch; z < dz; z++) {
				for (int x : new int[]{px - 1, px + 1}) {
					for (int y = Math.min(base, d.site().ground(x, z)) - 1; y < base; y++) {
						d.set(x, y, z, found.full());
					}
					for (int y = base; y <= base + 2; y++) {
						d.set(x, y, z, frame.log(Direction.Axis.Y));
					}
				}
				d.set(px, base - 1, z, d.mat(Role.FLOOR).full());
			}
		}
		d.carve(px - 1, base, zPorch, px + 1, base + 2, dz - 1);
		Roofs.Field porch = Roofs.gable(px - 2, zPorch - 1, px + 2, dz, Direction.Axis.Z, Roofs.Profile.straight(1.0));
		Roofs.Roof pr = Roofs.raster(d, porch, base + 3, Math.max(0, px - 2), Math.max(0, zPorch - 1), Math.min(w - 1, px + 2), dz - 1,
			d.mat(Role.ROOF), d.mat(Role.ROOF_TRIM), Habits.Ridge.SLAB);
		// the bell in a little cote on the porch's gable
		dev.voidmc.hearth.build.kit.Side pf = dev.voidmc.hearth.build.kit.Side.of(px - 1, zPorch, px + 1, dz - 1, Dirs.FRONT);
		int apex = pr.surfaceY(px, zPorch);
		if (apex != Integer.MIN_VALUE) {
			try (var s = d.stage(StageKind.WALLS)) {
				for (int x = px - 1; x <= px + 1; x++) {
					for (int y = base + 3; y < apex; y++) {
						d.set(x, y, zPorch, wall.full());
					}
				}
			}
			topY = Math.max(topY, Towers.bellCote(d, pf, 1, apex, maxH));
		}
		if (base >= 1) {
			Grounds.flight(d, px - 1, px + 1, zPorch - 1, base, base);
		}
		Grounds.approach(d, px, base, zPorch);

		// --- inside: rows of pews facing the altar at the back, the font, a lantern from the cone
		int iz0 = dz + 1;
		int iz1 = z1 - 1;
		int az = iz1;
		for (int[] c : disc) {
			if (c[1] == az && Math.abs(c[0] - doorX) <= 1 && !ringSet.contains(key(c[0], c[1]))) {
				az = c[1];
			}
		}
		Sacred.altar(d, doorX, base, az, Dirs.FRONT, r >= 4, doorX + 2);
		Mat wood = d.mat(Role.FURNITURE);
		for (int z = iz0 + 1; z <= az - 2; z += 2) {
			for (int[] c : disc) {
				if (c[1] != z || ringSet.contains(key(c[0], c[1])) || Math.abs(c[0] - doorX) == 0 || !d.isEmpty(c[0], base, c[1])) {
					continue;
				}
				// keep a walk round the inside of the drum
				if (ringSet.contains(key(c[0] - 1, c[1])) || ringSet.contains(key(c[0] + 1, c[1]))) {
					continue;
				}
				int fx = c[0];
				int fz = z;
				d.inStage(StageKind.INTERIOR, () -> d.set(fx, base, fz, wood.stairsOrSlab(Dirs.FRONT, Half.BOTTOM)));
				d.poi(dev.voidmc.hearth.api.PoiType.SEAT, c[0], base, z, Dirs.BACK);
			}
		}
		dev.voidmc.hearth.build.kit.civic.Halls.lanternUnder(d, (int) Math.round(cx), base, (int) Math.round(cz), 3);
		// graves round about
		Sacred.churchyard(d, 0, z1 + 1, w - 1, dp - 1, 0, true, Dirs.FRONT);
		if (x0 >= 3) {
			Sacred.churchyard(d, 0, z0, x0 - 2, z1, 0, true, Dirs.FRONT);
			Sacred.churchyard(d, x1 + 2, z0, w - 1, z1, 0, true, Dirs.FRONT);
		}
		dev.voidmc.hearth.build.kit.civic.Halls.lamp(d, Math.max(0, px - 3), 0, 0, 2);
		Groundwork.clear(d, 0, 0, w - 1, dp - 1, topY + 2);
		dev.voidmc.hearth.build.kit.refine.Voids.finish(d);
	}

	private static long key(int x, int z) {
		return ((long) x << 32) ^ (z & 0xFFFFFFFFL);
	}

	/** A ring cell where the ring changes direction: posts stand there. */
	private static boolean isTurn(Set<Long> ring, int[] c) {
		return turn(ring, c) && (straightNeighbour(ring, c));
	}

	/** Whether a turning cell sits at the end of a straight run (a compass point or a diagonal's shoulder). */
	private static boolean straightNeighbour(Set<Long> ring, int[] c) {
		int n = 0;
		for (int[] o : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
			int[] m = {c[0] + o[0], c[1] + o[1]};
			if (ring.contains(key(m[0], m[1])) && !turn(ring, m)) {
				n++;
			}
		}
		return n >= 1;
	}

	private static boolean turn(Set<Long> ring, int[] c) {
		boolean e = ring.contains(key(c[0] + 1, c[1]));
		boolean wv = ring.contains(key(c[0] - 1, c[1]));
		boolean n = ring.contains(key(c[0], c[1] + 1));
		boolean s = ring.contains(key(c[0], c[1] - 1));
		return !(e && wv && !n && !s) && !(n && s && !e && !wv);
	}
}
