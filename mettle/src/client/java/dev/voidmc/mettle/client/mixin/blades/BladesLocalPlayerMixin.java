package dev.voidmc.mettle.client.mixin.blades;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.AttackRange;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Measure (Blades) for spears on the crosshair: the client reads a spear's reach straight off the item, so route it
 * through getAttackRangeWith, where the perk stretches it the same way the server's stab does.
 */
@Mixin(LocalPlayer.class)
abstract class BladesLocalPlayerMixin {
	@ModifyExpressionValue(method = "raycastHitResult", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/world/item/ItemStack;get(Lnet/minecraft/core/component/DataComponentType;)Ljava/lang/Object;"))
	private Object mettle$measure(Object component, @Local ItemStack held) {
		return component instanceof AttackRange ? ((LocalPlayer) (Object) this).getAttackRangeWith(held) : component;
	}
}
