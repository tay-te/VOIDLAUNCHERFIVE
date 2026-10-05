package dev.voidmc.hearth.repair;

import dev.voidmc.hearth.api.HearthWorld;
import dev.voidmc.hearth.api.PlotState;
import dev.voidmc.hearth.api.PlotView;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.api.SettlementEvents.Damaged.Cause;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * Inspection rounds (docs/LIVING_WORLD.md §2): a builder walks a built plot and compares its blueprint with the world, a
 * budgeted number of cells per tick, catching what no event saw — an enderman's theft, a piston's shove, a block moved
 * while the server was off. A missing block is queued as damage ({@link Cause#OTHER}); a block left in a doorway or on a
 * street is queued as litter.
 *
 * <p>Found damage is held until the round ends and then committed, unless a quarter or more of the plot's blocks were
 * missing: that is a building whose chunks have not had their construction written yet (the sim's applier is behind),
 * not a ruin, and mending it would build it twice. Cells in chunks loaded less than a minute ago are skipped for the same
 * reason.
 */
public final class Inspector {
	/** Chunks younger than this (ticks since they loaded) are not inspected: the sim's applier may still be writing them. */
	static final int SETTLE_TICKS = 1200;

	private Inspector() {
	}

	/** One round over one plot. */
	public static final class Round {
		public final long settlementId;
		public final int plotId;
		final LongArrayList cells = new LongArrayList();
		final IntArrayList states = new IntArrayList();
		final IntArrayList stages = new IntArrayList();
		int cursor;
		int solid;
		int missing;
		int checked;
		boolean committed;
		/** Inspect chunks however recently they loaded (tests and the command, which know they are written). */
		public boolean trustChunks;
		final List<Found> found = new ArrayList<>();

		Round(long settlementId, int plotId) {
			this.settlementId = settlementId;
			this.plotId = plotId;
		}

		public boolean done() {
			return this.cursor >= this.cells.size();
		}

		public int size() {
			return this.cells.size();
		}

		public int checked() {
			return this.checked;
		}

		public boolean committed() {
			return this.committed;
		}
	}

	private record Found(long pos, int stage, BlockState expected, boolean litter) {
	}

	/** The plot a builder should inspect next: built, the settlement's own, nearest among the least recently seen. */
	public static @Nullable PlotView choose(Settlement s, BlockPos near, int radius, Map<Integer, Long> lastSeen, long now) {
		PlotView best = null;
		double bestScore = Double.MAX_VALUE;
		double r2 = (double) radius * radius;
		for (PlotView p : s.plots()) {
			if (p.state() != PlotState.BUILT || p.owner() != null || PlotLocator.isExtraction(p.spec())) {
				continue;
			}
			double d2 = p.spec().origin().distSqr(near);
			if (d2 > r2) {
				continue;
			}
			long seen = lastSeen.getOrDefault(p.id(), Long.MIN_VALUE / 2);
			// an hour of game time unseen outweighs a long walk
			double score = Math.sqrt(d2) - Math.min(72000, now - seen) / 600.0;
			if (score < bestScore) {
				bestScore = score;
				best = p;
			}
		}
		return best;
	}

	/** Starts a round: reads every completed cell of the plot (from the position index's sections). */
	public static @Nullable Round begin(RepairRuntime rt, HearthWorld world, Settlement s, PlotView plot) {
		Round r = new Round(s.id(), plot.id());
		boolean ok = rt.locator.forEachCell(s, PlotLocator.services(world), plot, (pos, stage, expected) -> {
			r.cells.add(pos);
			r.states.add(Block.getId(expected));
			r.stages.add(stage);
		});
		return ok ? r : null;
	}

	/** Compares up to {@code budget} cells with the world. Returns how many it looked at. */
	public static int step(RepairRuntime rt, ServerLevel level, HearthWorld world, Settlement s, Round r, int budget) {
		PlotView plot = s.plot(r.plotId);
		if (plot == null || plot.state() != PlotState.BUILT) {
			r.cursor = r.cells.size();
			return 0;
		}
		boolean litter = RepairModule.config().tracking().litter();
		PlotLocator.Source source = PlotLocator.services(world);
		RepairQueue q = s.data(RepairModule.QUEUE);
		long now = rt.now();
		int n = 0;
		while (n < budget && r.cursor < r.cells.size()) {
			int i = r.cursor++;
			long p = r.cells.getLong(i);
			n++;
			BlockPos pos = BlockPos.of(p);
			if (!level.isLoaded(pos)) {
				continue;
			}
			int cx = SectionPos.blockToSectionCoord(pos.getX());
			int cz = SectionPos.blockToSectionCoord(pos.getZ());
			// the sim says whether its applier has caught up with this chunk (requests/p3-repair.md §5); the settle time
			// stays as a guard for anything else still settling there
			if (!r.trustChunks && (!world.written(s, r.plotId, cx, cz) || now - rt.chunkLoadedAt(level, cx, cz) < SETTLE_TICKS / 4)) {
				continue;
			}
			r.checked++;
			BlockState expected = Block.stateById(r.states.getInt(i));
			BlockState current = level.getBlockState(pos);
			if (!DamageRules.volatileBlock(expected)) {
				r.solid++;
				if (DamageRules.missing(expected, current)) {
					r.missing++;
					if (q.get(p) == null) {
						r.found.add(new Found(p, r.stages.getInt(i), expected, false));
					}
				}
			} else if (litter && expected.isAir() && DamageRules.litterBlock(current) && q.get(p) == null
				&& Litter.blocksWay(rt.locator, s, source, plot, pos)) {
				r.found.add(new Found(p, r.stages.getInt(i), expected, true));
			}
		}
		return n;
	}

	/**
	 * Ends a round: queues what it found, unless so much was missing that the building cannot have been written yet.
	 * Returns how many entries were queued (-1 if the round was thrown away as unwritten).
	 */
	public static int commit(RepairRuntime rt, ServerLevel level, HearthWorld world, Settlement s, Round r) {
		PlotView plot = s.plot(r.plotId);
		if (r.committed) {
			return 0;
		}
		r.committed = true;
		if (plot == null) {
			return 0;
		}
		if (r.solid > 16 && r.missing * 4 >= r.solid) {
			RepairModule.LOG.debug("inspection of {} plot {}: {} of {} blocks missing, taken as not yet written", s.name(), r.plotId, r.missing, r.solid);
			return -1;
		}
		int added = 0;
		RepairQueue q = rt.queue(s);
		for (Found f : r.found) {
			BlockPos pos = BlockPos.of(f.pos);
			if (f.litter) {
				Damage d = new Damage(f.pos, plot.id(), f.stage, -1, Cause.OTHER, RepairRuntime.day(level), null, Damage.LITTER);
				d.setExpected(f.expected);
				if (q.add(d, RepairModule.config().queue().maxEntries())) {
					added++;
					rt.withDamage.add(s.id());
				}
			} else if (rt.tracker.record(level, world, new PlotLocator.Hit(s, plot, f.stage, f.expected), pos, Cause.OTHER, null, true)) {
				added++;
			}
		}
		if (added > 0) {
			s.markDirty();
		}
		return added;
	}

	/** Runs a whole round at once (the {@code /hearth repair inspect} command). */
	public static int inspectNow(RepairRuntime rt, ServerLevel level, HearthWorld world, Settlement s, PlotView plot, int[] counts) {
		Round r = begin(rt, world, s, plot);
		if (r == null) {
			return 0;
		}
		r.trustChunks = true;
		while (!r.done()) {
			step(rt, level, world, s, r, Integer.MAX_VALUE);
		}
		counts[0] = r.size();
		counts[1] = r.checked;
		counts[2] = r.missing;
		return commit(rt, level, world, s, r);
	}

	/** Per-settlement round bookkeeping, shared by the builders' behaviour. */
	public static final class Schedule {
		private final Map<Long, Long> next = new HashMap<>();
		private final Map<Long, java.util.UUID> inspector = new HashMap<>();
		private final Map<Long, Map<Integer, Long>> lastSeen = new HashMap<>();

		/** Makes a settlement due for a round now. */
		public void dueNow(long settlementId) {
			this.next.remove(settlementId);
		}

		public boolean due(long settlementId, long now) {
			return now >= this.next.getOrDefault(settlementId, 0L) && !this.inspector.containsKey(settlementId);
		}

		public boolean isInspector(long settlementId, java.util.UUID folk) {
			return folk.equals(this.inspector.get(settlementId));
		}

		public void take(long settlementId, java.util.UUID folk) {
			this.inspector.put(settlementId, folk);
		}

		public void finish(long settlementId, java.util.UUID folk, long now, int interval) {
			if (folk.equals(this.inspector.get(settlementId))) {
				this.inspector.remove(settlementId);
			}
			this.next.put(settlementId, now + interval);
		}

		public Map<Integer, Long> lastSeen(long settlementId) {
			return this.lastSeen.computeIfAbsent(settlementId, k -> new HashMap<>());
		}
	}
}
