package dev.voidmc.hearth.npc.work;

import dev.voidmc.hearth.api.Blueprint;
import dev.voidmc.hearth.api.PlotSpec;
import dev.voidmc.hearth.api.PlotState;
import dev.voidmc.hearth.api.PlotView;
import dev.voidmc.hearth.api.Settlement;
import java.util.BitSet;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * One building site as its crew shares it (docs/FOLK.md §6): which steps of the stage are still to do and which of
 * those may be placed now ({@link StepPlan}), the steps placed ahead of the counter, the site's scaffolding
 * ({@link Staging}), and where each builder stands, so that no two work the same stretch of wall. The progress counter
 * is moved on here, over the done steps from the cursor, in one {@code advanceConstruction} call a tick at most.
 *
 * <p>Kept in the town's {@code TownState} (not saved): after a restart a site is read again from the world, and its
 * scaffolding, which the ledger lists under {@link #ledgerId}, is cleared as an orphan and put up afresh.
 */
public final class BuildSite {
	/** Steps looked at ahead of the cursor, and steps still to do kept, per scan. */
	static final int SCAN_STEPS = 2048;
	static final int SCAN_PENDING = 640;
	/** Ticks between scans when nothing was placed; and at least this many between two scans. */
	private static final int SCAN_IDLE = 40;
	private static final int SCAN_MIN = 6;

	public final int plotId;
	public final UUID ledgerId;
	private int stage = -1;
	/** Steps of the current stage a builder placed (by index), so the counter passes them even if the world moved on. */
	private final BitSet placed = new BitSet();
	private StepPlan.@Nullable Scan scan;
	private long scannedAt = Long.MIN_VALUE;
	private long refreshedAt = Long.MIN_VALUE;
	private long resyncedAt = Long.MIN_VALUE;
	/** When the counter last moved, and when the sim was last told the crew is at it all the same. */
	private long advancedAt = Long.MIN_VALUE;
	private long heldAt = Long.MIN_VALUE;
	private boolean dirty = true;
	private long lastTouched;
	/** When the crew last put a block in place (a step or a scaffold): the sim is told they are at it only while they are. */
	private long activeAt = Long.MIN_VALUE;
	/** Pending positions (packed), from the last scan: nobody stands in them. */
	private final it.unimi.dsi.fastutil.longs.LongOpenHashSet pendingAt = new it.unimi.dsi.fastutil.longs.LongOpenHashSet();

	@Nullable Staging staging;
	/** The stage a ring could not be planned for (none is tried again until the stage moves on). */
	private int noStagingFor = -1;
	/** Where each builder works from (feet, packed), while they work there. */
	final Map<UUID, Long> spots = new HashMap<>();
	/** Builders who have arrived at their spot and work there (the others are on their way). */
	final java.util.Set<UUID> arrived = new java.util.HashSet<>();
	/** The stage someone has already gone to the store for. */
	private int fetchedFor = -1;
	/** Which column each raiser or dismantler has taken. */
	final Map<UUID, Integer> columns = new HashMap<>();
	/** Folk carrying materials for this site, until when (game time). */
	final Map<UUID, Long> carriers = new HashMap<>();

	public BuildSite(int plotId) {
		this.plotId = plotId;
		this.ledgerId = ledgerId(plotId);
	}

	/** The ledger id a site's scaffolding is listed under (one ledger per settlement, so the plot id is enough). */
	public static UUID ledgerId(int plotId) {
		return new UUID(0x5CAF_F01D_5175_E000L, plotId);
	}

	public long lastTouched() {
		return this.lastTouched;
	}

	public boolean hasScaffolding() {
		return this.staging != null && this.staging.standing();
	}

	int stage() {
		return this.stage;
	}

	StepPlan.@Nullable Scan scan() {
		return this.scan;
	}

	long scannedAt() {
		return this.scannedAt;
	}

	boolean pendingAt(long pos) {
		return this.pendingAt.contains(pos);
	}

	/**
	 * Brings the site up to date, once a tick whoever asks: a new stage starts afresh; the counter moves over the done
	 * steps from the cursor (one {@code advanceConstruction} for all of them); the stage is read again when something was
	 * placed (or every couple of seconds); the scaffolding is checked against the world now and then.
	 */
	void refresh(ServerLevel level, Settlement settlement, PlotView plot, Blueprint bp, long now) {
		this.lastTouched = now;
		if (now == this.refreshedAt) {
			return;
		}
		this.refreshedAt = now;
		if (plot.stage() != this.stage) {
			newStage(plot.stage());
		}
		if (plot.state() != PlotState.BUILDING || this.stage >= bp.stageCount()) {
			return;
		}
		boolean due = this.scan == null || this.scan.stage() != this.stage || (this.dirty && now - this.scannedAt >= SCAN_MIN)
			|| now - this.scannedAt >= SCAN_IDLE;
		if (due) {
			int moved = advance(level, settlement, plot, bp);
			if (moved > 0) {
				this.advancedAt = now;
				if (plot.stage() != this.stage) {
					newStage(plot.stage());
				}
			}
			if (plot.state() != PlotState.BUILDING || this.stage >= bp.stageCount()) {
				return;
			}
			rescan(level, plot, bp, now);
			wantRing(level, plot, bp);
		}
		if (now - this.advancedAt >= 100 && now - this.heldAt >= 100 && now - this.activeAt < 300) {
			// the counter has not moved for a while (the ring going up, work ahead of the cursor) but the crew is at it: abstract
			// labour keeps off. A crew that has placed nothing for a while is not covered, so a stalled site never stalls the town
			this.heldAt = now;
			settlement.advanceConstruction(plot.id(), 0);
		}
		if (this.staging != null && now - this.resyncedAt >= 100) {
			this.resyncedAt = now;
			this.staging.resync(level);
		}
	}

	private void newStage(int stage) {
		this.stage = stage;
		this.placed.clear();
		this.scan = null;
		this.dirty = true;
	}

	/** Moves the counter over the done steps from the cursor; returns how many. */
	int advance(ServerLevel level, Settlement settlement, PlotView plot, Blueprint bp) {
		int stage = plot.stage();
		int from = plot.step();
		int count = bp.stepCount(stage);
		int i = from;
		Object[] read = new Object[1];
		long[] at = new long[1];
		while (i < count && i - from < 512) {
			read[0] = null;
			bp.step(stage, i, (x, y, z, state, tag) -> {
				at[0] = BlockPos.asLong(x, y, z);
				read[0] = state;
			});
			if (read[0] == null) {
				i++;
				continue;
			}
			BlockPos p = BlockPos.of(at[0]);
			BlockState there = level.isLoaded(p) ? level.getBlockState(p) : null;
			if (!StepPlan.done(there, (BlockState) read[0], this.placed.get(i))) {
				break;
			}
			i++;
		}
		if (i > from) {
			settlement.advanceConstruction(plot.id(), i - from);
		}
		return i - from;
	}

	private void rescan(ServerLevel level, PlotView plot, Blueprint bp, long now) {
		BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
		this.scan = StepPlan.scan(bp, this.stage, plot.step(), this.placed, pos -> {
			m.set(pos);
			return level.isLoaded(m) ? level.getBlockState(m) : null;
		}, SCAN_STEPS, SCAN_PENDING);
		this.scannedAt = now;
		this.dirty = false;
		this.pendingAt.clear();
		for (StepPlan.Pending p : this.scan.pending()) {
			if (!p.state().isAir()) {
				this.pendingAt.add(p.pos());
			}
		}
	}

	/**
	 * The ring goes up before anyone needs it: as soon as the stage has work high on the shell, to the height from which
	 * its highest course is at eye level (and it stays up, rising as the stage climbs, until the last high stage is done).
	 */
	private void wantRing(ServerLevel level, PlotView plot, Blueprint bp) {
		StepPlan.Scan s = this.scan;
		if (s == null || s.pending().isEmpty()) {
			return;
		}
		Staging st = staging(level, bp, plot.spec());
		if (st == null || st.minBase == Staging.NONE) {
			return;
		}
		int top = Integer.MIN_VALUE;
		for (StepPlan.Pending p : s.pending()) {
			if (!p.state().isAir() && high(st, p.pos())) {
				top = Math.max(top, BlockPos.getY(p.pos()));
			}
		}
		if (top != Integer.MIN_VALUE) {
			// feet two below the course: it is at eye height, and the course above it in reach too
			st.require(top - 2);
		}
	}

	/** The crew put something in place just now. */
	void active(long now) {
		this.activeAt = now;
	}

	/** A step was placed (by index of the current stage). */
	void placed(int stage, int index) {
		if (stage == this.stage) {
			this.placed.set(index);
		}
		this.dirty = true;
	}

	boolean isPlaced(int stage, int index) {
		return stage == this.stage && this.placed.get(index);
	}

	void changed() {
		this.dirty = true;
	}

	// ---------------------------------------------------------------- the crew

	/** Within reach of where another builder is working: theirs to place. */
	boolean takenByOther(UUID me, long pos, double eyeHeight) {
		Vec3 c = Vec3.atCenterOf(BlockPos.of(pos));
		for (Map.Entry<UUID, Long> e : this.spots.entrySet()) {
			if (e.getKey().equals(me) || !this.arrived.contains(e.getKey())) {
				continue;
			}
			BlockPos s = BlockPos.of(e.getValue());
			double dx = s.getX() + 0.5 - c.x;
			double dy = s.getY() + eyeHeight - c.y;
			double dz = s.getZ() + 0.5 - c.z;
			if (dx * dx + dy * dy + dz * dz <= BuilderWork.REACH * BuilderWork.REACH) {
				return true;
			}
		}
		return false;
	}

	/** Another builder works from within {@code within} blocks of this spot (horizontally, and about level). */
	boolean crowded(UUID me, BlockPos feet, double within) {
		for (Map.Entry<UUID, Long> e : this.spots.entrySet()) {
			if (e.getKey().equals(me)) {
				continue;
			}
			BlockPos s = BlockPos.of(e.getValue());
			double dx = s.getX() - feet.getX();
			double dz = s.getZ() - feet.getZ();
			if (dx * dx + dz * dz < within * within && Math.abs(s.getY() - feet.getY()) < 3) {
				return true;
			}
		}
		return false;
	}

	/** One of the crew goes to the store for a new stage's materials; true for the first to ask. */
	boolean fetchFor(int stage) {
		if (this.fetchedFor == stage) {
			return false;
		}
		this.fetchedFor = stage;
		return true;
	}

	void leave(UUID builder) {
		this.arrived.remove(builder);
		this.spots.remove(builder);
		this.columns.remove(builder);
	}

	boolean hasCarrier(long now) {
		this.carriers.values().removeIf(t -> t < now);
		return !this.carriers.isEmpty();
	}

	// ---------------------------------------------------------------- scaffolding

	/** The ring round the shell, planned now if there is none (and one could stand); null if none can. */
	@Nullable Staging staging(ServerLevel level, Blueprint bp, PlotSpec spec) {
		if (this.staging == null && this.noStagingFor != this.stage) {
			this.staging = Staging.plan(level, bp, Math.max(0, this.stage), spec);
			if (this.staging == null) {
				this.noStagingFor = this.stage;
			}
		}
		return this.staging;
	}

	/**
	 * Whether the scaffolding has done its work and should come down: the plot is finished or gone, or the last stage
	 * with high work on the shell is done (or, in that stage, no high work is left to do).
	 */
	boolean scaffoldDone(@Nullable PlotView plot) {
		Staging st = this.staging;
		if (st == null) {
			return false;
		}
		if (plot == null || plot.state() != PlotState.BUILDING) {
			return true;
		}
		if (plot.stage() > st.lastHighStage) {
			return true;
		}
		if (plot.stage() < st.lastHighStage || this.scan == null || this.scan.stage() != plot.stage() || !this.scan.complete()) {
			return false;
		}
		for (StepPlan.Pending p : this.scan.pending()) {
			if (high(st, p.pos())) {
				return false;
			}
		}
		return true;
	}

	/** Work out of reach from the ground, on the shell, where the ring serves. */
	static boolean high(Staging st, long pos) {
		int y = BlockPos.getY(pos);
		return st.minBase != Staging.NONE && y > st.minBase + Staging.HIGH
			&& Staging.nearShell(BlockPos.getX(pos), BlockPos.getZ(pos), st.shellMinX, st.shellMaxX, st.shellMinZ, st.shellMaxZ);
	}

	/** Takes everything down at once (the crew folded away, or a restart): nothing is left standing. */
	public void clearScaffolding(ServerLevel level, ScaffoldLedger ledger) {
		if (this.staging != null) {
			for (int c = 0; c < this.staging.n; c++) {
				if (this.staging.valid(c) && this.staging.height[c] >= this.staging.base[c]) {
					this.staging.takeDown(level, ledger, this.ledgerId, c);
				}
			}
		}
		ledger.clear(level, this.ledgerId);
		this.staging = null;
		this.columns.clear();
	}

	/** Forgets the ring once it is down (a later high stage plans a new one). */
	void scaffoldGone() {
		this.staging = null;
		this.noStagingFor = this.stage;
		this.columns.clear();
	}
}
