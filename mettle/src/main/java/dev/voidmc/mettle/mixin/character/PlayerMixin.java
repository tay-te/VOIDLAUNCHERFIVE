package dev.voidmc.mettle.mixin.character;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.voidmc.mettle.character.signature.SignaturePerks;
import dev.voidmc.mettle.character.skill.MiningSkill;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Well Read (Scholar): enchanting costs one level less (a two- or three-level enchant gives one level back). Mining's
 * speed perks (Quarryman, Sure Footing, Deep Delver) adjust the dig speed on both sides, so breaking stays predicted.
 */
@Mixin(Player.class)
abstract class PlayerMixin {
	// ModifyReturnValue chains with other trees' dig-speed hooks; a cancellable inject would end the method early.
	@ModifyReturnValue(method = "getDestroySpeed", at = @At("RETURN"))
	private float mettle$miningSpeed(float speed, BlockState state) {
		return MiningSkill.destroySpeed((Player) (Object) this, state, speed);
	}

	@Inject(method = "onEnchantmentPerformed", at = @At("TAIL"))
	private void mettle$wellRead(ItemStack stack, int cost, CallbackInfo ci) {
		if ((Object) this instanceof ServerPlayer player && cost > 1 && SignaturePerks.has(player, SignaturePerks.WELL_READ)) {
			player.giveExperienceLevels(1);
			SignaturePerks.feedback(player, SignaturePerks.WELL_READ, Component.translatable("mettle.feedback.well_read"));
		}
	}
}
