package dev.voidmc.fray.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.voidmc.fray.client.FirstPersonBody;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** First-person arms during an action with an {@code fp} block are Fray's, drawn inside vanilla's (and Iris's) hand pass. */
@Mixin(FirstPersonHandsAndItemsRenderer.class)
abstract class FirstPersonHandsMixin {
	@Inject(method = "submitHandsWithItems", at = @At("HEAD"), cancellable = true)
	private void fray$hands(float partialTicks, PoseStack poseStack, SubmitNodeCollector collector, PlayerRenderState playerState,
		FirstPersonHandsAndItemsRenderState state, CallbackInfo ci) {
		if (FirstPersonBody.draw(partialTicks, poseStack, collector, playerState, state)) {
			ci.cancel();
		}
	}
}
