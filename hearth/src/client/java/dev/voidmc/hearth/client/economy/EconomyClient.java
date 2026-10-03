package dev.voidmc.hearth.client.economy;

import dev.voidmc.hearth.economy.EconomyMenus;
import dev.voidmc.hearth.economy.bank.BankMenu;
import dev.voidmc.hearth.economy.item.PouchContents;
import dev.voidmc.hearth.economy.market.MarketMenu;
import dev.voidmc.hearth.economy.net.BankViewPayload;
import dev.voidmc.hearth.economy.net.MarketViewPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.ClientTooltipComponentCallback;
import net.minecraft.client.gui.screens.MenuScreens;

/** Client side of the economy module: the market and bank screens, their view updates, and the pouch tooltip. */
public final class EconomyClient {
	private EconomyClient() {
	}

	public static void init() {
		MenuScreens.register(EconomyMenus.MARKET, MarketScreen::new);
		MenuScreens.register(EconomyMenus.BANK, BankScreen::new);

		ClientPlayNetworking.registerGlobalReceiver(MarketViewPayload.TYPE, (payload, context) -> {
			if (context.player().containerMenu instanceof MarketMenu menu && menu.containerId == payload.containerId()) {
				menu.setView(payload.view());
			}
		});
		ClientPlayNetworking.registerGlobalReceiver(BankViewPayload.TYPE, (payload, context) -> {
			if (context.player().containerMenu instanceof BankMenu menu && menu.containerId == payload.containerId()) {
				menu.setView(payload.view());
				if (context.client().gui.screen() instanceof BankScreen screen) {
					screen.onView(payload.view());
				}
			}
		});
		ClientTooltipComponentCallback.EVENT.register(data -> data instanceof PouchContents contents ? new PouchTooltip(contents) : null);
	}
}
