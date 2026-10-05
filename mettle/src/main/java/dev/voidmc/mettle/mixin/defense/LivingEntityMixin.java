package dev.voidmc.mettle.mixin.defense;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.voidmc.mettle.character.skill.defense.DefenseRules;
import dev.voidmc.mettle.character.skill.defense.Grit;
import dev.voidmc.mettle.character.skill.defense.Ironclad;
import dev.voidmc.mettle.character.skill.defense.Shieldbearer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.component.BlocksAttacks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The Shieldbearer perks change blocking where vanilla decides it: Shield Up the raise delay (both sides, so it feels
 * instant) and knockback while blocking, Bulwark the angle a hit is judged at, Guardian the damage an ally takes. Also
 * Still Standing (a big hit cut), Shield Bash noticing a lowered shield and Unbowed shortening effects.
 */
@Mixin(LivingEntity.class)
abstract class LivingEntityMixin {
	@WrapOperation(method = "getItemBlockingWith", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/world/item/component/BlocksAttacks;blockDelayTicks()I"))
	private int mettle$shieldUp(BlocksAttacks blocks, Operation<Integer> original) {
		return Shieldbearer.blockDelay((LivingEntity) (Object) this, original.call(blocks));
	}

	@WrapOperation(method = "applyItemBlocking", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/world/item/component/BlocksAttacks;resolveBlockedDamage(Lnet/minecraft/world/damagesource/DamageSource;FD)F"))
	private float mettle$bulwark(BlocksAttacks blocks, DamageSource source, float damage, double angle, Operation<Float> original) {
		float blocked = original.call(blocks, source, damage, angle);
		LivingEntity self = (LivingEntity) (Object) this;
		if (blocked < damage && Shieldbearer.hasBulwark(self)) {
			float wider = original.call(blocks, source, damage, DefenseRules.bulwarkAngle(angle));
			if (wider > blocked) {
				Shieldbearer.bulwarkCaught(self);
				return wider;
			}
		}
		return blocked;
	}

	@Inject(method = "knockback(DDDLnet/minecraft/world/damagesource/DamageSource;FZ)V", at = @At("HEAD"), cancellable = true)
	private void mettle$holdGround(double power, double xd, double zd, DamageSource source, float damage, boolean fromEffect, CallbackInfo ci) {
		if (Shieldbearer.holdsGround((LivingEntity) (Object) this)) {
			ci.cancel();
		}
	}

	@ModifyVariable(method = "hurtServer", at = @At("HEAD"), argsOnly = true, ordinal = 0)
	private float mettle$guardAndStand(float damage, ServerLevel level, DamageSource source) {
		LivingEntity self = (LivingEntity) (Object) this;
		damage = Shieldbearer.guarded(self, source, damage);
		return self instanceof ServerPlayer player ? Grit.stillStanding(player, source, damage) : damage;
	}

	@Inject(method = "stopUsingItem", at = @At("HEAD"))
	private void mettle$shieldLowered(CallbackInfo ci) {
		Shieldbearer.lowered((LivingEntity) (Object) this);
	}

	@ModifyVariable(method = "addEffect(Lnet/minecraft/world/effect/MobEffectInstance;Lnet/minecraft/world/entity/Entity;)Z", at = @At("HEAD"),
		argsOnly = true, ordinal = 0)
	private MobEffectInstance mettle$unbowed(MobEffectInstance effect) {
		return Ironclad.effect((LivingEntity) (Object) this, effect);
	}
}
