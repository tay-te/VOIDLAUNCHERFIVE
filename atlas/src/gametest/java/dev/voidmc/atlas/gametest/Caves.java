package dev.voidmc.atlas.gametest;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Finds a real cave near a point for the tour (on the server): a pocket of air well under the ground, between two
 * heights, and a walk through it — stops a few blocks apart along the connected air — so the player can be taken
 * through it as a player would walk it.
 */
final class Caves {
	private Caves() {
	}

	private static boolean air(ServerLevel level, BlockPos p) {
		BlockState s = level.getBlockState(p);
		return s.isAir() && s.getFluidState().isEmpty();
	}

	/** A walk of up to a dozen stops through the first big enough cave found round {@code near} between {@code lo} and {@code hi}. */
	static List<BlockPos> path(ServerLevel level, BlockPos near, int lo, int hi) {
		for (int r = 0; r <= 160; r += 8) {
			for (int a = 0; a < 16; a++) {
				double angle = a * Math.PI / 8;
				int x = near.getX() + (int) Math.round(Math.cos(angle) * r);
				int z = near.getZ() + (int) Math.round(Math.sin(angle) * r);
				level.getChunk(x >> 4, z >> 4);
				int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
				for (int y = hi; y >= lo; y--) {
					BlockPos p = new BlockPos(x, y, z);
					if (y < surface - 16 && air(level, p) && air(level, p.above()) && !air(level, p.below())) {
						List<BlockPos> walk = walk(level, p, surface);
						if (walk.size() >= 5) {
							return walk;
						}
					}
				}
			}
		}
		return List.of();
	}

	/** Breadth-first through the cave's air (where a player could stand), taking a stop every 8 steps out. */
	private static List<BlockPos> walk(ServerLevel level, BlockPos start, int surface) {
		Map<BlockPos, Integer> seen = new HashMap<>();
		ArrayDeque<BlockPos> queue = new ArrayDeque<>();
		seen.put(start, 0);
		queue.add(start);
		List<BlockPos> stops = new ArrayList<>();
		stops.add(start);
		while (!queue.isEmpty() && seen.size() < 4000 && stops.size() < 12) {
			BlockPos p = queue.poll();
			int d = seen.get(p);
			for (BlockPos n : new BlockPos[]{p.east(), p.west(), p.north(), p.south(), p.above(), p.below()}) {
				if (seen.containsKey(n) || n.getY() > surface - 8 || !air(level, n) || !level.hasChunkAt(n)) {
					continue;
				}
				seen.put(n, d + 1);
				queue.add(n);
				if ((d + 1) % 8 == 0 && air(level, n.above()) && !air(level, n.below())) {
					boolean far = true;
					for (BlockPos s : stops) {
						far &= s.distSqr(n) > 25;
					}
					if (far) {
						stops.add(n);
					}
				}
			}
		}
		return stops;
	}
}
