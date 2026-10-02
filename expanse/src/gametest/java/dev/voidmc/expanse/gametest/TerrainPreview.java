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
		String slice = System.getProperty("expanse.preview.slice", "");
		if (slice.equals("side")) {
			// a vertical section along x at z = cz, y -64 to 256 (one pixel a block, scaled by step across):
			// sky, land, deepslate below y 0, open space dark, lakes blue, lava orange
			int width = 2 * radius / step;
			int top = 256;
			int bottom = -64;
			BufferedImage img = new BufferedImage(width, top - bottom, BufferedImage.TYPE_INT_RGB);
			var caverns = model.caverns();
			IntStream.range(0, width).parallel().forEach(i -> {
				int x = cx - radius + i * step;
				float ground = model.sample(x, cz).height;
				var info = caverns.info(x, cz);
				float liquid = info.hall ? info.liquid() : -999;
				boolean lava = info.lava && info.lake >= info.pool;
				for (int y = bottom; y < top; y++) {
					int rgb;
					if (y > ground) {
						rgb = y < 63 ? 0x3060C0 : 0xA8C8F0;
					} else if (caverns.inside(x, y, cz) > 0) {
						if (y < -54 || y <= liquid) {
							rgb = y < -54 || lava ? 0xF07020 : 0x2F6FEF;
						} else if (info.inChannel() && y < info.waterTop() && y >= info.bed()) {
							rgb = 0x2F7FFF;
						} else {
							rgb = 0x1C1814;
						}
					} else {
						rgb = y < 0 ? 0x4A4A52 : y > ground - 4 ? 0x6A8A40 : 0x8A8A8A;
					}
					img.setRGB(i, top - 1 - y, rgb);
				}
			});
			ImageIO.write(img, "png", out);
			System.out.printf("side section at z=%d, x %d..%d -> %s%n", cz, cx - radius, cx + radius, out);
			return;
		}
		if (slice.equals("cavebench")) {
			// (caverns) what the cavern model costs per column, against the terrain model's own column
			var caverns = model.caverns();
			int n = 768;
			long t0 = System.nanoTime();
			for (int j = 0; j < n; j++) {
				for (int i = 0; i < n; i++) {
					model.sample(cx + i, cz + j);
				}
			}
			long t1 = System.nanoTime();
			for (int j = 0; j < n; j++) {
				for (int i = 0; i < n; i++) {
					caverns.info(cx + i + n, cz + j);
				}
			}
			long t2 = System.nanoTime();
			for (int j = 0; j < n; j++) {
				for (int i = 0; i < n; i++) {
					model.sample(cx + i + n, cz + j);
				}
			}
			long t3 = System.nanoTime();
			float sink = 0;
			for (int j = 0; j < n; j += 2) {
				for (int i = 0; i < n; i += 2) {
					for (int y = -64; y < 384; y += 2) {
						sink += caverns.fine(cx + i + n, y, cz + j);
					}
				}
			}
			long t4 = System.nanoTime();
			double cols = (double) n * n;
			System.out.printf("terrain %.2f us/col (cold), caverns info %.2f us/col (incl. terrain it pulls), terrain after %.2f us/col; fine density %.1f ns/sample (%s)%n",
				(t1 - t0) / 1000.0 / cols, (t2 - t1) / 1000.0 / cols, (t3 - t2) / 1000.0 / cols, (t4 - t3) / (cols / 4 * 224), sink > 0 ? "" : "-");
			return;
		}
		if (!slice.isEmpty()) {
			// a horizontal cut through the caverns at one height: rock grey, open space dark, river water blue
			boolean plan = slice.equals("plan");
			int y = plan ? 0 : Integer.parseInt(slice);
			int cx0 = args.length > 4 ? Integer.parseInt(args[4]) : 0;
			int cz0 = args.length > 5 ? Integer.parseInt(args[5]) : 0;
			int size = 2 * radius / step;
			BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
			var caverns = model.caverns();
			int[] open = new int[1];
			IntStream.range(0, size).parallel().forEach(j -> {
				for (int i = 0; i < size; i++) {
					int x = cx0 - radius + i * step;
					int z = cz0 - radius + j * step;
					var info = caverns.info(x, z);
					if (plan) {
						// every hall's footprint (shaded by roof height) and every river, from above
						boolean hall = info.hall && info.hallEdge > 0;
						int rgb = 0x8A8A8A;
						if (hall) {
							// the realm floor, hill-shaded from the north-west, its lakes blue (lava orange)
							float floor = info.floor;
							float liquid = info.liquid();
							boolean lava = info.lava && info.lake >= info.pool;
							float east = caverns.info(x + step, z).floor;
							float south = caverns.info(x, z + step).floor;
							double light = 0.62 - 0.35 * ((east - floor) + (south - floor)) / step;
							int tone = (int) Math.max(30, Math.min(235, 60 + (floor + 60) * 1.3));
							int shade = (int) Math.max(0, Math.min(255, tone * light * 1.4));
							rgb = floor < liquid ? (lava ? 0xF07020 : 0x2F6FEF) : shade << 16 | (shade * 4 / 5) << 8 | shade * 3 / 5;
							// (caverns) each realm's floor tinted by its character
							if (floor >= liquid) {
								rgb = mix(rgb, THEME_TINT[info.theme], 0.45F);
							}
							synchronized (open) {
								open[0]++;
							}
						}
						// (caverns) the small spaces: passages by level, chambers, fissures, ways in
						for (int k = 2; k >= 0; k--) {
							var level = info.passages[k];
							if (level.on && level.dist < level.halfWidth) {
								rgb = k == 0 ? 0xC8B070 : k == 1 ? 0x9C7A4A : 0x6A5034;
							}
							if (level.chamber && level.chamberIn > 0) {
								rgb = k == 0 ? 0xD8C890 : k == 1 ? 0xB09060 : 0x80684A;
							}
						}
						if (info.fissure && info.fissureDist < info.fissureHalfWidth) {
							rgb = info.ravine ? 0x30E0E0 : 0x70A0A0;
						}
						if (info.adit && info.aditDist < info.aditHalfWidth) {
							rgb = 0x40FF40;
						}
						if (info.shaft && info.shaftDist < info.shaftRadius + 3) {
							rgb = 0xFF40FF;
						}
						if (info.cenote && info.cenoteDist < info.cenoteRadius) {
							rgb = 0xFFFFFF;
						}
						if (info.tunnel) {
							rgb = 0x5A4632;
						}
						if (info.river && info.riverDist < info.riverHalfWidth + 4) {
							rgb = 0x6E5A44;
						}
						if (info.river && info.riverDist < info.riverHalfWidth) {
							rgb = info.riverFalls ? 0xFF3030 : 0x2F7FFF;
						}
						img.setRGB(i, j, rgb);
						continue;
					}
					float inside = caverns.inside(x, y, z);
					int rgb;
					if (inside > 0 && info.inChannel() && y < info.waterTop() && y >= info.bed()) {
						rgb = 0x2F7FFF;
					} else if (inside > 0) {
						rgb = info.hall ? 0x241C14 : 0x3A2E22;
						synchronized (open) {
							open[0]++;
						}
					} else {
						rgb = 0x8A8A8A;
					}
					img.setRGB(i, j, rgb);
				}
			});
			ImageIO.write(img, "png", out);
			System.out.printf("cavern slice at y=%d: %.1f%% open -> %s%n", y, 100.0 * open[0] / (size * size), out);
			if (plan) {
				// (caverns) the realms in view by character, with the climate of the land over each
				String[] names = {"wilds", "lush", "dripstone", "crystal", "ember", "mere", "frozen", "roots", "fossil", "tidal", "amber", "brimstone"};
				int[] count = new int[names.length];
				StringBuilder each = new StringBuilder();
				for (int hx = Math.floorDiv(cx0 - radius, 640); hx <= Math.floorDiv(cx0 + radius, 640); hx++) {
					for (int hz = Math.floorDiv(cz0 - radius, 640); hz <= Math.floorDiv(cz0 + radius, 640); hz++) {
						var hall = caverns.hall(hx, hz);
						if (hall.exists()) {
							count[hall.theme()]++;
							Column land = model.sample(hall.x(), hall.z());
							each.append(String.format(" %s@%d,%d(r%.0f t%.2f h%.2f c%.0f%s)", names[hall.theme()], hall.x(), hall.z(), hall.radius(), land.temperature, land.humidity, land.coast, hall.cenotes() > 0 ? " cenotes " + hall.cenotes() : ""));
						}
					}
				}
				StringBuilder sum = new StringBuilder("realms:");
				for (int k = 0; k < names.length; k++) {
					sum.append(' ').append(names[k]).append('=').append(count[k]);
				}
				System.out.println(sum);
				if (Boolean.getBoolean("expanse.preview.stats")) {
					System.out.println(each);
				}
			}
			return;
		}
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
		byte[] landform = new byte[size * size];
		long[] plate = new long[size * size];
		long start = System.nanoTime();
		IntStream.range(0, size).parallel().forEach(j -> {
			for (int i = 0; i < size; i++) {
				Column c = model.sample(cx - radius + i * step, cz - radius + j * step);
				height[i + j * size] = c.height;
				water[i + j * size] = (byte) (c.inChannel() ? 2 : c.crater > c.height ? (c.craterLava ? 4 : 3) : c.height < 63 ? 1 : 0);
				landform[i + j * size] = (byte) c.landform;
				plate[i + j * size] = (long) c.plateX << 32 ^ c.plateZ & 0xFFFFFFFFL;
			}
		});
		long ms = (System.nanoTime() - start) / 1_000_000;
		if (Boolean.getBoolean("expanse.preview.stats")) {
			// how much of the land each landform takes, and how much is dry or wet-tropical enough to have them
			int[] counts = new int[8];
			double[] nearest = new double[8];
			String[] where = new String[8];
			java.util.Arrays.fill(nearest, Double.MAX_VALUE);
			int[] climate = new int[3];
			int landCount = 0;
			for (int j = 0; j < size; j += 2) {
				for (int i = 0; i < size; i += 2) {
					Column c = model.sample(cx - radius + i * step, cz - radius + j * step);
					if (c.height <= 64) {
						continue;
					}
					landCount++;
					counts[c.landform]++;
					int px = cx - radius + i * step;
					int pz = cz - radius + j * step;
					double dist = Math.hypot(px, pz);
					if (c.landform > 0 && dist < nearest[c.landform]) {
						nearest[c.landform] = dist;
						where[c.landform] = px + "," + pz;
					}
					climate[0] += c.humidity < -0.1F && c.temperature > 0.2F ? 1 : 0;
					climate[1] += c.humidity < -0.3F && c.temperature > 0.15F ? 1 : 0;
					climate[2] += c.humidity > 0.25F && c.temperature > 0.45F ? 1 : 0;
				}
			}
			System.out.printf("land %d: volcanic %.2f%%, canyon %.2f%%, salt %.2f%%, tepui %.2f%%, fjord %.2f%% | arid %.1f%%, very arid %.1f%%, wet tropics %.1f%%%n",
				landCount, 100.0 * counts[1] / landCount, 100.0 * counts[2] / landCount, 100.0 * counts[3] / landCount, 100.0 * counts[4] / landCount,
				100.0 * counts[5] / landCount, 100.0 * climate[0] / landCount, 100.0 * climate[1] / landCount, 100.0 * climate[2] / landCount);
			for (int k = 1; k <= 5; k++) {
				System.out.printf("  landform %d nearest the origin: %s (%.0f blocks)%n", k, where[k], nearest[k]);
			}
		}
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
				} else if (water[k] >= 3) {
					rgb = water[k] == 4 ? 0xFF6A10 : 0x3A9FD0;
				} else {
					land++;
					rgb = landColour(h);
					// landforms tinted: volcanoes ash-dark
					if (landform[k] == 1) {
						rgb = mix(rgb, 0x3A3438, 0.55F);
					} else if (landform[k] == 2) {
						rgb = mix(rgb, 0xC0602A, 0.6F);
					} else if (landform[k] == 3) {
						rgb = mix(rgb, 0xF4F1EA, 0.8F);
					} else if (landform[k] == 4) {
						rgb = mix(rgb, 0x1E5A2A, 0.55F);
					} else if (landform[k] == 5) {
						rgb = mix(rgb, 0x8EA4B8, 0.5F);
					}
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

	/** (caverns) A tint for each realm character, by CavernModel theme number. */
	private static final int[] THEME_TINT = {0xC03030, 0x40C040, 0xC08040, 0xA060E0, 0xFF5000, 0x3070FF, 0xE0F8FF, 0x6A8A20, 0xE8D090, 0x20C0B0,
		0xFFA020, 0xE0E020};

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
