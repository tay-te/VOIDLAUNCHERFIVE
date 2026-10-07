package dev.voidmc.hub.map.atlas;

import dev.voidmc.atlas.api.AtlasApi;
import dev.voidmc.atlas.api.MapView;
import dev.voidmc.atlas.api.MapWidget;
import dev.voidmc.atlas.api.Waypoint;
import dev.voidmc.hub.VoidHub;
import dev.voidmc.hub.map.MapHost;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.player.LocalPlayer;

/**
 * The hub's Map page over VOID Atlas's {@link MapWidget}: Atlas's whole map page (segments, search,
 * the map, layers, zoom, the places list, cards and editor), drawn natively into the hub's window.
 * Atlas's widget is a view of the session's one map page, the same one its own map screen (M) shows,
 * so the two share view, zoom, layer, depth and selection by construction.
 *
 * <p>The only class of the hub that names Atlas types. {@code HubMap.host()} reaches it through
 * {@link #create}, and only once {@code FabricLoader.isModLoaded("atlas")} said yes.
 */
public final class AtlasMapHost implements MapHost {

	private final AtlasApi api;
	private final MapWidget widget;
	private final boolean still;

	private AtlasMapHost(AtlasApi api, boolean still) {
		this.api = api;
		this.widget = api.createMapWidget();
		this.still = still;
	}

	/**
	 * Returns the hub-facing type, so the caller links against nothing of Atlas's. Throws on an Atlas
	 * from before the hub's API (6c4fb205): the hub then shows the stand-in rather than fail later.
	 */
	public static MapHost create() {
		try {
			MapWidget.class.getMethod("unitFor", int.class, int.class);
			AtlasApi.class.getMethod("isMapKey", KeyEvent.class);
		} catch (NoSuchMethodException e) {
			throw new IllegalStateException("this VOID Atlas is older than the hub needs (MapWidget.unitFor, AtlasApi.isMapKey)", e);
		}
		boolean still;
		try {
			MapWidget.class.getMethod("drawFrozenWorld", GuiGraphicsExtractor.class, float.class);
			still = true;
		} catch (NoSuchMethodException e) {
			still = false;  // before 792a7cfc: the still only comes at Atlas's own dim; stay live
		}
		return new AtlasMapHost(AtlasApi.get(), still);
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
	public boolean isMapKey(KeyEvent event) {
		return this.api.isMapKey(event);
	}

	@Override
	public void shown() {
		this.widget.shown();
	}

	@Override
	public void closed() {
		this.widget.closed();
	}

	@Override
	public double unitFor(int width, int height) {
		return this.widget.unitFor(width, height);
	}

	@Override
	public void freezeWorld(boolean freeze) {
		this.widget.freezeWorld(freeze);
	}

	@Override
	public boolean canDrawStill() {
		return this.still;
	}

	@Override
	public boolean drawFrozenWorld(GuiGraphicsExtractor graphics, float dim) {
		return this.still && this.widget.drawFrozenWorld(graphics, dim);
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
			this.widget.selectWaypoint(w);
		}
		VoidHub.LOGGER.info("VOID Hub: added Atlas waypoint \"{}\" at {} {} {} ({}, {})", w.name(), w.x(), w.y(), w.z(), w.dimension(), this.api.markerId(w));
		return w.name();
	}

	@Override
	public int waypointCount() {
		return this.api.waypoints().size();
	}

	@Override
	public double[] view() {
		MapView v = this.widget.view();
		return new double[] {v.centerX(), v.centerZ(), v.pixelsPerBlock()};
	}

	@Override
	public String describe() {
		MapView v = this.widget.view();
		return String.format(Locale.ROOT, "%s %.2f %.2f @%.3f", v.layer(), v.centerX(), v.centerZ(), v.pixelsPerBlock());
	}
}
