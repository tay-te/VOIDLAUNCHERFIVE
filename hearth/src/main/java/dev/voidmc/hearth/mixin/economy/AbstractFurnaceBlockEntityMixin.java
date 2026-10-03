package dev.voidmc.hearth.mixin.economy;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import dev.voidmc.hearth.economy.coin.CoinMelting;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Melting coin: the recipes in {@code data/hearth/recipe/melt_*.json} let furnaces take coins; how many nuggets come
 * out is the config's melting yield, applied here so a server owner can change it without a datapack.
 */
@Mixin(AbstractFurnaceBlockEntity.class)
abstract class AbstractFurnaceBlockEntityMixin {
	@ModifyExpressionValue(method = "serverTick", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/world/item/crafting/AbstractCookingRecipe;assemble(Lnet/minecraft/world/item/crafting/SingleRecipeInput;)Lnet/minecraft/world/item/ItemStack;"))
	private static ItemStack hearth$meltingYield(ItemStack result, @Local SingleRecipeInput input) {
		return CoinMelting.yield(input.item(), result);
	}
}
