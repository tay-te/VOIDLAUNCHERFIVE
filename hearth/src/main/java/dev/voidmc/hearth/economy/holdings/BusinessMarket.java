package dev.voidmc.hearth.economy.holdings;

import dev.voidmc.hearth.api.AgeStage;
import dev.voidmc.hearth.api.ArchetypeInfo;
import dev.voidmc.hearth.api.HearthServices;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.Poi;
import dev.voidmc.hearth.api.PoiType;
import dev.voidmc.hearth.api.PlotSpec;
import dev.voidmc.hearth.api.PlotState;
import dev.voidmc.hearth.api.PlotView;
import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.api.Station;
import dev.voidmc.hearth.economy.EconomyConfig;
import dev.voidmc.hearth.economy.EconomyModule;
import dev.voidmc.hearth.economy.Reputation;
import dev.voidmc.hearth.economy.bank.BankView;
import dev.voidmc.hearth.economy.bank.DepositStore;
import dev.voidmc.hearth.economy.coin.CoinFormat;
import dev.voidmc.hearth.economy.coin.PlayerPurse;
import dev.voidmc.hearth.economy.item.DeedData;
import dev.voidmc.hearth.economy.item.DeedItem;
import dev.voidmc.hearth.economy.property.PropertyMarket;
import dev.voidmc.hearth.economy.property.PropertyNames;
import dev.voidmc.hearth.economy.shop.Shop;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Prediction;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Owning the economy (docs/LIVING_WORLD.md §7.7): a player buys a workshop, a shop, a farm or an extraction site with a
 * business deed. Unlike a house, the place is not emptied: its folk keep working, but the town now buys their work from
 * the business at {@code productivity} times their wage (and a shop's profit goes to its owner instead of its keeper),
 * and what is left after wages and upkeep is paid into the owner's bank account each day. The owner may also hire an
 * idle resident into it, if it has room.
 */
public final class BusinessMarket {
	private BusinessMarket() {
	}

	public static Holdings holdings(Settlement settlement) {
		return settlement.data(EconomyModule.HOLDINGS);
	}

	/** Whether a building of this archetype path can be owned as a business. */
	public static boolean isBusiness(String path) {
		return path.startsWith("work/") || path.startsWith("farm/") || path.startsWith("extract/")
			|| Set.of("commerce/market_stall", "commerce/inn", "commerce/tavern", "commerce/hostel", "commerce/caravanserai", "commerce/caravan_yard",
				"commerce/counting_house", "deep/forge", "deep/glow_garden", "deep/market").contains(path);
	}

	/** Businesses for sale (built, not owned) and the player's own here. */
	public static List<BankView.PlotOffer> listings(Settlement settlement, ServerPlayer player) {
		EconomyConfig.Holdings cfg = EconomyModule.config().holdings();
		Holdings holdings = holdings(settlement);
		int rep = Reputation.standing(settlement, player.getUUID());
		long balance = PlayerPurse.balance(player);
		List<BankView.PlotOffer> out = new ArrayList<>();
		for (PlotView plot : settlement.plots()) {
			String path = plot.spec().archetype().getPath();
			if (plot.state() != PlotState.BUILT || !isBusiness(path) || plot.owner() != null) {
				continue;
			}
			Holdings.Holding h = holdings.get(plot.id());
			boolean yours = h != null && player.getUUID().equals(h.owner);
			if (h != null && !yours) {
				continue;
			}
			int workers = workers(settlement, plot.id()).size();
			long income = yours ? h.lastIncome : Math.round(expectedIncome(settlement, plot, workers));
			long price = price(settlement, plot, rep, workers);
			int room = room(settlement, plot) - workers;
			int flags = BankView.PlotOffer.BUSINESS | (yours ? BankView.PlotOffer.YOURS : 0) | (rep < cfg.minReputation() ? BankView.PlotOffer.LOW_STANDING : 0)
				| (balance < price ? BankView.PlotOffer.CANNOT_AFFORD : 0) | (yours && room > 0 && idle(settlement) != null ? BankView.PlotOffer.CAN_HIRE : 0);
			PlotSpec s = plot.spec();
			out.add(new BankView.PlotOffer(plot.id(), path, true, s.sizeX(), s.sizeZ(), PropertyMarket.storeys(s), yours ? hireCost(settlement, plot) : price,
				cfg.minReputation(), flags, income, workers));
		}
		return out;
	}

	/** Residents who work at a plot. */
	public static List<Resident> workers(Settlement settlement, int plotId) {
		List<Resident> out = new ArrayList<>();
		for (Resident r : settlement.residents()) {
			if (r.work == plotId && r.age == AgeStage.ADULT && r.job != JobType.NONE) {
				out.add(r);
			}
		}
		return out;
	}

	/** What a business earns its owner a day with this many workers (at least one is assumed, for pricing). */
	public static double expectedIncome(Settlement settlement, PlotView plot, int workers) {
		EconomyConfig cfg = EconomyModule.config();
		JobType job = job(plot);
		double wage = cfg.households().wage(Station.of(job == null ? JobType.NONE : job));
		return Math.max(1, workers) * wage * (cfg.holdings().productivity() - 1) * (1 - cfg.holdings().upkeep());
	}

	/** Land and building as for a house, plus {@code incomeDays} of what it earns. */
	public static long price(Settlement settlement, PlotView plot, int reputation, int workers) {
		PlotSpec s = plot.spec();
		EconomyConfig cfg = EconomyModule.config();
		long property = PropertyMarket.price(s.sizeX() * s.sizeZ(), PropertyMarket.storeys(s), s.tier(), settlement.tier(), true, reputation,
			cfg.property());
		return property + Math.round(cfg.holdings().incomeDays() * expectedIncome(settlement, plot, workers));
	}

	/** What hiring one more worker costs: some days of their wage, paid up front. */
	public static long hireCost(Settlement settlement, PlotView plot) {
		EconomyConfig cfg = EconomyModule.config();
		JobType job = job(plot);
		return Math.max(1, Math.round(cfg.holdings().hireCost() * cfg.households().wage(Station.of(job == null ? JobType.NONE : job))));
	}

	/** The trade a workplace's workers follow (from its archetype), or null if the build module does not say. */
	public static @Nullable JobType job(PlotView plot) {
		if (!HearthServices.hasBlueprints()) {
			return null;
		}
		ArchetypeInfo info = HearthServices.blueprints().archetype(plot.spec().archetype());
		return info == null || info.jobs().isEmpty() ? null : info.jobs().iterator().next();
	}

	/** How many can work there: its job sites, or one. */
	static int room(Settlement settlement, PlotView plot) {
		PlotSpec s = plot.spec();
		int n = 0;
		for (Poi poi : settlement.pois(PoiType.JOB_SITE)) {
			int x = poi.pos().getX() - s.origin().getX();
			int z = poi.pos().getZ() - s.origin().getZ();
			if (x >= 0 && z >= 0 && x < s.sizeX() && z < s.sizeZ()) {
				n++;
			}
		}
		return Math.max(1, n);
	}

	/** A grown resident with no trade and no workplace, who could be hired. */
	static @Nullable Resident idle(Settlement settlement) {
		for (Resident r : settlement.residents()) {
			if (r.age == AgeStage.ADULT && r.job == JobType.NONE && r.work == Resident.NO_PLOT) {
				return r;
			}
		}
		return null;
	}

	/** Buys a business for the player; the status says what happened. */
	public static Component buy(Settlement settlement, ServerPlayer player, int plotId, long day) {
		PlotView plot = settlement.plot(plotId);
		Holdings holdings = holdings(settlement);
		if (plot == null || plot.state() != PlotState.BUILT || !isBusiness(plot.spec().archetype().getPath()) || holdings.get(plotId) != null
			|| plot.owner() != null) {
			return Component.translatable("hearth.economy.property.gone").withStyle(ChatFormatting.DARK_RED);
		}
		EconomyConfig.Holdings cfg = EconomyModule.config().holdings();
		int rep = Reputation.standing(settlement, player.getUUID());
		if (rep < cfg.minReputation()) {
			return Component.translatable("hearth.economy.property.standing", cfg.minReputation()).withStyle(ChatFormatting.DARK_RED);
		}
		long price = price(settlement, plot, rep, workers(settlement, plotId).size());
		if (!PlayerPurse.charge(player, price)) {
			return Component.translatable("hearth.economy.property.cannot_afford", CoinFormat.format(price)).withStyle(ChatFormatting.DARK_RED);
		}
		settlement.deposit(price);
		holdings.byPlot.put(plotId, new Holdings.Holding(plotId, player.getUUID(), day));
		settlement.markDirty();
		PlotSpec s = plot.spec();
		ItemStack deed = DeedItem.create(new DeedData(settlement.id(), settlement.name(), plotId, s.archetype().getPath(), true, s.origin(), s.sizeX(),
			s.sizeZ(), true));
		if (!player.getInventory().add(deed)) {
			player.drop(deed, false, Prediction.SERVER_ONLY);
		}
		Reputation.deed(settlement, player.getUUID(), Reputation.BOUGHT_BUSINESS, 3, null, player.blockPosition(), true);
		return Component.translatable("hearth.economy.business.bought", PropertyNames.lower(s.archetype().getPath()), settlement.name(),
			CoinFormat.format(price)).withStyle(ChatFormatting.DARK_GREEN);
	}

	/**
	 * Hires an idle resident into the player's business: they take its trade and work there. The sim's daily job
	 * assignment is asked to leave workers of player-owned businesses where they are (docs/requests/p3-economy.md).
	 */
	public static Component hire(Settlement settlement, ServerPlayer player, int plotId) {
		PlotView plot = settlement.plot(plotId);
		Holdings.Holding h = holdings(settlement).get(plotId);
		if (plot == null || h == null || !player.getUUID().equals(h.owner)) {
			return Component.translatable("hearth.economy.property.gone").withStyle(ChatFormatting.DARK_RED);
		}
		JobType job = job(plot);
		if (job == null) {
			return Component.translatable("hearth.economy.business.no_trade").withStyle(ChatFormatting.DARK_RED);
		}
		if (workers(settlement, plotId).size() >= room(settlement, plot)) {
			return Component.translatable("hearth.economy.business.full").withStyle(ChatFormatting.DARK_RED);
		}
		Resident hand = idle(settlement);
		if (hand == null) {
			return Component.translatable("hearth.economy.business.nobody").withStyle(ChatFormatting.DARK_RED);
		}
		long cost = hireCost(settlement, plot);
		if (!PlayerPurse.charge(player, cost)) {
			return Component.translatable("hearth.economy.property.cannot_afford", CoinFormat.format(cost)).withStyle(ChatFormatting.DARK_RED);
		}
		dev.voidmc.hearth.economy.purse.Purses.add(hand, cost); // the signing money goes to the new hand
		hand.job = job;
		hand.work = plotId;
		hand.station = Station.of(job);
		settlement.markDirty();
		return Component.translatable("hearth.economy.business.hired", hand.fullName(), PropertyNames.lower(plot.spec().archetype().getPath()))
			.withStyle(ChatFormatting.DARK_GREEN);
	}

	/** Profit of a shop a player owns, credited to the owner instead of the keeper; false if nobody owns it. */
	public static boolean creditShop(Settlement settlement, Shop shop, long bits) {
		if (shop.plot == Resident.NO_PLOT) {
			return false;
		}
		Holdings.Holding h = holdings(settlement).get(shop.plot);
		if (h == null) {
			return false;
		}
		h.today += bits;
		return true;
	}

	/**
	 * Settles the day's income of every business: whole bits into the owner's bank account (or held as pending until the
	 * bank ledger can be reached), fractions carried. Holdings whose building is gone end; the deed then names nothing.
	 */
	public static long settle(Settlement settlement, @Nullable MinecraftServer server) {
		Holdings holdings = holdings(settlement);
		long total = 0;
		for (Iterator<Holdings.Holding> it = holdings.byPlot.values().iterator(); it.hasNext(); ) {
			Holdings.Holding h = it.next();
			PlotView plot = settlement.plot(h.plot);
			if (plot == null || plot.state() != PlotState.BUILT && plot.state() != PlotState.BUILDING) {
				it.remove();
				continue;
			}
			double sum = h.today + h.fraction;
			long whole = (long) Math.floor(sum);
			h.fraction = sum - whole;
			h.today = 0;
			h.lastIncome = whole;
			h.pending += whole;
			if (server != null && h.pending > 0) {
				DepositStore.add(server, h.owner, h.pending);
				h.earned += h.pending;
				h.pending = 0;
			}
			total += whole;
		}
		settlement.markDirty();
		return total;
	}

	/** The owner of a business at a plot, or null. */
	public static @Nullable UUID owner(Settlement settlement, int plotId) {
		return holdings(settlement).owner(plotId);
	}
}
