package dev.voidmc.hearth.economy.dialogue;

import dev.voidmc.hearth.api.DialogueProvider;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.PlotView;
import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.economy.EconomyMenus;
import dev.voidmc.hearth.economy.bank.BankView;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * The economy's lines in conversation: "Let's trade." with merchants and anyone keeping a market stall or shop;
 * banking and property with minters (the bank counter); property with whoever keeps the town hall.
 */
public final class EconomyDialogue implements DialogueProvider {
	public static final String TRADE = "economy:trade";
	public static final String BANK = "economy:bank";
	public static final String PROPERTY = "economy:property";

	@Override
	public void collect(ServerPlayer player, Settlement settlement, Resident resident, List<Option> out) {
		String work = workplace(settlement, resident);
		if (isTrader(resident, work)) {
			out.add(new Option(TRADE, Component.translatable("hearth.economy.dialogue.trade"), 100));
		}
		if (isBanker(resident, work)) {
			out.add(new Option(BANK, Component.translatable("hearth.economy.dialogue.bank"), 110));
		}
		if (isBanker(resident, work) || work.equals("civic/town_hall")) {
			out.add(new Option(PROPERTY, Component.translatable("hearth.economy.dialogue.property"), 120));
		}
	}

	@Override
	public boolean select(ServerPlayer player, Settlement settlement, Resident resident, String optionId) {
		String work = workplace(settlement, resident);
		switch (optionId) {
			case TRADE -> {
				if (!isTrader(resident, work)) {
					return false;
				}
				EconomyMenus.openMarket(player, settlement, true);
			}
			case BANK -> {
				if (!isBanker(resident, work)) {
					return false;
				}
				EconomyMenus.openBank(player, settlement, true, BankView.TAB_BANK, true);
			}
			case PROPERTY -> {
				boolean banker = isBanker(resident, work);
				if (!banker && !work.equals("civic/town_hall")) {
					return false;
				}
				EconomyMenus.openBank(player, settlement, banker, BankView.TAB_PROPERTY, true);
			}
			default -> {
				return false;
			}
		}
		return true;
	}

	/** Merchants, and anyone whose workplace is a market stall, market hall or shophouse. */
	static boolean isTrader(Resident r, String work) {
		return r.job == JobType.MERCHANT || work.startsWith("commerce/market") || work.equals("house/shophouse");
	}

	/** Minters, and anyone who works at the bank. */
	static boolean isBanker(Resident r, String work) {
		return r.job == JobType.MINTER || work.equals("commerce/bank");
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
