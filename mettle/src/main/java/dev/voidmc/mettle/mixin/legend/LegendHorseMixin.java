package dev.voidmc.mettle.mixin.legend;

import dev.voidmc.mettle.character.cards.legend.LegendWild;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The Beastspeaker's Torc: horses, donkeys, mules and llamas eat by their own path (not Mob#usePlayerItem). */
@Mixin(AbstractHorse.class)
abstract class LegendHorseMixin {
	@Inject(method = "fedFood", at = @At("RETURN"))
	private void mettle$legendFed(Player player, ItemStack stack, CallbackInfoReturnable<InteractionResult> cir) {
		if (!player.level().isClientSide() && cir.getReturnValue() != InteractionResult.PASS) {
			LegendWild.fed((AbstractHorse) (Object) this, player);
		}
	}
}
