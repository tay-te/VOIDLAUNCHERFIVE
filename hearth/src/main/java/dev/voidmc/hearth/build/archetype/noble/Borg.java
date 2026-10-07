package dev.voidmc.hearth.build.archetype.noble;

import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.PoiType;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.kit.Rooms;
import dev.voidmc.hearth.build.kit.civic.Sacred;
import dev.voidmc.hearth.build.kit.light.Lighting;
import dev.voidmc.hearth.build.kit.noble.Chambers;
import dev.voidmc.hearth.build.kit.noble.Court;
import dev.voidmc.hearth.build.kit.noble.Curtain;
import dev.voidmc.hearth.build.kit.noble.Dress;
import dev.voidmc.hearth.build.kit.noble.Flag;
import dev.voidmc.hearth.build.kit.noble.Gate;
import dev.voidmc.hearth.build.kit.noble.Manner;
import dev.voidmc.hearth.build.kit.noble.Outline;
import dev.voidmc.hearth.build.kit.noble.Range;
import dev.voidmc.hearth.build.kit.noble.Tower;
import dev.voidmc.hearth.build.style.Habits.RoofForm;
import dev.voidmc.hearth.build.terrain.Groundwork;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.core.Direction;

/**
 * The north's castle, a borg of stone and timber (Eketorp's ring, the kastal of Gotland, Hammershus's older keep): a
 * ring wall of fieldstone laid out by eye round its knoll, a timber breastwork along its walk; a gate tower of stone
 * under a timber hoarding; the kastal on the ring at the back, a round stone tower whose timber gallery and steep shingle
 * cap stand over everything, the lord's rooms and the strongbox in it; in the ring the great hall of timber under a
 * steep gable, the throne at its end, and the houses of the household about it, timber on stone footings: the stave
 * chapel with its belfry, the cookhouse, the stable, the sleeping house. Low and spread on the ground, one tower: the
 * opposite of a castle of towers.
 *
 * <p>Raised in six campaigns: the ring; the gate tower; the kastal; the hall; the chapel and the cookhouse; the stable,
 * the sleeping house and the court.
 */
final class Borg {
	private Borg() {
	}

	/** Where everything stands (local coordinates). */
	static final class Plan {
		int w;
		int dp;
		int c;
		int maxTop;
		int y;
		int walk;
		int thick = 2;
		boolean towerLeft;
		Outline ring;
		/** The front of the ring on the axis (the gate's face). */
		int gz;
		/** The kastal's centre and diameter. */
		int kx;
		int kz;
		int kd = 11;
		/** Boxes already standing, {x0, z0, x1, z1}, for placing the houses. */
		final List<int[]> taken = new ArrayList<>();
	}

	static void draw(Draft d, Manner man, Dress m) {
		Plan p = plan(d);
		d.body(p.ring.x0, p.ring.z0, p.ring.x1, p.ring.z1);
		d.eave(p.walk + 6);
		try (var ph = d.campaign(0)) {
			Court.fill(d, m, p.ring, p.thick, p.y);
			Curtain.enclose(d, m, man, p.ring, p.thick, p.walk, Curtain.Opts.of(man));
		}
		try (var ph = d.campaign(1)) {
			gate(d, m, man, p);
		}
		try (var ph = d.campaign(2)) {
			kastal(d, m, man, p);
		}
		try (var ph = d.campaign(3)) {
			hall(d, m, man, p);
		}
		try (var ph = d.campaign(4)) {
			chapel(d, m, man, p);
			cookhouse(d, m, man, p);
		}
		try (var ph = d.campaign(5)) {
			stable(d, m, man, p);
			sleepingHouse(d, m, man, p);
			Court.lamps(d, p.ring, p.thick, p.y);
			d.poi(PoiType.MEETING_POINT, p.c, p.y, p.gz + 5, Dirs.BACK);
		}
		try (var ph = d.campaign(0)) {
			Groundwork.clear(d, 0, 0, p.w - 1, p.dp - 1, p.maxTop);
		}
	}

	static Plan plan(Draft d) {
		Plan p = new Plan();
		p.w = d.frontage();
		p.dp = d.depth();
		p.c = (p.w - 1) / 2;
		p.maxTop = d.maxHeight() - 1;
		p.towerLeft = d.param("tower_side", d.chance(0.5) ? "left" : "right").equals("left");
		double cz = (6 + p.dp - 2) / 2.0;
		double rx = p.c - 2.5;
		double rz = (p.dp - 2 - 6) / 2.0;
		p.ring = Outline.oval(p.c, cz, rx, rz, 16, 0.05, d.seed());
		p.y = Court.level(d, p.ring.x0 + 4, p.ring.z0 + 4, p.ring.x1 - 4, p.ring.z1 - 4, -1, 2);
		p.walk = p.y + 5;
		p.gz = p.ring.front(p.c);
		// the kastal on the ring at the back, to one side of the axis
		p.kx = p.towerLeft ? p.c - 6 : p.c + 6;
		p.kz = Math.min(p.dp - 1 - p.kd / 2, p.ring.back(p.kx) - 1);
		p.taken.add(new int[]{p.c - 5, p.gz - 3, p.c + 5, p.gz + 4});
		p.taken.add(new int[]{p.kx - p.kd / 2, p.kz - p.kd / 2, p.kx + p.kd / 2, p.kz + p.kd / 2});
		return p;
	}

	/** A box bw × bd as near (px, pz) as the ring and the buildings already placed allow, or null. */
	static int[] place(Plan p, int bw, int bd, int px, int pz) {
		return place(p.ring, p.thick, p.taken, bw, bd, px, pz);
	}

	/**
	 * A box bw × bd standing in the ward of {@code ring} (a block clear of its curtain) and two clear of every box in
	 * {@code taken}, searched outward from (px, pz); added to {@code taken} and returned, or null if none is found.
	 */
	static int[] place(Outline ring, int thick, List<int[]> taken, int bw, int bd, int px, int pz) {
		for (int r = 0; r <= 9; r++) {
			for (int dz = -r; dz <= r; dz++) {
				for (int dx = -r; dx <= r; dx++) {
					if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
						continue;
					}
					int x0 = px - bw / 2 + dx;
					int z0 = pz - bd / 2 + dz;
					int[] box = {x0, z0, x0 + bw - 1, z0 + bd - 1};
					if (ring.fits(box[0], box[1], box[2], box[3], thick, 1) && free(taken, box, 2)) {
						taken.add(box);
						return box;
					}
				}
			}
		}
		return null;
	}

	private static boolean free(List<int[]> taken, int[] b, int gap) {
		for (int[] t : taken) {
			if (b[0] <= t[2] + gap && b[2] >= t[0] - gap && b[1] <= t[3] + gap && b[3] >= t[1] - gap) {
				return false;
			}
		}
		return true;
	}

	// ------------------------------------------------------------------------------------------------ the gate and the kastal

	/** The gate tower: stone, a passage through it, its hall at the walk, a timber hoarding and a shingle cap over it. */
	private static void gate(Draft d, Dress m, Manner man, Plan p) {
		int top = Math.min(p.walk + 5, p.maxTop - 12);
		Gate.tower(d, m, man, p.c - 4, p.gz - 3, p.c + 4, p.gz + 3, 3, p.y, p.walk, top, p.maxTop);
		d.entrance(p.c, p.y, p.gz - 4);
		Groundwork.approach(d, p.c, p.y, p.gz - 4);
		Lighting.doorLamp(d, p.c - 2, p.y + 2, p.gz - 4, Dirs.BACK);
		Lighting.doorLamp(d, p.c + 2, p.y + 2, p.gz - 4, Dirs.BACK);
		Flag.banner(d, m, p.c, p.walk + 1, p.gz - 4, Dirs.FRONT, 0);
		d.poi(PoiType.GUARD_POST, p.c + 2, p.y, p.gz - 4, Dirs.FRONT);
	}

	/**
	 * The kastal: a round tower of stone on the ring, its door on the court, a floor at the walk opening onto it both
	 * ways (the guards'), the steward's room with the strongbox, the lord's chamber with his chapel niche, the stores at
	 * the foot; a timber gallery under the steep cap, the lord's flag over it.
	 */
	private static void kastal(Draft d, Dress m, Manner man, Plan p) {
		Manner.Cap cap = man.keepCap();
		int head = Math.min(14, Tower.headRows(cap, p.kd, man));
		int top = Math.max(p.walk + 10, Math.min(p.maxTop - head, p.y + 24));
		List<Integer> levels = new ArrayList<>();
		for (int y = p.walk; y < top - 3; y += 5) {
			levels.add(y);
		}
		int[] lv = levels.stream().mapToInt(Integer::intValue).toArray();
		Tower t = Tower.round(p.kx, p.kz, p.kd, p.y, top, cap);
		t.footings(d, m);
		t.shell(d, m, top - 1, lv);
		t.stair(d, m, true, Dirs.FRONT);
		t.floors(d, m, top, lv);
		t.door(d, m, Dirs.FRONT, p.y, 1, true);
		int[] f = t.face(Dirs.FRONT);
		Lighting.doorLamp(d, f[0] + 1, p.y + 2, f[1] - 1, Dirs.BACK);
		t.doorToward(d, m, Dirs.LEFT, p.walk, false);
		t.doorToward(d, m, Dirs.RIGHT, p.walk, false);
		t.loops(d, Set.of(Dirs.BACK, p.towerLeft ? Dirs.LEFT : Dirs.RIGHT), p.y + 2, top);
		for (int k = 1; k < lv.length; k++) {
			t.window(d, m, Dirs.FRONT, lv[k] + 1, 2);
			t.window(d, m, p.towerLeft ? Dirs.RIGHT : Dirs.LEFT, lv[k] + 1, 2);
		}
		t.flag(p.towerLeft ? Dirs.RIGHT : Dirs.LEFT);
		t.head(d, m, man, p.maxTop);
		Direction field = p.towerLeft ? Dirs.LEFT : Dirs.RIGHT;
		for (int k = 0; k <= lv.length; k++) {
			int y = k == 0 ? p.y : lv[k - 1];
			Rooms.Room room = t.room(y);
			switch (k) {
				case 0 -> Rooms.furnish(d, room, Rooms.Type.STORAGE, 0, JobType.NONE);
				case 1 -> Rooms.furnish(d, room, Rooms.Type.WORKSHOP, 0, JobType.GUARD);
				case 2 -> {
					Chambers.desk(d, m, room, room.x0, (room.z0 + room.z1) / 2, Dirs.LEFT);
					Chambers.furnish(d, room, Rooms.Type.BEDROOM, 1);
				}
				default -> {
					Chambers.niche(d, m, room, field == Dirs.LEFT ? room.x0 : room.x1, room.z1, Dirs.BACK);
					Chambers.furnish(d, room, Rooms.Type.BEDROOM, 2);
				}
			}
		}
		if (lv.length < 3) {
			// a short kastal: the lord's niche in the guards' storey
			Rooms.Room room = t.room(lv.length > 0 ? lv[lv.length - 1] : p.y);
			Chambers.niche(d, m, room, room.x1, room.z1, Dirs.BACK);
		}
		t.light(d, top - 1);
		t.watch(d, Dirs.FRONT);
	}

	// ------------------------------------------------------------------------------------------------ the houses

	/**
	 * The great hall in the middle of the ring: one tall room of timber on a stone footing under a steep gable, the
	 * throne at the end away from the kastal, long tables, the fire on the back wall; a porch door on the court.
	 */
	private static void hall(Draft d, Dress m, Manner man, Plan p) {
		int[] b = null;
		for (int half = 11; half >= 6 && b == null; half--) {
			b = place(p, 2 * half + 1, 8, p.c, (p.gz + p.kz) / 2 + 1);
		}
		if (b == null) {
			b = new int[]{p.c - 6, p.gz + 6, p.c + 6, p.gz + 13};
			p.taken.add(b);
		}
		Range r = new Range(b[0], b[1], b[2], b[3], p.y, new int[]{7}, Dirs.FRONT).timber();
		r.footings(d, m);
		r.walls(d, m, man);
		r.floors(d, m);
		int c = (b[0] + b[2]) / 2;
		r.door(d, m, Dirs.FRONT, c, 0, 2, true);
		r.windows(d, m, man, 0, 2, 3, null);
		int[] hearth = r.hearth(d, c + 3, b[3], 0, Dirs.FRONT);
		r.roof(d, m, man, RoofForm.GABLE, Direction.Axis.X, p.maxTop, 0, List.of(new int[]{hearth[0], hearth[1]}));
		int[] in = r.room(0);
		Rooms.Room room = Chambers.room(in[0], in[1], in[2], in[3], in[4], new int[]{c, in[1]}, new int[]{c + 1, in[1]}, new int[]{c + 3, in[3]});
		Chambers.hall(d, m, room, p.towerLeft ? Dirs.RIGHT : Dirs.LEFT, true);
		Range.hang(d, in, 4);
		Lighting.room(d, in[0], in[1], in[2], in[3], in[4]);
	}

	/** The stave chapel: a little timber church under a steep gable, the belfry on its front ridge, the altar at its end. */
	private static void chapel(Draft d, Dress m, Manner man, Plan p) {
		int side = p.towerLeft ? 1 : -1;
		int[] b = place(p, 7, 9, p.c + side * 9, p.gz + 8);
		if (b == null) {
			// no room in the ring: the altar and the chaplain's lectern in the kastal's ground storey
			Tower t = Tower.round(p.kx, p.kz, p.kd, p.y, p.y + 5, Manner.Cap.CRENEL);
			Rooms.Room room = t.room(p.y);
			Sacred.altar(d, p.kx, p.y, room.z1, Dirs.FRONT, false, p.kx + 2);
			return;
		}
		Range r = new Range(b[0], b[1], b[2], b[3], p.y, new int[]{6}, Dirs.FRONT).timber();
		r.footings(d, m);
		r.walls(d, m, man);
		r.floors(d, m);
		int c = (b[0] + b[2]) / 2;
		r.door(d, m, Dirs.FRONT, c, 0, 1, false);
		r.windows(d, m, man, 0, 2, 3, null);
		r.roof(d, m, man, RoofForm.GABLE, Direction.Axis.Z, p.maxTop - 2, 0, List.of());
		if (r.roof != null && r.roof.covers(c, b[1] + 1)) {
			Castle.belfry(d, m, man, c, b[1] + 1, r.roof.surfaceY(c, b[1] + 1) + 1, p.maxTop);
		}
		int[] in = r.room(0);
		Sacred.altar(d, c, p.y, in[3], Dirs.FRONT, false, c + 2);
		Sacred.pews(d, in[0], in[2], in[3] - 3, in[1] + 1, p.y, c, Dirs.BACK, false);
		Lighting.room(d, in[0], in[1], in[2], in[3], in[4]);
	}

	/** The cookhouse: a small timber house with the kitchen's fire and its chimney. */
	private static void cookhouse(Draft d, Dress m, Manner man, Plan p) {
		int side = p.towerLeft ? -1 : 1;
		int[] b = place(p, 7, 7, p.c + side * 9, p.gz + 7);
		if (b == null) {
			return;
		}
		Direction door = side < 0 ? Dirs.RIGHT : Dirs.LEFT;
		Range r = new Range(b[0], b[1], b[2], b[3], p.y, new int[]{5}, door).timber();
		r.footings(d, m);
		r.walls(d, m, man);
		r.floors(d, m);
		int mz = (b[1] + b[3]) / 2;
		r.door(d, m, door, mz, 0, 1, false);
		r.windows(d, m, man, 0, 2, 3, null);
		int[] hearth = r.hearth(d, (b[0] + b[2]) / 2, b[3], 0, Dirs.FRONT);
		r.roof(d, m, man, RoofForm.GABLE, Direction.Axis.X, p.maxTop - 6, 0, List.of(new int[]{hearth[0], hearth[1]}));
		int[] in = r.room(0);
		Rooms.Room room = Chambers.room(in[0], in[1], in[2], in[3], in[4], new int[]{door == Dirs.LEFT ? in[0] : in[2], mz});
		Chambers.furnish(d, room, Rooms.Type.KITCHEN, 0);
		Lighting.room(d, in[0], in[1], in[2], in[3], in[4]);
	}

	/** The stable: stalls along its back wall, a hitching post at its door. */
	private static void stable(Draft d, Dress m, Manner man, Plan p) {
		int side = p.towerLeft ? -1 : 1;
		int[] b = place(p, 9, 6, p.c + side * 10, (p.gz + p.kz) / 2 + 3);
		if (b == null) {
			return;
		}
		Direction door = side < 0 ? Dirs.RIGHT : Dirs.LEFT;
		Range r = new Range(b[0], b[1], b[2], b[3], p.y, new int[]{5}, Dirs.FRONT).timber();
		r.footings(d, m);
		r.walls(d, m, man);
		r.floors(d, m);
		int c = (b[0] + b[2]) / 2;
		int[] out = r.door(d, m, Dirs.FRONT, c, 0, 2, false);
		r.windows(d, m, man, 0, 1, 3, null);
		r.roof(d, m, man, RoofForm.GABLE, Direction.Axis.X, p.maxTop - 8, 0, List.of());
		int[] in = r.room(0);
		Rooms.Room room = Chambers.room(in[0], in[1], in[2], in[3], in[4], new int[]{c, in[1]}, new int[]{c + 1, in[1]});
		Castle.stalls(d, m, room, door.getOpposite());
		Lighting.room(d, in[0], in[1], in[2], in[3], in[4]);
		d.poi(PoiType.HITCHING_POST, out[0], p.y, out[1], Dirs.BACK);
	}

	/** The sleeping house of the household. */
	private static void sleepingHouse(Draft d, Dress m, Manner man, Plan p) {
		int side = p.towerLeft ? 1 : -1;
		int[] b = place(p, 8, 6, p.c + side * 10, (p.gz + p.kz) / 2 + 3);
		if (b == null) {
			return;
		}
		Range r = new Range(b[0], b[1], b[2], b[3], p.y, new int[]{5}, Dirs.FRONT).timber();
		r.footings(d, m);
		r.walls(d, m, man);
		r.floors(d, m);
		int c = (b[0] + b[2]) / 2;
		r.door(d, m, Dirs.FRONT, c, 0, 1, false);
		r.windows(d, m, man, 0, 2, 3, null);
		r.roof(d, m, man, RoofForm.GABLE, Direction.Axis.X, p.maxTop - 8, 0, List.of());
		int[] in = r.room(0);
		Rooms.Room room = Chambers.room(in[0], in[1], in[2], in[3], in[4], new int[]{c, in[1]});
		Chambers.furnish(d, room, Rooms.Type.BEDROOM, 3);
		Lighting.room(d, in[0], in[1], in[2], in[3], in[4]);
	}
}
