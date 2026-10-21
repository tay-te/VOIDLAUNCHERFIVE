package dev.voidmc.hearth.sim.roads;

import dev.voidmc.hearth.api.Blueprint;
import dev.voidmc.hearth.api.BlueprintService;
import dev.voidmc.hearth.api.HearthServices;
import dev.voidmc.hearth.sim.apply.AppliedLedger;
import dev.voidmc.hearth.sim.apply.ChunkApplier;
import dev.voidmc.hearth.sim.apply.ChunkLedgers;
import dev.voidmc.hearth.sim.apply.StepWriter;
import dev.voidmc.hearth.sim.atlas.Atlas;
import dev.voidmc.hearth.sim.settlement.Cursor;
import dev.voidmc.hearth.sim.world.SimWorld;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.LongAdder;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.storage.TagValueInput;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Writes roads into loaded chunks (docs/LIVING_WORLD_2.md §1.2, risk 1: roads in worlds that already exist): historic
 * roads over land generated before they were, and live roads as they are built, a budgeted amount a tick (a third of
 * the construction applier's by default). Every plot goes through {@link RoadWriter}'s edit safety and its first look:
 * a plot that would land on a player's work is refused (recorded in the road book; the road has a gap there), one over
 * a live settlement's planned ground waits (the town's own plot comes first), and natural trees on its corridor come
 * down whole. Server thread only.
 */
public final class RoadApplier {
	private static final Logger LOG = LoggerFactory.getLogger("hearth");
	private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS | Block.UPDATE_SKIP_ON_PLACE;
	private static final int WAIT_TICKS = 10;

	private final SimWorld world;
	private final RoadWorld net;
	private final LiveRoads live;
	private final ServerLevel level;
	private final LongLinkedOpenHashSet queue = new LongLinkedOpenHashSet();
	private final LongLinkedOpenHashSet waiting = new LongLinkedOpenHashSet();
	private final Long2IntOpenHashMap missing = new Long2IntOpenHashMap();
	private int ticks;

	public final LongAdder blocks = new LongAdder();
	public final LongAdder felled = new LongAdder();
	public final LongAdder chunks = new LongAdder();
	public final LongAdder refusals = new LongAdder();
	public final LongAdder nanos = new LongAdder();
	public final java.util.concurrent.atomic.AtomicLong maxNanos = new java.util.concurrent.atomic.AtomicLong();

	public RoadApplier(SimWorld world, RoadWorld net, LiveRoads live) {
		this.world = world;
		this.net = net;
		this.live = live;
		this.level = world.level();
		live.onProgress(this::enqueue);
	}

	/** A chunk finished loading: if roads owe it blocks, it is queued (its region's roads worked out first, off-thread). */
	public void onChunkLoad(LevelChunk chunk) {
		if (!this.net.enabled()) {
			return;
		}
		ChunkPos pos = chunk.getPos();
		long key = pos.pack();
		if (this.net.live().byChunk().containsKey(key)) {
			this.queue.add(key);
		}
		if (!this.live.historicLive()) {
			return;
		}
		int rx = pos.x() >> (Atlas.REGION_BITS - 4);
		int rz = pos.z() >> (Atlas.REGION_BITS - 4);
		RoadWorld.Region r = this.net.regionIfReady(rx, rz, false);
		if (r != null) {
			if (owes(chunk, r)) {
				this.queue.add(key);
			}
			return;
		}
		this.net.regionAsync(rx, rz).whenComplete((region, t) -> this.level.getServer().execute(() -> {
			if (t == null && region != null) {
				LevelChunk now = this.level.getChunkSource().getChunkNow(pos.x(), pos.z());
				if (now != null && owes(now, region)) {
					this.queue.add(key);
				}
			}
		}));
	}

	/** Whether a historic road built in history is missing from a chunk (land generated before it). */
	private boolean owes(LevelChunk chunk, RoadWorld.Region r) {
		long[] refs = r.byChunk().get(chunk.getPos().pack());
		if (refs == null) {
			return false;
		}
		AppliedLedger ledger = ChunkLedgers.of(chunk);
		for (long ref : refs) {
			Way w = r.ways().get((int) (ref >>> 32));
			if (w.edge().historic() && !ledger.has(RoadWorld.ledgerId(w.id()), (int) ref)) {
				return true;
			}
		}
		return false;
	}

	/** A plot of a live road can be written further: its loaded chunks are queued. */
	public void enqueue(Way w, int index) {
		RoadSegments.Segment s = w.segments().get(index);
		for (int cx = s.minX() >> 4; cx <= s.maxX() >> 4; cx++) {
			for (int cz = s.minZ() >> 4; cz <= s.maxZ() >> 4; cz++) {
				if (this.level.getChunkSource().getChunkNow(cx, cz) != null) {
					this.queue.add(ChunkPos.pack(cx, cz));
				}
			}
		}
	}

	public int pending() {
		return this.queue.size() + this.waiting.size();
	}

	/** Works through the queue until the time or block budget is spent. */
	public void tick() {
		if (++this.ticks % WAIT_TICKS == 0 && !this.waiting.isEmpty()) {
			this.queue.addAll(this.waiting);
			this.waiting.clear();
		}
		if (this.queue.isEmpty()) {
			return;
		}
		RoadsConfig.Roads cfg = this.net.config().roads();
		long budget = (long) (cfg.writeBudgetMs() * 1e6);
		int maxBlocks = cfg.writeMaxBlocks();
		long start = System.nanoTime();
		int placed = 0;
		while (!this.queue.isEmpty() && System.nanoTime() - start < budget && placed < maxBlocks) {
			long key = this.queue.removeFirstLong();
			LevelChunk chunk = this.level.getChunkSource().getChunkNow(ChunkPos.getX(key), ChunkPos.getZ(key));
			if (chunk == null) {
				int tries = this.missing.addTo(key, 1);
				if (tries < 6) {
					this.waiting.add(key);
				} else {
					this.missing.remove(key);
				}
				continue;
			}
			this.missing.remove(key);
			int done;
			try {
				done = apply(chunk, start, budget, maxBlocks - placed);
			} catch (RuntimeException e) {
				LOG.error("hearth roads: writing roads into chunk {},{} failed", ChunkPos.getX(key), ChunkPos.getZ(key), e);
				continue;
			}
			if (done < 0) {
				this.queue.addAndMoveToFirst(key);
				placed += -done;
				break;
			}
			placed += done;
		}
		long took = System.nanoTime() - start;
		this.nanos.add(took);
		this.maxNanos.accumulateAndGet(took, Math::max);
	}

	/** The road plots owed to a chunk, in order along each road. */
	List<RoadWriter.Job> jobs(LevelChunk chunk) {
		long key = chunk.getPos().pack();
		List<RoadWriter.Job> out = new ArrayList<>();
		RoadBook book = this.live.book();
		if (this.live.historicLive()) {
			int rx = chunk.getPos().x() >> (Atlas.REGION_BITS - 4);
			int rz = chunk.getPos().z() >> (Atlas.REGION_BITS - 4);
			RoadWorld.Region r = this.net.regionIfReady(rx, rz, false);
			long[] refs = r == null ? null : r.byChunk().get(key);
			if (refs != null) {
				for (long ref : refs) {
					Way w = r.ways().get((int) (ref >>> 32));
					int i = (int) ref;
					if (w.edge().historic() && !book.refused(w.id(), i)) {
						out.add(new RoadWriter.Job(w, i, Long.MAX_VALUE));
					}
				}
			}
		}
		long[] ids = this.net.live().byChunk().get(key);
		if (ids != null) {
			for (long id : ids) {
				Way w = this.net.live().ways().get(id);
				RoadBook.LiveRoad lr = book.road(id);
				if (w == null || lr == null) {
					continue;
				}
				for (int i = 0; i < w.segments().size(); i++) {
					if (!w.segments().get(i).touchesChunk(chunk.getPos().x(), chunk.getPos().z()) || book.refused(id, i)) {
						continue;
					}
					long target = target(lr, w, i);
					if (target > Cursor.ZERO) {
						out.add(new RoadWriter.Job(w, i, target));
					}
				}
			}
		}
		out.sort(Comparator.comparingLong((RoadWriter.Job j) -> j.way().id()).thenComparingInt(RoadWriter.Job::index));
		return out;
	}

	/** How far a live road's plot may be written: all of it once built, as far as its work goes at the head. */
	private long target(RoadBook.LiveRoad r, Way w, int i) {
		if (r.built(i)) {
			return Long.MAX_VALUE;
		}
		boolean headA = r.head(true) == i;
		boolean headB = r.head(false) == i;
		if (!headA && !headB) {
			return Cursor.ZERO;
		}
		double f = Math.max(headA ? this.live.headProgress(r, true) : 0, headB ? this.live.headProgress(r, false) : 0);
		if (f <= 0) {
			return Cursor.ZERO;
		}
		BlueprintService bps = HearthServices.blueprints();
		RoadSegments.Segment s = w.segments().get(i);
		if (!bps.prepare(s.spec(), this.world.terrain())) {
			return Cursor.ZERO;
		}
		Blueprint bp = bps.blueprint(s.spec(), this.world.terrain());
		return fraction(bp, f);
	}

	/** The cursor {@code f} of the way through a blueprint's steps. */
	static long fraction(Blueprint bp, double f) {
		int total = 0;
		for (int st = 0; st < bp.stageCount(); st++) {
			total += bp.stepCount(st);
		}
		int want = (int) Math.floor(total * Math.min(1, f));
		for (int st = 0; st < bp.stageCount(); st++) {
			int n = bp.stepCount(st);
			if (want < n) {
				return Cursor.pack(st, want);
			}
			want -= n;
		}
		return Cursor.pack(bp.stageCount(), 0);
	}

	/** Returns blocks placed, negated if the chunk still has work left. */
	private int apply(LevelChunk chunk, long start, long budget, int maxBlocks) {
		List<RoadWriter.Job> jobs = jobs(chunk);
		if (jobs.isEmpty()) {
			return 0;
		}
		BlueprintService bps = HearthServices.blueprints();
		boolean ready = true;
		List<RoadWriter.Job> due = new ArrayList<>(jobs.size());
		AppliedLedger ledger = ChunkLedgers.of(chunk);
		for (RoadWriter.Job j : jobs) {
			if (ledger.get(RoadWorld.ledgerId(j.way().id()), j.index()) >= j.target()) {
				continue;
			}
			// the blueprints are made in the background (the build module's makers), not on the tick
			ready &= bps.prepare(j.segment().spec(), this.world.terrain());
			due.add(j);
		}
		if (due.isEmpty()) {
			return 0;
		}
		if (!ready) {
			this.waiting.add(chunk.getPos().pack());
			return 0;
		}
		// a live settlement's planned ground comes first: a road plot over it waits (and is looked at again later)
		List<RoadWriter.Job> clear = new ArrayList<>(due.size());
		for (RoadWriter.Job j : due) {
			if (ledger.get(RoadWorld.ledgerId(j.way().id()), j.index()) > Cursor.ZERO || !overLivePlots(j.segment())) {
				clear.add(j);
			}
		}
		Blocks world = new Blocks(chunk);
		ChunkPos pos = chunk.getPos();
		RoadWriter.Outcome o = RoadWriter.write(pos.x(), pos.z(), ledger, clear, world, bps, this.world.terrain(), true, j -> {
			int n = ChunkApplier.clearCanopy(chunk, j.segment().spec(), world);
			this.felled.add(n);
			return n;
		}, start, budget, maxBlocks);
		for (int k = 0; k < o.refused.size(); k++) {
			RoadWriter.Job j = clear.get(o.refused.getInt(k));
			this.live.book().refuse(j.way().id(), j.index());
			this.world.markDirty();
			this.refusals.increment();
			LOG.info("hearth roads: the road from {} to {} stops at someone's work near {},{} (plot {} refused)", j.way().nameA(), j.way().nameB(),
				pos.getMiddleBlockX(), pos.getMiddleBlockZ(), j.index());
		}
		if (o.ledger != ledger) {
			ChunkLedgers.store(chunk, o.ledger);
			this.chunks.increment();
		}
		this.blocks.add(o.placed);
		return o.unfinished ? -Math.max(1, o.placed) : o.placed;
	}

	/** Whether a road plot's corridor runs over a live settlement's planned plots (sampled every other column). */
	private boolean overLivePlots(RoadSegments.Segment s) {
		int[] pts = s.spec().params().getIntArray("points").orElse(new int[0]);
		boolean[] hit = {false};
		RoadSegments.corridor(pts, s.spec().params().getIntOr("width", 3), (x, z) -> {
			if (!hit[0] && ((x ^ z) & 1) == 0 && this.world.spatial().isPlanned(x, z)) {
				hit[0] = true;
			}
		});
		return hit[0];
	}

	public String describe() {
		return String.format(Locale.ROOT, "road writing: %d blocks in %d chunk passes, %d natural tree blocks felled, %d plots refused, %d chunks waiting, %.2f ms max a tick",
			this.blocks.sum(), this.chunks.sum(), this.felled.sum(), this.refusals.sum(), pending(), this.maxNanos.get() / 1e6);
	}

	/** One loaded chunk as the writer sees it (reads and writes never leave it). */
	private final class Blocks implements StepWriter.Blocks {
		private final LevelChunk chunk;
		private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

		Blocks(LevelChunk chunk) {
			this.chunk = chunk;
		}

		@Override
		public BlockState get(int x, int y, int z) {
			return this.chunk.getBlockState(this.pos.set(x, y, z));
		}

		@Override
		public void set(int x, int y, int z, BlockState state, @Nullable CompoundTag tag) {
			BlockPos at = new BlockPos(x, y, z);
			RoadApplier.this.level.setBlock(at, state, FLAGS);
			if (tag != null) {
				BlockEntity be = RoadApplier.this.level.getBlockEntity(at);
				if (be != null) {
					be.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, RoadApplier.this.level.registryAccess(), tag));
					this.chunk.markUnsaved();
				}
			}
		}

		@Override
		public boolean naturalTree(int x, int y, int z) {
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
