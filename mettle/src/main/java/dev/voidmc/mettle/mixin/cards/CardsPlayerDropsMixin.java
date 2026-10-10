package dev.voidmc.mettle.mixin.cards;

import dev.voidmc.mettle.character.cards.MythicEffects;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Hundredth Dawn's Pardon (a Mythic): a pardoned death drops nothing; the respawn gets it all back. */
@Mixin(Player.class)
abstract class CardsPlayerDropsMixin {
	@Inject(method = "dropEquipment", at = @At("HEAD"), cancellable = true)
	private void mettle$pardonItems(ServerLevel level, CallbackInfo ci) {
		if (MythicEffects.pardoned((Player) (Object) this)) {
			ci.cancel();
		}
	}
}
