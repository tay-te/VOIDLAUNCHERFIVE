package dev.voidmc.hearth.gametest.render;

import dev.voidmc.hearth.api.PlotSpec;
import dev.voidmc.hearth.build.draft.Draft;
import java.util.Locale;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * Text sections through a draft, for debugging generators: a vertical cut across the frontage (constant local z) or
 * across the depth (constant local x), one character per block. Stairs show which way they rise.
 */
public final class Slices {
	private Slices() {
	}

	/** A section at local z (x across, y up), or at local x (z across) when {@code alongX} is false. */
	public static String section(Draft d, boolean alongX, int at) {
		int[] b = bounds(d);
		StringBuilder sb = new StringBuilder();
		sb.append(alongX ? "section z=" + at : "section x=" + at).append('\n');
		for (int y = b[3]; y >= b[2]; y--) {
			sb.append(String.format(Locale.ROOT, "%4d ", y));
			int lo = alongX ? b[0] : b[4];
			int hi = alongX ? b[1] : b[5];
			for (int c = lo; c <= hi; c++) {
				BlockState s = alongX ? d.get(c, y, at) : d.get(at, y, c);
				sb.append(glyph(s));
			}
			sb.append('\n');
		}
		return sb.toString();
	}

	private static int[] bounds(Draft d) {
		int[] b = {Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE};
		d.forEach((x, y, z, c) -> {
			b[0] = Math.min(b[0], x);
			b[1] = Math.max(b[1], x);
			b[2] = Math.min(b[2], y);
			b[3] = Math.max(b[3], y);
			b[4] = Math.min(b[4], z);
			b[5] = Math.max(b[5], z);
		});
		return b;
	}

	static char glyph(BlockState s) {
		if (s == null) {
			return ' ';
		}
		if (s.isAir()) {
			return '.';
		}
		String p = BuiltInRegistries.BLOCK.getKey(s.getBlock()).getPath();
		if (s.getBlock() instanceof StairBlock) {
			boolean top = s.getValue(StairBlock.HALF) == Half.TOP;
			return switch (s.getValue(StairBlock.FACING)) {
				case NORTH -> top ? 'n' : 'N';
				case SOUTH -> top ? 's' : 'S';
				case EAST -> top ? 'e' : 'E';
				default -> top ? 'w' : 'W';
			};
		}
		if (s.hasProperty(BlockStateProperties.SLAB_TYPE)) {
			return s.getValue(BlockStateProperties.SLAB_TYPE) == SlabType.TOP ? '‾' : '_';
		}
		if (p.endsWith("_log") || p.endsWith("_wood")) {
			return '#';
		}
		if (p.endsWith("planks")) {
			return '=';
		}
		if (p.contains("glass") || p.endsWith("_pane")) {
			return '|';
		}
		if (p.endsWith("_door")) {
			return 'D';
		}
		if (p.contains("trapdoor")) {
			return 't';
		}
		if (p.contains("fence")) {
			return 'f';
		}
		if (p.contains("leaves")) {
			return '%';
		}
		if (p.contains("stone") || p.contains("cobble") || p.contains("brick")) {
			return 'o';
		}
		return '@';
	}

	/** Prints sections for a spec. */
	public static void print(Draft d, PlotSpec spec) {
		for (int z = 0; z < d.depth(); z += Math.max(1, d.depth() / 3)) {
			System.out.println(section(d, true, z));
		}
		System.out.println(section(d, false, d.frontage() / 2));
	}
}
