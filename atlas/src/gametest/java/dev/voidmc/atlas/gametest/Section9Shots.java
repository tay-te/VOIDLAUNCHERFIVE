package dev.voidmc.atlas.gametest;

import dev.voidmc.atlas.client.AtlasClient;
import dev.voidmc.atlas.client.AtlasSettings;
import dev.voidmc.atlas.client.render.MapLight;
import dev.voidmc.atlas.client.ui.AtlasScreen;
import dev.voidmc.atlas.client.ui.MapPanel;
import dev.voidmc.atlas.client.ui.SettingsScreen;
import dev.voidmc.atlas.core.MapArt;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * The tour's shots of section 9 (Figma 395:19899): the biome view, a highlighted family with the hover, the
 * underground's and the Nether's biomes, the minimap's biome tint, the block colours, water, relief and contours,
 * the night with block light, details at 2:1, the three styles, and the settings rows. With Expanse installed the same
 * biome view shows a mod's biomes and their colours.
 */
final class Section9Shots {
	private final AtlasGameTest tour;
	private final ClientGameTestContext context;
	private final TestSingleplayerContext world;
	private final BlockPos spawn;
	private final BlockPos coast;
	private final BlockPos peaks;

	Section9Shots(AtlasGameTest tour, ClientGameTestContext context, TestSingleplayerContext world, BlockPos spawn, BlockPos coast, BlockPos peaks) {
		this.tour = tour;
		this.context = context;
		this.world = world;
		this.spawn = spawn;
		this.coast = coast;
		this.peaks = peaks;
	}

	private void client(Consumer<Minecraft> action) {
		this.context.runOnClient(action::accept);
	}

	private void shot(String name) {
		this.tour.shootPublic(this.context, name);
	}

	/** The map open on a layer and look, centred, zoomed, settled; then the shot. */
	private void map(String name, double x, double z, double zoom, MapPanel.Kind kind, boolean biomes, int tab) {
		this.client(mc -> {
			if (!(mc.gui.screen() instanceof AtlasScreen)) {
				AtlasClient.get().openMap();
			}
			AtlasScreen screen = (AtlasScreen) mc.gui.screen();
			screen.page.map.setKind(kind);
			screen.page.map.setBiomeLook(biomes);
			screen.page.rightTab = tab;
			screen.page.map.centreOn(x, z);
			screen.page.map.setZoom(zoom);
			screen.page.map.selected = null;
		});
		this.settle(240);
		this.shot(name);
	}

	/** Lets the tiles drawn again come in (a restyle redraws a few a frame). */
	private void settle(int ticks) {
		long until = System.currentTimeMillis() + 15_000;
		this.context.waitTicks(20);
		while (System.currentTimeMillis() < until && this.context.computeOnClient(mc -> AtlasClient.get().busy()) > 0) {
			this.context.waitTicks(10);
		}
		this.context.waitTicks(Math.max(10, ticks / 8));
	}

	void run(boolean modded) {
		int sx = this.spawn.getX();
		int sz = this.spawn.getZ();
		this.client(mc -> {
			mc.gui.setScreen(null);
			AtlasSettings s = AtlasClient.get().settings();
			s.mapStyle = MapArt.Style.NATURAL;
			s.contours = null;
			s.mapLook = AtlasSettings.MapLook.DAY;
			MapLight.settle(s.mapLook, 0);
			s.biomeLook.clear();
			AtlasClient.get().biomeHighlight = null;
		});
		if (modded) {
			// an Expanse world: the biome view with a mod's biomes and their colours, from its grass and foliage
			this.map("s9_modded_biomes", sx, sz, 0.5, MapPanel.Kind.SURFACE, true, 1);
			return;
		}
		// the biome view, then a family highlighted with the hover on it
		this.map("s9_biome_view", sx, sz, 0.5, MapPanel.Kind.SURFACE, true, 1);
		this.client(mc -> AtlasClient.get().biomeHighlight = MapArt.Family.FORESTS);
		this.settle(240);
		double[] at = this.context.computeOnClient(mc -> {
			AtlasScreen screen = (AtlasScreen) mc.gui.screen();
			var f = screen.page.map.frame();
			return new double[]{f.screenX(sx + 60, sz + 40) * screen.unit(), f.screenY(sx + 60, sz + 40) * screen.unit()};
		});
		this.context.getInput().setCursorPos(at[0], at[1]);
		this.context.waitTicks(10);
		this.shot("s9_biome_highlight");
		this.context.getInput().setCursorPos(5, 5);
		this.client(mc -> AtlasClient.get().biomeHighlight = null);

		// the terrain art: block colours, water, relief, details at 2:1
		this.map("s9_block_colours", sx, sz, 1, MapPanel.Kind.SURFACE, false, 0);
		this.map("s9_water", this.coast.getX(), this.coast.getZ(), 1, MapPanel.Kind.SURFACE, false, 0);
		this.map("s9_relief", this.peaks.getX(), this.peaks.getZ(), 0.5, MapPanel.Kind.SURFACE, false, 0);
		this.map("s9_details", sx, sz, 2, MapPanel.Kind.SURFACE, false, 0);
		// the styles
		for (MapArt.Style style : MapArt.Style.values()) {
			this.client(mc -> {
				AtlasClient.get().settings().mapStyle = style;
				AtlasClient.get().settings().contours = null;
			});
			this.map("s9_style_" + style.name().toLowerCase(Locale.ROOT), this.peaks.getX(), this.peaks.getZ(), 1, MapPanel.Kind.SURFACE, false, 0);
		}
		this.client(mc -> {
			AtlasClient.get().settings().mapStyle = MapArt.Style.NATURAL;
			AtlasClient.get().settings().contours = true;
		});
		this.map("s9_contours", this.peaks.getX(), this.peaks.getZ(), 1, MapPanel.Kind.SURFACE, false, 0);
		this.client(mc -> AtlasClient.get().settings().contours = null);

		// the night: a few torches and lanterns near the spawn, then the moonlit map
		int gy = this.world.getServer().computeOnServer(s -> s.overworld().getHeight(Heightmap.Types.MOTION_BLOCKING, sx, sz));
		for (int i = 0; i < 9; i++) {
			int tx = sx + 10 + (i % 3) * 9;
			int tz = sz + 10 + (i / 3) * 9;
			int ty = this.world.getServer().computeOnServer(s -> s.overworld().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, tx, tz));
			this.world.getServer().runCommand(String.format(Locale.ROOT, "setblock %d %d %d %s", tx, ty, tz, i % 2 == 0 ? "minecraft:lantern" : "minecraft:torch"));
		}
		this.world.getServer().runCommand(String.format(Locale.ROOT, "tp @a %d %d %d", sx, gy + 40, sz));
		this.context.waitTicks(60);
		this.client(mc -> {
			AtlasClient.get().settings().mapLook = AtlasSettings.MapLook.NIGHT;
			MapLight.settle(AtlasSettings.MapLook.NIGHT, 0);
		});
		this.map("s9_night", sx + 20, sz + 20, 1, MapPanel.Kind.SURFACE, false, 0);
		this.client(mc -> {
			AtlasClient.get().settings().mapLook = AtlasSettings.MapLook.DAY;
			MapLight.settle(AtlasSettings.MapLook.DAY, 0);
		});

		// the minimap's biome tint, in the HUD
		this.client(mc -> {
			mc.gui.setScreen(null);
			AtlasClient.get().settings().minimapBiomeTint = true;
		});
		this.settle(120);
		this.shot("s9_minimap_tint");
		this.client(mc -> AtlasClient.get().settings().minimapBiomeTint = false);
		// the settings: the map style and contours, and the minimap's colours
		this.client(mc -> mc.gui.setScreen(new SettingsScreen(AtlasClient.get(), null).showTab(1)));
		this.context.waitTicks(10);
		this.shot("s9_settings_map");

		// the underground's biomes: a walk through a cave, then the slice in the biome view
		List<BlockPos> path = this.world.getServer().computeOnServer(server -> Caves.path(server.overworld(), this.spawn, -48, 0));
		if (!path.isEmpty()) {
			this.client(mc -> mc.gui.setScreen(null));
			for (BlockPos stop : path) {
				this.world.getServer().runCommand(String.format(Locale.ROOT, "tp @a %d %d %d", stop.getX(), stop.getY(), stop.getZ()));
				this.context.waitTicks(5);
			}
			BlockPos mid = path.get(path.size() / 2);
			int band = Math.floorDiv(mid.getY(), dev.voidmc.atlas.client.data.DimensionData.BAND);
			this.client(mc -> {
				AtlasClient.get().openMap();
				((AtlasScreen) mc.gui.screen()).page.map.setKind(MapPanel.Kind.UNDERGROUND);
				((AtlasScreen) mc.gui.screen()).page.map.setBand(band);
			});
			this.map("s9_underground_biomes", mid.getX(), mid.getZ(), 3, MapPanel.Kind.UNDERGROUND, true, 0);
			this.client(mc -> ((AtlasScreen) mc.gui.screen()).page.map.setBiomeLook(false));
		}
		// the Nether's biomes
		this.client(mc -> mc.gui.setScreen(null));
		this.world.getServer().runCommand("execute in minecraft:the_nether run tp @a 0 70 0");
		this.context.waitTicks(40);
		this.world.getServer().runOnServer(server -> {
			var nether = server.getLevel(net.minecraft.world.level.Level.NETHER);
			for (int dz = -6; dz <= 6; dz++) {
				for (int dx = -6; dx <= 6; dx++) {
					nether.getChunk(dx, dz);
				}
			}
		});
		this.context.waitTicks(300);
		this.map("s9_nether_biomes", 0, 0, 0.5, MapPanel.Kind.NETHER, true, 1);
		this.client(mc -> {
			((AtlasScreen) mc.gui.screen()).page.map.setBiomeLook(false);
			mc.gui.setScreen(null);
		});
		this.world.getServer().runCommand(String.format(Locale.ROOT, "execute in minecraft:overworld run tp @a %d %d %d", sx, 140, sz));
		this.context.waitTicks(60);
	}
}
