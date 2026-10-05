package dev.voidmc.mettle.mixin.survival;

import dev.voidmc.mettle.character.skill.survival.Hunting;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Stalker: a sneak attack on an animal that hasn't noticed you is a kill, on the incoming damage. */
@Mixin(LivingEntity.class)
abstract class SurvivalLivingEntityMixin {
	@ModifyVariable(method = "hurtServer", at = @At("HEAD"), argsOnly = true, ordinal = 0)
	private float mettle$stalker(float damage, ServerLevel level, DamageSource source) {
		return Hunting.damage((LivingEntity) (Object) this, source, damage);
	}
}
