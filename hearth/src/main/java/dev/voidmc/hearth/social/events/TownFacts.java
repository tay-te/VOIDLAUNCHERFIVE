package dev.voidmc.hearth.social.events;

import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.SettlementTier;
import java.util.Set;

/**
 * What the event rules read of a town on a day (docs/LIVING_WORLD_2.md §3.2), gathered once when the day turns
 * ({@link FactsReader}) so the scheduler is pure and cheap.
 *
 * @param timber     0–1: how much of the town is timber and thatch (fires catch there)
 * @param seat       the settlement id of the lord's seat whose domain the town is in (0: none; its own id for the seat)
 * @param threat     the strength of the boldest band within reach of the town by road (0: none)
 * @param threatBand that band's id
 * @param defence    guards × 2, walls +4, a keep +6 (§3.2)
 * @param places     the venues the town has: {@code chapel cemetery stage courthouse pillory gallows tilt_yard well
 *                   festival_ground town_hall barn storehouse}
 * @param season     0 spring, 1 summer, 2 autumn, 3 winter
 */
public record TownFacts(long id, SettlementTier tier, int population, int adults, double prosperity, Culture culture, double timber, boolean lord,
	boolean courtDay, boolean festivalDay, boolean marketDay, boolean restDay, boolean fairDay, boolean tourneyDay, long seat, int threat,
	long threatBand, int defence, Set<String> places, int season) {

	public boolean has(String place) {
		return this.places.contains(place);
	}

	/** Whether a rule's needs hold here ({@code lord}, {@code timber}, a place, a season, {@code poor}, {@code rich}). */
	public boolean meets(String need) {
		return switch (need) {
			case "lord" -> this.lord;
			case "timber" -> this.timber >= 0.25;
			case "poor" -> this.prosperity < 0.4;
			case "rich" -> this.prosperity >= 0.6;
			case "spring" -> this.season == 0;
			case "summer" -> this.season == 1;
			case "autumn" -> this.season == 2;
			case "winter" -> this.season == 3;
			case "not_winter" -> this.season != 3;
			case "market_day" -> this.marketDay;
			case "rest_day" -> this.restDay;
			case "work_day" -> !this.restDay && !this.festivalDay;
			case "domain" -> this.seat != 0 && this.seat != this.id;
			case "children" -> this.population - this.adults >= 3;
			default -> this.places.contains(need);
		};
	}

	/** A copy with another day's calendar facts (the scheduler's tests run a town across many days). */
	public TownFacts on(boolean courtDay, boolean festivalDay, boolean marketDay, boolean restDay, boolean fairDay, boolean tourneyDay, int season) {
		return new TownFacts(this.id, this.tier, this.population, this.adults, this.prosperity, this.culture, this.timber, this.lord, courtDay, festivalDay,
			marketDay, restDay, fairDay, tourneyDay, this.seat, this.threat, this.threatBand, this.defence, this.places, season);
	}
}
