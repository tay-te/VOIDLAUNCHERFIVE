package dev.voidmc.atlas.client.ui;

import dev.voidmc.atlas.api.Waypoint;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntConsumer;

/**
 * The form pieces the settings window and the quick-add dialog share, as the Figma components draw them (section
 * "Components", node 355:14559): the settings row, the cell-grip toggle, the segmented control, the steps, keycaps,
 * buttons, caps labels and the shape-coded waypoint pin. One kit per frame: it draws with {@code ui} and records what
 * answers clicks in {@code hits} (later entries sit on top).
 */
final class FormKit {
	/** A settings row's height. */
	static final float ROW_H = 56;
	/** A segmented control's height. */
	static final float SEG_H = 40;

	record Hit(float x, float y, float w, float h, Runnable action) {
		boolean contains(float px, float py) {
			return px >= this.x && px <= this.x + this.w && py >= this.y && py <= this.y + this.h;
		}
	}

	final Ui ui;
	private final List<Hit> hits;
	private final float mx;
	private final float my;

	FormKit(Ui ui, List<Hit> hits, float mx, float my) {
		this.ui = ui;
		this.hits = hits;
		this.mx = mx;
		this.my = my;
	}

	/** Runs the topmost hit under a point; false if there is none. */
	static boolean click(List<Hit> hits, float x, float y) {
		for (int i = hits.size() - 1; i >= 0; i--) {
			Hit h = hits.get(i);
			if (h.contains(x, y)) {
				h.action.run();
				return true;
			}
		}
		return false;
	}

	boolean over(float x, float y, float w, float h) {
		return this.mx >= x && this.mx <= x + w && this.my >= y && this.my <= y + h;
	}

	void hit(float x, float y, float w, float h, Runnable action) {
		this.hits.add(new Hit(x, y, w, h, action));
	}

	/** A caps label: Outfit 8.5, tracked 1, the faintest ink. */
	void caps(String text, float x, float y) {
		this.ui.text(text, x, y, Ui.Font.F85, 1, Theme.INK_4);
	}

	// --- keycaps ---------------------------------------------------------------------------------------------------

	float keycapWidth(String key) {
		return this.ui.width(key, Ui.Font.F12) + 18;
	}

	/** The large keycap (25 high): a 6% white fill, a 10% hairline, the key in 12 px. */
	void keycap(String key, float x, float y) {
		float w = this.keycapWidth(key);
		this.ui.panel(x, y, w, 25, 7, 0x0FFFFFFF, Theme.HAIR_10);
		this.ui.text(key, x + 9, y + 5, Ui.Font.F12, Theme.INK_2);
	}

	float keycapSmallWidth(String key) {
		return this.ui.width(key, Ui.Font.F85, 0.5F) + 14;
	}

	/** The small keycap (17 high) inside buttons. */
	void keycapSmall(String key, float x, float y) {
		float w = this.keycapSmallWidth(key);
		this.ui.panel(x, y, w, 17, 5, 0x8C0B0B0C, Theme.HAIR_10);
		this.ui.text(key, x + 7, y + 3, Ui.Font.F85, 0.5F, Theme.INK_4);
	}

	// --- toggle ----------------------------------------------------------------------------------------------------

	/** The cell-grip toggle (32 x 18), at an opacity (0.4 when the server forbids what it switches). */
	void toggle(float x, float y, boolean on, float alpha) {
		Ui ui = this.ui;
		if (on) {
			ui.round(x, y, 32, 18, 6, Theme.alpha(Theme.ink(0.88F), alpha));
			ui.round(x + 17, y + 3, 12, 12, 4, Theme.alpha(Theme.SHELL, alpha));
			for (int i = 0; i < 3; i++) {
				ui.round(x + 18.5F + i * 3.5F, y + 8, 2, 2, 1, Theme.alpha(Theme.ink(0.3F), alpha));
			}
		} else {
			ui.panel(x, y, 32, 18, 6, Theme.alpha(Theme.WELL, alpha), Theme.alpha(Theme.HAIR_16, alpha));
			ui.round(x + 3, y + 3, 12, 12, 4, Theme.alpha(Theme.INK_4, alpha));
			for (int i = 0; i < 3; i++) {
				ui.round(x + 4.5F + i * 3.5F, y + 8, 2, 2, 1, Theme.alpha(0x4D0B0B0C, alpha));
			}
		}
	}

	// --- settings row ----------------------------------------------------------------------------------------------

	/** The settings row's box (56 high, radius 14, 7% hairline) with its label and dim hint on the left. */
	void row(float x, float y, float w, int fill, String label, String hint) {
		this.ui.panel(x, y, w, ROW_H, 14, fill, Theme.HAIR_7);
		this.ui.text(label, x + 17, y + 10.5F, Ui.Font.F13, Theme.INK);
		this.ui.text(hint, x + 17, y + 29.5F, Ui.Font.F105, Theme.INK_3);
	}

	/** A row with a toggle on the right; the whole row flips it (unless {@code enabled} is false). */
	void toggleRow(float x, float y, float w, int fill, String label, String hint, boolean on, boolean enabled, Runnable flip) {
		this.row(x, y, w, fill, label, hint);
		this.toggle(x + w - 13 - 32, y + 19, on, enabled ? 1 : 0.4F);
		if (enabled) {
			this.hit(x, y, w, ROW_H, flip);
		}
	}

	/** A row with a segmented control on the right, sized to its labels. */
	<T> void choiceRow(float x, float y, float w, int fill, String label, String hint, T[] values, Function<T, String> names, T current, Consumer<T> set) {
		this.row(x, y, w, fill, label, hint);
		float sw = this.segmentedWidth(values, names);
		this.segmented(x + w - 13 - sw, y + 8, sw, false, values, names, current, set);
	}

	/**
	 * A row with steps on the right: {@code count} cells, the chosen one bright, those before it half, those after
	 * faint, and the chosen value in words. Clicking a cell picks it; clicking the row steps on (wrapping).
	 */
	void stepsRow(float x, float y, float w, int fill, String label, String hint, String[] values, int selected, IntConsumer set) {
		this.row(x, y, w, fill, label, hint);
		int count = values.length;
		float slot = 0;
		for (String v : values) {
			slot = Math.max(slot, this.ui.width(v, Ui.Font.F11));
		}
		this.hit(x, y, w, ROW_H, () -> set.accept((selected + 1) % count));
		float sx = x + w - 13 - slot - 4 - (count * 14 + (count - 1) * 4);
		for (int i = 0; i < count; i++) {
			float cx = sx + i * 18;
			float a = i < selected ? 0.42F : i == selected ? 0.85F : 0.12F;
			if (i != selected && this.over(cx - 2, y + 8, 18, 40)) {
				a = Math.max(a, 0.6F);
			}
			this.ui.round(cx, y + 24, 14, 8, 3, Theme.ink(a));
			int index = i;
			this.hit(cx - 2, y + 8, 18, 40, () -> set.accept(index));
		}
		if (selected >= 0 && selected < count) {
			this.ui.text(values[selected], x + w - 13 - slot, y + 21, Ui.Font.F11, Theme.INK_2);
		}
	}

	/** A row with a small button on the right; the button and the row both run it. */
	void buttonRow(float x, float y, float w, int fill, String label, String hint, String button, Runnable action) {
		this.row(x, y, w, fill, label, hint);
		float bw = this.ui.width(button, Ui.Font.F12) + 28;
		float bx = x + w - 13 - bw;
		boolean hover = this.over(x, y, w, ROW_H);
		this.ui.panel(bx, y + 13, bw, 30, 15, hover ? 0xFF2A2B2F : Theme.BUTTON, Theme.HAIR_8);
		this.ui.text(button, bx + 14, y + 20.5F, Ui.Font.F12, Theme.INK);
		this.hit(x, y, w, ROW_H, action);
	}

	// --- segmented -------------------------------------------------------------------------------------------------

	<T> float segmentedWidth(T[] values, Function<T, String> names) {
		float w = 8 + (values.length - 1) * 2;
		for (T v : values) {
			w += this.ui.width(names.apply(v), Ui.Font.F13) + 28;
		}
		return w;
	}

	/**
	 * The segmented control (40 high): a well with a 6% hairline, the chosen segment raised with an 8% hairline, the
	 * others in the second ink. {@code equal}: the segments share the width; otherwise each hugs its label.
	 */
	<T> void segmented(float x, float y, float w, boolean equal, T[] values, Function<T, String> names, T current, Consumer<T> set) {
		Ui ui = this.ui;
		ui.panel(x, y, w, SEG_H, 12, Theme.WELL, Theme.HAIR_6);
		int n = values.length;
		float equalW = (w - 8 - (n - 1) * 2) / n;
		float sx = x + 4;
		for (int i = 0; i < n; i++) {
			T v = values[i];
			String label = names.apply(v);
			float sw = equal ? equalW : ui.width(label, Ui.Font.F13) + 28;
			boolean on = v.equals(current);
			if (on) {
				ui.panel(sx, y + 4, sw, 32, 10, Theme.RAISED, Theme.HAIR_8);
			} else if (this.over(sx, y + 4, sw, 32)) {
				ui.round(sx, y + 4, sw, 32, 10, Theme.ISLAND);
			}
			ui.text(ui.fit(label, Ui.Font.F13, sw - 20), sx + 14, y + 12, Ui.Font.F13, on ? Theme.INK : Theme.INK_2);
			this.hit(sx, y + 4, sw, 32, () -> set.accept(v));
			sx += sw + 2;
		}
	}

	// --- buttons ---------------------------------------------------------------------------------------------------

	/** The dialog button (37 high, radius 20): primary raised, secondary in a well; its label then a small keycap. */
	void button(float x, float y, float w, boolean primary, String label, String key, Runnable action) {
		Ui ui = this.ui;
		boolean hover = this.over(x, y, w, 37);
		if (primary) {
			ui.panel(x, y, w, 37, 20, hover ? 0xFF2C2D31 : Theme.RAISED, Theme.HAIR_10);
		} else {
			ui.panel(x, y, w, 37, 20, hover ? Theme.BUTTON : Theme.WELL, Theme.HAIR_7);
		}
		float tw = ui.width(label, Ui.Font.F13);
		float content = tw + 10 + this.keycapSmallWidth(key);
		float cx = x + 16 + (w - 28 - content) / 2;
		ui.text(label, cx, y + 10.5F, Ui.Font.F13, primary ? Theme.INK : Theme.INK_2);
		this.keycapSmall(key, cx + tw + 10, y + 10);
		this.hit(x, y, w, 37, action);
	}

	// --- the waypoint pin ------------------------------------------------------------------------------------------

	/**
	 * The design's {@code atlas/pin} (20 x 30): a head in the waypoint's colour, shaped by colour when {@code shapes}
	 * is on (white circle, sage square, blue diamond, lilac triangle, rose hexagon, clay plus, amber shield, grey pill;
	 * a circle otherwise), a dark eye and a stem.
	 */
	static void pin(Ui ui, float x, float y, int colour, boolean shapes) {
		int c = Math.floorMod(colour, Waypoint.COLOURS.length);
		int argb = 0xFF000000 | Waypoint.COLOURS[c];
		switch (shapes ? c : 0) {
			case 1 -> ui.round(x + 1, y + 1, 18, 18, 5, argb);
			case 2 -> ui.convex(new float[]{x + 10, y + 0.3F, x + 19.7F, y + 10, x + 10, y + 19.7F, x + 0.3F, y + 10}, argb);
			case 3 -> ui.convex(new float[]{x + 10, y + 0.9F, x + 19.4F, y + 18.3F, x + 0.6F, y + 18.3F}, argb);
			case 4 -> ui.convex(new float[]{x + 10, y + 0.4F, x + 18.5F, y + 5.2F, x + 18.5F, y + 14.8F, x + 10, y + 19.6F, x + 1.5F, y + 14.8F,
				x + 1.5F, y + 5.2F}, argb);
			case 5 -> {
				ui.round(x + 6.2F, y + 0.7F, 7.6F, 18.6F, 1, argb);
				ui.round(x + 0.7F, y + 6.2F, 18.6F, 7.6F, 1, argb);
			}
			case 6 -> ui.convex(new float[]{x + 1.8F, y + 1.8F, x + 18.2F, y + 1.8F, x + 18.2F, y + 10, x + 17.5F, y + 13.4F, x + 15.7F, y + 16,
				x + 10, y + 19.5F, x + 4.3F, y + 16, x + 2.5F, y + 13.4F, x + 1.8F, y + 10}, argb);
			case 7 -> ui.round(x, y + 4, 20, 12, 6, argb);
			default -> ui.circle(x + 10, y + 10, 9.5F, argb);
		}
		ui.round(x + 7, y + 7, 6, 6, 2, Theme.SHELL);
		ui.round(x + 9, y + 21, 2, 8, 1, Theme.alpha(argb, 0.92F));
	}
}
