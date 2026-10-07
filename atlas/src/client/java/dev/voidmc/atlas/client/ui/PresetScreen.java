package dev.voidmc.atlas.client.ui;

import com.mojang.blaze3d.platform.InputConstants;
import dev.voidmc.atlas.api.MarkerGlyph;
import dev.voidmc.atlas.client.AtlasClient;
import dev.voidmc.atlas.client.AtlasSettings;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * The first-run choice (Figma 375:37201, atlas/preset tile): "How much map do you want?", Minimal, Explorer or
 * PvP-safe, shown once, on the first M. PvP-safe is chosen beforehand on a server whose rules turn things off, Explorer
 * elsewhere; Esc takes Explorer, Enter the one chosen. A preset only sets defaults (each can be changed after); the map
 * opens straight after.
 */
public final class PresetScreen extends Screen {
	private static final float W = 650;
	private static final float H = 288;
	private static final AtlasSettings.Preset[] PRESETS = AtlasSettings.Preset.values();

	private final AtlasClient atlas;
	private final List<FormKit.Hit> hits = new ArrayList<>();
	private int chosen;
	private float scale = 1;

	public PresetScreen(AtlasClient atlas) {
		super(Component.literal("Atlas"));
		this.atlas = atlas;
		boolean pvpRules = atlas.multiplayer() && atlas.rules().turnedOff() != null;
		this.chosen = (pvpRules ? AtlasSettings.Preset.PVP_SAFE : AtlasSettings.Preset.EXPLORER).ordinal();
	}

	/** The glyph on a preset's tile. */
	static MarkerGlyph glyph(AtlasSettings.Preset p) {
		return switch (p) {
			case MINIMAL -> MarkerGlyph.GRID;
			case EXPLORER -> MarkerGlyph.PEAK;
			case PVP_SAFE -> MarkerGlyph.SHIELD;
		};
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	private float unit() {
		var window = Minecraft.getInstance().getWindow();
		return Math.min(MinimapHud.hudUnit(), Ui.fit(window.getWidth(), window.getHeight(), W + 48, H + 48));
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		Ui ui = new Ui(graphics, this.unit());
		ui.begin();
		ui.rect(0, 0, ui.width(), ui.height(), 0x590B0B0C);
		ui.end();
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		Ui ui = new Ui(graphics, this.unit());
		this.scale = ui.scale;
		this.hits.clear();
		ui.begin();
		ui.prewarm();
		FormKit kit = new FormKit(ui, this.hits, ui.design(mouseX), ui.design(mouseY));
		float px = (ui.width() - W) / 2;
		float py = (ui.height() - H) / 2;
		ui.panel(px, py, W, H, 24, Theme.ISLAND, Theme.HAIR_10);
		float cx = px + 20;
		float cw = W - 40;
		kit.caps("ATLAS  ·  FIRST TIME", cx, py + 22);
		ui.text("How much map do you want?", cx, py + 37, Ui.Font.F18, Theme.INK);
		float kw = kit.keycapWidth("Esc");
		kit.keycap("Esc", cx + cw - kw, py + 27);
		kit.hit(cx + cw - kw, py + 27, kw, 25, () -> this.start(AtlasSettings.Preset.EXPLORER));
		float tw = (cw - 2 * 12) / 3;
		float ty = py + 78;
		for (int i = 0; i < PRESETS.length; i++) {
			this.tile(ui, kit, PRESETS[i], cx + i * (tw + 12), ty, tw, 133, i == this.chosen, i);
		}
		ui.text("Change later in settings", cx, py + 241, Ui.Font.F11, Theme.INK_3);
		AtlasSettings.Preset p = PRESETS[this.chosen];
		String label = "Start with " + p.label;
		float bw = 32 + ui.width(label, Ui.Font.F13) + 10 + kit.keycapSmallWidth("Enter");
		kit.button(cx + cw - bw, py + 229, bw, true, label, "Enter", () -> this.start(p));
		ui.end();
	}

	/** One preset (atlas/preset tile): its glyph tile, a checkbox, the name and what it turns on and off. */
	private void tile(Ui ui, FormKit kit, AtlasSettings.Preset p, float x, float y, float w, float h, boolean on, int index) {
		boolean hover = !on && kit.over(x, y, w, h);
		ui.panel(x, y, w, h, 16, on ? Theme.RAISED : hover ? Theme.BUTTON : Theme.WELL, on ? 0x66FFFFFF : Theme.HAIR_7);
		ui.panel(x + 15, y + 15, 32, 32, 9, Theme.SHELL, Theme.HAIR_8);
		ui.glyph(glyph(p), x + 23, y + 23, 16, Theme.INK, Theme.INK);
		float bx = x + w - 15 - 16;
		float by = y + 23;
		if (on) {
			ui.round(bx, by, 16, 16, 5, Theme.INK);
			// the check: two strokes
			ui.line(bx + 4, by + 8.5F, bx + 7, by + 11.5F, 1.8F, Theme.SHELL);
			ui.line(bx + 7, by + 11.5F, bx + 12.5F, by + 5, 1.8F, Theme.SHELL);
		} else {
			ui.border(bx, by, 16, 16, 5, 1.2F, Theme.HAIR_16);
		}
		ui.text(p.label, x + 15, y + 61, Ui.Font.F13, Theme.INK);
		// the detail on two lines, broken at the full stop between "On" and "Off"
		String d = p.detail;
		int cut = d.indexOf(". ");
		float lineW = w - 30;
		if (ui.width(d, Ui.Font.F11) <= lineW) {
			ui.text(d, x + 15, y + 88, Ui.Font.F11, Theme.INK_2);
		} else {
			String first = cut > 0 ? d.substring(0, cut + 1) : d;
			String rest = cut > 0 ? d.substring(cut + 2) : "";
			if (ui.width(first, Ui.Font.F11) > lineW) {
				// a long first sentence: wrap by words
				int sp = first.lastIndexOf(' ', Math.max(1, (int) (first.length() * lineW / ui.width(first, Ui.Font.F11))));
				rest = first.substring(sp + 1) + (rest.isEmpty() ? "" : " " + rest);
				first = first.substring(0, Math.max(0, sp));
			}
			ui.text(first, x + 15, y + 88, Ui.Font.F11, Theme.INK_2);
			ui.text(ui.fit(rest, Ui.Font.F11, lineW), x + 15, y + 103, Ui.Font.F11, Theme.INK_2);
		}
		kit.hit(x, y, w, h, () -> this.chosen = index);
	}

	private void start(AtlasSettings.Preset p) {
		this.atlas.settings().apply(p);
		this.atlas.openMap();
	}

	@Override
	public void onClose() {
		// Esc: Explorer, the everyday choice
		this.start(AtlasSettings.Preset.EXPLORER);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && FormKit.click(this.hits, (float) (event.x() / this.scale), (float) (event.y() / this.scale))) {
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		int key = event.key();
		if (key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER) {
			this.start(PRESETS[this.chosen]);
			return true;
		}
		if (key == InputConstants.KEY_LEFT || key == InputConstants.KEY_RIGHT || key == InputConstants.KEY_TAB) {
			int step = key == InputConstants.KEY_LEFT || key == InputConstants.KEY_TAB && event.hasShiftDown() ? -1 : 1;
			this.chosen = Math.floorMod(this.chosen + step, PRESETS.length);
			return true;
		}
		return super.keyPressed(event);
	}
}
