package dev.voidmc.hearth.build.archetype.civic;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.ArchetypeCategory;
import dev.voidmc.hearth.api.ArchetypeInfo;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.PoiType;
import dev.voidmc.hearth.api.SettlementTier;
import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.Archetype;
import dev.voidmc.hearth.build.archetype.civic.Precinct.Kind;
import dev.voidmc.hearth.build.archetype.civic.Precinct.Range;
import dev.voidmc.hearth.build.archetype.civic.Precinct.Want;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Paint;
import dev.voidmc.hearth.build.kit.Side;
import dev.voidmc.hearth.build.kit.civic.Towers;
import dev.voidmc.hearth.build.kit.civic.Tradition;
import dev.voidmc.hearth.build.kit.landmark.Details;
import dev.voidmc.hearth.build.kit.landmark.Stone;
import dev.voidmc.hearth.build.kit.landscape.Benches;
import dev.voidmc.hearth.build.kit.learning.Instruments;
import dev.voidmc.hearth.build.style.Role;
import java.util.List;
import java.util.Set;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;

/**
 * {@code hearth:civic/university}: the city's college of masters and scholars, a closed quadrangle of four ranges round
 * an arcaded court ({@link Precinct}, without a church), its gate under a tower.
 * <ul>
 *   <li><b>The gate tower</b> over the passage in the front range: dressed quoins, a clock toward the street, an open
 *   belfry with the bell that rings the hours of lectures, capped in the culture's manner (a needle spire in the timber
 *   lands, an onion in the mountains, a dome in the dry lands, a curved roof in the karst); the porter's lodge beside the
 *   passage, the rector's parlour on the other side, scholars' chambers over.</li>
 *   <li><b>The lecture halls</b> in the side ranges: the master's lectern at the end of the hall, benches in rows facing
 *   it, shelves; scholars' chambers over (bed, desk, shelf of books by turns).</li>
 *   <li><b>The library hall</b> filling the back range on both floors: shelves along every windowless wall, lecterns,
 *   reading tables and carrels.</li>
 *   <li><b>The court</b>: arcades on every side, a lawn crossed by paths, a sundial in the middle, benches in the walk.</li>
 * </ul>
 * Folk: SCHOLARs at the lecterns of the halls and the library; eight beds in the chambers.
 *
 * <p>Params: {@code variant} (int).
 */
public final class University implements Archetype {
	public static final ArchetypeInfo INFO = new ArchetypeInfo(Hearth.id("civic/university"), ArchetypeCategory.CIVIC, Set.of(JobType.SCHOLAR), 8,
		23, 33, 23, 33, 14, 24, SettlementTier.CITY, Set.of(), Set.of(), List.of(), 1, 1.0);

	@Override
	public ArchetypeInfo info() {
		return INFO;
	}

	@Override
	public void draw(Draft d) {
		Precinct p = new Precinct(d, false, false);
		p.gateGable = false;
		boolean two = p.storeys() > 1;
		if (two) {
			p.front.wants.add(List.of(Want.stair(Kind.PARLOUR, 1), Want.of(Kind.GATE, 0), Want.stair(Kind.PORTER, 1)));
			p.front.wants.add(List.of(Want.of(Kind.CHAMBERS, 1), Want.of(Kind.GATE, 0), Want.of(Kind.CHAMBERS, 1)));
			p.near.wants.add(List.of(Want.stair(Kind.LECTURE, 1)));
			p.near.wants.add(List.of(Want.of(Kind.CHAMBERS, 1)));
			p.far.wants.add(List.of(Want.stair(Kind.LECTURE, 1)));
			p.far.wants.add(List.of(Want.of(Kind.CHAMBERS, 1)));
			p.back.wants.add(List.of(Want.stair(Kind.LIBRARY, 1)));
			p.back.wants.add(List.of(Want.of(Kind.LIBRARY, 1)));
		} else {
			p.front.wants.add(List.of(Want.of(Kind.CHAMBERS, 1), Want.of(Kind.GATE, 0), Want.of(Kind.PORTER, 1)));
			p.near.wants.add(List.of(Want.of(Kind.LECTURE, 1.4), Want.of(Kind.CHAMBERS, 1)));
			p.far.wants.add(List.of(Want.of(Kind.LECTURE, 1.4), Want.of(Kind.CHAMBERS, 1)));
			p.back.wants.add(List.of(Want.of(Kind.LIBRARY, 1)));
		}
		p.shell();
		p.rooms();
		p.beds(Kind.CHAMBERS, 8);
		p.skyline = Math.max(p.skyline, p.roofs());
		p.skyline = Math.max(p.skyline, gateTower(p));
		p.walk();
		court(p);
		p.furnish();
		p.forecourt(-1);
		p.finish();
	}

	/**
	 * The tower over the gate: its walls are the passage's walls up to the ranges' plate (a wall across the passage's
	 * inner end, arched), then a shaft of its own above the roof, a clock toward the street, the belfry and the cap.
	 * Returns its top.
	 */
	private static int gateTower(Precinct p) {
		Draft d = p.d;
		Range r = p.front;
		if (r == null || r.gate == Integer.MIN_VALUE) {
			return 0;
		}
		int g = r.gate;
		int x0 = g - 2;
		int z0 = r.z0;
		int x1 = g + 2;
		int z1 = z0 + 4;
		int top = p.top;
		Tradition t = p.t;
		Stone body = Stone.panel(d);
		Stone dress = Stone.dressing(d);
		// the inner wall across the passage (z1), arched for the way through, with a light over
		int plate = p.plate;
		if (z1 < r.z1) {
			try (var s = d.stage(StageKind.WALLS)) {
				for (int x = x0; x <= x1; x++) {
					for (int y = 0; y <= plate; y++) {
						if (d.isEmpty(x, y, z1) || d.isAbsent(x, y, z1) || y > 3) {
							d.set(x, y, z1, x == x0 || x == x1 ? dress.full() : body.full());
						}
					}
				}
			}
			Side inner = Side.of(x0, z0, x1, z1, Dirs.BACK);
			Details.arch(d, inner, inner.index(g - 1), 3, 0, Math.min(4, r.body.height(0) - 1), dress, null);
			// the tower room over the passage: doors through its walls into the chambers either side, upstairs
			for (int y = 0; y < 3; y++) {
				for (int x = g - 1; x <= g + 1; x++) {
					p.keep(x, y, z1);
					p.keep(x, y, z1 + 1);
				}
			}
		}
		// the shaft above the roof: from the plate up to the belfry
		int ridge = p.roof != null ? p.roof.ridgeY : plate + 2;
		Towers.Cap cap = Towers.cap(t, true);
		if (d.style().culture() == dev.voidmc.hearth.api.Culture.TUNDRA) {
			cap = Towers.Cap.ONION;
		}
		// the belfry clear of the roofs, the cap taking what height is left (it flattens to fit)
		int bh = 4;
		int yb = Math.max(ridge + 1, plate + 4);
		if (yb + bh + 3 > top) {
			bh = Math.max(3, top - 3 - yb);
		}
		if (yb + bh + 3 > top) {
			yb = Math.max(plate + 2, top - 3 - bh);
		}
		// the tower reads from the ground: dressed quoins up its front corners, toothed out into the range's wall
		try (var s = d.stage(StageKind.WALLS)) {
			for (int y = 0; y <= plate; y++) {
				d.set(x0, y, z0, dress.full());
				d.set(x1, y, z0, dress.full());
				if ((y & 1) == 0) {
					if (dev.voidmc.hearth.build.kit.Openings.isWallBlock(d, x0 - 1, y, z0)) {
						d.set(x0 - 1, y, z0, dress.full());
					}
					if (dev.voidmc.hearth.build.kit.Openings.isWallBlock(d, x1 + 1, y, z0)) {
						d.set(x1 + 1, y, z0, dress.full());
					}
				}
			}
		}
		try (var s = d.stage(StageKind.WALLS)) {
			for (int[] c : dev.voidmc.hearth.build.draft.Draft.ring(x0, z0, x1, z1)) {
				boolean corner = (c[0] == x0 || c[0] == x1) && (c[1] == z0 || c[1] == z1);
				boolean next = !corner && (Math.abs(c[0] - x0) == 1 || Math.abs(c[0] - x1) == 1 || Math.abs(c[1] - z0) == 1 || Math.abs(c[1] - z1) == 1);
				for (int y = plate + 1; y < yb; y++) {
					Paint pt = y == plate + 1 || corner || next && ((y - plate) & 1) == 0 ? dress.full() : body.full();
					d.set(c[0], y, c[1], pt);
				}
			}
		}
		d.clearBox(x0 + 1, plate + 1, z0 + 1, x1 - 1, yb - 2, z1 - 1);
		// a floor under the belfry, and the room under it walled from the roof
		try (var s = d.stage(StageKind.FLOORS)) {
			for (int z = z0 + 1; z < z1; z++) {
				for (int x = x0 + 1; x < x1; x++) {
					d.set(x, yb - 1, z, d.mat(Role.FLOOR).full());
				}
			}
		}
		// a clock toward the street under the belfry, slits in the faces
		Side front = Side.of(x0, z0, x1, z1, Dirs.FRONT);
		if (yb - 2 > ridge - 1 && yb - 2 > plate + 2) {
			Towers.clock(d, front, 2, yb - 2, false);
		}
		for (Side f : Side.of(x0, z0, x1, z1)) {
			int y = Math.max(plate + 2, ridge - 1);
			if (y + 1 < yb - 3 || f.out() != Dirs.FRONT && y + 1 < yb - 1) {
				Details.slit(d, f, 2, y, 2, dress, d.mat(Role.GLASS).pane());
			}
		}
		Towers.belfry(d, x0, z0, 5, yb, bh, t, true);
		return Towers.cap(d, x0, z0, 5, yb + bh, cap, top + 1);
	}

	/** The court: a sundial in the middle of the lawn's crossing, benches in the walk against the ranges. */
	private static void court(Precinct p) {
		p.garth(false);
		Draft d = p.d;
		int cx = (p.gax0 + p.gax1) / 2;
		int cz = (p.gaz0 + p.gaz1) / 2;
		if (p.gax1 - p.gax0 >= 2 && p.gaz1 - p.gaz0 >= 2 && d.isEmpty(cx, 0, cz)) {
			Instruments.sundial(d, cx, 0, cz);
			d.poi(PoiType.MEETING_POINT, cx, 0, cz - 1, Dirs.BACK);
		}
		// benches along the back walk, facing the court
		if (p.wB >= 2) {
			int z = p.kz1;
			for (int x = p.kx0 + 2; x + 1 <= p.kx1 - 2; x += 5) {
				if (free2(p, x, z) && free2(p, x + 1, z)) {
					Benches.bench(d, x, 0, z, Dirs.FRONT, Dirs.RIGHT, 2, false);
				}
			}
		}
	}

	private static boolean free2(Precinct p, int x, int z) {
		return p.free(x, 0, z) && !p.kept(x, 0, z - 1);
	}
}
