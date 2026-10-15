package dev.voidpvp.client.mixin;

import dev.voidpvp.client.visual.VisualHooks;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.block.BlockRenderManager;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The {@code clear_glass} mod: glass contributes no faces when a chunk is built.
 *
 * <p>{@code BlockRenderManager.renderBlock} is what the chunk builder calls per block to emit its
 * faces into the section's buffer; answering {@code false} at its head is "this block drew
 * nothing", which is exactly what air answers. The block itself is untouched — still solid, still
 * targetable, still culling nothing, since glass never culled its neighbours' faces to begin
 * with. Runs on the chunk-builder threads, so it reads only {@code volatile} config.</p>
 */
@Mixin(BlockRenderManager.class)
public abstract class ClearGlassMixin {

    @Inject(method = "renderBlock", at = @At("HEAD"), cancellable = true)
    private void void$clear(BlockState state, BlockPos pos, BlockView view, BufferBuilder buffer,
                            CallbackInfoReturnable<Boolean> cir) {
        if (state != null && VisualHooks.isClearedGlass(state.getBlock())) {
            cir.setReturnValue(false);
        }
    }
}
