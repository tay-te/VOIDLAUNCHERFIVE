package dev.voidmc.mettle.mixin.heavyweapons;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.voidmc.mettle.character.skill.heavy.HeavyHit;
import dev.voidmc.mettle.character.skill.heavy.HeavyRules;
import dev.voidmc.mettle.character.skill.heavy.Strikes;
import dev.voidmc.mettle.character.skill.heavy.Woodcraft;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Heavy Weapons on the player. Dig speed and "right tool" (Feller, Rhythm of the Axe, Stonebreaker) on both sides, so
 * breaking stays predicted. On the server, a melee swing is followed from start to end ({@link HeavyHit}): vanilla
 * decides charge, critical and smash inside {@code attack} and keeps them to itself, and the perks that act after the
 * hit need them; Overhead Blow swaps the critical's ×1.5 for ×2.
 */
@Mixin(Player.class)
abstract class PlayerMixin {
	// Not at RETURN: Mining's cancellable RETURN inject sets the value and returns there, so anything later at that
	// return never runs. The speed is scaled just before vanilla's airborne penalty instead (all factors multiply).
	@ModifyVariable(method = "getDestroySpeed", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;onGround()Z"),
		ordinal = 0)
	private float mettle$heavyDigSpeed(float speed, BlockState state) {
		return Woodcraft.destroySpeed((Player) (Object) this, state, speed);
	}

	@ModifyReturnValue(method = "hasCorrectToolForDrops", at = @At("RETURN"))
	private boolean mettle$heavyStonebreaker(boolean correct, BlockState state) {
		return correct || Woodcraft.stonebreaks((Player) (Object) this, state);
	}

	@Inject(method = "attack", at = @At("HEAD"))
	private void mettle$heavyBeginSwing(Entity target, CallbackInfo ci) {
		if ((Object) this instanceof ServerPlayer player) {
			HeavyHit.begin(player, target);
		}
	}

	@WrapOperation(method = "attack", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/world/entity/player/Player;canCriticalAttack(Lnet/minecraft/world/entity/Entity;)Z"))
	private boolean mettle$heavyNoteCrit(Player self, Entity target, Operation<Boolean> original) {
		boolean crit = original.call(self, target);
		if (self instanceof ServerPlayer player) {
			HeavyHit hit = HeavyHit.of(player, target);
			if (hit != null) {
				hit.crit = crit;
			}
		}
		return crit;
	}

	@ModifyConstant(method = "attack", constant = @Constant(floatValue = 1.5F))
	private float mettle$heavyOverheadBlow(float multiplier) {
		if ((Object) this instanceof ServerPlayer player) {
			HeavyHit hit = HeavyHit.current();
			if (hit != null && hit.player == player && Strikes.overhead(hit)) {
				hit.overhead = true;
				return HeavyRules.OVERHEAD_CRIT;
			}
		}
		return multiplier;
	}

	@WrapOperation(method = "attack", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/world/entity/Entity;hurtOrSimulate(Lnet/minecraft/world/damagesource/DamageSource;F)Z"))
	private boolean mettle$heavyNoteHurt(Entity target, DamageSource source, float damage, Operation<Boolean> original) {
		boolean hurt = original.call(target, source, damage);
		if ((Object) this instanceof ServerPlayer player) {
			HeavyHit hit = HeavyHit.of(player, target);
			if (hit != null) {
				hit.hurt = hurt;
			}
		}
		return hurt;
	}

	@Inject(method = "attack", at = @At("TAIL"))
	private void mettle$heavyEndSwing(Entity target, CallbackInfo ci) {
		if ((Object) this instanceof ServerPlayer player) {
			HeavyHit hit = HeavyHit.end(player);
			if (hit != null) {
				Strikes.afterAttack(hit);
			}
		}
	}
}
