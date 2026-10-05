package dev.voidmc.hearth.build.archetype.landmark;

import dev.voidmc.hearth.api.PoiType;
import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Mat;
import dev.voidmc.hearth.build.draft.Paint;
import dev.voidmc.hearth.build.kit.Floors;
import dev.voidmc.hearth.build.kit.Garden;
import dev.voidmc.hearth.build.kit.Openings;
import dev.voidmc.hearth.build.kit.Roofs;
import dev.voidmc.hearth.build.kit.Side;
import dev.voidmc.hearth.build.kit.civic.Halls;
import dev.voidmc.hearth.build.kit.civic.Sacred;
import dev.voidmc.hearth.build.kit.landmark.Crowns;
import dev.voidmc.hearth.build.kit.landmark.Grounds;
import dev.voidmc.hearth.build.kit.landmark.Shapes;
import dev.voidmc.hearth.build.kit.landmark.Stone;
import dev.voidmc.hearth.build.style.Form;
import dev.voidmc.hearth.build.style.Habits;
import dev.voidmc.hearth.build.style.Role;
import dev.voidmc.hearth.build.terrain.Groundwork;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * The northern cathedral: a great stave church in the manner of Heddal and Borgund. A tall core of staves (the
 * midtrom) rises over aisles that wrap it under their own lean-to roofs, and the svalgang, a low covered gallery on
 * arched posts, runs round the foot of everything; the roofs climb in tiers, gallery, aisles, the steep shingled roof of
 * the core, and from the core's ridge the roof turret rises in two stages, a louvred belfry under a skirt and a steep
 * shingle spire. Behind, a lower chancel with its own little turret and a round apse under a cone; before, a steep-gabled
 * porch on carved posts. Dragon heads rear from every gable's apex. Dark staves on a fieldstone sill, small round lights
 * high in the core; within, columns down both sides of the nave, pews, lanterns hung from the tie beams, the altar and the
 * priest's lectern in the chancel, candles in the apse.
 *
 * <p>Drawn by {@link Cathedral} for cultures whose cathedral is a stave church (the northern one); it reads the same
 * params (the tower layout is not used: a stave church has its turret).
 */
final class StaveCathedral {
	private StaveCathedral() {
	}

	static void draw(Draft d) {
		int w = d.frontage();
		int dp = d.depth();
		int top = d.maxHeight() - 1;
		Habits h = d.habits();
		int cx = (w - 1) / 2;
		// half-widths: the core walls, the aisle walls, the gallery's posts
		int hc = w >= 21 ? 4 : 3;
		int ho = hc + 3;
		int hg = ho + 2;
		int zG0 = 2;
		int zO0 = zG0 + 2;
		int chancelLen = 6;
		int hk = hc - 1;
		int apseR = Math.max(1, hk - 1);
		int len = Math.clamp(dp - (zO0 + chancelLen + apseR + 3), 11, 21);
		int zN1 = zO0 + len - 1;
		int zG1 = zN1 + 2;
		int zCh1 = zN1 + chancelLen;
		int yO = 6;
		int yC = top >= 50 ? 14 : 12;
		int yCh = yC - 3;
		int yA = yCh - 2;
		d.body(cx - ho, zO0, cx + ho, zN1);
		d.eave(yO);
		Mat wall = d.mat(Role.WALL);
		Mat frame = d.mat(Role.FRAME);
		Mat beam = d.mat(Role.BEAM);
		// shingles: the roof where it is timber, else the boards of the gables (a stave church is never slated)
		Role roofRole = d.mat(Role.ROOF).has(Form.LOG) ? Role.ROOF : d.mat(Role.GABLE).has(Form.LOG) ? Role.GABLE : Role.ROOF;
		Mat roof = d.mat(roofRole);
		Mat trim = d.mat(Role.ROOF_TRIM).has(Form.LOG) ? d.mat(Role.ROOF_TRIM) : d.mat(Role.FRAME);
		Mat plinth = Stone.stony(d, Role.PLINTH) ? d.mat(Role.PLINTH) : d.mat(Role.FOUNDATION);
		Paint stave = wall.has(Form.LOG) ? wall.log(Direction.Axis.Y) : wall.full();

		// the main entrance on the street, before the porch
		d.entrance(cx, 0, 0);
		// --- the ground: the whole plot levelled, a sill of fieldstone under every wall
		Grounds.podium(d, 0, 0, w - 1, dp - 1, 0, false);
		Grounds.footings(d, cx - hg, zG0, cx + hg, zG1, -1);
		Grounds.footings(d, cx - hk, zN1, cx + hk, zCh1 + apseR, -1);
		Floors.boards(d, cx - hg, zG0, cx + hg, zG1, -1);
		Floors.boards(d, cx - hk, zN1, cx + hk, zCh1 + apseR, -1);

		// --- the gallery round the foot: arched posts every other cell, a rail between, a lean-to of shingles
		int hk0 = cx - hk;
		int hk1 = cx + hk;
		for (Side s : Side.of(cx - hg, zG0, cx + hg, zG1)) {
			for (int i = 0; i <= s.last(); i++) {
				int x = s.x(i);
				int z = s.z(i);
				if (s.out() == Dirs.BACK && x >= hk0 && x <= hk1) {
					// the chancel runs out through the gallery here
					continue;
				}
				boolean post = i % 2 == 0 || s.isCorner(i);
				boolean opening = s.out() == Dirs.FRONT && Math.abs(x - cx) <= 1 || s.out() == Dirs.BACK && Math.abs(x - cx) <= hk;
				try (var st = d.stage(StageKind.FRAME)) {
					if (post && !(opening && !s.isCorner(i) && Math.abs(x - cx) <= 1 && s.out() == Dirs.FRONT)) {
						for (int y = 0; y <= 2; y++) {
							d.set(x, y, z, frame.log(Direction.Axis.Y));
						}
					} else {
						// the arched head between posts: upside-down stairs shouldering into the plate
						d.set(x, 2, z, frame.stairsOrSlab(i % 2 == 1 ? s.along().getOpposite() : s.along(), Half.TOP));
					}
					d.set(x, 3, z, beam.log(s.axis()));
				}
				if (!post && !opening && d.isEmpty(x, 0, z)) {
					d.inStage(StageKind.EXTERIOR, () -> d.set(x, 0, z, d.mat(Role.RAILING).has(Form.FENCE) ? d.mat(Role.RAILING).fence()
						: frame.fence()));
				}
			}
		}
		int gx0 = cx - hg;
		int gx1 = cx + hg;
		int ox0 = cx - ho;
		int ox1 = cx + ho;
		Roofs.Field gallery = (x, z) -> {
			if (x < gx0 - 1 || x > gx1 + 1 || z < zG0 - 1 || z > zG1 + 1) {
				return Double.NaN;
			}
			if (x >= ox0 && x <= ox1 && z >= zO0 && z <= zN1 || x >= hk0 && x <= hk1 && z >= zN1) {
				return Double.NaN;
			}
			int dd = Math.min(Math.min(x - (gx0 - 1), (gx1 + 1) - x), Math.min(z - (zG0 - 1), (zG1 + 1) - z));
			return 1 + Math.min(3, dd);
		};
		Roofs.raster(d, gallery, 3, Math.max(0, gx0 - 1), Math.max(0, zG0 - 1), Math.min(w - 1, gx1 + 1), Math.min(dp - 1, zG1 + 1), roof, trim,
			Habits.Ridge.SLAB);

		// --- the aisle walls: staves on a stone sill between corner posts, a plate
		try (var st = d.stage(StageKind.WALLS)) {
			for (int[] c : Draft.ring(ox0, zO0, ox1, zN1)) {
				boolean corner = (c[0] == ox0 || c[0] == ox1) && (c[1] == zO0 || c[1] == zN1);
				for (int y = 0; y <= yO; y++) {
					Paint p = y == 0 ? plinth.full() : corner ? frame.log(Direction.Axis.Y)
						: y == yO ? beam.log(c[1] == zO0 || c[1] == zN1 ? Direction.Axis.X : Direction.Axis.Z) : stave;
					d.set(c[0], y, c[1], p);
				}
			}
		}
		// the core: columns down both sides of the nave carrying the tall clerestory walls on a beam
		int kx0 = cx - hc;
		int kx1 = cx + hc;
		try (var st = d.stage(StageKind.FRAME)) {
			for (int z = zO0; z <= zN1; z++) {
				for (int x : new int[]{kx0, kx1}) {
					boolean column = (z - zO0) % 2 == 0 || z == zN1;
					for (int y = 0; y < yO; y++) {
						if (column || z == zO0 || z == zN1) {
							d.set(x, y, z, frame.log(Direction.Axis.Y));
						}
					}
					d.set(x, yO, z, beam.log(Direction.Axis.Z));
				}
			}
		}
		try (var st = d.stage(StageKind.WALLS)) {
			for (int[] c : Draft.ring(kx0, zO0, kx1, zN1)) {
				boolean corner = (c[0] == kx0 || c[0] == kx1) && (c[1] == zO0 || c[1] == zN1);
				boolean side = c[0] == kx0 || c[0] == kx1;
				for (int y = side ? yO + 1 : 0; y <= yC; y++) {
					Paint p = corner ? frame.log(Direction.Axis.Y) : y == yC ? beam.log(c[1] == zO0 || c[1] == zN1 ? Direction.Axis.X : Direction.Axis.Z)
						: y == 0 ? plinth.full() : stave;
					d.set(c[0], y, c[1], p);
				}
			}
		}
		d.carve(ox0 + 1, 0, zO0 + 1, ox1 - 1, yO - 1, zN1 - 1);
		d.carve(kx0 + 1, 0, zO0 + 1, kx1 - 1, yC + 10, zN1 - 1);
		// the arcade between the columns: open bays from the nave into the aisles
		for (int z = zO0 + 1; z < zN1; z++) {
			if ((z - zO0) % 2 == 0) {
				continue;
			}
			for (int x : new int[]{kx0, kx1}) {
				int fz = z;
				d.inStage(StageKind.WALLS, () -> {
					for (int y = 0; y < yO - 1; y++) {
						d.set(x, y, fz, Blocks.AIR.defaultBlockState());
					}
					d.set(x, yO - 1, fz, frame.stairsOrSlab(Dirs.FRONT, Half.TOP));
				});
			}
		}
		// little round lights high in the core, above the aisle roofs
		int lightY = yC - 1;
		for (int z = zO0 + 2; z < zN1 - 1; z += 3) {
			for (int x : new int[]{kx0, kx1}) {
				int fz = z;
				d.inStage(StageKind.OPENINGS, () -> d.set(x, lightY, fz, d.mat(Role.GLASS).pane()));
			}
		}

		// --- the aisles' lean-to roofs, rising from the aisle walls to the core
		Roofs.Field aisles = (x, z) -> {
			if (x < ox0 - 1 || x > ox1 + 1 || z < zO0 - 1 || z > zN1 + 1) {
				return Double.NaN;
			}
			if (x >= kx0 && x <= kx1) {
				return Double.NaN;
			}
			int dd = x < kx0 ? x - (ox0 - 1) : (ox1 + 1) - x;
			return 1 + dd;
		};
		Roofs.Roof ar0 = Roofs.raster(d, aisles, yO + 1, Math.max(0, ox0 - 1), zO0 - 1, Math.min(w - 1, ox1 + 1), zN1 + 1, roof, trim, Habits.Ridge.SLAB);
		// the aisles' ends closed in staves up to their roof
		try (var st = d.stage(StageKind.WALLS)) {
			for (int z : new int[]{zO0, zN1}) {
				for (int x = ox0 + 1; x <= ox1 - 1; x++) {
					if (x >= kx0 && x <= kx1 || !ar0.covers(x, z)) {
						continue;
					}
					for (int y = yO + 1; y < ar0.bottomY(x, z); y++) {
						d.set(x, y, z, stave);
					}
				}
			}
		}

		// --- the core's steep roof, its gables of staves, dragons on its apexes
		double pitch = 2.0;
		double half = hc + 1.5;
		int turretRoom = 18;
		if (yC + 1 + pitch * half > top - turretRoom) {
			pitch = Math.max(1.0, (top - turretRoom - yC - 1) / half);
		}
		Roofs.Field core = Roofs.gable(kx0 - 1, zO0 - 1, kx1 + 1, zN1 + 1, Direction.Axis.Z, Roofs.Profile.straight(pitch));
		Roofs.Roof cr = Roofs.raster(d, core, yC + 1, Math.max(0, kx0 - 1), zO0 - 1, Math.min(w - 1, kx1 + 1), zN1 + 1, roof, trim, Habits.Ridge.TRIM);
		Shapes.support(d, cr, roof.full());
		gableFill(d, cr, kx0, kx1, zO0, yC, stave);
		gableFill(d, cr, kx0, kx1, zN1, yC, stave);
		// the west gable carved: a band of boards across it, a round light over the band
		try (var st = d.stage(StageKind.WALLS)) {
			for (int x = kx0; x <= kx1; x++) {
				if (cr.covers(x, zO0) && cr.bottomY(x, zO0) > yC + 3) {
					d.set(x, yC + 3, zO0, beam.log(Direction.Axis.X));
				}
			}
		}
		if (cr.covers(cx, zO0) && cr.bottomY(cx, zO0) > yC + 7) {
			try (var st = d.stage(StageKind.OPENINGS)) {
				d.set(cx, yC + 5, zO0, d.mat(Role.GLASS).pane());
				d.set(cx - 1, yC + 5, zO0, d.mat(Role.GLASS).pane());
				d.set(cx + 1, yC + 5, zO0, d.mat(Role.GLASS).pane());
				d.set(cx, yC + 4, zO0, d.mat(Role.GLASS).pane());
				d.set(cx, yC + 6, zO0, d.mat(Role.GLASS).pane());
				d.set(cx, yC + 5, zO0, frame.log(Direction.Axis.Z));
			}
		}
		dragon(d, cx, cr.surfaceY(cx, zO0 - 1) + 1, zO0 - 1, Dirs.FRONT, trim, frame);
		dragon(d, cx, cr.surfaceY(cx, zN1 + 1) + 1, zN1 + 1, Dirs.BACK, trim, frame);
		// tie beams across the core and lanterns hung from them
		for (int z = zO0 + 2; z < zN1; z += 3) {
			try (var st = d.stage(StageKind.FRAME)) {
				for (int x = kx0 + 1; x < kx1; x++) {
					d.set(x, yC, z, beam.log(Direction.Axis.X));
				}
			}
			Halls.lanternUnder(d, cx, 0, z, 6);
		}

		// --- the roof turret on the core's ridge: a louvred belfry under a skirt, then the spire
		int tz = (zO0 + zN1) / 2;
		int tw = hc >= 4 ? 5 : 3;
		int t0x = cx - tw / 2;
		int t0z = tz - tw / 2;
		int ridge = cr.ridgeY;
		int yT0 = ridge - 1;
		// its posts stand on the core's plate, hidden in the roof
		try (var st = d.stage(StageKind.FRAME)) {
			for (int[] c : new int[][]{{t0x, t0z}, {t0x + tw - 1, t0z}, {t0x, t0z + tw - 1}, {t0x + tw - 1, t0z + tw - 1}}) {
				for (int y = yC + 1; y < yT0; y++) {
					if (d.isEmpty(c[0], y, c[1])) {
						d.set(c[0], y, c[1], frame.log(Direction.Axis.Y));
					}
				}
			}
		}
		// the turret's walls boarded down to the roof's slope round it
		try (var st = d.stage(StageKind.WALLS)) {
			for (int[] c : Draft.ring(t0x, t0z, t0x + tw - 1, t0z + tw - 1)) {
				if (!cr.covers(c[0], c[1])) {
					continue;
				}
				for (int y = cr.surfaceY(c[0], c[1]) + 1; y < yT0; y++) {
					d.set(c[0], y, c[1], stave);
				}
			}
		}
		int belfryH = 4;
		try (var st = d.stage(StageKind.ROOF)) {
			for (Side s : Side.of(t0x, t0z, t0x + tw - 1, t0z + tw - 1)) {
				for (int i = 0; i <= s.last(); i++) {
					for (int k = 0; k < belfryH; k++) {
						int y = yT0 + k;
						Paint p;
						if (s.isCorner(i)) {
							p = frame.log(Direction.Axis.Y);
						} else if (k == 0 || k == belfryH - 1) {
							p = beam.log(s.axis());
						} else {
							p = d.mat(Role.SHUTTER).has(Form.TRAPDOOR) ? d.mat(Role.SHUTTER).trapdoor(s.out().getOpposite(), Half.TOP, true) : frame.fence();
						}
						d.set(s.x(i), y, s.z(i), p);
					}
				}
			}
			for (int z = t0z + 1; z < t0z + tw - 1; z++) {
				for (int x = t0x + 1; x < t0x + tw - 1; x++) {
					d.set(x, yT0, z, beam.full());
				}
			}
		}
		dev.voidmc.hearth.build.kit.landmark.Details.bell(d, cx, yT0 + belfryH - 2, tz, Dirs.FRONT);
		int yS = yT0 + belfryH;
		Crowns.Mats mats = new Crowns.Mats(roof, trim, Stone.dressing(d), frame, Stone.of(d, roofRole));
		int peak;
		if (tw >= 5) {
			peak = Crowns.crown(d, Crowns.Kind.STAVE, t0x, t0z, t0x + tw - 1, t0z + tw - 1, yS, top, mats);
		} else {
			// a skirt of shingles round the belfry's head, then a slender spire
			skirt(d, t0x, t0z, t0x + tw - 1, t0z + tw - 1, yS, roof);
			peak = Crowns.crown(d, Crowns.Kind.SPIRE, t0x, t0z, t0x + tw - 1, t0z + tw - 1, yS + 1, Math.min(top, yS + 13), mats);
		}

		// --- the chancel, lower and narrower, with its own little turret, and the round apse
		int hx0 = cx - hk;
		int hx1 = cx + hk;
		try (var st = d.stage(StageKind.WALLS)) {
			for (int[] c : Draft.ring(hx0, zN1, hx1, zCh1)) {
				if (c[1] == zN1) {
					continue;
				}
				boolean corner = (c[0] == hx0 || c[0] == hx1) && c[1] == zCh1;
				for (int y = 0; y <= yCh; y++) {
					d.set(c[0], y, c[1], y == 0 ? plinth.full() : corner ? frame.log(Direction.Axis.Y)
						: y == yCh ? beam.log(c[1] == zCh1 ? Direction.Axis.X : Direction.Axis.Z) : stave);
				}
			}
		}
		d.carve(hx0 + 1, 0, zN1 + 1, hx1 - 1, yCh - 1, zCh1 - 1);
		// the chancel arch through the core's east wall
		try (var st = d.stage(StageKind.WALLS)) {
			for (int x = hx0 + 1; x <= hx1 - 1; x++) {
				for (int y = 0; y < Math.min(yO, yCh - 1); y++) {
					d.set(x, y, zN1, Blocks.AIR.defaultBlockState());
				}
			}
		}
		Roofs.Field chf = Roofs.gable(hx0 - 1, zN1, hx1 + 1, zCh1 + 1, Direction.Axis.Z, Roofs.Profile.straight(pitch));
		Roofs.Roof chr = Roofs.raster(d, chf, yCh + 1, Math.max(0, hx0 - 1), zN1 + 1, Math.min(w - 1, hx1 + 1), Math.min(dp - 1, zCh1 + 1), roof, trim,
			Habits.Ridge.TRIM);
		gableFill(d, chr, hx0, hx1, zCh1, yCh, stave);
		int cz1 = Math.min(dp - 1, zCh1 + 1);
		dragon(d, cx, chr.surfaceY(cx, cz1) + 1, cz1, Dirs.BACK, trim, frame);
		// the chancel's little turret: a louvred post and a needle
		int ctz = (zN1 + zCh1) / 2 + 1;
		int ctY = chr.surfaceY(cx, ctz) + 1;
		if (ctY > 0 && ctY + 8 < top) {
			try (var st = d.stage(StageKind.ROOF)) {
				for (int y = yCh + 1; y < ctY + 2; y++) {
					d.set(cx, y, ctz, frame.log(Direction.Axis.Y));
				}
				for (Direction dir : Dirs.HORIZONTAL) {
					int x = cx + Dirs.dx(dir);
					int z = ctz + Dirs.dz(dir);
					if (d.isEmpty(x, ctY + 1, z)) {
						d.set(x, ctY + 1, z, d.mat(Role.SHUTTER).has(Form.TRAPDOOR) ? d.mat(Role.SHUTTER).trapdoor(dir.getOpposite(), Half.TOP, true)
							: frame.fence());
					}
				}
			}
			Crowns.crown(d, Crowns.Kind.SPIRE, cx - 1, ctz - 1, cx + 1, ctz + 1, ctY + 2, Math.min(top, ctY + 9), mats);
		}
		// the apse: a round wall beyond the chancel's east end, a cone over it
		for (int[] c : Shapes.ring(cx, zCh1, apseR)) {
			if (c[1] <= zCh1) {
				continue;
			}
			try (var st = d.stage(StageKind.WALLS)) {
				for (int y = 0; y <= yA; y++) {
					d.set(c[0], y, c[1], y == 0 ? plinth.full() : stave);
				}
			}
		}
		try (var st = d.stage(StageKind.WALLS)) {
			for (int x = cx - apseR + 1; x <= cx + apseR - 1; x++) {
				for (int y = 0; y < yA - 1; y++) {
					d.set(x, y, zCh1, Blocks.AIR.defaultBlockState());
				}
			}
		}
		Roofs.Field cone = Roofs.cone(cx, zCh1, apseR + 1, 2.0);
		Roofs.Field apseCone = (x, z) -> z > zCh1 ? cone.h(x, z) : Double.NaN;
		Roofs.Roof ar = Roofs.raster(d, apseCone, yA + 1, Math.max(0, cx - apseR - 1), zCh1 + 1, Math.min(w - 1, cx + apseR + 1),
			Math.min(dp - 1, zCh1 + apseR + 1), roof, trim, Habits.Ridge.FULL);
		Shapes.support(d, ar, roof.full());

		// --- the porch before the west door: carved posts, a steep gable, a dragon
		try (var st = d.stage(StageKind.FRAME)) {
			for (int x : new int[]{cx - 1, cx + 1}) {
				for (int z = 0; z < zG0; z++) {
					for (int y = 0; y <= 2; y++) {
						d.set(x, y, z, z == 0 ? frame.log(Direction.Axis.Y) : y == 2 ? beam.log(Direction.Axis.Z) : Paint.of(Blocks.AIR.defaultBlockState()));
					}
				}
			}
			d.set(cx, 2, 0, frame.stairsOrSlab(Dirs.LEFT, Half.TOP));
		}
		Roofs.Field porch = Roofs.gable(cx - 2, 0, cx + 2, zG0, Direction.Axis.Z, Roofs.Profile.straight(1.5));
		Roofs.Roof pr = Roofs.raster(d, porch, 3, Math.max(0, cx - 2), 0, Math.min(w - 1, cx + 2), zG0, roof, trim, Habits.Ridge.TRIM);
		try (var st = d.stage(StageKind.WALLS)) {
			for (int x = cx - 1; x <= cx + 1; x++) {
				if (pr.covers(x, 0)) {
					for (int y = 3; y < pr.bottomY(x, 0); y++) {
						d.set(x, y, 0, stave);
					}
				}
			}
		}
		dragon(d, cx, pr.surfaceY(cx, 0) + 1, 0, Dirs.FRONT, trim, frame);

		// --- the doors: the great west door under the gallery, side doors from the gallery into the aisles
		Side west = Side.of(ox0, zO0, ox1, zN1, Dirs.FRONT);
		try (var st = d.stage(StageKind.OPENINGS)) {
			// a carved portal: a round head of the trim over the door
			int wi = west.index(cx);
			d.set(west.x(wi - 1), 3, zO0, frame.log(Direction.Axis.Y));
			d.set(west.x(wi + 1), 3, zO0, frame.log(Direction.Axis.Y));
			d.set(cx, 3, zO0, trim.stairsOrSlab(Dirs.BACK, Half.TOP));
		}
		Openings.door(d, west, cx, 0, 1, new Openings.DoorOpts(true, false, true));
		for (Side s : new Side[]{Side.of(ox0, zO0, ox1, zN1, Dirs.LEFT), Side.of(ox0, zO0, ox1, zN1, Dirs.RIGHT)}) {
			int dz = zO0 + 2 + (len - 4) / 2;
			if ((dz - zO0) % 2 == 0) {
				dz++;
			}
			Openings.door(d, s, dz, 0, 1, Openings.DoorOpts.BACK);
		}

		// --- within: pews down the nave, the altar and lectern in the chancel, candles in the apse
		Sacred.pews(d, kx0 + 1, kx1 - 1, zO0 + 2, zN1 - 2, 0, cx, Dirs.BACK, false);
		Sacred.altar(d, cx, 0, zCh1 - 1, Dirs.FRONT, hk >= 2, cx + 1 <= hx1 - 1 && hk >= 2 ? cx + 1 : Integer.MIN_VALUE);
		if (hk < 2) {
			dev.voidmc.hearth.build.kit.Furniture.workstation(d, dev.voidmc.hearth.api.JobType.PRIEST, cx + 1, 0, zN1 - 1, Dirs.FRONT);
		}
		Sacred.candles(d, cx, 0, zCh1 + apseR - 1, 3);
		for (int z = zN1 + 2; z < zCh1; z += 2) {
			Halls.lanternUnder(d, cx, 0, z, 3);
		}
		// lanterns in the aisles, hung from the aisle walls' plates
		for (int z = zO0 + 2; z < zN1; z += 4) {
			for (int x : new int[]{kx0 - 2, kx1 + 2}) {
				Halls.lanternUnder(d, x, 0, z, 1);
			}
		}
		d.poi(PoiType.MEETING_POINT, cx, 0, zO0 + 1, Dirs.BACK);
		d.poi(PoiType.VIEWPOINT, cx, 0, Math.max(0, zG0 - 1), Dirs.FRONT);

		// --- outside: the way to the street, a churchyard where the close leaves room, snow
		Groundwork.approach(d, cx, 0, 0);
		Halls.lamp(d, cx - 3, 0, 0, 2);
		Halls.lamp(d, cx + 3, 0, 0, 2);
		int yardZ0 = zG1 + 2;
		int sideW = (w - 1) - (cx + hg) - 1;
		if (sideW >= 3) {
			Sacred.churchyard(d, cx + hg + 2, zG0, w - 1, dp - 2, 0, true, Dirs.FRONT);
		}
		if (dp - 2 - (zCh1 + apseR + 2) >= 3) {
			Sacred.churchyard(d, 1, zCh1 + apseR + 2, cx - 2, dp - 2, 0, true, Dirs.FRONT);
		}
		if (h.snow() || d.site().snowy()) {
			Garden.snowBanks(d, cx - hg, zG0, cx + hg, zG1, 0, cx);
		}
		Groundwork.clear(d, 0, 0, w - 1, dp - 1, Math.min(top, Math.max(peak, ridge) + 2));
		dev.voidmc.hearth.build.kit.refine.Voids.finish(d);
	}

	/** Staves filling a gable end at row z under a roof, from over the plate to the roof's underside. */
	private static void gableFill(Draft d, Roofs.Roof r, int x0, int x1, int z, int plate, Paint stave) {
		try (var s = d.stage(StageKind.WALLS)) {
			for (int x = x0; x <= x1; x++) {
				if (!r.covers(x, z)) {
					continue;
				}
				for (int y = plate + 1; y < r.bottomY(x, z); y++) {
					d.set(x, y, z, stave);
				}
			}
		}
	}

	/** A ring of the roof's stairs round a square's head, a pent skirt shedding off the belfry. */
	private static void skirt(Draft d, int x0, int z0, int x1, int z1, int y, Mat roof) {
		try (var s = d.stage(StageKind.ROOF)) {
			for (int[] c : Draft.ring(x0 - 1, z0 - 1, x1 + 1, z1 + 1)) {
				if (!d.allowed(c[0], y, c[1])) {
					continue;
				}
				Direction in = Dirs.nearest((x0 + x1) / 2.0 - c[0], (z0 + z1) / 2.0 - c[1]);
				d.set(c[0], y, c[1], roof.stairsOrSlab(in, Half.BOTTOM));
			}
			for (int z = z0; z <= z1; z++) {
				for (int x = x0; x <= x1; x++) {
					d.set(x, y, z, roof.full());
				}
			}
		}
	}

	/**
	 * A dragon rearing from a gable's apex at (x, y, z): a neck of the trim rising outward, a head with a snout and a
	 * hanging jaw, the curl of its crest behind; facing {@code out}, away from the roof.
	 */
	static void dragon(Draft d, int x, int y, int z, Direction out, Mat trim, Mat frame) {
		if (y <= 0 || !d.allowed(x, y + 2, z)) {
			return;
		}
		int ox = x + Dirs.dx(out);
		int oz = z + Dirs.dz(out);
		try (var s = d.stage(StageKind.ROOF)) {
			d.set(x, y, z, frame.has(Form.FENCE) ? frame.fence() : trim.full());
			d.set(x, y + 1, z, trim.full());
			d.set(x, y + 2, z, trim.stairsOrSlab(out, Half.BOTTOM));
			if (d.allowed(ox, y + 1, oz) && d.isEmpty(ox, y + 1, oz)) {
				// the snout and the jaw hanging under it
				d.set(ox, y + 1, oz, trim.stairsOrSlab(out.getOpposite(), Half.TOP));
				if (d.isEmpty(ox, y, oz) && d.allowed(ox, y, oz)) {
					d.set(ox, y, oz, frame.has(Form.TRAPDOOR) ? frame.trapdoor(out.getOpposite(), Half.TOP, true) : trim.slab(SlabType.TOP));
				}
			}
		}
	}
}
