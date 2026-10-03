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
