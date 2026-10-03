package dev.voidmc.hearth.quest;

import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.quest.model.QuestInstance;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

/**
 * What the quest module announces, for modules that make quests change the world. Until the api has a hook for it,
 * this is how the sim learns that a scouted hamlet site should be founded (see docs/requests/quest.md). Fired on the
 * server thread.
 */
public final class QuestEvents {
	/** A scouting quest was handed in: {@code site} is where the player stood, chosen for a new hamlet of {@code origin}. */
	public static final Event<SiteScouted> SITE_SCOUTED = EventFactory.createArrayBacked(SiteScouted.class, ls -> (level, origin, site, player) -> {
		for (SiteScouted l : ls) {
			l.onSiteScouted(level, origin, site, player);
		}
	});

	/** A quest was completed (after rewards were paid). */
	public static final Event<Completed> COMPLETED = EventFactory.createArrayBacked(Completed.class, ls -> (player, settlement, quest) -> {
		for (Completed l : ls) {
			l.onCompleted(player, settlement, quest);
		}
	});

	private QuestEvents() {
	}

	@FunctionalInterface
	public interface SiteScouted {
		void onSiteScouted(ServerLevel level, Settlement origin, BlockPos site, ServerPlayer player);
	}

	@FunctionalInterface
	public interface Completed {
		void onCompleted(ServerPlayer player, @Nullable Settlement settlement, QuestInstance quest);
	}
}
