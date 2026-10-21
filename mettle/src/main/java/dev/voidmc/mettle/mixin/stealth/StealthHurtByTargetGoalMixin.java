package dev.voidmc.mettle.mixin.stealth;

import dev.voidmc.mettle.character.skill.stealth.StealthHooks;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Unseen Blade and Muffled: a mob struck from stealth calls none of its kind to help. */
@Mixin(HurtByTargetGoal.class)
abstract class StealthHurtByTargetGoalMixin {
	@Inject(method = "alertOthers", at = @At("HEAD"), cancellable = true)
	private void mettle$unseenBlade(CallbackInfo ci) {
		if (StealthHooks.silenced(((TargetGoalAccess) this).mettle$mob())) {
			ci.cancel();
		}
	}
}
