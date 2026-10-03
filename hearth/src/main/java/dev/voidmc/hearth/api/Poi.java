package dev.voidmc.hearth.api;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.jspecify.annotations.Nullable;

/**
 * A point of interest in a blueprint, in world coordinates.
 *
 * @param pos    the block a folk stands in (or lies in, for a bed's head; sits in, for a seat) to use it
 * @param facing which way they face while using it (toward the workstation, the counter, the view)
 * @param job    for {@link PoiType#JOB_SITE}, whose job site it is; otherwise null
 */
public record Poi(PoiType type, BlockPos pos, Direction facing, @Nullable JobType job) {
}
