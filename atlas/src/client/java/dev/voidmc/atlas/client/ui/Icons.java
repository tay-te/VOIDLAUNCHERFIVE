package dev.voidmc.atlas.client.ui;

/** The design's line icons, drawn as anti-aliased strokes (16 px boxes unless noted). */
public final class Icons {
	private Icons() {
	}

	/** A magnifier in a 15 px box. */
	public static void search(Ui ui, float x, float y, int argb) {
		float s = 15 / 15F;
		ui.ring(x + 6.74F * s, y + 6.74F * s, 6.1F * s, 1.48F, argb);
		ui.line(x + 11.07F * s, y + 11.07F * s, x + 14.3F * s, y + 14.3F * s, 1.48F, argb);
	}

	/** A plus in a box of {@code size} (16 for zoom, 12 for "new"). */
	public static void plus(Ui ui, float x, float y, float size, float width, int argb) {
		float k = size / 16F;
		ui.line(x + 8 * k, y + 3.5F * k, x + 8 * k, y + 12.5F * k, width, argb);
		ui.line(x + 3.5F * k, y + 8 * k, x + 12.5F * k, y + 8 * k, width, argb);
	}

	public static void minus(Ui ui, float x, float y, float size, float width, int argb) {
		float k = size / 16F;
		ui.line(x + 3.5F * k, y + 8 * k, x + 12.5F * k, y + 8 * k, width, argb);
	}

	/** A cross in a 12 px box. */
	public static void close(Ui ui, float x, float y, int argb) {
		ui.line(x + 3.5F, y + 3.5F, x + 8.5F, y + 8.5F, 1.1F, argb);
		ui.line(x + 8.5F, y + 3.5F, x + 3.5F, y + 8.5F, 1.1F, argb);
	}

	/** A map pin in a 16 px box. */
	public static void pin(Ui ui, float x, float y, int argb) {
		ui.ring(x + 8, y + 6.9F, 4.6F, 1.17F, argb);
		ui.line(x + 4.3F, y + 9.6F, x + 8, y + 14.2F, 1.17F, argb);
		ui.line(x + 11.7F, y + 9.6F, x + 8, y + 14.2F, 1.17F, argb);
		ui.round(x + 6.5F, y + 5.4F, 3, 3, 1, argb);
	}

	/** A gear (settings) in a 16 px box: a ring with eight square teeth. */
	public static void gear(Ui ui, float x, float y, int argb) {
		float cx = x + 8, cy = y + 8;
		ui.ring(cx, cy, 4.6F, 1.3F, argb);
		ui.ring(cx, cy, 1.9F, 1.2F, argb);
		for (int i = 0; i < 8; i++) {
			double a = i * Math.PI / 4;
			float c = (float) Math.cos(a), s = (float) Math.sin(a);
			ui.line(cx + c * 5.2F, cy + s * 5.2F, cx + c * 6.9F, cy + s * 6.9F, 1.8F, argb);
		}
	}

	/** A chevron pointing down, 10 px. */
	public static void chevron(Ui ui, float x, float y, int argb) {
		ui.line(x + 2, y + 3.5F, x + 5, y + 6.5F, 1.2F, argb);
		ui.line(x + 8, y + 3.5F, x + 5, y + 6.5F, 1.2F, argb);
	}
}
