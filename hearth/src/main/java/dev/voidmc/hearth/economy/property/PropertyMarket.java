package dev.voidmc.hearth.economy.property;

import dev.voidmc.hearth.api.PlotSpec;
import dev.voidmc.hearth.api.PlotState;
import dev.voidmc.hearth.api.PlotView;
import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.api.SettlementTier;
import dev.voidmc.hearth.economy.EconomyConfig;
import dev.voidmc.hearth.economy.EconomyModule;
import dev.voidmc.hearth.economy.bank.BankView;
import dev.voidmc.hearth.economy.coin.CoinFormat;
import dev.voidmc.hearth.economy.coin.PlayerPurse;
import dev.voidmc.hearth.economy.item.DeedData;
import dev.voidmc.hearth.economy.item.DeedItem;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Prediction;
import net.minecraft.world.item.ItemStack;

/**
 * Houses and land a settlement will sell: built homes nobody lives in, and housing plots staked out but not yet begun.
 * The buyer gets a {@link DeedItem}; the settlement records the owner and its folk leave the place alone.
 */
public final class PropertyMarket {
	private static final int MAX_LISTED = 32;

	private PropertyMarket() {
	}

	/** What is for sale here (and what this player already owns here), cheapest first. */
	public static List<BankView.PlotOffer> listings(Settlement settlement, ServerPlayer player) {
		EconomyConfig.Property cfg = EconomyModule.config().property();
		int rep = settlement.reputation(player.getUUID());
		long balance = PlayerPurse.balance(player);
		Set<Integer> occupied = new HashSet<>();
		for (Resident r : settlement.residents()) {
			occupied.add(r.home);
		}
		List<BankView.PlotOffer> out = new ArrayList<>();
		for (PlotView plot : settlement.plots()) {
			UUID owner = plot.owner();
			boolean yours = player.getUUID().equals(owner);
			if (owner != null && !yours || !forSale(plot, occupied) && !yours) {
				continue;
			}
			boolean built = plot.state() == PlotState.BUILT;
			int min = built ? cfg.houseMinReputation() : cfg.plotMinReputation();
			long price = price(settlement, plot, rep);
			int flags = (yours ? BankView.PlotOffer.YOURS : 0) | (rep < min ? BankView.PlotOffer.LOW_STANDING : 0)
				| (balance < price ? BankView.PlotOffer.CANNOT_AFFORD : 0);
			PlotSpec s = plot.spec();
			out.add(new BankView.PlotOffer(plot.id(), s.archetype().getPath(), built, s.sizeX(), s.sizeZ(), storeys(s), price, min, flags));
		}
		out.sort(Comparator.comparing((BankView.PlotOffer o) -> !o.has(BankView.PlotOffer.YOURS)).thenComparingLong(BankView.PlotOffer::price));
		return out.size() > MAX_LISTED ? List.copyOf(out.subList(0, MAX_LISTED)) : out;
	}

	/** A built house nobody lives in, or a housing plot not yet begun. */
	static boolean forSale(PlotView plot, Set<Integer> occupied) {
		if (plot.owner() != null || !plot.spec().archetype().getPath().startsWith("house/")) {
			return false;
		}
		return plot.state() == PlotState.BUILT && !occupied.contains(plot.id())
			|| plot.state() == PlotState.PLANNED && plot.stage() == 0 && plot.step() == 0;
	}

	public static long price(Settlement settlement, PlotView plot, int reputation) {
		PlotSpec s = plot.spec();
		return price(s.sizeX() * s.sizeZ(), storeys(s), s.tier(), settlement.tier(), plot.state() == PlotState.BUILT, reputation,
			EconomyModule.config().property());
	}

	/**
	 * Land is priced by its footprint and how big the town is (a city lot costs three times a hamlet's); a house adds its
	 * floor area times its finish. Standing earns a discount.
	 */
	public static long price(int area, int storeys, int finishTier, SettlementTier tier, boolean built, int reputation, EconomyConfig.Property cfg) {
		double townFactor = switch (tier) {
			case CAMP -> 0.5;
			case HAMLET -> 0.7;
			case VILLAGE -> 1.0;
			case TOWN -> 1.5;
			case CITY -> 2.2;
		};
		double land = area * cfg.landPerBlock() * townFactor;
		double house = built ? area * Math.max(1, storeys) * cfg.buildingPerBlock() * (1 + 0.35 * Math.max(0, finishTier)) : 0;
		double discount = 1 - cfg.reputationDiscount() * Math.max(0, Math.min(100, reputation)) / 100.0;
		return Math.max(1, Math.round((land + house) * discount));
	}

	/** Storeys of a house: the {@code storeys} param if the planner set one, else guessed from its height limit. */
	public static int storeys(PlotSpec spec) {
		return spec.params().getIntOr("storeys", Math.max(1, Math.min(6, spec.maxHeight() / 5)));
	}

	/** Buys plot {@code plotId} for the player; the status says what happened. */
	public static Component buy(Settlement settlement, ServerPlayer player, int plotId) {
		PlotView plot = settlement.plot(plotId);
		Set<Integer> occupied = new HashSet<>();
		for (Resident r : settlement.residents()) {
			occupied.add(r.home);
		}
		if (plot == null || !forSale(plot, occupied)) {
			return Component.translatable("hearth.economy.property.gone").withStyle(ChatFormatting.DARK_RED);
		}
		EconomyConfig.Property cfg = EconomyModule.config().property();
		int rep = settlement.reputation(player.getUUID());
		boolean built = plot.state() == PlotState.BUILT;
		int min = built ? cfg.houseMinReputation() : cfg.plotMinReputation();
		if (rep < min) {
			return Component.translatable("hearth.economy.property.standing", min).withStyle(ChatFormatting.DARK_RED);
		}
		long price = price(settlement, plot, rep);
		if (!PlayerPurse.charge(player, price)) {
			return Component.translatable("hearth.economy.property.cannot_afford", CoinFormat.format(price)).withStyle(ChatFormatting.DARK_RED);
		}
		settlement.deposit(price);
		settlement.setPlotOwner(plot.id(), player.getUUID());
		settlement.markDirty();
		PlotSpec s = plot.spec();
		ItemStack deed = DeedItem.create(new DeedData(settlement.id(), settlement.name(), plot.id(), s.archetype().getPath(), built, s.origin(),
			s.sizeX(), s.sizeZ()));
		if (!player.getInventory().add(deed)) {
			player.drop(deed, false, Prediction.SERVER_ONLY);
		}
		return Component.translatable("hearth.economy.property.bought", PropertyNames.building(s.archetype().getPath()), settlement.name(),
			CoinFormat.format(price)).withStyle(ChatFormatting.DARK_GREEN);
	}
}
