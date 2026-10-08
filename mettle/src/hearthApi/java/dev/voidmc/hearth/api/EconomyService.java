package dev.voidmc.hearth.api;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/** Stub of Hearth's economy service: only the members Mettle calls (see package-info). All amounts in bits. */
public interface EconomyService {
	/** Coin the player can spend: carried coins and pouches (not bank deposits). */
	long balance(ServerPlayer player);

	/** Takes coin from the player (making change as needed); returns false and takes nothing if they cannot pay. */
	boolean charge(ServerPlayer player, long bits);

	/** Gives the player coin in the fewest coins (into a pouch or inventory, dropping what does not fit). */
	void pay(ServerPlayer player, long bits);

	/** "3 crowns, 4 marks, 2 bits". */
	Component format(long bits);

	/** What a settlement's market charges for this stack (playerBuys) or pays for it, before any player's standing. */
	long price(Settlement settlement, ItemStack stack, boolean playerBuys);

	/** As the market counter prices it for this player, every {@link PriceModifier} applied. */
	default long price(ServerPlayer player, Settlement settlement, ItemStack stack, boolean playerBuys) {
		throw new UnsupportedOperationException("Hearth api stub");
	}

	default void addPriceModifier(PriceModifier modifier) {
		throw new UnsupportedOperationException("Hearth api stub");
	}

	/** A fee for a resident's own service: player → their shop till or purse. False (nothing taken) if they cannot pay. */
	default boolean charge(ServerPlayer player, Settlement settlement, @Nullable Resident payee, long bits) {
		throw new UnsupportedOperationException("Hearth api stub");
	}
}
