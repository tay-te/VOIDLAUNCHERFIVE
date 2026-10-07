package dev.voidmc.gloam.gametest;

import dev.voidmc.expanse.world.terrain.EarthChunkGenerator;
import dev.voidmc.expanse.world.terrain.TerrainModel;
import dev.voidmc.gloam.GloamSettings;
import dev.voidmc.gloam.air.Atmosphere;
import dev.voidmc.gloam.air.AirSample;
import dev.voidmc.gloam.air.Air;
import dev.voidmc.gloam.far.FarAir;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.InactivityFpsLimit;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Photographs an Earth world (Expanse, with VOID LOD drawing the far land) at the times and places Gloam changes most,
 * each view with Gloam off and on: a valley at dawn, a meadow at noon, the edge of water at dusk, the night under a full
 * and a new moon, rain, and the land from a peak. Each part of Gloam adds its own views to {@link #views}.
 *
 * <p>Run with {@code ./gradlew runClientGameTest}; screenshots land in build/run/clientGameTest/screenshots, and each
 * view logs the air Gloam saw there ({@code [gloam] name: ...}). Properties (pass as -P to Gradle):
 * {@code gloam.show.seed}, {@code gloam.show.distance} (vanilla render distance), {@code gloam.show.settle} (ms to let
 * chunks and the LOD load after moving), {@code gloam.show.only} (comma-separated view names),
 * {@code gloam.show.off} (false to skip the Gloam-off shots), {@code gloam.show.clouds} (fancy, fast or off), {@code gloam.show.width}/{@code gloam.show.height}.
 */
public class GloamShowcase implements FabricClientGameTest {
	private static final String SEED = System.getProperty("gloam.show.seed", "8675309");
	private static final int DISTANCE = Integer.getInteger("gloam.show.distance", 12);
	private static final long SETTLE_MS = Long.getLong("gloam.show.settle", 20_000L);
	private static final String ONLY = System.getProperty("gloam.show.only", "");
	private static final boolean OFF = !"false".equals(System.getProperty("gloam.show.off"));
	private static final int WIDTH = Integer.getInteger("gloam.show.width", 1600);
	private static final int HEIGHT = Integer.getInteger("gloam.show.height", 900);
	private static final CloudStatus CLOUDS = switch (System.getProperty("gloam.show.clouds", "fancy")) {
		case "off" -> CloudStatus.OFF;
		case "fast" -> CloudStatus.FAST;
		default -> CloudStatus.FANCY;
	};

	/**
	 * A view: where the camera stands and looks, through what lens, at what time ({@code time set}: the day matters for
	 * the moon, day 0 a full moon, day 4 a new one) and weather, and how many seconds to wait there before shooting (for
	 * motes and weather to build up).
	 */
	record View(String name, int x, int y, int z, float yaw, float pitch, int fov, long time, String weather, int linger) {
		View at(String name, long time, String weather) {
			return new View(name, this.x, this.y, this.z, this.yaw, this.pitch, this.fov, time, weather, this.linger);
		}

		View looking(String name, float yaw, float pitch) {
			return new View(name, this.x, this.y, this.z, yaw, pitch, this.fov, this.time, this.weather, this.linger);
		}

		boolean samePlace(View o) {
			return o != null && o.x == this.x && o.y == this.y && o.z == this.z;
		}
	}

	/** The places the views are taken from, found once on the server. */
	record Places(int peakX, int peakZ, int peakY, int valleyX, int valleyZ, int valleyY, int waterX, int waterZ, int waterY, float toPeak) {
	}

	@Override
	public void runTest(ClientGameTestContext context) {
		context.runOnClient(mc -> {
			if (!mc.gui.hud.isHidden()) {
				mc.gui.hud.toggle();
			}
			mc.options.renderDistance().set(DISTANCE);
			mc.options.enableVsync().set(false);
			mc.options.framerateLimit().set(260);
			mc.options.inactivityFpsLimit().set(InactivityFpsLimit.MINIMIZED);
			// Fabric's game tests switch the clouds off; the showcase wants them as a player has them
			mc.options.cloudStatus().set(CLOUDS);
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
			world.getServer().runCommand("gamemode spectator @a");

			Places places = world.getServer().computeOnServer(server -> find(server.overworld()));
			System.out.printf(Locale.ROOT, "[gloam] places: %s%n", places);
			List<View> views = views(places);
			if (!ONLY.isEmpty()) {
				List<String> names = List.of(ONLY.split(","));
				views.removeIf(v -> !names.contains(v.name()));
			}
			View last = null;
			for (View view : views) {
				context.runOnClient(mc -> mc.options.fov().set(view.fov()));
				boolean moved = !view.samePlace(last);
				world.getServer().runCommand("time set " + view.time());
				world.getServer().runCommand("weather " + view.weather());
				world.getServer().runCommand(String.format(Locale.ROOT, "tp @a %d %d %d %.1f %.1f", view.x(), view.y(), view.z(), view.yaw(), view.pitch()));
				boolean weatherChanged = last == null || !last.weather().equals(view.weather());
				this.settle(context, moved ? SETTLE_MS : weatherChanged ? 7_000 : 1_500);
				if (view.name().startsWith("storm")) {
					this.aimAtStorm(context, world, view, places);
				}
				int offFps = -1;
				if (OFF) {
					context.runOnClient(mc -> GloamSettings.get().enabled = false);
					context.waitTicks(40);
					offFps = context.computeOnClient(mc -> mc.getFps());
					this.shoot(context, view.name() + "_off");
				}
				context.runOnClient(mc -> GloamSettings.get().enabled = true);
				context.waitTicks(20 + view.linger() * 20);
				this.shoot(context, view.name() + "_on");
				AirSample air = context.computeOnClient(mc -> Atmosphere.get().here());
				int fps = context.computeOnClient(mc -> mc.getFps());
				System.out.printf(Locale.ROOT, "[gloam] %s: %s, hour %.1f, front %.2f, cloud %.2f, rain %.2f, storm %.2f, mist %.2f (top y=%.0f), wind %.1f+%.2f, "
						+ "wet %.2f, lowness %.2f, water %.2f, indoors %.2f, %d fps%n", view.name(), air.season().serializedName(), air.hour(), air.front(),
					air.cloudCover(), air.rain(), air.storm(), air.mist(), air.mistTop(), air.wind().speed(), air.wind().gust(), air.wetness(),
					air.climate().lowness(), air.climate().water(), air.indoors(), fps);
				String far = context.computeOnClient(mc -> FarAir.stats());
				System.out.printf(Locale.ROOT, "[gloam] %s: %s; %d fps off, %d fps on%n", view.name(), far, offFps, fps);
				last = view;
			}
		}
	}

	/** The views, in order (shot in a row from the same place where they can be, so chunks only load once per place). */
	static List<View> views(Places p) {
		List<View> views = new ArrayList<>();
		View valley = new View("dawn0", p.valleyX(), p.valleyY() + 3, p.valleyZ(), p.toPeak(), 4, 70, 23400, "clear", 2);
		views.add(valley);
		views.add(valley.at("dawn1", 1200, "clear"));
		views.add(valley.at("noon0", 6000, "clear"));
		views.add(valley.at("rain0", 6000, "rain"));
		views.add(valley.at("night0", 18000, "clear"));
		views.add(valley.at("night1", 18000 + 4 * 24000, "clear"));
		View peak = new View("peak0", p.peakX(), p.peakY() + 20, p.peakZ(), p.toPeak() + 180, 6, 70, 6000, "clear", 2);
		views.add(peak);
		views.add(peak.at("peak1", 23500, "clear"));
		views.add(peak.at("peak2", 12600, "clear"));
		View water = new View("water0", p.waterX(), p.waterY() + 2, p.waterZ(), 0, 4, 70, 13000, "clear", 4);
		views.add(water);
		// the far air: a long lens from the peak at noon over broken cloud, mist in the far valleys at sunrise, the deck
		// at dusk, and a storm on the horizon (aimed at the strongest weather 4-12 km off when it is shot)
		View far = new View("far0", p.peakX(), p.peakY() + 20, p.peakZ(), p.toPeak() + 180, 3, 30, 6000, "clear", 2);
		views.add(far);
		views.add(far.at("mist0", 300, "clear").looking("mist0", p.toPeak() + 180, 12));
		views.add(new View("mist1", p.peakX(), p.peakY() + 20, p.peakZ(), p.toPeak() + 180, 8, 70, 23800, "clear", 2));
		views.add(new View("dusk0", p.peakX(), p.peakY() + 20, p.peakZ(), 90, 2, 70, 12300, "clear", 2));
		views.add(new View("storm0", p.peakX(), p.peakY() + 20, p.peakZ(), 0, 1, 45, 7000, "clear", 2));
		View low = new View("sky0", p.valleyX(), p.valleyY() + 3, p.valleyZ(), p.toPeak(), -18, 70, 4000, "clear", 2);
		views.add(low);
		return views;
	}

	/** The highest ground within 3 km, a low valley floor within 2 km of it, and open water nearest the valley. */
	static Places find(ServerLevel level) {
		if (!(level.getChunkSource().getGenerator() instanceof EarthChunkGenerator earth)) {
			return new Places(0, 0, 120, 64, 64, 70, 128, 128, 63, 0);
		}
		TerrainModel model = earth.model(level.getChunkSource().randomState());
		int bx = 0, bz = 0, lx = 0, lz = 0, wx = 0, wz = 0;
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
				if (c.height > 66 && c.height < low && Math.hypot(x - bx, z - bz) > 900) {
					low = c.height;
					lx = x;
					lz = z;
				}
			}
		}
		int sea = level.getSeaLevel();
		double nearest = Double.MAX_VALUE;
		for (int x = lx - 1500; x <= lx + 1500; x += 24) {
			for (int z = lz - 1500; z <= lz + 1500; z += 24) {
				var c = model.sample(x, z);
				double d = Math.hypot(x - lx, z - lz);
				if ((c.height < sea - 2 || c.nearRiver()) && d < nearest) {
					nearest = d;
					wx = x;
					wz = z;
				}
			}
		}
		for (int[] at : new int[][]{{bx, bz}, {lx, lz}, {wx, wz}}) {
			level.getChunk(at[0] >> 4, at[1] >> 4);
		}
		int peakY = level.getHeight(Heightmap.Types.MOTION_BLOCKING, bx, bz);
		int valleyY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, lx, lz);
		int waterY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, wx, wz);
		float toPeak = (float) Math.toDegrees(Math.atan2(-(bx - lx), bz - lz));
		return new Places(bx, bz, peakY, lx, lz, valleyY, wx, wz, waterY, toPeak);
	}

	/**
	 * Turns the camera toward the strongest weather between 4 and 12 km off, as the fronts will be a moment from now: a
	 * storm if there is one, else the heaviest shower, else the cloudiest sky.
	 */
	private void aimAtStorm(ClientGameTestContext context, TestSingleplayerContext world, View view, Places p) {
		float[] best = context.computeOnClient(mc -> {
			Air air = Atmosphere.get().air();
			if (air == null || mc.level == null) {
				return new float[]{view.yaw(), 0, 0};
			}
			long gameTime = mc.level.getGameTime() + 60;
			float year = air.year(mc.level.getOverworldClockTime());
			double cx = view.x() + 0.5, cz = view.z() + 0.5;
			float bestScore = -1, bestYaw = view.yaw(), bestDist = 0;
			for (int a = 0; a < 360; a += 3) {
				double rad = Math.toRadians(a);
				for (int d = 4000; d <= 12000; d += 250) {
					// Minecraft's yaw: 0 looks south (+z), 90 west (-x)
					double x = cx - Math.sin(rad) * d, z = cz + Math.cos(rad) * d;
					float front = air.front(x, z, gameTime, year, 0.6F);
					float score = Air.storm(front, year) * 2 + Air.showers(front) + front * 0.1F;
					if (score > bestScore) {
						bestScore = score;
						bestYaw = a;
						bestDist = d;
					}
				}
			}
			return new float[]{bestYaw, bestScore, bestDist};
		});
		System.out.printf(Locale.ROOT, "[gloam] %s: aiming at yaw %.0f, %.0f blocks off (score %.2f)%n", view.name(), best[0], best[2], best[1]);
		world.getServer().runCommand(String.format(Locale.ROOT, "tp @a %d %d %d %.1f %.1f", view.x(), view.y(), view.z(), best[0], view.pitch()));
		context.waitTicks(10);
	}

	private void shoot(ClientGameTestContext context, String name) {
		context.takeScreenshot(TestScreenshotOptions.of("gloam_" + name).withSize(WIDTH, HEIGHT));
	}

	private void settle(ClientGameTestContext context, long ms) {
		long until = System.currentTimeMillis() + ms;
		while (System.currentTimeMillis() < until) {
			context.waitTick();
		}
	}
}
