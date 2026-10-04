package dev.voidmc.hearth.mixin.repair;

import dev.voidmc.hearth.repair.RepairHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A player pouring water: on a burning town, that is fighting the fire. */
@Mixin(BucketItem.class)
abstract class BucketItemMixin {
	@Shadow
	@Final
	protected Fluid content;

	@Inject(method = "emptyContents(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/phys/BlockHitResult;)Z",
		at = @At("RETURN"))
	private void hearth$repairWater(@Nullable LivingEntity user, Level level, BlockPos pos, @Nullable BlockHitResult hit,
		CallbackInfoReturnable<Boolean> cir) {
		if (cir.getReturnValueZ() && user instanceof ServerPlayer player && level instanceof ServerLevel server
			&& this.content.defaultFluidState().is(FluidTags.WATER) && RepairHooks.active(server)) {
			RepairHooks.waterPoured(server, player, pos);
		}
	}
}
