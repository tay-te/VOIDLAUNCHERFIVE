package dev.voidmc.atlas.core;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;

/**
 * A made-up landscape (sea, beach, river, plains, forest, mountains with snow, red dunes) drawn with the palette, for
 * judging the look against the design without the game: {@code ./gradlew test --tests '*SyntheticPreview*'} writes
 * {@code build/test-images/synthetic.png}.
 */
public final class SyntheticPreview {
	static final int SAND_RED = 9;

	public static void main(String[] args) throws IOException {
		int w = 900;
		int h = 588;
		int scale = args.length > 0 ? Integer.parseInt(args[0]) : 1;
		RegionRaw[] around = new RegionRaw[9];
		RegionRaw raw = new RegionRaw(0, 0);
		around[4] = raw;
		for (int z = 0; z < Coords.REGION_SIZE; z++) {
			for (int x = 0; x < Coords.REGION_SIZE; x++) {
				fill(raw, x, z);
			}
		}
		TileRenderer.Looks looks = new TileRenderer.Looks() {
			@Override
			public Material material(int block) {
				return block == SAND_RED ? Material.SAND : Scenes.LOOKS.material(block);
			}

			@Override
			public int colour(int block, int biome) {
				return block == SAND_RED ? 0xBE6621 : Scenes.LOOKS.colour(block, biome);
			}

			@Override
			public int water(int biome) {
				return 0x3F76E4;
			}
		};
		RegionImage image = new RegionImage(0, 0, Palette.VERSION);
		new TileRenderer(looks).render(around, image, 0, 0, 512, 512, TileRenderer.Mode.SURFACE);
		int[] px = image.level(0);
		// the 512 region, stretched to the design's 900 x 588 map at the design's zoom (about 1.75 px a block)
		BufferedImage img = new BufferedImage(w * scale, h * scale, BufferedImage.TYPE_INT_RGB);
		double k = 512.0 / w;
		for (int y = 0; y < h * scale; y++) {
			for (int x = 0; x < w * scale; x++) {
				int bx = (int) (x / (double) scale * k);
				int bz = (int) (y / (double) scale * k * 0.98);
				int p = px[bz * 512 + bx];
				img.setRGB(x, y, Palette.shows(p >>> 24, bx, bz) ? p & 0xFFFFFF : Palette.UNEXPLORED);
			}
		}
		Path out = Path.of(System.getProperty("atlas.test.out", "build/test-images"), "synthetic.png");
		Files.createDirectories(out.getParent());
		ImageIO.write(img, "png", out.toFile());
		System.out.println("wrote " + out);
	}

	private static double noise(double x, double z) {
		int x0 = (int) Math.floor(x);
		int z0 = (int) Math.floor(z);
		double fx = x - x0, fz = z - z0;
		double sx = fx * fx * (3 - 2 * fx), sz = fz * fz * (3 - 2 * fz);
		double a = Scenes.hash(x0, z0) / (double) Integer.MAX_VALUE * 2 - 1;
		double b = Scenes.hash(x0 + 1, z0) / (double) Integer.MAX_VALUE * 2 - 1;
		double c = Scenes.hash(x0, z0 + 1) / (double) Integer.MAX_VALUE * 2 - 1;
		double d = Scenes.hash(x0 + 1, z0 + 1) / (double) Integer.MAX_VALUE * 2 - 1;
		return (a + (b - a) * sx) + ((c + (d - c) * sx) - (a + (b - a) * sx)) * sz;
	}

	private static double fbm(double x, double z) {
		return noise(x, z) * 0.6 + noise(x * 2.1 + 5, z * 2.1 - 3) * 0.28 + noise(x * 4.3 - 9, z * 4.3 + 2) * 0.12;
	}

	static void fill(RegionRaw raw, int x, int z) {
		// unexplored corners, as in the design
		double ex = (x - 256) / 300.0, ez = (z - 256) / 250.0;
		if (ex * ex + ez * ez + fbm(x * 0.02, z * 0.02) * 0.25 > 1.0) {
			return;
		}
		// land rises from a sea in the west to mountains in the north-east, dunes in the south-east
		double base = (x - 70) * 0.22 + fbm(x * 0.008, z * 0.008) * 30 + fbm(x * 0.03, z * 0.03) * 4;
		double mountain = Math.max(0, (x - 300) * 0.5 + (230 - z) * 0.45) + fbm(x * 0.012 + 40, z * 0.012) * 25;
		double h = 58 + base * 0.35 + Math.max(0, mountain) * 0.55;
		boolean dunes = z > 360 && x > 250;
		if (dunes) {
			h = 66 + Math.abs(Math.sin(x * 0.11 + Math.sin(z * 0.05) * 2)) * 4 + fbm(x * 0.02, z * 0.02) * 3;
		}
		int y = (int) Math.round(h);
		// a river from the mountains to the sea, and a lake
		double riverZ = 180 + Math.sin(x * 0.012) * 40 + x * 0.05;
		boolean river = Math.abs(z - riverZ) < 1.8 && x > 60 && x < 420;
		boolean lake = Math.hypot((x - 230) * 1.2, z - 330) < 32 + fbm(x * 0.05, z * 0.05) * 6;
		if (river && y > 62) {
			raw.set(x, z, Math.max(63, y - 1), Scenes.GRAVEL, 2, Scenes.PLAINS, RegionRaw.KNOWN);
		} else if (lake) {
			raw.set(x, z, 64, Scenes.SAND, Math.max(1, (int) (6 - Math.hypot((x - 230) * 1.2, z - 330) / 6)), Scenes.PLAINS, RegionRaw.KNOWN);
		} else if (y < 62) {
			raw.set(x, z, 62, y < 52 ? Scenes.GRAVEL : Scenes.SAND, 62 - y, Scenes.PLAINS, RegionRaw.KNOWN);
		} else if (y <= 64 && !dunes) {
			raw.set(x, z, y, Scenes.SAND, 0, Scenes.PLAINS, RegionRaw.KNOWN);
		} else if (dunes) {
			raw.set(x, z, y, SAND_RED, 0, Scenes.PLAINS, RegionRaw.KNOWN);
		} else if (y > 150) {
			raw.set(x, z, y, Scenes.SNOW, 0, Scenes.PLAINS, RegionRaw.KNOWN);
		} else if (y > 108) {
			raw.set(x, z, y, Scenes.STONE, 0, Scenes.PLAINS, RegionRaw.KNOWN);
		} else if (fbm(x * 0.015 + 70, z * 0.015) > 0.12 || (x > 190 && x < 320 && z > 250 && z < 380)) {
			int crown = y + 4 + Scenes.hash(x / 2, z / 2) % 4;
			raw.set(x, z, crown, y > 90 ? Scenes.SPRUCE : Scenes.LEAVES, 0, y > 90 ? Scenes.TAIGA : Scenes.PLAINS, RegionRaw.KNOWN);
		} else {
			raw.set(x, z, y, Scenes.GRASS, 0, Scenes.PLAINS, RegionRaw.KNOWN);
		}
	}
}
