package dev.voidmc.lod.gametest;

import dev.voidmc.lod.LodSettings;
import dev.voidmc.lod.VoidLod;
import dev.voidmc.lod.gui.LodSettingsScreen;
import dev.voidmc.lod.render.LodRenderer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.fabricmc.fabric.api.client.gametest.v1.world.TestWorldSave;
import net.minecraft.client.InactivityFpsLimit;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.OptionsList;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.options.VideoSettingsScreen;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Photographs an Earth world with the LOD off and on from a few kinds of place: a high peak looking out
 * each way, a low spot looking across the land, and the air high over the peak, logging what the LOD drew
 * and what it cost. Then the same land in other light and weather (dusk, night, rain, thunder), from the sea
 * and under it, over conifer forests, and among the islands of the End.
 *
 * <p>Run with {@code ./gradlew runClientGameTest} (headless on Linux: {@code SDL_VIDEO_FORCE_EGL=1 xvfb-run});
 * screenshots land in build/run/clientGameTest/screenshots. Properties (pass as -P to Gradle):
 * {@code lod.show.seed}, {@code lod.show.distance} (vanilla render distance), {@code lod.show.settle} (ms for
 * vanilla chunks), {@code lod.show.views} (how many of the views to take), {@code lod.show.width} and
 * {@code lod.show.height} (screenshot size), {@code lod.show.off} (false to skip the vanilla shots),
 * {@code lod.show.only} (a comma-separated list of view names), {@code lod.show.settings} (true to also
 * photograph the settings screen and a preset chosen on it, applied live, plus the F3 lines), {@code lod.show.compare}
 * (true to also shoot each view with horizon culling off, the same frame otherwise, to check it hides nothing that
 * shows), {@code lod.show.ingest} (true: build something big in loaded chunks, photograph it from past the render
 * distance, then leave, rejoin and photograph it again, from what was saved), {@code lod.show.mp} (true: the same
 * on a dedicated server, where only seen land is drawn) and {@code lod.show.time} (the time of day in ticks, 6000 by
 * default).
 */
public class LodShowcase implements FabricClientGameTest {
	private static final String SEED = System.getProperty("lod.show.seed", "8675309");
	private static final int DISTANCE = Integer.getInteger("lod.show.distance", 8);
	private static final long SETTLE_MS = Long.getLong("lod.show.settle", 30_000L);
	private static final int VIEWS = Integer.getInteger("lod.show.views", 16);
	/** Only the views with these names, comma separated (all when unset). */
	private static final String ONLY = System.getProperty("lod.show.only", "");
	private static final int WIDTH = Integer.getInteger("lod.show.width", 1600);
	private static final int HEIGHT = Integer.getInteger("lod.show.height", 900);
	private static final boolean OFF = Boolean.parseBoolean(System.getProperty("lod.show.off", "true"));
	private static final boolean SETTINGS = Boolean.getBoolean("lod.show.settings");
	private static final boolean COMPARE = Boolean.getBoolean("lod.show.compare");
	/** The time of day, in ticks (6000 noon, 12500 a low evening sun, 23500 a low morning one). */
	private static final int TIME = Integer.getInteger("lod.show.time", 6000);
	private static final boolean INGEST = Boolean.getBoolean("lod.show.ingest");
	/**
	 * The seam with the chunks, measured: each view again with the chunks out to {@code lod.show.seamFar} (24 by
	 * default) and the LOD off, against the LOD from the usual render distance, compared band by band of distance.
	 */
	private static final boolean SEAM = Boolean.getBoolean("lod.show.seam");
	/** Measures each view's GPU time with face culling on and off, alternating, in the same run (the GPU may be shared). */
	private static final boolean FACE_COMPARE = Boolean.getBoolean("lod.show.faceCompare");
	private static final int SEAM_FAR = Integer.getInteger("lod.show.seamFar", 24);
	private static final long SEAM_SETTLE_MS = Long.getLong("lod.show.seamSettle", 300_000L);
	private static final boolean MULTIPLAYER = Boolean.getBoolean("lod.show.mp");
	/**
	 * Listens for the drawn tiles' terrain: {@code true} only once the view is built, as a map mod that starts late does;
	 * {@code early} from client start, before the world exists, as one that starts with the game does.
	 */
	/** Views of VOID Hearth's nearest town (with -Phearth=<jar>): town0 from 400 m, town1 from 1200 m. */
	private static final boolean HEARTH = Boolean.getBoolean("lod.show.hearth");
	/** An autumn through LodApi's seasons: broadleaf crowns red, orange, gold and late green, the grass gone to straw. */
	private static final boolean AUTUMN = Boolean.getBoolean("lod.show.autumn");
	private static final String LISTEN_MODE = System.getProperty("lod.show.listen", "false");
	private static final boolean LISTEN = !LISTEN_MODE.equals("false");
	private final java.util.Map<Integer, Integer> heardByCell = new java.util.concurrent.ConcurrentSkipListMap<>();
	private final java.util.Set<Long> heardSquares = java.util.concurrent.ConcurrentHashMap.newKeySet();
	private AutoCloseable listening;
	private static final boolean TOWN = Boolean.getBoolean("lod.show.town");
	private static final boolean STRUCTURES = Boolean.getBoolean("lod.show.structures");
	/** How far from the build the camera stands to photograph it: well past the render distance. */
	private static final int AWAY = 480;

	/** A view: where the camera stands and looks, through what lens, in which dimension, at what time and weather. */
	private record Spot(String name, String dimension, int x, int y, int z, float yaw, float pitch, int fov, int time, String weather) {
		Spot(String name, int x, int y, int z, float yaw, float pitch) {
			this(name, x, y, z, yaw, pitch, 70);
		}

		Spot(String name, int x, int y, int z, float yaw, float pitch, int fov) {
			this(name, "minecraft:overworld", x, y, z, yaw, pitch, fov, TIME, "clear");
		}

		/** The same view at another time of day and weather. */
		Spot at(String name, int time, String weather) {
			return new Spot(name, this.dimension, this.x, this.y, this.z, this.yaw, this.pitch, this.fov, time, weather);
		}

		boolean samePlace(Spot o) {
			return o != null && o.dimension.equals(this.dimension) && o.x == this.x && o.y == this.y && o.z == this.z;
		}
	}

	/** Biomes whose trees are mostly conifers, by name: vanilla's and Expanse's. */
	private static final Set<String> CONIFER_BIOMES = Set.of("taiga", "snowy_taiga", "old_growth_pine_taiga", "old_growth_spruce_taiga", "grove",
		"larch_taiga", "boreal_muskeg", "pine_heath", "redwood_giants");

	@Override
	public void runTest(ClientGameTestContext context) {
		if (AUTUMN) {
			autumn();
		}
		if (LISTEN_MODE.equals("early")) {
			this.startListening();
		}
		context.runOnClient(mc -> {
			if (!mc.gui.hud.isHidden()) {
				mc.gui.hud.toggle();
			}
			mc.options.renderDistance().set(DISTANCE);
			// measure real frame rates: no vsync, no cap, no throttling while the window is in the background
			mc.options.enableVsync().set(false);
			mc.options.framerateLimit().set(260);
			mc.options.inactivityFpsLimit().set(InactivityFpsLimit.MINIMIZED);
		});
		TestWorldSave save;
		Spot after = null;
		try (TestSingleplayerContext world = context.worldBuilder()
			.setUseConsistentSettings(false)
			.adjustSettings(s -> {
				s.setSeed(SEED);
				s.setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE);
				s.setAllowCommands(true);
			})
			.create()) {
			save = world.getWorldSave();
			world.getServer().runCommand("gamerule advance_time false");
			world.getServer().runCommand("gamerule advance_weather false");
			world.getServer().runCommand("weather clear");
			world.getServer().runCommand("time set " + TIME);
			world.getServer().runCommand("gamemode spectator @a");

			List<Spot> spots = world.getServer().computeOnServer(server -> {
				ServerLevel level = server.overworld();
				List<Spot> found = new ArrayList<>();
				// Expanse's model where it is loaded and makes the world; the generator's own heights otherwise (slower: a
				// coarser search), so the same views are taken of a vanilla world too
				Land expanse = net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("expanse") ? ExpanseLand.of(level) : null;
				var randomState = level.getChunkSource().randomState();
				var overworld = level.getChunkSource().getGenerator();
				Land model = expanse != null ? expanse : new Land() {
					@Override
					public float height(int x, int z) {
						return overworld.getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, level, randomState);
					}

					@Override
					public boolean river(int x, int z) {
						return false;
					}
				};
				int coarse = expanse != null ? 1 : 3;
				// the highest ground within 3 km, and the lowest dry land within 2 km of it
				int bx = 0, bz = 0, lx = 0, lz = 0;
				float best = -1, low = Float.MAX_VALUE;
				for (int x = -3000; x <= 3000; x += 48 * coarse) {
					for (int z = -3000; z <= 3000; z += 48 * coarse) {
						float h = model.height(x, z);
						if (h > best) {
							best = h;
							bx = x;
							bz = z;
						}
					}
				}
				for (int x = bx - 2000; x <= bx + 2000; x += 40 * coarse) {
					for (int z = bz - 2000; z <= bz + 2000; z += 40 * coarse) {
						float h = model.height(x, z);
						if (h > 68 && h < low && !model.river(x, z) && Math.hypot(x - bx, z - bz) > 900) {
							low = h;
							lx = x;
							lz = z;
						}
					}
				}
				for (int[] at : new int[][]{{bx, bz}, {lx, lz}}) {
					for (int dx = -DISTANCE - 1; dx <= DISTANCE + 1; dx++) {
						for (int dz = -DISTANCE - 1; dz <= DISTANCE + 1; dz++) {
							level.getChunk((at[0] >> 4) + dx, (at[1] >> 4) + dz);
						}
					}
				}
				int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, bx, bz);
				for (int v = 0; v < 4; v++) {
					found.add(new Spot("peak" + v, bx, top + 24, bz, v * 90.0F, 12));
				}
				int lowTop = level.getHeight(Heightmap.Types.MOTION_BLOCKING, lx, lz);
				float toPeak = (float) Math.toDegrees(Math.atan2(-(bx - lx), bz - lz));
				found.add(new Spot("low0", lx, lowTop + 12, lz, toPeak, 2));
				found.add(new Spot("low1", lx, lowTop + 12, lz, toPeak + 180, 2));
				found.add(new Spot("air", bx, top + 420, bz, 45, 32));
				// the far distance through a long lens: what a few kilometres away looks like up close
				found.add(new Spot("far0", bx, top + 24, bz, 90, 4, 30));
				found.add(new Spot("far1", lx, lowTop + 12, lz, toPeak, 2, 30));
				found.add(new Spot("far2", bx, top + 24, bz, 0, 3, 30));
				// the seam with the chunks: the blocky tiles just past the render distance, through a long lens and a
				// normal one
				found.add(seam("seam0", model, lx, lowTop + 12, lz, toPeak + 90, 150, 30));
				found.add(seam("seam1", model, bx, top + 24, bz, 90, 150, 30));
				found.add(seam("seam2", model, lx, lowTop + 12, lz, toPeak + 90, 110, 70));
				found.add(seam("seam3", model, lx, lowTop + 60, lz, toPeak + 90, 170, 30));
				System.out.printf(Locale.ROOT, "[lod] high point %d,%d at y=%d; low point %d,%d at y=%d%n", bx, bz, top, lx, lz, lowTop);

				// the peak looking west, into the sunset, and east, away from it; the valley at night and in the rain
				Spot west = found.get(1);
				Spot east = found.get(3);
				Spot valley = found.get(4);
				found.add(west.at("dusk0", 12500, "clear"));
				found.add(west.at("dusk1", 13000, "clear"));
				found.add(east.at("dusk2", 12800, "clear"));
				found.add(west.at("night0", 18000, "clear"));
				found.add(valley.at("night1", 18000, "clear"));
				found.add(west.at("rain0", 6000, "rain"));
				found.add(valley.at("rain1", 6000, "rain"));
				found.add(west.at("thunder0", 6000, "thunder"));

				// the open sea nearest the peak: from just above the water out to sea at dusk and at night, and under it
				int sea = level.getSeaLevel();
				int[] deep = nearest(bx, bz, 6000, 64, (x, z) -> model.height(x, z) < sea - 24);
				if (deep != null) {
					float out = (float) Math.toDegrees(Math.atan2(-(deep[0] - bx), deep[1] - bz));
					Spot above = new Spot("sea", deep[0], sea + 5, deep[1], out, 2);
					found.add(above.at("sea0", 12600, "clear"));
					found.add(above.at("sea1", 18000, "clear"));
					found.add(new Spot("water0", deep[0], sea - 5, deep[1], out + 180, 4));
					System.out.printf(Locale.ROOT, "[lod] open sea at %d,%d%n", deep[0], deep[1]);
				}

				// conifer forests, one temperate and one under snow, each from high enough over them that most of what
				// is in view is the LOD's
				BiomeResolver biomes = level.getChunkSource().getGenerator().getBiomeSource().createUncachedResolver(level.getChunkSource().randomState());
				for (boolean snowy : new boolean[]{false, true}) {
					int[] forest = nearest(bx, bz, 6000, 64, (x, z) -> {
						float h = model.height(x, z);
						if (h < sea + 4 || model.river(x, z)) {
							return false;
						}
						Holder<Biome> biome = biomes.getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock((int) h), QuartPos.fromBlock(z));
						String path = biome.unwrapKey().map(k -> k.identifier().getPath()).orElse("");
						return CONIFER_BIOMES.contains(path) && (biome.value().getBaseTemperature() < 0.15F) == snowy;
					});
					if (forest != null) {
						int ground = (int) model.height(forest[0], forest[1]);
						found.add(new Spot(snowy ? "forest1" : "forest0", forest[0], ground + 140, forest[1], 45, 24));
						System.out.printf(Locale.ROOT, "[lod] %s conifer forest at %d,%d, y=%d%n", snowy ? "snowy" : "temperate", forest[0], forest[1], ground);
					}
				}

				// the End's outer islands, past the void around the main island: from one island out over the others,
				// from high above them, and from the void beside one, looking up at the undersides
				ServerLevel end = server.getLevel(Level.END);
				if (end != null) {
					var generator = end.getChunkSource().getGenerator();
					var random = end.getChunkSource().randomState();
					for (int x = 1200; x < 4000; x += 16) {
						int h = generator.getBaseHeight(x, 0, Heightmap.Types.OCEAN_FLOOR_WG, end, random);
						if (h > end.getMinY() + 20) {
							found.add(new Spot("end0", "minecraft:the_end", x, h + 20, 0, 270, 6, 70, 6000, "clear"));
							found.add(new Spot("end1", "minecraft:the_end", x, h + 140, 0, 250, 30, 70, 6000, "clear"));
							found.add(new Spot("end2", "minecraft:the_end", x - 48, 16, 0, 270, -24, 70, 6000, "clear"));
							System.out.printf(Locale.ROOT, "[lod] End island at %d,0, y=%d%n", x, h);
							break;
						}
					}
				}
				return found;
			});

			if (Boolean.getBoolean("lod.show.lake")) {
				// the nearest lake, from 150 m off its shore and 40 m above it: lake0 from inside the chunks' reach, lake1 from beyond
				int[] lake = world.getServer().computeOnServer(server -> ExpanseLand.lake(server.overworld(), 0, 0, 6000));
				if (lake != null) {
					System.out.printf(Locale.ROOT, "[lod] lake at %d,%d, top water block %d%n", lake[0], lake[1], lake[2]);
					for (int[] view : new int[][]{{90, 0}, {300, 1}}) {
						int cx = lake[0] - view[0], cz = lake[1] - view[0];
						spots.add(new Spot("lake" + view[1], cx, lake[2] + 40, cz, -45, (float) Math.toDegrees(Math.atan2(40, view[0] * 1.414)), 70));
					}
				} else {
					System.out.println("[lod] lake: none within 6 km");
				}
			}
			if (Boolean.getBoolean("lod.show.farCheck")) {
				String check = world.getServer().computeOnServer(server -> ExpanseLand.farCheck(server.overworld(), 1899, 930, 24000, 384));
				System.out.println("[lod] far check: " + check);
			}
			if (HEARTH) {
				spots.addAll(world.getServer().computeOnServer(server -> {
					List<Spot> found = new ArrayList<>();
					ServerLevel level = server.overworld();
					var footprints = dev.voidmc.lod.source.HearthFootprints.of(level);
					if (footprints == null) {
						System.out.println("[lod] hearth: not running");
						return found;
					}
					Land model = ExpanseLand.of(level);
					int[] b = new int[3];
					// the nearest finished building to spawn, ring by ring
					for (int d = 0; d <= 4000; d += 32) {
						for (int step = 0; step < Math.max(1, d / 4); step++) {
							double a = step * 2 * Math.PI / Math.max(1, d / 4);
							int tx = (int) Math.round(Math.cos(a) * d), tz = (int) Math.round(Math.sin(a) * d);
							if (!footprints.sample(tx, tz, b)) {
								continue;
							}
							int eave = b[2] >> 16, ridge = (short) b[2];
							System.out.printf(Locale.ROOT, "[lod] hearth: a building at %d,%d, eaves %d, ridge %d, wall %06x, roof %06x%n", tx, tz, eave, ridge,
								b[0] & 0xFFFFFF, b[1] & 0xFFFFFF);
							for (int[] view : new int[][]{{400, 0}, {1200, 1}}) {
								// from the south-west, looking at it
								int cx = tx - (int) (view[0] * 0.707), cz = tz - (int) (view[0] * 0.707);
								// high enough to see over the land between
								float between = 63;
								for (int t = 0; t <= 20; t++) {
									between = Math.max(between, model.height(cx + (tx - cx) * t / 20, cz + (tz - cz) * t / 20));
								}
								int cy = (int) between + 40 + view[1] * 40;
								float yaw = (float) Math.toDegrees(Math.atan2(-(tx - cx), tz - cz));
								float pitch = (float) Math.toDegrees(Math.atan2(cy - ridge, view[0]));
								found.add(new Spot("town" + view[1], cx, cy, cz, yaw, pitch, 50));
							}
							return found;
						}
					}
					System.out.println("[lod] hearth: no finished building within 4 km");
					return found;
				}));
			}
			// fixed viewpoints of one's own: -Plod.show.at=name:x,y,z,yaw,pitch[,fov][;name:...]
			for (String at : System.getProperty("lod.show.at", "").split(";")) {
				if (at.isBlank()) {
					continue;
				}
				String[] parts = at.split(":", 2);
				String[] v = parts[1].split(",");
				spots.add(new Spot(parts[0].trim(), Integer.parseInt(v[0].trim()), Integer.parseInt(v[1].trim()), Integer.parseInt(v[2].trim()),
					Float.parseFloat(v[3].trim()), Float.parseFloat(v[4].trim()), v.length > 5 ? Integer.parseInt(v[5].trim()) : 70));
			}
			Spot valley = spots.stream().filter(spot -> spot.name().equals("low0")).findFirst().orElse(spots.get(0));
			if (!ONLY.isEmpty()) {
				List<String> names = List.of(ONLY.split(","));
				spots.removeIf(spot -> !names.contains(spot.name()));
			}
			for (int i = 0; i < Math.min(VIEWS, spots.size()); i++) {
				Spot spot = spots.get(i);
				context.runOnClient(mc -> mc.options.fov().set(spot.fov()));
				Spot last = i == 0 ? null : spots.get(i - 1);
				boolean moved = !spot.samePlace(last);
				boolean sky = last == null || last.time() != spot.time() || !last.weather().equals(spot.weather());
				world.getServer().runCommand("time set " + spot.time());
				world.getServer().runCommand("weather " + spot.weather());
				world.getServer().runCommand(String.format(Locale.ROOT, "execute in %s run tp @a %d %d %d %.1f %.1f", spot.dimension(), spot.x(), spot.y(),
					spot.z(), spot.yaw(), spot.pitch()));
				if (sky && !moved) {
					// rain and thunder take five seconds to set in, and the rain's fog a little longer
					context.waitTicks(140);
				}

				if (moved && !OFF) {
					// how long the far view has holes after arriving (not even a coarse tile over some of it)
					long t0 = System.currentTimeMillis();
					// a couple of frames for the selection to see where the camera is now
					context.waitTicks(2);
					int ticks = 0;
					while (ticks++ < 400 && context.computeOnClient(mc -> VoidLod.get().engine().uncovered() > 0
						|| VoidLod.get().engine().tileCount() == 0)) {
						context.waitTick();
					}
					System.out.printf(Locale.ROOT, "[lod] covered %s in %.1f s%n", spot.name(), (System.currentTimeMillis() - t0) / 1000.0);
				}
				if (OFF) {
					context.runOnClient(mc -> VoidLod.get().setEnabled(false));
					this.settle(context, moved ? SETTLE_MS : 2_000);
					this.shoot(context, spot.name() + "_off");
				} else if (moved) {
					this.settle(context, SETTLE_MS);
				}

				context.runOnClient(mc -> VoidLod.get().setEnabled(true));
				this.photograph(context, spot.name(), false);
				if (COMPARE) {
					context.runOnClient(mc -> LodRenderer.occlusion = false);
					context.waitTicks(4);
					this.shoot(context, spot.name() + "_unculled");
					String unculled = context.computeOnClient(mc -> VoidLod.get().stats());
					System.out.printf(Locale.ROOT, "[lod] %s unculled: %s%n", spot.name(), unculled.substring(unculled.indexOf('|') + 2));
					context.runOnClient(mc -> LodRenderer.occlusion = true);
				}
				if (SEAM) {
					this.seam(context, world.getServer(), spot);
				}
				if (LISTEN) {
					this.listen(context, spot);
				}
				if (Boolean.getBoolean("lod.show.sortCompare")) {
					double[] sum = new double[2];
					for (int round = 0; round < 10; round++) {
						boolean on = round % 2 == 0;
						context.runOnClient(mc -> LodRenderer.frontToBack = on);
						context.waitTicks(60);
						double ms = context.computeOnClient(mc -> VoidLod.get().renderer().gpuMillis);
						sum[on ? 0 : 1] += ms;
					}
					context.runOnClient(mc -> LodRenderer.frontToBack = true);
					System.out.printf(Locale.ROOT, "[lod] order %s: GPU %.2f ms nearest first, %.2f ms unsorted%n", spot.name(), sum[0] / 5, sum[1] / 5);
				}
				if (FACE_COMPARE) {
					double[] sum = new double[2];
					for (int round = 0; round < 6; round++) {
						boolean on = round % 2 == 0;
						context.runOnClient(mc -> LodRenderer.faceCulling = on);
						context.waitTicks(60);
						double ms = context.computeOnClient(mc -> VoidLod.get().renderer().gpuMillis);
						sum[on ? 0 : 1] += ms;
					}
					context.runOnClient(mc -> LodRenderer.faceCulling = true);
					System.out.printf(Locale.ROOT, "[lod] faces %s: GPU %.2f ms with face culling, %.2f ms without%n", spot.name(), sum[0] / 3, sum[1] / 3);
				}
			}
			if (SETTINGS && !spots.isEmpty()) {
				this.settings(context, spots.get(Math.min(VIEWS, spots.size()) - 1));
			}

			if (TOWN) {
				this.town(context, world.getServer(), valley.x(), valley.z() + 256, "town");
			}
			if (STRUCTURES) {
				this.village(context, world.getServer(), valley.x(), valley.z());
			}
			if (INGEST) {
				after = this.build(context, world.getServer(), valley.x(), valley.z(), "ingest");
			}
		}
		if (after != null) {
			// back into the same world, standing where the last photograph was taken: the build's chunks are past the
			// render distance and never load this time, so if it shows, it came off the disk
			try (TestSingleplayerContext again = save.open()) {
				context.runOnClient(mc -> VoidLod.get().setEnabled(true));
				this.settle(context, SETTLE_MS);
				this.photograph(context, "ingest_rejoin", true);
				String files = save.getSaveDirectory().resolve(VoidLod.ID).toString();
				System.out.printf(Locale.ROOT, "[lod] saved land in %s: %d KB%n", files, size(save.getSaveDirectory().resolve(VoidLod.ID)) / 1024);
			}
		}
		if (MULTIPLAYER) {
			this.multiplayer(context);
		}
	}

	/**
	 * Waits for the LOD to finish building what it wants (twice in a row, so a view that has not caught up with the
	 * teleport yet does not count), then a few frames more to upload and fade in; photographs it and logs what it
	 * drew and cost. With {@code changes}, also waits (a while at most) for tiles whose land changed to be rebuilt.
	 */
	private void photograph(ClientGameTestContext context, String name, boolean changes) {
		long start = System.currentTimeMillis();
		long firstFrame = context.computeOnClient(mc -> VoidLod.get().engine().frame());
		int calm = 0;
		while (System.currentTimeMillis() - start < 180_000 && calm < 3) {
			context.waitTicks(10);
			// done once it has drawn a while from here, holds tiles, and has nothing left to build
			// a fresh world keeps changing for a while (grass spreading, leaves falling, water finding its level)
			boolean settled = System.currentTimeMillis() - start > 30_000;
			String state = context.computeOnClient(mc -> {
				var engine = VoidLod.get().engine();
				var ingest = VoidLod.get().ingest();
				boolean done = engine.frame() - firstFrame > 200 && engine.tileCount() > 0 && engine.idle() && engine.queued() == 0
					&& VoidLod.get().remotePending() == 0 && VoidLod.get().treesPending() == 0 && (settled || engine.dirtyDrawn() == 0) && (!changes || settled || engine.dirtyDrawn() == 0 && (ingest == null || ingest.queued() == 0));
				return (done ? "done " : "busy ") + (engine.frame() - firstFrame) + " frames, " + engine.busy();
			});
			boolean idle = state.startsWith("done");
			if (Boolean.getBoolean("lod.show.trace")) {
				System.out.println("[lod] waiting: " + state);
			}
			calm = idle ? calm + 1 : 0;
		}
		long loaded = System.currentTimeMillis() - start;
		context.waitTicks(60);
		this.shoot(context, name + "_on");
		String stats = context.computeOnClient(mc -> VoidLod.get().stats() + String.format(Locale.ROOT, ", %d fps", mc.getFps()));
		System.out.printf(Locale.ROOT, "[lod] %s: complete in %.1f s | %s%n", name, loaded / 1000.0, stats);
	}

	/**
	 * Builds something big and unmistakable at {@code (x, z)} while its chunks are loaded (a red concrete deck pierced
	 * by a gold tower, a trench cut across the land), and photographs it from {@value #AWAY} blocks away, past the
	 * render distance: before, so the land is known, and after. It can only be in the second photograph if the
	 * loaded chunks went into the LOD. Returns where the photographs were taken from.
	 */
	private Spot build(ClientGameTestContext context, TestServerContext server, int x, int z, String name) {
		int[] heights = server.computeOnServer(s -> {
			ServerLevel level = s.overworld();
			return new int[]{level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z), level.getHeight(Heightmap.Types.MOTION_BLOCKING, x + AWAY, z)};
		});
		int ground = heights[0];
		int eye = Math.max(ground, heights[1]) + 140;
		float pitch = (float) Math.toDegrees(Math.atan2(eye - (ground + 40), AWAY));
		Spot from = new Spot(name, x + AWAY, eye, z, 90, pitch);
		server.runCommand("gamerule max_block_modifications 1000000");

		server.runCommand(String.format(Locale.ROOT, "execute in minecraft:overworld run tp @a %d %d %d %.1f %.1f", from.x(), from.y(), from.z(), from.yaw(), from.pitch()));
		context.runOnClient(mc -> VoidLod.get().setEnabled(true));
		this.settle(context, SETTLE_MS);
		this.photograph(context, name + "_before", false);

		server.runCommand(String.format(Locale.ROOT, "execute in minecraft:overworld run tp @a %d %d %d 0 60", x, ground + 60, z));
		this.settle(context, SETTLE_MS);
		long t0 = System.currentTimeMillis();
		server.runCommand(String.format(Locale.ROOT, "fill %d %d %d %d %d %d gold_block", x - 4, ground - 2, z - 4, x + 3, ground + 120, z + 3));
		server.runCommand(String.format(Locale.ROOT, "fill %d %d %d %d %d %d red_concrete", x - 64, ground + 40, z - 64, x + 63, ground + 42, z + 63));
		server.runCommand(String.format(Locale.ROOT, "fill %d %d %d %d %d %d gold_block", x - 4, ground + 40, z - 4, x + 3, ground + 120, z + 3));
		server.runCommand(String.format(Locale.ROOT, "fill %d %d %d %d %d %d air", x - 64, ground - 24, z + 72, x + 63, ground + 80, z + 95));
		// the block updates reach the client, settle, and are read
		for (int i = 0; i < 60; i++) {
			context.waitTicks(10);
			if (i >= 4 && context.computeOnClient(mc -> VoidLod.get().ingest() == null || VoidLod.get().ingest().queued() == 0)) {
				break;
			}
		}
		String ingest = context.computeOnClient(mc -> VoidLod.get().stats());
		System.out.printf(Locale.ROOT, "[lod] built at %d,%d (ground %d), read in %.1f s | %s%n", x, z, ground, (System.currentTimeMillis() - t0) / 1000.0,
			ingest);

		server.runCommand(String.format(Locale.ROOT, "execute in minecraft:overworld run tp @a %d %d %d %.1f %.1f", from.x(), from.y(), from.z(), from.yaw(), from.pitch()));
		this.settle(context, SETTLE_MS);
		context.runOnClient(mc -> VoidLod.get().setEnabled(false));
		context.waitTicks(20);
		this.shoot(context, name + "_after_off");
		context.runOnClient(mc -> VoidLod.get().setEnabled(true));
		this.photograph(context, name + "_after", true);
		return from;
	}

	/**
	 * Lays a town's worth of light at {@code (x, z)} while its chunks are loaded (a stone deck with glowstone streets
	 * every 16 blocks), then photographs it at midnight from {@value #AWAY} blocks away, past the render distance, with
	 * the LOD and without: it only glows in the first if the chunks' block light went into the LOD.
	 */
	private void town(ClientGameTestContext context, TestServerContext server, int x, int z, String name) {
		// heights only once each place's chunks are loaded: an unloaded column reads as the bottom of the world
		server.runCommand(String.format(Locale.ROOT, "execute in minecraft:overworld run tp @a %d 300 %d 0 60", x + AWAY, z));
		this.settle(context, SETTLE_MS);
		int far = server.computeOnServer(s -> s.overworld().getHeight(Heightmap.Types.MOTION_BLOCKING, x + AWAY, z));
		server.runCommand(String.format(Locale.ROOT, "execute in minecraft:overworld run tp @a %d 300 %d 0 60", x, z));
		this.settle(context, SETTLE_MS);
		int deck = server.computeOnServer(s -> s.overworld().getHeight(Heightmap.Types.MOTION_BLOCKING, x, z)) + 4;
		int eye = Math.max(deck, far) + 60;
		float pitch = (float) Math.toDegrees(Math.atan2(eye - deck, AWAY));
		server.runCommand("gamerule max_block_modifications 1000000");
		server.runCommand(String.format(Locale.ROOT, "fill %d %d %d %d %d %d stone_bricks", x - 48, deck, z - 48, x + 47, deck, z + 47));
		for (int d = -48; d <= 48; d += 16) {
			server.runCommand(String.format(Locale.ROOT, "fill %d %d %d %d %d %d glowstone", x - 48, deck, z + d, x + 47, deck, z + d));
			server.runCommand(String.format(Locale.ROOT, "fill %d %d %d %d %d %d glowstone", x + d, deck, z - 48, x + d, deck, z + 47));
		}
		for (int i = 0; i < 60; i++) {
			context.waitTicks(10);
			if (i >= 4 && context.computeOnClient(mc -> VoidLod.get().ingest() == null || VoidLod.get().ingest().queued() == 0)) {
				break;
			}
		}
		server.runCommand("time set 18000");
		server.runCommand(String.format(Locale.ROOT, "execute in minecraft:overworld run tp @a %d %d %d 90 %.1f", x + AWAY, eye, z, pitch));
		this.settle(context, SETTLE_MS);
		context.runOnClient(mc -> VoidLod.get().setEnabled(false));
		context.waitTicks(20);
		this.shoot(context, name + "_off");
		context.runOnClient(mc -> VoidLod.get().setEnabled(true));
		this.photograph(context, name, true);
		server.runCommand("time set 6000");
	}

	/**
	 * Finds the nearest village of the world's own (as /locate would) and photographs it from about 700 blocks off, by
	 * day and at midnight, without its chunks ever loading: if it shows, the LOD worked out where it stands.
	 */
	private void village(ClientGameTestContext context, TestServerContext server, int x, int z) {
		int[] at = server.computeOnServer(s -> {
			ServerLevel level = s.overworld();
			var structures = level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.STRUCTURE);
			var villages = structures.listElements().filter(h -> h.key().identifier().getPath().startsWith("village")).map(h -> (net.minecraft.core.Holder<net.minecraft.world.level.levelgen.structure.Structure>) h).toList();
			if (villages.isEmpty()) {
				return null;
			}
			var found = level.getChunkSource().getGenerator().findNearestMapStructure(level, net.minecraft.core.HolderSet.direct(villages),
				new net.minecraft.core.BlockPos(x + 1500, 100, z), 100, false);
			return found == null ? null : new int[]{found.getFirst().getX(), found.getFirst().getZ()};
		});
		if (at == null) {
			System.out.println("[lod] no village found");
			return;
		}
		int vx = at[0], vz = at[1];
		// look at it from the west, 700 blocks off and well above the land
		server.runCommand(String.format(Locale.ROOT, "execute in minecraft:overworld run tp @a %d 300 %d 90 30", vx + 700, vz));
		this.settle(context, SETTLE_MS);
		int ground = server.computeOnServer(s -> s.overworld().getHeight(Heightmap.Types.MOTION_BLOCKING, vx + 700, vz));
		int eye = ground + 90;
		float pitch = (float) Math.toDegrees(Math.atan2(eye - (ground - 10), 700));
		System.out.printf(Locale.ROOT, "[lod] village at %d,%d; looking from %d,%d,%d%n", vx, vz, vx + 700, eye, vz);
		server.runCommand(String.format(Locale.ROOT, "execute in minecraft:overworld run tp @a %d %d %d 90 %.1f", vx + 700, eye, vz, pitch));
		server.runCommand("time set 6000");
		this.settle(context, SETTLE_MS);
		// the village's region worked out, and the tiles over it built again
		for (int i = 0; i < 240 && !context.computeOnClient(mc -> VoidLod.get().sites() == null || VoidLod.get().sites().known(vx, vz)); i++) {
			context.waitTicks(10);
		}
		this.photograph(context, "village_day", true);
		server.runCommand("time set 18000");
		context.waitTicks(40);
		this.photograph(context, "village_night", false);
		server.runCommand("time set 6000");
	}

	/**
	 * The same on a dedicated server: no generator to sample, so the LOD draws only the land the client has seen.
	 * The build is photographed from far off; the land around it that was never loaded is not drawn at all.
	 */
	private void multiplayer(ClientGameTestContext context) {
		java.util.Properties properties = new java.util.Properties();
		properties.setProperty("level-seed", SEED);
		properties.setProperty("view-distance", String.valueOf(DISTANCE));
		properties.setProperty("gamemode", "spectator");
		try (TestDedicatedServerContext server = context.worldBuilder().setUseConsistentSettings(false).createServer(properties);
			TestDedicatedServerConnection connection = server.connect()) {
			server.runCommand("gamerule advance_time false");
			server.runCommand("gamerule advance_weather false");
			server.runCommand("weather clear");
			server.runCommand("time set 6000");
			server.runCommand("gamemode spectator @a");
			context.runOnClient(mc -> mc.options.fov().set(70));
			int[] at = server.computeOnServer(s -> {
				var spawn = s.overworld().getRespawnData().pos();
				return new int[]{spawn.getX(), spawn.getZ()};
			});
			this.build(context, server, at[0], at[1], "mp");
			// the land nobody has loaded: with VOID LOD on the server too, it describes it, and it is drawn as in singleplayer
			server.runCommand(String.format(Locale.ROOT, "execute in minecraft:overworld run tp @a %d 330 %d 60 18", at[0], at[1]));
			this.settle(context, SETTLE_MS);
			context.runOnClient(mc -> VoidLod.get().setEnabled(false));
			context.waitTicks(20);
			this.shoot(context, "mp_horizon_off");
			context.runOnClient(mc -> VoidLod.get().setEnabled(true));
			this.photograph(context, "mp_horizon", false);
			System.out.println("[lod] server described " + server.computeOnServer(s -> dev.voidmc.lod.net.FactsService.TILES.get()) + " tiles");
		}
	}

	/**
	 * The view as the chunks draw it (out to {@link #SEAM_FAR} chunks, LOD off) against the LOD's (from {@link #DISTANCE}):
	 * per band of distance across the ground, the mean colour and brightness of each and how much texture each has
	 * (the mean difference between neighbouring pixels: leaves and grass have plenty, a flat face none), and a picture
	 * of the two, one over the other. Distance is from the angle under the horizon and the eye's height over the
	 * ground, so it is right on level land and rough on hills.
	 */
	/**
	 * Starts listening for the drawn tiles' terrain with the view already built, waits for {@code LodApi.complete()},
	 * and logs what came by cell size and how much of the reach it covers (256-block squares within 90% of it).
	 */
	private static void autumn() {
		dev.voidmc.lod.api.LodApi.setSeasons((kind, biome, material, conifer) -> {
			if (kind == dev.voidmc.lod.api.LodApi.Seasons.LEAVES) {
				var state = dev.voidmc.lod.api.LodApi.material(material);
				boolean needles = conifer >= 0 ? conifer > 0.5F
					: state != null && (state.is(net.minecraft.world.level.block.Blocks.SPRUCE_LEAVES));
				return needles ? 0 : 1;
			}
			var state = dev.voidmc.lod.api.LodApi.material(material);
			return state != null && state.is(net.minecraft.world.level.block.Blocks.GRASS_BLOCK) ? 2 : 0;
		});
		int n = dev.voidmc.lod.api.LodApi.Seasons.CLASSES * dev.voidmc.lod.api.LodApi.Seasons.VARIANTS;
		float[] table = new float[n * 5];
		for (int e = 0; e < n; e++) {
			table[e * 5 + 4] = 1;
		}
		float[][] crowns = {{0.85F, 0.25F, 0.08F, 0.9F}, {0.95F, 0.5F, 0.1F, 0.9F}, {0.9F, 0.75F, 0.15F, 0.85F}, {0.4F, 0.55F, 0.15F, 0.5F}};
		for (int v = 0; v < 4; v++) {
			System.arraycopy(crowns[v], 0, table, (4 + v) * 5, 4);
			// straw: toward a pale ochre, a little brighter
			table[(8 + v) * 5] = 0.75F;
			table[(8 + v) * 5 + 1] = 0.68F;
			table[(8 + v) * 5 + 2] = 0.35F;
			table[(8 + v) * 5 + 3] = 0.7F;
			table[(8 + v) * 5 + 4] = 1.05F;
		}
		dev.voidmc.lod.api.LodApi.setSeasonTints(table);
	}

	private void startListening() {
		this.listening = dev.voidmc.lod.api.LodApi.addVisibleTerrainListener(tile -> {
			this.heardByCell.merge(tile.cell(), 1, Integer::sum);
			int span = tile.cell() * tile.size();
			for (int x = Math.floorDiv(tile.minX(), 256); x < Math.floorDiv(tile.minX() + span + 255, 256); x++) {
				for (int z = Math.floorDiv(tile.minZ(), 256); z < Math.floorDiv(tile.minZ() + span + 255, 256); z++) {
					this.heardSquares.add((long) x << 32 ^ z & 0xFFFFFFFFL);
				}
			}
		});
	}

	private void listen(ClientGameTestContext context, Spot spot) {
		if (this.listening == null) {
			this.startListening();
		}
		java.util.Map<Integer, Integer> byCell = this.heardByCell;
		java.util.Set<Long> covered = this.heardSquares;
		long start = System.currentTimeMillis();
		int calm = 0;
		while (System.currentTimeMillis() - start < 180_000 && calm < 5) {
			this.settle(context, 1_000);
			calm = context.computeOnClient(mc -> dev.voidmc.lod.api.LodApi.complete()) ? calm + 1 : 0;
		}
		double reach = 0.9 * context.computeOnClient(mc -> VoidLod.get().radius);
		int want = 0, got = 0;
		for (int x = (int) Math.floor((spot.x() - reach) / 256); x <= (int) Math.floor((spot.x() + reach) / 256); x++) {
			for (int z = (int) Math.floor((spot.z() - reach) / 256); z <= (int) Math.floor((spot.z() + reach) / 256); z++) {
				double dx = x * 256 + 128 - spot.x(), dz = z * 256 + 128 - spot.z();
				if (dx * dx + dz * dz <= reach * reach) {
					want++;
					if (covered.contains((long) x << 32 ^ z & 0xFFFFFFFFL)) {
						got++;
					}
				}
			}
		}
		System.out.printf(Locale.ROOT, "[lod] listen %s: complete after %.1f s, tiles by cell %s, %d of %d squares in 90%% of the reach covered%n",
			spot.name(), (System.currentTimeMillis() - start) / 1000.0, byCell, got, want);
	}

	private void seam(ClientGameTestContext context, TestServerContext server, Spot spot) {
		java.nio.file.Path lod = context.takeScreenshot(TestScreenshotOptions.of("lod_" + spot.name() + "_seam_lod").withSize(WIDTH, HEIGHT));
		context.runOnClient(mc -> {
			VoidLod.get().setEnabled(false);
			mc.options.renderDistance().set(SEAM_FAR);
			// the server sends a player no further than the distance their client says it draws
			mc.options.broadcastOptions();
		});
		server.runOnServer(s -> s.getPlayerList().setViewDistance(SEAM_FAR));
		// the chunks out to the far distance must exist and be drawn: a world generator takes minutes for that many
		long until = System.currentTimeMillis() + SEAM_SETTLE_MS;
		int want = (int) (0.85 * Math.PI * SEAM_FAR * SEAM_FAR);
		int last = -1, calm = 0;
		while (System.currentTimeMillis() < until && calm < 4) {
			this.settle(context, 2_000);
			int[] state = context.computeOnClient(mc -> new int[]{mc.level.getChunkSource().getLoadedChunksCount(),
				mc.levelRenderer.hasRenderedAllSections() ? 1 : 0});
			calm = state[0] >= want && state[0] == last && state[1] == 1 ? calm + 1 : 0;
			last = state[0];
		}
		String distances = context.computeOnClient(mc -> mc.options.renderDistance().get() + " option, " + mc.options.getEffectiveRenderDistance()
			+ " effective") + ", server " + server.computeOnServer(s -> s.getPlayerList().getViewDistance());
		System.out.printf(Locale.ROOT, "[lod] seam %s: %d chunks loaded for the far shot (want %d; render distance %s)%n", spot.name(), last, want,
			distances);
		// the chunks' ground against the terrain model's along the view: what the far tiles stand their land on
		if (net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("expanse")) {
			String heights = server.computeOnServer(s -> {
				var level = s.overworld();
				Land model = ExpanseLand.of(level);
				StringBuilder out = new StringBuilder();
				double a = Math.toRadians(spot.yaw());
				for (int d = 32; d <= 384; d += 32) {
					for (int side = -1; side <= 1; side++) {
						double b = a + side * Math.toRadians(20);
						int x = (int) Math.round(spot.x() - Math.sin(b) * d), z = (int) Math.round(spot.z() + Math.cos(b) * d);
						int real = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
						out.append(String.format(Locale.ROOT, " %d:%+.0f", d, real - model.height(x, z)));
					}
				}
				return out.toString();
			});
			System.out.printf(Locale.ROOT, "[lod] heights %s (chunks minus model, by distance, 3 bearings):%s%n", spot.name(), heights);
		}
		this.treeMatch(context, server, spot);
		// back where the LOD's shot was taken: the player falls while the far chunks load
		server.runCommand(String.format(Locale.ROOT, "execute in %s run tp @a %d %d %d %.1f %.1f", spot.dimension(), spot.x(), spot.y(), spot.z(),
			spot.yaw(), spot.pitch()));
		context.waitTicks(20);
		java.nio.file.Path vanilla = context.takeScreenshot(TestScreenshotOptions.of("lod_" + spot.name() + "_seam_vanilla").withSize(WIDTH, HEIGHT));
		context.runOnClient(mc -> {
			mc.options.renderDistance().set(DISTANCE);
			mc.options.broadcastOptions();
			VoidLod.get().setEnabled(true);
		});
		this.settle(context, 5_000);
		int ground = server.computeOnServer(s -> s.overworld().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, spot.x(), spot.z()));
		try {
			compare(spot, spot.y() + 1.62 - ground, javax.imageio.ImageIO.read(vanilla.toFile()), javax.imageio.ImageIO.read(lod.toFile()),
				vanilla.resolveSibling("seam_" + spot.name() + ".png"));
		} catch (java.io.IOException e) {
			throw new java.io.UncheckedIOException(e);
		}
	}

	/**
	 * How well the trees the LOD places match the chunks': over a square of 96 blocks, 200 blocks out along the view (its
	 * chunks loaded for the far shot), the logs in the real world against the logs the source's decorator places there.
	 */
	private void treeMatch(ClientGameTestContext context, TestServerContext server, Spot spot) {
		double yaw = Math.toRadians(spot.yaw());
		int cx = (int) Math.round(spot.x() - Math.sin(yaw) * 200), cz = (int) Math.round(spot.z() + Math.cos(yaw) * 200);
		int x0 = cx - 48, z0 = cz - 48, x1 = cx + 48, z1 = cz + 48;
		java.util.Map<String, Integer> realKinds = new java.util.TreeMap<>();
		java.util.Set<Long> real = server.computeOnServer(s -> {
			ServerLevel level = s.overworld();
			java.util.Set<Long> logs = new java.util.HashSet<>();
			net.minecraft.core.BlockPos.MutableBlockPos p = new net.minecraft.core.BlockPos.MutableBlockPos();
			for (int x = x0; x < x1; x++) {
				for (int z = z0; z < z1; z++) {
					int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
					for (int y = top - 40; y <= top; y++) {
						net.minecraft.world.level.block.state.BlockState state = level.getBlockState(p.set(x, y, z));
						if (state.is(net.minecraft.tags.BlockTags.LOGS)) {
							logs.add(net.minecraft.core.BlockPos.asLong(x, y, z));
							realKinds.merge(state.getBlock().getDescriptionId(), 1, Integer::sum);
						}
					}
				}
			}
			return logs;
		});
		java.util.Map<String, Integer> placedKinds = new java.util.TreeMap<>();
		// the trees there are placed in the background: asked until they are ready
		for (int tries = 0; tries < 120; tries++) {
			boolean ready = context.computeOnClient(mc -> {
				var source = VoidLod.get().engine().source();
				var decorator = source == null ? null : source.decorator();
				return decorator == null || decorator.decorate(x0, z0, x1, z1, (x, z, out) -> source.sample(x, z, 1, out),
					new dev.voidmc.lod.core.Voxels());
			});
			if (ready) {
				break;
			}
			context.waitTicks(20);
		}
		java.util.Set<Long> placed = context.computeOnClient(mc -> {
			var source = VoidLod.get().engine().source();
			var decorator = source == null ? null : source.decorator();
			java.util.Set<Long> logs = new java.util.HashSet<>();
			if (decorator == null) {
				return logs;
			}
			dev.voidmc.lod.core.Voxels v = new dev.voidmc.lod.core.Voxels();
			decorator.decorate(x0, z0, x1, z1, (x, z, out) -> source.sample(x, z, 1, out), v);
			for (int n = 0; n < v.size; n++) {
				if (v.kind[n] == dev.voidmc.lod.core.Voxels.LOG) {
					logs.add(net.minecraft.core.BlockPos.asLong(v.x[n], v.y[n], v.z[n]));
					var face = VoidLod.get().materials().face(v.sideMaterial[n]);
					placedKinds.merge(face == null ? "?" : face.state().getBlock().getDescriptionId(), 1, Integer::sum);
				}
			}
			return logs;
		});
		java.util.Set<Long> both = new java.util.HashSet<>(real);
		both.retainAll(placed);
		// a trunk a block lower or higher (the ground a block off) still stands in the right place
		java.util.Set<Long> columnsReal = new java.util.HashSet<>(), columnsPlaced = new java.util.HashSet<>();
		real.forEach(l -> columnsReal.add((long) net.minecraft.core.BlockPos.getX(l) << 32 ^ net.minecraft.core.BlockPos.getZ(l) & 0xFFFFFFFFL));
		placed.forEach(l -> columnsPlaced.add((long) net.minecraft.core.BlockPos.getX(l) << 32 ^ net.minecraft.core.BlockPos.getZ(l) & 0xFFFFFFFFL));
		java.util.Set<Long> columnsBoth = new java.util.HashSet<>(columnsReal);
		columnsBoth.retainAll(columnsPlaced);
		// the ground under it all: the chunks' against the source's, and a map of where the logs are in each
		int[] realGround = server.computeOnServer(s -> {
			int[] g = new int[96 * 96];
			for (int x = x0; x < x1; x++) {
				for (int z = z0; z < z1; z++) {
					// the ground under the trees: past leaves, and past a trunk down to its foot
					net.minecraft.core.BlockPos.MutableBlockPos p = new net.minecraft.core.BlockPos.MutableBlockPos();
					int y = s.overworld().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
					while (s.overworld().getBlockState(p.set(x, y - 1, z)).is(net.minecraft.tags.BlockTags.LOGS)) {
						y--;
					}
					g[(x - x0) + (z - z0) * 96] = y;
				}
			}
			return g;
		});
		int[] lodGround = context.computeOnClient(mc -> {
			var source = VoidLod.get().engine().source();
			int[] g = new int[96 * 96];
			dev.voidmc.lod.core.ColumnSample c = new dev.voidmc.lod.core.ColumnSample();
			for (int x = x0; x < x1; x++) {
				for (int z = z0; z < z1; z++) {
					c.clear();
					source.sample(x, z, 1, c);
					g[(x - x0) + (z - z0) * 96] = (int) Math.ceil(c.ground - 1e-3F);
				}
			}
			return g;
		});
		int same = 0, off1 = 0;
		long absDiff = 0;
		java.awt.image.BufferedImage map = new java.awt.image.BufferedImage(96 * 4, 96 * 4, java.awt.image.BufferedImage.TYPE_INT_RGB);
		for (int i = 0; i < 96 * 96; i++) {
			int d = Math.abs(realGround[i] - lodGround[i]);
			same += d == 0 ? 1 : 0;
			off1 += d == 1 ? 1 : 0;
			absDiff += d;
			long column = (long) (x0 + i % 96) << 32 ^ (z0 + i / 96) & 0xFFFFFFFFL;
			boolean r = columnsReal.contains(column), p = columnsPlaced.contains(column);
			int shade = Math.min(255, d * 40);
			int rgb = r && p ? 0xFFFF00 : r ? 0xFF2020 : p ? 0x20FF20 : shade << 16 | shade << 8 | shade;
			for (int a = 0; a < 16; a++) {
				map.setRGB((i % 96) * 4 + a % 4, (i / 96) * 4 + a / 4, rgb);
			}
		}
		try {
			javax.imageio.ImageIO.write(map, "png", java.nio.file.Path.of("screenshots", "trees_" + spot.name() + ".png").toFile());
		} catch (java.io.IOException e) {
			throw new java.io.UncheckedIOException(e);
		}
		System.out.printf(Locale.ROOT, "[lod] ground %s: %.0f%% of columns at the chunks' height, %.0f%% a block off, mean %.2f blocks off%n",
			spot.name(), 100.0 * same / (96 * 96), 100.0 * off1 / (96 * 96), absDiff / (double) (96 * 96));
		System.out.printf(Locale.ROOT, "[lod] tree kinds %s: chunks %s | LOD %s%n", spot.name(), realKinds, placedKinds);
		System.out.printf(Locale.ROOT, "[lod] trees %s: chunks have %d logs, LOD placed %d, %d exactly the same (%.0f%%); log columns %d vs %d, %d shared (%.0f%%)%n",
			spot.name(), real.size(), placed.size(), both.size(), real.isEmpty() ? 0 : 100.0 * both.size() / real.size(), columnsReal.size(),
			columnsPlaced.size(), columnsBoth.size(), columnsReal.isEmpty() ? 0 : 100.0 * columnsBoth.size() / columnsReal.size());
	}

	/** Distance bands, in blocks across the ground: the first lies inside both pictures' chunks, a control. */
	private static final int[] BANDS = {64, 112, 144, 192, 256, 384};

	private static void compare(Spot spot, double eye, java.awt.image.BufferedImage vanilla, java.awt.image.BufferedImage lod, java.nio.file.Path out)
		throws java.io.IOException {
		int w = Math.min(vanilla.getWidth(), lod.getWidth()), h = Math.min(vanilla.getHeight(), lod.getHeight());
		double f = 0.5 * h / Math.tan(Math.toRadians(spot.fov()) / 2);
		double pitch = Math.toRadians(spot.pitch());
		int bands = BANDS.length - 1;
		double[][] sum = new double[2][bands * 3];
		double[][] texture = new double[2][bands];
		double[] diff = new double[bands];
		long[] count = new long[bands];
		for (int y = 0; y < h; y++) {
			double below = Math.atan((y - 0.5 * h) / f) + pitch;
			if (below <= 1e-4) {
				continue;
			}
			double d = eye / Math.tan(below);
			int band = -1;
			for (int b = 0; b < bands; b++) {
				if (d >= BANDS[b] && d < BANDS[b + 1]) {
					band = b;
				}
			}
			if (band < 0) {
				continue;
			}
			for (int x = 0; x + 1 < w; x++) {
				java.awt.image.BufferedImage[] pictures = {vanilla, lod};
				int[] here = new int[2];
				for (int p = 0; p < 2; p++) {
					int c = pictures[p].getRGB(x, y), n = pictures[p].getRGB(x + 1, y);
					here[p] = c;
					sum[p][band * 3] += c >> 16 & 255;
					sum[p][band * 3 + 1] += c >> 8 & 255;
					sum[p][band * 3 + 2] += c & 255;
					texture[p][band] += (Math.abs((c >> 16 & 255) - (n >> 16 & 255)) + Math.abs((c >> 8 & 255) - (n >> 8 & 255))
						+ Math.abs((c & 255) - (n & 255))) / 3.0;
				}
				diff[band] += (Math.abs((here[0] >> 16 & 255) - (here[1] >> 16 & 255)) + Math.abs((here[0] >> 8 & 255) - (here[1] >> 8 & 255))
					+ Math.abs((here[0] & 255) - (here[1] & 255))) / 3.0;
				count[band]++;
			}
		}
		for (int b = 0; b < bands; b++) {
			if (count[b] == 0) {
				continue;
			}
			double n = count[b];
			double[] lum = new double[2];
			for (int p = 0; p < 2; p++) {
				lum[p] = (0.2126 * sum[p][b * 3] + 0.7152 * sum[p][b * 3 + 1] + 0.0722 * sum[p][b * 3 + 2]) / n;
			}
			System.out.printf(Locale.ROOT,
				"[lod] seam %s %d-%d m: chunks rgb(%.0f %.0f %.0f) lum %.1f texture %.1f | lod rgb(%.0f %.0f %.0f) lum %.1f texture %.1f | brightness %+.1f%%, pixel diff %.1f%n",
				spot.name(), BANDS[b], BANDS[b + 1], sum[0][b * 3] / n, sum[0][b * 3 + 1] / n, sum[0][b * 3 + 2] / n, lum[0], texture[0][b] / n,
				sum[1][b * 3] / n, sum[1][b * 3 + 1] / n, sum[1][b * 3 + 2] / n, lum[1], texture[1][b] / n, 100 * (lum[1] / lum[0] - 1), diff[b] / n);
		}
		java.awt.image.BufferedImage both = new java.awt.image.BufferedImage(w, 2 * h, java.awt.image.BufferedImage.TYPE_INT_RGB);
		java.awt.Graphics2D g = both.createGraphics();
		g.drawImage(vanilla, 0, 0, null);
		g.drawImage(lod, 0, h, null);
		g.dispose();
		javax.imageio.ImageIO.write(both, "png", out.toFile());
	}

	private static long size(java.nio.file.Path dir) {
		try (var files = java.nio.file.Files.walk(dir)) {
			return files.filter(java.nio.file.Files::isRegularFile).mapToLong(p -> p.toFile().length()).sum();
		} catch (java.io.IOException e) {
			return -1;
		}
	}

	/**
	 * The settings screen, reached the ways a player reaches it (the button in Options, the one at the end of
	 * Video Settings), and the Low preset chosen on it while the world renders behind: the view is photographed
	 * again once the LOD has settled at the new reach and detail, and at the other presets, with the stats saying
	 * what changed. Then the F3 lines, and High restored, file included.
	 */
	private void settings(ClientGameTestContext context, Spot spot) {
		context.runOnClient(mc -> mc.gui.setScreen(new OptionsScreen(null, mc.options)));
		context.waitTicks(5);
		this.shoot(context, "options");
		context.clickScreenButton("options.void_lod.button");
		context.waitForScreen(LodSettingsScreen.class);
		context.waitTicks(40);
		this.shoot(context, "settings");
		context.runOnClient(mc -> ((LodSettingsScreen) mc.gui.screen()).choose(LodSettings.Preset.LOW));
		context.waitTicks(40);
		this.shoot(context, "settings_low");

		context.runOnClient(mc -> mc.gui.setScreen(new VideoSettingsScreen(null, mc, mc.options)));
		context.waitTicks(5);
		context.runOnClient(mc -> {
			// the button closes the list, which scrolls to it
			OptionsList list = optionsList(mc.gui.screen());
			list.setScrollAmount(list.maxScrollAmount());
		});
		context.waitTicks(5);
		this.shoot(context, "video_settings");
		context.runOnClient(mc -> {
			String label = Component.translatable("options.void_lod.button").getString();
			for (GuiEventListener entry : ((ContainerEventHandler) optionsList(mc.gui.screen())).children()) {
				for (GuiEventListener child : ((ContainerEventHandler) entry).children()) {
					if (child instanceof Button button && button.getMessage().getString().equals(label)) {
						button.onPress(new MouseButtonInfo(0, 0));
						return;
					}
				}
			}
			throw new AssertionError("no VOID LOD button in Video Settings");
		});
		context.waitForScreen(LodSettingsScreen.class);
		context.runOnClient(mc -> mc.gui.setScreen(null));
		this.settlePreset(context, spot, LodSettings.Preset.LOW);
		// the other presets, for what each costs from the same place
		for (LodSettings.Preset preset : new LodSettings.Preset[]{LodSettings.Preset.MEDIUM, LodSettings.Preset.ULTRA}) {
			context.runOnClient(mc -> VoidLod.get().change(settings -> settings.apply(preset)));
			this.settlePreset(context, spot, preset);
		}

		context.runOnClient(mc -> {
			mc.gui.hud.toggle();
			mc.debugEntries.setOverlayVisible(true);
		});
		context.waitTicks(20);
		this.shoot(context, "debug");
		context.runOnClient(mc -> {
			mc.debugEntries.setOverlayVisible(false);
			mc.gui.hud.toggle();
			VoidLod.get().change(settings -> settings.apply(LodSettings.Preset.HIGH));
			VoidLod.get().save();
		});
	}

	/** Waits for the LOD to settle at a preset just chosen, then photographs the view and logs what it drew and cost. */
	private void settlePreset(ClientGameTestContext context, Spot spot, LodSettings.Preset preset) {
		long start = System.currentTimeMillis();
		long firstFrame = context.computeOnClient(mc -> VoidLod.get().engine().frame());
		int calm = 0;
		while (System.currentTimeMillis() - start < 120_000 && calm < 3) {
			context.waitTicks(10);
			boolean idle = context.computeOnClient(mc -> {
				var engine = VoidLod.get().engine();
				return engine.frame() - firstFrame > 200 && engine.idle() && engine.queued() == 0;
			});
			calm = idle ? calm + 1 : 0;
		}
		context.waitTicks(60);
		String name = spot.name() + "_" + preset.serializedName();
		this.shoot(context, name);
		String stats = context.computeOnClient(mc -> VoidLod.get().stats() + String.format(Locale.ROOT, ", radius %d, detail %.1f/%.1f, %d fps",
			(int) VoidLod.get().radius, VoidLod.get().detail, VoidLod.get().farDetail, mc.getFps()));
		System.out.printf(Locale.ROOT, "[lod] %s: complete in %.1f s | %s%n", name, (System.currentTimeMillis() - start) / 1000.0, stats);
	}

	private static OptionsList optionsList(Screen screen) {
		for (AbstractWidget widget : Screens.getWidgets(screen)) {
			if (widget instanceof OptionsList list) {
				return list;
			}
		}
		throw new AssertionError("no options list in " + screen);
	}

	/** A view along {@code yaw} aimed down at the ground {@code reach} blocks out. */
	/** The land's height at a column, and whether a river runs there: what finding the views needs of a world. */
	interface Land {
		float height(int x, int z);

		boolean river(int x, int z);
	}

	private static Spot seam(String name, Land model, int x, int y, int z, float yaw, int reach, int fov) {
		double a = Math.toRadians(yaw);
		float ground = model.height((int) Math.round(x - Math.sin(a) * reach), (int) Math.round(z + Math.cos(a) * reach));
		float pitch = (float) Math.toDegrees(Math.atan2(y + 1.62 - ground, reach));
		return new Spot(name, x, y, z, yaw, pitch, fov);
	}

	private void shoot(ClientGameTestContext context, String name) {
		context.takeScreenshot(TestScreenshotOptions.of("lod_" + name).withSize(WIDTH, HEIGHT));
	}

	private interface ColumnTest {
		boolean test(int x, int z);
	}

	/** About the nearest point to (cx, cz) within {@code reach} blocks, on a grid {@code step} apart, that passes the test. */
	private static int[] nearest(int cx, int cz, int reach, int step, ColumnTest test) {
		for (int r = 0; r <= reach; r += step) {
			for (int x = cx - r; x <= cx + r; x += step) {
				for (int z = cz - r; z <= cz + r; z += step) {
					if ((Math.abs(x - cx) == r || Math.abs(z - cz) == r) && test.test(x, z)) {
						return new int[]{x, z};
					}
				}
			}
		}
		return null;
	}

	private void settle(ClientGameTestContext context, long ms) {
		long until = System.currentTimeMillis() + ms;
		while (System.currentTimeMillis() < until) {
			context.waitTick();
		}
	}
}
