package dev.voidmc.hearth.build.archetype.farm;

import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.kit.Furniture;
import dev.voidmc.hearth.build.kit.homestead.Terraces;
import dev.voidmc.hearth.build.style.Role;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The store of a terraced field that has no room for its hut: the compost bin and a chest set side by side on trodden
 * ground beside the path up, on the lowest terrace that has two rows for them, both turned to the path. Every worked
 * field keeps its job block and its store this way (the audit's "no composter, no chest" fields of wave 6).
 */
final class PathStore {
	private PathStore() {
	}

	/**
	 * Lays the bin and the chest beside the path across {@code path} of the terraces {@code t}; returns whether it found
	 * room.
	 */
	static boolean beside(Draft d, Terraces t, int path, JobType job, List<Object[]> items) {
		if (path == Integer.MIN_VALUE) {
			return false;
		}
		List<Terraces.Band> bands = new ArrayList<>(t.bands);
		bands.sort(Comparator.comparingInt(Terraces.Band::level));
		for (Terraces.Band b : bands) {
			int y = b.level();
			if (y + 2 >= d.maxHeight()) {
				continue;
			}
			for (int a = t.plantFrom(b); a < t.plantTo(b); a++) {
				for (int c : new int[]{path + 1, path - 1}) {
					if (c <= t.acrossMin() || c >= t.acrossMax()) {
						continue;
					}
					if (!fits(d, t, b, a, c, path) || !fits(d, t, b, a + 1, c, path)) {
						continue;
					}
					int[] bin = t.cell(a, c);
					int[] box = t.cell(a + 1, c);
					int[] by = t.cell(a, path);
					Direction toPath = Dirs.of(Integer.signum(by[0] - bin[0]), Integer.signum(by[1] - bin[1]));
					for (int[] xz : new int[][]{bin, box}) {
						try (var s = d.stage(dev.voidmc.hearth.api.StageKind.EXTERIOR)) {
							d.set(xz[0], y - 1, xz[1], d.mat(Role.PATH).full());
							d.set(xz[0], y, xz[1], Blocks.AIR.defaultBlockState());
							d.set(xz[0], y + 1, xz[1], Blocks.AIR.defaultBlockState());
						}
					}
					Furniture.workstation(d, job, bin[0], y, bin[1], toPath);
					Furniture.chest(d, box[0], y, box[1], toPath, items);
					return true;
				}
			}
		}
		return false;
	}

	/** Whether a cell of band b can take a piece: on the band, its path cell beside it walkable at the band's level. */
	private static boolean fits(Draft d, Terraces t, Terraces.Band b, int a, int c, int path) {
		int[] xz = t.cell(a, c);
		int[] p = t.cell(a, path);
		if (t.band(xz[0], xz[1]) != b || t.band(p[0], p[1]) != b) {
			return false;
		}
		BlockState under = d.get(p[0], b.level() - 1, p[1]);
		BlockState at = d.get(p[0], b.level(), p[1]);
		if (under != null && (under.isAir() || under.getBlock() instanceof StairBlock) || at != null && !at.isAir()) {
			return false;
		}
		BlockState own = d.get(xz[0], b.level() - 1, xz[1]);
		return own == null || !(own.getBlock() instanceof StairBlock);
	}
}
