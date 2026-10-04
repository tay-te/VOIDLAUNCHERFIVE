package dev.voidmc.hearth.npc.person;

import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.api.Station;

/**
 * Keeps a resident's station in step with a change of trade (docs/LIVING_WORLD.md §8.1: a station "starts from the job,
 * then follows the purse"). Residents are made with the station of their first job — usually none, which is poor — and
 * may be given a trade later; if nobody has moved their station since (it is still what the old trade gave), it follows
 * the new trade. A station the economy moved (a prosperous farmer risen to merchant, a ruined smith fallen to poor) is
 * left alone. The trade the station was last derived from is remembered in {@code extra("npc").station_job}.
 */
public final class Stations {
	private static final String KEY = "station_job";

	private Stations() {
	}

	/** Brings the station up to date with the trade; returns true if it changed. */
	public static boolean sync(Resident resident) {
		NpcData data = NpcData.of(resident);
		String recorded = data.tag().getStringOr(KEY, "");
		JobType from = null;
		for (JobType j : JobType.values()) {
			if (j.getSerializedName().equals(recorded)) {
				from = j;
				break;
			}
		}
		if (from == resident.job) {
			return false;
		}
		// First sight: assume the station was derived from no trade, the way a newborn record is made.
		Station derived = Station.of(from != null ? from : JobType.NONE);
		data.tag().putString(KEY, resident.job.getSerializedName());
		if (resident.station == derived || from == null && resident.station == Station.of(resident.job)) {
			Station now = Station.of(resident.job);
			if (resident.station != now) {
				resident.station = now;
				return true;
			}
		}
		return false;
	}
}
