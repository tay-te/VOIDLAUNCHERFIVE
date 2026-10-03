package dev.voidmc.hearth.economy;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.DialogueProvider;
import dev.voidmc.hearth.api.HearthConfig;
import dev.voidmc.hearth.api.HearthDataKey;
import dev.voidmc.hearth.api.HearthServices;
import dev.voidmc.hearth.api.HearthWorld;
import dev.voidmc.hearth.api.SettlementEvents;
import dev.voidmc.hearth.economy.bank.Deposits;
import dev.voidmc.hearth.economy.command.EconomyCommands;
import dev.voidmc.hearth.economy.daily.SettlementEconomy;
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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.world.level.Level;

/**
 * The economy module: coin, pouches and deeds; prices and markets; banks, the money changer and property; the daily
 * settlement economy (taxes, trade, wages, the mint, price drift); coin in chest loot. Registers the
 * {@link dev.voidmc.hearth.api.EconomyService}. See docs/ECONOMY.md.
 */
public final class EconomyModule {
	/** The economy's record of each settlement: price drift, use, builders' needs, the changer's emeralds, the books. */
	public static final HearthDataKey<MarketLedger> LEDGER = HearthDataKey.register(Hearth.id("economy/ledger"), HearthDataKey.Scope.SETTLEMENT,
		MarketLedger.CODEC, MarketLedger::new);

	private static final EconomyDialogue DIALOGUE = new EconomyDialogue();
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
		Deposits.init();
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

		SettlementEvents.DAY_PASSED.register((world, settlement, day) -> SettlementEconomy.dayPassed(settlement, day));
		SettlementEvents.CONSTRUCTION_STALLED.register((world, settlement, plot, goods, missing) ->
			SettlementEconomy.constructionStalled(settlement, plot, goods, missing, world.day()));
		SettlementEvents.FOUNDED.register((world, settlement) -> {
			if (settlement.treasury() == 0) {
				settlement.deposit(Math.max(4, settlement.population()) * FOUNDING_COIN_PER_HEAD);
			}
		});

		DialogueProvider.register(DIALOGUE);
		CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> EconomyCommands.register(dispatcher));
		HearthServices.setEconomy(new EconomyServiceImpl());
	}

	public static EconomyConfig config() {
		return config;
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
