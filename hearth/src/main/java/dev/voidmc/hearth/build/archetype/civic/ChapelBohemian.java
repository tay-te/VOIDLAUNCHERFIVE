package dev.voidmc.hearth.build.archetype.civic;

import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Mat;
import dev.voidmc.hearth.build.draft.Paint;
import dev.voidmc.hearth.build.kit.Body;
import dev.voidmc.hearth.build.kit.Facade;
import dev.voidmc.hearth.build.kit.Openings;
import dev.voidmc.hearth.build.kit.Roofs;
import dev.voidmc.hearth.build.kit.Side;
import dev.voidmc.hearth.build.kit.Walls;
import dev.voidmc.hearth.build.kit.civic.Arches;
import dev.voidmc.hearth.build.kit.civic.Bohemian;
import dev.voidmc.hearth.build.kit.civic.Grounds;
import dev.voidmc.hearth.build.kit.civic.Halls;
import dev.voidmc.hearth.build.kit.civic.Sacred;
import dev.voidmc.hearth.build.kit.civic.Signs;
import dev.voidmc.hearth.build.kit.civic.Towers;
import dev.voidmc.hearth.build.kit.civic.Tradition;
import dev.voidmc.hearth.build.kit.landmark.Details;
import dev.voidmc.hearth.build.kit.landmark.Shapes;
import dev.voidmc.hearth.build.kit.landmark.Stone;
import dev.voidmc.hearth.build.style.Form;
import dev.voidmc.hearth.build.style.Habits;
import dev.voidmc.hearth.build.style.Habits.RoofForm;
import dev.voidmc.hearth.build.style.Habits.WallKind;
import dev.voidmc.hearth.build.style.Role;
import dev.voidmc.hearth.build.terrain.Groundwork;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;

/**
 * The parish church of the timber-framing towns (temperate, wisteria), built the way Bohemia's are: whatever its houses
 * are made of, a parish raises its church in stone. The walls are rendered in the style's own plaster between dressed
 * quoins over a stone plinth, stepped buttresses stand between tall pointed windows of coloured glass, and the nave
 * runs back under a steep roof to a narrower chancel that ends in a three-sided apse (a buttress at each angle, a light
 * in each face) under its own lower hipped roof.
 *
 * <p>A town's church (and some villages') raises a tall west tower in front of the nave, half-engaged in it: a portal
 * in its foot, string courses over little arched corbel tables between its stages, a clock under the belfry, an open
 * belfry of arches with its bell, and a slender needle spire with a pinnacle at each corner, the crown Bohemian towers
 * carry. Where the plot is too short for that it stands beside the nave's front; a village's church carries a slender
 * shingled turret on the ridge over its west gable instead, and its portal and a round window are in the gable.
 *
 * <p>Inside: pews either side of a runner to the chancel arch, the font by the door, lanterns from tie beams, the altar
 * in the apse with candles and the priest's lectern, banners of the church's colours between the windows; a churchyard
 * behind a wall and a roofed gate beside it where the plot is wide.
 */
final class ChapelBohemian {
	private ChapelBohemian() {
	}

	private enum Bell {
		FRONT, SIDE, TURRET
	}

	static void draw(Draft d) {
		int w = d.frontage();
		int dp = d.depth();
		int tier = d.tier();
		int maxH = d.maxHeight();

		// --- the plan: nave width, a tower or a turret, the chancel and its apse
		boolean wantTower = d.param("tower", tier >= 3 || d.chance(0.3));
		int zF = dp >= 16 ? 2 : 1;
		int zC1 = dp - 2;
		Bell bell = Bell.TURRET;
		int nw = w >= 11 ? 9 : 7;
		boolean apse = true;
		int zN1 = naveEnd(dp, zC1, nw, true);
		if (wantTower && maxH >= 24) {
			if (zN1 - 1 - (zF + 5) + 1 >= 3) {
				bell = Bell.FRONT;
			} else if (naveEnd(dp, zC1, nw, false) - 1 - (zF + 5) + 1 >= 3) {
				bell = Bell.FRONT;
				apse = false;
				zN1 = naveEnd(dp, zC1, nw, false);
			} else if (w >= 12) {
				bell = Bell.SIDE;
				nw = 7;
				zN1 = naveEnd(dp, zC1, nw, true);
			}
		}
		int cw = nw - 2;
		int ra = cw / 2;
		int zA = zC1 - ra;
		int margin = w - nw - (bell == Bell.SIDE ? 4 : 0);
		boolean yard = d.param("graveyard", true);
		boolean graveSide = yard && margin >= 4;
		boolean right = d.chance(0.5);
		int nx0;
		if (bell == Bell.SIDE) {
			// the tower on one side of the nave's front, sharing its wall
			nx0 = right ? Math.max(0, (w - nw - 4) / 2) : w - nw - Math.max(0, (w - nw - 4) / 2);
		} else {
			nx0 = graveSide ? (right ? 1 : w - 1 - nw) : (w - nw) / 2;
		}
		int nx1 = nx0 + nw - 1;
		int cx = nx0 + nw / 2;
		int cx0 = cx - ra;
		int cx1 = cx + ra;
		int zN0 = bell == Bell.FRONT ? zF + 2 : zF;
		int narthex = bell == Bell.FRONT ? zF + 4 : zN0;

		// --- heights: walls, then the steepest roof that leaves the tower its belfry and its crown
		int base = Grounds.raisedBase(d, nx0, nx1, bell == Bell.FRONT ? zF : zN0);
		int hn = tier <= 2 ? 7 : tier == 3 ? 8 : 9;
		int eaves = 1;
		double run = nw / 2.0 + eaves - 0.5;
		int ridgeMax = bell == Bell.TURRET ? maxH - 1 - 7 : maxH - 1 - 10 - 6;
		// a whole block or two per cell lays clean courses; a pitch between them terraces the roof in lips. The steep
		// Gothic roof (two per cell) where the plot's height allows it, a course or two off the walls if that buys it;
		// else a plain one, and only at the lowest heights a flatter one
		int hn0 = hn;
		double pitch = 2.0;
		while (base + hn - 1 + 1 + pitch * run > ridgeMax && hn > hn0 - 2) {
			hn--;
		}
		if (base + hn - 1 + 1 + pitch * run > ridgeMax) {
			hn = hn0;
			pitch = 1.0;
			while (base + hn - 1 + 1 + pitch * run > ridgeMax && hn > 5) {
				hn--;
			}
			if (base + hn - 1 + 1 + pitch * run > ridgeMax) {
				pitch = Math.max(0.75, (ridgeMax - (base + hn - 1) - 1) / run);
			}
		}
		int plate = base + hn - 1;
		int hc = hn - 1;
		int plateC = base + hc - 1;
		d.body(nx0, zN0, nx1, zN1);
		d.eave(plate);

		// --- ground: footings under every wall, an apron round them, the forecourt
		Set<Long> east = eastEnd(cx, zN1, zA, ra, apse, zC1);
		Groundwork.footings(d, nx0, zN0, nx1, zN1, base - 1);
		for (long k : east) {
			int x = (int) (k >> 32);
			int z = (int) k;
			Groundwork.footings(d, x, z, x, z, base - 1);
		}
		Groundwork.apron(d, nx0, zN0, nx1, zC1, base, 2);
		int doorZ = bell == Bell.FRONT ? zF : zN0;
		Grounds.forecourt(d, 0, w - 1, doorZ - 1, true);

		// --- the walls: render between quoins over a stone plinth, whatever the houses are
		Body nave = new Body(nx0, zN0, nx1, zN1, base, new int[]{hn}, false, false);
		Halls.floor(d, nx0 + 1, zN0 + 1, nx1 - 1, zN1 - 1, base - 1, true);
		Walls.storey(d, nave, 0, new Facade(), WallKind.STONE);
		d.carve(nx0 + 1, base, zN0 + 1, nx1 - 1, plate + 14, zN1 - 1);
		eastWalls(d, east, cx, zN1, zA, ra, base, plateC);
		for (long k : interior(east)) {
			int x = (int) (k >> 32);
			int z = (int) k;
			Halls.floor(d, x, z, x, z, base - 1, true);
			d.carve(x, base, z, x, plateC + 6, z);
		}
		// the chancel arch through the nave's back wall: pointed, in dressed stone
		Mat accent = d.mat(Role.WALL_ACCENT);
		Side back = nave.side(0, Dirs.BACK);
		Arches.arcade(d, back, new int[]{back.index(cx0), back.index(cx1)}, base, base + Math.min(hn - 2, 5), Arches.Kind.POINTED, accent.full(),
			null);

		// --- buttresses and windows down both sides of the nave
		int[] bays = Arches.piers(zN0, zN1, zN1 - zN0 >= 11 ? 4 : 3);
		int winH = Math.max(2, hn - 3);
		for (Direction sd : new Direction[]{Dirs.LEFT, Dirs.RIGHT}) {
			Side s = nave.side(0, sd);
			boolean towerSide = bell == Bell.SIDE && (sd == Dirs.RIGHT) == right;
			for (int k = 0; k + 1 < bays.length; k++) {
				int mid = (bays[k] + bays[k + 1]) / 2;
				if (towerSide && mid <= zF + 5) {
					continue;
				}
				Sacred.lancet(d, s, mid, base + 1, 1, winH, true);
			}
			for (int k = 0; k < bays.length; k++) {
				if (towerSide && bays[k] <= zF + 5) {
					continue;
				}
				int i = s.index(bays[k]);
				buttress(d, s.outX(i, 1), s.outZ(i, 1), base, base + hn - 3, sd);
			}
		}
		// the apse's lights: a tall one in the middle of its back face, one in each of its slanting faces
		apseLights(d, cx, zA, ra, apse, zC1, base, hc);

		// --- the front
		Side front = nave.side(0, Dirs.FRONT);
		if (bell != Bell.FRONT) {
			Halls.portal(d, front, cx, base, Tradition.STONE, tier);
			// the door's head in dressed stone (the kit lays a timber lintel in a timber-framing town)
			if (d.get(cx, base + 2, zN0) != null && d.get(cx, base + 2, zN0).hasProperty(BlockStateProperties.AXIS)) {
				d.set(cx, base + 2, zN0, accent.full());
			}
			if (nw >= 9) {
				Sacred.lancet(d, front, cx - 3, base + 1, 1, winH - 1, false);
				Sacred.lancet(d, front, cx + 3, base + 1, 1, winH - 1, false);
			}
		}

		// --- roofs: the nave's, steep, its gables rendered; the chancel's lower, hipped round the apse
		Roofs.Opts o = Roofs.Opts.of(d).withPitch(pitch).withEaves(eaves, 0);
		Roofs.Built naveRoof = Roofs.build(d, nx0, zN0, nx1, zN1, plate, RoofForm.GABLE, Direction.Axis.Z, o, false, false);
		Roofs.Roof roof = naveRoof.roof();
		int ridge = roof.ridgeY;
		// the gables in the walls' own render, no king post or collar (a style may give its gables boards, timbers or a
		// colour the church does not wear)
		Mat wallMat = d.mat(Role.WALL);
		try (var s = d.stage(StageKind.WALLS)) {
			for (int z : new int[]{zN0, zN1}) {
				for (int x = nx0; x <= nx1; x++) {
					if (!roof.covers(x, z)) {
						continue;
					}
					for (int y = plate + 1; y < roof.bottomY(x, z); y++) {
						if (Openings.isWallBlock(d, x, y, z)) {
							d.set(x, y, z, wallMat.full());
						}
					}
				}
			}
		}
		int eastTop = eastRoof(d, cx, zN1, zA, ra, apse, zC1, plateC, pitch);
		if (bell != Bell.FRONT) {
			// a round window high in the west gable
			int ry = plate + 2;
			if (roof.covers(cx, zN0) && roof.bottomY(cx, zN0) - 1 > ry + 1) {
				Sacred.rose(d, front, front.index(cx), ry);
			}
		}
		// a cross on the apse's peak
		if (apse && eastTop + 1 < maxH - 1) {
			eastTop = Math.max(eastTop, Towers.finial(d, cx, eastTop + 1, zA, maxH, false));
		}
		// tie beams across the nave, lanterns hung from them
		Mat beam = d.mat(Role.BEAM);
		for (int k = 1; k < bays.length - 1; k++) {
			int z = bays[k];
			if (z <= narthex) {
				continue;
			}
			try (var s = d.stage(StageKind.FRAME)) {
				for (int x = nx0 + 1; x <= nx1 - 1; x++) {
					d.set(x, plate, z, beam.log(Direction.Axis.X));
				}
			}
			Halls.lanternUnder(d, cx, base, z, 3);
		}

		// --- the bell
		int skyline = Math.max(ridge, eastTop);
		switch (bell) {
			case FRONT -> skyline = Math.max(skyline, tower(d, cx - 2, zF, base, plate, ridge, maxH, tier, null));
			case SIDE -> {
				int tx0 = right ? nx1 : nx0 - 4;
				skyline = Math.max(skyline, tower(d, tx0, zF, base, plate, ridge, maxH, tier, right ? Dirs.LEFT : Dirs.RIGHT));
				// the way from the nave into the tower's foot
				int wx = right ? nx1 : nx0;
				for (int y = base; y < base + 3; y++) {
					d.set(wx, y, zF + 2, Blocks.AIR.defaultBlockState());
				}
			}
			case TURRET -> {
				if (roof.covers(cx, zN0 + 2) && ridge + 7 <= maxH - 1) {
					skyline = Math.max(skyline, ChapelWestern.turret(d, Tradition.TIMBER, cx, zN0 + 1, plate, ridge, maxH));
				} else {
					int apex = roof.surfaceY(cx, zN0);
					if (apex != Integer.MIN_VALUE) {
						skyline = Math.max(skyline, Towers.bellCote(d, front, front.index(cx), apex, maxH));
					}
				}
			}
		}

		// --- inside: the runner, pews, font, altar, banners
		Mat cloth = d.mat(Role.CLOTH);
		int iz0 = narthex + 1;
		if (cloth.has(Form.CARPET)) {
			try (var s = d.stage(StageKind.INTERIOR)) {
				for (int z = zN0 + 1; z <= zN1; z++) {
					if (d.isEmpty(cx, base, z)) {
						d.set(cx, base, z, cloth.carpet());
					}
				}
			}
		}
		// pews from the narthex (a row kept free inside a west door) up to the chancel arch, the altar being in the apse
		Sacred.pews(d, nx0 + 1, nx1 - 1, bell == Bell.FRONT ? iz0 : iz0 + 1, apse ? zN1 - 1 : zN1 - 2, base, cx, Dirs.BACK, nw >= 9);
		// the font just inside, on the side away from the bell rope
		int fx = bell == Bell.FRONT ? (nw >= 9 ? nx0 + 1 : cx - 1) : nx0 + 1;
		int fz = bell == Bell.FRONT ? (nw >= 9 ? zF + 3 : iz0) : zN0 + 1;
		if (d.isEmpty(fx, base, fz)) {
			d.inStage(StageKind.INTERIOR, () -> d.set(fx, base, fz, Blocks.WATER_CAULDRON.defaultBlockState()
				.setValue(BlockStateProperties.LEVEL_CAULDRON, 3)));
		}
		boolean wide = cw >= 7;
		int altarZ = apse ? zA + ra - 1 : zC1 - 1;
		Sacred.altar(d, cx, base, altarZ, Dirs.FRONT, wide, cx + (wide ? 2 : 1));
		Sacred.candles(d, cx0 + 1, base, altarZ, 3);
		Halls.lanternUnder(d, cx, base, altarZ - 1, 2);
		List<String> colours = Signs.clothColours(d);
		String field = colours.get(0);
		String cross = colours.size() > 1 ? colours.get(1) : "white";
		for (int k = 1; k < bays.length - 1; k++) {
			if (bays[k] <= narthex) {
				continue;
			}
			Signs.wallBanner(d, nx0 + 1, base + hn - 3, bays[k], Dirs.RIGHT, field, List.<String[]>of(new String[]{"cross", cross}));
			Signs.wallBanner(d, nx1 - 1, base + hn - 3, bays[k], Dirs.LEFT, field, List.<String[]>of(new String[]{"cross", cross}));
		}

		// --- outside: the steps, lamps, churchyard
		int doorX = cx;
		if (base == 1 && doorZ >= 1) {
			Grounds.flight(d, doorX - 1, doorX + 1, doorZ - 1, base, 1);
		}
		Grounds.approach(d, doorX, base, doorZ - 1);
		if (doorZ >= 2) {
			Halls.lamp(d, Math.max(0, doorX - 3), 0, doorZ - 2, 2);
			Halls.lamp(d, Math.min(w - 1, doorX + 3), 0, doorZ - 2, 2);
		}
		if (graveSide) {
			int gx0 = right ? nx1 + 2 + (bell == Bell.SIDE ? 4 : 0) : 0;
			int gx1 = right ? w - 1 : nx0 - 2 - (bell == Bell.SIDE ? 4 : 0);
			int gz0 = Math.max(1, zN0);
			if (gx1 - gx0 >= 1) {
				Sacred.churchyard(d, gx0, gz0, gx1, dp - 2, 0, true, Dirs.FRONT);
				Halls.yardEdge(d, gx0, gz0 - 1, gx1, dp - 1, 0, right ? Dirs.LEFT : Dirs.RIGHT);
			}
		}
		Groundwork.clear(d, 0, 0, w - 1, dp - 1, Math.min(maxH - 1, skyline + 2));
	}

	/** Where the nave's back wall stands: the chancel and its apse (or its square end) take the rows behind. */
	private static int naveEnd(int dp, int zC1, int nw, boolean apse) {
		int ra = (nw - 2) / 2;
		if (apse) {
			int straight = dp >= 17 ? 2 : 1;
			return zC1 - ra - straight;
		}
		return zC1 - (dp >= 17 ? 4 : 3);
	}

	/**
	 * The cells of the chancel and its apse (walls and floor): the rectangle {@code cx ± ra} from the nave's back wall to
	 * {@code zA}, then the half of an octagon beyond (three-sided), or a square end to {@code zC1}.
	 */
	static Set<Long> eastEnd(int cx, int zN1, int zA, int ra, boolean apse, int zC1) {
		Set<Long> s = new HashSet<>();
		int zr = apse ? zA : zC1;
		for (int z = zN1; z <= zr; z++) {
			for (int x = cx - ra; x <= cx + ra; x++) {
				s.add(Shapes.cell(x, z));
			}
		}
		if (apse) {
			for (long k : Shapes.oct(cx, zA, ra, ra >= 3 ? 2 : 1)) {
				if ((int) k > zA) {
					s.add(k);
				}
			}
		}
		return s;
	}

	/** The cells of a shape not on its outline (every neighbour in the shape), without the nave's back wall row. */
	static Set<Long> interior(Set<Long> shape) {
		Set<Long> in = new HashSet<>();
		int zMin = Integer.MAX_VALUE;
		for (long k : shape) {
			zMin = Math.min(zMin, (int) k);
		}
		for (long k : shape) {
			int x = (int) (k >> 32);
			int z = (int) k;
			if (z == zMin) {
				continue;
			}
			boolean inner = true;
			for (Direction dir : Dirs.HORIZONTAL) {
				if (!shape.contains(Shapes.cell(x + Dirs.dx(dir), z + Dirs.dz(dir)))) {
					inner = false;
					break;
				}
			}
			if (inner) {
				in.add(k);
			}
		}
		return in;
	}

	/**
	 * The chancel's and the apse's walls up to {@code plateC}: the render, dressed quoins at the angles, the plinth course
	 * at the foot; the nave's back wall row is left to the nave.
	 */
	static void eastWalls(Draft d, Set<Long> east, int cx, int zN1, int zA, int ra, int base, int plateC) {
		Set<Long> inner = interior(east);
		Mat wall = d.mat(Role.WALL);
		Mat plinth = d.mat(Role.PLINTH);
		Stone dress = Stone.dressing(d);
		try (var st = d.stage(StageKind.WALLS)) {
			for (long k : east) {
				int x = (int) (k >> 32);
				int z = (int) k;
				if (z == zN1 || inner.contains(k)) {
					continue;
				}
				for (int y = base; y <= plateC; y++) {
					Paint p = y == base ? plinth.full() : wall.full();
					if (y == plateC) {
						p = dress.full();
					}
					d.set(x, y, z, p);
				}
			}
		}
		// buttresses in the notches of the octagon: one at every angle, stepping down outward
		for (long k : east) {
			int x = (int) (k >> 32);
			int z = (int) k;
			if (z <= zA || inner.contains(k)) {
				continue;
			}
			for (int sx : new int[]{-1, 1}) {
				int bx = x + sx;
				// a notch: outside the shape, the cell in front of it and the one toward the axis both in it
				if (east.contains(Shapes.cell(bx, z)) || !east.contains(Shapes.cell(bx, z - 1))) {
					continue;
				}
				Direction out = z - zA >= ra ? Dirs.BACK : sx > 0 ? Dirs.RIGHT : Dirs.LEFT;
				buttress(d, bx, z, base, plateC - 2, out);
			}
		}
	}

	/**
	 * A rendered buttress against the nave: footed to the ground, the plinth course in stone, its body in the walls' render
	 * (Bohemian buttresses are plastered with the church), a dressed set-off half way and a dressed weathering on top.
	 */
	static void buttress(Draft d, int x, int z, int base, int top, Direction out) {
		if (x < 0 || x >= d.frontage() || z < 0 || z >= d.depth()) {
			return;
		}
		Mat plinth = d.mat(Role.PLINTH);
		Mat wall = d.mat(Role.WALL);
		Stone dress = Stone.dressing(d);
		int setOff = base + (top - base) / 2;
		try (var st = d.stage(StageKind.WALLS)) {
			int g = d.site().ground(x, z);
			for (int y = Math.min(base, g) - 1; y < top; y++) {
				d.set(x, y, z, y <= base ? plinth.full() : y == setOff ? dress.full() : wall.full());
			}
			d.set(x, top, z, dress.stairsOrSlab(out.getOpposite(), Half.BOTTOM));
		}
	}

	/** Lights of coloured glass in the apse: a tall one in the back face, one in each slanting face, or a pair in a square end. */
	static void apseLights(Draft d, int cx, int zA, int ra, boolean apse, int zC1, int base, int hc) {
		Mat cloth = d.mat(Role.CLOTH);
		Mat glass = d.mat(Role.GLASS);
		Stone dress = Stone.dressing(d);
		int h = Math.max(2, hc - 3);
		Paint stained = cloth.has(Form.GLASS) ? cloth.form(Form.GLASS) : Paint.of(Blocks.GLASS.defaultBlockState());
		try (var st = d.stage(StageKind.OPENINGS)) {
			List<int[]> cells = new java.util.ArrayList<>();
			if (apse) {
				cells.add(new int[]{cx, zA + ra});
				if (ra >= 3) {
					cells.add(new int[]{cx - 2, zA + 2});
					cells.add(new int[]{cx + 2, zA + 2});
				} else {
					cells.add(new int[]{cx - ra, zA + 1});
					cells.add(new int[]{cx + ra, zA + 1});
				}
			} else {
				cells.add(new int[]{cx - 1, zC1});
				cells.add(new int[]{cx + 1, zC1});
			}
			for (int[] c : cells) {
				for (int y = base + 1; y < base + 1 + h; y++) {
					boolean mid = y > base + 1 && y < base + h;
					d.set(c[0], y, c[1], mid ? stained : glass.has(Form.GLASS) ? glass.form(Form.GLASS) : Paint.of(Blocks.GLASS.defaultBlockState()));
				}
				d.set(c[0], base + 1 + h, c[1], dress.full());
			}
		}
	}

	/**
	 * The chancel's roof: a gable over the straight part, hipped round the apse as one surface (or a gable to a square
	 * end), eaves a block out, its wall plate a course under the nave's. Returns its highest row.
	 */
	static int eastRoof(Draft d, int cx, int zN1, int zA, int ra, boolean apse, int zC1, int plateC, double pitch) {
		Mat roof = d.mat(Role.ROOF);
		Mat trim = d.mat(Role.ROOF_TRIM);
		int e = 1;
		int x0 = cx - ra - e;
		int x1 = cx + ra + e;
		Roofs.Field f;
		int zEnd;
		if (apse) {
			Roofs.Field gable = Roofs.gable(x0, zN1, x1, zA, Direction.Axis.Z, Roofs.Profile.straight(pitch));
			Roofs.Field hip = Shapes.octagon(cx, zA, ra + e, pitch);
			f = (x, z) -> z > zA ? hip.h(x, z) : gable.h(x, z);
			zEnd = Math.min(d.depth() - 1, zA + ra + e);
		} else {
			f = Roofs.gable(x0, zN1, x1, zC1 + e, Direction.Axis.Z, Roofs.Profile.straight(pitch));
			zEnd = Math.min(d.depth() - 1, zC1 + e);
		}
		Roofs.Roof r = Roofs.raster(d, f, plateC + 1, Math.max(0, x0), zN1 + 1, Math.min(d.frontage() - 1, x1), zEnd, roof, trim, Habits.Ridge.SLAB);
		Shapes.support(d, r, roof.full());
		// the square end's gable filled in render
		if (!apse) {
			try (var st = d.stage(StageKind.WALLS)) {
				for (int x = cx - ra; x <= cx + ra; x++) {
					if (r.covers(x, zC1)) {
						for (int y = plateC + 1; y < r.bottomY(x, zC1); y++) {
							d.set(x, y, zC1, d.mat(Role.WALL).full());
						}
					}
				}
			}
		}
		return r.ridgeY;
	}

	/**
	 * The church's tower ({@link Bohemian#tower}): a west tower's portal in its foot under a little gable and an arch
	 * through its back face into the nave; a side tower's face against the nave left blind. Clocks toward the street and
	 * over the roof behind. Returns the top.
	 */
	static int tower(Draft d, int x0, int z0, int base, int plate, int ridge, int maxH, int tier, Direction shared) {
		boolean west = shared == null;
		int yb = Bohemian.belfryFloor(plate, ridge, maxH);
		int doorTop = Math.max(base + 5, Math.min(plate + 1, base + 7));
		Side fr = Side.of(x0, z0, x0 + 4, z0 + 4, Dirs.FRONT);
		Set<Direction> blind = west ? Set.of(Dirs.BACK) : Set.of(shared);
		Set<Direction> clocks = tier >= 3 || d.chance(0.4) ? (yb - 3 > ridge ? Set.of(Dirs.FRONT, Dirs.BACK) : Set.of(Dirs.FRONT)) : Set.of();
		int top = Bohemian.tower(d, x0, z0, base, doorTop, yb, maxH, tier, blind, clocks, new int[]{x0 + 3, z0 + 1}, Dirs.BACK);
		Stone dress = Stone.dressing(d);
		Paint glass = d.mat(Role.GLASS).pane();
		if (west) {
			Bohemian.portal(d, fr, fr.middle(), base);
			// a light over the portal, and the way through into the nave under an arch in the tower's back face
			Details.slit(d, fr, fr.middle(), base + 4, 2, dress, glass);
			Details.arch(d, Side.of(x0, z0, x0 + 4, z0 + 4, Dirs.BACK), 1, 3, base, 4, dress, null);
		}
		for (Side f : Side.of(x0, z0, x0 + 4, z0 + 4)) {
			if (f.out() != Dirs.FRONT && !blind.contains(f.out()) && base + 4 < doorTop) {
				Details.slit(d, f, f.middle(), base + 2, 2, dress, glass);
			}
		}
		return top;
	}
}
