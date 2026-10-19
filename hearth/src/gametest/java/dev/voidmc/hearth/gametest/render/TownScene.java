package dev.voidmc.hearth.gametest.render;

import dev.voidmc.hearth.api.Blueprint;
import dev.voidmc.hearth.api.BlueprintService;
import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.PlotSpec;
import dev.voidmc.hearth.api.PlotState;
import dev.voidmc.hearth.api.PlotView;
import dev.voidmc.hearth.api.TerrainProbe;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * A window of a grown town as a renderable world: the natural ground from the terrain probe (only its skin, so a whole
 * town fits in memory), then every plot over the window from its real blueprint, in id order with the sim's writer rule
 * (a later plot replaces only natural ground, air, plants and its own blocks; what an earlier plot built stays), as far
 * as each plot is built. Snow lies on open ground in the cold and stops under whatever now stands there.
 */
public final class TownScene {
	private TownScene() {
	}

	public static Voxels build(Iterable<? extends PlotView> plots, BlueprintService service, TerrainProbe terrain, int x0, int z0, int x1, int z1,
		Culture culture) {
		return build(plots, service, terrain, x0, z0, x1, z1, culture, new Long2IntOpenHashMap());
	}

	/**
	 * As {@link #build(Iterable, BlueprintService, TerrainProbe, int, int, int, int, Culture)}, filling {@code owner} with
	 * who placed each position ({@link Voxels#key}; the plot's id + 1, absent for nature): what the floating-piece probe
	 * of the composed town reads ({@link TownFloat}).
	 */
	public static Voxels build(Iterable<? extends PlotView> plots, BlueprintService service, TerrainProbe terrain, int x0, int z0, int x1, int z1,
		Culture culture, Long2IntOpenHashMap owner) {
		Voxels v = new Voxels();
		for (int x = x0; x <= x1; x++) {
			for (int z = z0; z <= z1; z++) {
				int g = terrain.groundY(x, z);
				// down to the lowest neighbour (so slopes show their side), deeper along the window's edge (a cut block)
				int low = g;
				for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
					low = Math.min(low, terrain.groundY(x + d[0], z + d[1]));
				}
				int bottom = x == x0 || x == x1 || z == z0 || z == z1 ? low - 4 : low - 1;
				int water = terrain.waterY(x, z);
				boolean wet = water != TerrainProbe.NO_WATER;
				for (int y = bottom; y < g; y++) {
					v.set(x, y, z, ground(culture, g - 1 - y, wet, x, z));
				}
				if (wet) {
					for (int y = g; y < water; y++) {
						v.set(x, y, z, Blocks.WATER.defaultBlockState());
					}
				} else if (terrain.temperature(x, z) < 0.15F) {
					v.set(x, g, z, Blocks.SNOW.defaultBlockState().setValue(BlockStateProperties.LAYERS, 1 + Math.floorMod(x * 7 + z * 13, 2)));
				}
			}
		}
		List<PlotView> sorted = new ArrayList<>();
		plots.forEach(sorted::add);
		sorted.sort(Comparator.comparingInt(PlotView::id));
		// who placed each position (0: nature)
		for (PlotView p : sorted) {
			if (p.state() == PlotState.PLANNED) {
				continue;
			}
			PlotSpec s = p.spec();
			if (s.origin().getX() > x1 + 1 || s.origin().getZ() > z1 + 1 || s.origin().getX() + s.sizeX() < x0 - 1 || s.origin().getZ() + s.sizeZ() < z0 - 1) {
				continue;
			}
			Blueprint bp;
			try {
				bp = service.blueprint(s, terrain);
			} catch (RuntimeException e) {
				System.out.println("  plot " + p.id() + " " + s.archetype() + ": " + e);
				continue;
			}
			int id = p.id() + 1;
			int stages = p.state() == PlotState.BUILT ? bp.stageCount() : Math.min(bp.stageCount(), Math.max(1, p.stage()));
			for (int st = 0; st < stages; st++) {
				for (int i = 0; i < bp.stepCount(st); i++) {
					bp.step(st, i, (x, y, z, state, tag) -> {
						if (x < x0 || x > x1 || z < z0 || z > z1) {
							return;
						}
						long k = Voxels.key(x, y, z);
						int who = owner.getOrDefault(k, 0);
						BlockState cur = v.get(x, y, z);
						if (who != 0 && who != id && cur != null && !cur.isAir() && !cur.canBeReplaced()) {
							return;
						}
						if (state.isAir() && cur == null) {
							return;
						}
						v.set(x, y, z, state);
						owner.put(k, id);
					});
				}
			}
		}
		// snow stops where something now stands on it
		for (int x = x0; x <= x1; x++) {
			for (int z = z0; z <= z1; z++) {
				int g = terrain.groundY(x, z);
				BlockState s = v.get(x, g, z);
				BlockState below = v.get(x, g - 1, z);
				if (s != null && s.is(Blocks.SNOW) && (below == null || !below.isSolidRender() || owner.containsKey(Voxels.key(x, g - 1, z)))) {
					v.set(x, g, z, Blocks.AIR.defaultBlockState());
				}
			}
		}
		return v;
	}

	/** The ground cover a culture's land wears (as {@link PreviewScene}): grass on dirt, sand in the desert, red sand on the mesa. */
	static BlockState ground(Culture c, int depth, boolean wet, int x, int z) {
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
				return depth == 0 ? (h < 4 ? Blocks.RED_SAND : Blocks.COARSE_DIRT).defaultBlockState() : Blocks.TERRACOTTA.defaultBlockState();
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
		return depth == 0 ? Blocks.GRASS_BLOCK.defaultBlockState() : Blocks.DIRT.defaultBlockState();
	}
}
