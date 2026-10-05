package dev.voidmc.mettle.client.character;

import dev.voidmc.mettle.api.Background;
import dev.voidmc.mettle.api.MettleRegistries;
import dev.voidmc.mettle.api.Skill;
import dev.voidmc.mettle.client.ui.kit.Ui;
import dev.voidmc.mettle.client.ui.kit.UiCanvas;
import dev.voidmc.mettle.client.ui.kit.UiCanvas.Weight;
import dev.voidmc.mettle.core.CharacterState;
import dev.voidmc.mettle.core.CharacterStore;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import org.jspecify.annotations.Nullable;

/**
 * The K screen, for now: your background, your attributes, what you favour and your signature perk, in one centred VOID
 * panel. The Attributes and Skills tabs (UI.md §3) grow out of this when skills land. Undecided players get a button
 * back into the picker.
 */
public class CharacterScreen extends Screen {
	private float u = 1f;
	private BackgroundPanel.@Nullable Rect choose;

	public CharacterScreen() {
		super(Component.translatable("key.mettle.character"));
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		g.fill(0, 0, this.width, this.height, Ui.DIM);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		super.extractRenderState(g, mouseX, mouseY, a);
		if (this.minecraft.player == null || this.minecraft.getConnection() == null) {
			return;
		}
		RegistryAccess registries = this.minecraft.getConnection().registryAccess();
		CharacterState state = CharacterStore.get(this.minecraft.player);
		Background background = CharacterStore.background(registries, state);
		int screenW = this.minecraft.getWindow().getWidth();
		int screenH = this.minecraft.getWindow().getHeight();
		int figmaH = background == null ? 220 : BackgroundPanel.ownedHeight(background);
		int wanted = Math.round(6f * screenH / 1080f);
		int fit = (int) Math.floor(6f * (screenH - 32) / figmaH);
		this.u = Math.max(2, Math.min(wanted, fit)) / 6f;
		double gs = this.minecraft.getWindow().getGuiScale();
		double mx = mouseX * gs;
		double my = mouseY * gs;

		UiCanvas c = UiCanvas.begin(g, this.u);
		try {
			int w = c.px(BackgroundPanel.W);
			int h = c.px(figmaH);
			int x = (screenW - w) / 2;
			int y = (screenH - h) / 2;
			if (background == null || state.background().isEmpty()) {
				drawUndecided(c, x, y, w, h, mx, my);
				return;
			}
			List<Holder.Reference<Skill>> skills = registries.lookupOrThrow(MettleRegistries.SKILL).listElements()
				.sorted(Comparator.comparingInt(r -> r.value().order())).toList();
			BackgroundPanel panel = new BackgroundPanel(state.background().get(), background, skills);
			List<ResourceKey<Skill>> favoured = state.favoured().isEmpty() ? background.favoured() : state.favoured();
			panel.drawOwned(c, x, y, h, attribute -> state.attribute(attribute, background), favoured);
			this.choose = null;
		} finally {
			c.end();
		}
	}

	private void drawUndecided(UiCanvas c, int x, int y, int w, int h, double mx, double my) {
		c.card(x, y, w, h, c.px(24), Ui.BASE, Ui.white(0.08f), 1);
		int ix = x + c.px(24);
		c.tracked(Component.translatable("mettle.character.eyebrow").getString(), Weight.MEDIUM, 10, 0.12f, ix, y + c.px(28), Ui.TEXT_2);
		c.wrapped(Component.translatable("mettle.character.undecided"), Weight.REGULAR, 17, ix, y + c.px(56), c.px(288), Ui.TEXT);
		BackgroundPanel.Rect b = new BackgroundPanel.Rect(ix, y + h - c.px(24 + 48), c.px(288), c.px(48));
		boolean hover = b.contains(mx, my);
		c.round(b.x(), b.y(), b.w(), b.h(), c.px(9), hover ? 0xFFFFFFFF : Ui.TEXT);
		c.textCentred(Component.translatable("mettle.character.choose"), Weight.MEDIUM, 15, b.x() + b.w() / 2, b.y() + (b.h() - c.px(19)) / 2, Ui.SHELL);
		this.choose = b;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		double gs = this.minecraft.getWindow().getGuiScale();
		if (this.choose != null && event.button() == 0 && this.choose.contains(event.x() * gs, event.y() * gs)) {
			this.minecraft.gui.setScreen(new BackgroundPickerScreen(false));
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}
}
