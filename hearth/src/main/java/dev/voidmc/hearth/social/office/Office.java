package dev.voidmc.hearth.social.office;

import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.Station;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * The offices of a settlement. Some are posts of their own — the reeve, the lord and the steward, the judge and the
 * jailer — and give the holder that {@link JobType} and a seat to work from; the others are honours within a trade —
 * the guard captain is the senior guard, the parish priest the senior priest, the master builder the senior builder —
 * and change only the holder's title. Every holder carries {@code extra("social").office} = {@link #id()}.
 */
public enum Office {
	REEVE("reeve", JobType.REEVE, null, Station.OFFICIAL, List.of("civic/town_hall", "law/courthouse", "house/reeve_house")),
	GUARD_CAPTAIN("guard_captain", null, JobType.GUARD, Station.OFFICIAL, List.of()),
	PRIEST("priest", null, JobType.PRIEST, Station.CLERGY, List.of()),
	MASTER_BUILDER("master_builder", null, JobType.BUILDER, Station.ARTISAN, List.of()),
	LORD("lord", JobType.LORD, null, Station.LORD, List.of("noble/palace", "noble/castle", "noble/keep", "house/manor", "civic/town_hall")),
	STEWARD("steward", JobType.STEWARD, null, Station.GENTRY, List.of("noble/palace", "noble/castle", "noble/keep", "house/manor", "civic/town_hall")),
	JUDGE("judge", JobType.JUDGE, null, Station.OFFICIAL, List.of("law/courthouse")),
	JAILER("jailer", JobType.JAILER, null, Station.OFFICIAL, List.of("law/jail", "civic/guardhouse", "law/watch_house"));

	private final String name;
	/** The job the post gives its holder, or null for an honour within a trade. */
	public final @Nullable JobType post;
	/** The trade an honour is chosen from, or null for a post. */
	public final @Nullable JobType trade;
	/** The least station the holder has while in office. */
	public final Station station;
	/** Archetype paths of the buildings the holder works from, best first. */
	public final List<String> seats;

	Office(String name, @Nullable JobType post, @Nullable JobType trade, Station station, List<String> seats) {
		this.name = name;
		this.post = post;
		this.trade = trade;
		this.station = station;
		this.seats = seats;
	}

	public String id() {
		return this.name;
	}

	public String translationKey() {
		return "hearth.social.office." + this.name;
	}

	public boolean isPost() {
		return this.post != null;
	}

	public static @Nullable Office byId(String id) {
		for (Office o : values()) {
			if (o.name.equals(id)) {
				return o;
			}
		}
		return null;
	}
}
