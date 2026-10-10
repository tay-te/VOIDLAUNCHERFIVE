package dev.voidmc.mettle.mixin.cards;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.voidmc.mettle.character.cards.CardHooks;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Card traits on the player: dig speed (both sides, so breaking stays predicted) and hunger. */
@Mixin(Player.class)
abstract class CardsPlayerMixin {
	@ModifyReturnValue(method = "getDestroySpeed", at = @At("RETURN"))
	private float mettle$cardsDig(float speed, BlockState state) {
		return CardHooks.destroySpeed((Player) (Object) this, state, speed);
	}

	@ModifyVariable(method = "causeFoodExhaustion", at = @At("HEAD"), argsOnly = true, ordinal = 0)
	private float mettle$cardsHunger(float amount) {
		return CardHooks.exhaustion((Player) (Object) this, amount);
	}
}
