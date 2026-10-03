package dev.voidmc.hearth.api;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * The economy module, as other modules use it. All amounts are in <b>bits</b>, the smallest coin; the economy module
 * defines the coins and what each is worth in bits. Registered in {@link HearthServices}; may be absent.
 */
public interface EconomyService {
	/** Coin the player can spend: carried coins and pouches (not bank deposits). */
	long balance(ServerPlayer player);

	/** Takes coin from the player (making change as needed); returns false and takes nothing if they cannot pay. */
	boolean charge(ServerPlayer player, long bits);

	/** Gives the player coin in the fewest coins (into a pouch or inventory, dropping what does not fit). */
	void pay(ServerPlayer player, long bits);

	/** "3 crowns, 4 marks, 2 bits" — however the economy module names its coins. */
	Component format(long bits);

	/** What a settlement's market charges for one of this item ({@code playerBuys}) or pays for one (not). */
	long price(Settlement settlement, ItemStack stack, boolean playerBuys);
}
