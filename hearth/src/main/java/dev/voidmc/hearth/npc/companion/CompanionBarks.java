package dev.voidmc.hearth.npc.companion;

import dev.voidmc.hearth.api.HearthServices;
import dev.voidmc.hearth.api.NpcService;
import dev.voidmc.hearth.api.StandingService;
import dev.voidmc.hearth.npc.crowd.Barks;
import dev.voidmc.hearth.npc.crowd.Role;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

/**
 * What companions remark (docs/LIVING_WORLD_2.md §8.7): lines from {@code hearth_dialogue/barks/companion.json} by trait and
 * culture, through the shared bark engine and its cooldowns — on the road, in a town not their own, at home, before a
 * fight, hurt, at night, on the player's deeds and crimes, when hired, told to wait or sent home.
 */
public final class CompanionBarks {
	private CompanionBarks() {
	}

	/** Says a line of the category about the player, if the cooldowns allow it. */
	public static boolean say(NpcService.FolkHandle folk, String category, @Nullable ServerPlayer player) {
		StandingService standing = HearthServices.standing();
		StandingService.Title title = standing == null || player == null ? null : standing.title(folk.settlement().id(), player.getUUID());
		return Barks.say(folk, category, Role.of(folk.resident(), false), player, title);
	}

	/**
	 * A line of the category in the companion's voice regardless of the cooldowns (an answer to an order), or the
	 * fallback translation key when no line fits.
	 */
	public static net.minecraft.network.chat.Component line(NpcService.FolkHandle folk, String category, String fallbackKey) {
		java.util.List<Barks.Line> lines = Barks.lines(category);
		if (!lines.isEmpty()) {
			Barks.Line l = Barks.pick(lines, Role.of(folk.resident(), false), null, folk.settlement().culture(), 720,
				dev.voidmc.hearth.npc.person.NpcData.of(folk.resident()).traits(), x -> true, folk.entity().getRandom());
			if (l != null) {
				return net.minecraft.network.chat.Component.literal(l.text().replace("{given}", folk.resident().givenName)
					.replace("{settlement}", folk.settlement().name()));
			}
		}
		return net.minecraft.network.chat.Component.translatable(fallbackKey);
	}
}
