package dev.voidmc.hearth.gametest.render;

import dev.voidmc.hearth.build.kit.light.Grid;
import dev.voidmc.hearth.build.kit.light.LightField;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Light for night renders: block light by the game's rules ({@link LightField}) and sky light, over a whole preview
 * scene, and the colour a face takes from them on a moonless-ish night: warm pools round lanterns and candles, a faint
 * blue where only the sky reaches, black where neither does.
 */
public final class NightLight {
	private final Grid grid;
	private final LightField block;
	private final LightField sky;

	private NightLight(Grid grid) {
		this.grid = grid;
		this.block = new LightField(grid);
		this.sky = LightField.sky(grid);
	}

	/** Light over a scene (computed once; read per vertex by the renderer). */
	public static NightLight of(Voxels v) {
		int m = 2;
		Grid g = new Grid(new Grid.Source() {
			@Override
			public BlockState at(int x, int y, int z) {
				BlockState s = v.get(x, y, z);
				return s == null ? Blocks.AIR.defaultBlockState() : s;
			}

			@Override
			public boolean placed(int x, int y, int z) {
				return v.get(x, y, z) != null;
			}

			@Override
			public boolean inPlot(int x, int z) {
				return true;
			}

			@Override
			public Direction plusZ() {
				return Direction.SOUTH;
			}
		}, v.minX() - m, v.minY() - m, v.minZ() - m, v.maxX() + m, v.maxY() + m + 4, v.maxZ() + m);
		return new NightLight(g);
	}

	public int block(int x, int y, int z) {
		return this.block.at(x, y, z);
	}

	public int sky(int x, int y, int z) {
		return this.grid.contains(x, y, z) ? this.sky.at(x, y, z) : 15;
	}

	/** Whether a cell is opaque to light (for smooth-light averaging). */
	public boolean opaque(int x, int y, int z) {
		return this.grid.at(x, y, z).getLightDampening() >= 15;
	}

	/** Debug markers drawn into cutaways (unmet light needs): rendered glowing so they show in the dark. */
	public static boolean marker(BlockState s) {
		return s.is(Blocks.CONCRETE.pick(net.minecraft.world.item.DyeColor.RED)) || s.is(Blocks.CONCRETE.pick(net.minecraft.world.item.DyeColor.ORANGE)) || s.is(Blocks.CONCRETE.pick(net.minecraft.world.item.DyeColor.MAGENTA)) || s.is(Blocks.CONCRETE.pick(net.minecraft.world.item.DyeColor.LIME));
	}

	/** The game's light curve: level to brightness, at the "bright" gamma (so pools of light read in a render). */
	private static float curve(float level) {
		float f = level / 15f;
		float b = f / (4f - 3f * f);
		float ng = 1f - (1f - b) * (1f - b) * (1f - b) * (1f - b);
		return ng;
	}

	/** The RGB multiplier for a vertex lit by these (averaged) block and sky levels at night. */
	public static float[] colour(float block, float sky) {
		float bl = curve(block);
		float sk = curve(sky) * 0.11f;
		float r = 0.02f + sk * 0.55f + bl * 1.00f;
		float g = 0.02f + sk * 0.62f + bl * 0.80f;
		float b = 0.035f + sk * 0.95f + bl * 0.52f;
		return new float[]{Math.min(1.1f, r), Math.min(1.1f, g), Math.min(1.1f, b)};
	}
}
