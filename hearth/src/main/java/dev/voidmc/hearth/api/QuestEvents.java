package dev.voidmc.hearth.api;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

/**
 * How a player's quests end, for other mods (a skills mod granting XP by quest type, stats, advancements). Fired by the
 * quest module on the server thread, after rewards are paid (completed) or the escrow refunded (failed, abandoned).
 * The quest module's own events for the sim and the law stay in {@code quest.QuestEvents}.
 */
public final class QuestEvents {
	public static final Event<Ended> COMPLETED = EventFactory.createArrayBacked(Ended.class, ls -> (p, s, t, id, tier) -> {
		for (Ended l : ls) {
			l.onEnded(p, s, t, id, tier);
		}
	});

	/** Failed: a deadline passed, an escorted folk was lost, the quest item gone. */
	public static final Event<Ended> FAILED = EventFactory.createArrayBacked(Ended.class, ls -> (p, s, t, id, tier) -> {
		for (Ended l : ls) {
			l.onEnded(p, s, t, id, tier);
		}
	});

	/** The player gave it up. */
	public static final Event<Ended> ABANDONED = EventFactory.createArrayBacked(Ended.class, ls -> (p, s, t, id, tier) -> {
		for (Ended l : ls) {
			l.onEnded(p, s, t, id, tier);
		}
	});

	private QuestEvents() {
	}

	/**
	 * @param settlement the settlement that gave the quest, or null if it is gone (or not loaded)
	 * @param template   the quest's template id ({@code hearth:bounty/wolves}...)
	 * @param tier       the giving settlement's tier now ({@link SettlementTier#CAMP} when the settlement is gone)
	 */
	@FunctionalInterface
	public interface Ended {
		void onEnded(ServerPlayer player, @Nullable Settlement settlement, QuestType type, Identifier template, SettlementTier tier);
	}
}
