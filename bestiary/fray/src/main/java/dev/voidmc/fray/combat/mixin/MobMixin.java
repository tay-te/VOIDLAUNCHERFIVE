package dev.voidmc.fray.combat.mixin;

import dev.voidmc.fray.combat.Statuses;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A Staggered, Dazed, Frozen or stunned creature can't strike (vanilla AI included). */
@Mixin(Mob.class)
abstract class MobMixin {
	@Inject(method = "doHurtTarget", at = @At("HEAD"), cancellable = true)
	private void fray$incapacitated(ServerLevel level, Entity target, CallbackInfoReturnable<Boolean> cir) {
		if (Statuses.INSTANCE.incapacitated((Mob) (Object) this)) {
			cir.setReturnValue(false);
		}
	}
}
