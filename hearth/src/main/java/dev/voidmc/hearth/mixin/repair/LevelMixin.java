package dev.voidmc.hearth.mixin.repair;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.voidmc.hearth.repair.RepairHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;

/**
 * {@code destroyBlock}: withers and ravagers, {@code /setblock … destroy}, and every block that pops off because what
 * held it went (a torch whose wall was broken, a door whose floor was). The cause is whatever is breaking around it.
 */
@Mixin(Level.class)
abstract class LevelMixin {
	@WrapMethod(method = "destroyBlock(Lnet/minecraft/core/BlockPos;ZLnet/minecraft/world/entity/Entity;I)Z")
	private boolean hearth$repairTrack(BlockPos pos, boolean drop, @Nullable Entity breaker, int updateLimit, Operation<Boolean> original) {
		if (!((Object) this instanceof ServerLevel level) || !RepairHooks.active(level) || !RepairHooks.inSettlement(level, pos)) {
			return original.call(pos, drop, breaker, updateLimit);
		}
		BlockState before = level.getBlockState(pos);
		boolean destroyed = original.call(pos, drop, breaker, updateLimit);
		if (destroyed) {
			RepairHooks.destroyed(level, pos, before, level.getBlockState(pos), breaker);
		}
		return destroyed;
	}
}
