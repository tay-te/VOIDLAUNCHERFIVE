package dev.voidmc.hearth.build.archetype.civic;

import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.kit.Body;
import dev.voidmc.hearth.build.kit.Facade;
import dev.voidmc.hearth.build.kit.Furniture;
import dev.voidmc.hearth.build.kit.Garden;
import dev.voidmc.hearth.build.kit.Openings;
import dev.voidmc.hearth.build.kit.Roofs;
import dev.voidmc.hearth.build.kit.Side;
import dev.voidmc.hearth.build.kit.Walls;
import dev.voidmc.hearth.build.kit.civic.Arches;
import dev.voidmc.hearth.build.kit.civic.Fam;
import dev.voidmc.hearth.build.kit.civic.Grounds;
import dev.voidmc.hearth.build.kit.civic.Halls;
import dev.voidmc.hearth.build.kit.civic.Sacred;
import dev.voidmc.hearth.build.kit.civic.Towers;
import dev.voidmc.hearth.build.kit.civic.Tradition;
import dev.voidmc.hearth.build.style.Form;
import dev.voidmc.hearth.build.style.Habits.RoofForm;
import dev.voidmc.hearth.build.style.Role;
import dev.voidmc.hearth.build.terrain.Groundwork;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * The whitewashed chapel of the mediterranean, on its rise: a white nave under a low tiled roof, a bell wall pierced for
 * its bells over the front gable, arched windows with painted shutters, a square sanctuary behind crowned by a dome on a
 * drum (blue as the sea, in the colour of the town's cloth) or, in towns, a campanile with a tiled cap beside the front;
 * before it a paved terrace with cypresses and pots inside a low white wall.
 */
final class ChapelWhite {
	private ChapelWhite() {
	}

	static void draw(Draft d) {
		int w = d.frontage();
		int dp = d.depth();
		int tier = d.tier();
		int maxH = d.maxHeight();
		Tradition t = Tradition.MEDITERRANEAN;
		int nw = w >= 12 ? 9 : 7;
		boolean campanile = d.param("tower", tier >= 3) && w - nw >= 4;
		boolean dome = !campanile || d.chance(0.5);
		boolean yard = d.param("graveyard", true);
		boolean right = d.chance(0.5);
		int nx0 = campanile || yard && w - nw >= 4 ? (right ? 1 : w - 1 - nw) : (w - nw) / 2;
		int nx1 = nx0 + nw - 1;
		int cx = (nx0 + nx1) / 2;
		int zF = dp >= 15 ? 3 : 2;
		int cw = nw - 2;
		int cd = dome ? cw : 3;
		int z0 = zF;
		int z1 = Math.max(z0 + 7, dp - 2 - cd);
		int zc1 = Math.min(dp - 2, z1 + cd);
		int cx0 = cx - cw / 2;
		int cx1 = cx + cw / 2;
		int hn = tier <= 2 ? 6 : 7;
		int base = Grounds.raisedBase(d, nx0, nx1, z0);
		int plate = base + hn - 1;
		d.body(nx0, z0, nx1, z1);
		d.eave(plate);

		Body nave = new Body(nx0, z0, nx1, z1, base, new int[]{hn}, false, false);
		Body sanct = new Body(cx0, z1, cx1, zc1, base, new int[]{hn - 1}, false, false);
		Groundwork.footings(d, nx0, z0, nx1, z1, base - 1);
		Groundwork.footings(d, cx0, z1, cx1, zc1, base - 1);
		Groundwork.apron(d, nx0, z0, nx1, zc1, base, 2);
		Grounds.forecourt(d, 0, w - 1, z0 - 1, true);
		Halls.floor(d, nx0 + 1, z0 + 1, nx1 - 1, z1 - 1, base - 1, true);
		Halls.floor(d, cx0 + 1, z1, cx1 - 1, zc1 - 1, base - 1, true);
		Walls.storey(d, nave, 0, new Facade());
		Walls.storey(d, sanct, 0, new Facade());
		d.carve(nx0 + 1, base, z0 + 1, nx1 - 1, plate + 6, z1 - 1);
		d.carve(cx0 + 1, base, z1, cx1 - 1, plate + 6, zc1 - 1);
		Side back = nave.side(0, Dirs.BACK);
		Arches.arcade(d, back, new int[]{back.index(cx0), back.index(cx1)}, base, base + 4, Arches.Kind.ROUND, d.mat(Role.WALL_ACCENT).full(), null);
		// arched windows with painted shutters
		int[] bays = Arches.piers(z0, z1, 3);
		for (Direction sd : new Direction[]{Dirs.LEFT, Dirs.RIGHT}) {
			Side s = nave.side(0, sd);
			for (int k = 0; k + 1 < bays.length; k++) {
				int mid = (bays[k] + bays[k + 1]) / 2;
				if (campanile && (right ? sd == Dirs.RIGHT : sd == Dirs.LEFT) && mid <= z0 + 3) {
					continue;
				}
				Openings.window(d, s, mid, base + 2, 1, 2, Openings.WindowStyle.ARCHED, new Openings.WindowOpts(true, false, true, true));
			}
			Side ss = sanct.side(0, sd);
			if (zc1 - z1 >= 3) {
				Openings.window(d, ss, (z1 + zc1) / 2, base + 2, 1, 2, Openings.WindowStyle.ARCHED, new Openings.WindowOpts(false, false, true, true));
			}
		}
		Side front = nave.side(0, Dirs.FRONT);
		Halls.portal(d, front, cx, base, t, tier);
		Openings.window(d, front, nx0 + 1 + (nw >= 9 ? 1 : 0), base + 2, 1, 2, Openings.WindowStyle.ARCHED, new Openings.WindowOpts(true, false, true, true));
		Openings.window(d, front, nx1 - 1 - (nw >= 9 ? 1 : 0), base + 2, 1, 2, Openings.WindowStyle.ARCHED, new Openings.WindowOpts(true, false, true, true));

		// --- roofs: a low tiled gable on the nave, the sanctuary under a dome or a little hip
		Roofs.Opts o = Roofs.Opts.of(d).withPitch(0.5).withEaves(1, 0);
		Roofs.Roof roof = Roofs.build(d, nx0, z0, nx1, z1, plate, RoofForm.GABLE, Direction.Axis.Z, o, false, false).roof();
		int top = roof.ridgeY;
		int splate = base + hn - 2;
		if (dome) {
			// a drum on the sanctuary's walls, then the dome; blue in the town's cloth where it has a blue
			int size = cw;
			int dx0 = cx0;
			int dz0 = z1 + (zc1 - z1 + 1 - size) / 2;
			Roofs.flat(d, cx0, z1, cx1, zc1, splate, false);
			int drumTop = Math.max(splate + 2, roof.ridgeY - 1);
			Towers.shaft(d, dx0 + 1, dz0 + 1, size - 2, splate + 1, drumTop, t, -1);
			for (Side s : Side.of(dx0 + 1, dz0 + 1, dx0 + size - 2, dz0 + size - 2)) {
				int mid = s.middle();
				int y = drumTop - 1;
				d.inStage(StageKind.OPENINGS, () -> d.set(s.x(mid), y, s.z(mid), d.mat(Role.GLASS).pane()));
			}
			Fam blue = blueDome(d);
			int dtop = Towers.lathe(d, cx, dz0 + (size - 1) / 2.0, drumTop + 1, domeRadii(size - 2), blue, maxH);
			top = Math.max(top, Towers.finial(d, cx, dtop + 1, dz0 + (size - 1) / 2, maxH, false));
		} else {
			Roofs.build(d, cx0, z1 + 1, cx1, zc1, splate, RoofForm.HIP, Direction.Axis.Z, o, false, false);
		}
		// the bell wall over the front gable
		int apex = roof.surfaceY(cx, z0);
		if (apex != Integer.MIN_VALUE) {
			top = Math.max(top, Towers.bellWall(d, front, front.index(cx), apex, nw >= 9 ? 2 : 1, maxH));
		}
		if (campanile) {
			int size = 3;
			int tx0 = right ? nx1 + 1 : nx0 - size;
			if (tx0 >= 0 && tx0 + size <= w) {
				Groundwork.footings(d, tx0, z0, tx0 + size - 1, z0 + size - 1, base - 1);
				int yb = Math.max(roof.ridgeY + 1, plate + 3);
				int capRoom = 4;
				if (yb + 3 + capRoom > maxH - 1) {
					yb = Math.max(plate + 2, maxH - 1 - 3 - capRoom);
				}
				Towers.shaft(d, tx0, z0, size, base, yb - 1, t, plate);
				Towers.belfry(d, tx0, z0, size, yb, 3, t, true);
				top = Math.max(top, Towers.cap(d, tx0, z0, size, yb + 3, Towers.Cap.HIP, maxH));
			}
		}

		// --- inside
		Sacred.pews(d, nx0 + 1, nx1 - 1, z0 + 3, z1 - 2, base, cx, Dirs.BACK, nw >= 9);
		Sacred.altar(d, cx, base, zc1 - 1, Dirs.FRONT, cw >= 5, cx + 1);
		Halls.lanternUnder(d, cx, base, (z0 + z1) / 2, 2);
		d.inStage(StageKind.INTERIOR, () -> d.set(nx0 + 1, base, z0 + 1, Blocks.WATER_CAULDRON.defaultBlockState()
			.setValue(BlockStateProperties.LEVEL_CAULDRON, 3)));

		// --- the terrace: steps, a low white wall, cypresses and pots, graves beside
		if (base == 1) {
			Grounds.flight(d, cx - 1, cx + 1, z0 - 1, base, 1);
		}
		Grounds.approach(d, cx, base, z0 - 1);
		List<int[]> wallRun = new ArrayList<>();
		for (int x = 0; x < w; x++) {
			wallRun.add(new int[]{x, 0});
		}
		Garden.fence(d, wallRun, cx, Dirs.FRONT);
		for (int x : new int[]{nx0, nx1}) {
			if (zF >= 3 && Groundwork.surface(d, x, 1) == 0 && d.isEmpty(x, 0, 1)) {
				cypress(d, x, 0, 1);
			}
		}
		for (int x = 1; x < w - 1; x += 2) {
			if (Math.abs(x - cx) > 1 && d.isEmpty(x, 0, z0 - 1) && Groundwork.surface(d, x, z0 - 1) == 0) {
				Garden.pot(d, x, 0, z0 - 1);
			}
		}
		if (yard && !campanile && w - nw >= 4) {
			int gx0 = right ? nx1 + 2 : 0;
			int gx1 = right ? w - 1 : nx0 - 2;
			Sacred.churchyard(d, gx0, z0, gx1, dp - 2, 0, true, Dirs.FRONT);
		}
		Groundwork.clear(d, 0, 0, w - 1, dp - 1, top + 2);
	}

	/** The dome's family: the concrete of the town's first cloth colour (a blue), else the roof's. */
	static Fam blueDome(Draft d) {
		Fam f = Fam.of(d, Role.CLOTH, Form.CONCRETE, StageKind.ROOF);
		if (f != null && f.has(Form.STAIRS)) {
			return f;
		}
		Fam r = Fam.of(d, Role.ROOF, Form.FULL, StageKind.ROOF);
		return r != null ? r : Fam.of("minecraft:blue_concrete", StageKind.ROOF);
	}

	/** Radii of a dome over a drum {@code size} wide. */
	static double[] domeRadii(int size) {
		double r = size / 2.0 + 0.5;
		if (size <= 3) {
			return new double[]{r, r - 0.6, 0.6};
		}
		return new double[]{r, r - 0.3, r - 1.0, r - 1.9, 0.6};
	}

	/** A slim cypress: a trunk and a tall narrow column of leaves. */
	static void cypress(Draft d, int x, int y, int z) {
		var leaves = d.mat(Role.HEDGE);
		var log = d.mat(Role.TREE_LOG);
		d.inStage(StageKind.EXTERIOR, () -> {
			d.set(x, y - 1, z, Blocks.GRASS_BLOCK.defaultBlockState());
			d.set(x, y, z, log.log(Direction.Axis.Y).orElse(log.full()));
			for (int k = 1; k <= 4; k++) {
				d.set(x, y + k, z, leaves.leaves());
			}
			d.set(x, y + 5, z, leaves.leaves());
		});
	}
}
