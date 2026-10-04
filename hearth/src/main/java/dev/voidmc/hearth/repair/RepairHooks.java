package dev.voidmc.hearth.repair;

import dev.voidmc.hearth.api.HearthWorld;
import dev.voidmc.hearth.api.SettlementEvents.Damaged.Cause;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * Where the repair module's mixins call in. Each hook is a few field reads when nothing needs it: no server running, the
 * module switched off, a level Hearth does not run in, a position outside every settlement.
 */
public final class RepairHooks {
	private RepairHooks() {
	}

	/** Whether hooks should capture anything at all in this level. */
	public static boolean active(ServerLevel level) {
		RepairRuntime rt = RepairRuntime.current();
		return rt != null && !rt.tracker.self() && RepairModule.config().tracking().enabled() && HearthWorld.of(level) != null;
	}

	/** Whether a position lies in (or by) a settlement: the cheap test before reading a block state. */
	public static boolean inSettlement(ServerLevel level, BlockPos pos) {
		HearthWorld w = HearthWorld.of(level);
		return w != null && w.settlementAt(pos) != null;
	}

	private static boolean wanted(Cause cause) {
		RepairConfig.Tracking t = RepairModule.config().tracking();
		return switch (cause) {
			case PLAYER -> t.playerBreaks();
			case EXPLOSION -> t.explosions();
			case FIRE -> t.fire();
			case OTHER -> t.otherBreaks();
		};
	}

	public static void push(Cause cause, @Nullable ServerPlayer player) {
		RepairRuntime rt = RepairRuntime.current();
		if (rt != null) {
			rt.tracker.push(cause, player);
		}
	}

	public static void pop() {
		RepairRuntime rt = RepairRuntime.current();
		if (rt != null) {
			rt.tracker.pop();
		}
	}

	/** A block a player broke (or one that came down with it). */
	public static void changed(ServerLevel level, BlockPos pos, BlockState before, BlockState after, Cause cause, @Nullable ServerPlayer player) {
		RepairRuntime rt = RepairRuntime.current();
		if (rt != null && wanted(cause)) {
			rt.tracker.changed(level, pos, before, after, cause, player);
		}
	}

	/** {@code Level.destroyBlock}: a mob, a command, or a block popping off because what held it went. */
	public static void destroyed(ServerLevel level, BlockPos pos, BlockState before, BlockState after, @Nullable Entity breaker) {
		RepairRuntime rt = RepairRuntime.current();
		if (rt == null) {
			return;
		}
		ServerPlayer player = breaker instanceof ServerPlayer p ? p : rt.tracker.currentPlayer();
		Cause cause = rt.tracker.currentCause(breaker instanceof ServerPlayer ? Cause.PLAYER : Cause.OTHER);
		if (wanted(cause)) {
			rt.tracker.changed(level, pos, before, after, cause, player);
		}
	}

	/** States before an explosion takes its blocks, for the positions inside settlements (null where it is not). */
	public static BlockState @Nullable [] beforeExplosion(ServerLevel level, List<BlockPos> targets) {
		if (!active(level) || !wanted(Cause.EXPLOSION)) {
			return null;
		}
		HearthWorld w = HearthWorld.of(level);
		BlockState[] before = null;
		for (int i = 0; i < targets.size(); i++) {
			BlockPos p = targets.get(i);
			if (w.settlementAt(p) != null) {
				if (before == null) {
					before = new BlockState[targets.size()];
				}
				before[i] = level.getBlockState(p);
			}
		}
		return before;
	}

	/** After the explosion: what it took. */
	public static void afterExplosion(ServerLevel level, List<BlockPos> targets, BlockState[] before, @Nullable Entity source) {
		ServerPlayer player = source instanceof ServerPlayer p ? p : null;
		for (int i = 0; i < targets.size() && i < before.length; i++) {
			if (before[i] != null) {
				BlockPos p = targets.get(i);
				changed(level, p, before[i], level.getBlockState(p), Cause.EXPLOSION, player);
			}
		}
	}

	/** A fire block ticked (the fire watch's feed). */
	public static void fireTick(ServerLevel level, BlockPos pos) {
		RepairRuntime rt = RepairRuntime.current();
		if (rt != null) {
			rt.fires.fireTick(level, pos);
		}
	}

	/** A player placed a block. */
	public static void placed(ServerLevel level, ServerPlayer player, BlockPos pos) {
		RepairRuntime rt = RepairRuntime.current();
		if (rt != null && !rt.tracker.self()) {
			rt.tracker.placed(level, player, pos, level.getBlockState(pos));
		}
	}

	/** A player emptied a water bucket. */
	public static void waterPoured(ServerLevel level, ServerPlayer player, BlockPos pos) {
		RepairRuntime rt = RepairRuntime.current();
		if (rt != null) {
			rt.fires.waterPoured(level, player, pos);
		}
	}
}
