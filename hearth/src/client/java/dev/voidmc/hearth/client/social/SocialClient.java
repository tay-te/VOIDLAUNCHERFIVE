package dev.voidmc.hearth.client.social;

import dev.voidmc.hearth.social.dice.DiceMenu;
import dev.voidmc.hearth.social.dice.DicePayloads;
import dev.voidmc.hearth.social.dice.DiceTables;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.screens.MenuScreens;

/** Client side of the social module: the dice table. */
public final class SocialClient {
	private SocialClient() {
	}

	public static void init() {
		MenuScreens.register(DiceTables.MENU, DiceScreen::new);
		ClientPlayNetworking.registerGlobalReceiver(DicePayloads.View.TYPE, (payload, context) -> {
			if (context.player().containerMenu instanceof DiceMenu menu && menu.containerId == payload.containerId()) {
				menu.setView(payload.view());
				dev.voidmc.hearth.client.overlay.HearthViews.bump();
				if (context.client().gui.screen() instanceof DiceScreen screen) {
					screen.onView(payload.view());
				}
			}
		});
		SocialDevPilot.initIfRequested();
	}
}
