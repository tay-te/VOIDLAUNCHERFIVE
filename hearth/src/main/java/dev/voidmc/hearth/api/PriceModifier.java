package dev.voidmc.hearth.api;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Moves a market's prices for one player (a character mod's speech perks, presence, a merchant's background). Added with
 * {@link EconomyService#addPriceModifier}; applied after reputation to what the player pays and what they are paid. All
 * modifiers' factors are multiplied together and the product kept within 0.5–1.5, and what the town pays stays below
 * what it charges. Server thread only.
 */
@FunctionalInterface
public interface PriceModifier {
	/** The lowest and highest the product of every modifier's factor may reach. */
	double MIN = 0.5;
	double MAX = 1.5;

	/**
	 * The factor on one item's price: below 1 cheaper for the player when they buy, above 1 better for them when they
	 * sell. 1 for no change.
	 *
	 * @param one        one item of the stack being priced
	 * @param playerBuys whether the player buys (else sells)
	 */
	double factor(ServerPlayer player, Settlement settlement, ItemStack one, boolean playerBuys);
}
