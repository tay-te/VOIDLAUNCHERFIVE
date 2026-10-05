package dev.voidmc.mettle.mixin.survival;

import dev.voidmc.mettle.character.skill.survival.Camp;
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

/** Pathfinder: berry bushes neither slow nor scratch you. Both sides, so the client's own movement agrees. */
@Mixin(SweetBerryBushBlock.class)
abstract class SurvivalSweetBerryBushMixin {
	@Inject(method = "entityInside", at = @At("HEAD"), cancellable = true)
	private void mettle$pathfinder(BlockState state, Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier effects, boolean precise, CallbackInfo ci) {
		if (Camp.ignoresBerryBush(entity)) {
			ci.cancel();
		}
	}
}
