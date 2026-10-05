package dev.voidmc.atlas.client.render;

import dev.voidmc.atlas.client.Perf;
import dev.voidmc.atlas.client.data.LayerData;
import dev.voidmc.atlas.client.ui.MeshBuilder;
import dev.voidmc.atlas.client.ui.Ui;
import dev.voidmc.atlas.core.Coords;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Draws a map view into a rounded rectangle: the tiles of the right mip level, each clipped (on the CPU) to the
 * rounded viewport, with an anti-aliased edge made of the same tiles faded out, so the map is a crisp, rounded,
 * pixel-art picture at any rotation. Where a tile is not ready yet, a coarser one stands in, so zooming never shows
 * holes, only briefly softer land.
 *
 * <p>Zoom is in physical pixels per block: at whole numbers every block is exactly that many pixels.
 */
public final class MapRenderer {
	/** The view: centre (blocks), zoom (physical pixels per block), rotation (radians, clockwise). */
	public record View(double centerX, double centerZ, double pixelsPerBlock, double rotation) {
	}

	/** The transform of one draw, for markers: map to screen and back, in design pixels. */
	public static final class Frame {
		public final float x;
		public final float y;
		public final float w;
		public final float h;
		public final float radius;
		final double cx;
		final double cz;
		/** Design pixels per block. */
		public final double k;
		final double cos;
		final double sin;
		public final View view;

		Frame(float x, float y, float w, float h, float radius, View view, float unit) {
			this.x = x;
			this.y = y;
			this.w = w;
			this.h = h;
			this.radius = radius;
			this.view = view;
			this.cx = view.centerX();
			this.cz = view.centerZ();
			this.k = view.pixelsPerBlock() / unit;
			this.cos = Math.cos(view.rotation());
			this.sin = Math.sin(view.rotation());
		}

		public float screenX(double mx, double mz) {
			double dx = (mx - this.cx) * this.k;
			double dz = (mz - this.cz) * this.k;
			return (float) (this.x + this.w / 2 + dx * this.cos - dz * this.sin);
		}

		public float screenY(double mx, double mz) {
			double dx = (mx - this.cx) * this.k;
			double dz = (mz - this.cz) * this.k;
			return (float) (this.y + this.h / 2 + dx * this.sin + dz * this.cos);
		}

		public double mapX(double sx, double sy) {
			double dx = sx - (this.x + this.w / 2);
			double dy = sy - (this.y + this.h / 2);
			return this.cx + (dx * this.cos + dy * this.sin) / this.k;
		}

		public double mapZ(double sx, double sy) {
			double dx = sx - (this.x + this.w / 2);
			double dy = sy - (this.y + this.h / 2);
			return this.cz + (-dx * this.sin + dy * this.cos) / this.k;
		}

		public boolean contains(float sx, float sy, float margin) {
			return sx >= this.x - margin && sx <= this.x + this.w + margin && sy >= this.y - margin && sy <= this.y + this.h + margin;
		}

		/** Whether a point is inside the rounded viewport. */
		public boolean inside(float sx, float sy) {
			if (sx < this.x || sy < this.y || sx > this.x + this.w || sy > this.y + this.h) {
				return false;
			}
			float r = this.radius;
			float qx = Math.max(Math.max(this.x + r - sx, sx - (this.x + this.w - r)), 0);
			float qy = Math.max(Math.max(this.y + r - sy, sy - (this.y + this.h - r)), 0);
			return qx * qx + qy * qy <= r * r;
		}
	}

	private final Map<TileCache.Page, MeshBuilder> meshes = new IdentityHashMap<>();
	// scratch polygons: x, z, alpha per vertex
	private double[] poly = new double[64 * 3];
	private double[] tmp = new double[64 * 3];
	private int polyCount;
	private final float[] screenContour = new float[512];
	private final float[] screenOuter = new float[512];
	/** The two outlines in map space, worked out once a frame. */
	private final double[] mapContour = new double[512];
	private final double[] mapOuter = new double[512];

	/**
	 * Draws {@code layer} into the rounded rectangle; returns the transform for markers. {@code request}: ask for tiles
	 * that are missing (off for a thumbnail that should not churn the cache).
	 */
	public Frame draw(Ui ui, TileCache tiles, LayerData layer, float x, float y, float w, float h, float radius, View view, boolean request) {
		long t0 = System.nanoTime();
		Frame f = new Frame(x, y, w, h, radius, view, ui.unit);
		if (layer == null) {
			return f;
		}
		int level = Coords.levelFor(view.pixelsPerBlock());
		int n = Math.max(3, Math.min(14, Math.round(radius * ui.unit / 1.6F)));
		float feather = 0.5F / ui.unit;
		int count = contour(this.screenContour, x, y, w, h, radius, feather, n);
		contour(this.screenOuter, x, y, w, h, radius, -feather, n);
		for (int i = 0; i < count; i++) {
			this.mapContour[i * 2] = f.mapX(this.screenContour[i * 2], this.screenContour[i * 2 + 1]);
			this.mapContour[i * 2 + 1] = f.mapZ(this.screenContour[i * 2], this.screenContour[i * 2 + 1]);
			this.mapOuter[i * 2] = f.mapX(this.screenOuter[i * 2], this.screenOuter[i * 2 + 1]);
			this.mapOuter[i * 2 + 1] = f.mapZ(this.screenOuter[i * 2], this.screenOuter[i * 2 + 1]);
		}
		// the viewport's bounding box in map space
		double minX = Double.MAX_VALUE, minZ = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
		float[][] corners = {{x, y}, {x + w, y}, {x + w, y + h}, {x, y + h}};
		for (float[] c : corners) {
			double mx = f.mapX(c[0], c[1]);
			double mz = f.mapZ(c[0], c[1]);
			minX = Math.min(minX, mx);
			maxX = Math.max(maxX, mx);
			minZ = Math.min(minZ, mz);
			maxZ = Math.max(maxZ, mz);
		}
		int span = Coords.tileSpan(level);
		int tx0 = Math.floorDiv((int) Math.floor(minX), span);
		int tx1 = Math.floorDiv((int) Math.floor(maxX), span);
		int tz0 = Math.floorDiv((int) Math.floor(minZ), span);
		int tz1 = Math.floorDiv((int) Math.floor(maxZ), span);
		int drawn = 0;
		for (int tz = tz0; tz <= tz1; tz++) {
			for (int tx = tx0; tx <= tx1; tx++) {
				if (!layer.tileExists(level, tx, tz)) {
					continue;
				}
				TileCache.Slot slot = tiles.get(layer, level, tx, tz, request);
				int useLevel = level;
				int ux = tx;
				int uz = tz;
				if (slot == null) {
					// a coarser tile stands in until this one is ready
					for (int up = 1; up <= 3 && level + up <= Coords.MAX_LEVEL && slot == null; up++) {
						ux = tx >> up;
						uz = tz >> up;
						useLevel = level + up;
						slot = tiles.get(layer, useLevel, ux, uz, false);
					}
				}
				if (slot == null) {
					continue;
				}
				double ox = (double) tx * span;
				double oz = (double) tz * span;
				double uox = (double) Coords.tileOrigin(ux, useLevel);
				double uoz = (double) Coords.tileOrigin(uz, useLevel);
				double texelBlocks = 1 << useLevel;
				MeshBuilder mesh = this.meshes.computeIfAbsent(slot.page, p -> new MeshBuilder(true));
				// the inside of the viewport
				this.setPolygon(count);
				this.clip(ox, oz, ox + span, oz + span);
				this.emitFan(f, mesh, slot, uox, uoz, texelBlocks);
				// the feathered edge: the band between the outline and a pixel outside it, fading out
				for (int i = 0; i < count; i++) {
					int j = (i + 1) % count;
					if (this.setQuad(i, j, ox, oz, ox + span, oz + span)) {
						this.clip(ox, oz, ox + span, oz + span);
						this.emitFan(f, mesh, slot, uox, uoz, texelBlocks);
					}
				}
				drawn++;
			}
		}
		for (var e : this.meshes.entrySet()) {
			if (!e.getValue().isEmpty()) {
				ui.submit(e.getKey().setup(), e.getValue());
				e.getValue().clear();
			}
		}
		Perf.tiles(drawn);
		Perf.map(System.nanoTime() - t0);
		return f;
	}

	private static int contour(float[] out, float x, float y, float w, float h, float r, float inset, int n) {
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

	private void ensure(int vertices) {
		if (this.poly.length < vertices * 3) {
			this.poly = java.util.Arrays.copyOf(this.poly, vertices * 3 * 2);
			this.tmp = new double[vertices * 3 * 2];
		} else if (this.tmp.length < vertices * 3) {
			this.tmp = new double[this.poly.length];
		}
	}

	private void setPolygon(int count) {
		this.ensure(count + 8);
		for (int i = 0; i < count; i++) {
			this.poly[i * 3] = this.mapContour[i * 2];
			this.poly[i * 3 + 1] = this.mapContour[i * 2 + 1];
			this.poly[i * 3 + 2] = 1;
		}
		this.polyCount = count;
	}

	/** Sets the edge band's quad {@code i..j}; false if it misses the tile's rectangle altogether. */
	private boolean setQuad(int i, int j, double x0, double z0, double x1, double z1) {
		double[] in = this.mapContour;
		double[] out = this.mapOuter;
		double minX = Math.min(Math.min(in[i * 2], in[j * 2]), Math.min(out[i * 2], out[j * 2]));
		double maxX = Math.max(Math.max(in[i * 2], in[j * 2]), Math.max(out[i * 2], out[j * 2]));
		double minZ = Math.min(Math.min(in[i * 2 + 1], in[j * 2 + 1]), Math.min(out[i * 2 + 1], out[j * 2 + 1]));
		double maxZ = Math.max(Math.max(in[i * 2 + 1], in[j * 2 + 1]), Math.max(out[i * 2 + 1], out[j * 2 + 1]));
		if (maxX < x0 || minX > x1 || maxZ < z0 || minZ > z1) {
			return false;
		}
		this.ensure(12);
		double[] p = this.poly;
		p[0] = in[i * 2];
		p[1] = in[i * 2 + 1];
		p[2] = 1;
		p[3] = out[i * 2];
		p[4] = out[i * 2 + 1];
		p[5] = 0;
		p[6] = out[j * 2];
		p[7] = out[j * 2 + 1];
		p[8] = 0;
		p[9] = in[j * 2];
		p[10] = in[j * 2 + 1];
		p[11] = 1;
		this.polyCount = 4;
		return true;
	}

	/** Sutherland–Hodgman against an axis-aligned rectangle in map space, carrying alpha along. */
	private void clip(double x0, double z0, double x1, double z1) {
		this.clipEdge(0, x0, false);
		this.clipEdge(0, x1, true);
		this.clipEdge(1, z0, false);
		this.clipEdge(1, z1, true);
	}

	private void clipEdge(int axis, double bound, boolean max) {
		int n = this.polyCount;
		if (n == 0) {
			return;
		}
		this.ensure(n * 2 + 4);
		double[] in = this.poly;
		double[] out = this.tmp;
		int m = 0;
		for (int i = 0; i < n; i++) {
			int j = (i + 1) % n;
			double a = in[i * 3 + axis];
			double b = in[j * 3 + axis];
			boolean aIn = max ? a <= bound : a >= bound;
			boolean bIn = max ? b <= bound : b >= bound;
			if (aIn) {
				out[m * 3] = in[i * 3];
				out[m * 3 + 1] = in[i * 3 + 1];
				out[m * 3 + 2] = in[i * 3 + 2];
				m++;
			}
			if (aIn != bIn) {
				double t = (bound - a) / (b - a);
				out[m * 3] = in[i * 3] + (in[j * 3] - in[i * 3]) * t;
				out[m * 3 + 1] = in[i * 3 + 1] + (in[j * 3 + 1] - in[i * 3 + 1]) * t;
				out[m * 3 + 2] = in[i * 3 + 2] + (in[j * 3 + 2] - in[i * 3 + 2]) * t;
				out[m * 3 + axis] = bound;
				m++;
			}
		}
		this.tmp = in;
		this.poly = out;
		this.polyCount = m;
	}

	private void emitFan(Frame f, MeshBuilder mesh, TileCache.Slot slot, double ox, double oz, double texelBlocks) {
		int n = this.polyCount;
		if (n < 3) {
			return;
		}
		float eps = 0.002F;
		float u0 = (slot.px + eps) / TileCache.PAGE;
		float u1 = (slot.px + TileCache.SLOT - eps) / TileCache.PAGE;
		float v0 = (slot.py + eps) / TileCache.PAGE;
		float v1 = (slot.py + TileCache.SLOT - eps) / TileCache.PAGE;
		double[] p = this.poly;
		for (int i = 1; i + 1 < n; i++) {
			this.vertex(f, mesh, slot, p, 0, ox, oz, texelBlocks, u0, u1, v0, v1);
			this.vertex(f, mesh, slot, p, i, ox, oz, texelBlocks, u0, u1, v0, v1);
			this.vertex(f, mesh, slot, p, i + 1, ox, oz, texelBlocks, u0, u1, v0, v1);
			this.vertex(f, mesh, slot, p, i + 1, ox, oz, texelBlocks, u0, u1, v0, v1);
		}
	}

	private void vertex(Frame f, MeshBuilder mesh, TileCache.Slot slot, double[] p, int i, double ox, double oz, double texelBlocks, float u0, float u1,
		float v0, float v1) {
		double mx = p[i * 3];
		double mz = p[i * 3 + 1];
		int alpha = (int) Math.round(Math.max(0, Math.min(1, p[i * 3 + 2])) * 255);
		float u = (float) ((slot.px + (mx - ox) / texelBlocks) / TileCache.PAGE);
		float v = (float) ((slot.py + (mz - oz) / texelBlocks) / TileCache.PAGE);
		mesh.vertex(f.screenX(mx, mz), f.screenY(mx, mz), Math.max(u0, Math.min(u1, u)), Math.max(v0, Math.min(v1, v)), alpha << 24 | this.tint);
	}

	/** The colour the tiles are multiplied by (0xRRGGBB): white, or the time of day's light (see {@link #daylight}). */
	private int tint = 0xFFFFFF;

	public void setTint(int rgb) {
		this.tint = rgb & 0xFFFFFF;
	}

	/**
	 * The map's light at a time of day (ticks, 0 = sunrise... 24000): white by day, a cool dark blue at night (the
	 * land at about 55%, kept readable), blending through dusk and dawn over the game's own 1,800-tick twilights.
	 */
	public static int daylight(long dayTime) {
		long t = Math.floorMod(dayTime, 24000L);
		double night;
		if (t < 12000) {
			night = 0;
		} else if (t < 13800) {
			night = (t - 12000) / 1800.0;
		} else if (t < 22200) {
			night = 1;
		} else {
			night = 1 - (t - 22200) / 1800.0;
		}
		night = night * night * (3 - 2 * night);
		int r = (int) Math.round(255 * (1 - night * 0.47));
		int g = (int) Math.round(255 * (1 - night * 0.42));
		int b = (int) Math.round(255 * (1 - night * 0.24));
		return r << 16 | g << 8 | b;
	}
}
