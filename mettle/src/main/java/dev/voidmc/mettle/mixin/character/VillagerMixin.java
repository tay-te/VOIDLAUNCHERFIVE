package dev.voidmc.mettle.mixin.character;

import dev.voidmc.mettle.character.signature.SignaturePerks;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.trading.MerchantOffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Coin Sense (Merchant), vanilla half: villagers knock a tenth (at least one) off the price for a Merchant, applied with
 * the other special prices so the trade screen shows it struck through. Hearth's markets get their share through the
 * Hearth price hook (INTEGRATION.md §1).
 */
@Mixin(Villager.class)
abstract class VillagerMixin {
	@Inject(method = "updateSpecialPrices", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/world/entity/npc/villager/Villager;getTradingPlayer()Lnet/minecraft/world/entity/player/Player;"))
	private void mettle$coinSense(Player player, CallbackInfo ci) {
		if (!SignaturePerks.has(player, SignaturePerks.COIN_SENSE)) {
			return;
		}
		for (MerchantOffer offer : ((Villager) (Object) this).getOffers()) {
			int cut = Math.max(1, offer.getBaseCostA().getCount() / 10);
			offer.addToSpecialPriceDiff(-cut);
		}
	}
}
