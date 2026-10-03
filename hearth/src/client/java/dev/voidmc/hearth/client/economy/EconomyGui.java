package dev.voidmc.hearth.client.economy;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.economy.coin.Change;
import dev.voidmc.hearth.economy.coin.Coin;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;

/** Drawing shared by the market and bank screens: coin amounts as little coins, parchment buttons, ink colours. */
final class EconomyGui {
	static final Identifier SPRITES = Hearth.id("textures/gui/economy/sprites.png");
	/** Dark brown ink on parchment. */
	static final int INK = 0xFF3E2A16;
	static final int INK_FADED = 0xFF8A7458;
	static final int INK_RED = 0xFF8C1F14;
	static final int INK_GREEN = 0xFF2F5A1E;
	/** Pale text on the wooden frame. */
	static final int ON_WOOD = 0xFFF4E6C4;
	static final int COIN_ICON = 10;

	private EconomyGui() {
	}

	/** Width of {@link #coins}' drawing. */
	static int coinsWidth(Font font, long bits) {
		long[] c = Change.fewest(Math.max(0, bits));
		int w = 0;
		boolean any = false;
		for (Coin coin : Coin.DESCENDING) {
			long n = c[coin.ordinal()];
			if (n > 0 || !any && coin == Coin.BIT && bits <= 0) {
				w += font.width(Long.toString(n)) + 1 + COIN_ICON + 2;
				any = true;
			}
		}
		return Math.max(0, w - 2);
	}

	/** Draws an amount as "3 (gold) 4 (iron) 2 (copper)" with the text baseline at {@code y}, starting at {@code x}. */
	static void coins(GuiGraphicsExtractor g, Font font, int x, int y, long bits, int color) {
		long[] c = Change.fewest(Math.max(0, bits));
		boolean any = false;
		for (Coin coin : Coin.DESCENDING) {
			long n = c[coin.ordinal()];
			if (n > 0 || !any && coin == Coin.BIT && bits <= 0) {
				String s = Long.toString(n);
				g.text(font, s, x, y, color, false);
				x += font.width(s) + 1;
				g.blit(RenderPipelines.GUI_TEXTURED, SPRITES, x, y - 1, 16 + coin.ordinal() * COIN_ICON, 0, COIN_ICON, COIN_ICON, 64, 64);
				x += COIN_ICON + 2;
				any = true;
			}
		}
	}

	/** Right-aligned at {@code right}. */
	static void coinsRight(GuiGraphicsExtractor g, Font font, int right, int y, long bits, int color) {
		coins(g, font, right - coinsWidth(font, bits), y, bits, color);
	}

	static void hammer(GuiGraphicsExtractor g, int x, int y) {
		g.blit(RenderPipelines.GUI_TEXTURED, SPRITES, x, y, 0, 0, 9, 9, 64, 64);
	}

	/** A small parchment button with an ink border; returns nothing, hit-testing is the screen's. */
	static void button(GuiGraphicsExtractor g, Font font, int x, int y, int w, int h, Component label, boolean enabled, boolean hovered) {
		int border = enabled ? 0xFF4A3420 : 0xFF9A8670;
		int fill = !enabled ? 0xFFD8C8A4 : hovered ? 0xFFF8ECCB : 0xFFE8D7AE;
		g.fill(x, y, x + w, y + h, border);
		g.fill(x + 1, y + 1, x + w - 1, y + h - 1, fill);
		if (enabled) {
			g.fill(x + 1, y + h - 2, x + w - 1, y + h - 1, hovered ? 0xFFD9C390 : 0xFFC4AC7C);
		}
		int tw = font.width(label);
		g.text(font, label, x + (w - tw) / 2, y + (h - 8) / 2, enabled ? INK : INK_FADED, false);
	}

	static boolean inside(double mx, double my, int x, int y, int w, int h) {
		return mx >= x && mx < x + w && my >= y && my < y + h;
	}

	/** Trims to a width, with an ellipsis. */
	static String trim(Font font, String s, int width) {
		if (font.width(s) <= width) {
			return s;
		}
		return font.plainSubstrByWidth(s, Math.max(0, width - font.width("…"))) + "…";
	}

	static void click() {
		Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
	}

	/** "Stranger", "Friend"... from a reputation. */
	static Component standing(int reputation) {
		String key = reputation >= 75 ? "hero" : reputation >= 40 ? "friend" : reputation >= 15 ? "known" : reputation > -15 ? "stranger"
			: reputation > -50 ? "distrusted" : "outcast";
		return Component.translatable("hearth.economy.standing." + key);
	}

	static Component cultureName(String culture) {
		String pretty = culture.isEmpty() ? culture : culture.substring(0, 1).toUpperCase(Locale.ROOT) + culture.substring(1);
		return Component.translatableWithFallback("hearth.culture." + culture, pretty);
	}
}
