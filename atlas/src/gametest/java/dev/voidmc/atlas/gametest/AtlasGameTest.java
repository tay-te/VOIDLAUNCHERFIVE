package dev.voidmc.atlas.gametest;

import com.mojang.datafixers.util.Pair;
import dev.voidmc.atlas.api.AtlasApi;
import dev.voidmc.atlas.api.Place;
import dev.voidmc.atlas.api.Waypoint;
import dev.voidmc.atlas.client.AtlasClient;
import dev.voidmc.atlas.client.AtlasSettings;
import dev.voidmc.atlas.client.Perf;
import dev.voidmc.atlas.client.data.DimensionData;
import dev.voidmc.atlas.client.data.LayerData;
import dev.voidmc.atlas.client.ui.AtlasScreen;
import dev.voidmc.atlas.client.ui.MapPanel;
import dev.voidmc.atlas.core.Coords;
import dev.voidmc.atlas.core.RegionRaw;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.InactivityFpsLimit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Atlas in a real game. Two modes ({@code -Datlas.test=tour|perf}):
 *
 * <ul>
 *   <li><b>tour</b>: makes a world, flies a route from spawn to the nearest coast and on to the nearest mountains (and
 *   down into the caves, and into the Nether), letting Atlas map it, then photographs the full map at three zooms, the
 *   coast and mountains, the underground and Nether layers, the minimap in the HUD, a marker's detail card, the
 *   waypoint editor and the list, into {@code atlas.test.out}.</li>
 *   <li><b>perf</b>: the same flight, timed with the minimap on (frame times, minimap cost, client-thread ingest per
 *   frame); then a few hundred map tiles of synthetic land, panned and zoomed over by a script with the map open; all
 *   uncapped (no vsync, no frame limit). Writes the numbers as JSON to {@code atlas.test.out}.</li>
 * </ul>
 */
public class AtlasGameTest implements FabricClientGameTest {
	private static final String MODE = System.getProperty("atlas.test", "tour");
	private static final String SEED = System.getProperty("atlas.test.seed", "8675309");
	private static final long SETTLE_MS = Long.getLong("atlas.test.settle", 6000L);
	private static final int DISTANCE = Integer.getInteger("atlas.test.distance", 12);
	private static final Path OUT = Path.of(System.getProperty("atlas.test.out", "build/tour"));
	private static final String ONLY = System.getProperty("atlas.test.only", "");
	private static final int FRAMES_SECONDS = Integer.getInteger("atlas.test.frames", 12);

	private final List<String> shots = new ArrayList<>();
	private BlockPos spawn = BlockPos.ZERO;
	private BlockPos coast;
	private BlockPos peaks;

	@Override
	public void runTest(ClientGameTestContext context) {
		watchdog(Integer.getInteger("atlas.test.watchdogMinutes", 45) * 60_000L, 3);
		context.runOnClient(mc -> {
			if (mc.gui.hud.isHidden()) {
				mc.gui.hud.toggle();
			}
			mc.options.renderDistance().set(DISTANCE);
			mc.options.enableVsync().set(false);
			mc.options.framerateLimit().set(260);
			mc.options.inactivityFpsLimit().set(InactivityFpsLimit.MINIMIZED);
			AtlasSettings s = AtlasClient.get().settings();
			s.minimap = true;
			s.minimapZoom = 5;
			s.mapZoom = 6;
		});
		try {
			Files.createDirectories(MODE.equals("perf") ? OUT.getParent() : OUT);
		} catch (IOException e) {
			throw new RuntimeException(e);
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
			world.getServer().runCommand("time set 6000");
			world.getServer().runCommand("gamemode spectator @a");
			world.getServer().runCommand("gamerule send_command_feedback false");
			this.findSpots(world);
			if (MODE.equals("perf")) {
				this.perf(context, world);
			} else {
				this.tour(context, world);
			}
			context.runOnClient(mc -> mc.gui.setScreen(null));
			context.waitTicks(5);
		}
		log("finished " + MODE + "; " + this.shots.size() + " screenshots");
		// the world close can deadlock in the lock-step test threading; everything is written by now
		watchdog(90_000L, 0);
	}

	private static void log(String s) {
		System.out.println("[atlas-test] " + s);
	}

	/** Ends the game after {@code ms} if it is still running (a hung world close, a stuck run). */
	private static void watchdog(long ms, int status) {
		Thread t = new Thread(() -> {
			try {
				Thread.sleep(ms);
			} catch (InterruptedException e) {
				return;
			}
			System.out.println("[atlas-test] watchdog: halting after " + ms / 1000 + " s");
			Runtime.getRuntime().halt(status);
		}, "Atlas test watchdog");
		t.setDaemon(true);
		t.start();
	}

	// --- the route -------------------------------------------------------------------------------------------------

	private void findSpots(TestSingleplayerContext world) {
		int[] found = world.getServer().computeOnServer(server -> {
			ServerLevel level = server.overworld();
			BlockPos s = level.getRespawnData().pos();
			Pair<BlockPos, Holder<Biome>> ocean = level.findClosestBiome3d(h -> h.is(BiomeTags.IS_OCEAN), s, 2400, 32, 64);
			BlockPos o = ocean == null ? s.offset(600, 0, 0) : ocean.getFirst();
			Pair<BlockPos, Holder<Biome>> mountain = level.findClosestBiome3d(h -> h.is(BiomeTags.IS_MOUNTAIN), o, 2400, 32, 64);
			BlockPos m = mountain == null ? s.offset(-600, 0, 0) : mountain.getFirst();
			return new int[]{s.getX(), s.getZ(), o.getX(), o.getZ(), m.getX(), m.getZ()};
		});
		this.spawn = new BlockPos(found[0], 100, found[1]);
		this.coast = new BlockPos(found[2], 100, found[3]);
		this.peaks = new BlockPos(found[4], 100, found[5]);
		log("spawn " + this.spawn.toShortString() + ", coast " + this.coast.toShortString() + ", peaks " + this.peaks.toShortString());
	}

	/** Flies a route through spawn, the coast and the mountains, a band three stops wide, mapping as it goes. */
	private void fly(ClientGameTestContext context, TestSingleplayerContext world) {
		List<BlockPos> stops = new ArrayList<>();
		addLine(stops, this.spawn, this.coast);
		addLine(stops, this.coast, this.peaks);
		addLine(stops, this.peaks, this.spawn);
		if (ONLY.contains("quick")) {
			// a short look round spawn only, for quick iterations on the look
			stops = new ArrayList<>(List.of(this.spawn, this.spawn.offset(300, 0, 0), this.spawn.offset(0, 0, 300)));
		}
		log("flying " + stops.size() + " stops");
		long t0 = System.currentTimeMillis();
		for (BlockPos stop : stops) {
			this.visit(context, world, stop.getX(), stop.getZ(), 4000);
		}
		log("flight done in " + (System.currentTimeMillis() - t0) / 1000 + " s");
	}

	private static void addLine(List<BlockPos> stops, BlockPos a, BlockPos b) {
		double len = Math.hypot(b.getX() - a.getX(), b.getZ() - a.getZ());
		int n = Math.max(1, (int) Math.ceil(len / 300));
		double nx = -(b.getZ() - a.getZ()) / Math.max(1, len);
		double nz = (b.getX() - a.getX()) / Math.max(1, len);
		for (int i = 0; i <= n; i++) {
			double t = i / (double) n;
			int x = (int) (a.getX() + (b.getX() - a.getX()) * t);
			int z = (int) (a.getZ() + (b.getZ() - a.getZ()) * t);
			stops.add(new BlockPos(x, 0, z));
			if (i % 2 == 1) {
				stops.add(new BlockPos((int) (x + nx * 340), 0, (int) (z + nz * 340)));
				stops.add(new BlockPos((int) (x - nx * 340), 0, (int) (z - nz * 340)));
			}
		}
	}

	/** Generates round a point on the server, moves the player there, and waits for Atlas to map it. */
	private void visit(ClientGameTestContext context, TestSingleplayerContext world, int x, int z, long maxMs) {
		int y = world.getServer().computeOnServer(server -> {
			ServerLevel level = server.overworld();
			int r = DISTANCE + 1;
			for (int dz = -r; dz <= r; dz++) {
				for (int dx = -r; dx <= r; dx++) {
					level.getChunk((x >> 4) + dx, (z >> 4) + dz);
				}
			}
			return level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
		});
		world.getServer().runCommand(String.format(Locale.ROOT, "tp @a %d %d %d 0 30", x, Math.max(y + 40, 140), z));
		this.settle(context, maxMs);
	}

	/** Waits until Atlas has nothing left to do (chunks read, tiles drawn and uploaded), or {@code maxMs}. */
	private void settle(ClientGameTestContext context, long maxMs) {
		long until = System.currentTimeMillis() + maxMs;
		long minUntil = System.currentTimeMillis() + Math.min(maxMs, 1500);
		int quiet = 0;
		while (System.currentTimeMillis() < until) {
			context.waitTicks(5);
			int busy = context.computeOnClient(mc -> AtlasClient.get().busy());
			quiet = busy == 0 ? quiet + 1 : 0;
			if (quiet >= 4 && System.currentTimeMillis() > minUntil) {
				break;
			}
		}
	}

	// --- the tour ----------------------------------------------------------------------------------------------------

	private boolean want(String name) {
		return ONLY.isBlank() || List.of(ONLY.split(",")).contains(name);
	}

	private void tour(ClientGameTestContext context, TestSingleplayerContext world) {
		this.fly(context, world);
		// a few waypoints, as a player would have
		context.runOnClient(mc -> {
			AtlasApi api = AtlasApi.get();
			api.addWaypoint("Home", this.spawn.getX() + 12, 72, this.spawn.getZ() - 20, "minecraft:overworld");
			api.addWaypoint("Lookout", this.peaks.getX(), 150, this.peaks.getZ(), "minecraft:overworld");
			api.addWaypoint("Harbour", this.coast.getX(), 63, this.coast.getZ(), "minecraft:overworld");
			api.addWaypoint("Mine shaft", this.spawn.getX() - 60, -42, this.spawn.getZ() + 40, "minecraft:overworld");
		});
		BlockPos mid = new BlockPos((this.coast.getX() + this.peaks.getX()) / 2, 0, (this.coast.getZ() + this.peaks.getZ()) / 2);
		this.visit(context, world, mid.getX(), mid.getZ(), SETTLE_MS);
		this.analyse(context);

		if (this.want("minimap")) {
			world.getServer().runCommand(String.format(Locale.ROOT, "tp @a %d %d %d 135 25", mid.getX(), 150, mid.getZ()));
			this.settle(context, SETTLE_MS);
			context.waitTicks(40);
			this.shoot(context, "minimap");
			context.runOnClient(mc -> AtlasClient.get().settings().minimapRotates = true);
			context.waitTicks(10);
			this.shoot(context, "minimap_rotating");
			context.runOnClient(mc -> AtlasClient.get().settings().minimapRotates = false);
		}
		this.openMap(context);
		double[][] zooms = {{0.5, 0}, {2, 1}, {4, 2}};
		for (double[] z : zooms) {
			if (this.want("map_" + (int) z[1])) {
				this.mapShot(context, "map_zoom" + ((int) z[1] + 1), this.spawn.getX(), this.spawn.getZ(), z[0], MapPanel.Kind.SURFACE);
			}
		}
		if (this.want("coast")) {
			double span = Math.hypot(this.coast.getX() - this.peaks.getX(), this.coast.getZ() - this.peaks.getZ());
			double zoom = span > 900 ? 0.5 : span > 450 ? 1 : 2;
			this.mapShot(context, "coast_mountains", mid.getX(), mid.getZ(), zoom, MapPanel.Kind.SURFACE);
			this.mapShot(context, "coast_close", this.coast.getX(), this.coast.getZ(), 3, MapPanel.Kind.SURFACE);
			this.mapShot(context, "mountains_close", this.peaks.getX(), this.peaks.getZ(), 3, MapPanel.Kind.SURFACE);
		}
		if (this.want("card")) {
			this.cardShots(context);
		}
		if (this.want("places")) {
			this.placeShots(context);
		}
		if (this.want("widget")) {
			this.widgetShot(context);
		}
		if (this.want("toast")) {
			this.toastShot(context, world);
		}
		if (this.want("underground")) {
			this.undergroundShots(context, world);
		}
		if (this.want("nether")) {
			context.runOnClient(mc -> mc.gui.setScreen(null));
			world.getServer().runCommand("execute in minecraft:the_nether run tp @a 0 70 0");
			context.waitTicks(40);
			world.getServer().runOnServer(server -> {
				ServerLevel nether = server.getLevel(net.minecraft.world.level.Level.NETHER);
				for (int dz = -DISTANCE; dz <= DISTANCE; dz++) {
					for (int dx = -DISTANCE; dx <= DISTANCE; dx++) {
						nether.getChunk(dx, dz);
					}
				}
			});
			this.settle(context, SETTLE_MS * 2);
			context.waitTicks(20);
			this.shoot(context, "nether_minimap");
			this.openMap(context);
			this.mapShot(context, "nether", 0, 0, 2, MapPanel.Kind.NETHER);
			context.runOnClient(mc -> mc.gui.setScreen(null));
			world.getServer().runCommand(String.format(Locale.ROOT, "execute in minecraft:overworld run tp @a %d 120 %d", this.spawn.getX(), this.spawn.getZ()));
			context.waitTicks(40);
		}
		this.writeIndex();
		// everything mapped goes to disk now (the world close may hang in the test threading)
		context.runOnClient(mc -> AtlasClient.get().session().saveSoon(true));
		context.waitTicks(60);
	}

	private void openMap(ClientGameTestContext context) {
		context.runOnClient(mc -> AtlasClient.get().openMap());
		context.waitTicks(5);
	}

	private void mapShot(ClientGameTestContext context, String name, double x, double z, double zoom, MapPanel.Kind kind) {
		context.runOnClient(mc -> {
			if (!(mc.gui.screen() instanceof AtlasScreen)) {
				AtlasClient.get().openMap();
			}
			AtlasScreen screen = (AtlasScreen) mc.gui.screen();
			screen.page.map.setKind(kind);
			screen.page.map.centreOn(x, z);
			screen.page.map.setZoom(zoom);
			screen.page.map.selected = null;
		});
		this.settle(context, SETTLE_MS);
		context.waitTicks(10);
		this.shoot(context, name);
	}

	/** Analyses every surface region now (normally it waits for the land to be quiet), and lets the names settle. */
	private void analyse(ClientGameTestContext context) {
		context.runOnClient(mc -> {
			var session = AtlasClient.get().session();
			var d = session.dimension("minecraft:overworld", false);
			session.workers.execute(() -> session.landmarks.analyseAll(d));
		});
		this.settle(context, 60_000);
		context.waitTicks(40);
		String summary = context.computeOnClient(mc -> {
			var session = AtlasClient.get().session();
			int[] counts = new int[4];
			for (Place p : session.places.visible("minecraft:overworld")) {
				if (p.kind().equals(Place.REGION)) {
					counts[0]++;
				} else if (p.isNatural()) {
					counts[1]++;
				} else if (p.isDiscovered()) {
					counts[2]++;
				} else {
					counts[3]++;
				}
			}
			return counts[0] + " named regions, " + counts[1] + " natural landmarks, " + counts[2] + " structures discovered, " + counts[3] + " sighted";
		});
		log("places: " + summary);
		this.precision = context.computeOnClient(mc -> precisionReport());
		log(this.precision.replace('\n', ' '));
	}

	/**
	 * How the block heuristics did against the game's own structure data in this singleplayer world: sightings of the
	 * same structure folded into exact data (right), sightings inside another structure's bounds (wrong), and sightings
	 * of structures left over with no exact structure near them (false landmarks, or features the game has no
	 * structure for: dungeons, geodes, cave mouths).
	 */
	private static String precisionReport() {
		var session = AtlasClient.get().session();
		int[] p = session.places.precision();
		StringBuilder left = new StringBuilder();
		int leftover = 0;
		for (Place place : session.places.all()) {
			if (place.source().kind() == dev.voidmc.atlas.api.Source.Kind.SEEN && !place.isNatural() && !place.kind().equals("atlas:dungeon")
				&& !place.kind().equals("atlas:geode")) {
				leftover++;
				left.append("\n- ").append(place.kind()).append(" at ").append(place.x()).append(", ").append(place.y()).append(", ").append(place.z());
			}
		}
		return "Heuristics against exact data: " + p[0] + " sightings confirmed, " + p[1] + " inside another structure "
			+ session.places.contradictions() + ", " + leftover + " structure sightings with no exact structure" + left;
	}

	/** Surface places: zoomed out with names and "?"s, the legend, a sighted place's card, a discovered place's card. */
	private void placeShots(ClientGameTestContext context) {
		context.runOnClient(mc -> AtlasClient.get().settings().mapZoom = 2);
		this.mapShot(context, "surface_zoomed_out", this.spawn.getX(), this.spawn.getZ(), 0.25, MapPanel.Kind.SURFACE);
		context.runOnClient(mc -> ((AtlasScreen) mc.gui.screen()).page.legendTab = true);
		this.mapShot(context, "surface_legend", this.spawn.getX(), this.spawn.getZ(), 0.5, MapPanel.Kind.SURFACE);
		context.runOnClient(mc -> ((AtlasScreen) mc.gui.screen()).page.legendTab = false);
		// a sighted place and a discovered one, near each other if possible
		String[] picks = context.computeOnClient(mc -> {
			var session = AtlasClient.get().session();
			Place sighted = null;
			Place discovered = null;
			for (Place p : session.places.visible("minecraft:overworld")) {
				if (p.isLabel() || p.underground()) {
					continue;
				}
				if (dev.voidmc.atlas.client.ui.Kinds.sighted(p)) {
					if (sighted == null || !p.isNatural() && sighted.isNatural()) {
						sighted = p;
					}
				} else if (discovered == null || !p.isNatural() && discovered.isNatural()) {
					discovered = p;
				}
			}
			return new String[]{sighted == null ? "" : sighted.id() + "|" + sighted.x() + "|" + sighted.z(),
				discovered == null ? "" : discovered.id() + "|" + discovered.x() + "|" + discovered.z()};
		});
		String[] names = {"sighted_place_card", "discovered_place_card"};
		for (int i = 0; i < 2; i++) {
			if (picks[i].isEmpty()) {
				log("no " + names[i] + " to photograph");
				continue;
			}
			String[] parts = picks[i].split("\\|");
			double x = Double.parseDouble(parts[1]);
			double z = Double.parseDouble(parts[2]);
			context.runOnClient(mc -> {
				AtlasScreen screen = (AtlasScreen) mc.gui.screen();
				screen.page.map.setKind(MapPanel.Kind.SURFACE);
				screen.page.map.centreOn(x - 60, z);
				screen.page.map.setZoom(2);
				screen.page.map.selected = "atlas:place:" + parts[0];
			});
			this.settle(context, SETTLE_MS);
			context.waitTicks(10);
			this.shoot(context, names[i]);
		}
		context.runOnClient(mc -> ((AtlasScreen) mc.gui.screen()).page.map.selected = null);
		// sighted and discovered side by side: the middle of the two, close enough to read
		if (!picks[0].isEmpty() && !picks[1].isEmpty()) {
			String[] a = picks[0].split("\\|");
			String[] b = picks[1].split("\\|");
			double ax = Double.parseDouble(a[1]);
			double az = Double.parseDouble(a[2]);
			double bx = Double.parseDouble(b[1]);
			double bz = Double.parseDouble(b[2]);
			double span = Math.hypot(ax - bx, az - bz);
			double zoom = span > 700 ? 0.5 : span > 350 ? 1 : 2;
			this.mapShot(context, "sighted_and_discovered", (ax + bx) / 2, (az + bz) / 2, zoom, MapPanel.Kind.SURFACE);
		}
		this.mapShot(context, "region_labels", this.spawn.getX(), this.spawn.getZ(), 0.5, MapPanel.Kind.SURFACE);
		context.runOnClient(mc -> mc.gui.setScreen(null));
	}

	/** The whole map page as another mod embeds it (the VOID Hub's Map page): hosted by a plain screen. */
	private void widgetShot(ClientGameTestContext context) {
		context.runOnClient(mc -> mc.gui.setScreen(new WidgetHost()));
		context.waitTicks(10);
		this.settle(context, SETTLE_MS);
		this.shoot(context, "hub_widget");
		context.runOnClient(mc -> mc.gui.setScreen(null));
		context.waitTicks(2);
	}

	/**
	 * The HUD: the player flies to a sighted place; within reach it is discovered and its toast shows, with the minimap's
	 * edge markers pointing at the places still only sighted, and the compass strip on.
	 */
	private void toastShot(ClientGameTestContext context, TestSingleplayerContext world) {
		String target = context.computeOnClient(mc -> {
			var session = AtlasClient.get().session();
			var p = mc.player;
			Place best = null;
			for (Place pl : session.places.visible("minecraft:overworld")) {
				if (!pl.isLabel() && !pl.underground() && dev.voidmc.atlas.client.ui.Kinds.sighted(pl) && !pl.isNatural()
					&& (best == null || pl.distanceTo(p.getX(), p.getZ()) < best.distanceTo(p.getX(), p.getZ()))) {
					best = pl;
				}
			}
			if (best == null) {
				for (Place pl : session.places.visible("minecraft:overworld")) {
					if (!pl.isLabel() && !pl.underground() && dev.voidmc.atlas.client.ui.Kinds.sighted(pl)) {
						best = pl;
						break;
					}
				}
			}
			return best == null ? "" : best.x() + "|" + best.y() + "|" + best.z();
		});
		if (target.isEmpty()) {
			log("no sighted place for the toast");
			return;
		}
		String[] t = target.split("\\|");
		context.runOnClient(mc -> AtlasClient.get().settings().compass = true);
		world.getServer().runCommand(String.format(Locale.ROOT, "tp @a %s %d %s 0 40", t[0], Integer.parseInt(t[1]) + 12, t[2]));
		context.waitTicks(30);
		this.shoot(context, "hud_toast");
		context.runOnClient(mc -> AtlasClient.get().settings().compass = false);
	}

	/**
	 * The underground at two depths: the player is taken into a real cave near spawn at each depth and through it, so
	 * the reveal draws what they could see; then the map is opened on that slice.
	 */
	private void undergroundShots(ClientGameTestContext context, TestSingleplayerContext world) {
		int[][] bands = {{24, 56}, {-48, -8}};
		String[] names = {"underground_upper", "underground_deep"};
		for (int i = 0; i < 2; i++) {
			int lo = bands[i][0];
			int hi = bands[i][1];
			List<BlockPos> path = world.getServer().computeOnServer(server -> Caves.path(server.overworld(), this.spawn, lo, hi));
			if (path.isEmpty()) {
				log("no cave between Y " + lo + " and " + hi + " near spawn");
				continue;
			}
			context.runOnClient(mc -> mc.gui.setScreen(null));
			for (BlockPos stop : path) {
				world.getServer().runCommand(String.format(Locale.ROOT, "tp @a %d %d %d", stop.getX(), stop.getY(), stop.getZ()));
				context.waitTicks(6);
			}
			this.settle(context, SETTLE_MS * 2);
			BlockPos at = path.get(path.size() / 2);
			int band = Math.floorDiv(at.getY(), dev.voidmc.atlas.client.data.DimensionData.BAND);
			log(names[i] + ": " + path.size() + " stops through a cave at Y " + at.getY());
			this.openMap(context);
			context.runOnClient(mc -> {
				AtlasScreen screen = (AtlasScreen) mc.gui.screen();
				screen.page.map.setKind(MapPanel.Kind.UNDERGROUND);
				screen.page.map.setBand(band);
				screen.page.map.centreOn(at.getX(), at.getZ());
				screen.page.map.setZoom(3);
				screen.page.map.selected = null;
			});
			this.settle(context, SETTLE_MS);
			context.waitTicks(10);
			this.shoot(context, names[i]);
			context.runOnClient(mc -> mc.gui.setScreen(null));
		}
		world.getServer().runCommand(String.format(Locale.ROOT, "tp @a %d %d %d", this.spawn.getX(), 140, this.spawn.getZ()));
		context.waitTicks(20);
	}

	private void cardShots(ClientGameTestContext context) {
		// the detail card of a discovery if there is one (a waypoint if not), and the list beside it
		String selected = context.computeOnClient(mc -> {
			AtlasClient atlas = AtlasClient.get();
			for (Place d : atlas.session().places.visible("minecraft:overworld")) {
				if (d.isDiscovered() && !d.isNatural()) {
					return AtlasClient.placeMarkerId(d) + "|" + d.x() + "|" + d.z();
				}
			}
			Waypoint w = atlas.session().waypoints.all().get(0);
			return AtlasClient.waypointMarkerId(w) + "|" + w.x() + "|" + w.z();
		});
		String[] parts = selected.split("\\|");
		context.runOnClient(mc -> {
			if (!(mc.gui.screen() instanceof AtlasScreen)) {
				AtlasClient.get().openMap();
			}
			AtlasScreen screen = (AtlasScreen) mc.gui.screen();
			screen.page.map.setKind(MapPanel.Kind.SURFACE);
			screen.page.map.centreOn(Double.parseDouble(parts[1]) + 60, Double.parseDouble(parts[2]));
			screen.page.map.setZoom(2);
			screen.page.map.selected = parts[0];
		});
		this.settle(context, SETTLE_MS);
		context.waitTicks(10);
		this.shoot(context, "card");
		context.runOnClient(mc -> {
			AtlasScreen screen = (AtlasScreen) mc.gui.screen();
			screen.page.map.selected = null;
			Waypoint w = AtlasClient.get().session().waypoints.all().get(0);
			screen.page.edit(w, false);
		});
		context.waitTicks(10);
		this.shoot(context, "waypoint_editor");
		context.runOnClient(mc -> mc.gui.setScreen(null));
		context.waitTicks(2);
	}

	private void shoot(ClientGameTestContext context, String name) {
		Path shot = context.takeScreenshot("atlas_" + name);
		try {
			Path to = OUT.resolve(name + ".png");
			Files.copy(shot, to, StandardCopyOption.REPLACE_EXISTING);
			this.shots.add(name);
			log("screenshot " + to);
		} catch (IOException e) {
			log("could not copy " + shot + ": " + e);
		}
	}

	private String precision = "";

	private void writeIndex() {
		StringBuilder b = new StringBuilder("# Atlas tour\n\n");
		if (!this.precision.isEmpty()) {
			b.append(this.precision).append("\n\n");
		}
		for (String s : this.shots) {
			b.append("## ").append(s).append("\n\n![").append(s).append("](").append(s).append(".png)\n\n");
		}
		try {
			Files.writeString(OUT.resolve("index.md"), b.toString(), StandardCharsets.UTF_8);
		} catch (IOException e) {
			log("could not write the index: " + e);
		}
	}

	// --- the performance gate ------------------------------------------------------------------------------------------

	private void perf(ClientGameTestContext context, TestSingleplayerContext world) {
		String backend = System.getProperty("atlas.test.backend", "opengl");
		// 1. the flight, timed: minimap on, chunks arriving, Atlas reading them
		this.visit(context, world, this.spawn.getX(), this.spawn.getZ(), SETTLE_MS);
		world.getServer().runCommand(String.format(Locale.ROOT, "tp @a %d %d %d -90 20", this.spawn.getX(), 150, this.spawn.getZ()));
		context.waitTicks(20);
		long uploads0 = context.computeOnClient(mc -> Perf.uploads);
		long bytes0 = context.computeOnClient(mc -> Perf.uploadBytes);
		long scanned0 = Perf.SCANNED.get();
		long scanNanos0 = Perf.SCAN_NANOS.get();
		long detected0 = Perf.DETECTED.get();
		long detectNanos0 = Perf.DETECT_NANOS.get();
		context.runOnClient(mc -> Perf.startRecording());
		context.getInput().holdKey(o -> o.keyUp);
		context.getInput().holdKey(o -> o.keySprint);
		context.waitTicks(FRAMES_SECONDS * 20);
		context.getInput().releaseKey(o -> o.keyUp);
		context.getInput().releaseKey(o -> o.keySprint);
		Perf.Recording flight = context.computeOnClient(mc -> Perf.stopRecording());
		long[] spike = context.computeOnClient(mc -> Perf.MINIMAP_SPIKE.clone()); // the worst minimap frame of the flight
		if (AtlasClient.BASELINE) {
			// the vanilla baseline: the same flight with Atlas doing nothing, for comparison; nothing else to time
			String json = String.format(Locale.ROOT, """
				{
				  "backend": "%s",
				  "baseline": true,
				  "flight": {"frames": %d, "avgFrameMs": %.3f, "avgFps": %.0f, "p99FrameMs": %.3f, "maxFrameMs": %.3f}
				}
				""", backend, flight.count(), Perf.Recording.avgMs(flight.frames()), 1000 / Math.max(1e-6, Perf.Recording.avgMs(flight.frames())),
				Perf.Recording.percentileMs(flight.frames(), 0.99), Perf.Recording.maxMs(flight.frames()));
			try {
				Path out = Path.of(System.getProperty("atlas.test.out"));
				Files.createDirectories(out.getParent());
				Files.writeString(out, json, StandardCharsets.UTF_8);
			} catch (IOException e) {
				log("could not write the baseline: " + e);
			}
			log(String.format(Locale.ROOT, "baseline flight: %d frames, avg %.2f ms, p99 %.2f ms, max %.2f ms", flight.count(),
				Perf.Recording.avgMs(flight.frames()), Perf.Recording.percentileMs(flight.frames(), 0.99), Perf.Recording.maxMs(flight.frames())));
			return;
		}
		long flightUploads = context.computeOnClient(mc -> Perf.uploads) - uploads0;
		long flightBytes = context.computeOnClient(mc -> Perf.uploadBytes) - bytes0;
		long scanned = Perf.SCANNED.get() - scanned0;
		long scanNanos = Perf.SCAN_NANOS.get() - scanNanos0;
		long detected = Perf.DETECTED.get() - detected0;
		long detectNanos = Perf.DETECT_NANOS.get() - detectNanos0;
		double detectMs = detectNanos / 1e6 / Math.max(1, detected);
		log(String.format(Locale.ROOT, "detection: %d chunks at %.3f ms on the workers", detected, detectMs));

		// 1b. the underground reveal: the player walked through a real cave, the reveal timed on the workers
		long reveals0 = Perf.REVEALS.get();
		long revealNanos0 = Perf.REVEAL_NANOS.get();
		long opened0 = Perf.REVEAL_OPENED.get();
		long walkNanos0 = Perf.REVEAL_WALK_NANOS.get();
		List<BlockPos> cave = world.getServer().computeOnServer(server -> Caves.path(server.overworld(), this.spawn, -48, 40));
		for (BlockPos stop : cave) {
			world.getServer().runCommand(String.format(Locale.ROOT, "tp @a %d %d %d", stop.getX(), stop.getY(), stop.getZ()));
			context.waitTicks(8);
		}
		this.settle(context, SETTLE_MS);
		long reveals = Perf.REVEALS.get() - reveals0;
		double revealMs = (Perf.REVEAL_NANOS.get() - revealNanos0) / 1e6 / Math.max(1, reveals);
		long opened = (Perf.REVEAL_OPENED.get() - opened0) / Math.max(1, reveals);
		double walkMs = (Perf.REVEAL_WALK_NANOS.get() - walkNanos0) / 1e6 / Math.max(1, reveals);
		log(String.format(Locale.ROOT, "reveal: %d walks at %.3f ms (%.3f ms walking), %d open blocks each, through %d stops", reveals, revealMs, walkMs,
			opened, cave.size()));
		world.getServer().runCommand(String.format(Locale.ROOT, "tp @a %d %d %d -90 20", this.spawn.getX(), 150, this.spawn.getZ()));
		context.waitTicks(20);
		log(String.format(Locale.ROOT, "flight: %d frames, avg %.2f ms, max %.2f ms, minimap avg %.3f ms, ingest max %.3f ms/frame, %d chunks at %.3f ms",
			flight.count(), Perf.Recording.avgMs(flight.frames()), Perf.Recording.maxMs(flight.frames()), Perf.Recording.avgMs(flight.minimap()),
			Perf.Recording.maxMs(flight.ingest()), scanned, scanNanos / 1e6 / Math.max(1, scanned)));

		// 2. a few hundred tiles of synthetic land, far from the real map
		int regions = Integer.getInteger("atlas.test.regions", 16);
		int ox = 200;
		int oz = 200;
		context.runOnClient(mc -> Synthetic.fill(AtlasClient.get(), "minecraft:overworld", ox, oz, regions));
		long synthStart = System.currentTimeMillis();
		this.settle(context, 120_000);
		log("synthetic land: " + regions * regions + " regions drawn in " + (System.currentTimeMillis() - synthStart) / 1000 + " s");

		// 3. the map open over it, panned and zoomed by a script, timed
		this.openMap(context);
		double cx = (ox + regions / 2.0) * Coords.REGION_SIZE;
		double cz = (oz + regions / 2.0) * Coords.REGION_SIZE;
		double extent = regions * Coords.REGION_SIZE / 2.0;
		context.runOnClient(mc -> {
			AtlasScreen screen = (AtlasScreen) mc.gui.screen();
			screen.page.map.setKind(MapPanel.Kind.SURFACE);
			screen.page.map.centreOn(cx, cz);
			screen.page.map.setZoom(1);
		});
		context.waitTicks(20);
		long u0 = context.computeOnClient(mc -> Perf.uploads);
		long b0 = context.computeOnClient(mc -> Perf.uploadBytes);
		double seconds = FRAMES_SECONDS;
		context.runOnClient(mc -> {
			AtlasScreen screen = (AtlasScreen) mc.gui.screen();
			screen.page.map.script = new PanZoom(cx, cz, extent, seconds);
			Perf.startRecording();
		});
		context.waitTicks((int) (seconds * 20) + 10);
		Perf.Recording map = context.computeOnClient(mc -> Perf.stopRecording());
		long mapUploads = context.computeOnClient(mc -> Perf.uploads) - u0;
		long mapBytes = context.computeOnClient(mc -> Perf.uploadBytes) - b0;
		int distinct = PanZoom.lastDistinct;
		// 4. the underground: a band of synthetic caves, panned and zoomed the same way
		context.runOnClient(mc -> mc.gui.setScreen(null));
		int caveBand = -4;
		int cox = ox + regions + 4;
		context.runOnClient(mc -> Synthetic.fillCaves(AtlasClient.get(), "minecraft:overworld", cox, oz, regions, caveBand));
		this.settle(context, 120_000);
		this.openMap(context);
		double ucx = (cox + regions / 2.0) * Coords.REGION_SIZE;
		context.runOnClient(mc -> {
			AtlasScreen screen = (AtlasScreen) mc.gui.screen();
			screen.page.map.setKind(MapPanel.Kind.UNDERGROUND);
			screen.page.map.setBand(caveBand);
			screen.page.map.centreOn(ucx, cz);
			screen.page.map.setZoom(1);
		});
		context.waitTicks(20);
		long uu0 = context.computeOnClient(mc -> Perf.uploads);
		context.runOnClient(mc -> {
			AtlasScreen screen = (AtlasScreen) mc.gui.screen();
			screen.page.map.script = new PanZoom(ucx, cz, extent, seconds);
			Perf.startRecording();
		});
		context.waitTicks((int) (seconds * 20) + 10);
		Perf.Recording under = context.computeOnClient(mc -> Perf.stopRecording());
		long underUploads = context.computeOnClient(mc -> Perf.uploads) - uu0;
		log(String.format(Locale.ROOT, "underground map: %d frames, avg %.2f ms, p99 %.2f ms, max %.2f ms, atlas %.3f ms/frame, %d uploads",
			under.count(), Perf.Recording.avgMs(under.frames()), Perf.Recording.percentileMs(under.frames(), 0.99), Perf.Recording.maxMs(under.frames()),
			Perf.Recording.avgMs(under.map()), underUploads));

		String gpu = context.computeOnClient(mc -> {
			var info = com.mojang.blaze3d.systems.RenderSystem.getDevice().getDeviceInfo();
			return info.name() + " (" + info.backendName() + ")";
		});
		log(String.format(Locale.ROOT, "map: %d frames, avg %.2f ms (%.0f fps), p99 %.2f ms, max %.2f ms, atlas %.3f ms/frame, %.1f tiles/frame, %d uploads",
			map.count(), Perf.Recording.avgMs(map.frames()), 1000 / Math.max(1e-6, Perf.Recording.avgMs(map.frames())),
			Perf.Recording.percentileMs(map.frames(), 0.99), Perf.Recording.maxMs(map.frames()), Perf.Recording.avgMs(map.map()), map.avgTiles(), mapUploads));

		String json = String.format(Locale.ROOT, """
			{
			  "backend": "%s",
			  "gpu": "%s",
			  "window": "%s",
			  "map": {"frames": %d, "avgFrameMs": %.3f, "avgFps": %.0f, "p99FrameMs": %.3f, "maxFrameMs": %.3f, "atlasAvgMs": %.3f, "tilesDrawnAvg": %.1f, "tilesVisited": %d, "uploads": %d, "uploadMb": %.1f},
			  "flight": {"frames": %d, "avgFrameMs": %.3f, "avgFps": %.0f, "p99FrameMs": %.3f, "maxFrameMs": %.3f, "uploads": %d, "uploadMb": %.1f},
			  "minimap": {"avgMs": %.3f, "maxMs": %.3f, "p99Ms": %.3f},
			  "ingest": {"maxFrameMs": %.3f, "avgFrameMs": %.4f, "chunks": %d, "scanMsPerChunk": %.3f},
			  "detect": {"chunks": %d, "msPerChunk": %.3f},
			  "reveal": {"walks": %d, "msPerWalk": %.3f, "openPerWalk": %d},
			  "underMap": {"frames": %d, "avgFrameMs": %.3f, "avgFps": %.0f, "p99FrameMs": %.3f, "maxFrameMs": %.3f, "atlasAvgMs": %.3f, "uploads": %d},
			  "minimapSpike": {"totalMs": %.3f, "setupMs": %.3f, "tilesMs": %.3f, "markersMs": %.3f, "restMs": %.3f}
			}
			""",
			backend, gpu.replace("\"", "'"), context.computeOnClient(mc -> mc.getWindow().getWidth() + "x" + mc.getWindow().getHeight()),
			map.count(), Perf.Recording.avgMs(map.frames()), 1000 / Math.max(1e-6, Perf.Recording.avgMs(map.frames())),
			Perf.Recording.percentileMs(map.frames(), 0.99), Perf.Recording.maxMs(map.frames()), Perf.Recording.avgMs(map.map()), map.avgTiles(), distinct,
			mapUploads, mapBytes / 1048576.0,
			flight.count(), Perf.Recording.avgMs(flight.frames()), 1000 / Math.max(1e-6, Perf.Recording.avgMs(flight.frames())),
			Perf.Recording.percentileMs(flight.frames(), 0.99), Perf.Recording.maxMs(flight.frames()), flightUploads, flightBytes / 1048576.0,
			Perf.Recording.avgMs(flight.minimap()), Perf.Recording.maxMs(flight.minimap()), Perf.Recording.percentileMs(flight.minimap(), 0.99),
			Math.max(Math.max(Perf.Recording.maxMs(flight.ingest()), Perf.Recording.maxMs(map.ingest())), Perf.Recording.maxMs(under.ingest())),
			Perf.Recording.avgMs(flight.ingest()), scanned, scanNanos / 1e6 / Math.max(1, scanned),
			detected, detectMs,
			reveals, revealMs, opened,
			under.count(), Perf.Recording.avgMs(under.frames()), 1000 / Math.max(1e-6, Perf.Recording.avgMs(under.frames())),
			Perf.Recording.percentileMs(under.frames(), 0.99), Perf.Recording.maxMs(under.frames()), Perf.Recording.avgMs(under.map()), underUploads,
			spike[0] / 1e6, spike[1] / 1e6, spike[2] / 1e6, spike[3] / 1e6, spike[4] / 1e6);
		try {
			Files.writeString(OUT, json, StandardCharsets.UTF_8);
			log("results written to " + OUT);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		this.shoot(context, "perf_end");
	}

	/**
	 * The performance test's camera: over {@code seconds}, zooms from close up out to the coarsest level and back in
	 * while sweeping across the synthetic land, so every mip level and a few hundred tiles pass through the view.
	 */
	static final class PanZoom implements MapPanel.Script {
		static int lastDistinct;
		private final double cx;
		private final double cz;
		private final double extent;
		private final double seconds;
		private double t;
		private final java.util.Set<Long> seen = new java.util.HashSet<>();

		PanZoom(double cx, double cz, double extent, double seconds) {
			this.cx = cx;
			this.cz = cz;
			this.extent = extent;
			this.seconds = seconds;
		}

		@Override
		public boolean step(MapPanel panel, double dt) {
			this.t += dt;
			double u = this.t / this.seconds;
			if (u >= 1) {
				return false;
			}
			// zoom: 4 px/block down to 1/32 and back, twice; position: a Lissajous sweep over the land
			double phase = (u * 2) % 1;
			double logZoom = Math.log(4) + (Math.log(1.0 / 32) - Math.log(4)) * (1 - Math.cos(phase * 2 * Math.PI)) / 2;
			double zoom = Math.exp(logZoom);
			double x = this.cx + Math.sin(u * 2 * Math.PI * 3) * this.extent * 0.8;
			double z = this.cz + Math.sin(u * 2 * Math.PI * 2 + 1) * this.extent * 0.8;
			panel.setZoom(zoom);
			panel.centreOn(x, z);
			int level = Coords.levelFor(zoom);
			int span = Coords.tileSpan(level);
			for (int dz = -2; dz <= 2; dz++) {
				for (int dx = -3; dx <= 3; dx++) {
					this.seen.add(((long) level << 56) ^ Coords.pack(Math.floorDiv((int) x, span) + dx, Math.floorDiv((int) z, span) + dz));
				}
			}
			lastDistinct = this.seen.size();
			return true;
		}
	}
}
