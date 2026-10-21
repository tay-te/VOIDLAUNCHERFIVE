package dev.voidmc.fray.client.combat.mixin;

import dev.voidmc.fray.client.combat.LockOn;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Lock-on: scroll switches target while locked; the camera's soft follow runs after the mouse turned the player. */
@Mixin(MouseHandler.class)
abstract class MouseHandlerCombatMixin {
	@Shadow
	@Final
	private Minecraft minecraft;

	@Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
	private void fray$switch(long handle, double xoffset, double yoffset, CallbackInfo ci) {
		if (this.minecraft.gui.screen() == null && LockOn.target >= 0 && LockOn.scroll(yoffset)) {
			ci.cancel();
		}
	}

	@Inject(method = "handleAccumulatedMovement", at = @At("TAIL"))
	private void fray$follow(CallbackInfo ci) {
		LockOn.frame(this.minecraft);
	}
}
