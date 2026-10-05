package dev.voidmc.mettle.mixin.smithing;

import dev.voidmc.mettle.character.skill.smithing.Forging;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Thrifty: once per craft taken (click, shift-click or swap), while the ingredients are still in the grid. */
@Mixin(ResultSlot.class)
abstract class SmithingResultSlotMixin {
	@Shadow
	@Final
	private CraftingContainer craftSlots;

	@Shadow
	@Final
	private Player player;

	@Shadow
	private int removeCount;

	@Inject(method = "checkTakeAchievements", at = @At("HEAD"))
	private void mettle$thrifty(ItemStack carried, CallbackInfo ci) {
		if (this.removeCount > 0 && this.player instanceof ServerPlayer sp) {
			Forging.thrifty(sp, this.craftSlots, carried);
		}
	}
}
