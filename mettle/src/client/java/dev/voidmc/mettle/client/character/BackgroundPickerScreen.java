package dev.voidmc.mettle.client.character;

import com.mojang.blaze3d.platform.InputConstants;
import dev.voidmc.mettle.api.Background;
import dev.voidmc.mettle.api.CharacterAttribute;
import dev.voidmc.mettle.api.MettleRegistries;
import dev.voidmc.mettle.api.Skill;
import dev.voidmc.mettle.client.ui.kit.Ui;
import dev.voidmc.mettle.client.ui.kit.UiCanvas;
import dev.voidmc.mettle.client.ui.kit.UiCanvas.Weight;
import dev.voidmc.mettle.core.net.MettlePayloads;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * "Choose your background" (Figma 1a/1b/1c, page 264:2): nine pixel-art cards in a centred panel; picking one opens a
 * fixed side panel with what it gives and a Begin button. Never pauses, never blurs; Esc closes it without choosing
 * (the K screen offers it again).
 *
 * <p>Sizes follow the frames: a Figma pixel is {@code u} screen pixels, where {@code u = m / 6} and {@code m} (the
 * screen pixels per pixel of a 48 × 24 scene) is the largest whole number that fits the window, so the pixel art is
 * always drawn at an exact multiple.
 */
public class BackgroundPickerScreen extends Screen {
	private static final int MAIN_W = 952;
	private static final int MAIN_H = 882;
	private static final int GAP = 16;
	private static final int PAD = 32;
	private static final int CARD_W = 288;
	private static final int CARD_H = 216;
	private static final int CARD_GAP = 12;
	private static final int COLS = 3;

	private final List<BackgroundPanel> panels = new ArrayList<>();
	private int selected = -1;
	private boolean waiting;
	private @Nullable CharacterAttribute dragging;

	// Layout of the last frame, in screen pixels.
	private float u = 1f;
	private int mainX;
	private int mainY;
	private final List<BackgroundPanel.Rect> cardRects = new ArrayList<>();

	/** Set when the server refused the last choice; shown in the footer until the player picks again. */
	private @Nullable Component notice;
	private final boolean byOperator;

	public BackgroundPickerScreen(boolean byOperator) {
		super(Component.translatable("mettle.picker.title"));
		this.byOperator = byOperator;
	}

	/** The server refused the choice: say why and let the player choose again. */
	public void reject(Component reason) {
		this.waiting = false;
		this.notice = reason;
	}

	@Override
	protected void init() {
		if (!this.panels.isEmpty() || this.minecraft.getConnection() == null) {
			return;
		}
		RegistryAccess registries = this.minecraft.getConnection().registryAccess();
		List<Holder.Reference<Skill>> skills = registries.lookupOrThrow(MettleRegistries.SKILL).listElements()
			.sorted(Comparator.comparingInt(r -> r.value().order()))
			.toList();
		registries.lookupOrThrow(MettleRegistries.BACKGROUND).listElements()
			.sorted(Comparator.comparingInt(r -> r.value().order()))
			.forEach(ref -> this.panels.add(new BackgroundPanel(ref.key(), ref.value(), skills)));
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		g.fill(0, 0, this.width, this.height, Ui.DIM);
	}

	private void layout(int screenW, int screenH) {
		int margin = 16;
		// Round up slightly so a 768-px laptop gets 5/6 rather than 4/6; the fit limits below keep it on screen.
		int wanted = (int) Math.ceil(6f * screenH / 1080f - 0.15f);
		int fitW = (int) Math.floor(6f * (screenW - 2 * margin) / (MAIN_W + GAP + BackgroundPanel.W));
		int fitH = (int) Math.floor(6f * (screenH - 2 * margin) / MAIN_H);
		int m = Math.max(2, Math.min(wanted, Math.min(fitW, fitH)));
		this.u = m / 6f;
		int mainW = Math.round(MAIN_W * this.u);
		int pairW = Math.round((MAIN_W + GAP + BackgroundPanel.W) * this.u);
		this.mainX = (screenW - mainW) / 2;
		// The main panel stays centred so the cards never move; only if the side panel would not fit is the pair centred.
		if (this.selected >= 0 && this.mainX + pairW > screenW - margin) {
			this.mainX = (screenW - pairW) / 2;
		}
		this.mainY = (screenH - Math.round(MAIN_H * this.u)) / 2;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		super.extractRenderState(g, mouseX, mouseY, a);
		int screenW = this.minecraft.getWindow().getWidth();
		int screenH = this.minecraft.getWindow().getHeight();
		layout(screenW, screenH);
		UiCanvas c = UiCanvas.begin(g, this.u);
		double mx = this.minecraft.mouseHandler.xpos() * screenW / Math.max(1, this.minecraft.getWindow().getScreenWidth());
		double my = this.minecraft.mouseHandler.ypos() * screenH / Math.max(1, this.minecraft.getWindow().getScreenHeight());
		try {
			drawMain(c, mx, my);
			if (this.selected >= 0) {
				int sideX = this.mainX + c.px(MAIN_W + GAP);
				this.panels.get(this.selected).drawPreview(c, sideX, this.mainY, mx, my, this.waiting);
			}
		} finally {
			c.end();
		}
	}

	private void drawMain(UiCanvas c, double mx, double my) {
		int x = this.mainX;
		int y = this.mainY;
		c.card(x, y, c.px(MAIN_W), c.px(MAIN_H), c.px(24), Ui.BASE, Ui.white(0.08f), 1);
		int ix = x + c.px(PAD);
		c.tracked(Component.translatable("mettle.picker.eyebrow").getString(), Weight.MEDIUM, 10, 0.12f, ix, y + c.px(32), Ui.TEXT_2);
		c.tracked(this.title.getString(), Weight.REGULAR, 34, -0.02f, ix, y + c.px(54), Ui.TEXT);
		if (this.byOperator) {
			c.text(Component.translatable("mettle.picker.reset_banner"), Weight.REGULAR, 14, ix, y + c.px(98), Ui.TEXT);
		} else {
			c.text(Component.translatable("mettle.picker.subtitle"), Weight.LIGHT, 14, ix, y + c.px(98), Ui.TEXT_2);
		}

		this.cardRects.clear();
		for (int i = 0; i < this.panels.size(); i++) {
			int cx = ix + c.px((CARD_W + CARD_GAP) * (i % COLS));
			int cy = y + c.px(142 + (CARD_H + CARD_GAP) * (i / COLS));
			BackgroundPanel.Rect r = new BackgroundPanel.Rect(cx, cy, c.px(CARD_W), c.px(CARD_H));
			this.cardRects.add(r);
			drawCard(c, this.panels.get(i), r, i == this.selected, r.contains(mx, my));
		}

		// Footer: a hairline, the hint, and the keys.
		c.fill(x, y + c.px(822), c.px(MAIN_W), 1, Ui.white(0.06f));
		Component hint;
		if (this.selected < 0) {
			hint = Component.translatable("mettle.picker.hint.none");
		} else {
			BackgroundPanel p = this.panels.get(this.selected);
			hint = p.editable()
				? Component.translatable("mettle.picker.hint.wanderer")
				: Component.translatable("mettle.picker.hint.selected", p.background.name(), bonusSummary(p.background));
		}
		if (this.notice != null) {
			c.text(this.notice, Weight.MEDIUM, 13, ix, y + c.px(843), Ui.DANGER);
		} else {
			c.text(hint, Weight.REGULAR, 13, ix, y + c.px(843), Ui.TEXT_3);
		}

		int right = x + c.px(MAIN_W - PAD);
		int top = y + c.px(842);
		right -= c.width(Component.translatable("mettle.picker.key.choose"), Weight.REGULAR, 12);
		c.text(Component.translatable("mettle.picker.key.choose"), Weight.REGULAR, 12, right, top + c.px(1), Ui.TEXT_3);
		right = keycap(c, right - c.px(7), top, 44, Component.translatable("mettle.picker.key.enter"));
		right -= c.px(16) + c.width(Component.translatable("mettle.picker.key.browse"), Weight.REGULAR, 12);
		c.text(Component.translatable("mettle.picker.key.browse"), Weight.REGULAR, 12, right, top + c.px(1), Ui.TEXT_3);
		right = keycap(c, right - c.px(7), top, 22, null);
		c.arrow(right + c.px(11), top + c.px(10), true, Ui.TEXT_2);
		int left = keycap(c, right - c.px(7), top, 22, null);
		c.arrow(left + c.px(11), top + c.px(10), false, Ui.TEXT_2);
	}

	/** Draws a keycap ending at {@code right}; returns its left edge. */
	private static int keycap(UiCanvas c, int right, int top, int figmaW, @Nullable Component label) {
		int w = c.px(figmaW);
		c.round(right - w, top, w, c.px(20), c.px(6), Ui.white(0.08f));
		if (label != null) {
			c.textCentred(label, Weight.MEDIUM, 11, right - w / 2, top + c.px(2), Ui.TEXT_2);
		}
		return right - w;
	}

	private static void drawCard(UiCanvas c, BackgroundPanel p, BackgroundPanel.Rect r, boolean selected, boolean hover) {
		int radius = c.px(14);
		c.round(r.x(), r.y(), r.w(), r.h(), radius, selected ? Ui.RAISED : hover ? Ui.CARD_HOVER : Ui.CARD);
		int artH = c.px(144);
		c.sprite(Background.art(p.key), r.x(), r.y(), r.w(), artH, 48, 24, 0xFFFFFFFF);
		c.roundOff(r.x(), r.y(), r.w(), artH, radius, Ui.BASE, true, false);
		if (selected) {
			c.stroke(r.x(), r.y(), r.w(), r.h(), radius, Math.max(1, c.px(1.5)), Ui.ACCENT);
		} else {
			c.stroke(r.x(), r.y(), r.w(), r.h(), radius, 1, Ui.white(hover ? 0.12f : 0.06f));
		}
		int nameW = c.text(p.background.name(), Weight.MEDIUM, 15, r.x() + c.px(16), r.y() + c.px(158), Ui.TEXT);
		if (selected) {
			int d = c.px(6);
			c.round(r.x() + c.px(16) + nameW + c.px(8), r.y() + c.px(165), d, d, d / 2, Ui.alpha(Ui.ACCENT, 0.95f));
		}
		c.text(p.background.tagline(), Weight.LIGHT, 13, r.x() + c.px(16), r.y() + c.px(181), Ui.TEXT_2);
	}

	/** "Agility +3, Vitality +1" for the footer hint. */
	private static Component bonusSummary(Background background) {
		StringBuilder sb = new StringBuilder();
		for (CharacterAttribute a : CharacterAttribute.values()) {
			int b = background.bonus(a);
			if (b != 0) {
				if (!sb.isEmpty()) {
					sb.append(", ");
				}
				sb.append(a.displayName().getString()).append(" +").append(b);
			}
		}
		return Component.literal(sb.toString());
	}

	// ---------------------------------------------------------------- input

	private double nativeX(double guiX) {
		return guiX * this.minecraft.getWindow().getGuiScale();
	}

	private double nativeY(double guiY) {
		return guiY * this.minecraft.getWindow().getGuiScale();
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (event.button() != 0 || this.waiting) {
			return super.mouseClicked(event, doubleClick);
		}
		double mx = nativeX(event.x());
		double my = nativeY(event.y());
		for (int i = 0; i < this.cardRects.size(); i++) {
			if (this.cardRects.get(i).contains(mx, my)) {
				boolean same = i == this.selected;
				select(i);
				if (same && doubleClick) {
					begin();
				}
				return true;
			}
		}
		if (this.selected >= 0) {
			BackgroundPanel p = this.panels.get(this.selected);
			if (p.button != null && p.button.contains(mx, my)) {
				begin();
				return true;
			}
			for (var e : p.meters.entrySet()) {
				if (e.getValue().contains(mx, my)) {
					this.dragging = e.getKey();
					p.placeAt(e.getKey(), mx);
					return true;
				}
			}
			for (var e : p.chips.entrySet()) {
				if (e.getValue().contains(mx, my)) {
					p.togglePick(e.getKey());
					return true;
				}
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if (this.dragging != null && this.selected >= 0) {
			this.panels.get(this.selected).placeAt(this.dragging, nativeX(event.x()));
			return true;
		}
		return super.mouseDragged(event, dx, dy);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		this.dragging = null;
		return super.mouseReleased(event);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		int key = event.key();
		if (!this.waiting && !this.panels.isEmpty()) {
			int cur = Math.max(this.selected, 0);
			switch (key) {
				case InputConstants.KEY_RIGHT -> {
					select(this.selected < 0 ? 0 : Math.min(this.panels.size() - 1, cur + 1));
					return true;
				}
				case InputConstants.KEY_LEFT -> {
					select(Math.max(0, cur - 1));
					return true;
				}
				case InputConstants.KEY_DOWN -> {
					select(this.selected < 0 ? 0 : Math.min(this.panels.size() - 1, cur + COLS));
					return true;
				}
				case InputConstants.KEY_UP -> {
					select(Math.max(0, cur - COLS));
					return true;
				}
				case InputConstants.KEY_RETURN, InputConstants.KEY_NUMPADENTER -> {
					begin();
					return true;
				}
				default -> {
				}
			}
		}
		return super.keyPressed(event);
	}

	// ---------------------------------------------------------------- actions

	/** Selects a card (also used by the client game test). */
	public void select(int index) {
		if (index >= 0 && index < this.panels.size()) {
			this.selected = index;
			this.notice = null;
		}
	}

	/** Selects a card by background id path, e.g. "ranger" (client game test). */
	public boolean select(String path) {
		for (int i = 0; i < this.panels.size(); i++) {
			if (this.panels.get(i).key.identifier().getPath().equals(path)) {
				select(i);
				return true;
			}
		}
		return false;
	}

	/** Places points in an attribute on the selected (editable) background, for the client game test. */
	public void placePoints(CharacterAttribute attribute, int points) {
		if (this.selected >= 0 && this.panels.get(this.selected).background.freePoints() > 0) {
			this.panels.get(this.selected).placed.put(attribute, points);
		}
	}

	/** Picks a skill to favour on the selected (editable) background, for the client game test. */
	public void pickSkill(String path) {
		if (this.selected < 0) {
			return;
		}
		BackgroundPanel p = this.panels.get(this.selected);
		p.skills.stream().filter(r -> r.key().identifier().getPath().equals(path) && r.value().released()).findFirst()
			.ifPresent(r -> p.togglePick(r.key()));
	}

	public void begin() {
		if (this.waiting || this.selected < 0) {
			return;
		}
		BackgroundPanel p = this.panels.get(this.selected);
		if (!p.ready()) {
			return;
		}
		this.waiting = true;
		ClientPlayNetworking.send(new MettlePayloads.ChooseBackground(p.key, BackgroundPanel.copy(p.placed), List.copyOf(p.picked)));
	}
}
