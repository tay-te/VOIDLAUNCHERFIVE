package dev.voidmc.mettle.mixin.cards;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.voidmc.mettle.character.cards.CardHooks;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Card traits and sets on living things (docs/CARDS.md §4.2, §6.3): damage dealt and taken, effect durations, breath and
 * climbing. Each entry asks {@link CardHooks}, which reads the synced card cache.
 */
@Mixin(LivingEntity.class)
abstract class CardsLivingEntityMixin {
	@ModifyVariable(method = "hurtServer", at = @At("HEAD"), argsOnly = true, ordinal = 0)
	private float mettle$cards(float damage, ServerLevel level, DamageSource source) {
		return CardHooks.hurt((LivingEntity) (Object) this, source, damage);
	}

	@ModifyVariable(method = "addEffect(Lnet/minecraft/world/effect/MobEffectInstance;Lnet/minecraft/world/entity/Entity;)Z", at = @At("HEAD"),
		argsOnly = true, ordinal = 0)
	private MobEffectInstance mettle$cardsEffect(MobEffectInstance effect) {
		return CardHooks.effect((LivingEntity) (Object) this, effect);
	}

	@ModifyReturnValue(method = "getVisibilityPercent", at = @At("RETURN"))
	private double mettle$cardsUnseen(double percent, ServerLevel level, net.minecraft.world.entity.Entity looking) {
		return CardHooks.visibility((LivingEntity) (Object) this, looking, percent);
	}

	@ModifyReturnValue(method = "decreaseAirSupply", at = @At("RETURN"))
	private int mettle$cardsBreath(int next, int current) {
		return CardHooks.air((LivingEntity) (Object) this, current, next);
	}

	@ModifyReturnValue(method = "handleRelativeFrictionAndCalculateMovement", at = @At("RETURN"))
	private Vec3 mettle$cardsClimb(Vec3 movement) {
		return CardHooks.climb((LivingEntity) (Object) this, movement);
	}
}
