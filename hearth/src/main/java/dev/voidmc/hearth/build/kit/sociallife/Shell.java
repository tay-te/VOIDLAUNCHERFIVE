package dev.voidmc.hearth.build.kit.sociallife;

import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Mat;
import dev.voidmc.hearth.build.kit.Body;
import dev.voidmc.hearth.build.kit.Facade;
import dev.voidmc.hearth.build.kit.Openings;
import dev.voidmc.hearth.build.kit.Roofs;
import dev.voidmc.hearth.build.kit.Side;
import dev.voidmc.hearth.build.kit.civic.Adobe;
import dev.voidmc.hearth.build.kit.civic.Storeys;
import dev.voidmc.hearth.build.kit.civic.Tradition;
import dev.voidmc.hearth.build.kit.homestead.Bohemia;
import dev.voidmc.hearth.build.kit.townhome.Projections;
import dev.voidmc.hearth.build.kit.light.Lighting;
import dev.voidmc.hearth.build.style.Habits.RoofForm;
import dev.voidmc.hearth.build.style.Habits.WallKind;
import dev.voidmc.hearth.build.style.Role;
import java.util.ArrayList;
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

	/**
	 * What a shell drew. {@code doors} holds every door's column (the main one first); {@code frontZ} is the first row
	 * inside the ground storey (behind the arcade's walk where there is one).
	 */
	public record Built(Body body, Facade facade, Roofs.Roof roof, int deck, int skyline, Side doorSide, int doorCol, int[] inside, int[] outside,
		int[] doors, int frontZ) {
	}

	/**
	 * Builds a shell over {@code body} with the ridge along {@code ridge}: the main door in wall {@code doorSide} at column
	 * {@code doorCol} (nearest bay where the walls are framed), windows of {@code windowWidth} at {@code density} in the
	 * other bays, {@code dormers} on long slopes. {@code stoneBase} puts a masonry ground storey under timber upper ones.
	 */
	public static Built build(Draft d, Body body, Direction.Axis ridge, Direction doorSide, int doorCol, int windowWidth, double density, boolean stoneBase,
		boolean stoneFloor, boolean dormers) {
		return build(d, body, ridge, doorSide, new int[]{doorCol}, windowWidth, density, stoneBase, stoneFloor, dormers, 0);
	}

	/**
	 * {@link #build} with more than one door in the door's wall (a row of almshouse cells, each with its own door onto the
	 * court: {@code doorCols}, the main one first, each kept clear of the others' windows) and, where {@code walk} is
	 * above nought and the house has an upper storey over a front door, the ground storey's street wall opened into an
	 * arcade (the Bohemian {@code podloubí}): piers and round heads along the front, a paved walk {@code walk} rows deep
	 * under the upper storey with beams across its ceiling, and the door and windows in the inner wall behind it.
	 */
	public static Built build(Draft d, Body body, Direction.Axis ridge, Direction doorSide, int[] doorCols, int windowWidth, double density,
		boolean stoneBase, boolean stoneFloor, boolean dormers, int walk) {
		Tradition t = Tradition.of(d);
		boolean raised = t.raised();
		Storeys.ground(d, body, raised);
		int storeys = body.storeys();
		boolean arcade = arcades(body, doorSide, walk);
		int[] doors = doorCols.clone();
		Facade fac;
		if (doorSide == Dirs.FRONT) {
			fac = Facade.plan(d, body, doors[0], 1, windowWidth, density);
			Integer placed = null;
			for (Facade.Opening o : fac.openings(Dirs.FRONT)) {
				if (o.kind() == Facade.Kind.DOOR) {
					placed = o.at();
				}
			}
			if (placed == null) {
				fac.add(Dirs.FRONT, new Facade.Opening(Facade.Kind.DOOR, doors[0], 1));
			} else {
				doors[0] = placed;
			}
		} else {
			fac = Facade.plan(d, body, -1, 1, windowWidth, density);
			doors[0] = cutDoor(fac, doorSide, doors[0]);
		}
		for (int k = 1; k < doors.length; k++) {
			doors[k] = cutDoor(fac, doorSide, doors[k]);
		}
		int doorCol = doors[0];
		WallKind[] kinds = stoneBase && storeys > 1 ? Storeys.stoneBase(d, storeys) : null;
		Storeys.walls(d, body, fac, 0, storeys - 1, kinds, stoneFloor);
		if (arcade) {
			// the arcaded front's ground storey has no windows of its own: the arches stand there
			Storeys.windows(d, body, fac, 1, storeys - 1, 2);
			List<Facade.Opening> front = fac.openings(Dirs.FRONT);
			List<Facade.Opening> kept = new ArrayList<>(front);
			if (!front.isEmpty()) {
				front.clear();
			}
			Storeys.windows(d, body, fac, 0, 0, 2);
			for (Facade.Opening o : kept) {
				fac.add(Dirs.FRONT, o);
			}
		} else {
			Storeys.windows(d, body, fac, 0, storeys - 1, 2);
		}
		if (Adobe.is(d)) {
			for (int k = 0; k < storeys; k++) {
				Adobe.screens(d, body, k);
			}
		}
		Side side = body.side(0, doorSide);
		int y = body.baseY;
		int frontZ = body.rect(0)[1] + 1;
		if (arcade) {
			side = arcade(d, body, walk);
			frontZ = side.z(0) + 1;
		}
		for (int k = doors.length - 1; k >= 0; k--) {
			Openings.door(d, side, doors[k], y, 1, new Openings.DoorOpts(false, true, true));
			int di = side.index(doors[k]);
			int li = di + 1 <= side.last() - 1 ? di + 1 : di - 1;
			Lighting.doorLamp(d, side.outX(li, 1), y + 2, side.outZ(li, 1), side.out().getOpposite());
		}
		int i = side.index(doorCol);
		int[] inside = {side.x(i) - Dirs.dx(side.out()), side.z(i) - Dirs.dz(side.out())};
		int[] outside = {side.outX(i, 1), side.outZ(i, 1)};

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
		return new Built(body, fac, roof, deck, skyline, side, doorCol, inside, outside, doors, frontZ);
	}

	/** Whether {@link #build} opens the body's front into an arcade of a walk this deep. */
	public static boolean arcades(Body body, Direction doorSide, int walk) {
		return walk > 0 && doorSide == Dirs.FRONT && body.storeys() > 1 && body.depth() - walk >= 6 && body.height(0) >= 4;
	}

	/**
	 * Makes room for a door at column {@code col} of wall {@code side} in a planned facade: off a post (the next column),
	 * clear of the windows beside it. Returns the column it went in.
	 */
	private static int cutDoor(Facade fac, Direction side, int col) {
		if (fac.posts(side).contains(col)) {
			col++;
		}
		int dc = col;
		List<Facade.Opening> list = fac.openings(side);
		if (!list.isEmpty()) {
			list.removeIf(o -> o.at() <= dc + 1 && o.at() + o.width() - 1 >= dc - 1);
		}
		fac.add(side, new Facade.Opening(Facade.Kind.DOOR, col, 1));
		return col;
	}

	/**
	 * The arcade under a front upper storey: the ground storey's street wall opened into round-headed arches on piers of
	 * the accent stone, the walk behind them {@code walk} rows deep, paved and ceiled with beams, a lantern hung over it,
	 * and the inner wall across the house from side wall to side wall up to the floor above. Returns that inner wall as a
	 * side facing the street (the door goes in it).
	 */
	private static Side arcade(Draft d, Body b, int walk) {
		int[] r = b.rect(0);
		Side front = b.side(0, Dirs.FRONT);
		int y = b.baseY;
		int innerZ = r[1] + walk + 1;
		int ceiling = b.floorY(1) - 2 - y;
		int bay = front.length() >= 11 ? 3 : 2;
		Projections.arcade(d, front, 0, front.last(), y, ceiling, bay, false);
		Mat wall = d.mat(Role.WALL);
		Mat paving = d.mat(Role.PAVING);
		try (var s = d.stage(StageKind.WALLS)) {
			for (int x = r[0] + 1; x <= r[2] - 1; x++) {
				for (int yy = y; yy <= y + ceiling; yy++) {
					d.set(x, yy, innerZ, wall.full());
				}
			}
		}
		try (var s = d.stage(StageKind.FLOORS)) {
			for (int z = r[1]; z < innerZ; z++) {
				for (int x = r[0] + 1; x <= r[2] - 1; x++) {
					if (z > r[1] || d.isEmpty(x, y, z)) {
						d.set(x, y - 1, z, paving.full());
					}
				}
			}
		}
		// beams across the walk's ceiling, a lantern hung over its middle
		try (var s = d.stage(StageKind.WALLS)) {
			for (int z = r[1] + 1; z < innerZ; z++) {
				for (int x = r[0] + 1; x <= r[2] - 1; x++) {
					if (d.isEmpty(x, y + ceiling, z) && (x - r[0]) % 3 == 0) {
						d.set(x, y + ceiling, z, d.mat(Role.BEAM).log(Direction.Axis.Z));
					}
				}
			}
		}
		return Side.of(r[0], innerZ, r[2], r[3], Dirs.FRONT);
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
