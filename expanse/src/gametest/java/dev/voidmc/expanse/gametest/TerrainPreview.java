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
	private static final boolean COUNTRIES = Boolean.getBoolean("expanse.preview.countries");
	private static final boolean WOODS = Boolean.getBoolean("expanse.preview.woods");

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
		if (slice.equals("far")) {
			// (-Pslice=far) the far view's coarse columns against the fine ones averaged over each cell (step = cell)
			int cells = 2 * radius / step;
			long t0 = System.nanoTime();
			TerrainModel.FarColumn far = new TerrainModel.FarColumn();
			for (int j = 0; j < cells; j++) {
				for (int i = 0; i < cells; i++) {
					model.sampleFar(cx - radius + i * step + step / 2, cz - radius + j * step + step / 2, far);
				}
			}
			long cold = System.nanoTime() - t0;
			t0 = System.nanoTime();
			for (int j = 0; j < cells; j++) {
				for (int i = 0; i < cells; i++) {
					model.sampleFar(cx - radius + i * step + step / 2, cz - radius + j * step + step / 2, far);
				}
			}
			long warm = System.nanoTime() - t0;
			double sumAbs = 0;
			double sumSigned = 0;
			int over10 = 0;
			int n = 0;
			int biomeKey = 0;
			for (int j = 0; j < cells; j += 2) {
				for (int i = 0; i < cells; i += 2) {
					int x0 = cx - radius + i * step;
					int z0 = cz - radius + j * step;
					double mean = 0;
					int k = 0;
					for (int u = 0; u < step; u += step / 8) {
						for (int v = 0; v < step; v += step / 8) {
							mean += Math.max(63, model.sample(x0 + u, z0 + v).height);
							k++;
						}
					}
					mean /= k;
					model.sampleFar(x0 + step / 2, z0 + step / 2, far);
					double diff = Math.max(63, far.height) - mean;
					sumAbs += Math.abs(diff);
					sumSigned += diff;
					over10 += Math.abs(diff) > 10 ? 1 : 0;
					n++;
				}
			}
			System.out.printf("far: %d cells of %d: cold %.1f s, warm %.2f us/sample; vs fine mean: |diff| %.1f, bias %+.1f, %.0f%% over 10 blocks%n",
				cells * cells, step, cold / 1e9, warm / 1e3 / (cells * cells), sumAbs / n, sumSigned / n, 100.0 * over10 / n);
			return;
		}
		if (slice.equals("vistas")) {
			// (-Pslice=vistas) for each country, an overlook well inside it in temperate country, and the way to look
			// that shows most of it (the most relief and most of the country along the line of sight), as
			// -Plod.show.at viewpoints for the VOID LOD showcase
			String[] names = {"", "heart", "rock", "volc", "lakes", "gorge", "oldf"};
			StringBuilder at = new StringBuilder();
			for (int kind = 1; kind <= 6; kind++) {
				double best = -1e9;
				int bx = 0;
				int bz = 0;
				for (int x = cx - radius; x <= cx + radius; x += step) {
					for (int z = cz - radius; z <= cz + radius; z += step) {
						Column c = model.sample(x, z);
						if (c.country != kind || c.countryStrength < 0.9F || c.height < 66 || c.temperature < (kind == 4 ? -0.9F : -0.35F) || c.temperature > 0.5F
							|| c.humidity < (kind == 4 ? -0.5F : -0.15F) || c.nearRiver() && c.riverDist < c.riverHalfWidth + 8) {
							continue;
						}
						float h = c.height;
						float around = 0;
						for (int k = 0; k < 8; k++) {
							around += model.sample(x + (int) (300 * Math.cos(k * Math.PI / 4)), z + (int) (300 * Math.sin(k * Math.PI / 4))).height;
						}
						// a rise over the country round it, not a peak or the top of a tower
						double score = Math.min(h - around / 8, 35) - 0.6 * Math.max(0, h - 150) - 0.002 * Math.hypot(x - cx, z - cz);
						if (score > best) {
							best = score;
							bx = x;
							bz = z;
						}
					}
				}
				if (best < -1e8) {
					System.out.println("vista " + names[kind] + ": none found");
					continue;
				}
				float ground = model.sample(bx, bz).height;
				int yaw = 0;
				double bestView = -1e9;
				for (int y = 0; y < 360; y += 30) {
					double dx = -Math.sin(Math.toRadians(y));
					double dz = Math.cos(Math.toRadians(y));
					double inside = 0;
					float lo = Float.MAX_VALUE;
					float hi = -Float.MAX_VALUE;
					for (int d = 200; d <= 2600; d += 100) {
						Column c = model.sample(bx + (int) (dx * d), bz + (int) (dz * d));
						inside += c.country == kind ? c.countryStrength : 0;
						lo = Math.min(lo, c.height);
						hi = Math.max(hi, c.height);
					}
					double view = inside * 4 + Math.min(80, hi - lo);
					if (view > bestView) {
						bestView = view;
						yaw = y;
					}
				}
				int camY = Math.round(ground) + 60;   // over the tallest canopy (the redwoods)
				System.out.printf("vista %s: %d,%d ground %.0f prominence %.0f yaw %d%n", names[kind], bx, bz, ground, best, yaw);
				at.append(at.isEmpty() ? "" : ";").append(String.format("%s:%d,%d,%d,%d,7,70", names[kind], bx, camY, bz, yaw));
				if (kind == 1) {
					// and from the same rise, the great range: toward the highest ground 8 to 40 km off
					float top = -1;
					double tx = 0;
					double tz = 0;
					for (int r = 8000; r <= 40000; r += 400) {
						for (int k = 0; k < 240; k++) {
							double px = bx + r * Math.cos(k * Math.PI / 120);
							double pz = bz + r * Math.sin(k * Math.PI / 120);
							float h = model.sample((int) px, (int) pz).height;
							// (what looks tallest from here: the angle it stands above the eye, the earth's curve aside)
							float look = (h - camY) / r;
							if (h > 300 && look > top) {
								top = look;
								tx = px;
								tz = pz;
							}
						}
					}
					int toward = (int) Math.round(Math.toDegrees(Math.atan2(-(tx - bx), tz - bz)));
					System.out.printf("vista range: toward %.0f,%.0f (%.0f high, %.0f km)%n", tx, tz, model.sample((int) tx, (int) tz).height, Math.hypot(tx - bx, tz - bz) / 1000);
					at.append(String.format(";range:%d,%d,%d,%d,2,60", bx, camY + 20, bz, toward));
				}
			}
			// close views: down a gorge (a river deep in the plateau, looking the way the walls stay highest), among
			// the volcanic hills (in a vale, looking at the nearest big hill), inside a rock town (a corridor floor)
			for (String close : new String[]{"gorgein", "volcnear", "rockin"}) {
				int kind = close.equals("gorgein") ? 5 : close.equals("volcnear") ? 3 : 2;
				double best = -1e9;
				int bx = 0;
				int bz = 0;
				for (int x = cx - radius; x <= cx + radius; x += step / 2) {
					for (int z = cz - radius; z <= cz + radius; z += step / 2) {
						Column c = model.sample(x, z);
						if (c.country != kind || c.countryStrength < 0.8F || c.temperature < -0.35F || c.temperature > 0.5F || c.humidity < -0.15F) {
							continue;
						}
						double score;
						if (kind == 5) {
							if (!c.inChannel() || c.riverHalfWidth < 2.5F) {
								continue;
							}
							score = model.sample(x + 40, z).height + model.sample(x - 40, z).height + model.sample(x, z + 40).height
								+ model.sample(x, z - 40).height - 4 * c.height;
						} else if (kind == 3) {
							float peak = 0;
							for (int k = 0; k < 8; k++) {
								peak = Math.max(peak, model.sample(x + (int) (500 * Math.cos(k * Math.PI / 4)), z + (int) (500 * Math.sin(k * Math.PI / 4))).height);
							}
							score = peak - c.height - (c.nearRiver() && c.riverDist < c.riverHalfWidth + 6 ? 1000 : 0);
						} else {
							float around = 0;
							for (int k = 0; k < 8; k++) {
								around += model.sample(x + (int) (10 * Math.cos(k * Math.PI / 4)), z + (int) (10 * Math.sin(k * Math.PI / 4))).height;
							}
							score = around / 8 - c.height;
						}
						if (score > best) {
							best = score;
							bx = x;
							bz = z;
						}
					}
				}
				Column c = model.sample(bx, bz);
				float ground = c.inChannel() ? c.riverWater : c.height;
				int yaw = 0;
				double bestView = -1e9;
				for (int y = 0; y < 360; y += 15) {
					double dx = -Math.sin(Math.toRadians(y));
					double dz = Math.cos(Math.toRadians(y));
					double view = 0;
					for (int d = 30; d <= (kind == 3 ? 1200 : 300); d += 15) {
						float h = model.sample(bx + (int) (dx * d), bz + (int) (dz * d)).height;
						view += kind == 5 ? -Math.abs(h - ground) : h;   // a gorge: along its floor; a hill: at it
					}
					if (view > bestView) {
						bestView = view;
						yaw = y;
					}
				}
				int camY = Math.round(ground) + (kind == 3 ? 40 : kind == 5 ? 6 : 3);
				int pitch = kind == 2 ? -12 : kind == 5 ? -2 : 2;
				System.out.printf("vista %s: %d,%d ground %.0f score %.0f yaw %d%n", close, bx, bz, ground, best, yaw);
				at.append(";").append(String.format("%s:%d,%d,%d,%d,%d,70", close, bx, camY, bz, yaw, pitch));
			}
			System.out.println("lod.show.at=" + at);
			return;
		}
		if (slice.equals("probe")) {
			// the columns along x at z = cz, every step blocks: what the model says about each
			for (int x = cx - radius; x <= cx + radius; x += step) {
				Column c = model.sample(x, cz);
				System.out.printf("%d,%d h %.1f landform %d rawT %.3f hum %.3f uplift %.2f coast %.0f plate %d,%d river %.0f%n", x, cz, c.height, c.landform,
					c.temperature - c.lapse, c.humidity, c.uplift, c.coast, c.plateX, c.plateZ, c.nearRiver() ? c.riverDist : -1);
			}
			return;
		}
		if (slice.equals("profile")) {
			// heights along the slope through (cx, cz), every 4 blocks from 64 downhill to 64 uphill; ~ marks a river channel
			float gx = model.sample(cx + 2, cz).height - model.sample(cx - 2, cz).height;
			float gz = model.sample(cx, cz + 2).height - model.sample(cx, cz - 2).height;
			double len = Math.max(1.0E-6, Math.hypot(gx, gz));
			StringBuilder sb = new StringBuilder();
			for (int r = -64; r <= 64; r += 4) {
				Column c = model.sample((int) Math.round(cx + gx / len * r), (int) Math.round(cz + gz / len * r));
				sb.append(Math.round(c.height)).append(c.inChannel() ? "~" : "").append(c.nearRiver() ? "(" + Math.round(c.riverDist) + ")" : "")
					.append(r == 0 ? " | " : " ");
			}
			System.out.println(cx + "," + cz + ": " + sb);
			return;
		}
		if (slice.equals("fronts")) {
			fronts(model, radius, step, cx, cz);
			return;
		}
		if (slice.equals("walls")) {
			walls(model, radius, step, cx, cz, out);
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
			int[] systems = new int[5];
			java.util.List<java.util.List<String>> where = new java.util.ArrayList<>();
			for (int k = 0; k < 5; k++) {
				where.add(new java.util.ArrayList<>());
			}
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
						// (caverns) the cave systems of their own
						if (info.maze && (info.mazeDist < info.mazeHalfWidth || info.mazeRoom > 0)) {
							rgb = 0xB060FF;
						}
						if (info.rift && info.riftDist < info.riftHalfWidth) {
							rgb = 0x40C0FF;
						}
						if (info.tube && info.tubeDist < info.tubeHalfWidth) {
							rgb = info.skylight && info.skylightDist < info.skylightRadius ? 0xFFFF40 : 0xFF5A1E;
						}
						if (info.geode && Math.hypot(x - info.geodeX, z - info.geodeZ) < info.geodeA) {
							rgb = 0xFF40C0;
						}
						if (info.abyss && info.abyssDist < info.abyssRadius) {
							rgb = 0x000000;
						}
						if (info.systems) {
							synchronized (systems) {
								boolean[] here = {info.tube && info.tubeDist < 1.5F, info.maze && info.mazeDist < info.mazeHalfWidth,
									info.abyss && info.abyssDist < 4, info.rift && info.riftDist < 1.5F && Math.abs(info.riftAlong) < 20,
									info.geode && Math.hypot(x - info.geodeX, z - info.geodeZ) < 4};
								for (int k = 0; k < 5; k++) {
									if (here[k]) {
										systems[k]++;
										boolean far = true;
										for (String w : where.get(k)) {
											String[] xz = w.split(",");
											far &= Math.hypot(Integer.parseInt(xz[0]) - x, Integer.parseInt(xz[1]) - z) > 400;
										}
										if (far && where.get(k).size() < 5) {
											where.get(k).add(x + "," + z);
										}
									}
								}
							}
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
				System.out.printf("systems (pixels): lava tubes %d, mazes %d, abysses %d, rifts %d, geodes %d%n", systems[0], systems[1], systems[2],
					systems[3], systems[4]);
				String[] names = {"tube", "maze", "abyss", "rift", "geode"};
				for (int k = 0; k < 5; k++) {
					System.out.println("  " + names[k] + " at " + where.get(k));
				}
			}
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
		byte[] country = new byte[size * size];
		float[] strength = new float[size * size];
		float[] woods = new float[size * size];
		long start = System.nanoTime();
		IntStream.range(0, size).parallel().forEach(j -> {
			for (int i = 0; i < size; i++) {
				Column c = model.sample(cx - radius + i * step, cz - radius + j * step);
				height[i + j * size] = c.height;
				water[i + j * size] = (byte) (c.inChannel() ? 2 : c.crater > c.height ? (c.craterLava ? 4 : 3) : c.lake > c.height && c.lakeEdge > 0 ? 3 : c.height < 63 ? 1 : 0);
				landform[i + j * size] = (byte) c.landform;
				plate[i + j * size] = (long) c.plateX << 32 ^ c.plateZ & 0xFFFFFFFFL;
				country[i + j * size] = (byte) c.country;
				strength[i + j * size] = c.countryStrength;
				if (WOODS) {
					woods[i + j * size] = model.woodland(cx - radius + i * step, cz - radius + j * step);
				}
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
					// (-Pcountries) the countries tinted: heartland gold, rock city ochre, volcanic hills plum,
					// lake district teal, gorges pale grey, old forest deep green
					if (COUNTRIES && country[k] > 0) {
						int[] tint = {0, 0xE8C860, 0xC07830, 0x7A3A8A, 0x30B0C0, 0xE0E0E0, 0x1A4A20};
						rgb = mix(rgb, tint[country[k]], 0.5F * strength[k]);
					}
					if (WOODS) {
						// (-Pwoods) the woods dark green, the open country pale
						rgb = mix(rgb, woods[k] > 0.5F ? 0x1F4A1C : 0xC8D890, 0.55F);
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

	/**
	 * Where flat ground meets a steep rise ("walls"): every column whose slope over four blocks passes 1.5 (56
	 * degrees) and that has a flat foot (slope under 0.12) within 24 blocks below it, sorted by what makes it
	 * there. Prints the share of each cause and a few places of each; writes a relief map with the walls marked.
	 */
	private static void walls(TerrainModel model, int radius, int step, int cx, int cz, File out) throws Exception {
		int size = 2 * radius / step;
		float[] h = new float[size * size];
		byte[] cause = new byte[size * size];
		long[] plates = new long[size * size];
		IntStream.range(0, size).parallel().forEach(j -> {
			for (int i = 0; i < size; i++) {
				Column c = model.sample(cx - radius + i * step, cz - radius + j * step);
				h[i + j * size] = c.height;
				plates[i + j * size] = (long) c.plateX << 32 ^ c.plateZ & 0xFFFFFFFFL;
				byte k = 0;  // the land's own (eroded node surface)
				if (c.height < 64) {
					k = 7;  // sea
				} else if (c.landform == 2) {
					k = 1;  // canyon
				} else if (c.landform == 4) {
					k = 2;  // tepui
				} else if (c.landform == 1) {
					k = 3;  // volcanic
				} else if (c.landform == 5) {
					k = 4;  // fjord
				} else if (c.nearRiver() && c.riverDist < c.riverHalfWidth * 2.2F + 16) {
					k = 5;  // river valley wall
				}
				cause[i + j * size] = k;
			}
		});
		String[] names = {"land surface", "canyon", "tepui", "volcano", "fjord", "river valley wall", "plate seam", "sea"};
		int[] count = new int[8];
		int[] big = new int[8];
		java.util.List<java.util.List<String>> where = new java.util.ArrayList<>();
		for (int k = 0; k < 8; k++) {
			where.add(new java.util.ArrayList<>());
		}
		boolean[] wall = new boolean[size * size];
		int land = 0;
		int reach = Math.max(2, 24 / step);
		for (int j = 2; j < size - 2; j++) {
			for (int i = 2; i < size - 2; i++) {
				int k = i + j * size;
				if (h[k] < 64) {
					continue;
				}
				land++;
				float gx = (h[k + 1] - h[k - 1]) / (2f * step);
				float gz = (h[k + size] - h[k - size]) / (2f * step);
				float slope = (float) Math.hypot(gx, gz);
				if (slope < 1.5F) {
					continue;
				}
				// a flat foot downhill within reach
				double dx = -gx / slope;
				double dz = -gz / slope;
				boolean flatFoot = false;
				float footH = h[k];
				for (int r = 1; r <= reach && !flatFoot; r++) {
					int fi = i + (int) Math.round(dx * r);
					int fj = j + (int) Math.round(dz * r);
					if (fi < 1 || fj < 1 || fi >= size - 1 || fj >= size - 1) {
						break;
					}
					int f = fi + fj * size;
					float fs = (float) Math.hypot((h[f + 1] - h[f - 1]) / (2f * step), (h[f + size] - h[f - size]) / (2f * step));
					flatFoot = fs < 0.12F && h[f] >= 64 && h[k] - h[f] > 8;
					footH = h[f];
				}
				if (!flatFoot) {
					continue;
				}
				wall[k] = true;
				for (int o = -2; o <= 2; o++) {
					if (plates[k + o] != plates[k] || plates[k + o * size] != plates[k]) {
						cause[k] = 6;
					}
				}
				count[cause[k]]++;
				// how high the rise goes above the foot, within the next 24 blocks uphill
				float topH = h[k];
				for (int r = 1; r <= reach; r++) {
					int ti = i + (int) Math.round(-dx * r);
					int tj = j + (int) Math.round(-dz * r);
					if (ti < 0 || tj < 0 || ti >= size || tj >= size) {
						break;
					}
					topH = Math.max(topH, h[ti + tj * size]);
				}
				if (topH - footH >= 25) {
					big[cause[k]]++;
				}
				if (where.get(cause[k]).size() < 6 && (i * 7 + j * 13) % 97 == 0) {
					where.get(cause[k]).add((cx - radius + i * step) + "," + (cz - radius + j * step) + "@" + Math.round(h[k]));
				}
			}
		}
		int total = 0;
		for (int c : count) {
			total += c;
		}
		System.out.printf("walls: %d of %d land samples (%.2f%%) are steep rises over a flat foot%n", total, land, 100.0 * total / Math.max(1, land));
		for (int k = 0; k < 8; k++) {
			if (count[k] > 0) {
				System.out.printf("  %-18s %6.1f%%  (%d rising 25+)  e.g. %s%n", names[k], 100.0 * count[k] / total, big[k], where.get(k));
			}
		}
		int[] tint = {0xFF2020, 0xFF9020, 0x20C040, 0x202020, 0x80B0FF, 0xFF20FF, 0xFFFF00, 0};
		BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
		for (int j = 0; j < size; j++) {
			for (int i = 0; i < size; i++) {
				int k = i + j * size;
				int rgb;
				if (h[k] < 63) {
					rgb = 0x2A5A9A;
				} else {
					float dx = h[Math.min(size - 1, i + 1) + j * size] - h[Math.max(0, i - 1) + j * size];
					float dz = h[i + Math.min(size - 1, j + 1) * size] - h[i + Math.max(0, j - 1) * size];
					float shade = clamp(1.0F - (dx + dz) / (4.0F * step) * 1.6F, 0.45F, 1.35F);
					rgb = scale(mix(landColour(h[k]), 0xB0B0B0, 0.5F), shade);
				}
				if (wall[k]) {
					rgb = tint[cause[k]];
				}
				img.setRGB(i, j, rgb);
			}
		}
		ImageIO.write(img, "png", out);
		System.out.println("walls map -> " + out);
	}

	/**
	 * Mountain fronts: where a steep flank (rising at least 60 blocks over the next 96 uphill) stands straight
	 * out of a plain that stays flat (no slope over 0.08) for 160 blocks downhill. Prints how much of the land
	 * that is and a height profile across a few of them, every 8 blocks from 160 out on the plain to 96 up.
	 */
	private static void fronts(TerrainModel model, int radius, int step, int cx, int cz) {
		int size = 2 * radius / step;
		float[] h = new float[size * size];
		IntStream.range(0, size).parallel().forEach(j -> {
			for (int i = 0; i < size; i++) {
				h[i + j * size] = model.sample(cx - radius + i * step, cz - radius + j * step).height;
			}
		});
		java.util.List<int[]> found = new java.util.ArrayList<>();
		int land = 0;
		for (int j = 1; j < size - 1; j++) {
			for (int i = 1; i < size - 1; i++) {
				int k = i + j * size;
				if (h[k] < 66) {
					continue;
				}
				land++;
				float gx = (h[k + 1] - h[k - 1]) / (2f * step);
				float gz = (h[k + size] - h[k - size]) / (2f * step);
				float slope = (float) Math.hypot(gx, gz);
				if (slope < 0.35F) {
					continue;
				}
				double ux = gx / slope;
				double uz = gz / slope;
				int x = cx - radius + i * step;
				int z = cz - radius + j * step;
				float top = model.sample((int) (x + ux * 96), (int) (z + uz * 96)).height;
				if (top - h[k] < 60) {
					continue;
				}
				// the foot: where it flattens going down, then flat for 160 blocks
				int foot = -1;
				float prev = h[k];
				for (int r = 8; r <= 64; r += 4) {
					float here = model.sample((int) (x - ux * r), (int) (z - uz * r)).height;
					if (prev - here < 0.3F) {
						foot = r;
						break;
					}
					prev = here;
				}
				if (foot < 0) {
					continue;
				}
				boolean flat = true;
				float fh = model.sample((int) (x - ux * foot), (int) (z - uz * foot)).height;
				float lo = fh;
				float hi = fh;
				for (int r = foot; r <= foot + 160; r += 8) {
					Column c = model.sample((int) (x - ux * r), (int) (z - uz * r));
					lo = Math.min(lo, c.height);
					hi = Math.max(hi, c.height);
				}
				flat = hi - lo < 160 * 0.08F && fh >= 64;
				if (flat) {
					found.add(new int[]{x, z, (int) Math.round(ux * 1000), (int) Math.round(uz * 1000), foot});
				}
			}
		}
		System.out.printf("fronts: %d of %d land samples (%.2f%%) are steep flanks straight out of a flat plain%n", found.size(), land,
			100.0 * found.size() / Math.max(1, land));
		for (int n = 0; n < Math.min(10, found.size()); n++) {
			int[] f = found.get((int) ((long) n * found.size() / 10));
			StringBuilder sb = new StringBuilder();
			Column at = model.sample(f[0], f[1]);
			sb.append(String.format("  %d,%d (uplift %.2f, landform %d, plate %d,%d): ", f[0], f[1], at.uplift, at.landform, at.plateX, at.plateZ));
			for (int r = -160; r <= 96; r += 8) {
				Column c = model.sample((int) (f[0] + f[2] / 1000.0 * r), (int) (f[1] + f[3] / 1000.0 * r));
				sb.append(Math.round(c.height)).append(c.nearRiver() && c.riverDist < c.riverHalfWidth + 3 ? "~" : "").append(r == 0 ? " | " : " ");
			}
			System.out.println(sb);
		}
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
