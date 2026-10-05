package dev.voidmc.atlas.client.ui;

import dev.voidmc.atlas.api.MapLayer;
import dev.voidmc.atlas.api.Marker;
import dev.voidmc.atlas.api.MarkerQuery;
import dev.voidmc.atlas.api.Place;
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
	private List<Place> edgeFrom = List.of();
	private List<Place> edgeCandidates = List.of();

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
		if (!settings.minimap || p == null || mc.level == null || mc.gui.screen() != null && !SettingsScreen.previewing() || mc.gui.hud.isHidden()
			|| settings.minimapHideWithDebug && mc.getDebugOverlay().showDebugScreen() || this.atlas.session() == null || !this.atlas.minimapAllowed()) {
			return;
		}
		long t0 = System.nanoTime();
		Ui ui = new Ui(graphics, hudUnit());
		ui.begin();
		ui.prewarm();
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
		boolean round = settings.minimapShape == AtlasSettings.Shape.ROUND;
		float radius = round ? side / 2 : Math.round(side * 0.12F);
		String dim = mc.level.dimension().identifier().toString();
		boolean ceiling = mc.level.dimensionType().hasCeiling();
		DimensionData data = this.atlas.session().dimension(dim, ceiling);
		// underground, the slice round the player (if wanted, and where the server allows it)
		boolean underground = ceiling || settings.minimapAutoCave && this.atlas.underground() && this.atlas.caveViewAllowed(ceiling);
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
		// day and night on the map: the surface of a world with a sky, lit as the setting and the time of day say
		net.minecraft.client.multiplayer.ClientLevel lightLevel = net.minecraft.client.Minecraft.getInstance().level;
		boolean sky = !underground && lightLevel != null && !lightLevel.dimensionType().hasCeiling();
		dev.voidmc.atlas.client.render.MapLight.update(this.atlas.settings().mapLook, lightLevel == null ? 0 : lightLevel.getOverworldClockTime(), sky);
		this.renderer.setTint(sky ? dev.voidmc.atlas.client.render.MapLight.tint() : 0xFFFFFF);
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
		this.radar(ui, f, mc, p, px, pz);
		this.edgeMarkers(ui, f, x, y, side, dim, px, pz, underground, ceiling, rotation);
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
			float k = rim / (round ? (float) Math.hypot(dx, dy) : Math.max(Math.abs(dx), Math.abs(dy)));
			float rx = cx + dx * k;
			float ry = cy + dy * k;
			ui.circle(rx, ry, 4, Theme.SHELL);
			ui.circle(rx, ry, 3, Theme.alpha(0xFF000000 | w.rgb(), 0.9F));
		}
		MarkerPainter.self(ui, x + side / 2, y + side / 2, settings.minimapRotates ? 0 : (float) Math.toRadians(yaw + 180), 30);
		ui.flush();
		ui.g.disableScissor();
		ui.border(x, y, side, side, radius, 0, Theme.HAIR_10);
		this.north(ui, f, x, y, side, radius, rotation, round);
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

	/** Most dots the radar draws in a frame. */
	private static final int RADAR_MAX = 96;

	/**
	 * The radar: other players (a ring with a dot) and, if wanted, mobs (small dots: warm for hostile ones, cool for the
	 * rest), from the entities the client already has, where the settings and the server allow. Fainter the further
	 * above or below the player they are.
	 */
	private void radar(Ui ui, MapRenderer.Frame f, Minecraft mc, LocalPlayer self, double px, double pz) {
		AtlasSettings settings = this.atlas.settings();
		boolean players = settings.radarPlayers && this.atlas.radarAllowed(false);
		boolean mobs = settings.radarMobs && this.atlas.radarAllowed(true);
		if (!players && !mobs || mc.level == null) {
			return;
		}
		long t0 = System.nanoTime();
		int drawn = 0;
		double py = self.getY();
		for (net.minecraft.world.entity.Entity e : mc.level.entitiesForRendering()) {
			if (e == self || e.isInvisible() || !(e instanceof net.minecraft.world.entity.LivingEntity living) || !living.isAlive()) {
				continue;
			}
			boolean isPlayer = e instanceof net.minecraft.world.entity.player.Player;
			if (isPlayer ? !players : !mobs) {
				continue;
			}
			float sx = f.screenX(e.getX(), e.getZ());
			float sy = f.screenY(e.getX(), e.getZ());
			if (!f.inside(sx, sy)) {
				continue;
			}
			float alpha = (float) Math.max(0.35, 1 - Math.abs(e.getY() - py) / 40);
			if (isPlayer) {
				ui.circle(sx, sy, 4.5F, Theme.alpha(Theme.SHELL, alpha));
				ui.circle(sx, sy, 3.5F, Theme.ink(0.9F * alpha));
				ui.circle(sx, sy, 1.6F, Theme.alpha(Theme.SHELL, alpha));
			} else {
				int colour = e instanceof net.minecraft.world.entity.monster.Enemy ? 0xFFC98F7A : 0xFF8FB8A0;
				ui.circle(sx, sy, 2.5F, Theme.alpha(Theme.SHELL, alpha * 0.8F));
				ui.circle(sx, sy, 1.8F, Theme.alpha(colour, alpha));
			}
			if (++drawn >= RADAR_MAX) {
				break;
			}
		}
		Perf.radar(System.nanoTime() - t0);
	}

	/** The north mark: a small disc on the rim, where north is. */
	private void north(Ui ui, MapRenderer.Frame f, float x, float y, float side, float radius, double rotation, boolean round) {
		double angle = -Math.PI / 2 + rotation;
		float cx = x + side / 2;
		float cy = y + side / 2;
		// walk out from the middle until the point leaves the rounded square, less a margin
		float r = side / 2 - 9;
		float dx = (float) Math.cos(angle);
		float dy = (float) Math.sin(angle);
		float k = round ? r : r / Math.max(Math.abs(dx), Math.abs(dy));
		float nx = cx + dx * Math.min(k, side / 2 - 9);
		float ny = cy + dy * Math.min(k, side / 2 - 9);
		if (!f.inside(nx, ny)) {
			nx = cx + dx * (r - radius * 0.3F);
			ny = cy + dy * (r - radius * 0.3F);
		}
		ui.panel(nx - 8, ny - 8, 16, 16, 8, Theme.ISLAND, Theme.HAIR_10);
		ui.textCentred("N", nx, ny - 6.5F, Ui.Font.F9, 0, Theme.INK);
	}

	/** Under the minimap: the named region (or biome) and the position and day, quietly ("300 · 64 · 536 · DAY 34"). */
	private void info(Ui ui, LocalPlayer p, float x, float y, float w, boolean underground) {
		String[] biome = this.atlas.biomeAt(p.blockPosition());
		ui.panel(x, y, w, 40, 12, Theme.ISLAND, Theme.HAIR_8);
		var region = this.atlas.regionHere();
		String place = region != null && region.isDiscovered() && !underground ? region.name() : biome[0];
		String name = underground ? "Underground  ·  " + place : place;
		ui.text(ui.fit(name, Ui.Font.F12, w - 24), x + 12, y + 6, Ui.Font.F12, Theme.INK);
		long day = p.level().getOverworldClockTime() / 24000 + 1;
		String line = MapPage.minus(p.getBlockX()) + "  ·  " + MapPage.minus(p.getBlockY()) + "  ·  " + MapPage.minus(p.getBlockZ()) + "  ·  DAY " + day;
		ui.text(ui.fit(line, Ui.Font.F85, 1, w - 24), x + 12, y + 24, Ui.Font.F85, 1, Theme.INK_4);
	}

	/**
	 * Places sighted (and the tracked one) beyond the minimap's rim, as the design's edge markers: the hollow "?" tile
	 * (the track glyph, filled, for the tracked place) pinned 14 px inside the rim in the nearest of eight directions,
	 * with a three-cell chevron pointing out. The nearest place per direction only.
	 */
	private void edgeMarkers(Ui ui, MapRenderer.Frame f, float x, float y, float side, String dim, double px, double pz, boolean underground,
		boolean ceiling, double rotation) {
		var session = this.atlas.session();
		if (session == null) {
			return;
		}
		double[] best = new double[8];
		Place[] chosen = new Place[8];
		java.util.Arrays.fill(best, Double.MAX_VALUE);
		String tracked = this.atlas.settings().tracked;
		Place trackedPlace = tracked == null ? null : this.atlas.placeFor(tracked);
		List<Place> visible = session.places.visible(dim);
		if (visible != this.edgeFrom) {
			// the places that can be edge markers, worked out again only when the places change
			List<Place> candidates = new ArrayList<>();
			for (Place pl : visible) {
				if (Kinds.sighted(pl) && !pl.isLabel()) {
					candidates.add(pl);
				}
			}
			this.edgeFrom = visible;
			this.edgeCandidates = candidates;
		}
		List<Place> pool = new ArrayList<>(this.edgeCandidates);
		if (trackedPlace != null && !pool.contains(trackedPlace)) {
			pool.add(trackedPlace);
		}
		for (Place pl : pool) {
			boolean isTracked = pl == trackedPlace;
			if (!isTracked && (!Kinds.sighted(pl) || pl.isLabel() || !ceiling && pl.underground() != underground
				|| !this.atlas.settings().kindVisible(Kinds.Kind.SIGHTED.name()))) {
				continue;
			}
			double d = Math.hypot(pl.x() + 0.5 - px, pl.z() + 0.5 - pz);
			if (d > 1024 && !isTracked) {
				continue;
			}
			float sx = f.screenX(pl.x() + 0.5, pl.z() + 0.5);
			float sy = f.screenY(pl.x() + 0.5, pl.z() + 0.5);
			if (f.inside(sx, sy)) {
				continue;
			}
			double angle = Math.atan2(sy - (y + side / 2), sx - (x + side / 2));
			int octant = Math.floorMod((int) Math.round(angle / (Math.PI / 4)), 8);
			double rank = isTracked ? -1 : d;
			if (rank < best[octant]) {
				best[octant] = rank;
				chosen[octant] = pl;
			}
		}
		float cx = x + side / 2;
		float cy = y + side / 2;
		float reach = side / 2 - 14 - 8;
		int northOctant = Math.floorMod((int) Math.round((-Math.PI / 2 + rotation) / (Math.PI / 4)), 8);
		for (int o = 0; o < 8; o++) {
			Place pl = chosen[o];
			if (pl == null) {
				continue;
			}
			double a = o * Math.PI / 4;
			float dx = (float) Math.cos(a);
			float dy = (float) Math.sin(a);
			float k = reach / Math.max(Math.abs(dx), Math.abs(dy));
			float tx = cx + dx * k;
			float ty = cy + dy * k;
			if (o == northOctant) {
				// the north mark sits there: step aside along the rim
				tx += dy != 0 ? 22 : 0;
				ty += dx != 0 ? 22 : 0;
			}
			boolean isTracked = pl == trackedPlace;
			if (isTracked) {
				ui.round(tx - 8, ty - 8, 16, 16, 5, Theme.ISLAND);
				ui.border(tx - 8, ty - 8, 16, 16, 5, 0, Theme.HAIR_16);
				ui.glyph(dev.voidmc.atlas.api.MarkerGlyph.TRACK, tx - 5, ty - 5, 10, Theme.INK, Theme.INK);
			} else {
				ui.round(tx - 8, ty - 8, 16, 16, 5, 0xBF0B0B0C);
				ui.border(tx - 8, ty - 8, 16, 16, 5, 0, 0x4DFFFFFF);
				ui.glyph(dev.voidmc.atlas.api.MarkerGlyph.UNKNOWN, tx - 5, ty - 5, 10, Theme.ink(0.75F), Theme.ink(0.75F));
			}
			// the chevron: three cells pointing out along the bearing
			float ox = tx + dx * 13;
			float oy = ty + dy * 13;
			float nx = -dy;
			float ny = dx;
			int ink = Theme.ink(0.75F);
			ui.round(ox + dx * 1.25F - 1.25F, oy + dy * 1.25F - 1.25F, 2.5F, 2.5F, 0.8F, ink);
			ui.round(ox - dx * 1.25F + nx * 2.5F - 1.25F, oy - dy * 1.25F + ny * 2.5F - 1.25F, 2.5F, 2.5F, 0.8F, ink);
			ui.round(ox - dx * 1.25F - nx * 2.5F - 1.25F, oy - dy * 1.25F - ny * 2.5F - 1.25F, 2.5F, 2.5F, 0.8F, ink);
		}
	}
}
