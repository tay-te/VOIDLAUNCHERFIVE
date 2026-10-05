package dev.voidmc.atlas.client.ui;

import dev.voidmc.atlas.api.MarkerGlyph;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Matrix3x2f;

/**
 * Atlas's drawing kit. Everything is laid out in <em>design pixels</em>, the Figma file's units; {@link #unit} is how
 * many physical pixels one design pixel is (0.75 .. 3, from a fixed set, so the Outfit fonts, rasterised at exactly
 * that scale, land pixel for pixel on the screen). Shapes are anti-aliased with a one-physical-pixel feather and
 * batched into one {@link Mesh} until text or a texture needs to go on top.
 *
 * <p>Flat by rule: fills and hairlines only, every surface rounded. No gradients, shadows or blur are offered.
 */
public final class Ui {
	/** The scales Atlas draws at; each has its own rasterisation of every font size. */
	public static final float[] UNITS = {0.75F, 1.0F, 1.25F, 1.5F, 1.75F, 2.0F, 2.5F, 3.0F};
	private static final String[] UNIT_KEYS = {"75", "100", "125", "150", "175", "200", "250", "300"};

	/** Outfit in the design's sizes: Medium ({@code F}), and Regular ({@code R}) for the names of water. */
	public enum Font {
		F85(8.5F, "o85"), F9(9, "o9"), F95(9.5F, "o95"), F10(10, "o10"), F105(10.5F, "o105"), F11(11, "o11"), F115(11.5F, "o115"), F12(12, "o12"),
		F13(13, "o13"), F15(15, "o15"), F16(16, "o16"), F17(17, "o17"), F18(18, "o18"), F20(20, "o20"), F24(24, "o24"), R11(11, "r11"), R13(13, "r13");

		public final float size;
		final String key;
		final Style[] styles = new Style[UNITS.length];

		Font(float size, String key) {
			this.size = size;
			this.key = key;
			for (int i = 0; i < UNITS.length; i++) {
				this.styles[i] = Style.EMPTY.withFont(new FontDescription.Resource(Identifier.fromNamespaceAndPath("atlas", key + "_" + UNIT_KEYS[i])));
			}
		}
	}

	public final Minecraft mc = Minecraft.getInstance();
	public final GuiGraphicsExtractor g;
	/** Physical pixels per design pixel. */
	public final float unit;
	private final int unitIndex;
	/** GUI units per design pixel (the pose scale). */
	public final float scale;
	private final MeshBuilder shapes = new MeshBuilder(false);
	private final float feather;
	private final float hairline;
	private final float[] contour = new float[256];
	private final float[] contour2 = new float[256];

	public Ui(GuiGraphicsExtractor g, float unit) {
		this.g = g;
		this.unitIndex = indexOf(unit);
		this.unit = UNITS[this.unitIndex];
		int guiScale = Math.max(1, (int) Math.round(this.mc.getWindow().getGuiScale()));
		this.scale = this.unit / guiScale;
		this.feather = 0.5F / this.unit;
		this.hairline = Math.max(1, Math.round(this.unit)) / this.unit;
	}

	/** The nearest scale Atlas draws at to {@code unit}, rounding down. */
	public static float pick(float unit) {
		return UNITS[indexOf(unit)];
	}

	private static int indexOf(float unit) {
		int best = 0;
		for (int i = 0; i < UNITS.length; i++) {
			if (UNITS[i] <= unit + 1e-4F) {
				best = i;
			}
		}
		return best;
	}

	/** The largest scale at which {@code w x h} design pixels fit a window of {@code windowW x windowH} physical pixels. */
	public static float fit(int windowW, int windowH, float w, float h) {
		return pick(Math.min(windowW / w, windowH / h));
	}

	public void begin() {
		this.g.pose().pushMatrix();
		this.g.pose().scale(this.scale, this.scale);
	}

	public void end() {
		this.flush();
		this.g.pose().popMatrix();
	}

	/** One physical pixel, in design pixels. */
	public float px() {
		return 1.0F / this.unit;
	}

	public float hairline() {
		return this.hairline;
	}

	/** Rounds a design coordinate to the nearest physical pixel. */
	public float snap(float v) {
		return Math.round(v * this.unit) / this.unit;
	}

	/** A GUI coordinate (mouse positions) in design pixels. */
	public float design(double gui) {
		return (float) (gui / this.scale);
	}

	/** The screen's size in design pixels. */
	public float width() {
		return this.mc.getWindow().getWidth() / this.unit;
	}

	public float height() {
		return this.mc.getWindow().getHeight() / this.unit;
	}

	// --- batching ------------------------------------------------------------------------------------------------

	/** Submits the shapes so far as one element; what follows draws above them. */
	public void flush() {
		if (this.shapes.isEmpty()) {
			return;
		}
		this.g.guiRenderState.addGuiElement(new Mesh(RenderPipelines.GUI, TextureSetup.noTexture(), new Matrix3x2f(this.g.pose()),
			this.shapes.xyCopy(), null, this.shapes.coloursCopy(), this.shapes.count(), this.g.scissorStack.peek()));
		this.shapes.clear();
	}

	/** Submits a textured mesh (map tiles), above everything drawn so far. */
	public void submit(TextureSetup textures, MeshBuilder mesh) {
		if (mesh.isEmpty()) {
			return;
		}
		this.flush();
		this.g.guiRenderState.addGuiElement(new Mesh(RenderPipelines.GUI_TEXTURED, textures, new Matrix3x2f(this.g.pose()), mesh.xyCopy(),
			mesh.uvCopy(), mesh.coloursCopy(), mesh.count(), this.g.scissorStack.peek()));
	}

	public MeshBuilder shapes() {
		return this.shapes;
	}

	// --- shapes --------------------------------------------------------------------------------------------------

	public void rect(float x, float y, float w, float h, int argb) {
		if ((argb >>> 24) == 0 || w <= 0 || h <= 0) {
			return;
		}
		this.shapes.rect(x, y, x + w, y + h, argb);
	}

	/** A cell snapped to whole physical pixels: glyph cells, dither, pixel art. */
	public void cell(float x, float y, float w, float h, int argb) {
		float x0 = this.snap(x);
		float y0 = this.snap(y);
		float x1 = Math.max(x0 + this.px(), this.snap(x + w));
		float y1 = Math.max(y0 + this.px(), this.snap(y + h));
		this.shapes.rect(x0, y0, x1, y1, argb);
	}

	private int segments(float r) {
		return Math.max(2, Math.min(14, Math.round(r * this.unit / 1.6F)));
	}

	/**
	 * The outline of a rounded rectangle inset by {@code inset} (negative: outset), clockwise from the top-left
	 * corner's left end; {@code n + 1} points per corner. Returns the number of points.
	 */
	private int contour(float[] out, float x, float y, float w, float h, float r, float inset, int n) {
		float rr = Math.max(0, r - inset);
		float x0 = x + inset, y0 = y + inset, x1 = x + w - inset, y1 = y + h - inset;
		float[] cx = {x0 + rr, x1 - rr, x1 - rr, x0 + rr};
		float[] cy = {y0 + rr, y0 + rr, y1 - rr, y1 - rr};
		int k = 0;
		for (int c = 0; c < 4; c++) {
			double start = Math.PI + c * Math.PI / 2;
			for (int i = 0; i <= n; i++) {
				double a = start + i * (Math.PI / 2) / n;
				out[k++] = cx[c] + (float) Math.cos(a) * rr;
				out[k++] = cy[c] + (float) Math.sin(a) * rr;
			}
		}
		return k / 2;
	}

	/** A filled rounded rectangle with an anti-aliased edge. */
	public void round(float x, float y, float w, float h, float r, int argb) {
		if ((argb >>> 24) == 0 || w <= 0 || h <= 0) {
			return;
		}
		r = Math.min(r, Math.min(w, h) / 2);
		int n = this.segments(r);
		float f = this.feather;
		int count = this.contour(this.contour, x, y, w, h, r, f, n);
		this.contour(this.contour2, x, y, w, h, r, -f, n);
		float mx = x + w / 2, my = y + h / 2;
		int clear = argb & 0xFFFFFF;
		float[] in = this.contour;
		float[] out = this.contour2;
		for (int i = 0; i < count; i++) {
			int j = (i + 1) % count;
			this.shapes.triangle(mx, my, argb, in[i * 2], in[i * 2 + 1], argb, in[j * 2], in[j * 2 + 1], argb);
			this.shapes.quad(in[i * 2], in[i * 2 + 1], argb, out[i * 2], out[i * 2 + 1], clear, out[j * 2], out[j * 2 + 1], clear, in[j * 2], in[j * 2 + 1], argb);
		}
	}

	/** A rounded ring just inside the rectangle's edge: a border {@code width} wide (hairline if 0). */
	public void border(float x, float y, float w, float h, float r, float width, int argb) {
		if ((argb >>> 24) == 0 || w <= 0 || h <= 0) {
			return;
		}
		if (width <= 0) {
			width = this.hairline;
		}
		r = Math.min(r, Math.min(w, h) / 2);
		int n = this.segments(r);
		float f = this.feather;
		int clear = argb & 0xFFFFFF;
		// outer feather, solid band, inner feather
		this.band(x, y, w, h, r, -f, 0, clear, argb, n);
		this.band(x, y, w, h, r, 0, width, argb, argb, n);
		this.band(x, y, w, h, r, width, width + f, argb, clear, n);
	}

	private void band(float x, float y, float w, float h, float r, float insetOut, float insetIn, int cOut, int cIn, int n) {
		int count = this.contour(this.contour, x, y, w, h, r, insetOut, n);
		this.contour(this.contour2, x, y, w, h, r, insetIn, n);
		float[] a = this.contour;
		float[] b = this.contour2;
		for (int i = 0; i < count; i++) {
			int j = (i + 1) % count;
			this.shapes.quad(a[i * 2], a[i * 2 + 1], cOut, b[i * 2], b[i * 2 + 1], cIn, b[j * 2], b[j * 2 + 1], cIn, a[j * 2], a[j * 2 + 1], cOut);
		}
	}

	/** A card or chip: a rounded fill with a hairline border on it. */
	public void panel(float x, float y, float w, float h, float r, int fill, int border) {
		this.round(x, y, w, h, r, fill);
		this.border(x, y, w, h, r, 0, border);
	}

	public void circle(float cx, float cy, float r, int argb) {
		this.round(cx - r, cy - r, r * 2, r * 2, r, argb);
	}

	public void ring(float cx, float cy, float r, float width, int argb) {
		this.border(cx - r, cy - r, r * 2, r * 2, r, width, argb);
	}

	/** A straight stroke with round-looking ends (an icon's line), anti-aliased. */
	public void line(float x0, float y0, float x1, float y1, float width, int argb) {
		float dx = x1 - x0, dy = y1 - y0;
		float len = (float) Math.hypot(dx, dy);
		if (len < 1e-4F) {
			return;
		}
		float ux = dx / len, uy = dy / len;
		float nx = -uy, ny = ux;
		float hw = width / 2;
		float f = this.feather;
		// extend by half the width for the caps
		float ax = x0 - ux * hw * 0.6F, ay = y0 - uy * hw * 0.6F, bx = x1 + ux * hw * 0.6F, by = y1 + uy * hw * 0.6F;
		int clear = argb & 0xFFFFFF;
		float i = Math.max(0, hw - f), o = hw + f;
		MeshBuilder s = this.shapes;
		s.quad(ax + nx * i, ay + ny * i, argb, bx + nx * i, by + ny * i, argb, bx - nx * i, by - ny * i, argb, ax - nx * i, ay - ny * i, argb);
		s.quad(ax + nx * o, ay + ny * o, clear, bx + nx * o, by + ny * o, clear, bx + nx * i, by + ny * i, argb, ax + nx * i, ay + ny * i, argb);
		s.quad(ax - nx * i, ay - ny * i, argb, bx - nx * i, by - ny * i, argb, bx - nx * o, by - ny * o, clear, ax - nx * o, ay - ny * o, clear);
	}

	/** A filled convex polygon (points x0, y0, x1, y1, ...), with a feathered edge. */
	public void convex(float[] pts, int argb) {
		int n = pts.length / 2;
		float mx = 0, my = 0;
		for (int i = 0; i < n; i++) {
			mx += pts[i * 2];
			my += pts[i * 2 + 1];
		}
		mx /= n;
		my /= n;
		int clear = argb & 0xFFFFFF;
		float f = this.feather;
		for (int i = 0; i < n; i++) {
			int j = (i + 1) % n;
			float ax = pts[i * 2], ay = pts[i * 2 + 1], bx = pts[j * 2], by = pts[j * 2 + 1];
			// pull the solid part in by the feather, along the direction from the middle
			float ai = shrink(ax, mx, ay, my, f), bi = shrink(bx, mx, by, my, f);
			float aix = mx + (ax - mx) * ai, aiy = my + (ay - my) * ai, bix = mx + (bx - mx) * bi, biy = my + (by - my) * bi;
			float ao = 2 - ai, bo = 2 - bi;
			float aox = mx + (ax - mx) * ao, aoy = my + (ay - my) * ao, box = mx + (bx - mx) * bo, boy = my + (by - my) * bo;
			this.shapes.triangle(mx, my, argb, aix, aiy, argb, bix, biy, argb);
			this.shapes.quad(aix, aiy, argb, aox, aoy, clear, box, boy, clear, bix, biy, argb);
		}
	}

	private static float shrink(float x, float mx, float y, float my, float by) {
		float d = (float) Math.hypot(x - mx, y - my);
		return d < 1e-4F ? 1 : Math.max(0, (d - by) / d);
	}

	/**
	 * A 5 x 5 glyph in a {@code size} square, as the design draws them: cells on a pitch of 3.1/16 of the square, 2.8/16
	 * wide, with corners rounded 0.8/16 (square, snapped to physical pixels, when a cell is under three pixels, where a
	 * rounded corner would only blur it); accent cells in {@code accent}.
	 */
	public void glyph(MarkerGlyph glyph, float x, float y, float size, int argb, int accent) {
		float k = size / 16;
		float pitch = 3.1F * k;
		float cellSize = 2.8F * k;
		float r = 0.8F * k;
		boolean round = cellSize * this.unit >= 3;
		for (int cy = 0; cy < 5; cy++) {
			for (int cx = 0; cx < 5; cx++) {
				if (glyph.cell(cx, cy)) {
					int c = glyph.accent(cx, cy) ? accent : argb;
					float gx = x + 0.4F * k + cx * pitch;
					float gy = y + 0.4F * k + cy * pitch;
					if (round) {
						this.round(this.snap(gx), this.snap(gy), cellSize, cellSize, r, c);
					} else {
						this.cell(gx, gy, cellSize, cellSize, c);
					}
				}
			}
		}
	}

	// --- text ----------------------------------------------------------------------------------------------------

	private FormattedCharSequence sequence(String text, Font font) {
		return FormattedCharSequence.forward(text, font.styles[this.unitIndex]);
	}

	public float width(String text, Font font) {
		return this.mc.font.getSplitter().stringWidth(this.sequence(text, font));
	}

	public float width(String text, Font font, float tracking) {
		if (tracking == 0) {
			return this.width(text, font);
		}
		float w = 0;
		for (int i = 0; i < text.length(); ) {
			int cp = text.codePointAt(i);
			int n = Character.charCount(cp);
			w += this.width(text.substring(i, i + n), font) + tracking;
			i += n;
		}
		return Math.max(0, w - tracking);
	}

	/**
	 * Draws text with its line box's top at {@code top} (CSS "normal" line height: the baseline is one em below the
	 * top, as Figma lays Outfit out).
	 */
	public void text(String text, float x, float top, Font font, int argb) {
		if (text == null || text.isEmpty() || (argb >>> 24) == 0) {
			return;
		}
		this.flush();
		// the font puts glyphs' baseline 7 units below the drawing position
		float baseline = top + font.size;
		this.g.pose().pushMatrix();
		this.g.pose().translate(this.snap(x), this.snap(baseline) - 7.0F);
		this.g.text(this.mc.font, this.sequence(text, font), 0, 0, argb, false);
		this.g.pose().popMatrix();
	}

	/** Text with letter spacing (the design's caps labels). */
	public void text(String text, float x, float top, Font font, float tracking, int argb) {
		if (tracking == 0) {
			this.text(text, x, top, font, argb);
			return;
		}
		if (text == null || text.isEmpty() || (argb >>> 24) == 0) {
			return;
		}
		this.flush();
		float baseline = this.snap(top + font.size) - 7.0F;
		float cx = x;
		for (int i = 0; i < text.length(); ) {
			int cp = text.codePointAt(i);
			int n = Character.charCount(cp);
			String ch = text.substring(i, i + n);
			if (cp != ' ') {
				this.g.pose().pushMatrix();
				this.g.pose().translate(this.snap(cx), baseline);
				this.g.text(this.mc.font, this.sequence(ch, font), 0, 0, argb, false);
				this.g.pose().popMatrix();
			}
			cx += this.width(ch, font) + tracking;
			i += n;
		}
	}

	public void textRight(String text, float right, float top, Font font, float tracking, int argb) {
		this.text(text, right - this.width(text, font, tracking), top, font, tracking, argb);
	}

	public void textCentred(String text, float cx, float top, Font font, float tracking, int argb) {
		this.text(text, cx - this.width(text, font, tracking) / 2, top, font, tracking, argb);
	}

	/** The design's line box height for a font size (Outfit, normal line height). */
	public static float lineHeight(Font font) {
		return Math.round(font.size * 1.26F);
	}

	/** Cuts text to fit {@code max} design pixels, with an ellipsis. */
	public String fit(String text, Font font, float max) {
		return this.fit(text, font, 0, max);
	}

	/**
	 * Cuts text drawn with letter spacing {@code tracking} to fit {@code max} design pixels, with an ellipsis. The caps
	 * lines are tracked, so measuring them untracked lets them run into whatever stands to their right.
	 */
	public String fit(String text, Font font, float tracking, float max) {
		if (this.width(text, font, tracking) <= max) {
			return text;
		}
		String dots = "…";
		int end = text.length();
		while (end > 0 && this.width(text.substring(0, end).stripTrailing() + dots, font, tracking) > max) {
			end--;
		}
		return text.substring(0, end).stripTrailing() + dots;
	}
}
