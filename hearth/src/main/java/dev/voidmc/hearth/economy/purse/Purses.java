package dev.voidmc.hearth.economy.purse;

import dev.voidmc.hearth.api.Resident;
import net.minecraft.nbt.CompoundTag;

/**
 * A resident's purse and the economy's other notes on them, kept in {@code Resident.extra("economy")} so the sim saves
 * them with the person without knowing what they are:
 *
 * <pre>
 *   purse        long   coin they hold, in bits
 *   hunger       int    days in a row their household could not pay for its food
 *   seeded       bool   the economy has given them their starting savings
 *   station_day  long   the sim day their household's station last moved
 * </pre>
 *
 * Reads never create the compound (a resident the economy has not touched costs nothing); writes do. Server thread only.
 */
public final class Purses {
	public static final String KEY = "economy";
	static final String PURSE = "purse";
	static final String HUNGER = "hunger";
	static final String SEEDED = "seeded";
	static final String STATION_DAY = "station_day";

	private Purses() {
	}

	private static CompoundTag peek(Resident r) {
		return r.extra.getCompoundOrEmpty(KEY);
	}

	/** Coin the resident holds, in bits. */
	public static long get(Resident r) {
		return peek(r).getLongOr(PURSE, 0L);
	}

	public static void set(Resident r, long bits) {
		r.extra(KEY).putLong(PURSE, Math.max(0, bits));
	}

	/** Adds coin (or, negative, takes it, never below zero). */
	public static void add(Resident r, long bits) {
		if (bits != 0) {
			set(r, get(r) + bits);
		}
	}

	/** Takes coin if the purse holds it all; returns whether it did. */
	public static boolean take(Resident r, long bits) {
		long have = get(r);
		if (bits < 0 || have < bits) {
			return false;
		}
		set(r, have - bits);
		return true;
	}

	public static int hunger(Resident r) {
		return peek(r).getIntOr(HUNGER, 0);
	}

	public static void setHunger(Resident r, int days) {
		if (days != hunger(r)) {
			r.extra(KEY).putInt(HUNGER, Math.max(0, days));
		}
	}

	public static boolean seeded(Resident r) {
		return peek(r).getBooleanOr(SEEDED, false);
	}

	public static void markSeeded(Resident r) {
		r.extra(KEY).putBoolean(SEEDED, true);
	}

	public static long stationDay(Resident r) {
		return peek(r).getLongOr(STATION_DAY, Long.MIN_VALUE);
	}

	public static void setStationDay(Resident r, long day) {
		r.extra(KEY).putLong(STATION_DAY, day);
	}
}
