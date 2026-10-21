package dev.voidmc.fray.combat.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.voidmc.fray.api.StatusType;
import dev.voidmc.fray.combat.DamageProfiles;
import dev.voidmc.fray.combat.Defense;
import dev.voidmc.fray.combat.Statuses;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Fray's hooks on vanilla's damage path (CONTRACTS.md §4.2): foreign hits on players meet the defender's guard and
 * grace; foreign damage runs only the resistance step (by damage profile); vanilla's shield blocking stands down for
 * players (Fray's guard blocks); Hex cuts healing.
 */
@Mixin(LivingEntity.class)
abstract class LivingEntityMixin {
	@WrapMethod(method = "hurtServer")
	private boolean fray$guard(ServerLevel level, DamageSource source, float damage, Operation<Boolean> original) {
		if ((Object) this instanceof ServerPlayer p && !Defense.replaying) {
			float d = Defense.INSTANCE.foreignHit(p, source, damage, level);
			if (d <= 0 && damage > 0) {
				return false;
			}
			return original.call(level, source, d);
		}
		return original.call(level, source, damage);
	}

	@Inject(method = "getDamageAfterMagicAbsorb", at = @At("RETURN"), cancellable = true)
	private void fray$resist(DamageSource source, float damage, CallbackInfoReturnable<Float> cir) {
		float v = cir.getReturnValueF();
		float r = DamageProfiles.apply((LivingEntity) (Object) this, source, v);
		if (r != v) {
			cir.setReturnValue(r);
		}
	}

	@Inject(method = "applyItemBlocking", at = @At("HEAD"), cancellable = true)
	private void fray$noVanillaBlock(ServerLevel level, DamageSource source, float damage, CallbackInfoReturnable<Float> cir) {
		if ((Object) this instanceof ServerPlayer) {
			cir.setReturnValue(0F);
		}
	}

	@ModifyVariable(method = "heal", at = @At("HEAD"), argsOnly = true)
	private float fray$hex(float heal) {
		LivingEntity self = (LivingEntity) (Object) this;
		return !self.level().isClientSide() && Statuses.INSTANCE.has(self, StatusType.HEX) ? heal * 0.75F : heal;
	}
}
