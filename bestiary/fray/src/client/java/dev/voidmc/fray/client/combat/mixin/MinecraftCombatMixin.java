package dev.voidmc.fray.client.combat.mixin;

import dev.voidmc.fray.client.combat.CombatInput;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The attack key belongs to Fray's actions (a sword never mines; tools mine solid blocks), the use key also feeds
 * Guard, and middle mouse is lock-on: vanilla's pick block stays on Alt + middle mouse, and outside a fight on a block
 * or a creature that isn't hostile ({@link CombatInput#middleIsPick}).
 */
@Mixin(Minecraft.class)
abstract class MinecraftCombatMixin {
	@Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
	private void fray$attack(CallbackInfoReturnable<Boolean> cir) {
		Minecraft mc = (Minecraft) (Object) this;
		if (CombatInput.takeAttack(mc)) {
			CombatInput.onAttackClick();
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "continueAttack", at = @At("HEAD"), cancellable = true)
	private void fray$hold(boolean down, CallbackInfo ci) {
		if (CombatInput.attackHeld) {
			ci.cancel();
		}
	}

	@Inject(method = "startUseItem", at = @At("HEAD"))
	private void fray$use(CallbackInfo ci) {
		CombatInput.onUseClick();
	}

	@Inject(method = "pickBlockOrEntity", at = @At("HEAD"), cancellable = true)
	private void fray$pick(CallbackInfo ci) {
		if (CombatInput.middleIsPick((Minecraft) (Object) this)) {
			CombatInput.picksAllowed++;
		} else {
			ci.cancel();
		}
	}
}
