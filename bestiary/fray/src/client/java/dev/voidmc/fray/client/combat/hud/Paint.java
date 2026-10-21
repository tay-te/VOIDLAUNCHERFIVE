package dev.voidmc.fray.client.combat.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.ARGB;

/**
 * Drawing in the Figma frame's pixels ("design px", the 1920 × 1080 frames of section 3a). One design px is
 * {@link #k} GUI px, set so the vanilla hotbar (182 GUI px) is as wide as the VOID hotbar in the frames (573.6 px):
 * the HUD keeps its proportions to the hotbar at every GUI scale. Text is drawn at a scale whose font pixel lands on
 * whole screen pixels (crisp), as close as possible to the frame's type size. Flat and rounded only (the VOID UI rule).
 */
public final class Paint {
	public static final float HOTBAR_DESIGN = 573.6F;

	public static final int INK = 0x141416;
	public static final int TEXT = 0xFFF2F2EE;
	public static final int DIM = 0xFFA3A39C;
	public static final int HEALTH = 0xFFFF7A7A;
	public static final int LOW = 0xFFFF5A5A;
	public static final int RECENT = 0xFFFFD0C8;
	public static final int ABSORB = 0xFFE2BC5A;
	public static final int STAMINA = 0xFFEDE6CF;
	public static final int WINDED = 0xFFFF9E5A;
	public static final int BOSS = 0xFFE0574C;
	public static final int BREAK = 0xFFE8C46A;
	public static final int GOLD = 0xFFFFD36A;

	public GuiGraphicsExtractor g;
	public Font font;
	/** GUI px per design px. */
	public float k;
	public int gs;
	public int guiW, guiH;
	/** The screen centre and the hotbar's top edge, GUI px. */
	public float cx, cy, hotbarTop;

	public void begin(GuiGraphicsExtractor g, Minecraft mc, float hudScale) {
		this.g = g;
		this.font = mc.font;
		this.guiW = g.guiWidth();
		this.guiH = g.guiHeight();
		this.gs = Math.max(1, mc.getWindow().getGuiScale());
		this.k = 182F / HOTBAR_DESIGN * Math.max(0.5F, Math.min(2F, hudScale));
		this.cx = this.guiW / 2F;
		this.cy = this.guiH / 2F;
		this.hotbarTop = this.guiH - 22;
	}

	/** Design space anchored at a GUI point: (0, 0) there, one unit a design px. */
	public void push(float gx, float gy) {
		this.g.pose().pushMatrix();
		this.g.pose().translate(gx, gy);
		this.g.pose().scale(this.k, this.k);
	}

	/** Anchored so Figma coordinates can be used as they are: x relative to the centre (960), y to the hotbar's top (980). */
	public void pushBottom() {
		this.push(this.cx - 960 * this.k, this.hotbarTop - 980 * this.k);
	}

	/** Figma coordinates for the top of the screen: x relative to the centre, y from the top. */
	public void pushTop() {
		this.push(this.cx - 960 * this.k, 0);
	}

	public void pop() {
		this.g.pose().popMatrix();
	}

	public static int alpha(int argb, float f) {
		int a = (int) (((argb >>> 24) & 0xFF) * Math.max(0, Math.min(1, f)));
		return (a << 24) | (argb & 0xFFFFFF);
	}

	public static int ink(float opacity) {
		return ((int) (opacity * 255) << 24) | INK;
	}

	public void rect(float x, float y, float w, float h, int col) {
		if (w <= 0 || h <= 0 || (col >>> 24) == 0) {
			return;
		}
		int x0 = Math.round(x), y0 = Math.round(y);
		this.g.fill(x0, y0, Math.max(x0 + 1, Math.round(x + w)), Math.max(y0 + 1, Math.round(y + h)), col);
	}

	private static final int[] RADII = {2, 3, 4, 5, 6, 7, 8, 10, 11, 12, 14, 18, 22};
	private static final Identifier[] ROUND = new Identifier[RADII.length];
	private static final Identifier DISC = sprite("disc"), RING = sprite("ring"), RING_DASHED = sprite("ring_dashed"), DIAMOND = sprite("diamond"),
		DIAMOND_OUTLINE = sprite("diamond_outline"), TRIANGLE = sprite("triangle"), CAP_L = sprite("cap_l"), CAP_R = sprite("cap_r");

	static {
		for (int i = 0; i < RADII.length; i++) {
			ROUND[i] = sprite("round_" + RADII[i]);
		}
	}

	public static Identifier sprite(String name) {
		return Identifier.fromNamespaceAndPath("fray", "hud/" + name);
	}

	/** Draws a GUI sprite (nine-sliced or stretched) over a design-space rectangle, tinted. */
	public void blit(Identifier sprite, float x, float y, float w, float h, int col) {
		int x0 = Math.round(x), y0 = Math.round(y);
		int w0 = Math.max(1, Math.round(x + w) - x0), h0 = Math.max(1, Math.round(y + h) - y0);
		this.g.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, x0, y0, w0, h0, col);
	}

	/** A rounded rectangle (radius in design px): one nine-sliced sprite, the largest baked radius not above r. */
	public void round(float x, float y, float w, float h, float r, int col) {
		if (w <= 0 || h <= 0 || (col >>> 24) == 0) {
			return;
		}
		float rr = Math.min(r, Math.min(w, h) / 2);
		if (rr >= h / 2 - 0.5F && w >= h) {
			this.pill(x, y, w, h, col);
			return;
		}
		if (rr >= 2) {
			int pick = 0;
			for (int i = 0; i < RADII.length; i++) {
				if (RADII[i] <= rr + 0.5F) {
					pick = i;
				}
			}
			this.blit(ROUND[pick], x, y, w, h, col);
			return;
		}
		r = Math.min(r, Math.min(w, h) / 2);
		int ri = Math.round(r);
		if (ri <= 1) {
			this.rect(x, y, w, h, col);
			return;
		}
		this.rect(x, y + ri, w, h - 2 * ri, col);
		for (int i = 0; i < ri; i++) {
			double dy = ri - i - 0.5;
			float inset = (float) (ri - Math.sqrt(Math.max(0, ri * ri - dy * dy)));
			this.rect(x + inset, y + i, w - 2 * inset, 1, col);
			this.rect(x + inset, y + h - i - 1, w - 2 * inset, 1, col);
		}
	}

	/** A pill (radius h / 2): two half-disc caps and a fill (3 draws). */
	public void pill(float x, float y, float w, float h, int col) {
		if (w <= h) {
			this.blit(DISC, x, y, w, h, col);
			return;
		}
		float c = h / 2;
		int x0 = Math.round(x), x1 = Math.round(x + c), x2 = Math.round(x + w - c), x3 = Math.round(x + w);
		int y0 = Math.round(y), y1 = Math.max(y0 + 1, Math.round(y + h));
		this.g.blitSprite(RenderPipelines.GUI_TEXTURED, CAP_L, x0, y0, Math.max(1, x1 - x0), y1 - y0, col);
		if (x2 > x1) {
			this.g.fill(x1, y0, x2, y1, col);
		}
		this.g.blitSprite(RenderPipelines.GUI_TEXTURED, CAP_R, x2, y0, Math.max(1, x3 - x2), y1 - y0, col);
	}

	/** A rounded outline {@code t} px thick (baked sprites for the radii the HUD uses). */
	public void outline(float x, float y, float w, float h, float r, float t, int col) {
		int ri0 = Math.round(r), ti = Math.round(t);
		if ((ri0 == 6 || ri0 == 8 || ri0 == 10) && ti == 1 || ri0 == 12 && ti == 2) {
			this.blit(sprite("outline_" + ri0 + "_" + ti), x, y, w, h, col);
			return;
		}
		int ri = Math.round(Math.min(r, Math.min(w, h) / 2));
		this.rect(x + ri, y, w - 2 * ri, t, col);
		this.rect(x + ri, y + h - t, w - 2 * ri, t, col);
		this.rect(x, y + ri, t, h - 2 * ri, col);
		this.rect(x + w - t, y + ri, t, h - 2 * ri, col);
		float rin = Math.max(0, ri - t);
		for (int i = 0; i < ri; i++) {
			double dy = ri - i - 0.5;
			float outer = (float) (ri - Math.sqrt(Math.max(0, ri * ri - dy * dy)));
			float inner = dy >= rin ? ri : (float) (ri - Math.sqrt(Math.max(0, rin * rin - dy * dy)));
			float seg = Math.max(1, inner - outer);
			this.rect(x + outer, y + i, seg, 1, col);
			this.rect(x + w - outer - seg, y + i, seg, 1, col);
			this.rect(x + outer, y + h - i - 1, seg, 1, col);
			this.rect(x + w - outer - seg, y + h - i - 1, seg, 1, col);
		}
	}

	/** A horizontal bar: track, then a fill of {@code frac} inset by {@code inset}. */
	public void bar(float x, float y, float w, float h, float frac, int track, int fill, float inset) {
		this.round(x, y, w, h, h / 2, track);
		float iw = (w - 2 * inset) * Math.max(0, Math.min(1, frac));
		if (iw > 0.5F) {
			this.round(x + inset, y + inset, iw, h - 2 * inset, (h - 2 * inset) / 2, fill);
		}
	}

	/** Scale (in design units) for text whose capitals are about {@code size} design px tall, snapped to whole screen pixels. */
	public float textScale(float size) {
		float screenPerFontPx = size / 7F * this.k * this.gs;

		float snapped = Math.max(1, Math.round(screenPerFontPx));
		return snapped / (this.k * this.gs);
	}

	public float width(String s, float size) {
		return this.font.width(s) * this.textScale(size);
	}

	public float width(Component c, float size) {
		return this.font.width(c) * this.textScale(size);
	}

	/** Text with its top-left at (x, y); returns its width. */
	public float text(String s, float x, float y, int col, float size) {
		return this.text(Component.literal(s), x, y, col, size);
	}

	public float text(Component c, float x, float y, int col, float size) {
		if ((col >>> 24) < 8) {
			return this.width(c, size);
		}
		float t = this.textScale(size);
		this.g.pose().pushMatrix();
		this.g.pose().translate(x, y);
		this.g.pose().scale(t, t);
		this.g.text(this.font, c, 0, 0, col, false);
		this.g.pose().popMatrix();
		return this.font.width(c) * t;
	}

	public float bold(String s, float x, float y, int col, float size) {
		return this.text(Component.literal(s).withStyle(Style.EMPTY.withBold(true)), x, y, col, size);
	}

	public void centred(String s, float x, float y, int col, float size) {
		this.text(s, x - this.width(s, size) / 2, y, col, size);
	}

	public void right(String s, float x, float y, int col, float size) {
		this.text(s, x - this.width(s, size), y, col, size);
	}

	/** A ring of {@code segments} short strokes (rotated fills): the parry ring, the cue ring. */
	public void ring(float cx, float cy, float r, float thick, int col, float fromTurn, float toTurn, int segments, boolean dashed) {
		float len = (float) (2 * Math.PI * r / segments) + 0.8F;
		for (int i = 0; i < segments; i++) {
			if (dashed && (i & 1) == 1) {
				continue;
			}
			float turn = (i + 0.5F) / segments;
			if (turn < fromTurn || turn > toTurn) {
				continue;
			}
			double a = turn * 2 * Math.PI - Math.PI / 2;
			this.g.pose().pushMatrix();
			this.g.pose().translate((float) (cx + Math.cos(a) * r), (float) (cy + Math.sin(a) * r));
			this.g.pose().rotate((float) a);
			this.g.fill(Math.round(-thick / 2), Math.round(-len / 2), Math.max(1, Math.round(thick / 2)), Math.round(len / 2), col);
			this.g.pose().popMatrix();
		}
	}

	/** A full ring of radius r (the baked ring is about 2 px thick at r 32; the dashed one 1 px at r 84). */
	public void fullRing(float cx, float cy, float r, boolean dashed, int col) {
		this.blit(dashed ? RING_DASHED : RING, cx - r, cy - r, 2 * r, 2 * r, col);
	}

	/** A filled diamond (a square turned 45°) centred at (x, y), {@code size} across. */
	public void diamond(float x, float y, float size, int col) {
		this.blit(DIAMOND, x - size / 2, y - size / 2, size, size, col);
	}

	public void diamondFills(float x, float y, float size, int col) {
		this.g.pose().pushMatrix();
		this.g.pose().translate(x, y);
		this.g.pose().rotate((float) (Math.PI / 4));
		float s = size / 1.4142F / 2;
		this.g.fill(Math.round(-s), Math.round(-s), Math.round(s), Math.round(s), col);
		this.g.pose().popMatrix();
	}

	/** A diamond outline (about 2 px). */
	public void diamondOutline(float x, float y, float size, float t, int col) {
		this.blit(DIAMOND_OUTLINE, x - size / 2, y - size / 2, size, size, col);
	}

	public void diamondOutlineFills(float x, float y, float size, float t, int col) {
		this.g.pose().pushMatrix();
		this.g.pose().translate(x, y);
		this.g.pose().rotate((float) (Math.PI / 4));
		int s = Math.round(size / 1.4142F / 2);
		int ti = Math.max(1, Math.round(t));
		this.g.fill(-s, -s, s, -s + ti, col);
		this.g.fill(-s, s - ti, s, s, col);
		this.g.fill(-s, -s + ti, -s + ti, s - ti, col);
		this.g.fill(s - ti, -s + ti, s, s - ti, col);
		this.g.pose().popMatrix();
	}

	/** A filled triangle pointing up, centred at (x, y). */
	public void triangle(float x, float y, float size, int col) {
		this.blit(TRIANGLE, x - size / 2, y - size * 0.44F, size, size * 0.875F, col);
	}

	public void triangleFills(float x, float y, float size, int col) {
		int h = Math.round(size * 0.87F);
		for (int i = 0; i < h; i++) {
			float w = size * (i + 1) / h;
			this.rect(x - w / 2, y - h / 2F + i, w, 1, col);
		}
	}

	/** A disc. */
	public void disc(float x, float y, float size, int col) {
		this.blit(DISC, x - size / 2, y - size / 2, size, size, col);
	}

	public static int lerpColour(float f, int a, int b) {
		return ARGB.srgbLerp(Math.max(0, Math.min(1, f)), a, b);
	}
}
