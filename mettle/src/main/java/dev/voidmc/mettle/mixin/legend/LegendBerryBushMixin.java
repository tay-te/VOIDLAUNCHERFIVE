package dev.voidmc.mettle.mixin.legend;

import dev.voidmc.mettle.character.cards.legend.LegendWild;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The Greenway: berry bushes neither slow nor scratch a holder. Both sides, so the client's own movement agrees. */
@Mixin(SweetBerryBushBlock.class)
abstract class LegendBerryBushMixin {
	@Inject(method = "entityInside", at = @At("HEAD"), cancellable = true)
	private void mettle$greenway(BlockState state, Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier effects, boolean precise, CallbackInfo ci) {
		if (LegendWild.throughBushes(entity)) {
			ci.cancel();
		}
	}
}
