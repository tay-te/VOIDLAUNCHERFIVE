package dev.voidmc.mettle.mixin.smithing;

import dev.voidmc.mettle.character.skill.smithing.Forging;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Craft stamps. Item#onCraftedBy is the one call every take path makes on the piece itself: a click on the result slot
 * (through ItemStack#onCraftedBy), a shift-click (directly, before the move), a smithing table upgrade.
 */
@Mixin(Item.class)
abstract class SmithingItemMixin {
	@Inject(method = "onCraftedBy", at = @At("HEAD"))
	private void mettle$stamp(ItemStack stack, Player player, CallbackInfo ci) {
		if (player instanceof ServerPlayer sp) {
			Forging.onCrafted(sp, stack);
		}
	}
}
