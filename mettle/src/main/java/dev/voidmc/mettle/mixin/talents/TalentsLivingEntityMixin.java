package dev.voidmc.mettle.mixin.talents;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.voidmc.mettle.character.cards.TalentHooks;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/** Quick Hands (a talent): food and drink take less time to finish. Both sides, so the client's bar matches. */
@Mixin(LivingEntity.class)
abstract class TalentsLivingEntityMixin {
	@Shadow
	protected ItemStack useItem;

	@ModifyExpressionValue(method = "startUsingItem", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/world/item/ItemStack;getUseDuration(Lnet/minecraft/world/entity/LivingEntity;)I"))
	private int mettle$quickHands(int duration) {
		return TalentHooks.useDuration((LivingEntity) (Object) this, this.useItem, duration);
	}

	@ModifyExpressionValue(method = "onSyncedDataUpdated", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/world/item/ItemStack;getUseDuration(Lnet/minecraft/world/entity/LivingEntity;)I"))
	private int mettle$quickHandsSynced(int duration) {
		return TalentHooks.useDuration((LivingEntity) (Object) this, this.useItem, duration);
	}
}
