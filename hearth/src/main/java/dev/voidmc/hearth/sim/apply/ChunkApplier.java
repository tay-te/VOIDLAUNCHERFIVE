package dev.voidmc.hearth.sim.apply;

import dev.voidmc.hearth.api.Blueprint;
import dev.voidmc.hearth.api.BlueprintService;
import dev.voidmc.hearth.api.PlotSpec;
import dev.voidmc.hearth.api.PlotState;
import dev.voidmc.hearth.api.TerrainProbe;
import dev.voidmc.hearth.sim.atlas.PlotColumns;
import dev.voidmc.hearth.sim.settlement.Extraction;
import dev.voidmc.hearth.sim.settlement.Cursor;
import dev.voidmc.hearth.sim.settlement.SimPlot;
import dev.voidmc.hearth.sim.settlement.SimSettlement;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import java.util.List;
import java.util.concurrent.atomic.LongAdder;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.tags.BlockTags;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Writes recorded construction into chunks as they are loaded, a budgeted amount per tick. A chunk is queued when it
 * loads with plots in it, and again whenever a plot over it may be written further (a stage finished abstractly; each
 * step in an embodied settlement). For each plot it compares the chunk's {@link AppliedLedger} with how far the plot may
 * be written ({@link SimSettlement#applicable}) and writes the difference, safely ({@link StepWriter}), without
 * neighbour updates — the build module resolved shapes already.
 */
public final class ChunkApplier {
	private static final Logger LOG = LoggerFactory.getLogger("hearth");
	/**
	 * No neighbour updates and no {@code onPlace} (shapes are resolved; a placed block reacting to its neighbours would
	 * read the next chunk, which may not be loaded: a carved pumpkin looking for a golem's body crashed the server, a rail
	 * and water read and flowed into it).
	 */
	private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS | Block.UPDATE_SKIP_ON_PLACE;

	/** A plot over a chunk. */
	public record PlotRef(SimSettlement settlement, int plotId) {
	}

	/** Which plots touch a chunk (the world's spatial index). */
	public interface PlotIndex {
		List<PlotRef> refs(long chunk);
	}

	public final LongAdder blocksPlaced = new LongAdder();
	/** Blocks of buildings taken down for their rebuilds. */
	public final LongAdder blocksCleared = new LongAdder();
	public final LongAdder conflicts = new LongAdder();
	/** Leaves and branches of natural trees cleared from over plots as their first stage went in. */
	public final LongAdder canopyCleared = new LongAdder();
	public final LongAdder chunksApplied = new LongAdder();
	public final LongAdder nanos = new LongAdder();
	/** The longest one tick's writing took. */
	public final java.util.concurrent.atomic.AtomicLong maxTickNanos = new java.util.concurrent.atomic.AtomicLong();

	private final ServerLevel level;
	private final PlotIndex index;
	private final BlueprintService blueprints;
	private final TerrainProbe terrain;
	private final LongLinkedOpenHashSet queue = new LongLinkedOpenHashSet();
	/** Chunks waiting for blueprints being made in the background; queued again every {@link #WAIT_TICKS} ticks. */
	private final LongLinkedOpenHashSet waiting = new LongLinkedOpenHashSet();
	private static final int WAIT_TICKS = 10;
	/** How often a queued chunk that is not there is looked for again (every {@link #WAIT_TICKS} ticks). */
	private static final int MISSING_TRIES = 6;
	private final it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap missing = new it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap();
	private final java.util.function.BooleanSupplier defer;
	private int ticks;
	/** The slowest single chunk pass so far, described. */
	public String slowest = "none";
	private long slowestNanos;
	/** Chunk passes that threw (and were skipped). */
	public final LongAdder failures = new LongAdder();
	/** Chunk passes put off because a blueprint they needed was still being made. */
	public final LongAdder deferred = new LongAdder();

	public ChunkApplier(ServerLevel level, PlotIndex index, BlueprintService blueprints, TerrainProbe terrain) {
		this(level, index, blueprints, terrain, () -> false);
	}

	/**
	 * {@code defer}: whether a chunk whose blueprints are not made yet waits for the build module's background makers
	 * ({@link BlueprintService#prepare}) instead of the tick making them (a castle takes up to a second).
	 */
	public ChunkApplier(ServerLevel level, PlotIndex index, BlueprintService blueprints, TerrainProbe terrain, java.util.function.BooleanSupplier defer) {
		this.level = level;
		this.index = index;
		this.blueprints = blueprints;
		this.terrain = terrain;
		this.defer = defer;
	}

	public void enqueue(long chunk) {
		this.queue.add(chunk);
	}

	public int pending() {
		return this.queue.size() + this.waiting.size();
	}

	/** Queues every loaded chunk a plot touches. */
	public void enqueuePlot(SimPlot p) {
		var reach = p.reach();
		for (int cx = reach.minX() >> 4; cx <= reach.maxX() >> 4; cx++) {
			for (int cz = reach.minZ() >> 4; cz <= reach.maxZ() >> 4; cz++) {
				if (this.level.getChunkSource().getChunkNow(cx, cz) != null) {
					this.queue.add(ChunkPos.pack(cx, cz));
				}
			}
		}
	}

	/**
	 * An embodied builder placed steps {@code [from, to)} itself: every loaded chunk that had all of the plot before
	 * {@code from} now has all of it before {@code to}. Chunks still behind keep their gap for the applier.
	 */
	public void placedByEntity(SimSettlement s, SimPlot p, long from, long to) {
		var reach = p.reach();
		for (int cx = reach.minX() >> 4; cx <= reach.maxX() >> 4; cx++) {
			for (int cz = reach.minZ() >> 4; cz <= reach.maxZ() >> 4; cz++) {
				LevelChunk chunk = this.level.getChunkSource().getChunkNow(cx, cz);
				if (chunk == null) {
					continue;
				}
				AppliedLedger ledger = ChunkLedgers.of(chunk);
				if (ledger.get(s.id(), p.id()) >= from) {
					ChunkLedgers.store(chunk, ledger.with(s.id(), p.id(), to));
				} else {
					this.queue.add(ChunkPos.pack(cx, cz));
				}
			}
		}
	}

	/** Works through the queue until the time or block budget is spent. */
	public void tick(long budgetNanos, int maxBlocks) {
		if (++this.ticks % WAIT_TICKS == 0 && !this.waiting.isEmpty()) {
			this.queue.addAll(this.waiting);
			this.waiting.clear();
		}
		if (this.queue.isEmpty()) {
			return;
		}
		long start = System.nanoTime();
		int blocks = 0;
		while (!this.queue.isEmpty() && System.nanoTime() - start < budgetNanos && blocks < maxBlocks) {
			long key = this.queue.removeFirstLong();
			LevelChunk chunk = this.level.getChunkSource().getChunkNow(ChunkPos.getX(key), ChunkPos.getZ(key));
			if (chunk == null) {
				// not reachable yet (a chunk's load event comes before getChunkNow finds it) or unloaded: looked for again a
				// few times, and queued again when it loads or becomes reachable
				int tries = this.missing.addTo(key, 1);
				if (tries < MISSING_TRIES) {
					this.waiting.add(key);
				} else {
					this.missing.remove(key);
				}
				continue;
			}
			this.missing.remove(key);
			int done;
			long passStart = System.nanoTime();
			try {
				done = applyChunk(chunk, key, start, budgetNanos, maxBlocks - blocks);
				long pass = System.nanoTime() - passStart;
				if (pass > this.slowestNanos) {
					// (one chunk pass cannot be cut short inside a stage: this says which chunk and how much, for /hearth sim stats)
					this.slowestNanos = pass;
					this.slowest = String.format(java.util.Locale.ROOT, "chunk %d,%d: %.1f ms, %d plots, %d blocks", ChunkPos.getX(key), ChunkPos.getZ(key),
						pass / 1e6, this.index.refs(key).size(), Math.abs(done));
				}
			} catch (RuntimeException e) {
				// a blueprint or block entity that will not write: this chunk waits for its next load or the plot's next
				// progress, the rest of the queue goes on (blocks written before the failure are found in place next time)
				this.failures.increment();
				if (this.failures.sum() == 1 || Long.bitCount(this.failures.sum()) == 1) {
					LOG.error("hearth: writing construction into chunk {},{} failed ({} so far)", ChunkPos.getX(key), ChunkPos.getZ(key), this.failures.sum(), e);
				}
				continue;
			}
			if (done < 0) {
				// ran out of budget part way: finish it first next tick
				this.queue.addAndMoveToFirst(key);
				blocks += -done;
				break;
			}
			blocks += done;
		}
		long took = System.nanoTime() - start;
		this.nanos.add(took);
		this.maxTickNanos.accumulateAndGet(took, Math::max);
	}

	/** Returns blocks placed, negated if the chunk still has work left. */
	private int applyChunk(LevelChunk chunk, long key, long start, long budgetNanos, int blockBudget) {
		int cx = ChunkPos.getX(key);
		int cz = ChunkPos.getZ(key);
		AppliedLedger ledger = ChunkLedgers.of(chunk);
		AppliedLedger before = ledger;
		int placed = 0;
		boolean unfinished = false;
		// a copy: giving up a plot fires PLOT_ABANDONED, whose listeners may change the settlement's plots and so the index
		List<PlotRef> refs = List.copyOf(this.index.refs(key));
		if (this.defer.getAsBoolean() && !ready(refs, ledger)) {
			// every blueprint this chunk may need is being made in the background: write it once they are all ready
			this.waiting.add(key);
			this.deferred.increment();
			return 0;
		}
		// rebuilds first: what stands of a building coming down is cleared before its replacement's blocks go in
		for (PlotRef ref : refs) {
			SimSettlement s = ref.settlement();
			SimPlot p = s.plot(ref.plotId());
			if (p == null) {
				continue;
			}
			int oldId;
			PlotSpec oldSpec;
			if (p.state() == PlotState.DEMOLISHING) {
				oldId = p.id();
				oldSpec = p.spec();
			} else if (p.replaces() != SimPlot.NONE && (p.state() == PlotState.BUILDING || p.state() == PlotState.BUILT || p.state() == PlotState.ABANDONED)) {
				// (an abandoned plot replacing another is a building weathered in place, SimSettlement#weather: what stood of
				// it comes down where it is still as built, and the weathered drawing goes in)
				oldId = p.replaces();
				SimPlot standing = s.plot(oldId);
				if (standing != null && standing.state() != PlotState.DEMOLISHING) {
					continue;
				}
				oldSpec = standing != null ? standing.spec() : p.replacedSpec();
				if (SimSettlement.inPlace(oldSpec, p.spec())) {
					// changed in place (an addition, time's decay): nothing comes down, the new drawing is written over what
					// stands, which counts as the plot's own (owned) until it is done
					continue;
				}
			} else {
				continue;
			}
			long cur = ledger.get(s.id(), oldId);
			if (cur <= Cursor.ZERO || oldSpec == null) {
				continue;
			}
			try {
				int cleared = demolish(this.blueprints.blueprint(oldSpec, this.terrain), oldSpec, cx, cz, cur, new World(chunk));
				placed += cleared;
				this.blocksCleared.add(cleared);
			} catch (RuntimeException e) {
				// its archetype is gone: nothing of it can be told apart from the land, leave it
			}
			ledger = ledger.with(s.id(), oldId, Cursor.ZERO);
		}
		for (PlotRef ref : refs) {
			SimSettlement s = ref.settlement();
			SimPlot p = s.plot(ref.plotId());
			if (p == null) {
				continue;
			}
			long target = s.applicable(p);
			long cur = ledger.get(s.id(), p.id());
			if (cur >= target) {
				continue;
			}
			if (placed > 0 && (placed >= blockBudget || System.nanoTime() - start >= budgetNanos)) {
				unfinished = true;
				break;
			}
			Blueprint bp;
			try {
				bp = this.blueprints.blueprint(p.spec(), this.terrain);
			} catch (RuntimeException e) {
				// an unknown archetype or a generator bug: leave this plot, write the others
				continue;
			}
			if (cur <= Cursor.ZERO) {
				// the plot's first blocks here: the crowns of trees rooted beside it come off its ground first
				int felled = clearCanopy(chunk, s, p);
				placed += felled;
				this.canopyCleared.add(felled);
			}
			StepWriter.Owned owned = owned(s, p, bp, cx, cz, cur, ledger, refs);
			// a stage at a time, the budget looked at between them: a building whose every stage is due (a town built while
			// nobody was near) does not take a tick on its own; the ledger records how far it got
			StepWriter.Result r = new StepWriter.Result();
			World world = new World(chunk);
			long reached = cur;
			while (reached < target) {
				long next = Math.min(target, Cursor.pack(Cursor.stage(reached) + 1, 0));
				StepWriter.Result part = StepWriter.write(bp, cx, cz, reached, next, world, owned);
				r.placed += part.placed;
				r.same += part.same;
				r.conflicts += part.conflicts;
				reached = next;
				if (reached < target && (placed + r.placed >= blockBudget || System.nanoTime() - start >= budgetNanos)) {
					unfinished = true;
					break;
				}
			}
			ledger = ledger.with(s.id(), p.id(), reached);
			placed += r.placed;
			this.blocksPlaced.add(r.placed);
			this.conflicts.add(r.conflicts);
			if (r.conflicts > 16 && r.conflicts * 4 > r.attempted() && p.isActive()) {
				// the player has built over this plot: give it up, the planner will choose other ground
				LOG.info("hearth: settlement {} gives up plot {} ({}): {} of {} blocks built over", s.name(), p.id(), p.spec().archetype(), r.conflicts,
					r.attempted());
				s.abandonPlot(p);
			}
			if (unfinished) {
				break;
			}
		}
		if (ledger != before) {
			ChunkLedgers.store(chunk, ledger);
			this.chunksApplied.increment();
		}
		return unfinished ? -Math.max(1, placed) : placed;
	}

	/** How far over a plot's floor the canopy is looked for (the tallest crowns, Expanse's redwoods aside). */
	private static final int CANOPY_HEIGHT = 40;

	/**
	 * Clears the overhang of natural trees from a plot's columns in this chunk, as settlers lop the branches over a new
	 * square or lane (requests/w5-tour.md §5): non-persistent leaves, and the logs among them that belong to a natural
	 * tree, from the top of each column down to the plot's floor, stopping at the first block that is neither (a roof, a
	 * player's work, the ground). A tree standing on the plot itself goes the same way, its trunk down to the foot; one
	 * beside it keeps its trunk and the rest of its crown. Excavations (a woodlot fells its own trees) and plots down in a realm are left alone. Cheap:
	 * the heightmap gives each column's top, and most columns hold nothing over the ground.
	 */
	int clearCanopy(LevelChunk chunk, SimSettlement s, SimPlot p) {
		PlotSpec spec = p.spec();
		if (Extraction.is(s, p) || PlotColumns.underground(spec)) {
			return 0;
		}
		int minX = chunk.getPos().getMinBlockX();
		int minZ = chunk.getPos().getMinBlockZ();
		int floor = spec.floorY();
		World world = new World(chunk);
		// found first, then cleared: a log is told to be a tree's by the leaves round it, which must still be there
		it.unimi.dsi.fastutil.longs.LongArrayList found = new it.unimi.dsi.fastutil.longs.LongArrayList();
		long[] done = new long[4];
		PlotColumns.forEach(spec, 0, (x, z) -> {
			if (x < minX || x > minX + 15 || z < minZ || z > minZ + 15) {
				return;
			}
			int bit = (z & 15) << 4 | x & 15;
			if ((done[bit >> 6] >>> (bit & 63) & 1L) != 0) {
				return;
			}
			done[bit >> 6] |= 1L << (bit & 63);
			int top = Math.min(chunk.getHeight(Heightmap.Types.MOTION_BLOCKING, x & 15, z & 15), floor + CANOPY_HEIGHT);
			boolean trunk = false;
			for (int y = top; y > chunk.getMinY(); y--) {
				BlockState st = world.get(x, y, z);
				if (st.is(BlockTags.LOGS) && (trunk || world.naturalTree(x, y, z))) {
					// a trunk standing on the plot goes down to its foot, under the floor too; a branch over it is lopped
					// and the leaves under it go on
					trunk = world.get(x, y - 1, z).is(BlockTags.LOGS);
					found.add(BlockPos.asLong(x, y, z));
					continue;
				}
				if (trunk || y <= floor) {
					break;
				}
				if (st.isAir() || st.canBeReplaced()) {
					continue;
				}
				if (!(st.is(BlockTags.LEAVES) && st.hasProperty(LeavesBlock.PERSISTENT) && !st.getValue(LeavesBlock.PERSISTENT))) {
					break;
				}
				found.add(BlockPos.asLong(x, y, z));
			}
		});
		BlockState air = net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
		for (int i = 0; i < found.size(); i++) {
			long at = found.getLong(i);
			world.set(BlockPos.getX(at), BlockPos.getY(at), BlockPos.getZ(at), air, null);
		}
		return found.size();
	}

	/**
	 * Whether every blueprint writing this chunk may need is made (the plots over it, the buildings they replace, and for a
	 * building changed in place with work to write, what it stood as before), starting those that are not. Asks for all of
	 * them, so they are made together.
	 */
	private boolean ready(List<PlotRef> refs, AppliedLedger ledger) {
		boolean ready = true;
		for (PlotRef ref : refs) {
			SimSettlement s = ref.settlement();
			SimPlot p = s.plot(ref.plotId());
			if (p == null) {
				continue;
			}
			if (!s.blueprintsReady(p)) {
				ready = false;
				continue;
			}
			if (p.replaces() != SimPlot.NONE || p.replacedSpec() != null) {
				PlotSpec old = s.replacedSpec(p);
				if (SimSettlement.inPlace(old, p.spec()) && ledger.get(s.id(), p.id()) < s.applicable(p)) {
					for (PlotSpec earlier : SimSettlement.earlierSelves(p.spec())) {
						ready &= this.blueprints.prepare(earlier, this.terrain);
					}
				}
			}
		}
		return ready;
	}

	/**
	 * Takes down what a plot put in this chunk ({@code [0, cursor)}): every block above its floor that is still exactly as
	 * it was placed becomes air (containers drop what they hold). Foundations and earthwork below the floor stay for the
	 * rebuild to stand on; anything a player changed stays too.
	 */
	static int demolish(Blueprint bp, PlotSpec spec, int cx, int cz, long cursor, StepWriter.Blocks world) {
		java.util.Map<Long, BlockState> last = new java.util.LinkedHashMap<>();
		StepWriter.forApplied(bp, cx, cz, cursor, (x, y, z, state, tag) -> {
			if (y >= spec.floorY()) {
				last.put(BlockPos.asLong(x, y, z), state);
			}
		});
		int n = 0;
		// top down, so nothing is left hanging for a tick
		List<Long> order = new java.util.ArrayList<>(last.keySet());
		order.sort((a, b) -> Integer.compare(BlockPos.getY(b), BlockPos.getY(a)));
		BlockState air = net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
		for (long at : order) {
			BlockState placed = last.get(at);
			int x = BlockPos.getX(at);
			int y = BlockPos.getY(at);
			int z = BlockPos.getZ(at);
			if (!placed.isAir() && world.get(x, y, z) == placed) {
				world.set(x, y, z, air, null);
				n++;
			}
		}
		return n;
	}

	/**
	 * What this plot (and the plot it rebuilds) already put in this chunk, read lazily — and what the settlement's other
	 * plots here put there: the planner overlaps plots on purpose (a building on its terrace, furniture in the plaza, a
	 * gatehouse over its road; ARCHITECTURE.md §5.3), and the applier writes both.
	 */
	private StepWriter.Owned owned(SimSettlement s, SimPlot p, Blueprint bp, int cx, int cz, long cur, AppliedLedger ledger, List<PlotRef> refs) {
		return StepWriter.lazyOwned(sink -> {
			for (PlotRef ref : refs) {
				if (ref.settlement() != s || ref.plotId() == p.id() || ref.plotId() == p.replaces()) {
					continue;
				}
				SimPlot other = s.plot(ref.plotId());
				long otherCursor = ledger.get(s.id(), ref.plotId());
				if (other == null || otherCursor <= Cursor.ZERO) {
					continue;
				}
				try {
					StepWriter.forApplied(this.blueprints.blueprint(other.spec(), this.terrain), cx, cz, otherCursor, sink);
				} catch (RuntimeException ignored) {
					// its archetype is gone
				}
			}
			int old = p.replaces();
			if (old != SimPlot.NONE) {
				SimPlot standing = s.plot(old);
				PlotSpec spec = standing != null ? standing.spec() : p.replacedSpec();
				long oldCursor = ledger.get(s.id(), old);
				if (spec != null && oldCursor > Cursor.ZERO) {
					try {
						StepWriter.forApplied(this.blueprints.blueprint(spec, this.terrain), cx, cz, oldCursor, sink);
					} catch (IllegalArgumentException ignored) {
						// its archetype is gone; nothing of it counts as ours
					}
				}
				if (SimSettlement.inPlace(spec, p.spec())) {
					// a building changed in place may stand here as any of its earlier selves (a chunk unloaded while it grew
					// twice, or weathered a step further): what each of them put here is ours too
					for (PlotSpec earlier : SimSettlement.earlierSelves(p.spec())) {
						try {
							Blueprint e = this.blueprints.blueprint(earlier, this.terrain);
							StepWriter.forApplied(e, cx, cz, dev.voidmc.hearth.sim.settlement.Cursor.pack(e.stageCount(), 0), sink);
						} catch (RuntimeException ignored) {
							// nothing of it counts
						}
					}
				}
			}
			StepWriter.forApplied(bp, cx, cz, cur, sink);
		});
	}

	/** One loaded chunk as the writer sees it. Reads and writes never leave the chunk, so nothing else loads. */
	private final class World implements StepWriter.Blocks {
		private final LevelChunk chunk;
		private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

		World(LevelChunk chunk) {
			this.chunk = chunk;
		}

		@Override
		public BlockState get(int x, int y, int z) {
			return this.chunk.getBlockState(this.pos.set(x, y, z));
		}

		@Override
		public void set(int x, int y, int z, BlockState state, @Nullable CompoundTag tag) {
			BlockPos at = new BlockPos(x, y, z);
			ChunkApplier.this.level.setBlock(at, state, FLAGS);
			if (tag != null) {
				BlockEntity be = ChunkApplier.this.level.getBlockEntity(at);
				if (be != null) {
					be.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, ChunkApplier.this.level.registryAccess(), tag));
					// the chunk is to be saved; not be.setChanged(), whose comparator update reads the neighbours and would
					// load (or generate) the chunk next door in the tick when the block entity stands on the chunk's edge
					this.chunk.markUnsaved();
				}
			}
		}

		@Override
		public boolean naturalTree(int x, int y, int z) {
			// non-persistent leaves within a few blocks above and around (staying inside this chunk)
			int minX = this.chunk.getPos().getMinBlockX();
			int minZ = this.chunk.getPos().getMinBlockZ();
			for (int dy = 0; dy <= 12; dy++) {
				for (int dx = -2; dx <= 2; dx++) {
					for (int dz = -2; dz <= 2; dz++) {
						int bx = x + dx;
						int bz = z + dz;
						if (bx < minX || bx > minX + 15 || bz < minZ || bz > minZ + 15) {
							continue;
						}
						BlockState s = this.chunk.getBlockState(this.pos.set(bx, y + dy, bz));
						if (s.is(BlockTags.LEAVES) && s.hasProperty(LeavesBlock.PERSISTENT) && !s.getValue(LeavesBlock.PERSISTENT)) {
							return true;
						}
					}
				}
			}
			return false;
		}
	}
}
