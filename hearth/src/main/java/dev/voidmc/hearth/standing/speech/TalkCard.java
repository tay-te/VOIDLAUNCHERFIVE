package dev.voidmc.hearth.standing.speech;

import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.api.StandingService.Title;
import dev.voidmc.hearth.standing.StandingEngine;
import dev.voidmc.hearth.standing.StandingModule;
import dev.voidmc.hearth.standing.logic.Titles;
import dev.voidmc.hearth.standing.model.Memories;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

/**
 * The standing module's facts for a conversation's resident card (docs/OVERLAY.md, {@code talk.resident} and
 * {@code talk.standing}): what this resident thinks of the player, what they remember most, and where the player stands
 * in the town and what the next title takes. Server thread.
 *
 * @param standing     the player's standing in the town, −100…100
 * @param title        their title there ({@link Title#getSerializedName})
 * @param nextTitle    the next title up, or empty at the top
 * @param nextAt       the standing it is earned at (0 when there is none)
 * @param opinion      the resident's opinion of the player, −100…100
 * @param memoryKind   the kind of the player's strongest memory with them ({@code standing:amends}, a deed kind), or empty
 * @param memoryWeight that memory's weight (negative: a bad one)
 */
public record TalkCard(int standing, String title, String nextTitle, int nextAt, int opinion, String memoryKind, int memoryWeight) {
	/** Without the standing module: a stranger with no opinion and nothing remembered. */
	public static final TalkCard NONE = new TalkCard(0, Title.STRANGER.getSerializedName(), Title.KNOWN.getSerializedName(), 10, 0, "", 0);

	public static TalkCard of(ServerPlayer player, Settlement settlement, Resident resident) {
		StandingEngine engine = StandingModule.engine();
		if (engine == null) {
			return NONE;
		}
		UUID id = player.getUUID();
		int standing = engine.standing(settlement, id);
		Title title = engine.title(settlement, id);
		Titles.Next next = Titles.next(standing, engine.config().titles());
		Memories.@Nullable Memory memory = Memories.of(resident).strongest(id, engine.host().day(), engine.config().opinion());
		return new TalkCard(standing, title.getSerializedName(), next == null ? "" : next.title().getSerializedName(), next == null ? 0 : next.at(),
			engine.opinion(settlement, resident, id), memory == null ? "" : memory.kind(), memory == null ? 0 : memory.weight());
	}
}
