package dev.voidmc.hearth.social.lives;

import dev.voidmc.hearth.api.AgeStage;
import dev.voidmc.hearth.api.HearthServices;
import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.npc.routine.Activity;
import dev.voidmc.hearth.npc.routine.Leisure;
import dev.voidmc.hearth.npc.routine.Schedule;
import dev.voidmc.hearth.npc.routine.ScheduleOverrides;
import dev.voidmc.hearth.sim.lives.Life;
import dev.voidmc.hearth.sim.lives.Lives;
import dev.voidmc.hearth.sim.lives.LivesBook;
import dev.voidmc.hearth.sim.lives.TownLives;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.jspecify.annotations.Nullable;

/**
 * Personal days (docs/LIVING_WORLD_2.md §4.3, band 35), laid over the routine through {@link ScheduleOverrides} so the
 * embodied and the placed day agree: a named character keeps their role's windows (the drunk smith at the tavern every
 * night, the widow at the well of a morning, the old soldier on his bench at noon), sweethearts walk out on the rest
 * day's afternoon to a viewpoint, and those in mourning go to the grave on the rest-day morning. Registered at init, so
 * these win over the week's own windows (lane A2's rest day) where they overlap. Pure of the day; asked once per resident
 * per day.
 */
final class NotableSchedule implements ScheduleOverrides.Source {
	static final NotableSchedule INSTANCE = new NotableSchedule();
	static final int COURT_FROM = 14 * 60;
	static final int COURT_TO = 16 * 60 + 30;
	static final int GRAVE_FROM = 9 * 60 + 30;
	static final int GRAVE_TO = 11 * 60;

	private NotableSchedule() {
	}

	@Override
	public List<ScheduleOverrides.Window> windows(Settlement settlement, Resident resident, long day) {
		if (settlement == null || resident == null || !Lives.config().lives().enabled()) {
			return List.of();
		}
		boolean rest = HearthServices.calendar().restDay(settlement, day);
		List<ScheduleOverrides.Window> out = null;
		TownLives town = settlement.data(Lives.TOWN);
		TownLives.Notable n = town.notables.isEmpty() ? null : town.notable(resident.id);
		if (n != null) {
			LivesBook.Role role = LivesBook.get().role(n.role);
			if (role != null) {
				for (LivesBook.Window w : role.schedule()) {
					if (!w.beats().isEmpty() && !w.beats().contains(n.beat)) {
						continue;
					}
					if (w.days().equals("rest") && !rest || w.days().equals("work") && rest) {
						continue;
					}
					ScheduleOverrides.Window win = window(w);
					if (win != null) {
						if (out == null) {
							out = new ArrayList<>(3);
						}
						out.add(win);
					}
				}
			}
		}
		if (rest && resident.age != AgeStage.CHILD && Life.peek(resident) != null) {
			if (Life.mourning(resident, day)) {
				if (out == null) {
					out = new ArrayList<>(2);
				}
				out.add(new ScheduleOverrides.Window(GRAVE_FROM, GRAVE_TO, Activity.LEISURE, Leisure.PRAY, null));
			}
			if (Life.sweetheart(resident) != null) {
				if (out == null) {
					out = new ArrayList<>(2);
				}
				out.add(new ScheduleOverrides.Window(COURT_FROM, COURT_TO, Activity.LEISURE, Leisure.VIEWPOINT, null));
			}
		}
		return out == null ? List.of() : out;
	}

	static ScheduleOverrides.@Nullable Window window(LivesBook.Window w) {
		try {
			Activity a = Activity.valueOf(w.activity().toUpperCase(Locale.ROOT));
			Leisure l = w.leisure() == null ? null : Leisure.valueOf(w.leisure().toUpperCase(Locale.ROOT));
			Schedule.Where where = w.where() == null ? null : Schedule.Where.valueOf(w.where().toUpperCase(Locale.ROOT));
			if (a == Activity.LEISURE && l == null) {
				l = Leisure.PLAZA;
			}
			return new ScheduleOverrides.Window(w.from(), w.to(), a, l, where);
		} catch (IllegalArgumentException e) {
			return null;
		}
	}
}
