package dev.voidmc.hearth.build.archetype.landmark;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.ArchetypeCategory;
import dev.voidmc.hearth.api.ArchetypeInfo;
import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.PoiType;
import dev.voidmc.hearth.api.SettlementTier;
import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.Archetype;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Paint;
import dev.voidmc.hearth.build.draft.States;
import dev.voidmc.hearth.build.kit.Furniture;
import dev.voidmc.hearth.build.kit.Side;
import dev.voidmc.hearth.build.kit.landmark.Crowns;
import dev.voidmc.hearth.build.kit.landmark.Details;
import dev.voidmc.hearth.build.kit.landmark.Grounds;
import dev.voidmc.hearth.build.kit.landmark.Stone;
import dev.voidmc.hearth.build.kit.landmark.Tower;
import dev.voidmc.hearth.build.style.Habits;
import dev.voidmc.hearth.build.style.Role;
import dev.voidmc.hearth.build.terrain.Groundwork;
import java.util.List;
import java.util.Set;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.Half;

/**
 * {@code hearth:landmark/clock_tower}: the city's clock on its square. A plain shaft on a chamfered plinth carries a
 * clock stage corbelled out wider than itself, a dial laid in blocks on each of its four faces (white faces, black
 * hands each showing its own hour, gold marks on the big ones) and pinnacles at its corners; above it the bell chamber
 * steps back behind a balustraded gallery folk can walk round (a viewpoint over the town), and a crown in the culture's
 * manner: a steep tiled spire, an open cupola, a dome, an onion, a curved hip. Inside, a spiral stair climbs past the
 * clock room (the backs of the dials, the keeper's lectern, the works) to the bells.
 *
 * <p>Params (all optional): {@code crown} (a {@link Crowns.Kind} name), {@code variant}.
 */
public final class ClockTower implements Archetype {
	public static final ArchetypeInfo INFO = new ArchetypeInfo(Hearth.id("landmark/clock_tower"), ArchetypeCategory.LANDMARK, Set.of(), 0,
		7, 11, 7, 11, 28, 44, SettlementTier.CITY, Set.of(),
		Set.of(ArchetypeInfo.SiteFlag.VERTICAL, ArchetypeInfo.SiteFlag.CENTRAL), List.of(), 1, 1.0);

	@Override
	public ArchetypeInfo info() {
		return INFO;
	}

	@Override
	public void draw(Draft d) {
		int w = d.frontage();
		int dp = d.depth();
		int avail = Math.min(w, dp);
		// the clock stage is the widest part; the shaft and the belfry stand two narrower
		int clockSize = avail >= 11 ? 11 : avail >= 9 ? 9 : 7;
		int shaftOuter = clockSize - 2;
		boolean piers = shaftOuter >= 7;
		int core = piers ? shaftOuter - 2 : shaftOuter;
		int cx0 = (w - clockSize) / 2;
		int cz0 = (dp - clockSize + 1) / 2;
		int sx0 = cx0 + 1 + (piers ? 1 : 0);
		int sz0 = cz0 + 1 + (piers ? 1 : 0);
		Stone body = Stone.panel(d);
		Stone dress = Stone.dressing(d);
		Stone base = Stone.stony(d, Role.PLINTH) ? Stone.of(d, Role.PLINTH) : Stone.footing(d);
		Tower shaft = new Tower(sx0, sz0, core, body, dress, base, piers);
		Tower clock = new Tower(cx0, cz0, clockSize, body, dress, base, false);
		// the bell chamber stands back behind a gallery on the clock stage's deck where there is room for one
		boolean gallery = clockSize >= 11;
		int belSize = gallery ? clockSize - 4 : clockSize - 2;
		int bo = (clockSize - belSize) / 2;
		Tower bel = new Tower(cx0 + bo, cz0 + bo, belSize, dress, dress, base, false);
		d.body(cx0, cz0, cx0 + clockSize - 1, cz0 + clockSize - 1);
		Crowns.Kind crown = crown(d);

		int top = d.maxHeight() - 1;
		int height = Math.max(24, (int) Math.round(top * (0.9 + 0.1 * d.random().nextDouble())));
		int r = clockSize >= 11 ? 4 : clockSize >= 9 ? 3 : 2;
		int clockRows = 2 * r + 3;
		int crownRows = Crowns.rows(crown, belSize, height);
		int ycB = height - crownRows;                  // the belfry's cornice
		int ycC = ycB - 6;                             // the clock stage's cornice: the gallery deck and the belfry floor
		int yk = ycC - clockRows - 1;                  // the corbel course under the clock stage
		if (yk < 9) {
			int lift = 9 - yk;
			yk += lift;
			ycC += lift;
			ycB += lift;
		}
		d.eave(ycC);
		Side fr = shaft.face(Dirs.FRONT);
		int door = fr.middle();

		// the shaft
		Grounds.footings(d, clock.x0, clock.z0, clock.x1, clock.z1, -1);
		shaft.plinth(d, 0, 1, true, fr.outX(door, 1), fr.outZ(door, 1));
		shaft.shaft(d, 2, yk);
		Paint glass = d.mat(Role.GLASS).pane();
		shaft.course(d, 7, true);
		for (Side f : shaft.faces()) {
			Details.slit(d, f, f.middle(), f.out() == Dirs.FRONT ? 5 : 3, 2, dress, glass);
		}
		int lo = 8;
		int hi = yk - 2;
		if (core >= 7 && hi - lo >= 5) {
			shaft.recess(d, lo + 1, hi, 3);
			int lh = Math.min(4, hi - lo - 1);
			for (Side f : shaft.faces()) {
				Details.arch(d, f.shifted(-1), f.middle(), 1, lo + 1 + (hi - lo - lh) / 2, lh, dress, glass);
			}
		} else if (hi - lo >= 2) {
			int lh = Math.min(3, hi - lo);
			shaft.openings(d, lo + (hi - lo + 1 - lh) / 2, lh, Tower.Opening.SINGLE, true, glass);
		}
		shaft.band(d, yk - 1);

		// the clock stage on its corbels, dials on every face, pinnacles at its corners
		try (var s = d.stage(StageKind.WALLS)) {
			for (int[] c : Draft.ring(clock.x0, clock.z0, clock.x1, clock.z1)) {
				Direction in = inward(clock, c[0], c[1]);
				d.set(c[0], yk, c[1], in != null ? dress.stairsOrSlab(in, Half.TOP) : dress.full());
			}
		}
		clock.shaft(d, yk + 1, ycC - 1);
		int ycl = yk + 2 + r;
		for (Side f : clock.faces()) {
			Details.clock(d, f, f.middle(), ycl, r, dress, 0xC10C);
		}
		clock.band(d, ycC - 1);
		clock.cornice(d, ycC);
		int pinH = 2;
		for (int[] c : new int[][]{{clock.x0, clock.z0}, {clock.x1, clock.z0}, {clock.x0, clock.z1}, {clock.x1, clock.z1}}) {
			Details.pinnacle(d, c[0], ycC + 1, c[1], pinH, dress, true);
		}
		// the gallery's balustrade along the clock stage's edge
		try (var s = d.stage(StageKind.EXTERIOR)) {
			for (int[] c : Draft.ring(clock.x0, clock.z0, clock.x1, clock.z1)) {
				if (gallery && d.isEmpty(c[0], ycC + 1, c[1])) {
					d.set(c[0], ycC + 1, c[1], dress.wallOrFull());
				}
			}
		}

		// the bell chamber, set back behind the gallery
		bel.shaft(d, ycC + 1, ycB - 1);
		bel.openings(d, ycC + 1, ycB - ycC - 2, bel.size >= 7 && d.chance(0.5) ? Tower.Opening.TRIFORA : Tower.Opening.ARCH, true, null);
		bel.band(d, ycB - 1);
		bel.cornice(d, ycB);
		int peak = Crowns.crown(d, crown, bel.x0, bel.z0, bel.x1, bel.z1, ycB + 1, top, crown == Crowns.Kind.STAVE || crown == Crowns.Kind.PAGODA
			|| crown == Crowns.Kind.PYRAMID ? Crowns.Mats.of(d, dress) : Crowns.Mats.coppered(d, dress, 0.45));

		// the door, floors, the stair, the clock room and the bells
		Grounds.door(d, fr, door, 0, dress);
		shaft.floor(d, -1, base.full());
		clock.floor(d, yk, d.mat(Role.FLOOR).full());
		bel.floor(d, ycC, dress.full());
		shaft.stair(d, 0, ycC + 1, Stone.dressing(d), dress.full(), 1);
		shaft.hollow(d, 0, yk - 1);
		clock.hollow(d, yk + 1, ycC - 1);
		bel.hollow(d, ycC + 1, ycB - 1);
		bel.bells(d, ycB - 1, d.mat(Role.BEAM).log(Direction.Axis.X), Direction.Axis.X);
		clockRoom(d, clock, shaft, yk + 1);
		// the gallery: a door out of each belfry arch is the arch itself; the walk is a viewpoint
		if (gallery) {
			d.poi(PoiType.VIEWPOINT, clock.x0 + 1, ycC + 1, clock.z0 + 1, Dirs.FRONT);
			d.poi(PoiType.VIEWPOINT, clock.x1 - 1, ycC + 1, clock.z1 - 1, Dirs.BACK);
		} else {
			d.poi(PoiType.VIEWPOINT, bel.x0 + 1, ycC + 1, bel.z0 + 1, Dirs.FRONT);
		}
		d.poi(PoiType.MEETING_POINT, fr.outX(door, 1), 0, Math.max(0, fr.outZ(door, 1) - 1), Dirs.BACK);

		Grounds.forecourt(d, shaft.outer(), fr.x(door), 0, fr.outZ(door, 1));
		Groundwork.clear(d, 0, 0, w - 1, dp - 1, Math.min(top, peak + 2));
		dev.voidmc.hearth.build.kit.refine.Voids.finish(d);
	}

	/** The direction from a ring cell of a tower's outline toward the shaft inside it (null at a corner). */
	private static Direction inward(Tower t, int x, int z) {
		boolean cornerX = x == t.x0 || x == t.x1;
		boolean cornerZ = z == t.z0 || z == t.z1;
		if (cornerX && cornerZ) {
			return null;
		}
		if (z == t.z0) {
			return Dirs.BACK;
		}
		if (z == t.z1) {
			return Dirs.FRONT;
		}
		return x == t.x0 ? Dirs.RIGHT : Dirs.LEFT;
	}

	/** The clock room: the works (grindstones for wheels, a chain for the pendulum's weight), a lectern, a chest. */
	private static void clockRoom(Draft d, Tower clock, Tower shaft, int y) {
		int ix0 = clock.x0 + 1;
		int iz0 = clock.z0 + 1;
		int ix1 = clock.x1 - 1;
		int iz1 = clock.z1 - 1;
		// the corners of the room, clear of the spiral
		int[][] corners = {{ix0, iz0}, {ix1, iz0}, {ix0, iz1}, {ix1, iz1}};
		try (var s = d.stage(StageKind.INTERIOR)) {
			for (int k = 0; k < corners.length; k++) {
				int[] c = corners[k];
				if (shaft.inside(c[0], c[1]) && Math.abs(c[0] - shaft.cx) <= 1 && Math.abs(c[1] - shaft.cz) <= 1) {
					continue;
				}
				switch (k) {
					case 0 -> d.set(c[0], y, c[1], States.attached(Blocks.GRINDSTONE, AttachFace.FLOOR, Dirs.RIGHT));
					case 1 -> d.set(c[0], y, c[1], States.attached(Blocks.GRINDSTONE, AttachFace.FLOOR, Dirs.LEFT));
					case 2 -> d.set(c[0], y, c[1], States.facing(Blocks.LECTERN, Dirs.FRONT));
					default -> Furniture.chest(d, c[0], y, c[1], Dirs.FRONT, List.of(new Object[]{"minecraft:clock", 1}, new Object[]{"minecraft:iron_ingot", 3}));
				}
			}
		}
		if (clock.size >= 9) {
			d.poi(PoiType.LECTERN, ix0, y, iz1 - 1, Dirs.BACK);
		}
	}

	/** The crown, from the param or the culture's manner. */
	static Crowns.Kind crown(Draft d) {
		String p = d.param("crown", "");
		if (!p.isEmpty()) {
			try {
				return Crowns.Kind.valueOf(p.toUpperCase(java.util.Locale.ROOT));
			} catch (IllegalArgumentException e) {
				// fall through
			}
		}
		Culture c = d.style().culture();
		Habits h = d.habits();
		if (h.roof().flare() || h.roof().weight(Habits.RoofForm.PAGODA) > 0) {
			return Crowns.Kind.PAGODA;
		}
		if (c == null) {
			return h.roof().weight(Habits.RoofForm.FLAT) > 2 ? Crowns.Kind.DOME : Crowns.Kind.SPIRE;
		}
		return switch (c) {
			case ALPINE -> d.chance(0.7) ? Crowns.Kind.ONION : Crowns.Kind.CUPOLA;
			case MEDITERRANEAN -> d.pick(Crowns.Kind.CUPOLA, Crowns.Kind.PYRAMID, Crowns.Kind.DOME);
			case DESERT, MESA, STEPPE -> d.pick(Crowns.Kind.DOME, Crowns.Kind.DOME, Crowns.Kind.CUPOLA);
			case NORTHERN, TUNDRA -> d.pick(Crowns.Kind.STAVE, Crowns.Kind.SPIRE);
			case BAYOU, TROPICAL -> d.pick(Crowns.Kind.PYRAMID, Crowns.Kind.CUPOLA);
			case MOORLAND -> d.pick(Crowns.Kind.SPIRE, Crowns.Kind.PARAPET, Crowns.Kind.OCTAGON);
			default -> d.pick(Crowns.Kind.SPIRE, Crowns.Kind.SPIRE, Crowns.Kind.CUPOLA, Crowns.Kind.OCTAGON);
		};
	}

}
