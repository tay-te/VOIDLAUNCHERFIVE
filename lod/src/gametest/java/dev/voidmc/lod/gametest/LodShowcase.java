package dev.voidmc.lod.gametest;

import dev.voidmc.expanse.world.terrain.EarthChunkGenerator;
import dev.voidmc.expanse.world.terrain.TerrainModel;
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
	private static final boolean MULTIPLAYER = Boolean.getBoolean("lod.show.mp");
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
				if (!(level.getChunkSource().getGenerator() instanceof EarthChunkGenerator earth)) {
					found.add(new Spot("view0", 0, 200, 0, 0, 12));
					return found;
				}
				TerrainModel model = earth.model(level.getChunkSource().randomState());
				// the highest ground within 3 km, and the lowest dry land within 2 km of it
				int bx = 0, bz = 0, lx = 0, lz = 0;
				float best = -1, low = Float.MAX_VALUE;
				for (int x = -3000; x <= 3000; x += 48) {
					for (int z = -3000; z <= 3000; z += 48) {
						float h = model.sample(x, z).height;
						if (h > best) {
							best = h;
							bx = x;
							bz = z;
						}
					}
				}
				for (int x = bx - 2000; x <= bx + 2000; x += 40) {
					for (int z = bz - 2000; z <= bz + 2000; z += 40) {
						var c = model.sample(x, z);
						if (c.height > 68 && c.height < low && !c.nearRiver() && Math.hypot(x - bx, z - bz) > 900) {
							low = c.height;
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
				int[] deep = nearest(bx, bz, 6000, 64, (x, z) -> model.sample(x, z).height < sea - 24);
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
						var c = model.sample(x, z);
						if (c.height < sea + 4 || c.nearRiver()) {
							return false;
						}
						Holder<Biome> biome = biomes.getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock((int) c.height), QuartPos.fromBlock(z));
						String path = biome.unwrapKey().map(k -> k.identifier().getPath()).orElse("");
						return CONIFER_BIOMES.contains(path) && (biome.value().getBaseTemperature() < 0.15F) == snowy;
					});
					if (forest != null) {
						int ground = (int) model.sample(forest[0], forest[1]).height;
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
			}
			if (SETTINGS && !spots.isEmpty()) {
				this.settings(context, spots.get(Math.min(VIEWS, spots.size()) - 1));
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
					&& (!changes || settled || engine.dirtyDrawn() == 0 && (ingest == null || ingest.queued() == 0));
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
	 * The same on a dedicated server: no generator to sample, so the LOD draws only the land the client has seen.
	 * The build is photographed from far off; the land around it that was never loaded is not drawn at all.
	 */
	private void multiplayer(ClientGameTestContext context) {
		java.util.Properties properties = new java.util.Properties();
		properties.setProperty("level-seed", SEED);
		properties.setProperty("view-distance", String.valueOf(DISTANCE));
		properties.setProperty("gamemode", "spectator");
		try (TestDedicatedServerContext server = context.worldBuilder().createServer(properties);
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
		}
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
	private static Spot seam(String name, TerrainModel model, int x, int y, int z, float yaw, int reach, int fov) {
		double a = Math.toRadians(yaw);
		float ground = model.sample((int) Math.round(x - Math.sin(a) * reach), (int) Math.round(z + Math.cos(a) * reach)).height;
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
