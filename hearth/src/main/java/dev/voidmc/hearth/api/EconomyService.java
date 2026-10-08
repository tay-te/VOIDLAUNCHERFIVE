package dev.voidmc.hearth.api;

import java.util.Set;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

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

	/**
	 * What a settlement's market charges for this stack ({@code stack.getCount()} items; {@code playerBuys}) or pays for
	 * it (not). Priced as a lot, so cheap items are not rounded up to a whole bit each.
	 */
	long price(Settlement settlement, ItemStack stack, boolean playerBuys);

	/**
	 * As {@link #price(Settlement, ItemStack, boolean)} for this player: their standing in the settlement and every
	 * {@link PriceModifier} apply, as at the market counter.
	 */
	default long price(ServerPlayer player, Settlement settlement, ItemStack stack, boolean playerBuys) {
		return price(settlement, stack, playerBuys);
	}

	/**
	 * Adds a player price modifier (a character mod's speech perks, say), applied to every market price a player sees
	 * after reputation: the product of all modifiers, kept within {@link PriceModifier#MIN}–{@link PriceModifier#MAX}.
	 */
	default void addPriceModifier(PriceModifier modifier) {
	}

	// ---- phase 3: money through people (docs/LIVING_WORLD.md §7.7)

	/** Coin a resident holds (their purse), in bits. */
	default long purse(Resident resident) {
		return 0;
	}

	/** All the coin a resident's household (everyone sharing their home plot) holds: what its station follows. */
	default long householdWealth(Settlement settlement, Resident resident) {
		return purse(resident);
	}

	/**
	 * What a service costs this player here: {@code inn_bed}, {@code inn_room_week}, {@code meal}, {@code ale}, {@code bath},
	 * {@code stable_hire_day}, {@code stable_keep_day}, {@code horse}, {@code tailor_dye}, {@code healer}, {@code dice_stake}
	 * (base prices in {@code config/hearth/economy.json}); 0 if unknown.
	 */
	default long servicePrice(Settlement settlement, String service, @Nullable UUID player) {
		return 0;
	}

	/**
	 * Charges the player for a service and pays the vendor's shop till (or purse, or the treasury). Returns the bits charged,
	 * or -1 if they could not pay (nothing is taken).
	 */
	default long chargeService(ServerPlayer player, Settlement settlement, String service, @Nullable UUID vendor) {
		return -1;
	}

	/** A fine, bail or bounty: player → treasury. False (nothing taken) if they cannot pay. */
	default boolean fine(ServerPlayer player, Settlement settlement, long bits) {
		if (bits <= 0) {
			return true;
		}
		if (!charge(player, bits)) {
			return false;
		}
		settlement.deposit(bits);
		return true;
	}

	/**
	 * A fee for a resident's own service (a trainer's lesson, a scribe's letter): player → the resident's shop till if they
	 * keep one, else their purse; with no {@code payee}, the treasury. False (nothing taken) if the player cannot pay.
	 */
	default boolean charge(ServerPlayer player, Settlement settlement, @Nullable Resident payee, long bits) {
		if (bits <= 0) {
			return true;
		}
		if (!charge(player, bits)) {
			return false;
		}
		if (payee != null) {
			payResident(settlement, payee, bits);
		} else {
			settlement.deposit(bits);
		}
		return true;
	}

	/** A bribe: player → a resident's purse. False (nothing taken) if they cannot pay. */
	default boolean bribe(ServerPlayer player, Settlement settlement, Resident resident, long bits) {
		if (bits <= 0 || !charge(player, bits)) {
			return false;
		}
		payResident(settlement, resident, bits);
		return true;
	}

	/** Coin into a resident's purse (wages, a reward, a bribe already taken from the player). */
	default void payResident(Settlement settlement, Resident resident, long bits) {
	}

	/** Coin taken out of a resident's purse (pickpocketed, confiscated); returns what they had of it. Pay it on with {@link #pay}. */
	default long takeFromPurse(Settlement settlement, Resident resident, long bits) {
		return 0;
	}

	/** Plots of unfinished public buildings players have paid toward: the sim works them first, the planner never drops them. */
	default Set<Integer> fundedPlots(Settlement settlement) {
		return Set.of();
	}

	/** The player who owns the business (workshop, shop, farm, extraction site) at a plot, or null. */
	default @Nullable UUID businessOwner(Settlement settlement, int plotId) {
		return null;
	}
}
