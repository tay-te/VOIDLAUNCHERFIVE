package dev.voidmc.hearth.social.calendar;

import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Something happening in a settlement today, from {@link Agenda}: a market day, a festival, a wedding and its feast, a
 * funeral, the lord's court.
 *
 * @param key        unique within the settlement (dressings and attendance are kept by it)
 * @param start      town ticks ({@link SocialTime})
 * @param end        town ticks
 * @param festival   for a festival, which
 * @param principals the couple, the lord and steward; empty otherwise
 * @param family     those who always come (the couple's or the dead one's kin)
 * @param names      names for the words of it (the couple, the dead, the visiting trader's town)
 */
public record TownEvent(String key, Kind kind, long start, long end, @Nullable Festival festival, List<UUID> principals, List<UUID> family,
	List<String> names) {

	public enum Kind {
		MARKET("market"),
		FESTIVAL("festival"),
		WEDDING("wedding"),
		FEAST("feast"),
		FUNERAL("funeral"),
		COURT("court");

		private final String name;

		Kind(String name) {
			this.name = name;
		}

		public String id() {
			return this.name;
		}
	}

	public boolean activeAt(long townTick) {
		return townTick >= this.start && townTick < this.end;
	}

	public boolean involves(UUID resident) {
		return this.principals.contains(resident) || this.family.contains(resident);
	}
}
