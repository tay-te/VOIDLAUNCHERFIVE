package dev.voidmc.fray.api;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/**
 * Stamina and poise (CONTRACTS.md §7). Stamina is spent only by combat actions; reaching 0 makes a player Winded.
 *
 * @since 0.1.0 (2026-10-21)
 */
public interface ResourceService {
	float stamina(ServerPlayer player);

	float maxStamina(ServerPlayer player);

	/** Spends stamina for an action (fires RESOURCE_SPENT); false, spending nothing, when the player has none. */
	boolean spend(ServerPlayer player, float amount, Identifier action);

	void restore(ServerPlayer player, float amount);

	boolean winded(ServerPlayer player);

	/** Poise left before a stagger (creatures) or a flinch (players). */
	float poise(LivingEntity entity);

	float maxPoise(LivingEntity entity);

	/** True while the player is in a fight (hit, hurt, locked on or targeted within the last 5 s). */
	boolean inCombat(ServerPlayer player);
}
