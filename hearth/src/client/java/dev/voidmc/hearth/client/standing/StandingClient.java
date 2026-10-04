package dev.voidmc.hearth.client.standing;

import com.mojang.blaze3d.platform.InputConstants;
import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.standing.net.StandingPayloads;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;

/**
 * Client side of the standing module: the standing line under the arrival banner, the standing line on the
 * conversation and trade screens, and the reputation page (the K key, or {@code /hearth standing}). The client decides
 * nothing; it shows what the server sends.
 */
public final class StandingClient {
	private static KeyMapping reputationKey;

	private StandingClient() {
	}

	public static void init() {
		reputationKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.hearth.standing", InputConstants.KEY_K,
			KeyMapping.Category.register(Hearth.id("standing"))));
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			while (reputationKey.consumeClick()) {
				if (mc.player != null && mc.gui.screen() == null && ClientPlayNetworking.canSend(StandingPayloads.RequestPage.TYPE)) {
					ClientPlayNetworking.send(StandingPayloads.RequestPage.INSTANCE);
				}
			}
		});
		ClientPlayNetworking.registerGlobalReceiver(StandingPayloads.Here.TYPE, (payload, context) -> ClientStanding.receive(payload));
		ClientPlayNetworking.registerGlobalReceiver(StandingPayloads.Talk.TYPE, (payload, context) -> ClientStanding.receive(payload));
		ClientPlayNetworking.registerGlobalReceiver(StandingPayloads.Page.TYPE, (payload, context) -> {
			Minecraft mc = context.client();
			if (mc.gui.screen() instanceof ReputationScreen open) {
				open.update(payload);
			} else {
				mc.gui.setScreen(new ReputationScreen(payload));
			}
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ClientStanding.reset());
		HudElementRegistry.addLast(Hearth.id("standing/arrival"), StandingHud::extract);
		ScreenLine.install();
		StandingClientTour.install();
	}

	static boolean isReputationKey(KeyEvent event) {
		return reputationKey != null && reputationKey.matches(event);
	}
}
