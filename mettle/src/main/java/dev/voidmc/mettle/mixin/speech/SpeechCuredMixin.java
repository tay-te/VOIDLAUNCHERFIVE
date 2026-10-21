package dev.voidmc.mettle.mixin.speech;

import dev.voidmc.mettle.character.skill.speech.SpeechHooks;
import net.minecraft.advancements.triggers.CuredZombieVillagerTrigger;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.npc.villager.Villager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A zombie villager cured by the player: Speech XP (and Golden Mouth's tally without Hearth). */
@Mixin(CuredZombieVillagerTrigger.class)
abstract class SpeechCuredMixin {
	@Inject(method = "trigger", at = @At("HEAD"))
	private void mettle$cured(ServerPlayer player, Zombie zombie, Villager villager, CallbackInfo ci) {
		SpeechHooks.cured(player);
	}
}
