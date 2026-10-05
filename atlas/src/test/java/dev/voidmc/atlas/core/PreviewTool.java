package dev.voidmc.atlas.core;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;

/**
 * Draws a real map offline, from a world's Atlas folder, with today's palette: for tuning the look without starting
 * the game. The block colours come from the {@code looks.tsv} a game run with {@code -Datlas.dumpLooks=true} (the tour)
 * leaves in the world's folder.
 *
 * <pre>./gradlew atlasPreview -Pworld=build/run/atlasTour/atlas/sp/world -Pdim=minecraft/overworld -Players=surface \
 *     -Pcx=0 -Pcz=0 -Pw=1024 -Ph=640 -Pscale=2 -Pout=build/preview.png</pre>
 */
public final class PreviewTool {
	public static void main(String[] args) throws IOException {
		Path world = Path.of(args[0]);
		String dim = args[1];
		String layer = args[2];
		int cx = Integer.parseInt(args[3]);
		int cz = Integer.parseInt(args[4]);
		int w = Integer.parseInt(args[5]);
		int h = Integer.parseInt(args[6]);
		int scale = Integer.parseInt(args[7]);
		Path out = Path.of(args[8]);
		NamePalette blocks = NamePalette.load(world.resolve("blocks.txt"));
		NamePalette biomes = NamePalette.load(world.resolve("biomes.txt"));
		TileRenderer.Looks looks = looks(world.resolve("looks.tsv"), blocks, biomes);
		RegionStore store = new RegionStore(world.resolve(dim), System.err::println);
		TileRenderer renderer = new TileRenderer(looks);
		TileRenderer.Mode mode = layer.equals("surface") ? TileRenderer.Mode.SURFACE : TileRenderer.Mode.CAVE;
		int x0 = cx - w / 2;
		int z0 = cz - h / 2;
		BufferedImage img = new BufferedImage(w * scale, h * scale, BufferedImage.TYPE_INT_RGB);
		Map<Long, RegionRaw> raws = new HashMap<>();
		Map<Long, int[]> drawn = new HashMap<>();
		for (int z = 0; z < h; z++) {
			for (int x = 0; x < w; x++) {
				int bx = x0 + x;
				int bz = z0 + z;
				int rx = Coords.region(bx);
				int rz = Coords.region(bz);
				long key = Coords.pack(rx, rz);
				int[] pixels = drawn.computeIfAbsent(key, k -> {
					RegionRaw[] around = new RegionRaw[9];
					for (int dz = -1; dz <= 1; dz++) {
						for (int dx = -1; dx <= 1; dx++) {
							int nx = rx + dx;
							int nz = rz + dz;
							around[(dz + 1) * 3 + dx + 1] = raws.computeIfAbsent(Coords.pack(nx, nz), n -> store.loadRaw(layer, nx, nz));
						}
					}
					if (around[4] == null) {
						return null;
					}
					RegionImage image = new RegionImage(rx, rz, Palette.VERSION);
					renderer.render(around, image, 0, 0, Coords.REGION_SIZE, Coords.REGION_SIZE, mode);
					return image.level(0);
				});
				int rgb = Palette.UNEXPLORED;
				if (pixels != null) {
					int p = pixels[Coords.inRegion(bz) * Coords.REGION_SIZE + Coords.inRegion(bx)];
					if (Palette.shows(p >>> 24, bx, bz)) {
						rgb = p & 0xFFFFFF;
					}
				}
				for (int sy = 0; sy < scale; sy++) {
					for (int sx = 0; sx < scale; sx++) {
						img.setRGB(x * scale + sx, z * scale + sy, rgb);
					}
				}
			}
		}
		Files.createDirectories(out.toAbsolutePath().getParent());
		ImageIO.write(img, "png", out.toFile());
		System.out.println("wrote " + out + " (" + drawn.values().stream().filter(p -> p != null).count() + " regions)");
	}

	/** Looks from a dump: per block its material, and its colour per biome (or one colour for every biome). */
	static TileRenderer.Looks looks(Path tsv, NamePalette blocks, NamePalette biomes) throws IOException {
		Map<String, Material> materials = new HashMap<>();
		Map<String, Integer> colours = new HashMap<>();
		Map<String, Integer> water = new HashMap<>();
		List<String> lines = Files.readAllLines(tsv);
		for (String line : lines) {
			String[] f = line.split("\t");
			if (f[0].equals("water")) {
				water.put(f[1], Integer.parseInt(f[2], 16));
			} else if (f.length >= 4) {
				materials.put(f[0], Material.valueOf(f[2]));
				colours.put(f[0] + "|" + f[1], Integer.parseInt(f[3], 16));
				colours.putIfAbsent(f[0] + "|*", Integer.parseInt(f[3], 16));
			}
		}
		return new TileRenderer.Looks() {
			@Override
			public Material material(int block) {
				String name = blocks.name(block);
				return name == null ? Material.OTHER : materials.getOrDefault(name, Material.OTHER);
			}

			@Override
			public int colour(int block, int biome) {
				String name = blocks.name(block);
				Integer c = colours.get(name + "|" + biomes.name(biome));
				if (c == null) {
					c = colours.get(name + "|*");
				}
				return c == null ? 0x808080 : c;
			}

			@Override
			public int water(int biome) {
				return water.getOrDefault(biomes.name(biome), 0x3F76E4);
			}
		};
	}
}
