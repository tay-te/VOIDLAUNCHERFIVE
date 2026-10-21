package dev.voidmc.mettle.mixin.speech;

import dev.voidmc.mettle.character.skill.speech.SpeechHooks;
import java.util.List;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.entity.monster.piglin.PiglinAi;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A piglin barter: Speech XP for the player it throws to; Barterer and Bartering add one more item. */
@Mixin(PiglinAi.class)
abstract class SpeechPiglinAiMixin {
	@Inject(method = "getBarterResponseItems", at = @At("RETURN"), cancellable = true)
	private static void mettle$bartered(Piglin body, CallbackInfoReturnable<List<ItemStack>> cir) {
		cir.setReturnValue(SpeechHooks.bartered(body, cir.getReturnValue()));
	}
}
