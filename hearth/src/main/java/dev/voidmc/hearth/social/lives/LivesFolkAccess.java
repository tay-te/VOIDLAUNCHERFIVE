package dev.voidmc.hearth.social.lives;

import dev.voidmc.hearth.api.NpcService;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.npc.entity.Folk;
import java.util.UUID;
import net.minecraft.core.BlockPos;

/** Reads of the lives' embodied side for tours and the dev harness. */
public final class LivesFolkAccess {
	private LivesFolkAccess() {
	}

	/** Where this couple walks out to on the rest day. */
	public static BlockPos courtingSpot(Settlement s, UUID a, UUID b) {
		return a.compareTo(b) < 0 ? CourtingBehaviour.spot(s, a, b) : CourtingBehaviour.spot(s, b, a);
	}

	/** Forgets the talk pass's timings (tours, after the town's first moments). */
	public static void resetTimings() {
		LivesTalk.PASSES.reset();
		LivesTalk.NANOS.reset();
		LivesTalk.PLAYERS.reset();
	}

	/** The talk pass's cost since the last reset: a pass, and a player in an embodied town a pass (budget 20 µs). */
	public static String timings() {
		long passes = LivesTalk.PASSES.sum();
		long players = LivesTalk.PLAYERS.sum();
		return String.format(java.util.Locale.ROOT, "lives talk: %d passes, %.1f us a pass, %.1f us a player in a town (%d)", passes,
			passes == 0 ? 0 : LivesTalk.NANOS.sum() / 1e3 / passes, players == 0 ? 0 : LivesTalk.NANOS.sum() / 1e3 / players, players);
	}

	/** The id of the behaviour the folk is running, or "routine". */
	public static String running(Folk f) {
		NpcService.FolkBehaviour b = f.brain() == null ? null : f.brain().behaviour();
		return b == null ? "routine" : b.id().toString();
	}
}
