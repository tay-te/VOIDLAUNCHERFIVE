package dev.voidmc.fray.client.combat.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import dev.voidmc.fray.client.combat.CombatInput;
import dev.voidmc.fray.client.combat.LockOn;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Lock-on: scroll switches target while locked; the camera's soft follow runs after the mouse turned the player. Mouse
 * buttons also feed the lone-key gates (Alt + middle mouse is pick block, not a dodge).
 */
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

	@Inject(method = "onButton", at = @At("HEAD"))
	private void fray$button(long handle, MouseButtonInfo info, int action, CallbackInfo ci) {
		if (action == InputConstants.PRESS || action == InputConstants.RELEASE) {
			CombatInput.onKey(InputConstants.Type.MOUSE.getOrCreate(info.button()), action == InputConstants.PRESS);
		}
	}

	@Inject(method = "handleAccumulatedMovement", at = @At("TAIL"))
	private void fray$follow(CallbackInfo ci) {
		LockOn.frame(this.minecraft);
	}
}
