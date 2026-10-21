package dev.voidmc.mettle.mixin.stealth;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.voidmc.mettle.character.skill.stealth.StealthHooks;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Stealth: sneak attacks and their costs (incoming damage), how far hostiles notice a player, Darkness and Blindness. */
@Mixin(LivingEntity.class)
abstract class StealthLivingEntityMixin {
	@ModifyVariable(method = "hurtServer", at = @At("HEAD"), argsOnly = true, ordinal = 0)
	private float mettle$stealthHurt(float damage, ServerLevel level, DamageSource source) {
		return StealthHooks.hurt((LivingEntity) (Object) this, source, damage);
	}

	@ModifyReturnValue(method = "getVisibilityPercent", at = @At("RETURN"))
	private double mettle$stealthUnseen(double percent, ServerLevel level, Entity looking) {
		return StealthHooks.visibility((LivingEntity) (Object) this, looking, percent);
	}

	@ModifyVariable(method = "addEffect(Lnet/minecraft/world/effect/MobEffectInstance;Lnet/minecraft/world/entity/Entity;)Z", at = @At("HEAD"),
		argsOnly = true, ordinal = 0)
	private MobEffectInstance mettle$stealthEffect(MobEffectInstance effect) {
		return StealthHooks.effect((LivingEntity) (Object) this, effect);
	}
}
