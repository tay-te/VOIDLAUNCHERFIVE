package dev.voidmc.hearth.build.kit.civic;

import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Paint;
import dev.voidmc.hearth.build.kit.Side;
import dev.voidmc.hearth.build.kit.Stairs;
import dev.voidmc.hearth.build.kit.landmark.Crowns;
import dev.voidmc.hearth.build.kit.landmark.Details;
import dev.voidmc.hearth.build.kit.landmark.Shapes;
import dev.voidmc.hearth.build.kit.landmark.Stone;
import dev.voidmc.hearth.build.kit.landmark.Tower;
import dev.voidmc.hearth.build.style.Form;
import dev.voidmc.hearth.build.style.Role;
import dev.voidmc.hearth.build.terrain.Groundwork;
import java.util.Set;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * The towers of a Bohemian market town (the temperate and wisteria traditions' churches and town halls): five square,
 * rendered in the walls' plaster between corner stones of dressed stone over a stone plinth, a string course at each
 * stage, little arched corbels under the belfry, slit lights and a clock, a belfry of paired lights parted by a
 * colonnette, and the crown those towers are known by: a gallery corbelled out a block on the cornice, its parapet cut in
 * merlons, a slender pinnacle at each corner, and a needle spire rising from the deck inside it, slated or shingled like
 * the roofs (copper on a share of a city's towers).
 */
public final class Bohemian {
	private Bohemian() {
	}

	/** Rows from the belfry's floor to its cornice. */
	public static final int BELFRY = 6;
	/** The fewest rows a crown is given. */
	public static final int CROWN = 9;

	/**
	 * The belfry's floor for a tower beside a roof whose ridge is {@code ridge} (the arches clear of it), lowered toward
	 * {@code plate + 3} where the plot's height would otherwise starve the crown.
	 */
	public static int belfryFloor(int plate, int ridge, int maxH) {
		int top = maxH - 1;
		int yb = Math.max(plate + 4, ridge);
		if (top - (yb + BELFRY) < CROWN) {
			yb = Math.max(plate + 3, top - CROWN - BELFRY);
		}
		return yb;
	}

	/**
	 * A tower five square from standing level {@code base}, its core at {@code (x0, z0)}: the plinth, the shaft, stage
	 * courses at {@code doorTop} (and half way to the belfry when the shaft is tall), slits in the faces not in
	 * {@code blind}, clocks on the faces in {@code clocks}, the belfry from {@code yb} with its bell, the gallery and the
	 * spire. The inside is cleared from the floor to the belfry and floored in the paving; the ringer's ladder hangs at
	 * {@code ladder} ({x, z}, facing {@code ladderFacing}, out of the wall behind it). Doors and ways through are the
	 * caller's. Returns the top.
	 */
	public static int tower(Draft d, int x0, int z0, int base, int doorTop, int yb, int maxH, int tier, Set<Direction> blind,
		Set<Direction> clocks, int[] ladder, Direction ladderFacing) {
		// the tower in the same render as the church's or the hall's walls (they lay the wall role as render whatever it is)
		Stone body = Stone.of(d, Role.WALL);
		Stone dress = Stone.dressing(d);
		Stone foot = Stone.stony(d, Role.PLINTH) ? Stone.of(d, Role.PLINTH) : Stone.footing(d);
		Tower t = new Tower(x0, z0, 5, body, dress, foot, false);
		int top = maxH - 1;
		int yc = yb + BELFRY;
		Side fr = t.face(Dirs.FRONT);
		Groundwork.footings(d, t.x0, t.z0, t.x1, t.z1, base - 1);
		t.plinth(d, base, base, true, fr.outX(fr.middle(), 1), fr.outZ(fr.middle(), 1));
		t.shaft(d, base + 1, yc - 1);
		d.clearBox(t.x0 + 1, base, t.z0 + 1, t.x1 - 1, yb - 1, t.z1 - 1);
		t.floor(d, base - 1, d.mat(Role.PAVING).full());
		// a course at each stage, little arches only under the belfry, so the render reads between the dressings
		t.course(d, doorTop, false);
		int mid = yb - doorTop >= 10 ? doorTop + (yb - doorTop) / 2 : -1;
		if (mid > 0) {
			t.course(d, mid, false);
		}
		t.band(d, yb - 1);
		t.course(d, yb, false);
		Paint glass = d.mat(Role.GLASS).pane();
		for (Side f : t.faces()) {
			if (blind.contains(f.out())) {
				continue;
			}
			int lo = doorTop + 1;
			int hi = (mid > 0 ? mid : yb) - 1;
			if (hi - lo >= 2) {
				Details.slit(d, f, f.middle(), lo + (hi - lo - 1) / 2, 2, dress, glass);
			}
		}
		// the clock under the belfry
		int clo = (mid > 0 ? mid : doorTop) + 1;
		int ycl = Math.max(clo + 1, yb - 2);
		if (ycl + 1 < yb) {
			for (Side f : t.faces()) {
				if (clocks.contains(f.out())) {
					Towers.clock(d, f, 2, ycl, false);
				}
			}
		}
		// the belfry: paired lights parted by a colonnette in every face, open so the bell is heard
		t.openings(d, yb + 1, yc - yb - 2, Tower.Opening.BIFORA, true, null);
		t.floor(d, yb, dress.full());
		t.hollow(d, yb + 1, yc - 1);
		t.band(d, yc - 1);
		t.cornice(d, yc);
		t.bells(d, yc - 1, d.mat(Role.BEAM).log(Direction.Axis.X), Direction.Axis.X);
		if (ladder != null) {
			Stairs.ladder(d, ladder[0], ladder[1], base, yb, ladderFacing);
		}
		return crown(d, t, yc, top, dress, tier);
	}

	/**
	 * The crown: the cornice's corbels carry a gallery a block proud of the shaft, its parapet cut in merlons and a slender
	 * pinnacle at each corner, and a needle spire three wide rises from the deck inside (a walk all round it), slated or
	 * shingled like the roofs, coppered on a share of a city's towers. Returns the top.
	 */
	static int crown(Draft d, Tower t, int yc, int top, Stone dress, int tier) {
		int x0 = t.x0 - 1;
		int z0 = t.z0 - 1;
		int x1 = t.x1 + 1;
		int z1 = t.z1 + 1;
		int y = yc + 1;
		int peak = y;
		try (var st = d.stage(StageKind.ROOF)) {
			for (int[] c : Draft.ring(x0, z0, x1, z1)) {
				d.set(c[0], y, c[1], dress.full());
				if (!Crowns.isCorner(c, x0, z0, x1, z1) && ((c[0] + c[1]) & 1) == 0 && dress.has(Form.SLAB)) {
					d.set(c[0], y + 1, c[1], dress.slab(SlabType.BOTTOM));
				}
			}
			int ph = Math.max(2, Math.min(4, (top - y) / 5));
			for (int[] c : new int[][]{{x0, z0}, {x1, z0}, {x0, z1}, {x1, z1}}) {
				peak = Math.max(peak, Details.pinnacle(d, c[0], y + 1, c[1], ph, dress, true));
			}
			Stone skin = tier >= 4 && d.chance(0.35) || d.habits().roof().thatch() || !d.mat(Role.ROOF).has(Form.STAIRS) ? Stone.copper(d)
				: Stone.of(d, Role.ROOF);
			Paint post = skin.has(Form.WALL) ? skin.wall() : dress.wallOrFull();
			peak = Math.max(peak, Shapes.needle(d, t.cx, t.cz, 1, y, Math.max(6, top - y), skin.full(), skin, post, 0));
		}
		return peak;
	}

	/** A pointed doorway of dressed stone in cell i of wall f, standing at y, under a little gable of the dressing. */
	public static void portal(Draft d, Side f, int i, int y) {
		Stone dress = Stone.dressing(d);
		dev.voidmc.hearth.build.kit.landmark.Grounds.door(d, f, i, y, dress);
		if (dress.has(Form.STAIRS) && f.outZ(i, 1) >= 0) {
			int gy = y + 3;
			try (var st = d.stage(StageKind.WALLS)) {
				d.set(f.outX(i - 1, 1), gy, f.outZ(i - 1, 1), dress.stairs(f.along(), Half.BOTTOM));
				d.set(f.outX(i + 1, 1), gy, f.outZ(i + 1, 1), dress.stairs(f.along().getOpposite(), Half.BOTTOM));
				d.set(f.outX(i, 1), gy, f.outZ(i, 1), dress.full());
				d.set(f.outX(i, 1), gy + 1, f.outZ(i, 1), dress.slabOrNothing(SlabType.BOTTOM));
			}
		}
	}
}
