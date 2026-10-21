package dev.voidmc.hearth.economy;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.DialogueProvider;
import dev.voidmc.hearth.api.HearthConfig;
import dev.voidmc.hearth.api.HearthDataKey;
import dev.voidmc.hearth.api.HearthServices;
import dev.voidmc.hearth.api.HearthWorld;
import dev.voidmc.hearth.api.SettlementEvents;
import dev.voidmc.hearth.economy.bank.DepositStore;
import dev.voidmc.hearth.economy.command.EconomyCommands;
import dev.voidmc.hearth.economy.daily.SettlementEconomy;
import dev.voidmc.hearth.economy.holdings.Holdings;
import dev.voidmc.hearth.economy.purse.Inheritance;
import dev.voidmc.hearth.economy.shop.ShopBook;
import dev.voidmc.hearth.economy.shop.Shops;
import dev.voidmc.hearth.economy.works.Projects;
import dev.voidmc.hearth.economy.works.Works;
import dev.voidmc.hearth.economy.dialogue.EconomyDialogue;
import dev.voidmc.hearth.economy.item.EconomyComponents;
import dev.voidmc.hearth.economy.item.EconomyItems;
import dev.voidmc.hearth.economy.loot.CoinLootTables;
import dev.voidmc.hearth.economy.market.MarketLedger;
import dev.voidmc.hearth.economy.net.EconomyNetwork;
import dev.voidmc.hearth.economy.price.PriceData;
import dev.voidmc.hearth.economy.price.PriceTable;
import dev.voidmc.hearth.economy.sandbox.Sandbox;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.fabricmc.fabric.api.resource.v1.reloader.SimpleReloadListener;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * The economy module: coin, pouches and deeds; prices and markets; shops with their own stock and tills, haggling,
 * stolen goods and fences; residents' purses, wages and spending, and stations that follow them; banks, the money
 * changer, property and businesses; public works and supply contracts; the daily settlement economy; coin in chest loot.
 * Registers the {@link dev.voidmc.hearth.api.EconomyService}. See docs/ECONOMY.md.
 */
public final class EconomyModule {
	/** The economy's record of each settlement: price drift, use, builders' needs, the changer's emeralds, the books. */
	public static final HearthDataKey<MarketLedger> LEDGER = HearthDataKey.register(Hearth.id("economy/ledger"), HearthDataKey.Scope.SETTLEMENT,
		MarketLedger.CODEC, MarketLedger::new);
	/** Each settlement's shops: their stock and tills, and merchants soured by haggling. */
	public static final HearthDataKey<ShopBook> SHOPS = HearthDataKey.register(Hearth.id("economy/shops"), HearthDataKey.Scope.SETTLEMENT,
		ShopBook.CODEC, ShopBook::new);
	/** Businesses players own in each settlement. */
	public static final HearthDataKey<Holdings> HOLDINGS = HearthDataKey.register(Hearth.id("economy/holdings"), HearthDataKey.Scope.SETTLEMENT,
		Holdings.CODEC, Holdings::new);
	/** Public works players fund and supply contracts in each settlement. */
	public static final HearthDataKey<Works> WORKS = HearthDataKey.register(Hearth.id("economy/works"), HearthDataKey.Scope.SETTLEMENT,
		Works.CODEC, Works::new);

	private static final EconomyDialogue DIALOGUE = new EconomyDialogue();
	private static final dev.voidmc.hearth.economy.broker.BrokerDialogue BROKER = new dev.voidmc.hearth.economy.broker.BrokerDialogue();
	/** Starting treasury per resident for a settlement the sim founds with an empty one. */
	private static final long FOUNDING_COIN_PER_HEAD = 40;
	private static volatile EconomyConfig config = EconomyConfig.DEFAULTS;

	private EconomyModule() {
	}

	public static void init() {
		config = HearthConfig.load("economy", EconomyConfig.CODEC, EconomyConfig.DEFAULTS);
		EconomyComponents.init();
		EconomyItems.init();
		EconomyMenus.init();
		DepositStore.init();
		dev.voidmc.hearth.economy.bank.BankLedger.init();
		EconomyNetwork.init();
		CoinLootTables.init();

		ResourceLoader.get(PackType.SERVER_DATA).registerReloadListener(Hearth.id("economy/prices"), new SimpleReloadListener<PriceData>() {
			@Override
			protected PriceData prepare(PreparableReloadListener.SharedState state) {
				return PriceData.load(state.resourceManager());
			}

			@Override
			protected void apply(PriceData data, PreparableReloadListener.SharedState state) {
				PriceTable.setData(data);
			}
		});
		// Tags are bound after the reload listeners run, so lookups cached during the reload are dropped again here.
		ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resources, success) -> PriceTable.invalidate());
		ServerLifecycleEvents.SERVER_STARTED.register(server -> PriceTable.invalidate());
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> Sandbox.clear());

		SettlementEvents.DAY_PASSED.register((world, settlement, day) -> SettlementEconomy.dayPassed(settlement, day, server(world)));
		SettlementEvents.CONSTRUCTION_STALLED.register((world, settlement, plot, goods, missing) ->
			SettlementEconomy.constructionStalled(settlement, plot, goods, missing, world.day()));
		SettlementEvents.FOUNDED.register((world, settlement) -> {
			if (settlement.treasury() == 0) {
				settlement.deposit(Math.max(4, settlement.population()) * FOUNDING_COIN_PER_HEAD);
			}
		});
		// a new shop opens the day its building is finished; a gone plot ends its project (givers repaid) and its business
		SettlementEvents.BUILDING_COMPLETED.register((world, settlement, plot) -> Shops.sync(settlement, world.day()));
		SettlementEvents.PLOT_ABANDONED.register((world, settlement, plot, why) -> {
			Projects.abandoned(settlement, plot.id(), server(world));
			if (settlement.data(HOLDINGS).byPlot.remove(plot.id()) != null) {
				settlement.markDirty();
			}
		});
		// the dead leave their purse to their household, or to the town
		SettlementEvents.RESIDENT_REMOVED.register((world, settlement, resident) -> Inheritance.settle(settlement, resident));

		DialogueProvider.register(DIALOGUE);
		// the card broker (docs/requests/mettle-cards.md §7): listings of goods that live outside inventories
		dev.voidmc.hearth.economy.broker.Broker.init();
		DialogueProvider.register(BROKER);
		SettlementEvents.DAY_PASSED.register((world, settlement, day) -> dev.voidmc.hearth.economy.broker.Broker.expire(settlement, day));
		net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> BROKER.forget(handler.player.getUUID()));
		CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> EconomyCommands.register(dispatcher));
		HearthServices.setEconomy(new EconomyServiceImpl());

		// the living world, part 2 (docs/LIVING_WORLD_2.md §11.2): each lane fills in its own entry point
		dev.voidmc.hearth.economy.household.Household.init(); // B4 household
	}

	public static EconomyConfig config() {
		return config;
	}

	/** For tests and dev tools: replaces the loaded config. */
	public static void setConfig(EconomyConfig cfg) {
		config = cfg;
	}

	private static @Nullable MinecraftServer server(HearthWorld world) {
		try {
			return world.level().getServer();
		} catch (RuntimeException e) {
			return null; // a world without a level (tests)
		}
	}

	public static EconomyDialogue dialogue() {
		return DIALOGUE;
	}

	/** The sim day: the sim's calendar when it runs here, otherwise the overworld's day count. */
	public static long day(Level level) {
		if (level instanceof ServerLevel server) {
			HearthWorld world = HearthWorld.of(server);
			if (world != null) {
				return world.day();
			}
		}
		return level.getOverworldClockTime() / 24000L;
	}
}
