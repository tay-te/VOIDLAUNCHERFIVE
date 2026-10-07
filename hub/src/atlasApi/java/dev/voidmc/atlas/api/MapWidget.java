package dev.voidmc.atlas.api;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;

/** Compile-time copy of Atlas's {@code MapWidget} (see {@link AtlasApi}). */
public interface MapWidget {
	void render(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int mouseX, int mouseY);

	boolean mouseClicked(double x, double y, int button);

	boolean mouseReleased(double x, double y, int button);

	boolean mouseDragged(double x, double y);

	boolean mouseScrolled(double x, double y, double amount);

	boolean keyPressed(KeyEvent event);

	boolean charTyped(CharacterEvent event);

	boolean typing();

	void select(String markerId);

	default void showLayer(String layer) {
	}

	default void showDepth(int y) {
	}

	default MapView view() {
		return new MapView(MapView.SURFACE, 0, 0, 1, 0, true, true, 12);
	}

	default void selectWaypoint(Waypoint waypoint) {
	}

	default void closed() {
	}

	default void shown() {
	}

	default float unitFor(int width, int height) {
		return 1;
	}

	default void freezeWorld(boolean freeze) {
	}

	default boolean drawFrozenWorld(GuiGraphicsExtractor graphics) {
		return false;
	}
}
