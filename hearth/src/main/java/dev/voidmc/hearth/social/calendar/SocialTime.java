package dev.voidmc.hearth.social.calendar;

import dev.voidmc.hearth.api.HearthWorld;
import net.minecraft.server.level.ServerLevel;

/**
 * Time as town life reads it: the sim calendar's day (which runs from sunrise, 06:00, to the next sunrise, as the
 * overworld day does) and the minute of the day on the overworld clock. An instant is written as "town ticks":
 * {@code day × 24000 + ticks since 06:00}, so events and the clock compare directly even when the sim calendar runs
 * ahead of the overworld clock (a fast-forwarded settlement).
 */
public final class SocialTime {
	public static final int DAY_TICKS = 24000;
	public static final int MINUTES = 24 * 60;
	/** The sim day starts at sunrise. */
	public static final int DAY_START_MINUTE = 6 * 60;

	private SocialTime() {
	}

	/** Ticks since sunrise of a minute of the day (0 = midnight, so 05:00 is late in the day that began at 06:00). */
	public static long offset(int minute) {
		return (long) Math.floorMod(minute - DAY_START_MINUTE, MINUTES) * DAY_TICKS / MINUTES;
	}

	/** The town tick of a minute of a day. */
	public static long at(long day, int minute) {
		return day * DAY_TICKS + offset(minute);
	}

	/** The minute of the day (0 = midnight) for an overworld clock. */
	public static int minuteOfDay(long clock) {
		long t = Math.floorMod(clock, (long) DAY_TICKS);
		return (int) ((t * MINUTES / DAY_TICKS + DAY_START_MINUTE) % MINUTES);
	}

	/** Now, in town ticks, for a Hearth world (the sim's day and the overworld clock's time of day). */
	public static long now(HearthWorld world) {
		ServerLevel level = world.level();
		long clock = level.getOverworldClockTime();
		return world.day() * DAY_TICKS + Math.floorMod(clock, (long) DAY_TICKS);
	}

	/** Now, without a Hearth world (the overworld clock alone). */
	public static long now(ServerLevel level) {
		return level.getOverworldClockTime();
	}

	public static long day(long townTick) {
		return Math.floorDiv(townTick, (long) DAY_TICKS);
	}

	public static int minute(long townTick) {
		return minuteOfDay(townTick);
	}
}
