package dev.voidmc.hearth.client.law;

import dev.voidmc.hearth.Hearth;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

/**
 * Drawing for the law's screens and HUD, in Minecraft's own look: chunky bevelled panels, inset slots, pixel circles and
 * lines laid out on a grid of "pixels" so everything stays crisp at any GUI scale.
 */
final class LawGui {
	static final Identifier SPRITES = Hearth.id("textures/gui/law/sprites.png");

	static final int OAK_DARK = 0xFF3B2614;
	static final int OAK = 0xFF6B4A2B;
	static final int OAK_LIGHT = 0xFF8E6A41;
	static final int SLATE_DARK = 0xFF1C1B20;
	static final int SLATE = 0xFF2C2B31;
	static final int SLATE_LIGHT = 0xFF3D3C44;
	static final int PARCHMENT = 0xFFE9DAB6;
	static final int PARCHMENT_LIGHT = 0xFFF3E7CB;
	static final int PARCHMENT_DARK = 0xFFD6C192;
	static final int INK = 0xFF3A2A1A;
	static final int INK_SOFT = 0xFF7A6040;
	static final int TEXT = 0xFFE8E2D4;
	static final int TEXT_DIM = 0xFFA89F8C;
	static final int RED = 0xFFD8402F;
	static final int GOLD = 0xFFE9B949;
	static final int GREEN = 0xFF7FCF6A;

	private LawGui() {
	}

	/** A framed oak panel with a dark slate inside (the thief's tool roll). */
	static void woodPanel(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1) {
		g.fill(x0 - 3, y0 - 3, x1 + 3, y1 + 3, 0xFF000000);
		g.fill(x0 - 2, y0 - 2, x1 + 2, y1 + 2, OAK_DARK);
		g.fill(x0 - 1, y0 - 1, x1 + 1, y1 + 1, OAK);
		g.fill(x0 - 1, y0 - 1, x1 + 1, y0, OAK_LIGHT);
		g.fill(x0 - 1, y0 - 1, x0, y1 + 1, OAK_LIGHT);
		g.fill(x0, y0, x1, y1, SLATE);
		g.fill(x0, y0, x1, y0 + 1, SLATE_DARK);
		g.fill(x0, y0, x0 + 1, y1, SLATE_DARK);
		g.fill(x0 + 1, y1 - 1, x1, y1, SLATE_LIGHT);
		g.fill(x1 - 1, y0 + 1, x1, y1, SLATE_LIGHT);
	}

	/** A sheet of parchment with a dark edge (the guard's notice, the witness's accusation). */
	static void parchment(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1) {
		g.fill(x0 - 2, y0 - 2, x1 + 2, y1 + 2, 0xFF5A4026);
		g.fill(x0, y0, x1, y1, PARCHMENT);
		g.fill(x0 + 2, y0 + 2, x1 - 2, y1 - 2, PARCHMENT_LIGHT);
		g.fill(x0 + 4, y0 + 4, x1 - 4, y1 - 4, PARCHMENT);
		g.fill(x0 + 4, y0 + 4, x1 - 4, y0 + 6, PARCHMENT_DARK);
		g.fill(x0 + 4, y1 - 6, x1 - 4, y1 - 4, PARCHMENT_DARK);
		g.fill(x0 - 2, y0 - 2, x0 + 2, y0 + 2, 0);
		g.fill(x1 - 2, y1 - 2, x1 + 2, y1 + 2, 0);
	}

	/** A leather pouch: stitched brown hide. */
	static void leather(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1) {
		g.fill(x0 - 3, y0 - 3, x1 + 3, y1 + 3, 0xFF000000);
		g.fill(x0 - 2, y0 - 2, x1 + 2, y1 + 2, 0xFF3A2212);
		g.fill(x0, y0, x1, y1, 0xFF7A4A28);
		g.fill(x0, y0, x1, y0 + 2, 0xFF95603A);
		g.fill(x0, y1 - 2, x1, y1, 0xFF5E361C);
		// stitching round the edge
		for (int x = x0 + 4; x < x1 - 4; x += 6) {
			g.fill(x, y0 + 3, x + 3, y0 + 4, 0xFFD9C29A);
			g.fill(x, y1 - 4, x + 3, y1 - 3, 0xFFD9C29A);
		}
		for (int y = y0 + 6; y < y1 - 6; y += 6) {
			g.fill(x0 + 3, y, x0 + 4, y + 3, 0xFFD9C29A);
			g.fill(x1 - 4, y, x1 - 3, y + 3, 0xFFD9C29A);
		}
	}

	/** A vanilla-style inventory slot (18 × 18 with the item area inset). */
	static void slot(GuiGraphicsExtractor g, int x, int y) {
		g.fill(x, y, x + 18, y + 18, 0xFF8B8B8B);
		g.fill(x, y, x + 17, y + 1, 0xFF373737);
		g.fill(x, y, x + 1, y + 17, 0xFF373737);
		g.fill(x + 1, y + 17, x + 18, y + 18, 0xFFFFFFFF);
		g.fill(x + 17, y + 1, x + 18, y + 18, 0xFFFFFFFF);
	}

	/**
	 * A filled circle of "pixels" of size {@code px}, centre (cx, cy), radius in pixels. Drawn row by row so it stays
	 * blocky like the rest of the game.
	 */
	static void disc(GuiGraphicsExtractor g, int cx, int cy, int radius, int px, int color) {
		for (int row = -radius; row < radius; row++) {
			double y = row + 0.5;
			int half = (int) Math.round(Math.sqrt(Math.max(0, radius * radius - y * y)));
			if (half > 0) {
				g.fill(cx - half * px, cy + row * px, cx + half * px, cy + (row + 1) * px, color);
			}
		}
	}

	/** A line of square "pixels" from (x0, y0) to (x1, y1). */
	static void line(GuiGraphicsExtractor g, double x0, double y0, double x1, double y1, int px, int color) {
		double len = Math.hypot(x1 - x0, y1 - y0);
		int steps = Math.max(1, (int) Math.ceil(len / px));
		int lastX = Integer.MIN_VALUE;
		int lastY = Integer.MIN_VALUE;
		for (int i = 0; i <= steps; i++) {
			double t = i / (double) steps;
			int x = (int) Math.floor((x0 + (x1 - x0) * t) / px) * px;
			int y = (int) Math.floor((y0 + (y1 - y0) * t) / px) * px;
			if (x != lastX || y != lastY) {
				g.fill(x, y, x + px, y + px, color);
				lastX = x;
				lastY = y;
			}
		}
	}

	/** One of the HUD sprites (sheet coordinates). */
	static void sprite(GuiGraphicsExtractor g, int x, int y, int u, int v, int w, int h) {
		g.blit(RenderPipelines.GUI_TEXTURED, SPRITES, x, y, u, v, w, h, 64, 64);
	}

	static void sound(SoundEvent sound, float pitch, float volume) {
		Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, volume));
	}

	static void sound(Holder<SoundEvent> sound, float pitch, float volume) {
		Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound.value(), pitch, volume));
	}

	/** Blends two ARGB colours. */
	static int mix(int a, int b, float t) {
		float k = Math.clamp(t, 0.0F, 1.0F);
		int aa = a >>> 24, ar = a >> 16 & 255, ag = a >> 8 & 255, ab = a & 255;
		int ba = b >>> 24, br = b >> 16 & 255, bg = b >> 8 & 255, bb = b & 255;
		return Math.round(aa + (ba - aa) * k) << 24 | Math.round(ar + (br - ar) * k) << 16 | Math.round(ag + (bg - ag) * k) << 8
			| Math.round(ab + (bb - ab) * k);
	}

	/** "4:07" from ticks. */
	static String clock(int ticks) {
		int seconds = Math.max(0, ticks / 20);
		return seconds / 60 + ":" + String.format(java.util.Locale.ROOT, "%02d", seconds % 60);
	}
}
