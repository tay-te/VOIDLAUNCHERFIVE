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
		long c0 = Perf.cpuNow();
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
		boolean underground = ceiling || settings.minimapAutoCave && this.atlas.minimapCaveView() && this.atlas.caveViewAllowed(ceiling);
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
		LayerData far = !underground && settings.farSight ? data.layerIfOpen(dev.voidmc.atlas.client.FarSight.LAYER) : null;
		if (far != null) {
			this.renderer.setTint(0xA8B4C4);
			this.renderer.draw(ui, this.atlas.tiles(), far, x, y, side, side, radius, new MapRenderer.View(px, pz, this.zoom, rotation), true);
			this.renderer.setTint(sky ? dev.voidmc.atlas.client.render.MapLight.tint() : 0xFFFFFF);
		}
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
		boolean light = dev.voidmc.atlas.client.PerfGovernor.light();
		if (!light) {
			// Light: a simpler minimap (no radar, no edge markers, no rim dots)
			this.radar(ui, f, mc, p, px, pz);
			this.edgeMarkers(ui, f, x, y, side, dim, px, pz, underground, ceiling, rotation);
		}
		long t3 = System.nanoTime();
		// waypoints beyond the edge: a small dot on the rim, in the direction to walk
		var session = this.atlas.session();
		float cx = x + side / 2;
		float cy = y + side / 2;
		long wv = session.waypoints.version();
		if (wv != this.rimVersion || !dim.equals(this.rimDim)) {
			List<dev.voidmc.atlas.api.Waypoint> visible = new ArrayList<>();
			for (var w : session.waypoints.in(dim)) {
				if (w.visible()) {
					visible.add(w);
				}
			}
			this.rim = visible;
			this.rimVersion = wv;
			this.rimDim = dim;
		}
		// the nearest few beyond the edge only (each a dot on the rim): more would crowd the rim and cost every frame
		int shown = 0;
		int count = 0;
		for (int i = 0; i < this.rim.size() && count < this.rimNear.length && !light; i++) {
			var w = this.rim.get(i);
			float sx = f.screenX(w.x() + 0.5, w.z() + 0.5);
			float sy = f.screenY(w.x() + 0.5, w.z() + 0.5);
			if (f.inside(sx, sy)) {
				continue;
			}
			double d = Math.hypot(w.x() + 0.5 - px, w.z() + 0.5 - pz);
			if (d > RIM_REACH && !dev.voidmc.atlas.client.AtlasClient.waypointMarkerId(w).equals(settings.tracked)) {
				continue;
			}
			if (dev.voidmc.atlas.client.AtlasClient.waypointMarkerId(w).equals(settings.tracked)) {
				d = -1;
			}
			// insertion into the nearest RIM_DOTS so far
			int at = Math.min(shown, RIM_DOTS - 1);
			if (shown == RIM_DOTS && d >= this.rimDist[RIM_DOTS - 1]) {
				continue;
			}
			while (at > 0 && this.rimDist[at - 1] > d) {
				this.rimDist[at] = this.rimDist[at - 1];
				this.rimNear[at] = this.rimNear[at - 1];
				at--;
			}
			this.rimDist[at] = d;
			this.rimNear[at] = i;
			shown = Math.min(RIM_DOTS, shown + 1);
		}
		for (int j = 0; j < shown && settings.layerVisible(MapLayer.WAYPOINTS); j++) {
			var w = this.rim.get(this.rimNear[j]);
			float sx = f.screenX(w.x() + 0.5, w.z() + 0.5);
			float sy = f.screenY(w.x() + 0.5, w.z() + 0.5);
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
		Perf.minimapPhases(t1 - t0, t2 - t1, t3 - t2, t4 - t3);
		Perf.cpu(Perf.cpuNow() - c0);
		if (t4 - t0 > 3_000_000L) {
			// a slow minimap frame: say where the time went (the perf gate looks at the worst frame)
			Perf.minimapSpike(t1 - t0, t2 - t1, t3 - t2, t4 - t3);
		}
	}

	private static final int RIM_DOTS = 6;
	/** Sighted places shown on the rim at most (the nearest), besides the tracked one. */
	private static final int EDGE_MARKS = 3;
	/** Waypoints get a dot on the rim within this many blocks (or when tracked). */
	private static final double RIM_REACH = 256;
	private final int[] rimNear = new int[256];
	private final double[] rimDist = new double[RIM_DOTS];
	private List<dev.voidmc.atlas.api.Waypoint> rim = List.of();
	private long rimVersion = -1;
	private String rimDim = "";

	/** The radar (the design's): at most 40 within 64 blocks, the list made again four times a second. */
	private static final int RADAR_MAX = 40;
	private static final double RADAR_RANGE = 64;
	private final net.minecraft.world.entity.LivingEntity[] radarSeen = new net.minecraft.world.entity.LivingEntity[RADAR_MAX];
	private int radarCount;
	private long radarAt;

	/**
	 * The radar: other players as 14 px face tiles, hostile mobs as 6 px filled squares, the rest as 6 px hollow
	 * circles, all in the ink (no red: shape tells them apart), fainter the further above or below; from the entities
	 * the client already has, where the settings and the server allow.
	 */
	private void radar(Ui ui, MapRenderer.Frame f, Minecraft mc, LocalPlayer self, double px, double pz) {
		AtlasSettings settings = this.atlas.settings();
		boolean players = settings.radarPlayers && this.atlas.radarAllowed(false);
		boolean mobs = settings.radarMobs && this.atlas.radarAllowed(true);
		if (!players && !mobs || mc.level == null) {
			this.radarCount = 0;
			return;
		}
		long t0 = System.nanoTime();
		if (t0 - this.radarAt > 250_000_000L) {
			this.radarAt = t0;
			int n = 0;
			for (net.minecraft.world.entity.Entity e : mc.level.entitiesForRendering()) {
				if (n >= RADAR_MAX) {
					break;
				}
				if (e == self || e.isInvisible() || !(e instanceof net.minecraft.world.entity.LivingEntity living) || !living.isAlive()
					|| Math.abs(e.getX() - px) > RADAR_RANGE || Math.abs(e.getZ() - pz) > RADAR_RANGE) {
					continue;
				}
				boolean isPlayer = e instanceof net.minecraft.world.entity.player.Player;
				if (isPlayer ? players : mobs) {
					this.radarSeen[n++] = living;
				}
			}
			for (int i = n; i < this.radarCount; i++) {
				this.radarSeen[i] = null;
			}
			this.radarCount = n;
		}
		double py = self.getY();
		for (int i = 0; i < this.radarCount; i++) {
			var e = this.radarSeen[i];
			if (e == null || e.isRemoved()) {
				continue;
			}
			float sx = f.screenX(e.getX(), e.getZ());
			float sy = f.screenY(e.getX(), e.getZ());
			if (!f.inside(sx, sy)) {
				continue;
			}
			// never over the player's own arrow
			float fcx = f.x + f.w / 2;
			float fcy = f.y + f.h / 2;
			if (Math.abs(sx - fcx) < 18 && Math.abs(sy - fcy) < 18) {
				continue;
			}
			float alpha = (float) Math.max(0.35, 1 - Math.abs(e.getY() - py) / 40);
			if (e instanceof net.minecraft.client.player.AbstractClientPlayer player) {
				ui.round(sx - 8, sy - 8, 16, 16, 4, Theme.alpha(Theme.SHELL, alpha));
				ui.border(sx - 8, sy - 8, 16, 16, 4, 0, Theme.alpha(Theme.HAIR_16, alpha));
				ui.flush();
				ui.g.pose().pushMatrix();
				ui.g.pose().translate(sx - 7, sy - 7);
				net.minecraft.client.gui.components.PlayerFaceExtractor.extractRenderState(ui.g, player.getSkin(), 0, 0, 14);
				ui.g.pose().popMatrix();
			} else if (e instanceof net.minecraft.world.entity.monster.Enemy) {
				ui.round(sx - 4, sy - 4, 8, 8, 1.5F, Theme.alpha(Theme.SHELL, alpha * 0.8F));
				ui.round(sx - 3, sy - 3, 6, 6, 1, Theme.ink(0.85F * alpha));
			} else {
				ui.ring(sx, sy, 3, 1.4F, Theme.ink(0.8F * alpha));
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
	private long infoKey = Long.MIN_VALUE;
	private Object infoRegion;
	private boolean infoUnder;
	private float infoWidth;
	private String infoName = "";
	private String infoLine = "";

	private void info(Ui ui, LocalPlayer p, float x, float y, float w, boolean underground) {
		ui.panel(x, y, w, 40, 12, Theme.ISLAND, Theme.HAIR_8);
		var region = this.atlas.regionHere();
		long day = p.level().getOverworldClockTime() / 24000 + 1;
		long key = (long) p.getBlockX() * 0x9E3779B97F4A7C15L ^ (long) p.getBlockY() * 0xC2B2AE3D27D4EB4FL ^ p.getBlockZ() ^ day << 48;
		if (key != this.infoKey || region != this.infoRegion || underground != this.infoUnder || w != this.infoWidth) {
			// made again only when the player moves a block (or the day, the region or the view changes)
			String biome = this.atlas.biomeNameAt(p.blockPosition());
			String place = region != null && region.isDiscovered() && !underground ? region.name() : biome;
			String name = underground ? "Underground  ·  " + place : place;
			this.infoName = ui.fit(name, Ui.Font.F12, w - 24);
			String line = MapPage.minus(p.getBlockX()) + "  ·  " + MapPage.minus(p.getBlockY()) + "  ·  " + MapPage.minus(p.getBlockZ()) + "  ·  DAY " + day;
			this.infoLine = ui.fit(line, Ui.Font.F85, 1, w - 24);
			this.infoKey = key;
			this.infoRegion = region;
			this.infoUnder = underground;
			this.infoWidth = w;
		}
		ui.text(this.infoName, x + 12, y + 6, Ui.Font.F12, Theme.INK);
		ui.text(this.infoLine, x + 12, y + 24, Ui.Font.F85, 1, Theme.INK_4);
		// the quiet note after a server rule: what is off here
		String off = this.atlas.multiplayer() ? this.atlas.rules().turnedOff() : null;
		if (off != null) {
			String note = Character.toUpperCase(off.charAt(0)) + off.substring(1) + " off here";
			float nw = Math.min(w, 26 + ui.width(note, Ui.Font.F11) + 14);
			ui.panel(x, y + 48, nw, 24, 12, Theme.ISLAND, Theme.HAIR_8);
			ui.ring(x + 13, y + 60, 2.5F, 1.1F, Theme.INK_4);
			ui.text(ui.fit(note, Ui.Font.F11, w - 40), x + 24, y + 53, Ui.Font.F11, Theme.INK_3);
		}
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
		// the nearest three sighted places only, and the tracked one: a rim with a mark in every direction tells nothing
		int kept = 0;
		for (int pass = 0; pass < 8; pass++) {
			int nearest = -1;
			for (int o = 0; o < 8; o++) {
				if (chosen[o] != null && chosen[o] != trackedPlace && best[o] >= 0 && (nearest < 0 || best[o] < best[nearest])) {
					nearest = o;
				}
			}
			if (nearest < 0) {
				break;
			}
			if (kept < EDGE_MARKS) {
				kept++;
				best[nearest] = -2 - kept;
			} else {
				chosen[nearest] = null;
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
