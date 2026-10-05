package dev.voidmc.atlas.client.mixin;

import dev.voidmc.atlas.client.Backdrop;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.GameRenderState;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The map's frozen backdrop. While the full map is open the world behind it is only a dimmed picture, so instead of
 * drawing the whole world again every frame Atlas copies the frame once (after the world, before the GUI) and then
 * skips the world: the map screen costs what the map costs, not what the world costs. Only GPU-side copies through
 * the backend's own command encoder are used, so it works the same on OpenGL and Vulkan.
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
	@Shadow
	@Final
	private GameRenderState gameRenderState;

	@Inject(
		method = "extract",
		at = @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/state/GameRenderState;shouldRenderLevel:Z", opcode = Opcodes.PUTFIELD,
			shift = At.Shift.AFTER)
	)
	private void atlas$skipWorldBehindMap(DeltaTracker deltaTracker, boolean advanceGameTime, CallbackInfo ci) {
		if (this.gameRenderState.shouldRenderLevel && Backdrop.skipWorld()) {
			this.gameRenderState.shouldRenderLevel = false;
		}
	}

	@Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/render/GuiRenderer;render()V"))
	private void atlas$captureBackdrop(CallbackInfo ci) {
		if (this.gameRenderState.shouldRenderLevel) {
			Backdrop.captureIfWanted(((GameRenderer) (Object) this).mainRenderTarget());
		}
	}
}
