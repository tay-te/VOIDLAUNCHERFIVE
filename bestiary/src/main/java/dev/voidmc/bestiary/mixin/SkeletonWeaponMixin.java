package dev.voidmc.bestiary.mixin;

import dev.voidmc.bestiary.ai.Brain;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A skeleton Bestiary fights with doesn't get vanilla's bow or melee goal back when its hands change. */
@Mixin(AbstractSkeleton.class)
abstract class SkeletonWeaponMixin {
	@Inject(method = "reassessWeaponGoal", at = @At("HEAD"), cancellable = true)
	private void bestiary$keep(CallbackInfo ci) {
		if (Brain.get((AbstractSkeleton) (Object) this) != null) {
			ci.cancel();
		}
	}
}
