package dev.voidmc.mettle.mixin.legend;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.voidmc.mettle.character.cards.legend.LegendWild;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * The Ferryman's Stride: borne on the water's surface a runner is not "in" it (no wading stop to the sprint, no current,
 * no splash), as on ground. Both sides.
 */
@Mixin(Entity.class)
abstract class LegendEntityMixin {
	@ModifyExpressionValue(method = "updateFluidInteraction", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/world/entity/EntityFluidInteraction;isInFluid(Lnet/minecraft/tags/TagKey;)Z", ordinal = 0))
	private boolean mettle$ferrymansStride(boolean inWater) {
		return inWater && !LegendWild.borneOnWater((Entity) (Object) this);
	}
}
