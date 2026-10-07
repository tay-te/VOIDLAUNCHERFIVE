package dev.voidmc.hearth.gametest.render;

import dev.voidmc.hearth.api.Blueprint;
import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.PlotSpec;
import dev.voidmc.hearth.api.TerrainProbe;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.jspecify.annotations.Nullable;

/** Builds a renderable world: the natural ground round a plot from the terrain probe, with a blueprint applied on it. */
public final class PreviewScene {
	private PreviewScene() {
	}

	/**
	 * @param margin  columns of terrain shown round the footprint
	 * @param culture decides the ground cover (sand in the desert, snow in the tundra...)
	 */
	public static Voxels build(@Nullable Blueprint bp, PlotSpec spec, TerrainProbe probe, int margin, Culture culture) {
		Voxels v = new Voxels();
		int x0 = Math.min(spec.origin().getX(), spec.origin().getX()) - margin;
		int z0 = spec.origin().getZ() - margin;
		int x1 = spec.origin().getX() + spec.sizeX() - 1 + margin;
		int z1 = spec.origin().getZ() + spec.sizeZ() - 1 + margin;
		boolean snowy = probe.temperature(spec.origin().getX(), spec.origin().getZ()) < 0.15F;
		for (int x = x0; x <= x1; x++) {
			for (int z = z0; z <= z1; z++) {
				int g = probe.groundY(x, z);
				int lowest = g;
				for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
					int nx = x + d[0];
					int nz = z + d[1];
					if (nx >= x0 && nx <= x1 && nz >= z0 && nz <= z1) {
						lowest = Math.min(lowest, probe.groundY(nx, nz));
					}
				}
				int bottom = Math.min(lowest, g) - 2;
				if (x == x0 || x == x1 || z == z0 || z == z1) {
					bottom = Math.min(bottom, Math.min(g, spec.floorY()) - 3);
				}
				int water = probe.waterY(x, z);
				for (int y = bottom; y < g; y++) {
					int depth = g - 1 - y;
					v.set(x, y, z, ground(culture, depth, water != TerrainProbe.NO_WATER, snowy, x, z));
				}
				if (water != TerrainProbe.NO_WATER) {
					for (int y = g; y < water; y++) {
						v.set(x, y, z, Blocks.WATER.defaultBlockState());
					}
				} else if (snowy) {
					v.set(x, g, z, Blocks.SNOW.defaultBlockState().setValue(BlockStateProperties.LAYERS, 1 + Math.floorMod(x * 7 + z * 13, 2)));
				}
			}
		}
		if (bp != null) {
			for (int s = 0; s < bp.stageCount(); s++) {
				for (int i = 0; i < bp.stepCount(s); i++) {
					bp.step(s, i, (x, y, z, state, tag) -> v.set(x, y, z, state));
				}
			}
			// snow on the ground stops where something now stands on it
			if (snowy) {
				for (int x = x0; x <= x1; x++) {
					for (int z = z0; z <= z1; z++) {
						int g = probe.groundY(x, z);
						BlockState s = v.get(x, g, z);
						BlockState below = v.get(x, g - 1, z);
						if (s != null && s.is(Blocks.SNOW) && (below == null || !below.isSolidRender())) {
							v.set(x, g, z, Blocks.AIR.defaultBlockState());
						}
					}
				}
			}
		}
		return v;
	}

	private static BlockState ground(Culture c, int depth, boolean wet, boolean snowy, int x, int z) {
		int h = GroundNoise.patch(x, z, 7);
		if (wet) {
			return depth == 0 ? (h < 3 ? Blocks.SAND : h < 5 ? Blocks.GRAVEL : Blocks.DIRT).defaultBlockState() : Blocks.DIRT.defaultBlockState();
		}
		if (depth >= 4) {
			return Blocks.STONE.defaultBlockState();
		}
		switch (c) {
			case DESERT -> {
				return depth < 3 ? Blocks.SAND.defaultBlockState() : Blocks.SANDSTONE.defaultBlockState();
			}
			case MESA -> {
				return depth == 0 ? (h < 4 ? Blocks.RED_SAND : Blocks.COARSE_DIRT).defaultBlockState()
					: (depth % 2 == 0 ? Blocks.DYED_TERRACOTTA.pick(net.minecraft.world.item.DyeColor.ORANGE) : Blocks.TERRACOTTA).defaultBlockState();
			}
			case STEPPE -> {
				if (depth == 0) {
					return (h < 2 ? Blocks.COARSE_DIRT : Blocks.GRASS_BLOCK).defaultBlockState();
				}
			}
			case KARST -> {
				if (depth > 1) {
					return Blocks.CALCITE.defaultBlockState();
				}
			}
			default -> {
			}
		}
		if (depth == 0) {
			BlockState g = Blocks.GRASS_BLOCK.defaultBlockState();
			return snowy ? g.setValue(BlockStateProperties.SNOWY, true) : g;
		}
		return Blocks.DIRT.defaultBlockState();
	}
}
