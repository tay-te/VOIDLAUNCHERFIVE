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

	/** The first level drawn smooth; the levels below it are blocky. */
	public static volatile int smoothFrom = Integer.getInteger("void.lod.smoothFrom", 2);

	private final Samples samples;
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
		boolean holes = false;
		long t0 = System.nanoTime();
		this.samples.sample(source, key, smooth, smooth ? 2 : 1);
		SAMPLE_NANOS.addAndGet(System.nanoTime() - t0);
		if (smooth) {
			indices = this.smooth.mesh();
			holes = indices > 0 && this.smooth.holes;
		} else {
			this.blocky.mesh();
			indices = this.vertices / 4 * 6;
		}
		if (this.vertices == 0) {
			this.minY = this.maxY = 0;
		}
		this.out.flip();
		return new Mesh(this.out, this.vertices, indices, smooth, this.minY, this.maxY, holes);
	}

	Samples samples() {
		return this.samples;
	}

	/** The index buffer every smooth mesh is drawn with (16-bit triangles), to upload once. */
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
	 * one grid index buffer ({@link Smooth#indices}). {@code holes}: a smooth mesh with void in it, which only
	 * the shader variant that discards can draw.
	 */
	public record Mesh(ByteBuffer vertices, int vertexCount, int indexCount, boolean smooth, int minY, int maxY, boolean holes) {
		public int bytes() {
			return this.vertexCount * VERTEX_BYTES;
		}

		/** Quads, or quad-equivalents for a smooth mesh: what the statistics count. */
		public int quads() {
			return this.indexCount / 6;
		}
	}
}
