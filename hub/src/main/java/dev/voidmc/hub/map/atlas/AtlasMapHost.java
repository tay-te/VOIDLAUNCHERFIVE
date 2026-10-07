package dev.voidmc.hub.map.atlas;

import dev.voidmc.atlas.api.AtlasApi;
import dev.voidmc.atlas.api.MapView;
import dev.voidmc.atlas.api.MapWidget;
import dev.voidmc.atlas.api.Waypoint;
import dev.voidmc.hub.VoidHub;
import dev.voidmc.hub.map.HubMap;
import dev.voidmc.hub.map.MapHost;
import java.lang.reflect.Field;
import java.util.Locale;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.player.LocalPlayer;

/**
 * The hub's Map page over VOID Atlas's {@link MapWidget}: Atlas's whole map page (segments, search,
 * the map, layers, zoom, the places list, cards and editor), drawn natively into the hub's window.
 *
 * <p>The only class of the hub that names Atlas types. {@code HubMap.host()} reaches it through
 * {@link #create}, and only once {@code FabricLoader.isModLoaded("atlas")} said yes.
 *
 * <p><b>One map with M.</b> The Map page and Atlas's own map screen (M) show one map state: the
 * same view, zoom, layer, depth and selection (Figma 375:36239). Atlas's API has no shared state
 * yet (each {@code createMapWidget()} and each M screen makes its own page), so until it does the
 * hub carries the state across: when M opens it is given the Map page's state, and when it closes
 * the Map page takes M's. That reads and writes Atlas's page and map panel reflectively
 * ({@link #copy}); on an Atlas whose insides differ it logs once and stops, and the two keep their
 * own state as before. See the README's "What Atlas would need".
 */
public final class AtlasMapHost implements MapHost {

	/** Atlas's marker id for a waypoint ({@code AtlasClient.waypointMarkerId}; not in its API yet). */
	private static final String WAYPOINT_MARKER = "atlas:wp:";

	private final AtlasApi api;
	private final MapWidget widget;
	private String dimension;

	private AtlasMapHost(AtlasApi api) {
		this.api = api;
		this.widget = api.createMapWidget();
		this.dimension = currentDimension();
	}

	/** Returns the hub-facing type, so the caller links against nothing of Atlas's. */
	public static MapHost create() {
		AtlasMapHost host = new AtlasMapHost(AtlasApi.get());
		if (lastScreenPage != null) {
			copy(lastScreenPage, host.page());  // M was used before the Map page: start where M left off
		}
		return host;
	}

	// ---- one map state with Atlas's own map screen (M) -------------------------------------------

	private static final String ATLAS_SCREEN = "dev.voidmc.atlas.client.ui.AtlasScreen";
	/** M's page after it last closed (this world). */
	private static Object lastScreenPage;
	/** The M screen that was last given the Map page's state (init runs again on every resize). */
	private static Screen syncedScreen;
	private static boolean syncBroken;

	/** Hooks M's screen opening and closing; called once at start-up when Atlas is installed. */
	public static void installSync() {
		ScreenEvents.AFTER_INIT.register((mc, screen, w, h) -> {
			if (syncBroken || screen == syncedScreen || !screen.getClass().getName().equals(ATLAS_SCREEN)) {
				return;
			}
			syncedScreen = screen;
			Object page = screenPage(screen);
			if (page == null) {
				return;
			}
			Object from = HubMap.existing() instanceof AtlasMapHost host ? host.page() : lastScreenPage;
			if (from != null) {
				copy(from, page);
			}
			ScreenEvents.remove(screen).register(closed -> {
				lastScreenPage = page;
				if (HubMap.existing() instanceof AtlasMapHost host) {
					copy(page, host.page());
				}
			});
		});
	}

	/** A world was joined: M's state from the last one is not this one's. */
	public static void forgetShared() {
		lastScreenPage = null;
		syncedScreen = null;
	}

	/** The widget's page (Atlas's {@code MapPage}). */
	Object page() {
		try {
			return field(this.widget, "page");
		} catch (ReflectiveOperationException | RuntimeException e) {
			return null;
		}
	}

	private static Object screenPage(Screen screen) {
		try {
			return screen.getClass().getField("page").get(screen);
		} catch (ReflectiveOperationException | RuntimeException e) {
			broken(e);
			return null;
		}
	}

	/**
	 * Copies the map state from one of Atlas's pages to another: the layer first (a layer change
	 * rescales the centre and clears the selection), the chosen depth, the centre (once the source
	 * has one), the zoom step, the selection, and the right panel's tab.
	 */
	static void copy(Object fromPage, Object toPage) {
		if (syncBroken || fromPage == null || toPage == null || fromPage == toPage) {
			return;
		}
		try {
			Object from = fromPage.getClass().getField("map").get(fromPage);
			Object to = toPage.getClass().getField("map").get(toPage);
			Class<?> panel = from.getClass();
			Field kind = panel.getField("kind");
			Object k = kind.get(from);
			if (!k.equals(kind.get(to))) {
				panel.getMethod("setKind", kind.getType()).invoke(to, k);
			}
			panel.getMethod("setBand", Integer.class).invoke(to, field(from, "band"));
			if ((Boolean) field(from, "centred")) {
				panel.getMethod("centreOn", double.class, double.class).invoke(to, panel.getMethod("centreX").invoke(from), panel.getMethod("centreZ").invoke(from));
			}
			panel.getMethod("setZoomIndex", int.class).invoke(to, panel.getMethod("zoomIndex").invoke(from));
			Field selected = panel.getField("selected");
			selected.set(to, selected.get(from));
			Field legend = fromPage.getClass().getField("legendTab");
			legend.set(toPage, legend.get(fromPage));
		} catch (ReflectiveOperationException | RuntimeException e) {
			broken(e);
		}
	}

	private static void broken(Throwable e) {
		if (!syncBroken) {
			syncBroken = true;
			VoidHub.LOGGER.warn("VOID Hub: this VOID Atlas's map page is not the one the hub knows; the Map page and M keep separate map state", e);
		}
	}

	/**
	 * A page's map state as text, for tests: layer, centre, zoom step, selection. Takes Atlas's
	 * page or a screen holding one (M).
	 */
	public static String describe(Object pageOrScreen) {
		try {
			Object page = pageOrScreen instanceof Screen screen ? screen.getClass().getField("page").get(screen) : pageOrScreen;
			Object map = page.getClass().getField("map").get(page);
			Class<?> panel = map.getClass();
			return String.format(Locale.ROOT, "%s %.2f %.2f z%d %s", panel.getField("kind").get(map), (Double) panel.getMethod("centreX").invoke(map),
				(Double) panel.getMethod("centreZ").invoke(map), (Integer) panel.getMethod("zoomIndex").invoke(map), panel.getField("selected").get(map));
		} catch (ReflectiveOperationException | RuntimeException e) {
			return "unreadable: " + e;
		}
	}

	@Override
	public String describe() {
		return describe(this.page());
	}

	private static String currentDimension() {
		Minecraft mc = Minecraft.getInstance();
		return mc.level == null ? "" : mc.level.dimension().identifier().toString();
	}

	@Override
	public String version() {
		return this.api.version();
	}

	@Override
	public void render(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int mouseX, int mouseY) {
		this.widget.render(graphics, x, y, width, height, mouseX, mouseY);
	}

	@Override
	public boolean mouseClicked(double x, double y, int button) {
		return this.widget.mouseClicked(x, y, button);
	}

	@Override
	public boolean mouseReleased(double x, double y, int button) {
		return this.widget.mouseReleased(x, y, button);
	}

	@Override
	public boolean mouseDragged(double x, double y) {
		return this.widget.mouseDragged(x, y);
	}

	@Override
	public boolean mouseScrolled(double x, double y, double amount) {
		return this.widget.mouseScrolled(x, y, amount);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		return this.widget.keyPressed(event);
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		return this.widget.charTyped(event);
	}

	@Override
	public boolean typing() {
		return this.widget.typing();
	}

	@Override
	public void shown() {
		// The widget is kept between opens. Atlas's own map screen starts on the player's dimension
		// every time it opens, so the kept one follows the player when they changed dimension.
		String now = currentDimension();
		if (!now.equals(this.dimension)) {
			this.dimension = now;
			try {
				this.widget.showLayer(now.equals("minecraft:the_nether") ? MapView.NETHER : MapView.SURFACE);
			} catch (LinkageError e) {
				// an Atlas from before showLayer: the map keeps the layer last chosen
			}
		}
	}

	@Override
	public String addWaypointHere(boolean select) {
		LocalPlayer p = Minecraft.getInstance().player;
		if (p == null) {
			return null;
		}
		// Named the way Atlas's own quick-add (B) names them.
		String name = "Waypoint " + (this.api.waypoints().size() + 1);
		Waypoint w = this.api.addWaypoint(name, p.getBlockX(), p.getBlockY(), p.getBlockZ(), p.level().dimension().identifier().toString());
		if (w == null) {
			return null;
		}
		if (select) {
			this.widget.select(WAYPOINT_MARKER + w.id());
		}
		VoidHub.LOGGER.info("VOID Hub: added Atlas waypoint \"{}\" at {} {} {} ({})", w.name(), w.x(), w.y(), w.z(), w.dimension());
		return w.name();
	}

	@Override
	public int waypointCount() {
		return this.api.waypoints().size();
	}

	@Override
	public double[] view() {
		// MapWidget has no getter for its view yet: read Atlas's page and map panel reflectively.
		// Tests only; a different Atlas just answers null.
		try {
			Object page = this.page();
			Object map = page.getClass().getField("map").get(page);
			double x = (Double) map.getClass().getMethod("centreX").invoke(map);
			double z = (Double) map.getClass().getMethod("centreZ").invoke(map);
			double k = (Double) map.getClass().getMethod("zoom").invoke(map);
			return new double[] {x, z, k};
		} catch (ReflectiveOperationException | RuntimeException e) {
			return null;
		}
	}

	private static Object field(Object o, String name) throws ReflectiveOperationException {
		Field f = o.getClass().getDeclaredField(name);
		f.setAccessible(true);
		return f.get(o);
	}
}
