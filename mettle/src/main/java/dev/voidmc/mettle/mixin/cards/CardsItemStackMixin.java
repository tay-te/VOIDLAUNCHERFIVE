package dev.voidmc.mettle.mixin.cards;

import dev.voidmc.mettle.character.cards.CardHooks;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Unworn, Honed, Mending and the Iron set: wear on tools, weapons, armour and shields with a known owner. */
@Mixin(ItemStack.class)
abstract class CardsItemStackMixin {
	@ModifyVariable(method = "hurtAndBreak(ILnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/EquipmentSlot;)V",
		at = @At("HEAD"), argsOnly = true, ordinal = 0)
	private int mettle$cardsWear(int amount, int original, LivingEntity owner, EquipmentSlot slot) {
		return CardHooks.wear((ItemStack) (Object) this, amount, owner, slot);
	}
}
