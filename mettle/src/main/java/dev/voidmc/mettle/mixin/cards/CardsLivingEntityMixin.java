package dev.voidmc.mettle.mixin.cards;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.voidmc.mettle.character.cards.CardHooks;
import dev.voidmc.mettle.character.cards.MythicEffects;
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

	/** The hit got past invulnerability, fire resistance and the hurt frames: what it does besides damage happens now. */
	@org.spongepowered.asm.mixin.injection.Inject(method = "hurtServer", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/world/entity/LivingEntity;actuallyHurt(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;F)V"))
	private void mettle$cardsLanded(ServerLevel level, DamageSource source, float damage,
		org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Boolean> ci) {
		CardHooks.landed((LivingEntity) (Object) this);
	}

	@org.spongepowered.asm.mixin.injection.Inject(method = "hurtServer", at = @At("RETURN"))
	private void mettle$cardsSettled(ServerLevel level, DamageSource source, float damage,
		org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Boolean> ci) {
		CardHooks.settled((LivingEntity) (Object) this);
	}

	/** Ironskin (Iron 6) works after armour. */
	@ModifyReturnValue(method = "getDamageAfterMagicAbsorb", at = @At("RETURN"))
	private float mettle$cardsIronskin(float damage, DamageSource source) {
		return CardHooks.afterArmour((LivingEntity) (Object) this, source, damage);
	}

	/** A jump never reaches two blocks from attributes (Springing, Gale 4, Spring): walls stay walls. */
	@ModifyReturnValue(method = "getJumpPower(F)F", at = @At("RETURN"))
	private float mettle$cardsJump(float power) {
		return CardHooks.jumpPower((LivingEntity) (Object) this, power);
	}

	@ModifyVariable(method = "addEffect(Lnet/minecraft/world/effect/MobEffectInstance;Lnet/minecraft/world/entity/Entity;)Z", at = @At("HEAD"),
		argsOnly = true, ordinal = 0)
	private MobEffectInstance mettle$cardsEffect(MobEffectInstance effect) {
		return CardHooks.effect((LivingEntity) (Object) this, effect);
	}

	@ModifyReturnValue(method = "canBeAffected", at = @At("RETURN"))
	private boolean mettle$witherbane(boolean original, MobEffectInstance effect) {
		return MythicEffects.canBeAffected((LivingEntity) (Object) this, effect, original);
	}

	@org.spongepowered.asm.mixin.injection.Inject(method = "dropExperience", at = @At("HEAD"), cancellable = true)
	private void mettle$pardonXp(ServerLevel level, net.minecraft.world.entity.Entity killer, org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
		if (MythicEffects.pardoned((LivingEntity) (Object) this)) {
			ci.cancel();
		}
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
