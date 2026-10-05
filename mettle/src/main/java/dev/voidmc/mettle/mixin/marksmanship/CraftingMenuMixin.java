package dev.voidmc.mettle.mixin.marksmanship;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import dev.voidmc.mettle.character.skill.marksmanship.Ammo;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Fletcher: the crafting table's result slot shows (and gives) the bigger stack. The server fills that slot and syncs
 * it, so what the player sees is what they get, shift-click crafting included.
 */
@Mixin(CraftingMenu.class)
abstract class CraftingMenuMixin {
	@ModifyExpressionValue(method = "slotChangedCraftingGrid", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/world/item/crafting/CraftingRecipe;assemble(Lnet/minecraft/world/item/crafting/RecipeInput;)Lnet/minecraft/world/item/ItemStack;"))
	private static ItemStack mettle$fletcher(ItemStack result, @Local(argsOnly = true) Player player) {
		return player instanceof ServerPlayer sp ? Ammo.fletcher(sp, result) : result;
	}
}
