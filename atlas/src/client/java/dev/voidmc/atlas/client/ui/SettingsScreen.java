package dev.voidmc.atlas.client.ui;

import com.mojang.blaze3d.platform.InputConstants;
import dev.voidmc.atlas.client.AtlasClient;
import dev.voidmc.atlas.client.AtlasSettings;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * Atlas's settings in the same flat style as the map: a small centred window of rows, each a label and a segmented
 * choice or a toggle. Every change applies at once; the file is written when the window closes.
 */
public final class SettingsScreen extends Screen {
	private static final float W = 560;
	private final AtlasClient atlas;
	private final @Nullable Screen parent;
	private final List<Hit> hits = new ArrayList<>();
	private float scale = 1;

	private record Hit(float x, float y, float w, float h, Runnable action) {
	}

	private record Choice<T>(String label, String hint, T[] values, java.util.function.Function<T, String> names, Supplier<T> get, Consumer<T> set) {
	}

	public SettingsScreen(AtlasClient atlas, @Nullable Screen parent) {
		super(Component.translatable("atlas.settings.title"));
		this.atlas = atlas;
		this.parent = parent;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void onClose() {
		this.atlas.settings().save();
		Minecraft.getInstance().gui.setScreen(this.parent);
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		Ui ui = new Ui(graphics, this.unit());
		ui.begin();
		if (this.parent == null) {
			ui.rect(0, 0, ui.width(), ui.height(), 0xB30B0B0C);
		} else {
			ui.rect(0, 0, ui.width(), ui.height(), 0x800B0B0C);
		}
		ui.end();
	}

	private float unit() {
		var window = Minecraft.getInstance().getWindow();
		return Ui.fit(window.getWidth(), window.getHeight(), 1360, 824);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		Ui ui = new Ui(graphics, this.unit());
		this.scale = ui.scale;
		this.hits.clear();
		ui.begin();
		float mx = ui.design(mouseX);
		float my = ui.design(mouseY);
		AtlasSettings s = this.atlas.settings();
		List<Object> rows = new ArrayList<>();
		rows.add(new Choice<>("Minimap", "N toggles it in game", new Boolean[]{true, false}, v -> v ? "On" : "Off", () -> s.minimap, v -> s.minimap = v));
		rows.add(new Choice<>("Position", "", AtlasSettings.Corner.values(), c -> c.label, () -> s.minimapCorner, v -> s.minimapCorner = v));
		rows.add(new Choice<>("Size", "", AtlasSettings.Size.values(), c -> c.label, () -> s.minimapSize, v -> s.minimapSize = v));
		Integer[] zooms = new Integer[AtlasSettings.ZOOMS.length - 4];
		for (int i = 0; i < zooms.length; i++) {
			zooms[i] = i + 2;
		}
		rows.add(new Choice<>("Minimap zoom", "= and – in game", zooms, i -> MapPage.scaleLabel(AtlasSettings.ZOOMS[i]), () -> s.minimapZoom, v -> s.minimapZoom = v));
		rows.add(new Choice<>("Orientation", "", new Boolean[]{false, true}, v -> v ? "Turns with you" : "North up", () -> s.minimapRotates,
			v -> s.minimapRotates = v));
		rows.add(new Choice<>("Readout", "Biome and coordinates under the minimap", new Boolean[]{true, false}, v -> v ? "On" : "Off",
			() -> s.minimapCoordinates, v -> s.minimapCoordinates = v));
		rows.add(new Choice<>("Compass", "A strip at the top with the places you have sighted", new Boolean[]{false, true}, v -> v ? "On" : "Off",
			() -> s.compass, v -> s.compass = v));
		rows.add(new Choice<>("Map backdrop", "A still costs nothing; live redraws the world", new Boolean[]{false, true}, v -> v ? "Live" : "Still",
			() -> s.liveBackdrop, v -> s.liveBackdrop = v));
		rows.add(new Choice<>("Map workers", "Threads that read chunks and draw tiles (next world)", new Integer[]{1, 2, 3, 4}, String::valueOf,
			() -> s.workerThreads, v -> s.workerThreads = v));
		float rowH = 56;
		float h = 88 + rows.size() * rowH + 72;
		float x = (ui.width() - W) / 2;
		float y = (ui.height() - h) / 2;
		ui.round(x, y, W, h, 28, 0xF50B0B0C);
		ui.border(x, y, W, h, 28, 0, Theme.HAIR_7);
		ui.text("Atlas", x + 28, y + 26, Ui.Font.F16, Theme.INK);
		ui.text("SETTINGS  ·  CHANGES APPLY IMMEDIATELY", x + 28, y + 50, Ui.Font.F85, 1, Theme.INK_4);
		float ry = y + 88;
		for (Object row : rows) {
			this.choice(ui, (Choice<?>) row, x + 20, ry, W - 40, mx, my);
			ry += rowH;
		}
		float bw = 120;
		boolean hover = mx >= x + W - 28 - bw && mx <= x + W - 28 && my >= ry + 16 && my <= ry + 56;
		ui.panel(x + W - 28 - bw, ry + 16, bw, 40, 20, hover ? 0xFF2A2B2F : Theme.BUTTON, Theme.HAIR_8);
		ui.textCentred("Done", x + W - 28 - bw / 2, ry + 28, Ui.Font.F13, 0, Theme.INK);
		this.hits.add(new Hit(x + W - 28 - bw, ry + 16, bw, 40, this::onClose));
		ui.text("M  MAP    ·    N  MINIMAP    ·    B  WAYPOINT", x + 28, ry + 31, Ui.Font.F85, 1, Theme.INK_4);
		ui.end();
	}

	private <T> void choice(Ui ui, Choice<T> c, float x, float y, float w, float mx, float my) {
		ui.panel(x, y, w, 50, 14, Theme.ISLAND, Theme.HAIR_7);
		ui.text(c.label(), x + 16, y + (c.hint().isEmpty() ? 17 : 9), Ui.Font.F13, Theme.INK);
		if (!c.hint().isEmpty()) {
			ui.text(c.hint().toUpperCase(java.util.Locale.ROOT), x + 16, y + 30, Ui.Font.F85, 1, Theme.INK_4);
		}
		T current = c.get().get();
		float total = 8;
		float[] widths = new float[c.values().length];
		for (int i = 0; i < c.values().length; i++) {
			widths[i] = ui.width(c.names().apply(c.values()[i]), Ui.Font.F12) + 22;
			total += widths[i] + (i > 0 ? 2 : 0);
		}
		float sx = x + w - 9 - total;
		ui.panel(sx, y + 8, total, 34, 12, Theme.WELL, Theme.HAIR_6);
		float cx = sx + 4;
		for (int i = 0; i < c.values().length; i++) {
			T v = c.values()[i];
			boolean on = v.equals(current);
			boolean hover = !on && mx >= cx && mx <= cx + widths[i] && my >= y + 12 && my <= y + 38;
			if (on) {
				ui.panel(cx, y + 12, widths[i], 26, 9, Theme.RAISED, Theme.HAIR_8);
			} else if (hover) {
				ui.round(cx, y + 12, widths[i], 26, 9, Theme.ISLAND);
			}
			ui.text(c.names().apply(v), cx + 11, y + 17, Ui.Font.F12, on ? Theme.INK : Theme.INK_2);
			this.hits.add(new Hit(cx, y + 12, widths[i], 26, () -> c.set().accept(v)));
			cx += widths[i] + 2;
		}
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
			float x = (float) (event.x() / this.scale);
			float y = (float) (event.y() / this.scale);
			for (Hit h : this.hits) {
				if (x >= h.x && x <= h.x + h.w && y >= h.y && y <= h.y + h.h) {
					h.action.run();
					return true;
				}
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		return super.keyPressed(event);
	}
}
