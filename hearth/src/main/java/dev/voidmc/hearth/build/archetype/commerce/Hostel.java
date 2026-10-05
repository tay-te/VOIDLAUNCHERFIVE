package dev.voidmc.hearth.build.archetype.commerce;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.ArchetypeCategory;
import dev.voidmc.hearth.api.ArchetypeInfo;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.PoiType;
import dev.voidmc.hearth.api.SettlementTier;
import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.Archetype;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Mat;
import dev.voidmc.hearth.build.draft.States;
import dev.voidmc.hearth.build.kit.Body;
import dev.voidmc.hearth.build.kit.Furniture;
import dev.voidmc.hearth.build.kit.Garden;
import dev.voidmc.hearth.build.kit.Rooms;
import dev.voidmc.hearth.build.kit.Side;
import dev.voidmc.hearth.build.kit.Stairs;
import dev.voidmc.hearth.build.kit.civic.Grounds;
import dev.voidmc.hearth.build.kit.civic.Trade;
import dev.voidmc.hearth.build.kit.civic.Tradition;
import dev.voidmc.hearth.build.kit.homestead.Bohemia;
import dev.voidmc.hearth.build.kit.homestead.Yard;
import dev.voidmc.hearth.build.kit.light.Lighting;
import dev.voidmc.hearth.build.kit.sociallife.Shell;
import dev.voidmc.hearth.build.kit.sociallife.Signboard;
import dev.voidmc.hearth.build.kit.sociallife.Venue;
import dev.voidmc.hearth.build.kit.sociallife.Wards;
import dev.voidmc.hearth.build.kit.sociallife.Yardwork;
import dev.voidmc.hearth.build.kit.trade.Floorplan;
import dev.voidmc.hearth.build.style.Role;
import dev.voidmc.hearth.build.terrain.Groundwork;
import java.util.List;
import java.util.Set;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.Half;

/**
 * {@code hearth:commerce/hostel}: the house at the town's gate where pilgrims and travellers sleep cheaply. A long plain
 * range of two storeys with its ridge along the road: on the ground floor the refectory, long tables and benches for the
 * travellers' supper, the keeper's counter by the door and the kitchen hearth on the end wall; up the stair the
 * dormitory, beds head to the wall in two rows down its length with a chest between each pair and lanterns over the
 * aisle (on a low plot, one storey: refectory and dormitory side by side behind a partition). Outside, a bench by the
 * door under the house's board, a wayside cross for the pilgrims, a trough and a hitching rail for the travellers' beasts
 * (the stables' hire works from it), and behind, a draw-well and the woodpile.
 *
 * <p>POIs: BEDs (6 or more), the INNKEEPER's JOB_SITE, COUNTER and MARKET_STALL, TABLE, SEATs, the HEARTH, STORAGE, a
 * HITCHING_POST, the DOOR. Params: {@code name} (int, the board's name, default by seed).
 */
public final class Hostel implements Archetype {
	public static final ArchetypeInfo INFO = new ArchetypeInfo(Hearth.id("commerce/hostel"), ArchetypeCategory.COMMERCE, Set.of(JobType.INNKEEPER), 6,
		11, 15, 11, 15, 12, 16, SettlementTier.VILLAGE, Set.of(), Set.of(), List.of(), 1, 1.0);

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

		// --- the plan: the range across the front, a strip before it, the yard behind
		int md = dp >= 14 ? 8 : 7;
		int zF = raised ? Math.max(2, Math.min(dp - md, 1 + Grounds.stiltBase(d, 0, 1, w - 1, md))) : 2;
		int zM = Math.min(dp - 1, zF + md - 1);
		int base = raised ? Grounds.stiltBase(d, 0, zF, w - 1, zM) : 0;
		// the dormitory a full storey over the refectory; on a low plot all on one floor
		boolean loft = false;
		// (a low upper storey under a raised house's height)
		boolean two = maxH - base >= 8;
		int[] heights = loft ? new int[]{4} : two ? new int[]{4, maxH - base >= 10 ? 4 : 3} : new int[]{5};
		Body body = new Body(0, zF, w - 1, zM, base, heights, false, false);
		d.body(0, zF, w - 1, zM);
		d.eave(body.plateY());
		int doorCol = d.chance(0.5) ? w / 2 - 1 : w / 2 + 1;
		Grounds.forecourt(d, 0, w - 1, zF - 1, !raised);
		Shell.Built sh = Shell.build(d, body, Direction.Axis.X, Dirs.FRONT, doorCol, 1, 0.7, !loft, t.masonry(), loft);
		if (loft) {
			// the loft's floor of boards on the wall plate
			dev.voidmc.hearth.build.kit.Floors.boards(d, 1, zF + 1, w - 2, zM - 1, body.plateY());
		}
		doorCol = sh.doorCol();
		int ix0 = 1;
		int ix1 = w - 2;
		int iz0 = zF + 1;
		int iz1 = zM - 1;

		// --- the stair (two storeys): along the back wall at the end away from the door, climbing toward the door
		boolean stairLeft = doorCol > w / 2;
		int stairX = stairLeft ? ix0 : ix1 - 3;
		int beds = 0;
		Floorplan ground = new Floorplan(ix0, iz0, ix1, iz1, base, heights[0] - 1);
		ground.keep(sh.inside()[0], sh.inside()[1]);
		if (two) {
			Direction climb = stairLeft ? Dirs.RIGHT : Dirs.LEFT;
			int sx0 = stairLeft ? ix0 : ix1;
			Stairs.straight(d, sx0, base, iz1, climb, heights[0], 1);
			int fy = base + heights[0];
			try (var s = d.stage(StageKind.FLOORS)) {
				for (int k = 0; k < heights[0]; k++) {
					int x = sx0 + Dirs.dx(climb) * k;
					if (k < heights[0] - 1) {
						d.set(x, fy - 1, iz1, Blocks.AIR.defaultBlockState());
					}
				}
			}
			for (int k = 0; k < heights[0]; k++) {
				ground.block(sx0 + Dirs.dx(climb) * k, iz1);
			}
			ground.keep(sx0 + Dirs.dx(climb) * heights[0], iz1);
			ground.keep(sx0, iz1 - 1);
			stairX = sx0;
		}

		// --- the refectory: the keeper's counter by the door, the kitchen hearth on the end wall, long tables
		int rx1 = two ? ix1 : w / 2 - 1;
		int rx0 = ix0;
		if (!two) {
			// one storey: the refectory on the door's side, the dormitory beyond a partition
			// the partition clear of the door (the facade may have moved it onto the middle)
			boolean dormRight = doorCol <= w / 2;
			int px = dormRight ? Math.max(w / 2, doorCol + 2) : Math.min(w / 2, doorCol - 2);
			rx0 = dormRight ? ix0 : px + 1;
			rx1 = dormRight ? px - 1 : ix1;
			Mat gable = d.mat(Role.GABLE);
			try (var s = d.stage(StageKind.WALLS)) {
				for (int z = iz0; z <= iz1; z++) {
					for (int y = base; y < base + heights[0] - 1; y++) {
						d.set(px, y, z, z == (iz0 + iz1) / 2 ? dev.voidmc.hearth.build.draft.Paint.of(Blocks.AIR.defaultBlockState()) : gable.full());
					}
				}
			}
			d.inStage(StageKind.OPENINGS, () -> d.door(px, base, (iz0 + iz1) / 2, d.mat(Role.DOOR).mainOr(dev.voidmc.hearth.build.style.Form.DOOR,
				Blocks.SPRUCE_DOOR), dormRight ? Dirs.RIGHT : Dirs.LEFT, net.minecraft.world.level.block.state.properties.DoorHingeSide.LEFT));
			ground = new Floorplan(rx0, iz0, rx1, iz1, base, heights[0] - 1);
			ground.keep(sh.inside()[0], sh.inside()[1]);
			int dx = dormRight ? px - 1 : px + 1;
			ground.keep(dx, (iz0 + iz1) / 2);
			Floorplan dorm = new Floorplan(dormRight ? px + 1 : ix0, iz0, dormRight ? ix1 : px - 1, iz1, base, heights[0] - 1);
			dorm.keep(dormRight ? px + 1 : px - 1, (iz0 + iz1) / 2);
			beds += dormitory(d, dorm);
		}
		// the counter by the door: along the front wall beside it, the keeper behind
		int cx = sh.inside()[0] + (stairLeft ? 2 : -2);
		if (cx >= rx0 + 1 && cx <= rx1 - 1 && ground.free(cx, iz0 + 1)) {
			Direction run = stairLeft ? Dirs.RIGHT : Dirs.LEFT;
			int n = 2;
			int[][] cells = {{cx, iz0 + 1}, {cx + Dirs.dx(run), iz0 + 1}};
			if (ground.free(cx, iz0) && ground.free(cx + Dirs.dx(run), iz0) && ground.take(cells, new int[]{cx + Dirs.dx(run), iz0})) {
				ground.keep(cx, iz0);
				ground.keep(cx, iz0 + 2);
				Trade.counter(d, cx, base, iz0 + 1, run, n, Dirs.BACK, JobType.INNKEEPER);
			}
		}
		// the kitchen hearth on the end wall of the refectory
		int hx = stairLeft ? rx1 : rx0;
		Direction hearthFace = stairLeft ? Dirs.LEFT : Dirs.RIGHT;
		int hz = (iz0 + iz1) / 2;
		if (ground.free(hx, hz) && ground.inside(hx + Dirs.dx(hearthFace), hz) && ground.take(hx, hz, hearthFace)) {
			Furniture.hearth(d, hx, base, hz, hearthFace, true);
			if (sh.roof() != null) {
				dev.voidmc.hearth.build.kit.Chimneys.stack(d, hx + (stairLeft ? 1 : -1), hz, base + 2, sh.roof(), sh.roof().ridgeY + 1);
			}
			Lighting.flank(d, hx + Dirs.dx(hearthFace), base + 2, hz, hearthFace.getOpposite(), 1);
			// the pot and the stores beside it
			int bz = hz + 1;
			if (ground.free(hx, bz) && ground.take(hx, bz, hearthFace)) {
				d.inStage(StageKind.INTERIOR, () -> d.set(hx, base, bz, Blocks.WATER_CAULDRON.defaultBlockState()
					.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.LEVEL_CAULDRON, 3)));
			}
			int cz = hz - 1;
			if (ground.free(hx, cz) && ground.take(hx, cz, hearthFace)) {
				Furniture.chest(d, hx, base, cz, hearthFace, List.of(new Object[]{"minecraft:bread", 12}, new Object[]{"minecraft:beetroot", 8},
					new Object[]{"minecraft:bowl", 6}));
			}
		}
		// long tables down the room
		for (int z = iz0 + 1; z <= iz1 - 1; z++) {
			for (int x = rx0 + 1; x + 3 <= rx1 - 1; x++) {
				int[][] cells = {{x, z}, {x + 1, z}, {x + 2, z}, {x, z - 1}, {x + 1, z - 1}, {x + 2, z - 1}, {x, z + 1}, {x + 1, z + 1}, {x + 2, z + 1}};
				boolean ok = true;
				for (int[] c : cells) {
					ok &= ground.free(c[0], c[1]);
				}
				int[] end = {x + 3, z};
				if (!ok || !ground.free(end[0], end[1]) || !ground.take(cells, end)) {
					continue;
				}
				Venue.trestle(d, x, base, z, Dirs.RIGHT, 3);
				Venue.candles(d, x + 1, base + 1, z, 2);
				z += 3;
				break;
			}
		}
		ground.blockDrawn(d);
		Rooms.furnish(d, ground.room(), Rooms.Type.HALL, 0, JobType.NONE);
		Lighting.room(d, rx0, iz0, rx1, iz1, base);

		// --- upstairs: the dormitory
		if (two) {
			int fy = base + heights[0];
			int[] up = body.rect(loft ? 0 : 1);
			Floorplan dorm = new Floorplan(up[0] + 1, up[1] + 1, up[2] - 1, up[3] - 1, fy, loft ? 3 : heights[1] - 1);
			int landX = stairX + (stairLeft ? heights[0] : -heights[0]);
			dorm.keep(landX, iz1);
			for (int k = 0; k < heights[0] - 1; k++) {
				dorm.block(stairX + (stairLeft ? k : -k), iz1);
			}
			dorm.blockHoles(d);
			beds += dormitory(d, dorm);
		}

		// --- outside: the board, a bench, the wayside cross, the trough and the hitching rail
		Side front = body.side(0, Dirs.FRONT);
		List<String[]> name = Signboard.name(d, "hostel", Signboard.HOSTELS);
		for (int k : new int[]{2, -2, 3, -3}) {
			int i = front.index(doorCol) + k;
			if (i >= 1 && i <= front.last() - 1 && Signboard.bracket(d, front.x(i), base + 3, front.z(i), Dirs.FRONT, name)) {
				break;
			}
		}
		if (!raised) {
			Bohemia.bench(d, doorCol, zF, base);
			Grounds.approach(d, sh.outside()[0], base, sh.outside()[1]);
			// the wayside cross at the forecourt's corner, the trough and the rail at the other
			int crossX = doorCol < w / 2 ? w - 2 : 1;
			if (zF >= 2 && Venue.standable(d, crossX, base, 0)) {
				Yardwork.wayside(d, crossX, base, 0);
			}
			int railX = doorCol < w / 2 ? 1 : w - 4;
			Yardwork.hitchingRail(d, railX, base, 0, Dirs.RIGHT, 2, Dirs.BACK);
		} else {
			Grounds.approach(d, sh.outside()[0], base, sh.outside()[1]);
		}

		// --- the yard behind: a draw-well, the woodpile
		int yz0 = zM + 1;
		if (dp - 1 - yz0 >= 2 && Grounds.gentle(d, 0, yz0, w - 1, dp - 1, base) && !raised) {
			Groundwork.level(d, 0, yz0, w - 1, dp - 1, base);
			int wx = stairLeft ? w - 3 : 2;
			if (dp - 1 - yz0 >= 3) {
				Yard.well(d, wx, base, Math.min(dp - 2, yz0 + 2), Dirs.FRONT);
			}
			Garden.woodpile(d, stairLeft ? 1 : w - 4, base, yz0, Dirs.RIGHT, 3);
		}
		if (d.habits().snow()) {
			Garden.snowBanks(d, 0, zF, w - 1, zM, base, doorCol);
		}
		Groundwork.clear(d, 0, 0, w - 1, dp - 1, Math.min(maxH - 1, sh.skyline() + 3));
	}

	/** A dormitory: beds head to the wall down both long sides with chests between, lanterns over the aisle. */
	private static int dormitory(Draft d, Floorplan dorm) {
		List<Object[]> kit = List.of(new Object[]{"minecraft:white_wool", 2}, new Object[]{"minecraft:candle", 2});
		int n = 0;
		for (Direction wall : new Direction[]{Dirs.BACK, Dirs.FRONT, Dirs.LEFT, Dirs.RIGHT}) {
			n += Wards.row(d, dorm, wall, 2, false, 12, kit);
		}
		Lighting.room(d, dorm.x0, dorm.z0, dorm.x1, dorm.z1, dorm.y);
		return n;
	}
}
