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

	/**
	 * Whether {@link #pay} could give the player this much without dropping any at their feet (pouches, stacks and empty
	 * slots). Added 2026-10-21 (docs/requests/mettle-cards.md §8).
	 */
	default boolean canReceive(ServerPlayer player, long bits) {
		return true;
	}

	/**
	 * Moves coin from one player to another in one step (making change): false and nothing moves if the payer cannot pay
	 * or the receiver cannot hold it ({@link #canReceive}). A face-to-face trade's coin. Added 2026-10-21.
	 */
	default boolean transfer(ServerPlayer from, ServerPlayer to, long bits) {
		if (bits <= 0) {
			return bits == 0;
		}
		if (from == to || !canReceive(to, bits) || !charge(from, bits)) {
			return from == to && balance(from) >= bits;
		}
		pay(to, bits);
		return true;
	}

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
