package dev.voidmc.hub.layout;

/**
 * An axis-aligned rectangle in physical (framebuffer) pixels. Integer on purpose: every edge the
 * page draws and every edge Java hit-tests is snapped to the same device pixel.
 */
public record Rect(int x, int y, int w, int h) {

	public int right() {
		return this.x + this.w;
	}

	public int bottom() {
		return this.y + this.h;
	}

	public boolean contains(double px, double py) {
		return px >= this.x && px < this.x + this.w && py >= this.y && py < this.y + this.h;
	}

	public boolean intersects(Rect o) {
		return this.x < o.right() && o.x < this.right() && this.y < o.bottom() && o.y < this.bottom();
	}

	public boolean containsRect(Rect o) {
		return o.x >= this.x && o.y >= this.y && o.right() <= this.right() && o.bottom() <= this.bottom();
	}

	public double centerX() {
		return this.x + this.w / 2.0;
	}

	public double centerY() {
		return this.y + this.h / 2.0;
	}

	/**
	 * Design units (CSS px in the page) at {@code origin + d * u}, snapped to device pixels. The
	 * size is rounded on its own so every cell of a grid is the same width; a 1 px difference
	 * lands in the gaps instead.
	 */
	static Rect design(double originX, double originY, double dx, double dy, double dw, double dh, double u) {
		int x0 = (int) Math.round(originX + dx * u);
		int y0 = (int) Math.round(originY + dy * u);
		return new Rect(x0, y0, (int) Math.round(dw * u), (int) Math.round(dh * u));
	}
}
