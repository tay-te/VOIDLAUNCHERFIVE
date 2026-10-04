package dev.voidmc.hearth.economy.bank;

import dev.voidmc.hearth.api.HearthWorld;
import dev.voidmc.hearth.api.PlotState;
import dev.voidmc.hearth.api.PlotView;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.economy.EconomyConfig;
import dev.voidmc.hearth.economy.EconomyModule;
import dev.voidmc.hearth.economy.Reputation;
import dev.voidmc.hearth.economy.coin.CoinFormat;
import dev.voidmc.hearth.economy.coin.PlayerPurse;
import dev.voidmc.hearth.economy.holdings.BusinessMarket;
import dev.voidmc.hearth.economy.market.MarketLedger;
import dev.voidmc.hearth.economy.net.EconomyActionPayload;
import dev.voidmc.hearth.economy.property.PropertyMarket;
import dev.voidmc.hearth.economy.works.Contracts;
import dev.voidmc.hearth.economy.works.Projects;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Prediction;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * A settlement's bank counter: deposits (one world-wide ledger), the money changer (emeralds, vanilla's villager
 * currency, for coin and back, through the treasury and the changer's own emerald reserve), property and business
 * sales, and — at a bank or the town hall — public works to fund and supply contracts.
 */
public final class Bank {
	private final Settlement settlement;
	private final boolean hasBank;
	private final boolean hasWorks;
	private int tab;
	private Component status = Component.empty();

	public Bank(Settlement settlement, boolean hasBank, int tab) {
		this(settlement, hasBank, true, tab);
	}

	public Bank(Settlement settlement, boolean hasBank, boolean hasWorks, int tab) {
		this.settlement = settlement;
		this.hasBank = hasBank;
		this.hasWorks = hasWorks;
		this.tab = !hasBank && tab == BankView.TAB_BANK ? BankView.TAB_PROPERTY : tab;
		if (!hasWorks && (tab == BankView.TAB_WORKS || tab == BankView.TAB_CONTRACTS)) {
			this.tab = hasBank ? BankView.TAB_BANK : BankView.TAB_PROPERTY;
		}
	}

	public Settlement settlement() {
		return this.settlement;
	}

	public BankView view(ServerPlayer player) {
		EconomyConfig.Bank cfg = EconomyModule.config().bank();
		long day = EconomyModule.day(player.level());
		HearthWorld world = player.level() instanceof ServerLevel level ? HearthWorld.of(level) : null;
		List<BankView.PlotOffer> plots = new ArrayList<>(PropertyMarket.listings(this.settlement, player));
		plots.addAll(BusinessMarket.listings(this.settlement, player));
		return new BankView(this.settlement.name(), this.tab, DepositStore.balance(player.level().getServer(), player.getUUID()),
			PlayerPurse.balance(player), this.settlement.treasury(), countEmeralds(player), reserve(), cfg.emeraldBuyPrice(), cfg.emeraldSellPrice(),
			Reputation.standing(this.settlement, player.getUUID()), this.hasBank, plots, this.status, this.hasWorks,
			this.hasWorks ? Projects.listings(this.settlement, player, world) : List.of(),
			this.hasWorks ? Contracts.listings(this.settlement, player, day) : List.of());
	}

	public void handle(ServerPlayer player, EconomyActionPayload a) {
		boolean banking = a.action() >= EconomyActionPayload.DEPOSIT && a.action() <= EconomyActionPayload.BUY_EMERALDS;
		boolean works = a.action() == EconomyActionPayload.FUND || a.action() == EconomyActionPayload.ACCEPT_CONTRACT
			|| a.action() == EconomyActionPayload.DELIVER;
		if (banking && !this.hasBank || works && !this.hasWorks) {
			return;
		}
		long day = EconomyModule.day(player.level());
		this.status = switch (a.action()) {
			case EconomyActionPayload.DEPOSIT -> deposit(player, a.amount());
			case EconomyActionPayload.WITHDRAW -> withdraw(player, a.amount());
			case EconomyActionPayload.SELL_EMERALDS -> sellEmeralds(player, (int) Math.min(a.amount() < 0 ? Integer.MAX_VALUE : a.amount(), Integer.MAX_VALUE));
			case EconomyActionPayload.BUY_EMERALDS -> buyEmeralds(player, (int) Math.min(Math.max(a.amount(), 0), 64 * 36));
			case EconomyActionPayload.BUY_PLOT -> {
				PlotView plot = this.settlement.plot(a.index());
				yield plot != null && plot.state() == PlotState.BUILT && BusinessMarket.isBusiness(plot.spec().archetype().getPath())
					? BusinessMarket.buy(this.settlement, player, a.index(), day)
					: PropertyMarket.buy(this.settlement, player, a.index());
			}
			case EconomyActionPayload.HIRE -> BusinessMarket.hire(this.settlement, player, a.index());
			case EconomyActionPayload.FUND -> Projects.fund(this.settlement, player, a.index(), Math.max(0, a.amount()), day);
			case EconomyActionPayload.ACCEPT_CONTRACT -> Contracts.accept(this.settlement, player, a.index(), day);
			case EconomyActionPayload.DELIVER -> Contracts.deliver(this.settlement, player, a.index());
			default -> this.status;
		};
		this.tab = switch (a.action()) {
			case EconomyActionPayload.BUY_PLOT, EconomyActionPayload.HIRE -> BankView.TAB_PROPERTY;
			case EconomyActionPayload.FUND -> BankView.TAB_WORKS;
			case EconomyActionPayload.ACCEPT_CONTRACT, EconomyActionPayload.DELIVER -> BankView.TAB_CONTRACTS;
			default -> BankView.TAB_BANK;
		};
	}

	Component deposit(ServerPlayer player, long amount) {
		long carried = PlayerPurse.balance(player);
		long bits = amount == EconomyActionPayload.ALL ? carried : Math.min(amount, carried);
		if (bits <= 0) {
			return Component.translatable("hearth.economy.bank.nothing_to_deposit").withStyle(ChatFormatting.DARK_RED);
		}
		if (!PlayerPurse.charge(player, bits)) {
			return Component.translatable("hearth.economy.bank.nothing_to_deposit").withStyle(ChatFormatting.DARK_RED);
		}
		DepositStore.add(player.level().getServer(), player.getUUID(), bits);
		return Component.translatable("hearth.economy.bank.deposited", CoinFormat.format(bits)).withStyle(ChatFormatting.DARK_GREEN);
	}

	Component withdraw(ServerPlayer player, long amount) {
		long held = DepositStore.balance(player.level().getServer(), player.getUUID());
		long bits = amount == EconomyActionPayload.ALL ? held : Math.min(amount, held);
		if (bits <= 0) {
			return Component.translatable("hearth.economy.bank.nothing_on_deposit").withStyle(ChatFormatting.DARK_RED);
		}
		if (!DepositStore.add(player.level().getServer(), player.getUUID(), -bits)) {
			return Component.translatable("hearth.economy.bank.nothing_on_deposit").withStyle(ChatFormatting.DARK_RED);
		}
		PlayerPurse.pay(player, bits);
		return Component.translatable("hearth.economy.bank.withdrew", CoinFormat.format(bits)).withStyle(ChatFormatting.DARK_GREEN);
	}

	Component sellEmeralds(ServerPlayer player, int wanted) {
		EconomyConfig.Bank cfg = EconomyModule.config().bank();
		int n = Math.min(wanted, countEmeralds(player));
		if (n <= 0) {
			return Component.translatable("hearth.economy.bank.no_emeralds").withStyle(ChatFormatting.DARK_RED);
		}
		n = (int) Math.min(n, this.settlement.treasury() / Math.max(1, cfg.emeraldBuyPrice()));
		if (n <= 0) {
			return Component.translatable("hearth.economy.market.town_cannot_pay").withStyle(ChatFormatting.DARK_RED);
		}
		long bits = (long) n * cfg.emeraldBuyPrice();
		if (!this.settlement.withdraw(bits)) {
			return Component.translatable("hearth.economy.market.town_cannot_pay").withStyle(ChatFormatting.DARK_RED);
		}
		removeEmeralds(player, n);
		MarketLedger ledger = ledger();
		ledger.emeralds = reserve() + n;
		this.settlement.markDirty();
		PlayerPurse.pay(player, bits);
		return Component.translatable("hearth.economy.bank.sold_emeralds", n, CoinFormat.format(bits)).withStyle(ChatFormatting.DARK_GREEN);
	}

	Component buyEmeralds(ServerPlayer player, int wanted) {
		EconomyConfig.Bank cfg = EconomyModule.config().bank();
		int reserve = reserve();
		int n = Math.min(wanted, reserve);
		if (n <= 0) {
			return Component.translatable("hearth.economy.bank.changer_out").withStyle(ChatFormatting.DARK_RED);
		}
		n = (int) Math.min(n, PlayerPurse.balance(player) / Math.max(1, cfg.emeraldSellPrice()));
		long bits = (long) n * cfg.emeraldSellPrice();
		if (n <= 0 || !PlayerPurse.charge(player, bits)) {
			return Component.translatable("hearth.economy.market.cannot_afford", CoinFormat.format(cfg.emeraldSellPrice())).withStyle(ChatFormatting.DARK_RED);
		}
		this.settlement.deposit(bits);
		ledger().emeralds = reserve - n;
		this.settlement.markDirty();
		int left = n;
		while (left > 0) {
			int k = Math.min(left, 64);
			ItemStack s = new ItemStack(Items.EMERALD, k);
			if (!player.getInventory().add(s) || !s.isEmpty()) {
				player.drop(s, false, Prediction.SERVER_ONLY);
			}
			left -= k;
		}
		return Component.translatable("hearth.economy.bank.bought_emeralds", n, CoinFormat.format(bits)).withStyle(ChatFormatting.DARK_GREEN);
	}

	private MarketLedger ledger() {
		return this.settlement.data(EconomyModule.LEDGER);
	}

	/** The changer's emeralds, seeded from the town's size the first time it is asked. */
	int reserve() {
		MarketLedger ledger = ledger();
		if (ledger.emeralds < 0) {
			EconomyConfig.Bank cfg = EconomyModule.config().bank();
			ledger.emeralds = cfg.emeraldReserveBase() + cfg.emeraldReservePerTier() * this.settlement.tier().ordinal();
			this.settlement.markDirty();
		}
		return ledger.emeralds;
	}

	private static int countEmeralds(ServerPlayer player) {
		Inventory inv = player.getInventory();
		int n = 0;
		for (int i = 0; i < Inventory.INVENTORY_SIZE; i++) {
			if (inv.getItem(i).is(Items.EMERALD)) {
				n += inv.getItem(i).getCount();
			}
		}
		return n;
	}

	private static void removeEmeralds(ServerPlayer player, int n) {
		Inventory inv = player.getInventory();
		for (int i = 0; i < Inventory.INVENTORY_SIZE && n > 0; i++) {
			ItemStack s = inv.getItem(i);
			if (s.is(Items.EMERALD)) {
				int t = Math.min(n, s.getCount());
				s.shrink(t);
				n -= t;
			}
		}
		inv.setChanged();
	}
}
