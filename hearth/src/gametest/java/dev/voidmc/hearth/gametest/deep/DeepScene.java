package dev.voidmc.hearth.gametest.deep;

import dev.voidmc.hearth.api.Blueprint;
import dev.voidmc.hearth.api.TerrainProbe;
import dev.voidmc.hearth.build.kit.deep.DeepTerrain;
import dev.voidmc.hearth.gametest.render.Voxels;
import java.util.function.IntBinaryOperator;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A realm round a deep plot for the previews: its floor dressed as the realm's character dresses it (moss in the wilds
 * and the lush realms, dripstone and tuff, calcite and amethyst in the crystal realms, basalt and blackstone in the ember
 * and brimstone realms, sand in the fossil realms...), the rock of its walls, and the vault overhead, which is kept apart
 * so a render can show the town lit by its own light under the vault and still see in from above.
 */
public final class DeepScene {
	/** The scene without its vault (what a render shows). */
	public final Voxels world = new Voxels();
	/** The scene with the vault over it (what the light is computed on: no sky reaches a realm). */
	public final Voxels sealed = new Voxels();

	private DeepScene() {
	}

	/**
	 * @param x0..z1  the world rectangle shown
	 * @param ceiling the vault's underside per column (or null: open sky, a surface terrain)
	 */
	public static DeepScene build(TerrainProbe probe, int x0, int z0, int x1, int z1, int bottom, String character, IntBinaryOperator ceiling) {
		DeepScene s = new DeepScene();
		for (int x = x0; x <= x1; x++) {
			for (int z = z0; z <= z1; z++) {
				int g = probe.groundY(x, z);
				int low = g;
				for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
					int nx = x + d[0];
					int nz = z + d[1];
					if (nx >= x0 && nx <= x1 && nz >= z0 && nz <= z1) {
						low = Math.min(low, probe.groundY(nx, nz));
					}
				}
				boolean edge = x == x0 || x == x1 || z == z0 || z == z1;
				int from = edge ? Math.min(bottom, g - 3) : Math.min(low, g) - 2;
				int vault = ceiling == null ? Integer.MAX_VALUE : ceiling.applyAsInt(x, z);
				// rock walls reach the vault: draw them only a little way up, the render looks in over them
				int shown = Math.min(g, vault);
				for (int y = from; y < shown; y++) {
					BlockState b = rock(character, shown - 1 - y, x, y, z, g > DeepTerrain.FLOOR + 20);
					s.world.set(x, y, z, b);
					s.sealed.set(x, y, z, b);
				}
				if (ceiling != null) {
					for (int y = vault; y < vault + 2; y++) {
						s.sealed.set(x, y, z, Blocks.DEEPSLATE.defaultBlockState());
					}
				}
			}
		}
		return s;
	}

	/** Lays a blueprint over the scene. */
	public void apply(Blueprint bp) {
		for (int st = 0; st < bp.stageCount(); st++) {
			for (int i = 0; i < bp.stepCount(st); i++) {
				bp.step(st, i, (x, y, z, state, tag) -> {
					this.world.set(x, y, z, state);
					this.sealed.set(x, y, z, state);
				});
			}
		}
	}

	private static BlockState rock(String character, int depth, int x, int y, int z, boolean wall) {
		// patches, not salt and pepper: smooth noise over a few blocks, a little grain on top
		double n = dev.voidmc.hearth.build.draft.Noise.value(0x5CE7E, x / 4.5, y / 3.0, z / 4.5) * 0.85
			+ dev.voidmc.hearth.build.draft.Noise.unit(0x5CE7F, x, y, z) * 0.15;
		int h = Math.min(10, (int) (n * 11));
		if (wall) {
			// the realm's wall: banded rock
			Block b = switch (character) {
				case "crystal" -> h < 6 ? Blocks.CALCITE : h < 9 ? Blocks.TUFF : Blocks.AMETHYST_BLOCK;
				case "ember", "brimstone" -> h < 6 ? Blocks.BASALT : h < 9 ? Blocks.BLACKSTONE : Blocks.MAGMA_BLOCK;
				case "fossil" -> h < 7 ? Blocks.SANDSTONE : h < 10 ? Blocks.SMOOTH_SANDSTONE : Blocks.BONE_BLOCK;
				case "dripstone" -> h < 6 ? Blocks.DRIPSTONE_BLOCK : Blocks.DEEPSLATE;
				case "frozen" -> h < 5 ? Blocks.PACKED_ICE : Blocks.DEEPSLATE;
				case "amber" -> h < 8 ? Blocks.DEEPSLATE : h < 10 ? Blocks.TUFF : Blocks.SHROOMLIGHT;
				case "lush", "roots" -> h < 3 ? Blocks.MOSS_BLOCK : h < 5 ? Blocks.ROOTED_DIRT : Blocks.DEEPSLATE;
				default -> (y & 3) == 0 ? Blocks.TUFF : Blocks.DEEPSLATE;
			};
			return b.defaultBlockState();
		}
		if (depth == 0) {
			Block top = switch (character) {
				case "wilds", "lush" -> h < 8 ? Blocks.MOSS_BLOCK : Blocks.ROOTED_DIRT;
				case "roots" -> h < 6 ? Blocks.ROOTED_DIRT : Blocks.MOSS_BLOCK;
				case "crystal" -> h < 7 ? Blocks.CALCITE : Blocks.TUFF;
				case "ember", "brimstone" -> h < 6 ? Blocks.BASALT : h < 10 ? Blocks.BLACKSTONE : Blocks.MAGMA_BLOCK;
				case "fossil" -> h < 8 ? Blocks.SAND : Blocks.SANDSTONE;
				case "dripstone" -> h < 6 ? Blocks.DRIPSTONE_BLOCK : Blocks.TUFF;
				case "frozen" -> h < 5 ? Blocks.PACKED_ICE : Blocks.SNOW_BLOCK;
				case "mere", "tidal" -> h < 6 ? Blocks.CALCITE : Blocks.GRAVEL;
				case "amber" -> h < 8 ? Blocks.COARSE_DIRT : Blocks.TUFF;
				default -> Blocks.TUFF;
			};
			return top.defaultBlockState();
		}
		return (depth < 3 ? (h < 5 ? Blocks.TUFF : Blocks.COBBLED_DEEPSLATE) : Blocks.DEEPSLATE).defaultBlockState();
	}
}
