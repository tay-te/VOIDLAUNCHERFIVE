package dev.voidmc.hub.map.atlas;

import dev.voidmc.atlas.api.AtlasApi;
import dev.voidmc.atlas.api.MapView;
import dev.voidmc.atlas.api.MapWidget;
import dev.voidmc.atlas.api.Waypoint;
import dev.voidmc.hub.VoidHub;
import dev.voidmc.hub.map.MapHost;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.player.LocalPlayer;

/**
 * The hub's Map page over VOID Atlas's {@link MapWidget}: Atlas's whole map page (segments, search,
 * the map, layers, zoom, the places list, cards and editor), drawn natively into the hub's window.
 *
 * <p>The only class of the hub that names Atlas types. {@code HubMap.host()} reaches it through
 * {@link #create}, and only once {@code FabricLoader.isModLoaded("atlas")} said yes.
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
		return new AtlasMapHost(AtlasApi.get());
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
			var pageField = this.widget.getClass().getDeclaredField("page");
			pageField.setAccessible(true);
			Object page = pageField.get(this.widget);
			Object map = page.getClass().getField("map").get(page);
			double x = (Double) map.getClass().getMethod("centreX").invoke(map);
			double z = (Double) map.getClass().getMethod("centreZ").invoke(map);
			double k = (Double) map.getClass().getMethod("zoom").invoke(map);
			return new double[] {x, z, k};
		} catch (ReflectiveOperationException | RuntimeException e) {
			return null;
		}
	}
}
