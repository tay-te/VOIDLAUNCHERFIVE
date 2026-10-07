package dev.voidmc.gloam.gametest;

import dev.voidmc.expanse.world.terrain.EarthChunkGenerator;
import dev.voidmc.expanse.world.terrain.TerrainModel;
import dev.voidmc.gloam.GloamSettings;
import dev.voidmc.gloam.air.Atmosphere;
import dev.voidmc.gloam.air.Air;
import dev.voidmc.gloam.air.AirSample;
import dev.voidmc.gloam.compat.Expanse;
import dev.voidmc.gloam.sky.GloamSky;
import dev.voidmc.gloam.sky.Haze;
import dev.voidmc.gloam.sound.Soundscape;
import dev.voidmc.gloam.motes.Motes;
import dev.voidmc.gloam.far.FarAir;
import dev.voidmc.gloam.season.Seasons;
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
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Photographs an Earth world (Expanse, with VOID LOD drawing the far land) at the times and places Gloam changes most,
 * each view with Gloam off and on: a valley at dawn, a meadow at noon, the edge of water at dusk, the night under a full
 * and a new moon, rain, and the land from a peak. Each part of Gloam adds its own views to {@link #views}.
 *
 * <p>Run with {@code ./gradlew runClientGameTest}; screenshots land in build/run/clientGameTest/screenshots, and each
 * view logs the air Gloam saw there ({@code [gloam] name: ...}) and the soundscape's beds, with Gloam off and on
 * ({@code [gloam] name sound: ...}). Properties (pass as -P to Gradle):
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

		View at(View place) {
			return new View(this.name, place.x, place.y, place.z, this.yaw, this.pitch, this.fov, this.time, this.weather, this.linger);
		}

		boolean samePlace(View o) {
			return o != null && o.x == this.x && o.y == this.y && o.z == this.z;
		}
	}

	/**
	 * Where the motes' views are taken: a spot under a broadleaf canopy (the falling leaves), one in the snow (breath and
	 * snow) and a waterfall the showcase builds itself (spray).
	 */
	/**
	 * The highest mountain range near the spawn and the places it is shot from: its summit, a valley floor a few km east
	 * of it (so the setting sun goes down behind it), and a spot on its flank between the two at about mid-height.
	 */
	record RangePlaces(int peakX, int peakY, int peakZ, int valleyX, int valleyY, int valleyZ, int flankX, int flankY, int flankZ) {
		/** The yaw from (x, z) toward (tx, tz), in Minecraft's degrees. */
		static float yaw(int x, int z, int tx, int tz) {
			return (float) Math.toDegrees(Math.atan2(-(tx - x), tz - z));
		}

		/** The pitch from (x, y, z) up (negative) or down to (tx, ty, tz). */
		static float pitch(int x, int y, int z, int tx, int ty, int tz) {
			return (float) -Math.toDegrees(Math.atan2(ty - y, Math.hypot(tx - x, tz - z)));
		}
	}

	private static RangePlaces rangePlaces = new RangePlaces(0, 200, 0, 2000, 80, 0, 1000, 140, 0);
	/** Whether the ranges have been looked for in this run (just before the first range view, see place). */
	private static boolean rangesFound;

	record MotePlaces(int forestX, int forestY, int forestZ, float forestYaw, int coldX, int coldY, int coldZ, float coldYaw, int fallX, int fallY, int fallZ) {
	}

	private static MotePlaces motePlaces = new MotePlaces(0, 80, 0, 0, 0, 80, 0, 0, 0, 80, 0);

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
			motePlaces = world.getServer().computeOnServer(server -> findMotePlaces(server.overworld(), places));
			System.out.printf(Locale.ROOT, "[gloam] mote places: %s%n", motePlaces);
			hill = world.getServer().computeOnServer(server -> findHill(server.overworld(), places));
			System.out.printf(Locale.ROOT, "[gloam] hill: %d %d %d%n", hill[0], hill[1], hill[2]);
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
				if (view.name().startsWith("storm")) {
					this.aimAtStorm(context, world, view, places);
				}
				awaitLod(context, view);
				int fpsOff = -1;
				if (OFF) {
					context.runOnClient(mc -> GloamSettings.get().enabled = false);
					context.waitTicks(40);
					awaitSeasons(context);
					this.shoot(context, view.name() + "_off");
					String quiet = context.computeOnClient(mc -> Soundscape.describe());
					System.out.printf(Locale.ROOT, "[gloam] %s sound (off): %s%n", view.name(), quiet);
					fpsOff = context.computeOnClient(mc -> mc.getFps());
				}
				context.runOnClient(mc -> Motes.stats());
				context.runOnClient(mc -> GloamSettings.get().enabled = true);
				context.waitTicks(20 + view.linger() * 20);
				awaitSeasons(context);
				awaitBreath(context, view);
				this.shoot(context, view.name() + "_on");
				AirSample air = context.computeOnClient(mc -> Atmosphere.get().here());
				int fps = context.computeOnClient(mc -> mc.getFps());
				System.out.printf(Locale.ROOT, "[gloam] %s: %s, hour %.1f, front %.2f, cloud %.2f, rain %.2f, storm %.2f, mist %.2f (top y=%.0f), wind %.1f+%.2f, "
						+ "wet %.2f, lowness %.2f, water %.2f, indoors %.2f, %d fps%n", view.name(), air.season().serializedName(), air.hour(), air.front(),
					air.cloudCover(), air.rain(), air.storm(), air.mist(), air.mistTop(), air.wind().speed(), air.wind().gust(), air.wetness(),
					air.climate().lowness(), air.climate().water(), air.indoors(), fps);
				String sky = context.computeOnClient(mc -> {
					Expanse.Sampler country = Expanse.sampler(mc.level);
					var eye = mc.gameRenderer.mainCamera().position();
					return GloamSky.describe() + "; country " + (country == null ? "none" : country.at((int) eye.x, (int) eye.z));
				});
				System.out.printf(Locale.ROOT, "[gloam] %s %s%n", view.name(), sky);
				String beds = context.computeOnClient(mc -> Soundscape.describe());
				System.out.printf(Locale.ROOT, "[gloam] %s sound: %s%n", view.name(), beds);
				String motes = context.computeOnClient(mc -> Motes.stats() + "; particles " + mc.particleEngine.countParticles());
				System.out.printf(Locale.ROOT, "[gloam] %s motes: %s; %d fps off, %d on%n", view.name(), motes, fpsOff, fps);
				String far = context.computeOnClient(mc -> FarAir.stats());
				System.out.printf(Locale.ROOT, "[gloam] %s: %s; %d fps off, %d fps on%n", view.name(), far, fpsOff, fps);
				String season = context.computeOnClient(mc -> Seasons.stats());
				System.out.printf(Locale.ROOT, "[gloam] %s seasons: %s%n", view.name(), season);
				last = view;
			}
			String clips = context.computeOnClient(mc -> Soundscape.loadedClips());
			System.out.printf(Locale.ROOT, "[gloam] sound clips %s%n", clips);
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
		addMoteViews(views, p);
		// the far air: a long lens from the peak at noon over broken cloud, mist in the far valleys at sunrise, the deck
		// at dusk, and a storm on the horizon (aimed at the strongest weather 4-12 km off when it is shot)
		View far = new View("far0", p.peakX(), p.peakY() + 20, p.peakZ(), p.toPeak() + 180, 3, 30, 6000, "clear", 2);
		views.add(far);
		views.add(far.at("mist0", 300, "clear").looking("mist0", p.toPeak() + 180, 12));
		views.add(new View("mist1", p.peakX(), p.peakY() + 20, p.peakZ(), p.toPeak() + 180, 8, 70, 23800, "clear", 2));
		views.add(new View("deck0", p.peakX(), p.peakY() + 20, p.peakZ(), 90, 2, 70, 12300, "clear", 2));
		views.add(new View("storm0", p.peakX(), p.peakY() + 20, p.peakZ(), 0, 1, 45, 7000, "clear", 2));
		View low = new View("sky0", p.valleyX(), p.valleyY() + 3, p.valleyZ(), p.toPeak(), -18, 70, 4000, "clear", 2);
		views.add(low);
		addRangeViews(views);
		addSeasonViews(views, p);
		return views;
	}

	/**
	 * The highest ranges (the Earth world's tallest land, some 585 blocks): the golden hour from the valley east of them
	 * with the sun going down behind them, the morning mist in the valleys from their flank at an autumn sunrise, and the ranges
	 * from the summit at noon among the cloud layers (the stratus below, the high deck above).
	 */
	static void addRangeViews(List<View> views) {
		RangePlaces r = rangePlaces;
		// the flank camera a little above its ground, over the mist that settles on the slope, looking down on the valleys
		int vy = r.valleyY() + 3, fy = r.flankY() + 30, py = r.peakY() + 6;
		float toPeak = RangePlaces.yaw(r.valleyX(), r.valleyZ(), r.peakX(), r.peakZ());
		float up = Math.max(-25, RangePlaces.pitch(r.valleyX(), vy, r.valleyZ(), r.peakX(), r.peakY(), r.peakZ()) + 4);
		views.add(new View("range0", r.valleyX(), vy, r.valleyZ(), toPeak, up, 55, 11700, "clear", 2));
		views.add(new View("range1", r.valleyX(), vy, r.valleyZ(), toPeak, up, 55, 12350, "clear", 2));
		float toValley = RangePlaces.yaw(r.flankX(), r.flankZ(), r.valleyX(), r.valleyZ());
		views.add(new View("range2", r.flankX(), fy, r.flankZ(), toValley, 14, 70, 14 * 24000 + 23900, "clear", 2));
		views.add(new View("range3", r.flankX(), fy, r.flankZ(), toValley, 14, 70, 15 * 24000 + 600, "clear", 2));
		float fromPeak = RangePlaces.yaw(r.peakX(), r.peakZ(), r.valleyX(), r.valleyZ());
		views.add(new View("range4", r.peakX(), py, r.peakZ(), fromPeak, 4, 70, 6000, "clear", 2));
		views.add(new View("range5", r.peakX(), py, r.peakZ(), fromPeak + 120, 4, 70, 6000, "clear", 2));
	}

	/**
	 * Finds the ranges to shoot: of the highest summits within 8 km (the tallest land first), the one whose sky will be
	 * fairest while its views are shot (the fronts over it, from Gloam's own model, a few minutes on), with a valley floor
	 * 2.5..6 km east of it that sees the summit over the land between, and a spot on its flank at about mid-height.
	 */
	static RangePlaces findRanges(ServerLevel level) {
		if (!(level.getChunkSource().getGenerator() instanceof EarthChunkGenerator earth)) {
			return rangePlaces;
		}
		TerrainModel model = earth.model(level.getChunkSource().randomState());
		// the summits: the highest sample of each 1.5 km square, highest first
		java.util.Map<Long, int[]> tops = new java.util.HashMap<>();
		for (int x = -8000; x <= 8000; x += 96) {
			for (int z = -8000; z <= 8000; z += 96) {
				int h = Math.round(model.sample(x, z).height);
				long key = ((long) Math.floorDiv(x, 1536) << 32) ^ (Math.floorDiv(z, 1536) & 0xFFFFFFFFL);
				int[] t = tops.get(key);
				if (t == null || h > t[2]) {
					tops.put(key, new int[]{x, z, h});
				}
			}
		}
		List<int[]> summits = new ArrayList<>(tops.values());
		summits.sort((a, b) -> b[2] - a[2]);
		Air air = new Air(BiomeManager.obfuscateSeed(level.getSeed()), GloamSettings.get().seasonDays);
		// the range views take a few minutes from here; the fronts are read half way through
		long gameTime = level.getGameTime() + 3 * SETTLE_MS / 50 + 2400;
		int sea = level.getSeaLevel();
		RangePlaces best = null;
		float bestScore = Float.MAX_VALUE;
		for (int[] top : summits.subList(0, Math.min(12, summits.size()))) {
			if (top[2] < summits.get(0)[2] - 90) {
				break;
			}
			RangePlaces r = range(model, sea, top[0], top[1], top[2]);
			if (r == null) {
				continue;
			}
			// the stormiest sky along the line from the valley to the summit, at noon and at the autumn dawn
			float worst = 0;
			for (int i = 0; i <= 4; i++) {
				double x = r.valleyX() + (r.peakX() - r.valleyX()) * i / 4.0, z = r.valleyZ() + (r.peakZ() - r.valleyZ()) * i / 4.0;
				worst = Math.max(worst, air.front(x, z, gameTime, air.year(6000), 0.6F));
				worst = Math.max(worst, air.front(x, z, gameTime, air.year(15 * 24000), 0.6F));
			}
			// fair weather first (a few clouds are welcome), then the tallest
			float score = Math.max(0, worst - 0.4F) * 1000 - r.peakY() * 0.1F;
			System.out.printf(Locale.ROOT, "[gloam] range candidate %d %d (y %d): front up to %.2f%n", top[0], top[1], top[2], worst);
			if (score < bestScore) {
				bestScore = score;
				best = r;
			}
		}
		if (best == null) {
			return rangePlaces;
		}
		int[][] at = {{best.peakX(), best.peakZ()}, {best.valleyX(), best.valleyZ()}, {best.flankX(), best.flankZ()}};
		int[] ys = new int[3];
		for (int i = 0; i < 3; i++) {
			level.getChunk(at[i][0] >> 4, at[i][1] >> 4);
			ys[i] = level.getHeight(Heightmap.Types.MOTION_BLOCKING, at[i][0], at[i][1]);
		}
		return new RangePlaces(best.peakX(), ys[0], best.peakZ(), best.valleyX(), ys[1], best.valleyZ(), best.flankX(), ys[2], best.flankZ());
	}

	/**
	 * One summit's places (heights from the model, to be replaced by the real ground): the summit itself, the lowest dry
	 * floor east of it that sees it over the land between, and the flank half way; null without such a floor.
	 */
	private static RangePlaces range(TerrainModel model, int sea, int px, int pz, int ph) {
		int sx = px, sz = pz;
		float peak = ph;
		for (int x = px - 96; x <= px + 96; x += 8) {
			for (int z = pz - 96; z <= pz + 96; z += 8) {
				float h = model.sample(x, z).height;
				if (h > peak) {
					peak = h;
					sx = x;
					sz = z;
				}
			}
		}
		int vx = 0, vz = 0;
		float low = Float.MAX_VALUE;
		for (int dx = 2500; dx <= 6000; dx += 96) {
			for (int dz = -dx * 5 / 6; dz <= dx * 5 / 6; dz += 96) {
				float h = model.sample(sx + dx, sz + dz).height;
				if (h > sea + 4 && h < low && sees(model, sx + dx, sz + dz, h + 6, sx, sz, peak)) {
					low = h;
					vx = sx + dx;
					vz = sz + dz;
				}
			}
		}
		if (low == Float.MAX_VALUE) {
			return null;
		}
		// on the line from the summit to the valley, the first ground below 45% of the way down: the flank
		int fx = (sx + vx) / 2, fz = (sz + vz) / 2;
		float target = low + 0.45F * (peak - low);
		for (int i = 1; i < 200; i++) {
			int x = sx + (vx - sx) * i / 200, z = sz + (vz - sz) * i / 200;
			if (model.sample(x, z).height < target) {
				fx = x;
				fz = z;
				break;
			}
		}
		return new RangePlaces(sx, Math.round(peak), sz, vx, Math.round(low), vz, fx, Math.round(target), fz);
	}

	/** Whether an eye at (x, y, z) sees the top of (tx, tz) at height th over the land between (the model's ground). */
	private static boolean sees(TerrainModel model, int x, int z, float y, int tx, int tz, float th) {
		for (int i = 1; i < 48; i++) {
			float t = i / 48.0F;
			float line = y + (th - 40 - y) * t;
			if (model.sample(Math.round(x + (tx - x) * t), Math.round(z + (tz - z) * t)).height > line) {
				return false;
			}
		}
		return true;
	}

	/**
	 * The seasons' views (Seasons): the valley and the forest at noon on a day of each season ({@code seasonDays} 8 and the
	 * year starting 0.15 in: day 1 is mid-spring, day 6 early summer, day 15 mid-autumn, day 23 deep winter), the winter
	 * snowline (rain in the valley, snow on the peak), and the land from the peak in autumn with a long lens, where the
	 * chunks meet VOID LOD's far land. Shot place by place, so the chunks load once for each.
	 */
	static void addSeasonViews(List<View> views, Places p) {
		MotePlaces m = motePlaces;
		int[] days = {1, 6, 15, 23};
		String[] names = {"spring", "summer", "autumn", "winter"};
		for (int i = 0; i < 4; i++) {
			views.add(new View(names[i] + "0", p.valleyX(), p.valleyY() + 3, p.valleyZ(), p.toPeak(), 3, 70, days[i] * 24000L + 6000, "clear", 3));
		}
		views.add(new View("snowline0", p.valleyX(), p.valleyY() + 3, p.valleyZ(), p.toPeak(), 3, 70, 23 * 24000L + 6000, "rain", 4));
		// high over the valley, under the lowest clouds, out over the land where the chunks give way to VOID LOD's
		views.add(new View("autumnfar0", p.valleyX(), p.valleyY() + 100, p.valleyZ(), p.toPeak() + 180, 10, 50, 15 * 24000L + 5000, "clear", 3));
		// over the forest's crowns, looking out across them
		for (int i = 0; i < 4; i++) {
			views.add(new View(names[i] + "1", m.forestX(), m.forestY() + 24, m.forestZ(), m.forestYaw(), 22, 70, days[i] * 24000L + 6000, "clear", 3));
		}
		// on temperate high ground, where vanilla rains all year, in a winter rain: it snows there now
		views.add(new View("snowline1", hill[0], hill[1] + 4, hill[2], hill[3], 6, 70, 23 * 24000L + 6000, "rain", 4));
	}

	/** Where the snowline view is shot: {x, ground y, z, yaw}. */
	private static int[] hill = {0, 120, 0, 0};

	/**
	 * Temperate high ground near the valley for the snowline view: the nearest column 130 to 180 high whose biome rains
	 * there in vanilla (warm enough at that height) but is cool enough to snow in deep winter, looking back down to the
	 * valley.
	 */
	static int[] findHill(ServerLevel level, Places p) {
		if (!(level.getChunkSource().getGenerator() instanceof EarthChunkGenerator earth)) {
			return new int[]{p.valleyX(), p.valleyY(), p.valleyZ(), 0};
		}
		TerrainModel model = earth.model(level.getChunkSource().randomState());
		int checked = 0;
		for (int r = 64; r <= 2400 && checked < 120; r += 48) {
			for (int i = 0; i < 24 && checked < 120; i++) {
				double a = i * Math.PI / 12;
				int x = p.valleyX() + (int) (Math.cos(a) * r), z = p.valleyZ() + (int) (Math.sin(a) * r);
				float h = model.sample(x, z).height;
				if (h < 130 || h > 180) {
					continue;
				}
				checked++;
				// out in the open, so the view has its sky and the snow falling against it
				if (!open(level, x, z)) {
					continue;
				}
				int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
				var biome = level.getBiome(new BlockPos(x, y, z)).value();
				float t = biome.getBaseTemperature();
				if (biome.hasPrecipitation() && t >= 0.6F && t <= 1.0F && y >= 130) {
					float yaw = (float) Math.toDegrees(Math.atan2(-(p.valleyX() - x), p.valleyZ() - z));
					return new int[]{x, y, z, (int) yaw};
				}
			}
		}
		return new int[]{p.valleyX(), p.valleyY(), p.valleyZ(), 0};
	}

	/** Waits (up to six seconds) until the chunks have taken the season's colours, after a step or Gloam switched off or on. */
	private static void awaitSeasons(ClientGameTestContext context) {
		for (int t = 0; t < 120 && !context.computeOnClient(mc -> Seasons.settled()); t++) {
			context.waitTick();
		}
	}

	/** For the far view: waits (up to two minutes) until VOID LOD has rebuilt the tiles on screen in the season's colours. */
	private static void awaitLod(ClientGameTestContext context, View view) {
		if (!view.name().endsWith("far0") || !net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("void_lod")) {
			return;
		}
		long until = System.currentTimeMillis() + 120_000;
		int dirty = -1;
		while (System.currentTimeMillis() < until) {
			context.waitTicks(20);
			dirty = context.computeOnClient(mc -> {
				var lod = dev.voidmc.lod.VoidLod.get();
				return lod == null ? 0 : lod.engine().dirtyDrawn() + lod.engine().rebuilding() + lod.engine().queued();
			});
			if (dirty == 0) {
				break;
			}
		}
		System.out.printf(Locale.ROOT, "[gloam] %s: far land settled (%d tiles still out of date)%n", view.name(), dirty);
	}

	/** Where a view is shot: the overcast and haze views go looking for their place, the rest stay where they are. */
	private View place(TestSingleplayerContext world, View planned, View last) {
		if (planned.name().startsWith("range")) {
			if (!rangesFound) {
				rangesFound = true;
				rangePlaces = world.getServer().computeOnServer(server -> findRanges(server.overworld()));
				System.out.printf(Locale.ROOT, "[gloam] range places: %s%n", rangePlaces);
			}
			List<View> fresh = new ArrayList<>();
			addRangeViews(fresh);
			return fresh.stream().filter(v -> v.name().equals(planned.name())).findFirst().orElse(planned);
		}
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

	/**
	 * The motes' views. Days are picked for their season ({@code seasonDays} 8, the year starting 0.15 in, so day 6 is
	 * early summer, day 15 mid-autumn, day 23 deep winter): falling leaves under an autumn canopy, pollen over the valley
	 * meadow at a summer noon, fireflies by the water on a summer dusk and after dark, spray where a waterfall lands,
	 * breath and snow in the snowy land in winter.
	 */
	static void addMoteViews(List<View> views, Places p) {
		MotePlaces m = motePlaces;
		// by the water first: water0 is shot from there, so the chunks are already loaded
		View water = new View("firefly0", p.waterX(), p.waterY() + 2, p.waterZ(), 0, 4, 70, 6 * 24000 + 13200, "clear", 20);
		views.add(water);
		views.add(water.at("firefly1", 6 * 24000 + 14400, "clear"));
		views.add(new View("forest0", m.forestX(), m.forestY(), m.forestZ(), m.forestYaw(), -6, 70, 15 * 24000 + 6000, "clear", 15));
		views.add(new View("pollen0", p.valleyX(), p.valleyY() + 2, p.valleyZ(), p.toPeak() + 90, 6, 70, 6 * 24000 + 5000, "clear", 6));
		views.add(new View("fall0", m.fallX() - 9, m.fallY() + 3, m.fallZ(), -90, 8, 70, 6 * 24000 + 6000, "clear", 5));
		views.add(new View("cold0", m.coldX(), m.coldY() + 1, m.coldZ(), m.coldYaw(), -6, 70, 23 * 24000 + 2000, "rain", 8));
		// a winter dawn in the valley, the breath against the dark grass (the shot waits for a breath, see shoot's caller)
		views.add(new View("breath0", p.valleyX(), p.valleyY() + 1, p.valleyZ(), p.toPeak() + 90, 10, 70, 23 * 24000 + 23400, "clear", 2));
	}

	/**
	 * The motes' places: under the canopy of the forest nearest the valley (the column with most leaves around it and no
	 * trunk in the way), the snowy land nearest the valley, and a waterfall built 120 blocks east of the valley: a stone
	 * pillar twelve blocks tall with a water source on top, which runs off all four sides.
	 */
	static MotePlaces findMotePlaces(ServerLevel level, Places p) {
		BlockPos origin = new BlockPos(p.valleyX(), p.valleyY(), p.valleyZ());
		int[] forest = findForest(level, p, origin);
		var snowy = level.findClosestBiome3d(b -> b.value().getBaseTemperature() < 0.15F && !b.is(BiomeTags.IS_TAIGA) && !b.is(BiomeTags.IS_FOREST), origin, 4000,
			64, 64);
		BlockPos snow = snowy != null ? snowy.getFirst() : new BlockPos(p.peakX(), p.peakY(), p.peakZ());
		int[] cold = findCanopy(level, snow.getX(), snow.getZ(), false);
		// out over the open snow, as far as the eye goes: the blown powder and the breath show against distance, not a slope
		float coldYaw = clearest(level, cold[0], cold[1], cold[2], 48);
		int fallX = p.valleyX() + 120, fallZ = p.valleyZ();
		level.getChunk(fallX >> 4, fallZ >> 4);
		int fallY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, fallX, fallZ);
		for (int y = fallY; y < fallY + 12; y++) {
			level.setBlockAndUpdate(new BlockPos(fallX, y, fallZ), Blocks.STONE.defaultBlockState());
		}
		level.setBlockAndUpdate(new BlockPos(fallX, fallY + 12, fallZ), Blocks.WATER.defaultBlockState());
		return new MotePlaces(forest[0], forest[1], forest[2], underCanopy(level, forest[0], forest[1], forest[2]), cold[0], cold[1], cold[2], coldYaw, fallX, fallY,
			fallZ);
	}

	private static int[] findForest(ServerLevel level, Places p, BlockPos origin) {
		var found = level.findClosestBiome3d(b -> b.is(BiomeTags.IS_FOREST) && b.unwrapKey().map(k -> {
			String name = k.identifier().getPath();
			return !name.contains("lumen") && !name.contains("wisteria") && !name.contains("cherry") && !name.contains("pale");
		}).orElse(false), origin, 2400, 32, 64);
		if (found == null) {
			return new int[]{p.valleyX(), p.valleyY(), p.valleyZ()};
		}
		return findCanopy(level, found.getFirst().getX(), found.getFirst().getZ(), true);
	}

	/**
	 * Near (cx, cz), the column with the most leaves overhead around it ({@code under}) or the fewest (out in the open),
	 * and no trunk in the way of the camera, as {x, ground y, z}.
	 */
	private static int[] findCanopy(ServerLevel level, int cx, int cz, boolean under) {
		int bestX = cx, bestZ = cz, best = -1;
		for (int dx = -24; dx <= 24; dx += 2) {
			for (int dz = -24; dz <= 24; dz += 2) {
				int score = 0;
				for (int ox = -6; ox <= 6; ox += 3) {
					for (int oz = -6; oz <= 6; oz += 3) {
						int x = cx + dx + ox, z = cz + dz + oz;
						level.getChunk(x >> 4, z >> 4);
						int canopy = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) - level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
						if (under ? canopy >= 3 : canopy == 0) {
							score++;
						}
					}
				}
				// the camera's own column must be open to stand in, with no trunk right in front of it
				int x = cx + dx, z = cz + dz;
				int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
				boolean clear = under || level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) == ground;
				if (score > best && clear && open(level, x, ground + 1, z)) {
					best = score;
					bestX = x;
					bestZ = z;
				}
			}
		}
		return new int[]{bestX, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bestX, bestZ), bestZ};
	}

	/**
	 * For the falling leaves: the yaw (of sixteen) from (x, ground, z) that sees the most canopy two to twelve blocks
	 * ahead across the middle of the frame (where leaves are let go and fall), counting only what the eye reaches past
	 * trunks and bushes, and none with something in the way within four blocks of the middle.
	 */
	private static float underCanopy(ServerLevel level, int x, int ground, int z) {
		float best = 0;
		int most = -1;
		for (int k = 0; k < 16; k++) {
			float yaw = k * 22.5F;
			int score = 0;
			boolean blocked = false;
			for (int off = -30; off <= 30; off += 10) {
				double rad = Math.toRadians(yaw + off), dx = -Math.sin(rad), dz = Math.cos(rad);
				for (int step = 1; step <= 12; step++) {
					int cx = Mth.floor(x + 0.5 + dx * step), cz = Mth.floor(z + 0.5 + dz * step);
					BlockState state = level.getBlockState(new BlockPos(cx, ground + 1, cz));
					if (!state.isAir() && !state.canBeReplaced()) {
						blocked |= step <= 4 && Math.abs(off) <= 10;
						break;
					}
					int canopy = level.getHeight(Heightmap.Types.MOTION_BLOCKING, cx, cz) - level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, cx, cz);
					if (step >= 2 && canopy >= 2) {
						score++;
					}
				}
			}
			if (!blocked && score > most) {
				most = score;
				best = yaw;
			}
		}
		return most < 0 ? clearest(level, x, ground, z, 16) : best;
	}

	/** The yaw (of eight) from (x, ground, z) with the longest clear view at eye height, up to {@code range} blocks. */
	private static float clearest(ServerLevel level, int x, int ground, int z, int range) {
		float best = 0;
		int longest = -1;
		for (int k = 0; k < 8; k++) {
			float yaw = k * 45;
			double dx = -Math.sin(Math.toRadians(yaw)), dz = Math.cos(Math.toRadians(yaw));
			int clear = 0;
			for (int step = 1; step <= range; step++) {
				BlockPos at = BlockPos.containing(x + 0.5 + dx * step, ground + 1.6, z + 0.5 + dz * step);
				BlockState state = level.getBlockState(at);
				if (!state.isAir() && !state.canBeReplaced()) {
					break;
				}
				clear++;
			}
			if (clear > longest) {
				longest = clear;
				best = yaw;
			}
		}
		return best;
	}

	/** Whether there is no log within three blocks of (x, y, z) at eye height. */
	private static boolean open(ServerLevel level, int x, int y, int z) {
		for (int dx = -3; dx <= 3; dx++) {
			for (int dz = -3; dz <= 3; dz++) {
				BlockState state = level.getBlockState(new BlockPos(x + dx, y, z + dz));
				if (state.is(BlockTags.LOGS) || (dx == 0 && dz == 0 && !state.isAir())) {
					return false;
				}
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

	/** For the breath and cold views: waits (up to ten seconds) until a breath is in the air, a few ticks after it starts. */
	private static void awaitBreath(ClientGameTestContext context, View view) {
		if (!view.name().startsWith("breath") && !view.name().startsWith("cold")) {
			return;
		}
		for (int t = 0; t < 200 && context.computeOnClient(mc -> Motes.Kind.BREATH.count()) < 4; t++) {
			context.waitTick();
		}
		context.waitTicks(6);
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
			double bx = cx - Math.sin(Math.toRadians(bestYaw)) * bestDist, bz = cz + Math.cos(Math.toRadians(bestYaw)) * bestDist;
			return new float[]{bestYaw, bestScore, bestDist, FarAir.frontAt(bx, bz), air.front(bx, bz, mc.level.getGameTime(), year, 0.6F)};
		});
		System.out.printf(Locale.ROOT, "[gloam] %s: aiming at yaw %.0f, %.0f blocks off (score %.2f, front there %.2f on the map, %.2f in the model)%n",
			view.name(), best[0], best[2], best[1], best.length > 3 ? best[3] : -1, best.length > 4 ? best[4] : -1);
		// the fronts across the view, for checking the picture against the model
		String table = context.computeOnClient(mc -> {
			StringBuilder out = new StringBuilder();
			for (int da = -40; da <= 40; da += 10) {
				double rad = Math.toRadians(best[0] + da);
				out.append(String.format(Locale.ROOT, " %+d:", da));
				for (int d = 4000; d <= 16000; d += 4000) {
					out.append(String.format(Locale.ROOT, " %.2f", FarAir.frontAt(view.x() - Math.sin(rad) * d, view.z() + Math.cos(rad) * d)));
				}
			}
			return out.toString();
		});
		System.out.printf(Locale.ROOT, "[gloam] %s: fronts by yaw offset at 4/8/12/16 km:%s%n", view.name(), table);
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
