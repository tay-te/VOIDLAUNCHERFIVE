package dev.voidmc.hearth.build.kit.civic;

import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Mat;
import dev.voidmc.hearth.build.draft.Paint;
import dev.voidmc.hearth.build.style.Form;
import dev.voidmc.hearth.build.style.Role;
import dev.voidmc.hearth.build.terrain.Groundwork;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.Half;

/**
 * How a civic building meets its ground and its street: a paved forecourt levelled to the street, a floor raised a step
 * above it for presence with a broad flight across the front, and the way from the door to the street wherever the
 * land falls or rises.
 */
public final class Grounds {
	private Grounds() {
	}

	/** Whether the ground over a rectangle stays within a block of level {@code y} (gentle enough to level quietly). */
	public static boolean gentle(Draft d, int x0, int z0, int x1, int z1, int y) {
		return d.site().maxGround(x0, z0, x1, z1) - y <= 1 && y - d.site().minGround(x0, z0, x1, z1) <= 1 && !d.site().anyWater(x0, z0, x1, z1);
	}

	/**
	 * The floor a building of this footprint stands on: a step above the street (1) when the land in front of it is level
	 * enough for a flight, else level with it (0).
	 */
	public static int raisedBase(Draft d, int x0, int x1, int zFront) {
		if (zFront < 2) {
			return 0;
		}
		return gentle(d, x0, 0, x1, zFront - 1, 0) ? 1 : 0;
	}

	/**
	 * A forecourt over rows {@code 0 .. z1} between {@code x0} and {@code x1}, levelled to the street (standing level 0)
	 * where that is gentle, and paved in the style's paving with a border; ragged edges are left to the ground.
	 */
	public static void forecourt(Draft d, int x0, int x1, int z1, boolean pave) {
		if (z1 < 0) {
			return;
		}
		if (gentle(d, x0, 0, x1, z1, 0)) {
			Groundwork.level(d, x0, 0, x1, z1, 0);
		}
		if (!pave) {
			return;
		}
		Mat paving = d.mat(Role.PAVING);
		try (var s = d.stage(StageKind.EXTERIOR)) {
			for (int z = 0; z <= z1; z++) {
				for (int x = x0; x <= x1; x++) {
					if (Groundwork.surface(d, x, z) != 0 || !d.isEmpty(x, 0, z) || d.site().isWater(x, z)) {
						continue;
					}
					d.set(x, -1, z, paving.full());
				}
			}
		}
	}

	/**
	 * A broad flight of {@code rise} steps up to a raised floor: stairs climbing toward the back across columns
	 * {@code x0..x1}, the top step in row {@code zTop} at standing level {@code yTop}, each lower step a row nearer the
	 * street, footed down to the ground; cheek blocks at the ends where there is room. Stops at the front row.
	 */
	public static void flight(Draft d, int x0, int x1, int zTop, int yTop, int rise) {
		Mat paving = d.mat(Role.PAVING);
		Mat stone = d.mat(Role.FOUNDATION);
		Mat cheek = d.mat(Role.PLINTH);
		Paint step = paving.has(Form.STAIRS) ? paving.stairs(Dirs.BACK, Half.BOTTOM) : stone.stairsOrSlab(Dirs.BACK, Half.BOTTOM);
		for (int k = 0; k < rise; k++) {
			int z = zTop - k;
			int top = yTop - 1 - k;
			if (z < 0) {
				break;
			}
			for (int x = x0; x <= x1; x++) {
				int g = d.site().ground(x, z);
				try (var s = d.stage(StageKind.FOUNDATION)) {
					for (int y = g - 1; y < top; y++) {
						d.set(x, y, z, stone.full());
					}
				}
				try (var s = d.stage(StageKind.EXTERIOR)) {
					d.set(x, top, z, step);
				}
				// cut the earth over the step (a flight up a bank is a cutting)
				try (var s = d.stage(StageKind.EARTHWORK)) {
					for (int y = top + 1; y <= Math.max(top + 2, g - 1); y++) {
						if (d.isAbsent(x, y, z)) {
							d.set(x, y, z, Blocks.AIR.defaultBlockState());
						}
					}
				}
			}
			for (int x : new int[]{x0 - 1, x1 + 1}) {
				if (x < 0 || x >= d.frontage() || !d.isAbsent(x, top, z)) {
					continue;
				}
				try (var s = d.stage(StageKind.WALLS)) {
					for (int y = d.site().ground(x, z) - 1; y <= top; y++) {
						d.set(x, y, z, cheek.full());
					}
				}
			}
		}
	}

	/**
	 * Cuts the natural ground away over a rectangle from standing level {@code y} up (air in EARTHWORK where nothing is
	 * drawn): open floors, terraces, arcades and porches set into rising ground stay open.
	 */
	public static void cut(Draft d, int x0, int z0, int x1, int z1, int y) {
		try (var s = d.stage(StageKind.EARTHWORK)) {
			for (int z = Math.max(0, Math.min(z0, z1)); z <= Math.min(d.depth() - 1, Math.max(z0, z1)); z++) {
				for (int x = Math.max(0, Math.min(x0, x1)); x <= Math.min(d.frontage() - 1, Math.max(x0, x1)); x++) {
					int g = d.site().ground(x, z);
					for (int yy = y; yy < g; yy++) {
						if (d.isAbsent(x, yy, z)) {
							d.set(x, yy, z, Blocks.AIR.defaultBlockState());
						}
					}
				}
			}
		}
	}

	/**
	 * The way in: from the cell outside a door (x, zOut) at standing level {@code y} to the street, by the kit's approach
	 * (steps down or up, paving, footings) when the ground does not already meet it.
	 */
	public static void approach(Draft d, int x, int y, int zOut) {
		if (zOut < 0) {
			return;
		}
		Groundwork.approach(d, x, y, zOut);
	}
}
