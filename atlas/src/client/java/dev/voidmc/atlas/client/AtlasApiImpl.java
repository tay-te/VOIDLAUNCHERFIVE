package dev.voidmc.atlas.client;

import dev.voidmc.atlas.api.AtlasApi;
import dev.voidmc.atlas.api.AtlasMarkers;
import dev.voidmc.atlas.api.AtlasPlaces;
import dev.voidmc.atlas.api.Discovery;
import dev.voidmc.atlas.api.MapLayer;
import dev.voidmc.atlas.api.MapView;
import dev.voidmc.atlas.api.MapWidget;
import dev.voidmc.atlas.api.Marker;
import dev.voidmc.atlas.api.MarkerQuery;
import dev.voidmc.atlas.api.Waypoint;
import dev.voidmc.atlas.client.render.MapRenderer;
import dev.voidmc.atlas.client.ui.MapPage;
import dev.voidmc.atlas.client.ui.MapPanel;
import dev.voidmc.atlas.client.ui.MarkerPainter;
import dev.voidmc.atlas.client.ui.Theme;
import dev.voidmc.atlas.client.ui.Ui;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.player.LocalPlayer;
import org.jspecify.annotations.Nullable;

/** The client API's implementation. */
public final class AtlasApiImpl implements AtlasApi {
	private final AtlasClient atlas;
	private final MapRenderer renderer = new MapRenderer();
	private MapPanel panel;

	AtlasApiImpl(AtlasClient atlas) {
		this.atlas = atlas;
		AtlasApi.Holder.set(this);
	}

	@Override
	public String shareCode(List<Waypoint> waypoints) {
		return dev.voidmc.atlas.core.waypoints.WaypointShare.encode(waypoints.stream().map(dev.voidmc.atlas.core.waypoints.SharedWaypoint::from).toList());
	}

	@Override
	public void offerShared(String code, String from) {
		for (var w : dev.voidmc.atlas.core.waypoints.WaypointShare.decode(code)) {
			dev.voidmc.atlas.client.ui.ShareCards.offer(w, from, "from the VOID server");
		}
	}

	@Override
	public dev.voidmc.atlas.api.AtlasWorlds worlds() {
		return AtlasClient.get().worlds;
	}

	@Override
	public String version() {
		return FabricLoader.getInstance().getModContainer(AtlasClient.ID).map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("dev");
	}

	@Override
	public AtlasMarkers markers() {
		return this.atlas.markersRegistry();
	}

	@Override
	public List<Marker> collect(MarkerQuery query) {
		return this.atlas.collect(query);
	}

	@Override
	public List<Waypoint> waypoints() {
		var session = this.atlas.session();
		return session == null ? List.of() : session.waypoints.all();
	}

	@Override
	public @Nullable Waypoint addWaypoint(String name, int x, int y, int z, String dimension) {
		var session = this.atlas.session();
		if (session == null) {
			return null;
		}
		Waypoint w = Waypoint.create(name, x, y, z, dimension, 0);
		session.waypoints.put(w);
		session.saveSoon(false);
		return w;
	}

	@Override
	public boolean removeWaypoint(String id) {
		var session = this.atlas.session();
		if (session == null || !session.waypoints.remove(id)) {
			return false;
		}
		session.saveSoon(false);
		return true;
	}

	@Override
	public List<Discovery> discoveries() {
		var session = this.atlas.session();
		return session == null ? List.of() : session.places.discoveries();
	}

	@Override
	public AtlasPlaces places() {
		return this.atlas.places();
	}

	@Override
	public boolean track(String markerId) {
		Minecraft mc = Minecraft.getInstance();
		if (markerId == null || mc.level == null || mc.player == null) {
			return false;
		}
		String dim = mc.level.dimension().identifier().toString();
		MarkerQuery q = new MarkerQuery(dim, -3e7, -3e7, 3e7, 3e7, mc.player.getX(), mc.player.getZ(), MarkerQuery.Purpose.LIST);
		for (Marker m : this.atlas.collect(q)) {
			if (m.id().equals(markerId)) {
				if (!markerId.equals(this.atlas.settings().tracked)) {
					this.atlas.track(m);
				} else {
					// tracked already (remembered from an earlier session): followed again, not switched off
					this.atlas.rearm(m);
				}
				return true;
			}
		}
		return false;
	}

	@Override
	public dev.voidmc.atlas.api.AtlasHud hud() {
		return dev.voidmc.atlas.client.ui.HudRoom.INSTANCE;
	}

	@Override
	public dev.voidmc.atlas.api.AtlasJournal journal() {
		return this.atlas.journal;
	}

	@Override
	public void useSocialTransport(dev.voidmc.atlas.api.AtlasSocialTransport transport) {
		this.atlas.social.offer(transport);
	}

	@Override
	public MapView playerView() {
		LocalPlayer p = Minecraft.getInstance().player;
		double zoom = AtlasSettings.ZOOMS[this.atlas.settings().mapZoom];
		return p == null ? MapView.surface(0, 0, zoom) : MapView.surface(p.getX(), p.getZ(), zoom);
	}

	private MapPanel panel() {
		if (this.panel == null) {
			this.panel = new MapPanel(this.atlas);
		}
		return this.panel;
	}

	@Override
	public void renderMap(GuiGraphicsExtractor graphics, int x, int y, int width, int height, MapView view) {
		Minecraft mc = Minecraft.getInstance();
		if (this.atlas.session() == null || width <= 0 || height <= 0) {
			return;
		}
		MapPanel.Kind kind = view.layer().startsWith(MapView.UNDERGROUND) ? MapPanel.Kind.UNDERGROUND
			: view.layer().equals(MapView.NETHER) ? MapPanel.Kind.NETHER : MapPanel.Kind.SURFACE;
		Integer depth = view.depth();
		Ui ui = new Ui(graphics, Ui.pick((float) mc.getWindow().getGuiScale()));
		ui.begin();
		float dx = ui.design(x);
		float dy = ui.design(y);
		float dw = ui.design(width);
		float dh = ui.design(height);
		float r = ui.design(view.cornerRadius());
		ui.round(dx, dy, dw, dh, r, Theme.MAP_WELL);
		var layer = depth == null ? this.panel().layer(kind) : this.panel().layerAtBand(kind, dev.voidmc.atlas.client.data.DimensionData.bandOf(depth));
		String dim = this.panel().dimensionOf(kind);
		MapRenderer.Frame f = this.renderer.draw(ui, this.atlas.tiles(), layer, dx, dy, dw, dh, r,
			new MapRenderer.View(view.centerX(), view.centerZ(), view.pixelsPerBlock(), view.rotation()), true);
		ui.flush();
		graphics.enableScissor((int) Math.floor(dx), (int) Math.floor(dy), (int) Math.ceil(dx + dw), (int) Math.ceil(dy + dh));
		LocalPlayer p = mc.player;
		double px = p == null ? view.centerX() : p.getX();
		double pz = p == null ? view.centerZ() : p.getZ();
		if (view.markers()) {
			double reach = Math.max(dw, dh) / f.k;
			MarkerQuery q = new MarkerQuery(dim, view.centerX() - reach, view.centerZ() - reach, view.centerX() + reach, view.centerZ() + reach, px, pz,
				MarkerQuery.Purpose.MAP);
			List<Marker> shown = new ArrayList<>();
			for (Marker m : this.atlas.collect(q)) {
				if (this.atlas.settings().layerVisible(m.layer()) && m.dimension().equals(dim) && !m.layer().equals(MapLayer.TERRAIN)) {
					shown.add(m);
				}
			}
			MarkerPainter.paint(ui, f, shown, null, null, Math.min(dw, dh) < 240, px, pz, view.pixelsPerBlock());
		}
		if (view.player() && p != null && mc.level != null && mc.level.dimension().identifier().toString().equals(dim)) {
			float sx = f.screenX(p.getX(), p.getZ());
			float sy = f.screenY(p.getX(), p.getZ());
			if (f.inside(sx, sy)) {
				MarkerPainter.self(ui, sx, sy, (float) (Math.toRadians(p.getYRot() + 180) + view.rotation()), 36);
			}
		}
		ui.flush();
		graphics.disableScissor();
		ui.border(dx, dy, dw, dh, r, 0, Theme.HAIR_8);
		ui.end();
	}

	@Override
	public MapWidget createMapWidget() {
		// the same page as the M screen's: one map state (view, layer, depth, selection, tab) however it is shown
		return new Widget(this.atlas, this.atlas.sharedPage());
	}

	@Override
	public void openMap() {
		this.atlas.openMap();
	}

	@Override
	public void openSettings(@Nullable Screen parent) {
		this.atlas.openSettings(parent);
	}

	/** Whether a key press is the map key (so the map screen closes on M, as it opened). */
	public boolean mapKeyMatches(KeyEvent event) {
		return this.atlas.mapKey().matches(event);
	}

	@Override
	public boolean isMapKey(KeyEvent event) {
		return event != null && this.mapKeyMatches(event);
	}

	@Override
	public String markerId(Waypoint waypoint) {
		return AtlasClient.waypointMarkerId(waypoint);
	}

	private static final class Widget implements MapWidget {
		private final AtlasClient atlas;
		private final MapPage page;
		private float scale = 1;

		Widget(AtlasClient atlas, MapPage page) {
			this.atlas = atlas;
			this.page = page;
		}

		@Override
		public MapView view() {
			MapPanel map = this.page.map;
			String layer = switch (map.kind) {
				case SURFACE -> MapView.SURFACE;
				case NETHER -> MapView.NETHER;
				case UNDERGROUND -> MapView.UNDERGROUND + ":" + (map.band(map.kind) * dev.voidmc.atlas.client.data.DimensionData.BAND);
			};
			return new MapView(layer, map.centreX(), map.centreZ(), map.zoom(), 0, true, true, 20);
		}

		@Override
		public void closed() {
			this.atlas.settings().save();
			Backdrop.close();
		}

		@Override
		public void shown() {
			this.page.shown();
		}

		@Override
		public float unitFor(int width, int height) {
			return Ui.fit(width, height, 1280, 744);
		}

		@Override
		public void freezeWorld(boolean freeze) {
			if (freeze) {
				Backdrop.open(true);
			} else {
				Backdrop.close();
			}
		}

		@Override
		public boolean drawFrozenWorld(GuiGraphicsExtractor graphics) {
			return this.drawFrozenWorld(graphics, 0xC2 / 255F);
		}

		@Override
		public boolean drawFrozenWorld(GuiGraphicsExtractor graphics, float dim) {
			if (!Backdrop.ready()) {
				return false;
			}
			Minecraft mc = Minecraft.getInstance();
			Ui ui = new Ui(graphics, Ui.fit(mc.getWindow().getWidth(), mc.getWindow().getHeight(), 1360, 824));
			ui.begin();
			boolean drawn = Backdrop.draw(ui, ui.width(), ui.height());
			int a = Math.round(Math.max(0, Math.min(1, dim)) * 255);
			if (a > 0) {
				ui.rect(0, 0, ui.width(), ui.height(), a << 24 | 0x0B0B0C);
			}
			ui.end();
			return drawn;
		}

		@Override
		public void render(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int mouseX, int mouseY) {
			Minecraft mc = Minecraft.getInstance();
			double gs = mc.getWindow().getGuiScale();
			Ui ui = new Ui(graphics, Ui.fit((int) (width * gs), (int) (height * gs), 1280, 744));
			this.scale = ui.scale;
			ui.begin();
			this.page.render(ui, ui.design(x), ui.design(y), ui.design(width), ui.design(height), ui.design(mouseX), ui.design(mouseY));
			ui.end();
		}

		private float d(double gui) {
			return (float) (gui / this.scale);
		}

		@Override
		public boolean mouseClicked(double x, double y, int button) {
			return this.page.mouseClicked(this.d(x), this.d(y), button);
		}

		@Override
		public boolean mouseReleased(double x, double y, int button) {
			return this.page.mouseReleased(this.d(x), this.d(y), button);
		}

		@Override
		public boolean mouseDragged(double x, double y) {
			return this.page.mouseDragged(this.d(x), this.d(y));
		}

		@Override
		public boolean mouseScrolled(double x, double y, double amount) {
			return this.page.mouseScrolled(this.d(x), this.d(y), amount);
		}

		@Override
		public boolean keyPressed(KeyEvent event) {
			return this.page.keyPressed(event);
		}

		@Override
		public boolean charTyped(CharacterEvent event) {
			return this.page.charTyped(event);
		}

		@Override
		public boolean typing() {
			return this.page.typing();
		}

		@Override
		public void showLayer(String layer) {
			this.page.map.setKind(layer.startsWith(MapView.UNDERGROUND) ? MapPanel.Kind.UNDERGROUND
				: layer.equals(MapView.NETHER) ? MapPanel.Kind.NETHER : MapPanel.Kind.SURFACE);
		}

		@Override
		public void showDepth(int y) {
			this.page.map.setBand(dev.voidmc.atlas.client.data.DimensionData.bandOf(y));
		}

		@Override
		public void select(String markerId) {
			this.page.map.selected = markerId;
			Marker m = this.page.map.marker(markerId);
			if (m != null) {
				this.page.map.centreOn(m.x(), m.z());
			}
		}
	}
}
