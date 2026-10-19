package dev.voidmc.hearth.build.kit.noble;

import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.kit.Roofs;
import dev.voidmc.hearth.build.kit.landmark.Shapes;
import dev.voidmc.hearth.build.kit.landmark.Stone;
import dev.voidmc.hearth.build.style.Habits;
import java.util.List;
import java.util.Set;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.Half;

/**
 * Turrets: the small round and corbelled towers that make a castle's skyline busy. A bartizan hangs on the corner of a
 * square tower or the keep on stepped corbels; a garita (the tropics' sentry box) stands on a bastion's point under a
 * little dome; a stair turret climbs the court face of a palace under its own cone.
 */
public final class Turret {
	private Turret() {
	}

	/**
	 * A bartizan on the corner (x, z) of a square mass, hung toward (sx, sz) (each -1 or 1): a 3 × 3 turret centred on the
	 * corner cell, its five cells off the mass carried out at {@code y - 1} on corbels (a single corbel under the outer
	 * corner a course lower), its walls two high with a slit to each side, open to the mass through the two cells beside
	 * the corner, under a little cap (a steep pyramid, a dome in the tropics and the desert). Returns the highest row used
	 * (or {@code y - 1} when the plot leaves no room).
	 */
	public static int corner(Draft d, Dress m, Manner man, int x, int z, int sx, int sz, int y, int maxTop) {
		if (!Mason.inPlot(d, x + sx, z + sz) && !(z + sz == -1 && y - 3 >= 3) || y + 4 > maxTop) {
			return y - 1;
		}
		Direction outX = sx < 0 ? Dirs.LEFT : Dirs.RIGHT;
		Direction outZ = sz < 0 ? Dirs.FRONT : Dirs.BACK;
		try (var s = d.stage(StageKind.WALLS)) {
			for (int dz = -1; dz <= 1; dz++) {
				for (int dx = -1; dx <= 1; dx++) {
					boolean mass = dx * sx <= 0 && dz * sz <= 0;
					int px = x + dx;
					int pz = z + dz;
					if (mass) {
						// the corner and the two cells beside it open into the turret; the inner cell stays as it is
						if (dx * sx < 0 && dz * sz < 0) {
							continue;
						}
						for (int yy = y; yy <= y + 1; yy++) {
							if (Mason.ok(d, px, yy, pz)) {
								d.set(px, yy, pz, Blocks.AIR.defaultBlockState());
							}
						}
						continue;
					}
					if (!Mason.ok(d, px, y - 1, pz)) {
						continue;
					}
					d.set(px, y - 1, pz, m.dress.full());
					Direction toWall = dx * sx > 0 && dz * sz > 0 ? outX.getOpposite() : dx * sx > 0 ? outX.getOpposite() : outZ.getOpposite();
					Mason.corbel(d, m.dress, px, y - 2, pz, toWall);
					for (int yy = y; yy <= y + 1; yy++) {
						if (!Mason.ok(d, px, yy, pz)) {
							continue;
						}
						boolean slit = yy == y && (dx == 0 || dz == 0);
						d.set(px, yy, pz, slit ? Blocks.AIR.defaultBlockState() : m.wall.full().at(px, yy, pz));
					}
				}
			}
			int ox = x + sx;
			int oz = z + sz;
			if (Mason.ok(d, ox, y - 3, oz) && d.isEmpty(ox, y - 3, oz)) {
				d.set(ox, y - 3, oz, m.dress.stairsOrSlab(outX.getOpposite(), Half.TOP));
			}
		}
		return cap(d, m, man, x, z, 3, y + 2, maxTop);
	}

	/**
	 * The cap of a turret {@code diam} wide centred on (cx, cz), from row y: a steep pyramid or cone in the roofing, a dome
	 * of the sheet in the tropics and the desert, a curved hip in the karst; a spike on top. Returns the highest row used.
	 */
	public static int cap(Draft d, Dress m, Manner man, int cx, int cz, int diam, int y, int maxTop) {
		int r = diam / 2;
		if (man.cap() == Manner.Cap.DOME || man.culture() == dev.voidmc.hearth.api.Culture.DESERT) {
			Stone sheet = Stone.sheet(d);
			List<Set<Long>> rows = Shapes.domeRows(cx, cz, Math.max(1, r));
			if (y + rows.size() - 1 > maxTop) {
				return y - 1;
			}
			try (var s = d.stage(StageKind.ROOF)) {
				return Shapes.stack(d, cx, cz, y, rows, sheet.full(), sheet);
			}
		}
		double pitch = man.culture() == dev.voidmc.hearth.api.Culture.KARST ? 1.0 : man.culture() == dev.voidmc.hearth.api.Culture.TEMPERATE ? 1.5
			: r <= 1 ? 2.0 : 2.2;
		int x0 = cx - r;
		int z0 = cz - r;
		int x1 = cx + r;
		int z1 = cz + r;
		pitch = Math.max(1.0, Math.min(pitch, (maxTop - y - 1) / Math.max(1.0, r + 0.5)));
		Roofs.Field f = diam >= 5 ? Roofs.cone(cx, cz, r, pitch) : Roofs.hip(x0, z0, x1, z1, Roofs.Profile.straight(pitch));
		int ex0 = Math.max(0, x0);
		int ez0 = Math.max(-1, z0);
		int ex1 = Math.min(d.frontage() - 1, x1);
		int ez1 = Math.min(d.depth() - 1, z1);
		Roofs.Roof roof;
		try (var s = d.stage(StageKind.ROOF)) {
			roof = Roofs.raster(d, f, y, ex0, ez0, ex1, ez1, m.roof, m.trim, Habits.Ridge.FULL);
		}
		Mason.solidify(d, roof, m.roof);
		int hi = roof.ridgeY;
		if (hi + 1 <= maxTop && Mason.ok(d, cx, hi + 1, cz) && d.isSolid(cx, hi, cz)) {
			try (var s = d.stage(StageKind.ROOF)) {
				d.set(cx, hi + 1, cz, dev.voidmc.hearth.build.kit.landmark.Details.rod());
			}
			hi++;
		}
		return hi;
	}

	/**
	 * A round stair turret of diameter {@code diam} (5 or 7) centred on (cx, cz), from standing level {@code base} to
	 * {@code top}: a shell of the walling with slit lights up its face, a spiral inside (a ladder in a five-wide one), a
	 * cap over it. Doors into it are the caller's ({@link Tower#door}). Returns the tower so the caller can cut them.
	 */
	public static Tower stair(Draft d, Dress m, Manner man, int cx, int cz, int diam, int base, int top, int maxTop, boolean timber, int... levels) {
		return stair(d, m, man, cx, cz, diam, base, top, timber, true, null, levels);
	}

	/**
	 * {@link #stair}, round or square. A round one of diameter 7 has a ring round its spiral broken by the wall at the
	 * corners, so each landing is reached only from the tread that arrives on it: where the doorways' side is fixed (a
	 * palace's storeys) give it in {@code arrive} and the spiral is turned to arrive on that side at the {@code levels}.
	 */
	public static Tower stair(Draft d, Dress m, Manner man, int cx, int cz, int diam, int base, int top, boolean timber, boolean round,
		@org.jspecify.annotations.Nullable Direction arrive, int... levels) {
		Manner.Cap cap = switch (man.cap()) {
			case CRENEL, DOME -> Manner.Cap.CRENEL;
			case PAGODA -> Manner.Cap.PAGODA;
			default -> round ? Manner.Cap.CONE : Manner.Cap.PYRAMID;
		};
		int r = diam / 2;
		Tower t = round ? Tower.round(cx, cz, diam, base, top, cap) : Tower.square(cx - r, cz - r, cx + r, cz + r, base, top, cap);
		t.footings(d, m);
		t.shell(d, m, top - 1);
		if (arrive != null) {
			t.arriveToward(arrive, levels);
		}
		t.stair(d, m, timber, null);
		t.floors(d, m, top, levels);
		return t;
	}

	/**
	 * A wall turret of diameter {@code diam} centred on (cx, cz) where curtains meet: solid masonry from its footings up
	 * to the walk's floor (a bastion), a little guard chamber at the walk's level opened onto the walks toward
	 * {@code doors}, loops out of it, and the manner's cap (an open crenellated top where the manner leaves towers open).
	 * Returns the highest row used.
	 */
	public static int wall(Draft d, Dress m, Manner man, int cx, int cz, int diam, int walk, int maxTop, java.util.Set<Direction> doors) {
		return wall(d, m, man, cx, cz, diam, walk, maxTop, doors, null);
	}

	/** {@link #wall} under a given cap (null: the manner's). */
	public static int wall(Draft d, Dress m, Manner man, int cx, int cz, int diam, int walk, int maxTop, java.util.Set<Direction> doors,
		Manner.@org.jspecify.annotations.Nullable Cap given) {
		Manner.Cap cap = given != null ? given
			: man.cap() == Manner.Cap.HOARD ? Manner.Cap.PYRAMID : man.cap() == Manner.Cap.ONION ? Manner.Cap.PYRAMID : man.cap();
		int rows = Tower.headRows(cap, diam, man);
		int top = Math.min(walk + 3, maxTop - Math.min(rows, 6));
		top = Math.max(walk + 1, top);
		Tower t = Tower.round(cx, cz, diam, walk, top, cap);
		try (var s = d.stage(StageKind.WALLS)) {
			for (int[] c : t.cells()) {
				if (!Mason.inPlot(d, c[0], c[1])) {
					continue;
				}
				int g = d.site().ground(c[0], c[1]);
				Mason.footing(d, m, c[0], c[1], Math.min(g, walk - 2));
				for (int y = Math.min(g, walk - 2); y < walk - 1; y++) {
					d.set(c[0], y, c[1], y <= g ? m.plinth.full() : m.wall.full());
				}
			}
		}
		for (int[] c : t.around()) {
			if (Mason.inPlot(d, c[0], c[1]) && d.site().ground(c[0], c[1]) < walk - 4) {
				Mason.talus(d, m, c[0], c[1], t.out(c[0], c[1]), walk - 4);
			}
		}
		t.noPlinth().shell(d, m, top - 1);
		t.floors(d, m, top);
		for (Direction dir : Tower.ordered(doors)) {
			t.doorToward(d, m, dir, walk, false);
		}
		java.util.Set<Direction> look = Tower.all();
		look.removeAll(doors);
		t.loops(d, look, walk, top);
		int hi = t.head(d, m, man, maxTop);
		return hi;
	}
}
