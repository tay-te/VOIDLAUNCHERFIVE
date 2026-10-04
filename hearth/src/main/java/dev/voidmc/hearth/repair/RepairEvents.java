package dev.voidmc.hearth.repair;

import dev.voidmc.hearth.api.HearthWorld;
import dev.voidmc.hearth.api.Settlement;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.core.BlockPos;

/**
 * The repair module's own events, for modules that want more than {@link dev.voidmc.hearth.api.SettlementEvents#DAMAGED}:
 * a fire breaking out and being put out (quests to help, town news, gossip), and repairs finishing. Fired on the server
 * thread. docs/requests/p3-repair.md proposes moving them into the api's {@code SettlementEvents}.
 */
public final class RepairEvents {
	public static final Event<FireStarted> FIRE_STARTED = EventFactory.createArrayBacked(FireStarted.class, ls -> (w, s, at) -> {
		for (FireStarted l : ls) {
			l.onFireStarted(w, s, at);
		}
	});

	public static final Event<FireOut> FIRE_OUT = EventFactory.createArrayBacked(FireOut.class, ls -> (w, s, burned, helpers) -> {
		for (FireOut l : ls) {
			l.onFireOut(w, s, burned, helpers);
		}
	});

	public static final Event<Repaired> REPAIRED = EventFactory.createArrayBacked(Repaired.class, ls -> (w, s, plot, blocks) -> {
		for (Repaired l : ls) {
			l.onRepaired(w, s, plot, blocks);
		}
	});

	private RepairEvents() {
	}

	/** A fire took hold in a settlement's buildings at {@code at}. */
	@FunctionalInterface
	public interface FireStarted {
		void onFireStarted(HearthWorld world, Settlement settlement, BlockPos at);
	}

	/** The fire is out: {@code burned} blocks of buildings were lost; {@code helpers} are the players who fought it. */
	@FunctionalInterface
	public interface FireOut {
		void onFireOut(HearthWorld world, Settlement settlement, int burned, Set<UUID> helpers);
	}

	/** The last damaged block of a plot was mended ({@code blocks} were put back in this round of work). */
	@FunctionalInterface
	public interface Repaired {
		void onRepaired(HearthWorld world, Settlement settlement, int plotId, int blocks);
	}
}
