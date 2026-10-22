package dev.voidmc.fray.client.combat.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import dev.voidmc.fray.client.combat.CombatInput;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Key presses and releases for the lone-key gates (Alt dodge alone, C without a hotbar number). */
@Mixin(KeyboardHandler.class)
abstract class KeyboardHandlerCombatMixin {
	@Inject(method = "keyPress", at = @At("HEAD"))
	private void fray$key(long window, int action, KeyEvent event, CallbackInfo ci) {
		if (action == InputConstants.PRESS || action == InputConstants.RELEASE) {
			CombatInput.onKey(InputConstants.getKey(event), action == InputConstants.PRESS);
		}
	}
}
