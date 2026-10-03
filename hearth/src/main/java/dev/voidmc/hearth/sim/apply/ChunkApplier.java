package dev.voidmc.hearth.sim.apply;

import dev.voidmc.hearth.api.Blueprint;
import dev.voidmc.hearth.api.BlueprintService;
import dev.voidmc.hearth.api.PlotSpec;
import dev.voidmc.hearth.api.PlotState;
import dev.voidmc.hearth.api.TerrainProbe;
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
	private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS;

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
	public final LongAdder chunksApplied = new LongAdder();
	public final LongAdder nanos = new LongAdder();

	private final ServerLevel level;
	private final PlotIndex index;
	private final BlueprintService blueprints;
	private final TerrainProbe terrain;
	private final LongLinkedOpenHashSet queue = new LongLinkedOpenHashSet();

	public ChunkApplier(ServerLevel level, PlotIndex index, BlueprintService blueprints, TerrainProbe terrain) {
		this.level = level;
		this.index = index;
		this.blueprints = blueprints;
		this.terrain = terrain;
	}

	public void enqueue(long chunk) {
		this.queue.add(chunk);
	}

	public int pending() {
		return this.queue.size();
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
		if (this.queue.isEmpty()) {
			return;
		}
		long start = System.nanoTime();
		int blocks = 0;
		while (!this.queue.isEmpty() && System.nanoTime() - start < budgetNanos && blocks < maxBlocks) {
			long key = this.queue.removeFirstLong();
			LevelChunk chunk = this.level.getChunkSource().getChunkNow(ChunkPos.getX(key), ChunkPos.getZ(key));
			if (chunk == null) {
				// it will be queued again when it loads
				continue;
			}
			int done = applyChunk(chunk, key, start, budgetNanos, maxBlocks - blocks);
			if (done < 0) {
				// ran out of budget part way: finish it first next tick
				this.queue.addAndMoveToFirst(key);
				blocks += -done;
				break;
			}
			blocks += done;
		}
		this.nanos.add(System.nanoTime() - start);
	}

	/** Returns blocks placed, negated if the chunk still has work left. */
	private int applyChunk(LevelChunk chunk, long key, long start, long budgetNanos, int blockBudget) {
		int cx = ChunkPos.getX(key);
		int cz = ChunkPos.getZ(key);
		AppliedLedger ledger = ChunkLedgers.of(chunk);
		AppliedLedger before = ledger;
		int placed = 0;
		boolean unfinished = false;
		List<PlotRef> refs = this.index.refs(key);
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
			} else if (p.replaces() != SimPlot.NONE && (p.state() == PlotState.BUILDING || p.state() == PlotState.BUILT)) {
				oldId = p.replaces();
				SimPlot standing = s.plot(oldId);
				if (standing != null && standing.state() != PlotState.DEMOLISHING) {
					continue;
				}
				oldSpec = standing != null ? standing.spec() : p.replacedSpec();
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
			StepWriter.Owned owned = owned(s, p, bp, cx, cz, cur, ledger);
			StepWriter.Result r = StepWriter.write(bp, cx, cz, cur, target, new World(chunk), owned);
			ledger = ledger.with(s.id(), p.id(), target);
			placed += r.placed;
			this.blocksPlaced.add(r.placed);
			this.conflicts.add(r.conflicts);
			if (r.conflicts > 16 && r.conflicts * 4 > r.attempted() && p.isActive()) {
				// the player has built over this plot: give it up, the planner will choose other ground
				LOG.info("hearth: settlement {} gives up plot {} ({}): {} of {} blocks built over", s.name(), p.id(), p.spec().archetype(), r.conflicts,
					r.attempted());
				s.abandonPlot(p);
			}
		}
		if (ledger != before) {
			ChunkLedgers.store(chunk, ledger);
			this.chunksApplied.increment();
		}
		return unfinished ? -Math.max(1, placed) : placed;
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

	/** What this plot (and the plot it rebuilds) already put in this chunk, read lazily. */
	private StepWriter.Owned owned(SimSettlement s, SimPlot p, Blueprint bp, int cx, int cz, long cur, AppliedLedger ledger) {
		return StepWriter.lazyOwned(sink -> {
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
					be.setChanged();
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
