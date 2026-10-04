package dev.voidmc.hearth.build.kit.station;

import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Noise;
import dev.voidmc.hearth.build.draft.States;
import dev.voidmc.hearth.build.kit.Garden;
import dev.voidmc.hearth.build.kit.homestead.Spots;
import dev.voidmc.hearth.build.style.Role;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * What grows up against a house that has stood a while: tufts of grass and fern along the foot of its walls, a flower
 * of the garden's here and there, a bush of the culture's hedge in a corner, so a building sits in its ground instead of
 * on it. Only on free natural ground (never on a path, a step, paving or a POI), and never on the cells beside a door.
 */
public final class Verdure {
	private Verdure() {
	}

	/**
	 * Plants the foot of the walls round a rectangle (x0, z0, x1, z1, the walls), on the row outside them, at about
	 * {@code density} of the free cells. Cells within a block of a door's step are left clear.
	 */
	public static void foot(Draft d, int x0, int z0, int x1, int z1, double density) {
		long seed = Noise.hash(d.seed(), 0x5E7D);
		for (int[] c : Draft.ring(x0 - 1, z0 - 1, x1 + 1, z1 + 1)) {
			int x = c[0];
			int z = c[1];
			if (x < 0 || z < 0 || x >= d.frontage() || z >= d.depth() || nearDoor(d, x, z)) {
				continue;
			}
			int y = Spots.level(d, x, z);
			if (!Spots.standable(d, x, y, z) || Spots.claimed(d, x, y, z) || !natural(d, x, y - 1, z)) {
				continue;
			}
			double u = Noise.unit(seed, x, y, z);
			if (u > density) {
				continue;
			}
			boolean corner = (x == x0 - 1 || x == x1 + 1) && (z == z0 - 1 || z == z1 + 1);
			plant(d, x, y, z, corner ? 0.95 : u / density);
		}
	}

	/** One plant at (x, y, z) on its ground, by a roll in [0, 1): a bush, a flower, a fern, grass. */
	public static void plant(Draft d, int x, int y, int z, double roll) {
		try (var s = d.stage(StageKind.EXTERIOR)) {
			if (roll > 0.85) {
				Block hedge = d.mat(Role.HEDGE).main(dev.voidmc.hearth.build.style.Form.FULL);
				BlockState leaves = hedge == null ? States.leaves(Blocks.OAK_LEAVES) : States.leaves(hedge);
				d.set(x, y, z, leaves);
			} else if (roll > 0.65) {
				Garden.flower(d, x, y, z);
			} else if (roll > 0.35) {
				d.set(x, y, z, Blocks.FERN.defaultBlockState());
			} else {
				d.set(x, y, z, Blocks.SHORT_GRASS.defaultBlockState());
			}
		}
	}

	private static boolean nearDoor(Draft d, int x, int z) {
		for (Draft.LocalPoi p : d.pois()) {
			if (p.type() == dev.voidmc.hearth.api.PoiType.DOOR && Math.abs(p.x() - x) + Math.abs(p.z() - z) <= 1) {
				return true;
			}
		}
		for (int[] e : d.entrances()) {
			if (Math.abs(e[0] - x) + Math.abs(e[2] - z) <= 1) {
				return true;
			}
		}
		return false;
	}

	/** Whether the block under a cell is the natural ground or plain earth laid as yard (not paving, a path or a floor). */
	static boolean natural(Draft d, int x, int y, int z) {
		BlockState s = d.get(x, y, z);
		if (s == null) {
			return y < d.site().ground(x, z) && !d.site().isWater(x, z);
		}
		return s.is(Blocks.GRASS_BLOCK) || s.is(Blocks.DIRT) || s.is(Blocks.PODZOL) || s.is(Blocks.COARSE_DIRT);
	}

	/** Unused direction helper kept for symmetry with other kits. */
	static Direction toward(int dx, int dz) {
		return Dirs.of(dx, dz);
	}
}
