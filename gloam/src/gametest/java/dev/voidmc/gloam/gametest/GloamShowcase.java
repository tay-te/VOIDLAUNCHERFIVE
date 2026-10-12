package dev.voidmc.gloam.gametest;

import dev.voidmc.expanse.api.ExpanseUnderground;
import dev.voidmc.expanse.world.terrain.EarthChunkGenerator;
import dev.voidmc.expanse.world.terrain.TerrainModel;
import dev.voidmc.gloam.GloamSettings;
import dev.voidmc.gloam.air.Atmosphere;
import dev.voidmc.gloam.air.Air;
import dev.voidmc.gloam.air.AirSample;
import dev.voidmc.gloam.bridge.Bridge;
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
	/**
	 * Shader pack options to compare within one run, as KEY=ab:base pairs separated by commas (for example
	 * {@code -Pgloam.show.packAB=SHADOWS=false:true}): every Welkin view is shot twice from the same spot, the same land
	 * and the same clock, once with the pack's base values and once with the ab ones, each with its frame time, with one
	 * Iris reload between them (the order alternates view by view, so each view costs one reload). The ab shots end
	 * in _ab. A/B comparisons across runs are not fair while the world's generation is not repeatable.
	 */
	private static final String PACK_AB = System.getProperty("gloam.show.packAB", "");
	/** Whether the pack runs the ab values now. */
	private static boolean packAbNow;
	/**
	 * A per-pass profile of the pack: option changes separated by semicolons, each one or more KEY=value:base pairs
	 * separated by commas (for example {@code -Pgloam.show.packSweep=CLOUDS=false:true;SHADOWS=false:true}). After each
	 * Welkin view's own frame time, each change in turn is made (one Iris reload), the frame time logged as
	 * {@code <view>_sweep_<change>} (with a screenshot of that name), and the base values put back, so what a pass costs
	 * is the view's time minus that line's. The first frames after a reload can stall (shadow chunks rebuilding): a
	 * reading far above the view's own is that, not the change.
	 */
	private static final String PACK_SWEEP = System.getProperty("gloam.show.packSweep", "");
	/**
	 * The shader pack a run uses (-Pgloam.show.pack=ComplementaryReimagined_r5.9.3.zip, a zip or a folder in the run's
	 * shaderpacks): set in Iris's config and loaded before the world is made, so a bench run names its pack on the command
	 * line. Logged as {@code [gloam] pack: ...}. Empty: whatever Iris's config already names.
	 */
	private static final String PACK = System.getProperty("gloam.show.pack", "");

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

	record MotePlaces(int forestX, int forestY, int forestZ, float forestYaw, int coldX, int coldY, int coldZ, float coldYaw, int fallX, int fallY, int fallZ,
		float waterYaw, float valleyYaw) {
	}

	private static MotePlaces motePlaces = new MotePlaces(0, 80, 0, 0, 0, 80, 0, 0, 0, 80, 0, 0, 0);

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
		if (!PACK.isEmpty()) {
			usePack(context, PACK);
		}
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
			} else if (!System.getProperty("gloam.show.bench", "").isEmpty()) {
				views.removeIf(v -> !v.name().startsWith("bench_")); // a bench run shoots the bench alone
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
				if (moved && (view.name().startsWith("wvale") || view.name().startsWith("wlight") || view.name().startsWith("whaze")
					|| view.name().startsWith("bench"))) {
					// Welkin's comparisons (mist on and off, the uniforms and the header mirror) are only fair with the
					// same land drawn in each run: wait for the chunks round the camera, then for the LOD's tiles
					awaitChunks(context, view);
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
				this.shoot(context, view.name() + "_on" + abSuffix());
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
				String bridge = context.computeOnClient(mc -> Bridge.stats());
				System.out.printf(Locale.ROOT, "[gloam] %s %s%n", view.name(), bridge);
				String season = context.computeOnClient(mc -> Seasons.stats());
				System.out.printf(Locale.ROOT, "[gloam] %s seasons: %s%n", view.name(), season);
				if (view.name().startsWith("w") && !view.name().startsWith("water") && !view.name().startsWith("winter") || view.name().startsWith("bench")) {
					logFrameTime(context, view.name() + abSuffix());
					if (!PACK_SWEEP.isEmpty()) {
						for (String change : PACK_SWEEP.split(";")) {
							if (change.isBlank()) {
								continue;
							}
							packSet(context, change, true);
							awaitChunks(context, view);
							awaitLod(context, view);
							context.waitTicks(40);
							String label = view.name() + "_sweep_" + change.trim().replaceAll("[=:,]", "_");
							this.shoot(context, label);
							logFrameTime(context, label);
							packSet(context, change, false);
						}
						awaitChunks(context, view);
						awaitLod(context, view);
						context.waitTicks(20);
					}
					if (!PACK_AB.isEmpty()) {
						// the same view with the other option values: same spot, land and clock, one reload between
						packOptions(context, !packAbNow);
						awaitChunks(context, view);
						awaitLod(context, view);
						context.waitTicks(40 + view.linger() * 20);
						awaitSeasons(context);
						this.shoot(context, view.name() + "_on" + abSuffix());
						logFrameTime(context, view.name() + abSuffix());
					}
				}
				last = view;
			}
			if (packAbNow) {
				packOptions(context, false); // Iris saved the ab values to the pack's settings file: leave the base ones there
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
		addWelkinViews(views, p);
		addLightViews(views, p);
		addSkyViews(views, p);
		addBenchViews(views);
		return views;
	}

	/**
	 * The lighting bench (-Pgloam.show.bench=full|reduced): one fixed set of views, the same in every run and both test
	 * worlds, shot with whichever pack the run uses (gloam.show.pack), so shots from different packs line up. Each
	 * place is shot at each hour of an early-summer day (day 6, so the season's colours match), the night under the full
	 * moon of day 8: bench_{place}_{hour}. full: plain, forest (under the canopy), village, valley, shore at
	 * 0530 0800 1200 1630 1830 and night, and the torch-lit village street at night; reduced: plain, forest and village
	 * at 1200 1630 1830. The places are fixed eyes (x,y,z,yaw,pitch; the y is the feet's, as tp sets it); any can be
	 * overridden with -Pgloam.show.benchAt=name=x,y,z,yaw,pitch;... where a negative y stands that many blocks over the
	 * ground of that very column, and find:kind[@x,z] asks LightPlaces to find one (forestfloor, shore, street, village,
	 * canyon) near (x, z) (default the valley's forest). Each place is logged as {@code [gloam] bench place ...} with
	 * the eye it was shot from.
	 */
	static final java.util.Map<String, String> BENCH_PLACES = new java.util.LinkedHashMap<>();
	static {
		// found once (2026-10-11, seed 8675309) and fixed here, so every run and both test worlds shoot the same eyes:
		// an open meadow in the old forest country looking north; under a flower forest's canopy (found by
		// find:forestfloor@-1009,-1386); a steppe village from 64 south and 34 up; the valley's river gorge looking south;
		// a lake district lake from its bank looking west over the water (find:shore@3200,488, then lifted to see it);
		// and a street of the same village by its torches (find:street@2256,-544)
		BENCH_PLACES.put("plain", "-1009,148,-1386,180,6");
		BENCH_PLACES.put("forest", "-1109,138,-1530,135,2");
		BENCH_PLACES.put("village", "2256,148,-480,180,28");
		BENCH_PLACES.put("valley", "3200,100,488,0,12");
		BENCH_PLACES.put("shore", "14140,66,10239,105,10");
		BENCH_PLACES.put("torch", "2251,114,-557,338,4");
		for (String kv : System.getProperty("gloam.show.benchAt", "").split(";")) {
			String[] a = kv.trim().split("=", 2);
			if (a.length == 2) {
				BENCH_PLACES.put(a[0].trim(), a[1].trim());
			}
		}
	}
	static final String[] BENCH_HOURS = {"0530", "0800", "1200", "1630", "1830", "night"};
	private static final java.util.Map<String, int[]> benchFound = new java.util.HashMap<>();

	static long benchTime(String hour) {
		long day = 6 * 24000L;
		return switch (hour) {
			case "0530" -> day - 500;
			case "0800" -> day + 2000;
			case "1200" -> day + 6000;
			case "1630" -> day + 10500;
			case "1830" -> day + 12500;
			case "night" -> 8 * 24000L + 18000;
			default -> day + Long.parseLong(hour);
		};
	}

	static void addBenchViews(List<View> views) {
		String set = System.getProperty("gloam.show.bench", "");
		if (set.isEmpty()) {
			return;
		}
		List<String> places;
		String[] hours;
		switch (set) {
			case "reduced" -> {
				places = List.of("plain", "forest", "village");
				hours = new String[]{"1200", "1630", "1830"};
			}
			case "survey" -> {
				places = new ArrayList<>(BENCH_PLACES.keySet());
				hours = new String[]{"1200", "night"};
			}
			default -> {
				places = List.of("plain", "forest", "village", "valley", "shore", "torch");
				hours = BENCH_HOURS;
			}
		}
		String only = System.getProperty("gloam.show.benchHours", "");
		if (!only.isEmpty()) {
			hours = only.split(",");
		}
		for (String place : places) {
			for (String hour : place.equals("torch") && !set.equals("survey") ? new String[]{"night"} : hours) {
				views.add(new View("bench_" + place + "_" + hour, 0, 0, 0, 0, 0, 70, benchTime(hour), "clear", 2));
			}
		}
	}

	/** A bench place's eye {x, y, z, yaw, pitch} from its spec (see addBenchViews). */
	static int[] benchPlace(ServerLevel level, String name) {
		String spec = BENCH_PLACES.getOrDefault(name, "");
		int[] eye = null;
		if (spec.startsWith("find:")) {
			String kind = spec.substring(5);
			int nx = motePlaces.forestX(), nz = motePlaces.forestZ();
			int at = kind.indexOf('@');
			if (at >= 0) {
				String[] xz = kind.substring(at + 1).split(",");
				nx = Integer.parseInt(xz[0].trim());
				nz = Integer.parseInt(xz[1].trim());
				kind = kind.substring(0, at);
			}
			eye = LightPlaces.find(level, kind, nx, nz);
		} else {
			String[] a = spec.split(",");
			if (a.length == 5) {
				eye = new int[]{Integer.parseInt(a[0].trim()), Integer.parseInt(a[1].trim()), Integer.parseInt(a[2].trim()),
					Math.round(Float.parseFloat(a[3].trim())), Math.round(Float.parseFloat(a[4].trim()))};
				if (eye[1] < 0) {
					level.getChunk(eye[0] >> 4, eye[2] >> 4);
					eye[1] = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, eye[0], eye[2]) - eye[1];
				}
			}
		}
		if (eye == null) {
			eye = new int[]{motePlaces.forestX(), motePlaces.forestY() + 40, motePlaces.forestZ(), 180, 30};
		}
		System.out.printf(Locale.ROOT, "[gloam] bench place %s (%s): %d,%d,%d,%d,%d%n", name, spec, eye[0], eye[1], eye[2], eye[3], eye[4]);
		return eye;
	}

	/**
	 * VOID Welkin's valley mist (bridge M1): the valley floor seen from 20 blocks up, looking toward the nearest water, at
	 * 05:30, 08:00 and noon; the same spot looking down (for the pack's tile debug view, DEBUG_VIEW=7) and again 160
	 * blocks east, to show the tiles following the camera; and the land below the peak at 05:30 with the far land in view.
	 */
	static void addWelkinViews(List<View> views, Places p) {
		float toWater = (float) Math.toDegrees(Math.atan2(-(p.waterX() - p.valleyX()), p.waterZ() - p.valleyZ()));
		if (p.waterX() == p.valleyX() && p.waterZ() == p.valleyZ()) {
			toWater = p.toPeak() + 180;
		}
		View vale = new View("wmist0", p.valleyX(), p.valleyY() + 20, p.valleyZ(), toWater, 10, 70, 23500, "clear", 2);
		views.add(vale);
		views.add(vale.at("wmist1", 2000, "clear"));
		views.add(vale.at("wmist2", 6000, "clear"));
		views.add(new View("wtiles0", p.valleyX(), p.valleyY() + 40, p.valleyZ(), toWater, 35, 70, 23500, "clear", 2));
		views.add(new View("wtiles1", p.valleyX() + 160, p.valleyY() + 40, p.valleyZ(), toWater, 35, 70, 23500, "clear", 2));
		views.add(new View("wfar0", p.peakX(), p.peakY() + 20, p.peakZ(), p.toPeak() + 180, 8, 70, 23500, "clear", 2));
		// under the land: the widest cavern Expanse knows near the valley (found when shot, see findCave), for the
		// underground tile U, void_underground / void_cave and the cave beds
		views.add(new View("wcave0", p.valleyX(), p.valleyY() - 40, p.valleyZ(), 0, 10, 70, 6000, "clear", 6));
		// a survey from 40 blocks over the valley floor, four ways round, to find the view with the most hollows
		for (int i = 0; i < 4; i++) {
			views.add(new View("wsurvey" + i, p.valleyX(), p.valleyY() + 40, p.valleyZ(), i * 90, 20, 70, 23500, "clear", 2));
		}
		// the chosen vale view (-Pgloam.show.welkin=x,y,z,yaw,pitch), at 05:30, 08:00 and noon
		// (several separated by ';': wvale0.., wvaleB0.., wvaleC0..)
		String[] spots = System.getProperty("gloam.show.welkin", "").split(";");
		for (int s = 0; s < spots.length; s++) {
			String[] at = spots[s].split(",");
			if (at.length != 5) {
				continue;
			}
			String n = "wvale" + (s == 0 ? "" : String.valueOf((char) ('A' + s)));
			View v = new View(n + "0", Integer.parseInt(at[0]), Integer.parseInt(at[1]), Integer.parseInt(at[2]), Float.parseFloat(at[3]),
				Float.parseFloat(at[4]), 70, 23500, "clear", 2);
			views.add(v);
			views.add(v.at(n + "1", 2000, "clear"));
			views.add(v.at(n + "2", 6000, "clear"));
		}
	}

	/**
	 * VOID Welkin's sunlight and near shadows: on an early-summer day (day 6) at noon, mid-afternoon, late afternoon and
	 * dusk (6000, 9800, 11600, 12300), from 40 blocks over the forest's crowns looking north, so the afternoon sun comes
	 * from the left (wlightA: crowns, trunks and the clearings between them), and from 28 blocks over the valley floor
	 * looking south across it (wlightB: slopes, cliffs and the woods on the rim); then under the canopy at noon (wlightC0).
	 * A chosen spot can be added with -Pgloam.show.light=x,y,z,yaw,pitch (wlightD, the same four times; a negative y
	 * stands that many blocks over the highest ground near it). Their names start with "w", so each logs its frame time.
	 */
	static void addLightViews(List<View> views, Places p) {
		MotePlaces m = motePlaces;
		long day = 6 * 24000L;
		long[] times = {6000, 9800, 11600, 12300};
		for (int i = 0; i < times.length; i++) {
			views.add(new View("wlightA" + i, m.forestX(), m.forestY() + 40, m.forestZ(), 180, 30, 70, day + times[i], "clear", 2));
		}
		for (int i = 0; i < times.length; i++) {
			views.add(new View("wlightB" + i, p.valleyX(), p.valleyY() + 28, p.valleyZ(), 0, 12, 70, day + times[i], "clear", 2));
		}
		views.add(new View("wlightC0", m.forestX(), m.forestY(), m.forestZ(), m.forestYaw(), -4, 70, day + 6000, "clear", 2));
		// the same view a few seconds later, so the two frames show the leaves' shadows swaying with the leaves
		views.add(new View("wlightC1", m.forestX(), m.forestY(), m.forestZ(), m.forestYaw(), -4, 70, day + 6000, "clear", 2));
		// chosen spots, ';' between them (wlightD.., wlightE.., ...): x,y,z,yaw,pitch, or a name LightPlaces finds when shot
		// (village, canyon); a found spot stands at (letter, 0, 0) until place() looks it up
		String[] spots = System.getProperty("gloam.show.light", "").split(";");
		for (int s = 0; s < spots.length; s++) {
			String n = "wlight" + (char) ('D' + s);
			String[] at = spots[s].trim().split(",");
			if (at.length == 1 && !at[0].isEmpty()) {
				lightFinds.put(n, at[0]);
				for (int i = 0; i < times.length; i++) {
					views.add(new View(n + i, s, 0, 0, 0, 0, 70, day + times[i], "clear", 2));
				}
			} else if (at.length == 5) {
				for (int i = 0; i < times.length; i++) {
					views.add(new View(n + i, Integer.parseInt(at[0]), Integer.parseInt(at[1]), Integer.parseInt(at[2]), Float.parseFloat(at[3]),
						Float.parseFloat(at[4]), 70, day + times[i], "clear", 2));
				}
			}
		}
	}

	/** The light views' spots found by name (view prefix to LightPlaces' name), and where they were found. */
	private static final java.util.Map<String, String> lightFinds = new java.util.HashMap<>();
	private static final java.util.Map<String, int[]> lightFound = new java.util.HashMap<>();

	/**
	 * VOID Welkin's sky, haze, night and rain (wave 2 check): the sky across a day from the summit (pre-dawn, sunrise and
	 * morning looking east, noon south, afternoon and sunset west, dusk west, the full moon rising in the east and a
	 * new-moon night overhead: wsky*), one view per haze kind at the same hour of an early-summer morning (10:00, day 6),
	 * each in its own Expanse country or air (found when shot, see findCountry: whaze_*), the overcast under a front
	 * (wover0), rain on a meadow and the ground still wet after it (wrainB0, wwetB0, wwetB1), the near water at noon
	 * (wwater0, its reflection), and the night's darkness on
	 * the valley under a full and a new moon (wnight0, wnight1). Their names start with "w", so each logs its frame time.
	 */
	static void addSkyViews(List<View> views, Places p) {
		int py = p.peakY() + 20;
		views.add(new View("wsky0_predawn", p.peakX(), py, p.peakZ(), -90, -6, 70, 22900, "clear", 2));
		views.add(new View("wsky1_sunrise", p.peakX(), py, p.peakZ(), -90, -6, 70, 23700, "clear", 2));
		views.add(new View("wsky2_morning", p.peakX(), py, p.peakZ(), -90, -12, 70, 1500, "clear", 2));
		views.add(new View("wsky3_noon", p.peakX(), py, p.peakZ(), 0, -18, 70, 6000, "clear", 2));
		views.add(new View("wsky4_afternoon", p.peakX(), py, p.peakZ(), 90, -6, 70, 10500, "clear", 2));
		views.add(new View("wsky5_sunset", p.peakX(), py, p.peakZ(), 90, -4, 70, 12400, "clear", 2));
		views.add(new View("wsky6_dusk", p.peakX(), py, p.peakZ(), 90, -6, 70, 13300, "clear", 2));
		views.add(new View("wsky7_moonrise", p.peakX(), py, p.peakZ(), -90, -30, 70, 15800, "clear", 2));
		views.add(new View("wsky8_fullmoon", p.peakX(), py, p.peakZ(), 0, -40, 70, 17300, "clear", 2));
		views.add(new View("wsky9_newmoon", p.peakX(), py, p.peakZ(), 0, -40, 70, 4 * 24000 + 18000, "clear", 2));
		long hour = 6 * 24000L + 4000;
		for (String kind : new String[]{"heartland", "lake_district", "gorges", "rock_city", "old_forest", "dusty", "clear_alpine"}) {
			views.add(new View("whaze_" + kind, p.valleyX(), p.valleyY() + 30, p.valleyZ(), 180, 5, 70, hour, "clear", 2));
		}
		views.add(new View("wover0", p.valleyX(), p.valleyY() + 3, p.valleyZ(), 90, -4, 70, 6 * 24000L + 6000, "clear", 2));
		// (the valley's rain and wet views were a stone gorge and water, which say nothing of rain or wet ground: rain and
		// wetness are judged on the meadow below; the water's reflection at noon on the near water, wwater0)
		views.add(new View("wwater0", p.valleyX(), p.valleyY() + 3, p.valleyZ(), 180, 8, 70, 6 * 24000L + 6000, "clear", 2));
		// rain and the wet ground after it on open grass (the old forest country's meadows, found as for whaze_old_forest)
		View meadow = new View("wrainB0", p.valleyX(), p.valleyY() + 3, p.valleyZ(), 180, 20, 70, 6 * 24000L + 6000, "rain", 20);
		views.add(meadow);
		views.add(meadow.at("wwetB0", 6 * 24000L + 6000, "clear").looking("wwetB0", 180, 35));
		views.add(meadow.at("wwetB1", 6 * 24000L + 9800, "clear").looking("wwetB1", 180, 20));
		views.add(new View("wnight0", p.valleyX(), p.valleyY() + 3, p.valleyZ(), p.toPeak(), 4, 70, 18000, "clear", 2));
		views.add(new View("wnight1", p.valleyX(), p.valleyY() + 3, p.valleyZ(), p.toPeak(), 4, 70, 4 * 24000 + 18000, "clear", 2));
	}

	/**
	 * For a whaze_ view: the nearest place to {@code near} (within 24 km) well inside the Expanse country it names, or with
	 * the haze hint it names (dusty, clear_alpine), from Expanse's country map; the eye 30 blocks over the highest ground
	 * within 32 blocks and under the lowest clouds (6 blocks over it for alpine air), looking out over the lowest land round it. Null without Expanse or a match.
	 */
	static int[] findCountry(ServerLevel level, View near, String kind) {
		dev.voidmc.expanse.api.ExpanseCountry.Sampler s = dev.voidmc.expanse.api.ExpanseCountry.sampler(level);
		if (s == null) {
			return null;
		}
		boolean byHaze = kind.equals("dusty") || kind.equals("clear_alpine");
		for (int r = 0; r <= 24000; r += 192) {
			int n = r == 0 ? 1 : Math.max(8, (int) (2 * Math.PI * r / 192));
			for (int i = 0; i < n; i++) {
				double a = 2 * Math.PI * i / n;
				int x = near.x() + (int) (Math.cos(a) * r), z = near.z() + (int) (Math.sin(a) * r);
				var h = s.at(x, z);
				// under the lowest cloud layer (about y 208), so the eye is not inside a cloud; alpine air is shot from just over its ground
				boolean alpine = kind.equals("clear_alpine");
				boolean match = byHaze ? h.haze().equals(kind) && h.altitude() > level.getSeaLevel() + 2 && (alpine || h.altitude() < 150)
					: h.country().equals(kind) && h.strength() > 0.85F && h.altitude() > level.getSeaLevel() + 2 && h.altitude() < 150;
				if (!match) {
					continue;
				}
				// the same kind all round, so the view looks across it
				boolean all = true;
				for (int[] d : new int[][]{{-400, 0}, {400, 0}, {0, -400}, {0, 400}}) {
					var o = s.at(x + d[0], z + d[1]);
					all &= byHaze ? o.haze().equals(kind) : o.country().equals(kind);
				}
				if (!all) {
					continue;
				}
				int top = level.getMinY();
				for (int dx = -32; dx <= 32; dx += 16) {
					for (int dz = -32; dz <= 32; dz += 16) {
						level.getChunk((x + dx) >> 4, (z + dz) >> 4);
						top = Math.max(top, level.getHeight(Heightmap.Types.MOTION_BLOCKING, x + dx, z + dz));
					}
				}
				int lift = alpine ? 6 : 30;
				if (!alpine && top + lift > 185) {
					continue;
				}
				// the yaw (of eight) over the lowest land 300 blocks out
				float yaw = 180, lowest = Float.MAX_VALUE;
				for (int k = 0; k < 8; k++) {
					double rad = Math.toRadians(k * 45);
					float far = s.at(x - (int) (Math.sin(rad) * 300), z + (int) (Math.cos(rad) * 300)).altitude();
					if (far < lowest) {
						lowest = far;
						yaw = k * 45;
					}
				}
				System.out.printf(Locale.ROOT, "[gloam] %s: %s at %d %d (country %s %.2f, haze %s, ground %d)%n", near.name(), kind, x, z, h.country(),
					h.strength(), h.haze(), top);
				return new int[]{x, top + lift, z, (int) yaw};
			}
		}
		return null;
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
			// over the treetops round about, so a valley in the woods still sees its range
			for (int[] d : new int[][]{{0, 0}, {-10, 0}, {10, 0}, {0, -10}, {0, 10}}) {
				level.getChunk((at[i][0] + d[0]) >> 4, (at[i][1] + d[1]) >> 4);
				ys[i] = Math.max(ys[i], level.getHeight(Heightmap.Types.MOTION_BLOCKING, at[i][0] + d[0], at[i][1] + d[1]));
			}
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
	 * year starting 0.15 in: day 1 is mid-spring, day 6 early summer, day 15 mid-autumn, day 17 late autumn with the crowns
	 * thinning, day 23 deep winter), the winter
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
		views.add(new View("winterfar0", p.valleyX(), p.valleyY() + 100, p.valleyZ(), p.toPeak() + 180, 10, 50, 23 * 24000L + 5000, "clear", 3));
		// over the forest's crowns, looking out across them
		for (int i = 0; i < 4; i++) {
			views.add(new View(names[i] + "1", m.forestX(), m.forestY() + 24, m.forestZ(), m.forestYaw(), 22, 70, days[i] * 24000L + 6000, "clear", 3));
		}
		// late autumn (day 17, the crowns half bare): the leaves thinning to twigs, over the forest and in the valley
		views.add(new View("lateautumn1", m.forestX(), m.forestY() + 24, m.forestZ(), m.forestYaw(), 22, 70, 17 * 24000L + 6000, "clear", 3));
		views.add(new View("lateautumn0", p.valleyX(), p.valleyY() + 3, p.valleyZ(), p.toPeak(), 3, 70, 17 * 24000L + 6000, "clear", 3));
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
	/**
	 * The most open point Expanse's caverns have within 1.5 km of {@code near} and 24..96 blocks under the land: the eye
	 * three blocks over its floor (x, y, z, space). Without Expanse, {@code near} as it is.
	 */
	static int[] findCave(ServerLevel level, View near) {
		ExpanseUnderground.Sampler caves = ExpanseUnderground.sampler(level);
		if (caves == null || !(level.getChunkSource().getGenerator() instanceof EarthChunkGenerator earth)) {
			return new int[]{near.x(), near.y(), near.z(), 0};
		}
		TerrainModel model = earth.model(level.getChunkSource().randomState());
		int[] best = {near.x(), near.y(), near.z(), 0};
		float most = 0;
		for (int dx = -1536; dx <= 1536; dx += 96) {
			for (int dz = -1536; dz <= 1536; dz += 96) {
				int x = near.x() + dx, z = near.z() + dz;
				int top = (int) model.sample(x, z).height;
				for (int y = top - 24; y >= top - 96 && y > level.getMinY() + 8; y -= 8) {
					ExpanseUnderground.Here h = caves.at(x, y, z);
					if (h.underground() && h.space() > most && h.ceiling() - h.floor() >= 8 && h.waterDistance() > 3) {
						most = h.space();
						best = new int[]{x, h.floor() + 3, z, (int) h.space()};
					}
				}
			}
		}
		return best;
	}

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
		// Sodium keeps its own rebuild queue, which Seasons cannot see: give it time to catch up
		if (net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("sodium")) {
			context.waitTicks(80);
		}
	}

	/** For the far view: waits (up to two minutes) until VOID LOD has rebuilt the tiles on screen in the season's colours. */
	/**
	 * Waits (90 s at most) until the chunks within the render distance are loaded and drawn: at least 80 % of the circle
	 * loaded, the count unchanged over three checks two seconds apart, and every section built.
	 */
	private static void awaitChunks(ClientGameTestContext context, View view) {
		long start = System.currentTimeMillis(), until = start + 90_000;
		int want = (int) (0.8 * Math.PI * DISTANCE * DISTANCE);
		int last = -1, calm = 0;
		while (System.currentTimeMillis() < until && calm < 3) {
			context.waitTicks(40);
			int[] state = context.computeOnClient(mc -> new int[]{mc.level.getChunkSource().getLoadedChunksCount(),
				mc.levelRenderer.hasRenderedAllSections() ? 1 : 0});
			calm = state[0] >= want && state[0] == last && state[1] == 1 ? calm + 1 : 0;
			last = state[0];
		}
		System.out.printf(Locale.ROOT, "[gloam] %s: %d chunks loaded and drawn after %.1f s (want %d, %s)%n", view.name(), last,
			(System.currentTimeMillis() - start) / 1000.0, want, calm >= 3 ? "settled" : "timed out");
	}

	private static void awaitLod(ClientGameTestContext context, View view) {
		if (!(view.name().endsWith("far0") || view.name().startsWith("wvale") || view.name().startsWith("whaze") || view.name().startsWith("wsky")) || !net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("void_lod")) {
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
		if (planned.name().startsWith("bench_")) {
			String name = planned.name().split("_")[1];
			int[] c = benchFound.computeIfAbsent(name, k -> world.getServer().computeOnServer(server -> benchPlace(server.overworld(), k)));
			return new View(planned.name(), c[0], c[1], c[2], c[3], c[4], planned.fov(), planned.time(), planned.weather(), planned.linger());
		}
		// a light view at a spot found by name: looked up once (near the forest), then every time of day shot from there
		if (planned.name().startsWith("wlight") && planned.name().length() > 7) {
			String prefix = planned.name().substring(0, 7);
			String find = lightFinds.get(prefix);
			if (find != null) {
				int[] c = lightFound.computeIfAbsent(prefix, k -> {
					int[] f = world.getServer().computeOnServer(server -> LightPlaces.find(server.overworld(), find, motePlaces.forestX(), motePlaces.forestZ()));
					return f != null ? f : new int[]{motePlaces.forestX(), motePlaces.forestY() + 40, motePlaces.forestZ(), 180, 30};
				});
				return new View(planned.name(), c[0], c[1], c[2], c[3], c[4], planned.fov(), planned.time(), planned.weather(), planned.linger());
			}
		}
		// a Welkin vale or light view given a negative y stands that many blocks over the highest ground within 16 blocks
		if ((planned.name().startsWith("wvale") || planned.name().startsWith("wlight")) && planned.y() < 0) {
			int ground = world.getServer().computeOnServer(server -> {
				ServerLevel level = server.overworld();
				int top = level.getMinY();
				for (int dx = -16; dx <= 16; dx += 8) {
					for (int dz = -16; dz <= 16; dz += 8) {
						level.getChunk((planned.x() + dx) >> 4, (planned.z() + dz) >> 4);
						top = Math.max(top, level.getHeight(Heightmap.Types.MOTION_BLOCKING, planned.x() + dx, planned.z() + dz));
					}
				}
				return top;
			});
			System.out.printf(Locale.ROOT, "[gloam] %s: ground %d at %d %d%n", planned.name(), ground, planned.x(), planned.z());
			return new View(planned.name(), planned.x(), ground - planned.y(), planned.z(), planned.yaw(), planned.pitch(), planned.fov(), planned.time(),
				planned.weather(), planned.linger());
		}
		if (planned.name().startsWith("wrainB") || planned.name().startsWith("wwetB")) {
			if (last != null && (last.name().startsWith("wrainB") || last.name().startsWith("wwetB"))) {
				return planned.at(last);
			}
			int[] c = world.getServer().computeOnServer(server -> findCountry(server.overworld(), planned, "old_forest"));
			if (c == null) {
				return planned;
			}
			// four blocks over the meadow, looking down on the grass
			return new View(planned.name(), c[0], c[1] - 30 + 4, c[2], c[3], planned.pitch(), planned.fov(), planned.time(), planned.weather(), planned.linger());
		}
		if (planned.name().startsWith("wwater")) {
			// the lake district's shore (found as for whaze_lake_district), a few blocks over the ground, looking out
			// over the lake at a low angle: the sky's reflection on near and far water
			int[] c = world.getServer().computeOnServer(server -> findCountry(server.overworld(), planned, "lake_district"));
			if (c == null) {
				return planned;
			}
			return new View(planned.name(), c[0], c[1] - 30 + 6, c[2], c[3], planned.pitch(), planned.fov(), planned.time(), planned.weather(), planned.linger());
		}
		if (planned.name().startsWith("whaze_")) {
			String kind = planned.name().substring("whaze_".length());
			int[] c = world.getServer().computeOnServer(server -> findCountry(server.overworld(), planned, kind));
			if (c == null) {
				System.out.printf(Locale.ROOT, "[gloam] %s: no %s found%n", planned.name(), kind);
				return planned;
			}
			return new View(planned.name(), c[0], c[1], c[2], c[3], planned.pitch(), planned.fov(), planned.time(), planned.weather(), planned.linger());
		}
		if (planned.name().startsWith("wcave")) {
			int[] c = world.getServer().computeOnServer(server -> findCave(server.overworld(), planned));
			System.out.printf(Locale.ROOT, "[gloam] %s: cavern at %d %d %d, space %d%n", planned.name(), c[0], c[1], c[2], c[3]);
			return new View(planned.name(), c[0], c[1], c[2], planned.yaw(), planned.pitch(), planned.fov(), planned.time(), planned.weather(),
				planned.linger());
		}
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
		if (planned.name().startsWith("wover")) {
			return this.underFront(world, planned);
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
		View water = new View("firefly0", p.waterX(), p.waterY() + 2, p.waterZ(), m.waterYaw(), 4, 70, 6 * 24000 + 13200, "clear", 20);
		views.add(water);
		views.add(water.at("firefly1", 6 * 24000 + 14400, "clear"));
		views.add(new View("forest0", m.forestX(), m.forestY(), m.forestZ(), m.forestYaw(), -6, 70, 15 * 24000 + 6000, "clear", 15));
		views.add(new View("pollen0", p.valleyX(), p.valleyY() + 2, p.valleyZ(), p.toPeak() + 90, 6, 70, 6 * 24000 + 5000, "clear", 6));
		views.add(new View("fall0", m.fallX() - 9, m.fallY() + 3, m.fallZ(), -90, 8, 70, 6 * 24000 + 6000, "clear", 5));
		views.add(new View("cold0", m.coldX(), m.coldY() + 1, m.coldZ(), m.coldYaw(), -6, 70, 23 * 24000 + 2000, "rain", 8));
		// a winter dawn, the breath against the land (the shot waits for a breath, see shoot's caller)
		// (in the snowy land: Expanse's valleys are often canyons, under a fall or too mild for breath to show)
		views.add(new View("breath0", m.coldX(), m.coldY() + 1, m.coldZ(), m.coldYaw(), 10, 70, 23 * 24000 + 23400, "clear", 2));
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
			fallZ,
			lowLand(level, p.waterX(), p.waterY(), p.waterZ()), clearest(level, p.valleyX(), p.valleyY(), p.valleyZ(), 24));
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

	/**
	 * For the fireflies: the yaw (of sixteen) from (x, ground, z) that sees the most low land (ground within three blocks
	 * of its own, where fireflies wander over the grass and the water) three to sixteen blocks ahead, past whatever stands
	 * in the way.
	 */
	private static float lowLand(ServerLevel level, int x, int ground, int z) {
		float best = 0;
		int most = -1;
		for (int k = 0; k < 16; k++) {
			float yaw = k * 22.5F;
			int score = 0;
			for (int off = -30; off <= 30; off += 10) {
				double rad = Math.toRadians(yaw + off), dx = -Math.sin(rad), dz = Math.cos(rad);
				for (int step = 1; step <= 16; step++) {
					int cx = Mth.floor(x + 0.5 + dx * step), cz = Mth.floor(z + 0.5 + dz * step);
					level.getChunk(cx >> 4, cz >> 4);
					int h = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, cx, cz);
					if (h > ground + 3) {
						break;
					}
					if (step >= 3 && Math.abs(h - ground) <= 3) {
						score++;
					}
				}
			}
			if (score > most) {
				most = score;
				best = yaw;
			}
		}
		return best;
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

	/** Makes {@code name} Iris's pack (shaders on), saves Iris's config and reloads, then logs the pack Iris runs. */
	private static void usePack(ClientGameTestContext context, String name) {
		String now = context.computeOnClient(mc -> {
			try {
				Class<?> iris = Class.forName("net.irisshaders.iris.Iris");
				Object config = iris.getMethod("getIrisConfig").invoke(null);
				config.getClass().getMethod("setShaderPackName", String.class).invoke(config, name);
				config.getClass().getMethod("setShadersEnabled", boolean.class).invoke(config, true);
				config.getClass().getMethod("save").invoke(config);
				iris.getMethod("reload").invoke(null);
				return iris.getMethod("getCurrentPackName").invoke(null) + (Boolean.TRUE.equals(iris.getMethod("loadedIncompatiblePack").invoke(null))
					? " (Iris says incompatible)" : "");
			} catch (ReflectiveOperationException e) {
				throw new IllegalStateException("gloam.show.pack needs Iris (-Piris)", e);
			}
		});
		System.out.printf(Locale.ROOT, "[gloam] pack: %s (asked for %s)%n", now, name);
	}

	/** "_ab" while the pack runs gloam.show.packAB's ab values, else "". */
	private static String abSuffix() {
		return !PACK_AB.isEmpty() && packAbNow ? "_ab" : "";
	}

	/** Switches the pack to gloam.show.packAB's ab values (or back to the base ones) through Iris's option queue, and reloads it. */
	private static void packOptions(ClientGameTestContext context, boolean ab) {
		packSet(context, PACK_AB, ab);
		packAbNow = ab;
	}

	/** Sets the KEY=ab:base pairs of {@code pairs} to their ab (or base) values through Iris's option queue, and reloads the pack. */
	private static void packSet(ClientGameTestContext context, String pairs, boolean ab) {
		java.util.Map<String, String> set = new java.util.LinkedHashMap<>();
		for (String kv : pairs.split(",")) {
			String[] a = kv.trim().split("=", 2);
			String[] v = a.length == 2 ? a[1].split(":", 2) : new String[0];
			if (v.length == 2) {
				set.put(a[0], ab ? v[0] : v[1]);
			}
		}
		context.runOnClient(mc -> {
			try {
				Class<?> iris = Class.forName("net.irisshaders.iris.Iris");
				@SuppressWarnings("unchecked")
				java.util.Map<String, String> queue = (java.util.Map<String, String>) iris.getMethod("getShaderPackOptionQueue").invoke(null);
				queue.putAll(set);
				iris.getMethod("reload").invoke(null);
			} catch (ReflectiveOperationException e) {
				throw new IllegalStateException("gloam.show.packAB and packSweep need Iris (-Piris)", e);
			}
		});
		System.out.printf(Locale.ROOT, "[gloam] pack options now %s (%s)%n", set, ab ? "ab" : "base");
	}

	/**
	 * Welkin's pass costs are a fraction of a millisecond: one fps reading is too noisy to judge them, so this averages
	 * the frame time over gloam.show.fpsSamples one-second readings (getFps counts the last whole second).
	 */
	private static void logFrameTime(ClientGameTestContext context, String label) {
		int samples = Integer.getInteger("gloam.show.fpsSamples", 8);
		double ms = 0;
		int min = Integer.MAX_VALUE, max = 0;
		for (int i = 0; i < samples; i++) {
			context.waitTicks(21);
			int f = Math.max(1, context.computeOnClient(mc -> mc.getFps()));
			ms += 1000.0 / f;
			min = Math.min(min, f);
			max = Math.max(max, f);
		}
		ms /= samples;
		System.out.printf(Locale.ROOT, "[gloam] %s frame time: %.3f ms mean over %d s (%.0f fps; %d..%d)%n", label, ms, samples, 1000.0 / ms, min, max);
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
