package dev.voidmc.hearth.economy.dialogue;

import dev.voidmc.hearth.api.DialogueProvider;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.PlotView;
import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.economy.EconomyMenus;
import dev.voidmc.hearth.economy.EconomyModule;
import dev.voidmc.hearth.economy.bank.BankView;
import dev.voidmc.hearth.economy.shop.Shop;
import dev.voidmc.hearth.economy.shop.Shops;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

/**
 * The economy's lines in conversation:
 * <ul>
 *   <li>"Let's trade." with whoever keeps a shop (its own stock and till) — or, for a merchant without one, the town's
 *       market;</li>
 *   <li>a fence's quieter offer, which opens their back room (stolen goods bought, cheaply);</li>
 *   <li>banking and property with minters and bank workers; property, public works and supply contracts with the reeve,
 *       the steward and whoever keeps the town hall; public works with the master builder at the builders' yard.</li>
 * </ul>
 */
public final class EconomyDialogue implements DialogueProvider {
	public static final String TRADE = "economy:trade";
	public static final String FENCE = "economy:fence";
	public static final String BANK = "economy:bank";
	public static final String PROPERTY = "economy:property";
	public static final String WORKS = "economy:works";
	public static final String CONTRACTS = "economy:contracts";

	@Override
	public void collect(ServerPlayer player, Settlement settlement, Resident resident, List<Option> out) {
		String work = workplace(settlement, resident);
		Shop shop = shop(player, settlement, resident);
		if (shop != null && shop.fence) {
			out.add(new Option(FENCE, Component.translatable("hearth.economy.dialogue.fence"), 105));
		} else if (shop != null || isTrader(resident, work)) {
			out.add(new Option(TRADE, Component.translatable("hearth.economy.dialogue.trade"), 100));
		}
		if (isBanker(resident, work)) {
			out.add(new Option(BANK, Component.translatable("hearth.economy.dialogue.bank"), 110));
		}
		if (isBanker(resident, work) || isOfficial(resident, work)) {
			out.add(new Option(PROPERTY, Component.translatable("hearth.economy.dialogue.property"), 120));
		}
		if (isOfficial(resident, work) || isMasterBuilder(resident, work)) {
			out.add(new Option(WORKS, Component.translatable("hearth.economy.dialogue.works"), 125));
		}
		if (isOfficial(resident, work)) {
			out.add(new Option(CONTRACTS, Component.translatable("hearth.economy.dialogue.contracts"), 130));
		}
	}

	@Override
	public boolean select(ServerPlayer player, Settlement settlement, Resident resident, String optionId) {
		String work = workplace(settlement, resident);
		switch (optionId) {
			case TRADE, FENCE -> {
				Shop shop = shop(player, settlement, resident);
				if (shop == null && !isTrader(resident, work) || optionId.equals(FENCE) && (shop == null || !shop.fence)) {
					return false;
				}
				EconomyMenus.openShop(player, settlement, shop, true);
			}
			case BANK -> {
				if (!isBanker(resident, work)) {
					return false;
				}
				EconomyMenus.openBank(player, settlement, true, true, BankView.TAB_BANK, true);
			}
			case PROPERTY -> {
				boolean banker = isBanker(resident, work);
				if (!banker && !isOfficial(resident, work)) {
					return false;
				}
				EconomyMenus.openBank(player, settlement, banker, true, BankView.TAB_PROPERTY, true);
			}
			case WORKS -> {
				if (!isOfficial(resident, work) && !isMasterBuilder(resident, work)) {
					return false;
				}
				EconomyMenus.openBank(player, settlement, false, true, BankView.TAB_WORKS, true);
			}
			case CONTRACTS -> {
				if (!isOfficial(resident, work)) {
					return false;
				}
				EconomyMenus.openBank(player, settlement, false, true, BankView.TAB_CONTRACTS, true);
			}
			default -> {
				return false;
			}
		}
		return true;
	}

	/** The open shop this resident keeps, if any. */
	private static @Nullable Shop shop(ServerPlayer player, Settlement settlement, Resident resident) {
		return Shops.keptBy(settlement, resident.id, EconomyModule.day(player.level()));
	}

	/** Merchants and traders, and anyone whose workplace is a market stall, market hall or shophouse. */
	static boolean isTrader(Resident r, String work) {
		return r.job == JobType.MERCHANT || r.job == JobType.TRADER || work.startsWith("commerce/market") || work.equals("house/shophouse");
	}

	/** Minters, and anyone who works at the bank. */
	static boolean isBanker(Resident r, String work) {
		return r.job == JobType.MINTER || work.equals("commerce/bank");
	}

	/** The reeve, the steward and whoever keeps the town hall (or the counting house). */
	static boolean isOfficial(Resident r, String work) {
		return r.job == JobType.REEVE || r.job == JobType.STEWARD || work.equals("civic/town_hall") || work.equals("commerce/counting_house");
	}

	/** A builder at the builders' yard. */
	static boolean isMasterBuilder(Resident r, String work) {
		return r.job == JobType.BUILDER && work.equals("work/builders_yard");
	}

	/** The archetype path of the resident's workplace, or "". */
	static String workplace(Settlement settlement, Resident r) {
		if (r.work == Resident.NO_PLOT) {
			return "";
		}
		PlotView plot = settlement.plot(r.work);
		return plot == null ? "" : plot.spec().archetype().getPath();
	}
}
