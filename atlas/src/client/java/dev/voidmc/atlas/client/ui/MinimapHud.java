package dev.voidmc.atlas.client.ui;

import dev.voidmc.atlas.api.MapLayer;
import dev.voidmc.atlas.api.Marker;
import dev.voidmc.atlas.api.MarkerQuery;
import dev.voidmc.atlas.client.AtlasClient;
import dev.voidmc.atlas.client.AtlasSettings;
import dev.voidmc.atlas.client.Perf;
import dev.voidmc.atlas.client.data.DimensionData;
import dev.voidmc.atlas.client.data.LayerData;
import dev.voidmc.atlas.client.render.MapRenderer;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * The minimap: a rounded square of the map round the player, north-up or turning with them, with the player's arrow,
 * nearby markers, a north mark on the rim and a small readout of the biome and coordinates under it. Hidden with the
 * HUD (F1), behind any screen and under the debug overlay. Underground (or in the Nether) it shows the slice the
 * player is in.
 */
public final class MinimapHud {
	private final AtlasClient atlas;
	private final MapRenderer renderer = new MapRenderer();
	private double zoom = Double.NaN;

	public MinimapHud(AtlasClient atlas) {
		this.atlas = atlas;
	}

	public static float hudUnit() {
		var window = Minecraft.getInstance().getWindow();
		return Ui.pick(Math.max(0.75F, window.getHeight() / 864F));
	}

	public void extract(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		AtlasSettings settings = this.atlas.settings();
		LocalPlayer p = mc.player;
		if (!settings.minimap || p == null || mc.level == null || mc.gui.screen() != null || mc.gui.hud.isHidden()
			|| mc.getDebugOverlay().showDebugScreen() || this.atlas.session() == null) {
			return;
		}
		long t0 = System.nanoTime();
		Ui ui = new Ui(graphics, hudUnit());
		ui.begin();
		float side = settings.minimapSize.side;
		float margin = 16;
		float info = settings.minimapCoordinates ? 46 : 0;
		float sw = ui.width();
		float sh = ui.height();
		float x = switch (settings.minimapCorner) {
			case TOP_LEFT, BOTTOM_LEFT -> margin;
			default -> sw - margin - side;
		};
		float y = switch (settings.minimapCorner) {
			case BOTTOM_LEFT, BOTTOM_RIGHT -> sh - margin - side - info;
			default -> margin;
		};
		float radius = Math.round(side * 0.12F);
		String dim = mc.level.dimension().identifier().toString();
		boolean ceiling = mc.level.dimensionType().hasCeiling();
		DimensionData data = this.atlas.session().dimension(dim, ceiling);
		boolean underground = ceiling || this.atlas.underground();
		LayerData layer = underground ? data.under(this.atlas.sliceBand(dim)) : data.layer(DimensionData.SURFACE);
		long t1 = System.nanoTime();
		double target = AtlasSettings.ZOOMS[settings.minimapZoom];
		if (Double.isNaN(this.zoom)) {
			this.zoom = target;
		}
		double dt = Math.min(0.1, Perf.lastFrameNanos() / 1e9);
		this.zoom = Math.exp(Math.log(this.zoom) + (Math.log(target) - Math.log(this.zoom)) * (1 - Math.exp(-dt * 14)));
		if (Math.abs(Math.log(this.zoom / target)) < 0.004) {
			this.zoom = target;
		}
		float pt = delta.getGameTimeDeltaPartialTick(false);
		double px = p.xo + (p.getX() - p.xo) * pt;
		double pz = p.zo + (p.getZ() - p.zo) * pt;
		float yaw = p.getViewYRot(pt);
		double rotation = settings.minimapRotates ? Math.toRadians(180 - yaw) : 0;
		// a dark hairline outside, so the map's edge reads over bright snow and sky
		ui.border(x - 1.5F, y - 1.5F, side + 3, side + 3, radius + 1.5F, 1.5F, 0x8C0B0B0C);
		ui.round(x, y, side, side, radius, Theme.MAP_WELL);
		MapRenderer.Frame f = this.renderer.draw(ui, this.atlas.tiles(), layer, x, y, side, side, radius, new MapRenderer.View(px, pz, this.zoom, rotation), true);
		long t2 = System.nanoTime();
		ui.flush();
		ui.g.enableScissor((int) Math.floor(x), (int) Math.floor(y), (int) Math.ceil(x + side), (int) Math.ceil(y + side));
		List<Marker> markers = new ArrayList<>();
		double reach = side / f.k;
		MarkerQuery q = new MarkerQuery(dim, px - reach, pz - reach, px + reach, pz + reach, px, pz, MarkerQuery.Purpose.MINIMAP);
		for (Marker m : this.atlas.collect(q)) {
			if (settings.layerVisible(m.layer()) && m.dimension().equals(dim) && m.style() != Marker.Style.LABEL
				&& MapPanel.shows(m, ceiling ? null : underground, this.zoom)) {
				markers.add(m);
			}
		}
		MarkerPainter.paint(ui, f, markers, null, null, true, px, pz, this.zoom);
		long t3 = System.nanoTime();
		// waypoints beyond the edge: a small dot on the rim, in the direction to walk
		var session = this.atlas.session();
		float cx = x + side / 2;
		float cy = y + side / 2;
		for (var w : session.waypoints.in(dim)) {
			if (!w.visible() || !settings.layerVisible(MapLayer.WAYPOINTS)) {
				continue;
			}
			float sx = f.screenX(w.x() + 0.5, w.z() + 0.5);
			float sy = f.screenY(w.x() + 0.5, w.z() + 0.5);
			if (f.inside(sx, sy)) {
				continue;
			}
			float dx = sx - cx;
			float dy = sy - cy;
			float rim = side / 2 - 8;
			float k = rim / Math.max(Math.abs(dx), Math.abs(dy));
			float rx = cx + dx * k;
			float ry = cy + dy * k;
			ui.circle(rx, ry, 4, Theme.SHELL);
			ui.circle(rx, ry, 3, Theme.alpha(0xFF000000 | w.rgb(), 0.9F));
		}
		MarkerPainter.self(ui, x + side / 2, y + side / 2, settings.minimapRotates ? 0 : (float) Math.toRadians(yaw + 180), 30);
		ui.flush();
		ui.g.disableScissor();
		ui.border(x, y, side, side, radius, 0, Theme.HAIR_10);
		this.north(ui, f, x, y, side, radius, rotation);
		if (settings.minimapCoordinates) {
			this.info(ui, p, x, y + side + 6, side, underground);
		}
		ui.end();
		long t4 = System.nanoTime();
		Perf.minimap(t4 - t0);
		if (t4 - t0 > 3_000_000L) {
			// a slow minimap frame: say where the time went (the perf gate looks at the worst frame)
			Perf.minimapSpike(t1 - t0, t2 - t1, t3 - t2, t4 - t3);
		}
	}

	/** The north mark: a small disc on the rim, where north is. */
	private void north(Ui ui, MapRenderer.Frame f, float x, float y, float side, float radius, double rotation) {
		double angle = -Math.PI / 2 + rotation;
		float cx = x + side / 2;
		float cy = y + side / 2;
		// walk out from the middle until the point leaves the rounded square, less a margin
		float r = side / 2 - 9;
		float dx = (float) Math.cos(angle);
		float dy = (float) Math.sin(angle);
		float k = r / Math.max(Math.abs(dx), Math.abs(dy));
		float nx = cx + dx * Math.min(k, side / 2 - 9);
		float ny = cy + dy * Math.min(k, side / 2 - 9);
		if (!f.inside(nx, ny)) {
			nx = cx + dx * (r - radius * 0.3F);
			ny = cy + dy * (r - radius * 0.3F);
		}
		ui.panel(nx - 8, ny - 8, 16, 16, 8, Theme.ISLAND, Theme.HAIR_10);
		ui.textCentred("N", nx, ny - 6.5F, Ui.Font.F9, 0, Theme.INK);
	}

	private void info(Ui ui, LocalPlayer p, float x, float y, float w, boolean underground) {
		String[] biome = this.atlas.biomeAt(p.blockPosition());
		ui.panel(x, y, w, 40, 12, Theme.ISLAND, Theme.HAIR_8);
		String facing = MapPage.facing(p.getYRot());
		float fw = ui.width(facing, Ui.Font.F10) + 11;
		var region = this.atlas.regionHere();
		String place = region != null && region.isDiscovered() ? region.name() : biome[0];
		String name = underground ? "Underground  ·  " + place : place;
		ui.text(ui.fit(name, Ui.Font.F11, w - 24 - fw - 6), x + 11, y + 6, Ui.Font.F11, Theme.INK);
		ui.round(x + w - 11 - fw, y + 11, 5, 5, 1.5F, Theme.alpha(Theme.VIOLET, 0.95F));
		ui.text(facing, x + w - 11 - fw + 10, y + 7, Ui.Font.F10, Theme.INK_2);
		float cx = x + 11;
		float cy = y + 22;
		String[] axes = {"X", "Y", "Z"};
		int[] values = {p.getBlockX(), p.getBlockY(), p.getBlockZ()};
		for (int i = 0; i < 3; i++) {
			ui.text(axes[i], cx, cy + 0.5F, Ui.Font.F10, Theme.INK_4);
			cx += ui.width(axes[i], Ui.Font.F10) + 4;
			String v = MapPage.minus(values[i]);
			ui.text(v, cx, cy, Ui.Font.F11, Theme.INK);
			cx += ui.width(v, Ui.Font.F11) + 10;
		}
	}
}
