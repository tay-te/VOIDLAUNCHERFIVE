package dev.voidmc.hearth.sim.roads;

import dev.voidmc.hearth.api.Blueprint;
import dev.voidmc.hearth.api.BlueprintService;
import dev.voidmc.hearth.api.TerrainProbe;
import dev.voidmc.hearth.sim.apply.AppliedLedger;
import dev.voidmc.hearth.sim.apply.StepWriter;
import dev.voidmc.hearth.sim.settlement.Cursor;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * Writes road plots into one chunk with the sim's edit safety ({@link StepWriter}): a road replaces only air, plants,
 * natural ground and natural trees, and what this road (any of its plots) put there itself — never a player's blocks, a
 * town's paving or a chest. Shared by world generation (historic roads, written as the chunk is made) and the roads'
 * applier (land generated before the road: existing worlds, live roads).
 *
 * <p>Before a plot's first blocks go into a chunk at runtime the writer looks first: where too much of what the plot
 * would replace is somebody's (a player's house, a wall), nothing is written and the plot is refused, so a road stops at
 * a player's build instead of running half through it.
 */
public final class RoadWriter {
	/** A plot refused here: this many of its blocks would land on somebody's... */
	static final int REFUSE_CONFLICTS = 12;
	/** ...and they are at least this share of the blocks it would write. */
	static final double REFUSE_SHARE = 0.08;
	/** Columns of a refused plot's obstacle kept for its detour. */
	static final int MAX_BLOCKED = 96;

	private RoadWriter() {
	}

	/** One plot of a road to write, and how far it may be written. */
	public record Job(Way way, int index, long target) {
		RoadSegments.Segment segment() {
			return this.way.segments().get(this.index);
		}
	}

	/** What writing a chunk did. */
	public static final class Outcome {
		public AppliedLedger ledger;
		public int placed;
		public int conflicts;
		/** Plots refused here (index into the jobs). */
		public final it.unimi.dsi.fastutil.ints.IntArrayList refused = new it.unimi.dsi.fastutil.ints.IntArrayList();
		/** For each refused plot, the columns ({@code x, z} pairs, at most {@link #MAX_BLOCKED}) where somebody's work stood in its way. */
		public final List<int[]> blocked = new java.util.ArrayList<>();
		/** Jobs left for later (out of budget). */
		public boolean unfinished;

		Outcome(AppliedLedger ledger) {
			this.ledger = ledger;
		}
	}

	/**
	 * Writes the jobs into the chunk in order (each road's plots in order along it, so where two meet the earlier one's
	 * blocks count as the road's own).
	 *
	 * @param look        look before the first blocks of a plot (runtime): refuse it if it would land on somebody's work
	 * @param canopy      clears the natural trees over a plot's columns before its first blocks (runtime), or null
	 * @param budgetNanos time allowed (from {@code start}); world generation passes {@link Long#MAX_VALUE}
	 */
	public static Outcome write(int cx, int cz, AppliedLedger ledger, List<Job> jobs, StepWriter.Blocks world, BlueprintService blueprints,
		TerrainProbe terrain, boolean look, java.util.function.@Nullable ToIntFunction<Job> canopy, long start, long budgetNanos, int maxBlocks) {
		Outcome out = new Outcome(ledger);
		for (int j = 0; j < jobs.size(); j++) {
			Job job = jobs.get(j);
			long id = RoadWorld.ledgerId(job.way().id());
			long cur = out.ledger.get(id, job.index());
			if (cur >= job.target()) {
				continue;
			}
			if (out.placed > 0 && (out.placed >= maxBlocks || System.nanoTime() - start >= budgetNanos)) {
				out.unfinished = true;
				break;
			}
			Blueprint bp;
			try {
				bp = blueprints.blueprint(job.segment().spec(), terrain);
			} catch (RuntimeException e) {
				// an unknown archetype or a generator bug: this plot stays unwritten, the others go on
				continue;
			}
			long target = Math.min(job.target(), Cursor.pack(bp.stageCount(), 0));
			StepWriter.Owned owned = owned(cx, cz, out.ledger, jobs, job, bp, cur, blueprints, terrain);
			if (cur <= Cursor.ZERO && look) {
				it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet columns = new it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet();
				StepWriter.Result dry = look(bp, cx, cz, target, world, owned, columns);
				if (dry.conflicts > REFUSE_CONFLICTS && dry.conflicts > dry.attempted() * REFUSE_SHARE) {
					out.refused.add(j);
					int[] cols = new int[columns.size() * 2];
					int k = 0;
					for (long c : columns) {
						cols[k++] = (int) (c >> 32);
						cols[k++] = (int) c;
					}
					out.blocked.add(cols);
					out.conflicts += dry.conflicts;
					continue;
				}
				owned = owned(cx, cz, out.ledger, jobs, job, bp, cur, blueprints, terrain);
			}
			if (cur <= Cursor.ZERO && canopy != null) {
				out.placed += canopy.applyAsInt(job);
			}
			long reached = cur;
			while (reached < target) {
				long next = Math.min(target, Cursor.pack(Cursor.stage(reached) + 1, 0));
				StepWriter.Result r = StepWriter.write(bp, cx, cz, reached, next, world, owned);
				out.placed += r.placed;
				out.conflicts += r.conflicts;
				reached = next;
				if (reached < target && (out.placed >= maxBlocks || System.nanoTime() - start >= budgetNanos)) {
					out.unfinished = true;
					break;
				}
			}
			out.ledger = out.ledger.with(id, job.index(), reached);
			if (out.unfinished) {
				break;
			}
		}
		return out;
	}

	/**
	 * The columns of one chunk where somebody's work stands in a plot's way (packed {@code x << 32 | z}, added to
	 * {@code out} up to {@link #MAX_BLOCKED}): the obstacle a refused plot's detour keeps clear of, gathered from every
	 * loaded chunk the plot crosses, not only the one that refused it.
	 */
	public static void blocked(int cx, int cz, AppliedLedger ledger, List<Job> jobs, Job job, StepWriter.Blocks world, BlueprintService blueprints,
		TerrainProbe terrain, it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet out) {
		Blueprint bp;
		try {
			bp = blueprints.blueprint(job.segment().spec(), terrain);
		} catch (RuntimeException e) {
			return;
		}
		long target = Cursor.pack(bp.stageCount(), 0);
		look(bp, cx, cz, target, world, owned(cx, cz, ledger, jobs, job, bp, Cursor.ZERO, blueprints, terrain), out);
	}

	/** What the road's other plots here (and this one, so far) already put in this chunk counts as the road's own. */
	private static StepWriter.Owned owned(int cx, int cz, AppliedLedger ledger, List<Job> jobs, Job job, Blueprint bp, long cur, BlueprintService blueprints,
		TerrainProbe terrain) {
		return StepWriter.lazyOwned(sink -> {
			for (Job other : jobs) {
				if (other == job || !kin(other.way(), job.way())) {
					continue;
				}
				long c = ledger.get(RoadWorld.ledgerId(other.way().id()), other.index());
				if (c <= Cursor.ZERO) {
					continue;
				}
				try {
					StepWriter.forApplied(blueprints.blueprint(other.segment().spec(), terrain), cx, cz, c, sink);
				} catch (RuntimeException ignored) {
					// its archetype is gone
				}
			}
			StepWriter.forApplied(bp, cx, cz, cur, sink);
		});
	}

	/**
	 * Whether two ways' blocks count as one road's: the same way, or a detour and the road it goes round (or two detours of
	 * one road), which overlap where the detour leaves and rejoins it.
	 */
	static boolean kin(Way a, Way b) {
		if (a.id() == b.id()) {
			return true;
		}
		long pa = detourOf(a);
		long pb = detourOf(b);
		return pa == b.id() || pb == a.id() || pa != 0 && pa == pb;
	}

	/** The road a detour goes round (a detour's two ends are that road), or 0. */
	static long detourOf(Way w) {
		return w.spur() && w.live() && w.a() == w.b() ? w.a() : 0;
	}

	/** Counts what writing {@code [0, target)} would do, without writing. */
	static StepWriter.Result look(Blueprint bp, int cx, int cz, long target, StepWriter.Blocks world, StepWriter.Owned owned) {
		return look(bp, cx, cz, target, world, owned, null);
	}

	/** As {@link #look}, also gathering the columns of the conflicts (packed {@code x << 32 | z}, at most {@link #MAX_BLOCKED}). */
	static StepWriter.Result look(Blueprint bp, int cx, int cz, long target, StepWriter.Blocks world, StepWriter.Owned owned,
		it.unimi.dsi.fastutil.longs.@Nullable LongLinkedOpenHashSet columns) {
		StepWriter.Result r = new StepWriter.Result();
		StepWriter.forRange(bp, cx, cz, Cursor.ZERO, target, (x, y, z, state, tag) -> {
			BlockState current = world.get(x, y, z);
			long key = BlockPos.asLong(x, y, z);
			if (current == state) {
				r.same++;
			} else if (!StepWriter.replaceable(current, x, y, z, world) && !owned.owns(key, current)) {
				r.conflicts++;
				if (columns != null && columns.size() < MAX_BLOCKED) {
					columns.add((long) x << 32 | (z & 0xFFFFFFFFL));
				}
			} else {
				r.placed++;
			}
		});
		return r;
	}
}
