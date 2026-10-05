package dev.voidmc.atlas.client.ui;

import dev.voidmc.atlas.client.AtlasClient;
import dev.voidmc.atlas.client.Backdrop;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * The full map (M): the design's map window, centred over a dimmed, still picture of the world. Flat: a rounded window
 * with a hairline, no blur, no shadow. The window is laid out in design pixels at the largest of Atlas's scales that
 * fits the screen.
 */
public final class AtlasScreen extends Screen {
	public static final float WINDOW_W = 1280;
	public static final float WINDOW_H = 744;
	private static final float MARGIN = 40;

	private final AtlasClient atlas;
	public final MapPage page;
	private float unit = 1;
	private float scale = 1;

	public AtlasScreen(AtlasClient atlas) {
		super(Component.translatable("atlas.map.title"));
		this.atlas = atlas;
		this.page = new MapPage(atlas, () -> atlas.openSettings(this));
	}

	@Override
	protected void init() {
		Backdrop.open(!this.atlas.settings().liveBackdrop);
	}

	@Override
	public void removed() {
		Backdrop.close();
		this.atlas.settings().save();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	private float fitUnit() {
		var window = Minecraft.getInstance().getWindow();
		return Ui.fit(window.getWidth(), window.getHeight(), WINDOW_W + MARGIN * 2, WINDOW_H + MARGIN * 2);
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		// no vanilla blur or menu texture: the still of the world, dimmed flat
		Ui ui = new Ui(graphics, this.fitUnit());
		ui.begin();
		float w = ui.width();
		float h = ui.height();
		Backdrop.draw(ui, w, h);
		ui.rect(0, 0, w, h, 0xC20B0B0C);
		ui.end();
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		Ui ui = new Ui(graphics, this.fitUnit());
		this.unit = ui.unit;
		this.scale = ui.scale;
		ui.begin();
		float sw = ui.width();
		float sh = ui.height();
		float ww = Math.min(WINDOW_W, sw - MARGIN);
		float wh = Math.min(WINDOW_H, sh - MARGIN);
		float wx = (sw - ww) / 2;
		float wy = (sh - wh) / 2;
		ui.round(wx, wy, ww, wh, 32, 0xF50B0B0C);
		ui.border(wx, wy, ww, wh, 32, 0, Theme.HAIR_7);
		this.page.render(ui, wx, wy, ww, wh, ui.design(mouseX), ui.design(mouseY));
		ui.end();
	}

	private float d(double gui) {
		return (float) (gui / this.scale);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		return this.page.mouseClicked(this.d(event.x()), this.d(event.y()), event.button()) || super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		return this.page.mouseReleased(this.d(event.x()), this.d(event.y()), event.button()) || super.mouseReleased(event);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		return this.page.mouseDragged(this.d(event.x()), this.d(event.y())) || super.mouseDragged(event, dx, dy);
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		return this.page.mouseScrolled(this.d(x), this.d(y), scrollY) || super.mouseScrolled(x, y, scrollX, scrollY);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (this.page.keyPressed(event)) {
			return true;
		}
		if (!this.page.typing() && Minecraft.getInstance().options != null && this.atlas.api().mapKeyMatches(event)) {
			this.onClose();
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		return this.page.charTyped(event) || super.charTyped(event);
	}
}
