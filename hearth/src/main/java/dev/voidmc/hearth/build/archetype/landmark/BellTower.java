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
import dev.voidmc.hearth.build.draft.Mat;
import dev.voidmc.hearth.build.draft.Paint;
import dev.voidmc.hearth.build.kit.Side;
import dev.voidmc.hearth.build.kit.landmark.Crowns;
import dev.voidmc.hearth.build.kit.landmark.Details;
import dev.voidmc.hearth.build.kit.landmark.Grounds;
import dev.voidmc.hearth.build.kit.landmark.Stone;
import dev.voidmc.hearth.build.kit.landmark.Tower;
import dev.voidmc.hearth.build.kit.landmark.Wooden;
import dev.voidmc.hearth.build.style.Form;
import dev.voidmc.hearth.build.style.Role;
import dev.voidmc.hearth.build.terrain.Groundwork;
import java.util.List;
import java.util.Set;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;

/**
 * {@code hearth:landmark/bell_tower}: the campanile or belfry that rises over a town's roofs. A square shaft on a
 * chamfered plinth, its faces recessed between clasping corner piers, ringed by string courses over Lombard bands of
 * little arches; its openings widen as it climbs (slits, single lights, paired lights) to an open belfry where bells hang
 * from a beam; a corbelled cornice; and a crown in the culture's manner: a needle or broach spire behind pinnacles
 * (temperate, maritime, moorland), a low tiled pyramid on brackets or a brick campanile (mediterranean), an onion dome
 * (alpine), a crenellated parapet with a flag (moorland), a curved hip (wisteria, karst). Timber cultures (northern,
 * tundra, bayou, tropical) raise a stone base and a framed belfry above it under stacked shingle caps or a tin pyramid.
 * A spiral stair climbs from the door to the belfry, which is a viewpoint; a paved forecourt with a bench and a lamp
 * meets the street.
 *
 * <p>Params (all optional): {@code crown} (a {@link Crowns.Kind} name), {@code clock} (boolean: a dial on the stage under
 * the belfry), {@code variant}.
 */
public final class BellTower implements Archetype {
	public static final ArchetypeInfo INFO = new ArchetypeInfo(Hearth.id("landmark/bell_tower"), ArchetypeCategory.LANDMARK, Set.of(), 0,
		7, 11, 7, 11, 26, 44, SettlementTier.TOWN,
		Set.of(Culture.TEMPERATE, Culture.WISTERIA, Culture.NORTHERN, Culture.ALPINE, Culture.MOORLAND, Culture.MARITIME, Culture.MEDITERRANEAN,
			Culture.BAYOU),
		Set.of(ArchetypeInfo.SiteFlag.VERTICAL), List.of(), 1, 1.0);

	@Override
	public ArchetypeInfo info() {
		return INFO;
	}

	@Override
	public void draw(Draft d) {
		int w = d.frontage();
		int dp = d.depth();
		int avail = Math.min(w, dp);
		boolean timber = Wooden.timberCulture(d);
		boolean piers = !timber && d.chance(0.75);
		// the core: as massive as the plot allows, a margin round it where there is room
		int size = piers ? (avail >= 9 ? 7 : 5) : (avail >= 9 ? 7 : avail >= 7 ? 5 : 5);
		if (piers && avail >= 9 && avail <= 10 && d.chance(0.3)) {
			size = 5;
		}
		int outer = size + (piers ? 2 : 0);
		int ox = (w - outer) / 2 + (piers ? 1 : 0);
		int front = (dp - outer + 1) / 2;
		int oz = front + (piers ? 1 : 0);
		d.body(ox, oz, ox + size - 1, oz + size - 1);
		Crowns.Kind crown = crown(d, timber);
		Stone body = body(d, crown);
		Stone dress = Stone.dressing(d);
		Stone base = Stone.of(d, Role.PLINTH).isRole() && Stone.stony(d, Role.PLINTH) ? Stone.of(d, Role.PLINTH) : Stone.footing(d);
		Tower t = new Tower(ox, oz, size, body, dress, base, piers);
		int top = d.maxHeight() - 1;
		int height = Math.max(22, (int) Math.round(top * (0.86 + 0.14 * d.random().nextDouble())));
		// the cornice row, the belfry under it, the stages under that
		int yc = Math.max(14, height - Crowns.rows(crown, timber ? outer + 2 : outer, height));
		int belfryRows = size >= 7 ? 6 : 5;
		int yb = yc - belfryRows - 1;          // the belfry's floor row
		d.eave(yc);
		Side fr = t.face(Dirs.FRONT);
		int door = fr.middle();

		// footings, the shaft and its stages
		Grounds.footings(d, t.outer()[0], t.outer()[1], t.outer()[2], t.outer()[3], -1);
		int[] cs = t.outer();
		if (timber) {
			cs = Wooden.belfryTower(d, t, yb, yc, fr.outX(door, 1), fr.outZ(door, 1));
		} else {
			masonry(d, t, yb, yc, fr.outX(door, 1), fr.outZ(door, 1));
		}
		int peak = Crowns.crown(d, crown, cs[0], cs[1], cs[2], cs[3], yc + 1, top, crown == Crowns.Kind.STAVE || crown == Crowns.Kind.PAGODA
			|| crown == Crowns.Kind.PYRAMID ? Crowns.Mats.of(d, dress) : Crowns.Mats.coppered(d, dress, 0.4));

		// the door, the stair, the belfry and its bells
		Grounds.door(d, fr, door, 0, dress);
		Stone treads = Stone.dressing(d);
		Paint post = timber ? d.mat(Role.FRAME).log(Direction.Axis.Y) : dress.full();
		t.floor(d, -1, base.full());
		t.floor(d, yb, timber ? d.mat(Role.FLOOR).full() : dress.full());
		t.stair(d, 0, yb + 1, treads, post, 1);
		t.hollow(d, 0, yc - 1);
		t.bells(d, yc - 1, d.mat(Role.BEAM).log(Direction.Axis.X), Direction.Axis.X);
		d.poi(PoiType.VIEWPOINT, t.x0 + 1, yb + 1, t.z0 + 1, Dirs.FRONT);
		d.poi(PoiType.MEETING_POINT, fr.outX(door, 1), 0, Math.max(0, fr.outZ(door, 1) - 1), Dirs.BACK);

		// the forecourt and the way to the street
		Grounds.forecourt(d, t.outer(), fr.x(door), 0, fr.outZ(door, 1));
		Groundwork.clear(d, 0, 0, w - 1, dp - 1, Math.min(top, peak + 2));
	}

	/**
	 * The stone tower: a chamfered plinth, the door stage, stages ringed by courses over Lombard bands whose openings
	 * open up as they climb (slits, lancets, paired lights or a clock), the open belfry, the cornice.
	 */
	private static void masonry(Draft d, Tower t, int yb, int yc, int keepX, int keepZ) {
		t.plinth(d, 0, 1, true, keepX, keepZ);
		t.shaft(d, 2, yc - 1);
		// the belfry in dressed stone over the rougher shaft
		t.ring(d, yb + 1, yc - 1, t.dress);
		Paint glass = d.mat(Role.GLASS).pane();
		int doorTop = 7;
		// one course over the door stage, one under the belfry, one between if the shaft is long
		int mid = yb - doorTop >= 15 ? doorTop + (yb - doorTop) * 3 / 5 : -1;
		t.course(d, doorTop, true);
		t.course(d, yb, true);
		// a Lombard band of little arches under every course, so each stage reads as its own
		t.band(d, yb - 1);
		t.band(d, doorTop - 1);
		if (mid > 0) {
			t.course(d, mid, true);
			t.band(d, mid - 1);
			// the upper stage in dressed stone over the rendered shaft below: the tower lightens as it climbs
			if (t.body.main(Form.FULL) != t.dress.main(Form.FULL)) {
				t.ring(d, mid + 1, yb - 1, t.dress);
			}
		}
		// the door stage rusticated: every other course of its panels in the base stone
		try (var st = d.stage(StageKind.WALLS)) {
			for (int y = 2; y < doorTop - 1; y += 2) {
				for (Side f : t.faces()) {
					for (int i = 1; i < f.last(); i++) {
						if (d.isSolid(f.x(i), y, f.z(i))) {
							d.set(f.x(i), y, f.z(i), t.base.full());
						}
					}
				}
			}
		}
		// the door stage: a light over the door, slits on the other faces
		for (Side f : t.faces()) {
			int y = f.out() == Dirs.FRONT ? 5 : 3;
			Details.slit(d, f, f.middle(), y, 2, t.dress, glass);
		}
		// the long shaft: a deep blind arch with a lancet in it (or a lancet in the panel on a small tower)
		int lo = doorTop + 1;
		int hi = (mid > 0 ? mid : yb - 1) - 1;
		boolean clock = d.param("clock", t.size >= 7 && d.chance(0.55));
		if (t.size >= 7 && hi - lo >= 4) {
			t.recess(d, lo + 1, hi, 3);
			int lh = Math.min(4, hi - lo - 1);
			for (Side f : t.faces()) {
				Details.arch(d, f.shifted(-1), f.middle(), 1, lo + 1 + (hi - lo - lh) / 2, lh, t.dress, glass);
			}
		} else if (hi - lo >= 2) {
			int lh = Math.min(3, hi - lo);
			t.openings(d, lo + (hi - lo + 1 - lh) / 2, lh, Tower.Opening.SINGLE, true, glass);
		}
		// the upper stage: paired lights or a clock
		if (mid > 0) {
			int ulo = mid + 1;
			int uhi = yb - 2;
			int r = t.size >= 9 ? 3 : 2;
			if (clock && uhi - ulo + 1 >= 2 * r + 1) {
				int ycl = ulo + (uhi - ulo) / 2;
				for (Side f : t.faces()) {
					Details.clock(d, f, f.middle(), ycl, r, t.dress, 0xC10C);
				}
			} else if (uhi - ulo >= 2) {
				int lh = Math.min(3, uhi - ulo + 1);
				t.openings(d, ulo + (uhi - ulo + 1 - lh) / 2, lh, t.size >= 7 ? Tower.Opening.BIFORA : Tower.Opening.SINGLE, true, glass);
			}
		}
		// the belfry: open arches on every face, a balustrade along their sills
		int bh = yc - yb - 2;
		Tower.Opening belfry = t.size >= 7 && d.chance(0.4) ? Tower.Opening.TRIFORA : Tower.Opening.ARCH;
		t.openings(d, yb + 1, bh, belfry, true, null);
		if (belfry == Tower.Opening.ARCH) {
			for (Side f : t.faces()) {
				int m = f.middle();
				for (int i = m - 1; i <= m + 1; i++) {
					d.set(f.x(i), yb + 1, f.z(i), t.dress.wallOrFull());
				}
			}
		}
		t.band(d, yc - 1);
		t.cornice(d, yc);
	}

	/** The crown, from the param or the culture's manner. */
	static Crowns.Kind crown(Draft d, boolean timber) {
		String p = d.param("crown", "");
		if (!p.isEmpty()) {
			try {
				return Crowns.Kind.valueOf(p.toUpperCase(java.util.Locale.ROOT));
			} catch (IllegalArgumentException e) {
				// fall through to the culture's choice
			}
		}
		Culture c = d.style().culture();
		var h = d.habits();
		if (timber) {
			return h.roof().flare() ? Crowns.Kind.PAGODA : c == Culture.BAYOU || c == Culture.TROPICAL ? Crowns.Kind.PYRAMID : Crowns.Kind.STAVE;
		}
		if (h.roof().flare() || h.roof().weight(dev.voidmc.hearth.build.style.Habits.RoofForm.PAGODA) > 0) {
			return Crowns.Kind.PAGODA;
		}
		if (c == null) {
			return h.roof().weight(dev.voidmc.hearth.build.style.Habits.RoofForm.FLAT) > 2 ? Crowns.Kind.DOME : Crowns.Kind.SPIRE;
		}
		return switch (c) {
			case ALPINE -> d.chance(0.65) ? Crowns.Kind.ONION : Crowns.Kind.PYRAMID;
			case MEDITERRANEAN -> d.pick(Crowns.Kind.PYRAMID, Crowns.Kind.PYRAMID, Crowns.Kind.CUPOLA, Crowns.Kind.PARAPET);
			case MOORLAND -> d.pick(Crowns.Kind.PARAPET, Crowns.Kind.PARAPET, Crowns.Kind.BROACH, Crowns.Kind.SPIRE);
			case MARITIME -> d.pick(Crowns.Kind.SPIRE, Crowns.Kind.BROACH, Crowns.Kind.OCTAGON, Crowns.Kind.PARAPET);
			case DESERT, MESA, STEPPE -> d.pick(Crowns.Kind.DOME, Crowns.Kind.PARAPET, Crowns.Kind.CUPOLA);
			case WISTERIA -> d.pick(Crowns.Kind.BROACH, Crowns.Kind.OCTAGON, Crowns.Kind.PYRAMID);
			default -> d.pick(Crowns.Kind.SPIRE, Crowns.Kind.OCTAGON, Crowns.Kind.BROACH, Crowns.Kind.SPIRE, Crowns.Kind.PARAPET);
		};
	}

	/** The shaft's walling: a brick campanile under a tiled crown where the roof is brick, else the style's masonry. */
	private static Stone body(Draft d, Crowns.Kind crown) {
		if ((crown == Crowns.Kind.PYRAMID || crown == Crowns.Kind.CUPOLA) && Stone.stony(d, Role.ROOF) && d.chance(0.5)) {
			Mat roof = d.mat(Role.ROOF);
			if (roof.main(Form.FULL) != null && roof.has(Form.STAIRS)) {
				return Stone.of(d, Role.ROOF);
			}
		}
		return Stone.panel(d);
	}
}
