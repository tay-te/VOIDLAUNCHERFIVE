package dev.voidmc.atlas.client.ui;

/**
 * The colour-blind-safe pin heads (the design's 355:14556): every waypoint colour has its own shape, so a waypoint
 * reads the same with no colour at all. White a circle, Sage a square, Blue a diamond, Lilac a triangle, Rose a
 * hexagon, Clay a plus, Amber a shield, Grey a pill. With the setting off, every head is a circle.
 */
public final class PinShapes {
	public static final String[] NAMES = {"Circle", "Square", "Diamond", "Triangle", "Hexagon", "Plus", "Shield", "Pill"};

	private PinShapes() {
	}

	/** Draws a head of {@code size} (its height) centred on (cx, cy): the shape for {@code colour}, or a circle. */
	public static void head(Ui ui, float cx, float cy, float size, int colour, boolean shapes, int argb) {
		float r = size / 2;
		if (!shapes) {
			ui.circle(cx, cy, r, argb);
			return;
		}
		switch (Math.floorMod(colour, 8)) {
			case 1 -> ui.round(cx - r * 0.92F, cy - r * 0.92F, r * 1.84F, r * 1.84F, r * 0.32F, argb);
			case 2 -> ui.convex(new float[]{cx, cy - r * 1.12F, cx + r * 1.12F, cy, cx, cy + r * 1.12F, cx - r * 1.12F, cy}, argb);
			case 3 -> ui.convex(new float[]{cx, cy - r * 1.1F, cx + r * 1.1F, cy + r * 0.85F, cx - r * 1.1F, cy + r * 0.85F}, argb);
			case 4 -> {
				float[] p = new float[12];
				for (int i = 0; i < 6; i++) {
					double a = Math.PI / 6 + i * Math.PI / 3;
					p[i * 2] = cx + (float) Math.cos(a) * r * 1.05F;
					p[i * 2 + 1] = cy + (float) Math.sin(a) * r * 1.05F;
				}
				ui.convex(p, argb);
			}
			case 5 -> {
				float t = r * 0.62F;
				ui.round(cx - t, cy - r, t * 2, r * 2, t * 0.4F, argb);
				ui.round(cx - r, cy - t, r * 2, t * 2, t * 0.4F, argb);
			}
			case 6 -> ui.convex(new float[]{cx - r, cy - r * 0.95F, cx + r, cy - r * 0.95F, cx + r, cy + r * 0.15F, cx, cy + r * 1.05F, cx - r, cy + r * 0.15F},
				argb);
			case 7 -> ui.round(cx - r * 1.3F, cy - r * 0.72F, r * 2.6F, r * 1.44F, r * 0.72F, argb);
			default -> ui.circle(cx, cy, r, argb);
		}
	}

	/** A pin: the head over a short stem, the stem's foot at (x, foot). The head's centre is {@code size * 0.9} up. */
	public static void pin(Ui ui, float x, float foot, float size, int colour, boolean shapes, int rgb, float alpha) {
		int argb = Theme.alpha(0xFF000000 | rgb, alpha);
		float cy = foot - size * 0.95F;
		ui.round(x - size * 0.07F, cy, size * 0.14F, foot - cy, size * 0.07F, argb);
		head(ui, x, cy, size, colour, shapes, argb);
		ui.round(x - size * 0.16F, cy - size * 0.16F, size * 0.32F, size * 0.32F, size * 0.09F, Theme.alpha(Theme.SHELL, alpha));
	}
}
