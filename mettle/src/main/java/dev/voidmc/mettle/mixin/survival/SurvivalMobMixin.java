package dev.voidmc.mettle.mixin.survival;

import dev.voidmc.mettle.character.skill.survival.Hunting;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Sixth Sense: the moment a hostile switches its target to a player. */
@Mixin(Mob.class)
abstract class SurvivalMobMixin {
	@Shadow
	public abstract @Nullable LivingEntity getTarget();

	@Inject(method = "setTarget", at = @At("HEAD"))
	private void mettle$sixthSense(@Nullable LivingEntity target, CallbackInfo ci) {
		Hunting.targeted((Mob) (Object) this, this.getTarget(), target);
	}
}
