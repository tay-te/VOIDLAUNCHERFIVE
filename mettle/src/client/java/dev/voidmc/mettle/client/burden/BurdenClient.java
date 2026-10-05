package dev.voidmc.mettle.client.burden;

import dev.voidmc.mettle.Mettle;
import dev.voidmc.mettle.burden.WeightTable;
import dev.voidmc.mettle.burden.Weights;
import dev.voidmc.mettle.burden.WeightsPayload;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;

/**
 * Burden on the client: the weight table from the server, the weight line on every tooltip, and a plain text line by
 * the hotbar while Burdened or worse (a dev fallback until the overlay's HUD chip; OVERLAY.md).
 */
public final class BurdenClient {
	private BurdenClient() {
	}

	public static void init() {
		ClientPlayNetworking.registerGlobalReceiver(WeightsPayload.TYPE, (payload, context) -> Weights.setClient(new WeightTable(payload.weights(), null)));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, mc) -> Weights.setClient(WeightTable.EMPTY));
		ItemTooltipCallback.EVENT.register(BurdenTooltip::add);
		HudElementRegistry.addLast(Mettle.id("burden/load"), BurdenHud::extract);
	}
}
