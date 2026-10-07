package dev.voidmc.hearth.build.archetype.noble;

import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.PoiType;
import dev.voidmc.hearth.api.StageKind;
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
import dev.voidmc.hearth.build.kit.noble.Mason;
import dev.voidmc.hearth.build.kit.noble.Outline;
import dev.voidmc.hearth.build.kit.noble.Range;
import dev.voidmc.hearth.build.kit.noble.Turret;
import dev.voidmc.hearth.build.style.Habits.RoofForm;
import dev.voidmc.hearth.build.terrain.Groundwork;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;

/**
 * The castle of the warm coasts and the bayou: a fort of the gunpowder age (San Felipe, Fort de Chartres): a square of
 * low thick walls with an arrowhead bastion thrust out at every corner, a garita on each bastion's point (a domed sentry
 * box in the tropics, a little hipped one on the bayou), the gate in the middle of the front with its pediment. Inside
 * the parade ground with the flagstaff; the commandant's house across the back, two storeys behind an arcaded gallery,
 * the great room upstairs; the chapel and the cookhouse in the front corners; barracks down the sides where the fort is
 * deep enough. Low and star-shaped: the walls are the silhouette, not towers.
 *
 * <p>Raised in campaigns: the trace (in up to three, a stretch at a time, on a hillside); the gate and the garitas; the
 * commandant's house; the chapel and the cookhouse; the barracks; the parade ground.
 */
final class Bastion {
	private Bastion() {
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
		/** The square between the bastions (the curtains' field faces). */
		int x0;
		int x1;
		int z0;
		int z1;
		/** How far a bastion's point stands out from the square's corner, along each axis. */
		int b = 6;
		Outline trace;
	}

	static void draw(Draft d, Manner man, Dress m) {
		Plan p = plan(d);
		d.body(p.trace.x0, p.trace.z0, p.trace.x1, p.trace.z1);
		d.eave(p.walk + 6);
		// the parade ground levelled, the trace raised a stretch at a time where it is footed far down a hillside
		try (var ph = d.campaign(0)) {
			Court.fill(d, m, p.trace, p.thick, p.y);
		}
		Curtain.encloseInCampaigns(d, m, man, p.trace, p.thick, p.walk, Curtain.Opts.of(man), null, 0, 3);
		try (var ph = d.campaign(3)) {
			gate(d, m, man, p);
			garitas(d, m, man, p);
		}
		try (var ph = d.campaign(4)) {
			house(d, m, man, p);
		}
		try (var ph = d.campaign(5)) {
			chapel(d, m, man, p);
			cookhouse(d, m, man, p);
		}
		try (var ph = d.campaign(6)) {
			barracks(d, m, man, p, true);
			barracks(d, m, man, p, false);
		}
		try (var ph = d.campaign(7)) {
			parade(d, m, p);
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
		p.x0 = p.b + 2;
		p.x1 = p.w - p.b - 3;
		p.z0 = p.b + 2;
		p.z1 = p.dp - p.b - 3;
		int f = 4;
		int s = 3;
		int b = p.b;
		// round the trace: each bastion's flank out from the curtain, its two faces to the point, the other flank back
		p.trace = Outline.of(
			p.x0 + f, p.z0, p.x1 - f, p.z0,
			p.x1 - f, p.z0 - s, p.x1 + b, p.z0 - b, p.x1 + s, p.z0 + f, p.x1, p.z0 + f,
			p.x1, p.z1 - f,
			p.x1 + s, p.z1 - f, p.x1 + b, p.z1 + b, p.x1 - f, p.z1 + s, p.x1 - f, p.z1,
			p.x0 + f, p.z1,
			p.x0 + f, p.z1 + s, p.x0 - b, p.z1 + b, p.x0 - s, p.z1 - f, p.x0, p.z1 - f,
			p.x0, p.z0 + f,
			p.x0 - s, p.z0 + f, p.x0 - b, p.z0 - b, p.x0 + f, p.z0 - s);
		p.y = Court.level(d, p.x0 + 2, p.z0 + 2, p.x1 - 2, p.z1 - 2, -1, 1);
		p.walk = p.y + 5;
		return p;
	}

	// ------------------------------------------------------------------------------------------------ gate and garitas

	/** The gate in the middle of the front curtain: a passage under a low gate house with its pediment and the arms. */
	private static void gate(Draft d, Dress m, Manner man, Plan p) {
		int top = Math.min(p.walk + 3, p.maxTop - 8);
		Gate.tower(d, m, man, p.c - 3, p.z0 - 2, p.c + 3, p.z0 + 3, 3, p.y, p.walk, top, p.maxTop);
		d.entrance(p.c, p.y, p.z0 - 3);
		Groundwork.approach(d, p.c, p.y, p.z0 - 3);
		Lighting.doorLamp(d, p.c - 2, p.y + 2, p.z0 - 3, Dirs.BACK);
		Lighting.doorLamp(d, p.c + 2, p.y + 2, p.z0 - 3, Dirs.BACK);
		Flag.banner(d, m, p.c, p.walk + 1, p.z0 - 3, Dirs.FRONT, 0);
		d.poi(PoiType.GUARD_POST, p.c + 2, p.y, p.z0 - 3, Dirs.FRONT);
	}

	/** A garita on each bastion's point: a sentry box three across on the walk, slits out, open behind, its cap over it. */
	private static void garitas(Draft d, Dress m, Manner man, Plan p) {
		int[][] points = {{p.x0 - p.b, p.z0 - p.b, 1, 1}, {p.x1 + p.b, p.z0 - p.b, -1, 1}, {p.x0 - p.b, p.z1 + p.b, 1, -1},
			{p.x1 + p.b, p.z1 + p.b, -1, -1}};
		for (int[] pt : points) {
			int cx = pt[0] + pt[2] * 2;
			int cz = pt[1] + pt[3] * 2;
			int y = p.walk;
			if (!Mason.inPlot(d, cx - 1, cz - 1) || !Mason.inPlot(d, cx + 1, cz + 1)) {
				continue;
			}
			try (var s = d.stage(StageKind.WALLS)) {
				for (int dx = -1; dx <= 1; dx++) {
					for (int dz = -1; dz <= 1; dz++) {
						d.set(cx + dx, y - 1, cz + dz, m.paving.full());
						boolean ring = dx != 0 || dz != 0;
						// open toward the bastion's gorge, the inward side
						boolean open = dx == pt[2] && dz == 0 || dz == pt[3] && dx == 0;
						for (int yy = y; yy <= y + 1; yy++) {
							if (!ring || open) {
								d.set(cx + dx, yy, cz + dz, Blocks.AIR.defaultBlockState());
							} else {
								boolean slit = yy == y && (dx == 0 || dz == 0);
								d.set(cx + dx, yy, cz + dz, slit ? dev.voidmc.hearth.build.draft.Paint.of(Blocks.AIR.defaultBlockState()) : m.dress.full());
							}
						}
					}
				}
			}
			Turret.cap(d, m, man, cx, cz, 3, y + 2, p.maxTop);
			d.poi(PoiType.GUARD_POST, cx, y, cz, Dirs.nearest(-pt[2], -pt[3]));
		}
	}

	// ------------------------------------------------------------------------------------------------ the buildings

	/**
	 * The commandant's house across the back: the guards' room and the steward's room below, the great room with the
	 * throne and the lord's chamber above, a flight inside; an arcaded gallery along its front; a hipped roof.
	 */
	private static void house(Draft d, Dress m, Manner man, Plan p) {
		int hw = Math.min(8, (p.x1 - p.x0) / 2 - 2);
		int x0 = p.c - hw;
		int x1 = p.c + hw;
		int z1 = p.z1 - p.thick;
		int z0 = z1 - 7;
		Range r = new Range(x0, z0, x1, z1, p.y, new int[]{5, 5}, Dirs.FRONT);
		r.blind(Dirs.BACK);
		int fx = x1 - 1;
		r.flight(fx, z1 - 1, 0, Dirs.LEFT);
		r.footings(d, m, true);
		r.walls(d, m, man);
		r.floors(d, m);
		r.flights(d, m);
		int xm = p.c;
		int d1 = (x0 + xm) / 2;
		int d2 = (xm + x1) / 2;
		r.door(d, m, Dirs.FRONT, d1, 0, 1, false);
		r.door(d, m, Dirs.FRONT, d2, 0, 1, true);
		r.windows(d, m, man, 0, 2, 3, null);
		r.windows(d, m, man, 1, 3, 3, null);
		int[] hearth = r.hearth(d, x0 + 2, z1, 1, Dirs.FRONT);
		r.roof(d, m, man, RoofForm.HIP, Direction.Axis.X, p.maxTop, 0, List.of(new int[]{hearth[0], hearth[1]}));
		Palace.arcade(d, m, man, x0 + 1, z0, x1 - 1, z0, p.y, Dirs.FRONT, d1, d2);
		int land = fx - 5;
		int[] g = r.room(0);
		r.partition(d, m, 0, true, xm, g[1] + 1);
		Rooms.Room guards = Chambers.room(g[0], g[1], xm - 1, g[3], g[4], new int[]{d1, g[1]}, new int[]{xm - 1, g[1] + 1});
		Rooms.furnish(d, guards, Rooms.Type.WORKSHOP, 0, JobType.GUARD);
		Rooms.Room office = Chambers.room(xm + 1, g[1], g[2], g[3], g[4], new int[]{d2, g[1]}, new int[]{xm + 1, g[1] + 1}, new int[]{fx, z1 - 2});
		office.keepArea(land, z1 - 1, fx, z1 - 1);
		Chambers.desk(d, m, office, xm + 1, g[3] - 1, Dirs.LEFT);
		Chambers.furnish(d, office, Rooms.Type.STORAGE, 0);
		int[] u = r.room(1);
		r.partition(d, m, 1, true, xm, u[1] + 1);
		Rooms.Room hall = Chambers.room(xm + 1, u[1], u[2], u[3], u[4], new int[]{land, z1 - 1}, new int[]{xm + 1, u[1] + 1});
		hall.keepArea(land, z1 - 1, fx, z1 - 1);
		Chambers.hall(d, m, hall, Dirs.RIGHT, (u[2] - xm) >= 7);
		Rooms.Room lord = Chambers.room(u[0], u[1], xm - 1, u[3], u[4], new int[]{xm - 1, u[1] + 1});
		Chambers.niche(d, m, lord, u[0], u[3], Dirs.LEFT);
		Chambers.furnish(d, lord, Rooms.Type.BEDROOM, 2);
		for (int k = 0; k < 2; k++) {
			int[] rm = r.room(k);
			Lighting.room(d, rm[0], rm[1], rm[2], rm[3], rm[4]);
		}
	}

	/** The chapel in the front left corner: a gabled cell, the bell gable over its door, the altar at the back. */
	private static void chapel(Draft d, Dress m, Manner man, Plan p) {
		int x0 = p.x0 + p.thick + 1;
		int x1 = x0 + 6;
		int z0 = p.z0 + p.thick + 2;
		int z1 = Math.min(z0 + 8, p.z1 - 12);
		if (x1 >= p.c - 4) {
			x1 = p.c - 4;
		}
		Range r = new Range(x0, z0, x1, z1, p.y, new int[]{6}, Dirs.FRONT);
		r.footings(d, m, true);
		r.walls(d, m, man);
		r.floors(d, m);
		int cx = (x0 + x1) / 2;
		r.door(d, m, Dirs.FRONT, cx, 0, 1, false);
		r.windows(d, m, man, 0, 3, 3, null);
		r.roof(d, m, man, RoofForm.GABLE, Direction.Axis.Z, p.maxTop - 6, 0, List.of());
		if (r.roof != null && r.roof.covers(cx, z0 + 1)) {
			Castle.belfry(d, m, man, cx, z0 + 1, r.roof.surfaceY(cx, z0 + 1) + 1, p.maxTop);
		}
		int[] in = r.room(0);
		// the priest's lectern beside the altar, inside the walls however narrow the corner leaves the cell
		boolean wide = in[2] - in[0] >= 4;
		int lx = cx + 2 <= in[2] ? cx + 2 : cx - 2 >= in[0] ? cx - 2 : cx + 1 <= in[2] ? cx + 1 : cx - 1;
		Sacred.altar(d, cx, p.y, in[3], Dirs.FRONT, wide, lx);
		Sacred.pews(d, in[0], in[2], in[3] - 3, in[1] + 2, p.y, cx, Dirs.BACK, false);
		Lighting.room(d, in[0], in[1], in[2], in[3], in[4]);
	}

	/** The cookhouse in the front right corner, the household's beds in its loft room. */
	private static void cookhouse(Draft d, Dress m, Manner man, Plan p) {
		int x1 = p.x1 - p.thick - 1;
		int x0 = Math.max(x1 - 6, p.c + 4);
		int z0 = p.z0 + p.thick + 2;
		int z1 = Math.min(z0 + 7, p.z1 - 12);
		Range r = new Range(x0, z0, x1, z1, p.y, new int[]{5}, Dirs.FRONT);
		r.footings(d, m, true);
		r.walls(d, m, man);
		r.floors(d, m);
		int cx = (x0 + x1) / 2;
		r.door(d, m, Dirs.FRONT, cx, 0, 1, false);
		r.windows(d, m, man, 0, 2, 3, null);
		int[] hearth = r.hearth(d, cx, z1, 0, Dirs.FRONT);
		r.roof(d, m, man, RoofForm.HIP, Direction.Axis.Z, p.maxTop - 6, 0, List.of(new int[]{hearth[0], hearth[1]}));
		int[] in = r.room(0);
		Rooms.Room room = Chambers.room(in[0], in[1], in[2], in[3], in[4], new int[]{cx, in[1]}, new int[]{cx, in[3]});
		Chambers.furnish(d, room, Rooms.Type.KITCHEN, 0);
		Chambers.furnish(d, room, Rooms.Type.BEDROOM, 2);
		Lighting.room(d, in[0], in[1], in[2], in[3], in[4]);
	}

	/** Barracks down a side between the corner houses and the commandant's: one long room of bunks. */
	private static void barracks(Draft d, Dress m, Manner man, Plan p, boolean left) {
		int z0 = p.z0 + p.thick + 12;
		int z1 = p.z1 - p.thick - 10;
		if (z1 - z0 < 6) {
			return;
		}
		int x0 = left ? p.x0 + p.thick : p.x1 - p.thick - 5;
		int x1 = x0 + 5;
		Direction court = left ? Dirs.RIGHT : Dirs.LEFT;
		Range r = new Range(x0, z0, x1, z1, p.y, new int[]{5}, court);
		r.blind(court.getOpposite());
		r.footings(d, m, true);
		r.walls(d, m, man);
		r.floors(d, m);
		int mz = (z0 + z1) / 2;
		r.door(d, m, court, mz, 0, 1, false);
		r.windows(d, m, man, 0, 2, 3, null);
		r.roof(d, m, man, RoofForm.HIP, Direction.Axis.Z, p.maxTop - 8, 0, List.of());
		int[] in = r.room(0);
		Rooms.Room room = Chambers.room(in[0], in[1], in[2], in[3], in[4], new int[]{left ? in[2] : in[0], mz});
		Chambers.furnish(d, room, Rooms.Type.BEDROOM, 3);
		Lighting.room(d, in[0], in[1], in[2], in[3], in[4]);
	}

	/** The parade ground: the flagstaff on its plinth in the middle, lamps. */
	private static void parade(Draft d, Dress m, Plan p) {
		int cz = (p.z0 + p.z1) / 2 - 1;
		if (Castle.open(d, p.c - 1, cz - 1, p.c + 1, cz + 1, p.y, p.y + 3)) {
			try (var s = d.stage(StageKind.EXTERIOR)) {
				for (int dx = -1; dx <= 1; dx++) {
					for (int dz = -1; dz <= 1; dz++) {
						d.set(p.c + dx, p.y, cz + dz, m.dress.has(dev.voidmc.hearth.build.style.Form.SLAB)
							? m.dress.slab(net.minecraft.world.level.block.state.properties.SlabType.BOTTOM) : m.dress.full());
					}
				}
				d.set(p.c, p.y, cz, m.dress.full());
			}
			Flag.pole(d, m, p.c, p.y + 1, cz, 8, Dirs.RIGHT, 3, p.maxTop);
		}
		d.poi(PoiType.MEETING_POINT, p.c, p.y, cz - 3, Dirs.BACK);
		Court.lamps(d, p.trace, p.thick, p.y);
	}
}
