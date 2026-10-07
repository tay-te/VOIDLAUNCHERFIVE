package dev.voidmc.gloam.gametest;

import dev.voidmc.expanse.world.terrain.EarthChunkGenerator;
import dev.voidmc.expanse.world.terrain.TerrainModel;
import dev.voidmc.gloam.GloamSettings;
import dev.voidmc.gloam.air.Atmosphere;
import dev.voidmc.gloam.air.Air;
import dev.voidmc.gloam.air.AirSample;
import dev.voidmc.gloam.sky.Haze;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.client.InactivityFpsLimit;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.BiomeManager;
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
 * {@code gloam.show.off} (false to skip the Gloam-off shots), {@code gloam.show.width}/{@code gloam.show.height}.
 */
public class GloamShowcase implements FabricClientGameTest {
	private static final String SEED = System.getProperty("gloam.show.seed", "8675309");
	private static final int DISTANCE = Integer.getInteger("gloam.show.distance", 12);
	private static final long SETTLE_MS = Long.getLong("gloam.show.settle", 20_000L);
	private static final String ONLY = System.getProperty("gloam.show.only", "");
	private static final boolean OFF = !"false".equals(System.getProperty("gloam.show.off"));
	private static final int WIDTH = Integer.getInteger("gloam.show.width", 1600);
	private static final int HEIGHT = Integer.getInteger("gloam.show.height", 900);

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

		View at(View place) {
			return new View(this.name, place.x, place.y, place.z, this.yaw, this.pitch, this.fov, this.time, this.weather, this.linger);
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
			for (View planned : views) {
				View view = this.place(world, planned, last);
				context.runOnClient(mc -> mc.options.fov().set(view.fov()));
				boolean moved = !view.samePlace(last);
				world.getServer().runCommand("time set " + view.time());
				world.getServer().runCommand("weather " + view.weather());
				world.getServer().runCommand(String.format(Locale.ROOT, "tp @a %d %d %d %.1f %.1f", view.x(), view.y(), view.z(), view.yaw(), view.pitch()));
				boolean weatherChanged = last == null || !last.weather().equals(view.weather());
				this.settle(context, moved ? SETTLE_MS : weatherChanged ? 7_000 : 1_500);
				if (OFF) {
					context.runOnClient(mc -> GloamSettings.get().enabled = false);
					context.waitTicks(20);
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

		// the sky and the light (GloamSky): dawn and dusk from the peak, the moon's light on the land, and the overcast a
		// front brings under a clear vanilla sky (moved under a thick front just before it is shot: see underFront)
		views.add(peak.at("dusk0", 12550, "clear").looking("dusk0", 90, 3));
		views.add(peak.at("dusk1", 13250, "clear").looking("dusk1", 90, 3));
		views.add(peak.at("dawn2", 23450, "clear").looking("dawn2", -90, 3));
		views.add(peak.at("moon0", 18000, "clear").looking("moon0", p.toPeak() + 180, -2));
		views.add(peak.at("moon1", 18000 + 4 * 24000, "clear").looking("moon1", p.toPeak() + 180, -2));
		View overcast = new View("overcast0", p.valleyX(), p.valleyY() + 3, p.valleyZ(), 90, 4, 70, 6000, "clear", 2);
		views.add(overcast);
		views.add(overcast.at("overcast1", 18000 + 4 * 24000, "clear"));
		// a hazy land (a cloud forest, or the haziest one found) in the morning and at noon, moved there by inHaze
		View hazy = new View("haze0", p.valleyX(), p.valleyY() + 3, p.valleyZ(), 90, 4, 70, 2000, "clear", 4);
		views.add(hazy);
		views.add(hazy.at("haze1", 6500, "clear"));
		return views;
	}

	/** Where a view is shot: the overcast and haze views go looking for their place, the rest stay where they are. */
	private View place(TestSingleplayerContext world, View planned, View last) {
		for (String kind : new String[]{"overcast", "haze"}) {
			if (planned.name().startsWith(kind)) {
				return last != null && last.name().startsWith(kind) ? planned.at(last)
					: kind.equals("haze") ? this.inHaze(world, planned) : this.underFront(world, planned);
			}
		}
		return planned;
	}

	/**
	 * Moves a view to land under a thick front that is not raining (the front 0.62..0.74: the sky covered, no shower),
	 * where the front will be once the chunks have settled, so the overcast is Gloam's own under a clear vanilla sky.
	 */
	private View underFront(TestSingleplayerContext world, View view) {
		int[] at = world.getServer().computeOnServer(server -> {
			ServerLevel level = server.overworld();
			Air air = new Air(BiomeManager.obfuscateSeed(level.getSeed()), GloamSettings.get().seasonDays);
			long gameTime = level.getGameTime() + SETTLE_MS / 50 + 60;
			float year = air.year(view.time());
			TerrainModel model = level.getChunkSource().getGenerator() instanceof EarthChunkGenerator earth
				? earth.model(level.getChunkSource().randomState()) : null;
			int bx = view.x(), bz = view.z();
			float best = Float.MAX_VALUE;
			for (int x = view.x() - 2400; x <= view.x() + 2400; x += 64) {
				for (int z = view.z() - 2400; z <= view.z() + 2400; z += 64) {
					if (model != null && model.sample(x, z).height < level.getSeaLevel() + 3) {
						continue;
					}
					// the whole neighbourhood under the front, so the drift while chunks load does not carry it off
					float worst = 0;
					for (int[] d : new int[][]{{0, 0}, {-160, 0}, {160, 0}, {0, -160}, {0, 160}}) {
						float front = air.front(x + d[0], z + d[1], gameTime, year, 0.6F);
						worst = Math.max(worst, Math.abs(front - 0.68F));
					}
					float score = worst + (float) Math.hypot(x - view.x(), z - view.z()) / 40000.0F;
					if (score < best) {
						best = score;
						bx = x;
						bz = z;
					}
				}
			}
			// open ground nearby, out of the trees, so the view has its sky
			for (int r = 0; r <= 256; r += 32) {
				for (int i = 0; i < (r == 0 ? 1 : 8); i++) {
					int x = bx + (int) (Math.cos(i * Math.PI / 4) * r), z = bz + (int) (Math.sin(i * Math.PI / 4) * r);
					if (open(level, x, z)) {
						int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
						if (y > level.getSeaLevel()) {
							// above the treetops round about, so the view has the sky and the land under it
							int top = y;
							for (int[] d : new int[][]{{-24, 0}, {24, 0}, {0, -24}, {0, 24}}) {
								level.getChunk((x + d[0]) >> 4, (z + d[1]) >> 4);
								top = Math.max(top, level.getHeight(Heightmap.Types.MOTION_BLOCKING, x + d[0], z + d[1]));
							}
							return new int[]{x, top + 4, z};
						}
					}
				}
			}
			level.getChunk(bx >> 4, bz >> 4);
			return new int[]{bx, level.getHeight(Heightmap.Types.MOTION_BLOCKING, bx, bz) + 4, bz};
		});
		System.out.printf(Locale.ROOT, "[gloam] %s: under a front at %d %d %d%n", view.name(), at[0], at[1], at[2]);
		return new View(view.name(), at[0], at[1], at[2], view.yaw(), view.pitch(), view.fov(), view.time(), view.weather(), view.linger());
	}

	/** Moves a view into the haziest land of Gloam's haze table near it (a cloud forest, if one is near). */
	private View inHaze(TestSingleplayerContext world, View view) {
		int[] at = world.getServer().computeOnServer(server -> {
			ServerLevel level = server.overworld();
			BlockPos origin = new BlockPos(view.x(), 80, view.z());
			// the murkiest first, then any of the hazy ones
			for (float most : new float[]{80, 160, 320}) {
				var found = level.findClosestBiome3d(b -> b.unwrapKey().map(k -> Haze.BIOMES.get(k.identifier()))
					.map(e -> e.reach() <= most).orElse(false), origin, 6400, 64, 64);
				if (found != null) {
					BlockPos pos = found.getFirst();
					System.out.printf(Locale.ROOT, "[gloam] %s: %s at %d %d%n", view.name(), found.getSecond().getRegisteredName(), pos.getX(), pos.getZ());
					level.getChunk(pos.getX() >> 4, pos.getZ() >> 4);
					return new int[]{pos.getX(), level.getHeight(Heightmap.Types.MOTION_BLOCKING, pos.getX(), pos.getZ()) + 3, pos.getZ()};
				}
			}
			return new int[]{view.x(), view.y(), view.z()};
		});
		return new View(view.name(), at[0], at[1], at[2], view.yaw(), view.pitch(), view.fov(), view.time(), view.weather(), view.linger());
	}

	/** Whether the ground at (x, z) and around it is out from under trees. */
	private static boolean open(ServerLevel level, int x, int z) {
		for (int[] d : new int[][]{{0, 0}, {-12, 0}, {12, 0}, {0, -12}, {0, 12}}) {
			level.getChunk((x + d[0]) >> 4, (z + d[1]) >> 4);
			if (level.getHeight(Heightmap.Types.MOTION_BLOCKING, x + d[0], z + d[1])
				> level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x + d[0], z + d[1]) + 1) {
				return false;
			}
		}
		return true;
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
