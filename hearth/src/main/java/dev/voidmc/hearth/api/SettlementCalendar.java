package dev.voidmc.hearth.api;

import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * A settlement's week and year (docs/LIVING_WORLD_2.md §2, §3): {@link HearthServices#calendar()}, never null
 * ({@link #NONE} until the social module's rhythm registers one). Pure lookups on the sim day.
 */
public interface SettlementCalendar {
	boolean restDay(Settlement s, long day);

	boolean washDay(Settlement s, long day);

	boolean marketDay(Settlement s, long day);

	/** The feast kept that day, or null. */
	@Nullable String feast(Settlement s, long day);

	/** Ids of the day's town events (§3). */
	List<String> events(Settlement s, long day);

	SettlementCalendar NONE = new SettlementCalendar() {
		@Override
		public boolean restDay(Settlement s, long day) {
			return false;
		}

		@Override
		public boolean washDay(Settlement s, long day) {
			return false;
		}

		@Override
		public boolean marketDay(Settlement s, long day) {
			return false;
		}

		@Override
		public @Nullable String feast(Settlement s, long day) {
			return null;
		}

		@Override
		public List<String> events(Settlement s, long day) {
			return List.of();
		}
	};
}
