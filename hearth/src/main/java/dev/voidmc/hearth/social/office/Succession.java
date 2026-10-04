package dev.voidmc.hearth.social.office;

import dev.voidmc.hearth.api.AgeStage;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.api.Station;
import dev.voidmc.hearth.social.FolkFacts;
import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Who should hold an office: the incumbent while they can, then (for the lord) the eldest grown child of the last lord,
 * otherwise the best-suited resident by station, trade, temper and years. Pure: the same town gives the same choice.
 */
public final class Succession {
	private Succession() {
	}

	/** Whether a resident can hold the office at all (age and, for honours, trade). */
	public static boolean eligible(Office office, Resident r) {
		boolean adult = r.age == AgeStage.ADULT;
		boolean elder = r.age == AgeStage.ELDER;
		if (office.trade != null) {
			return adult && r.job == office.trade;
		}
		return switch (office) {
			case REEVE, LORD, JUDGE -> adult || elder;
			default -> adult;
		};
	}

	/**
	 * Chooses the holder.
	 *
	 * @param incumbent  who holds it now (kept while eligible), or null
	 * @param lastLord   for {@link Office#LORD}: the previous lord, whose eldest grown child inherits when {@code dynasty}
	 * @param otherHolders residents holding some other office (one office each)
	 * @param today      the sim day (for years)
	 */
	public static @Nullable Resident choose(Office office, Collection<Resident> residents, @Nullable UUID incumbent, @Nullable UUID lastLord,
		boolean dynasty, Set<UUID> otherHolders, long today) {
		if (incumbent != null) {
			for (Resident r : residents) {
				if (r.id.equals(incumbent) && eligible(office, r)) {
					return r;
				}
			}
		}
		if (office == Office.LORD && dynasty && lastLord != null) {
			Resident heir = null;
			for (Resident r : residents) {
				if (FolkFacts.parents(r).contains(lastLord) && eligible(office, r) && !otherHolders.contains(r.id)
					&& (heir == null || r.bornDay < heir.bornDay || r.bornDay == heir.bornDay && r.id.compareTo(heir.id) < 0)) {
					heir = r;
				}
			}
			if (heir != null) {
				return heir;
			}
		}
		Resident best = null;
		double bestScore = Double.NEGATIVE_INFINITY;
		for (Resident r : residents) {
			if (!eligible(office, r) || otherHolders.contains(r.id)) {
				continue;
			}
			double s = suitability(office, r, today);
			if (s == Double.NEGATIVE_INFINITY) {
				continue;
			}
			if (s > bestScore || s == bestScore && best != null && r.id.compareTo(best.id) < 0) {
				bestScore = s;
				best = r;
			}
		}
		return best;
	}

	/** How well a resident suits an office; higher is better, negative infinity is never. */
	static double suitability(Office office, Resident r, long today) {
		// Experience counts, up to a point: a year (in sim days) of adulthood is one point, at most four.
		double years = Math.min(4, Math.max(0, (today - r.bornDay) / 120.0));
		Station st = r.station;
		double s = years;
		switch (office) {
			case REEVE -> {
				if (r.job == JobType.PRIEST || r.job == JobType.MINTER || r.job == JobType.LORD || r.job == JobType.STEWARD) {
					return Double.NEGATIVE_INFINITY;
				}
				s += rank(st, Station.MERCHANT, 4, Station.ARTISAN, 3, Station.OFFICIAL, 3, Station.SCHOLAR, 3, Station.PEASANT, 2, Station.GENTRY, 2);
				s += trait(r, "industrious", 2) + trait(r, "sociable", 1) + trait(r, "lazy", -2) + trait(r, "dreamy", -1) + trait(r, "shy", -1);
			}
			case LORD -> {
				s += rank(st, Station.LORD, 10, Station.GENTRY, 8, Station.MERCHANT, 6, Station.OFFICIAL, 4, Station.SCHOLAR, 3, Station.ARTISAN, 2);
				s += trait(r, "brave", 1) + trait(r, "generous", 1) + trait(r, "industrious", 1);
				if (r.job == JobType.PRIEST) {
					return Double.NEGATIVE_INFINITY;
				}
			}
			case STEWARD -> {
				if (r.job == JobType.PRIEST || r.job == JobType.LORD) {
					return Double.NEGATIVE_INFINITY;
				}
				s += rank(st, Station.SCHOLAR, 5, Station.MERCHANT, 4, Station.GENTRY, 4, Station.OFFICIAL, 3, Station.ARTISAN, 2);
				s += trait(r, "industrious", 2) + trait(r, "curious", 1) + trait(r, "greedy", -1);
			}
			case JUDGE -> {
				if (r.job == JobType.LORD || r.job == JobType.PRIEST) {
					return Double.NEGATIVE_INFINITY;
				}
				s += rank(st, Station.SCHOLAR, 4, Station.OFFICIAL, 3, Station.GENTRY, 3, Station.MERCHANT, 2);
				s += trait(r, "cautious", 1) + trait(r, "pious", 1) + trait(r, "generous", 1) + trait(r, "greedy", -2);
				s += r.age == AgeStage.ELDER ? 2 : 0;
			}
			case JAILER -> {
				if (r.job == JobType.PRIEST || r.job == JobType.LORD || r.job == JobType.STEWARD) {
					return Double.NEGATIVE_INFINITY;
				}
				s += r.job == JobType.GUARD ? 5 : 0;
				s += trait(r, "brave", 2) + trait(r, "cautious", 1) + trait(r, "shy", -1);
			}
			case GUARD_CAPTAIN -> s += trait(r, "brave", 2) + trait(r, "industrious", 1) + trait(r, "lazy", -2);
			case PRIEST -> s += trait(r, "pious", 2) + trait(r, "generous", 1);
			case MASTER_BUILDER -> s += trait(r, "industrious", 2) + trait(r, "curious", 1) + trait(r, "lazy", -2);
		}
		return s;
	}

	private static double trait(Resident r, String t, double w) {
		return FolkFacts.has(r, t) ? w : 0;
	}

	private static double rank(Station st, Object... pairs) {
		for (int i = 0; i + 1 < pairs.length; i += 2) {
			if (pairs[i] == st) {
				return ((Number) pairs[i + 1]).doubleValue();
			}
		}
		return 0;
	}
}
