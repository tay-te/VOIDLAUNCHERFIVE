package dev.voidmc.mettle.client.burden;

import dev.voidmc.mettle.api.LoadBand;
import dev.voidmc.mettle.burden.LoadState;
import dev.voidmc.mettle.burden.Weights;
import dev.voidmc.mettle.client.overlay.OverlayBridge;
import java.util.Locale;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * Dev fallback for the HUD chip (UI.md §6): one line of text left of the hotbar, only while Burdened or worse, only when
 * the overlay is not running. "Overburdened · 52.3 / 49.0 kg". Deliberately plain.
 */
final class BurdenHud {
	private static final int[] COLOUR = {0xFFFFFFFF, 0xFFE8D27A, 0xFFE89A4A, 0xFFE05A4A};

	private BurdenHud() {
	}

	static void extract(GuiGraphicsExtractor g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || OverlayBridge.hasListener()) {
			return;
		}
		LoadState s = LoadState.of(mc.player);
		if (s == null || s.exempt() || s.band() == LoadBand.LIGHT) {
			return;
		}
		Component text = Component.translatable("mettle.burden.hud", Component.translatable("mettle.burden.band." + s.band().name().toLowerCase(Locale.ROOT)),
			Weights.total(s.load()), Weights.total(s.capacity()));
		int x = g.guiWidth() / 2 - 91 - 6 - mc.font.width(text);
		int y = g.guiHeight() - 15;
		g.text(mc.font, text, x, y, COLOUR[s.band().ordinal()], true);
	}
}
