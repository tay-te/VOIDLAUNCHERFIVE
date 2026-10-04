package dev.voidmc.hearth.economy.purse;

import dev.voidmc.hearth.api.AgeStage;
import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.api.Station;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Residents grouped into households: everyone who shares a home plot is one household, sharing its purse when it comes
 * to wealth and station; someone without a home plot is a household of one. Built on demand, in resident order, so the
 * daily step is deterministic.
 */
public final class Households {
	private Households() {
	}

	/** One household. */
	public record Household(int home, List<Resident> members) {
		/** All the coin its members hold. */
		public long wealth() {
			long w = 0;
			for (Resident r : this.members) {
				w += Purses.get(r);
			}
			return w;
		}

		public int adults() {
			int n = 0;
			for (Resident r : this.members) {
				if (r.age != AgeStage.CHILD) {
					n++;
				}
			}
			return n;
		}

		/** The household's station: the highest among its grown members (a merchant's children are a merchant's). */
		public Station station() {
			Station best = null;
			for (Resident r : this.members) {
				if (r.age != AgeStage.CHILD && (best == null || StationRule.rank(r.station) > StationRule.rank(best))) {
					best = r.station;
				}
			}
			return best != null ? best : this.members.getFirst().station;
		}

		/** The richest member: who pays first when the household buys something. */
		public Resident payer() {
			Resident best = this.members.getFirst();
			for (Resident r : this.members) {
				if (Purses.get(r) > Purses.get(best)) {
					best = r;
				}
			}
			return best;
		}

		/**
		 * Takes {@code bits} from the household's purses, richest first; returns what it could take (all of it, or what
		 * they had).
		 */
		public long spend(long bits) {
			long left = bits;
			while (left > 0) {
				Resident payer = payer();
				long have = Purses.get(payer);
				if (have <= 0) {
					break;
				}
				long t = Math.min(have, left);
				Purses.add(payer, -t);
				left -= t;
			}
			return bits - left;
		}
	}

	public static List<Household> of(Settlement settlement) {
		Map<Integer, List<Resident>> byHome = new LinkedHashMap<>();
		int alone = -2;
		for (Resident r : settlement.residents()) {
			int key = r.home != Resident.NO_PLOT ? r.home : alone--;
			byHome.computeIfAbsent(key, k -> new ArrayList<>()).add(r);
		}
		List<Household> out = new ArrayList<>(byHome.size());
		byHome.forEach((home, members) -> out.add(new Household(home, members)));
		return out;
	}

	/** The household a resident belongs to (those sharing their home plot), or just them. */
	public static Household of(Settlement settlement, Resident resident) {
		if (resident.home == Resident.NO_PLOT) {
			return new Household(-2, List.of(resident));
		}
		List<Resident> members = new ArrayList<>();
		for (Resident r : settlement.residents()) {
			if (r.home == resident.home) {
				members.add(r);
			}
		}
		if (members.isEmpty()) {
			members.add(resident);
		}
		return new Household(resident.home, members);
	}

	/** All the coin a resident's household holds. */
	public static long wealth(Settlement settlement, Resident resident) {
		return of(settlement, resident).wealth();
	}
}
