package dev.voidmc.fray.client.combat.mixin;

import dev.voidmc.fray.client.combat.CombatInput;
import dev.voidmc.fray.client.combat.LockOn;
import dev.voidmc.fray.client.combat.ClientCombat;
import dev.voidmc.fray.combat.net.CombatPayloads;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The attack key belongs to Fray's actions (a sword never mines; tools mine solid blocks), the use key also feeds
 * Guard, and middle mouse is lock-on: vanilla's pick block stays on Alt + middle mouse, and on a block outside a fight.
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
		Minecraft mc = (Minecraft) (Object) this;
		boolean alt = com.mojang.blaze3d.platform.InputConstants.isKeyDown(com.mojang.blaze3d.platform.InputConstants.KEY_LALT)
			|| com.mojang.blaze3d.platform.InputConstants.isKeyDown(com.mojang.blaze3d.platform.InputConstants.KEY_RALT);
		boolean fight = ClientCombat.self != null && (ClientCombat.self.flags() & CombatPayloads.SELF_IN_COMBAT) != 0;
		boolean block = mc.hitResult != null && mc.hitResult.getType() == HitResult.Type.BLOCK;
		if (!alt && (LockOn.target >= 0 || fight || !block)) {
			ci.cancel();
		}
	}
}
