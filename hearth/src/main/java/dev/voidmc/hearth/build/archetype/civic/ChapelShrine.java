package dev.voidmc.hearth.build.archetype.civic;

import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.PoiType;
import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Mat;
import dev.voidmc.hearth.build.draft.States;
import dev.voidmc.hearth.build.kit.Furniture;
import dev.voidmc.hearth.build.kit.Garden;
import dev.voidmc.hearth.build.kit.Openings;
import dev.voidmc.hearth.build.kit.Roofs;
import dev.voidmc.hearth.build.kit.Side;
import dev.voidmc.hearth.build.kit.civic.Grounds;
import dev.voidmc.hearth.build.kit.civic.Halls;
import dev.voidmc.hearth.build.kit.civic.Sacred;
import dev.voidmc.hearth.build.kit.civic.Towers;
import dev.voidmc.hearth.build.style.Form;
import dev.voidmc.hearth.build.style.Habits.RoofForm;
import dev.voidmc.hearth.build.style.Role;
import dev.voidmc.hearth.build.terrain.Groundwork;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * The karst shrine-temple: a hall of lacquered columns and lattice on a limestone terrace with a balustrade, reached by
 * steps up the middle, under a double roof of curved green tiles whose corners lift, the upper roof raised on a
 * clerestory over the lower; inside an altar with candles, flowers and incense before the image, low benches on
 * either side; before it a paired gate of posts under a little curved roof, stone lanterns along the way, and a bell in
 * its own open pavilion; the rest of the ground a raked garden.
 */
final class ChapelShrine {
	private ChapelShrine() {
	}

	static void draw(Draft d) {
		int w = d.frontage();
		int dp = d.depth();
		int tier = d.tier();
		int maxH = d.maxHeight();
		int hw = w >= 11 ? 9 : 7;
		int hx0 = (w - hw) / 2;
		int hx1 = hx0 + hw - 1;
		int cx = (hx0 + hx1) / 2;
		// the terrace: a walk all round the hall
		int tx0 = Math.max(0, hx0 - 1);
		int tx1 = Math.min(w - 1, hx1 + 1);
		int zGate = 0;
		int tz0 = dp >= 16 ? 5 : 4;
		int hz0 = tz0 + 1;
		int hd = Math.min(hw + 2, dp - 2 - hz0);
		int hz1 = hz0 + hd - 1;
		int tz1 = Math.min(dp - 1, hz1 + 1);
		int lift = 2;
		int base = lift;
		int hh = tier <= 2 ? 5 : 6;
		int plate = base + hh - 1;
		d.body(hx0, hz0, hx1, hz1);
		d.eave(plate);
		Mat found = d.mat(Role.FOUNDATION);
		Mat plinth = d.mat(Role.PLINTH);
		Mat paving = d.mat(Role.PAVING);
		Mat accent = d.mat(Role.WALL_ACCENT);
		Mat frame = d.mat(Role.FRAME);
		Mat shutter = d.mat(Role.SHUTTER);
		Mat wall = d.mat(Role.WALL);

		// --- the terrace: dressed limestone, a balustrade, steps up the middle
		Groundwork.footings(d, tx0, tz0, tx1, tz1, base - 1);
		try (var s = d.stage(StageKind.FOUNDATION)) {
			for (int z = tz0; z <= tz1; z++) {
				for (int x = tx0; x <= tx1; x++) {
					boolean edge = x == tx0 || x == tx1 || z == tz0 || z == tz1;
					for (int y = Math.min(d.site().ground(x, z), base) - 1; y < base - 1; y++) {
						d.set(x, y, z, edge ? plinth.full() : found.full());
					}
					d.set(x, base - 1, z, edge ? accent.full() : paving.full());
				}
			}
		}
		try (var s = d.stage(StageKind.EXTERIOR)) {
			for (int[] c : Draft.ring(tx0, tz0, tx1, tz1)) {
				boolean steps = c[1] == tz0 && Math.abs(c[0] - cx) <= 1;
				boolean post = (c[0] - tx0) % 2 == 0;
				if (!steps) {
					d.set(c[0], base, c[1], post ? (accent.has(Form.WALL) ? accent.wall() : found.wall()) : d.mat(Role.RAILING).fence());
				}
			}
		}
		Grounds.cut(d, tx0, tz0, tx1, tz1, base);
		// steps from the court up to the terrace
		Grounds.forecourt(d, 0, w - 1, tz0 - 1, true);
		Grounds.flight(d, cx - 1, cx + 1, tz0 - 1, base, base);
		Grounds.approach(d, cx, base, hz0);

		// --- the hall: columns round a porch at the front, lattice between them elsewhere
		int[] cols = {0, 2, hw - 3, hw - 1};
		try (var s = d.stage(StageKind.FRAME)) {
			for (int[] c : Draft.ring(hx0, hz0, hx1, hz1)) {
				int ix = c[0] - hx0;
				int iz = c[1] - hz0;
				boolean column = c[0] == hx0 || c[0] == hx1 || c[1] == hz0 || c[1] == hz1;
				boolean colPos = c[1] == hz0 || c[1] == hz1 ? isIn(cols, ix) : iz % 2 == 0;
				for (int y = base; y < plate; y++) {
					if (colPos) {
						d.set(c[0], y, c[1], frame.log(Direction.Axis.Y));
					}
				}
				d.set(c[0], plate, c[1], d.mat(Role.BEAM).log(c[1] == hz0 || c[1] == hz1 ? Direction.Axis.X : Direction.Axis.Z));
			}
		}
		// the inner wall a row behind the front columns (a porch before it), lattice panels in the other bays
		int iw0 = hz0 + 1;
		try (var s = d.stage(StageKind.WALLS)) {
			for (int[] c : Draft.ring(hx0, iw0, hx1, hz1)) {
				if (!d.isEmpty(c[0], base, c[1]) && c[1] != iw0) {
					continue;
				}
				for (int y = base; y < plate; y++) {
					if (!d.isEmpty(c[0], y, c[1])) {
						continue;
					}
					boolean low = y == base;
					boolean lattice = y >= base + 1 && y <= plate - 2;
					Direction out = c[1] == iw0 ? Dirs.FRONT : c[0] == hx0 ? Dirs.LEFT : c[0] == hx1 ? Dirs.RIGHT : Dirs.BACK;
					d.set(c[0], y, c[1], low ? plinth.full() : lattice ? shutter.trapdoor(out.getOpposite(), Half.BOTTOM, true) : wall.full());
				}
			}
		}
		d.carve(hx0 + 1, base, iw0 + 1, hx1 - 1, plate + 6, hz1 - 1);
		d.carve(hx0 + 1, base, hz0, hx1 - 1, plate - 1, hz0);
		// the doors: a pair in the middle of the inner wall
		Side inner = Side.of(hx0, iw0, hx1, hz1, Dirs.FRONT);
		Openings.door(d, inner, cx, base, 1, new Openings.DoorOpts(false, false, true));
		for (int x = cx - 1; x <= cx + 1; x += 2) {
			int fx = x;
			d.inStage(StageKind.WALLS, () -> {
				d.set(fx, base, iw0, frame.log(Direction.Axis.Y));
				d.set(fx, base + 1, iw0, frame.log(Direction.Axis.Y));
			});
		}

		// --- the double roof: a lower curved hip round the hall, a clerestory, an upper curved hip
		Roofs.Opts o = Roofs.Opts.of(d).withPitch(1.0).withEaves(2, 1);
		int ux0 = hx0 + 2;
		int ux1 = hx1 - 2;
		int uz0 = hz0 + 2;
		int uz1 = hz1 - 2;
		int uplate = plate + 5;
		boolean twoTier = ux1 - ux0 >= 2 && uz1 - uz0 >= 2 && uplate + 5 < maxH - 1;
		Roofs.Roof lower = twoTier
			? dev.voidmc.hearth.build.kit.civic.Eaves.skirt(d, hx0, hz0, hx1, hz1, ux0, uz0, ux1, uz1, plate, 2, 1.0, true, true)
			: Roofs.build(d, hx0, hz0, hx1, hz1, plate, RoofForm.PAGODA, Direction.Axis.X, o, false, false).roof();
		int top = lower.ridgeY;
		if (twoTier) {
			try (var s = d.stage(StageKind.WALLS)) {
				for (int[] c : Draft.ring(ux0, uz0, ux1, uz1)) {
					boolean corner = (c[0] == ux0 || c[0] == ux1) && (c[1] == uz0 || c[1] == uz1);
					for (int y = plate + 1; y <= uplate; y++) {
						boolean lattice = y == uplate - 1 && !corner;
						Direction out = c[1] == uz0 ? Dirs.FRONT : c[1] == uz1 ? Dirs.BACK : c[0] == ux0 ? Dirs.LEFT : Dirs.RIGHT;
						d.set(c[0], y, c[1], corner ? frame.log(Direction.Axis.Y) : y == uplate ? d.mat(Role.BEAM).log(c[1] == uz0 || c[1] == uz1
							? Direction.Axis.X : Direction.Axis.Z) : lattice ? shutter.trapdoor(out.getOpposite(), Half.BOTTOM, true) : wall.full());
					}
				}
			}
			d.clearBox(ux0 + 1, plate, uz0 + 1, ux1 - 1, uplate - 1, uz1 - 1);
			Roofs.Roof upper = Roofs.build(d, ux0, uz0, ux1, uz1, uplate, RoofForm.PAGODA, Direction.Axis.X, o.withEaves(2, 1), false, false).roof();
			top = upper.ridgeY;
			// a ridge ornament: a pearl on a post
			int ry = upper.ridgeY + 1;
			d.inStage(StageKind.ROOF, () -> {
				for (int x = ux0; x <= ux1; x++) {
					if (upper.covers(x, (uz0 + uz1) / 2) && upper.surfaceY(x, (uz0 + uz1) / 2) == upper.ridgeY && (x == ux0 + 1 || x == ux1 - 1)) {
						d.set(x, ry, (uz0 + uz1) / 2, d.mat(Role.ROOF).stairsOrSlab(x < cx ? Dirs.RIGHT : Dirs.LEFT, Half.BOTTOM));
					}
				}
			});
			top = Towers.finial(d, cx, ry, (uz0 + uz1) / 2, maxH, false);
		}

		// --- inside: the altar before the image, benches either side, lanterns
		int az = hz1 - 1;
		Sacred.altar(d, cx, base, az, Dirs.FRONT, hw >= 9, Integer.MIN_VALUE);
		Furniture.workstation(d, JobType.PRIEST, cx + 2, base, az - 1, Dirs.LEFT);
		try (var s = d.stage(StageKind.INTERIOR)) {
			// the image: a carved stone on a plinth against the back wall, banners of the town's cloth
			d.set(cx, base + 2, hz1, d.mat(Role.ACCENT).full());
			d.set(cx - 1, base, az, Blocks.DECORATED_POT.defaultBlockState());
			d.set(cx + 1, base, az, Blocks.DECORATED_POT.defaultBlockState());
		}
		Sacred.candles(d, cx, base + 1, az, 4);
		Mat wood = d.mat(Role.FURNITURE);
		for (int z = iw0 + 2; z <= az - 2; z += 2) {
			for (int x = hx0 + 1; x <= hx1 - 1; x++) {
				if (Math.abs(x - cx) <= 1 || !d.isEmpty(x, base, z)) {
					continue;
				}
				int fx = x;
				int fz = z;
				d.inStage(StageKind.INTERIOR, () -> d.set(fx, base, fz, wood.slab(SlabType.BOTTOM)));
				d.poi(PoiType.SEAT, x, base, z, Dirs.BACK);
			}
		}
		Halls.lanternUnder(d, cx, base, (iw0 + hz1) / 2, 2);

		// --- the court: a gate, stone lanterns along the way, an incense burner, the bell pavilion, a raked garden
		gate(d, cx, zGate, 0);
		for (int z = 1; z < tz0 - 1; z += 2) {
			stoneLantern(d, cx - 2, 0, z);
			stoneLantern(d, cx + 2, 0, z);
		}
		Garden.path(d, Garden.line(cx, 1, cx, tz0 - 1), 0);
		Garden.path(d, Garden.line(cx - 1, 1, cx - 1, tz0 - 1), 0);
		Garden.path(d, Garden.line(cx + 1, 1, cx + 1, tz0 - 1), 0);
		// incense burning in a cauldron on the terrace, either side of the steps
		for (int x : new int[]{cx - 3, cx + 3}) {
			if (x > tx0 && x < tx1 && d.isEmpty(x, base, tz0 + 0)) {
				int fx = x;
				d.inStage(StageKind.EXTERIOR, () -> d.set(fx, base, tz0, States.lit(Blocks.CAMPFIRE, true)
					.trySetValue(BlockStateProperties.SIGNAL_FIRE, false)));
			}
		}
		if (w - tx1 >= 4 && dp - tz0 >= 6) {
			bellPavilion(d, tx1 + 1, tz0 + 1, maxH);
		} else {
			// the temple bell hangs in the porch, under the eave beam
			Towers.bell(d, cx - 2, plate - 1, hz0, Direction.Axis.X);
		}
		for (int[] r : new int[][]{{0, 1, cx - 3, tz0 - 1}, {cx + 3, 1, w - 1, tz0 - 1}}) {
			if (r[2] - r[0] >= 1) {
				Garden.garden(d, r[0], r[1], r[2], r[3], 0);
			}
		}
		dev.voidmc.hearth.build.kit.civic.Halls.finish(d);
		Groundwork.clear(d, 0, 0, w - 1, dp - 1, top + 2);
		dev.voidmc.hearth.build.kit.refine.Voids.finish(d);
	}

	private static boolean isIn(int[] a, int v) {
		for (int x : a) {
			if (x == v) {
				return true;
			}
		}
		return false;
	}

	/** A gate of two posts under a little curved roof with lifted ends, at (cx, z), three wide. */
	static void gate(Draft d, int cx, int z, int y) {
		Mat frame = d.mat(Role.FRAME);
		Mat roof = d.mat(Role.ROOF);
		Mat beam = d.mat(Role.BEAM);
		if (cx - 2 < 0 || cx + 2 >= d.frontage()) {
			return;
		}
		try (var s = d.stage(StageKind.EXTERIOR)) {
			for (int x : new int[]{cx - 2, cx + 2}) {
				int g = d.site().ground(x, z);
				for (int yy = Math.min(g, y) - 1; yy < y; yy++) {
					d.set(x, yy, z, d.mat(Role.FOUNDATION).full());
				}
				d.set(x, y, z, d.mat(Role.WALL_ACCENT).full());
				for (int yy = y + 1; yy <= y + 4; yy++) {
					d.set(x, yy, z, frame.log(Direction.Axis.Y));
				}
			}
			// a tie beam, a lintel over it with the name board between, a thin curved cap with lifted ends
			for (int x = cx - 2; x <= cx + 2; x++) {
				d.set(x, y + 4, z, beam.log(Direction.Axis.X));
			}
			d.set(cx, y + 5, z, d.mat(Role.ACCENT).full());
			for (int x = cx - 3; x <= cx + 3; x++) {
				if (x < 0 || x >= d.frontage()) {
					continue;
				}
				boolean end = x == cx - 3 || x == cx + 3;
				d.set(x, y + (end ? 6 : 6), z, end ? roof.stairsOrSlab(x < cx ? Dirs.RIGHT : Dirs.LEFT, Half.BOTTOM)
					: roof.slab(net.minecraft.world.level.block.state.properties.SlabType.BOTTOM));
				if (!end && x != cx) {
					d.set(x, y + 5, z, x == cx - 2 || x == cx + 2 ? frame.log(Direction.Axis.Y) : beam.stairsOrSlab(x < cx ? Dirs.LEFT : Dirs.RIGHT, Half.TOP));
				}
			}
		}
	}

	/** A stone lantern: a post, a lantern in a little house of stone, a cap. */
	static void stoneLantern(Draft d, int x, int y, int z) {
		if (x < 0 || x >= d.frontage() || Groundwork.surface(d, x, z) != y || !d.isEmpty(x, y, z)) {
			return;
		}
		Mat accent = d.mat(Role.WALL_ACCENT);
		Mat found = d.mat(Role.FOUNDATION);
		try (var s = d.stage(StageKind.EXTERIOR)) {
			d.set(x, y, z, found.has(Form.WALL) ? found.wall() : accent.wall());
			d.set(x, y + 1, z, States.lantern(d.mat(Role.LIGHT).mainOr(Form.FULL, Blocks.LANTERN), false));
			d.set(x, y + 2, z, accent.slab(SlabType.BOTTOM));
		}
	}

	/** An open pavilion of four posts under a curved roof with the temple bell hung in it. */
	static void bellPavilion(Draft d, int x0, int z0, int maxH) {
		Mat frame = d.mat(Role.FRAME);
		int g = Groundwork.surface(d, x0 + 1, z0 + 1);
		if (x0 + 2 >= d.frontage() || z0 + 2 >= d.depth()) {
			return;
		}
		try (var s = d.stage(StageKind.FRAME)) {
			for (int z = z0; z <= z0 + 2; z++) {
				for (int x = x0; x <= x0 + 2; x++) {
					int gg = d.site().ground(x, z);
					for (int y = Math.min(gg, g) - 1; y < g; y++) {
						d.set(x, y, z, d.mat(Role.FOUNDATION).full());
					}
				}
			}
			for (int[] c : new int[][]{{x0, z0}, {x0 + 2, z0}, {x0, z0 + 2}, {x0 + 2, z0 + 2}}) {
				for (int y = g; y <= g + 3; y++) {
					d.set(c[0], y, c[1], frame.log(Direction.Axis.Y));
				}
			}
			for (int[] c : Draft.ring(x0, z0, x0 + 2, z0 + 2)) {
				d.set(c[0], g + 4, c[1], d.mat(Role.BEAM).log(c[1] == z0 || c[1] == z0 + 2 ? Direction.Axis.X : Direction.Axis.Z));
			}
			d.set(x0 + 1, g + 4, z0 + 1, d.mat(Role.BEAM).log(Direction.Axis.X));
		}
		Towers.bell(d, x0 + 1, g + 3, z0 + 1, Direction.Axis.X);
		Roofs.Opts o = Roofs.Opts.of(d).withEaves(1, 1).withPitch(1.0);
		Roofs.build(d, x0, z0, x0 + 2, z0 + 2, g + 4, RoofForm.PAGODA, Direction.Axis.X, o, false, false);
		d.poi(PoiType.MEETING_POINT, x0 + 1, g, z0 + 1, Dirs.FRONT);
	}
}
