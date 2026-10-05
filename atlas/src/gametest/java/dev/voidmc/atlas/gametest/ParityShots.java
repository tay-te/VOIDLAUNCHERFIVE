package dev.voidmc.atlas.gametest;

import dev.voidmc.atlas.api.Waypoint;
import dev.voidmc.atlas.client.AtlasClient;
import dev.voidmc.atlas.client.AtlasSettings;
import dev.voidmc.atlas.client.render.MapLight;
import dev.voidmc.atlas.client.ui.AtlasScreen;
import dev.voidmc.atlas.client.ui.HudOverlays;
import dev.voidmc.atlas.client.ui.MapPanel;
import dev.voidmc.atlas.client.ui.QuickAddScreen;
import dev.voidmc.atlas.client.ui.SettingsScreen;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * The tour's shots of the signed-off waypoints, minimap and reliability design (section 355:14013), one per frame
 * state: the beams far and near, quick-add and the death island, chat coordinates and the first-run hint, the
 * right-click menu and the waypoint card, search and the world picker, the minimap's variants and its settings, the
 * new-world and server-rule islands, the import card and the recovery island, the night map, the export dialog and the
 * colour-blind pin shapes.
 */
final class ParityShots {
	private final AtlasGameTest tour;
	private final ClientGameTestContext context;
	private final TestSingleplayerContext world;
	private final BlockPos spawn;

	ParityShots(AtlasGameTest tour, ClientGameTestContext context, TestSingleplayerContext world, BlockPos spawn) {
		this.tour = tour;
		this.context = context;
		this.world = world;
		this.spawn = spawn;
	}

	void run() {
		int ground = this.world.getServer().computeOnServer(s -> s.overworld().getHeight(Heightmap.Types.MOTION_BLOCKING, this.spawn.getX(), this.spawn.getZ()));
		int sx = this.spawn.getX();
		int sz = this.spawn.getZ();
		this.client(mc -> {
			AtlasClient.get().overlays().clearIslands();
			AtlasSettings s = AtlasClient.get().settings();
			s.waypointBeams = true;
			s.shapeMarkers = true;
			s.firstRunHintShown = true;
			var session = AtlasClient.get().session();
			long now = System.currentTimeMillis();
			// the beams' frame: one near (18 m, 6 above), and far ones, a temporary one and a death point
			session.waypoints.put(new Waypoint("t-home", "Home", sx + 14, ground + 6, sz + 11, "minecraft:overworld", 0, Waypoint.Kind.USER, now, true, 0,
				"HOUSE", 0, false));
			session.waypoints.put(new Waypoint("t-lookout", "Lookout", sx + 60, ground + 30, sz + 405, "minecraft:overworld", 1, Waypoint.Kind.USER, now, true));
			session.waypoints.put(new Waypoint("t-camp", "Camp", sx - 140, ground, sz + 260, "minecraft:overworld", 6, Waypoint.Kind.TEMPORARY, now, true,
				now + 4 * 60_000L));
			session.waypoints.put(new Waypoint("t-death", "Death", sx + 160, ground, sz + 300, "minecraft:overworld", 5, Waypoint.Kind.DEATH, now - 2 * 60_000L, true,
				now + 8 * 60_000L));
			session.waypoints.put(new Waypoint("t-harbour", "Harbour", sx + 520, ground, sz + 1100, "minecraft:overworld", 2, Waypoint.Kind.USER, now, true));
		});
		// standing on the ground, looking south over them
		this.world.getServer().runCommand(String.format(Locale.ROOT, "tp @a %d %d %d 0 8", sx, ground + 2, sz));
		this.context.waitTicks(60);
		this.tour.shootPublic(this.context, "beams");

		// quick-add (B) and the death island
		this.client(mc -> QuickAddScreen.open());
		this.context.waitTicks(10);
		this.tour.shootPublic(this.context, "quick_add");
		this.client(mc -> {
			mc.gui.setScreen(null);
			AtlasClient.get().overlays().death(8);
		});
		this.context.waitTicks(10);
		this.tour.shootPublic(this.context, "death_island");

		// the first-run hint, then chat with coordinates in it
		this.client(mc -> AtlasClient.get().overlays().island(new HudOverlays.Toast("ATLAS  ·  FIRST TIME", "Your map is ready",
			dev.voidmc.atlas.api.MarkerGlyph.DOT, true, "M", "opens your map", null, HudOverlays.FIRST_RUN_NANOS)));
		this.context.waitTicks(15);
		this.tour.shootPublic(this.context, "first_run_hint");
		this.client(mc -> {
			AtlasClient.get().overlays().clearIslands();
			mc.gui.hud.getChat().addClientSystemMessage(Component.literal("Jun: base is at 412 64 -1288 if you need it"));
			mc.gui.hud.getChat().addClientSystemMessage(Component.literal("Mara: the portal is at X: -120 Y: 70 Z: 340"));
			String code = dev.voidmc.atlas.core.waypoints.WaypointShare.encode(
				dev.voidmc.atlas.core.waypoints.SharedWaypoint.of("Amethyst cave", sx + 412, -18, sz - 128, "minecraft:overworld", 2));
			mc.gui.hud.getChat().addClientSystemMessage(Component.literal("Mara: " + code));
			mc.gui.setScreen(new net.minecraft.client.gui.screens.ChatScreen("", false));
		});
		this.context.waitTicks(10);
		this.tour.shootPublic(this.context, "chat_coordinates");
		this.client(mc -> mc.gui.setScreen(null));

		// the map: the right-click menu, the waypoint card with both dimensions, search, the world picker
		this.openMap(sx, sz, 1, MapPanel.Kind.SURFACE);
		this.client(mc -> {
			AtlasScreen screen = (AtlasScreen) mc.gui.screen();
			float[] r = screen.page.mapRect();
			screen.page.openMenuAt(r[0] + r[2] * 0.62F, r[1] + r[3] * 0.45F);
		});
		this.context.waitTicks(5);
		this.tour.shootPublic(this.context, "context_menu");
		this.client(mc -> {
			AtlasScreen screen = (AtlasScreen) mc.gui.screen();
			screen.page.closeMenu();
			screen.page.map.selected = "atlas:wp:t-home";
		});
		this.context.waitTicks(5);
		this.tour.shootPublic(this.context, "waypoint_card");
		this.client(mc -> {
			AtlasScreen screen = (AtlasScreen) mc.gui.screen();
			screen.page.map.selected = null;
			screen.page.searchFor("o");
		});
		this.context.waitTicks(10);
		this.tour.shootPublic(this.context, "search");
		this.client(mc -> {
			AtlasScreen screen = (AtlasScreen) mc.gui.screen();
			screen.page.searchFor("");
			screen.page.showPicker(true);
		});
		this.context.waitTicks(5);
		this.tour.shootPublic(this.context, "world_picker");
		this.client(mc -> ((AtlasScreen) mc.gui.screen()).page.showPicker(false));

		// the night map
		this.client(mc -> {
			AtlasClient.get().settings().mapLook = AtlasSettings.MapLook.NIGHT;
			MapLight.settle(AtlasSettings.MapLook.NIGHT, 0);
		});
		this.openMap(sx, sz, 2, MapPanel.Kind.SURFACE);
		this.tour.shootPublic(this.context, "night_map");
		this.client(mc -> {
			AtlasClient.get().settings().mapLook = AtlasSettings.MapLook.FOLLOW;
			MapLight.settle(AtlasSettings.MapLook.DAY, 0);
		});

		// the export dialog
		this.client(mc -> ((AtlasScreen) mc.gui.screen()).page.openExport());
		this.context.waitTicks(20);
		this.tour.shootPublic(this.context, "export_dialog");
		this.client(mc -> mc.gui.setScreen(null));

		// the colour-blind pin shapes: one waypoint of each colour
		this.client(mc -> {
			var session = AtlasClient.get().session();
			String[] names = {"White", "Sage", "Blue", "Lilac", "Rose", "Clay", "Amber", "Grey"};
			for (int i = 0; i < 8; i++) {
				session.waypoints.put(new Waypoint("t-shape-" + i, names[i], sx - 80 + (i % 4) * 54, ground, sz - 60 + (i / 4) * 50, "minecraft:overworld", i,
					Waypoint.Kind.USER, System.currentTimeMillis(), true));
			}
		});
		this.openMap(sx - 0, sz - 40, 3, MapPanel.Kind.SURFACE);
		this.tour.shootPublic(this.context, "pin_shapes");
		this.client(mc -> {
			var session = AtlasClient.get().session();
			for (int i = 0; i < 8; i++) {
				session.waypoints.remove("t-shape-" + i);
			}
			mc.gui.setScreen(null);
		});

		// the minimap: round, rotating; large; radar (with a few mobs about); the settings window
		this.world.getServer().runCommand(String.format(Locale.ROOT, "tp @a %d %d %d 0 30", sx, ground + 30, sz));
		for (int i = 0; i < 4; i++) {
			this.world.getServer().runCommand(String.format(Locale.ROOT, "summon minecraft:cow %d %d %d", sx + 10 + i * 4, ground + 1, sz - 12 + i * 5));
			this.world.getServer().runCommand(String.format(Locale.ROOT, "summon minecraft:zombie %d %d %d {NoAI:1b,PersistenceRequired:1b}", sx - 14 + i * 6, ground + 1,
				sz + 8 - i * 4));
		}
		this.client(mc -> {
			AtlasSettings s = AtlasClient.get().settings();
			s.minimapShape = AtlasSettings.Shape.ROUND;
			s.radarPlayers = true;
			s.radarMobs = true;
		});
		this.context.waitTicks(30);
		this.tour.shootPublic(this.context, "minimap_round_radar");
		this.client(mc -> {
			AtlasSettings s = AtlasClient.get().settings();
			s.minimapShape = AtlasSettings.Shape.SQUARE;
			s.minimapSize = AtlasSettings.Size.LARGE;
			s.minimapRotates = true;
		});
		this.context.waitTicks(20);
		this.tour.shootPublic(this.context, "minimap_large_rotating");
		this.client(mc -> {
			AtlasSettings s = AtlasClient.get().settings();
			s.minimapSize = AtlasSettings.Size.MEDIUM;
			s.minimapRotates = false;
			s.radarPlayers = false;
			s.radarMobs = false;
			mc.gui.setScreen(new SettingsScreen(AtlasClient.get(), null));
		});
		this.context.waitTicks(10);
		this.tour.shootPublic(this.context, "settings_minimap");
		this.client(mc -> mc.gui.setScreen(null));
		// cave view: deep under the ground, after its second
		this.world.getServer().runCommand(String.format(Locale.ROOT, "tp @a %d %d %d 0 30", sx, -30, sz));
		this.context.waitTicks(60);
		this.tour.shootPublic(this.context, "minimap_cave");
		this.world.getServer().runCommand(String.format(Locale.ROOT, "tp @a %d %d %d 0 30", sx, ground + 30, sz));
		this.context.waitTicks(30);

		// the islands: a new world (the prompt), the server's rules (and the note under the readout), recovery
		this.client(mc -> AtlasClient.get().overlays().prompt("ATLAS  ·  NEW WORLD", "New world detected on hypixel.net", "Keep separate", "Same as before"));
		this.context.waitTicks(15);
		this.tour.shootPublic(this.context, "new_world_prompt");
		this.client(mc -> {
			AtlasClient.get().overlays().clearIslands();
			AtlasClient.get().actAsServer = true;
			AtlasClient.get().rules().accept("§f§a§i§r§x§a§e§r§o");
		});
		this.context.waitTicks(20);
		this.tour.shootPublic(this.context, "server_rules");
		this.client(mc -> {
			AtlasClient.get().rules().reset();
			AtlasClient.get().actAsServer = false;
			AtlasClient.get().overlays().clearIslands();
			AtlasClient.get().overlays().info("ATLAS  ·  RECOVERY", "Map restored from backup");
		});
		this.context.waitTicks(15);
		this.tour.shootPublic(this.context, "restored_from_backup");

		// the import card: a Xaero's Minimap file for this world, found once
		String save = this.context.computeOnClient(mc -> AtlasClient.get().session().worldKey);
		String folder = save.substring(save.indexOf('/') + 1);
		Path dim = Path.of("xaero", "minimap", folder, "dim%0");
		try {
			Files.createDirectories(dim);
			Files.writeString(dim.resolve("mw$default_1.txt"), String.join("\n",
				"#waypoint:name:initials:x:y:z:color:disabled:type:set:rotate_on_tp:tp_yaw:visibility_type:destination",
				"waypoint:Home base:H:" + (sx + 30) + ":70:" + (sz - 20) + ":15:false:0:gui.xaero_default:false:0:0:false",
				"waypoint:Nether hub:N:" + (sx - 200) + ":64:" + (sz + 90) + ":12:false:0:gui.xaero_default:false:0:0:false",
				"waypoint:Iron farm:I:" + (sx + 300) + ":70:" + (sz + 310) + ":10:false:0:gui.xaero_default:false:0:0:false",
				"waypoint:Ocean village:O:" + (sx + 610) + ":63:" + (sz - 400) + ":9:false:0:gui.xaero_default:false:0:0:false",
				"waypoint:Sheep pen:S:" + (sx - 40) + ":70:" + (sz - 90) + ":2:false:0:gui.xaero_default:false:0:0:false", ""), StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		this.client(mc -> {
			AtlasClient.get().overlays().clearIslands();
			AtlasClient.get().offerImport();
		});
		this.context.waitTicks(40);
		this.tour.shootPublic(this.context, "import_card");
		this.client(mc -> mc.gui.setScreen(null));
	}

	private void openMap(double x, double z, double zoom, MapPanel.Kind kind) {
		this.client(mc -> {
			if (!(mc.gui.screen() instanceof AtlasScreen)) {
				AtlasClient.get().openMap();
			}
			AtlasScreen screen = (AtlasScreen) mc.gui.screen();
			screen.page.map.setKind(kind);
			screen.page.map.centreOn(x, z);
			screen.page.map.setZoom(zoom);
			screen.page.map.selected = null;
		});
		this.context.waitTicks(30);
	}

	private void client(Consumer<net.minecraft.client.Minecraft> action) {
		this.context.runOnClient(action::accept);
	}
}
