package dev.voidmc.expanse.gametest;

import dev.voidmc.expanse.world.terrain.Column;
import dev.voidmc.expanse.world.terrain.TerrainModel;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.stream.IntStream;
import javax.imageio.ImageIO;

/**
 * Renders the Earth terrain model straight to a shaded relief map, without starting the game: the quick
 * way to see what a change to the model does to a whole region.
 *
 * <pre>./gradlew terrainPreview -Pseed=8675309 -Pradius=8192 -Pstep=16 [-Pcx=0 -Pcz=0]</pre>
 *
 * writes build/terrain_preview.png: sea by depth, rivers in bright blue, land coloured by height and lit
 * from the north-west.
 */
public final class TerrainPreview {
	private static final boolean BORDERS = Boolean.getBoolean("expanse.preview.borders");

	public static void main(String[] args) throws Exception {
		long seed = Long.parseLong(args[0]);
		int radius = Integer.parseInt(args[1]);
		int step = Integer.parseInt(args[2]);
		File out = new File(args[3]);
		int cx = args.length > 4 ? Integer.parseInt(args[4]) : 0;
		int cz = args.length > 5 ? Integer.parseInt(args[5]) : 0;
		TerrainModel model = TerrainModel.forSeed(TerrainModel.seedFor(seed));
		if (Boolean.getBoolean("expanse.preview.bench")) {
			// one plate at a time, far apart: what a stronghold or biome search costs per plate
			long all = System.nanoTime();
			IntStream.range(0, 128).parallel().forEach(p -> {
				double angle = p * 2.8;
				double r = 1500 + (p / 128.0) * 21500;
				long t0 = System.nanoTime();
				int bx = (int) (Math.cos(angle) * r);
				int bz = (int) (Math.sin(angle) * r);
				for (int qx = -28; qx <= 28; qx++) {
					for (int qz = -28; qz <= 28; qz++) {
						model.sample(bx + qx * 4, bz + qz * 4);
					}
				}
				long ms = (System.nanoTime() - t0) / 1_000_000;
				if (ms > 1500) {
					System.out.printf("slow sample %d at r=%.0f: %d ms (%d plate builds so far)%n", p, r, ms, TerrainModel.BUILDS.get());
				}
			});
			System.out.printf("128 ring samples: %d ms, %d plate builds%n", (System.nanoTime() - all) / 1_000_000, TerrainModel.BUILDS.get());
			return;
		}
		int size = 2 * radius / step;
		float[] height = new float[size * size];
		byte[] water = new byte[size * size];
		long[] plate = new long[size * size];
		long start = System.nanoTime();
		IntStream.range(0, size).parallel().forEach(j -> {
			for (int i = 0; i < size; i++) {
				Column c = model.sample(cx - radius + i * step, cz - radius + j * step);
				height[i + j * size] = c.height;
				water[i + j * size] = (byte) (c.inChannel() ? 2 : c.height < 63 ? 1 : 0);
				plate[i + j * size] = (long) c.plateX << 32 ^ c.plateZ & 0xFFFFFFFFL;
			}
		});
		long ms = (System.nanoTime() - start) / 1_000_000;
		BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
		float min = Float.MAX_VALUE;
		float max = -Float.MAX_VALUE;
		int land = 0;
		int river = 0;
		for (int j = 0; j < size; j++) {
			for (int i = 0; i < size; i++) {
				int k = i + j * size;
				float h = height[k];
				min = Math.min(min, h);
				max = Math.max(max, h);
				float dx = height[Math.min(size - 1, i + 1) + j * size] - height[Math.max(0, i - 1) + j * size];
				float dz = height[i + Math.min(size - 1, j + 1) * size] - height[i + Math.max(0, j - 1) * size];
				float shade = clamp(1.0F - (dx + dz) / (4.0F * step) * 1.6F, 0.45F, 1.35F);
				int rgb;
				if (water[k] == 2) {
					rgb = 0x2F7FFF;
					river++;
				} else if (water[k] == 1) {
					float depth = clamp((63 - h) / 45.0F, 0, 1);
					rgb = mix(0x4FA3C8, 0x0E2C5C, depth);
				} else {
					land++;
					rgb = landColour(h);
					rgb = scale(rgb, shade);
				}
				if (BORDERS && (i + 1 < size && plate[k + 1] != plate[k] || j + 1 < size && plate[k + size] != plate[k])) {
					rgb = 0xFF2020;
				}
				img.setRGB(i, j, rgb);
			}
		}
		ImageIO.write(img, "png", out);
		System.out.printf("terrain preview %dx%d (%d blocks/px) in %d ms: height %.0f..%.0f, land %.0f%%, river %.2f%% -> %s%n",
			size, size, step, ms, min, max, 100.0 * land / (size * size), 100.0 * river / (size * size), out);
	}

	private static int landColour(float h) {
		float[][] stops = {{63, 0xD8C890}, {66, 0x7FB24E}, {100, 0x4E8A36}, {150, 0x7A8A44}, {200, 0x8A7552}, {250, 0x8C8780}, {300, 0xE8EEF2}};
		for (int s = 1; s < stops.length; s++) {
			if (h < stops[s][0]) {
				float t = (h - stops[s - 1][0]) / (stops[s][0] - stops[s - 1][0]);
				return mix((int) stops[s - 1][1], (int) stops[s][1], clamp(t, 0, 1));
			}
		}
		return (int) stops[stops.length - 1][1];
	}

	private static int mix(int a, int b, float t) {
		int r = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
		int g = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
		int bl = (int) ((a & 255) * (1 - t) + (b & 255) * t);
		return r << 16 | g << 8 | bl;
	}

	private static int scale(int c, float f) {
		int r = Math.min(255, (int) (((c >> 16) & 255) * f));
		int g = Math.min(255, (int) (((c >> 8) & 255) * f));
		int b = Math.min(255, (int) ((c & 255) * f));
		return r << 16 | g << 8 | b;
	}

	private static float clamp(float v, float lo, float hi) {
		return Math.max(lo, Math.min(hi, v));
	}
}
