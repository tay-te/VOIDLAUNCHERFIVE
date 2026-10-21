package dev.voidmc.mettle.mixin.legend;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.voidmc.mettle.character.cards.legend.LegendCombat;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.phys.EntityHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Hunter's Moon and the Pinning Star read an arrow's hit as it lands: its own damage (before the cards, with its draw's
 * critical and the bow's enchantments) and where on the target it struck.
 */
@Mixin(AbstractArrow.class)
abstract class LegendArrowMixin {
	@WrapOperation(method = "onHitEntity", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/world/entity/Entity;hurtOrSimulate(Lnet/minecraft/world/damagesource/DamageSource;F)Z"))
	private boolean mettle$legendArrowHit(Entity target, DamageSource source, float damage, Operation<Boolean> original,
		@Local(argsOnly = true) EntityHitResult hit) {
		LegendCombat.arrowHits((AbstractArrow) (Object) this, target, damage, hit.getLocation());
		try {
			return original.call(target, source, damage);
		} finally {
			LegendCombat.arrowDone();
		}
	}
}
