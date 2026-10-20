package dev.voidmc.hearth.sim.roads;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.PlotSpec;
import dev.voidmc.hearth.api.RoadNetwork.RoadClass;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;

/**
 * Cuts a {@link Route} into the plots that build it (docs/LIVING_WORLD_2.md §1.2): stretches of at most
 * {@link #MAX_COLUMNS} columns as ordinary {@code hearth:infra/road} plots ({@code kind} highway for a king's road, a
 * rutted three-wide track otherwise, {@code country} set so they are dressed as country roads: verges, milestones, no
 * lamps), and every bridge whole as one {@code hearth:infra/bridge} plot from bank to bank. Consecutive stretches share
 * their end column, so the road is unbroken. Pure.
 *
 * <p>Geometry follows the infrastructure params contract (PLANNING.md, "Infrastructure params"; the plan module's
 * {@code Polyline}): vertices reproduce the dense centreline exactly, and a column is in the corridor when its centre is
 * nearer the polyline than {@code (width + 0.5) / 2}.
 */
public final class RoadSegments {
	public static final Identifier ROAD = Hearth.id("infra/road");
	public static final Identifier BRIDGE = Hearth.id("infra/bridge");
	/** The longest stretch one road plot takes. */
	public static final int MAX_COLUMNS = 48;
	/** Columns between vertices at most (the plan module's rule). */
	static final int MAX_RUN = 8;
	/** Columns of planned ground kept round a road's corridor (trees and Expanse's features stay off it). */
	public static final int GUARD = 3;

	private RoadSegments() {
	}

	/**
	 * One plot of a road.
	 *
	 * @param from first dense column of the route it covers
	 * @param to   last dense column (inclusive)
	 */
	public record Segment(int index, PlotSpec spec, int from, int to, boolean bridge, int minX, int minZ, int maxX, int maxZ) {
		public boolean touchesChunk(int cx, int cz) {
			return this.maxX >= cx << 4 && this.minX <= (cx << 4) + 15 && this.maxZ >= cz << 4 && this.minZ <= (cz << 4) + 15;
		}
	}

	/**
	 * What the plots are dressed with: the style of the culture at each end (the nearer end's for each plot), signs at
	 * its ends.
	 *
	 * @param signA what the signpost at the {@code from} end says (the far town and its distance), or "" for none
	 */
	public record Dressing(Identifier styleA, Identifier styleB, String signA, String signB, boolean shrines) {
		Identifier style(int from, int to, int columns) {
			return from + to < columns ? this.styleA : this.styleB;
		}
	}

	public static int width(RoadClass kind) {
		return kind == RoadClass.KINGS_ROAD ? 5 : 3;
	}

	/** The plots of a road, in order from its {@code from} end. */
	public static List<Segment> cut(long edge, RoadClass kind, Route r, Dressing dress) {
		int n = r.size();
		List<Segment> out = new ArrayList<>();
		int width = width(kind);
		int i = 0;
		while (i < n - 1) {
			if (r.types()[i + 1] == Route.BRIDGE) {
				// a bridge from the bank before it to the bank after it
				int j = i + 1;
				while (j < n && r.types()[j] == Route.BRIDGE) {
					j++;
				}
				int end = Math.min(n - 1, j);
				out.add(bridge(edge, out.size(), kind, r, i, end, dress));
				i = end;
				continue;
			}
			int end = Math.min(n - 1, i + MAX_COLUMNS);
			// a road stretch stops at the bank before a bridge
			for (int k = i + 1; k <= end; k++) {
				if (r.types()[k] == Route.BRIDGE) {
					end = k - 1;
					break;
				}
			}
			if (end <= i) {
				end = i + 1;
			}
			// never leave a stub of a few columns for the next plot
			if (n - 1 - end > 0 && n - 1 - end < 8 && noBridge(r, end, n - 1)) {
				end = n - 1;
			}
			out.add(road(edge, out.size(), kind, r, i, end, width, dress));
			i = end;
		}
		return out;
	}

	private static boolean noBridge(Route r, int a, int b) {
		for (int k = a; k <= b; k++) {
			if (r.types()[k] == Route.BRIDGE) {
				return false;
			}
		}
		return true;
	}

	private static Segment road(long edge, int index, RoadClass kind, Route r, int from, int to, int width, Dressing dress) {
		int[] pts = vertices(r.xs(), r.ys(), r.zs(), from, to);
		CompoundTag p = new CompoundTag();
		p.putIntArray("points", pts);
		p.putInt("width", width);
		p.putString("kind", kind == RoadClass.KINGS_ROAD ? "highway" : "path");
		p.putBoolean("country", true);
		if (from == 0 && !dress.signA().isEmpty()) {
			p.putString("sign_start", dress.signA());
		}
		if (to == r.size() - 1 && !dress.signB().isEmpty()) {
			p.putString("sign_end", dress.signB());
		}
		if (dress.shrines() && index % 11 == 5) {
			// a wayside cross or shrine every 500 blocks or so
			p.putBoolean("shrine", true);
		}
		int lo = Integer.MAX_VALUE;
		int hi = Integer.MIN_VALUE;
		for (int k = from; k <= to; k++) {
			lo = Math.min(lo, r.ys()[k]);
			hi = Math.max(hi, r.ys()[k]);
		}
		return linear(edge, index, ROAD, pts, width, p, lo, hi - lo + 6, kind == RoadClass.KINGS_ROAD ? 1 : 0, dress, from, to, false, r.size());
	}

	private static Segment bridge(long edge, int index, RoadClass kind, Route r, int from, int to, Dressing dress) {
		int deck = r.ys()[from + 1];
		int water = Integer.MIN_VALUE;
		// the bank columns at their own levels, the rest at the deck
		int[] ys = Arrays.copyOfRange(r.ys(), from, to + 1);
		for (int k = 1; k < ys.length - 1; k++) {
			ys[k] = deck;
		}
		int[] xs = Arrays.copyOfRange(r.xs(), from, to + 1);
		int[] zs = Arrays.copyOfRange(r.zs(), from, to + 1);
		int low = Integer.MAX_VALUE;
		for (int k = from + 1; k < to; k++) {
			if (r.types()[k] == Route.BRIDGE) {
				water = Math.max(water, r.decks()[k]);
			}
		}
		int[] pts = vertices(xs, ys, zs, 0, xs.length - 1);
		for (int k = 1; k < pts.length; k += 3) {
			low = Math.min(low, pts[k]);
		}
		// (the water's top face is under the deck by the clearance at least; the bridge finds the bed itself)
		int waterY = Math.min(water, deck - 1);
		CompoundTag p = new CompoundTag();
		p.putIntArray("points", pts);
		int width = width(kind);
		int span = Math.max(width + 2, 5);
		p.putInt("width", span);
		p.putInt("deck_y", deck);
		p.putInt("water_y", waterY);
		p.putString("kind", "span");
		p.putString("road_kind", kind == RoadClass.KINGS_ROAD ? "highway" : "lane");
		int base = Math.min(low, waterY - 1);
		return linear(edge, index, BRIDGE, pts, span, p, base, Math.min(32, Math.max(8, deck - base + 8)), kind == RoadClass.KINGS_ROAD ? 1 : 0, dress,
			from, to, true, r.size());
	}

	private static Segment linear(long edge, int index, Identifier archetype, int[] points, int width, CompoundTag params, int originY, int height,
		int tier, Dressing dress, int from, int to, boolean bridge, int columns) {
		int[] box = {Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
		corridor(points, width, (x, z) -> {
			box[0] = Math.min(box[0], x);
			box[1] = Math.min(box[1], z);
			box[2] = Math.max(box[2], x);
			box[3] = Math.max(box[3], z);
		});
		int n = points.length / 3;
		int dx = points[(n - 1) * 3] - points[0];
		int dz = points[(n - 1) * 3 + 2] - points[2];
		Direction facing = Math.abs(dx) >= Math.abs(dz) ? (dx >= 0 ? Direction.EAST : Direction.WEST) : (dz >= 0 ? Direction.SOUTH : Direction.NORTH);
		long seed = edge * 0x9E3779B97F4A7C15L + index * 0xC2B2AE3D27D4EB4FL;
		PlotSpec spec = new PlotSpec(archetype, dress.style(from, to, columns), seed, new BlockPos(box[0], originY, box[1]), box[2] - box[0] + 1, box[3] - box[1] + 1,
			facing, Math.max(1, height), tier, params);
		return new Segment(index, spec, from, to, bridge, box[0], box[1], box[2], box[3]);
	}

	// ---------------------------------------------------------------- the infrastructure params rules

	/** {@code floor(d·i/n + 1/2)} in integers (the dense rule). */
	static int lerp(int d, int i, int n) {
		return Math.floorDiv(2 * d * i + n, 2 * n);
	}

	/** The fewest vertices that reproduce {@code [from, to]} of a dense path exactly, at most {@link #MAX_RUN} apart. */
	public static int[] vertices(int[] xs, int[] ys, int[] zs, int from, int to) {
		int[] out = new int[(to - from + 1) * 3];
		int n = 0;
		int a = from;
		out[n++] = xs[a];
		out[n++] = ys[a];
		out[n++] = zs[a];
		while (a < to) {
			int best = a + 1;
			for (int b = a + 2; b <= to && b - a <= MAX_RUN; b++) {
				if (!reproduces(xs, ys, zs, a, b)) {
					break;
				}
				best = b;
			}
			out[n++] = xs[best];
			out[n++] = ys[best];
			out[n++] = zs[best];
			a = best;
		}
		return Arrays.copyOf(out, n);
	}

	private static boolean reproduces(int[] xs, int[] ys, int[] zs, int a, int b) {
		int dx = xs[b] - xs[a];
		int dy = ys[b] - ys[a];
		int dz = zs[b] - zs[a];
		int steps = Math.max(Math.abs(dx), Math.abs(dz));
		if (steps != b - a) {
			return false;
		}
		for (int i = 1; i < steps; i++) {
			if (xs[a + i] != xs[a] + lerp(dx, i, steps) || zs[a + i] != zs[a] + lerp(dz, i, steps) || ys[a + i] != ys[a] + lerp(dy, i, steps)) {
				return false;
			}
		}
		return true;
	}

	@FunctionalInterface
	public interface Columns {
		void visit(int x, int z);
	}

	/** Every column of a polyline's corridor of {@code width} (columns may come twice). */
	public static void corridor(int[] points, int width, Columns visitor) {
		double limit = (width + 0.5) / 2.0;
		double limit2 = limit * limit;
		int reach = (int) Math.ceil(limit);
		int n = points.length / 3;
		for (int v = 0; v + 1 < n; v++) {
			int ax = points[v * 3];
			int az = points[v * 3 + 2];
			int bx = points[v * 3 + 3];
			int bz = points[v * 3 + 5];
			double ex = bx - ax;
			double ez = bz - az;
			double len2 = ex * ex + ez * ez;
			for (int z = Math.min(az, bz) - reach; z <= Math.max(az, bz) + reach; z++) {
				for (int x = Math.min(ax, bx) - reach; x <= Math.max(ax, bx) + reach; x++) {
					double px = x - ax;
					double pz = z - az;
					double t = len2 == 0 ? 0 : Math.max(0, Math.min(1, (px * ex + pz * ez) / len2));
					double qx = px - t * ex;
					double qz = pz - t * ez;
					if (qx * qx + qz * qz < limit2) {
						visitor.visit(x, z);
					}
				}
			}
		}
	}
}
