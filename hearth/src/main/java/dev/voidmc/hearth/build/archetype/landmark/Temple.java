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
import dev.voidmc.hearth.build.kit.Roofs;
import dev.voidmc.hearth.build.kit.Side;
import dev.voidmc.hearth.build.kit.landmark.Crowns;
import dev.voidmc.hearth.build.kit.landmark.Details;
import dev.voidmc.hearth.build.kit.landmark.Eaves;
import dev.voidmc.hearth.build.kit.landmark.Grounds;
import dev.voidmc.hearth.build.kit.landmark.Shapes;
import dev.voidmc.hearth.build.kit.landmark.Stone;
import dev.voidmc.hearth.build.kit.landmark.Tower;
import dev.voidmc.hearth.build.style.Form;
import dev.voidmc.hearth.build.style.Habits;
import dev.voidmc.hearth.build.style.Role;
import dev.voidmc.hearth.build.terrain.Groundwork;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * {@code hearth:landmark/temple}: the great temple of a warm or eastern land, in the form its culture builds:
 * <ul>
 *   <li><b>colonnade</b> (mediterranean, and any culture without a form of its own): a temple on a stepped base, a
 *   peristyle of columns under a frieze of glazed tiles and a low pedimented roof, a cella with its statue between
 *   braziers, an altar in the forecourt between trees;</li>
 *   <li><b>dome</b> (desert): a walled court with arcades and a basin, a minaret at its corner with a balcony, and a
 *   sanctuary under a great dome on a windowed drum, corner turrets with little domes, a tall portal framing a deep arch,
 *   rugs and lanterns within and a niche in the far wall;</li>
 *   <li><b>pyramid</b> (mesa, tropical): a stepped pyramid of banded terraces, a steep stair up its front between sloped
 *   balustrades to a shrine on the summit with braziers at its corners, stelae at the stair's foot;</li>
 *   <li><b>hall</b> (karst, bayou, wisteria): a temple hall on a stone podium (or on piles over the wet) behind a gate
 *   and stone lanterns, posts and lattices under a double eave, a veranda round it, an altar with its figure;</li>
 *   <li><b>round</b> (steppe): a sun temple: a ring wall banded at its head, a round platform, a ring of columns round a
 *   round sanctuary under one great cone, a fire at its heart and a gilded sun on the apex.</li>
 * </ul>
 * Every form has an altar (ALTAR), the priest's lectern (PRIEST job site), seats and a meeting point.
 *
 * <p>Params (all optional): {@code form} ({@code colonnade}, {@code dome}, {@code pyramid}, {@code hall},
 * {@code round}), {@code variant}. As a rebuild of a chapel the planner also sends {@code replaces}.
 */
public final class Temple implements Archetype {
	public static final ArchetypeInfo INFO = new ArchetypeInfo(Hearth.id("landmark/temple"), ArchetypeCategory.LANDMARK, Set.of(JobType.PRIEST), 0,
		15, 21, 17, 25, 16, 30, SettlementTier.CITY,
		Set.of(Culture.DESERT, Culture.MEDITERRANEAN, Culture.MESA, Culture.STEPPE, Culture.KARST, Culture.TROPICAL, Culture.BAYOU),
		Set.of(), List.of(), 1, 1.0);

	enum Shape {
		COLONNADE, DOME, PYRAMID, HALL, ROUND
	}

	@Override
	public ArchetypeInfo info() {
		return INFO;
	}

	@Override
	public void draw(Draft d) {
		Shape shape = shape(d);
		int peak = switch (shape) {
			case COLONNADE -> colonnade(d);
			case DOME -> dome(d);
			case PYRAMID -> pyramid(d);
			case HALL -> hall(d);
			case ROUND -> round(d);
		};
		// the hollows left between ceilings and roofs, sealed with a rubble core as the masons laid them
		dev.voidmc.hearth.build.kit.refine.Voids.sealAll(d, 0, dev.voidmc.hearth.build.kit.landmark.Stone.footing(d).full(), StageKind.ROOF);
		Groundwork.clear(d, 0, 0, d.frontage() - 1, d.depth() - 1, Math.min(d.maxHeight() - 1, peak + 2));
		dev.voidmc.hearth.build.kit.refine.Voids.finish(d);
	}

	static Shape shape(Draft d) {
		String p = d.param("form", "");
		for (Shape s : Shape.values()) {
			if (s.name().equalsIgnoreCase(p)) {
				return s;
			}
		}
		Culture c = d.style().culture();
		Habits h = d.habits();
		if (c != null) {
			switch (c) {
				case MEDITERRANEAN:
					return Shape.COLONNADE;
				case DESERT:
					return Shape.DOME;
				case MESA:
				case TROPICAL:
					return Shape.PYRAMID;
				case STEPPE:
					return Shape.ROUND;
				case KARST:
				case BAYOU:
				case WISTERIA:
					return Shape.HALL;
				default:
					break;
			}
		}
		if (h.roof().flare()) {
			return Shape.HALL;
		}
		if (h.roof().weight(Habits.RoofForm.FLAT) > 3) {
			return Shape.DOME;
		}
		if (h.plan().round() >= 0.5) {
			return Shape.ROUND;
		}
		return Shape.COLONNADE;
	}

	// ================================================================================================ colonnade

	private static int colonnade(Draft d) {
		int w = d.frontage();
		int dp = d.depth();
		int top = d.maxHeight() - 1;
		int s = w >= 19 ? 3 : 2;                      // steps of the base; the stylobate's standing level
		int n = (w - 2 * (s - 1) + 1) / 2;            // columns across the front: an even number
		n = Math.max(4, n - (n & 1));
		int ringW = 2 * n - 1;
		int cx0 = (w - ringW) / 2;
		int cx1 = cx0 + ringW - 1;
		int xc = cx0 + n - 1;                         // the axis, on the middle intercolumniation
		int cz1 = dp - s;
		int ringD = cz1 - (s + 3) + 1;
		ringD -= (ringD & 1) == 0 ? 1 : 0;
		int cz0 = cz1 - ringD + 1;
		Stone body = Stone.ashlar(d);
		Stone dress = Stone.dressing(d);
		Stone found = Stone.footing(d);
		d.body(cx0, cz0, cx1, cz1);
		int colH = Math.min(6, Math.max(4, (top - s - 6) / 2));
		int yArch = s + colH;                          // the architrave
		d.eave(yArch + 2);

		// the stepped base
		Grounds.podium(d, 0, 0, w - 1, dp - 1, 0, false);
		try (var st = d.stage(StageKind.FOUNDATION)) {
			for (int z = cz0 - (s - 1); z <= cz1 + (s - 1); z++) {
				for (int x = cx0 - (s - 1); x <= cx1 + (s - 1); x++) {
					if (x < 0 || x >= w || z < 0 || z >= dp) {
						continue;
					}
					int out = Math.max(Math.max(cx0 - x, x - cx1), Math.max(cz0 - z, z - cz1));
					int topRow = out <= 0 ? s - 1 : s - 1 - out;
					for (int y = Math.min(-1, d.site().ground(x, z) - 1); y <= topRow; y++) {
						d.set(x, y, z, y == topRow ? dress.full() : found.full());
					}
				}
			}
		}
		// the peristyle: columns on a base, shafts, capitals; the entablature over them
		Paint glass = d.mat(Role.GLASS).pane();
		Block accent = d.mat(Role.ACCENT).main(Form.FULL);
		try (var st = d.stage(StageKind.FRAME)) {
			for (int[] c : Draft.ring(cx0, cz0, cx1, cz1)) {
				boolean column = (c[1] == cz0 || c[1] == cz1) ? (c[0] - cx0) % 2 == 0 : (c[1] - cz0) % 2 == 0;
				if (!column) {
					continue;
				}
				d.set(c[0], s, c[1], dress.full());
				for (int y = s + 1; y < yArch - 1; y++) {
					d.set(c[0], y, c[1], body.full());
				}
				d.set(c[0], yArch - 1, c[1], dress.full());
			}
		}
		try (var st = d.stage(StageKind.WALLS)) {
			int k = 0;
			for (int[] c : Draft.ring(cx0, cz0, cx1, cz1)) {
				d.set(c[0], yArch, c[1], dress.full());
				boolean over = (c[1] == cz0 || c[1] == cz1) ? (c[0] - cx0) % 2 == 0 : (c[1] - cz0) % 2 == 0;
				d.set(c[0], yArch + 1, c[1], over || accent == null ? dress.full() : Paint.of(accent.defaultBlockState()));
				k++;
			}
			// the ceiling over the walk and the cella
			for (int z = cz0 + 1; z < cz1; z++) {
				for (int x = cx0 + 1; x < cx1; x++) {
					d.set(x, yArch + 1, z, d.mat(Role.CEILING).full());
				}
			}
		}
		// the cornice: a course of upside-down stairs round the entablature
		try (var st = d.stage(StageKind.WALLS)) {
			java.util.Set<Long> fp = new java.util.HashSet<>();
			for (int z = cz0; z <= cz1; z++) {
				for (int x = cx0; x <= cx1; x++) {
					fp.add(Shapes.cell(x, z));
				}
			}
			for (int[] c : Shapes.around(fp)) {
				if (d.allowed(c[0], yArch + 2, c[1])) {
					Direction in = c[0] < cx0 ? Dirs.RIGHT : c[0] > cx1 ? Dirs.LEFT : c[1] < cz0 ? Dirs.BACK : Dirs.FRONT;
					d.set(c[0], yArch + 2, c[1], dress.stairsOrSlab(in, Half.TOP));
				}
			}
			for (int[] c : Draft.ring(cx0, cz0, cx1, cz1)) {
				d.set(c[0], yArch + 2, c[1], dress.full());
			}
		}
		// the roof: a low gable along the depth, pediments front and back, acroteria
		Mat roof = d.mat(Role.ROOF);
		Roofs.Field gable = Roofs.gable(cx0 - 1, cz0 - 1, cx1 + 1, cz1 + 1, Direction.Axis.Z, Roofs.Profile.straight(0.5));
		Roofs.Roof r = Roofs.raster(d, gable, yArch + 3, Math.max(0, cx0 - 1), Math.max(-1, cz0 - 1), Math.min(w - 1, cx1 + 1), Math.min(dp - 1, cz1 + 1),
			roof, d.mat(Role.ROOF_TRIM), Habits.Ridge.TRIM);
		try (var st = d.stage(StageKind.WALLS)) {
			for (int x = cx0; x <= cx1; x++) {
				for (int z : new int[]{cz0, cz1}) {
					if (!r.covers(x, z)) {
						continue;
					}
					for (int y = yArch + 3; y < r.bottomY(x, z); y++) {
						d.set(x, y, z, y == yArch + 3 && accent != null && Math.abs(x - xc) <= 1 ? Paint.of(accent.defaultBlockState()) : body.full());
					}
				}
			}
			for (int z = cz0 + 1; z < cz1; z++) {
				for (int x = cx0 + 1; x < cx1; x++) {
					if (r.covers(x, z) && r.bottomY(x, z) - 1 >= yArch + 3) {
						d.set(x, yArch + 2, z, body.full());
					}
				}
			}
		}
		int peak = r.ridgeY;
		try (var st = d.stage(StageKind.ROOF)) {
			for (int z : new int[]{Math.max(-1, cz0 - 1), Math.min(dp - 1, cz1 + 1)}) {
				int sy = r.surfaceY(xc, z);
				if (sy != Integer.MIN_VALUE && d.allowed(xc, sy + 1, z)) {
					d.set(xc, sy + 1, z, dress.full());
					if (dress.has(Form.SLAB)) {
						d.set(xc, sy + 2, z, dress.slab(SlabType.BOTTOM));
					}
					peak = Math.max(peak, sy + 2);
				}
			}
		}

		// the cella: walls inside the walk, antae and two columns in front, a tall door
		int lx0 = cx0 + 2;
		int lx1 = cx1 - 2;
		// a porch of two or three rows before the cella, a walk behind it where the temple is deep enough
		int lz0 = cz0 + (ringD >= 13 ? 4 : 3);
		int lz1 = cz1 - (ringD >= 11 ? 2 : 1);
		try (var st = d.stage(StageKind.WALLS)) {
			for (int[] c : Draft.ring(lx0, lz0, lx1, lz1)) {
				boolean corner = Crowns.isCorner(c, lx0, lz0, lx1, lz1);
				for (int y = s; y <= yArch; y++) {
					d.set(c[0], y, c[1], corner ? dress.full() : body.full());
				}
			}
			for (int z = cz0 + 1; z < lz0; z++) {
				for (int x : new int[]{lx0, lx1}) {
					for (int y = s; y <= yArch; y++) {
						d.set(x, y, z, dress.full());
					}
				}
			}
		}
		try (var st = d.stage(StageKind.FRAME)) {
			for (int x : new int[]{xc - 2, xc + 2}) {
				if (x > lx0 && x < lx1) {
					d.set(x, s, cz0 + 1, dress.full());
					for (int y = s + 1; y < yArch - 1; y++) {
						d.set(x, y, cz0 + 1, body.full());
					}
					d.set(x, yArch - 1, cz0 + 1, dress.full());
				}
			}
		}
		d.carve(lx0 + 1, s, lz0 + 1, lx1 - 1, yArch, lz1 - 1);
		d.carve(cx0 + 1, s, cz0 + 1, cx1 - 1, yArch, lz0 - 1);
		Side cellaFront = Side.of(lx0, lz0, lx1, lz1, Dirs.FRONT);
		try (var st = d.stage(StageKind.OPENINGS)) {
			for (int y = s + 2; y < Math.min(yArch, s + 4); y++) {
				d.set(xc, y, lz0, glass);
			}
			d.set(xc - 1, s + 3, lz0, dress.full());
			d.set(xc + 1, s + 3, lz0, dress.full());
		}
		Openings.door(d, cellaFront, xc, s, 1, Openings.DoorOpts.MAIN);
		// side windows high in the cella walls
		try (var st = d.stage(StageKind.OPENINGS)) {
			for (int z = lz0 + 2; z < lz1 - 1; z += 3) {
				d.set(lx0, s + 3, z, glass);
				d.set(lx1, s + 3, z, glass);
			}
		}
		// within: the statue on its pedestal between braziers, benches, lanterns
		try (var st = d.stage(StageKind.INTERIOR)) {
			int sz = lz1 - 1;
			d.set(xc, s, sz, dress.full());
			d.set(xc, s + 1, sz, body.full());
			d.set(xc, s + 2, sz, Blocks.GOLD_BLOCK.defaultBlockState());
			for (int x : new int[]{xc - 2, xc + 2}) {
				if (x > lx0) {
					d.set(x, s, sz, dress.full());
					d.set(x, s + 1, sz, States.lit(Blocks.CAMPFIRE, true).trySetValue(BlockStateProperties.SIGNAL_FIRE, false));
				}
			}
		}
		d.poi(PoiType.ALTAR, xc, s, lz1 - 2, Dirs.BACK);
		// the priest's lectern just inside the door, benches down the far side
		Furniture.workstation(d, JobType.PRIEST, lx0 + 1, s, lz0 + 1, Dirs.BACK);
		for (int z = lz0 + 2; z < lz1 - 2; z += 2) {
			Furniture.chair(d, lx1 - 1, s, z, Dirs.RIGHT);
		}
		Furniture.hangingLantern(d, xc, yArch + 1, (lz0 + lz1) / 2, 1);
		Furniture.hangingLantern(d, xc, yArch + 1, cz0 + 2, 1);
		d.poi(PoiType.MEETING_POINT, xc, s, cz0 + 2, Dirs.BACK);

		// the forecourt: the way up from the street, an altar on the axis, trees and pots
		Groundwork.approach(d, xc, s, cz0 + 1 > lz0 - 1 ? cz0 + 1 : cz0 + 2);
		int fz = cz0 - s;
		if (fz >= 2) {
			int az = Math.max(0, fz / 2 - 1);
			for (int x : new int[]{xc - 3, xc + 3}) {
				if (Grounds.standable(d, x, 0, az)) {
					try (var st = d.stage(StageKind.EXTERIOR)) {
						d.set(x, 0, az, dress.full());
						d.set(x, 1, az, Details.candles(3, true));
					}
				}
			}
			for (int x : new int[]{1, w - 2}) {
				if (Grounds.standable(d, x, 0, az)) {
					Garden.pot(d, x, 0, az);
				}
			}
		}
		return Math.max(peak, yArch + 3);
	}

	// ================================================================================================ dome

	private static int dome(Draft d) {
		int w = d.frontage();
		int dp = d.depth();
		int top = d.maxHeight() - 1;
		Stone body = Stone.masonry(d);
		Stone dress = Stone.dressing(d);
		// the domes sheathed in copper gone turquoise, as tiled domes shine over a sand-coloured town (or in its roof stone)
		Stone sheet = d.chance(0.85) ? Stone.copper(d) : Stone.sheet(d);
		Block accent = d.mat(Role.ACCENT).main(Form.FULL);
		Paint glass = d.mat(Role.GLASS).pane();
		int hx0 = 1;
		int hx1 = w - 2 - ((w - 2) % 2 == 0 ? 1 : 0);
		int xc = (hx0 + hx1) / 2;
		int hallW = hx1 - hx0 + 1;
		int hz1 = dp - 2;
		int hz0 = Math.max(7, hz1 - Math.min(hallW, dp - 8) + 1);
		int hH = Math.min(7, Math.max(5, top / 4));
		d.body(hx0, hz0, hx1, hz1);
		d.eave(hH);
		Grounds.podium(d, 0, 0, w - 1, dp - 1, 0, true);
		Grounds.footings(d, hx0, hz0, hx1, hz1, -1);

		// the hall: walls, a coursed head, a crenellated parapet, a flat roof
		try (var st = d.stage(StageKind.WALLS)) {
			for (int[] c : Draft.ring(hx0, hz0, hx1, hz1)) {
				for (int y = 0; y <= hH; y++) {
					d.set(c[0], y, c[1], y == hH || y == 0 ? dress.full() : body.full());
				}
				if ((c[0] + c[1] & 1) == 0) {
					d.set(c[0], hH + 1, c[1], body.full());
				} else if (dress.has(Form.SLAB)) {
					d.set(c[0], hH + 1, c[1], dress.slab(SlabType.BOTTOM));
				}
			}
		}
		try (var st = d.stage(StageKind.ROOF)) {
			for (int z = hz0 + 1; z < hz1; z++) {
				for (int x = hx0 + 1; x < hx1; x++) {
					d.set(x, hH, z, d.mat(Role.ROOF).full());
				}
			}
		}
		try (var st = d.stage(StageKind.FLOORS)) {
			for (int z = hz0 + 1; z < hz1; z++) {
				for (int x = hx0 + 1; x < hx1; x++) {
					d.set(x, -1, z, d.mat(Role.PAVING).full());
				}
			}
		}
		d.carve(hx0 + 1, 0, hz0 + 1, hx1 - 1, hH - 1, hz1 - 1);
		// arched windows along the sides
		for (Side sd : Side.of(hx0, hz0, hx1, hz1)) {
			if (sd.out() == Dirs.FRONT) {
				continue;
			}
			for (int i = 2; i < sd.length() - 2; i += 3) {
				Details.arch(d, sd, i, 1, 2, Math.min(3, hH - 3), dress, glass);
			}
		}
		// the great dome on its drum, over the middle of the hall
		double zcH = (hz0 + hz1) / 2.0;
		double rd = Math.min(5.0, (Math.min(hallW, hz1 - hz0 + 1) - 1) / 2.0 - 1.0);
		int drumTop = hH + 4;
		try (var st = d.stage(StageKind.WALLS)) {
			for (int y = hH + 1; y <= drumTop; y++) {
				for (int[] c : Shapes.ring(xc, zcH, rd)) {
					boolean light = y > hH + 1 && y < drumTop && (Math.abs(c[0] - xc) < 0.6 || Math.abs(c[1] - zcH) < 0.6);
					d.set(c[0], y, c[1], light ? glass : y == drumTop ? dress.full() : body.full());
				}
			}
		}
		Details.ringCourse(d, xc, zcH, rd, drumTop, dress);
		int rows = Math.max(3, Math.min((int) Math.ceil(rd * 1.25), top - drumTop - 4));
		double[] radii = new double[rows];
		for (int i = 0; i < rows; i++) {
			double t = (i + 0.5) / rows;
			radii[i] = Math.max(0.45, (rd + 0.3) * Math.sqrt(Math.max(0, 1 - t * t)) - 0.1);
		}
		try (var st = d.stage(StageKind.ROOF)) {
			Shapes.turned(d, xc, zcH, drumTop + 1, radii, sheet.full(), sheet);
		}
		int yl = drumTop + 1 + rows;
		int zc = (int) Math.round(zcH);
		// the hall rises open into the drum, lit by its lights, under a vault at the drum's head (the dome over it a sealed
		// shell)
		java.util.Set<Long> drum = new java.util.HashSet<>();
		for (int[] c : Shapes.ring(xc, zcH, rd)) {
			drum.add(Shapes.cell(c[0], c[1]));
		}
		try (var st = d.stage(StageKind.ROOF)) {
			for (int[] c : Shapes.disc(xc, zcH, rd)) {
				if (!drum.contains(Shapes.cell(c[0], c[1]))) {
					d.set(c[0], hH, c[1], Blocks.AIR.defaultBlockState());
					d.set(c[0], drumTop, c[1], dress.full());
				}
			}
		}
		int peak = yl;
		try (var st = d.stage(StageKind.ROOF)) {
			if (yl + 2 <= top) {
				d.set(xc, yl, zc, dress.wallOrFull());
				d.set(xc, yl + 1, zc, Blocks.GOLD_BLOCK.defaultBlockState());
				d.set(xc, yl + 2, zc, Details.rod());
				peak = yl + 2;
			}
		}
		// corner turrets with little domes
		for (int[] c : new int[][]{{hx0, hz0}, {hx1, hz0}, {hx0, hz1}, {hx1, hz1}}) {
			try (var st = d.stage(StageKind.WALLS)) {
				for (int y = hH + 1; y <= hH + 2; y++) {
					d.set(c[0], y, c[1], dress.full());
				}
			}
			try (var st = d.stage(StageKind.ROOF)) {
				d.set(c[0], hH + 3, c[1], sheet.has(Form.SLAB) ? sheet.full() : sheet.full());
				d.set(c[0], hH + 4, c[1], Details.rod());
			}
		}
		// the portal: a tall frame standing proud of the front, a deep arch in it, the door at its back
		int py = Math.min(top - 2, hH + 2);
		int pz = hz0 - 1;
		try (var st = d.stage(StageKind.WALLS)) {
			for (int x = xc - 3; x <= xc + 3; x++) {
				for (int y = 0; y <= py; y++) {
					boolean border = Math.abs(x - xc) == 3 || y == py || y == py - 1;
					boolean recess = Math.abs(x - xc) <= 1 && y <= 4;
					if (recess) {
						d.set(x, y, pz, Blocks.AIR.defaultBlockState());
					} else {
						d.set(x, y, pz, border && accent != null && Math.abs(x - xc) < 3 ? Paint.of(accent.defaultBlockState()) : border ? dress.full() : body.full());
					}
				}
				if ((x - xc & 1) == 0 && Math.abs(x - xc) <= 3) {
					d.set(x, py + 1, pz, dress.full());
				}
			}
			d.set(xc - 1, 4, pz, dress.stairsOrSlab(Dirs.RIGHT, Half.TOP));
			d.set(xc + 1, 4, pz, dress.stairsOrSlab(Dirs.LEFT, Half.TOP));
			d.set(xc, 5, pz, dress.full());
			for (int x = xc - 1; x <= xc + 1; x += 2) {
				for (int y = 0; y < 4; y++) {
					d.set(x, y, hz0, dress.wallOrFull());
				}
			}
			for (int y = 2; y <= 4; y++) {
				d.set(xc, y, hz0, y == 4 ? dress.full() : glass);
			}
		}
		Side hallFront = Side.of(hx0, hz0, hx1, hz1, Dirs.FRONT);
		Openings.door(d, hallFront, xc, 0, 1, Openings.DoorOpts.BACK);

		// within: rugs, lanterns, the niche in the far wall, the priest's lectern
		Mat cloth = d.mat(Role.CLOTH);
		try (var st = d.stage(StageKind.INTERIOR)) {
			for (int z = hz0 + 2; z < hz1 - 2; z++) {
				for (int x = hx0 + 2; x <= hx1 - 2; x++) {
					if (x == xc) {
						continue;
					}
					Block carpet = cloth.block(Form.CARPET, x / 3, 0, z / 2);
					d.set(x, 0, z, carpet.defaultBlockState());
				}
			}
			for (int y = 0; y <= 2; y++) {
				d.set(xc, y, hz1, Blocks.AIR.defaultBlockState());
				d.set(xc, y, hz1 + 1, accent != null ? Paint.of(accent.defaultBlockState()) : dress.full());
			}
			d.set(xc, 3, hz1, dress.full());
		}
		d.poi(PoiType.ALTAR, xc, 0, hz1 - 1, Dirs.BACK);
		Furniture.workstation(d, JobType.PRIEST, xc + 2, 0, hz1 - 1, Dirs.FRONT);
		for (int z = hz0 + 2; z < hz1; z += 3) {
			int links = Math.max(0, hH - 5);
			if (d.isSolid(xc, hH, z)) {
				Furniture.hangingLantern(d, xc, hH, z, links);
			} else {
				// under the drum: on a long chain from the vault
				for (int y = hH + 1; y < top; y++) {
					if (!d.isEmpty(xc, y, z)) {
						if (d.isSolid(xc, y, z)) {
							dev.voidmc.hearth.build.kit.light.Lighting.chandelier(d, xc, z, y, hH - 1 - links);
						}
						break;
					}
				}
			}
		}
		d.poi(PoiType.SEAT, xc - 2, 0, hz0 + 3, Dirs.BACK);

		// the court: walls round it, arcades along its sides, a basin, a gate to the street, the minaret
		int cz1 = pz - 1;
		boolean left = d.chance(0.5);
		try (var st = d.stage(StageKind.WALLS)) {
			for (int z = 0; z <= cz1; z++) {
				for (int x : new int[]{0, w - 1}) {
					for (int y = 0; y <= 3; y++) {
						d.set(x, y, z, y == 3 ? dress.full() : body.full());
					}
				}
			}
			for (int x = 0; x < w; x++) {
				if (Math.abs(x - xc) <= 1) {
					d.set(x, 3, 0, dress.full());
					d.set(x, 4, 0, (x - xc & 1) == 0 ? dress.full() : body.full());
					continue;
				}
				for (int y = 0; y <= 3; y++) {
					d.set(x, y, 0, y == 3 ? dress.full() : body.full());
				}
			}
		}
		d.entrance(xc, 0, -1);
		// arcades: columns two in from the side walls, a slab roof over the walks
		try (var st = d.stage(StageKind.FRAME)) {
			for (int z = 2; z < cz1; z += 2) {
				for (int x : new int[]{2, w - 3}) {
					for (int y = 0; y < 3; y++) {
						d.set(x, y, z, dress.wallOrFull());
					}
				}
			}
		}
		try (var st = d.stage(StageKind.ROOF)) {
			for (int z = 1; z < cz1; z++) {
				for (int x : new int[]{1, 2, w - 3, w - 2}) {
					d.set(x, 3, z, x == 2 || x == w - 3 ? dress.full() : d.mat(Role.ROOF).slab(SlabType.BOTTOM));
				}
			}
		}
		int bz = Math.max(3, cz1 / 2 + 1);
		try (var st = d.stage(StageKind.EXTERIOR)) {
			for (int z = bz - 1; z <= bz + 1; z++) {
				for (int x = xc - 1; x <= xc + 1; x++) {
					boolean rim = z != bz || x != xc;
					if (cz1 - 1 > bz + 1) {
						d.set(x, 0, z, rim ? dress.slabOrNothing(SlabType.BOTTOM) : Paint.of(Blocks.AIR.defaultBlockState()));
						d.set(x, -1, z, rim ? dress.full() : Paint.of(Blocks.WATER.defaultBlockState()));
					}
				}
			}
		}
		d.poi(PoiType.MEETING_POINT, xc, 0, Math.max(1, bz - 2), Dirs.BACK);
		// the minaret at a front corner of the court
		int mx0 = left ? 0 : w - 5;
		if (cz1 >= 5 && top >= hH + 12) {
			peak = Math.max(peak, minaret(d, mx0, 0, top, body, dress, sheet));
		}
		Grounds.pave(d, 1, 1, w - 2, cz1, 0, d.mat(Role.PAVING).full());
		return peak;
	}

	/** A minaret: a square shaft with a door into the court, a corbelled balcony, a lantern stage, a small dome. */
	private static int minaret(Draft d, int x0, int z0, int top, Stone body, Stone dress, Stone sheet) {
		Tower t = new Tower(x0, z0, 5, body, dress, dress, false);
		int yBal = Math.min(top - 8, (int) Math.round(top * 0.65));
		t.plinth(d, 0, 0, false);
		t.shaft(d, 1, yBal);
		t.course(d, yBal - 1, false);
		// the balcony: a corbelled walk round the shaft with a railing
		try (var st = d.stage(StageKind.WALLS)) {
			for (int[] c : Draft.ring(t.x0 - 1, t.z0 - 1, t.x1 + 1, t.z1 + 1)) {
				if (d.allowed(c[0], yBal + 1, c[1])) {
					d.set(c[0], yBal, c[1], dress.full());
					d.set(c[0], yBal + 1, c[1], dress.wallOrFull());
				}
			}
		}
		// courses and slits up the shaft
		for (int y = 6; y < yBal - 3; y += 6) {
			t.course(d, y, false);
			for (Side f : t.faces()) {
				if (f.out() != Dirs.BACK || y > 6) {
					Details.slit(d, f, f.middle(), y + 2, 2, dress, null);
				}
			}
		}
		// the upper shaft: a slender stage over the balcony, arched lights in each face, a corbelled cornice, the dome
		int yl = yBal + 1;
		Tower lant = new Tower(x0 + 1, z0 + 1, 3, dress, dress, dress, false);
		int upH = Math.min(5, top - yl - 5);
		try (var st = d.stage(StageKind.WALLS)) {
			for (int y = yl; y < yl + upH; y++) {
				for (int[] c : Draft.ring(lant.x0, lant.z0, lant.x1, lant.z1)) {
					boolean mid = c[0] == lant.cx || c[1] == lant.cz;
					boolean light = mid && y >= yl + 1 && y <= yl + 2;
					d.set(c[0], y, c[1], light ? Paint.of(Blocks.AIR.defaultBlockState()) : y == yl + upH - 1 ? dress.full() : body.full());
				}
			}
			d.set(lant.cx, yl + upH - 1, lant.cz, dress.full());
		}
		lant.course(d, yl + upH - 1, false);
		int peak;
		int yd = yl + upH;
		try (var st = d.stage(StageKind.ROOF)) {
			Shapes.turned(d, lant.cx, lant.cz, yd, new double[]{1.4, 1.2, 0.7, 0.45}, sheet.full(), sheet);
			peak = Math.min(top, yd + 4);
			d.set(lant.cx, peak, lant.cz, Details.rod());
		}
		// the stair to the balcony, the door from the court
		Side door = t.face(Dirs.BACK);
		t.floor(d, -1, dress.full());
		t.floor(d, yBal, dress.full());
		try (var st = d.stage(StageKind.OPENINGS)) {
			for (Side f : t.faces()) {
				if (d.allowed(f.x(f.middle()), yBal + 1, f.z(f.middle()))) {
					d.set(f.x(f.middle()), yBal + 1, f.z(f.middle()), Blocks.AIR.defaultBlockState());
					d.set(f.x(f.middle()), yBal + 2, f.z(f.middle()), Blocks.AIR.defaultBlockState());
				}
			}
		}
		Openings.door(d, door, door.column(door.middle()), 0, 1, Openings.DoorOpts.BACK);
		t.stair(d, 0, yBal + 1, dress, dress.full(), 5);
		t.hollow(d, 0, yBal - 1);
		d.poi(PoiType.VIEWPOINT, t.x0 - 1 >= 0 ? t.x0 - 1 : t.x1 + 1, yBal + 1, t.z1, Dirs.FRONT);
		return peak;
	}

	// ================================================================================================ pyramid

	private static int pyramid(Draft d) {
		int w = d.frontage();
		int dp = d.depth();
		int top = d.maxHeight() - 1;
		Stone body = Stone.masonry(d);
		Stone dress = Stone.dressing(d);
		Stone found = Stone.footing(d);
		int xc = (w - 1) / 2;
		int topW = w >= 17 && dp >= 21 ? 7 : 5;
		// the stair runs a block per step, so the pyramid's height is bounded by the court in front of it (a landing at its foot)
		int tiers = Math.min(Math.min((dp - topW) / 3, (w - 2 - topW) / 2 + 1), (top - 8) / 2);
		tiers = Math.max(2, tiers);
		int b = topW + 2 * (tiers - 1);
		int bx0 = xc - b / 2;
		int bx1 = bx0 + b - 1;
		int bz1 = dp - 2;
		int bz0 = bz1 - b + 1;
		int ytop = 2 * tiers;
		int tz0 = bz0 + tiers - 1;
		d.body(bx0, bz0, bx1, bz1);
		d.eave(ytop);
		Grounds.podium(d, 0, 0, w - 1, dp - 1, 0, true);
		// the terraces: each a ring two thick, its lower course in the walling and its head in the dressing
		for (int k = 0; k < tiers; k++) {
			int x0 = bx0 + k;
			int x1 = bx1 - k;
			int z0 = bz0 + k;
			int z1 = bz1 - k;
			for (int z = z0; z <= z1; z++) {
				for (int x = x0; x <= x1; x++) {
					boolean outer = x <= x0 + 1 || x >= x1 - 1 || z <= z0 + 1 || z >= z1 - 1 || k == tiers - 1;
					if (!outer) {
						continue;
					}
					try (var st = d.stage(k == 0 ? StageKind.FOUNDATION : StageKind.WALLS)) {
						if (k == 0) {
							for (int y = Math.min(-1, d.site().ground(x, z) - 1); y < 0; y++) {
								d.set(x, y, z, found.full());
							}
						}
						d.set(x, 2 * k, z, body.full());
						d.set(x, 2 * k + 1, z, dress.full());
					}
				}
			}
		}
		// the stair up the front, between sloped balustrades, carried on a solid core
		try (var st = d.stage(StageKind.WALLS)) {
			for (int j = 0; j <= ytop - 2; j++) {
				int z = tz0 - 1 - j;
				int row = ytop - 2 - j;
				if (z < 0) {
					break;
				}
				for (int x = xc - 1; x <= xc + 1; x++) {
					d.set(x, row, z, dress.stairsOrSlab(Dirs.BACK, Half.BOTTOM));
					for (int y = -1; y < row; y++) {
						d.set(x, y, z, body.full());
					}
				}
				if (row >= 2) {
					for (int x : new int[]{xc - 2, xc + 2}) {
						for (int y = -1; y <= row + 1; y++) {
							d.set(x, y, z, y == row + 1 ? dress.full() : body.full());
						}
					}
				}
			}
		}
		// the shrine on the summit
		int sx0 = xc - 2;
		int sx1 = xc + 2;
		int sz0 = tz0 + (topW - 5) / 2;
		int sz1 = sz0 + 4;
		int wallH = 4;
		try (var st = d.stage(StageKind.WALLS)) {
			for (int[] c : Draft.ring(sx0, sz0, sx1, sz1)) {
				boolean corner = Crowns.isCorner(c, sx0, sz0, sx1, sz1);
				for (int y = ytop; y < ytop + wallH; y++) {
					d.set(c[0], y, c[1], corner || y == ytop + wallH - 1 ? dress.full() : body.full());
				}
			}
			for (int y = ytop; y < ytop + 3; y++) {
				d.set(xc, y, sz0, Blocks.AIR.defaultBlockState());
			}
		}
		d.carve(sx0 + 1, ytop, sz0 + 1, sx1 - 1, ytop + wallH - 1, sz1 - 1);
		int peak;
		boolean flat = d.habits().roof().weight(Habits.RoofForm.FLAT) >= d.habits().roof().weight(Habits.RoofForm.HIP);
		if (flat) {
			int deck = Roofs.flat(d, sx0, sz0, sx1, sz1, ytop + wallH, true);
			peak = deck + 1;
		} else {
			Roofs.Opts o = Roofs.Opts.of(d).withPitch(Math.max(1.0, d.habits().roof().pitch()));
			Roofs.Roof r = Roofs.build(d, sx0, sz0, sx1, sz1, ytop + wallH - 1, Habits.RoofForm.HIP, Direction.Axis.X, o, false, false).roof();
			peak = r.ridgeY;
			try (var st = d.stage(StageKind.ROOF)) {
				if (peak + 1 < top) {
					d.set(xc, peak + 1, (sz0 + sz1) / 2, Details.rod());
					peak++;
				}
			}
		}
		try (var st = d.stage(StageKind.INTERIOR)) {
			d.set(xc, ytop, sz1 - 1, dress.full());
			d.set(xc, ytop + 1, sz1 - 1, Details.candles(4, true));
			for (int[] c : new int[][]{{bx0 + tiers - 1, tz0}, {bx1 - tiers + 1, tz0}, {bx0 + tiers - 1, bz1 - tiers + 1}, {bx1 - tiers + 1, bz1 - tiers + 1}}) {
				if (c[0] < sx0 || c[0] > sx1 || c[1] < sz0 || c[1] > sz1) {
					d.set(c[0], ytop, c[1], States.lit(Blocks.CAMPFIRE, true).trySetValue(BlockStateProperties.SIGNAL_FIRE, false));
				}
			}
		}
		d.entrance(xc, 0, -1);
		d.poi(PoiType.DOOR, xc, ytop, sz0 - 1, Dirs.BACK);
		d.poi(PoiType.ALTAR, xc, ytop, sz1 - 2, Dirs.BACK);
		Furniture.workstation(d, JobType.PRIEST, sx0 + 1, ytop, sz1 - 1, Dirs.FRONT);
		d.poi(PoiType.VIEWPOINT, xc, ytop, tz0, Dirs.FRONT);
		if (flat) {
			Furniture.hangingLantern(d, xc, ytop + wallH, (sz0 + sz1) / 2, 0);
		}
		// stelae at the stair's foot, the way paved to the street
		int footZ = Math.max(0, tz0 - 1 - (ytop - 2));
		for (int x : new int[]{xc - 4, xc + 4}) {
			int z = Math.max(0, footZ - 1);
			if (Grounds.standable(d, x, 0, z)) {
				try (var st = d.stage(StageKind.EXTERIOR)) {
					d.set(x, 0, z, dress.full());
					d.set(x, 1, z, body.full());
					d.set(x, 2, z, dress.full());
				}
			}
		}
		Grounds.pave(d, xc - 1, 0, xc + 1, footZ - 1, 0, d.mat(Role.PAVING).full());
		d.poi(PoiType.MEETING_POINT, xc, 0, Math.max(0, footZ - 1), Dirs.BACK);
		d.poi(PoiType.SEAT, xc - 1, ytop, sz0 + 1, Dirs.BACK);
		return Math.min(top, peak);
	}

	// ================================================================================================ hall

	private static int hall(Draft d) {
		int w = d.frontage();
		int dp = d.depth();
		int top = d.maxHeight() - 1;
		Stone dress = Stone.dressing(d);
		Stone found = Stone.footing(d);
		Mat frame = d.mat(Role.FRAME);
		Mat beam = d.mat(Role.BEAM);
		Mat wall = d.mat(Role.WALL);
		Mat shutter = d.mat(Role.SHUTTER);
		boolean flare = d.habits().roof().flare() || d.habits().roof().weight(Habits.RoofForm.PAGODA) > 0;
		boolean stone = Stone.stony(d, Role.FOUNDATION);
		int xc = (w - 1) / 2;
		int pz0 = dp >= 21 ? 5 : 3;
		int px0 = 1;
		int px1 = w - 2;
		int pz1 = dp - 2;
		int ph = 2;
		int hx0 = px0 + 1;
		int hx1 = px1 - 1;
		if ((hx1 - hx0) % 2 == 1) {
			hx1--;
		}
		xc = (hx0 + hx1) / 2;
		int hz0 = pz0 + 3;
		int hz1 = pz1 - 1;
		int wallH = 5;
		d.body(hx0, hz0, hx1, hz1);
		d.eave(ph + wallH);
		d.entrance(xc, 0, -1);
		Grounds.podium(d, 0, 0, w - 1, dp - 1, 0, true);
		// the podium (or a deck on piles where the style builds in wood)
		Mat deck = d.mat(Role.FLOOR);
		try (var st = d.stage(StageKind.FOUNDATION)) {
			for (int z = pz0; z <= pz1; z++) {
				for (int x = px0; x <= px1; x++) {
					boolean edge = x == px0 || x == px1 || z == pz0 || z == pz1;
					if (stone) {
						for (int y = Math.min(-1, d.site().ground(x, z) - 1); y < ph; y++) {
							d.set(x, y, z, y == ph - 1 ? dress.full() : found.full());
						}
					} else {
						boolean pile = edge && ((x - px0) % 3 == 0 || (z - pz0) % 3 == 0) || Crowns.isCorner(new int[]{x, z}, px0, pz0, px1, pz1);
						if (pile) {
							for (int y = Math.min(-1, d.site().ground(x, z) - 2); y < ph - 1; y++) {
								d.set(x, y, z, d.mat(Role.STILT).log(Direction.Axis.Y));
							}
						}
						d.set(x, ph - 1, z, deck.full());
					}
				}
			}
		}
		try (var st = d.stage(StageKind.EXTERIOR)) {
			for (int[] c : Draft.ring(px0, pz0, px1, pz1)) {
				if (c[1] == pz0 && Math.abs(c[0] - xc) <= 2) {
					continue;
				}
				d.set(c[0], ph, c[1], stone ? dress.wallOrFull() : d.mat(Role.RAILING).fence());
			}
			for (int k = 0; k < ph; k++) {
				for (int x = xc - 2; x <= xc + 2; x++) {
					int z = pz0 - ph + k;
					d.set(x, k, z, stone ? dress.stairsOrSlab(Dirs.BACK, Half.BOTTOM) : deck.stairsOrSlab(Dirs.BACK, Half.BOTTOM));
					for (int y = -1; y < k; y++) {
						d.set(x, y, z, stone ? found.full() : d.mat(Role.STILT).full());
					}
				}
			}
		}
		// the veranda posts, the hall's walls of posts and lattices, a beam course
		int y0 = ph;
		try (var st = d.stage(StageKind.FRAME)) {
			for (int x = hx0; x <= hx1; x += 2) {
				for (int y = y0; y < y0 + wallH; y++) {
					d.set(x, y, pz0 + 1, frame.log(Direction.Axis.Y));
				}
			}
		}
		for (Side s : Side.of(hx0, hz0, hx1, hz1)) {
			int mid = s.middle();
			for (int i = 0; i < s.length(); i++) {
				boolean post = s.isCorner(i) || (i - mid) % 2 == 0;
				boolean light = !post && Math.abs(i - mid) <= 3;
				for (int y = y0; y < y0 + wallH; y++) {
					Paint p;
					StageKind st;
					if (post) {
						p = frame.log(Direction.Axis.Y);
						st = StageKind.FRAME;
					} else if (y == y0 + wallH - 1) {
						p = beam.log(s.axis());
						st = StageKind.FRAME;
					} else if (light && y > y0 && shutter.has(Form.TRAPDOOR)) {
						p = shutter.trapdoor(s.out().getOpposite(), Half.TOP, true);
						st = StageKind.OPENINGS;
					} else {
						p = wall.full();
						st = StageKind.WALLS;
					}
					try (var sc = d.stage(st)) {
						d.set(s.x(i), y, s.z(i), p);
					}
				}
			}
		}
		try (var st = d.stage(StageKind.FLOORS)) {
			for (int z = hz0 + 1; z < hz1; z++) {
				for (int x = hx0 + 1; x < hx1; x++) {
					d.set(x, y0 - 1, z, deck.full());
				}
			}
		}
		d.carve(hx0 + 1, y0, hz0 + 1, hx1 - 1, y0 + wallH + 1, hz1 - 1);
		Side hallFront = Side.of(hx0, hz0, hx1, hz1, Dirs.FRONT);
		Openings.door(d, hallFront, xc, y0, 1, Openings.DoorOpts.BACK);
		// the double eave: a skirt over the veranda, a clerestory, the upper hip
		Eaves.skirt(d, hx0, hz0, hx1, hz1, y0 + wallH - 1, 2, flare);
		int ux0 = hx0 + 1;
		int uz0 = hz0 + 1;
		int ux1 = hx1 - 1;
		int uz1 = hz1 - 1;
		int uy = y0 + wallH;
		for (Side s : Side.of(ux0, uz0, ux1, uz1)) {
			for (int i = 0; i < s.length(); i++) {
				for (int y = uy; y < uy + 2; y++) {
					boolean post = s.isCorner(i) || (i & 1) == 0;
					try (var sc = d.stage(post ? StageKind.FRAME : StageKind.WALLS)) {
						d.set(s.x(i), y, s.z(i), post ? frame.log(Direction.Axis.Y) : y == uy + 1 ? beam.log(s.axis()) : wall.full());
					}
				}
			}
		}
		int peak = Eaves.hipped(d, new int[]{ux0, uz0, ux1, uz1}, uy + 1, top, 2, flare, false);
		// within: the altar and its figure, candles, cushions, lanterns, a bell, the priest's lectern
		try (var st = d.stage(StageKind.INTERIOR)) {
			int az = hz1 - 1;
			d.set(xc, y0, az, dress.full());
			d.set(xc, y0 + 1, az, Blocks.GOLD_BLOCK.defaultBlockState());
			d.set(xc - 1, y0, az, dress.stairsOrSlab(Dirs.RIGHT, Half.BOTTOM));
			d.set(xc + 1, y0, az, dress.stairsOrSlab(Dirs.LEFT, Half.BOTTOM));
			d.set(xc - 2, y0, az, Details.candles(3, true));
			d.set(xc + 2, y0, az, Details.candles(3, true));
			Block carpet = d.mat(Role.CLOTH).mainOr(Form.CARPET, Blocks.CARPET.pick(net.minecraft.world.item.DyeColor.RED));
			for (int z = hz0 + 2; z < az - 1; z += 2) {
				d.set(xc - 2, y0, z, carpet.defaultBlockState());
				d.set(xc + 2, y0, z, carpet.defaultBlockState());
			}
		}
		d.poi(PoiType.ALTAR, xc, y0, hz1 - 2, Dirs.BACK);
		for (int z = hz0 + 2; z < hz1 - 2; z += 2) {
			d.poi(PoiType.SEAT, xc - 2, y0, z, Dirs.BACK);
		}
		Furniture.workstation(d, JobType.PRIEST, hx0 + 1, y0, hz1 - 1, Dirs.FRONT);
		for (int z = hz0 + 2; z < hz1; z += 3) {
			Furniture.hangingLantern(d, xc, uy + 1, z, 2);
		}
		Details.bell(d, hx1 - 1, y0 + wallH - 2, hz0 + 1, Dirs.FRONT);
		// the court: a gateway, stone lanterns, an incense burner, trees
		if (pz0 >= 4) {
			int gx0 = xc - 2;
			int gx1 = xc + 2;
			try (var st = d.stage(StageKind.FRAME)) {
				for (int x : new int[]{gx0, gx1}) {
					for (int y = 0; y <= 4; y++) {
						d.set(x, y, 0, y == 0 ? dress.full() : frame.log(Direction.Axis.Y));
					}
				}
				for (int x = gx0 - 1; x <= gx1 + 1; x++) {
					d.set(x, 4, 0, beam.log(Direction.Axis.X));
				}
			}
			Roofs.Field gf = Roofs.gable(gx0 - 2, -1, gx1 + 2, 1, Direction.Axis.X, Roofs.Profile.straight(0.5));
			Roofs.Roof gr = Roofs.raster(d, flare ? Roofs.flared(gf, gx0 - 2, -1, gx1 + 2, 1, 1, 2) : gf, 5, Math.max(0, gx0 - 2), -1,
				Math.min(w - 1, gx1 + 2), 1, d.mat(Role.ROOF), d.mat(Role.ROOF_TRIM), Habits.Ridge.FULL);
			Shapes.support(d, gr, d.mat(Role.ROOF).full());
		}
		int iz = Math.max(1, pz0 - ph - 2);
		if (Grounds.standable(d, xc, 0, iz) && iz < pz0 - ph) {
			try (var st = d.stage(StageKind.EXTERIOR)) {
				d.set(xc - 1, 0, iz, dress.stairsOrSlab(Dirs.RIGHT, Half.BOTTOM));
				d.set(xc + 1, 0, iz, dress.stairsOrSlab(Dirs.LEFT, Half.BOTTOM));
			}
		}
		for (int x : new int[]{xc - 4, xc + 4}) {
			int z = Math.max(1, pz0 - 2);
			if (Grounds.standable(d, x, 0, z)) {
				Details.stoneLantern(d, x, 0, z, dress);
			}
		}
		Grounds.pave(d, xc - 1, 0, xc + 1, pz0 - ph - 1, 0, d.mat(Role.PAVING).full());
		d.poi(PoiType.MEETING_POINT, xc, 0, Math.max(0, pz0 - ph - 1), Dirs.BACK);
		return peak;
	}

	// ================================================================================================ round

	private static int round(Draft d) {
		int w = d.frontage();
		int dp = d.depth();
		int top = d.maxHeight() - 1;
		Stone body = Stone.masonry(d);
		Stone dress = Stone.dressing(d);
		Stone found = Stone.footing(d);
		Block accent = d.mat(Role.ACCENT).main(Form.FULL);
		Block rays = States.block("minecraft:orange_glazed_terracotta", Blocks.GOLD_BLOCK);
		int r = (Math.min(w, dp) - 1) / 2;
		double cx = (w - 1) / 2.0;
		double cz = dp - 1 - r;
		int xc = (int) Math.round(cx);
		int zcI = (int) Math.round(cz);
		int rp = r - 2;                         // the platform
		int rc = Math.max(2, Math.min(4, rp - 2)); // the sanctuary's foot
		d.body(xc - rp, zcI - rp, xc + rp, zcI + rp);
		d.entrance(xc, 0, -1);
		Grounds.podium(d, 0, 0, w - 1, dp - 1, 0, true);
		// the ring wall, banded at its head, a gate to the street under a gilded sun
		try (var st = d.stage(StageKind.WALLS)) {
			int k0 = 0;
			for (int[] c : Shapes.ring(cx, cz, r)) {
				boolean gate = c[1] < cz && Math.abs(c[0] - cx) <= 1.01;
				boolean post = gate && Math.abs(c[0] - cx) > 0.99;
				int h = post ? 3 : 2;
				for (int y = 0; y <= h; y++) {
					if (gate && !post && y <= 2) {
						continue;
					}
					Paint p = y == h ? (k0 % 4 == 0 && accent != null ? Paint.of(accent.defaultBlockState()) : dress.full()) : body.full();
					d.set(c[0], y, c[1], p);
				}
				k0++;
			}
			int gz = zcI - r;
			d.set(xc, 3, gz, dress.full());
			d.set(xc, 4, gz, Blocks.GOLD_BLOCK.defaultBlockState());
		}
		// the round platform in two steps
		try (var st = d.stage(StageKind.FOUNDATION)) {
			for (int[] c : Shapes.disc(cx, cz, rp)) {
				double dist = Math.hypot(c[0] - cx, c[1] - cz);
				int topRow = dist > rp - 0.8 ? 0 : 1;
				for (int y = Math.min(-1, d.site().ground(c[0], c[1]) - 1); y <= topRow; y++) {
					d.set(c[0], y, c[1], y == topRow ? dress.full() : found.full());
				}
			}
		}
		// the steps up to the platform at the front
		int fz = zcI - rp;
		try (var st = d.stage(StageKind.EXTERIOR)) {
			for (int x = xc - 1; x <= xc + 1; x++) {
				d.set(x, 0, fz - 1, dress.stairsOrSlab(Dirs.BACK, Half.BOTTOM));
				d.set(x, 1, fz + 1, dress.stairsOrSlab(Dirs.BACK, Half.BOTTOM));
			}
		}
		Stone adobe = body.crisp();
		Mat beam = d.mat(Role.BEAM);
		int yb = 2;
		// the sanctuary's drum: adobe walls, buttresses carried up as pinnacles, beams bristling through the walls,
		// a crenellated head
		int rd = Math.max(3, rp - 2);
		int drumH = Math.min(7, Math.max(5, top / 4));
		List<int[]> ring = Shapes.ring(cx, cz, rd);
		try (var st = d.stage(StageKind.WALLS)) {
			for (int[] c : ring) {
				for (int y = yb; y < yb + drumH; y++) {
					d.set(c[0], y, c[1], adobe.full());
				}
				boolean merlon = (c[0] + c[1] & 1) == 0;
				if (merlon) {
					d.set(c[0], yb + drumH, c[1], adobe.full());
				}
			}
		}
		int k = 0;
		for (int[] c : ring) {
			Direction out = Dirs.nearest(c[0] - cx, c[1] - cz);
			int ox = c[0] + Dirs.dx(out);
			int oz = c[1] + Dirs.dz(out);
			boolean front = c[1] < cz && Math.abs(c[0] - cx) <= 1.5;
			if (k++ % 3 == 0 && !front && d.isEmpty(ox, yb, oz) && Grounds.inPlotBounds(d, ox, oz)) {
				// a buttress against the drum, its head a little cone above the parapet
				try (var st = d.stage(StageKind.FRAME)) {
					for (int y = yb; y <= yb + drumH; y++) {
						d.set(ox, y, oz, adobe.full());
					}
					d.set(ox, yb + drumH + 1, oz, adobe.full());
					d.set(ox, yb + drumH + 2, oz, adobe.wallOrFull());
				}
			} else if (!front) {
				// torons: the ends of the scaffold beams left in the wall, in rows
				try (var st = d.stage(StageKind.FRAME)) {
					for (int y = yb + 2; y < yb + drumH; y += 3) {
						if ((k & 1) == 0 && d.isEmpty(ox, y, oz) && Grounds.inPlotBounds(d, ox, oz)) {
							d.set(ox, y, oz, beam.log(out.getAxis()));
						}
					}
				}
			}
		}
		// the roof of the drum, the hall within
		try (var st = d.stage(StageKind.ROOF)) {
			for (int[] c : Shapes.disc(cx, cz, rd - 1)) {
				d.set(c[0], yb + drumH - 1, c[1], d.mat(Role.ROOF).full());
			}
		}
		for (int[] c : Shapes.disc(cx, cz, rd - 1)) {
			d.carve(c[0], yb, c[1], c[0], yb + drumH - 2, c[1]);
		}
		// the tall portal on the front: a frame standing proud of the drum, the door in its recess
		int doorZ = zcI;
		for (int[] c : ring) {
			if (c[0] == xc && c[1] < doorZ) {
				doorZ = c[1];
			}
		}
		try (var st = d.stage(StageKind.WALLS)) {
			for (int x = xc - 2; x <= xc + 2; x++) {
				for (int y = yb; y <= yb + drumH + 1; y++) {
					boolean opening = Math.abs(x - xc) <= 1 && y < yb + 4;
					int z = doorZ - 1;
					if (opening) {
						d.set(x, y, z, Blocks.AIR.defaultBlockState());
					} else {
						d.set(x, y, z, Math.abs(x - xc) == 2 || y >= yb + drumH ? dress.full() : adobe.full());
					}
				}
				if (Math.abs(x - xc) == 2) {
					d.set(x, yb + drumH + 2, doorZ - 1, adobe.wallOrFull());
				}
			}
			d.set(xc - 1, yb + 3, doorZ - 1, dress.stairsOrSlab(Dirs.RIGHT, Half.TOP));
			d.set(xc + 1, yb + 3, doorZ - 1, dress.stairsOrSlab(Dirs.LEFT, Half.TOP));
			d.set(xc, yb + drumH + 2, doorZ - 1, Blocks.GOLD_BLOCK.defaultBlockState());
		}
		Side doorSide = new Side(Dirs.FRONT, xc, doorZ, 1);
		try (var st = d.stage(StageKind.OPENINGS)) {
			d.set(xc, yb + 2, doorZ, d.mat(Role.GLASS).pane());
			d.set(xc - 1, yb, doorZ, adobe.full());
			d.set(xc + 1, yb, doorZ, adobe.full());
		}
		Openings.door(d, doorSide, xc, yb, 1, Openings.DoorOpts.BACK);
		// the sun tower rising from the drum's roof: a cone of adobe in rings, beams through it, the gilded sun on top
		int towerBase = yb + drumH;
		int rows = Math.max(6, Math.min(top - towerBase - 3, (int) Math.round(top * 0.45)));
		double r0 = Math.min(2.6, rd - 1.4);
		try (var st = d.stage(StageKind.WALLS)) {
			for (int i = 0; i < rows; i++) {
				double rr = Math.max(0.45, r0 * (1 - i / (double) rows) + 0.25);
				int y = towerBase + i;
				for (int[] c : Shapes.disc(cx, cz, rr)) {
					d.set(c[0], y, c[1], i % 4 == 3 ? dress.full() : adobe.full());
				}
				if (i % 3 == 1 && rr > 1.2) {
					for (Direction dir : Dirs.HORIZONTAL) {
						int ox = xc + Dirs.dx(dir) * ((int) Math.round(rr) + 1);
						int oz = zcI + Dirs.dz(dir) * ((int) Math.round(rr) + 1);
						if (d.isEmpty(ox, y, oz)) {
							d.set(ox, y, oz, beam.log(dir.getAxis()));
						}
					}
				}
			}
		}
		int peak = towerBase + rows;
		try (var st = d.stage(StageKind.ROOF)) {
			if (peak + 2 <= top) {
				d.set(xc, peak, zcI, Blocks.GOLD_BLOCK.defaultBlockState());
				d.set(xc, peak + 1, zcI, rays.defaultBlockState());
				d.set(xc - 1, peak + 1, zcI, rays.defaultBlockState());
				d.set(xc + 1, peak + 1, zcI, rays.defaultBlockState());
				d.set(xc, peak + 2, zcI, Details.rod());
				peak += 2;
			}
		}
		// within: a ring of pillars, rugs, the fire under the tower, the priest's lectern
		try (var st = d.stage(StageKind.FRAME)) {
			int q = 0;
			for (int[] c : Shapes.ring(cx, cz, Math.max(1.5, rd - 2.5))) {
				boolean byLectern = c[0] == xc && (c[1] == zcI + rd - 1 || c[1] == zcI + rd - 2);
				if (q++ % 2 == 0 && !(c[1] < cz && Math.abs(c[0] - cx) < 1.5) && !byLectern) {
					for (int y = yb; y < yb + drumH - 1; y++) {
						d.set(c[0], y, c[1], beam.log(Direction.Axis.Y));
					}
				}
			}
		}
		try (var st = d.stage(StageKind.INTERIOR)) {
			d.set(xc, yb - 1, zcI, dress.full());
			d.set(xc, yb, zcI, States.lit(Blocks.CAMPFIRE, true).trySetValue(BlockStateProperties.SIGNAL_FIRE, false));
			Block carpet = d.mat(Role.CLOTH).mainOr(Form.CARPET, Blocks.CARPET.pick(net.minecraft.world.item.DyeColor.ORANGE));
			for (int[] c : Shapes.ring(cx, cz, 1.6)) {
				if (d.isEmpty(c[0], yb, c[1]) && !(c[1] < cz && c[0] == xc)) {
					d.set(c[0], yb, c[1], carpet.defaultBlockState());
				}
			}
		}
		d.poi(PoiType.ALTAR, xc, yb, zcI - 1, Dirs.BACK);
		d.poi(PoiType.HEARTH, xc + 1, yb, zcI, Dirs.LEFT);
		d.poi(PoiType.SEAT, xc - 1, yb, zcI - 1, Dirs.BACK);
		Furniture.workstation(d, JobType.PRIEST, xc, yb, zcI + rd - 1, Dirs.FRONT);
		Furniture.hangingLantern(d, xc - 1, yb + drumH - 1, zcI + 1, 0);
		d.poi(PoiType.MEETING_POINT, xc, yb, doorZ - 2, Dirs.BACK);
		Grounds.pave(d, xc - 1, 0, xc + 1, zcI - rp - 2, 0, d.mat(Role.PATH).full());
		return Math.min(top, peak);
	}
}
