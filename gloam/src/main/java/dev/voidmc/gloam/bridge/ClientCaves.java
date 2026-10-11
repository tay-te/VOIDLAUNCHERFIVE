package dev.voidmc.gloam.bridge;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

/**
 * The caves read from the loaded blocks, where Expanse's caverns are not to hand (a dedicated server, another world
 * generator): for each cell a short look up and down the column at the slice's height for the floor and the roof, and
 * four short looks sideways for the walls. About 30 block reads a cell, so it runs on the client thread (the level's
 * chunks are not for other threads) with a small budget ({@value #BUDGET} cells a job, a job every half second). No
 * realms or life: those are Expanse's.
 */
final class ClientCaves implements UndergroundTile.CaveSource {
	/** Cells a job may read. */
	static final int BUDGET = 256;
	/** How far up and down the floor and roof are looked for, and how far sideways the walls. */
	private static final int LOOK = 32;
	private static final int[] SIDE = {2, 4, 8, 12};
	private static final int[][] DIRS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

	final ClientLevel level;
	private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

	ClientCaves(ClientLevel level) {
		this.level = level;
	}

	@Override
	public boolean at(int x, int y, int z, UndergroundTile.Sample out) {
		ClientLevel level = this.level;
		if (!level.hasChunk(x >> 4, z >> 4) || y < level.getMinY() || y >= level.getMaxY()) {
			return false;
		}
		out.clear(y);
		if (this.solid(x, y, z)) {
			return true;
		}
		int floor = y, ceiling = y;
		while (floor > y - LOOK && floor > level.getMinY() && !this.solid(x, floor - 1, z)) {
			floor--;
		}
		while (ceiling < y + LOOK && ceiling < level.getMaxY() - 1 && !this.solid(x, ceiling + 1, z)) {
			ceiling++;
		}
		float space = Math.min(y - floor, ceiling - y) + 1;
		for (int[] d : DIRS) {
			int reach = SIDE[SIDE.length - 1];
			for (int r : SIDE) {
				int sx = x + d[0] * r, sz = z + d[1] * r;
				if (level.hasChunk(sx >> 4, sz >> 4) && this.solid(sx, y, sz)) {
					reach = r;
					break;
				}
			}
			space = Math.min(space, reach);
		}
		out.space = space;
		out.floor = floor;
		out.ceiling = ceiling;
		FluidState fluid = level.getFluidState(this.pos.set(x, floor, z));
		if (fluid.is(FluidTags.WATER)) {
			out.water = TileCodec.WATER_LAKE;
			out.waterDistance = 0;
		} else if (fluid.is(FluidTags.LAVA)) {
			out.water = TileCodec.WATER_LAVA;
			out.waterDistance = 0;
		}
		return true;
	}

	/** Whether the block at (x, y, z) closes the cave: anything that stops movement (water and air do not). */
	private boolean solid(int x, int y, int z) {
		BlockState state = this.level.getBlockState(this.pos.set(x, y, z));
		return !state.isAir() && state.isSolid();
	}
}
