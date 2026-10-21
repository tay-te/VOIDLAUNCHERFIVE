package dev.voidmc.hearth.social.events;

import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.api.SettlementCalendar;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * The calendar with the town events in it (docs/LIVING_WORLD_2.md §11.4: {@code SettlementCalendar.events} — "ids of
 * the day's town events (§3)"): the rhythm's calendar (lane A2) answers the week and the feasts and its own gatherings;
 * this adds the event book's events of that day ({@code events:<kind>:<id>}). Everything else is passed through.
 */
final class EventsCalendar implements SettlementCalendar {
	private final SettlementCalendar base;

	EventsCalendar(SettlementCalendar base) {
		this.base = base;
	}

	@Override
	public boolean restDay(Settlement s, long day) {
		return this.base.restDay(s, day);
	}

	@Override
	public boolean washDay(Settlement s, long day) {
		return this.base.washDay(s, day);
	}

	@Override
	public boolean marketDay(Settlement s, long day) {
		return this.base.marketDay(s, day);
	}

	@Override
	public @Nullable String feast(Settlement s, long day) {
		return this.base.feast(s, day);
	}

	@Override
	public List<String> events(Settlement s, long day) {
		List<String> out = new ArrayList<>(this.base.events(s, day));
		for (Scheduled e : s.data(EventBook.KEY).scheduled) {
			if (e.day() == day) {
				out.add(e.key());
			}
		}
		return out;
	}

	SettlementCalendar base() {
		return this.base;
	}
}
