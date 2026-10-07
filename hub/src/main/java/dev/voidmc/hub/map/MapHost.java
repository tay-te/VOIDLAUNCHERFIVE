package dev.voidmc.hub.map;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;

/**
 * The map the hub's Map page hosts, in the hub's own terms (no Atlas types), so the hub's screens
 * load and run whether or not VOID Atlas is installed. The one implementation is
 * {@code map.atlas.AtlasMapHost}; {@link HubMap} decides whether it may be loaded.
 *
 * <p>Positions are GUI coordinates, like a {@code Screen}'s. Client thread only.
 */
public interface MapHost {

	/** Draws the whole map page into a rectangle, natively (Minecraft's GUI pipeline). */
	void render(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int mouseX, int mouseY);

	boolean mouseClicked(double x, double y, int button);

	boolean mouseReleased(double x, double y, int button);

	boolean mouseDragged(double x, double y);

	boolean mouseScrolled(double x, double y, double amount);

	/** False for keys the map does not use (Escape with nothing to close): the hub may act on them. */
	boolean keyPressed(KeyEvent event);

	boolean charTyped(CharacterEvent event);

	/** A text field has the keyboard: keys are text, not the hub's shortcuts. */
	boolean typing();

	/** The player's map key (M unless changed): the hub closes on it, as Atlas's own map does. */
	boolean isMapKey(KeyEvent event);

	/** The hub opened on (or switched to) the Map page: the map reads the player's dimension and depth. */
	void shown();

	/** The Map page stopped showing (hub closed, or another page): the map saves its settings. */
	void closed();

	/** The scale Atlas draws its 1280 x 744 page at in a rectangle of physical pixels. */
	double unitFor(int width, int height);

	/** Keeps the world still behind the page (drawn once, then skipped), or lets it move again. */
	void freezeWorld(boolean freeze);

	/** Draws Atlas's still of the world, full screen and dimmed; false while there is none yet. */
	boolean drawFrozenWorld(GuiGraphicsExtractor graphics);

	/**
	 * Adds a waypoint where the player stands and, if {@code select}, selects it on the map (which
	 * centres on it and shows its card). Returns its name, or null outside a world.
	 */
	String addWaypointHere(boolean select);

	/** How many waypoints the current world has. */
	int waypointCount();

	/** The map's centre and zoom as {@code {x, z, pixelsPerBlock}}. */
	double[] view();

	/** The map's view as text (layer, centre, zoom), for tests. */
	String describe();

	/** Atlas's version, for the log. */
	String version();
}
