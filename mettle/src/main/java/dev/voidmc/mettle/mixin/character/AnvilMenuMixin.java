package dev.voidmc.mettle.mixin.character;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.voidmc.mettle.character.signature.SignaturePerks;
import dev.voidmc.mettle.character.skill.SmithingSkill;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AnvilMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Smith's Eye (Smith): the anvil never refuses a job as "Too Expensive". Vanilla only lifts the 40-level cap for players
 * with infinite materials; for a Smith that one check reads true. They still pay every level. Every anvil job also
 * gives Smithing XP.
 */
@Mixin(AnvilMenu.class)
abstract class AnvilMenuMixin {
	@ModifyExpressionValue(method = "createResult", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/world/entity/player/Player;hasInfiniteMaterials()Z", ordinal = 1))
	private boolean mettle$smithsEye(boolean infinite) {
		Player player = ((ItemCombinerMenuAccessor) this).mettle$player();
		return infinite || SignaturePerks.has(player, SignaturePerks.SMITHS_EYE);
	}

	@Inject(method = "onTake", at = @At("HEAD"))
	private void mettle$smithingXp(Player player, ItemStack carried, CallbackInfo ci) {
		if (player instanceof ServerPlayer sp && !player.hasInfiniteMaterials()) {
			SmithingSkill.anvil(sp, ((AnvilMenu) (Object) this).getCost());
		}
	}
}
