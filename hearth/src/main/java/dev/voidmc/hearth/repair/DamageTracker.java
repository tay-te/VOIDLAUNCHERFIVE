package dev.voidmc.hearth.repair;

import dev.voidmc.hearth.api.HearthWorld;
import dev.voidmc.hearth.api.PlotView;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.api.SettlementEvents;
import dev.voidmc.hearth.api.SettlementEvents.Damaged.Cause;
import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * Turns block changes into damage records. The hooks (mixins and events) report what a position held before and after a
 * break, an explosion, a burn or a placement; the tracker finds the plot cell there ({@link PlotLocator}), decides by
 * {@link DamageRules} whether a blueprint block was lost, queues it, and batches {@link SettlementEvents#DAMAGED} to the
 * end of the tick (one event per plot and cause, however many blocks a creeper took).
 *
 * <p>A cause context follows nested changes: the torch that drops when its wall is blown away is the explosion's
 * damage, the door that falls when a player breaks the block under it is the player's. Changes the repair module makes
 * itself (mending, clearing litter) are never damage. Server thread only.
 */
public final class DamageTracker {
	private record Context(Cause cause, @Nullable ServerPlayer player) {
	}

	private record BatchKey(long settlement, int plot, Cause cause, @Nullable UUID player) {
	}

	private static final class Batch {
		final HearthWorld world;
		int blocks;

		Batch(HearthWorld world) {
			this.world = world;
		}
	}

	private final RepairRuntime runtime;
	private final ArrayDeque<Context> context = new ArrayDeque<>();
	private final Map<BatchKey, Batch> batches = new LinkedHashMap<>();
	private int selfDepth;
	long recorded;
	long litter;
	long rebuiltByPlayers;
	long hookNanos;
	long hookCalls;

	DamageTracker(RepairRuntime runtime) {
		this.runtime = runtime;
	}

	/** Changes inside are the repair module's own (mending, clearing litter): never damage. */
	public void beginSelf() {
		this.selfDepth++;
	}

	public void endSelf() {
		this.selfDepth = Math.max(0, this.selfDepth - 1);
	}

	public boolean self() {
		return this.selfDepth > 0;
	}

	public void push(Cause cause, @Nullable ServerPlayer player) {
		this.context.push(new Context(cause, player));
	}

	public void pop() {
		this.context.poll();
	}

	/** The cause of whatever is breaking now, given what broke it directly. */
	Cause currentCause(Cause fallback) {
		Context c = this.context.peek();
		return c != null ? c.cause : fallback;
	}

	@Nullable ServerPlayer currentPlayer() {
		Context c = this.context.peek();
		return c != null ? c.player : null;
	}

	/**
	 * A block at {@code pos} went from {@code before} to {@code after}. Records damage if it was a standing plot's
	 * blueprint block, and tells the fire watch and the vandalism tracker.
	 *
	 * @param player who did it, if a player did (directly, or by lighting the TNT)
	 */
	public void changed(ServerLevel level, BlockPos pos, BlockState before, BlockState after, Cause cause, @Nullable ServerPlayer player) {
		if (this.selfDepth > 0 || before == after) {
			return;
		}
		RepairConfig.Tracking cfg = RepairModule.config().tracking();
		if (!cfg.enabled()) {
			return;
		}
		HearthWorld world = HearthWorld.of(level);
		if (world == null) {
			return;
		}
		long t0 = System.nanoTime();
		this.hookCalls++;
		try {
			if (player != null && before.getBlock() instanceof BaseFireBlock) {
				// putting out a fire by hand
				this.runtime.fires.playerFought(level, player, pos);
			}
			PlotLocator.Hit hit = this.runtime.locator.locate(world, pos, this.runtime.now());
			if (hit == null || !DamageRules.isDamage(hit.expected(), before, after)) {
				return;
			}
			record(level, world, hit, pos, cause, player, false);
		} finally {
			this.hookNanos += System.nanoTime() - t0;
		}
	}

	/**
	 * Queues damage at a located cell (also used by inspections, which find damage no event saw). Returns whether a new
	 * entry was queued.
	 */
	public boolean record(ServerLevel level, HearthWorld world, PlotLocator.Hit hit, BlockPos pos, Cause cause, @Nullable ServerPlayer player,
		boolean found) {
		Settlement s = hit.settlement();
		PlotView plot = hit.plot();
		UUID who = player == null ? null : player.getUUID();
		UUID owner = plot.owner();
		if (owner != null && owner.equals(who)) {
			// a player's own house is theirs to change
			return false;
		}
		int flags = owner != null && RepairModule.config().owners().ownerPays() ? Damage.NEEDS_PAYMENT : 0;
		Damage d = new Damage(pos.asLong(), plot.id(), hit.stage(), -1, cause, RepairRuntime.day(level), who, flags);
		d.setExpected(hit.expected());
		RepairQueue q = this.runtime.queue(s);
		if (!q.add(d, RepairModule.config().queue().maxEntries())) {
			return false;
		}
		this.runtime.withDamage.add(s.id());
		s.markDirty();
		this.recorded++;
		this.batches.computeIfAbsent(new BatchKey(s.id(), plot.id(), cause, who), k -> new Batch(world)).blocks++;
		if (cause == Cause.FIRE) {
			this.runtime.fires.burned(level, s, pos);
		}
		if (player != null && !found && (cause == Cause.PLAYER || cause == Cause.EXPLOSION)) {
			if (cause == Cause.PLAYER && dev.voidmc.hearth.api.HearthServices.law() != null) {
				// the law module judges a player's breaks itself as they happen (witnesses, noise, forced locks): one
				// crime, not two. TNT and fire a player set are ours to report.
				d.set(Damage.REPORTED, true);
			} else {
				this.runtime.vandalism.note(level, s, player, pos, RepairBill.worth(s, hit.expected()));
			}
		}
		return true;
	}

	/**
	 * A player placed a block. Putting back a damaged blueprint block mends it (and earns standing); a block left in a
	 * doorway or on a street is litter for the builders to clear.
	 */
	public void placed(ServerLevel level, ServerPlayer player, BlockPos pos, BlockState placed) {
		if (this.selfDepth > 0) {
			return;
		}
		RepairConfig.Tracking cfg = RepairModule.config().tracking();
		HearthWorld world = HearthWorld.of(level);
		if (!cfg.enabled() || world == null) {
			return;
		}
		Settlement s = world.settlementAt(pos);
		if (s == null) {
			return;
		}
		RepairQueue q = s.data(RepairModule.QUEUE);
		Damage d = q.get(pos.asLong());
		if (d != null && !d.has(Damage.LITTER)) {
			BlockState expected = d.expected();
			if (expected == null) {
				PlotView plot = s.plot(d.plot);
				expected = plot == null ? null : this.runtime.locator.cellOf(s, PlotLocator.services(world), plot, d.pos);
			}
			if (expected != null && DamageRules.sameBlock(expected, placed)) {
				q.remove(d.pos);
				s.markDirty();
				this.rebuiltByPlayers++;
				this.runtime.vandalism.helped(level, s, player, pos);
				this.runtime.abstracts.mendedOne(world, s, d.plot);
			}
			return;
		}
		if (!cfg.litter() || d != null) {
			return;
		}
		PlotLocator.Hit hit = this.runtime.locator.locate(s, PlotLocator.services(world), pos, this.runtime.now());
		if (hit == null || !hit.expected().isAir() || player.getUUID().equals(hit.plot().owner()) || !DamageRules.litterBlock(placed)
			|| !Litter.blocksWay(this.runtime.locator, s, PlotLocator.services(world), hit.plot(), pos)) {
			return;
		}
		Damage litter = new Damage(pos.asLong(), hit.plot().id(), hit.stage(), -1, Cause.PLAYER, RepairRuntime.day(level), player.getUUID(), Damage.LITTER);
		litter.setExpected(hit.expected());
		if (this.runtime.queue(s).add(litter, RepairModule.config().queue().maxEntries())) {
			this.runtime.withDamage.add(s.id());
			s.markDirty();
			this.litter++;
		}
	}

	/** Fires the batched {@link SettlementEvents#DAMAGED} events (checked every tick, sent once a second). */
	void flush() {
		// once a second: an explosion is one event per plot, a fire one per plot per second however much it burns
		if (this.batches.isEmpty() || this.runtime.now() % 20 != 0) {
			return;
		}
		Map<BatchKey, Batch> todo = new LinkedHashMap<>(this.batches);
		this.batches.clear();
		todo.forEach((k, b) -> {
			Settlement s = b.world.settlement(k.settlement);
			PlotView plot = s == null ? null : s.plot(k.plot);
			if (plot != null) {
				try {
					SettlementEvents.DAMAGED.invoker().onDamaged(b.world, s, plot, k.cause, b.blocks, k.player);
				} catch (RuntimeException e) {
					RepairModule.LOG.warn("a DAMAGED listener failed", e);
				}
			}
		});
	}

	public String stats() {
		return String.format(java.util.Locale.ROOT, "%d recorded, %d litter, %d put back by players; %d hook calls in settlements, %.1f ms in all (%.1f µs each)",
			this.recorded, this.litter, this.rebuiltByPlayers, this.hookCalls, this.hookNanos / 1e6, this.hookCalls == 0 ? 0 : this.hookNanos / 1e3 / this.hookCalls);
	}
}
