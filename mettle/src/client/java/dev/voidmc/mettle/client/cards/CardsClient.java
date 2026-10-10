package dev.voidmc.mettle.client.cards;

import dev.voidmc.mettle.client.overlay.MettleCards;
import dev.voidmc.mettle.core.net.MettlePayloads;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/** Cards v2 on the client: remembers the open rebuild sitting (a Card Shrine or a trainer) for the overlay state. */
public final class CardsClient {
	private CardsClient() {
	}

	public static void init() {
		ClientPlayNetworking.registerGlobalReceiver(MettlePayloads.CardSitting.TYPE, (payload, context) -> MettleCards.sitting(payload.open() ? payload : null));
		ClientPlayNetworking.registerGlobalReceiver(MettlePayloads.TradeView.TYPE, (payload, context) -> MettleCards.trade(payload));
		ClientPlayNetworking.registerGlobalReceiver(MettlePayloads.HallOfFirsts.TYPE, (payload, context) -> MettleCards.hall(payload));
		ClientPlayNetworking.registerGlobalReceiver(MettlePayloads.MythicReveal.TYPE, (payload, context) -> MettleCards.reveal(payload));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			MettleCards.sitting(null);
			MettleCards.trade(null);
		});
		Updraft.init();
		Stride.init();
	}
}
