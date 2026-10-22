package dev.voidmc.fray.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.voidmc.fray.client.FirstPersonBody;
import dev.voidmc.fray.client.VanillaFirstPerson;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * First person during the local player's action. With the {@code fpAnimations} option on, the arms of a clip with an
 * {@code fp} block are Fray's, drawn inside vanilla's (and Iris's) hand pass; off (the default), vanilla draws its own
 * hands and Fray only hands it the action as vanilla's swing, or a guard's small raise ({@link VanillaFirstPerson}).
 */
@Mixin(FirstPersonHandsAndItemsRenderer.class)
abstract class FirstPersonHandsMixin {
	@Inject(method = "submitHandsWithItems", at = @At("HEAD"), cancellable = true)
	private void fray$hands(float partialTicks, PoseStack poseStack, SubmitNodeCollector collector, PlayerRenderState playerState,
		FirstPersonHandsAndItemsRenderState state, CallbackInfo ci) {
		if (FirstPersonBody.draw(partialTicks, poseStack, collector, playerState, state)) {
			ci.cancel();
			return;
		}
		VanillaFirstPerson.begin(partialTicks, playerState, state);
	}

	@Inject(method = "submitHandsWithItems", at = @At("RETURN"))
	private void fray$handsDone(float partialTicks, PoseStack poseStack, SubmitNodeCollector collector, PlayerRenderState playerState,
		FirstPersonHandsAndItemsRenderState state, CallbackInfo ci) {
		VanillaFirstPerson.end(playerState, state);
	}

	@Inject(method = "submitArmWithItem", require = 1, at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/renderer/item/ItemStackRenderState;submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;III)V"))
	private void fray$guard(PlayerRenderState playerState, FirstPersonHandsAndItemsRenderState state, float partialTicks, float xRot, InteractionHand hand,
		float attack, ItemStack itemStack, float inverseArmHeight, PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, CallbackInfo ci) {
		VanillaFirstPerson.raise(poseStack, playerState, hand, itemStack);
	}
}
