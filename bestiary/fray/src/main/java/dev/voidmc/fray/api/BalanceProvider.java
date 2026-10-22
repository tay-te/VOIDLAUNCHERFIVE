package dev.voidmc.fray.api;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

/**
 * Mettle's hook into every number that grows with the player (CONTRACTS.md §13.4). Fray asks for a key's value given
 * the neutral base; {@link #NEUTRAL} answers the base. Mettle replaces it at init with {@link FrayApi#setBalance}.
 * Fray also reads Mettle's attributes ({@code mettle:stamina_max}, ...) directly where Mettle registers them
 * (MYTHIC_POWER.md §8); a provider sees the value after those.
 *
 * @since 0.1.0 (2026-10-21)
 */
@FunctionalInterface
public interface BalanceProvider {
	/** The player's value for a key, given the base. NEUTRAL returns base. */
	double value(ServerPlayer player, BalanceKey key, double base, @Nullable Identifier context);

	BalanceProvider NEUTRAL = (p, k, b, c) -> b;
}
