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
}
