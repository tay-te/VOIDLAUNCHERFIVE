package dev.voidmc.mettle.api;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Reading a player's cards (docs/CARDS.md §10, §25), for other mods: VOID Hearth's card broker and greetings, VOID
 * Arsenal's forging, a map or a scoreboard. Read-only and server side; every answer is a snapshot. What happens to
 * cards is told by the card events on {@link MettleEvents} ({@code CARD_DRAWN} to {@code SET_TIER_CHANGED}).
 *
 * <p>Get it with {@link MettleServices#cards()}. Without Mettle's card module a neutral stand-in answers (no cards, no
 * sets, cooldowns unscaled).
 */
public interface CardService {
	/** Every card the player owns, wherever it is (binder, a deck, a trade), by id. */
	List<CardView> binder(ServerPlayer player);

	/** The cards slotted in one skill's deck, by slot (a Mythic slotted there included). */
	List<CardView> deck(ServerPlayer player, ResourceKey<Skill> skill);

	/** The offers dealt in a skill's hand, waiting to be kept (empty when no draft waits). */
	List<CardView> hand(ServerPlayer player, ResourceKey<Skill> skill);

	/** Any card in the world's ledger by id, whoever holds it (a released card too, as {@link CardView.Place#RELEASED}). */
	Optional<CardView> card(MinecraftServer server, long id);

	/** The active sets, at most three: each keyword and the tier it reached (2, 4 or 6). */
	Map<Identifier, Integer> activeSets(ServerPlayer player);

	/** Slotted, non-dormant cards counting towards a keyword's set (counted whether the set is active or not). */
	int setCount(ServerPlayer player, Identifier keyword);

	/** Whether a keyword's set is active at {@code tier} or higher. */
	default boolean setActive(ServerPlayer player, Identifier keyword, int tier) {
		return activeSets(player).getOrDefault(keyword, 0) >= tier;
	}

	/** A trait's effective total from the player's deck (boosted by attributes and capped), in its unit; 0 if none. */
	double trait(ServerPlayer player, Identifier trait);

	/** What perk cooldowns are multiplied by (the Echo set: 0.9 at 2, 0.8 at 4; otherwise 1). */
	float cooldownScale(ServerPlayer player);

	/** The Mythics the player holds ({@code mettle:twin_edge}), slotted or not. */
	List<Identifier> mythics(ServerPlayer player);

	/** The Mythics the player holds that are slotted and working. */
	List<Identifier> activeMythics(ServerPlayer player);
}
