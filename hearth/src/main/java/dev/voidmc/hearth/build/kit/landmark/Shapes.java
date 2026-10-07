package dev.voidmc.hearth.build.kit.landmark;

import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Paint;
import dev.voidmc.hearth.build.kit.Roofs;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * Geometry for towers and their crowns: spire fields for the roof rasteriser (octagonal needles, broaches, square
 * pyramids), solids of revolution laid ring by ring (onion domes, drums, lighthouse shafts, bells of roofs) with stairs
 * smoothing every step in and out, and a walkable spiral stair in any material.
 */
public final class Shapes {
	private Shapes() {
	}

	// ------------------------------------------------------------------------------------------------ fields

	/** An octagonal spire of apothem r round (cx, cz): its eight faces rise at {@code pitch}. */
	public static Roofs.Field octagon(double cx, double cz, double r, double pitch) {
		return (x, z) -> {
			double d8 = d8(x - cx, z - cz);
			if (d8 > r + 0.5) {
				return Double.NaN;
			}
			return 1 + pitch * Math.max(0, r - d8);
		};
	}

	/** Octagonal distance: the larger of the axial and the diagonal distance (a regular octagon's apothem). */
	public static double d8(double dx, double dz) {
		double ax = Math.abs(dx);
		double az = Math.abs(dz);
		return Math.max(Math.max(ax, az), (ax + az) * 0.7071);
	}

	/**
	 * A broach spire over a square: an octagonal needle whose four corners are carried by half-pyramids (broaches) rising
	 * from the square's corners onto its diagonal faces.
	 */
	public static Roofs.Field broach(int x0, int z0, int x1, int z1, double pitch) {
		double cx = (x0 + x1) / 2.0;
		double cz = (z0 + z1) / 2.0;
		double r = (x1 - x0) / 2.0;
		Roofs.Field spire = octagon(cx, cz, r, pitch);
		Roofs.Field broaches = Roofs.hip(x0, z0, x1, z1, Roofs.Profile.straight(Math.max(1, pitch * 0.55)));
		return (x, z) -> {
			double a = spire.h(x, z);
			double b = broaches.h(x, z);
			if (Double.isNaN(b)) {
				return a;
			}
			if (Double.isNaN(a)) {
				// beyond the octagon only the broach stands, and it is low
				return Math.min(b, 1 + pitch * 0.55 * 1.5);
			}
			return Math.max(a, Math.min(b, 1 + pitch * 0.55 * 1.5));
		};
	}

	/** A square pyramid over a rectangle at {@code pitch}. */
	public static Roofs.Field pyramid(int x0, int z0, int x1, int z1, double pitch) {
		return Roofs.hip(x0, z0, x1, z1, Roofs.Profile.straight(pitch));
	}

	/** A bell-cast pyramid: steep at the top, kicked out shallow at the eaves (a helm of shingles, a karst tower's cap). */
	public static Roofs.Field bellcast(int x0, int z0, int x1, int z1, double pitch) {
		return Roofs.hip(x0, z0, x1, z1, d -> 1 + (d <= 1 ? 0.5 * d : 0.5 + pitch * (d - 1)));
	}

	/** Caps any field at a height (a truncated roof: a skirt round the foot of a tier, a deck at the top). */
	public static Roofs.Field capped(Roofs.Field f, double maxH) {
		return (x, z) -> {
			double h = f.h(x, z);
			return Double.isNaN(h) ? h : Math.min(h, maxH);
		};
	}

	/** A field defined only outside a rectangle (a skirt roof round a tower's tier, whose walls stand within). */
	public static Roofs.Field outside(Roofs.Field f, int x0, int z0, int x1, int z1) {
		return (x, z) -> x >= x0 && x <= x1 && z >= z0 && z <= z1 ? Double.NaN : f.h(x, z);
	}

	/**
	 * Props up a rasterised roof wherever a column stands more than a block clear of every neighbour (the peak of a steep
	 * spire, a lifted ridge): full blocks of the roof material down to the highest neighbouring surface, so nothing on a
	 * needle floats and its arrises read solid.
	 */
	public static void support(Draft d, Roofs.Roof r, Paint fill) {
		for (int z = r.z0; z <= r.z1; z++) {
			for (int x = r.x0; x <= r.x1; x++) {
				if (!r.covers(x, z)) {
					continue;
				}
				int bottom = r.bottomY(x, z);
				// the highest neighbouring surface below this column's foot; level neighbours (a ridge) do not hold it up
				int best = Integer.MIN_VALUE;
				boolean touching = false;
				for (Direction dir : Dirs.HORIZONTAL) {
					int nx = x + Dirs.dx(dir);
					int nz = z + Dirs.dz(dir);
					if (!r.covers(nx, nz)) {
						continue;
					}
					int ns = r.surfaceY(nx, nz);
					int nb = r.bottomY(nx, nz);
					if (ns < bottom) {
						best = Math.max(best, ns);
					} else if (nb < bottom) {
						touching = true;
					}
				}
				if (touching || best == Integer.MIN_VALUE || bottom - 1 <= best) {
					continue;
				}
				for (int y = best + 1; y < bottom; y++) {
					if (d.isEmpty(x, y, z)) {
						d.set(x, y, z, fill);
					}
				}
			}
		}
	}

	/**
	 * Re-clads a rasterised roof in another sheet (copper over a spire the town roofs in shingles): every block of the
	 * roof's columns between its foot and its surface that is not the sheet already is replaced by the sheet's block of
	 * the same cut and orientation.
	 */
	public static void reclad(Draft d, Roofs.Roof r, Stone sheet) {
		for (int z = r.z0; z <= r.z1; z++) {
			for (int x = r.x0; x <= r.x1; x++) {
				if (!r.covers(x, z)) {
					continue;
				}
				for (int y = r.bottomY(x, z) - 1; y <= r.surfaceY(x, z) + 1; y++) {
					recladCell(d, x, y, z, sheet);
				}
			}
		}
	}

	/** Re-clads one cell if it holds a stair, slab or full block of a roof covering (leaves rods, logs, lanterns alone). */
	public static void recladCell(Draft d, int x, int y, int z, Stone sheet) {
		Draft.Cell c = d.cell(x, y, z);
		if (c == null || c.state().isAir() || c.stage() != StageKind.ROOF) {
			return;
		}
		var st = c.state();
		var b = st.getBlock();
		Paint p;
		if (b instanceof net.minecraft.world.level.block.StairBlock) {
			p = sheet.stairs(st.getValue(net.minecraft.world.level.block.StairBlock.FACING), st.getValue(net.minecraft.world.level.block.StairBlock.HALF));
		} else if (b instanceof net.minecraft.world.level.block.SlabBlock) {
			p = sheet.slab(st.getValue(net.minecraft.world.level.block.SlabBlock.TYPE));
		} else if (st.isCollisionShapeFullBlock(net.minecraft.world.level.EmptyBlockGetter.INSTANCE, net.minecraft.core.BlockPos.ZERO)
			&& !(b instanceof net.minecraft.world.level.block.RotatedPillarBlock) && b != Blocks.GOLD_BLOCK) {
			p = sheet.full();
		} else {
			return;
		}
		try (var sc = d.stage(StageKind.ROOF)) {
			d.set(x, y, z, p);
		}
	}

	// ------------------------------------------------------------------------------------------------ turned solids

	/**
	 * A solid of revolution round the vertical axis through (cx, cz) (half-block centres allowed): ring {@code i} at
	 * height {@code y0 + i} has radius {@code radii[i]}. Each ring is a shell one or two blocks thick, closed over where
	 * the next ring steps in (stairs facing inward) and under where it oversails the one below (upside-down stairs), so
	 * domes, onions and drums come out smooth and closed. Returns the cells of the last ring.
	 *
	 * @param body  the shell's full blocks
	 * @param steps stairs for the smoothing (null: full blocks everywhere)
	 */
	public static void turned(Draft d, double cx, double cz, int y0, double[] radii, Paint body, Stone steps) {
		for (int i = 0; i < radii.length; i++) {
			double r = radii[i];
			if (r < 0) {
				continue;
			}
			double below = i > 0 ? radii[i - 1] : r;
			double above = i + 1 < radii.length ? radii[i + 1] : -1;
			Set<Long> inAbove = cells(cx, cz, above);
			Set<Long> inBelow = cells(cx, cz, below);
			double inner = Math.min(r, Math.min(below, above < 0 ? 0 : above)) - 1.1;
			Set<Long> core = cells(cx, cz, inner);
			int y = y0 + i;
			for (int[] c : Draft.ellipseCells(cx, cz, r, r, true)) {
				long k = key(c[0], c[1]);
				if (core.contains(k)) {
					continue;
				}
				if (!d.allowed(c[0], y, c[1])) {
					continue;
				}
				Direction in = Dirs.nearest(cx - c[0], cz - c[1]);
				Paint p = body;
				if (steps != null && steps.has(dev.voidmc.hearth.build.style.Form.STAIRS)) {
					if (!inAbove.contains(k)) {
						p = steps.stairs(in, Half.BOTTOM);
					} else if (!inBelow.contains(k) && i > 0) {
						p = steps.stairs(in, Half.TOP);
					}
				}
				d.set(c[0], y, c[1], p);
			}
		}
	}

	// ------------------------------------------------------------------------------------------------ ladders

	/**
	 * A square of half-width {@code r} round (cx, cz) with its corners cut by {@code cut} (cells with
	 * {@code |dx| + |dz| <= 2r - cut}): cut 0 is the square, cut 1 an octagon with the corner cells off, cut 2 a rounder
	 * octagon, and half-width 1 with cut 1 the plus. Centres may be half-integers (even widths).
	 */
	public static Set<Long> oct(double cx, double cz, double r, int cut) {
		Set<Long> s = new HashSet<>();
		int x0 = (int) Math.floor(cx - r - 0.01);
		int x1 = (int) Math.ceil(cx + r + 0.01);
		int z0 = (int) Math.floor(cz - r - 0.01);
		int z1 = (int) Math.ceil(cz + r + 0.01);
		for (int z = z0; z <= z1; z++) {
			for (int x = x0; x <= x1; x++) {
				double dx = Math.abs(x - cx);
				double dz = Math.abs(z - cz);
				if (dx <= r + 0.01 && dz <= r + 0.01 && dx + dz <= 2 * r - cut + 0.01) {
					s.add(key(x, z));
				}
			}
		}
		return s;
	}

	/** A half-width snapped to the centre's parity (whole for a centre on a block, half for one between blocks), rounding down. */
	public static double snap(double c, double r) {
		boolean half = Math.abs(c - Math.floor(c) - 0.5) < 0.25;
		return half ? Math.floor(r - 0.5 + 0.01) + 0.5 : Math.floor(r + 0.01);
	}

	/**
	 * The ladder of nested outlines a tapering form climbs from a square of half-width {@code r} to a point: square,
	 * octagon, rounder octagon (where wide enough), the square one smaller, and so on down to the plus and the single
	 * cell (a 2 × 2 for even widths). Each outline lies inside the one before, so a form stacked from them never
	 * overhangs.
	 */
	public static List<Set<Long>> ladder(double cx, double cz, double r) {
		List<Set<Long>> out = new ArrayList<>();
		double min = Math.abs(r - Math.floor(r)) > 0.25 ? 0.5 : 0;
		for (double rr = r; rr >= min - 0.01; rr -= 1) {
			out.add(oct(cx, cz, rr, 0));
			if (rr >= 0.99) {
				out.add(oct(cx, cz, rr, 1));
			}
			if (rr >= 2.49) {
				out.add(oct(cx, cz, rr, 2));
			}
		}
		// drop repeats (a cut that removes nothing)
		List<Set<Long>> clean = new ArrayList<>();
		for (Set<Long> s : out) {
			if (!s.isEmpty() && (clean.isEmpty() || !clean.get(clean.size() - 1).equals(s))) {
				clean.add(s);
			}
		}
		return clean;
	}

	/**
	 * A form stacked from one outline per row (row i at {@code y0 + i}): every cell of a row is the body, except where the
	 * row above draws in (a stair rising toward the axis: the slope of a spire or the shoulder of a dome) or where the row
	 * swells out past the one below (an upside-down stair: the underside of an onion's bulb). The interior more than a
	 * block in from every face is left undrawn. Returns the top row's y.
	 */
	public static int stack(Draft d, double cx, double cz, int y0, List<Set<Long>> rows, Paint body, Stone steps) {
		boolean stairs = steps != null && steps.has(dev.voidmc.hearth.build.style.Form.STAIRS);
		for (int i = 0; i < rows.size(); i++) {
			Set<Long> row = rows.get(i);
			Set<Long> above = i + 1 < rows.size() ? rows.get(i + 1) : Set.of();
			Set<Long> below = i > 0 ? rows.get(i - 1) : row;
			int y = y0 + i;
			for (long k : row) {
				int x = (int) (k >> 32);
				int z = (int) k;
				if (!d.allowed(x, y, z)) {
					continue;
				}
				// deep inside: neighbours all round in this row, the row above and the row below
				boolean deep = above.contains(k) && below.contains(k);
				for (Direction dir : Dirs.HORIZONTAL) {
					long n = key(x + Dirs.dx(dir), z + Dirs.dz(dir));
					if (!row.contains(n) || !above.contains(n) || !below.contains(n)) {
						deep = false;
						break;
					}
				}
				if (deep && i > 0 && i + 1 < rows.size()) {
					continue;
				}
				Paint p = body;
				boolean axis = i + 1 == rows.size() && Math.abs(x - cx) < 0.25 && Math.abs(z - cz) < 0.25;
				if (row.size() == 1 || axis) {
					// the point (or the middle of a dome's cap): a whole block, whatever comes over it
					p = body;
				} else if (stairs && !above.contains(k)) {
					p = steps.stairs(inward(row, x, z, cx, cz), Half.BOTTOM);
				} else if (stairs && i > 0 && !below.contains(k)) {
					p = steps.stairs(inward(row, x, z, cx, cz), Half.TOP);
				}
				d.set(x, y, z, p);
			}
		}
		return y0 + rows.size() - 1;
	}

	/**
	 * The rows of an onion dome of half-width r (a drum of half-width r - 1 under it): a neck, the bulb swelling out over
	 * it on upside-down stairs, held, then drawn in outline by outline to an ogee point two cells tall. Laid in blocks
	 * this way (not as rasterised circles) a five- or seven-wide onion keeps its shape instead of reading as a stepped cone.
	 */
	public static List<Set<Long>> onionRows(double cx, double cz, double r) {
		List<Set<Long>> rows = new ArrayList<>();
		if (r < 1.49) {
			// a three-wide onion: a plus for the neck, the square bulb, a plus, the point
			rows.add(oct(cx, cz, r, 1));
			rows.add(oct(cx, cz, r, 0));
			rows.add(oct(cx, cz, r, 0));
			rows.add(oct(cx, cz, r, 1));
			rows.add(oct(cx, cz, r - 1, 0));
			rows.add(oct(cx, cz, r - 1, 0));
			return rows;
		}
		boolean whole = Math.abs(r - Math.floor(r)) < 0.25;
		if (whole) {
			// a waist a cell in from the bulb, the bulb held two rows (three on a broad onion), the shoulder drawn in and a
			// slender point two cells tall (the lantern over it is the caller's); every outline the round octagon nearest a
			// circle (a diamond's saw-toothed edge reads as a heap at a distance), so the bulb's lower row swells out on
			// upside-down stairs and its upper row draws in on stairs, never a held wall of blocks
			int k = (int) Math.round(r);
			rows.add(oct(cx, cz, k - 1, round(k - 1)));
			rows.add(oct(cx, cz, k, round(k)));
			rows.add(oct(cx, cz, k, round(k)));
			if (k >= 4) {
				rows.add(oct(cx, cz, k, round(k)));
			}
			for (int j = k - 1; j >= 2; j--) {
				rows.add(oct(cx, cz, j, round(j)));
			}
			rows.add(oct(cx, cz, 1, 1));
			Set<Long> point = oct(cx, cz, 0, 0);
			rows.add(point);
			rows.add(point);
			return rows;
		}
		double n = r - 1;
		rows.add(oct(cx, cz, n, n >= 0.99 ? 1 : 0));
		// the bulb: swelling out over the neck on upside-down stairs, held, then rounding over
		if (r >= 2.49) {
			rows.add(oct(cx, cz, r, 2));
		}
		rows.add(oct(cx, cz, r, 1));
		rows.add(oct(cx, cz, r, 1));
		if (r < 2.49) {
			rows.add(oct(cx, cz, r, 1));
		} else {
			rows.add(oct(cx, cz, r, 2));
		}
		// the shoulder drawn in quickly, then the ogee point: a slender spike two cells tall over a plus
		if (r - 1 >= 1.99) {
			rows.add(oct(cx, cz, r - 1, 1));
		}
		boolean half = Math.abs(r - Math.floor(r)) > 0.25;
		if (!half) {
			rows.add(oct(cx, cz, 1, 1));
		}
		Set<Long> point = oct(cx, cz, half ? 0.5 : 0, 0);
		rows.add(point);
		rows.add(point);
		return rows;
	}

	/**
	 * The rows of a helm (a bell-cast cap with a waist, the Bohemian and Bavarian little tower's crown) over a square of
	 * half-width r: the square kicked out as an eave of stairs, a belly a cell in held a row (two on a broad helm), a
	 * shoulder drawn in, the point. The lantern and the finial over it are the caller's. Three, five or seven wide.
	 */
	public static List<Set<Long>> helmRows(double cx, double cz, double r) {
		List<Set<Long>> rows = new ArrayList<>();
		boolean half = Math.abs(r - Math.floor(r)) > 0.25;
		double tip = half ? 0.5 : 0;
		if (r < 1.49) {
			rows.add(oct(cx, cz, r, 0));
			rows.add(oct(cx, cz, tip, 0));
			return rows;
		}
		rows.add(oct(cx, cz, r, 0));
		double belly = r - 1;
		rows.add(oct(cx, cz, belly, belly >= 1.49 ? 1 : 0));
		if (belly >= 1.49) {
			rows.add(oct(cx, cz, belly, 1));
			rows.add(oct(cx, cz, belly - 1, belly - 1 >= 0.99 ? 1 : 0));
		} else {
			rows.add(oct(cx, cz, belly, 0));
		}
		Set<Long> point = oct(cx, cz, tip, 0);
		if (!rows.get(rows.size() - 1).equals(point)) {
			rows.add(point);
		}
		return rows;
	}

	/**
	 * The rows of a dome of half-width r, laid to a hemisphere's profile rather than a cone's: each row the round outline
	 * (an octagon cut to the circle) nearest the sphere's section at that height, so the springing holds near-vertical
	 * for a row or two and the crown flattens; it ends in a three-wide cap whose middle is a whole block for the finial.
	 * A dome of half-width r is about r + 1 rows tall: half its width, not a heap of cream or a stepped pyramid.
	 */
	public static List<Set<Long>> domeRows(double cx, double cz, double r) {
		List<Set<Long>> rows = new ArrayList<>();
		if (r < 1.49) {
			// a three-wide cap: the square with its corners turned, the plus, the point
			rows.add(oct(cx, cz, r, 0));
			rows.add(oct(cx, cz, r, 1));
			rows.add(oct(cx, cz, r - 1, 0));
			return rows;
		}
		boolean half = Math.abs(r - Math.floor(r)) > 0.25;
		if (!half) {
			int big = (int) Math.round(r);
			double sphere = big + 0.5;
			int last = big + 1;
			for (int i = 0; ; i++) {
				double h = i + 0.5;
				double rr = sphere * sphere - h * h;
				if (rr <= 0) {
					break;
				}
				int k = Math.min(big, (int) Math.round(Math.sqrt(rr) - 0.5));
				if (k < 2) {
					break;
				}
				rows.add(oct(cx, cz, k, round(k)));
				last = k;
			}
			// down to the cap a cell at a time, so the crown never steps in by two
			for (int k = last - 1; k >= 2; k--) {
				rows.add(oct(cx, cz, k, round(k)));
			}
			rows.add(oct(cx, cz, 1, 0));
			return rows;
		}
		// an even width: the springing outline held two rows, then an octagon a cell smaller each row
		Set<Long> spring = oct(cx, cz, r, r >= 2.49 ? 2 : 1);
		rows.add(spring);
		rows.add(spring);
		for (double rr = r - 1; rr >= 0.99; rr -= 1) {
			rows.add(oct(cx, cz, rr, 1));
		}
		rows.add(oct(cx, cz, 0.5, 0));
		return rows;
	}

	/** The corner cut that brings an octagon of half-width k nearest a circle (about 0.6 k, at least 1). */
	static int round(int k) {
		return Math.max(1, (int) Math.round(0.6 * k));
	}

	/** The way from a rim cell toward the axis: toward a neighbour in the outline, the nearest to the centre. */
	private static Direction inward(Set<Long> row, int x, int z, double cx, double cz) {
		Direction best = Dirs.nearest(cx - x, cz - z);
		if (row.contains(key(x + Dirs.dx(best), z + Dirs.dz(best)))) {
			return best;
		}
		for (Direction dir : Dirs.HORIZONTAL) {
			if (row.contains(key(x + Dirs.dx(dir), z + Dirs.dz(dir)))) {
				return dir;
			}
		}
		return best;
	}

	/**
	 * A tapering needle spire over the outline {@code ladder(cx, cz, r)} from row y0, {@code rows} high in all: each
	 * outline of the ladder held for a share of the height (the broad ones briefly, the slender top longest, as a straight
	 * spire's faces look when laid in blocks), then a shaft of wall posts and a rod. Returns the highest row drawn.
	 */
	public static int needle(Draft d, double cx, double cz, double r, int y0, int rows, Paint body, Stone steps, Paint post, int skip) {
		List<Set<Long>> ladder = new ArrayList<>(ladder(cx, cz, r));
		for (int k = 0; k < skip && ladder.size() > 1; k++) {
			ladder.remove(0);
		}
		int n = ladder.size();
		int tip = Math.max(1, Math.min(4, rows / 5));
		int forBody = Math.max(n, rows - tip - 1);
		// the rows each outline holds: the faces follow a slightly concave profile (width ~ (1 - h/H)^1.7, the way a
		// needle spire draws in quickly off its base and runs long and slender to the point), measured by each outline's
		// seen width (between its width square on and its width across the diagonal); never less than a row each
		double w0 = seenWidth(ladder.get(0), cx, cz);
		// a small spire (five wide) tapers straighter, or its slender top reads as a pencil on a skirt
		double bow = r >= 2.9 ? 1.7 : 1.3;
		List<Set<Long>> stackRows = new ArrayList<>();
		int used = 0;
		for (int i = 0; i < n; i++) {
			double wNext = i + 1 < n ? seenWidth(ladder.get(i + 1), cx, cz) : 0;
			double h = 1 - Math.pow(Math.max(0, wNext / w0), 1 / bow);
			int until = (int) Math.round(h * forBody);
			int want = Math.max(1, until - used);
			if (i == n - 1) {
				want = Math.max(1, forBody - used);
			}
			for (int k = 0; k < want; k++) {
				stackRows.add(ladder.get(i));
			}
			used += want;
		}
		int top = stack(d, cx, cz, y0, stackRows, body, steps);
		// the shaft: wall posts (or the body) up the axis of a one-cell top, then the rod
		Set<Long> last = ladder.get(n - 1);
		if (last.size() == 1) {
			long k = last.iterator().next();
			int x = (int) (k >> 32);
			int z = (int) k;
			for (int t = 0; t < tip && d.allowed(x, top + 1, z); t++) {
				top++;
				d.set(x, top, z, post);
			}
			if (d.allowed(x, top + 1, z)) {
				top++;
				d.set(x, top, z, Details.rod());
			}
		}
		return top;
	}

	/** How wide an outline looks: the mean of its width square on and its width across the diagonal. */
	static double seenWidth(Set<Long> o, double cx, double cz) {
		double ax = 0;
		double dg = 0;
		for (long k : o) {
			double dx = Math.abs((int) (k >> 32) - cx);
			double dz = Math.abs((int) k - cz);
			ax = Math.max(ax, Math.max(dx, dz));
			dg = Math.max(dg, dx + dz);
		}
		return ((2 * ax + 1) + (2 * dg + 2) / Math.sqrt(2)) / 2;
	}

	private static Set<Long> cells(double cx, double cz, double r) {
		Set<Long> s = new HashSet<>();
		if (r < 0) {
			return s;
		}
		for (int[] c : Draft.ellipseCells(cx, cz, r, r, true)) {
			s.add(key(c[0], c[1]));
		}
		return s;
	}

	private static long key(int x, int z) {
		return ((long) x << 32) ^ (z & 0xFFFFFFFFL);
	}

	/** The ring of cells of a disc of radius r (4-connected outline). */
	public static List<int[]> ring(double cx, double cz, double r) {
		return Draft.ellipseCells(cx, cz, r, r, false);
	}

	/** The cells of a disc of radius r. */
	public static List<int[]> disc(double cx, double cz, double r) {
		return Draft.ellipseCells(cx, cz, r, r, true);
	}

	// ------------------------------------------------------------------------------------------------ stairs

	/** The eight cells round a spiral's post, in climbing order (front-left first, clockwise seen from above). */
	public static final int[][] SPIRAL = {{-1, -1}, {0, -1}, {1, -1}, {1, 0}, {1, 1}, {0, 1}, {-1, 1}, {-1, 0}};

	/**
	 * A spiral stair of slabs round a post at (cx, cz), from standing level {@code y0} up {@code rise} blocks; every ring
	 * cell is half a block above the last, so a full turn climbs four. Headroom is cleared over each step (draw floors
	 * first: the stairwell is cut through them). Step {@code k} (1-based) stands on ring cell {@code (start + k) % 8}.
	 * Returns the ring index of the last step.
	 */
	public static int spiral(Draft d, int cx, int cz, int y0, int rise, int start, Stone treads, Paint post) {
		BlockState air = Blocks.AIR.defaultBlockState();
		int last = start;
		try (var s = d.stage(StageKind.FLOORS)) {
			for (int y = y0 - 1; y < y0 + rise - 1; y++) {
				d.set(cx, y, cz, post);
			}
			int steps = rise * 2;
			for (int k = 1; k <= steps; k++) {
				int idx = Math.floorMod(start + k, 8);
				int[] c = SPIRAL[idx];
				int px = cx + c[0];
				int pz = cz + c[1];
				int y = y0 + (k - 1) / 2;
				boolean top = (k & 1) == 0;
				d.set(px, y, pz, treads.slab(top ? SlabType.TOP : SlabType.BOTTOM));
				// three rows of headroom: a person stepping up off this tread needs them under a floor
				for (int h = 1; h <= 3; h++) {
					if (k + 2 * h <= steps || h < 3) {
						d.set(px, y + h, pz, air);
					}
				}
				last = idx;
			}
		}
		return last;
	}

	/** Every cell of a rectangle's outline at distance 1 outside a shape given as a cell set (for courses round anything). */
	public static List<int[]> around(Set<Long> shape) {
		List<int[]> out = new ArrayList<>();
		Set<Long> seen = new HashSet<>();
		for (long k : shape) {
			int x = (int) (k >> 32);
			int z = (int) k;
			for (Direction dir : Dirs.HORIZONTAL) {
				int nx = x + Dirs.dx(dir);
				int nz = z + Dirs.dz(dir);
				long n = key(nx, nz);
				if (!shape.contains(n) && seen.add(n)) {
					out.add(new int[]{nx, nz});
				}
			}
		}
		return out;
	}

	/** A cell set key, for {@link #around}. */
	public static long cell(int x, int z) {
		return key(x, z);
	}

	/** Whether a cell set contains a cell. */
	public static boolean has(Set<Long> shape, int x, int z) {
		return shape.contains(key(x, z));
	}
}
