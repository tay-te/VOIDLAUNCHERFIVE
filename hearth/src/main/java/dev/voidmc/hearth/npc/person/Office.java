package dev.voidmc.hearth.npc.person;

import dev.voidmc.hearth.api.AgeStage;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.api.Settlement;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Offices a town gives some of its people beyond their trade (docs/LIVING_WORLD.md §5): the guard captain leads the
 * watch and the master builder directs the builders. The reeve, priest, steward, judge and lord are trades of their
 * own ({@link JobType}); these two are the senior members of a trade, so they are derived rather than stored: the
 * longest-serving adult of the trade (earliest born, then lowest id) holds the office, and when they die or retire the
 * next in line takes it up.
 */
public enum Office {
	NONE,
	/** The senior guard: drills the watch, inspects the posts, reports to the hall each morning. */
	CAPTAIN,
	/** The senior builder: walks the sites with the plans and directs the crews between stints of building. */
	MASTER_BUILDER;

	private static final Office[] VALUES = values();

	public static Office byId(int id) {
		return id >= 0 && id < VALUES.length ? VALUES[id] : NONE;
	}

	/** The trade whose senior member holds this office, or null for {@link #NONE}. */
	public @Nullable JobType trade() {
		return switch (this) {
			case CAPTAIN -> JobType.GUARD;
			case MASTER_BUILDER -> JobType.BUILDER;
			case NONE -> null;
		};
	}

	/** The office a resident holds in their settlement: cheap (one pass over the residents), so callers cache it. */
	public static Office of(Settlement settlement, Resident resident) {
		if (resident.age != AgeStage.ADULT) {
			return NONE;
		}
		for (Office o : VALUES) {
			JobType trade = o.trade();
			if (trade != null && trade == resident.job) {
				UUID holder = holder(settlement, trade);
				return resident.id.equals(holder) ? o : NONE;
			}
		}
		return NONE;
	}

	/**
	 * Who holds the office of a trade: the adult of that trade born earliest (ties by id). Null if the trade has fewer
	 * than two adults — a lone guard is just the guard, not a captain.
	 */
	public static @Nullable UUID holder(Settlement settlement, JobType trade) {
		Resident best = null;
		int count = 0;
		for (Resident r : settlement.residents()) {
			if (r.job != trade || r.age != AgeStage.ADULT) {
				continue;
			}
			count++;
			if (best == null || r.bornDay < best.bornDay || r.bornDay == best.bornDay && r.id.compareTo(best.id) < 0) {
				best = r;
			}
		}
		return count >= 2 && best != null ? best.id : null;
	}

	public String translationKey() {
		return "hearth.npc.office." + name().toLowerCase(java.util.Locale.ROOT);
	}
}
