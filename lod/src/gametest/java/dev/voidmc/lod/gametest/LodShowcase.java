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
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
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
 * shows).
 */
public class LodShowcase implements FabricClientGameTest {
	private static final String SEED = System.getProperty("lod.show.seed", "8675309");
	private static final int DISTANCE = Integer.getInteger("lod.show.distance", 8);
	private static final long SETTLE_MS = Long.getLong("lod.show.settle", 30_000L);
	private static final int VIEWS = Integer.getInteger("lod.show.views", 10);
	/** Only the views with these names, comma separated (all when unset). */
	private static final String ONLY = System.getProperty("lod.show.only", "");
	private static final int WIDTH = Integer.getInteger("lod.show.width", 1600);
	private static final int HEIGHT = Integer.getInteger("lod.show.height", 900);
	private static final boolean OFF = Boolean.parseBoolean(System.getProperty("lod.show.off", "true"));
	private static final boolean SETTINGS = Boolean.getBoolean("lod.show.settings");
	private static final boolean COMPARE = Boolean.getBoolean("lod.show.compare");

	/** A view: where the camera stands and looks, through what lens, in which dimension, at what time and weather. */
	private record Spot(String name, String dimension, int x, int y, int z, float yaw, float pitch, int fov, int time, String weather) {
		Spot(String name, int x, int y, int z, float yaw, float pitch) {
			this(name, x, y, z, yaw, pitch, 70);
		}

		Spot(String name, int x, int y, int z, float yaw, float pitch, int fov) {
			this(name, "minecraft:overworld", x, y, z, yaw, pitch, fov, 6000, "clear");
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
		try (TestSingleplayerContext world = context.worldBuilder()
			.setUseConsistentSettings(false)
			.adjustSettings(s -> {
				s.setSeed(SEED);
				s.setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE);
				s.setAllowCommands(true);
			})
			.create()) {
			world.getServer().runCommand("gamerule advance_time false");
			world.getServer().runCommand("gamerule advance_weather false");
			world.getServer().runCommand("weather clear");
			world.getServer().runCommand("time set 6000");
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
				long start = System.currentTimeMillis();
				long firstFrame = context.computeOnClient(mc -> VoidLod.get().engine().frame());
				// wait for the LOD to finish building what it wants (twice in a row, so a view that has not caught up
				// with the teleport yet does not count), then a few frames more to upload and fade in
				int calm = 0;
				while (System.currentTimeMillis() - start < 180_000 && calm < 3) {
					context.waitTicks(10);
					// done once it has drawn a while from here, holds tiles, and has nothing left to build
					String state = context.computeOnClient(mc -> {
						var engine = VoidLod.get().engine();
						boolean done = engine.frame() - firstFrame > 200 && engine.tileCount() > 0 && engine.idle() && engine.queued() == 0;
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
				this.shoot(context, spot.name() + "_on");
				String stats = context.computeOnClient(mc -> VoidLod.get().stats() + String.format(Locale.ROOT, ", %d fps", mc.getFps()));
				System.out.printf(Locale.ROOT, "[lod] %s: complete in %.1f s | %s%n", spot.name(), loaded / 1000.0, stats);
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
