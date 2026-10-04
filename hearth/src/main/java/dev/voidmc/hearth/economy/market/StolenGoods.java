package dev.voidmc.hearth.economy.market;

import dev.voidmc.hearth.api.HearthServices;
import dev.voidmc.hearth.api.LawService;
import dev.voidmc.hearth.economy.EconomyConfig;
import dev.voidmc.hearth.economy.EconomyModule;
import net.minecraft.world.item.ItemStack;

/**
 * What merchants make of stolen goods (docs/LIVING_WORLD.md §4): the law module marks them; an honest merchant will not
 * touch goods stolen in their own town (or anywhere, on strict servers); a fence buys them anywhere, cheaply. Without
 * the law module nothing is ever stolen.
 */
public final class StolenGoods {
	private StolenGoods() {
	}

	public enum Verdict {
		/** Not stolen (or stolen far enough away that nobody here cares): an ordinary sale. */
		CLEAN,
		/** Stolen, and this merchant will not buy it. */
		REFUSED,
		/** Stolen, and this fence will buy it at the fence's rate. */
		FENCED
	}

	public static Verdict judge(ItemStack stack, long settlementId, boolean fence) {
		LawService law = HearthServices.law();
		if (law == null || !law.isStolen(stack)) {
			return Verdict.CLEAN;
		}
		LawService.Owner from = law.stolenFrom(stack);
		return judge(true, from == null ? 0 : from.settlementId(), settlementId, fence, EconomyModule.config().fence());
	}

	/** The rule itself, free of the game. */
	public static Verdict judge(boolean stolen, long stolenFrom, long here, boolean fence, EconomyConfig.Fence cfg) {
		if (!stolen) {
			return Verdict.CLEAN;
		}
		if (fence) {
			return Verdict.FENCED;
		}
		if (cfg.refuseAnywhere() || cfg.refuseStolen() && (stolenFrom == here || stolenFrom == 0)) {
			return Verdict.REFUSED;
		}
		return Verdict.CLEAN;
	}
}
