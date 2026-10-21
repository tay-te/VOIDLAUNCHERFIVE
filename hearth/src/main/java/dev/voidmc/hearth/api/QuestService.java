package dev.voidmc.hearth.api;

import net.minecraft.server.level.ServerPlayer;

/** The quest module, as the NPC module uses it to mark folk who have work for the player. May be absent. */
public interface QuestService {
	Marker marker(ServerPlayer player, Settlement settlement, Resident resident);

	/**
	 * Offers the player a personal quest from {@code giver}, bound from a template with {@code vars} (a named character's
	 * thread, docs/LIVING_WORLD_2.md §4). Returns whether it was offered.
	 */
	default boolean offerPersonal(ServerPlayer player, Settlement settlement, Resident giver, net.minecraft.resources.Identifier template,
		net.minecraft.nbt.CompoundTag vars) {
		return false;
	}

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
