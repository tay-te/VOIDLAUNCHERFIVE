package dev.voidmc.fray.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.voidmc.fray.client.PoseApply;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The {@code item_r} / {@code item_l} bones (ANIMATION.md §7.5): a held item turned about the grip, right after vanilla's translateToHand. */
@Mixin(ItemInHandLayer.class)
abstract class ItemInHandLayerMixin {
	@Inject(method = "submitArmWithItem", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/model/ArmedModel;translateToHand(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;Lnet/minecraft/world/entity/HumanoidArm;Lcom/mojang/blaze3d/vertex/PoseStack;)V",
		shift = At.Shift.AFTER))
	private void fray$itemBone(ArmedEntityRenderState state, ItemStackRenderState item, ItemStack stack, HumanoidArm arm, PoseStack poseStack,
		SubmitNodeCollector collector, int light, CallbackInfo ci) {
		PoseApply.forearm(((net.minecraft.client.renderer.entity.layers.RenderLayer<?, ?>) (Object) this).getParentModel(), arm == HumanoidArm.LEFT, poseStack);
		PoseApply.itemBone(state, arm == HumanoidArm.LEFT, poseStack);
	}
}
