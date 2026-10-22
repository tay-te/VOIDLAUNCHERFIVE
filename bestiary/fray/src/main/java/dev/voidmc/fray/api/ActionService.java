package dev.voidmc.fray.api;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.Nullable;

/**
 * The player's combat actions on the server (COMBAT.md §3-§11): light, heavy and charged attacks, dodge, parry and block,
 * lock-on, the specials and the Arts. Creature actions run through Fray Motion's timelines.
 *
 * @since 0.1.0 (2026-10-21)
 */
public interface ActionService {
	/** The running action's id, or null. */
	@Nullable Identifier current(LivingEntity entity);

	/** Interrupts the running action (no refund). */
	boolean interrupt(LivingEntity entity, InterruptReason reason);

	/** The locked target of a player, validated by the server. */
	@Nullable LivingEntity lockTarget(ServerPlayer player);

	/** Who has which Art (Mettle replaces {@link ArtProvider#NONE}). */
	void setArtProvider(ArtProvider provider);

	/** Overrides the provider for one player (commands, tests); null clears the override. */
	void setArt(ServerPlayer player, @Nullable Identifier art);

	/** Ticks left on the player's Art cooldown. */
	int artCooldown(ServerPlayer player);

	/** True within a dodge's i-frames (the server's view, lag allowance included). */
	boolean invulnerable(LivingEntity entity);
}
