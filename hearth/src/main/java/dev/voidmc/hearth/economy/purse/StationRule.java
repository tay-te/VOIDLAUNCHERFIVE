package dev.voidmc.hearth.economy.purse;

import dev.voidmc.hearth.api.Station;
import dev.voidmc.hearth.economy.EconomyConfig;
import java.util.function.ToDoubleFunction;

/**
 * Station follows the purse (docs/LIVING_WORLD.md §8.1), as pure arithmetic. Working households climb a ladder —
 * poor, peasant, artisan, merchant, gentry — one rung at a time: a household that has saved {@code riseDays} of the next
 * rung's wages per head rises (at most one rung above what its trade brings, and gentry only in a town or city); one that
 * has gone hungry for {@code hungerDays} with less than {@code fallDays} of its own wages per head falls a rung, as far
 * as poor. Clergy, scholars, officials and lords hold their station by office, not by purse. A move is held for
 * {@code holdDays} before the next.
 */
public final class StationRule {
	private static final Station[] LADDER = {Station.POOR, Station.PEASANT, Station.ARTISAN, Station.MERCHANT, Station.GENTRY};

	private StationRule() {
	}

	/** Place on the ladder, or −1 for stations held by office. */
	public static int ladder(Station s) {
		for (int i = 0; i < LADDER.length; i++) {
			if (LADDER[i] == s) {
				return i;
			}
		}
		return -1;
	}

	/** An order of standing for every station (for picking a household's highest). */
	public static int rank(Station s) {
		return switch (s) {
			case POOR -> 0;
			case PEASANT -> 1;
			case ARTISAN -> 2;
			case CLERGY, SCHOLAR -> 3;
			case MERCHANT, OFFICIAL -> 4;
			case GENTRY -> 5;
			case LORD -> 6;
		};
	}

	/**
	 * The station a household should have now.
	 *
	 * @param current       its station today
	 * @param base          the station its trade brings ({@link Station#of})
	 * @param wealthPerHead its coin per member
	 * @param hungerDays    days in a row it could not pay for food
	 * @param gentryAllowed whether the settlement is large enough for gentry (town or city)
	 * @param wage          a day's wage by station
	 */
	public static Station next(Station current, Station base, double wealthPerHead, int hungerDays, boolean gentryAllowed,
		ToDoubleFunction<Station> wage, EconomyConfig.Station cfg) {
		int b = ladder(base);
		int c = ladder(current);
		if (b < 0) {
			// held by office: the office decides, unless ruin has already dragged them onto the ladder and they recover
			return c < 0 || wealthPerHead >= cfg.fallDays() * wage.applyAsDouble(base) && hungerDays == 0 ? base : current;
		}
		if (c < 0) {
			return base; // their trade changed from an office to a living
		}
		if (hungerDays >= cfg.hungerDays() && c > 0 && wealthPerHead < cfg.fallDays() * wage.applyAsDouble(current)) {
			return LADDER[c - 1];
		}
		if (hungerDays == 0 && c < LADDER.length - 1 && c < b + 1) {
			Station up = LADDER[c + 1];
			if ((up != Station.GENTRY || gentryAllowed) && wealthPerHead >= cfg.riseDays() * wage.applyAsDouble(up)) {
				return up;
			}
		}
		if (c > b + 1) {
			return LADDER[b + 1]; // their trade changed to a humbler one
		}
		return current;
	}
}
