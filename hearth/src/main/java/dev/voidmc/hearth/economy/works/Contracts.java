package dev.voidmc.hearth.economy.works;

import dev.voidmc.hearth.api.Goods;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.economy.EconomyConfig;
import dev.voidmc.hearth.economy.EconomyModule;
import dev.voidmc.hearth.economy.Reputation;
import dev.voidmc.hearth.economy.bank.BankView;
import dev.voidmc.hearth.economy.coin.CoinFormat;
import dev.voidmc.hearth.economy.coin.PlayerPurse;
import dev.voidmc.hearth.economy.item.EconomyItems;
import dev.voidmc.hearth.economy.market.MarketLedger;
import dev.voidmc.hearth.economy.market.StolenGoods;
import dev.voidmc.hearth.economy.price.MarketPricer;
import dev.voidmc.hearth.economy.price.PriceTable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * Supply contracts: when a settlement is persistently short of a goods (its scarcity at or above
 * {@code contractScarcity}, or its builders waiting on it), the reeve offers a contract — so many units by a day, for the
 * town's buying price plus a premium. A player takes it (the treasury sets the whole sum aside then), delivers in as
 * many trips as they like at the counter, and is paid for each delivery from the escrow. Fulfilled, it earns standing;
 * left to lapse, it costs some, and the rest of the escrow returns to the treasury.
 */
public final class Contracts {
	/** Contracts one player may hold in one settlement at once. */
	static final int MAX_TAKEN = 2;

	private Contracts() {
	}

	/** The day's work: lapse what is overdue or unwanted, and offer new contracts for what the town lacks. */
	public static void daily(Settlement settlement, MarketPricer pricer, long day) {
		EconomyConfig.Works cfg = EconomyModule.config().works();
		Works works = Projects.works(settlement);
		for (Iterator<Works.Contract> it = works.contracts.iterator(); it.hasNext(); ) {
			Works.Contract c = it.next();
			if (c.taken() && day > c.due) {
				settlement.deposit(c.escrow);
				c.escrow = 0;
				Reputation.deed(settlement, c.taker, Reputation.CONTRACT_FAILED, cfg.failWeight(), null, null, true);
				it.remove();
			} else if (!c.taken() && day - c.offered > cfg.offerDays()) {
				it.remove();
			}
		}
		MarketLedger ledger = pricer.ledger();
		for (Goods g : Goods.VALUES) {
			if (works.contracts.size() >= cfg.maxContracts()) {
				break;
			}
			if (offered(works, g)) {
				continue;
			}
			boolean needed = ledger.need(g) > 0;
			if (!needed && pricer.scarcity(g) < cfg.contractScarcity()) {
				continue;
			}
			int short_ = (int) Math.ceil(pricer.target(g) - settlement.stockpile().get(g));
			int units = needed ? Math.max(short_, ledger.need(g)) : short_;
			units = Math.min(512, Math.max(32, (units + 15) / 16 * 16));
			units = Math.min(units, Math.max(0, pricer.room(g)));
			if (units < 16) {
				continue;
			}
			long total = (long) Math.ceil(units * unitPay(pricer, g, cfg));
			if (total <= 0 || total > settlement.treasury()) {
				continue; // a town that could not pay does not offer
			}
			works.contracts.add(new Works.Contract(works.nextContract++, g, units, total, day));
		}
		settlement.markDirty();
	}

	/** What a contract pays per unit: the town's buying price for one unit, plus the premium. */
	static double unitPay(MarketPricer pricer, Goods g, EconomyConfig.Works cfg) {
		double spread = EconomyModule.config().pricing().spread();
		return pricer.unitMid(g) * (1 - spread / 2) * (1 + cfg.contractPremium());
	}

	private static boolean offered(Works works, Goods g) {
		for (Works.Contract c : works.contracts) {
			if (c.goods == g) {
				return true;
			}
		}
		return false;
	}

	public static List<BankView.ContractOffer> listings(Settlement settlement, ServerPlayer player, long day) {
		List<BankView.ContractOffer> out = new ArrayList<>();
		for (Works.Contract c : Projects.works(settlement).contracts) {
			if (c.taken() && !player.getUUID().equals(c.taker)) {
				continue;
			}
			int flags = (c.taken() ? BankView.ContractOffer.TAKEN : 0) | (carried(player, c.goods, settlement.id()) > 0 ? BankView.ContractOffer.CARRYING : 0);
			out.add(new BankView.ContractOffer(c.id, c.goods.getSerializedName(), c.units, c.delivered, c.total, c.taken() ? (int) Math.max(0, c.due - day)
				: (int) Math.max(0, EconomyModule.config().works().offerDays() - (day - c.offered)), flags));
		}
		return out;
	}

	/** The player takes a contract: the treasury sets its whole sum aside. */
	public static Component accept(Settlement settlement, ServerPlayer player, int id, long day) {
		Works works = Projects.works(settlement);
		Works.Contract c = works.contract(id);
		if (c == null || c.taken()) {
			return Component.translatable("hearth.economy.contract.gone").withStyle(ChatFormatting.DARK_RED);
		}
		int held = 0;
		for (Works.Contract o : works.contracts) {
			if (player.getUUID().equals(o.taker)) {
				held++;
			}
		}
		if (held >= MAX_TAKEN) {
			return Component.translatable("hearth.economy.contract.too_many", MAX_TAKEN).withStyle(ChatFormatting.DARK_RED);
		}
		if (Reputation.standing(settlement, player.getUUID()) < EconomyModule.config().pricing().minTradeReputation()) {
			return Component.translatable("hearth.economy.market.refused").withStyle(ChatFormatting.DARK_RED);
		}
		if (!settlement.withdraw(c.total)) {
			return Component.translatable("hearth.economy.market.town_cannot_pay").withStyle(ChatFormatting.DARK_RED);
		}
		c.escrow = c.total;
		c.taker = player.getUUID();
		c.due = day + EconomyModule.config().works().contractDays();
		settlement.markDirty();
		return Component.translatable("hearth.economy.contract.accepted", c.units, Component.translatable(c.goods.translationKey()),
			CoinFormat.format(c.total), EconomyModule.config().works().contractDays()).withStyle(ChatFormatting.DARK_GREEN);
	}

	/** Units of a goods the player carries that this town will take (stolen goods from here are not). */
	static int carried(ServerPlayer player, Goods goods, long settlementId) {
		int units = 0;
		Inventory inv = player.getInventory();
		for (int i = 0; i < Inventory.INVENTORY_SIZE; i++) {
			ItemStack s = inv.getItem(i);
			if (acceptable(s, goods, settlementId)) {
				units += PriceTable.units(s.getItem()) * s.getCount();
			}
		}
		return units;
	}

	private static boolean acceptable(ItemStack s, Goods goods, long settlementId) {
		return !s.isEmpty() && EconomyItems.coinOf(s) == null && !EconomyItems.isPouch(s) && PriceTable.goods(s.getItem()) == goods
			&& StolenGoods.judge(s, settlementId, false) != StolenGoods.Verdict.REFUSED;
	}

	/** The player hands over what they carry of the contract's goods (up to what is still owed) and is paid for it. */
	public static Component deliver(Settlement settlement, ServerPlayer player, int id) {
		Works works = Projects.works(settlement);
		Works.Contract c = works.contract(id);
		if (c == null || !player.getUUID().equals(c.taker)) {
			return Component.translatable("hearth.economy.contract.gone").withStyle(ChatFormatting.DARK_RED);
		}
		int owed = c.units - c.delivered;
		int got = 0;
		Inventory inv = player.getInventory();
		for (int i = 0; i < Inventory.INVENTORY_SIZE && got < owed; i++) {
			ItemStack s = inv.getItem(i);
			if (!acceptable(s, c.goods, settlement.id())) {
				continue;
			}
			int per = PriceTable.units(s.getItem());
			int items = Math.min(s.getCount(), (int) Math.ceil((owed - got) / (double) per));
			s.shrink(items);
			got += items * per;
		}
		inv.setChanged();
		if (got <= 0) {
			return Component.translatable("hearth.economy.contract.nothing", Component.translatable(c.goods.translationKey())).withStyle(ChatFormatting.DARK_RED);
		}
		got = Math.min(got, owed);
		settlement.stockpile().add(c.goods, got);
		new MarketPricer(settlement).ledger().traded[c.goods.ordinal()] += got;
		c.delivered += got;
		long pay = Math.min(c.escrow, c.earned() - c.paid);
		c.paid += pay;
		c.escrow -= pay;
		PlayerPurse.pay(player, pay);
		Component status;
		if (c.delivered >= c.units) {
			settlement.deposit(c.escrow);
			c.escrow = 0;
			works.contracts.remove(c);
			Reputation.deed(settlement, player.getUUID(), Reputation.CONTRACT_FULFILLED, EconomyModule.config().works().doneWeight(), null,
				player.blockPosition(), true);
			status = Component.translatable("hearth.economy.contract.fulfilled", CoinFormat.format(pay));
		} else {
			status = Component.translatable("hearth.economy.contract.delivered", got, Component.translatable(c.goods.translationKey()), CoinFormat.format(pay),
				c.units - c.delivered);
		}
		settlement.markDirty();
		return status.copy().withStyle(ChatFormatting.DARK_GREEN);
	}

	/** Contracts a player holds here (for dialogue). */
	public static List<Works.Contract> heldBy(Settlement settlement, UUID player) {
		List<Works.Contract> out = new ArrayList<>();
		for (Works.Contract c : Projects.works(settlement).contracts) {
			if (player.equals(c.taker)) {
				out.add(c);
			}
		}
		return out;
	}
}
