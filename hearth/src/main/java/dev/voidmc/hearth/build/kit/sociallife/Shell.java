package dev.voidmc.hearth.build.kit.sociallife;

import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.kit.Body;
import dev.voidmc.hearth.build.kit.Facade;
import dev.voidmc.hearth.build.kit.Openings;
import dev.voidmc.hearth.build.kit.Roofs;
import dev.voidmc.hearth.build.kit.Side;
import dev.voidmc.hearth.build.kit.civic.Adobe;
import dev.voidmc.hearth.build.kit.civic.Storeys;
import dev.voidmc.hearth.build.kit.civic.Tradition;
import dev.voidmc.hearth.build.kit.homestead.Bohemia;
import dev.voidmc.hearth.build.kit.light.Lighting;
import dev.voidmc.hearth.build.style.Habits.RoofForm;
import dev.voidmc.hearth.build.style.Habits.WallKind;
import java.util.List;
import net.minecraft.core.Direction;

/**
 * The shell of a house of the social-life family (a hostel, an almshouse range, an orphanage, an infirmary): its body
 * grounded, its walls storey by storey in the culture's construction (a stone ground storey under timber where the
 * culture builds so), windows lined up storey over storey, the door with its lamp, and the culture's roof: the Bohemian
 * village's steep shingles with boarded gables over their little pent, the
 * karst's curved roof, the dry south's flat roof behind a parapet, the others' own form and pitch, always under the
 * plot's height. The archetype then partitions, stairs and furnishes it.
 */
public final class Shell {
	private Shell() {
	}

	/** What a shell drew. */
	public record Built(Body body, Facade facade, Roofs.Roof roof, int deck, int skyline, Side doorSide, int doorCol, int[] inside, int[] outside) {
	}

	/**
	 * Builds a shell over {@code body} with the ridge along {@code ridge}: the main door in wall {@code doorSide} at column
	 * {@code doorCol} (nearest bay where the walls are framed), windows of {@code windowWidth} at {@code density} in the
	 * other bays, {@code dormers} on long slopes. {@code stoneBase} puts a masonry ground storey under timber upper ones.
	 */
	public static Built build(Draft d, Body body, Direction.Axis ridge, Direction doorSide, int doorCol, int windowWidth, double density, boolean stoneBase,
		boolean stoneFloor, boolean dormers) {
		Tradition t = Tradition.of(d);
		boolean raised = t.raised();
		Storeys.ground(d, body, raised);
		int storeys = body.storeys();
		Facade fac;
		if (doorSide == Dirs.FRONT) {
			fac = Facade.plan(d, body, doorCol, 1, windowWidth, density);
			Integer placed = null;
			for (Facade.Opening o : fac.openings(Dirs.FRONT)) {
				if (o.kind() == Facade.Kind.DOOR) {
					placed = o.at();
				}
			}
			if (placed == null) {
				fac.add(Dirs.FRONT, new Facade.Opening(Facade.Kind.DOOR, doorCol, 1));
			} else {
				doorCol = placed;
			}
		} else {
			fac = Facade.plan(d, body, -1, 1, windowWidth, density);
			if (fac.posts(doorSide).contains(doorCol)) {
				doorCol++;
			}
			int dc = doorCol;
			List<Facade.Opening> list = fac.openings(doorSide);
			if (!list.isEmpty()) {
				list.removeIf(o -> o.at() <= dc + 1 && o.at() + o.width() - 1 >= dc - 1);
			}
			fac.add(doorSide, new Facade.Opening(Facade.Kind.DOOR, doorCol, 1));
		}
		WallKind[] kinds = stoneBase && storeys > 1 ? Storeys.stoneBase(d, storeys) : null;
		Storeys.walls(d, body, fac, 0, storeys - 1, kinds, stoneFloor);
		Storeys.windows(d, body, fac, 0, storeys - 1, 2);
		if (Adobe.is(d)) {
			for (int k = 0; k < storeys; k++) {
				Adobe.screens(d, body, k);
			}
		}
		Side side = body.side(0, doorSide);
		int y = body.baseY;
		Openings.door(d, side, doorCol, y, 1, new Openings.DoorOpts(false, true, true));
		int i = side.index(doorCol);
		int[] inside = {side.x(i) - Dirs.dx(side.out()), side.z(i) - Dirs.dz(side.out())};
		int[] outside = {side.outX(i, 1), side.outZ(i, 1)};
		int li = i + 1 <= side.last() - 1 ? i + 1 : i - 1;
		Lighting.doorLamp(d, side.outX(li, 1), y + 2, side.outZ(li, 1), side.out().getOpposite());

		// the roof
		RoofForm form = roofForm(d, t);
		Roofs.Roof roof = null;
		int deck = Integer.MIN_VALUE;
		int skyline;
		if (form == RoofForm.FLAT || form == RoofForm.PAGODA) {
			Storeys.Top tp = Storeys.roof(d, body, form, ridge, Math.max(1.0, d.habits().roof().pitch()), 1, dormers);
			roof = tp.roof();
			deck = tp.deck();
			skyline = roof == null ? deck + 3 : tp.ridge();
		} else {
			roof = pitched(d, body, form, ridge, dormers);
			skyline = roof == null ? body.plateY() + 2 : roof.ridgeY;
		}
		return new Built(body, fac, roof, deck, skyline, side, doorCol, inside, outside);
	}

	/** The roof form a culture raises over a house of this family. */
	public static RoofForm roofForm(Draft d, Tradition t) {
		if (t.flatRoofed()) {
			return RoofForm.FLAT;
		}
		if (t == Tradition.KARST) {
			return RoofForm.PAGODA;
		}
		if (Bohemia.is(d)) {
			return Bohemia.form(d);
		}
		return d.habits().roof().pick(d.random(), RoofForm.GABLE, RoofForm.HALF_HIP, RoofForm.HIP);
	}

	/**
	 * A pitched roof over the body's top storey: steep (two to one) in the Bohemian village where the height allows, with
	 * its gables boarded over a pent; horns over the gables in the north; the culture's pitch elsewhere; dormers on long
	 * slopes when asked. The ridge stays three under the plot's height (room for a chimney over it); a flat roof is drawn
	 * (and null returned) where nothing pitched fits.
	 */
	public static Roofs.Roof pitched(Draft d, Body body, RoofForm form, Direction.Axis axis, boolean dormers) {
		int[] tr = body.topRect();
		int plate = body.plateY();
		Roofs.Opts o = Roofs.Opts.of(d);
		int span = axis == Direction.Axis.X ? tr[3] - tr[1] + 1 : tr[2] - tr[0] + 1;
		if (form == RoofForm.HIP) {
			span = Math.min(tr[3] - tr[1] + 1, tr[2] - tr[0] + 1);
		}
		double half = span / 2.0 + o.eaves();
		if (plate + 1 + 0.5 * half + 1 > d.maxHeight() - 1) {
			Roofs.flat(d, tr[0], tr[1], tr[2], tr[3], plate, plate + 2 <= d.maxHeight() - 1);
			return null;
		}
		double base = d.habits().roof().pitch();
		boolean hipped = form == RoofForm.HIP || form == RoofForm.HALF_HIP;
		double want = Bohemia.is(d) ? (hipped ? Math.max(1.0, base) : 2.0) : base < 0.75 ? base : Math.max(1.0, base);
		double fits = (d.maxHeight() - 3 - (plate + 1)) / Math.max(1.0, half);
		double p = Math.min(want, Math.max(0.5, fits));
		p = p >= 2.0 ? 2.0 : p >= 1.5 ? 1.5 : p >= 1.0 ? 1.0 : 0.5;
		Roofs.Plan plan = Roofs.plan(d, tr[0], tr[1], tr[2], tr[3], plate, form, axis, o.withPitch(p), body.partyLeft, body.partyRight);
		if (dormers && p >= 1.0 && half >= 5) {
			Direction[] faces = axis == Direction.Axis.X ? new Direction[]{Dirs.FRONT, Dirs.BACK} : new Direction[]{Dirs.LEFT, Dirs.RIGHT};
			int top = body.storeys() - 1;
			for (Direction sd : faces) {
				Side side = body.side(top, sd);
				int n = side.length();
				int count = Math.max(1, (n - 2) / 6);
				for (int k = 0; k < count; k++) {
					int i = 2 + (int) Math.round((k + 0.5) * (n - 4) / count) - 1;
					if (i >= 2 && i <= n - 3) {
						plan.dormer(side, side.column(i), 1, plate + 1);
					}
				}
			}
		}
		Roofs.Roof r = plan.draw(d).roof();
		if (Bohemia.is(d)) {
			Bohemia.carryRidge(d, r);
			if (form == RoofForm.GABLE) {
				Bohemia.gables(d, r, axis, tr, plate);
			}
		}
		return r;
	}
}
