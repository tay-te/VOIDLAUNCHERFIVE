package dev.voidmc.hearth.build.archetype.civic;

import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Mat;
import dev.voidmc.hearth.build.kit.Body;
import dev.voidmc.hearth.build.kit.Facade;
import dev.voidmc.hearth.build.kit.Floors;
import dev.voidmc.hearth.build.kit.Garden;
import dev.voidmc.hearth.build.kit.Openings;
import dev.voidmc.hearth.build.kit.Porches;
import dev.voidmc.hearth.build.kit.Roofs;
import dev.voidmc.hearth.build.kit.Side;
import dev.voidmc.hearth.build.kit.Walls;
import dev.voidmc.hearth.build.kit.civic.Arches;
import dev.voidmc.hearth.build.kit.civic.Grounds;
import dev.voidmc.hearth.build.kit.civic.Halls;
import dev.voidmc.hearth.build.kit.civic.Sacred;
import dev.voidmc.hearth.build.kit.civic.Signs;
import dev.voidmc.hearth.build.kit.civic.Towers;
import dev.voidmc.hearth.build.kit.civic.Tradition;
import dev.voidmc.hearth.build.style.Form;
import dev.voidmc.hearth.build.style.Habits;
import dev.voidmc.hearth.build.style.Habits.RoofForm;
import dev.voidmc.hearth.build.style.Habits.WallKind;
import dev.voidmc.hearth.build.style.Role;
import dev.voidmc.hearth.build.terrain.Groundwork;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * The parish chapel of the timber, stone, alpine, tundra and raised traditions: a long nave under a steep gable facing the
 * street, a narrower and lower chancel behind it, tall coloured windows between buttresses (masonry) or posts (timber),
 * a portal with a hood over the door, and the bell in a bell-cote on the front gable (a village's), a turret on the
 * ridge, or a tower in front of the nave or beside it (a town's), capped by a spire, a pyramid or an onion dome.
 */
final class ChapelWestern {
	private ChapelWestern() {
	}

	private enum Bell {
		COTE, TURRET, FRONT, SIDE
	}

	static void draw(Draft d, Tradition t) {
		int w = d.frontage();
		int dp = d.depth();
		int tier = d.tier();
		int maxH = d.maxHeight();
		Habits h = d.habits();
		boolean raised = t == Tradition.STILT || h.plan().stilts() == Habits.Stilts.ALWAYS;
		boolean masonry = h.walls().kind() == WallKind.STONE || h.walls().kind() == WallKind.BRICK || h.walls().kind() == WallKind.PLASTER
			|| h.walls().kind() == WallKind.ADOBE;

		// --- the plan: nave, chancel, tower, churchyard
		int nw = w >= 12 ? 9 : 7;
		int margin = w - nw;
		boolean yard = d.param("graveyard", true);
		boolean graveSide = yard && margin >= 4;
		boolean graveRight = d.chance(0.5);
		int nx0 = graveSide ? (graveRight ? 1 : w - 1 - nw) : (w - nw) / 2;
		int nx1 = nx0 + nw - 1;
		int cx = (nx0 + nx1) / 2;
		int zF = dp >= 15 ? 2 : 1;
		int cd = dp >= 17 ? 4 : 3;
		int bm = 1;
		boolean wantTower = d.param("tower", tier >= 3 || d.chance(0.2));
		Bell bell;
		if (wantTower && zF + 4 + 7 + cd + bm <= dp - 1 && !raised) {
			bell = Bell.FRONT;
		} else if (wantTower && graveSide && margin >= 6 && !raised) {
			bell = Bell.SIDE;
		} else if (wantTower || raised) {
			bell = Bell.TURRET;
		} else {
			bell = Bell.COTE;
		}
		int zN0 = bell == Bell.FRONT ? zF + 4 : zF;
		int zC1 = dp - 1 - bm;
		// a churchyard behind when there is depth to spare and none beside
		int backYard = 0;
		if (yard && !graveSide && zC1 - cd - zN0 + 1 >= 12) {
			backYard = Math.min(4, zC1 - cd - zN0 + 1 - 9);
			zC1 -= backYard;
		}
		int zN1 = zC1 - cd;
		int cw = nw - 2;
		int cx0 = cx - cw / 2;
		int cx1 = cx + cw / 2;

		// --- heights
		int hn = tier <= 2 ? 6 : tier == 3 ? 7 : 8;
		int base = raised ? Grounds.stiltBase(d, nx0, zN0, nx1, zC1) : Grounds.raisedBase(d, nx0, nx1, zN0);
		double pitch = 1.0;
		int eaves = Math.min(2, Math.max(1, h.roof().eaves()));
		int plate = base + hn - 1;
		// keep the nave's ridge well under the plot's height (a tower or a turret must still rise over it)
		int spanHalf = nw / 2 + eaves;
		int roomForRoof = maxH - 1 - (bell == Bell.COTE ? 5 : 9) - plate;
		if (1 + pitch * spanHalf > roomForRoof) {
			pitch = Math.max(0.75, (roomForRoof - 1.0) / spanHalf);
		}
		d.body(nx0, zN0, nx1, zN1);
		d.eave(plate);

		// --- ground
		Body nave = new Body(nx0, zN0, nx1, zN1, base, new int[]{hn}, false, false);
		Body chancel = new Body(cx0, zN1, cx1, zC1, base, new int[]{hn - 1}, false, false);
		if (raised) {
			Groundwork.stilts(d, nx0, zN0, nx1, zN1, base - 1, 2);
			Groundwork.stilts(d, cx0, zN1, cx1, zC1, base - 1, 2);
			dev.voidmc.hearth.build.kit.Floors.boards(d, nx0, zN0, nx1, zN1, base - 1);
			dev.voidmc.hearth.build.kit.Floors.boards(d, cx0, zN1, cx1, zC1, base - 1);
		} else {
			Groundwork.footings(d, nx0, zN0, nx1, zN1, base - 1);
			Groundwork.footings(d, cx0, zN1, cx1, zC1, base - 1);
			Groundwork.apron(d, nx0, zN0, nx1, zC1, base, 2);
		}
		Grounds.forecourt(d, 0, w - 1, zN0 - 1 - (bell == Bell.FRONT ? 4 : 0), !raised);

		// --- walls: posts or buttresses at even bays, windows between them
		int[] bays = Arches.piers(zN0, zN1, nl(zN0, zN1) >= 12 ? 4 : 3);
		Facade fac = new Facade();
		for (int k = 1; k < bays.length - 1; k++) {
			fac.post(Dirs.LEFT, bays[k]);
			fac.post(Dirs.RIGHT, bays[k]);
		}
		Halls.floor(d, nx0 + 1, zN0 + 1, nx1 - 1, zN1 - 1, base - 1, masonry);
		Halls.floor(d, cx0 + 1, zN1, cx1 - 1, zC1 - 1, base - 1, masonry);
		Walls.storey(d, nave, 0, fac);
		Facade cfac = new Facade();
		Walls.storey(d, chancel, 0, cfac);
		d.carve(nx0 + 1, base, zN0 + 1, nx1 - 1, plate + 12, zN1 - 1);
		d.carve(cx0 + 1, base, zN1, cx1 - 1, base + hn - 3, zC1 - 1);
		// the chancel arch through the nave's back wall
		Mat accent = d.mat(Role.WALL_ACCENT);
		Side back = nave.side(0, Dirs.BACK);
		int archTop = base + Math.min(hn - 2, 4);
		Arches.arcade(d, back, new int[]{back.index(cx0), back.index(cx1)}, base, archTop, masonry ? Arches.Kind.ROUND : Arches.Kind.BRACED,
			masonry ? accent.full() : d.mat(Role.FRAME).log(Direction.Axis.Y), null);
		// windows down both sides, coloured; buttresses on masonry
		int winH = Math.max(2, hn - 3);
		for (Direction sd : new Direction[]{Dirs.LEFT, Dirs.RIGHT}) {
			Side s = nave.side(0, sd);
			for (int k = 0; k + 1 < bays.length; k++) {
				int mid = (bays[k] + bays[k + 1]) / 2;
				if (bell == Bell.SIDE && (graveRight ? sd == Dirs.RIGHT : sd == Dirs.LEFT) && mid <= zN0 + 5) {
					continue;
				}
				Sacred.lancet(d, s, mid, base + 1, 1, winH, true);
			}
			if (masonry || tier >= 4) {
				for (int k = 1; k < bays.length - 1; k++) {
					if (bell == Bell.SIDE && (graveRight ? sd == Dirs.RIGHT : sd == Dirs.LEFT) && bays[k] <= zN0 + 5) {
						continue;
					}
					Sacred.buttress(d, s, s.index(bays[k]), base, base + hn - 3);
				}
			}
		}
		// the chancel: an east window, small side lights
		Side east = chancel.side(0, Dirs.BACK);
		Sacred.lancet(d, east, cw >= 5 ? cx - 1 + (cw % 2 == 0 ? 0 : 0) : cx, base + 1, cw >= 5 ? 2 : 1, Math.max(2, hn - 3), true);
		if (cw >= 5) {
			// a pair of lights centred under the gable reads as one window: shift the pair to straddle the axis
			Sacred.lancet(d, east, cx - 1, base + 1, 1, Math.max(2, hn - 3), true);
			Sacred.lancet(d, east, cx + 1, base + 1, 1, Math.max(2, hn - 3), true);
		}
		for (Direction sd : new Direction[]{Dirs.LEFT, Dirs.RIGHT}) {
			Side s = chancel.side(0, sd);
			if (cd >= 3) {
				Sacred.lancet(d, s, zN1 + 2, base + 1, 1, 2, true);
			}
		}

		// --- the front: a portal, a rose or a pair of lights, the steps
		Side front = nave.side(0, Dirs.FRONT);
		int doorZ = bell == Bell.FRONT ? zF : zN0;
		if (bell != Bell.FRONT) {
			Halls.portal(d, front, cx, base, t, tier);
			Sacred.lancet(d, front, nx0 + 1 + (nw >= 9 ? 1 : 0), base + 1, 1, winH, false);
			Sacred.lancet(d, front, nx1 - 1 - (nw >= 9 ? 1 : 0), base + 1, 1, winH, false);
		} else {
			// the nave opens into the tower's ground floor
			Arches.arcade(d, front, new int[]{front.index(cx - 2), front.index(cx + 2)}, base, base + 3, masonry ? Arches.Kind.ROUND : Arches.Kind.BRACED,
				masonry ? accent.full() : d.mat(Role.FRAME).log(Direction.Axis.Y), null);
		}

		// --- roofs
		Roofs.Opts o = Roofs.Opts.of(d).withPitch(pitch).withEaves(eaves, Math.max(1, h.roof().gableOverhang()));
		RoofForm form = RoofForm.GABLE;
		Roofs.Built naveRoof = Roofs.build(d, nx0, zN0, nx1, zN1, plate, form, Direction.Axis.Z, o, false, false);
		Roofs.Roof roof = naveRoof.roof();
		int ridge = roof.ridgeY;
		int cplate = base + hn - 2;
		Roofs.Built chRoof = Roofs.build(d, cx0, zN1 + 1, cx1, zC1, cplate, form, Direction.Axis.Z, o.withEaves(1, 1), false, false);
		// a rose window high in the front gable when nothing stands before it
		if (bell != Bell.FRONT) {
			int ry = plate + 2;
			if (roof.covers(cx, zN0) && roof.bottomY(cx, zN0) - 1 > ry + 1) {
				Sacred.rose(d, front, front.index(cx), ry);
			}
		}
		// tie beams across the nave with lanterns hung from them
		Mat beam = d.mat(Role.BEAM);
		for (int k = 1; k < bays.length - 1; k++) {
			int z = bays[k];
			try (var s = d.stage(StageKind.FRAME)) {
				for (int x = nx0 + 1; x <= nx1 - 1; x++) {
					d.set(x, plate, z, beam.log(Direction.Axis.X));
				}
			}
			if (plate - base >= 4) {
				Halls.lanternUnder(d, cx, base, z, 3);
			}
		}

		// --- the bell
		int capTop = ridge;
		switch (bell) {
			case COTE -> {
				int apex = roof.surfaceY(cx, zN0);
				if (apex != Integer.MIN_VALUE) {
					capTop = Towers.bellCote(d, front, front.index(cx), apex, maxH);
				}
			}
			case TURRET -> capTop = turret(d, t, cx, zN0 + 1, plate, ridge, maxH);
			case FRONT -> {
				capTop = tower(d, t, cx - 2, zF, base, plate, ridge, maxH, tier, true);
				// the tower's ground floor opens into the nave under an arch
				Arches.arcade(d, front, new int[]{front.index(cx - 2), front.index(cx + 2)}, base, base + 3, masonry ? Arches.Kind.ROUND
					: Arches.Kind.BRACED, masonry ? accent.full() : d.mat(Role.FRAME).log(Direction.Axis.Y), null);
			}
			case SIDE -> capTop = tower(d, t, graveRight ? nx1 + 1 : nx0 - 5, zN0, base, plate, ridge, maxH, tier, false);
		}

		// --- inside: aisle, pews, font, altar, banners
		int iz0 = zN0 + 1;
		int iz1 = zN1 - 1;
		Mat cloth = d.mat(Role.CLOTH);
		if (cloth.has(Form.CARPET)) {
			try (var s = d.stage(StageKind.INTERIOR)) {
				for (int z = iz0; z <= zN1; z++) {
					d.set(cx, base, z, cloth.carpet());
				}
			}
		}
		Sacred.pews(d, nx0 + 1, nx1 - 1, iz0 + 2, iz1 - 1, base, cx, Dirs.BACK, nw >= 9);
		// the font by the door
		d.inStage(StageKind.INTERIOR, () -> d.set(nx0 + 1, base, iz0, Blocks.WATER_CAULDRON.defaultBlockState()
			.setValue(BlockStateProperties.LEVEL_CAULDRON, 3)));
		boolean wide = cw >= 7;
		Sacred.altar(d, cx, base, zC1 - 1, Dirs.FRONT, wide, cx + (wide ? 2 : 1));
		Sacred.candles(d, cx0 + 1, base, zC1 - 1, 3);
		// banners in the church's colours on the inner walls between the windows
		java.util.List<String> colours = Signs.clothColours(d);
		for (int k = 1; k < bays.length - 1; k++) {
			Signs.wallBanner(d, nx0 + 1, base + hn - 3, bays[k], Dirs.RIGHT, colours.get(0), java.util.List.<String[]>of(new String[]{"cross",
				colours.size() > 1 ? colours.get(1) : "white"}));
			Signs.wallBanner(d, nx1 - 1, base + hn - 3, bays[k], Dirs.LEFT, colours.get(0), java.util.List.<String[]>of(new String[]{"cross",
				colours.size() > 1 ? colours.get(1) : "white"}));
		}

		// --- outside: the way in, lamps, churchyard
		if (raised) {
			// a gallery before the door where there is room to come down from it, else a landing and the stair
			if (zN0 >= 3) {
				Porches.porch(d, front, nx0, nx1, 1, base, 3, cx);
				Grounds.approach(d, cx, base, zN0 - 1);
			} else {
				Grounds.approach(d, cx, base, zN0 - 1);
			}
		} else {
			if (base == 1 && doorZ >= 1) {
				Grounds.flight(d, cx - 1, cx + 1, doorZ - 1, base, 1);
			}
			Grounds.approach(d, cx, base, doorZ - 1);
		}
		if (!raised && zN0 >= 2) {
			Halls.lamp(d, nx0, 0, Math.max(0, doorZ - 2), 2);
			Halls.lamp(d, nx1, 0, Math.max(0, doorZ - 2), 2);
		}
		if (graveSide) {
			int gx0 = graveRight ? nx1 + 2 : 0;
			int gx1 = graveRight ? w - 1 : nx0 - 2;
			int gz0 = bell == Bell.SIDE ? zN0 + 6 : Math.max(1, zN0);
			Sacred.churchyard(d, gx0, gz0, gx1, dp - 2, 0, true, Dirs.FRONT);
			Halls.yardEdge(d, gx0, gz0 - 1, gx1, dp - 1, 0, graveRight ? Dirs.LEFT : Dirs.RIGHT);
		} else if (backYard >= 3) {
			Sacred.churchyard(d, 1, zC1 + 2, w - 2, dp - 2, 0, true, Dirs.FRONT);
		}
		if (h.snow()) {
			Garden.snowBanks(d, nx0, zN0, nx1, zC1, base, cx);
		}
		Groundwork.clear(d, 0, 0, w - 1, dp - 1, Math.max(capTop, ridge) + 2);
	}

	private static int nl(int z0, int z1) {
		return z1 - z0 + 1;
	}

	/** The floor of a raised chapel: clear of the ground and water under it by a man's height. */
	static int stiltBase(Draft d, int x0, int z0, int x1, int z1) {
		int base = 2;
		for (int z = z0; z <= z1; z++) {
			for (int x = x0; x <= x1; x++) {
				int wy = d.site().water(x, z);
				base = Math.max(base, Math.max(d.site().ground(x, z) + 3, wy == dev.voidmc.hearth.build.terrain.Site.NONE ? 0 : wy + 2));
			}
		}
		return Math.min(base, 4);
	}

	/**
	 * A bell turret on the ridge: a square of three from the wall plate up through the roof, an open belfry with its bell
	 * and a spire or onion or pyramid on it. Returns the top.
	 */
	static int turret(Draft d, Tradition t, int cx, int z0, int plate, int ridge, int maxH) {
		int x0 = cx - 1;
		Mat beam = d.mat(Role.BEAM);
		// a tie beam carries it across the nave
		try (var s = d.stage(StageKind.FRAME)) {
			for (int z = z0; z <= z0 + 2; z++) {
				for (int x = x0; x <= x0 + 2; x++) {
					d.set(x, plate, z, beam.log(Direction.Axis.X));
				}
			}
		}
		int yb = ridge + 1;
		Towers.shaft(d, x0, z0, 3, plate + 1, yb - 1, t, -1);
		int bh = 3;
		if (yb + bh + 3 > maxH - 1) {
			yb = Math.max(plate + 2, maxH - 1 - bh - 3);
		}
		Towers.belfry(d, x0, z0, 3, yb, bh, t, true);
		Towers.Cap cap = t == Tradition.ALPINE || d.style().culture() == dev.voidmc.hearth.api.Culture.TUNDRA ? Towers.Cap.ONION
			: t == Tradition.STILT ? Towers.Cap.PYRAMID : Towers.Cap.SPIRE;
		return Towers.cap(d, x0, z0, 3, yb + bh, cap, maxH);
	}

	/**
	 * A bell tower five square from the ground: the shaft with quoins and a string course, the main door in its front
	 * (a front tower), slit lights, a clock in towns, the belfry over the nave's ridge and its cap. Returns the top.
	 */
	static int tower(Draft d, Tradition t, int x0, int z0, int base, int plate, int ridge, int maxH, int tier, boolean front) {
		int size = 5;
		int cx = x0 + 2;
		Groundwork.footings(d, x0, z0, x0 + size - 1, z0 + size - 1, base - 1);
		Halls.floor(d, x0 + 1, z0 + 1, x0 + size - 2, z0 + size - 2, base - 1, true);
		int bh = 4;
		Towers.Cap cap = Towers.cap(t, true);
		if (d.style().culture() == dev.voidmc.hearth.api.Culture.TUNDRA) {
			cap = Towers.Cap.ONION;
		}
		int capRoom = cap == Towers.Cap.SPIRE ? 9 : cap == Towers.Cap.ONION ? 7 : 5;
		int yb = Math.max(ridge + 1, plate + 4);
		if (yb + bh + capRoom > maxH - 1) {
			yb = Math.max(plate + 2, maxH - 1 - bh - capRoom);
		}
		Towers.shaft(d, x0, z0, size, base, yb - 1, t, plate + 1);
		d.carve(x0 + 1, base, z0 + 1, x0 + size - 2, yb - 2, z0 + size - 2);
		// a ceiling over the ground floor
		Floors.boards(d, x0 + 1, z0 + 1, x0 + size - 2, z0 + size - 2, base + 4);
		Side f = Side.of(x0, z0, x0 + size - 1, z0 + size - 1, Dirs.FRONT);
		if (front) {
			Halls.portal(d, f, cx, base, t, tier);
		}
		// slit lights up the shaft, a clock under the belfry in towns
		for (int y = base + 6; y < yb - 3; y += 4) {
			for (Side s : Side.of(x0, z0, x0 + size - 1, z0 + size - 1)) {
				if (s.out() == Dirs.BACK && front) {
					continue;
				}
				int yy = y;
				d.inStage(StageKind.OPENINGS, () -> d.set(s.x(2), yy, s.z(2), d.mat(Role.GLASS).pane()));
			}
		}
		if (tier >= 3 && yb - 2 > plate + 1) {
			Towers.clock(d, Side.of(x0, z0, x0 + size - 1, z0 + size - 1, Dirs.FRONT), 2, yb - 2, false, 0);
		}
		Towers.belfry(d, x0, z0, size, yb, bh, t, true);
		int top = Towers.cap(d, x0, z0, size, yb + bh, cap, maxH);
		// a weather vane or a cross, the lamp at its foot
		if (front) {
			Openings.wallLantern(d, f, 0, base + 2);
			Openings.wallLantern(d, f, 4, base + 2);
		}
		return top;
	}
}
