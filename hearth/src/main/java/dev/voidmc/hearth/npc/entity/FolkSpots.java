package dev.voidmc.hearth.npc.entity;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
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

	/**
	 * Where a folk stands to use {@code target}: the spot itself if one can stand there, else the standable cells
	 * beside it (level, a step up or a step down), beside either half of a bed. Under a roof only cells under a roof
	 * count, so a bed or a bench against an outside wall is reached from the room, not from the street through the
	 * wall. Empty if none (the caller falls back on the target).
	 *
	 * <p>Paths are asked for these rather than for the block itself: a bed, a chest or an anvil is no place to stand, and
	 * a search for it never ends early but runs through its whole node budget and returns wherever it got nearest,
	 * which for a bed upstairs is often the floor below it.
	 */
	public static List<BlockPos> approaches(Level level, BlockPos target) {
		List<BlockPos> out = new ArrayList<>(4);
		if (!level.isLoaded(target)) {
			return out;
		}
		if (standable(level, target)) {
			out.add(target);
			return out;
		}
		boolean roofed = !level.canSeeSky(target);
		List<BlockPos> around = new ArrayList<>(2);
		around.add(target);
		BlockState state = level.getBlockState(target);
		if (state.getBlock() instanceof BedBlock && state.hasProperty(BedBlock.PART) && state.hasProperty(BedBlock.FACING)) {
			around.add(target.relative(BedBlock.getConnectedDirection(state)));
		}
		for (BlockPos at : around) {
			for (Direction d : Direction.Plane.HORIZONTAL) {
				for (int dy : new int[]{0, 1, -1}) {
					BlockPos c = at.relative(d).above(dy);
					if (!out.contains(c) && !around.contains(c) && level.isLoaded(c) && standable(level, c) && (!roofed || !level.canSeeSky(c))) {
						out.add(c);
						break;
					}
				}
			}
		}
		return out;
	}
}
