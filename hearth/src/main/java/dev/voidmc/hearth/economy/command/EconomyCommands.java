package dev.voidmc.hearth.economy.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.Goods;
import dev.voidmc.hearth.api.PlotView;
import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.api.Station;
import dev.voidmc.hearth.api.testing.SimpleSettlement;
import dev.voidmc.hearth.economy.EconomyMenus;
import dev.voidmc.hearth.economy.EconomyModule;
import dev.voidmc.hearth.economy.SettlementLookup;
import dev.voidmc.hearth.economy.bank.BankView;
import dev.voidmc.hearth.economy.bank.DepositStore;
import dev.voidmc.hearth.economy.coin.CoinFormat;
import dev.voidmc.hearth.economy.coin.PlayerPurse;
import dev.voidmc.hearth.economy.daily.SettlementEconomy;
import dev.voidmc.hearth.economy.dialogue.EconomyDialogue;
import dev.voidmc.hearth.economy.market.MarketLedger;
import dev.voidmc.hearth.economy.price.MarketPricer;
import dev.voidmc.hearth.economy.price.PriceTable;
import dev.voidmc.hearth.economy.purse.Households;
import dev.voidmc.hearth.economy.purse.Purses;
import dev.voidmc.hearth.economy.shop.Shop;
import dev.voidmc.hearth.economy.shop.ShopBook;
import dev.voidmc.hearth.economy.shop.Shops;
import dev.voidmc.hearth.economy.sandbox.Sandbox;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * {@code /hearth money ...}, {@code /hearth market <settlement> prices|shops|purses|open [shop]} and the dev sandbox
 * {@code /hearth economy ...}. Permission level 2 (game masters).
 */
public final class EconomyCommands {
	private static final SimpleCommandExceptionType BAD_AMOUNT = new SimpleCommandExceptionType(Component.translatable("hearth.economy.command.bad_amount"));
	private static final SimpleCommandExceptionType NO_SETTLEMENT = new SimpleCommandExceptionType(Component.translatable("hearth.economy.command.no_settlement"));

	private static final SuggestionProvider<CommandSourceStack> SETTLEMENTS = (c, b) -> SharedSuggestionProvider.suggest(
		SettlementLookup.all(c.getSource().getServer()).stream().map(s -> StringArgumentType.escapeIfRequired(s.name())), b);
	private static final SuggestionProvider<CommandSourceStack> CULTURES = (c, b) -> SharedSuggestionProvider.suggest(
		java.util.Arrays.stream(Culture.values()).map(Culture::getSerializedName), b);
	private static final SuggestionProvider<CommandSourceStack> GOODS = (c, b) -> SharedSuggestionProvider.suggest(
		java.util.Arrays.stream(Goods.values()).map(Goods::getSerializedName), b);

	private EconomyCommands() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("hearth")
			.then(Commands.literal("money")
				.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.literal("give")
					.then(Commands.argument("player", EntityArgument.player())
						.then(Commands.argument("amount", StringArgumentType.word())
							.executes(EconomyCommands::give))))
				.then(Commands.literal("take")
					.then(Commands.argument("player", EntityArgument.player())
						.then(Commands.argument("amount", StringArgumentType.word())
							.executes(EconomyCommands::take))))
				.then(Commands.literal("balance")
					.executes(c -> balance(c, c.getSource().getPlayerOrException()))
					.then(Commands.argument("player", EntityArgument.player())
						.executes(c -> balance(c, EntityArgument.getPlayer(c, "player"))))))
			.then(Commands.literal("market")
				.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.argument("settlement", StringArgumentType.string()).suggests(SETTLEMENTS)
					.then(Commands.literal("prices").executes(EconomyCommands::prices))
					.then(Commands.literal("shops").executes(EconomyCommands::shops))
					.then(Commands.literal("purses").executes(EconomyCommands::purses))
					.then(Commands.literal("counter").executes(c -> {
						EconomyMenus.openBank(c.getSource().getPlayerOrException(), settlement(c), true, true, BankView.TAB_WORKS, false);
						return 1;
					}))
					.then(Commands.literal("open").executes(c -> {
						EconomyMenus.openMarket(c.getSource().getPlayerOrException(), settlement(c), false);
						return 1;
					})
						.then(Commands.argument("shop", IntegerArgumentType.integer(1, 999))
							.executes(c -> openShop(c, settlement(c)))))))
			.then(Commands.literal("economy")
				.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.literal("sandbox")
					.executes(c -> sandboxMarket(c, null))
					.then(Commands.argument("culture", StringArgumentType.word()).suggests(CULTURES)
						.executes(c -> sandboxMarket(c, culture(c))))
					.then(Commands.literal("bank").executes(c -> sandboxBank(c, BankView.TAB_BANK)))
					.then(Commands.literal("property").executes(c -> sandboxBank(c, BankView.TAB_PROPERTY)))
					.then(Commands.literal("works").executes(c -> sandboxBank(c, BankView.TAB_WORKS)))
					.then(Commands.literal("contracts").executes(c -> sandboxBank(c, BankView.TAB_CONTRACTS)))
					.then(Commands.literal("shop")
						.then(Commands.argument("shop", IntegerArgumentType.integer(1, 999))
							.executes(c -> openShop(c, Sandbox.forPlayer(c.getSource().getPlayerOrException(), null)))))
					.then(Commands.literal("shops").executes(c -> shops(c, Sandbox.forPlayer(c.getSource().getPlayerOrException(), null))))
					.then(Commands.literal("purses").executes(c -> purses(c, Sandbox.forPlayer(c.getSource().getPlayerOrException(), null))))
					.then(Commands.literal("talk").executes(EconomyCommands::sandboxTalk))
					.then(Commands.literal("days")
						.then(Commands.argument("days", IntegerArgumentType.integer(1, 3650))
							.executes(EconomyCommands::sandboxDays)))
					.then(Commands.literal("stall")
						.then(Commands.argument("goods", StringArgumentType.word()).suggests(GOODS)
							.then(Commands.argument("amount", IntegerArgumentType.integer(1, 100_000))
								.executes(EconomyCommands::sandboxStall))))
					.then(Commands.literal("reputation")
						.then(Commands.argument("value", IntegerArgumentType.integer(-100, 100))
							.executes(EconomyCommands::sandboxReputation))))));
	}

	private static int give(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		ServerPlayer player = EntityArgument.getPlayer(c, "player");
		long bits = parseAmount(StringArgumentType.getString(c, "amount"));
		PlayerPurse.pay(player, bits);
		c.getSource().sendSuccess(() -> Component.translatable("hearth.economy.command.gave", CoinFormat.format(bits), player.getDisplayName()), true);
		return (int) Math.min(Integer.MAX_VALUE, bits);
	}

	private static int take(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		ServerPlayer player = EntityArgument.getPlayer(c, "player");
		long bits = parseAmount(StringArgumentType.getString(c, "amount"));
		if (!PlayerPurse.charge(player, bits)) {
			c.getSource().sendFailure(Component.translatable("hearth.economy.command.cannot_take", player.getDisplayName(), CoinFormat.format(bits)));
			return 0;
		}
		c.getSource().sendSuccess(() -> Component.translatable("hearth.economy.command.took", CoinFormat.format(bits), player.getDisplayName()), true);
		return 1;
	}

	private static int balance(CommandContext<CommandSourceStack> c, ServerPlayer player) {
		long carried = PlayerPurse.balance(player);
		long deposit = DepositStore.balance(c.getSource().getServer(), player.getUUID());
		c.getSource().sendSuccess(() -> Component.translatable("hearth.economy.command.balance", player.getDisplayName(), CoinFormat.format(carried),
			CoinFormat.format(deposit)), false);
		return (int) Math.min(Integer.MAX_VALUE, carried + deposit);
	}

	private static int prices(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		Settlement s = settlement(c);
		MarketPricer pricer = new MarketPricer(s);
		MarketLedger ledger = pricer.ledger();
		CommandSourceStack src = c.getSource();
		src.sendSuccess(() -> Component.translatable("hearth.economy.command.prices.header", s.name(), Component.translatableWithFallback(s.culture().translationKey(), s.culture().getSerializedName()),
			CoinFormat.format(s.treasury())).withStyle(ChatFormatting.GOLD), false);
		for (Goods g : Goods.VALUES) {
			List<Item> wares = PriceTable.wares(s.culture(), g);
			ItemStack sample = wares.isEmpty() ? ItemStack.EMPTY : new ItemStack(wares.getFirst());
			MutableComponent line = Component.literal(String.format(Locale.ROOT, "%-7s %5d/%-5d x%.2f ", g.getSerializedName(), s.stockpile().get(g),
				s.stockpile().capacity(g), pricer.scarcity(g))).withStyle(ChatFormatting.GRAY);
			if (!sample.isEmpty()) {
				MarketPricer.Quote q = pricer.quote(sample, src.getEntity() instanceof ServerPlayer p ? p.getUUID() : null);
				line.append(Component.translatable("hearth.economy.command.prices.line", q.lot(), sample.getHoverName(), CoinFormat.format(q.lotBuy()),
					CoinFormat.format(q.lotSell())).withStyle(ChatFormatting.WHITE));
			}
			if (ledger.need(g) > 0) {
				line.append(Component.translatable("hearth.economy.command.prices.need", ledger.need(g)).withStyle(ChatFormatting.GREEN));
			}
			src.sendSuccess(() -> line, false);
		}
		MarketLedger.Books b = ledger.books;
		src.sendSuccess(() -> Component.translatable("hearth.economy.command.prices.books", CoinFormat.format(b.taxes() + b.trade()),
			CoinFormat.format(b.wages() + b.upkeep()), CoinFormat.format(b.minted())).withStyle(ChatFormatting.GRAY), false);
		src.sendSuccess(() -> Component.translatable("hearth.economy.command.prices.folk", CoinFormat.format(b.spent()), CoinFormat.format(b.dues()),
			CoinFormat.format(b.income()), b.hungry()).withStyle(ChatFormatting.GRAY), false);
		return 1;
	}

	private static int shops(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		return shops(c, settlement(c));
	}

	/** Each shop: where, who keeps it, its till and its fullest shelves. */
	private static int shops(CommandContext<CommandSourceStack> c, Settlement s) {
		CommandSourceStack src = c.getSource();
		ShopBook book = Shops.ensure(s, EconomyModule.day(src.getLevel()));
		src.sendSuccess(() -> Component.translatable("hearth.economy.command.shops.header", s.name(), book.shops.size()).withStyle(ChatFormatting.GOLD), false);
		int i = 0;
		for (Shop shop : book.shops.values()) {
			int n = ++i;
			Resident keeper = shop.keeper == null ? null : s.resident(shop.keeper);
			StringBuilder shelves = new StringBuilder();
			java.util.List<Goods> stocked = new java.util.ArrayList<>(java.util.Arrays.asList(Goods.VALUES));
			stocked.removeIf(g -> shop.stock(g) <= 0);
			stocked.sort((a, b) -> Integer.compare(shop.stock(b), shop.stock(a)));
			for (int k = 0; k < Math.min(4, stocked.size()); k++) {
				shelves.append(k == 0 ? "" : ", ").append(stocked.get(k).getSerializedName()).append(' ').append(shop.stock(stocked.get(k)));
			}
			Component line = Component.translatable("hearth.economy.command.shops.line", n, shop.kind, keeper == null ? "-" : keeper.fullName(),
				CoinFormat.format(shop.till), shelves.toString()).withStyle(shop.open() ? ChatFormatting.WHITE : ChatFormatting.DARK_GRAY);
			src.sendSuccess(() -> shop.fence ? line.copy().append(Component.literal(" [fence]").withStyle(ChatFormatting.DARK_PURPLE)) : line, false);
		}
		return book.shops.size();
	}

	private static int purses(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		return purses(c, settlement(c));
	}

	/** Households: how many, their coin, their stations, who went hungry, and the richest few. */
	private static int purses(CommandContext<CommandSourceStack> c, Settlement s) {
		CommandSourceStack src = c.getSource();
		java.util.List<Households.Household> all = Households.of(s);
		long total = 0;
		int hungry = 0;
		java.util.Map<Station, Integer> stations = new java.util.EnumMap<>(Station.class);
		for (Households.Household h : all) {
			total += h.wealth();
			if (Purses.hunger(h.members().getFirst()) > 0) {
				hungry++;
			}
			stations.merge(h.station(), 1, Integer::sum);
		}
		long sum = total;
		int hungryCount = hungry;
		src.sendSuccess(() -> Component.translatable("hearth.economy.command.purses.header", s.name(), all.size(), CoinFormat.format(sum), hungryCount)
			.withStyle(ChatFormatting.GOLD), false);
		StringBuilder line = new StringBuilder();
		stations.forEach((st, n) -> line.append(line.isEmpty() ? "" : ", ").append(st.getSerializedName()).append(' ').append(n));
		src.sendSuccess(() -> Component.literal(line.toString()).withStyle(ChatFormatting.GRAY), false);
		java.util.List<Households.Household> rich = new java.util.ArrayList<>(all);
		rich.sort((a, b) -> Long.compare(b.wealth(), a.wealth()));
		for (int k = 0; k < Math.min(5, rich.size()); k++) {
			Households.Household h = rich.get(k);
			src.sendSuccess(() -> Component.translatable("hearth.economy.command.purses.line", h.members().getFirst().fullName(), h.members().size(),
				h.station().getSerializedName(), CoinFormat.format(h.wealth())).withStyle(ChatFormatting.WHITE), false);
		}
		return all.size();
	}

	/** Opens the n-th shop's counter (as listed by {@code shops}). */
	private static int openShop(CommandContext<CommandSourceStack> c, Settlement s) throws CommandSyntaxException {
		ServerPlayer player = c.getSource().getPlayerOrException();
		int n = IntegerArgumentType.getInteger(c, "shop");
		java.util.List<Shop> list = new java.util.ArrayList<>(Shops.ensure(s, EconomyModule.day(player.level())).shops.values());
		if (n > list.size()) {
			throw NO_SETTLEMENT.create();
		}
		EconomyMenus.openShop(player, s, list.get(n - 1), false);
		return 1;
	}

	private static int sandboxMarket(CommandContext<CommandSourceStack> c, Culture culture) throws CommandSyntaxException {
		ServerPlayer player = c.getSource().getPlayerOrException();
		SimpleSettlement s = Sandbox.forPlayer(player, culture);
		EconomyMenus.openMarket(player, s, false);
		return 1;
	}

	private static int sandboxBank(CommandContext<CommandSourceStack> c, int tab) throws CommandSyntaxException {
		ServerPlayer player = c.getSource().getPlayerOrException();
		EconomyMenus.openBank(player, Sandbox.forPlayer(player, null), true, tab, false);
		return 1;
	}

	/** Goes through the dialogue provider as the NPC module will: lists the merchant's options, picks "Let's trade". */
	private static int sandboxTalk(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		ServerPlayer player = c.getSource().getPlayerOrException();
		SimpleSettlement s = Sandbox.forPlayer(player, null);
		Resident merchant = Sandbox.merchant(s);
		EconomyDialogue dialogue = EconomyModule.dialogue();
		List<dev.voidmc.hearth.api.DialogueProvider.Option> options = new java.util.ArrayList<>();
		dialogue.collect(player, s, merchant, options);
		c.getSource().sendSuccess(() -> Component.literal(merchant.fullName() + ": " + options.stream().map(o -> o.label().getString()).toList()), false);
		dialogue.select(player, s, merchant, EconomyDialogue.TRADE);
		return options.size();
	}

	private static int sandboxDays(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		ServerPlayer player = c.getSource().getPlayerOrException();
		SimpleSettlement s = Sandbox.forPlayer(player, null);
		int days = IntegerArgumentType.getInteger(c, "days");
		long start = s.data(EconomyModule.LEDGER).lastDay == Long.MIN_VALUE ? 0 : s.data(EconomyModule.LEDGER).lastDay + 1;
		long before = s.treasury();
		for (int d = 0; d < days; d++) {
			SettlementEconomy.dayPassed(s, start + d);
		}
		long after = s.treasury();
		c.getSource().sendSuccess(() -> Component.translatable("hearth.economy.command.days", days, s.name(), CoinFormat.format(before),
			CoinFormat.format(after)), false);
		return days;
	}

	private static int sandboxStall(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		ServerPlayer player = c.getSource().getPlayerOrException();
		SimpleSettlement s = Sandbox.forPlayer(player, null);
		Goods goods = Goods.CODEC.byName(StringArgumentType.getString(c, "goods"));
		if (goods == null) {
			throw BAD_AMOUNT.create();
		}
		int amount = IntegerArgumentType.getInteger(c, "amount");
		PlotView plot = s.plots().stream().filter(p -> p.spec().archetype().getPath().startsWith("house/")).findFirst().orElseThrow();
		s.stockpile().take(goods, Math.max(0, s.stockpile().get(goods) - amount / 4));
		SettlementEconomy.constructionStalled(s, plot, goods, amount, EconomyModule.day(player.level()));
		c.getSource().sendSuccess(() -> Component.translatable("hearth.economy.command.stalled", s.name(), amount,
			Component.translatable(goods.translationKey())), false);
		return 1;
	}

	private static int sandboxReputation(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		ServerPlayer player = c.getSource().getPlayerOrException();
		SimpleSettlement s = Sandbox.forPlayer(player, null);
		int value = IntegerArgumentType.getInteger(c, "value");
		s.addReputation(player.getUUID(), value - s.reputation(player.getUUID()));
		c.getSource().sendSuccess(() -> Component.literal(s.name() + ": reputation " + value), false);
		return 1;
	}

	private static Settlement settlement(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		Settlement s = SettlementLookup.byName(c.getSource().getServer(), StringArgumentType.getString(c, "settlement"));
		if (s == null) {
			throw NO_SETTLEMENT.create();
		}
		return s;
	}

	private static Culture culture(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		Culture culture = Culture.CODEC.byName(StringArgumentType.getString(c, "culture"));
		if (culture == null) {
			throw NO_SETTLEMENT.create();
		}
		return culture;
	}

	/** "250" (bits), or coins: "2c5m3b", "3crowns", "12m". */
	static long parseAmount(String text) throws CommandSyntaxException {
		Long v = AmountParser.parse(text);
		if (v == null || v <= 0) {
			throw BAD_AMOUNT.create();
		}
		return v;
	}
}
