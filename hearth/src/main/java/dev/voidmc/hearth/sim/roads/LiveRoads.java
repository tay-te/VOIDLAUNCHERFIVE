package dev.voidmc.hearth.sim.roads;

import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.Goods;
import dev.voidmc.hearth.api.HearthServices;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.api.RoadNetwork.RoadClass;
import dev.voidmc.hearth.api.SettlementTier;
import dev.voidmc.hearth.sim.SimModule;
import dev.voidmc.hearth.sim.atlas.Atlas;
import dev.voidmc.hearth.sim.atlas.RegionPlan;
import dev.voidmc.hearth.sim.settlement.SimSettlement;
import dev.voidmc.hearth.sim.world.SimWorld;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.minecraft.core.BlockPos;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The roads built since day 0 (docs/LIVING_WORLD_2.md §1.2, "Building"), on the server thread: which are begun, finding
 * their ways (off the server thread, a couple a second), and building them from both ends with the towns' labour.
 *
 * <ul>
 *   <li>A historic road one of whose towns was too young at day 0 to have built it is begun when either town is live,
 *   along the way the seed gives it.</li>
 *   <li>A settlement founded after day 0 (a hamlet, a player's town) is joined to the nearest one or two places round
 *   it by a track (a king's road between two towns).</li>
 *   <li>Where a historic road ends short of its town's own streets, a spur joins its end to the nearest place the
 *   town's roads leave from ({@code SettlementPlanner.exits}).</li>
 * </ul>
 *
 * <p>On each {@code DAY_PASSED} a town gives {@code labour_share} of its idle builders' labour (and, for a king's road,
 * a unit of stone a column) to the plot at the head of each of its roads; coin players gave toward a road buys labour
 * at a fixed rate. A plot whose work is done is built: the applier writes it as its chunks load, the plot at the head
 * as far as its work goes. Cheap: a few multiplications per road per day.
 */
public final class LiveRoads {
	private static final Logger LOG = LoggerFactory.getLogger("hearth");
	/** Work units a bit buys when a player pays toward a road. */
	static final double WORK_PER_BIT = 4;
	/** Bits a unit of stone costs the road fund when its towns have none to give. */
	static final long BITS_PER_STONE = 2;
	private static final long SPUR_SALT = 0x5B0B_5B0BL;
	private static final long DETOUR_SALT = 0xDE70_0B5EL;
	/** How far a detour keeps from its obstacle's columns, beyond the road's half width. */
	static final int DETOUR_CLEARANCE = 6;
	/** Route columns a detour's ends may move back from the refused plot to get clear of its obstacle. */
	private static final int DETOUR_REACH = 64;
	/** Places a new settlement joins at most. */
	private static final int LIVE_NEIGHBOURS = 2;

	private final SimWorld world;
	private final RoadWorld net;
	private final Set<Long> considered = new HashSet<>();
	private final ArrayDeque<Long> routeQueue = new ArrayDeque<>();
	private final LongOpenHashSet routing = new LongOpenHashSet();
	private int tokens;
	private long ticks;
	private boolean loaded;
	/** Called when plots of a road can be written further (the applier queues their loaded chunks). */
	private java.util.function.BiConsumer<Way, Integer> progressed = (w, i) -> {
	};
	public long plotsBuilt;
	public long labourGiven;

	public LiveRoads(SimWorld world, RoadWorld net) {
		this.world = world;
		this.net = net;
		net.setBuilt(w -> {
			if (!w.live()) {
				return 1F;
			}
			RoadBook.LiveRoad r = book().road(w.id());
			return r == null ? 0F : (float) r.built();
		});
	}

	public void onProgress(java.util.function.BiConsumer<Way, Integer> listener) {
		this.progressed = listener;
	}

	public RoadBook book() {
		return this.world.data(RoadBook.KEY);
	}

	public RoadWorld net() {
		return this.net;
	}

	/** Rebuilds the live ways from the book and publishes them (first tick; after a road is routed). */
	public void republish() {
		List<Way> ways = new ArrayList<>();
		for (RoadBook.LiveRoad r : book().live.values()) {
			if (r.way == null && r.routed()) {
				r.way = wayOf(r, Route.decode(r.route));
			}
			if (r.way != null) {
				ways.add(r.way);
			}
		}
		this.net.publishLive(ways, (w, i) -> {
			RoadBook.LiveRoad r = book().road(w.id());
			return r != null && (r.built(i) || r.head(true) == i || r.head(false) == i);
		});
	}

	RoadGraph.Edge edgeOf(RoadBook.LiveRoad r) {
		double len = Math.hypot(r.ends[2] - r.ends[0], r.ends[3] - r.ends[1]);
		return new RoadGraph.Edge(r.id, r.a, r.b, r.kind, false, len);
	}

	private Way wayOf(RoadBook.LiveRoad r, Route route) {
		RoadGraph.Edge e = edgeOf(r);
		Culture ca = culture(r.cultures.get(0));
		Culture cb = culture(r.cultures.size() > 1 ? r.cultures.get(1) : r.cultures.get(0));
		double len = route.length();
		RoadSegments.Dressing dress = new RoadSegments.Dressing(style(ca), style(cb), r.spur ? "" : RoadWorld.sign(r.names.get(1), len),
			r.spur ? "" : RoadWorld.sign(r.names.get(0), len), r.kind == RoadClass.KINGS_ROAD && !r.spur);
		List<RoadSegments.Segment> segs = RoadSegments.cut(r.id, r.kind, route, dress);
		return Way.of(e, r.ends[0], r.ends[1], r.ends[2], r.ends[3], r.names.get(0), r.names.get(1), route, segs, true, r.spur);
	}

	private static Culture culture(String name) {
		for (Culture c : Culture.values()) {
			if (c.getSerializedName().equals(name)) {
				return c;
			}
		}
		return Culture.TEMPERATE;
	}

	private static net.minecraft.resources.Identifier style(Culture c) {
		return HearthServices.hasBlueprints() ? HearthServices.blueprints().style(c) : dev.voidmc.hearth.Hearth.id(c.getSerializedName());
	}

	// ---------------------------------------------------------------- the tick

	/** Every tick: a couple of route searches a second are started. */
	public void tick() {
		if (!this.loaded) {
			this.loaded = true;
			RoadBook b = book();
			if (b.fingerprint == 0) {
				b.fingerprint = this.net.fingerprint();
				this.world.markDirty();
			}
			for (RoadBook.LiveRoad r : b.live.values()) {
				if (!r.routed() && !r.unroutable()) {
					this.routeQueue.add(r.id);
				}
			}
			republish();
		}
		if (++this.ticks % 20 == 0) {
			this.tokens = Math.max(this.tokens, this.net.config().roads().liveRoutesPerSecond());
		}
		while (this.tokens > 0 && !this.routeQueue.isEmpty()) {
			long id = this.routeQueue.poll();
			RoadBook.LiveRoad r = book().road(id);
			if (r == null || r.routed() || r.unroutable() || !this.routing.add(id)) {
				continue;
			}
			this.tokens--;
			startRoute(r);
		}
	}

	/** Whether historic roads may be written live into land generated before them (the fingerprint still matches). */
	public boolean historicLive() {
		RoadBook b = book();
		return this.net.config().roads().liveHistoric() && (b.fingerprint == 0 || b.fingerprint == this.net.fingerprint());
	}

	private void startRoute(RoadBook.LiveRoad r) {
		RoadGraph.Edge e = edgeOf(r);
		int ax = r.ends[0];
		int az = r.ends[1];
		int bx = r.ends[2];
		int bz = r.ends[3];
		String na = r.names.get(0);
		String nb = r.names.get(1);
		Culture ca = culture(r.cultures.get(0));
		Culture cb = culture(r.cultures.get(1));
		// what keeps it away, gathered here on the server thread: the atlas's sites and structures (thread-safe), and the
		// live settlements' hearts and planned ground near its way (a snapshot)
		List<int[]> cores = new ArrayList<>();
		for (SimSettlement s : r.detour() ? List.<SimSettlement>of() : this.world.settlements()) {
			if (s.id() == r.a || s.id() == r.b || r.spur && s.id() == r.a) {
				continue;
			}
			if (RoadWorld.reaches(ax, az, bx, bz, s.center().getX() - 200, s.center().getZ() - 200, s.center().getX() + 200, s.center().getZ() + 200)) {
				cores.add(new int[]{s.center().getX(), s.center().getZ(), radius(s)});
			}
		}
		LongOpenHashSet planned = r.spur ? plannedNear(ax, az, bx, bz) : new LongOpenHashSet();
		RoadRouter.Avoid round = r.detour() ? detourAvoid(r.avoid, r.kind) : RoadRouter.Avoid.NONE;
		RoadRouter.Avoid sites = r.spur ? RoadRouter.Avoid.NONE : this.net.avoid(ax, az, bx, bz, r.a, r.b);
		int exitX = bx;
		int exitZ = bz;
		RoadRouter.Avoid avoid = (x, z) -> {
			if (!planned.isEmpty() && planned.contains(BlockPos.asLong(x >> 1, 0, z >> 1)) && Math.abs(x - exitX) + Math.abs(z - exitZ) > 8) {
				return Double.POSITIVE_INFINITY;
			}
			if (round != RoadRouter.Avoid.NONE && round.cost(x, z) == Double.POSITIVE_INFINITY) {
				return Double.POSITIVE_INFINITY;
			}
			double extra = sites.cost(x, z);
			for (int[] c : cores) {
				double d = Math.hypot(x - c[0], z - c[1]);
				if (d < c[2] * 0.9) {
					return Double.POSITIVE_INFINITY;
				}
				if (d < c[2] + 160) {
					double f = 1 - (d - c[2] * 0.9) / (c[2] * 0.1 + 160);
					extra += 4 * f * f;
				}
			}
			return extra;
		};
		long id = r.id;
		RoadWorld.background(() -> {
			Way w = this.net.route(e, ax, az, bx, bz, na, ca, nb, cb, avoid, true);
			Route route = w.route();
			if (route != null && r.spur && !r.detour()) {
				route = trimToExit(route, exitX, exitZ);
			}
			Route found = route;
			this.world.level().getServer().execute(() -> {
				this.routing.remove(id);
				RoadBook.LiveRoad live = book().road(id);
				if (live == null) {
					return;
				}
				live.route = found == null ? new int[]{0} : found.encode();
				live.way = found == null ? null : wayOf(live, found);
				this.world.markDirty();
				if (found == null) {
					LOG.info("hearth roads: no way for the road from {} to {}; it is not built", na, nb);
				}
				if (live.detour() && live.way != null) {
					// the plot it replaces was due: a detour is built whole at once
					live.fromA = live.way.segments().size();
					LOG.info("hearth roads: the road from {} to {} goes round someone's work ({} blocks)", na, nb, Math.round(live.way.length()));
				}
				republish();
				if (live.detour() && live.way != null) {
					for (int i = 0; i < live.way.segments().size(); i++) {
						this.progressed.accept(live.way, i);
					}
				}
			});
		});
	}

	/** Whether any column of {@code cols} ({@code x, z} pairs) is within {@code r} of {@code x, z}. */
	static boolean near(int[] cols, int x, int z, int r) {
		int r2 = r * r;
		for (int k = 0; k + 1 < cols.length; k += 2) {
			int dx = cols[k] - x;
			int dz = cols[k + 1] - z;
			if (dx * dx + dz * dz <= r2) {
				return true;
			}
		}
		return false;
	}

	/**
	 * A plot of a road was refused because somebody's work stands across it (docs/LIVING_WORLD_2.md §12, risk 1): a detour
	 * is laid round it, from the road a little before the obstacle to the road a little after, keeping
	 * {@link #DETOUR_CLEARANCE} blocks beyond the road's edge from every column where the work stood. A detour of a
	 * detour is never laid (that plot stays a gap), nor one whose obstacle stands at the road's very end (a town's own
	 * streets take over there). Returns whether a detour was begun.
	 */
	public boolean detour(Way w, int plot, int[] blocked, long day) {
		RoadBook book = book();
		RoadBook.LiveRoad self = book.road(w.id());
		if (self != null && self.detour() || w.route() == null || blocked.length < 2 || plot < 0 || plot >= w.segments().size()) {
			return false;
		}
		long id = RoadGraph.edgeId(w.id() ^ DETOUR_SALT, plot);
		if (book.road(id) != null) {
			return false;
		}
		Route route = w.route();
		int[] span = detourSpan(route, w.segments().get(plot), blocked, w.kind());
		if (span == null) {
			LOG.info("hearth roads: the road from {} to {} cannot go round the work across plot {}; it has a gap there", w.nameA(), w.nameB(), plot);
			return false;
		}
		int s0 = span[0];
		int e0 = span[1];
		String ca = cultureOf(w.a());
		String cb = cultureOf(w.b());
		RoadBook.LiveRoad r = new RoadBook.LiveRoad(id, w.id(), w.id(), w.kind(), true,
			new int[]{route.xs()[s0], route.zs()[s0], route.xs()[e0], route.zs()[e0]}, List.of(w.nameA(), w.nameB()), List.of(ca, cb), new int[0], 0, 0, 0, 0, 0,
			day);
		r.detourOf = plot;
		r.avoid = blocked;
		book.live.put(id, r);
		this.routeQueue.add(id);
		this.world.markDirty();
		return true;
	}

	/**
	 * Where a detour round the work across a plot leaves the road and rejoins it: the route columns before and after the
	 * plot, moved out until they are clear of the work (null when the work stands too near the road's end, or too far along it).
	 */
	static int @Nullable [] detourSpan(Route route, RoadSegments.Segment seg, int[] blocked, RoadClass kind) {
		int clear = DETOUR_CLEARANCE + (kind == RoadClass.KINGS_ROAD ? 3 : 2) + 2;
		int s0 = seg.from();
		while (s0 > 0 && seg.from() - s0 < DETOUR_REACH && near(blocked, route.xs()[s0], route.zs()[s0], clear)) {
			s0--;
		}
		int e0 = seg.to();
		while (e0 < route.size() - 1 && e0 - seg.to() < DETOUR_REACH && near(blocked, route.xs()[e0], route.zs()[e0], clear)) {
			e0++;
		}
		if (near(blocked, route.xs()[s0], route.zs()[s0], clear) || near(blocked, route.xs()[e0], route.zs()[e0], clear) || e0 - s0 < 4) {
			return null;
		}
		return new int[]{s0, e0};
	}

	/** What keeps a detour's way off its obstacle (and the obstacle's clearance). */
	static RoadRouter.Avoid detourAvoid(int[] blocked, RoadClass kind) {
		int clear = DETOUR_CLEARANCE + (kind == RoadClass.KINGS_ROAD ? 3 : 2);
		return (x, z) -> near(blocked, x, z, clear) ? Double.POSITIVE_INFINITY : 0;
	}

	private String cultureOf(long place) {
		SimSettlement s = this.world.settlement(place);
		return s != null ? s.culture().getSerializedName() : cultureName(this.net.site(place));
	}

	/** Columns planned by live plots round a spur's ends (half resolution), for its search. */
	private LongOpenHashSet plannedNear(int ax, int az, int bx, int bz) {
		LongOpenHashSet out = new LongOpenHashSet();
		int x0 = Math.min(ax, bx) - 48;
		int x1 = Math.max(ax, bx) + 48;
		int z0 = Math.min(az, bz) - 48;
		int z1 = Math.max(az, bz) + 48;
		for (int x = x0; x <= x1; x += 2) {
			for (int z = z0; z <= z1; z += 2) {
				if (this.world.spatial().isPlanned(x, z)) {
					out.add(BlockPos.asLong(x >> 1, 0, z >> 1));
				}
			}
		}
		return out;
	}

	/** A spur stops three blocks short of the town's road it joins (the town's paving takes over there). */
	static Route trimToExit(Route r, int ex, int ez) {
		int n = r.size();
		int keep = n;
		while (keep > 2 && Math.hypot(r.xs()[keep - 1] - ex, r.zs()[keep - 1] - ez) < 3) {
			keep--;
		}
		if (keep == n) {
			return r;
		}
		return new Route(java.util.Arrays.copyOf(r.xs(), keep), java.util.Arrays.copyOf(r.ys(), keep), java.util.Arrays.copyOf(r.zs(), keep),
			java.util.Arrays.copyOf(r.types(), keep), java.util.Arrays.copyOf(r.decks(), keep), r.states());
	}

	/** How far a live settlement's own ground reaches from its heart (where a road out of it ends). */
	static int radius(SimSettlement s) {
		SettlementTier t = s.tier();
		return switch (t) {
			case CAMP -> 28;
			case HAMLET -> 48;
			case VILLAGE -> 72;
			case TOWN -> 110;
			case CITY -> 160;
		};
	}

	// ---------------------------------------------------------------- a day

	/** A sim day passed for a settlement: its roads are begun if they are not, and given their share of its labour. */
	public void dayPassed(SimSettlement s, long day) {
		if (!this.net.enabled()) {
			return;
		}
		if (!this.considered.contains(s.id()) && consider(s, day)) {
			this.considered.add(s.id());
		}
		labour(s);
	}

	/** Begins the roads a settlement should have. Returns false if it must be asked again (the network is still being worked out). */
	boolean consider(SimSettlement s, long day) {
		RoadBook book = book();
		if (Atlas.isHistoric(s.id())) {
			List<RoadGraph.Edge> edges = this.net.adjacencyIfReady(s.id());
			if (edges == null) {
				return false;
			}
			boolean done = true;
			for (RoadGraph.Edge e : edges) {
				Way w = this.net.wayIfReady(e.id());
				if (w == null) {
					this.net.wayAsync(e);
					done = false;
					continue;
				}
				if (!e.historic() && book.road(e.id()) == null && w.route() != null) {
					// a historic road its towns were too young to have built: begun now, along the way the seed gives it
					RegionPlan.Site a = this.net.site(e.a());
					RegionPlan.Site b = this.net.site(e.b());
					RoadBook.LiveRoad r = new RoadBook.LiveRoad(e.id(), e.a(), e.b(), e.kind(), false, new int[]{w.ax(), w.az(), w.bx(), w.bz()},
						List.of(w.nameA(), w.nameB()), List.of(cultureName(a), cultureName(b)), w.route().encode(), 0, 0, 0, 0, 0, day);
					r.way = w;
					book.live.put(r.id, r);
					this.world.markDirty();
					LOG.info("hearth roads: {} and {} begin the road between them ({} blocks)", w.nameA(), w.nameB(), Math.round(w.length()));
				}
				if (w.route() != null && (e.historic() || book.road(e.id()) != null)) {
					spur(s, w, day);
				}
			}
			republish();
			return done;
		}
		for (RoadBook.LiveRoad r : book.live.values()) {
			if (!r.spur && (r.a == s.id() || r.b == s.id())) {
				return true;
			}
		}
		joinLive(s, day);
		return true;
	}

	private static String cultureName(RegionPlan.@Nullable Site s) {
		return s == null ? Culture.TEMPERATE.getSerializedName() : s.culture().getSerializedName();
	}

	/** A spur from a road's end near {@code s} to the nearest exit of its streets. */
	private void spur(SimSettlement s, Way w, long day) {
		long id = RoadGraph.edgeId(s.id(), w.id() ^ SPUR_SALT);
		RoadBook book = book();
		if (book.road(id) != null || !HearthServices.hasPlanner()) {
			return;
		}
		int ex = w.a() == s.id() ? w.ax() : w.bx();
		int ez = w.a() == s.id() ? w.az() : w.bz();
		List<BlockPos> exits = HearthServices.planner().exits(s);
		BlockPos best = null;
		double bestD = Double.MAX_VALUE;
		for (BlockPos p : exits) {
			double d = Math.hypot(p.getX() - ex, p.getZ() - ez);
			if (d < bestD) {
				bestD = d;
				best = p;
			}
		}
		if (best == null || bestD < 6 || bestD > 320) {
			return;
		}
		String culture = s.culture().getSerializedName();
		RoadBook.LiveRoad r = new RoadBook.LiveRoad(id, s.id(), w.id(), RoadClass.TRACK, true, new int[]{ex, ez, best.getX(), best.getZ()},
			List.of(s.name(), s.name()), List.of(culture, culture), new int[0], 0, 0, 0, 0, 0, day);
		book.live.put(id, r);
		this.routeQueue.add(id);
		this.world.markDirty();
	}

	/** A settlement founded after day 0 is joined to its nearest places. */
	private void joinLive(SimSettlement s, long day) {
		int len = this.net.rules().maxLength();
		RoadGraph.Node self = new RoadGraph.Node(s.id(), s.center().getX(), s.center().getZ(), -1, s.tier().ordinal() >= SettlementTier.TOWN.ordinal());
		List<RoadGraph.Node> around = new ArrayList<>(this.net.sitesWithin(self.x(), self.z(), len));
		for (SimSettlement o : this.world.settlements()) {
			if (o != s && !Atlas.isHistoric(o.id()) && o.center().distSqr(s.center()) <= (double) len * len) {
				around.add(new RoadGraph.Node(o.id(), o.center().getX(), o.center().getZ(), -1, o.tier().ordinal() >= SettlementTier.TOWN.ordinal()));
			}
		}
		List<RoadGraph.Node> cands = RoadGraph.candidates(self, around, new RoadGraph.Rules(len, LIVE_NEIGHBOURS, 200, 30));
		// a hamlet's first road is to the town that sent it
		long parent = s.clockState().parent();
		cands.sort(java.util.Comparator.comparingInt((RoadGraph.Node n) -> n.id() == parent ? 0 : 1).thenComparingDouble(n -> n.distance(self)));
		for (RoadGraph.Node m : cands.subList(0, Math.min(LIVE_NEIGHBOURS, cands.size()))) {
			long id = RoadGraph.edgeId(s.id(), m.id());
			if (book().road(id) != null) {
				continue;
			}
			SimSettlement other = this.world.settlement(m.id());
			RegionPlan.Site site = this.net.site(m.id());
			int mr = other != null ? radius(other) : site != null ? RoadWorld.endRadius(site.age()) : 48;
			String mName = other != null ? other.name() : site != null ? site.name() : "";
			String mCulture = other != null ? other.culture().getSerializedName() : cultureName(site);
			int[] ea = this.net.end(self.x(), self.z(), radius(s), m.x(), m.z());
			int[] eb = this.net.end(m.x(), m.z(), mr, self.x(), self.z());
			boolean towns = self.town() && (other != null ? other.tier().ordinal() >= SettlementTier.TOWN.ordinal() : site != null && site.age() >= 200);
			RoadBook.LiveRoad r = new RoadBook.LiveRoad(id, s.id(), m.id(), towns ? RoadClass.KINGS_ROAD : RoadClass.TRACK, false,
				new int[]{ea[0], ea[1], eb[0], eb[1]}, List.of(s.name(), mName), List.of(s.culture().getSerializedName(), mCulture), new int[0], 0, 0, 0, 0, 0,
				day);
			book().live.put(id, r);
			this.routeQueue.add(id);
			this.world.markDirty();
			LOG.info("hearth roads: {} lays out a road to {}", s.name(), mName);
		}
	}

	// ---------------------------------------------------------------- building

	/** Work units a plot of a road takes. */
	static double cost(RoadSegments.Segment seg) {
		int columns = seg.to() - seg.from() + 1;
		int width = seg.spec().params().getIntOr("width", 3);
		return columns * width * (seg.bridge() ? 9.0 : 4.0);
	}

	/** Builders' labour a settlement could give today (work units), before anyone takes any. */
	static double pool(SimSettlement s) {
		int builders = 0;
		for (Resident r : s.residentList()) {
			if (r.job == JobType.BUILDER && !s.isResidentOut(r.id)) {
				builders++;
			}
		}
		var l = SimModule.rules().config.labour();
		return builders * l.workPerBuilderHour() * l.builderHoursPerDay();
	}

	private void labour(SimSettlement s) {
		RoadBook book = book();
		double share = this.net.config().roads().labourShare();
		double pool = -1;
		for (RoadBook.LiveRoad r : book.live.values()) {
			boolean atA = r.a == s.id();
			boolean atB = !r.spur && r.b == s.id();
			if (!atA && !atB || r.way == null || r.finished()) {
				continue;
			}
			if (pool < 0) {
				pool = pool(s);
			}
			int want = (int) Math.ceil(pool * share);
			double work = want <= 0 ? 0 : s.takeBuilderLabour(want);
			if (r.funds > 0) {
				// coin given toward the road hires labour
				double hired = Math.min(r.funds * WORK_PER_BIT, 4 * Math.max(want, 64));
				long spent = (long) Math.ceil(hired / WORK_PER_BIT);
				r.funds -= Math.min(r.funds, spent);
				work += hired;
			}
			if (work <= 0) {
				continue;
			}
			this.labourGiven += (long) work;
			advance(r, atA, work, s);
			this.world.markDirty();
		}
	}

	/** Puts {@code work} into the plot at the head of the road from one end; finished plots are built. */
	public void advance(RoadBook.LiveRoad r, boolean fromA, double work, @Nullable SimSettlement giver) {
		Way w = r.way;
		if (w == null) {
			return;
		}
		double left = work;
		while (left > 0) {
			int head = r.head(fromA);
			if (head < 0) {
				break;
			}
			RoadSegments.Segment seg = w.segments().get(head);
			double cost = cost(seg);
			double done = fromA ? r.progressA : r.progressB;
			double take = Math.min(left, cost - done);
			done += take;
			left -= take;
			if (done >= cost - 1e-6) {
				if (r.kind == RoadClass.KINGS_ROAD && !seg.bridge() && !payStone(r, seg, giver)) {
					// metalled roads want their stone: the plot waits for it
					done = cost - 1;
					left = 0;
					if (fromA) {
						r.progressA = done;
					} else {
						r.progressB = done;
					}
					break;
				}
				if (fromA) {
					r.fromA++;
					r.progressA = 0;
				} else {
					r.fromB++;
					r.progressB = 0;
				}
				this.plotsBuilt++;
				this.progressed.accept(w, head);
				republish();
				if (r.finished()) {
					LOG.info("hearth roads: the road from {} to {} is finished", w.nameA(), w.nameB());
				}
			} else {
				if (fromA) {
					r.progressA = done;
				} else {
					r.progressB = done;
				}
				this.progressed.accept(w, head);
			}
		}
	}

	private boolean payStone(RoadBook.LiveRoad r, RoadSegments.Segment seg, @Nullable SimSettlement giver) {
		int need = seg.to() - seg.from() + 1;
		int have = giver == null ? 0 : giver.stockpile().get(Goods.STONE);
		if (have >= need) {
			giver.stockpile().take(Goods.STONE, need);
			return true;
		}
		long bits = (need - have) * BITS_PER_STONE;
		if (r.funds >= bits) {
			if (giver != null && have > 0) {
				giver.stockpile().take(Goods.STONE, have);
			}
			r.funds -= bits;
			return true;
		}
		return false;
	}

	/** How far the plot at a road's head is (0–1) from one end, for the applier's partial writes. */
	public double headProgress(RoadBook.LiveRoad r, boolean fromA) {
		int head = r.head(fromA);
		if (head < 0 || r.way == null) {
			return 0;
		}
		double cost = cost(r.way.segments().get(head));
		return Math.min(1, (fromA ? r.progressA : r.progressB) / cost);
	}

	/**
	 * A player gives coin toward a road (the reeve's or steward's "pay toward the road"): it hires labour from the next day.
	 * Returns false if there is no such unfinished road.
	 */
	public boolean fund(long roadId, long bits) {
		RoadBook.LiveRoad r = book().road(roadId);
		if (r == null || r.finished() || bits <= 0) {
			return false;
		}
		r.funds += bits;
		this.world.markDirty();
		return true;
	}

	/** Work done by an embodied road crew right now (the crew's tick): the head plot advances a little. */
	public void crewWork(long roadId, boolean fromA, double work) {
		RoadBook.LiveRoad r = book().road(roadId);
		if (r != null && !r.finished()) {
			advance(r, fromA, work, this.world.settlement(fromA ? r.a : r.b));
			this.world.markDirty();
		}
	}

	public String describe() {
		RoadBook b = book();
		int routed = 0;
		int finished = 0;
		for (RoadBook.LiveRoad r : b.live.values()) {
			routed += r.routed() ? 1 : 0;
			finished += r.finished() ? 1 : 0;
		}
		return String.format(Locale.ROOT, "%d live roads (%d routed, %d finished, %d waiting for a route), %d plots built this session, %d work given, %d plots refused",
			b.live.size(), routed, finished, this.routeQueue.size() + this.routing.size(), this.plotsBuilt, this.labourGiven, b.refusedCount());
	}
}
