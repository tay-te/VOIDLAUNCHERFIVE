package dev.voidmc.fray.api;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

/**
 * Which Art a player's sneak + C performs (Mettle's Mastery: Whirlwind, Earthshaker, ...). Fray runs the Art; Mettle
 * decides who has which.
 *
 * @since 0.1.0 (2026-10-21)
 */
@FunctionalInterface
public interface ArtProvider {
	@Nullable Identifier art(ServerPlayer player);

	ArtProvider NONE = p -> null;
}
