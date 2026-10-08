package dev.voidmc.expanse.api;

import dev.voidmc.expanse.world.terrain.CavernModel;
import dev.voidmc.expanse.world.terrain.EarthChunkGenerator;
import dev.voidmc.expanse.world.terrain.GreatMines;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * The Expanse's own sights, for other mods to find and build on: for now the great mines (VOID Hearth may people a
 * pithead, or send a quest down one).
 *
 * <p>All of it is worked out from the seed, the same before and after the chunks are generated: asking is cheap once
 * a place has been asked about, and safe from any thread.
 */
public final class ExpanseSights {
	private ExpanseSights() {
	}

	/**
	 * A great mine: a shaft sunk from a pithead high on a mountain down into a realm.
	 *
	 * @param pithead     the deck over the shaft's mouth: the centre of the shaft, at the deck's level (the deck is
	 *                    eleven blocks square; the headframe stands over its middle seven)
	 * @param shaftBottom the lowest open block of the shaft (the great drift's floor)
	 * @param levels      the floors of the levels opening off the shaft, highest first (the lowest is the great drift's)
	 * @param realmMouth  where the great drift ends, well inside the realm (on its floor)
	 * @param realmCentre the realm's centre (x, z; y is the shaft's bottom)
	 */
	public record GreatMine(BlockPos pithead, int shaftBottom, List<Integer> levels, BlockPos realmMouth, BlockPos realmCentre) {
	}

	/**
	 * The great mines whose shafts lie within {@code radius} blocks (square) of (x, z), nearest first; empty if the level
	 * is not on the Expanse's Earth terrain. They are rare: about one in each 2.4 km square that has a realm under high
	 * ground beside it.
	 */
	public static List<GreatMine> greatMines(ServerLevel level, int x, int z, int radius) {
		if (!(level.getChunkSource().getGenerator() instanceof EarthChunkGenerator earth)) {
			return List.of();
		}
		CavernModel caverns = earth.model(level.getChunkSource().randomState()).caverns();
		List<GreatMine> found = new ArrayList<>();
		for (int mx = Math.floorDiv(x - radius, GreatMines.CELL); mx <= Math.floorDiv(x + radius, GreatMines.CELL); mx++) {
			for (int mz = Math.floorDiv(z - radius, GreatMines.CELL); mz <= Math.floorDiv(z + radius, GreatMines.CELL); mz++) {
				GreatMines.Mine m = caverns.mine(mx, mz);
				if (!m.exists() || Math.abs(m.x() - x) > radius || Math.abs(m.z() - z) > radius) {
					continue;
				}
				List<Integer> levels = m.drifts().stream().map(GreatMines.Drift::floor).distinct().sorted((a, b) -> b - a).toList();
				found.add(new GreatMine(new BlockPos(m.x(), m.ground() + 1, m.z()), m.bottom(), levels,
					new BlockPos(m.mouthX(), m.bottom(), m.mouthZ()), new BlockPos(m.realmX(), m.bottom(), m.realmZ())));
			}
		}
		found.sort((a, b) -> Long.compare(dist(a.pithead(), x, z), dist(b.pithead(), x, z)));
		return found;
	}

	private static long dist(BlockPos p, int x, int z) {
		long dx = p.getX() - x;
		long dz = p.getZ() - z;
		return dx * dx + dz * dz;
	}
}
