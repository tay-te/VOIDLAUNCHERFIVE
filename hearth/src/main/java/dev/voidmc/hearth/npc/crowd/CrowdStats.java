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
	/** Shutter scans and toggles. */
	public static final LivingWorldStats.Timer SHUTTERS = LivingWorldStats.timer("shutters");

	public static final LongAdder BARKS = new LongAdder();
	public static final LongAdder DOORS_SHUT = new LongAdder();
	/** Folk who got indoors away from an outlaw. */
	public static final LongAdder INDOORS = new LongAdder();
	public static final LongAdder SHUTTERS_TOGGLED = new LongAdder();
	public static final LongAdder PLAYER_PASSES = new LongAdder();
	public static final LongAdder BRAWLS = new LongAdder();
	private static final Map<Reaction, LongAdder> REACTIONS = new EnumMap<>(Reaction.class);

	static {
		for (Reaction r : Reaction.VALUES) {
			REACTIONS.put(r, new LongAdder());
		}
	}

	private CrowdStats() {
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
		return sb.toString();
	}

	/** Server time the director's passes took, in all. */
	public static final LongAdder DIRECTOR_NANOS = new LongAdder();
}
