package dev.voidmc.mettle.mixin.legend;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import dev.voidmc.mettle.character.cards.legend.LegendCraft;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** The Ever-Ember: a furnace its holder last filled cooks each item in half the time (the one place vanilla times it). */
@Mixin(AbstractFurnaceBlockEntity.class)
abstract class LegendFurnaceMixin {
	@ModifyReturnValue(method = "getTotalCookTime(Lnet/minecraft/world/item/crafting/RecipeHolder;Lnet/minecraft/world/level/block/entity/AbstractFurnaceBlockEntity;)I",
		at = @At("RETURN"))
	private static int mettle$everEmber(int ticks, @Local(argsOnly = true) AbstractFurnaceBlockEntity furnace) {
		return LegendCraft.cookTime(furnace, ticks);
	}
}
