package dev.voidmc.fray.client.combat.mixin;

import dev.voidmc.fray.client.combat.ClientCombat;
import dev.voidmc.fray.client.combat.hud.FrayHud;
import dev.voidmc.fray.combat.net.CombatPayloads;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Elites and bosses carry their names on Fray's nameplate, target frame and boss bar (H4-H6): vanilla's floating name
 * tag stands down for them while Fray's HUD is on.
 */
@Mixin(LivingEntityRenderer.class)
abstract class NameTagMixin {
	@Inject(method = "shouldShowName(Lnet/minecraft/world/entity/LivingEntity;D)Z", at = @At("HEAD"), cancellable = true)
	private void fray$ownPlate(LivingEntity entity, double distanceToCameraSq, CallbackInfoReturnable<Boolean> cir) {
		if (!FrayHud.active()) {
			return;
		}
		CombatPayloads.Profile p = ClientCombat.PROFILES.get(entity.getId());
		boolean boss = ClientCombat.bar != null && ClientCombat.bar.boss() == entity.getId();
		if (boss || p != null && p.rank() > 0) {
			cir.setReturnValue(false);
		}
	}
}
