package dev.voidmc.mettle.mixin.blades;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.sugar.Local;
import dev.voidmc.mettle.character.skill.BladesSkill;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The melee swing, for the Blades perks: which foe it was aimed at, forced criticals (riposte, Unbroken, Weapon Master,
 * Master Strike), the on-crit perks once it lands, Whirlwind after it, Light Feet's kept sprint, and a full charge that
 * survives the swing packet after a kill (Cut and Run). Spears stab through {@code stabAttack} and only meet the
 * damage-side perks.
 */
@Mixin(Player.class)
abstract class BladesPlayerMixin {
	@Inject(method = "attack", at = @At("HEAD"))
	private void mettle$beginSwing(Entity target, CallbackInfo ci) {
		BladesSkill.beginSwing((Player) (Object) this, target);
	}

	// Vanilla asks this only for a full swing, so forced criticals need a charged swing just like natural ones.
	@ModifyExpressionValue(method = "attack", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/world/entity/player/Player;canCriticalAttack(Lnet/minecraft/world/entity/Entity;)Z"))
	private boolean mettle$critical(boolean natural, @Local(argsOnly = true) Entity target) {
		return BladesSkill.critical((Player) (Object) this, target, natural);
	}

	@ModifyArg(method = "attack", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/world/entity/Entity;hurtOrSimulate(Lnet/minecraft/world/damagesource/DamageSource;F)Z"), index = 1)
	private float mettle$swingDamage(float damage) {
		BladesSkill.swingDamage((Player) (Object) this, damage);
		return damage;
	}

	@Inject(method = "attack", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/world/entity/player/Player;attackVisualEffects(Lnet/minecraft/world/entity/Entity;ZZZZF)V"))
	private void mettle$landed(Entity target, CallbackInfo ci) {
		BladesSkill.landed((Player) (Object) this, target);
	}

	@Inject(method = "attack", at = @At("TAIL"))
	private void mettle$endSwing(Entity target, CallbackInfo ci) {
		BladesSkill.endSwing((Player) (Object) this, target);
	}

	@WrapWithCondition(method = "causeExtraKnockback", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/world/entity/player/Player;setSprinting(Z)V"))
	private boolean mettle$lightFeetSprint(Player self, boolean sprinting) {
		return !BladesSkill.keepsSprint(self, true);
	}

	@WrapWithCondition(method = "causeExtraKnockback", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/world/entity/player/Player;setDeltaMovement(Lnet/minecraft/world/phys/Vec3;)V"))
	private boolean mettle$lightFeetPace(Player self, Vec3 movement) {
		return !BladesSkill.keepsSprint(self, false);
	}

	@Inject(method = "resetAttackStrengthTicker", at = @At("HEAD"), cancellable = true)
	private void mettle$keepCharge(CallbackInfo ci) {
		if (BladesSkill.keepCharge((Player) (Object) this)) {
			ci.cancel();
		}
	}
}
