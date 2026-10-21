package dev.voidmc.hearth.sim.terrain;

import dev.voidmc.expanse.world.terrain.CavernModel;
import dev.voidmc.expanse.world.terrain.GreatMines;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;

/**
 * Expanse's great mines as Hearth's sim reads them (docs/LIVING_WORLD_2.md §6.2), straight from the cavern model the
 * underground probe already holds — the same answer as {@code ExpanseSights.greatMines}, without needing a level, so a
 * history's survey on a world generation worker finds them too. Thread-safe (the model caches its mines). Only loaded
 * when Expanse is ({@link ExpanseUnderground}).
 */
final class ExpanseGreatMines implements dev.voidmc.hearth.sim.land.GreatMines.Source {
	private final CavernModel caverns;

	ExpanseGreatMines(CavernModel caverns) {
		this.caverns = caverns;
	}

	@Override
	public List<dev.voidmc.hearth.sim.land.GreatMines.Mine> near(int x, int z, int radius) {
		List<dev.voidmc.hearth.sim.land.GreatMines.Mine> found = new ArrayList<>(1);
		for (int mx = Math.floorDiv(x - radius, GreatMines.CELL); mx <= Math.floorDiv(x + radius, GreatMines.CELL); mx++) {
			for (int mz = Math.floorDiv(z - radius, GreatMines.CELL); mz <= Math.floorDiv(z + radius, GreatMines.CELL); mz++) {
				GreatMines.Mine m = this.caverns.mine(mx, mz);
				if (!m.exists() || Math.abs(m.x() - x) > radius || Math.abs(m.z() - z) > radius) {
					continue;
				}
				List<Integer> levels = m.drifts().stream().map(GreatMines.Drift::floor).distinct().sorted((a, b) -> b - a).toList();
				found.add(new dev.voidmc.hearth.sim.land.GreatMines.Mine(new BlockPos(m.x(), m.ground() + 1, m.z()), m.bottom(), levels));
			}
		}
		if (found.size() > 1) {
			found.sort((a, b) -> Long.compare(dist(a.pithead(), x, z), dist(b.pithead(), x, z)));
		}
		return found;
	}

	private static long dist(BlockPos p, int x, int z) {
		long dx = p.getX() - x;
		long dz = p.getZ() - z;
		return dx * dx + dz * dz;
	}
}
