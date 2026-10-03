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
import dev.voidmc.hearth.build.draft.States;
import dev.voidmc.hearth.build.kit.Furniture;
import dev.voidmc.hearth.build.kit.Openings;
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

	@Override
	public void draw(Draft d) {
		if (d.param("on_wall", false)) {
			onWall(d);
		} else {
			standing(d);
		}
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
		// the chamber and the stair up to the top along its back wall
		Towers.shell(d, l, 0, 0, w - 1, dp - 1, chamber, t - 2);
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
			highest = Towers.lookout(d, l, 0, 0, w - 1, dp - 1, t, top);
		}
		d.entrance(0, walk, cz);
		d.entrance(w - 1, walk, cz);
		d.poi(PoiType.DOOR, 0, walk, cz, Dirs.RIGHT);
		d.poi(PoiType.GUARD_POST, 1, t, 1, Dirs.FRONT);
		d.poi(PoiType.GUARD_POST, w - 2, t, 1, Dirs.BACK);
		d.poi(PoiType.VIEWPOINT, (w - 1) / 2, t, dp - 2, Dirs.BACK);
		Groundwork.clear(d, 0, 0, w - 1, dp - 1, highest + 1);
	}
}
