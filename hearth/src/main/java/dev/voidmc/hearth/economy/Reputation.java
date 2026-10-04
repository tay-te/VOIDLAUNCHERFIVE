package dev.voidmc.hearth.economy;

import dev.voidmc.hearth.api.HearthServices;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.api.StandingService;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

/**
 * The economy's side of reputation: reads standing, charisma and opinion from the standing service when it is present,
 * and reports what players do with their money as deeds ({@code economy:trade}, {@code economy:haggle_soured},
 * {@code economy:funded_project}, {@code economy:contract_fulfilled}, {@code economy:contract_failed},
 * {@code economy:bought_business}). Without the standing service, deeds move the settlement's raw
 * reputation by their weight and charisma and opinion read as neutral.
 */
public final class Reputation {
	public static final String TRADE = "economy:trade";
	public static final String HAGGLE_SOURED = "economy:haggle_soured";
	public static final String FUNDED_PROJECT = "economy:funded_project";
	public static final String CONTRACT_FULFILLED = "economy:contract_fulfilled";
	public static final String CONTRACT_FAILED = "economy:contract_failed";
	public static final String BOUGHT_BUSINESS = "economy:bought_business";
	public static final String BOUGHT_PROPERTY = "economy:bought_property";

	private Reputation() {
	}

	/** The player's standing in the settlement, −100..100. */
	public static int standing(Settlement settlement, UUID player) {
		StandingService s = HearthServices.standing();
		return s != null ? s.standing(settlement.id(), player) : settlement.reputation(player);
	}

	/** How the player comes across, −1..1 (0 without the standing service). */
	public static float charisma(@Nullable ServerPlayer player) {
		StandingService s = HearthServices.standing();
		return s != null && player != null ? s.charisma(player) : 0F;
	}

	/** A resident's opinion of the player, −100..100 (0 without the standing service). */
	public static int opinion(@Nullable UUID resident, UUID player) {
		StandingService s = HearthServices.standing();
		return s != null && resident != null ? s.opinion(resident, player) : 0;
	}

	/** Reports a deed (or, without the standing service, moves the settlement's reputation by its weight). */
	public static void deed(Settlement settlement, UUID player, String kind, int weight, @Nullable UUID resident, @Nullable BlockPos pos,
		boolean witnessed) {
		if (weight == 0) {
			return;
		}
		StandingService s = HearthServices.standing();
		if (s != null) {
			s.deed(new StandingService.Deed(player, settlement.id(), kind, weight, resident, pos != null ? pos : settlement.center(), witnessed));
		} else {
			settlement.addReputation(player, weight);
			settlement.markDirty();
		}
	}
}
