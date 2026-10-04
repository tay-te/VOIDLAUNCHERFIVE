package dev.voidmc.hearth.economy;

import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.api.SettlementTier;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Prices of services other modules sell (docs/LIVING_WORLD.md §6): a bed or a room at the inn, a meal, ale, a bath, a
 * horse to hire or keep, the tailor's dyeing, the healer, a stake at dice. Base prices are config
 * ({@code services} in {@code config/hearth/economy.json}, at village prices); a hamlet asks less, a city more, and good
 * standing earns a little off.
 */
public final class Tariff {
	public static final String INN_BED = "inn_bed";
	public static final String INN_ROOM_WEEK = "inn_room_week";
	public static final String MEAL = "meal";
	public static final String ALE = "ale";
	public static final String BATH = "bath";
	public static final String STABLE_HIRE_DAY = "stable_hire_day";
	public static final String STABLE_KEEP_DAY = "stable_keep_day";
	public static final String HORSE = "horse";
	public static final String TAILOR_DYE = "tailor_dye";
	public static final String HEALER = "healer";
	public static final String DICE_STAKE = "dice_stake";

	private Tariff() {
	}

	/** What a service costs a player here, in bits (at least 1; 0 for a service the config does not know). */
	public static long price(Settlement settlement, String service, @Nullable UUID player) {
		double base = EconomyModule.config().service(service);
		if (base <= 0) {
			return 0;
		}
		int standing = player == null ? 0 : Reputation.standing(settlement, player);
		return price(base, settlement.tier(), standing);
	}

	/** The arithmetic: base × town size × standing (up to 20 % off at 100, 20 % more at −100). */
	public static long price(double base, SettlementTier tier, int standing) {
		double town = switch (tier) {
			case CAMP -> 0.6;
			case HAMLET -> 0.8;
			case VILLAGE -> 1.0;
			case TOWN -> 1.25;
			case CITY -> 1.6;
		};
		double rep = 1 - 0.2 * Math.max(-100, Math.min(100, standing)) / 100.0;
		return Math.max(1, Math.round(base * town * rep));
	}
}
