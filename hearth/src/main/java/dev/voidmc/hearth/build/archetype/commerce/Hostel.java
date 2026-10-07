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
import dev.voidmc.hearth.build.kit.Openings;
import dev.voidmc.hearth.build.kit.Roofs;
import dev.voidmc.hearth.build.kit.Side;
import dev.voidmc.hearth.build.kit.Stairs;
import dev.voidmc.hearth.build.kit.civic.Grounds;
import dev.voidmc.hearth.build.kit.civic.Trade;
import dev.voidmc.hearth.build.kit.civic.Tradition;
import dev.voidmc.hearth.build.kit.homestead.Bohemia;
import dev.voidmc.hearth.build.kit.homestead.Yard;
import dev.voidmc.hearth.build.kit.light.Lighting;
import dev.voidmc.hearth.build.kit.sociallife.Range;
import dev.voidmc.hearth.build.kit.sociallife.Shell;
import dev.voidmc.hearth.build.kit.sociallife.Signboard;
import dev.voidmc.hearth.build.kit.sociallife.Venue;
import dev.voidmc.hearth.build.kit.sociallife.Wards;
import dev.voidmc.hearth.build.kit.sociallife.Yardwork;
import dev.voidmc.hearth.build.kit.trade.Floorplan;
import dev.voidmc.hearth.build.style.Form;
import dev.voidmc.hearth.build.style.Role;
import dev.voidmc.hearth.build.terrain.Groundwork;
import dev.voidmc.hearth.build.kit.landscape.Edges;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;

/**
 * {@code hearth:commerce/hostel}: the house at the town's gate where pilgrims and travellers sleep cheaply. A long
 * plain range of two storeys with its ridge along the road: on the ground floor the refectory, long tables and benches
 * for the travellers' supper, the keeper's counter by the door and the kitchen hearth on the end wall; up the stair the
 * dormitory, beds head to the wall in two rows down its length with a chest between each pair and lanterns over the
 * aisle (on a low plot, one storey: refectory and dormitory side by side behind a partition). Outside, a bench by the
 * door under the house's board, a wayside cross for the pilgrims, a trough and a hitching rail for the travellers'
 * beasts (the stables' hire works from it), and behind, a draw-well and the woodpile.
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

		// --- the plan: the range across the front, its arcade on the street line (on posts: a strip before it), the yard
		// and the stable behind
		int md = raised ? (dp >= 14 ? 8 : 7) : dp >= 14 ? 9 : 8;
		int walk = raised ? 0 : md - 7;
		int zF = raised ? Math.max(2, Math.min(dp - md, 1 + Grounds.stiltBase(d, 0, 1, w - 1, md))) : 0;
		int zM = Math.min(dp - 1, zF + md - 1);
		int base = raised ? Grounds.stiltBase(d, 0, zF, w - 1, zM) : 0;
		// the dormitory a full storey over the refectory; on a low plot all on one floor
		boolean loft = false;
		// (a low upper storey under a raised house's height)
		boolean two = maxH - base >= 8;
		int[] heights = loft ? new int[]{4} : two ? new int[]{4, maxH - base >= 10 ? 4 : 3} : new int[]{5};
		if (walk > 0 && maxH - base >= 12) {
			// a lofty ground storey behind the arcade: arches three high
			heights[0] = 5;
		}
		Body body = new Body(0, zF, w - 1, zM, base, heights, false, false);
		d.body(0, zF, w - 1, zM);
		d.eave(body.plateY());
		int doorCol = d.chance(0.5) ? w / 2 - 1 : w / 2 + 1;
		Grounds.forecourt(d, 0, w - 1, zF - 1, !raised);
		Shell.Built sh = Shell.build(d, body, Direction.Axis.X, Dirs.FRONT, new int[]{doorCol}, 1, 0.7, !loft, t.masonry(), loft, walk);
		if (loft) {
			// the loft's floor of boards on the wall plate
			dev.voidmc.hearth.build.kit.Floors.boards(d, 1, zF + 1, w - 2, zM - 1, body.plateY());
		}
		doorCol = sh.doorCol();
		int ix0 = 1;
		int ix1 = w - 2;
		int iz0 = sh.frontZ();
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

		// the back door out to the yard and the stable, at the end away from the stair
		boolean backDoor = false;
		if (two && !raised && dp - 1 - zM >= 3) {
			Side back = body.side(0, Dirs.BACK);
			for (int bx : stairLeft ? new int[]{ix1 - 2, ix1 - 1, ix1 - 3} : new int[]{ix0 + 2, ix0 + 1, ix0 + 3}) {
				if (Math.abs(bx - (stairLeft ? ix0 : ix1)) <= heights[0] || !Openings.isWallBlock(d, bx, base, zM)
					|| !Openings.isWallBlock(d, bx, base + 1, zM) || !ground.free(bx, iz1)) {
					continue;
				}
				Openings.door(d, back, bx, base, 1, Openings.DoorOpts.BACK);
				int li = back.index(bx) + 1 <= back.last() - 1 ? back.index(bx) + 1 : back.index(bx) - 1;
				Lighting.doorLamp(d, back.outX(li, 1), base + 2, back.outZ(li, 1), back.out().getOpposite());
				ground.keep(bx, iz1);
				backDoor = true;
				break;
			}
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
		// (the back half of the room: the keeper's counter stands in the front row)
		int hz = (iz0 + iz1 + 1) / 2;
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
		boolean tabled = false;
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
				tabled = true;
				z += 3;
				break;
			}
		}
		if (!tabled && Range.tables(d, ground, rx0, rx1, 2, 1) == 0) {
			// a narrow refectory behind the arcade: a shorter table, else a bench against a wall
			Range.wallBench(d, ground);
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

		// --- outside: the board over the arcade (on posts: the hitching rail in the strip before the house)
		Side front = body.side(0, Dirs.FRONT);
		List<String[]> name = Signboard.name(d, "hostel", Signboard.HOSTELS);
		boolean hung = false;
		for (int y : new int[]{base + 3, base + 5}) {
			for (int k : new int[]{2, -2, 3, -3}) {
				int i = front.index(doorCol) + k;
				if (!hung && i >= 1 && i <= front.last() - 1 && Signboard.bracket(d, front.x(i), y, front.z(i), Dirs.FRONT, name)) {
					hung = true;
				}
			}
		}
		Grounds.approach(d, sh.outside()[0], base, sh.outside()[1]);
		if (raised && zF >= 2) {
			int railX = doorCol < w / 2 ? 1 : w - 4;
			Yardwork.hitchingRail(d, railX, base, 0, Dirs.RIGHT, 2, Dirs.BACK);
		}

		// --- the yard behind: the stable across its back and the woodpile before it; else a draw-well, the woodpile and
		// a hitching rail
		int yz0 = zM + 1;
		if (dp - 1 - yz0 >= 2 && !raised && (backDoor || Grounds.gentle(d, 0, yz0, w - 1, dp - 1, base))) {
			Groundwork.level(d, 0, yz0, w - 1, dp - 1, base, true);
			int sd = Math.min(3, dp - yz0 - 1);
			boolean stable = backDoor && sd >= 2 && stable(d, 0, w - 1, dp - sd, dp - 1, base);
			int strip = stable ? dp - sd - yz0 : dp - yz0;
			if (!stable && strip >= 4) {
				Yard.well(d, stairLeft ? w - 3 : 2, base, Math.min(dp - 2, yz0 + 2), Dirs.FRONT);
			}
			if (!stable || strip >= 2) {
				Garden.woodpile(d, stairLeft ? 1 : w - 4, base, yz0, Dirs.RIGHT, 3);
			}
			if (!stable) {
				Yardwork.hitchingRail(d, stairLeft ? 1 : w - 4, base, dp - 1, Dirs.RIGHT, 2, Dirs.FRONT);
			}
		}
		if (d.habits().snow()) {
			Garden.snowBanks(d, 0, zF, w - 1, zM, base, doorCol);
		}
		Groundwork.clear(d, 0, 0, w - 1, dp - 1, Math.min(maxH - 1, sh.skyline() + 3));
		// rails round the dormitory's stairwell and on the balcony
		Edges.guard(d);
	}

	/**
	 * The stable across the back of the yard over [x0..x1] x [z0..z1]: a lean-to against a back wall (row z1) of the
	 * house's walling on a plinth course, side walls at x0 and x1, open to the yard on posts in row z0 under a beam;
	 * stalls two wide parted by rails, each with its bale of hay (and, three rows deep, a trough of water) at the back and
	 * the beast's stand before it (a HITCHING_POST, where the stables' hire works from); a lantern under the roof. Answers
	 * whether it was built.
	 */
	private static boolean stable(Draft d, int x0, int x1, int z0, int z1, int y) {
		int depth = z1 - z0 + 1;
		if (depth < 2 || x1 - x0 < 4 || y + 6 >= d.maxHeight()) {
			return false;
		}
		Mat frame = d.mat(Role.FRAME);
		Mat beam = d.mat(Role.BEAM);
		Mat wall = d.mat(Role.WALL);
		Mat plinth = d.mat(Role.PLINTH);
		Mat fence = d.mat(Role.FENCE).has(Form.FENCE) ? d.mat(Role.FENCE) : frame;
		Groundwork.footings(d, x0, z0, x1, z1, y - 1);
		// the roof: one slope from the back wall's top down over the posts and a block beyond
		int ez = Math.max(0, z0 - 1);
		Roofs.Field f = Roofs.shed(x0, ez, x1, z1, Dirs.BACK, Roofs.Profile.straight(0.5));
		Roofs.Roof roof = Roofs.raster(d, f, y + 3, x0, ez, x1, z1, d.mat(Role.ROOF), d.mat(Role.ROOF_TRIM), d.habits().roof().ridge());
		// the stalls' partitions: every third column
		List<Integer> parts = new ArrayList<>();
		for (int x = x0 + 3; x <= x1 - 3; x += 3) {
			parts.add(x);
		}
		try (var s = d.stage(StageKind.WALLS)) {
			for (int x = x0; x <= x1; x++) {
				for (int yy = y; yy < Math.max(y + 3, roof.bottomY(x, z1)); yy++) {
					d.set(x, yy, z1, yy == y ? plinth.full() : wall.full());
				}
			}
			for (int x : new int[]{x0, x1}) {
				for (int z = z0 + 1; z < z1; z++) {
					for (int yy = y; yy < Math.max(y + 3, roof.bottomY(x, z)); yy++) {
						d.set(x, yy, z, yy == y ? plinth.full() : wall.full());
					}
				}
			}
		}
		// the posts at the ends and the partitions, the beam over the open front
		try (var s = d.stage(StageKind.FRAME)) {
			for (int x = x0; x <= x1; x++) {
				boolean post = x == x0 || x == x1 || parts.contains(x);
				int top = Math.max(y + 3, roof.bottomY(x, z0));
				if (post) {
					for (int yy = y; yy < top; yy++) {
						d.set(x, yy, z0, frame.log(Direction.Axis.Y));
					}
				} else {
					for (int yy = y + 2; yy < top; yy++) {
						d.set(x, yy, z0, beam.log(Direction.Axis.X));
					}
				}
			}
		}
		try (var s = d.stage(StageKind.INTERIOR)) {
			for (int px : parts) {
				for (int z = z0 + 1; z < z1; z++) {
					d.set(px, y, z, fence.fence());
				}
			}
		}
		// in each stall: the hay and the water at the back, the stand before them
		int stall = 0;
		for (int sx = x0 + 1; sx + 1 <= x1 - 1; sx += 3) {
			int back = z1 - 1;
			boolean flip = (stall & 1) == 1;
			int hx = flip ? sx + 1 : sx;
			int wx = flip ? sx : sx + 1;
			try (var s = d.stage(StageKind.INTERIOR)) {
				d.set(hx, y, back, Blocks.HAY_BLOCK.defaultBlockState());
				if (depth >= 3) {
					d.set(wx, y, back, Blocks.WATER_CAULDRON.defaultBlockState().setValue(BlockStateProperties.LEVEL_CAULDRON, 3));
				}
			}
			int standX = depth >= 3 ? hx : wx;
			int standZ = depth >= 3 ? back - 1 : back;
			d.poi(PoiType.HITCHING_POST, standX, y, standZ, Dirs.BACK);
			stall++;
		}
		Lighting.room(d, x0 + 1, z0, x1 - 1, z1 - 1, y);
		return stall > 0;
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
