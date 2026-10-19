package dev.voidmc.hearth.build.archetype.defense;

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
import dev.voidmc.hearth.build.draft.Paint;
import dev.voidmc.hearth.build.draft.States;
import dev.voidmc.hearth.build.kit.Furniture;
import dev.voidmc.hearth.build.kit.Openings;
import dev.voidmc.hearth.build.kit.Roofs;
import dev.voidmc.hearth.build.kit.Side;
import dev.voidmc.hearth.build.kit.Stairs;
import dev.voidmc.hearth.build.kit.landscape.Lamps;
import dev.voidmc.hearth.build.kit.landscape.Towers;
import dev.voidmc.hearth.build.kit.landscape.Towers.Look;
import dev.voidmc.hearth.build.kit.landscape.Towers.Top;
import dev.voidmc.hearth.build.style.Form;
import dev.voidmc.hearth.build.style.Role;
import dev.voidmc.hearth.build.terrain.Groundwork;
import java.util.List;
import java.util.Set;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;

/**
 * {@code hearth:defense/watchtower}: a lookout tower where the guards keep watch.
 *
 * <p>Standing alone: a square shaft of masonry (logs and boards in timber cultures) on a battered skirt, a door with
 * its lamp, arrow slits climbing with the stair, a timber spiral stair up the inside (on the larger plots a guard room
 * on the ground floor and a floor every storey, the stair in a corner), and at the top a platform oversailing the shaft
 * on a ring of corbels: open and crenellated in the south (desert, mesa, mediterranean, and some temperate towers), or
 * a lookout on corner posts under a pyramid roof, a cone on the steppe, a flared curved roof in the karst.
 *
 * <p>On the wall ({@code on_wall}): a square bastion tower astride the wall line, solid up to the wall walk. The walk
 * runs into it through doorways in both flanks and climbs a few steps into a guard chamber over the wall top (so the
 * tower and a wall stretch built through it agree block for block, whichever is raised first); a stair along the back
 * of the chamber leads up to the top. The wall walk is taken at {@code wall_height} − 2 above the ground.
 *
 * <p>Job sites (GUARD): the grindstone. Storage: a chest. GUARD_POSTs at the top's corners, a VIEWPOINT at its front.
 *
 * <p>Params: {@code on_wall} (boolean), {@code wall_height} (int, 8), {@code roof} ({@code open}, {@code pyramid},
 * {@code cone}, {@code pagoda}; default by culture and seed).
 */
public final class Watchtower implements Archetype {
	public static final ArchetypeInfo INFO = new ArchetypeInfo(Hearth.id("defense/watchtower"), ArchetypeCategory.DEFENSE, Set.of(JobType.GUARD), 0,
		7, 9, 7, 9, 14, 22, SettlementTier.VILLAGE, Set.of(),
		Set.of(ArchetypeInfo.SiteFlag.VERTICAL, ArchetypeInfo.SiteFlag.OUTSKIRTS), List.of(), 0, 1.0);

	@Override
	public ArchetypeInfo info() {
		return INFO;
	}

	static Top topFor(Draft d) {
		return switch (d.param("roof", "")) {
			case "open", "flat" -> Top.OPEN;
			case "pyramid", "hip" -> Top.PYRAMID;
			case "cone" -> Top.CONE;
			case "pagoda" -> Top.PAGODA;
			default -> Towers.top(d);
		};
	}

	/** The tower's plan, by the culture's way of building a lookout (param {@code plan}). */
	enum Plan {
		/** A square masonry shaft under a lookout or battlements (Bohemia, the mountains, the moor, the south, the karst). */
		SQUARE,
		/** A round shaft as the culture's castles raise them, a cone or battlements on it (the coast, the vale, the steppe). */
		ROUND,
		/** A slender timber shaft carrying a hoarded watch room oversailing it on beams, under a steep cap (the north). */
		HOARD,
		/** A watch hut on four posts over the wet, a ladder up a mast in the middle (bayou, tropics). */
		STILTS
	}

	static Plan plan(Draft d) {
		String p = d.param("plan", "");
		for (Plan k : Plan.values()) {
			if (k.name().equalsIgnoreCase(p)) {
				return k;
			}
		}
		return switch (dev.voidmc.hearth.build.kit.landscape.Manner.culture(d)) {
			case MARITIME, WISTERIA, STEPPE -> Plan.ROUND;
			case NORTHERN, TUNDRA -> Plan.HOARD;
			case BAYOU, TROPICAL -> Plan.STILTS;
			default -> Plan.SQUARE;
		};
	}

	@Override
	public void draw(Draft d) {
		if (d.param("on_wall", false)) {
			onWall(d);
			return;
		}
		switch (plan(d)) {
			case ROUND -> round(d);
			case HOARD -> hoarded(d);
			case STILTS -> stilts(d);
			default -> standing(d);
		}
	}

	// ------------------------------------------------------------------------------------------------ the round tower

	/**
	 * A round tower as the culture's castles build them ({@link dev.voidmc.hearth.build.kit.noble.Tower}): a battered
	 * talus, a spiral round a newel, a floor every four rows (the guard room, the bunk room, the store), loops up the
	 * shaft, a door with its lamp, and a cone over a top room (or open battlements on the coast).
	 */
	private static void round(Draft d) {
		int w = d.frontage();
		int dp = d.depth();
		int diam = Math.min(Math.min(w, dp), 9);
		diam -= 1 - diam % 2;
		int cx = (w - 1) / 2;
		int cz = (dp - 1) / 2;
		var man = dev.voidmc.hearth.build.kit.noble.Manner.of(d);
		var m = dev.voidmc.hearth.build.kit.noble.Dress.of(d);
		var cap = dev.voidmc.hearth.build.kit.landscape.Manner.culture(d) == dev.voidmc.hearth.api.Culture.MARITIME && d.chance(0.5)
			? dev.voidmc.hearth.build.kit.noble.Manner.Cap.CRENEL : dev.voidmc.hearth.build.kit.noble.Manner.Cap.CONE;
		int maxTop = d.maxHeight() - 1;
		int rows = dev.voidmc.hearth.build.kit.noble.Tower.headRows(cap, diam, man);
		int top = Math.clamp(maxTop - Math.min(rows, 9), 9, 17);
		List<Integer> lv = new java.util.ArrayList<>();
		for (int y = 4; y <= top - 4; y += 4) {
			lv.add(y);
		}
		int[] levels = lv.stream().mapToInt(Integer::intValue).toArray();
		var t = dev.voidmc.hearth.build.kit.noble.Tower.round(cx, cz, diam, 0, top, cap);
		t.footings(d, m);
		t.shell(d, m, top - 1, levels);
		// the spiral turned to arrive at the top room on the front, where the watch looks out (in a tower seven across
		// the landing's quarters are cut apart by the wall)
		t.arriveToward(Dirs.FRONT, top);
		t.stair(d, m, man.timberTops(), Dirs.BACK);
		t.floors(d, m, top, levels);
		int[] out = t.door(d, m, Dirs.FRONT, 0, 1, true);
		d.entrance(out[0], 0, out[1]);
		Groundwork.approach(d, out[0], 0, out[1]);
		dev.voidmc.hearth.build.kit.light.Lighting.doorLamp(d, out[0] + 1, 2, out[1], Dirs.BACK);
		t.loops(d, Set.of(Dirs.LEFT, Dirs.RIGHT, Dirs.BACK), 2, top);
		t.window(d, m, Dirs.FRONT, Math.max(4, top - 4) + 1, 2);
		int hi = t.head(d, m, man, maxTop);
		dev.voidmc.hearth.build.kit.Rooms.furnish(d, t.room(0), dev.voidmc.hearth.build.kit.Rooms.Type.WORKSHOP, 0, JobType.GUARD);
		for (int k = 0; k < levels.length; k++) {
			dev.voidmc.hearth.build.kit.Rooms.furnish(d, t.room(levels[k]), k == 0 ? dev.voidmc.hearth.build.kit.Rooms.Type.BEDROOM
				: dev.voidmc.hearth.build.kit.Rooms.Type.STORAGE, k == 0 ? 1 : 0, JobType.NONE);
		}
		t.light(d, top - 1);
		t.watch(d, Dirs.FRONT);
		Groundwork.clear(d, 0, 0, w - 1, dp - 1, Math.min(maxTop, hi + 1));
		dev.voidmc.hearth.build.kit.refine.Voids.finish(d);
	}

	// ------------------------------------------------------------------------------------------------ the north

	/**
	 * The northern lookout: a slender shaft of logs five across (a ladder up its back wall from the door) carrying a
	 * watch room that oversails it on cantilevered beams, boarded, with shuttered lights all round under a steep cap:
	 * the grindstone, the arrows and a lamp up there where the watch is kept.
	 */
	private static void hoarded(Draft d) {
		int w = d.frontage();
		int dp = d.depth();
		Look l = Look.of(d);
		int sx0 = (w - 5) / 2;
		int sz0 = (dp - 5) / 2;
		int sx1 = sx0 + 4;
		int sz1 = sz0 + 4;
		int cx = sx0 + 2;
		int p = Math.clamp(d.maxHeight() - 9, 8, 13);
		Groundwork.footings(d, sx0, sz0, sx1, sz1, -1);
		Towers.floor(d, l, sx0 + 1, sz0 + 1, sx1 - 1, sz1 - 1, -1, true);
		Towers.shell(d, l, sx0, sz0, sx1, sz1, 0, p - 2);
		Side front = Side.of(sx0, sz0, sx1, sz1, Dirs.FRONT);
		Openings.door(d, front, cx, 0, 1, Openings.DoorOpts.MAIN);
		Groundwork.approach(d, cx, 0, sz0 - 1);
		for (int y = 4; y < p - 3; y += 3) {
			Towers.slit(d, sx0, y, sz0 + 2);
			Towers.slit(d, sx1, y + 1, sz0 + 2);
		}
		// a spiral of slabs round a newel up the shaft from the door to the watch room, which folk climb as they climb
		// a stair (a ladder they do not): its well left open through the watch room's floor
		// (its first step a cell in from the door, so nobody steps up under the door's head)
		int cz = sz0 + 2;
		Towers.spiral(d, cx, cz, 0, p, 1);
		cabin(d, l, 0, 0, w - 1, dp - 1, p, cx, cz, new int[]{sx0, sz0, sx1, sz1});
		dev.voidmc.hearth.build.kit.light.Lighting.room(d, sx0 + 1, sz0 + 1, sx1 - 1, sz1 - 1, 0);
		int hi = cabinRoof(d, 0, 0, w - 1, dp - 1, p);
		Groundwork.clear(d, 0, 0, w - 1, dp - 1, Math.min(d.maxHeight() - 1, hi + 1));
		dev.voidmc.hearth.build.kit.refine.Voids.finish(d);
	}

	// ------------------------------------------------------------------------------------------------ over the wet

	/**
	 * The lookout of the bayou and the palm coast: a watch hut on four corner posts braced with rails, a mast up the
	 * middle with a ladder on its front face to a hatch in the hut's floor, the hut boarded with shutters all round
	 * under a steep cap of the roofing.
	 */
	private static void stilts(Draft d) {
		int w = d.frontage();
		int dp = d.depth();
		Look l = Look.of(d);
		int x0 = 1;
		int z0 = 1;
		int x1 = w - 2;
		int z1 = dp - 2;
		int cx = (w - 1) / 2;
		int cz = (dp - 1) / 2;
		int p = Math.clamp(d.maxHeight() - 9, 7, 11);
		Mat post = l.post();
		Mat rail = d.mat(Role.RAILING);
		try (var s = d.stage(StageKind.FRAME)) {
			for (int[] c : new int[][]{{x0, z0}, {x1, z0}, {x0, z1}, {x1, z1}, {cx, cz}}) {
				int g = Math.min(d.site().ground(c[0], c[1]), 0);
				for (int y = g - 1; y < p - 1; y++) {
					d.set(c[0], y, c[1], post.log(Direction.Axis.Y).orElse(l.body().full()));
				}
			}
			// rails between the corner posts at two heights, braces against the wind (the front left open under the hut)
			if (rail.has(Form.FENCE)) {
				for (int y : new int[]{3, p - 3}) {
					for (int[] c : Draft.ring(x0, z0, x1, z1)) {
						if (d.isEmpty(c[0], y, c[1]) && !(c[1] == z0 && Math.abs(c[0] - cx) <= 1)) {
							d.set(c[0], y, c[1], rail.fence());
						}
					}
				}
			}
		}
		// a spiral of slabs round the mast up to the hut (folk climb it as a stair, where a ladder stops them), its foot
		// on the front, its well open through the hut's floor
		Towers.spiral(d, cx, cz, 0, p, 0);
		cabin(d, l, 0, 0, w - 1, dp - 1, p, cx, cz, null);
		try (var s = d.stage(StageKind.FLOORS)) {
			// the foot of the stair on a path of the paving from the street, on a footing down to the ground where the
			// ground dips under it (a river bank: the path is the way in, and a path on nothing falls at the first decay)
			Mat footing = d.mat(Role.FOUNDATION);
			for (int z = 0; z < cz - 1; z++) {
				d.set(cx, -1, z, d.mat(Role.PAVING).full());
				for (int y = Math.min(d.site().ground(cx, z), -1) - 1; y < -1; y++) {
					d.set(cx, y, z, footing.full());
				}
			}
		}
		d.entrance(cx, 0, 0);
		int hi = cabinRoof(d, 0, 0, w - 1, dp - 1, p);
		Groundwork.clear(d, 0, 0, w - 1, dp - 1, Math.min(d.maxHeight() - 1, hi + 1));
		dev.voidmc.hearth.build.kit.refine.Voids.finish(d);
	}

	/**
	 * A boarded watch room over the rectangle at standing level p: its floor (open over the spiral round (hx, hz)),
	 * beams cantilevered out under it from the walls of the shaft {@code shaft} where there is one, walls two high of
	 * boards between posts with shutters open in the middle of each side, and the watch's furniture.
	 */
	private static void cabin(Draft d, Look l, int x0, int z0, int x1, int z1, int p, int hx, int hz, int @org.jspecify.annotations.Nullable [] shaft) {
		Mat boards = l.timber() ? l.body() : d.mat(Role.FLOOR);
		Mat shutter = d.mat(Role.SHUTTER);
		Mat post = l.post();
		if (shaft != null) {
			try (var s = d.stage(StageKind.FRAME)) {
				// beams out from the shaft's head under the oversail, every other cell
				for (Side side : Side.of(shaft[0], shaft[1], shaft[2], shaft[3])) {
					for (int i = 0; i < side.length(); i += 2) {
						for (int k = 1; k <= 2; k++) {
							int bx = side.x(i) + Dirs.dx(side.out()) * k;
							int bz = side.z(i) + Dirs.dz(side.out()) * k;
							if (bx >= x0 && bx <= x1 && bz >= z0 && bz <= z1) {
								d.set(bx, p - 2, bz, post.log(side.out().getAxis()).orElse(l.body().full()));
							}
						}
					}
				}
			}
		}
		try (var s = d.stage(StageKind.FLOORS)) {
			for (int z = z0; z <= z1; z++) {
				for (int x = x0; x <= x1; x++) {
					// (the spiral's last treads and the air over them left as they are: the well up into the room)
					if ((x != hx || z != hz) && d.isAbsent(x, p - 1, z)) {
						d.set(x, p - 1, z, d.mat(Role.FLOOR).full());
					}
				}
			}
		}
		try (var s = d.stage(StageKind.WALLS)) {
			for (Side side : Side.of(x0, z0, x1, z1)) {
				int mid = side.middle();
				for (int i = 0; i < side.length(); i++) {
					for (int y = p; y <= p + 1; y++) {
						Paint pt;
						if (side.isCorner(i) || Math.abs(i - mid) == 2) {
							pt = post.log(Direction.Axis.Y).orElse(l.body().full());
						} else if (y == p + 1 && Math.abs(i - mid) <= 1 && shutter.has(Form.TRAPDOOR)) {
							pt = shutter.trapdoor(side.out().getOpposite(), Half.TOP, true);
						} else {
							pt = boards.full();
						}
						d.set(side.x(i), y, side.z(i), pt);
					}
				}
			}
		}
		java.util.Set<Long> taken = new java.util.HashSet<>();
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				taken.add(Draft.key(hx + dx, 0, hz + dz));
			}
		}
		int[] g = Towers.spot(d, x0 + 1, z0 + 1, x1 - 1, z1 - 1, p, taken);
		if (g != null) {
			Furniture.workstation(d, JobType.GUARD, g[0], p, g[1], Dirs.HORIZONTAL[g[2]]);
		} else {
			Furniture.workstation(d, JobType.GUARD, x0 + 1, p, z1 - 1, Dirs.FRONT);
		}
		int[] c = Towers.spot(d, x0 + 1, z0 + 1, x1 - 1, z1 - 1, p, taken);
		if (c != null) {
			Furniture.chest(d, c[0], p, c[1], Dirs.HORIZONTAL[c[2]], List.of(new Object[]{"minecraft:arrow", 24}, new Object[]{"minecraft:torch", 8}));
		}
		d.poi(PoiType.GUARD_POST, x0 + 1, p, z0 + 1, Dirs.FRONT);
		d.poi(PoiType.GUARD_POST, x1 - 1, p, z0 + 1, Dirs.FRONT);
		d.poi(PoiType.VIEWPOINT, (x0 + x1) / 2, p, z0 + 1, Dirs.FRONT);
		dev.voidmc.hearth.build.kit.light.Lighting.room(d, x0 + 1, z0 + 1, x1 - 1, z1 - 1, p);
	}

	/** The watch room's steep cap over its walls (their top course at p + 1). Returns its top. */
	private static int cabinRoof(Draft d, int x0, int z0, int x1, int z1, int p) {
		dev.voidmc.hearth.build.style.Habits.Roof hr = d.habits().roof();
		Roofs.Opts o = Roofs.Opts.of(d).withEaves(0, 0).withPitch(Math.max(hr.pitch(), 1.3));
		Roofs.Built b = Roofs.build(d, x0, z0, x1, z1, p + 1, dev.voidmc.hearth.build.style.Habits.RoofForm.HIP, Direction.Axis.X, o, false, false);
		return b.roof().ridgeY;
	}

	private static void standing(Draft d) {
		int w = d.frontage();
		int dp = d.depth();
		Look l = Look.of(d);
		Top top = topFor(d);
		int sx0 = 1;
		int sz0 = 1;
		int sx1 = w - 2;
		int sz1 = dp - 2;
		int cx = (sx0 + sx1) / 2;
		int roomAbove = top == Top.OPEN ? 3 : 8;
		int t = Math.max(8, Math.min(16, d.maxHeight() - roomAbove));
		t -= t % 2;
		// the door and the way to it come first: the shaft stands on its footings, the ground storey paved
		Side front = Side.of(sx0, sz0, sx1, sz1, Dirs.FRONT);
		Groundwork.footings(d, sx0, sz0, sx1, sz1, -1);
		Towers.floor(d, l, sx0 + 1, sz0 + 1, sx1 - 1, sz1 - 1, -1, true);
		Towers.shell(d, l, sx0, sz0, sx1, sz1, 0, t - 2);
		for (int y = 4; y < t - 3; y += l.timber() ? 4 : 8) {
			Towers.string(d, l, sx0, sz0, sx1, sz1, y);
		}
		skirt(d, l, w, dp);
		Towers.buttresses(d, l, sx0, sz0, sx1, sz1, 2);
		// the stair: a spiral in the middle of a small shaft, in the back corner of a big one with floors
		int ix0 = sx0 + 1;
		int iz0 = sz0 + 1;
		int ix1 = sx1 - 1;
		int iz1 = sz1 - 1;
		boolean roomy = ix1 - ix0 >= 3 && iz1 - iz0 >= 3;
		int scx = roomy ? ix1 - 1 : (ix0 + ix1) / 2;
		int scz = roomy ? iz1 - 1 : (iz0 + iz1) / 2;
		Towers.spiral(d, scx, scz, 0, t, 1);
		if (roomy) {
			for (int y = 3; y <= t - 5; y += 4) {
				Towers.floor(d, l, ix0, iz0, ix1, iz1, y, false);
			}
		}
		// the platform over a corbel ring, then the openings
		Towers.corbels(d, l, 0, 0, w - 1, dp - 1, t - 2);
		Towers.floor(d, l, 0, 0, w - 1, dp - 1, t - 1, !l.timber());
		Openings.door(d, front, cx, 0, 1, Openings.DoorOpts.MAIN);
		Groundwork.approach(d, cx, 0, sz0 - 1);
		slits(d, sx0, sz0, sx1, sz1, t, cx);
		Towers.banner(d, cx, t - 4, sz0 - 1, Dirs.FRONT);
		if (roomy) {
			guardRoom(d, ix0, iz0, ix1, iz1, scx, scz);
		}
		// the top
		int highest;
		if (top == Top.OPEN) {
			Towers.battlements(d, l, 0, 0, w - 1, dp - 1, t);
			highest = t + 1;
			Lamps.lanternOn(d, 0, t + 2, 0);
		} else {
			highest = Towers.lookout(d, l, 0, 0, w - 1, dp - 1, t, top);
			Lamps.lanternOn(d, (w - 1) / 2, t + 1, 0);
		}
		watch(d, w, dp, t);
		Groundwork.clear(d, 0, 0, w - 1, dp - 1, highest + 1);
		dev.voidmc.hearth.build.kit.refine.Voids.finish(d);
	}

	/** A battered skirt of stone round the shaft's foot where it stands on the ground. */
	private static void skirt(Draft d, Look l, int w, int dp) {
		if (l.timber() || !l.found().has(Form.STAIRS)) {
			return;
		}
		try (var s = d.stage(StageKind.FOUNDATION)) {
			for (Side side : Side.of(0, 0, w - 1, dp - 1)) {
				for (int i = 1; i < side.length() - 1; i++) {
					int x = side.x(i);
					int z = side.z(i);
					int g = d.site().ground(x, z);
					if (g > 0 || g < -2 || !d.isAbsent(x, g, z) || side.out() == Dirs.FRONT && Math.abs(i - side.middle()) <= 1) {
						continue;
					}
					d.set(x, g, z, l.found().stairs(side.out().getOpposite(), Half.BOTTOM));
				}
			}
		}
	}

	/** Arrow slits up every face, staggered so they follow the stair round. */
	private static void slits(Draft d, int x0, int z0, int x1, int z1, int t, int doorX) {
		int k = 0;
		for (int y = 2; y < t - 3; y += 2) {
			Side s = Side.of(x0, z0, x1, z1)[k % 4];
			k++;
			int i = s.middle();
			if (s.out() == Dirs.FRONT && y < 4) {
				continue;
			}
			Towers.slit(d, s.x(i), y, s.z(i));
		}
	}

	/** A guard room on the ground floor of a roomy tower: the grindstone, a chest, a rack of arms, a table. */
	private static void guardRoom(Draft d, int ix0, int iz0, int ix1, int iz1, int scx, int scz) {
		Furniture.chest(d, ix0, 0, iz1, Dirs.RIGHT, List.of(new Object[]{"minecraft:arrow", 16}, new Object[]{"minecraft:bread", 4}));
		if (iz1 - 1 > iz0) {
			Furniture.table(d, ix0, 0, iz1 - 2);
		}
		armsRack(d, ix0, 1, iz0 + 1 <= iz1 - 3 ? iz0 + 1 : iz0, Dirs.RIGHT);
		// a lantern over the middle of the free floor, clear of the stair's ring
		int lx = ix0 + 1;
		int lz = iz0;
		if (Math.abs(lx - scx) > 1 || Math.abs(lz - scz) > 1) {
			Furniture.hangingLantern(d, lx, 3, lz, 0);
		}
	}

	/** A shelf of arms against the wall. */
	static void armsRack(Draft d, int x, int y, int z, Direction facing) {
		var shelf = d.mat(Role.FURNITURE).main(Form.SHELF);
		try (var s = d.stage(StageKind.INTERIOR)) {
			if (shelf != null) {
				d.set(x, y, z, States.facing(shelf, facing), BlockEntities.shelf(List.of(new Object[]{"minecraft:iron_sword", 1, 0},
					new Object[]{"minecraft:bow", 1, 1}, new Object[]{"minecraft:shield", 1, 2})));
			}
		}
	}

	/** The watch at the top: guard posts and a viewpoint along the front, the grindstone and a chest where they fit. */
	static void watch(Draft d, int w, int dp, int t) {
		d.poi(PoiType.GUARD_POST, 2, t, 1, Dirs.FRONT);
		d.poi(PoiType.GUARD_POST, w - 3, t, 1, Dirs.FRONT);
		d.poi(PoiType.VIEWPOINT, (w - 1) / 2, t, 1, Dirs.FRONT);
		java.util.Set<Long> taken = new java.util.HashSet<>();
		for (int x = 1; x < w - 1; x++) {
			taken.add(Draft.key(x, 0, 1));
		}
		int[] g = Towers.spot(d, 1, 1, w - 2, dp - 2, t, taken);
		if (g != null) {
			Furniture.workstation(d, JobType.GUARD, g[0], t, g[1], Dirs.HORIZONTAL[g[2]]);
		}
		int[] c = Towers.spot(d, 1, 1, w - 2, dp - 2, t, taken);
		if (c != null) {
			Furniture.chest(d, c[0], t, c[1], Dirs.HORIZONTAL[c[2]], List.of(new Object[]{"minecraft:arrow", 24}, new Object[]{"minecraft:torch", 8}));
		}
		if (g == null) {
			// no room on top: the grindstone stands at the foot of the stair inside the door
			Furniture.workstation(d, JobType.GUARD, (w - 1) / 2 - 1, 0, 2, Dirs.RIGHT);
		}
	}

	// ------------------------------------------------------------------------------------------------ on the wall

	private static void onWall(Draft d) {
		int w = d.frontage();
		int dp = d.depth();
		Look l = Look.of(d);
		Top top = topFor(d);
		int walk = Math.clamp(d.param("wall_height", 8), 5, 14) - 2;
		int cz = (dp - 1) / 2;
		int chamber = walk + 2;
		int t = chamber + 4;
		Mat body = l.body();
		Mat found = l.found();
		BlockState air = Blocks.AIR.defaultBlockState();
		// the solid bastion, up to the wall top, footed in the ground
		try (var s = d.stage(StageKind.FOUNDATION)) {
			for (int z = 0; z < dp; z++) {
				for (int x = 0; x < w; x++) {
					int g = d.site().ground(x, z);
					for (int y = Math.min(g, 0) - 2; y < Math.min(g, 0) + 1 && y <= walk + 1; y++) {
						d.set(x, y, z, found.full());
					}
				}
			}
		}
		try (var s = d.stage(StageKind.WALLS)) {
			for (int z = 0; z < dp; z++) {
				for (int x = 0; x < w; x++) {
					int g = d.site().ground(x, z);
					boolean edge = x == 0 || z == 0 || x == w - 1 || z == dp - 1;
					boolean corner = (x == 0 || x == w - 1) && (z == 0 || z == dp - 1);
					for (int y = Math.min(g, 0) + 1; y <= walk + 1; y++) {
						d.set(x, y, z, corner && (y & 1) == 1 ? l.quoin().full() : body.full());
					}
				}
			}
			// the wall walk through it: level doorways in the flanks, two steps up into the chamber
			for (int x = 0; x < w; x++) {
				int fromEdge = Math.min(x, w - 1 - x);
				Direction in = x < w / 2 ? Dirs.RIGHT : Dirs.LEFT;
				switch (fromEdge) {
					case 0 -> {
						d.set(x, walk, cz, air);
						d.set(x, walk + 1, cz, air);
					}
					case 1 -> {
						d.set(x, walk, cz, body.stairsOrSlab(in, Half.BOTTOM));
						d.set(x, walk + 1, cz, air);
					}
					case 2 -> d.set(x, walk + 1, cz, body.stairsOrSlab(in, Half.BOTTOM));
					default -> {
					}
				}
			}
		}
		// the chamber and the stair up to the top along its back wall; the flank doorways three high so a person
		// stepping up into the chamber clears the lintel
		Towers.shell(d, l, 0, 0, w - 1, dp - 1, chamber, t - 2);
		try (var s = d.stage(StageKind.OPENINGS)) {
			d.set(0, chamber, cz, air);
			d.set(w - 1, chamber, cz, air);
		}
		Stairs.straight(d, 1, chamber, dp - 2, Dirs.RIGHT, 4, 1);
		Towers.corbels(d, l, 0, 0, w - 1, dp - 1, t - 2);
		Towers.floor(d, l, 0, 0, w - 1, dp - 1, t - 1, !l.timber());
		for (Side side : Side.of(0, 0, w - 1, dp - 1)) {
			if (side.out() == Dirs.FRONT || side.out() == Dirs.BACK) {
				Towers.slit(d, side.x(side.middle()), chamber + 1, side.z(side.middle()));
			}
		}
		Furniture.workstation(d, JobType.GUARD, 1, chamber, 1, Dirs.RIGHT);
		Furniture.chest(d, w - 2, chamber, 1, Dirs.LEFT, List.<Object[]>of(new Object[]{"minecraft:arrow", 16}));
		Furniture.hangingLantern(d, (w - 1) / 2, t - 1, 2, 0);
		int highest;
		if (top == Top.OPEN) {
			Towers.battlements(d, l, 0, 0, w - 1, dp - 1, t);
			highest = t + 1;
		} else {
			highest = Towers.lookout(d, l, 0, 0, w - 1, dp - 1, t, top, 0);
		}
		d.entrance(0, walk, cz);
		d.entrance(w - 1, walk, cz);
		d.poi(PoiType.DOOR, 0, walk, cz, Dirs.RIGHT);
		d.poi(PoiType.GUARD_POST, 2, t, 1, Dirs.FRONT);
		d.poi(PoiType.GUARD_POST, w - 3, t, 1, Dirs.BACK);
		d.poi(PoiType.VIEWPOINT, (w - 1) / 2, t, dp - 2, Dirs.BACK);
		Groundwork.clear(d, 0, 0, w - 1, dp - 1, highest + 1);
		dev.voidmc.hearth.build.kit.refine.Voids.finish(d);
	}
}
