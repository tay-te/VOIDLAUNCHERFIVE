package dev.voidmc.hearth.repair.folk;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Where a folk can stand and what it can reach from there, the way builders work (arm's length, never inside the block). */
final class Reach {
	/** Eye-to-block-centre distance a folk can place at (as the NPC module's builders). */
	static final double REACH = 4.6;

	private Reach() {
	}

	static boolean canReach(Entity e, BlockPos pos) {
		return e.getEyePosition().distanceToSqr(Vec3.atCenterOf(pos)) <= REACH * REACH;
	}

	/** Placing a solid block where the folk stands would bury it: step aside first. */
	static boolean inside(Entity e, BlockPos pos, BlockState state) {
		return !state.isAir() && e.getBoundingBox().intersects(new AABB(pos)) && !state.getCollisionShape(e.level(), pos).isEmpty();
	}

	/** A cell a person can stand in: room for feet and head, something solid underfoot. */
	static boolean standable(ServerLevel level, BlockPos p) {
		if (!level.isLoaded(p)) {
			return false;
		}
		BlockPos below = p.below();
		return level.getBlockState(p).getCollisionShape(level, p).isEmpty() && level.getBlockState(p.above()).getCollisionShape(level, p.above()).isEmpty()
			&& level.getBlockState(below).isFaceSturdy(level, below, Direction.UP);
	}

	/** Somewhere to stand within reach of a block, not in its way, nearest the folk. */
	static @Nullable BlockPos standFor(ServerLevel level, BlockPos target, Entity e) {
		BlockPos best = null;
		double bestD = Double.MAX_VALUE;
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		for (int dy = -3; dy <= 1; dy++) {
			for (int dx = -3; dx <= 3; dx++) {
				for (int dz = -3; dz <= 3; dz++) {
					if (dx == 0 && dz == 0 && dy >= -1) {
						continue;
					}
					p.set(target.getX() + dx, target.getY() + dy, target.getZ() + dz);
					if (!standable(level, p)) {
						continue;
					}
					Vec3 eye = new Vec3(p.getX() + 0.5, p.getY() + e.getEyeHeight(), p.getZ() + 0.5);
					if (eye.distanceToSqr(Vec3.atCenterOf(target)) > REACH * REACH) {
						continue;
					}
					double d = p.distSqr(e.blockPosition());
					if (d < bestD) {
						bestD = d;
						best = p.immutable();
					}
				}
			}
		}
		return best;
	}

	/** The time of day, 0–24000. */
	static int timeOfDay(ServerLevel level) {
		return (int) Math.floorMod(level.getOverworldClockTime(), 24000L);
	}

	static boolean between(ServerLevel level, int from, int to) {
		int t = timeOfDay(level);
		return from <= to ? t >= from && t <= to : t >= from || t <= to;
	}
}
