package dev.voidmc.hearth.economy.coin;

import dev.voidmc.hearth.economy.EconomyConfig;
import dev.voidmc.hearth.economy.EconomyModule;
import dev.voidmc.hearth.economy.item.EconomyItems;
import net.minecraft.world.item.ItemStack;

/**
 * Coins melt back into nuggets worth less than their face: a mark (10 bits) gives one iron nugget, a crown (100 bits)
 * two gold nuggets. Striking coin adds value only a mint can add, so melting is for a desperate smith, not a business.
 */
public final class CoinMelting {
	private CoinMelting() {
	}

	/** The furnace result for melting {@code input}, with the configured number of nuggets; others unchanged. */
	public static ItemStack yield(ItemStack input, ItemStack result) {
		Coin coin = EconomyItems.coinOf(input);
		if (coin == null || result.isEmpty()) {
			return result;
		}
		EconomyConfig.Coins c = EconomyModule.config().coins();
		int n = switch (coin) {
			case BIT -> c.meltBit();
			case MARK -> c.meltMark();
			case CROWN -> c.meltCrown();
		};
		return n <= 0 ? ItemStack.EMPTY : result.copyWithCount(Math.min(n, result.getMaxStackSize()));
	}
}
