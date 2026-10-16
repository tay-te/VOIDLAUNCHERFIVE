package dev.voidmc.hearth.sim;

import dev.voidmc.hearth.api.SettlementEvents;
import dev.voidmc.hearth.sim.model.SimRules;
import dev.voidmc.hearth.sim.net.ArrivalPayload;
import dev.voidmc.hearth.sim.settlement.SettlementClock;
import dev.voidmc.hearth.sim.settlement.SimPlot;
import dev.voidmc.hearth.sim.settlement.SimSettlement;
import dev.voidmc.hearth.sim.world.SimWorld;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jspecify.annotations.Nullable;

/**
 * Hearth on one running server: the {@link SimWorld} of each configured level, and the simulation clock that steps every
 * live settlement round-robin under a per-tick time budget. A priority queue keyed by due tick means a tick costs only
 * the settlements actually due, never a pass over all of them; embodied settlements come round every second, the rest at
 * least every {@code stepInterval} ticks, and one that is behind (adopted with years to catch up) comes round every tick
 * until it is not.
 */
public final class SimServer {
	private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger("hearth");
	private static volatile @Nullable SimServer current;

	private final MinecraftServer server;
	private final SimRules rules;
	private final Map<ResourceKey<Level>, SimWorld> worlds = new ConcurrentHashMap<>();
	private final PriorityQueue<Due> queue = new PriorityQueue<>();
	private final Map<SimSettlement, Long> nextDue = new IdentityHashMap<>();
	/** What a sim day of each settlement costs to step, smoothed: how much catch-up fits in a tick. */
	private final Map<SimSettlement, Double> nanosPerDay = new IdentityHashMap<>();
	private final Map<SimWorld, Set<SimSettlement>> embodied = new IdentityHashMap<>();
	/** Settlements held embodied with nobody near ({@code /hearth sim embody}): to test the embodied paths. */
	private final Set<SimSettlement> pinned = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
	private final Stats stats = new Stats();
	private final Set<ResourceKey<Level>> dimensions = new HashSet<>();
	/** Time per tick for writing queued settlements after an autosave. */
	private static final long SAVE_BUDGET = 1_000_000;
	/** Time per tick for looking at sites for daughter hamlets. */
	private static final long HAMLET_BUDGET = 200_000;
	private int plannerCalls;
	private long ticks;

	private record Due(long tick, long seq, SimWorld world, SimSettlement settlement) implements Comparable<Due> {
		@Override
		public int compareTo(Due o) {
			int c = Long.compare(this.tick, o.tick);
			return c != 0 ? c : Long.compare(this.seq, o.seq);
		}
	}

	private long seq;

	/** Timings and counters for the dev harness and {@code /hearth sim stats}. */
	public static final class Stats {
		public final LongAdder steps = new LongAdder();
		public final LongAdder stepNanos = new LongAdder();
		public final AtomicLong maxStepNanos = new AtomicLong();
		public final LongAdder simulatedTicks = new LongAdder();
		public final LongAdder plannerCalls = new LongAdder();
		public final LongAdder adoptions = new LongAdder();
		public final LongAdder adoptNanos = new LongAdder();
		public final LongAdder featureChunks = new LongAdder();
		public final LongAdder featureNanos = new LongAdder();
		public final LongAdder featureBlocks = new LongAdder();
		public final LongAdder guardSkips = new LongAdder();
		public final LongAdder saveNanos = new LongAdder();
		public final LongAdder saved = new LongAdder();
		public final LongAdder tickNanos = new LongAdder();
		/** The longest a tick's sim work took, and how many took over 5 ms (a hitch a player can feel at 20 tps). */
		public final AtomicLong maxTickNanos = new AtomicLong();
		public final LongAdder slowTicks = new LongAdder();
	}

	private SimServer(MinecraftServer server, SimRules rules) {
		this.server = server;
		this.rules = rules;
		for (String d : rules.config.dimensions()) {
			Identifier id = Identifier.tryParse(d);
			if (id != null) {
				this.dimensions.add(ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, id));
			}
		}
	}

	public static @Nullable SimServer current() {
		return current;
	}

	static SimServer start(MinecraftServer server, SimRules rules) {
		SimServer s = new SimServer(server, rules);
		current = s;
		return s;
	}

	static void stop() {
		current = null;
	}

	/** The Hearth state of a level, from any thread (world generation asks), or null if Hearth does not run there. */
	public static @Nullable SimWorld world(ServerLevel level) {
		SimServer s = current;
		if (s == null) {
			return null;
		}
		SimWorld w = s.worlds.get(level.dimension());
		return w != null && w.level() == level ? w : null;
	}

	public MinecraftServer server() {
		return this.server;
	}

	public SimRules rules() {
		return this.rules;
	}

	public Stats stats() {
		return this.stats;
	}

	public List<SimWorld> worlds() {
		return new ArrayList<>(this.worlds.values());
	}

	boolean wants(ServerLevel level) {
		return this.dimensions.contains(level.dimension());
	}

	void addWorld(SimWorld w) {
		this.worlds.put(w.level().dimension(), w);
		this.embodied.put(w, new HashSet<>());
		w.load();
	}

	void removeWorld(ServerLevel level) {
		SimWorld w = this.worlds.remove(level.dimension());
		if (w != null) {
			w.unload();
			w.save(true);
			this.embodied.remove(w);
		}
	}

	// ---------------------------------------------------------------- the clock

	/** Puts a settlement on the clock: now (new, or just embodied), or staggered over one interval (loaded). */
	public void schedule(SimWorld w, SimSettlement s, boolean now) {
		long due = this.ticks + (now ? 0 : Math.floorMod(s.id() * 0x9E3779B97F4A7C15L, this.rules.config.clock().stepInterval()));
		Long current = this.nextDue.get(s);
		if (current != null && current <= due) {
			return;
		}
		this.nextDue.put(s, due);
		this.queue.add(new Due(due, this.seq++, w, s));
	}

	/** One {@code planner.next} call per tick across the server (they cost milliseconds); false once spent. */
	public boolean tryPlannerCall() {
		if (this.plannerCalls >= this.rules.config.clock().plannerCallsPerTick()) {
			return false;
		}
		this.plannerCalls++;
		this.stats.plannerCalls.increment();
		return true;
	}

	/** The budget for this tick: the configured one while the server keeps up, shrinking toward the minimum as it slows. */
	long budgetNanos() {
		return budgetNanos(this.rules.config.clock(), this.server.getAverageTickTimeNanos() / 1e6);
	}

	/** The clock's budget at a given average tick time: full up to 60% of {@code highMspt}, the minimum from it on. */
	public static long budgetNanos(SimConfig.Clock c, double mspt) {
		double comfortable = c.highMspt() * 0.6;
		double f = mspt <= comfortable ? 1 : mspt >= c.highMspt() ? 0 : 1 - (mspt - comfortable) / (c.highMspt() - comfortable);
		// (a minimum set above the budget is the budget: slowing down never raises it)
		double min = Math.min(c.minBudgetMs(), c.budgetMs());
		return (long) ((min + (c.budgetMs() - min) * f) * 1e6);
	}

	void tick() {
		long tickStart = System.nanoTime();
		this.ticks++;
		this.plannerCalls = 0;
		long budget = budgetNanos();
		SimConfig.Clock c = this.rules.config.clock();
		while (!this.queue.isEmpty() && this.queue.peek().tick <= this.ticks && System.nanoTime() - tickStart < budget) {
			Due d = this.queue.poll();
			Long due = this.nextDue.get(d.settlement);
			if (due == null || due != d.tick || d.world.settlement(d.settlement.id()) != d.settlement) {
				continue;
			}
			long target = d.world.simNow(d.settlement);
			// a settlement far behind catches up only as many days as this tick's budget has room for
			long left = budget - (System.nanoTime() - tickStart);
			double perDay = this.nanosPerDay.getOrDefault(d.settlement, 50_000.0);
			long days = Math.max(1, Math.min(c.maxCatchUpDays(), (long) (left / perDay)));
			long capped = Math.min(target, d.settlement.simTick() + days * SimRules.DAY);
			long t0 = System.nanoTime();
			long simulated;
			try {
				simulated = SettlementClock.advance(d.settlement, capped);
			} catch (RuntimeException e) {
				// a bug in a listener, the planner or a blueprint must not take the server down: log it, try again later
				int n = this.failures.merge("step " + d.settlement.id(), 1, Integer::sum);
				if (n == 1) {
					LOG.error("hearth: stepping settlement {} ({}) failed; retrying in a minute", d.settlement.id(), d.settlement.name(), e);
				} else if (Integer.bitCount(n) == 1) {
					LOG.error("hearth: stepping settlement {} ({}) failed again ({} times now): {}", d.settlement.id(), d.settlement.name(), n, e.toString());
				}
				long retry = this.ticks + 1200;
				this.nextDue.put(d.settlement, retry);
				this.queue.add(new Due(retry, this.seq++, d.world, d.settlement));
				continue;
			}
			long dt = System.nanoTime() - t0;
			this.slowest.merge("a settlement's step", dt, Math::max);
			if (simulated >= SimRules.DAY / 4) {
				double measured = dt / (simulated / (double) SimRules.DAY);
				this.nanosPerDay.put(d.settlement, perDay * 0.7 + measured * 0.3);
			}
			this.stats.steps.increment();
			this.stats.stepNanos.add(dt);
			this.stats.maxStepNanos.accumulateAndGet(dt, Math::max);
			this.stats.simulatedTicks.add(simulated);
			boolean behind = d.settlement.simTick() < target;
			long next = this.ticks + (behind ? 1 : d.settlement.embodied() ? c.embodiedStepInterval() : c.stepInterval());
			this.nextDue.put(d.settlement, next);
			this.queue.add(new Due(next, this.seq++, d.world, d.settlement));
		}
		long applierBudget = (long) (this.rules.config.applier().budgetMs() * 1e6);
		for (SimWorld w : this.worlds.values()) {
			// one world's failure must not stop the others, nor the server: each part is caught, logged and tried next tick
			guard(w, "writing construction", () -> w.applier().tick(applierBudget, this.rules.config.applier().maxBlocks()));
			// sites for daughter hamlets, a few candidates a tick
			guard(w, "looking for hamlet sites", () -> w.tickHamlets(HAMLET_BUDGET));
			// caravans arriving, and trees owed by abstract foresters felled in loaded woods
			guard(w, "caravans", w::tickCaravans);
			guard(w, "felling", () -> w.tickFelling((long) (this.rules.resources.extraction().fellBudgetMs() * 1e6)));
		}
		if (this.ticks % 20 == 0) {
			long p0 = System.nanoTime();
			presence();
			this.slowest.merge("presence", System.nanoTime() - p0, Math::max);
		}
		// autosaves are written a slice at a time
		long saveStart = System.nanoTime();
		int saved = 0;
		for (SimWorld w : this.worlds.values()) {
			saved += w.saveSome(SAVE_BUDGET);
		}
		if (saved > 0) {
			this.slowest.merge("autosave slice", System.nanoTime() - saveStart, Math::max);
			this.stats.saved.add(saved);
			this.stats.saveNanos.add(System.nanoTime() - saveStart);
		}
		long tickNanos = System.nanoTime() - tickStart;
		this.stats.tickNanos.add(tickNanos);
		this.stats.maxTickNanos.accumulateAndGet(tickNanos, Math::max);
		if (tickNanos > 5_000_000L) {
			this.stats.slowTicks.increment();
		}
	}

	// ---------------------------------------------------------------- embodiment and arrivals

	/** Where each player stood at the last look: the level and the settlement (whose ids are per level), for the banner. */
	private record Presence(ResourceKey<Level> level, long settlement) {
	}

	private final Map<UUID, Presence> presence = new java.util.HashMap<>();
	/** The longest each part of the tick has taken (ns), for {@code /hearth sim stats}. */
	private final Map<String, Long> slowest = new java.util.TreeMap<>();

	/** The slowest parts of the tick so far, longest first: "caravans and felling 3.1 ms, ...". */
	public String slowestParts() {
		StringBuilder b = new StringBuilder();
		this.slowest.entrySet().stream().filter(e -> !e.getKey().startsWith("embodying") && !e.getKey().startsWith("letting"))
			.sorted(Map.Entry.<String, Long>comparingByValue().reversed()).limit(6)
			.forEach(e -> b.append(b.isEmpty() ? "" : ", ").append(e.getKey()).append(String.format(java.util.Locale.ROOT, " %.1f ms", e.getValue() / 1e6)));
		return b.toString();
	}

	/** Failures so far of each part of the tick in each world, so a part failing every tick logs once, then now and then. */
	private final Map<String, Integer> failures = new java.util.HashMap<>();

	/**
	 * Embodiment and the arrival banner, every second, for every player on the server: one who is in a level Hearth does
	 * not run in (the Nether, the End) stands in no settlement, so walking through a portal out of a town says so.
	 */
	private void presence() {
		SimConfig.Embody cfg = this.rules.config.embody();
		Map<SimWorld, Set<SimSettlement>> now = new IdentityHashMap<>();
		for (SimWorld w : this.worlds.values()) {
			now.put(w, new HashSet<>());
		}
		for (ServerPlayer p : this.server.getPlayerList().getPlayers()) {
			SimWorld w = world(p.level());
			SimSettlement in = null;
			if (w != null) {
				int px = p.getBlockX();
				int pz = p.getBlockZ();
				if (!p.isSpectator()) {
					Set<SimSettlement> near = now.get(w);
					for (long id : w.spatial().near(px, pz)) {
						SimSettlement s = w.settlement(id);
						if (s != null && distance(s.bounds(), px, pz) <= cfg.radius() + (s.embodied() ? cfg.hysteresis() : 0)) {
							near.add(s);
						}
					}
				}
				in = w.settlementAt(px, pz, cfg.bannerMargin());
			}
			Presence at = new Presence(p.level().dimension(), in == null ? Long.MIN_VALUE : in.id());
			Presence prev = this.presence.put(p.getUUID(), at);
			// (leaving every town for the wilderness of another level is no news to a client that thinks it is in none)
			boolean changed = prev == null ? in != null : !prev.equals(at) && (in != null || prev.settlement != Long.MIN_VALUE);
			if (changed && ServerPlayNetworking.canSend(p, ArrivalPayload.TYPE)) {
				ServerPlayNetworking.send(p, in == null ? ArrivalPayload.LEFT : ArrivalPayload.of(in));
			}
		}
		this.presence.keySet().removeIf(id -> this.server.getPlayerList().getPlayer(id) == null);
		for (SimSettlement s : this.pinned) {
			for (SimWorld w : this.worlds.values()) {
				if (w.settlement(s.id()) == s) {
					now.get(w).add(s);
				}
			}
		}
		for (SimWorld w : this.worlds.values()) {
			Set<SimSettlement> was = this.embodied.get(w);
			Set<SimSettlement> here = now.get(w);
			if (was == null || here == null) {
				continue;
			}
			for (SimSettlement s : was) {
				if (!here.contains(s)) {
					s.setEmbodied(false);
					guard(w, "letting " + s.name() + " go", () -> SettlementEvents.EMBODIMENT_CHANGED.invoker().onEmbodimentChanged(w, s, false));
				}
			}
			for (SimSettlement s : here) {
				if (!was.contains(s)) {
					s.setEmbodied(true);
					guard(w, "embodying " + s.name(), () -> SettlementEvents.EMBODIMENT_CHANGED.invoker().onEmbodimentChanged(w, s, true));
					schedule(w, s, true);
					// partial stages are now written as they accrue
					for (SimPlot plot : s.plots()) {
						if (plot.isActive()) {
							w.applier().enqueuePlot(plot);
						}
					}
				}
			}
			was.clear();
			was.addAll(here);
		}
	}

	/**
	 * Runs one part of a world's tick, catching what it throws: a bug in one settlement, a listener or a blueprint must
	 * not take the server down. The first failure of each part is logged with its trace, later ones now and then.
	 */
	void guard(SimWorld w, String what, Runnable part) {
		long t0 = System.nanoTime();
		try {
			part.run();
			this.slowest.merge(what, System.nanoTime() - t0, Math::max);
		} catch (RuntimeException | LinkageError e) {
			String key = w.level().dimension().identifier() + " " + what;
			int n = this.failures.merge(key, 1, Integer::sum);
			if (n == 1) {
				LOG.error("hearth: {} failed in {}; carrying on", what, w.level().dimension().identifier(), e);
			} else if (Integer.bitCount(n) == 1) {
				LOG.error("hearth: {} failed in {} again ({} times now): {}", what, w.level().dimension().identifier(), n, e.toString());
			}
		}
	}

	/** Horizontal distance from a column to a box (0 inside). */
	static int distance(BoundingBox b, int x, int z) {
		int dx = Math.max(0, Math.max(b.minX() - x, x - b.maxX()));
		int dz = Math.max(0, Math.max(b.minZ() - z, z - b.maxZ()));
		return (int) Math.sqrt((double) dx * dx + (double) dz * dz);
	}

	/** Holds a settlement embodied whether or not anyone is near (or lets it go); returns whether it is now pinned. */
	public boolean togglePinned(SimSettlement s) {
		if (this.pinned.remove(s)) {
			return false;
		}
		this.pinned.add(s);
		return true;
	}

	/** Settlements currently embodied in a world (for commands). */
	public int embodiedCount(SimWorld w) {
		Set<SimSettlement> s = this.embodied.get(w);
		return s == null ? 0 : s.size();
	}

	// ---------------------------------------------------------------- saving

	void save(boolean flush) {
		long start = System.nanoTime();
		int n = 0;
		for (SimWorld w : this.worlds.values()) {
			n += w.save(flush);
		}
		this.stats.saved.add(n);
		this.stats.saveNanos.add(System.nanoTime() - start);
	}

	public long ticks() {
		return this.ticks;
	}
}
