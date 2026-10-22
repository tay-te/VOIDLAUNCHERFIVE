package dev.voidmc.fray.motion;

import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.jspecify.annotations.Nullable;

/**
 * Defender-favoured timing (the user's decision, 2026-10-21; ANIM_EVENTS.md §4): a dodge or parry that was in time on
 * the defending player's screen counts, within a bounded window.
 *
 * <p>Why the stamp is the right clock: a client's game time follows the server's game-time packets and runs behind the
 * server by the downlink latency, and a creature's clip on that client is drawn at {@code clientTime - start}. So the
 * client's game time when the player pressed is exactly "the creature clip tick the player was looking at". A creature
 * hit that the server decides at tick {@code H} was on the defender's screen at their client time {@code H}; the
 * defender's press at client time {@code S} reaches the server around {@code S + RTT}.
 *
 * <p>So hits on players are held for up to {@link #graceTicks} (RTT + 1 tick, at most {@link #capTicks}); a request
 * whose (clamped) stamp puts a defensive window over {@code H} cancels or parries the hit. Anti-abuse: the stamp can't
 * claim more lag than the measured RTT + 1 tick, can't exceed the cap, can't be in the future and can't go back past the
 * player's previous stamp; the RTT is Fray's own probe, a median of the last five round trips.
 *
 * <p>The pure functions ({@link #clamp}, {@link #grace}) carry the rules and are unit-tested; the per-player state is
 * keyed by UUID (common code, dedicated-server safe).
 */
public final class DefenderTiming {
	/** The rewind cap in ticks (250 ms). Server config {@code fray.defender_grace_ms} (0 turns it off). */
	public static volatile int capTicks = Math.max(0, Math.min(10, Integer.getInteger("fray.defender_grace_ms", 250) / 50));
	/** PvP cap (an attacker that is a player): never more than 100 ms. */
	public static final int PVP_CAP_TICKS = 2;

	private static final Map<UUID, PlayerClock> CLOCKS = new ConcurrentHashMap<>();

	/** What a defender's own action covered at a moment of its timeline. */
	public enum Window {
		NONE, IFRAMES, PERFECT, PARRY, BLOCK
	}

	/** One player's measured round trips and stamp history. */
	public static final class PlayerClock {
		final int[] rtt = new int[5];
		int samples;
		long lastStamp = Long.MIN_VALUE;
		public long rewound;
		public long clampedOld;
		public long clampedFuture;
		public long saves;
		public long maxRewindTicks;

		synchronized void addRtt(int ms) {
			this.rtt[this.samples % this.rtt.length] = Math.max(0, ms);
			this.samples++;
		}

		/** The median of the last five round trips in ms (0 before any). */
		public synchronized int rttMs() {
			int n = Math.min(this.samples, this.rtt.length);
			if (n == 0) {
				return 0;
			}
			int[] c = Arrays.copyOf(this.rtt, n);
			Arrays.sort(c);
			return c[n / 2];
		}
	}

	private DefenderTiming() {
	}

	public static PlayerClock clock(UUID player) {
		return CLOCKS.computeIfAbsent(player, k -> new PlayerClock());
	}

	public static void forget(UUID player) {
		CLOCKS.remove(player);
	}

	public static void recordRtt(UUID player, int ms) {
		clock(player).addRtt(ms);
	}

	/** RTT in whole ticks, rounded up. */
	public static int rttTicks(int rttMs) {
		return (rttMs + 49) / 50;
	}

	/** How long a hit on this player may be held, in ticks: RTT + 1 tick, at most {@code cap}. */
	public static int grace(int rttMs, int cap) {
		if (cap <= 0) {
			return 0;
		}
		return Math.min(cap, rttTicks(rttMs) + 1);
	}

	/**
	 * Below this measured round trip the player is local (the integrated server's owner, a LAN): nothing is held and no
	 * stamp is rewound. The probe reads up to a tick above the network (its echo waits for the client tick), so 60 ms
	 * is a network round trip of about 10 ms.
	 */
	public static final int LOCAL_RTT_MS = 60;

	/** The cap for a player with this measured round trip: 0 when local. */
	static int cap(int rttMs, int cap) {
		return rttMs < LOCAL_RTT_MS ? 0 : cap;
	}

	public static int graceTicks(UUID player, boolean pvp) {
		int rtt = clock(player).rttMs();
		return grace(rtt, cap(rtt, pvp ? Math.min(capTicks, PVP_CAP_TICKS) : capTicks));
	}

	/**
	 * The stamp the server accepts for an action received at {@code receipt}, claimed at {@code stamp} by a client whose
	 * RTT is {@code rttMs}: no earlier than {@code receipt - grace}, no later than {@code receipt}, never before
	 * {@code lastStamp}.
	 */
	public static long clamp(long stamp, long receipt, int rttMs, int cap, long lastStamp) {
		long earliest = receipt - grace(rttMs, cap);
		long s = Math.max(earliest, Math.min(stamp, receipt));
		if (lastStamp != Long.MIN_VALUE) {
			s = Math.max(s, Math.min(lastStamp, receipt));
		}
		return s;
	}

	/** Clamps and records a player's action stamp. */
	public static long accept(UUID player, long stamp, long receipt) {
		PlayerClock c = clock(player);
		int rtt = c.rttMs();
		long s = clamp(stamp, receipt, rtt, cap(rtt, capTicks), c.lastStamp);
		if (stamp < s) {
			c.clampedOld++;
		}
		if (stamp > receipt) {
			c.clampedFuture++;
		}
		if (s < receipt) {
			c.rewound++;
			c.maxRewindTicks = Math.max(c.maxRewindTicks, receipt - s);
		}
		c.lastStamp = s;
		return s;
	}

	/** Which defensive window of {@code t}, started at {@code start} (defender's clock), covers tick {@code at}. */
	public static Window windowAt(Timeline t, long start, long at) {
		return windowAt(t, start, at, null);
	}

	/**
	 * The same, with per-action overrides of the timeline's windows ({@code name -> [from, to]} inclusive; a {@code to}
	 * below {@code from} closes the window): the combat side widens i-frames by Mettle's bonus or sets the parry window
	 * Mettle and spamming decide. This is the one judge of dodge and parry timing for every kind of hit.
	 */
	public static Window windowAt(Timeline t, long start, long at, @Nullable Map<String, int[]> overrides) {
		long k = at - start;
		if (k < 0) {
			return Window.NONE;
		}
		int i = (int) Math.min(k, Integer.MAX_VALUE);
		if (in(t, overrides, "perfect", i)) {
			return Window.PERFECT;
		}
		if (in(t, overrides, "iframes", i)) {
			return Window.IFRAMES;
		}
		if (in(t, overrides, "parry", i)) {
			return Window.PARRY;
		}
		if (in(t, overrides, "block", i)) {
			return Window.BLOCK;
		}
		return Window.NONE;
	}

	private static boolean in(Timeline t, @Nullable Map<String, int[]> overrides, String name, int k) {
		int[] w = overrides == null ? null : overrides.get(name);
		if (w == null) {
			return k < t.ticks && t.inWindow(name, k);
		}
		return k >= w[0] && k <= w[1];
	}

	public static String stats() {
		StringBuilder s = new StringBuilder();
		for (Map.Entry<UUID, PlayerClock> e : CLOCKS.entrySet()) {
			PlayerClock c = e.getValue();
			s.append(String.format(Locale.ROOT, "%s: rtt %d ms, rewound %d (max %d ticks), clamped old %d future %d, saves by late requests %d; ",
				e.getKey().toString().substring(0, 8), c.rttMs(), c.rewound, c.maxRewindTicks, c.clampedOld, c.clampedFuture, c.saves));
		}
		return s.toString();
	}
}
