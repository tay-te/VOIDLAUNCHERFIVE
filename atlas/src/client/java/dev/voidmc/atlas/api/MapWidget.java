package dev.voidmc.atlas.api;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;

/**
 * Atlas's whole map page (layer segments, search, the map with its readout, layers, zoom and scale, the waypoint and
 * discovery list, cards and editor) as a component another screen can host: the VOID Hub's "Map" page. Positions are
 * GUI coordinates (what a {@code Screen} receives); forward the screen's input and draw it every frame.
 *
 * <pre>{@code
 * MapWidget map = AtlasApi.get().createMapWidget();
 * // in extractRenderState:  map.render(graphics, x, y, w, h, mouseX, mouseY);
 * // in mouseClicked:        if (map.mouseClicked(event.x(), event.y(), event.button())) return true;
 * }</pre>
 */
public interface MapWidget {
	/** Draws the page into a rectangle (GUI coordinates); it lays itself out in the design's units inside it. */
	void render(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int mouseX, int mouseY);

	boolean mouseClicked(double x, double y, int button);

	boolean mouseReleased(double x, double y, int button);

	boolean mouseDragged(double x, double y);

	boolean mouseScrolled(double x, double y, double amount);

	/** Returns false for keys the page does not use (Escape with nothing to close, for one): the host may then act. */
	boolean keyPressed(KeyEvent event);

	boolean charTyped(CharacterEvent event);

	/** Whether a text field has the keyboard (the host should not treat keys as shortcuts). */
	boolean typing();

	/** Selects a marker by id and centres on it. */
	void select(String markerId);

	/** Shows a layer: {@link MapView#SURFACE}, {@link MapView#UNDERGROUND} or {@link MapView#NETHER}. */
	default void showLayer(String layer) {
	}

	/** Shows the underground at height {@code y} (the 8-block slice holding it). */
	default void showDepth(int y) {
	}

	/** The view shown now: layer (with its depth underground), centre and zoom. */
	default MapView view() {
		return MapView.surface(0, 0, 1);
	}

	/** Selects a waypoint (its card opens) and centres on it. */
	default void selectWaypoint(Waypoint waypoint) {
		if (waypoint != null) {
			this.select(AtlasApi.get().markerId(waypoint));
		}
	}

	/** The host stopped showing the page (its screen closed): the map's settings are saved, as Atlas's own screen does. */
	default void closed() {
	}

	/** The host shows the page again: it reads the player's dimension and whether they are underground, as M does. */
	default void shown() {
	}

	/**
	 * The scale Atlas draws its page at in a rectangle of {@code width x height} physical pixels: the largest of its own
	 * scales (0.75 to 3, those its fonts are made at) at which the 1280 x 744 page fits.
	 */
	default float unitFor(int width, int height) {
		return 1;
	}

	/**
	 * Keeps the world still behind the page (drawn once, then not again: about 1 ms a frame saved), or lets it move
	 * again. Draw the still with {@link #drawFrozenWorld}; let it go when the page closes.
	 */
	default void freezeWorld(boolean freeze) {
	}

	/** Draws the still of the world, full screen, dimmed as Atlas's own map does; false if there is none (yet). */
	default boolean drawFrozenWorld(GuiGraphicsExtractor graphics) {
		return false;
	}
}
