package dev.voidmc.hearth.economy.net;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

/** The economy's payloads: screen clicks up, refreshed views down. */
public final class EconomyNetwork {
	private EconomyNetwork() {
	}

	public static void init() {
		PayloadTypeRegistry.serverboundPlay().register(EconomyActionPayload.TYPE, EconomyActionPayload.STREAM_CODEC);
		PayloadTypeRegistry.clientboundPlay().register(MarketViewPayload.TYPE, MarketViewPayload.STREAM_CODEC);
		PayloadTypeRegistry.clientboundPlay().register(BankViewPayload.TYPE, BankViewPayload.STREAM_CODEC);
		ServerPlayNetworking.registerGlobalReceiver(EconomyActionPayload.TYPE, (payload, context) -> context.server().execute(() -> {
			var player = context.player();
			if (player.containerMenu.containerId == payload.containerId() && player.containerMenu instanceof EconomyMenu menu
				&& player.containerMenu.stillValid(player)) {
				menu.handle(player, payload);
			}
		}));
	}
}
