package dev.voidmc.mettle.mixin.survival;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.voidmc.mettle.character.skill.survival.Food;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Field Cook and Living off the Land: the food a player eats fills them more. */
@Mixin(FoodProperties.class)
abstract class SurvivalFoodPropertiesMixin {
	@WrapOperation(method = "onConsume", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/food/FoodData;eat(Lnet/minecraft/world/food/FoodProperties;)V"))
	private void mettle$fill(FoodData data, FoodProperties food, Operation<Void> original, Level level, LivingEntity user, ItemStack stack, Consumable consumable) {
		original.call(data, user instanceof Player player ? Food.fill(player, stack, food) : food);
	}
}
