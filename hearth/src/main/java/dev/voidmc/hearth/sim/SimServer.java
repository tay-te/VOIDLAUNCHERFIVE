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
	private static volatile @Nullable SimServer current;

	private final MinecraftServer server;
	private final SimRules rules;
	private final Map<ResourceKey<Level>, SimWorld> worlds = new ConcurrentHashMap<>();
	private final PriorityQueue<Due> queue = new PriorityQueue<>();
	private final Map<SimSettlement, Long> nextDue = new IdentityHashMap<>();
	private final Map<SimWorld, Set<SimSettlement>> embodied = new IdentityHashMap<>();
	private final Stats stats = new Stats();
	private final Set<ResourceKey<Level>> dimensions = new HashSet<>();
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
		return (long) ((c.minBudgetMs() + (c.budgetMs() - c.minBudgetMs()) * f) * 1e6);
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
			long t0 = System.nanoTime();
			long simulated = SettlementClock.advance(d.settlement, target);
			long dt = System.nanoTime() - t0;
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
			w.applier().tick(applierBudget, this.rules.config.applier().maxBlocks());
		}
		if (this.ticks % 20 == 0) {
			for (SimWorld w : this.worlds.values()) {
				presence(w);
			}
		}
		this.stats.tickNanos.add(System.nanoTime() - tickStart);
	}

	// ---------------------------------------------------------------- embodiment and arrivals

	private void presence(SimWorld w) {
		SimConfig.Embody cfg = this.rules.config.embody();
		Set<SimSettlement> was = this.embodied.get(w);
		Set<SimSettlement> now = new HashSet<>();
		for (ServerPlayer p : w.level().players()) {
			int px = p.getBlockX();
			int pz = p.getBlockZ();
			if (!p.isSpectator()) {
				for (long id : w.spatial().near(px, pz)) {
					SimSettlement s = w.settlement(id);
					if (s != null && distance(s.bounds(), px, pz) <= cfg.radius() + (s.embodied() ? cfg.hysteresis() : 0)) {
						now.add(s);
					}
				}
			}
			SimSettlement in = w.settlementAt(px, pz, cfg.bannerMargin());
			long inId = in == null ? Long.MIN_VALUE : in.id();
			Long prev = w.playerPresence().put(p.getUUID(), inId);
			if (prev == null || prev != inId) {
				if (ServerPlayNetworking.canSend(p, ArrivalPayload.TYPE)) {
					ServerPlayNetworking.send(p, in == null ? ArrivalPayload.LEFT : ArrivalPayload.of(in));
				}
			}
		}
		w.playerPresence().keySet().removeIf(id -> w.level().getServer().getPlayerList().getPlayer(id) == null);
		for (SimSettlement s : was) {
			if (!now.contains(s)) {
				s.setEmbodied(false);
				SettlementEvents.EMBODIMENT_CHANGED.invoker().onEmbodimentChanged(w, s, false);
			}
		}
		for (SimSettlement s : now) {
			if (!was.contains(s)) {
				s.setEmbodied(true);
				SettlementEvents.EMBODIMENT_CHANGED.invoker().onEmbodimentChanged(w, s, true);
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
		was.addAll(now);
	}

	/** Horizontal distance from a column to a box (0 inside). */
	static int distance(BoundingBox b, int x, int z) {
		int dx = Math.max(0, Math.max(b.minX() - x, x - b.maxX()));
		int dz = Math.max(0, Math.max(b.minZ() - z, z - b.maxZ()));
		return (int) Math.sqrt((double) dx * dx + (double) dz * dz);
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

	/** Players currently counted inside a settlement (for commands). */
	public static int playersIn(SimWorld w, long settlement) {
		int n = 0;
		for (Map.Entry<UUID, Long> e : w.playerPresence().entrySet()) {
			if (e.getValue() == settlement) {
				n++;
			}
		}
		return n;
	}
}
