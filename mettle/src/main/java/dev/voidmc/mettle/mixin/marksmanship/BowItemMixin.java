package dev.voidmc.mettle.mixin.marksmanship;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.voidmc.mettle.character.skill.marksmanship.Draw;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Draw speed (Steady Aim, Quick Nock, Steppe Draw): the bow's power comes from perk-scaled draw time. Runs on both
 * sides, like vanilla's own release, so client and server agree on the shot.
 */
@Mixin(BowItem.class)
abstract class BowItemMixin {
	@WrapOperation(method = "releaseUsing", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/BowItem;getPowerForTime(I)F"))
	private float mettle$drawSpeed(int timeHeld, Operation<Float> original, @Local(argsOnly = true) LivingEntity entity) {
		return entity instanceof Player player ? Draw.power(player, timeHeld) : original.call(timeHeld);
	}

	@Inject(method = "releaseUsing", at = @At("RETURN"))
	private void mettle$loosed(ItemStack stack, Level level, LivingEntity entity, int remainingTime, CallbackInfoReturnable<Boolean> cir) {
		if (cir.getReturnValueZ() && entity instanceof Player player) {
			Draw.loosed(player, ((BowItem) (Object) this).getUseDuration(stack, entity) - remainingTime);
		}
	}
}
