package dev.voidmc.hearth.npc.crowd;

import dev.voidmc.hearth.sim.LivingWorldStats;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.LongAdder;

/** Counts of what the crowds did, for {@code /hearth crowds stats}, the tour log and the dev harness. */
public final class CrowdStats {
	/** The director's passes (stimuli, selection, spectacles, brawls). */
	public static final LivingWorldStats.Timer DIRECTOR = LivingWorldStats.timer("crowds");
	/** Shutter toggles (ticks that changed or checked some). */
	public static final LivingWorldStats.Timer SHUTTERS = LivingWorldStats.timer("shutters");
	/** The shutters' look at the towns every second: which are embodied, plots to scan, the hour. */
	public static final LivingWorldStats.Timer SHUTTER_LOOKS = LivingWorldStats.timer("shutters_look");

	public static final LongAdder BARKS = new LongAdder();
	public static final LongAdder DOORS_SHUT = new LongAdder();
	/** Folk who got indoors away from an outlaw. */
	public static final LongAdder INDOORS = new LongAdder();
	public static final LongAdder SHUTTERS_TOGGLED = new LongAdder();
	public static final LongAdder PLAYER_PASSES = new LongAdder();
	/** Player passes that waited: a quiet player among folk who have all reacted (CrowdDirector.QUIET_TICKS). */
	public static final LongAdder QUIET_SKIPS = new LongAdder();
	public static final LongAdder BRAWLS = new LongAdder();
	private static final Map<Reaction, LongAdder> REACTIONS = new EnumMap<>(Reaction.class);

	static {
		for (Reaction r : Reaction.VALUES) {
			REACTIONS.put(r, new LongAdder());
		}
	}

	private CrowdStats() {
	}

	private static final java.util.ArrayDeque<String> RECENT = new java.util.ArrayDeque<>();

	/** Notes a bark said (who, in what role, the line), the last 32 kept for the tour log and the dev harness. */
	public static synchronized void barked(String who, Role role, String category, String line) {
		BARKS.increment();
		RECENT.addLast(who + " (" + role.id() + ", " + category + "): " + line);
		while (RECENT.size() > 32) {
			RECENT.removeFirst();
		}
	}

	/** The barks said lately, oldest first. */
	public static synchronized java.util.List<String> recentBarks() {
		return java.util.List.copyOf(RECENT);
	}

	public static void reacted(Reaction r) {
		REACTIONS.get(r).increment();
	}

	public static long reactions(Reaction r) {
		return REACTIONS.get(r).sum();
	}

	/** One line: reactions by kind, barks, doors, shutters, and the director's time per player pass. */
	public static String line() {
		StringBuilder sb = new StringBuilder("crowds:");
		for (Reaction r : Reaction.VALUES) {
			sb.append(' ').append(r.name().toLowerCase(Locale.ROOT)).append(' ').append(reactions(r));
		}
		long passes = PLAYER_PASSES.sum();
		double perPlayerUs = passes == 0 ? 0 : DIRECTOR_NANOS.sum() / 1000.0 / passes;
		sb.append(String.format(Locale.ROOT, ", barks %d, brawls %d, indoors %d, doors shut %d, shutters toggled %d, %.1f us a player a pass", BARKS.sum(), BRAWLS.sum(),
			INDOORS.sum(), DOORS_SHUT.sum(), SHUTTERS_TOGGLED.sum(), perPlayerUs));
		if (passes > 0) {
			sb.append(String.format(Locale.ROOT, " (reading the player %.1f us, the folk near %.1f us, choosing %.1f us; spectacles %.0f us in all; %d of %d quiet; dearest pass %.0f us)",
				FACTS_NANOS.sum() / 1000.0 / passes, NEAR_NANOS.sum() / 1000.0 / passes, CHOOSE_NANOS.sum() / 1000.0 / passes, SPECTACLE_NANOS.sum() / 1000.0, QUIET_SKIPS.sum(), passes,
				DIRECTOR_MAX.get() / 1000.0));
		}
		return sb.toString();
	}

	/** Parts of the director's time: reading the players, finding the folk near them, the spectacles' onlookers. */
	public static final LongAdder FACTS_NANOS = new LongAdder();
	public static final LongAdder NEAR_NANOS = new LongAdder();
	public static final LongAdder SPECTACLE_NANOS = new LongAdder();
	/** The choosing: who is in the middle of what, the selector, the orders issued (passes with anyone to choose among). */
	public static final LongAdder CHOOSE_NANOS = new LongAdder();

	/** Starts the per-player figures afresh (the tours' bench measures a warm stretch on its own). */
	public static void resetTimes() {
		PLAYER_PASSES.reset();
		QUIET_SKIPS.reset();
		FACTS_NANOS.reset();
		NEAR_NANOS.reset();
		SPECTACLE_NANOS.reset();
		CHOOSE_NANOS.reset();
		DIRECTOR_NANOS.reset();
		DIRECTOR_MAX.set(0);
	}

	/** The dearest director pass since the last reset (ns). */
	public static final java.util.concurrent.atomic.AtomicLong DIRECTOR_MAX = new java.util.concurrent.atomic.AtomicLong();

	/** Server time the director's passes took, in all. */
	public static final LongAdder DIRECTOR_NANOS = new LongAdder();
}
