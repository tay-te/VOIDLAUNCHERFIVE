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

	/** The id of the behaviour the folk is running, or "routine". */
	public static String running(Folk f) {
		NpcService.FolkBehaviour b = f.brain() == null ? null : f.brain().behaviour();
		return b == null ? "routine" : b.id().toString();
	}
}
