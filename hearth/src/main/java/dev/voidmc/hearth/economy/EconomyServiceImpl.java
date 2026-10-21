package dev.voidmc.hearth.economy;

import dev.voidmc.hearth.api.EconomyService;
import dev.voidmc.hearth.api.PriceModifier;
import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.economy.coin.CoinFormat;
import dev.voidmc.hearth.economy.coin.PlayerPurse;
import dev.voidmc.hearth.economy.holdings.BusinessMarket;
import dev.voidmc.hearth.economy.price.MarketPricer;
import dev.voidmc.hearth.economy.price.PriceModifiers;
import dev.voidmc.hearth.economy.purse.Households;
import dev.voidmc.hearth.economy.purse.Purses;
import dev.voidmc.hearth.economy.shop.Shop;
import dev.voidmc.hearth.economy.shop.Shops;
import dev.voidmc.hearth.economy.works.Projects;
import java.util.Set;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * {@link EconomyService} for the other modules. Server thread only.
 *
 * <p>{@link #price} prices the whole stack ({@code stack.getCount()} items; one item for a single), which is what a
 * quest paying for "32 stone" wants and avoids rounding each item to a whole bit.
 *
 * <p>Phase 3 adds what other modules need of money through people — purses, household wealth, service prices, fines,
 * bribes, funded projects, business owners. Until the api's {@code EconomyService} carries them
 * (docs/requests/p3-economy.md), they are public methods here and on {@link EconomyAccess}.
 */
public final class EconomyServiceImpl implements EconomyService {
	@Override
	public long balance(ServerPlayer player) {
		return PlayerPurse.balance(player);
	}

	@Override
	public boolean charge(ServerPlayer player, long bits) {
		return PlayerPurse.charge(player, bits);
	}

	/** The running server, for the bank's deposits ({@link #chargeDeposit}); set when it starts, cleared when it stops. */
	private static volatile net.minecraft.server.@Nullable MinecraftServer server;

	static {
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STARTED.register(s -> server = s);
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPED.register(s -> server = null);
	}

	/**
	 * Takes coin from a player's bank deposits (docs/LIVING_WORLD_2.md §8: a household's wages and larder): all or nothing.
	 * {@code why} is for the log only.
	 */
	@Override
	public boolean chargeDeposit(UUID player, long bits, String why) {
		if (bits <= 0) {
			return true;
		}
		net.minecraft.server.MinecraftServer srv = server;
		if (srv == null || dev.voidmc.hearth.economy.bank.DepositStore.balance(srv, player) < bits) {
			return false;
		}
		return dev.voidmc.hearth.economy.bank.DepositStore.add(srv, player, -bits);
	}

	@Override
	public void pay(ServerPlayer player, long bits) {
		PlayerPurse.pay(player, bits);
	}

	@Override
	public Component format(long bits) {
		return CoinFormat.format(bits);
	}

	@Override
	public long price(Settlement settlement, ItemStack stack, boolean playerBuys) {
		return new MarketPricer(settlement).price(stack, (UUID) null, playerBuys);
	}

	/** The whole stack's price for this player: their standing and the player price modifiers apply. */
	@Override
	public long price(ServerPlayer player, Settlement settlement, ItemStack stack, boolean playerBuys) {
		return new MarketPricer(settlement).price(stack, player, playerBuys);
	}

	@Override
	public void addPriceModifier(PriceModifier modifier) {
		PriceModifiers.add(modifier);
	}

	/** A resident's fee: into their shop's till if they keep one, else their purse; with no payee, the treasury. */
	@Override
	public boolean charge(ServerPlayer player, Settlement settlement, @Nullable Resident payee, long bits) {
		if (bits <= 0) {
			return true;
		}
		if (!PlayerPurse.charge(player, bits)) {
			return false;
		}
		receive(settlement, payee == null ? null : payee.id, bits);
		return true;
	}

	// ---------------------------------------------------------------- phase 3 (api since the phase 3 integration)

	/** Coin a resident holds. */
	@Override
	public long purse(Resident resident) {
		return Purses.get(resident);
	}

	/** All the coin a resident's household (everyone sharing their home) holds: what their station follows. */
	@Override
	public long householdWealth(Settlement settlement, Resident resident) {
		return Households.wealth(settlement, resident);
	}

	/** What a service ({@link Tariff}: {@code inn_bed}, {@code bath}, {@code stable_hire_day}...) costs this player here. */
	@Override
	public long servicePrice(Settlement settlement, String service, @Nullable UUID player) {
		return Tariff.price(settlement, service, player);
	}

	/**
	 * Charges the player for a service and pays whoever provides it: the vendor's shop till if they keep one (the inn,
	 * the bathhouse, the stable), else the vendor's purse, else the treasury.
	 *
	 * @return the bits charged, or −1 if the player could not pay (nothing is taken) or the service is unknown
	 */
	@Override
	public long chargeService(ServerPlayer player, Settlement settlement, String service, @Nullable UUID vendor) {
		long bits = Tariff.price(settlement, service, player.getUUID());
		if (bits <= 0 || !PlayerPurse.charge(player, bits)) {
			return -1;
		}
		receive(settlement, vendor, bits);
		return bits;
	}

	/** Pays coin the player already handed over to a vendor (their shop's till, their purse) or the treasury. */
	void receive(Settlement settlement, @Nullable UUID vendor, long bits) {
		long day = settlement.data(EconomyModule.LEDGER).lastDay;
		Shop shop = vendor == null ? null : Shops.keptBy(settlement, vendor, day == Long.MIN_VALUE ? 0 : day);
		Resident who = vendor == null ? null : settlement.resident(vendor);
		if (shop != null) {
			shop.till += bits;
			shop.takings += bits;
		} else if (who != null) {
			Purses.add(who, bits);
		} else {
			settlement.deposit(bits);
		}
		settlement.markDirty();
	}

	/** A fine (or bail, or a bounty paid off): from the player to the settlement's treasury. False if they cannot pay. */
	@Override
	public boolean fine(ServerPlayer player, Settlement settlement, long bits) {
		if (bits <= 0) {
			return true;
		}
		if (!PlayerPurse.charge(player, bits)) {
			return false;
		}
		settlement.deposit(bits);
		settlement.markDirty();
		return true;
	}

	/** A bribe: from the player into a resident's purse (a guard, a witness). False if they cannot pay. */
	@Override
	public boolean bribe(ServerPlayer player, Settlement settlement, Resident resident, long bits) {
		if (bits <= 0 || !PlayerPurse.charge(player, bits)) {
			return false;
		}
		Purses.add(resident, bits);
		settlement.markDirty();
		return true;
	}

	@Override
	public void payResident(Settlement settlement, Resident resident, long bits) {
		if (bits > 0) {
			Purses.add(resident, bits);
			settlement.markDirty();
		}
	}

	/** Coin taken from a resident (stolen by pickpocketing, confiscated): out of their purse; returns what they had of it. */
	@Override
	public long takeFromPurse(Settlement settlement, Resident resident, long bits) {
		long t = Math.min(Math.max(0, bits), Purses.get(resident));
		Purses.add(resident, -t);
		settlement.markDirty();
		return t;
	}

	/** Plots of unfinished buildings players have paid toward: the planner and the sim put them first. */
	@Override
	public Set<Integer> fundedPlots(Settlement settlement) {
		return Projects.funded(settlement);
	}

	/**
	 * What the shop at a plot has on its shelves (docs/LIVING_WORLD_2.md §6.2), units by goods, only goods it holds any of:
	 * what its stall and counter show. Empty for a plot with no shop (or a shop sold out).
	 */
	@Override
	public java.util.Map<dev.voidmc.hearth.api.Goods, Integer> shopStock(Settlement s, int plotId) {
		dev.voidmc.hearth.economy.shop.ShopBook book = s.data(EconomyModule.SHOPS);
		java.util.Map<dev.voidmc.hearth.api.Goods, Integer> out = new java.util.EnumMap<>(dev.voidmc.hearth.api.Goods.class);
		for (Shop shop : book.shops.values()) {
			if (shop.plot != plotId) {
				continue;
			}
			for (dev.voidmc.hearth.api.Goods g : dev.voidmc.hearth.api.Goods.VALUES) {
				int n = shop.stock(g);
				if (n > 0) {
					out.merge(g, n, Integer::sum);
				}
			}
		}
		return out;
	}

	/** The player who owns the business at a plot (a workshop, shop, farm, extraction site), or null. */
	@Override
	public @Nullable UUID businessOwner(Settlement settlement, int plotId) {
		return BusinessMarket.owner(settlement, plotId);
	}
}
