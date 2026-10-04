package dev.voidmc.hearth.economy;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.economy.bank.Bank;
import dev.voidmc.hearth.economy.bank.BankMenu;
import dev.voidmc.hearth.economy.bank.BankView;
import dev.voidmc.hearth.economy.market.Market;
import dev.voidmc.hearth.economy.market.MarketMenu;
import dev.voidmc.hearth.economy.market.MarketView;
import dev.voidmc.hearth.economy.property.PropertyNames;
import dev.voidmc.hearth.economy.shop.Shop;
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
import org.jspecify.annotations.Nullable;

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

	/** Opens the settlement's own market (its stores and treasury). {@code anchored}: closes if they walk away (false for dev tools). */
	public static void openMarket(ServerPlayer player, Settlement settlement, boolean anchored) {
		openShop(player, settlement, null, anchored);
	}

	/** Opens a shop's counter (null: the town's own market). */
	public static void openShop(ServerPlayer player, Settlement settlement, @Nullable Shop shop, boolean anchored) {
		Market market = new Market(settlement, shop);
		MarketView view = market.view(player);
		Vec3 anchor = anchored ? player.position() : null;
		Component title = shopTitle(settlement, shop);
		player.openMenu(new ExtendedMenuProvider<MarketView>() {
			@Override
			public MarketView getScreenOpeningData(ServerPlayer p) {
				return view;
			}

			@Override
			public Component getDisplayName() {
				return title;
			}

			@Override
			public AbstractContainerMenu createMenu(int id, Inventory inventory, Player p) {
				return new MarketMenu(id, inventory, view, market, anchor);
			}
		});
	}

	/** "Edda Miller's bakery", "Market of Ashford": who the player trades with. */
	public static Component shopTitle(Settlement settlement, @Nullable Shop shop) {
		if (shop == null) {
			return Component.translatable("hearth.economy.market.title", settlement.name());
		}
		Resident keeper = shop.keeper == null ? null : settlement.resident(shop.keeper);
		Component place = shop.kind.startsWith("job/")
			? Component.translatable(shop.fence ? "hearth.economy.shop.fence" : "hearth.economy.shop.stall")
			: PropertyNames.lower(shop.kind);
		return keeper == null ? Component.translatable("hearth.economy.shop.title_town", place, settlement.name())
			: Component.translatable("hearth.economy.shop.title", keeper.fullName(), place);
	}

	/** Opens the bank counter (or, with {@code hasBank} false, a town hall's: property, works and contracts). */
	public static void openBank(ServerPlayer player, Settlement settlement, boolean hasBank, int tab, boolean anchored) {
		openBank(player, settlement, hasBank, true, tab, anchored);
	}

	/** Opens the counter; {@code hasWorks} adds public works and supply contracts. */
	public static void openBank(ServerPlayer player, Settlement settlement, boolean hasBank, boolean hasWorks, int tab, boolean anchored) {
		Bank bank = new Bank(settlement, hasBank, hasWorks, tab);
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
