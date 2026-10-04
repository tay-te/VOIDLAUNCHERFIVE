package dev.voidmc.hearth.repair;

import dev.voidmc.hearth.api.HearthWorld;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.api.Settlement;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Repairs while the builders are records (docs/LIVING_WORLD.md §2: abstract settlements repair on chunk load).
 *
 * <p><b>Labour.</b> Every {@code interval_ticks}, each settlement with damage accrues repair labour for the sim time that
 * passed (the overworld clock, so sleeping and catch-up count; capped at {@code max_catch_up_days}): its builders who
 * are not walking about as entities × {@code blocks_per_builder_hour}. Each unit mends one queued block in the record —
 * earlier stages first, its goods taken from the stockpile (a block whose goods are not in store waits) — and marks it
 * {@link Damage#MENDED}.
 *
 * <p><b>Applying.</b> Mended blocks are written when their chunk is loaded: at once if it is, otherwise when it loads,
 * under a per-tick budget ({@code apply_max_blocks}, {@code apply_budget_ms}), through {@link Restorer} and its edit
 * safety. Damage claimed by an embodied builder is left to them.
 */
public final class AbstractRepairs {
	private record Job(ResourceKey<Level> level, long chunk) {
	}

	private final RepairRuntime runtime;
	/** Per level: chunk → settlements with mended blocks waiting there. */
	private final Map<ResourceKey<Level>, Long2ObjectOpenHashMap<LongOpenHashSet>> pending = new HashMap<>();
	private final ArrayDeque<Job> ready = new ArrayDeque<>();
	/** Blocks mended per (settlement, plot) in the current round, for {@link RepairEvents#REPAIRED}. */
	private final Map<Long, Int2IntOpenHashMap> rounds = new HashMap<>();
	private long nextAccrual;
	long mended;
	long applied;
	long refused;
	long deferred;
	long applyNanos;

	AbstractRepairs(RepairRuntime runtime) {
		this.runtime = runtime;
	}

	/** Notes the mended blocks a queue holds (at server start). */
	void noteQueue(ServerLevel level, Settlement s, RepairQueue q) {
		for (Damage d : q.all()) {
			if (d.has(Damage.MENDED)) {
				notePending(level, s, d);
			}
		}
	}

	private void notePending(ServerLevel level, Settlement s, Damage d) {
		long chunk = RepairQueue.chunkKey(d.pos);
		boolean fresh = this.pending.computeIfAbsent(level.dimension(), k -> new Long2ObjectOpenHashMap<>()).computeIfAbsent(chunk, k -> new LongOpenHashSet())
			.add(s.id());
		if (fresh && level.hasChunk(ChunkKeys.x(chunk), ChunkKeys.z(chunk))) {
			this.ready.add(new Job(level.dimension(), chunk));
		}
	}

	/** A chunk loaded: its mended blocks can go in. */
	void chunkLoaded(ServerLevel level, int chunkX, int chunkZ) {
		Long2ObjectOpenHashMap<LongOpenHashSet> m = this.pending.get(level.dimension());
		long key = ChunkKeys.key(chunkX, chunkZ);
		if (m != null && m.containsKey(key)) {
			this.ready.add(new Job(level.dimension(), key));
		}
	}

	void tick(long now) {
		RepairConfig.Abstract cfg = RepairModule.config().abstractRepair();
		if (!cfg.enabled()) {
			return;
		}
		if (now >= this.nextAccrual) {
			this.nextAccrual = now + Math.max(20, cfg.intervalTicks());
			accrueAll();
		}
		apply(cfg);
	}

	private void accrueAll() {
		LongIterator it = this.runtime.withDamage.iterator();
		List<Long> empty = new ArrayList<>();
		while (it.hasNext()) {
			long id = it.nextLong();
			RepairRuntime.Located at = this.runtime.find(id);
			if (at == null) {
				empty.add(id);
				continue;
			}
			RepairQueue q = at.settlement().data(RepairModule.QUEUE);
			if (q.isEmpty()) {
				empty.add(id);
				continue;
			}
			accrue(at.world(), at.settlement(), q);
		}
		empty.forEach(this.runtime.withDamage::remove);
	}

	/** Builders of a settlement who are records now (their entity, if any, mends for itself). */
	static int recordBuilders(Settlement s) {
		int n = 0;
		for (Resident r : s.residents()) {
			if (r.job == JobType.BUILDER && !s.isResidentEmbodied(r.id)) {
				n++;
			}
		}
		return n;
	}

	/** Repair labour, in blocks, that {@code builders} accrue over {@code elapsed} sim ticks (1000 a sim hour). */
	static double labour(int builders, double perBuilderHour, long elapsed, double carry) {
		return carry + builders * perBuilderHour * Math.max(0, elapsed) / 1000.0;
	}

	/** Labour left over is kept, but never more than a day's worth (a crew does not bank a month of idleness). */
	static double carry(double left, int builders, double perBuilderHour) {
		return Math.max(0, Math.min(left, builders * perBuilderHour * 24.0));
	}

	/** Accrues labour for the time since the last accrual and mends what it pays for. Returns blocks mended. */
	int accrue(HearthWorld world, Settlement s, RepairQueue q) {
		RepairConfig cfg = RepairModule.config();
		ServerLevel level = world.level();
		long clock = level.getOverworldClockTime();
		if (q.lastAccrual == 0 || clock < q.lastAccrual) {
			q.lastAccrual = clock;
			s.markDirty();
			return 0;
		}
		long elapsed = Math.min(clock - q.lastAccrual, cfg.abstractRepair().maxCatchUpDays() * 24000L);
		q.lastAccrual = clock;
		int builders = recordBuilders(s);
		// builders who walk about as entities mend for themselves; abstract labour comes only from those who do not
		double labour = labour(builders, cfg.abstractRepair().blocksPerBuilderHour(), elapsed, q.carry);
		if (builders == 0) {
			q.carry = 0;
			s.markDirty();
			return 0;
		}
		long now = this.runtime.now();
		int claimTicks = cfg.queue().claimTicks();
		List<Damage> todo = new ArrayList<>();
		for (Damage d : q.all()) {
			if (RepairQueue.open(d, now, claimTicks)) {
				todo.add(d);
			}
		}
		todo.sort(Comparator.comparingInt((Damage d) -> d.stage).thenComparingInt(d -> net.minecraft.core.BlockPos.getY(d.pos)));
		PlotLocator.Source source = PlotLocator.services(world);
		int done = 0;
		List<Damage> found = new ArrayList<>();
		for (Damage d : todo) {
			if (labour < 1.0) {
				break;
			}
			BlockState expected = Restorer.expected(this.runtime, s, source, d);
			if (expected == null) {
				q.remove(d.pos);
				continue;
			}
			if (!d.has(Damage.LITTER) && cfg.queue().chargeGoods()) {
				if (!RepairBill.affordable(s.stockpile(), expected)) {
					d.defer(now + cfg.queue().deferTicks());
					continue;
				}
				RepairBill.charge(s.stockpile(), expected);
			}
			d.set(Damage.MENDED, true);
			labour -= 1.0;
			done++;
			found.add(d);
			notePending(level, s, d);
		}
		// labour nothing could use is not banked beyond a day's worth
		q.carry = carry(labour, builders, cfg.abstractRepair().blocksPerBuilderHour());
		if (done > 0) {
			this.mended += done;
			s.markDirty();
			this.runtime.vandalism.discovered(level, s, found);
		}
		return done;
	}

	/**
	 * Mends everything open in a settlement's record now, as if its builders had the time ({@code /hearth repair mend}):
	 * goods are taken unless {@code free}, and blocks whose goods are short are left. Returns how many were mended.
	 */
	public int mendAll(HearthWorld world, Settlement s, boolean free) {
		RepairQueue q = s.data(RepairModule.QUEUE);
		PlotLocator.Source source = PlotLocator.services(world);
		long now = this.runtime.now();
		int done = 0;
		List<Damage> found = new ArrayList<>();
		for (Damage d : new ArrayList<>(q.all())) {
			if (d.has(Damage.MENDED) || d.awaitingPayment() || d.claimed(now, RepairModule.config().queue().claimTicks())) {
				continue;
			}
			BlockState expected = Restorer.expected(this.runtime, s, source, d);
			if (expected == null) {
				q.remove(d.pos);
				continue;
			}
			if (!free && !d.has(Damage.LITTER) && RepairModule.config().queue().chargeGoods()) {
				if (!RepairBill.affordable(s.stockpile(), expected)) {
					continue;
				}
				RepairBill.charge(s.stockpile(), expected);
			}
			d.set(Damage.MENDED, true);
			found.add(d);
			notePending(world.level(), s, d);
			done++;
		}
		this.mended += done;
		s.markDirty();
		this.runtime.vandalism.discovered(world.level(), s, found);
		return done;
	}

	private void apply(RepairConfig.Abstract cfg) {
		if (this.ready.isEmpty()) {
			return;
		}
		long t0 = System.nanoTime();
		long budget = (long) (cfg.applyBudgetMs() * 1e6);
		int blocks = 0;
		List<Job> again = new ArrayList<>();
		while (!this.ready.isEmpty() && blocks < cfg.applyMaxBlocks() && System.nanoTime() - t0 < budget) {
			Job job = this.ready.poll();
			ServerLevel level = this.runtime.server.getLevel(job.level);
			Long2ObjectOpenHashMap<LongOpenHashSet> m = this.pending.get(job.level);
			LongOpenHashSet ids = m == null ? null : m.get(job.chunk);
			if (level == null || ids == null || !level.hasChunk(ChunkKeys.x(job.chunk), ChunkKeys.z(job.chunk))) {
				continue;
			}
			HearthWorld world = HearthWorld.of(level);
			if (world == null) {
				continue;
			}
			boolean left = false;
			for (LongIterator it = ids.iterator(); it.hasNext(); ) {
				long id = it.nextLong();
				Settlement s = world.settlement(id);
				if (s == null) {
					it.remove();
					continue;
				}
				int[] used = {0};
				boolean rest = applyChunk(level, world, s, ChunkKeys.x(job.chunk), ChunkKeys.z(job.chunk), cfg.applyMaxBlocks() - blocks, used);
				blocks += used[0];
				if (rest) {
					left = true;
				} else {
					it.remove();
				}
			}
			if (ids.isEmpty()) {
				m.remove(job.chunk);
			} else if (left) {
				again.add(job);
			}
		}
		this.ready.addAll(again);
		this.applyNanos += System.nanoTime() - t0;
	}

	/** Writes one settlement's mended blocks in a chunk. Returns whether some are still waiting there (budget ran out). */
	private boolean applyChunk(ServerLevel level, HearthWorld world, Settlement s, int cx, int cz, int max, int[] used) {
		RepairQueue q = s.data(RepairModule.QUEUE);
		List<Damage> here = new ArrayList<>();
		for (Damage d : q.inChunk(cx, cz)) {
			if (d.has(Damage.MENDED)) {
				here.add(d);
			}
		}
		here.sort(Comparator.comparingInt((Damage d) -> d.stage).thenComparingInt(d -> net.minecraft.core.BlockPos.getY(d.pos)));
		PlotLocator.Source source = PlotLocator.services(world);
		List<Damage> retry = new ArrayList<>();
		boolean rest = false;
		for (Damage d : here) {
			if (used[0] >= max) {
				rest = true;
				break;
			}
			used[0]++;
			if (!applyOne(level, world, s, q, source, d)) {
				retry.add(d);
			}
		}
		// what holds a block up may have come later in the pass (a lamp on a wall lower in the list); one more try, then
		// it goes back to the queue for a builder, its goods returned to the store
		for (Damage d : retry) {
			if (q.get(d.pos) != null && !applyOne(level, world, s, q, source, d)) {
				d.set(Damage.MENDED, false);
				d.defer(this.runtime.now() + RepairModule.config().queue().deferTicks());
				BlockState expected = d.expected();
				if (expected != null && !d.has(Damage.LITTER) && RepairModule.config().queue().chargeGoods()) {
					RepairBill.refund(s.stockpile(), expected);
				}
				this.deferred++;
			}
		}
		s.markDirty();
		return rest;
	}

	/** Writes one mended block; false if it must wait (deferred). */
	private boolean applyOne(ServerLevel level, HearthWorld world, Settlement s, RepairQueue q, PlotLocator.Source source, Damage d) {
		if (q.get(d.pos) == null) {
			// went back with its other half
			return true;
		}
		Restorer.Result r = Restorer.restore(this.runtime, level, s, source, d, false);
		switch (r) {
			case PLACED, SAME -> {
				q.remove(d.pos);
				this.applied++;
				mendedOne(world, s, d.plot);
				return true;
			}
			case REFUSED, GONE -> {
				q.remove(d.pos);
				BlockState expected = d.expected();
				if (r == Restorer.Result.REFUSED && expected != null && !d.has(Damage.LITTER) && RepairModule.config().queue().chargeGoods()) {
					RepairBill.refund(s.stockpile(), expected);
				}
				this.refused++;
				return true;
			}
			default -> {
				return false;
			}
		}
	}

	/** A block of a plot was put back; when the plot has none left, {@link RepairEvents#REPAIRED} fires. */
	void mendedOne(HearthWorld world, Settlement s, int plot) {
		Int2IntOpenHashMap round = this.rounds.computeIfAbsent(s.id(), k -> new Int2IntOpenHashMap());
		int blocks = round.addTo(plot, 1) + 1;
		RepairQueue q = s.data(RepairModule.QUEUE);
		if (q.countOf(plot) == 0) {
			round.remove(plot);
			try {
				RepairEvents.REPAIRED.invoker().onRepaired(world, s, plot, blocks);
			} catch (RuntimeException e) {
				RepairModule.LOG.warn("a REPAIRED listener failed", e);
			}
		}
	}

	/** Chunks with mended blocks waiting, over all levels. */
	public int pendingChunks() {
		int n = 0;
		for (Long2ObjectOpenHashMap<LongOpenHashSet> m : this.pending.values()) {
			n += m.size();
		}
		return n;
	}

	public String stats() {
		return String.format(java.util.Locale.ROOT, "%d mended in the record, %d written, %d refused, %d deferred, %d chunks waiting, apply %.1f ms in all",
			this.mended, this.applied, this.refused, this.deferred, pendingChunks(), this.applyNanos / 1e6);
	}
}
