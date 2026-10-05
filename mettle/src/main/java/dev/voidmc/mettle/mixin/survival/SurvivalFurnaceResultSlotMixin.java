package dev.voidmc.mettle.mixin.survival;

import dev.voidmc.mettle.character.skill.survival.Food;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.FurnaceResultSlot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Survival XP for food taken out of a furnace, smoker or blast furnace (by click or shift-click). */
@Mixin(FurnaceResultSlot.class)
abstract class SurvivalFurnaceResultSlotMixin {
	@Shadow
	@Final
	private Player player;

	@Shadow
	private int removeCount;

	@Inject(method = "checkTakeAchievements", at = @At("HEAD"))
	private void mettle$cooked(ItemStack carried, CallbackInfo ci) {
		Food.taken(this.player, carried, this.removeCount);
	}
}
