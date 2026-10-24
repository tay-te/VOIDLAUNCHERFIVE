package dev.voidmc.reel.client;

import com.mojang.blaze3d.platform.NativeImage;
import dev.voidmc.reel.Reel;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.BlockStateModelSet;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FluidState;
import org.joml.Vector3fc;

/**
 * A loaded piece of the world as a textured mesh for Blender (tools/blender/reel_world.py): every block's own model
 * faces, culled against their neighbours the way the game culls them, with the game's textures (copied out of its
 * resources, first frame of animated ones) and biome tints as vertex colours. Water and lava are their surfaces, as
 * materials of their own for Blender to shade. Blocks that give light are marked so their textures can glow.
 *
 * <p>How the piece is cut from the world is a {@link Cut}: left open, closed as a box, or carved into a floating island.
 *
 * <p>Written as OBJ in Minecraft's axes less an origin, so Blender's default OBJ import (forward -Z, up Y) puts it in
 * the same space as reel_camera.py's cameras. Runs on the client thread, from what the client has loaded.
 */
final class WorldExport {
	private WorldExport() {
	}

	/** Where the exported piece ends. */
	enum Cut {
		/** faces against unexported neighbours are culled as in the game */
		NONE,
		/** everything outside the box counts as air, so the box comes out closed: its cut sides and floor show their earth and stone */
		BOX,
		/** a round, ragged outline and a rocky underside tapering to a point under the middle: the piece as a floating island */
		ISLAND
	}

	/**
	 * The bare land (no towns, no trees) the world generator would build in the export's square, one row of x per z, a
	 * cell wider than the square on every side. The island's underside follows it, so houses don't print their
	 * footprints on the rock. Must run on the server thread.
	 */
	static int[] bareLand(ServerLevel level, int cx, int cz, int radius) {
		int w = 2 * radius + 3;
		var source = level.getChunkSource();
		int[] ground = new int[w * w];
		for (int j = 0; j < w; j++) {
			for (int i = 0; i < w; i++) {
				ground[j * w + i] = source.getGenerator().getBaseHeight(cx - radius - 1 + i, cz - radius - 1 + j, Heightmap.Types.OCEAN_FLOOR_WG, level,
					source.randomState());
			}
		}
		return ground;
	}

	/**
	 * The lowest block kept in each column of the square (indexed as {@link #bareLand}), Integer.MAX_VALUE where the
	 * column is cut away. The island's underside sits under the lower of the bare land and the real ground (skipping
	 * leaves and water), a few blocks thick at the rim and about 0.85 radius deep in the middle, stepped in 2x2 cells.
	 */
	private static int[] floors(ClientLevel level, Cut cut, int[] bare, int cx, int cz, int radius, int y0, int y1) {
		int w = 2 * radius + 3;
		int[] floor = new int[w * w];
		java.util.Arrays.fill(floor, y0);
		if (cut != Cut.ISLAND) {
			return floor;
		}
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		for (int j = 0; j < w; j++) {
			for (int i = 0; i < w; i++) {
				int x = cx - radius - 1 + i;
				int z = cz - radius - 1 + j;
				double dx = x + 0.5 - cx;
				double dz = z + 0.5 - cz;
				double a = Math.atan2(dz, dx);
				double edge = radius * (0.86 + 0.06 * Math.sin(3 * a + 1.1) + 0.04 * Math.sin(5 * a + 0.4) + 0.025 * Math.sin(11 * a + 2.0))
					+ 1.5 * jag(x >> 1, z >> 1, 0);
				double r = Math.hypot(dx, dz);
				if (r > edge) {
					floor[j * w + i] = Integer.MAX_VALUE;
					continue;
				}
				int real = y0;
				for (int y = y1; y >= y0; y--) {
					BlockState state = level.getBlockState(p.set(x, y, z));
					if (state.is(BlockTags.BLOCKS_MOTION_IN_HEIGHTMAP_NO_LEAVES) && state.getFluidState().isEmpty()) {
						real = y + 1;
						break;
					}
				}
				int ground = Math.min(real, bare[j * w + i]);
				double t = 1 - r / edge;
				double depth = 4 + radius * 0.85 * Math.pow(t, 1.3) * (1 + 0.15 * Math.sin(x * 0.09 + z * 0.05)) + jag(x >> 1, z >> 1, 1) * (1.5 + 5 * t);
				floor[j * w + i] = Math.max(y0, (int) Math.round(ground - Math.max(depth, 3)));
			}
		}
		return floor;
	}

	/** A steady random number in -1..1 for a cell. */
	private static double jag(int x, int z, int salt) {
		return ((Mth.getSeed(x, salt, z) >>> 24) & 1023) / 511.5 - 1;
	}

	private static final class Writer {
		final BufferedWriter obj;
		final Map<String, Boolean> materials = new LinkedHashMap<>();
		final Path textures;
		String current = "";
		long vertices;
		long faces;

		Writer(BufferedWriter obj, Path textures) {
			this.obj = obj;
			this.textures = textures;
		}

		void use(String material) throws IOException {
			if (!material.equals(this.current)) {
				this.obj.write("usemtl " + material + "\n");
				this.current = material;
			}
		}

		void quad(double[][] p, double[][] uv, float r, float g, float b) throws IOException {
			for (int i = 0; i < 4; i++) {
				this.obj.write(String.format(Locale.ROOT, "v %.4f %.4f %.4f %.3f %.3f %.3f%nvt %.5f %.5f%n", p[i][0], p[i][1], p[i][2], r, g, b,
					uv[i][0], uv[i][1]));
			}
			long a = this.vertices + 1;
			this.obj.write(String.format(Locale.ROOT, "f %d/%d %d/%d %d/%d %d/%d%n", a, a, a + 1, a + 1, a + 2, a + 2, a + 3, a + 3));
			this.vertices += 4;
			this.faces++;
		}
	}

	static String export(Minecraft mc, String name, int cx, int cz, int radius, int y0, int y1, int oy, Cut cut, int[] bare) throws IOException {
		ClientLevel level = mc.level;
		if (level == null) {
			throw new IllegalStateException("no world loaded");
		}
		BlockStateModelSet models = mc.getModelManager().getBlockStateModelSet();
		BlockColors colors = mc.getBlockColors();
		Path out = Reel.renders().resolve("_export").resolve(name);
		Path textures = out.resolve("textures");
		Files.createDirectories(textures);
		long start = System.nanoTime();
		int w0 = 2 * radius + 3;
		int[] floor = floors(level, cut, bare, cx, cz, radius, y0, y1);
		List<BlockStateModelPart> parts = new ArrayList<>();
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		BlockPos.MutableBlockPos n = new BlockPos.MutableBlockPos();
		Writer w;
		int missing = 0;
		try (BufferedWriter obj = Files.newBufferedWriter(out.resolve("world.obj"), java.nio.charset.StandardCharsets.UTF_8)) {
			w = new Writer(obj, textures);
			obj.write("# VOID Reel world export: " + name + ", origin " + cx + " " + oy + " " + cz + "\nmtllib world.mtl\n");
			for (int x = cx - radius; x <= cx + radius; x++) {
				for (int z = cz - radius; z <= cz + radius; z++) {
					if (!level.hasChunk(x >> 4, z >> 4)) {
						missing++;
						continue;
					}
					int lowest = floor[(z - cz + radius + 1) * w0 + (x - cx + radius + 1)];
					for (int y = Math.max(y0, lowest); y <= y1; y++) {
						p.set(x, y, z);
						BlockState state = level.getBlockState(p);
						if (state.isAir()) {
							continue;
						}
						double ox = x - cx;
						double oyy = y - oy;
						double oz = z - cz;
						FluidState fluid = state.getFluidState();
						if (!fluid.isEmpty()) {
							n.set(x, y + 1, z);
							if (!level.getFluidState(n).getType().isSame(fluid.getType())) {
								double h = fluid.getHeight(level, p);
								w.use(fluid.is(FluidTags.LAVA) ? "lava" : "water");
								w.materials.putIfAbsent(w.current, false);
								w.quad(new double[][] {{ox, oyy + h, oz}, {ox, oyy + h, oz + 1}, {ox + 1, oyy + h, oz + 1}, {ox + 1, oyy + h, oz}},
									new double[][] {{0, 1}, {0, 0}, {1, 0}, {1, 1}}, 1, 1, 1);
							}
						}
						if (state.getRenderShape() != RenderShape.MODEL) {
							continue;
						}
						BlockStateModel model = models.get(state);
						parts.clear();
						model.collectParts(RandomSource.create(Mth.getSeed(p)), parts);
						boolean glows = state.getLightEmission() > 0;
						for (BlockStateModelPart part : parts) {
							for (Direction d : Direction.values()) {
								n.setWithOffset(p, d);
								boolean outside = cut != Cut.NONE && (Math.abs(n.getX() - cx) > radius || Math.abs(n.getZ() - cz) > radius || n.getY() > y1
									|| n.getY() < floor[(n.getZ() - cz + radius + 1) * w0 + (n.getX() - cx + radius + 1)]);
								BlockState neighbour = outside ? Blocks.AIR.defaultBlockState() : level.getBlockState(n);
								if (!Block.shouldRenderFace(state, neighbour, d)) {
									continue;
								}
								for (BakedQuad quad : part.getQuads(d)) {
									emit(mc, w, level, colors, state, p, quad, ox, oyy, oz, glows);
								}
							}
							for (BakedQuad quad : part.getQuads(null)) {
								emit(mc, w, level, colors, state, p, quad, ox, oyy, oz, glows);
							}
						}
					}
				}
			}
		}
		try (BufferedWriter mtl = Files.newBufferedWriter(out.resolve("world.mtl"))) {
			for (var m : w.materials.entrySet()) {
				String key = m.getKey();
				mtl.write("newmtl " + key + "\n");
				if (key.equals("water")) {
					mtl.write("Kd 0.18 0.32 0.45\nd 0.7\n\n");
				} else if (key.equals("lava")) {
					mtl.write("Kd 1.0 0.45 0.1\nKe 1.0 0.45 0.1\n\n");
				} else {
					mtl.write("Kd 1 1 1\nmap_Kd textures/" + key + ".png\n");
					if (m.getValue()) {
						mtl.write("Ke 1 1 1\nmap_Ke textures/" + key + ".png\n");
					}
					mtl.write("\n");
				}
			}
		}
		String meta = String.format(Locale.ROOT,
			"{\"name\":\"%s\",\"origin\":[%d,%d,%d],\"radius\":%d,\"y\":[%d,%d],\"cut\":\"%s\",\"faces\":%d,\"materials\":%d,\"missingColumns\":%d,\"seconds\":%.1f}",
			Report.escape(name), cx, oy, cz, radius, y0, y1, cut.name().toLowerCase(Locale.ROOT), w.faces, w.materials.size(), missing, (System.nanoTime() - start) / 1e9);
		Files.writeString(out.resolve("meta.json"), meta);
		return "{\"export\":\"" + Report.escape(out.toString()) + "\",\"meta\":" + meta + "}";
	}

	private static void emit(Minecraft mc, Writer w, ClientLevel level, BlockColors colors, BlockState state, BlockPos p, BakedQuad quad, double ox,
		double oy, double oz, boolean glows) throws IOException {
		TextureAtlasSprite sprite = quad.materialInfo().sprite();
		Identifier id = sprite.contents().name();
		String key = (id.getNamespace() + "_" + id.getPath()).replace('/', '_');
		if (!w.materials.containsKey(key)) {
			copyTexture(mc, id, w.textures.resolve(key + ".png"));
			w.materials.put(key, glows);
		} else if (glows && !w.materials.get(key)) {
			w.materials.put(key, true);
		}
		w.use(key);
		float r = 1;
		float g = 1;
		float b = 1;
		int tint = quad.materialInfo().tintIndex();
		if (tint >= 0) {
			BlockTintSource source = colors.getTintSource(state, tint);
			if (source != null) {
				int c = source.colorInWorld(state, level, p);
				r = ((c >> 16) & 255) / 255f;
				g = ((c >> 8) & 255) / 255f;
				b = (c & 255) / 255f;
			}
		}
		double u0 = sprite.getU0();
		double du = sprite.getU1() - u0;
		double v0 = sprite.getV0();
		double dv = sprite.getV1() - v0;
		double[][] pos = new double[4][];
		double[][] uv = new double[4][];
		for (int i = 0; i < 4; i++) {
			Vector3fc v = quad.position(i);
			pos[i] = new double[] {ox + v.x(), oy + v.y(), oz + v.z()};
			long packed = quad.packedUV(i);
			double u = du == 0 ? 0 : (UVPair.unpackU(packed) - u0) / du;
			double vv = dv == 0 ? 0 : (UVPair.unpackV(packed) - v0) / dv;
			uv[i] = new double[] {u, 1 - vv};
		}
		w.quad(pos, uv, r, g, b);
	}

	/** The sprite's own texture from the game's resources; an animated strip is cut to its first frame. */
	private static void copyTexture(Minecraft mc, Identifier sprite, Path to) throws IOException {
		Identifier file = Identifier.fromNamespaceAndPath(sprite.getNamespace(), "textures/" + sprite.getPath() + ".png");
		Optional<Resource> resource = mc.getResourceManager().getResource(file);
		if (resource.isEmpty()) {
			return;
		}
		try (InputStream in = resource.get().open(); NativeImage image = NativeImage.read(in)) {
			int width = image.getWidth();
			if (image.getHeight() <= width) {
				image.writeToFile(to);
				return;
			}
			try (NativeImage frame = new NativeImage(width, width, false)) {
				for (int y = 0; y < width; y++) {
					for (int x = 0; x < width; x++) {
						frame.setPixel(x, y, image.getPixel(x, y));
					}
				}
				frame.writeToFile(to);
			}
		}
	}
}
