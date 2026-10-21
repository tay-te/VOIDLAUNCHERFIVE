package dev.voidmc.mettle.mixin.legend;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.voidmc.mettle.character.cards.legend.LegendWild;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * The Ferryman's Stride (still water bears a sprinting holder like ground) and the Greenway (vines keep no one to a
 * crawl). Both sides, so the client's own movement agrees with the server's.
 */
@Mixin(LivingEntity.class)
abstract class LegendLivingEntityMixin {
	@ModifyReturnValue(method = "canStandOnFluid", at = @At("RETURN"))
	private boolean mettle$ferrymansStride(boolean stands, FluidState fluid) {
		return LegendWild.standsOnWater((LivingEntity) (Object) this, fluid, stands);
	}

	@ModifyReturnValue(method = "getLiquidCollisionShape", at = @At("RETURN"))
	private VoxelShape mettle$ferrymansFloor(VoxelShape shape) {
		return LegendWild.waterFloor((LivingEntity) (Object) this, shape);
	}

	@WrapOperation(method = "handleOnClimbable", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Mth;clamp(DDD)D"))
	private double mettle$greenwayVines(double value, double min, double max, Operation<Double> original) {
		return LegendWild.throughVines((LivingEntity) (Object) this) ? value : original.call(value, min, max);
	}
}
