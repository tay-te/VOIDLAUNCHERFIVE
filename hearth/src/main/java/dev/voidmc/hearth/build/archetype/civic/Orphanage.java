package dev.voidmc.hearth.build.archetype.civic;

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
import dev.voidmc.hearth.build.draft.States;
import dev.voidmc.hearth.build.draft.Mat;
import dev.voidmc.hearth.build.kit.Body;
import dev.voidmc.hearth.build.kit.Furniture;
import dev.voidmc.hearth.build.kit.Openings;
import dev.voidmc.hearth.build.kit.Stairs;
import dev.voidmc.hearth.build.kit.Garden;
import dev.voidmc.hearth.build.kit.Rooms;
import dev.voidmc.hearth.build.kit.Side;
import dev.voidmc.hearth.build.kit.civic.Pueblo;
import dev.voidmc.hearth.build.kit.civic.Grounds;
import dev.voidmc.hearth.build.kit.civic.Halls;
import dev.voidmc.hearth.build.kit.civic.Tradition;
import dev.voidmc.hearth.build.kit.homestead.Bohemia;
import dev.voidmc.hearth.build.kit.landscape.Trees;
import dev.voidmc.hearth.build.kit.light.Lighting;
import dev.voidmc.hearth.build.kit.sociallife.Range;
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
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * {@code hearth:civic/orphanage}: the foundlings' home, kept by the town or a pious house. A tall narrow house standing
 * gable-on to the street down one side of its plot, and beside it, behind a wall with its own gate, the yard where the
 * children play: a swing on its frame, a shade tree, a little garden they tend, a lamp. In the street gable beside the
 * door the foundling wheel (a barrel turned in the wall, where a child may be left unseen) and the house's board. Below,
 * the children's hall, schoolroom and refectory in one: the long tables where they eat and learn their letters, the
 * matron's lectern at the head of the room facing them, the kitchen fire on the back wall, a chest of their few toys and
 * clothes by the door, a door out to the yard. Up the stair under the steep roof the long dormitory, small beds packed
 * side by side down both walls, each reached from its foot. On posts (no yard can be levelled under a raised house) a
 * plain range across the front of the plot, the yard behind it.
 *
 * <p>Housing (the planner counts its beds, the orphans' home). POIs: BEDs (8 or more), the LECTERN, TABLE, SEATs, the
 * HEARTH, STORAGE, the DOOR. Params: {@code variant} (int).
 */
public final class Orphanage implements Archetype {
	public static final ArchetypeInfo INFO = new ArchetypeInfo(Hearth.id("civic/orphanage"), ArchetypeCategory.CIVIC, Set.of(), 8, 11, 15, 11, 15, 14,
		16, SettlementTier.TOWN, Set.of(), Set.of(), List.of(), 1, 1.0);

	@Override
	public ArchetypeInfo info() {
		return INFO;
	}

	@Override
	public void draw(Draft d) {
		if (!Tradition.of(d).raised()) {
			house(d);
			return;
		}
		int w = d.frontage();
		int dp = d.depth();
		Tradition t = Tradition.of(d);
		boolean raised = t.raised();
		Range.Built r = Range.build(d, dp >= 13 ? 8 : 7, t.masonry(), 0.75);
		int base = r.base();
		Floorplan ground = r.ground();
		boolean stairLeft = r.stairLeft();
		int doorX = r.shell().doorCol();

		// --- the hall: the fire at the end away from the stair; the matron's lectern at the stair's end, facing the tables
		int hx = stairLeft ? r.ix1() : r.ix0();
		Direction face = stairLeft ? Dirs.LEFT : Dirs.RIGHT;
		Range.hearth(d, r, ground, hx, face, List.of(new Object[]{"minecraft:bread", 10}, new Object[]{"minecraft:milk_bucket", 1},
			new Object[]{"minecraft:bowl", 10}));
		int lx = stairLeft ? r.ix0() : r.ix1();
		int lz = r.iz0() + 1;
		Direction toTables = stairLeft ? Dirs.RIGHT : Dirs.LEFT;
		if (ground.free(lx, lz) && ground.inside(lx + Dirs.dx(toTables), lz) && ground.take(lx, lz, toTables)) {
			d.inStage(StageKind.INTERIOR, () -> d.set(lx, base, lz, States.facing(Blocks.LECTERN, toTables)));
			d.poi(PoiType.LECTERN, lx + Dirs.dx(toTables), base, lz, toTables.getOpposite());
		}
		// the chest of toys and clothes by the door
		int[] in = r.shell().inside();
		for (int k : new int[]{1, -1, 2, -2}) {
			int bx = in[0] + k;
			if (ground.free(bx, r.iz0()) && ground.take(bx, r.iz0(), Dirs.BACK)) {
				Furniture.chest(d, bx, base, r.iz0(), Dirs.BACK, List.of(new Object[]{"minecraft:white_wool", 4}, new Object[]{"minecraft:stick", 4},
					new Object[]{"minecraft:book", 2}));
				break;
			}
		}
		if (Range.tables(d, ground, r.ix0(), r.ix1(), w >= 13 ? 4 : 3, 2) == 0) {
			Range.tables(d, ground, r.ix0(), r.ix1(), 2, 1);
		}
		Range.wallBench(d, ground);
		ground.blockDrawn(d);
		Rooms.furnish(d, dev.voidmc.hearth.build.kit.civic.Halls.keepOffWells(d, ground.room()), Rooms.Type.HALL, 0, JobType.NONE);
		Lighting.room(d, r.ix0(), r.iz0(), r.ix1(), r.iz1(), base);

		// --- the dormitory: small beds side by side down every wall, each reached from its foot
		Floorplan up = r.upper();
		for (Direction wall : new Direction[]{Dirs.FRONT, Dirs.BACK, Dirs.LEFT, Dirs.RIGHT}) {
			Wards.row(d, up, wall, 1, false, 12, null);
		}
		Lighting.room(d, up.x0, up.z0, up.x1, up.z1, up.y);
		// (a bed the eaves, its neighbours or a floor light's rug box in is no bed)
		Halls.dropBlockedBeds(d, up.x0, up.z0, up.x1, up.z1, up.y);

		// --- outside: the board, a bench by the door
		Side front = r.body().side(0, Dirs.FRONT);
		int di = front.index(doorX);
		List<String[]> name = Signboard.name(d, "orphanage", Signboard.ORPHANAGES);
		for (int k : new int[]{2, -2, 3, -3}) {
			int i = di + k;
			if (i >= 1 && i <= front.last() - 1 && Signboard.bracket(d, front.x(i), base + 3, front.z(i), Dirs.FRONT, name)) {
				break;
			}
		}
		Grounds.approach(d, r.shell().outside()[0], base, r.shell().outside()[1]);
		if (!raised) {
			Bohemia.bench(d, doorX, r.zF(), base);
		}

		// --- the yard behind: a swing, a shade tree, the children's garden
		int yz0 = r.zM() + 1;
		if (dp - 1 - yz0 >= 3 && !raised && Grounds.gentle(d, 0, yz0, w - 1, dp - 1, base)) {
			Groundwork.level(d, 0, yz0, w - 1, dp - 1, base);
			int sz = Math.min(dp - 2, yz0 + 2);
			int sx = stairLeft ? 3 : w - 4;
			Yardwork.swing(d, sx, base, sz, Dirs.RIGHT);
			int tx = stairLeft ? w - 3 : 2;
			int th = Math.min(7, d.maxHeight() - 1 - base);
			if (th >= 4 && dp - 1 - yz0 >= 4) {
				Trees.tree(d, tx, base, Math.min(dp - 2, yz0 + 2), th, Trees.kind(d), tx - 2, yz0, tx + 2, dp - 1);
			}
			int gx0 = w / 2 - 1;
			if (dp - 1 - yz0 >= 4) {
				Garden.kitchenRows(d, gx0, dp - 2, gx0 + 2, dp - 2, base, Dirs.RIGHT);
			}
		}
		if (d.habits().snow()) {
			Garden.snowBanks(d, 0, r.zF(), w - 1, r.zM(), base, doorX);
		}
		// the mesa's pueblo: a portal on posts along the front, a stepped room over the flat roof, a ladder up to its terrace
		if (Pueblo.is(d)) {
			Pueblo.portalFront(d);
		}
		int pueblo = Pueblo.is(d) ? Pueblo.crown(d) : Integer.MIN_VALUE;
		dev.voidmc.hearth.build.kit.civic.Halls.finish(d);
		Groundwork.clear(d, 0, 0, w - 1, dp - 1, Math.max(Math.min(d.maxHeight() - 1, r.shell().skyline() + 3), pueblo + 2));
	}

	/**
	 * The house gable-on to the street down one side of the plot with the walled yard beside it: the hall below (door in
	 * the street gable, the stair up the long wall away from the yard, the fire and the matron's lectern at the back, the
	 * tables down the middle, a door to the yard), the dormitory above.
	 */
	private static void house(Draft d) {
		int w = d.frontage();
		int dp = d.depth();
		Tradition t = Tradition.of(d);
		int hw = w >= 13 ? 8 : 7;
		boolean left = d.chance(0.5);
		int hx0 = left ? 0 : w - hw;
		int hx1 = hx0 + hw - 1;
		int zF = 1;
		int zM = dp - 1;
		int base = 0;
		int[] heights = d.maxHeight() - base >= 10 ? new int[]{4, 4} : new int[]{4, 3};
		Body body = new Body(hx0, zF, hx1, zM, base, heights, false, false);
		d.body(hx0, zF, hx1, zM);
		d.eave(body.plateY());
		// the yard levelled beside it (the house's footings take what falls under it)
		int yx0 = left ? hx1 + 1 : 0;
		int yx1 = left ? w - 1 : hx0 - 1;
		Groundwork.level(d, yx0, 0, yx1, dp - 1, base, true);
		Grounds.forecourt(d, hx0, hx1, zF - 1, true);
		int doorX = (hx0 + hx1) / 2 + (left ? 0 : 1);
		Shell.Built sh = Shell.build(d, body, Direction.Axis.Z, Dirs.FRONT, new int[]{doorX}, 1, 0.75, t.masonry(), t.masonry(), true, 0);
		doorX = sh.doorCol();
		int ix0 = hx0 + 1;
		int ix1 = hx1 - 1;
		int iz0 = zF + 1;
		int iz1 = zM - 1;
		Direction toYard = left ? Dirs.RIGHT : Dirs.LEFT;

		// the stair up the long wall away from the yard, from the back climbing toward the street
		int sx = left ? ix0 : ix1;
		int h0 = heights[0];
		int sz0 = iz1;
		Stairs.straight(d, sx, base, sz0, Dirs.FRONT, h0, 1);
		int fy = base + h0;
		try (var s = d.stage(StageKind.FLOORS)) {
			for (int k = 0; k < h0 - 1; k++) {
				d.set(sx, fy - 1, sz0 - k, Blocks.AIR.defaultBlockState());
			}
		}
		Floorplan ground = new Floorplan(ix0, iz0, ix1, iz1, base, h0 - 1);
		ground.keep(sh.inside()[0], sh.inside()[1]);
		for (int k = 0; k < h0; k++) {
			ground.block(sx, sz0 - k);
		}
		ground.keep(sx, sz0 - h0);
		ground.keep(sx + Dirs.dx(toYard), sz0);

		// the door out to the yard, in the long wall about the middle
		Side yardWall = body.side(0, toYard);
		int wx = left ? hx1 : hx0;
		boolean yardDoor = false;
		for (int z : new int[]{(iz0 + iz1) / 2, (iz0 + iz1) / 2 + 1, (iz0 + iz1) / 2 - 1, iz0 + 1}) {
			if (Openings.isWallBlock(d, wx, base, z) && Openings.isWallBlock(d, wx, base + 1, z) && ground.free(wx - Dirs.dx(toYard), z)) {
				Openings.door(d, yardWall, z, base, 1, Openings.DoorOpts.BACK);
				int i = yardWall.index(z);
				int li = i + 1 <= yardWall.last() - 1 ? i + 1 : i - 1;
				Lighting.doorLamp(d, yardWall.outX(li, 1), base + 2, yardWall.outZ(li, 1), toYard.getOpposite());
				ground.keep(wx - Dirs.dx(toYard), z);
				yardDoor = true;
				break;
			}
		}

		// --- the hall: the fire on the back wall, the matron's lectern at the head of the room facing the tables
		int hxF = left ? ix1 - 1 : ix0 + 1;
		if (ground.free(hxF, iz1) && ground.inside(hxF, iz1 - 1) && ground.take(hxF, iz1, Dirs.FRONT)) {
			Furniture.hearth(d, hxF, base, iz1, Dirs.FRONT, true);
			if (sh.roof() != null) {
				dev.voidmc.hearth.build.kit.Chimneys.stack(d, hxF, zM, base + 2, sh.roof(), sh.roof().ridgeY + 1);
			}
			Lighting.flank(d, hxF, base + 2, iz1 - 1, Dirs.BACK, 1);
		}
		int lz = iz1 - 1;
		int lx = left ? ix1 : ix0;
		if (ground.free(lx, lz) && ground.inside(lx, lz - 1) && ground.take(lx, lz, Dirs.FRONT)) {
			d.inStage(StageKind.INTERIOR, () -> d.set(lx, base, lz, States.facing(Blocks.LECTERN, Dirs.FRONT)));
			d.poi(PoiType.LECTERN, lx, base, lz - 1, Dirs.BACK);
		}
		int[] in = sh.inside();
		for (int k : new int[]{1, -1, 2, -2}) {
			int bx = in[0] + k;
			if (ground.free(bx, iz0) && ground.take(bx, iz0, Dirs.BACK)) {
				Furniture.chest(d, bx, base, iz0, Dirs.BACK, List.of(new Object[]{"minecraft:white_wool", 4}, new Object[]{"minecraft:stick", 4},
					new Object[]{"minecraft:book", 2}, new Object[]{"minecraft:bread", 6}));
				break;
			}
		}
		if (Range.tables(d, ground, ix0, ix1, hw >= 8 ? 3 : 2, 2) == 0) {
			Range.tables(d, ground, ix0 - 1, ix1 + 1, 2, 1);
		}
		Range.wallBench(d, ground);
		ground.blockDrawn(d);
		Rooms.furnish(d, dev.voidmc.hearth.build.kit.civic.Halls.keepOffWells(d, ground.room()), Rooms.Type.HALL, 0, JobType.NONE);
		Lighting.room(d, ix0, iz0, ix1, iz1, base);

		// --- the dormitory under the roof: small beds side by side down both long walls, each reached from its foot
		int[] up = body.rect(1);
		Floorplan dorm = new Floorplan(up[0] + 1, up[1] + 1, up[2] - 1, up[3] - 1, fy, heights[1] - 1);
		dorm.keep(sx, sz0 - h0);
		for (int k = 0; k < h0 - 1; k++) {
			dorm.block(sx, sz0 - k);
		}
		dorm.blockHoles(d);
		for (Direction wall : new Direction[]{toYard, toYard.getOpposite(), Dirs.BACK, Dirs.FRONT}) {
			Wards.row(d, dorm, wall, 1, false, 12, null);
		}
		Lighting.room(d, dorm.x0, dorm.z0, dorm.x1, dorm.z1, dorm.y);
		// (a bed the stair's head, its neighbours or a floor light's rug box in is no bed)
		Halls.dropBlockedBeds(d, dorm.x0, dorm.z0, dorm.x1, dorm.z1, dorm.y);

		// --- the street gable: the foundling wheel beside the door, the board, a bench
		Side front = body.side(0, Dirs.FRONT);
		int di = front.index(doorX);
		for (int k : new int[]{2, -2}) {
			int i = di + k;
			if (i >= 1 && i <= front.last() - 1 && Openings.isWallBlock(d, front.x(i), base + 1, front.z(i))) {
				int fx = front.x(i);
				int fz = front.z(i);
				try (var s = d.stage(StageKind.OPENINGS)) {
					d.set(fx, base + 1, fz, States.facing(Blocks.BARREL, Dirs.FRONT));
				}
				break;
			}
		}
		List<String[]> name = Signboard.name(d, "orphanage", Signboard.ORPHANAGES);
		for (int k : new int[]{-2, 2, 3, -3}) {
			int i = di + k;
			if (i >= 1 && i <= front.last() - 1 && Signboard.bracket(d, front.x(i), base + 3, front.z(i), Dirs.FRONT, name)) {
				break;
			}
		}
		Grounds.approach(d, sh.outside()[0], base, sh.outside()[1]);

		// --- the yard beside the house: its wall to the street with a gate where the street meets it, a fence round the
		// rest; the swing, the shade tree, the children's garden, a lamp
		int yw = yx1 - yx0 + 1;
		int gx = Integer.MIN_VALUE;
		for (int x = yx0 + 1; x <= yx1 - 1; x++) {
			if (Math.abs(d.site().ground(x, -1) - base) <= 1 && !d.site().isWater(x, -1) && (gx == Integer.MIN_VALUE || Math.abs(x - (yx0 + yx1) / 2) < Math
				.abs(gx - (yx0 + yx1) / 2))) {
				gx = x;
			}
		}
		if (!yardDoor && gx == Integer.MIN_VALUE) {
			// no way into the yard: leave its wall open at the house
			gx = left ? yx0 : yx1;
		}
		Mat stone = d.mat(Role.PLINTH);
		Mat cap = d.mat(Role.WALL_ACCENT);
		try (var s = d.stage(StageKind.WALLS)) {
			for (int x = yx0; x <= yx1; x++) {
				if (x == gx || !d.isEmpty(x, base, 0)) {
					continue;
				}
				for (int y = Math.min(base, d.site().ground(x, 0)) - 1; y <= base; y++) {
					d.set(x, y, 0, stone.full());
				}
				d.set(x, base + 1, 0, cap.has(dev.voidmc.hearth.build.style.Form.SLAB) ? cap.slab(SlabType.BOTTOM) : stone.full());
			}
		}
		int ex = left ? w - 1 : 0;
		if (yw >= 3) {
			Garden.fence(d, Garden.line(ex, 1, ex, dp - 1), -1, Dirs.FRONT);
			Garden.fence(d, Garden.line(yx0, dp - 1, yx1, dp - 1), -1, Dirs.FRONT);
		}
		int mx = (yx0 + yx1) / 2;
		if (yw >= 5 && dp >= 11) {
			Yardwork.swing(d, mx, base, 3, Dirs.BACK);
		}
		int th = Math.min(7, d.maxHeight() - 1 - base);
		int tx = left ? yx1 - 1 : yx0 + 1;
		int tz = dp - 3;
		if (th >= 4 && yw >= 3) {
			Trees.tree(d, tx, base, tz, th, Trees.kind(d), Math.max(yx0, tx - 2), tz - 2, Math.min(yx1, tx + 2), dp - 1);
		}
		if (yw >= 4 && dp >= 12) {
			int gx0 = left ? yx0 + 1 : yx0 + 2;
			Garden.kitchenRows(d, gx0, dp - 6, gx0 + 1, dp - 5, base, Dirs.BACK);
		}
		int px = left ? yx0 + 1 : yx1 - 1;
		if (yw >= 3 && Venue.standable(d, px, base, 1)) {
			Lighting.post(d, px, base, 1);
		}
		if (d.habits().snow()) {
			Garden.snowBanks(d, hx0, zF, hx1, zM, base, doorX);
		}
		// the mesa's pueblo: a portal on posts along the front, a stepped room over the flat roof, a ladder up to its terrace
		if (Pueblo.is(d)) {
			Pueblo.portalFront(d);
		}
		int pueblo = Pueblo.is(d) ? Pueblo.crown(d) : Integer.MIN_VALUE;
		dev.voidmc.hearth.build.kit.civic.Halls.finish(d);
		Groundwork.clear(d, 0, 0, w - 1, dp - 1, Math.max(Math.min(d.maxHeight() - 1, sh.skyline() + 3), pueblo + 2));
	}
}
