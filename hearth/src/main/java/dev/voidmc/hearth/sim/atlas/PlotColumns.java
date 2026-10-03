package dev.voidmc.hearth.sim.atlas;

import dev.voidmc.hearth.api.PlotSpec;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.world.level.ChunkPos;

/**
 * The ground a plot takes, column by column: its footprint, or, for long thin plots that carry a polyline (roads,
 * stairs, bridges, walkways, docks: {@code points} and {@code width} in their params), the strip along the line rather
 * than its whole bounding box, which for a diagonal road would claim a field of ground it never touches.
 */
public final class PlotColumns {
	private PlotColumns() {
	}

	@FunctionalInterface
	public interface ColumnSink {
		void accept(int x, int z);
	}

	/** Whether the spec is a line (it has a polyline in its params). */
	public static boolean isLine(PlotSpec spec) {
		return spec.params().getIntArray("points").map(p -> p.length >= 6).orElse(false);
	}

	public static void forEach(PlotSpec spec, int margin, ColumnSink sink) {
		int[] pts = spec.params().getIntArray("points").orElse(null);
		if (pts != null && pts.length >= 6) {
			int width = Math.max(1, spec.params().getIntOr("width", 3));
			int r = width / 2 + margin;
			for (int i = 0; i + 5 < pts.length; i += 3) {
				int x0 = pts[i];
				int z0 = pts[i + 2];
				int x1 = pts[i + 3];
				int z1 = pts[i + 5];
				int len = Math.max(1, Math.max(Math.abs(x1 - x0), Math.abs(z1 - z0)));
				for (int d = 0; d <= len; d++) {
					int x = Math.round(x0 + (x1 - x0) * d / (float) len);
					int z = Math.round(z0 + (z1 - z0) * d / (float) len);
					for (int dx = -r; dx <= r; dx++) {
						for (int dz = -r; dz <= r; dz++) {
							sink.accept(x + dx, z + dz);
						}
					}
				}
			}
			return;
		}
		int ox = spec.origin().getX() - margin;
		int oz = spec.origin().getZ() - margin;
		for (int dx = 0; dx < spec.sizeX() + 2 * margin; dx++) {
			for (int dz = 0; dz < spec.sizeZ() + 2 * margin; dz++) {
				sink.accept(ox + dx, oz + dz);
			}
		}
	}

	/** Marks the plot's columns in per-chunk 256-bit masks. */
	public static void mark(PlotSpec spec, int margin, Long2ObjectMap<long[]> masks) {
		forEach(spec, margin, (x, z) -> {
			long[] mask = masks.computeIfAbsent(ChunkPos.pack(x >> 4, z >> 4), k -> new long[4]);
			int bit = (z & 15) << 4 | x & 15;
			mask[bit >> 6] |= 1L << (bit & 63);
		});
	}

	public static Long2ObjectMap<long[]> masks() {
		return new Long2ObjectOpenHashMap<>();
	}
}
