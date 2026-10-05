package dev.voidmc.atlas.client.ui;

import java.util.Arrays;

/** Grows a vertex list (position, optional uv, colour) four vertices to a quad; triangles repeat their last vertex. */
public final class MeshBuilder {
	private final boolean textured;
	float[] xy = new float[256];
	float[] uv;
	int[] colours = new int[128];
	int count;

	public MeshBuilder(boolean textured) {
		this.textured = textured;
		this.uv = textured ? new float[256] : null;
	}

	public boolean isEmpty() {
		return this.count == 0;
	}

	public int count() {
		return this.count;
	}

	public void clear() {
		this.count = 0;
	}

	private void grow(int more) {
		int need = this.count + more;
		if (need > this.colours.length) {
			int size = Math.max(need, this.colours.length * 2);
			this.xy = Arrays.copyOf(this.xy, size * 2);
			this.colours = Arrays.copyOf(this.colours, size);
			if (this.textured) {
				this.uv = Arrays.copyOf(this.uv, size * 2);
			}
		}
	}

	public void vertex(float x, float y, int colour) {
		this.grow(1);
		this.xy[this.count * 2] = x;
		this.xy[this.count * 2 + 1] = y;
		this.colours[this.count] = colour;
		this.count++;
		if ((this.count & 3) == 0) {
			this.orient();
		}
	}

	/**
	 * The GUI pipeline culls back faces: a quad must wind the way the game's own do (x0,y0 / x0,y1 / x1,y1 / x1,y0,
	 * negative area in screen space). Shapes are built in whatever order is natural, and each finished quad is
	 * turned round here if it winds the other way.
	 */
	private void orient() {
		int b = this.count - 4;
		float[] p = this.xy;
		float area = 0;
		for (int i = 0; i < 4; i++) {
			int j = (i + 1) & 3;
			area += p[(b + i) * 2] * p[(b + j) * 2 + 1] - p[(b + j) * 2] * p[(b + i) * 2 + 1];
		}
		if (area > 0) {
			this.swap(b + 1, b + 3);
		}
	}

	private void swap(int a, int b) {
		float t = this.xy[a * 2];
		this.xy[a * 2] = this.xy[b * 2];
		this.xy[b * 2] = t;
		t = this.xy[a * 2 + 1];
		this.xy[a * 2 + 1] = this.xy[b * 2 + 1];
		this.xy[b * 2 + 1] = t;
		if (this.uv != null) {
			t = this.uv[a * 2];
			this.uv[a * 2] = this.uv[b * 2];
			this.uv[b * 2] = t;
			t = this.uv[a * 2 + 1];
			this.uv[a * 2 + 1] = this.uv[b * 2 + 1];
			this.uv[b * 2 + 1] = t;
		}
		int c = this.colours[a];
		this.colours[a] = this.colours[b];
		this.colours[b] = c;
	}

	public void vertex(float x, float y, float u, float v, int colour) {
		this.grow(1);
		this.xy[this.count * 2] = x;
		this.xy[this.count * 2 + 1] = y;
		this.uv[this.count * 2] = u;
		this.uv[this.count * 2 + 1] = v;
		this.colours[this.count] = colour;
		this.count++;
		if ((this.count & 3) == 0) {
			this.orient();
		}
	}

	/** A quad, counter-clockwise or clockwise: the GUI pipeline does not cull. */
	public void quad(float x0, float y0, int c0, float x1, float y1, int c1, float x2, float y2, int c2, float x3, float y3, int c3) {
		this.vertex(x0, y0, c0);
		this.vertex(x1, y1, c1);
		this.vertex(x2, y2, c2);
		this.vertex(x3, y3, c3);
	}

	public void triangle(float x0, float y0, int c0, float x1, float y1, int c1, float x2, float y2, int c2) {
		this.quad(x0, y0, c0, x1, y1, c1, x2, y2, c2, x2, y2, c2);
	}

	public void rect(float x0, float y0, float x1, float y1, int colour) {
		this.quad(x0, y0, colour, x0, y1, colour, x1, y1, colour, x1, y0, colour);
	}

	/** Copies out the vertices as a mesh's arrays. */
	float[] xyCopy() {
		return Arrays.copyOf(this.xy, this.count * 2);
	}

	float[] uvCopy() {
		return this.uv == null ? null : Arrays.copyOf(this.uv, this.count * 2);
	}

	int[] coloursCopy() {
		return Arrays.copyOf(this.colours, this.count);
	}
}
