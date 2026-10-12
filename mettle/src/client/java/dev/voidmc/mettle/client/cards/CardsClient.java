package dev.voidmc.mettle.client.cards;

import dev.voidmc.mettle.character.cards.CardConfig;
import dev.voidmc.mettle.client.overlay.MettleCards;
import dev.voidmc.mettle.client.overlay.MettleState;
import dev.voidmc.mettle.core.net.MettlePayloads;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/** Cards v2 on the client: remembers the open rebuild sitting (a Card Shrine or a trainer) for the overlay state. */
public final class CardsClient {
	private CardsClient() {
	}

	public static void init() {
		ClientPlayNetworking.registerGlobalReceiver(MettlePayloads.CardSitting.TYPE, (payload, context) -> {
			MettleCards.sitting(payload.open() ? payload : null);
			MettleState.bump();
		});
		ClientPlayNetworking.registerGlobalReceiver(MettlePayloads.TradeView.TYPE, (payload, context) -> {
			MettleCards.trade(payload);
			MettleState.bump();
		});
		ClientPlayNetworking.registerGlobalReceiver(MettlePayloads.HallOfFirsts.TYPE, (payload, context) -> {
			MettleCards.hall(payload);
			MettleState.bump();
		});
		ClientPlayNetworking.registerGlobalReceiver(MettlePayloads.MythicReveal.TYPE, (payload, context) -> {
			MettleCards.reveal(payload);
			MettleState.bump();
		});
		// The server's card config (cards.json): the overlay and CardRules on this thread use it until the client leaves.
		ClientPlayNetworking.registerGlobalReceiver(MettlePayloads.CardsConfig.TYPE, (payload, context) -> CardConfig.receive(payload.json()));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			MettleCards.sitting(null);
			MettleCards.trade(null);
			CardConfig.forgetServer();
			MettleState.bump();
		});
		Updraft.init();
		Stride.init();
	}
}
