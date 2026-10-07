package dev.voidmc.atlas.core;

/**
 * Where a HUD piece goes so it stays out of the room other mods' HUDs keep ({@code AtlasHud.reserve}): the piece is
 * moved the least way that clears every reserved rectangle and stays on the screen (down below one, across beside one,
 * up over one), never left overlapping while any clear spot is near. Pure geometry, in one unit throughout.
 */
public final class KeepOut {
	/** The gap kept between a piece and a reserved rectangle. */
	public static final float GAP = 6;
	/** How many spots are tried at most (each move out of one rectangle may land in another). */
	private static final int TRIES = 64;

	private KeepOut() {
	}

	/**
	 * The top-left corner a piece of {@code w x h} at {@code (x, y)} moves to, clear of {@code rects} (x0, y0, x1, y1
	 * each, {@code count} of them) and inside the screen ({@code sw x sh}, {@code margin} from its edges). Unmoved when
	 * nothing is in the way; if no clear spot is found, the tried spot that overlaps least.
	 */
	public static float[] place(float x, float y, float w, float h, float sw, float sh, float margin, float[] rects, int count) {
		if (count == 0 || !hits(x, y, w, h, rects, count)) {
			return new float[]{x, y};
		}
		float[] qx = new float[TRIES];
		float[] qy = new float[TRIES];
		int head = 0;
		int tail = 0;
		qx[tail] = x;
		qy[tail++] = y;
		float bestX = x;
		float bestY = y;
		double bestCost = Double.MAX_VALUE;
		float leastX = x;
		float leastY = y;
		double least = Double.MAX_VALUE;
		while (head < tail) {
			float px = qx[head];
			float py = qy[head++];
			double cost = Math.abs(px - x) + Math.abs(py - y);
			if (cost >= bestCost) {
				continue;
			}
			if (!hits(px, py, w, h, rects, count)) {
				bestCost = cost;
				bestX = px;
				bestY = py;
				continue;
			}
			double o = overlap(px, py, w, h, rects, count);
			if (o < least) {
				least = o;
				leastX = px;
				leastY = py;
			}
			for (int i = 0; i < count && tail < TRIES - 4; i++) {
				if (!overlaps(px, py, w, h, rects, i)) {
					continue;
				}
				float x0 = rects[i * 4];
				float y0 = rects[i * 4 + 1];
				float x1 = rects[i * 4 + 2];
				float y1 = rects[i * 4 + 3];
				// below, beside to the right, above, beside to the left: each kept on the screen
				float[][] moves = {{px, y1 + GAP}, {x1 + GAP, py}, {px, y0 - GAP - h}, {x0 - GAP - w, py}};
				for (float[] mv : moves) {
					float nx = clamp(mv[0], margin, sw - margin - w);
					float ny = clamp(mv[1], margin, sh - margin - h);
					if (nx != px || ny != py) {
						qx[tail] = nx;
						qy[tail++] = ny;
					}
				}
			}
		}
		return bestCost < Double.MAX_VALUE ? new float[]{bestX, bestY} : new float[]{leastX, leastY};
	}

	/** Whether a box overlaps any of the rectangles (with {@link #GAP} round them). */
	public static boolean hits(float x, float y, float w, float h, float[] rects, int count) {
		for (int i = 0; i < count; i++) {
			if (overlaps(x, y, w, h, rects, i)) {
				return true;
			}
		}
		return false;
	}

	private static boolean overlaps(float x, float y, float w, float h, float[] rects, int i) {
		float e = 0.01F;
		return x < rects[i * 4 + 2] + GAP - e && x + w > rects[i * 4] - GAP + e && y < rects[i * 4 + 3] + GAP - e && y + h > rects[i * 4 + 1] - GAP + e;
	}

	private static double overlap(float x, float y, float w, float h, float[] rects, int count) {
		double sum = 0;
		for (int i = 0; i < count; i++) {
			double ox = Math.min(x + w, rects[i * 4 + 2]) - Math.max(x, rects[i * 4]);
			double oy = Math.min(y + h, rects[i * 4 + 3]) - Math.max(y, rects[i * 4 + 1]);
			if (ox > 0 && oy > 0) {
				sum += ox * oy;
			}
		}
		return sum;
	}

	private static float clamp(float v, float lo, float hi) {
		return hi < lo ? lo : Math.max(lo, Math.min(hi, v));
	}
}
