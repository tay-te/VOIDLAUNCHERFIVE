package dev.voidmc.reel.client;

import dev.voidmc.reel.Reel;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * The land around a point as height grids, for Blender (tools/blender/reel_terrain.py): the solid ground, the
 * surface the camera sees (water, buildings and all, leaves left out) and the canopy (leaves too), plus the map
 * colour of the top block. Blender builds a mesh from them that hides whatever stands behind a hill, so overlays sit
 * in the land rather than on top of the picture; the colours and the gaps between the grids give water, buildings
 * and trees as layers of their own.
 *
 * <p>Where a chunk is loaded its real heightmaps are read; elsewhere the world generator is asked for the height it
 * would build, which is the bare land (no trees, no towns) and costs no chunk generation. Must run on the server thread.
 */
final class TerrainGrab {
	private TerrainGrab() {
	}

	static String grab(ServerLevel level, String name, int cx, int cz, int radius, int step) throws IOException {
		int n = 2 * (radius / step) + 1;
		if ((long) n * n > 4_000_000) {
			throw new IllegalArgumentException("grid of " + n + "x" + n + " is too big: raise step or lower radius");
		}
		int x0 = cx - (n / 2) * step;
		int z0 = cz - (n / 2) * step;
		var source = level.getChunkSource();
		var generator = source.getGenerator();
		var random = source.randomState();
		ByteBuffer ground = ByteBuffer.allocate(n * n * 4).order(ByteOrder.LITTLE_ENDIAN);
		ByteBuffer surface = ByteBuffer.allocate(n * n * 4).order(ByteOrder.LITTLE_ENDIAN);
		ByteBuffer canopy = ByteBuffer.allocate(n * n * 4).order(ByteOrder.LITTLE_ENDIAN);
		byte[] colour = new byte[n * n * 3];
		BlockPos.MutableBlockPos top = new BlockPos.MutableBlockPos();
		int loaded = 0;
		int generated = 0;
		long start = System.nanoTime();
		for (int j = 0; j < n; j++) {
			int z = z0 + j * step;
			for (int i = 0; i < n; i++) {
				int x = x0 + i * step;
				LevelChunk chunk = source.getChunkNow(x >> 4, z >> 4);
				int g;
				int s;
				int c;
				if (chunk != null) {
					// heightmaps give the top block; its top face is one higher
					g = chunk.getHeight(Heightmap.Types.OCEAN_FLOOR, x & 15, z & 15) + 1;
					s = chunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x & 15, z & 15) + 1;
					c = chunk.getHeight(Heightmap.Types.MOTION_BLOCKING, x & 15, z & 15) + 1;
					top.set(x, c - 1, z);
					int rgb = chunk.getBlockState(top).getMapColor(level, top).col;
					int at = (j * n + i) * 3;
					colour[at] = (byte) (rgb >> 16);
					colour[at + 1] = (byte) (rgb >> 8);
					colour[at + 2] = (byte) rgb;
					loaded++;
				} else {
					g = generator.getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, level, random);
					s = generator.getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, level, random);
					c = s;
					generated++;
				}
				ground.putFloat(g);
				surface.putFloat(Math.max(g, s));
				canopy.putFloat(Math.max(Math.max(g, s), c));
			}
		}
		Path out = Reel.renders().resolve("_terrain").resolve(name);
		Files.createDirectories(out);
		Files.write(out.resolve("ground.f32"), ground.array());
		Files.write(out.resolve("surface.f32"), surface.array());
		Files.write(out.resolve("canopy.f32"), canopy.array());
		Files.write(out.resolve("colour.rgb"), colour);
		String meta = String.format(Locale.ROOT,
			"{\"name\":\"%s\",\"x0\":%d,\"z0\":%d,\"step\":%d,\"nx\":%d,\"nz\":%d,\"loaded\":%d,\"generated\":%d,\"seconds\":%.2f,"
				+ "\"format\":\"ground/surface/canopy float32 little-endian, nz rows of nx: row j is z0 + j*step, column i is x0 + i*step; heights are top faces (y); colour.rgb 3 bytes a cell, the top block's map colour (0 where not loaded)\"}",
			Report.escape(name), x0, z0, step, n, n, loaded, generated, (System.nanoTime() - start) / 1e9);
		Files.writeString(out.resolve("meta.json"), meta);
		return "{\"terrain\":\"" + Report.escape(out.toString()) + "\",\"meta\":" + meta + "}";
	}
}
