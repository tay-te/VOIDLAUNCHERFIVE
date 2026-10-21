package dev.voidmc.mettle.mixin.speech;

import dev.voidmc.mettle.character.skill.speech.SpeechHooks;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.FurnaceResultSlot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Tavern-Keeper: food taken from a furnace or smoker carries the cook's name. */
@Mixin(FurnaceResultSlot.class)
abstract class SpeechFurnaceResultSlotMixin {
	@Shadow
	@Final
	private Player player;

	@Inject(method = "checkTakeAchievements", at = @At("HEAD"))
	private void mettle$cooked(ItemStack carried, CallbackInfo ci) {
		if (this.player instanceof ServerPlayer p) {
			SpeechHooks.cooked(p, carried);
		}
	}
}
