package dev.voidmc.bestiary.api;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * Adds to a place's danger (BESTIARY.md §6.1): Hearth's settlement territory (-1.0) and roads (-0.5), Expanse's ruins
 * (+0.5) and lairs (+1.0). Return the amount to add (negative lowers it); the sum is clamped to 1-5.
 */
@FunctionalInterface
public interface DangerModifier {
	float modify(ServerLevel level, BlockPos pos, float danger);
}
