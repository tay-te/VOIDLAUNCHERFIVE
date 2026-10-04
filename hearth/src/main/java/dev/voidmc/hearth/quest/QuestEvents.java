package dev.voidmc.hearth.quest;

import dev.voidmc.hearth.api.ResourceSite;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.quest.model.QuestInstance;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

/**
 * What the quest module announces, for modules that make quests change the world. Until the api has hooks for them
 * (docs/requests/p3-quest.md), this is how the sim learns that a prospected site should be surveyed and worked, and how
 * the law learns that a player cleared their name. Fired on the server thread.
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

	/**
	 * A prospecting quest was handed in: the player found {@code kind} (a rock face, an ore vein, clay) at {@code pos} for
	 * {@code settlement}, whose site of that kind was spent. The sim should add the site to the settlement's territory and
	 * let the planner open a quarry, mine or pit there (docs/LIVING_WORLD.md §7.2).
	 */
	public static final Event<SiteProspected> SITE_PROSPECTED = EventFactory.createArrayBacked(SiteProspected.class,
		ls -> (level, settlement, kind, pos, player) -> {
			for (SiteProspected l : ls) {
				l.onSiteProspected(level, settlement, kind, pos, player);
			}
		});

	/**
	 * A wanted player finished a quest that clears their name in {@code settlement} (work for the guard captain in place of
	 * the fine). The law should lift the wanted status there.
	 */
	public static final Event<NameCleared> NAME_CLEARED = EventFactory.createArrayBacked(NameCleared.class, ls -> (player, settlement, quest) -> {
		for (NameCleared l : ls) {
			l.onNameCleared(player, settlement, quest);
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

	@FunctionalInterface
	public interface SiteProspected {
		void onSiteProspected(ServerLevel level, Settlement settlement, ResourceSite.Kind kind, BlockPos pos, ServerPlayer player);
	}

	@FunctionalInterface
	public interface NameCleared {
		void onNameCleared(ServerPlayer player, Settlement settlement, QuestInstance quest);
	}
}
