package dev.voidmc.hearth.build.archetype.landmark;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.ArchetypeCategory;
import dev.voidmc.hearth.api.ArchetypeInfo;
import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.PoiType;
import dev.voidmc.hearth.api.SettlementTier;
import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.Archetype;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Mat;
import dev.voidmc.hearth.build.draft.Paint;
import dev.voidmc.hearth.build.draft.States;
import dev.voidmc.hearth.build.kit.Furniture;
import dev.voidmc.hearth.build.kit.Garden;
import dev.voidmc.hearth.build.kit.Openings;
import dev.voidmc.hearth.build.kit.Porches;
import dev.voidmc.hearth.build.kit.Roofs;
import dev.voidmc.hearth.build.kit.Side;
import dev.voidmc.hearth.build.kit.landmark.Details;
import dev.voidmc.hearth.build.kit.landmark.Grounds;
import dev.voidmc.hearth.build.kit.landmark.Stone;
import dev.voidmc.hearth.build.style.Form;
import dev.voidmc.hearth.build.style.Habits;
import dev.voidmc.hearth.build.style.Habits.WallKind;
import dev.voidmc.hearth.build.style.Role;
import dev.voidmc.hearth.build.terrain.Groundwork;
import java.util.List;
import java.util.Set;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * {@code hearth:landmark/great_hall}: the mead hall of a northern town. A long hall under a vast roof whose gable faces
 * the street: in the north and on the tundra walls of logs notched at the corners with raking braces leaning on them, the
 * gables boarded, their barge boards crossing over the apex and carved into dragon heads, a porch on carved posts before
 * the doors; on the moor rubble walls under slate with crow-stepped gables and buttresses; in the mountains whitewashed
 * walls under a broad low roof with a balcony across the gable. Inside: two rows of posts carry tie beams under the
 * roof; a long hearth burns down the middle between long tables and benches; at the far end the high seat stands on a
 * dais under banners; mead barrels by the door, lanterns on chains from the beams. Smoke vents ride the ridge.
 *
 * <p>Params (all optional): {@code variant}.
 */
public final class GreatHall implements Archetype {
	public static final ArchetypeInfo INFO = new ArchetypeInfo(Hearth.id("landmark/great_hall"), ArchetypeCategory.LANDMARK, Set.of(), 0,
		15, 23, 21, 31, 14, 22, SettlementTier.TOWN, Set.of(Culture.NORTHERN, Culture.TUNDRA, Culture.MOORLAND, Culture.ALPINE),
		Set.of(), List.of(), 1, 1.0);

	@Override
	public ArchetypeInfo info() {
		return INFO;
	}

	@Override
	public void draw(Draft d) {
		int w = d.frontage();
		int dp = d.depth();
		int top = d.maxHeight() - 1;
		Habits h = d.habits();
		WallKind kind = h.walls().kind();
		boolean logs = kind == WallKind.LOG;
		boolean stone = kind == WallKind.STONE || kind == WallKind.BRICK;
		boolean chalet = h.roof().pitch() < 0.75;
		// the hall: as wide as the plot allows with room for braces and woodpiles, odd so the hearth has a middle
		int hw = Math.min(w - 4, 17);
		hw -= (hw & 1) == 0 ? 1 : 0;
		int hx0 = (w - hw) / 2;
		int hx1 = hx0 + hw - 1;
		int xc = (hx0 + hx1) / 2;
		int front = dp >= 25 ? 4 : 3;
		int hz0 = front;
		int hz1 = dp - 2;
		int wallH = stone ? 5 : 4;
		d.body(hx0, hz0, hx1, hz1);
		d.eave(wallH);
		Mat frame = d.mat(Role.FRAME);
		Mat beam = d.mat(Role.BEAM);
		Mat wall = d.mat(Role.WALL);
		Stone dress = Stone.dressing(d);
		Stone found = Stone.footing(d);
		Paint glass = d.mat(Role.GLASS).pane();

		// ground, footings, floor
		Grounds.podium(d, 0, 0, w - 1, dp - 1, 0, false);
		Grounds.footings(d, hx0, hz0, hx1, hz1, -1);
		try (var s = d.stage(StageKind.FLOORS)) {
			for (int z = hz0 + 1; z < hz1; z++) {
				for (int x = hx0 + 1; x < hx1; x++) {
					d.set(x, -1, z, d.mat(Role.FLOOR).full());
				}
			}
		}
		// the walls
		for (Side s : Side.of(hx0, hz0, hx1, hz1)) {
			for (int i = 0; i < s.length(); i++) {
				int x = s.x(i);
				int z = s.z(i);
				boolean corner = s.isCorner(i);
				boolean post = !stone && (corner || i % 4 == 0);
				for (int y = 0; y <= wallH; y++) {
					Paint p;
					StageKind st = StageKind.WALLS;
					if (y == 0 && (stone || logs)) {
						p = found.full();
					} else if (stone) {
						p = corner || (i == 1 || i == s.length() - 2) && (y & 1) == 0 ? dress.full() : wall.full();
					} else if (post) {
						p = frame.log(Direction.Axis.Y);
						st = StageKind.FRAME;
					} else if (y == wallH) {
						p = beam.log(s.axis());
						st = StageKind.FRAME;
					} else if (logs) {
						p = wall.log(s.axis());
					} else {
						p = y <= 1 ? d.mat(Role.PLINTH).full() : wall.full();
					}
					try (var sc = d.stage(st)) {
						d.set(x, y, z, p);
					}
				}
				// log corners run past on alternate courses
				if (logs && corner) {
					int ox = x + (i == 0 ? -Dirs.dx(s.along()) : Dirs.dx(s.along()));
					int oz = z + (i == 0 ? -Dirs.dz(s.along()) : Dirs.dz(s.along()));
					for (int y = 1; y < wallH; y += 2) {
						if (d.allowed(ox, y, oz) && d.isEmpty(ox, y, oz)) {
							d.set(ox, y, oz, frame.log(s.axis()));
						}
					}
				}
			}
		}
		d.carve(hx0 + 1, 0, hz0 + 1, hx1 - 1, wallH - 1, hz1 - 1);
		// small high windows between the posts along the sides
		for (Side s : Side.of(hx0, hz0, hx1, hz1)) {
			if (s.out() == Dirs.FRONT || s.out() == Dirs.BACK) {
				continue;
			}
			for (int i = 2; i < s.length() - 2; i += 4) {
				Openings.window(d, s, s.column(i), 2, 1, Math.max(1, wallH - 3), Openings.WindowStyle.DRESSED,
					new Openings.WindowOpts(!stone, false, true, true));
			}
		}

		// the roof: steep in the north, broad and low in the mountains; gables filled in the style's manner
		double pitch = chalet ? 0.6 : stone ? 1.0 : 1.25;
		int eaves = chalet ? 2 : 1;
		Roofs.Opts o = Roofs.Opts.of(d).withPitch(pitch).withEaves(eaves, chalet ? 2 : 1);
		double half = hw / 2.0 + eaves;
		if (wallH + 1 + pitch * half > top - 3) {
			o = o.withPitch(Math.max(0.5, (top - 4 - wallH) / half));
		}
		Roofs.Built rb = Roofs.build(d, hx0, hz0, hx1, hz1, wallH, Habits.RoofForm.GABLE, Direction.Axis.Z, o, false, false);
		Roofs.Roof roof = rb.roof();
		int ridge = roof.ridgeY;
		// the gables: boards with a light high up, or crow steps on stone
		for (int z : new int[]{hz0, hz1}) {
			int gy = wallH + 2;
			if (roof.covers(xc, z) && roof.bottomY(xc, z) - 1 > gy) {
				try (var s = d.stage(StageKind.OPENINGS)) {
					d.set(xc, gy, z, glass);
				}
			}
		}
		if (stone) {
			crowSteps(d, roof, hx0, hx1, hz0, dress);
			crowSteps(d, roof, hx0, hx1, hz1, dress);
		} else if (!chalet) {
			dragons(d, roof, hx0, hx1, xc, rb.ez0(), d.mat(Role.ROOF_TRIM), frame);
			dragons(d, roof, hx0, hx1, xc, rb.ez1(), d.mat(Role.ROOF_TRIM), frame);
		}
		// smoke vents riding the ridge over the hearth
		int vz0 = hz0 + (hz1 - hz0) / 3;
		int vz1 = hz1 - (hz1 - hz0) / 3;
		for (int vz : new int[]{vz0, vz1}) {
			int sy = roof.surfaceY(xc, vz);
			if (sy != Integer.MIN_VALUE && sy + 2 <= top) {
				try (var s = d.stage(StageKind.ROOF)) {
					d.set(xc, sy + 1, vz, d.mat(Role.ROOF).slab(SlabType.BOTTOM));
					d.set(xc - 1, sy, vz, d.mat(Role.ROOF).full());
					d.set(xc + 1, sy, vz, d.mat(Role.ROOF).full());
				}
			}
		}

		// the porch before the doors, or a balcony across a chalet's gable
		Side frontWall = Side.of(hx0, hz0, hx1, hz1, Dirs.FRONT);
		try (var s = d.stage(StageKind.OPENINGS)) {
			Details.arch(d, frontWall, frontWall.index(xc), 1, 0, 3, stone ? dress : Stone.of(d, Role.BEAM), glass);
		}
		Openings.door(d, frontWall, xc, 0, 1, Openings.DoorOpts.MAIN);
		if (front >= 3) {
			Porches.porch(d, frontWall, xc - 2, xc + 2, 2, 0, 3, xc);
		}
		if (chalet) {
			Side up = frontWall;
			Porches.balcony(d, up, hx0 + 1, hx1 - 1, wallH + 1);
		}
		// raking braces leaning on the long walls (stone halls: buttresses)
		for (Side s : Side.of(hx0, hz0, hx1, hz1)) {
			if (s.out() != Dirs.LEFT && s.out() != Dirs.RIGHT) {
				continue;
			}
			for (int i = 3; i < s.length() - 2; i += 4) {
				int ox = s.outX(i, 1);
				int oz = s.outZ(i, 1);
				if (!d.allowed(ox, 0, oz)) {
					continue;
				}
				try (var st = d.stage(StageKind.FRAME)) {
					int g = d.site().ground(ox, oz);
					for (int y = Math.min(-1, g - 1); y < (stone ? wallH - 1 : 2); y++) {
						d.set(ox, y, oz, stone ? dress.full() : frame.log(Direction.Axis.Y));
					}
					int capY = stone ? wallH - 1 : 2;
					d.set(ox, capY, oz, stone ? dress.stairsOrSlab(s.out().getOpposite(), Half.BOTTOM) : frame.stairsOrSlab(s.out().getOpposite(), Half.BOTTOM));
				}
			}
		}

		// within: posts and tie beams, the long hearth, tables and benches, the high seat on its dais
		interior(d, hx0, hz0, hx1, hz1, xc, wallH, frame, beam, dress);
		// outside: woodpiles along the walls, a runestone, banners at the porch
		if (hx0 >= 2) {
			Garden.woodpile(d, hx0 - 2, 0, hz1 - 2, Dirs.FRONT, Math.min(4, (hz1 - hz0) / 3));
		}
		if (hx1 <= w - 3) {
			Garden.woodpile(d, hx1 + 2, 0, hz1 - 2, Dirs.FRONT, Math.min(4, (hz1 - hz0) / 3));
		}
		int rx = hx0 >= 3 ? hx0 - 2 : -1;
		if (rx >= 0 && Grounds.standable(d, rx, 0, Math.max(0, hz0 - 1))) {
			int rz = Math.max(0, hz0 - 1);
			try (var s = d.stage(StageKind.EXTERIOR)) {
				d.set(rx, 0, rz, dress.full());
				d.set(rx, 1, rz, dress.full());
				Block accent = d.mat(Role.ACCENT).main(Form.FULL);
				d.set(rx, 2, rz, accent != null ? Paint.of(accent.defaultBlockState()) : dress.full());
			}
		}
		Grounds.forecourt(d, new int[]{hx0, hz0, hx1, hz1}, xc, 0, front >= 3 ? hz0 - 3 : hz0 - 1);
		if (h.snow()) {
			Garden.snowBanks(d, hx0, hz0, hx1, hz1, 0, xc);
		}
		Groundwork.clear(d, 0, 0, w - 1, dp - 1, Math.min(top, ridge + 4));
	}

	/** Crow steps up a stone gable at row z: the gable wall carried above the roof's edge in steps capped with slabs. */
	private static void crowSteps(Draft d, Roofs.Roof roof, int x0, int x1, int z, Stone dress) {
		int xc = (x0 + x1) / 2;
		try (var s = d.stage(StageKind.WALLS)) {
			for (int x = x0; x <= x1; x++) {
				if (!roof.covers(x, z)) {
					continue;
				}
				int sy = roof.surfaceY(x, z);
				// step heights: in pairs of cells, rising toward the middle
				int k = Math.abs(x - xc);
				int stepped = roof.surfaceY(xc - (k / 2) * 2 * Integer.signum(x - xc == 0 ? 1 : x - xc), z);
				int topY = Math.max(sy, stepped) + 1;
				for (int y = roof.bottomY(x, z); y <= topY; y++) {
					d.set(x, y, z, dress.full());
				}
				if (dress.has(Form.SLAB)) {
					d.set(x, topY + 1, z, dress.slab(SlabType.BOTTOM));
				}
			}
		}
	}

	/**
	 * The barge boards of a gable crossing over its apex and carved into dragon heads: from the roof's edge row
	 * {@code z}, the two boards carry on past the ridge for two blocks each, ending in a head of stairs with a jaw.
	 */
	private static void dragons(Draft d, Roofs.Roof roof, int x0, int x1, int xc, int z, Mat trim, Mat frame) {
		int ridge = roof.surfaceY(xc, z);
		if (ridge == Integer.MIN_VALUE || ridge + 3 >= d.maxHeight() || !d.allowed(xc, ridge + 1, z)) {
			return;
		}
		try (var s = d.stage(StageKind.ROOF)) {
			for (int side : new int[]{-1, 1}) {
				// the board climbing from the far side crosses the apex and rises beyond it
				for (int k = 1; k <= 2; k++) {
					int x = xc + side * k;
					int y = ridge + k;
					d.set(x, y, z, frame.log(Direction.Axis.Y));
				}
				int hx = xc + side * 3;
				int hy = ridge + 2;
				Direction outward = side < 0 ? Dirs.LEFT : Dirs.RIGHT;
				if (d.allowed(hx, hy, z)) {
					// the head: a snout of stairs looking outward, a jaw under it
					d.set(hx, hy, z, trim.stairsOrSlab(outward.getOpposite(), Half.TOP));
					d.set(hx, hy + 1, z, trim.stairsOrSlab(outward.getOpposite(), Half.BOTTOM));
				}
			}
			d.set(xc, ridge + 1, z, frame.log(Direction.Axis.Y));
		}
	}

	private static void interior(Draft d, int hx0, int hz0, int hx1, int hz1, int xc, int wallH, Mat frame, Mat beam, Stone dress) {
		int half = (hx1 - hx0) / 2 - 1;   // interior half-width
		int px = Math.max(3, half - 1);    // the posts' offset from the middle
		// posts in two rows, tie beams across the hall on them
		try (var s = d.stage(StageKind.FRAME)) {
			for (int z = hz0 + 3; z < hz1 - 1; z += 3) {
				for (int x : new int[]{xc - px, xc + px}) {
					if (x > hx0 && x < hx1) {
						for (int y = 0; y < wallH; y++) {
							d.set(x, y, z, frame.log(Direction.Axis.Y));
						}
					}
				}
				for (int x = hx0 + 1; x < hx1; x++) {
					d.set(x, wallH, z, beam.log(Direction.Axis.X));
				}
			}
		}
		// the dais at the far end with the high seat, banners behind it
		int daisZ = hz1 - 2;
		try (var s = d.stage(StageKind.INTERIOR)) {
			for (int z = daisZ; z < hz1; z++) {
				for (int x = hx0 + 1; x < hx1; x++) {
					d.set(x, 0, z, d.mat(Role.FLOOR).slab(SlabType.BOTTOM));
				}
			}
			d.set(xc, 1, hz1 - 1, frame.stairsOrSlab(Dirs.FRONT, Half.BOTTOM).map(b -> b));
			d.set(xc - 1, 1, hz1 - 1, frame.log(Direction.Axis.Y));
			d.set(xc + 1, 1, hz1 - 1, frame.log(Direction.Axis.Y));
		}
		d.poi(PoiType.SEAT, xc, 1, hz1 - 1, Dirs.FRONT);
		Details.wallBanner(d, xc - 1, 3, hz1 - 1, Dirs.FRONT, 0);
		Details.wallBanner(d, xc + 1, 3, hz1 - 1, Dirs.FRONT, 1);
		Details.wallBanner(d, xc, 3, hz1 - 1, Dirs.FRONT, 2);
		// the long hearth down the middle: fires in a stone kerb
		int hzA = hz0 + 4;
		int hzB = daisZ - 3;
		try (var s = d.stage(StageKind.INTERIOR)) {
			for (int z = hzA; z <= hzB; z++) {
				boolean fire = (z - hzA) % 3 == 1;
				d.set(xc, -1, z, dress.full());
				d.set(xc, 0, z, fire ? States.lit(Blocks.CAMPFIRE, true).trySetValue(BlockStateProperties.SIGNAL_FIRE, false)
					: dress.slab(SlabType.BOTTOM).at(xc, 0, z));
			}
		}
		for (int z = hzA + 1; z <= hzB; z += 3) {
			d.poi(PoiType.HEARTH, xc - 1, 0, z, Dirs.RIGHT);
		}
		// long tables either side, benches on both sides of each
		for (int side : new int[]{-1, 1}) {
			int tx = xc + side * 3;
			if (Math.abs(tx - xc) >= half) {
				continue;
			}
			int len = hzB - hzA + 1;
			if (len >= 3) {
				Furniture.longTable(d, tx, 0, hzA, Dirs.BACK, len);
				for (int z = hzA + 1; z < hzA + len - 1; z += 2) {
					Furniture.chair(d, tx - side, 0, z, side < 0 ? Dirs.RIGHT : Dirs.LEFT);
					if (Math.abs(tx + side - xc) < half + 1 && tx + side != xc - px * -side) {
						Furniture.chair(d, tx + side, 0, z, side < 0 ? Dirs.LEFT : Dirs.RIGHT);
					}
				}
			}
		}
		// mead barrels and a chest by the door, lanterns on chains from the beams
		try (var s = d.stage(StageKind.INTERIOR)) {
			d.set(hx0 + 1, 0, hz0 + 1, States.facing(Blocks.BARREL, Direction.UP));
			d.set(hx0 + 1, 1, hz0 + 1, States.facing(Blocks.BARREL, Dirs.RIGHT));
			d.set(hx0 + 1, 0, hz0 + 2, States.facing(Blocks.BARREL, Direction.UP));
		}
		Furniture.chest(d, hx1 - 1, 0, hz0 + 1, Dirs.LEFT, List.of(new Object[]{"minecraft:honey_bottle", 6}, new Object[]{"minecraft:cooked_beef", 8}));
		for (int z = hz0 + 3; z < hz1 - 1; z += 6) {
			Furniture.hangingLantern(d, xc - 2, wallH, z, 1);
			Furniture.hangingLantern(d, xc + 2, wallH, z, 1);
		}
		d.poi(PoiType.MEETING_POINT, xc - 1, 0, (hz0 + hz1) / 2, Dirs.RIGHT);
		d.poi(PoiType.TABLE, xc - 3, 0, hzA, Dirs.RIGHT);
	}
}
