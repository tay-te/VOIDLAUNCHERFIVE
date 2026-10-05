package dev.voidmc.mettle.client.ui.kit;

import dev.voidmc.mettle.Mettle;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;

/**
 * Draws VOID panels at native screen resolution: between {@link #begin} and {@link #end} every coordinate is a whole
 * screen pixel. Layouts are written in Figma pixels (the frames are 1920 × 1080 at GUI scale 3) and converted with
 * {@link #px}, so a panel looks exactly like its frame on a 1080p screen and scales evenly elsewhere.
 *
 * <p>Text is Outfit through Mettle's generated font definitions ({@code mettle:outfit_<weight>_<px>}), one per whole
 * pixel size, so every glyph lands 1:1 on screen pixels.
 */
public final class UiCanvas {
	public final GuiGraphicsExtractor g;
	public final Font font;
	/** Figma pixels → screen pixels. */
	public final float u;
	/** Minecraft's GUI scale, for converting mouse positions. */
	public final int guiScale;

	private UiCanvas(GuiGraphicsExtractor g, Font font, float u, int guiScale) {
		this.g = g;
		this.font = font;
		this.u = u;
		this.guiScale = guiScale;
	}

	public static UiCanvas begin(GuiGraphicsExtractor g, float u) {
		Minecraft mc = Minecraft.getInstance();
		int guiScale = mc.getWindow().getGuiScale();
		g.pose().pushMatrix();
		g.pose().scale(1f / guiScale, 1f / guiScale);
		return new UiCanvas(g, mc.font, u, guiScale);
	}

	public void end() {
		this.g.pose().popMatrix();
	}

	/** A Figma length in screen pixels. */
	public int px(double figma) {
		return (int) Math.round(figma * this.u);
	}

	// ---------------------------------------------------------------- shapes

	public void fill(int x, int y, int w, int h, int argb) {
		if (w > 0 && h > 0) {
			this.g.fill(x, y, x + w, y + h, argb);
		}
	}

	public void round(int x, int y, int w, int h, int r, int argb) {
		r = Math.min(r, Math.min(w, h) / 2);
		if (r <= 0) {
			fill(x, y, w, h, argb);
			return;
		}
		corners(RoundCorners.disc(r), x, y, w, h, r, argb, true, true);
		fill(x + r, y, w - 2 * r, r, argb);
		fill(x, y + r, w, h - 2 * r, argb);
		fill(x + r, y + h - r, w - 2 * r, r, argb);
	}

	/** An inside stroke of thickness t around a rounded rectangle. */
	public void stroke(int x, int y, int w, int h, int r, int t, int argb) {
		r = Math.min(r, Math.min(w, h) / 2);
		if (r <= 0) {
			fill(x, y, w, t, argb);
			fill(x, y + h - t, w, t, argb);
			fill(x, y + t, t, h - 2 * t, argb);
			fill(x + w - t, y + t, t, h - 2 * t, argb);
			return;
		}
		corners(RoundCorners.ring(r, t), x, y, w, h, r, argb, true, true);
		fill(x + r, y, w - 2 * r, t, argb);
		fill(x + r, y + h - t, w - 2 * r, t, argb);
		fill(x, y + r, t, h - 2 * r, argb);
		fill(x + w - t, y + r, t, h - 2 * r, argb);
	}

	/** A rounded card: fill plus a hairline. */
	public void card(int x, int y, int w, int h, int r, int fillArgb, int strokeArgb, int strokeWidth) {
		round(x, y, w, h, r, fillArgb);
		if (strokeWidth > 0) {
			stroke(x, y, w, h, r, strokeWidth, strokeArgb);
		}
	}

	/**
	 * Rounds the corners of something square already drawn (a pixel-art scene) by laying {@code surround}, the colour
	 * around it, over what falls outside the radius.
	 */
	public void roundOff(int x, int y, int w, int h, int r, int surround, boolean top, boolean bottom) {
		if (r > 0) {
			corners(RoundCorners.outside(r), x, y, w, h, r, surround, top, bottom);
		}
	}

	private void corners(Identifier tex, int x, int y, int w, int h, int r, int argb, boolean top, boolean bottom) {
		int s = 2 * r;
		if (top) {
			this.g.blit(RenderPipelines.GUI_TEXTURED, tex, x, y, 0, 0, r, r, s, s, argb);
			this.g.blit(RenderPipelines.GUI_TEXTURED, tex, x + w - r, y, r, 0, r, r, s, s, argb);
		}
		if (bottom) {
			this.g.blit(RenderPipelines.GUI_TEXTURED, tex, x, y + h - r, 0, r, r, r, s, s, argb);
			this.g.blit(RenderPipelines.GUI_TEXTURED, tex, x + w - r, y + h - r, r, r, r, r, s, s, argb);
		}
	}

	// ---------------------------------------------------------------- images

	/** A texture stretched to w × h with nearest sampling: pixel art stays crisp at whole multiples. */
	public void sprite(Identifier texture, int x, int y, int w, int h, int texW, int texH, int argb) {
		this.g.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, 0, 0, w, h, texW, texH, texW, texH, argb);
	}

	/** A Minecraft item at {@code size} screen pixels, with its count and durability bar drawn the vanilla way. */
	public void item(ItemStack stack, int x, int y, int size, boolean decorations) {
		this.g.pose().pushMatrix();
		this.g.pose().translate(x, y);
		this.g.pose().scale(size / 16f, size / 16f);
		this.g.item(stack, 0, 0);
		if (decorations) {
			this.g.itemDecorations(this.font, stack, 0, 0);
		}
		this.g.pose().popMatrix();
	}

	// ---------------------------------------------------------------- text

	public enum Weight {
		LIGHT(300), REGULAR(400), MEDIUM(500);

		final int value;

		Weight(int value) {
			this.value = value;
		}
	}

	/** The whole-pixel size a Figma font size becomes. */
	public int fontPx(float figmaSize) {
		return Math.clamp(Math.round(figmaSize * this.u), 7, 72);
	}

	public Style style(Weight weight, float figmaSize) {
		Identifier id = Mettle.id("outfit_" + weight.value + "_" + fontPx(figmaSize));
		return Style.EMPTY.withFont(new FontDescription.Resource(id));
	}

	public MutableComponent styled(Component text, Weight weight, float figmaSize) {
		return text.copy().withStyle(style(weight, figmaSize));
	}

	public int width(Component text, Weight weight, float figmaSize) {
		return this.font.width(styled(text, weight, figmaSize));
	}

	/**
	 * Draws text with its line box's top at {@code top} (Figma places text by the top of an auto-height line box; Outfit's
	 * ascent is one em, so the baseline sits one font size below the top). Returns the width drawn.
	 */
	public int text(Component text, Weight weight, float figmaSize, int x, int top, int argb) {
		MutableComponent styled = styled(text, weight, figmaSize);
		this.g.text(this.font, styled, x, baselineY(top, figmaSize), argb, false);
		return this.font.width(styled);
	}

	public int text(String text, Weight weight, float figmaSize, int x, int top, int argb) {
		return text(Component.literal(text), weight, figmaSize, x, top, argb);
	}

	public void textRight(Component text, Weight weight, float figmaSize, int right, int top, int argb) {
		text(text, weight, figmaSize, right - width(text, weight, figmaSize), top, argb);
	}

	public void textCentred(Component text, Weight weight, float figmaSize, int centreX, int top, int argb) {
		text(text, weight, figmaSize, centreX - width(text, weight, figmaSize) / 2, top, argb);
	}

	/**
	 * Letter-spaced text (Figma's tracking, as a share of the font size), drawn glyph by glyph since Minecraft's text has
	 * no tracking of its own. Used for the small caps labels (+12 %) and big titles (−2 %).
	 */
	public int tracked(String text, Weight weight, float figmaSize, float tracking, int x, int top, int argb) {
		Style style = style(weight, figmaSize);
		float step = tracking * fontPx(figmaSize);
		float cx = x;
		int y = baselineY(top, figmaSize);
		for (int i = 0; i < text.length(); ) {
			int cp = text.codePointAt(i);
			String glyph = new String(Character.toChars(cp));
			Component c = Component.literal(glyph).withStyle(style);
			this.g.text(this.font, c, Math.round(cx), y, argb, false);
			cx += this.font.width(c) + step;
			i += Character.charCount(cp);
		}
		return Math.round(cx - step) - x;
	}

	public int trackedWidth(String text, Weight weight, float figmaSize, float tracking) {
		Style style = style(weight, figmaSize);
		float step = tracking * fontPx(figmaSize);
		float w = 0;
		int glyphs = 0;
		for (int i = 0; i < text.length(); ) {
			int cp = text.codePointAt(i);
			w += this.font.width(Component.literal(new String(Character.toChars(cp))).withStyle(style)) + step;
			glyphs++;
			i += Character.charCount(cp);
		}
		return glyphs == 0 ? 0 : Math.round(w - step);
	}

	/**
	 * The largest size, at most {@code figmaSize} and no less than 85 % of it, at which the text fits {@code maxWidth}.
	 * Minecraft hints Outfit to whole pixels, so text runs a few percent wider than in Figma.
	 */
	public float fit(Component text, Weight weight, float figmaSize, int maxWidth) {
		float size = figmaSize;
		while (size > figmaSize * 0.85f && width(text, weight, size) > maxWidth) {
			size -= 0.5f;
		}
		return size;
	}

	/** A small left or right arrow (Outfit has no arrow glyphs), centred on (cx, cy). */
	public void arrow(int cx, int cy, boolean right, int argb) {
		int t = Math.max(1, px(1.2));
		int half = Math.max(2, px(4));
		int x0 = cx - half;
		int x1 = cx + half;
		fill(x0, cy - t / 2, x1 - x0, t, argb);
		int head = Math.max(2, px(3.5));
		for (int i = 0; i <= head; i++) {
			int x = right ? x1 - t - i : x0 + i;
			fill(x, cy - t / 2 - i, t, t, argb);
			fill(x, cy - t / 2 + i, t, t, argb);
		}
	}

	/** Wrapped text; returns the height used. Lines are spaced at 1.4 em. */
	public int wrapped(Component text, Weight weight, float figmaSize, int x, int top, int maxWidth, int argb) {
		List<FormattedCharSequence> lines = this.font.split(styled(text, weight, figmaSize), maxWidth);
		int lineHeight = Math.round(fontPx(figmaSize) * 1.4f);
		int y = top;
		for (FormattedCharSequence line : lines) {
			this.g.text(this.font, line, x, baselineY(y, figmaSize), argb, false);
			y += lineHeight;
		}
		return y - top;
	}

	/** Minecraft draws a glyph's top at y + 7 − its height above the baseline, so the baseline is at y + 7. */
	private int baselineY(int top, float figmaSize) {
		return top + fontPx(figmaSize) - 7;
	}
}
