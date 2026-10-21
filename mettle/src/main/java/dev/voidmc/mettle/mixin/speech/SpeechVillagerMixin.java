package dev.voidmc.mettle.mixin.speech;

import dev.voidmc.mettle.character.skill.speech.SpeechHooks;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.village.ReputationEventType;
import net.minecraft.world.entity.npc.villager.Villager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Glib without Hearth: a trade's gossip about the player grows faster. */
@Mixin(Villager.class)
abstract class SpeechVillagerMixin {
	@Inject(method = "onReputationEventFrom", at = @At("TAIL"))
	private void mettle$gossip(ReputationEventType type, Entity source, CallbackInfo ci) {
		if (type == ReputationEventType.TRADE) {
			SpeechHooks.gossip((Villager) (Object) this, source);
		}
	}
}
