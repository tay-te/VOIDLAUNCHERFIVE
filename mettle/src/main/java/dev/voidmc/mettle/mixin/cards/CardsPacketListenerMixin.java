package dev.voidmc.mettle.mixin.cards;

import dev.voidmc.mettle.character.cards.CardHooks;
import net.minecraft.network.protocol.game.ServerboundPunchPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Rally Cry (Kin 6): an empty swing at the air reaches the server as a punch. */
@Mixin(ServerGamePacketListenerImpl.class)
abstract class CardsPacketListenerMixin {
	@Shadow
	public ServerPlayer player;

	@Inject(method = "handlePunch", at = @At("TAIL"))
	private void mettle$rallyCry(ServerboundPunchPacket packet, CallbackInfo ci) {
		CardHooks.punched(this.player);
	}
}
