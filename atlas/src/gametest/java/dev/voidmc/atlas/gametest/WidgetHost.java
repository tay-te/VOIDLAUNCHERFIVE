package dev.voidmc.atlas.gametest;

import dev.voidmc.atlas.api.AtlasApi;
import dev.voidmc.atlas.api.MapWidget;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * A stand-in for the VOID Hub's Map page: a plain screen that hosts Atlas's {@link MapWidget} in a rectangle, the way the
 * hub does (the hub's own shell is not part of Atlas), for the tour's photograph of the embeddable map.
 */
final class WidgetHost extends Screen {
	final MapWidget widget = AtlasApi.get().createMapWidget();

	WidgetHost() {
		super(Component.literal("Atlas widget"));
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		graphics.fill(0, 0, this.width, this.height, 0xFF0B0B0C);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		int mx = this.width / 12;
		int my = this.height / 9;
		this.widget.render(graphics, mx, my, this.width - mx * 2, this.height - my * 2, mouseX, mouseY);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
