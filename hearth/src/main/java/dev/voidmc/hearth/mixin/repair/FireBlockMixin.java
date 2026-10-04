package dev.voidmc.hearth.mixin.repair;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.voidmc.hearth.api.SettlementEvents.Damaged.Cause;
import dev.voidmc.hearth.repair.RepairHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.FireBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Fire: the blocks it burns away, and each flame's tick (how the fire watch knows a town is burning). */
@Mixin(FireBlock.class)
abstract class FireBlockMixin {
	@Inject(method = "tick(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/util/RandomSource;)V",
		at = @At("HEAD"))
	private void hearth$repairFlame(BlockState state, ServerLevel level, BlockPos pos, RandomSource random, CallbackInfo ci) {
		if (RepairHooks.active(level)) {
			RepairHooks.fireTick(level, pos);
		}
	}

	@WrapMethod(method = "checkBurnOut(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;ILnet/minecraft/util/RandomSource;I)V")
	private void hearth$repairBurn(Level level, BlockPos pos, int chance, RandomSource random, int age, Operation<Void> original) {
		if (!(level instanceof ServerLevel server) || !RepairHooks.active(server) || !RepairHooks.inSettlement(server, pos)) {
			original.call(level, pos, chance, random, age);
			return;
		}
		BlockState before = level.getBlockState(pos);
		RepairHooks.push(Cause.FIRE, null);
		try {
			original.call(level, pos, chance, random, age);
			RepairHooks.changed(server, pos, before, level.getBlockState(pos), Cause.FIRE, null);
		} finally {
			RepairHooks.pop();
		}
	}
}
