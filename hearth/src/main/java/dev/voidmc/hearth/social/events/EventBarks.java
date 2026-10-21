package dev.voidmc.hearth.social.events;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.AgeStage;
import dev.voidmc.hearth.api.HearthServices;
import dev.voidmc.hearth.api.NpcService;
import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.api.StandingService;
import dev.voidmc.hearth.npc.crowd.Barks;
import dev.voidmc.hearth.npc.crowd.Role;
import dev.voidmc.hearth.social.FolkFacts;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

/**
 * The town events' barks (docs/LIVING_WORLD_2.md §0.2, §3.7): lines from {@code hearth_dialogue/barks/events.json} said
 * through the shared bark engine of the crowds (lane A3: 60 s a folk, 4 s a player a town, 10 min a line), so a wedding,
 * a trial and the street's own remarks never pile up on a player.
 */
final class EventBarks {
	private EventBarks() {
	}

	/** Has a folk say a line of the category to the nearest player within 16, if the cooldowns allow; whether it did. */
	static boolean say(NpcService.FolkHandle folk, String category) {
		if (category.isEmpty() || !(folk.entity().level() instanceof ServerLevel level)) {
			return false;
		}
		try {
			ServerPlayer near = (ServerPlayer) level.getNearestPlayer(folk.entity(), 16);
			if (near == null) {
				return false;
			}
			Resident r = folk.resident();
			boolean mother = r.female && r.age == AgeStage.ADULT && !FolkFacts.children(r).isEmpty();
			StandingService standing = HearthServices.standing();
			StandingService.Title title = standing == null ? null : standing.title(folk.settlement().id(), near.getUUID());
			return Barks.say(folk, category, Role.of(r, mother), near, title);
		} catch (RuntimeException | LinkageError e) {
			Hearth.LOG.debug("events: bark {} failed", category, e);
			return false;
		}
	}

	/** Whether a category has any lines (the timeline's validation in tests reads the data directly). */
	static boolean known(String category) {
		return !Barks.lines(category).isEmpty();
	}

	static @Nullable String categoryOf(EventRule rule, String arg) {
		return arg.isEmpty() ? rule.barks() : arg;
	}
}
