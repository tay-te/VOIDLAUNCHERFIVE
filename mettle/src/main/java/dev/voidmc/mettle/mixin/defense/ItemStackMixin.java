package dev.voidmc.mettle.mixin.defense;

import dev.voidmc.mettle.character.skill.defense.Ironclad;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Mender and Battle-Worn: wear on worn armour and shields. Hooked where an owner and slot are known (armour hits,
 * shield blocks and thorns all come through here), before Unbreaking, which can only lower it further.
 */
@Mixin(ItemStack.class)
abstract class ItemStackMixin {
	@ModifyVariable(method = "hurtAndBreak(ILnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/EquipmentSlot;)V",
		at = @At("HEAD"), argsOnly = true, ordinal = 0)
	private int mettle$wear(int amount, int original, LivingEntity owner, EquipmentSlot slot) {
		return Ironclad.wear((ItemStack) (Object) this, amount, owner, slot);
	}
}
