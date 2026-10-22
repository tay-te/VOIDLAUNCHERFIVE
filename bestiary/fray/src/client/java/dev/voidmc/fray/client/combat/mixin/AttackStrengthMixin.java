package dev.voidmc.fray.client.combat.mixin;

import dev.voidmc.fray.client.combat.FrayCombatClient;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Fray's actions replace vanilla's attack cooldown (COMBAT.md §3): on a Fray server the local player's attack strength
 * reads full, so vanilla's attack indicator under the crosshair doesn't show a meaningless charge.
 */
@Mixin(Player.class)
abstract class AttackStrengthMixin {
	@Inject(method = "getAttackStrengthScale", at = @At("HEAD"), cancellable = true)
	private void fray$full(float a, CallbackInfoReturnable<Float> cir) {
		if ((Object) this instanceof LocalPlayer && FrayCombatClient.serverHasFray()) {
			cir.setReturnValue(1F);
		}
	}
}
