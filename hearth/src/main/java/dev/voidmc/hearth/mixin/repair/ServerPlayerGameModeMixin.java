package dev.voidmc.hearth.mixin.repair;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.voidmc.hearth.api.SettlementEvents.Damaged.Cause;
import dev.voidmc.hearth.repair.RepairHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/**
 * Blocks players break. The whole break runs inside a "player" cause, so what comes down with the block (the torch on
 * it, the door on top of it) is the player's doing too.
 */
@Mixin(ServerPlayerGameMode.class)
abstract class ServerPlayerGameModeMixin {
	@Shadow
	protected ServerLevel level;

	@Shadow
	@Final
	protected ServerPlayer player;

	@WrapMethod(method = "destroyBlock(Lnet/minecraft/core/BlockPos;)Z")
	private boolean hearth$repairTrack(BlockPos pos, Operation<Boolean> original) {
		ServerLevel level = this.level;
		if (!RepairHooks.active(level) || !RepairHooks.inSettlement(level, pos)) {
			return original.call(pos);
		}
		BlockState before = level.getBlockState(pos);
		RepairHooks.push(Cause.PLAYER, this.player);
		try {
			boolean broke = original.call(pos);
			RepairHooks.changed(level, pos, before, level.getBlockState(pos), Cause.PLAYER, this.player);
			return broke;
		} finally {
			RepairHooks.pop();
		}
	}
}
