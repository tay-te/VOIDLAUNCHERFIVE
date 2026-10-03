package dev.voidmc.hearth.api;

import net.minecraft.server.level.ServerPlayer;

/** The quest module, as the NPC module uses it to mark folk who have work for the player. May be absent. */
public interface QuestService {
	Marker marker(ServerPlayer player, Settlement settlement, Resident resident);

	enum Marker {
		NONE,
		/** Has a quest to offer this player. */
		OFFER,
		/** Waiting on this player to hand in a quest that is ready. */
		TURN_IN,
		/** The player has a quest from them in progress. */
		ACTIVE
	}
}
