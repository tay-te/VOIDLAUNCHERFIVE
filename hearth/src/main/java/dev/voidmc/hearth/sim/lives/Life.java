package dev.voidmc.hearth.sim.lives;

import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.Resident;
import java.util.Arrays;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.Tag;
import org.jspecify.annotations.Nullable;

/**
 * One resident's part of the lives (docs/LIVING_WORLD_2.md §4.2), kept in {@code Resident.extra("lives")} so it is saved
 * with them and dies with them:
 *
 * <pre>
 *   rel     long[]  up to 8 relations, 3 longs each: the other's id (2) and (strength + 128) | kind << 8 | (last day + 2^40) << 12
 *   am, al  long    an apprentice's master; at the trade; as the day apprenticed
 *   jt, js          a journeyman's trade and the day they took it up; jh the days since spent off it (jd the last)
 *   mt, md          a master's trade and the day they made master
 *   gu      long    in mourning until this day
 *   idle    long    kept from their trade until this day (a thread's beat: the forge lost)
 *   wed     byte    their wedding has been counted (sweethearts' weddings in the stats)
 *   widowed byte    their spouse died
 *   arr     long    the day a grown newcomer arrived
 * </pre>
 *
 * At most ~200 bytes a resident. Read in place without copying (the relations array is the tag's own and is changed in
 * place); server thread only.
 */
public final class Life {
	public static final String KEY = "lives";
	public static final int MAX_RELATIONS = 8;
	private static final long DAY_OFFSET = 1L << 40;

	/** What two residents are to each other. Order matters only for saving (the ordinal is saved). */
	public enum Bond {
		NONE, ACQUAINTANCE, FRIEND, SWEETHEART, RIVAL, FEUD, MENTOR, APPRENTICE;

		static final Bond[] VALUES = values();

		public String id() {
			return name().toLowerCase(java.util.Locale.ROOT);
		}

		/** Bonds kept whatever the strength (not pushed out of a full book by a new acquaintance). */
		boolean pinned() {
			return this == SWEETHEART || this == FEUD || this == MENTOR || this == APPRENTICE;
		}
	}

	private Life() {
	}

	/** The resident's compound, or null if they have none (nothing is created). */
	public static @Nullable CompoundTag peek(Resident r) {
		return r.extra.get(KEY) instanceof CompoundTag t ? t : null;
	}

	static CompoundTag of(Resident r) {
		CompoundTag t = peek(r);
		return t != null ? t : r.extra(KEY);
	}

	// ---------------------------------------------------------------- relations

	static long @Nullable [] rel(Resident r) {
		CompoundTag t = peek(r);
		return t != null && t.get("rel") instanceof LongArrayTag a ? a.getAsLongArray() : null;
	}

	static int strength(long meta) {
		return (int) (meta & 0xFF) - 128;
	}

	static Bond bond(long meta) {
		int k = (int) (meta >>> 8 & 0xF);
		return k < Bond.VALUES.length ? Bond.VALUES[k] : Bond.NONE;
	}

	static long last(long meta) {
		return (meta >>> 12) - DAY_OFFSET;
	}

	static long meta(int strength, Bond bond, long lastDay) {
		int s = Math.clamp(strength, -100, 100) + 128;
		return s | (long) bond.ordinal() << 8 | (lastDay + DAY_OFFSET) << 12;
	}

	/** The relation's index in the array (multiple of 3), or -1. */
	static int find(long @Nullable [] a, UUID other) {
		if (a == null) {
			return -1;
		}
		long m = other.getMostSignificantBits();
		long l = other.getLeastSignificantBits();
		for (int i = 0; i + 2 < a.length; i += 3) {
			if (a[i] == m && a[i + 1] == l) {
				return i;
			}
		}
		return -1;
	}

	/** Strength as it stands on {@code today}: friendships fade without contact, quarrels cool with time. */
	static int effective(long meta, long today, LivesConfig.Relations cfg) {
		int s = strength(meta);
		Bond b = bond(meta);
		long idle = today - last(meta) - cfg.graceDays();
		if (idle <= 0 || b == Bond.MENTOR || b == Bond.APPRENTICE) {
			return s;
		}
		int fade = (int) Math.min(200, idle * cfg.decayPerDay() / (b == Bond.SWEETHEART ? 3 : b == Bond.FEUD || b == Bond.RIVAL ? 4 : 1));
		return s > 0 ? Math.max(0, s - fade) : Math.min(0, s + fade);
	}

	/** One relation, read. */
	public record Relation(UUID other, Bond bond, int strength, long lastDay) {
	}

	/** The resident's relations as they stand on {@code today} (empty bonds and faded acquaintances left out). */
	public static java.util.List<Relation> relations(Resident r, long today, LivesConfig.Relations cfg) {
		long[] a = rel(r);
		if (a == null) {
			return java.util.List.of();
		}
		java.util.List<Relation> out = new java.util.ArrayList<>(a.length / 3);
		for (int i = 0; i + 2 < a.length; i += 3) {
			Bond b = bond(a[i + 2]);
			int s = effective(a[i + 2], today, cfg);
			if (b != Bond.NONE && (s != 0 || b.pinned())) {
				out.add(new Relation(new UUID(a[i], a[i + 1]), b, s, last(a[i + 2])));
			}
		}
		return out;
	}

	public static @Nullable Relation relation(Resident r, UUID other, long today, LivesConfig.Relations cfg) {
		long[] a = rel(r);
		int i = find(a, other);
		if (i < 0) {
			return null;
		}
		Bond b = bond(a[i + 2]);
		int s = effective(a[i + 2], today, cfg);
		return b == Bond.NONE || s == 0 && !b.pinned() ? null : new Relation(other, b, s, last(a[i + 2]));
	}

	/**
	 * Adds {@code delta} to the relation with {@code other} (made if absent: pushing out the weakest unpinned one when the
	 * book is full, if the new one would be stronger) and sets its bond if {@code bond} is not null. Returns the new
	 * strength, or {@code Integer.MIN_VALUE} if there was no room.
	 */
	static int touch(Resident r, UUID other, int delta, @Nullable Bond bond, long today, LivesConfig.Relations cfg) {
		CompoundTag t = of(r);
		long[] a = t.get("rel") instanceof LongArrayTag lt ? lt.getAsLongArray() : null;
		int i = find(a, other);
		if (i < 0) {
			int slot = -1;
			if (a == null || a.length < MAX_RELATIONS * 3) {
				a = a == null ? new long[3] : Arrays.copyOf(a, a.length + 3);
				slot = a.length - 3;
				t.put("rel", new LongArrayTag(a));
			} else {
				int weakest = Integer.MAX_VALUE;
				for (int j = 0; j < a.length; j += 3) {
					Bond b = bond(a[j + 2]);
					if (b.pinned()) {
						continue;
					}
					int s = Math.abs(effective(a[j + 2], today, cfg));
					if (s < weakest) {
						weakest = s;
						slot = j;
					}
				}
				if (slot < 0 || weakest >= Math.abs(delta) && bond == null) {
					return Integer.MIN_VALUE;
				}
			}
			a[slot] = other.getMostSignificantBits();
			a[slot + 1] = other.getLeastSignificantBits();
			a[slot + 2] = meta(0, Bond.ACQUAINTANCE, today);
			i = slot;
		}
		long m = a[i + 2];
		int s = Math.clamp(effective(m, today, cfg) + delta, -100, 100);
		Bond b = bond != null ? bond : bond(m);
		if (bond == null && (b == Bond.ACQUAINTANCE || b == Bond.FRIEND)) {
			b = s >= cfg.friendAt() ? Bond.FRIEND : Bond.ACQUAINTANCE;
		}
		a[i + 2] = meta(s, b, today);
		return s;
	}

	/** Forgets the relation with {@code other}. */
	static void forget(Resident r, UUID other) {
		CompoundTag t = peek(r);
		long[] a = t != null && t.get("rel") instanceof LongArrayTag lt ? lt.getAsLongArray() : null;
		int i = find(a, other);
		if (i < 0) {
			return;
		}
		long[] b = new long[a.length - 3];
		System.arraycopy(a, 0, b, 0, i);
		System.arraycopy(a, i + 3, b, i, a.length - i - 3);
		if (b.length == 0) {
			t.remove("rel");
		} else {
			t.put("rel", new LongArrayTag(b));
		}
	}

	/** The resident's sweetheart, if they have one. */
	public static @Nullable UUID sweetheart(Resident r) {
		long[] a = rel(r);
		if (a == null) {
			return null;
		}
		for (int i = 0; i + 2 < a.length; i += 3) {
			if (bond(a[i + 2]) == Bond.SWEETHEART) {
				return new UUID(a[i], a[i + 1]);
			}
		}
		return null;
	}

	// ---------------------------------------------------------------- crafts

	public static @Nullable UUID master(Resident r) {
		CompoundTag t = peek(r);
		if (t == null || !t.contains("am")) {
			return null;
		}
		return new UUID(t.getLongOr("am", 0), t.getLongOr("al", 0));
	}

	public static @Nullable JobType apprenticeTrade(Resident r) {
		CompoundTag t = peek(r);
		return t == null || !t.contains("am") ? null : job(t.getStringOr("at", ""));
	}

	public static long apprenticedDay(Resident r) {
		CompoundTag t = peek(r);
		return t == null ? 0 : t.getLongOr("as", 0);
	}

	static void apprentice(Resident r, UUID master, JobType trade, long day) {
		CompoundTag t = of(r);
		t.putLong("am", master.getMostSignificantBits());
		t.putLong("al", master.getLeastSignificantBits());
		t.putString("at", trade.getSerializedName());
		t.putLong("as", day);
	}

	static void endApprenticeship(Resident r) {
		CompoundTag t = peek(r);
		if (t != null) {
			t.remove("am");
			t.remove("al");
			t.remove("at");
			t.remove("as");
		}
	}

	public static @Nullable JobType journeyman(Resident r) {
		CompoundTag t = peek(r);
		return t == null || !t.contains("jt") ? null : job(t.getStringOr("jt", ""));
	}

	public static long journeymanSince(Resident r) {
		CompoundTag t = peek(r);
		return t == null ? 0 : t.getLongOr("js", 0);
	}

	static void journeyman(Resident r, @Nullable JobType trade, long day) {
		CompoundTag t = of(r);
		if (trade == null) {
			t.remove("jt");
			t.remove("js");
			t.remove("jh");
			t.remove("jd");
		} else {
			t.putString("jt", trade.getSerializedName());
			t.putLong("js", day);
		}
	}

	/** The trade the resident is master of, or null. Read by the planner's needs too (a documented field). */
	public static @Nullable JobType masterOf(Resident r) {
		CompoundTag t = peek(r);
		return t == null || !t.contains("mt") ? null : job(t.getStringOr("mt", ""));
	}

	public static long masterDay(Resident r) {
		CompoundTag t = peek(r);
		return t == null ? 0 : t.getLongOr("md", 0);
	}

	static void master(Resident r, @Nullable JobType trade, long day) {
		CompoundTag t = of(r);
		if (trade == null) {
			t.remove("mt");
			t.remove("md");
		} else {
			t.putString("mt", trade.getSerializedName());
			t.putLong("md", day);
		}
	}

	// ---------------------------------------------------------------- grief and the rest

	public static boolean mourning(Resident r, long today) {
		CompoundTag t = peek(r);
		return t != null && t.getLongOr("gu", Long.MIN_VALUE) >= today;
	}

	static void mourn(Resident r, long until) {
		of(r).putLong("gu", until);
	}

	public static boolean idle(Resident r, long today) {
		CompoundTag t = peek(r);
		return t != null && t.getLongOr("idle", Long.MIN_VALUE) >= today;
	}

	static void keepIdle(Resident r, long until) {
		of(r).putLong("idle", until);
	}

	/** The day a grown newcomer arrived in town (settlers, an exile home), or {@code Long.MIN_VALUE}. */
	public static long arrived(Resident r) {
		CompoundTag t = peek(r);
		return t == null ? Long.MIN_VALUE : t.getLongOr("arr", Long.MIN_VALUE);
	}

	static void arrived(Resident r, long day) {
		of(r).putLong("arr", day);
	}

	static boolean flag(Resident r, String key) {
		CompoundTag t = peek(r);
		return t != null && t.getBooleanOr(key, false);
	}

	static void flag(Resident r, String key, boolean on) {
		if (on) {
			of(r).putBoolean(key, true);
		} else {
			CompoundTag t = peek(r);
			if (t != null) {
				t.remove(key);
			}
		}
	}

	/** Bytes the resident's lives take when saved (the budget is ~200). */
	public static int bytes(Resident r) {
		Tag t = peek(r);
		return t == null ? 0 : t.sizeInBytes();
	}

	private static final java.util.Map<String, JobType> JOBS = new java.util.HashMap<>();

	static {
		for (JobType j : JobType.values()) {
			JOBS.put(j.getSerializedName(), j);
		}
	}

	static @Nullable JobType job(String id) {
		return JOBS.get(id);
	}
}
