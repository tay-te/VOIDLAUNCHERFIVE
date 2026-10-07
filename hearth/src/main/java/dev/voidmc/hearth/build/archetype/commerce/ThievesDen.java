package dev.voidmc.hearth.build.archetype.commerce;

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
import dev.voidmc.hearth.build.kit.Floors;
import dev.voidmc.hearth.build.kit.Furniture;
import dev.voidmc.hearth.build.kit.Garden;
import dev.voidmc.hearth.build.kit.Rooms;
import dev.voidmc.hearth.build.kit.Stairs;
import dev.voidmc.hearth.build.kit.civic.Grounds;
import dev.voidmc.hearth.build.kit.civic.Trade;
import dev.voidmc.hearth.build.kit.civic.Tradition;
import dev.voidmc.hearth.build.kit.light.Lighting;
import dev.voidmc.hearth.build.kit.sociallife.Cellar;
import dev.voidmc.hearth.build.kit.sociallife.Shell;
import dev.voidmc.hearth.build.kit.sociallife.Venue;
import dev.voidmc.hearth.build.kit.sociallife.Yardwork;
import dev.voidmc.hearth.build.kit.trade.Floorplan;
import dev.voidmc.hearth.build.style.Role;
import dev.voidmc.hearth.build.terrain.Groundwork;
import dev.voidmc.hearth.build.kit.landscape.Edges;
import java.util.List;
import java.util.Set;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;

/**
 * {@code hearth:commerce/thieves_den}: an ordinary poor house in a back lane, and under it the fence's room. Above, a
 * single shabby room like any of its neighbours: a table and a stool, a fire, a pallet, a few barrels; in its back
 * corner the stair down, half hidden behind a stack of barrels and a hanging cloth. Below, dug into the ground, the
 * secret room: the fence's counter where stolen goods are bought, the strongbox, chests and crates of loot, a dice
 * table for those who wait, all by candle and lantern light. Where the house stands on posts (no cellar can be dug),
 * the secret room is a back room behind a partition instead.
 *
 * <p>POIs: the FENCE's JOB_SITE, COUNTER and MARKET_STALL (the fence's trade), STRONGBOX, STORAGE, GAMING_TABLE, the
 * HEARTH, the DOOR. Params: {@code variant} (int).
 */
public final class ThievesDen implements Archetype {
	public static final ArchetypeInfo INFO = new ArchetypeInfo(Hearth.id("commerce/thieves_den"), ArchetypeCategory.COMMERCE, Set.of(JobType.FENCE), 0,
		7, 10, 7, 10, 7, 10, SettlementTier.TOWN, Set.of(), Set.of(), List.of(), 1, 1.0);

	private static final List<Object[]> LOOT = List.of(new Object[]{"minecraft:gold_nugget", 14}, new Object[]{"minecraft:iron_ingot", 3},
		new Object[]{"minecraft:leather", 4}, new Object[]{"minecraft:candle", 3});

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
		boolean raised = t.raised();

		// --- the house: a single room over the whole plot but a strip before it
		// (a den keeps low: even where the culture builds on posts it sits on short ones at the lane's level)
		int zF = 1;
		int zM = dp - 1;
		int base = 0;
		int h0 = Math.max(3, Math.min(4, maxH - base - 1));
		Body body = new Body(0, zF, w - 1, zM, base, new int[]{h0}, false, false);
		d.body(0, zF, w - 1, zM);
		d.eave(body.plateY());
		// the lane before it levelled to the street the whole width (a den's door is off the middle, where the lane's ground
		// may stand higher or lower on a hillside)
		Groundwork.level(d, 0, 0, w - 1, zF - 1, base, true);
		int doorCol = d.chance(0.5) ? 2 : w - 3;
		Shell.Built sh = Shell.build(d, body, Direction.Axis.X, Dirs.FRONT, doorCol, 1, 0.45, false, false, false);
		doorCol = sh.doorCol();
		int ix0 = 1;
		int ix1 = w - 2;
		int iz0 = zF + 1;
		int iz1 = zM - 1;
		// the stair (or the back room) in the back corner away from the door
		boolean far = doorCol <= w / 2;
		int cornerX = far ? ix1 : ix0;
		Direction toDoorSide = far ? Dirs.LEFT : Dirs.RIGHT;

		Floorplan room = new Floorplan(ix0, iz0, ix1, iz1, base, h0 - 1);
		room.keep(sh.inside()[0], sh.inside()[1]);
		Floorplan secret;
		int sy;
		boolean cellar = !raised && iz1 - iz0 + 1 >= Cellar.HEADROOM + 2 && maxH - base >= 5;
		if (cellar) {
			// the cellar under the whole room, the stair down along the end wall from the back corner toward the front
			Cellar.dig(d, ix0, iz0, ix1, iz1, base - 1);
			Floors.boards(d, ix0, iz0, ix1, iz1, base - 1);
			sy = base - 1 - Cellar.HEADROOM;
			int run = Cellar.HEADROOM + 1;
			// the stair climbs from the cellar toward the back wall: its top step in the floor, one steps off at the corner
			int sz0 = iz1 - run;
			Stairs.straight(d, cornerX, sy, sz0, Dirs.BACK, run, 1);
			for (int k = 0; k < run; k++) {
				room.block(cornerX, sz0 + k);
			}
			room.keep(cornerX, iz1);
			room.keep(cornerX + Dirs.dx(toDoorSide), iz1);
			// the rail along the stairwell's open side, barrels stacked to hide its foot
			try (var s = d.stage(StageKind.INTERIOR)) {
				for (int k = 0; k < run - 1; k++) {
					int rx = cornerX + Dirs.dx(toDoorSide);
					int rz = sz0 + k;
					if (room.free(rx, rz)) {
						d.set(rx, base, rz, d.mat(Role.RAILING).fence());
						room.block(rx, rz);
					}
				}
			}
			int bz = sz0 - 1;
			if (room.free(cornerX, bz) && room.take(cornerX, bz, null)) {
				Furniture.barrel(d, cornerX, base, bz, Dirs.BACK);
				d.inStage(StageKind.INTERIOR, () -> {
					if (d.isEmpty(cornerX, base + 1, bz)) {
						d.set(cornerX, base + 1, bz, States.facing(Blocks.BARREL, Direction.UP));
					}
				});
			}
			secret = new Floorplan(ix0, iz0, ix1, iz1, sy, Cellar.HEADROOM);
			secret.keep(cornerX, sz0 - 1);
			secret.keep(cornerX + Dirs.dx(toDoorSide), sz0);
			for (int k = 0; k < run; k++) {
				secret.block(cornerX, sz0 + k);
			}
		} else {
			// a back room behind a partition across the house, its door in the corner away from the street door
			int pz = iz1 - 3;
			if (pz <= iz0 + 1) {
				pz = iz0 + 2;
			}
			int doorX = cornerX + Dirs.dx(toDoorSide);
			sy = base;
			Yardwork.partitionAcross(d, pz, ix0, ix1, base, h0 - 1, doorX, Dirs.BACK);
			room = new Floorplan(ix0, iz0, ix1, pz - 1, base, h0 - 1);
			room.keep(sh.inside()[0], sh.inside()[1]);
			room.keep(doorX, pz - 1);
			secret = new Floorplan(ix0, pz + 1, ix1, iz1, base, h0 - 1);
			secret.keep(doorX, pz + 1);
		}

		// --- the ordinary room above: a fire, a table and stool, barrels, a pallet of straw
		room.blockDrawn(d);
		for (Direction wall : new Direction[]{Dirs.LEFT, Dirs.RIGHT, Dirs.BACK}) {
			boolean done = false;
			for (int[] c : room.along(wall)) {
				int fx = c[0] - Dirs.dx(wall);
				int fz = c[1] - Dirs.dz(wall);
				if (room.free(c[0], c[1]) && room.inside(fx, fz) && room.take(c[0], c[1], wall.getOpposite())) {
					Furniture.hearth(d, c[0], base, c[1], wall.getOpposite(), false);
					done = true;
					break;
				}
			}
			if (done) {
				break;
			}
		}
		Rooms.furnish(d, room.room(), Rooms.Type.LIVING, 0, JobType.NONE);
		Lighting.room(d, room.x0, room.z0, room.x1, room.z1, base);

		// --- the secret room: the fence's counter facing the stair, the strongbox, loot, a dice table
		secret.blockDrawn(d);
		boolean counter = false;
		for (Direction wall : new Direction[]{Dirs.FRONT, toDoorSide, Dirs.BACK, toDoorSide.getOpposite()}) {
			List<int[]> run = secret.along(wall);
			Direction into = wall.getOpposite();
			for (int i = 1; i + 2 < run.size() && !counter; i++) {
				// the fence's lane against the wall, the counter of two before it, the customer before that
				int[] a = run.get(i);
				int[] b = run.get(i + 1);
				int[] ca = {a[0] + Dirs.dx(into), a[1] + Dirs.dz(into)};
				int[] cb = {b[0] + Dirs.dx(into), b[1] + Dirs.dz(into)};
				int[] cust = {ca[0] + Dirs.dx(into), ca[1] + Dirs.dz(into)};
				if (!secret.free(a[0], a[1]) || !secret.free(b[0], b[1]) || !secret.free(ca[0], ca[1]) || !secret.free(cb[0], cb[1])
					|| !secret.inside(cust[0], cust[1])) {
					continue;
				}
				// the lane must be entered from its end
				int[] end = run.get(i + 2);
				if (!secret.free(end[0], end[1]) || !secret.take(new int[][]{ca, cb}, cust)) {
					continue;
				}
				secret.take(new int[][]{}, a);
				secret.keep(end[0], end[1]);
				Direction along = Dirs.of(b[0] - a[0], b[1] - a[1]);
				Trade.counter(d, cb[0], sy, cb[1], along.getOpposite(), 2, into, JobType.NONE);
				d.poi(PoiType.JOB_SITE, a[0], sy, a[1], into, JobType.FENCE);
				counter = true;
			}
			if (counter) {
				break;
			}
		}
		if (!counter) {
			// a cramped room: the fence's barrel against any wall
			for (Direction wall : Dirs.HORIZONTAL) {
				if (counter) {
					break;
				}
				for (int[] c : secret.along(wall)) {
					int fx = c[0] - Dirs.dx(wall);
					int fz = c[1] - Dirs.dz(wall);
					if (secret.free(c[0], c[1]) && secret.inside(fx, fz) && secret.take(c[0], c[1], wall.getOpposite())) {
						// the fence stands before his barrel, the customer beside him
						Furniture.barrel(d, c[0], sy, c[1], wall.getOpposite());
						d.poi(PoiType.JOB_SITE, fx, sy, fz, wall, JobType.FENCE);
						d.poi(PoiType.COUNTER, fx, sy, fz, wall);
						int[] cu = {fx - Dirs.dx(wall), fz - Dirs.dz(wall)};
						if (!secret.open(cu[0], cu[1])) {
							cu = new int[]{fx, fz};
						}
						d.poi(PoiType.MARKET_STALL, cu[0], sy, cu[1], wall);
						counter = true;
						break;
					}
				}
			}
		}
		// the strongbox and the loot against the walls
		int chests = 0;
		for (Direction wall : Dirs.HORIZONTAL) {
			for (int[] c : secret.along(wall)) {
				if (chests >= 3) {
					break;
				}
				int fx = c[0] - Dirs.dx(wall);
				int fz = c[1] - Dirs.dz(wall);
				if (!secret.free(c[0], c[1]) || !secret.inside(fx, fz) || !secret.take(c[0], c[1], wall.getOpposite())) {
					continue;
				}
				Direction face = wall.getOpposite();
				if (chests == 0) {
					try (var s = d.stage(StageKind.INTERIOR)) {
						d.set(c[0], sy, c[1], States.facing(Blocks.CHEST, face), BlockEntities.container(List.of(new Object[]{"minecraft:gold_ingot", 4},
							new Object[]{"minecraft:emerald", 2}, new Object[]{"minecraft:gold_nugget", 20})));
					}
					d.poi(PoiType.STRONGBOX, fx, sy, fz, wall);
					d.poi(PoiType.STORAGE, fx, sy, fz, wall);
				} else if (chests == 1) {
					Furniture.chest(d, c[0], sy, c[1], face, LOOT);
				} else {
					Furniture.barrel(d, c[0], sy, c[1], face);
				}
				chests++;
			}
		}
		Venue.placeGaming(d, secret, sy, 1);
		Lighting.room(d, secret.x0, secret.z0, secret.x1, secret.z1, sy);

		// --- outside: nothing to remark on; a water butt, some refuse, a door lamp the shell already hung
		Grounds.approach(d, sh.outside()[0], base, sh.outside()[1]);
		if (!raised && zF >= 1) {
			int bx = doorCol <= w / 2 ? w - 2 : 1;
			if (Venue.standable(d, bx, base, 0)) {
				Garden.waterButt(d, bx, base, 0);
			}
		}
		if (d.habits().snow()) {
			Garden.snowBanks(d, 0, zF, w - 1, zM, base, doorCol);
		}
		Groundwork.clear(d, 0, 0, w - 1, dp - 1, Math.min(maxH - 1, sh.skyline() + 3));
		Edges.guard(d);
	}
}
