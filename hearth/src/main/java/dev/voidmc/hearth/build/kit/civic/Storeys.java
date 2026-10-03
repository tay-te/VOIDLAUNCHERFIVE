package dev.voidmc.hearth.build.kit.civic;

import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Mat;
import dev.voidmc.hearth.build.kit.Body;
import dev.voidmc.hearth.build.kit.Facade;
import dev.voidmc.hearth.build.kit.Floors;
import dev.voidmc.hearth.build.kit.Openings;
import dev.voidmc.hearth.build.kit.Roofs;
import dev.voidmc.hearth.build.kit.Side;
import dev.voidmc.hearth.build.kit.Stairs;
import dev.voidmc.hearth.build.kit.Walls;
import dev.voidmc.hearth.build.style.Habits;
import dev.voidmc.hearth.build.style.Habits.RoofForm;
import dev.voidmc.hearth.build.style.Habits.WallKind;
import dev.voidmc.hearth.build.style.Role;
import dev.voidmc.hearth.build.terrain.Groundwork;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.Half;

/**
 * The storeys of a civic building: grounding, floors and walls storey by storey (each storey may be built in its own
 * wall kind: a stone ground floor under a timber hall), tall windows lined up storey over storey, the roof the culture
 * raises over a wide hall (pitched low enough to leave room for a cupola), and stairs between floors.
 */
public final class Storeys {
	private Storeys() {
	}

	/** Grounds a body: footings to the ground (or stilts and boards when raised) and an apron of earth round it. */
	public static void ground(Draft d, Body b, boolean raised) {
		int[] r = b.rect(0);
		if (raised) {
			Groundwork.stilts(d, r[0], r[1], r[2], r[3], b.baseY - 1, 2);
			Floors.boards(d, r[0], r[1], r[2], r[3], b.baseY - 1);
		} else {
			Groundwork.footings(d, r[0], r[1], r[2], r[3], b.baseY - 1);
			Groundwork.apron(d, r[0], r[1], r[2], r[3], b.baseY, 2);
		}
	}

	/**
	 * Floors and walls for storeys {@code k0..k1} of a body, storey k in {@code kinds[k]} (null for the style's own),
	 * and each storey's room carved. Jettied floors run out over the wall below.
	 */
	public static void walls(Draft d, Body b, Facade f, int k0, int k1, WallKind[] kinds, boolean stoneFloors) {
		for (int k = k0; k <= k1; k++) {
			int[] r = b.rect(k);
			Halls.floor(d, r[0] + 1, r[1] + 1, r[2] - 1, r[3] - 1, b.floorY(k) - 1, stoneFloors && k == 0);
			if (k > 0) {
				Floors.boards(d, r[0], r[1], r[2], r[3], b.floorY(k) - 1);
			}
			WallKind kind = kinds != null && k < kinds.length && kinds[k] != null ? kinds[k] : d.habits().walls().kind(k);
			Walls.storey(d, b, k, f, kind);
		}
		for (int k = k0; k <= k1; k++) {
			int[] r = b.rect(k);
			d.carve(r[0] + 1, b.floorY(k), r[1] + 1, r[2] - 1, b.floorY(k + 1) - 2, r[3] - 1);
		}
	}

	/**
	 * Windows of storeys {@code k0..k1} where the facade has them (doors on upper storeys become windows), {@code h}
	 * high, in the culture's manner (lattice, arched or dressed with shutters and boxes).
	 */
	public static void windows(Draft d, Body b, Facade f, int k0, int k1, int h) {
		Habits.Windows hw = d.habits().windows();
		Openings.WindowStyle style = hw.lattice() ? Openings.WindowStyle.LATTICE
			: d.chance(hw.arched()) ? Openings.WindowStyle.ARCHED : Openings.WindowStyle.DRESSED;
		for (int k = k0; k <= k1; k++) {
			int sh = b.height(k);
			for (Side s : b.sides(k)) {
				if (b.isParty(s.out())) {
					continue;
				}
				for (Facade.Opening o : f.openings(s.out())) {
					if (!s.contains(o.at()) || !s.contains(o.at() + o.width() - 1)) {
						continue;
					}
					if (o.kind() == Facade.Kind.DOOR && k == 0) {
						continue;
					}
					int height = Math.min(h, sh - 2);
					if (height >= 1) {
						Openings.window(d, s, o.at(), b.floorY(k) + 1, o.width(), height, style);
					}
				}
			}
		}
	}

	/** The wall course on which the roof sits, and what the roof drew. */
	public record Top(Roofs.Roof roof, int deck, int ridge) {
	}

	/**
	 * The roof over a body's top storey in a form the culture uses, its pitch held so the ridge stays at least
	 * {@code headroom} under the plot's height. Flat roofs get a parapet; returns the roof (null when flat), the deck
	 * level (flat) and the ridge.
	 */
	public static Top roof(Draft d, Body b, RoofForm form, Direction.Axis axis, double pitch, int headroom) {
		int[] tr = b.topRect();
		int plate = b.plateY();
		if (form == RoofForm.FLAT) {
			int deck = Roofs.flat(d, tr[0], tr[1], tr[2], tr[3], plate, true);
			return new Top(null, deck, deck + 1);
		}
		Roofs.Opts o = Roofs.Opts.of(d);
		int span = axis == Direction.Axis.X ? tr[3] - tr[1] + 1 : tr[2] - tr[0] + 1;
		if (form == RoofForm.HIP || form == RoofForm.PAGODA || form == RoofForm.MANSARD) {
			span = Math.min(tr[3] - tr[1] + 1, tr[2] - tr[0] + 1);
		}
		double half = span / 2.0 + o.eaves();
		int room = d.maxHeight() - 1 - headroom - plate;
		double p = Math.min(pitch, Math.max(0.5, (room - 1.0) / Math.max(1.0, half)));
		// whole and half pitches rasterise cleanly
		p = p >= 1.0 ? Math.floor(p) : 0.5;
		o = o.withPitch(p);
		RoofForm f = form == RoofForm.CROSS_GABLE ? RoofForm.GABLE : form;
		Roofs.Built built = Roofs.build(d, tr[0], tr[1], tr[2], tr[3], plate, f, axis, o, b.partyLeft, b.partyRight);
		return new Top(built.roof(), Integer.MIN_VALUE, built.roof().ridgeY);
	}

	/**
	 * A straight flight from storey k to k + 1 along the inside of wall {@code along}, starting at (x, z) and climbing
	 * toward {@code dir}; opens the floor above over its last three steps. Returns {x, z} of the landing beyond the top
	 * step (on the floor above).
	 */
	public static int[] flight(Draft d, Body b, int k, int x, int z, Direction dir) {
		int rise = b.height(k);
		int y = b.floorY(k);
		Stairs.straight(d, x, y, z, dir, rise, 1);
		int fy = b.floorY(k + 1) - 1;
		try (var s = d.stage(StageKind.FLOORS)) {
			for (int i = Math.max(0, rise - 3); i < rise; i++) {
				d.set(x + Dirs.dx(dir) * i, fy, z + Dirs.dz(dir) * i, Blocks.AIR.defaultBlockState());
			}
			d.set(x + Dirs.dx(dir) * (rise - 1), fy, z + Dirs.dz(dir) * (rise - 1), d.mat(Role.FURNITURE).stairs(dir, Half.BOTTOM));
		}
		// a rail along the open side of the stairwell above
		Mat rail = d.mat(Role.RAILING);
		Direction side = Dirs.right(dir);
		for (int i = Math.max(0, rise - 3); i < rise - 1; i++) {
			int rx = x + Dirs.dx(dir) * i + Dirs.dx(side);
			int rz = z + Dirs.dz(dir) * i + Dirs.dz(side);
			int ry = b.floorY(k + 1);
			if (d.isEmpty(rx, ry, rz) && d.isSolid(rx, ry - 1, rz)) {
				d.inStage(StageKind.INTERIOR, () -> d.set(rx, ry, rz, rail.fence()));
			}
		}
		return new int[]{x + Dirs.dx(dir) * rise, z + Dirs.dz(dir) * rise};
	}

	/**
	 * Storey k's walls in dressed stone whatever the style's walls are (a bank's or a hall's base): the plinth stone in
	 * the body, toothed quoins and a string course of the wall-accent stone at its head, a darker sill course at the
	 * ground. Openings are cut afterwards as usual.
	 */
	public static void stoneStorey(Draft d, Body b, int k) {
		Mat body = d.mat(Role.PLINTH);
		Mat accent = d.mat(Role.WALL_ACCENT);
		Mat found = d.mat(Role.FOUNDATION);
		int ya = Walls.bottom(b, k);
		int yg = b.floorY(k + 1) - 1;
		try (var st = d.stage(StageKind.WALLS)) {
			for (Side s : b.sides(k)) {
				for (int i = 0; i < s.length(); i++) {
					boolean corner = s.isCorner(i);
					boolean next = i == 1 || i == s.length() - 2;
					for (int y = ya; y <= yg; y++) {
						boolean quoin = corner || next && ((y - ya) & 1) == 0;
						d.set(s.x(i), y, s.z(i), y == yg || quoin ? accent.full() : k == 0 && y == ya ? found.full() : body.full());
					}
				}
			}
		}
	}

	/** An odd number of bays (so one is in the middle) about {@code pitch} apart over cells {@code i0..i1}. */
	public static int[] oddPiers(int i0, int i1, int pitch) {
		int n = i1 - i0;
		int bays = Math.max(1, (int) Math.round(n / (double) Math.max(2, pitch)));
		if ((bays & 1) == 0) {
			int lo = bays - 1;
			int hi = bays + 1;
			bays = lo >= 1 && Math.abs(n / (double) lo - pitch) <= Math.abs(n / (double) hi - pitch) ? lo : hi;
		}
		int[] out = new int[bays + 1];
		for (int k = 0; k <= bays; k++) {
			out[k] = i0 + (int) Math.round(k * n / (double) bays);
		}
		return out;
	}

	/** The wall kinds of a building whose ground storey is masonry (a rusticated or stone base) under the style's own. */
	public static WallKind[] stoneBase(Draft d, int storeys) {
		WallKind[] k = new WallKind[storeys];
		WallKind base = d.habits().walls().kind();
		k[0] = base == WallKind.TIMBER_FRAME || base == WallKind.PLANK || base == WallKind.BAMBOO || base == WallKind.LOG ? WallKind.STONE : base;
		return k;
	}
}
