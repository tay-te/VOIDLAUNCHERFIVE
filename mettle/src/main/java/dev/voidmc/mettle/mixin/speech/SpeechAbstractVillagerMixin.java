package dev.voidmc.mettle.mixin.speech;

import dev.voidmc.mettle.character.skill.speech.SpeechHooks;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.item.trading.MerchantOffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A trade with a villager or wandering trader: Speech XP, Market Regular's tally, Barterer's restock. */
@Mixin(AbstractVillager.class)
abstract class SpeechAbstractVillagerMixin {
	@Inject(method = "notifyTrade", at = @At("TAIL"))
	private void mettle$traded(MerchantOffer offer, CallbackInfo ci) {
		if (!((AbstractVillager) (Object) this).level().isClientSide()) {
			SpeechHooks.traded((AbstractVillager) (Object) this, offer);
		}
	}
}
