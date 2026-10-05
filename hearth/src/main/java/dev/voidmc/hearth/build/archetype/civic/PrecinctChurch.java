package dev.voidmc.hearth.build.archetype.civic;

import dev.voidmc.hearth.api.PoiType;
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
import dev.voidmc.hearth.build.kit.civic.Adobe;
import dev.voidmc.hearth.build.kit.civic.Arches;
import dev.voidmc.hearth.build.kit.civic.Fam;
import dev.voidmc.hearth.build.kit.civic.Halls;
import dev.voidmc.hearth.build.kit.civic.Sacred;
import dev.voidmc.hearth.build.kit.civic.Signs;
import dev.voidmc.hearth.build.kit.civic.Towers;
import dev.voidmc.hearth.build.kit.civic.Tradition;
import dev.voidmc.hearth.build.kit.landmark.Details;
import dev.voidmc.hearth.build.kit.landmark.Stone;
import dev.voidmc.hearth.build.kit.learning.Rite;
import dev.voidmc.hearth.build.style.Form;
import dev.voidmc.hearth.build.style.Habits.RoofForm;
import dev.voidmc.hearth.build.style.Habits.WallKind;
import dev.voidmc.hearth.build.style.Role;
import dev.voidmc.hearth.build.terrain.Groundwork;
import java.util.List;
import java.util.Set;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;

/**
 * The church of a religious house, on the near side of its {@link Precinct}: its west front on the street beside the
 * gate range, its flank along the cloister walk, its east end beside the back range. Built in the culture's rite
 * ({@link Rite}): walls rendered, dressed, logged or of adobe; a polygonal apse or a square end; lancets of coloured glass
 * or round-headed lights, high on the cloister side over the walk's roof; buttresses on the open flank; and its bell in
 * a ridge turret, a west tower, a bell wall over the front or a bell-cote, as the rite and the plot's height allow (a
 * karst house rings its bell in a pavilion in the garth, and its church is a hall of the Buddha under a curved roof).
 *
 * <p>Inside: the people's pews in the nave, the brethren's choir stalls facing each other before the chancel, the altar
 * in the apse with its candles and the priest's lectern; a door from the choir to the cloister walk.
 */
final class PrecinctChurch {
	private PrecinctChurch() {
	}

	/** What was built: the skyline, the west door's column, whether the bell is to hang in a pavilion in the garth. */
	record Built(int skyline, int doorX, boolean pavilion) {
	}

	static Built draw(Precinct p, boolean small) {
		Draft d = p.d;
		Rite r = p.rite;
		Tradition t = p.t;
		int x0 = p.nx0;
		int x1 = p.nx1;
		int nw = x1 - x0 + 1;
		int cx = x0 + nw / 2;
		int zFront = p.zF;
		int zC1 = p.dp - 1;
		int top = p.top;
		int maxH = top + 1;
		boolean karst = t == Tradition.KARST;
		boolean flat = r.flat();
		Direction toCourt = p.left ? Dirs.RIGHT : Dirs.LEFT;
		int courtWall = p.left ? x1 : x0;

		// --- the plan: a west tower where the rite raises one and the plot is tall enough, the east end
		Rite.Bell bell = r.bell;
		boolean westTower = (bell == Rite.Bell.WEST || bell == Rite.Bell.CAMPANILE || bell == Rite.Bell.ROUND) && top >= 18 && !small
			&& p.dp - zFront >= 20;
		int zN0 = westTower ? zFront + 4 : zFront;
		boolean apse = r.apse && !karst && !flat;
		int ra = (nw - 2) / 2;
		int zA = zC1 - ra;
		int zN1 = karst ? zC1 : apse ? zA - (p.dp >= 25 ? 2 : 1) : zC1 - (p.dp >= 25 ? 4 : 3);

		// --- heights: the walls over the ranges' plate, the steepest roof that leaves the bell its room
		int rangeTop = p.roof != null ? p.roof.ridgeY : p.plate + 2;
		double run = nw / 2.0 + 1 - 0.5;
		boolean turret = bell == Rite.Bell.RIDGE || !westTower && bell != Rite.Bell.WALL && bell != Rite.Bell.PAVILION;
		int hn;
		double pitch;
		if (flat) {
			hn = Math.max(6, Math.min(p.plate + 4, top - 4));
			pitch = 0;
		} else {
			double[] pitches = t == Tradition.TIMBER || t == Tradition.LOG ? new double[]{2, 1, 0.5} : karst ? new double[]{1, 0.5}
				: new double[]{Math.max(0.5, Math.min(1.0, r.pitch)), 0.5};
			hn = -1;
			pitch = 0.5;
			// a turret only on a church that still rises well over the ranges; else as tall as the bell-cote allows
			for (int pass = 0; pass < 3 && hn < 0; pass++) {
				int budget = pass == 0 ? top - 7 : pass == 1 ? top - 5 : top - 1;
				int least = pass < 2 ? rangeTop + 3 : 0;
				turret = pass == 0 && turret;
				if (pass == 0 && !turret) {
					continue;
				}
				for (double pt : pitches) {
					for (int h = Math.min(13, p.plate + 6); h >= Math.max(6, Math.min(p.plate + 2, 9)); h--) {
						double ridge = h - 1 + 1 + pt * run;
						if (ridge <= budget && ridge >= least) {
							hn = h;
							pitch = pt;
							break;
						}
					}
					if (hn > 0) {
						break;
					}
				}
			}
			if (hn < 0) {
				hn = 6;
				pitch = 0.5;
			}
		}
		int plate = hn - 1;
		int hc = karst ? hn : hn - 1;
		int plateC = hc - 1;

		// --- the walls
		WallKind kind = switch (r.ranges) {
			case LOG, PLANK -> r.ranges;
			case ADOBE -> WallKind.ADOBE;
			case PLASTER -> WallKind.PLASTER;
			default -> WallKind.STONE;
		};
		Body nave = new Body(x0, zN0, x1, zN1, 0, new int[]{hn}, false, false);
		Groundwork.footings(d, x0, zN0, x1, zN1, -1);
		p.groundFloor(x0 + 1, zN0 + 1, x1 - 1, zN1 - 1);
		Walls.storey(d, nave, 0, new Facade(), kind);
		d.carve(x0 + 1, 0, zN0 + 1, x1 - 1, plate + 14, zN1 - 1);
		Set<Long> east = karst ? Set.of() : ChapelBohemian.eastEnd(cx, zN1, zA, ra, apse, zC1);
		if (!karst) {
			for (long k : east) {
				int x = (int) (k >> 32);
				int z = (int) k;
				Groundwork.footings(d, x, z, x, z, -1);
			}
			ChapelBohemian.eastWalls(d, east, cx, zN1, zA, ra, 0, plateC);
			for (long k : ChapelBohemian.interior(east)) {
				int x = (int) (k >> 32);
				int z = (int) k;
				p.groundFloor(x, z, x, z);
				d.carve(x, 0, z, x, plateC + 6, z);
			}
			// the slot between the chancel and the back range filled solid (no dead pocket between the walls)
			Paint wall = d.mat(Role.WALL).full();
			try (var s = d.stage(StageKind.WALLS)) {
				for (int z = zN1 + 1; z <= zC1; z++) {
					int g = d.site().ground(courtWall, z);
					for (int y = Math.min(0, g) - 1; y <= plateC; y++) {
						if (d.isAbsent(courtWall, y, z)) {
							d.set(courtWall, y, z, y < 0 ? d.mat(Role.FOUNDATION).full() : wall);
						}
					}
				}
			}
			// the chancel arch
			Side backWall = nave.side(0, Dirs.BACK);
			Arches.arcade(d, backWall, new int[]{backWall.index(cx - ra), backWall.index(cx + ra)}, 0, Math.min(hn - 2, 5),
				r.lancets ? Arches.Kind.POINTED : Arches.Kind.ROUND, d.mat(Role.WALL_ACCENT).full(), null);
		}

		// --- windows: tall on the open flank between buttresses, high on the cloister side over the walk's roof
		int[] bays = Arches.piers(zN0, zN1, zN1 - zN0 >= 11 ? 4 : 3);
		Side outer = nave.side(0, toCourt.getOpposite());
		Side court = nave.side(0, toCourt);
		int winH = Math.max(2, hn - 4);
		for (int k = 0; k + 1 < bays.length; k++) {
			int mid = (bays[k] + bays[k + 1]) / 2;
			light(d, r, outer, mid, 1, winH);
			int hy = 6;
			int hh = plate - hy - 1;
			if (hh >= 2 && mid > p.kz0 && mid < p.kz1) {
				light(d, r, court, mid, hy, hh);
			} else if (mid < p.kz0 || mid > p.kz1 || !p.frontRange && mid < p.kz0) {
				// over the ranges' roofs: a small light where the wall stands clear above them
				int ly = rangeTop + 1;
				if (ly + 2 <= plate - 1) {
					light(d, r, court, mid, ly, Math.min(2, plate - 1 - ly));
				}
			}
		}
		if (r.buttresses && p.m > 0) {
			for (int b : bays) {
				int i = outer.index(b);
				ChapelBohemian.buttress(d, outer.outX(i, 1), outer.outZ(i, 1), 0, Math.max(2, hn - 3), outer.out());
			}
		}
		if (!karst) {
			ChapelBohemian.apseLights(d, cx, zA, ra, apse, zC1, 0, hc);
		}

		// --- the front: the portal (in the tower's foot when there is one), lights beside it
		Side front = nave.side(0, Dirs.FRONT);
		int tier = d.tier();
		if (!westTower) {
			Halls.portal(d, front, cx, 0, t == Tradition.TIMBER ? Tradition.STONE : t, tier);
			if (nw >= 9) {
				light(d, r, front, cx - 3, 1, winH - 1);
				light(d, r, front, cx + 3, 1, winH - 1);
			}
		}

		// --- the roofs
		int skyline = plate;
		Roofs.Roof roof = null;
		boolean partyL = toCourt == Dirs.LEFT;
		boolean partyR = toCourt == Dirs.RIGHT;
		if (flat) {
			int deck = Adobe.is(d) ? Adobe.roof(d, x0, zN0, x1, zN1, plate, true, true) : Roofs.flat(d, x0, zN0, x1, zN1, plate, true);
			skyline = Math.max(skyline, deck + 2);
			int ex0 = cx - ra;
			int ex1 = cx + ra;
			int deckE = Adobe.is(d) ? Adobe.roof(d, ex0, zN1, ex1, zC1, plateC, true, false) : Roofs.flat(d, ex0, zN1, ex1, zC1, plateC, true);
			skyline = Math.max(skyline, deckE + 1);
			if (t == Tradition.ADOBE && ex1 - ex0 >= 4) {
				// a dome over the sanctuary
				Fam dome = Adobe.is(d) ? Adobe.domeSheet(d) : Fam.of(d, Role.ROOF, Form.FULL, StageKind.ROOF);
				if (dome != null) {
					double dz = (zN1 + zC1) / 2.0;
					skyline = Math.max(skyline, Towers.lathe(d, cx, dz, deckE + 1, new double[]{2.2, 1.9, 1.4, 0.7}, dome, maxH));
				}
			}
		} else if (karst) {
			Roofs.Opts o = Roofs.Opts.of(d).withPitch(pitch).withEaves(1, 1);
			Roofs.Built b = Roofs.build(d, x0, zN0, x1, zN1, plate, RoofForm.PAGODA, Direction.Axis.Z, o, partyL, partyR);
			roof = b.roof();
			skyline = Math.max(skyline, roof.ridgeY);
		} else {
			RoofForm form = t == Tradition.STILT ? RoofForm.HIP : RoofForm.GABLE;
			Roofs.Opts o = Roofs.Opts.of(d).withPitch(pitch).withEaves(1, 0);
			Roofs.Built b = Roofs.build(d, x0, zN0, x1, zN1, plate, form, Direction.Axis.Z, o, partyL, partyR);
			roof = b.roof();
			skyline = Math.max(skyline, roof.ridgeY);
			// the gables in the walls' own stuff
			Mat wallMat = d.mat(Role.WALL);
			try (var s = d.stage(StageKind.WALLS)) {
				for (int z : new int[]{zN0, zN1}) {
					for (int x = x0; x <= x1; x++) {
						if (!roof.covers(x, z)) {
							continue;
						}
						for (int y = plate + 1; y < roof.bottomY(x, z); y++) {
							if (Openings.isWallBlock(d, x, y, z) || d.isAbsent(x, y, z) && z == zN1) {
								d.set(x, y, z, kind == WallKind.LOG ? wallMat.log(Direction.Axis.X) : wallMat.full());
							}
						}
					}
				}
			}
			skyline = Math.max(skyline, ChapelBohemian.eastRoof(d, cx, zN1, zA, ra, apse, zC1, plateC, Math.max(0.5, Math.min(pitch, 1.0))));
			if (form == RoofForm.GABLE && !westTower && roof.covers(cx, zN0) && roof.bottomY(cx, zN0) - 1 > plate + 3) {
				Sacred.rose(d, front, front.index(cx), plate + 2);
			}
		}

		// --- the bell
		boolean pavilion = false;
		if (westTower) {
			int ridge = roof != null ? roof.ridgeY : plate + 2;
			skyline = Math.max(skyline, ChapelWestern.tower(d, t, cx - 2, zFront, 0, plate, ridge, maxH, tier, true));
			p.groundFloor(cx - 1, zFront + 1, cx + 1, zFront + 3);
			boolean masonry = t.masonry() || kind == WallKind.STONE || kind == WallKind.PLASTER;
			Arches.arcade(d, front, new int[]{front.index(cx - 2), front.index(cx + 2)}, 0, 3, masonry ? Arches.Kind.ROUND : Arches.Kind.BRACED,
				masonry ? d.mat(Role.WALL_ACCENT).full() : d.mat(Role.FRAME).log(Direction.Axis.Y), null);
		} else if (bell == Rite.Bell.PAVILION) {
			pavilion = true;
		} else if (bell == Rite.Bell.WALL || flat) {
			skyline = Math.max(skyline, Towers.bellWall(d, front, front.index(cx), plate + 1, nw >= 9 ? 2 : 1, maxH));
		} else if (roof != null) {
			int ridge = roof.ridgeY;
			if (turret && ridge + 7 <= top && roof.covers(cx, zN0 + 2)) {
				skyline = Math.max(skyline, ChapelWestern.turret(d, t, cx, zN0 + 1, plate, ridge, maxH));
			} else {
				int apex = roof.surfaceY(cx, zN0);
				if (apex != Integer.MIN_VALUE) {
					skyline = Math.max(skyline, Towers.bellCote(d, front, front.index(cx), apex, maxH));
				}
			}
		}
		if (apse && skyline + 0 >= 0) {
			int eastTop = Integer.MIN_VALUE;
			for (int y = Math.min(top - 1, plateC + 12); y > plateC; y--) {
				if (d.isSolid(cx, y, zA)) {
					eastTop = y;
					break;
				}
			}
			if (eastTop != Integer.MIN_VALUE && eastTop + 2 < top) {
				skyline = Math.max(skyline, Towers.finial(d, cx, eastTop + 1, zA, maxH, false));
			}
		}

		// --- inside
		interior(p, r, x0, x1, cx, zN0, zN1, zA, ra, zC1, apse, karst, westTower, hn);
		return new Built(skyline, cx, pavilion);
	}

	/** A light in the rite's manner: a pointed lancet of coloured glass, or a round-headed light. */
	private static void light(Draft d, Rite r, Side s, int col, int y, int h) {
		if (!s.contains(col) || s.isCorner(s.index(col)) || h < 1) {
			return;
		}
		if (r.lancets) {
			Sacred.lancet(d, s, col, y, 1, h, true);
		} else {
			Details.arch(d, s, s.index(col), 1, y, Math.max(2, h), Stone.dressing(d), d.mat(Role.GLASS).pane());
		}
	}

	/**
	 * The people's pews in the nave (kneeling mats in the karst hall), the choir stalls facing each other before the
	 * chancel, the altar with its candles and the priest's lectern, the font by the door, banners; a door from the choir
	 * into the cloister walk.
	 */
	private static void interior(Precinct p, Rite r, int x0, int x1, int cx, int zN0, int zN1, int zA, int ra, int zC1, boolean apse, boolean karst,
		boolean westTower, int hn) {
		Draft d = p.d;
		Direction toCourt = p.left ? Dirs.RIGHT : Dirs.LEFT;
		int courtWall = p.left ? x1 : x0;
		int inner = p.left ? x1 - 1 : x0 + 1;
		// the door to the walk: near the choir, where the walk is outside
		int dz = Math.max(p.kz0, Math.min(p.kz1 - 1, zN1 - 2));
		if (dz > zN0 && dz < zN1) {
			Side court = new Side(toCourt, toCourt == Dirs.RIGHT ? x1 : x0, toCourt == Dirs.RIGHT ? zN0 : zN1, zN1 - zN0 + 1);
			Openings.door(d, court, dz, 0, 1, new Openings.DoorOpts(false, false, false));
			p.keep(inner, 0, dz);
			p.keep(inner, 0, dz - 1);
			p.keep(inner, 0, dz + 1);
			p.keep(inner - Dirs.dx(toCourt), 0, dz);
			p.keep(courtWall + Dirs.dx(toCourt), 0, dz);
		}
		// the runner up the middle
		Mat cloth = d.mat(Role.CLOTH);
		int zEnd = karst ? zC1 - 2 : zN1;
		if (cloth.has(Form.CARPET)) {
			try (var s = d.stage(StageKind.INTERIOR)) {
				for (int z = zN0 + 1; z <= zEnd; z++) {
					if (d.isEmpty(cx, 0, z)) {
						d.set(cx, 0, z, cloth.carpet());
					}
				}
			}
		}
		for (int z = zN0 + 1; z <= zEnd; z++) {
			p.keep(cx, 0, z);
		}
		// the choir: stalls along both walls over the last rows of the nave, facing each other
		Mat wood = d.mat(Role.FURNITURE);
		int choir0 = karst ? zC1 : Math.max(zN0 + 3, zN1 - 4);
		int n = 0;
		for (int z = choir0; z < zN1; z++) {
			for (int x : new int[]{x0 + 1, x1 - 1}) {
				if (!d.isEmpty(x, 0, z) || p.kept(x, 0, z)) {
					continue;
				}
				Direction back = x == x0 + 1 ? Dirs.LEFT : Dirs.RIGHT;
				int xx = x;
				int zz = z;
				d.inStage(StageKind.INTERIOR, () -> d.set(xx, 0, zz, wood.stairsOrSlab(back, Half.BOTTOM)));
				if ((n++ & 1) == 0) {
					d.poi(PoiType.SEAT, x, 0, z, back.getOpposite());
				}
			}
		}
		// the people's pews (or mats) from inside the door up to the choir, a walk kept along the cloister wall
		int first = westTower ? zN0 + 1 : zN0 + 2;
		n = 0;
		for (int z = first; z < choir0 - 1; z += 2) {
			for (int x = x0 + 1; x <= x1 - 1; x++) {
				if (x == cx || x == inner && x1 - x0 + 1 >= 9 || p.kept(x, 0, z) || !d.isEmpty(x, 0, z)) {
					continue;
				}
				int xx = x;
				int zz = z;
				if (karst && cloth.has(Form.CARPET)) {
					d.inStage(StageKind.INTERIOR, () -> d.set(xx, 0, zz, cloth.carpet()));
				} else {
					d.inStage(StageKind.INTERIOR, () -> d.set(xx, 0, zz, wood.stairsOrSlab(Dirs.FRONT, Half.BOTTOM)));
				}
				if ((n++ & 1) == 0) {
					d.poi(PoiType.SEAT, x, 0, z, Dirs.BACK);
				}
			}
		}
		// the altar
		int altarZ = karst ? zC1 - 1 : apse ? zA + ra - 1 : zC1 - 1;
		boolean wide = karst ? x1 - x0 >= 6 : 2 * ra - 1 >= 5;
		Sacred.altar(d, cx, 0, altarZ, Dirs.FRONT, wide, cx + (wide ? 2 : 1));
		if (d.isEmpty(cx - 1, 0, altarZ) && !wide) {
			Sacred.candles(d, cx - 1, 0, altarZ, 3);
		}
		// the font inside the west door, on the side away from the cloister
		int fx = p.left ? x0 + 1 : x1 - 1;
		int fz = westTower ? zN0 + 1 : zN0 + 1;
		if (d.isEmpty(fx, 0, fz) && !p.kept(fx, 0, fz)) {
			d.inStage(StageKind.INTERIOR, () -> d.set(fx, 0, fz, Blocks.WATER_CAULDRON.defaultBlockState().setValue(BlockStateProperties.LEVEL_CAULDRON, 3)));
		}
		// banners of the house's colours between the windows
		List<String> colours = Signs.clothColours(d);
		String field = colours.get(0);
		String cross = colours.size() > 1 ? colours.get(1) : "white";
		int by = Math.min(hn - 3, 4);
		int[] bays = Arches.piers(zN0, zN1, zN1 - zN0 >= 11 ? 4 : 3);
		for (int k = 1; k < bays.length - 1; k++) {
			int z = bays[k];
			int ox = p.left ? x0 + 1 : x1 - 1;
			if (d.isEmpty(ox, by, z) && Openings.isWallBlock(d, p.left ? x0 : x1, by, z)) {
				Signs.wallBanner(d, ox, by, z, p.left ? Dirs.RIGHT : Dirs.LEFT, field, List.<String[]>of(new String[]{"cross", cross}));
			}
		}
	}
}
