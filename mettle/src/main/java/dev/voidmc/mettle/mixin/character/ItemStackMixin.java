package dev.voidmc.mettle.mixin.character;

import dev.voidmc.mettle.character.skill.SmithingSkill;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Smithing XP: crafting tables, furnaces and smithing tables all report what a player made through onCraftedBy. */
@Mixin(ItemStack.class)
abstract class ItemStackMixin {
	@Inject(method = "onCraftedBy", at = @At("TAIL"))
	private void mettle$smithingXp(Player player, int count, CallbackInfo ci) {
		if (player instanceof ServerPlayer sp) {
			SmithingSkill.crafted(sp, (ItemStack) (Object) this, count);
		}
	}
}
