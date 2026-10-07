package dev.voidmc.hearth.plan.growth;

import dev.voidmc.hearth.api.ArchetypeCategory;
import dev.voidmc.hearth.api.ArchetypeInfo;
import dev.voidmc.hearth.api.ArchetypeInfo.SiteFlag;
import dev.voidmc.hearth.plan.PlanContext;
import dev.voidmc.hearth.plan.grid.Cell;
import dev.voidmc.hearth.plan.grid.Occupancy;
import dev.voidmc.hearth.plan.model.District;
import dev.voidmc.hearth.plan.model.RoadEdge;
import dev.voidmc.hearth.plan.model.Site;
import dev.voidmc.hearth.plan.road.Polyline;
import dev.voidmc.hearth.plan.terrain.Ground;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntPredicate;
import net.minecraft.core.Direction;
import org.jspecify.annotations.Nullable;

/**
 * Finds land for a building along the streets: the frontage search (PLANNING.md, "Plots along roads").
 *
 * <p>Every few blocks along each built road (and the main square's edge), on each side, the first column past the
 * road's corridor is a place a plot could front the street. Places are scored cheaply for what the building wants
 * (near the heart, on the main street, on the rise, by the water, at the edge, in its district) and examined best
 * first, within a budget: a rectangle of the archetype's size is set facing the road with its entrance on it, set back
 * when the road runs at a slant, checked against everything already planned, the side gaps of its zone and the water,
 * and given a floor level that meets the street — or a terrace where the ground under it is too uneven to build on as
 * it is. The best of the first few that fit wins; it is also slid along the street to sit at exactly its zone's gap
 * from its neighbour, so streets fill in tidy rows.
 */
public final class Parcels {
	private static final int STRIDE = 2;
	private static final int ENOUGH = 8;

	private Parcels() {
	}

	/** What a building wants from its site. */
	public enum Placement {
		/** Housing: as near the heart as land allows, the density gradient doing the rest. */
		HOME,
		/** On the main square, or as near it as can be. */
		CENTRAL,
		/** On a main street (an arm), not far out. */
		MAIN,
		/** Civic: central, a little raised. */
		CIVIC,
		/** On the rise: high ground overlooking the town. */
		RISE,
		/** On the highest ground within reach. */
		SUMMIT,
		/** With water along its back or side. */
		WATER,
		/** Over the water, on stilts. */
		ON_WATER,
		/** Just outside the built-up town: farms, yards, mills. */
		EDGE,
		/** Far out along the tracks: farmsteads. */
		FARMSTEAD,
		/** High, quiet ground: manors and grand houses. */
		RICH,
		/** Workshops: by the water or at the town's edge. */
		CRAFTS,
		/** As near a given point as can be (a farmstead's fields round its house). */
		ANCHOR,
		/** The poor's homes: at the edge of town, downwind, on the low ground by the tannery and the fields. */
		HOVELS,
		/** Peasants' crofts and cottages: at the edge of town, by the fields they work. */
		CROFTS,
		/** As the request's {@link Siting} says: near its inputs, by the water, downwind, at the edge (the chains' workshops). */
		SITED,
		/** Where travellers come in: on a main road at the edge of town (or just outside the gate) — hostels, caravan yards. */
		GATEWAY,
		/** Hidden: on a lane or alley of the poor quarter, off the main streets (the thieves' den). */
		HIDDEN,
		/** Quiet ground outside the town, raised and apart: monasteries and convents outside the walls. */
		QUIET
	}

	/**
	 * @param gap      side gap to keep, or -1 for the layout's gap in the zone
	 * @param frontage frontage to use, or -1 to choose from the archetype's range by zone
	 * @param depth    depth to use, or -1 to choose
	 */
	public record Request(ArchetypeInfo info, Placement placement, District.@Nullable Kind district, int gap, int frontage, int depth,
		boolean allowTerrace, int anchorX, int anchorZ, @Nullable Siting siting, double sizeBias, dev.voidmc.hearth.api.@Nullable Station station) {
		public Request(ArchetypeInfo info, Placement placement, District.@Nullable Kind district) {
			this(info, placement, district, -1, -1, -1, true, 0, 0, null, 0, null);
		}

		public Request(ArchetypeInfo info, Placement placement, District.@Nullable Kind district, int gap, int frontage, int depth,
			boolean allowTerrace, int anchorX, int anchorZ) {
			this(info, placement, district, gap, frontage, depth, allowTerrace, anchorX, anchorZ, null, 0, null);
		}

		/** A home for this station: neighbours of the same station draw it, the very different keep it off (a quarter forms). */
		public Request forStation(dev.voidmc.hearth.api.@Nullable Station s) {
			return new Request(this.info, this.placement, this.district, this.gap, this.frontage, this.depth, this.allowTerrace, this.anchorX,
				this.anchorZ, this.siting, this.sizeBias, s);
		}

		public Request withGap(int g) {
			return new Request(this.info, this.placement, this.district, g, this.frontage, this.depth, this.allowTerrace, this.anchorX, this.anchorZ,
				this.siting, this.sizeBias, this.station);
		}

		public Request withSize(int f, int d) {
			return new Request(this.info, this.placement, this.district, this.gap, f, d, this.allowTerrace, this.anchorX, this.anchorZ, this.siting,
				this.sizeBias, this.station);
		}

		public Request anchoredAt(int x, int z) {
			return new Request(this.info, Placement.ANCHOR, this.district, this.gap, this.frontage, this.depth, this.allowTerrace, x, z, this.siting,
				this.sizeBias, this.station);
		}

		/** Sited as {@code s} says (placement {@link Placement#SITED}). */
		public Request sited(Siting s) {
			return new Request(this.info, Placement.SITED, this.district, this.gap, this.frontage, this.depth, this.allowTerrace, s.x(), s.z(), s,
				this.sizeBias, this.station);
		}

		/** Leans the building's size within its archetype's range: negative smaller (a poor family), positive larger. */
		public Request withSizeBias(double b) {
			return new Request(this.info, this.placement, this.district, this.gap, this.frontage, this.depth, this.allowTerrace, this.anchorX,
				this.anchorZ, this.siting, b, this.station);
		}

		public Request withInfo(ArchetypeInfo i) {
			return new Request(i, this.placement, this.district, this.gap, this.frontage, this.depth, this.allowTerrace, this.anchorX, this.anchorZ,
				this.siting, this.sizeBias, this.station);
		}

		public Request withPlacement(Placement p) {
			return new Request(this.info, p, this.district, this.gap, this.frontage, this.depth, this.allowTerrace, this.anchorX, this.anchorZ,
				this.siting, this.sizeBias, this.station);
		}
	}

	/** A place found for a building. Bounds are inclusive. */
	public static final class Parcel {
		public int x0;
		public int z0;
		public int sizeX;
		public int sizeZ;
		public Direction facing;
		public int floorY;
		public int streetY;
		public int edge;
		public int index;
		public int doorGap;
		public boolean terrace;
		public int lo;
		public int hi;
		public boolean cornerLeft;
		public boolean cornerRight;
		public int zone;
		public int gap;
		public int backGap;
		/** Cut into the slope behind (cliff dwellings): the ground rises past the roof at the back. */
		public boolean cut;
		public double score;

		public int x1() {
			return this.x0 + this.sizeX - 1;
		}

		public int z1() {
			return this.z0 + this.sizeZ - 1;
		}

		public int frontage() {
			return this.facing.getAxis() == Direction.Axis.Z ? this.sizeX : this.sizeZ;
		}

		public int depth() {
			return this.facing.getAxis() == Direction.Axis.Z ? this.sizeZ : this.sizeX;
		}

		/** The column just outside the middle of the front edge. */
		public int[] entrance() {
			return switch (this.facing) {
				case NORTH -> new int[]{this.x0 + this.sizeX / 2, this.z0 - 1};
				case SOUTH -> new int[]{this.x0 + this.sizeX / 2, z1() + 1};
				case WEST -> new int[]{this.x0 - 1, this.z0 + this.sizeZ / 2};
				default -> new int[]{x1() + 1, this.z0 + this.sizeZ / 2};
			};
		}
	}

	private record Pos(int edge, int index, Direction facing, int qx, int qz, double pre) {
	}

	/** The best place for the request, or null if no frontage fits. {@code edges} limits the roads searched. */
	public static @Nullable Parcel best(PlanContext ctx, Request req, @Nullable IntPredicate edges) {
		List<Parcel> found = find(ctx, req, edges, ENOUGH);
		Parcel best = null;
		for (Parcel p : found) {
			if (best == null || p.score > best.score) {
				best = p;
			}
		}
		return best;
	}

	public static List<Parcel> find(PlanContext ctx, Request req, @Nullable IntPredicate edges, int enough) {
		List<Pos> positions = new ArrayList<>();
		Occupancy occ = ctx.occ;
		for (RoadEdge e : ctx.state.graph.edges()) {
			if (ctx.pastDeadline()) {
				// a live call out of time: what was gathered so far is all this call looks at
				break;
			}
			boolean square = e.role == RoadEdge.Role.SQUARE;
			int lim = square ? e.length() : Math.min(e.built, e.length());
			if (lim < 2 || (edges != null && !edges.test(e.id))) {
				continue;
			}
			if (!frontsOk(e, req)) {
				continue;
			}
			Polyline.Dense d = e.dense();
			int[] xs = d.xs();
			int[] zs = d.zs();
			for (int i = 0; i < lim; i += STRIDE) {
				int a = Math.max(0, i - 2);
				int b = Math.min(lim - 1, i + 2);
				int tx = xs[b] - xs[a];
				int tz = zs[b] - zs[a];
				if (tx == 0 && tz == 0) {
					continue;
				}
				for (int side = -1; side <= 1; side += 2) {
					if (square && side > 0) {
						continue;
					}
					// the normal toward the plot side; the plot faces back along it
					int nx = side < 0 ? tz : -tz;
					int nz = side < 0 ? -tx : tx;
					Direction primary = facingToward(-nx, -nz);
					addPos(ctx, req, e, i, primary, positions, occ);
					int ax = Math.abs(nx);
					int az = Math.abs(nz);
					if (ax > 0 && az > 0 && Math.min(ax, az) * 5 >= Math.max(ax, az) * 2) {
						Direction secondary = ax >= az ? facingToward(0, -nz) : facingToward(-nx, 0);
						if (secondary != primary) {
							addPos(ctx, req, e, i, secondary, positions, occ);
						}
					}
				}
			}
		}
		// best first; a city has thousands of places and the search usually stops within the first few dozen, so they are
		// drawn from a heap rather than all sorted
		Ranking ranking = new Ranking(positions);
		List<Parcel> found = new ArrayList<>();
		int budget = ctx.config.searchBudget();
		int i = 0;
		for (; i < positions.size() && budget > 0 && !ctx.pastDeadline(); i++, budget--) {
			Parcel p = place(ctx, req, positions.get(ranking.next()));
			if (p != null) {
				found.add(p);
				if (found.size() >= enough) {
					return found;
				}
			}
		}
		if (found.isEmpty() && i < positions.size()) {
			// a city's best-ranked places (near the heart, on the main street) can all be leftover gaps too small for
			// anything: the budget spent on them would leave the frontage of its newer streets unseen and the town stalled
			// (requests/w5-tour.md §1), so a spread sample of the rest gets half a budget more
			List<Pos> rest = new ArrayList<>(positions.size() - i);
			while (rest.size() < positions.size() - i) {
				rest.add(positions.get(ranking.next()));
			}
			int more = Math.max(1, ctx.config.searchBudget() / 2);
			int step = Math.max(1, rest.size() / more);
			for (int k = 0; k < rest.size() && !ctx.pastDeadline(); k += step) {
				Parcel p = place(ctx, req, rest.get(k));
				if (p != null) {
					found.add(p);
					if (found.size() >= enough) {
						break;
					}
				}
			}
		}
		return found;
	}

	/**
	 * Places in the order a stable sort by falling pre-score would give (ties in the order they were gathered), drawn one
	 * at a time from a binary heap: building it is linear, and each draw costs a logarithm.
	 */
	private static final class Ranking {
		private final double[] pre;
		private final int[] heap;
		private int size;

		Ranking(List<Pos> positions) {
			this.size = positions.size();
			this.pre = new double[this.size];
			this.heap = new int[this.size];
			for (int k = 0; k < this.size; k++) {
				this.pre[k] = positions.get(k).pre;
				this.heap[k] = k;
			}
			for (int k = this.size / 2 - 1; k >= 0; k--) {
				down(k);
			}
		}

		/** The index of the next place; only as many times as there are places. */
		int next() {
			int top = this.heap[0];
			this.heap[0] = this.heap[--this.size];
			down(0);
			return top;
		}

		/** Whether place a comes before place b: the higher pre-score (compared as the sort compared it), then the earlier. */
		private boolean before(int a, int b) {
			int c = Double.compare(-this.pre[a], -this.pre[b]);
			return c < 0 || c == 0 && a < b;
		}

		private void down(int k) {
			int v = this.heap[k];
			while (true) {
				int c = 2 * k + 1;
				if (c >= this.size) {
					break;
				}
				if (c + 1 < this.size && before(this.heap[c + 1], this.heap[c])) {
					c++;
				}
				if (!before(this.heap[c], v)) {
					break;
				}
				this.heap[k] = this.heap[c];
				k = c;
			}
			this.heap[k] = v;
		}
	}

	/**
	 * Places that close the built end of a road within {@code r} blocks of (x, z), in every facing, rather than line its
	 * sides: the adit at the foot of a hill, its mouth on the track's end and its back into the slope.
	 */
	public static List<Parcel> atEnds(PlanContext ctx, Request req, int x, int z, int r) {
		List<Parcel> found = new ArrayList<>();
		for (RoadEdge e : ctx.state.graph.edges()) {
			int lim = Math.min(e.built, e.length());
			if (lim < 2 || !frontsOk(e, req)) {
				continue;
			}
			Polyline.Dense d = e.dense();
			for (int i : new int[]{0, lim - 1}) {
				if (Math.hypot(d.xs()[i] - x, d.zs()[i] - z) > r) {
					continue;
				}
				for (Direction facing : Direction.Plane.HORIZONTAL) {
					List<Pos> one = new ArrayList<>(1);
					addPos(ctx, req, e, i, facing, one, ctx.occ);
					for (Pos pos : one) {
						Parcel p = place(ctx, req, pos);
						if (p != null) {
							found.add(p);
						}
					}
				}
			}
		}
		return found;
	}

	private static boolean frontsOk(RoadEdge e, Request req) {
		boolean water = req.info.flags().contains(SiteFlag.ON_WATER);
		if (water) {
			return e.role == RoadEdge.Role.BOARDWALK;
		}
		if (e.role == RoadEdge.Role.BOARDWALK) {
			return req.info.flags().contains(SiteFlag.WATERFRONT);
		}
		if (e.role == RoadEdge.Role.PATH) {
			return false;
		}
		if (e.role == RoadEdge.Role.TRACK || e.role == RoadEdge.Role.HAUL) {
			ArchetypeCategory c = req.info.category();
			return switch (req.placement) {
				case FARMSTEAD, EDGE, SITED, QUIET, GATEWAY, HOVELS, CROFTS, ANCHOR -> true;
				default -> c == ArchetypeCategory.FARM || c == ArchetypeCategory.EXTRACTION || c == ArchetypeCategory.UNDERGROUND;
			};
		}
		return true;
	}

	private static Direction facingToward(int dx, int dz) {
		if (Math.abs(dx) >= Math.abs(dz)) {
			return dx >= 0 ? Direction.EAST : Direction.WEST;
		}
		return dz >= 0 ? Direction.SOUTH : Direction.NORTH;
	}

	private static void addPos(PlanContext ctx, Request req, RoadEdge e, int i, Direction facing, List<Pos> out, Occupancy occ) {
		Polyline.Dense d = e.dense();
		int sx = -facing.getStepX();
		int sz = -facing.getStepZ();
		int qx = d.xs()[i];
		int qz = d.zs()[i];
		int k = 0;
		int reach = e.width / 2 + 4;
		while (k < reach && Cell.isAccess(occ.get(qx, qz))) {
			qx += sx;
			qz += sz;
			k++;
		}
		// the road the plot would face must actually be there, and the first column past it free
		if (k == 0 || !Cell.isVacant(occ.get(qx, qz))) {
			return;
		}
		double pre = preScore(ctx, req, e, qx, qz);
		out.add(new Pos(e.id, i, facing, qx, qz, pre));
	}

	/** The cheap score a place is ranked by before it is examined. */
	private static double preScore(PlanContext ctx, Request req, RoadEdge e, int x, int z) {
		double dist = ctx.distanceToHeart(x, z);
		double core = ctx.coreRadius();
		Site site = ctx.state.site;
		Ground g = ctx.ground;
		double s;
		switch (req.placement) {
			case HOME -> s = -dist;
			case CENTRAL -> s = -dist * 2 + (e.role == RoadEdge.Role.SQUARE ? 40 : 0) + (e.role == RoadEdge.Role.ARM ? 6 : 0);
			case MAIN -> s = -dist + (e.role == RoadEdge.Role.ARM ? 30 : 0) + (e.role == RoadEdge.Role.SQUARE ? 20 : 0);
			case CIVIC -> s = -dist * 1.2 + (e.role == RoadEdge.Role.SQUARE ? 35 : 0) + (g.ground(x, z) - ctx.state.heartY) * 0.8;
			case RISE -> {
				double toRise = site == null ? dist : Math.hypot(x - site.riseX(), z - site.riseZ());
				s = (g.ground(x, z) - ctx.state.heartY) * 2.0 - toRise * 0.6 - dist * 0.1;
			}
			case SUMMIT -> {
				double toTop = site == null ? dist : Math.hypot(x - site.summitX(), z - site.summitZ());
				s = g.ground(x, z) * 2.0 - toTop * 0.4 - dist * 0.05;
			}
			case WATER, ON_WATER -> s = -dist * 0.4 - ctx.waterDistance(x, z) * 3;
			case EDGE -> {
				// fields go a little way out, leaving the town room to grow before it reaches them
				double ring = req.info.category() == ArchetypeCategory.FARM && !ctx.state.layout.expansive
					? Math.max(edgeRing(ctx), ctx.coreRadiusAt(Math.max(40, (int) (ctx.population * 1.5))) * 1.25) : edgeRing(ctx);
				s = -Math.abs(dist - ring) - (e.role == RoadEdge.Role.SQUARE ? 100 : 0);
			}
			case FARMSTEAD -> s = -Math.abs(dist - core * 2.2 * ctx.config.sprawlBias()) * 0.5 + (e.role == RoadEdge.Role.TRACK ? 30 : 0);
			case RICH -> s = (g.ground(x, z) - ctx.state.heartY) * 1.5 - Math.abs(dist - core * 0.9) * 0.6;
			case CRAFTS -> s = -Math.abs(dist - core * 0.9) - ctx.waterDistance(x, z) * 0.5;
			case ANCHOR -> s = -Math.hypot(x - req.anchorX, z - req.anchorZ) * 2;
			case HOVELS -> {
				var t = ctx.territory();
				// a walled town's poor live in the lanes along the inside of its wall, then in the suburbs outside the gates
				double ring = ctx.wallLine() > 0 ? ctx.wallLine() * 0.85 : edgeRing(ctx) * 0.9;
				s = -Math.abs(dist - ring) * 0.8 + t.downwind(x, z) * core * 0.45 - (g.ground(x, z) - ctx.state.heartY) * 0.6;
				int[] tannery = t.nearest(dev.voidmc.hearth.plan.catalogue.Archetypes.TANNERY, x, z);
				if (tannery != null) {
					s -= Math.hypot(x - tannery[0], z - tannery[1]) * 0.35;
				}
			}
			case CROFTS -> {
				double ring = ctx.wallLine() > 0 ? ctx.wallLine() * 0.8 : edgeRing(ctx) * 0.85;
				s = -Math.abs(dist - ring) * 0.8 - Math.max(0, ctx.territory().downwind(x, z)) * core * 0.15;
			}
			case SITED -> {
				Siting st = req.siting;
				if (st == null) {
					s = -dist;
				} else {
					double toAnchor = Math.hypot(x - st.x(), z - st.z());
					s = -toAnchor * st.anchor() - ctx.waterDistance(x, z) * st.water() + ctx.territory().downwind(x, z) * st.downwind() * core * 0.5
						- Math.abs(dist - edgeRing(ctx)) * st.edge() + (g.ground(x, z) - ctx.state.heartY) * st.height()
						+ (e.role == RoadEdge.Role.ARM || e.role == RoadEdge.Role.SQUARE ? st.main() * 10 : 0);
					if (toAnchor > st.maxDistance()) {
						s -= (toAnchor - st.maxDistance()) * 4;
					}
				}
			}
			case GATEWAY -> s = -Math.abs(dist - edgeRing(ctx)) + (e.role == RoadEdge.Role.ARM ? 40 : e.role == RoadEdge.Role.TRACK ? 8 : 0);
			case HIDDEN -> {
				s = -Math.abs(dist - core * 0.85) * 0.5 + switch (e.role) {
					case LANE, ALLEY, STAIR -> 15;
					case ARM, SQUARE, RING -> -45;
					default -> 0;
				};
				District d = ctx.district(x, z);
				if (d != null && d.kind() == District.Kind.POOR) {
					s += 30;
				}
			}
			case QUIET -> s = -Math.abs(dist - (edgeRing(ctx) + 60)) * 0.6 + (g.ground(x, z) - ctx.state.heartY) * 1.2
				+ (e.role == RoadEdge.Role.TRACK || e.role == RoadEdge.Role.APPROACH ? 6 : 0);
			default -> s = -dist;
		}
		// the square's frontage is kept for the buildings that make a square: hall, chapel, inn, bank, shops
		if (e.role == RoadEdge.Role.SQUARE && req.placement != Placement.CENTRAL && req.placement != Placement.CIVIC
			&& req.placement != Placement.MAIN && !req.info.jobs().contains(dev.voidmc.hearth.api.JobType.MERCHANT)) {
			s -= 60;
		}
		if (req.district != null && !ctx.state.districts.isEmpty()) {
			District d = ctx.district(x, z);
			if (d != null) {
				boolean home = req.info.category() == ArchetypeCategory.HOUSING && req.station != null;
				s += d.kind() == req.district ? (home ? 40 : 18) : d.kind() == District.Kind.FARMS && req.info.category() != ArchetypeCategory.FARM ? -25 : 0;
				// the hill and the castle are kept for the gentry and the lord; the poor quarter is not where others build homes
				if (req.info.category() == ArchetypeCategory.HOUSING && d.kind() != req.district) {
					s -= d.kind() == District.Kind.RICH || d.kind() == District.Kind.CASTLE ? (home ? 40 : 22)
						: d.kind() == District.Kind.POOR ? (home ? 25 : 10)
						: d.kind() == District.Kind.MARKET && req.station == dev.voidmc.hearth.api.Station.POOR ? 20 : 0;
				}
			}
		}
		if (req.station != null && ctx.ledger != null) {
			s += ctx.ledger.neighbourhood(req.station, x, z);
		}
		// a walled town keeps its town inside the wall line and its farms and yards outside
		double wall = ctx.wallLine();
		if (wall > 0) {
			boolean inside = dist < wall - 2;
			boolean outer = req.placement == Placement.EDGE || req.placement == Placement.FARMSTEAD || req.placement == Placement.QUIET
				|| req.placement == Placement.GATEWAY || req.info.category() == ArchetypeCategory.FARM || req.info.category() == ArchetypeCategory.EXTRACTION
				|| req.placement == Placement.SITED && req.info.flags().contains(SiteFlag.OUTSKIRTS);
			s += outer ? (inside ? -80 : 0) : (inside ? 45 : 0);
		}
		if (req.info.flags().contains(SiteFlag.VERTICAL)) {
			// towers where they read from afar
			s += (g.ground(x, z) - ctx.state.heartY) * 0.4;
		}
		for (int[] a : ctx.avoid) {
			if (Math.abs(x - a[0]) < a[2] && Math.abs(z - a[1]) < a[2] && Math.hypot(x - a[0], z - a[1]) < a[2]) {
				s -= 500;
			}
		}
		return s + ctx.hash(x, z, 7) * core * 0.08;
	}

	/** Where the edge of town is: just past the houses and workshops, or past the wall line of a walled town. */
	static double edgeRing(PlanContext ctx) {
		double ring = Math.max(ctx.coreRadius() * 1.3, ctx.extent() + 14);
		double wall = ctx.wallLine();
		return wall > 0 ? Math.max(wall + 10, ring * 0.8) : ring;
	}

	/** Distance to water by a few probes (0 if there is water right here, 24 if none within reach). */
	public static int waterDistance(Ground g, int x, int z) {
		if (g.isWater(x, z)) {
			return 0;
		}
		for (int r = 3; r <= 24; r += 3) {
			if (g.isWater(x + r, z) || g.isWater(x - r, z) || g.isWater(x, z + r) || g.isWater(x, z - r)
				|| g.isWater(x + r * 7 / 10, z + r * 7 / 10) || g.isWater(x - r * 7 / 10, z - r * 7 / 10)
				|| g.isWater(x + r * 7 / 10, z - r * 7 / 10) || g.isWater(x - r * 7 / 10, z + r * 7 / 10)) {
				return r;
			}
		}
		return 24;
	}

	private static @Nullable Parcel place(PlanContext ctx, Request req, Pos pos) {
		ArchetypeInfo info = req.info;
		int zone = ctx.zone(pos.qx, pos.qz);
		int[] size = size(ctx, req, zone, pos.qx, pos.qz);
		Parcel p = placeSized(ctx, req, pos, zone, size[0], size[1]);
		if (p == null && (size[0] > info.minFrontage() || size[1] > info.minDepth()) && req.frontage < 0) {
			p = placeSized(ctx, req, pos, zone, info.minFrontage(), info.minDepth());
		}
		return p;
	}

	/** Frontage and depth for a place: narrow and deep in the core, broad in the outskirts, varied by a stable hash. */
	static int[] size(PlanContext ctx, Request req, int zone, int x, int z) {
		ArchetypeInfo info = req.info;
		double tf;
		double td;
		if (info.category() == ArchetypeCategory.HOUSING) {
			tf = switch (zone) {
				case 0 -> 0.1;
				case 1 -> 0.35;
				case 2 -> 0.65;
				default -> 0.85;
			};
			td = zone == 0 ? 0.7 : 0.45;
			if (ctx.state.layout.dense) {
				tf *= 0.6;
			}
			if (ctx.state.layout.expansive) {
				tf = Math.min(1, tf + 0.2);
			}
		} else if (info.category() == ArchetypeCategory.FARM) {
			tf = 0.55 + 0.35 * ctx.hash(x, z, 3);
			td = 0.55 + 0.35 * ctx.hash(x, z, 4);
		} else {
			tf = 0.45;
			td = 0.5;
		}
		tf = clamp01(tf + (ctx.hash(x, z, 1) - 0.5) * 0.4 + req.sizeBias);
		td = clamp01(td + (ctx.hash(x, z, 2) - 0.5) * 0.4 + req.sizeBias * 0.6);
		int f = req.frontage > 0 ? req.frontage : info.minFrontage() + (int) Math.round((info.maxFrontage() - info.minFrontage()) * tf);
		int d = req.depth > 0 ? req.depth : info.minDepth() + (int) Math.round((info.maxDepth() - info.minDepth()) * td);
		return new int[]{Math.max(1, f), Math.max(1, d)};
	}

	private static double clamp01(double v) {
		return Math.max(0, Math.min(1, v));
	}

	private static @Nullable Parcel placeSized(PlanContext ctx, Request req, Pos pos, int zone, int f, int d) {
		ArchetypeInfo info = req.info;
		Occupancy occ = ctx.occ;
		Direction facing = pos.facing;
		// out among the fields a farmstead may stand well back from its track, at the end of a drive
		boolean rural = ctx.state.graph.edge(pos.edge).role == RoadEdge.Role.TRACK || ctx.state.graph.edge(pos.edge).role == RoadEdge.Role.HAUL;
		int doorMax = rural ? Math.max(ctx.config.doorPathMax(), 7) : ctx.config.doorPathMax();
		Parcel p = null;
		for (int push = 0; push <= doorMax; push++) {
			int qx = pos.qx - facing.getStepX() * push;
			int qz = pos.qz - facing.getStepZ() * push;
			int[] r = rect(qx, qz, facing, f, d);
			if (!frontRowFree(occ, r, facing)) {
				continue;
			}
			p = new Parcel();
			p.x0 = r[0];
			p.z0 = r[1];
			p.sizeX = r[2];
			p.sizeZ = r[3];
			p.facing = facing;
			p.doorGap = push;
			break;
		}
		if (p == null) {
			return null;
		}
		// the door path from the road to a set-back entrance must be open ground
		for (int k = 1; k <= p.doorGap; k++) {
			if (!Cell.isVacant(occ.get(pos.qx - facing.getStepX() * (k - 1), pos.qz - facing.getStepZ() * (k - 1)))) {
				return null;
			}
		}
		p.edge = pos.edge;
		p.index = pos.index;
		if (!evaluate(ctx, req, p, zone)) {
			return null;
		}
		snap(ctx, req, p);
		p.score = pos.pre + finalScore(ctx, req, p);
		return p;
	}

	/**
	 * Checks a building at a given rectangle fronting a given road, with its entrance on it (rows of attached houses
	 * are laid out this way, one beside the last): free, clear of water, side gaps, a floor that meets the street.
	 */
	public static @Nullable Parcel fitAt(PlanContext ctx, Request req, int edge, int x0, int z0, int sizeX, int sizeZ, Direction facing) {
		Parcel p = new Parcel();
		p.x0 = x0;
		p.z0 = z0;
		p.sizeX = sizeX;
		p.sizeZ = sizeZ;
		p.facing = facing;
		p.edge = edge;
		int[] ent = p.entrance();
		p.index = ctx.state.graph.edge(edge).dense().nearest(ent[0], ent[1]);
		if (!evaluate(ctx, req, p, ctx.zone(ent[0], ent[1]))) {
			return null;
		}
		p.score = finalScore(ctx, req, p);
		return p;
	}

	private static boolean evaluate(PlanContext ctx, Request req, Parcel p, int zone) {
		ArchetypeInfo info = req.info;
		Occupancy occ = ctx.occ;
		boolean onWater = info.flags().contains(SiteFlag.ON_WATER);
		boolean waterfront = info.flags().contains(SiteFlag.WATERFRONT);
		int[] ent = p.entrance();
		if (p.doorGap == 0 && !Cell.isAccess(occ.get(ent[0], ent[1]))) {
			return false;
		}
		if (!occ.isFree(p.x0, p.z0, p.x1(), p.z1())) {
			return false;
		}
		p.zone = zone;
		boolean attachable = info.flags().contains(SiteFlag.ATTACHABLE) && (zone == 0 || ctx.state.layout.dense && zone <= 1);
		p.gap = req.gap >= 0 ? req.gap : attachable ? 0 : Math.max(info.category() == ArchetypeCategory.FARM ? 1 : 0, ctx.state.layout.gap(zone));
		p.backGap = info.category() == ArchetypeCategory.HOUSING && zone >= 1 && req.gap != 0 ? 1 : 0;
		if (!sidesClear(occ, p, p.gap, p.backGap)) {
			return false;
		}
		// water
		Ground g = ctx.ground;
		int wet = 0;
		int area = p.sizeX * p.sizeZ;
		int maxDepth = 0;
		for (int z = p.z0; z <= p.z1(); z++) {
			for (int x = p.x0; x <= p.x1(); x++) {
				if (g.isWater(x, z)) {
					wet++;
					maxDepth = Math.max(maxDepth, g.depth(x, z));
				}
			}
		}
		boolean excavation = excavation(info);
		if (onWater) {
			if (wet * 2 < area || maxDepth > 8) {
				return false;
			}
		} else if (excavation) {
			// a clay or sand pit may take in the bank's edge; nothing else is dug under water
			boolean bank = info.id().equals(dev.voidmc.hearth.plan.catalogue.Archetypes.CLAY_PIT)
				|| info.id().equals(dev.voidmc.hearth.plan.catalogue.Archetypes.SAND_PIT);
			if (bank ? wet * 100 > area * 35 || frontWet(g, p) : wet > 0) {
				return false;
			}
		} else if (waterfront) {
			if (wet * 100 > area * 45 || frontWet(g, p) || (wet == 0 && !waterBehind(g, p))) {
				return false;
			}
		} else if (wet > 0) {
			return false;
		}
		// level: the floor meets the street at the entrance
		int streetY = streetLevel(ctx, p.edge, p.index, ent);
		p.streetY = streetY;
		int[] range = dryRange(g, p);
		p.lo = range[0];
		p.hi = range[1];
		boolean hillside = info.flags().contains(SiteFlag.HILLSIDE);
		int tol = hillside ? ctx.config.hillsideTolerance() : ctx.config.slopeTolerance();
		if (info.category() == ArchetypeCategory.FARM && !hillside) {
			tol += 1;
		}
		p.floorY = streetY;
		if (!onWater && wet < area && !excavation) {
			int relief = p.hi - p.lo;
			int cut = p.hi - streetY;
			int fill = streetY - p.lo;
			boolean fits = relief <= tol && cut <= tol + 1 && fill <= tol + 2;
			if (!fits) {
				// a building on a bank above the street: floor raised a little, steps up to the door
				int raised = Math.min(streetY + 3, Math.max(streetY, (p.lo + p.hi + 1) / 2));
				if (relief <= tol && p.hi - raised <= tol + 1 && raised - p.lo <= tol + 2) {
					p.floorY = raised;
					fits = true;
				}
			}
			if (!fits && hillside && cutsIn(ctx)) {
				// a cliff dwelling: its front stands at the street, its back is cut into the rising rock
				int[] front = frontRange(g, p);
				if (front[1] - front[0] <= tol && front[1] - streetY <= tol && streetY - front[0] <= tol + 2 && p.lo >= streetY - tol) {
					p.cut = true;
					fits = true;
				}
			}
			if (!fits) {
				boolean terraces = req.allowTerrace && (ctx.state.layout.terraces || hillside || relief <= tol + 3);
				int needed = Math.max(cut, fill);
				if (!terraces || needed > ctx.config.terraceMaxHeight() || info.category() == ArchetypeCategory.FARM && !hillside
					&& needed > ctx.config.terraceMaxHeight() - 2) {
					return false;
				}
				p.terrace = true;
			}
		}
		corners(occ, p);
		return true;
	}

	/**
	 * Plots whose blueprint works the ground rather than standing on it — quarry pits, mines, shafts, clay and sand pits,
	 * woodlots, a lift down to a realm: their floor is the street's level at the entrance, and they need no terrace.
	 */
	public static boolean excavation(ArchetypeInfo info) {
		return info.category() == ArchetypeCategory.EXTRACTION || info.id().equals(dev.voidmc.hearth.plan.catalogue.Archetypes.DEEP_LIFT);
	}

	/** Whether this settlement cuts its dwellings into slopes: cliff towns, mountain holds, pueblos and karst towns. */
	static boolean cutsIn(PlanContext ctx) {
		return switch (ctx.state.layout) {
			case CLIFF, MOUNTAIN -> true;
			default -> ctx.state.signature.stacks;
		};
	}

	/** Lowest and highest ground in the front half of a parcel (the street side). */
	private static int[] frontRange(Ground g, Parcel p) {
		Parcel f = new Parcel();
		f.facing = p.facing;
		f.x0 = p.x0;
		f.z0 = p.z0;
		f.sizeX = p.sizeX;
		f.sizeZ = p.sizeZ;
		int half = Math.max(1, p.depth() / 2);
		switch (p.facing) {
			case NORTH -> f.sizeZ = half;
			case SOUTH -> {
				f.z0 = p.z1() - half + 1;
				f.sizeZ = half;
			}
			case WEST -> f.sizeX = half;
			default -> {
				f.x0 = p.x1() - half + 1;
				f.sizeX = half;
			}
		}
		return dryRange(g, f);
	}

	/** Whether a parcel found earlier is still free and still has its road (a road planned since may cross it). */
	public static boolean stillClear(PlanContext ctx, Parcel p) {
		if (!ctx.occ.isFree(p.x0, p.z0, p.x1(), p.z1())) {
			return false;
		}
		int[] e = p.entrance();
		return p.doorGap > 0 || Cell.isAccess(ctx.occ.get(e[0], e[1]));
	}

	/** The rectangle {x0, z0, sizeX, sizeZ} of a plot whose front row is centred on (qx, qz), facing {@code facing}. */
	static int[] rect(int qx, int qz, Direction facing, int f, int d) {
		return switch (facing) {
			case NORTH -> new int[]{qx - f / 2, qz, f, d};
			case SOUTH -> new int[]{qx - f / 2, qz - d + 1, f, d};
			case WEST -> new int[]{qx, qz - f / 2, d, f};
			default -> new int[]{qx - d + 1, qz - f / 2, d, f};
		};
	}

	private static boolean frontRowFree(Occupancy occ, int[] r, Direction facing) {
		int x0 = r[0];
		int z0 = r[1];
		int x1 = x0 + r[2] - 1;
		int z1 = z0 + r[3] - 1;
		return switch (facing) {
			case NORTH -> occ.isFree(x0, z0, x1, z0);
			case SOUTH -> occ.isFree(x0, z1, x1, z1);
			case WEST -> occ.isFree(x0, z0, x0, z1);
			default -> occ.isFree(x1, z0, x1, z1);
		};
	}

	/** Side and back strips hold nothing solid (roads there make a corner lot; open ground and gaps are fine). */
	private static boolean sidesClear(Occupancy occ, Parcel p, int gap, int back) {
		int x0 = p.x0;
		int z0 = p.z0;
		int x1 = p.x1();
		int z1 = p.z1();
		boolean alongX = p.facing.getAxis() == Direction.Axis.Z;
		if (gap > 0) {
			if (alongX ? solid(occ, x0 - gap, z0, x0 - 1, z1) || solid(occ, x1 + 1, z0, x1 + gap, z1)
				: solid(occ, x0, z0 - gap, x1, z0 - 1) || solid(occ, x0, z1 + 1, x1, z1 + gap)) {
				return false;
			}
		}
		if (back > 0) {
			return switch (p.facing) {
				case NORTH -> !solid(occ, x0, z1 + 1, x1, z1 + back);
				case SOUTH -> !solid(occ, x0, z0 - back, x1, z0 - 1);
				case WEST -> !solid(occ, x1 + 1, z0, x1 + back, z1);
				default -> !solid(occ, x0 - back, z0, x0 - 1, z1);
			};
		}
		return true;
	}

	static boolean solid(Occupancy occ, int x0, int z0, int x1, int z1) {
		for (int z = z0; z <= z1; z++) {
			for (int x = x0; x <= x1; x++) {
				if (Cell.isSolid(occ.get(x, z))) {
					return true;
				}
			}
		}
		return false;
	}

	private static boolean frontWet(Ground g, Parcel p) {
		int x0 = p.x0;
		int z0 = p.z0;
		int x1 = p.x1();
		int z1 = p.z1();
		return switch (p.facing) {
			case NORTH -> anyWet(g, x0, z0, x1, z0 + 1);
			case SOUTH -> anyWet(g, x0, z1 - 1, x1, z1);
			case WEST -> anyWet(g, x0, z0, x0 + 1, z1);
			default -> anyWet(g, x1 - 1, z0, x1, z1);
		};
	}

	private static boolean waterBehind(Ground g, Parcel p) {
		int x0 = p.x0;
		int z0 = p.z0;
		int x1 = p.x1();
		int z1 = p.z1();
		return switch (p.facing) {
			case NORTH -> anyWet(g, x0, z1 + 1, x1, z1 + 3);
			case SOUTH -> anyWet(g, x0, z0 - 3, x1, z0 - 1);
			case WEST -> anyWet(g, x1 + 1, z0, x1 + 3, z1);
			default -> anyWet(g, x0 - 3, z0, x0 - 1, z1);
		} || anyWet(g, x0 - 2, z0, x0 - 1, z1) || anyWet(g, x1 + 1, z0, x1 + 2, z1);
	}

	private static boolean anyWet(Ground g, int x0, int z0, int x1, int z1) {
		for (int z = z0; z <= z1; z++) {
			for (int x = x0; x <= x1; x++) {
				if (g.isWater(x, z)) {
					return true;
				}
			}
		}
		return false;
	}

	private static int[] dryRange(Ground g, Parcel p) {
		int lo = Integer.MAX_VALUE;
		int hi = Integer.MIN_VALUE;
		for (int z = p.z0; ; z = Math.min(z + 2, p.z1())) {
			for (int x = p.x0; ; x = Math.min(x + 2, p.x1())) {
				if (!g.isWater(x, z)) {
					int h = g.ground(x, z);
					lo = Math.min(lo, h);
					hi = Math.max(hi, h);
				}
				if (x == p.x1()) {
					break;
				}
			}
			if (z == p.z1()) {
				break;
			}
		}
		if (lo == Integer.MAX_VALUE) {
			lo = hi = g.surface(p.x0, p.z0);
		}
		return new int[]{lo, hi};
	}

	/** The street's walking level in front of the entrance: the road's own profile, or the square's level. */
	private static int streetLevel(PlanContext ctx, int edge, int index, int[] ent) {
		RoadEdge e = ctx.state.graph.edge(edge);
		if (e.role == RoadEdge.Role.SQUARE) {
			return ctx.state.heartY;
		}
		Polyline.Dense d = e.dense();
		int lim = Math.min(d.length(), Math.max(1, e.built));
		int best = Math.min(index, lim - 1);
		long bestD = d.distance2(best, ent[0], ent[1]);
		for (int i = Math.max(0, index - 6); i < Math.min(lim, index + 7); i++) {
			long dd = d.distance2(i, ent[0], ent[1]);
			if (dd < bestD) {
				bestD = dd;
				best = i;
			}
		}
		return d.ys()[Math.min(best, d.length() - 1)];
	}

	private static void corners(Occupancy occ, Parcel p) {
		int x0 = p.x0;
		int z0 = p.z0;
		int x1 = p.x1();
		int z1 = p.z1();
		// the building's own left and right, looking out of its front door
		Direction left = p.facing.getCounterClockWise();
		boolean leftRoad = sideAccess(occ, p, left, x0, z0, x1, z1);
		boolean rightRoad = sideAccess(occ, p, left.getOpposite(), x0, z0, x1, z1);
		p.cornerLeft = leftRoad;
		p.cornerRight = rightRoad;
	}

	private static boolean sideAccess(Occupancy occ, Parcel p, Direction side, int x0, int z0, int x1, int z1) {
		int reach = Math.max(1, p.gap) + 1;
		for (int k = 1; k <= reach; k++) {
			int cnt = 0;
			int len = 0;
			switch (side) {
				case WEST -> {
					for (int z = z0; z <= z1; z++, len++) {
						cnt += Cell.isAccess(occ.get(x0 - k, z)) ? 1 : 0;
					}
				}
				case EAST -> {
					for (int z = z0; z <= z1; z++, len++) {
						cnt += Cell.isAccess(occ.get(x1 + k, z)) ? 1 : 0;
					}
				}
				case NORTH -> {
					for (int x = x0; x <= x1; x++, len++) {
						cnt += Cell.isAccess(occ.get(x, z0 - k)) ? 1 : 0;
					}
				}
				default -> {
					for (int x = x0; x <= x1; x++, len++) {
						cnt += Cell.isAccess(occ.get(x, z1 + k)) ? 1 : 0;
					}
				}
			}
			if (cnt * 2 >= len) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Slides a plot along the street toward its nearest neighbour until the gap between them is exactly its zone's
	 * gap: rows fill in tidily instead of leaving odd slivers.
	 */
	private static void snap(PlanContext ctx, Request req, Parcel p) {
		Occupancy occ = ctx.occ;
		boolean alongX = p.facing.getAxis() == Direction.Axis.Z;
		int want = p.gap;
		int bestShift = 0;
		for (int dir = -1; dir <= 1; dir += 2) {
			int free = 0;
			for (int k = 1; k <= want + 4; k++) {
				boolean blocked = alongX
					? solidOrRoad(occ, dir < 0 ? p.x0 - k : p.x1() + k, p.z0, dir < 0 ? p.x0 - k : p.x1() + k, p.z1())
					: solidOrRoad(occ, p.x0, dir < 0 ? p.z0 - k : p.z1() + k, p.x1(), dir < 0 ? p.z0 - k : p.z1() + k);
				if (blocked) {
					break;
				}
				free++;
			}
			if (free > want && free <= want + 4 && (bestShift == 0 || free - want < Math.abs(bestShift))) {
				bestShift = dir * (free - want);
			}
		}
		if (bestShift == 0) {
			return;
		}
		int ox = alongX ? bestShift : 0;
		int oz = alongX ? 0 : bestShift;
		int[] ent = p.entrance();
		if (!occ.isFree(p.x0 + ox, p.z0 + oz, p.x1() + ox, p.z1() + oz)) {
			return;
		}
		if (p.doorGap == 0 && !Cell.isAccess(occ.get(ent[0] + ox, ent[1] + oz))) {
			return;
		}
		if (p.doorGap > 0) {
			return;
		}
		Ground g = ctx.ground;
		for (int z = p.z0 + oz; z <= p.z1() + oz; z++) {
			for (int x = p.x0 + ox; x <= p.x1() + ox; x++) {
				if (g.isWater(x, z) != g.isWater(x - ox, z - oz)) {
					return;
				}
			}
		}
		int[] range = dryRangeShifted(g, p, ox, oz);
		if (Math.abs(range[0] - p.lo) > 1 || Math.abs(range[1] - p.hi) > 1) {
			return;
		}
		Parcel moved = p;
		moved.x0 += ox;
		moved.z0 += oz;
		corners(occ, moved);
	}

	private static int[] dryRangeShifted(Ground g, Parcel p, int ox, int oz) {
		Parcel q = new Parcel();
		q.x0 = p.x0 + ox;
		q.z0 = p.z0 + oz;
		q.sizeX = p.sizeX;
		q.sizeZ = p.sizeZ;
		q.facing = p.facing;
		return dryRange(g, q);
	}

	private static boolean solidOrRoad(Occupancy occ, int x0, int z0, int x1, int z1) {
		for (int z = z0; z <= z1; z++) {
			for (int x = x0; x <= x1; x++) {
				byte c = occ.get(x, z);
				if (Cell.isSolid(c) || Cell.isAccess(c) || c == Cell.PLANNED_ROAD) {
					return true;
				}
			}
		}
		return false;
	}

	private static double finalScore(PlanContext ctx, Request req, Parcel p) {
		double s = 0;
		s -= (p.hi - p.lo) * 0.8;
		s -= p.doorGap * 4;
		if (p.terrace) {
			s -= ctx.state.layout.terraces ? 3 : 10;
		}
		if ((p.cornerLeft || p.cornerRight)) {
			ArchetypeCategory c = req.info.category();
			s += c == ArchetypeCategory.COMMERCE || req.info.jobs().contains(dev.voidmc.hearth.api.JobType.MERCHANT) ? 8
				: c == ArchetypeCategory.HOUSING ? -1 : 2;
		}
		// tidy rows: a neighbour at exactly the gap
		if (hasNeighbourAtGap(ctx.occ, p)) {
			s += 6;
		}
		return s;
	}

	private static boolean hasNeighbourAtGap(Occupancy occ, Parcel p) {
		boolean alongX = p.facing.getAxis() == Direction.Axis.Z;
		int k = p.gap + 1;
		int mz = (p.z0 + p.z1()) / 2;
		int mx = (p.x0 + p.x1()) / 2;
		if (alongX) {
			return Cell.isSolid(occ.get(p.x0 - k, mz)) || Cell.isSolid(occ.get(p.x1() + k, mz));
		}
		return Cell.isSolid(occ.get(mx, p.z0 - k)) || Cell.isSolid(occ.get(mx, p.z1() + k));
	}
}
