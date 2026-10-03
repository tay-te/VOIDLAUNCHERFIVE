package dev.voidmc.hearth.build.kit.homestead;

import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.PoiType;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.terrain.Groundwork;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Where a person can stand on a draft: a cell free at feet and head (air, nothing drawn, or a plant) over something to
 * stand on (a drawn solid, farmland, a path, or the natural ground). Points of interest are registered only on such
 * cells, so every job site and seat is somewhere folk can actually be.
 */
public final class Spots {
	private Spots() {
	}

	/** Passable for a person: nothing, air, a plant, a carpet. */
	public static boolean free(Draft d, int x, int y, int z) {
		BlockState s = d.get(x, y, z);
		if (s == null) {
			return y >= d.site().ground(x, z) || d.site().isWater(x, z);
		}
		return s.isAir() || s.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).isEmpty();
	}

	/** Something under foot. */
	public static boolean floor(Draft d, int x, int y, int z) {
		BlockState s = d.get(x, y - 1, z);
		if (s == null) {
			return y - 1 < d.site().ground(x, z);
		}
		return !s.isAir() && !s.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).isEmpty()
			&& !(s.getBlock() instanceof net.minecraft.world.level.block.FenceBlock) && !(s.getBlock() instanceof net.minecraft.world.level.block.WallBlock);
	}

	public static boolean standable(Draft d, int x, int y, int z) {
		return x >= 0 && z >= 0 && x < d.frontage() && z < d.depth() && free(d, x, y, z) && free(d, x, y + 1, z) && floor(d, x, y, z);
	}

	/** The standing level at a column as drawn (ground-only: leaves and roofs overhead are ignored). */
	public static int level(Draft d, int x, int z) {
		return Groundwork.surface(d, x, z);
	}

	/**
	 * Registers a job site at (x, z) on the ground there, or at the first standable neighbour; answers whether one was
	 * registered.
	 */
	public static boolean job(Draft d, int x, int z, Direction facing, JobType job) {
		return poi(d, PoiType.JOB_SITE, x, z, facing, job);
	}

	public static boolean poi(Draft d, PoiType type, int x, int z, Direction facing, JobType job) {
		int[][] around = {{0, 0}, {1, 0}, {-1, 0}, {0, 1}, {0, -1}};
		for (int[] o : around) {
			int px = x + o[0];
			int pz = z + o[1];
			// never on the plot's edge, where its boundary runs
			if (px < 1 || pz < 1 || px >= d.frontage() - 1 || pz >= d.depth() - 1) {
				continue;
			}
			int y = level(d, px, pz);
			if (standable(d, px, y, pz)) {
				d.poi(type, px, y, pz, facing, job);
				return true;
			}
		}
		return false;
	}

	/** A stand on a path or on the ground (not on a step, a wall's coping or a sill): its floor is earth, path or farmland. */
	public static boolean onGround(Draft d, PoiType type, int x, int y, int z, Direction facing, JobType job) {
		BlockState below = d.get(x, y - 1, z);
		boolean ground = below == null ? y - 1 < d.site().ground(x, z) : dev.voidmc.hearth.build.kit.Garden.groundLike(d, x, y - 1, z);
		return ground && at(d, type, x, y, z, facing, job);
	}

	/** Registers a point of interest at an exact cell if it is standable. */
	public static boolean at(Draft d, PoiType type, int x, int y, int z, Direction facing, JobType job) {
		if (standable(d, x, y, z)) {
			d.poi(type, x, y, z, facing, job);
			return true;
		}
		return false;
	}
}
