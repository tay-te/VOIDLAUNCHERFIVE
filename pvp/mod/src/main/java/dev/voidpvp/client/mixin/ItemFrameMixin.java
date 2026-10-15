package dev.voidpvp.client.mixin;

import dev.voidpvp.client.perf.PerfConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.entity.ItemFrameEntityRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.ItemFrameEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * {@code culling.item_frame_distance}: past it, an item frame draws its frame and not the item.
 *
 * <p>{@code method_4334} is the private item half of {@code ItemFrameEntityRenderer.render}
 * (offset 205), called after the frame model is drawn — so cancelling it leaves the frame on the
 * wall and drops the item model, map or block inside it.</p>
 */
@Mixin(ItemFrameEntityRenderer.class)
public abstract class ItemFrameMixin {

    @Inject(method = "method_4334", at = @At("HEAD"), cancellable = true)
    private void void$skipFarItem(ItemFrameEntity frame, CallbackInfo ci) {
        if (!PerfConfig.culling()) {
            return;
        }
        Entity camera = MinecraftClient.getInstance().getCameraEntity();
        if (camera != null && camera.squaredDistanceTo(frame) > PerfConfig.itemFrameDistanceSq) {
            ci.cancel();
        }
    }
}
