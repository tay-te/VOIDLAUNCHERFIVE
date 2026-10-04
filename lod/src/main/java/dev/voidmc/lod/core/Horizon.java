package dev.voidmc.lod.core;

import java.util.Arrays;

/**
 * Horizon occlusion culling: which of a frame's tiles are wholly hidden behind nearer land, so they need not be
 * drawn. The valleys behind a ridge, the far side of a range, the plain beyond the hills around a low camera.
 *
 * <p>The directions around the camera are split into {@value #BINS} wedges, and each wedge keeps a horizon: the
 * steepest line of sight (rise over run) proven blocked by land already passed. Tiles go from near to far. A tile
 * is hidden when, in every wedge it touches, even its highest point lies under the horizon. Each tile drawn solid
 * then raises the horizon with its occluder grid ({@link TileBuilder.Mesh#occluders}): over each square of it the
 * surface stays above a known height, so every line passing under that height across the whole square is
 * blocked, in every wedge the square spans completely.
 *
 * <p>It never hides anything that would show:
 * <ul>
 *   <li>an occluder is the lowest the surface comes over its square, a block under the samples, never the skirts
 *       or trees; only tiles drawn solid occlude, not those fading between levels or dithered into the chunks;</li>
 *   <li>a square's line is taken at its nearest distance where it lies below the eye, at its farthest above; a
 *       tile's highest point at its nearest distance where it rises above the eye, at its farthest below;</li>
 *   <li>a tile's squares raise the horizon only for tiles wholly beyond its farthest corner, so a line they
 *       block is blocked before it reaches them;</li>
 *   <li>a wedge counts for a square only if the square spans all of it, and against a tile if the tile touches
 *       any of it.</li>
 * </ul>
 *
 * <p>Cheap enough to run every frame: wedges are equal steps of a pseudo-angle that orders directions as the
 * angle does without a trigonometric call, tiles go near to far in buckets a sixteenth of an octave of distance
 * wide instead of through a sort, and a tile whose best line is under the horizon everywhere it reaches skips
 * its squares. Nothing is allocated once the arrays have grown.
 */
public final class Horizon {
	/** Wedges around the camera, about 1.4 degrees each. */
	static final int BINS = 256;
	private static final float BIN_SCALE = BINS / 4.0F;
	/** Distance buckets: 16 an octave from a block out to 2^17 blocks. */
	private static final int BUCKETS = 17 * 16;
	private static final int BUCKET_BASE = Float.floatToRawIntBits(1.0F) >> 19;
	private static final int G = TileBuilder.OCCLUDERS;
	/** A little more than the rounding of a pseudo-angle to a wedge, so an edge never counts on the wrong side. */
	private static final float EDGE = 1e-3F;

	private final float[] horizon = new float[BINS];
	private final int[] tileHead = new int[BUCKETS];
	private final int[] occluderHead = new int[BUCKETS];
	private final float[] lattice = new float[(G + 1) * (G + 1)];
	private float highest;
	/** The wedges {@link #wedges} found. */
	private int first;
	private int last;
	private double cx;
	private double cy;
	private double cz;

	// per tile: footprint relative to the camera, highest point above the eye, occluders, distances, and the result
	private int tiles;
	private float[] x0 = new float[256];
	private float[] z0 = new float[256];
	private float[] span = new float[256];
	private float[] top = new float[256];
	private float[] near = new float[256];
	private float[] far = new float[256];
	private short[][] occluders = new short[256][];
	private boolean[] hidden = new boolean[256];
	private int[] tileNext = new int[256];
	private int[] occluderNext = new int[256];
	private int culled;

	/** Starts a frame seen from ({@code cx}, {@code cy}, {@code cz}). */
	public void begin(double cx, double cy, double cz) {
		this.cx = cx;
		this.cy = cy;
		this.cz = cz;
		this.tiles = 0;
		this.culled = 0;
	}

	/**
	 * Adds a tile to cull: its corner and side in blocks, its highest point, and its occluder grid, or null if it
	 * should not occlude (it is fading, or dithered into the chunks). Returns its index for {@link #hidden}.
	 */
	public int add(int minX, int minZ, int side, int maxY, short[] occluders) {
		int i = this.tiles++;
		if (i == this.x0.length) {
			int n = i * 2;
			this.x0 = Arrays.copyOf(this.x0, n);
			this.z0 = Arrays.copyOf(this.z0, n);
			this.span = Arrays.copyOf(this.span, n);
			this.top = Arrays.copyOf(this.top, n);
			this.near = Arrays.copyOf(this.near, n);
			this.far = Arrays.copyOf(this.far, n);
			this.occluders = Arrays.copyOf(this.occluders, n);
			this.hidden = Arrays.copyOf(this.hidden, n);
			this.tileNext = Arrays.copyOf(this.tileNext, n);
			this.occluderNext = Arrays.copyOf(this.occluderNext, n);
		}
		this.x0[i] = (float) (minX - this.cx);
		this.z0[i] = (float) (minZ - this.cz);
		this.span[i] = side;
		this.top[i] = (float) (maxY - this.cy);
		this.occluders[i] = occluders;
		return i;
	}

	/** Works out which of the tiles added since {@link #begin} are hidden. */
	public void cull() {
		this.culled = 0;
		Arrays.fill(this.hidden, 0, this.tiles, false);
		Arrays.fill(this.horizon, Float.NEGATIVE_INFINITY);
		Arrays.fill(this.tileHead, -1);
		Arrays.fill(this.occluderHead, -1);
		this.highest = Float.NEGATIVE_INFINITY;
		int last = -1;
		for (int i = 0; i < this.tiles; i++) {
			float ax = this.x0[i], az = this.z0[i], bx = ax + this.span[i], bz = az + this.span[i];
			if (ax <= 0 && bx >= 0 && az <= 0 && bz >= 0) {
				// the camera is over it: never hidden, and no use as an occluder around the camera
				continue;
			}
			this.near[i] = nearest(ax, az, bx, bz);
			this.far[i] = farthest(ax, az, bx, bz);
			int bucket = bucket(this.near[i]);
			this.tileNext[i] = this.tileHead[bucket];
			this.tileHead[bucket] = i;
			last = Math.max(last, bucket);
			if (this.occluders[i] != null) {
				bucket = bucket(this.far[i]);
				this.occluderNext[i] = this.occluderHead[bucket];
				this.occluderHead[bucket] = i;
			}
		}
		// near to far: a bucket's tiles against the horizon of the tiles wholly nearer, then its occluders join it,
		// up to the last bucket with tiles to test
		for (int b = 0; b <= last; b++) {
			for (int i = this.tileHead[b]; i >= 0; i = this.tileNext[i]) {
				if (this.covered(i)) {
					this.hidden[i] = true;
					this.culled++;
				}
			}
			if (b == last) {
				break;
			}
			for (int i = this.occluderHead[b]; i >= 0; i = this.occluderNext[i]) {
				if (!this.hidden[i]) {
					this.raise(i);
				}
			}
		}
	}

	public boolean hidden(int i) {
		return this.hidden[i];
	}

	/** Tiles hidden by the last {@link #cull}. */
	public int culled() {
		return this.culled;
	}

	/** Whether tile {@code i}'s highest point lies under the horizon in every wedge it touches. */
	private boolean covered(int i) {
		float top = this.top[i];
		float slope = top > 0 ? top / this.near[i] : top / this.far[i];
		if (slope >= this.highest) {
			return false;
		}
		float ax = this.x0[i], az = this.z0[i], bx = ax + this.span[i], bz = az + this.span[i];
		this.wedges(pseudo(ax, az), pseudo(bx, az), pseudo(ax, bz), pseudo(bx, bz), false);
		for (int w = this.first; w <= this.last; w++) {
			if (this.horizon[w & BINS - 1] <= slope) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Raises the horizon with each square of tile {@code i}'s occluder grid: in the wedges the square spans
	 * completely, to the steepest line that passes under the square's lowest surface all the way across it.
	 */
	private void raise(int i) {
		short[] occ = this.occluders[i];
		short best = TileBuilder.NO_OCCLUDER;
		for (short h : occ) {
			best = (short) Math.max(best, h);
		}
		if (best == TileBuilder.NO_OCCLUDER) {
			return;
		}
		float ax = this.x0[i], az = this.z0[i];
		float step = this.span[i] / G;
		float[] lattice = this.lattice;
		for (int b = 0; b <= G; b++) {
			for (int a = 0; a <= G; a++) {
				lattice[a + b * (G + 1)] = pseudo(ax + a * step, az + b * step);
			}
		}
		// nothing to do if even the highest square's line, at its best, is under the horizon all across the tile
		float rise = (float) (best - this.cy);
		float most = rise > 0 ? rise / this.near[i] : rise / this.far[i];
		this.wedges(lattice[0], lattice[G], lattice[G * (G + 1)], lattice[G + G * (G + 1)], true);
		boolean useful = false;
		for (int w = this.first; w <= this.last && !useful; w++) {
			useful = this.horizon[w & BINS - 1] < most;
		}
		if (!useful) {
			return;
		}
		for (int b = 0; b < G; b++) {
			for (int a = 0; a < G; a++) {
				short h = occ[a + b * G];
				if (h == TileBuilder.NO_OCCLUDER) {
					continue;
				}
				float sx = ax + a * step, sz = az + b * step;
				rise = (float) (h - this.cy);
				float slope = rise > 0 ? rise / farthest(sx, sz, sx + step, sz + step) : rise / nearest(sx, sz, sx + step, sz + step);
				int k = a + b * (G + 1);
				this.wedges(lattice[k], lattice[k + 1], lattice[k + G + 1], lattice[k + G + 2], true);
				for (int w = this.first; w <= this.last; w++) {
					int at = w & BINS - 1;
					if (slope > this.horizon[at]) {
						this.horizon[at] = slope;
						this.highest = Math.max(this.highest, slope);
					}
				}
			}
		}
	}

	/**
	 * The wedges a rectangle seen from outside covers, from the pseudo-angles of its corners: those it spans
	 * completely ({@code inner}) or those it touches at all, into {@link #first} and {@link #last} (not yet wrapped).
	 */
	private void wedges(float p0, float p1, float p2, float p3, boolean inner) {
		// under half a turn across, so each corner is within two units of the first, the short way round
		float d1 = wrap(p1 - p0), d2 = wrap(p2 - p0), d3 = wrap(p3 - p0);
		float lo = p0 + Math.min(0, Math.min(d1, Math.min(d2, d3)));
		float hi = p0 + Math.max(0, Math.max(d1, Math.max(d2, d3)));
		if (inner) {
			this.first = (int) Math.ceil(lo * BIN_SCALE + EDGE);
			this.last = (int) Math.floor(hi * BIN_SCALE - EDGE) - 1;
		} else {
			this.first = (int) Math.floor(lo * BIN_SCALE - EDGE);
			this.last = (int) Math.floor(hi * BIN_SCALE + EDGE);
		}
	}

	/** The bucket of a distance: 16 an octave, from one block. */
	private static int bucket(float distance) {
		int b = (Float.floatToRawIntBits(Math.max(1, distance)) >> 19) - BUCKET_BASE;
		return Math.min(b, BUCKETS - 1);
	}

	private static float nearest(float ax, float az, float bx, float bz) {
		float dx = Math.max(0, Math.max(ax, -bx));
		float dz = Math.max(0, Math.max(az, -bz));
		return (float) Math.sqrt(dx * dx + dz * dz);
	}

	private static float farthest(float ax, float az, float bx, float bz) {
		float dx = Math.max(Math.abs(ax), Math.abs(bx));
		float dz = Math.max(Math.abs(az), Math.abs(bz));
		return (float) Math.sqrt(dx * dx + dz * dz);
	}

	/**
	 * A pseudo-angle in [0, 4) for the direction ({@code x}, {@code z}): a quarter turn per unit, monotonic in the
	 * true angle, at the cost of one division. Not defined at the origin, which callers never pass.
	 */
	static float pseudo(float x, float z) {
		if (z >= 0) {
			return x >= 0 ? z / (x + z) : 1 - x / (z - x);
		}
		return x < 0 ? 2 - z / (-x - z) : 3 + x / (x - z);
	}

	/** A difference of pseudo-angles brought into [-2, 2): the short way round. */
	private static float wrap(float d) {
		return d >= 2 ? d - 4 : d < -2 ? d + 4 : d;
	}
}
