package dev.voidmc.hearth.npc.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.jspecify.annotations.Nullable;

/** Where a person can stand. Reads blocks only in loaded chunks (callers check), never loads any. */
public final class FolkSpots {
	private FolkSpots() {
	}

	/** Solid footing below, two blocks of room, no lava or deep water. */
	public static boolean standable(BlockGetter level, BlockPos pos) {
		BlockPos below = pos.below();
		BlockState floor = level.getBlockState(below);
		if (floor.getCollisionShape(level, below).isEmpty() && !floor.getFluidState().isSource()) {
			return false;
		}
		return passable(level, pos) && passable(level, pos.above());
	}

	public static boolean passable(BlockGetter level, BlockPos pos) {
		BlockState s = level.getBlockState(pos);
		FluidState f = s.getFluidState();
		if (!f.isEmpty() && !f.isSource()) {
			return s.getCollisionShape(level, pos).isEmpty();
		}
		return f.isEmpty() && s.getCollisionShape(level, pos).isEmpty();
	}

	/** The nearest standable spot within {@code radius} (a small cube search, nearest rings first), or null. */
	public static @Nullable BlockPos standableNear(Level level, BlockPos origin, int radius) {
		if (!level.isLoaded(origin)) {
			return null;
		}
		if (standable(level, origin)) {
			return origin;
		}
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		for (int r = 1; r <= radius; r++) {
			for (int dy = 0; dy <= r; dy = dy <= 0 ? -dy + 1 : -dy) {
				for (int dx = -r; dx <= r; dx++) {
					for (int dz = -r; dz <= r; dz++) {
						if (Math.max(Math.abs(dx), Math.abs(dz)) != r && Math.abs(dy) != r) {
							continue;
						}
						p.set(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);
						if (level.isLoaded(p) && standable(level, p)) {
							return p.immutable();
						}
					}
				}
			}
		}
		return null;
	}
}
