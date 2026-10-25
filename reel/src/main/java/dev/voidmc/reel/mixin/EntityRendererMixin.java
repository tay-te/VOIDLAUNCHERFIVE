package dev.voidmc.reel.mixin;

import dev.voidmc.reel.client.ActorRecorder;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Which entity each render state was made for, so a recorded movie knows whose model it is posing (ActorRecorder). */
@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin {
	@Inject(method = "createRenderState(Lnet/minecraft/world/entity/Entity;F)Lnet/minecraft/client/renderer/entity/state/EntityRenderState;",
		at = @At("RETURN"))
	private void reel$bind(Entity entity, float partialTicks, CallbackInfoReturnable<EntityRenderState> cir) {
		ActorRecorder.bind(cir.getReturnValue(), entity);
	}
}
