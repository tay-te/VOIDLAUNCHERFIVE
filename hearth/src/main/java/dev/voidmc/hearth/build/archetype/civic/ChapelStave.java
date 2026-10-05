package dev.voidmc.hearth.build.archetype.civic;

import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Mat;
import dev.voidmc.hearth.build.draft.Paint;
import dev.voidmc.hearth.build.kit.Furniture;
import dev.voidmc.hearth.build.kit.Garden;
import dev.voidmc.hearth.build.kit.Openings;
import dev.voidmc.hearth.build.kit.Roofs;
import dev.voidmc.hearth.build.kit.Side;
import dev.voidmc.hearth.build.kit.civic.Arches;
import dev.voidmc.hearth.build.kit.civic.Grounds;
import dev.voidmc.hearth.build.kit.civic.Halls;
import dev.voidmc.hearth.build.kit.civic.Sacred;
import dev.voidmc.hearth.build.kit.civic.Towers;
import dev.voidmc.hearth.build.kit.civic.Tradition;
import dev.voidmc.hearth.build.style.Form;
import dev.voidmc.hearth.build.style.Habits;
import dev.voidmc.hearth.build.style.Role;
import dev.voidmc.hearth.build.terrain.Groundwork;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.Half;

/**
 * The northern stave church: a tall nave of upright staves under a very steep shingle roof, wrapped at its foot in a
 * low covered gallery (the svalgang) whose lean-to roof runs all round, a steep-gabled porch to the street, a lower
 * chancel and a round-ended apse behind, roofs stacked one over the other, a belfry turret with a needle spire on the
 * ridge, and carved crests rising from the gable ends. Dark tarred timber throughout, on a fieldstone sill.
 */
final class ChapelStave {
	private ChapelStave() {
	}

	static void draw(Draft d) {
		int w = d.frontage();
		int dp = d.depth();
		int tier = d.tier();
		int maxH = d.maxHeight();
		Habits h = d.habits();
		Tradition t = Tradition.LOG;
		int cw = w >= 11 ? 7 : 5;
		int total = cw + 4;
		boolean yard = d.param("graveyard", true);
		boolean side = yard && w - total >= 3;
		boolean right = d.chance(0.5);
		int gx0 = side ? (right ? 0 : w - total) : (w - total) / 2;
		int x0 = gx0 + 2;
		int x1 = x0 + cw - 1;
		int cx = (x0 + x1) / 2;
		// the depth: a porch, the gallery, the nave, the chancel and the apse
		boolean apse = dp >= 16;
		int zPorch = 1;
		int zG0 = zPorch + 2;
		int z0 = zG0 + 2;
		int chancelDepth = 3;
		int back = (apse ? 2 : 0) + chancelDepth + 1;
		int z1 = Math.min(z0 + cw + 1, dp - 1 - back);
		int zCh1 = z1 + chancelDepth;
		int chw = cw - 2;
		int chx0 = cx - chw / 2;
		int chx1 = cx + chw / 2;
		int base = Grounds.raisedBase(d, gx0, gx0 + total - 1, zG0);
		int hc = tier <= 2 ? 8 : tier == 3 ? 9 : 10;
		int plate = base + hc - 1;
		double pitch = 2.0;
		int half = cw / 2 + 1;
		if (plate + 1 + pitch * half + 9 > maxH - 1) {
			pitch = Math.max(1.0, (maxH - 1 - 9 - plate - 1.0) / half);
		}
		d.body(x0, z0, x1, z1);
		d.eave(base + 4);

		Mat wall = d.mat(Role.WALL);
		Mat frame = d.mat(Role.FRAME);
		Mat beam = d.mat(Role.BEAM);
		Mat roof = d.mat(Role.ROOF);
		Mat trim = d.mat(Role.ROOF_TRIM);
		Mat plinth = d.mat(Role.PLINTH);

		// --- the ground: a fieldstone sill under everything
		Groundwork.footings(d, gx0, zG0, gx0 + total - 1, z1 + 2, base - 1);
		Groundwork.footings(d, chx0, z1, chx1, zCh1, base - 1);
		if (apse) {
			Groundwork.footings(d, cx - 1, zCh1, cx + 1, zCh1 + 2, base - 1);
		}
		Groundwork.footings(d, cx - 1, zPorch, cx + 1, zG0, base - 1);
		Grounds.forecourt(d, 0, w - 1, zPorch - 1, false);
		Halls.floor(d, gx0, zG0, gx0 + total - 1, z1 + 2, base - 1, false);
		Halls.floor(d, chx0 + 1, z1, chx1 - 1, zCh1 - 1, base - 1, false);
		Halls.floor(d, cx - 1, zPorch, cx + 1, zG0, base - 1, false);

		// --- the nave: upright staves between dark corner posts, a sill of stone
		try (var s = d.stage(StageKind.WALLS)) {
			for (int[] c : Draft.ring(x0, z0, x1, z1)) {
				boolean corner = (c[0] == x0 || c[0] == x1) && (c[1] == z0 || c[1] == z1);
				for (int y = base; y <= plate; y++) {
					Paint p = y == base ? plinth.full() : corner ? frame.log(Direction.Axis.Y) : y == plate || y == base + 5 ? beam.log(
						c[1] == z0 || c[1] == z1 ? Direction.Axis.X : Direction.Axis.Z) : wall.log(Direction.Axis.Y);
					d.set(c[0], y, c[1], p);
				}
			}
		}
		d.carve(x0 + 1, base, z0 + 1, x1 - 1, plate + 14, z1 - 1);
		// small round lights high in the nave above the gallery roof
		for (Side s : Side.of(x0, z0, x1, z1)) {
			if (s.out() == Dirs.BACK) {
				continue;
			}
			for (int i = 2; i < s.last() - 1; i += 2) {
				int fi = i;
				d.inStage(StageKind.OPENINGS, () -> d.set(s.x(fi), base + 7 <= plate - 1 ? base + 7 : plate - 1, s.z(fi), d.mat(Role.GLASS).pane()));
			}
		}

		// --- the gallery round the nave: posts with arched braces, a lean-to roof
		int px0 = gx0;
		int px1 = gx0 + total - 1;
		int pz0 = zG0;
		int pz1 = z1 + 2;
		for (Side s : Side.of(px0, pz0, px1, pz1)) {
			boolean frontSide = s.out() == Dirs.FRONT;
			int[] piers = Arches.piers(0, s.last(), 2);
			Arches.arcade(d, s, piers, base, base + 3, Arches.Kind.BRACED, frame.log(Direction.Axis.Y), null);
			// a rail between the posts, opened at the porch and where the chancel passes
			for (int i = 1; i < s.last(); i++) {
				int x = s.x(i);
				int z = s.z(i);
				boolean opening = frontSide && Math.abs(x - cx) <= 1 || s.out() == Dirs.BACK && x >= chx0 && x <= chx1;
				if (!opening && d.isEmpty(x, base, z) && !isPier(piers, i)) {
					d.inStage(StageKind.EXTERIOR, () -> d.set(x, base, z, d.mat(Role.RAILING).fence()));
				}
			}
		}
		Roofs.Field gallery = (x, z) -> {
			if (x < px0 - 1 || x > px1 + 1 || z < pz0 - 1 || z > pz1 + 1) {
				return Double.NaN;
			}
			if (x >= x0 && x <= x1 && z >= z0 && z <= z1 || x >= chx0 && x <= chx1 && z >= z1 && z <= zCh1) {
				return Double.NaN;
			}
			int dd = Math.min(Math.min(x - (px0 - 1), (px1 + 1) - x), Math.min(z - (pz0 - 1), (pz1 + 1) - z));
			return 1 + Math.min(2, dd);
		};
		Roofs.raster(d, gallery, base + 3, Math.max(0, px0 - 1), Math.max(0, pz0 - 1), Math.min(w - 1, px1 + 1), Math.min(dp - 1, pz1 + 1), roof, trim,
			Habits.Ridge.SLAB);

		// the way through the gallery's front from the porch
		try (var s = d.stage(StageKind.FRAME)) {
			for (int x = cx - 1; x <= cx + 1; x++) {
				for (int y = base; y <= base + 2; y++) {
					d.set(x, y, zG0, Blocks.AIR.defaultBlockState());
				}
				d.set(x, base + 3, zG0, beam.log(Direction.Axis.X));
			}
		}

		// --- the porch to the street: posts, a steep gable facing it
		Side pf = Side.of(cx - 1, zPorch, cx + 1, zG0 - 1, Dirs.FRONT);
		try (var s = d.stage(StageKind.FRAME)) {
			for (int z = zPorch; z < zG0; z++) {
				for (int x : new int[]{cx - 1, cx + 1}) {
					for (int y = base; y <= base + 2; y++) {
						d.set(x, y, z, z == zPorch ? frame.log(Direction.Axis.Y) : wall.log(Direction.Axis.Y));
					}
				}
			}
			d.set(cx, base + 2, zPorch, beam.stairsOrSlab(Dirs.LEFT, Half.TOP));
			for (int y = base; y <= base + 1; y++) {
				d.set(cx, y, zPorch, Blocks.AIR.defaultBlockState());
				d.set(cx, y, zPorch + 1, Blocks.AIR.defaultBlockState());
			}
		}
		Roofs.Field porch = Roofs.gable(cx - 2, zPorch - 1, cx + 2, zG0, Direction.Axis.Z, Roofs.Profile.straight(1.5));
		Roofs.Roof pr = Roofs.raster(d, porch, base + 3, Math.max(0, cx - 2), Math.max(0, zPorch - 1), Math.min(w - 1, cx + 2), zG0, roof, trim,
			Habits.Ridge.TRIM);
		fillGable(d, pr, cx - 1, cx + 1, zPorch, base + 2, wall);
		crest(d, cx, porchApex(d, cx, zPorch - 1, base) + 1, zPorch - 1, Dirs.FRONT);

		// --- the nave's steep roof and its turret
		Roofs.Opts o = Roofs.Opts.of(d).withPitch(pitch).withEaves(1, 1);
		Roofs.Built nr = Roofs.build(d, x0, z0, x1, z1, plate, dev.voidmc.hearth.build.style.Habits.RoofForm.GABLE, Direction.Axis.Z, o, false, false);
		Roofs.Roof r = nr.roof();
		int ridge = r.ridgeY;
		crest(d, cx, r.surfaceY(cx, z0 - 1) + 1, z0 - 1, Dirs.FRONT);
		crest(d, cx, r.surfaceY(cx, z1 + 1) + 1, z1 + 1, Dirs.BACK);
		int tz = (z0 + z1) / 2 - 1;
		int capTop = ChapelWestern.turret(d, t, cx, tz, plate, ridge, maxH);

		// --- the chancel and the apse, lower, stacked behind
		int chPlate = base + 5;
		try (var s = d.stage(StageKind.WALLS)) {
			for (int[] c : Draft.ring(chx0, z1, chx1, zCh1)) {
				if (c[1] == z1) {
					continue;
				}
				boolean corner = (c[0] == chx0 || c[0] == chx1) && c[1] == zCh1;
				for (int y = base; y <= chPlate; y++) {
					d.set(c[0], y, c[1], y == base ? plinth.full() : corner ? frame.log(Direction.Axis.Y) : wall.log(Direction.Axis.Y));
				}
			}
		}
		d.clearBox(chx0 + 1, base, z1 + 1, chx1 - 1, chPlate, zCh1 - 1);
		// open the nave's back wall into the chancel
		Side nb = Side.of(x0, z0, x1, z1, Dirs.BACK);
		Arches.arcade(d, nb, new int[]{nb.index(chx0), nb.index(chx1)}, base, base + 3, Arches.Kind.BRACED, frame.log(Direction.Axis.Y), null);
		Roofs.Field chf = Roofs.gable(chx0 - 1, z1, chx1 + 1, zCh1 + 1, Direction.Axis.Z, Roofs.Profile.straight(Math.min(2.0, pitch)));
		Roofs.Roof chr = Roofs.raster(d, chf, chPlate + 1, chx0 - 1, z1 + 1, chx1 + 1, Math.min(dp - 1, zCh1 + 1), roof, trim, Habits.Ridge.TRIM);
		fillGable(d, chr, chx0, chx1, zCh1, chPlate, wall);
		crest(d, cx, chr.surfaceY(cx, Math.min(dp - 1, zCh1 + 1)) + 1, Math.min(dp - 1, zCh1 + 1), Dirs.BACK);
		if (apse) {
			int az0 = zCh1;
			int az1 = zCh1 + 2;
			try (var s = d.stage(StageKind.WALLS)) {
				for (int z = az0 + 1; z <= az1; z++) {
					for (int x = cx - 1; x <= cx + 1; x++) {
						boolean edge = x != cx || z == az1;
						for (int y = base; y <= base + 3; y++) {
							d.set(x, y, z, edge ? (y == base ? plinth.full() : wall.log(Direction.Axis.Y)) : Paint.of(Blocks.AIR.defaultBlockState()));
						}
					}
				}
				d.set(cx, base, zCh1, Blocks.AIR.defaultBlockState());
				d.set(cx, base + 1, zCh1, Blocks.AIR.defaultBlockState());
			}
			Towers.spire(d, cx - 1, az0 + 1, 3, base + 3, Math.min(maxH, chPlate + 6));
		}
		// a wooden bell-house stands apart in the yard in old parishes; here the bell hangs in the turret

		// --- inside
		int iz0 = z0 + 1;
		Openings.door(d, Side.of(x0, z0, x1, z1, Dirs.FRONT), cx, base, 1, new Openings.DoorOpts(false, false, true));
		Sacred.pews(d, x0 + 1, x1 - 1, iz0 + 1, z1 - 3, base, cx, Dirs.BACK, false);
		Sacred.altar(d, cx, base, zCh1 - 1, Dirs.FRONT, chw >= 5, chw >= 5 ? cx + 1 : Integer.MIN_VALUE);
		if (chw < 5) {
			dev.voidmc.hearth.build.kit.Furniture.workstation(d, dev.voidmc.hearth.api.JobType.PRIEST, cx + 1, base, z1 - 1, Dirs.FRONT);
		}
		for (int z = z0 + 2; z < z1; z += 3) {
			try (var s = d.stage(StageKind.FRAME)) {
				for (int x = x0 + 1; x <= x1 - 1; x++) {
					d.set(x, plate, z, beam.log(Direction.Axis.X));
				}
			}
			Halls.lanternUnder(d, cx, base, z, 3);
		}
		Sacred.candles(d, chx0 + 1, base, zCh1 - 1, 2);

		// --- outside: the way to the porch, the churchyard, snow
		if (base >= 1 && zPorch >= 1) {
			Grounds.flight(d, cx, cx, zPorch - 1, base, base);
		}
		Grounds.approach(d, cx, base, zPorch);
		if (side) {
			int yx0 = right ? total + 1 : 0;
			int yx1 = right ? w - 1 : w - total - 2;
			Sacred.churchyard(d, yx0, 2, yx1, dp - 2, 0, true, Dirs.FRONT);
			Halls.yardEdge(d, yx0, 1, yx1, dp - 1, 0, right ? Dirs.LEFT : Dirs.RIGHT);
		}
		Halls.lamp(d, Math.max(0, cx - 3), 0, 0, 2);
		if (h.snow() || d.site().snowy()) {
			Garden.snowBanks(d, px0, pz0, px1, pz1, base, cx);
		}
		Groundwork.clear(d, 0, 0, w - 1, dp - 1, Math.max(capTop, ridge) + 2);
		dev.voidmc.hearth.build.kit.refine.Voids.finish(d);
	}

	private static boolean isPier(int[] piers, int i) {
		for (int p : piers) {
			if (p == i) {
				return true;
			}
		}
		return false;
	}

	/** The surface height of the porch roof's ridge at its front (for the crest). */
	private static int porchApex(Draft d, int cx, int z, int base) {
		for (int y = base + 10; y >= base; y--) {
			if (!d.isEmpty(cx, y, z)) {
				return y;
			}
		}
		return base + 4;
	}

	/** A carved crest on a gable's apex: a post of the trim rising from the ridge end and a scroll leaning out. */
	static void crest(Draft d, int x, int y, int z, Direction out) {
		if (y <= 0 || !d.allowed(x, y, z) || !d.allowed(x, y + 1, z)) {
			return;
		}
		Mat trim = d.mat(Role.ROOF_TRIM);
		Mat frame = d.mat(Role.FRAME);
		try (var s = d.stage(StageKind.ROOF)) {
			d.set(x, y, z, frame.has(Form.FENCE) ? frame.fence() : trim.full());
			d.set(x, y + 1, z, trim.stairsOrSlab(out.getOpposite(), Half.BOTTOM));
		}
	}

	/** Fills a gable end wall at row z under a roof, from above the plate up to the roof's underside, in staves. */
	private static void fillGable(Draft d, Roofs.Roof r, int x0, int x1, int z, int plate, Mat wall) {
		try (var s = d.stage(StageKind.WALLS)) {
			for (int x = x0; x <= x1; x++) {
				if (!r.covers(x, z)) {
					continue;
				}
				for (int y = plate + 1; y < r.bottomY(x, z); y++) {
					d.set(x, y, z, wall.log(Direction.Axis.Y));
				}
			}
		}
	}
}
