package dev.voidmc.lod.core;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Turns one tile of a {@link TerrainSource} into a mesh, in one of two styles chosen by level:
 *
 * <ul>
 *   <li><b>Blocky</b> ({@link Blocky}), for the tiles closest to the chunks: a flat top per cell and walls
 *       between cells, the way Minecraft looks where its blocks are still a few pixels across. Walls show
 *       what the ground is made of (grass side, then soil, then rock), corners get vanilla's ambient
 *       occlusion, and forests are individual crowns.</li>
 *   <li><b>Smooth</b> ({@link Smooth}), for everything further out: a continuous heightfield on the cell
 *       corners. Out there a block is under a pixel, and what the eye sees of a stair of blocks is its
 *       average: the vertex carries the colour of the tops and the colour of the risers, and the shader
 *       blends them by how much of each faces the camera. A hillside seen from above is mostly grass, seen
 *       from across a valley it is mostly its dirt and stone, exactly as it would be in the chunks, with
 *       none of the giant stair-steps a blocky mesh draws when its cells are 64 blocks wide.</li>
 * </ul>
 *
 * <p>One builder per worker thread: it owns its scratch arrays and its output grows in place, so a tile
 * costs no allocation beyond the copy handed to the renderer.
 *
 * <p>Vertex format, {@value #VERTEX_BYTES} bytes: {@code short x, y, z, flags}, then two RGBA8 colours. x and z
 * are in cells from the tile's corner (0..TILE), y in eighths of a block. The low three bits of flags are
 * the face ({@link #UP}, {@link #SMOOTH}, a wall: 2 north, 3 south, 4 west, 5 east, or {@link #DOWN}),
 * {@link #WATER} marks water and {@link #EMPTY} a smooth vertex over the void. A blocky face carries its colour
 * in the first RGBA (alpha: ambient occlusion on tops, height above the floor on walls); a smooth vertex
 * carries the colour of the tops and of the risers, with the surface's slope in the two alphas.
 */
public final class TileBuilder {
	public static final int VERTEX_BYTES = 16;
	public static final int UP = 0;
	public static final int SMOOTH = 1;
	/** The underside of land floating over the void. */
	public static final int DOWN = 6;
	public static final int WATER = 8;
	/** A smooth vertex over the void: the shader cuts out the triangles that have nothing but these. */
	public static final int EMPTY = 16;

	/** Time all builders have spent asking terrain sources about columns: the rest of a build is meshing. */
	public static final java.util.concurrent.atomic.AtomicLong SAMPLE_NANOS = new java.util.concurrent.atomic.AtomicLong();
	/** Samples all builders have laid out, and those they took from neighbouring tiles ({@link SampleCache}) instead of their sources. */
	public static final java.util.concurrent.atomic.AtomicLong SAMPLES = new java.util.concurrent.atomic.AtomicLong();
	public static final java.util.concurrent.atomic.AtomicLong SAMPLES_REUSED = new java.util.concurrent.atomic.AtomicLong();

	/** Squares along a side of a tile's occluder grid: see {@link Mesh#occluders}. */
	public static final int OCCLUDERS = 2;
	/** An occluder square with a hole in it, a column with no ground: it hides nothing. */
	public static final short NO_OCCLUDER = Short.MIN_VALUE;

	/** The first level drawn smooth; the levels below it are blocky. */
	public static volatile int smoothFrom = Integer.getInteger("void.lod.smoothFrom", 2);

	private final Samples samples;
	private final SampleCache cache;
	private final Blocky blocky;
	private final Smooth smooth;
	private ByteBuffer out = ByteBuffer.allocateDirect(128 * 1024).order(ByteOrder.nativeOrder());
	private int vertices;
	private int minY;
	private int maxY;

	public TileBuilder() {
		this(true);
	}

	/** @param variation whether to add natural variation (patchy forests, drifting tones): off for tests */
	public TileBuilder(boolean variation) {
		this(variation, null);
	}

	/**
	 * @param cache where builders that share it leave the edges of the tiles they build and take the columns
	 *        each tile shares with its neighbours (null for none); they must agree on {@code variation}
	 */
	public TileBuilder(boolean variation, SampleCache cache) {
		this.cache = cache;
		this.samples = new Samples(variation);
		this.blocky = new Blocky(this, this.samples);
		this.smooth = new Smooth(this, this.samples);
	}

	/** Samples and meshes tile {@code key}. The result stays valid until the next call. */
	public Mesh build(TerrainSource source, long key) {
		int level = TileKey.level(key);
		this.begin();
		boolean smooth = level >= smoothFrom;
		int indices;
		int firstIndex = 0;
		boolean holes = false;
		long t0 = System.nanoTime();
		this.samples.sample(source, key, smooth, smooth ? 2 : 1, this.cache);
		SAMPLE_NANOS.addAndGet(System.nanoTime() - t0);
		SAMPLES.addAndGet((long) this.samples.size * this.samples.size);
		if (smooth) {
			indices = this.smooth.mesh();
			holes = indices > 0 && this.smooth.holes;
			firstIndex = this.smooth.flat ? Smooth.FLAT_FIRST_INDEX : 0;
		} else {
			this.blocky.mesh();
			indices = this.vertices / 4 * 6;
		}
		if (this.vertices == 0) {
			this.minY = this.maxY = 0;
		}
		this.out.flip();
		return new Mesh(this.out, this.vertices, indices, smooth, this.minY, this.maxY, firstIndex, this.occluders(smooth), holes);
	}

	/**
	 * The lowest the surface comes in each square of a {@link #OCCLUDERS} x {@link #OCCLUDERS} grid over the tile,
	 * from the samples rather than the mesh, so it holds whatever the meshers make of them: a smooth surface is
	 * never below its corners' ground or water, a blocky top never below its cell's. A square with an empty
	 * column in it has a hole and hides nothing.
	 */
	private short[] occluders(boolean corners) {
		Samples s = this.samples;
		int step = TileKey.TILE / OCCLUDERS;
		// a smooth square spans the corners around its cells, a blocky one just its cells' centres
		int reach = corners ? step : step - 1;
		short[] out = new short[OCCLUDERS * OCCLUDERS];
		for (int b = 0; b < OCCLUDERS; b++) {
			for (int a = 0; a < OCCLUDERS; a++) {
				float low = Float.MAX_VALUE;
				boolean hole = false;
				for (int j = b * step; j <= b * step + reach && !hole; j++) {
					for (int i = a * step; i <= a * step + reach; i++) {
						int k = s.at(i, j);
						if (s.empty[k]) {
							hole = true;
							break;
						}
						low = Math.min(low, s.wet(k) ? s.water[k] : s.ground[k]);
					}
				}
				// a block under the lowest sample: the mesh rounds heights to an eighth, and blocky tops to blocks
				out[a + b * OCCLUDERS] = hole ? NO_OCCLUDER : (short) Math.max(-32767, Math.min(32767, (int) Math.floor(low) - 1));
			}
		}
		return out;
	}

	Samples samples() {
		return this.samples;
	}

	/**
	 * The index buffer every smooth mesh is drawn with (16-bit triangles), to upload once: the grid's
	 * {@link #smoothIndexCount} indices, then a flat tile's ({@link Mesh#firstIndex}).
	 */
	public static ByteBuffer smoothIndices() {
		return Smooth.indices();
	}

	public static int smoothIndexCount() {
		return Smooth.INDICES;
	}

	private void begin() {
		this.out.clear();
		this.vertices = 0;
		this.minY = Integer.MAX_VALUE;
		this.maxY = Integer.MIN_VALUE;
	}

	void vertex(int x, float y, int z, int flags, int top, int topAlpha, int side, int sideAlpha) {
		if (this.out.remaining() < VERTEX_BYTES) {
			ByteBuffer bigger = ByteBuffer.allocateDirect(this.out.capacity() * 2).order(ByteOrder.nativeOrder());
			this.out.flip();
			bigger.put(this.out);
			this.out = bigger;
		}
		int y8 = Math.max(-32768, Math.min(32767, Math.round(y * 8)));
		ByteBuffer b = this.out;
		b.putShort((short) x).putShort((short) y8).putShort((short) z).putShort((short) flags);
		b.put((byte) (top >> 16)).put((byte) (top >> 8)).put((byte) top).put((byte) topAlpha);
		b.put((byte) (side >> 16)).put((byte) (side >> 8)).put((byte) side).put((byte) sideAlpha);
		this.vertices++;
		int lo = y8 >> 3;
		int hi = (y8 + 7) >> 3;
		if (lo < this.minY) {
			this.minY = lo;
		}
		if (hi > this.maxY) {
			this.maxY = hi;
		}
	}

	/**
	 * A built mesh. {@code vertices} is the builder's own buffer: copy it before building another tile.
	 * Blocky meshes are quads drawn with the shared sequential quad index buffer; smooth meshes all share
	 * one grid index buffer ({@link Smooth#indices}), drawn from {@code firstIndex}.
	 *
	 * <p>{@code occluders} is the height the surface stays above in each square of a {@link #OCCLUDERS} x
	 * {@link #OCCLUDERS} grid over the tile, row by row from its north-west corner (skirts and trees left out;
	 * {@link #NO_OCCLUDER} where it has holes): what the renderer culls the land behind it with ({@link Horizon}).
	 * {@code holes}: a smooth mesh with void in it, which only the shader variant that discards can draw.
	 */
	public record Mesh(ByteBuffer vertices, int vertexCount, int indexCount, boolean smooth, int minY, int maxY, int firstIndex,
		short[] occluders, boolean holes) {
		/** The same mesh over other vertices: the copy handed to the renderer. */
		public Mesh withVertices(ByteBuffer vertices) {
			return new Mesh(vertices, this.vertexCount, this.indexCount, this.smooth, this.minY, this.maxY, this.firstIndex, this.occluders,
				this.holes);
		}

		public int bytes() {
			return this.vertexCount * VERTEX_BYTES;
		}

		/** Quads, or quad-equivalents for a smooth mesh: what the statistics count. */
		public int quads() {
			return this.indexCount / 6;
		}
	}
}
