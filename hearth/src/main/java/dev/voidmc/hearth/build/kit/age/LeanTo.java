package dev.voidmc.hearth.build.kit.age;

import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Mat;
import dev.voidmc.hearth.build.kit.Floors;
import dev.voidmc.hearth.build.kit.Garden;
import dev.voidmc.hearth.build.kit.Roofs;
import dev.voidmc.hearth.build.kit.Rooms;
import dev.voidmc.hearth.build.kit.Side;
import dev.voidmc.hearth.build.kit.light.Lighting;
import dev.voidmc.hearth.build.style.Form;
import dev.voidmc.hearth.build.style.Habits;
import dev.voidmc.hearth.build.style.Habits.WallKind;
import dev.voidmc.hearth.build.style.Role;
import dev.voidmc.hearth.build.terrain.Groundwork;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;

/**
 * A lean-to: a one-storey room built against a wall of the house under a roof of its own that falls away from it, the
 * commonest thing a household adds (a scullery, a store, a workshop for the trade, a dairy). Built the way the house is
 * built (boards between posts in timber country, logs in log country, the house's own masonry or render elsewhere, on a
 * plinth where the house has one), its roof in the house's roofing tucked under the house's eave, a door cut through
 * the house wall, a window, furnished and lit. Across the whole back it is an outshot. Where no door can be cut it is
 * left open-fronted as a woodshed.
 */
public final class LeanTo {
	private LeanTo() {
	}

	/**
	 * Tries to add a lean-to on one side of the house: {@code across} spans the whole side (an outshot), else a bay of four
	 * to seven at the back end of the side. Returns whether one was built.
	 */
	public static boolean add(HouseScan h, Direction out, boolean across) {
		// a flat-roofed house, or a house of a flat-roofing manner, adds under a flat deck
		// (unless this house itself wears a pitched roof: then its lean-to slopes in the same tiles)
		boolean flatManner = h.d.habits().roof().forms().getOrDefault(Habits.RoofForm.FLAT, 0.0) > 0.5;
		return add(h, out, across, h.flat || flatManner && !pitched(h));
	}

	/**
	 * {@link #add(HouseScan, Direction, boolean)}, {@code flat}: an annex under a flat deck with a coping instead of a
	 * sloping roof (the flat-roofed cultures' lean-to).
	 */
	public static boolean add(HouseScan h, Direction out, boolean across, boolean flat) {
		Draft d = h.d;
		if (h.party(out) || out == Dirs.FRONT) {
			return Why.no("lean-to " + out, "party or front");
		}
		Side s = h.side(out);
		int last = s.last();
		// how deep and how high it can be: three or four out, its roof under the house's eave
		for (int depth = 4; depth >= 3; depth--) {
			int top = Math.min(h.eave - 1, 4);
			if (top < 2) {
				return Why.no("lean-to " + out, "eave " + h.eave);
			}
			int[] seg = segment(h, s, across, depth, top);
			if (seg == null) {
				continue;
			}
			build(h, s, seg[0], seg[1], depth, top, flat);
			return true;
		}
		if (Why.ON) {
			StringBuilder sb = new StringBuilder();
			for (int i = 0; i <= s.last(); i++) {
				sb.append(h.depthOut(s, i, 4, Math.min(h.eave - 1, 4)));
			}
			Why.no("lean-to " + out, "no yard " + sb);
		}
		return false;
	}

	/** The cells along the side (a, b inclusive: the lean-to's end walls) where it fits, or null. */
	private static int[] segment(HouseScan h, Side s, boolean across, int depth, int top) {
		int last = s.last();
		if (across) {
			for (int i = 0; i <= last; i++) {
				if (h.depthOut(s, i, depth, top) < depth) {
					return null;
				}
			}
			return new int[]{0, last};
		}
		// a bay at one end: the back end of a side wall, either end of the back
		int maxLen = Math.min(7, last + 1);
		for (int len = maxLen; len >= 4; len--) {
			List<int[]> tries = new ArrayList<>();
			tries.add(new int[]{last - len + 1, last});
			tries.add(new int[]{0, len - 1});
			if (s.out() == Dirs.BACK && (h.d.seed() & 1) == 0) {
				java.util.Collections.reverse(tries);
			}
			for (int[] t : tries) {
				boolean ok = true;
				for (int i = t[0]; i <= t[1] && ok; i++) {
					ok = h.depthOut(s, i, depth, top) >= depth;
				}
				if (ok) {
					return t;
				}
			}
		}
		return null;
	}

	private static void build(HouseScan h, Side s, int a, int b, int depth, int top, boolean flat) {
		Draft d = h.d;
		Direction out = s.out();
		Direction in = out.getOpposite();
		Habits habits = d.habits();
		WallKind kind = habits.walls().kind();
		boolean timber = kind == WallKind.TIMBER_FRAME || kind == WallKind.PLANK || kind == WallKind.BAMBOO;
		boolean logs = kind == WallKind.LOG;
		Mat wall = d.mat(timber ? Role.GABLE : Role.WALL);
		Mat frame = d.mat(Role.FRAME);
		Mat plinth = d.mat(Role.PLINTH);
		// the rectangle: along the side from a to b, out from 1 to depth
		int ax = Math.min(s.outX(a, 1), s.outX(b, depth));
		int bx = Math.max(s.outX(a, 1), s.outX(b, depth));
		int az = Math.min(s.outZ(a, 1), s.outZ(b, depth));
		int bz = Math.max(s.outZ(a, 1), s.outZ(b, depth));
		// clear what stood in the yard there
		try (var sc = d.stage(StageKind.CLEAR)) {
			d.clearBox(ax, 0, az, bx, top + 1, bz);
		}
		Groundwork.footings(d, ax, az, bx, bz, -1);
		Floors.boards(d, ax, az, bx, bz, -1);
		// the roof first, so the walls know how high to go: a shed falling away from the house, its high edge under the eave
		int ox = Dirs.dx(out);
		int oz = Dirs.dz(out);
		int rx0 = ax;
		int rx1 = bx;
		int rz0 = az;
		int rz1 = bz;
		// a block of overhang beyond the outer wall where the plot and the yard allow
		boolean overhang = !flat;
		for (int i = a; i <= b && overhang; i++) {
			int xx = s.outX(i, depth + 1);
			int zz = s.outZ(i, depth + 1);
			overhang = xx >= 0 && zz >= 0 && xx < d.frontage() && zz < d.depth() && h.yard(xx, zz, top);
		}
		if (overhang) {
			if (ox > 0) {
				rx1++;
			} else if (ox < 0) {
				rx0--;
			} else if (oz > 0) {
				rz1++;
			} else {
				rz0--;
			}
		}
		Direction up = in;
		int rows = depth + (overhang ? 1 : 0);
		double pitch = 0.5;
		// surface at the house wall row: y0 + rows * pitch; keep it at or under the top
		int y0 = top - (int) Math.ceil((rows - 1) * pitch);
		if (y0 < 2) {
			y0 = 2;
			pitch = Math.max(0, (top - y0) / (double) Math.max(1, rows - 1));
		}
		Roofs.Roof roof;
		if (flat) {
			// a deck level with the top, a coping round its open edges
			int fx0 = rx0;
			int fz0 = rz0;
			int fx1 = rx1;
			int fz1 = rz1;
			Roofs.Field deck = (x, z) -> x >= fx0 && x <= fx1 && z >= fz0 && z <= fz1 ? 1.0 : Double.NaN;
			int deckY = Math.min(top, 3);
			try (var sc = d.stage(StageKind.ROOF)) {
				roof = Roofs.raster(d, deck, deckY, rx0, rz0, rx1, rz1, d.mat(Role.ROOF), d.mat(Role.ROOF_TRIM), Habits.Ridge.SLAB);
				Mat coping = d.mat(Role.WALL_ACCENT);
				for (int i = a; i <= b; i++) {
					for (int k = 1; k <= depth; k++) {
						if (i == a || i == b || k == depth) {
							d.set(s.outX(i, k), deckY + 1, s.outZ(i, k), coping.has(dev.voidmc.hearth.build.style.Form.SLAB)
								? coping.slab(net.minecraft.world.level.block.state.properties.SlabType.BOTTOM)
								: wall.slab(net.minecraft.world.level.block.state.properties.SlabType.BOTTOM));
						}
					}
				}
			}
		} else {
			Roofs.Field lean = Roofs.shed(rx0, rz0, rx1, rz1, up, Roofs.Profile.straight(pitch));
			roof = Roofs.raster(d, lean, y0, rx0, rz0, rx1, rz1, d.mat(Role.ROOF), d.mat(Role.ROOF_TRIM), Habits.Ridge.SLAB);
		}
		// walls: the two ends and the outer wall, up to the roof
		List<int[]> perimeter = new ArrayList<>();
		for (int i = a; i <= b; i++) {
			for (int k = 1; k <= depth; k++) {
				boolean end = i == a || i == b;
				boolean outer = k == depth;
				if (end || outer) {
					perimeter.add(new int[]{s.outX(i, k), s.outZ(i, k), end && outer ? 2 : end ? 1 : 0});
				}
			}
		}
		int plinthCourses = habits.walls().plinth() > 0 ? 1 : 0;
		try (var sc = d.stage(StageKind.WALLS)) {
			for (int[] p : perimeter) {
				int x = p[0];
				int z = p[1];
				boolean corner = p[2] == 2;
				int hi = roof.covers(x, z) ? roof.bottomY(x, z) - 1 : 1;
				Direction.Axis axis = p[2] == 1 ? out.getAxis() : s.axis();
				for (int y = 0; y <= hi; y++) {
					if (corner && (timber || logs)) {
						d.set(x, y, z, frame.log(Direction.Axis.Y));
					} else if (y < plinthCourses) {
						d.set(x, y, z, plinth.full());
					} else if (logs) {
						d.set(x, y, z, wall.log(axis));
					} else if (timber && (corner || y == hi && hi >= 2)) {
						d.set(x, y, z, frame.log(corner ? Direction.Axis.Y : axis));
					} else {
						d.set(x, y, z, wall.full());
					}
				}
			}
		}
		// the room
		int ix0 = Math.min(s.outX(a + 1, 1), s.outX(b - 1, depth - 1));
		int ix1 = Math.max(s.outX(a + 1, 1), s.outX(b - 1, depth - 1));
		int iz0 = Math.min(s.outZ(a + 1, 1), s.outZ(b - 1, depth - 1));
		int iz1 = Math.max(s.outZ(a + 1, 1), s.outZ(b - 1, depth - 1));
		d.carve(ix0, 0, iz0, ix1, 1, iz1);
		// a window in the outer wall
		int mid = (a + b) / 2;
		int wx = s.outX(mid, depth);
		int wz = s.outZ(mid, depth);
		if (roof.covers(wx, wz) && roof.bottomY(wx, wz) - 1 >= 2) {
			d.inStage(StageKind.OPENINGS, () -> d.set(wx, 1, wz, d.mat(Role.GLASS).pane()));
		}
		// the door through the house wall: from the middle outward, a plain wall with standing room inside
		int doorI = Integer.MIN_VALUE;
		for (int k = 0; k <= b - a; k++) {
			int i = k % 2 == 0 ? mid + k / 2 : mid - (k + 1) / 2;
			if (i <= a || i >= b || i <= 0 || i >= s.last()) {
				continue;
			}
			int x = s.x(i);
			int z = s.z(i);
			int inx = x + Dirs.dx(in);
			int inz = z + Dirs.dz(in);
			if (h.plainWall(x, 0, z) && h.plainWall(x, 1, z) && !d.isEmpty(x, 2, z) && h.standable(inx, 0, inz) && d.isEmpty(s.outX(i, 1), 0, s.outZ(i, 1))) {
				doorI = i;
				break;
			}
		}
		if (doorI == Integer.MIN_VALUE) {
			// no way in from the house: an open-fronted woodshed, its outer wall left out between the corner posts
			try (var sc = d.stage(StageKind.WALLS)) {
				for (int i = a + 1; i < b; i++) {
					for (int y = 0; y <= top; y++) {
						d.set(s.outX(i, depth), y, s.outZ(i, depth), Blocks.AIR.defaultBlockState());
					}
				}
			}
			int lx = s.outX(a + 1, 1);
			int lz = s.outZ(a + 1, 1);
			Garden.woodpile(d, lx, 0, lz, s.along(), Math.max(1, b - a - 1));
			return;
		}
		int dx = s.x(doorI);
		int dz = s.z(doorI);
		d.inStage(StageKind.OPENINGS, () -> d.door(dx, 0, dz, d.mat(Role.DOOR).mainOr(Form.DOOR, Blocks.SPRUCE_DOOR), out, DoorHingeSide.LEFT));
		Rooms.Room room = new Rooms.Room(ix0, iz0, ix1, iz1, 0, 2);
		room.keep(s.outX(doorI, 1), s.outZ(doorI, 1));
		JobType job = household(d);
		Rooms.furnish(d, room, job != JobType.NONE && (ix1 - ix0 + 1) * (iz1 - iz0 + 1) >= 6 ? Rooms.Type.WORKSHOP : Rooms.Type.STORAGE, 0, job);
		Lighting.room(d, room);
	}

	/** Whether the house's own roof is pitched: most of its roof over the body is stairs. */
	static boolean pitched(HouseScan h) {
		int roof = 0;
		int stairs = 0;
		for (var e : h.d.cells().long2ObjectEntrySet()) {
			int x = Draft.keyX(e.getLongKey());
			int z = Draft.keyZ(e.getLongKey());
			if (e.getValue().stage() != StageKind.ROOF || x < h.x0 || x > h.x1 || z < h.z0 || z > h.z1 || e.getValue().state().isAir()) {
				continue;
			}
			roof++;
			if (e.getValue().state().getBlock() instanceof net.minecraft.world.level.block.StairBlock) {
				stairs++;
			}
		}
		return roof > 0 && stairs * 10 >= roof * 3;
	}

	/** The household's trade (the {@code household} param's {@code job}), for what a lean-to is used for. */
	static JobType household(Draft d) {
		String job = d.params().getCompoundOrEmpty("household").getStringOr("job", "");
		for (JobType j : JobType.values()) {
			if (j.getSerializedName().equals(job)) {
				return j;
			}
		}
		return JobType.NONE;
	}
}
