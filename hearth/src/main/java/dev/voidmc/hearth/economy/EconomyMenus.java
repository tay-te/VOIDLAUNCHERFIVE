package dev.voidmc.hearth.economy;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.economy.bank.Bank;
import dev.voidmc.hearth.economy.bank.BankMenu;
import dev.voidmc.hearth.economy.bank.BankView;
import dev.voidmc.hearth.economy.market.Market;
import dev.voidmc.hearth.economy.market.MarketMenu;
import dev.voidmc.hearth.economy.market.MarketView;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuType;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.phys.Vec3;

/** The market and bank menus, and opening them for a player at a settlement. */
public final class EconomyMenus {
	public static final ExtendedMenuType<MarketMenu, MarketView> MARKET = Registry.register(BuiltInRegistries.MENU, Hearth.id("market"),
		new ExtendedMenuType<>(MarketMenu::new, MarketView.STREAM_CODEC));
	public static final ExtendedMenuType<BankMenu, BankView> BANK = Registry.register(BuiltInRegistries.MENU, Hearth.id("bank"),
		new ExtendedMenuType<>(BankMenu::new, BankView.STREAM_CODEC));

	private EconomyMenus() {
	}

	public static void init() {
	}

	/** Opens the settlement's market for the player. {@code anchored}: closes if they walk away (false for dev tools). */
	public static void openMarket(ServerPlayer player, Settlement settlement, boolean anchored) {
		Market market = new Market(settlement);
		MarketView view = market.view(player);
		Vec3 anchor = anchored ? player.position() : null;
		player.openMenu(new ExtendedMenuProvider<MarketView>() {
			@Override
			public MarketView getScreenOpeningData(ServerPlayer p) {
				return view;
			}

			@Override
			public Component getDisplayName() {
				return Component.translatable("hearth.economy.market.title", settlement.name());
			}

			@Override
			public AbstractContainerMenu createMenu(int id, Inventory inventory, Player p) {
				return new MarketMenu(id, inventory, view, market, anchor);
			}
		});
	}

	/** Opens the bank counter (or, with {@code hasBank} false, only its property tab: a town hall). */
	public static void openBank(ServerPlayer player, Settlement settlement, boolean hasBank, int tab, boolean anchored) {
		Bank bank = new Bank(settlement, hasBank, tab);
		BankView view = bank.view(player);
		Vec3 anchor = anchored ? player.position() : null;
		player.openMenu(new ExtendedMenuProvider<BankView>() {
			@Override
			public BankView getScreenOpeningData(ServerPlayer p) {
				return view;
			}

			@Override
			public Component getDisplayName() {
				return Component.translatable(hasBank ? "hearth.economy.bank.title" : "hearth.economy.property.title", settlement.name());
			}

			@Override
			public AbstractContainerMenu createMenu(int id, Inventory inventory, Player p) {
				return new BankMenu(id, inventory, view, bank, anchor);
			}
		});
	}
}
