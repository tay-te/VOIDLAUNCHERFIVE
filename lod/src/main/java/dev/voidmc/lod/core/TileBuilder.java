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
 * are in cells from the tile's corner (0..TILE) in their low byte, y in eighths of a block. The low three bits of
 * flags are the face ({@link #UP}, {@link #SMOOTH}, or a wall: 2 north, 3 south, 4 west, 5 east), and {@link #WATER}
 * marks water. The high bytes of x, z and flags carry the light ({@link Horizons}): the sun's horizon to the east
 * and to the west, and how much of the sky the land around hides. A blocky face carries its colour in the first RGBA (alpha: ambient occlusion on tops, height
 * above the floor on walls); a smooth vertex carries the colour of the tops and of the risers, with the
 * surface's slope in the two alphas.
 */
public final class TileBuilder {
	public static final int VERTEX_BYTES = 16;
	public static final int UP = 0;
	public static final int SMOOTH = 1;
	public static final int WATER = 8;

	/** Time all builders have spent asking terrain sources about columns: the rest of a build is meshing. */
	public static final java.util.concurrent.atomic.AtomicLong SAMPLE_NANOS = new java.util.concurrent.atomic.AtomicLong();
	/** Time all builders have spent working out horizons ({@link Horizons}). */
	public static final java.util.concurrent.atomic.AtomicLong LIGHT_NANOS = new java.util.concurrent.atomic.AtomicLong();

	/** The first level drawn smooth; the levels below it are blocky. */
	public static volatile int smoothFrom = Integer.getInteger("void.lod.smoothFrom", 2);

	private final Samples samples;
	private final Blocky blocky;
	private final Smooth smooth;
	private final Horizons horizons = new Horizons();
	/** Whether this build has horizons, and the light the next vertices carry ({@link #light}). */
	private boolean lit;
	private int lightX;
	private int lightZ;
	private int lightFlags;
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

	/** Samples and meshes tile {@code key}, unlit. The result stays valid until the next call. */
	public Mesh build(TerrainSource source, long key) {
		return this.build(source, key, null);
	}

	/**
	 * Samples and meshes tile {@code key}, lit by the land around it as {@code heights} knows it (null: unlit), and
	 * leaves its own heights there for the tiles after it. The result stays valid until the next call.
	 */
	public Mesh build(TerrainSource source, long key, Heights heights) {
		int level = TileKey.level(key);
		this.begin();
		boolean smooth = level >= smoothFrom;
		int indices;
		long t0 = System.nanoTime();
		this.samples.sample(source, key, smooth, smooth ? 2 : 1);
		SAMPLE_NANOS.addAndGet(System.nanoTime() - t0);
		this.lit = heights != null;
		if (this.lit) {
			if (smooth) {
				heights.put(key, this.cornerGrid());
			}
			long t1 = System.nanoTime();
			this.horizons.compute(this.samples, heights, level, smooth ? TileKey.TILE + 1 : TileKey.TILE);
			LIGHT_NANOS.addAndGet(System.nanoTime() - t1);
		}
		if (smooth) {
			this.smooth.mesh();
			indices = Smooth.INDICES;
		} else {
			this.blocky.mesh();
			indices = this.vertices / 4 * 6;
		}
		if (this.vertices == 0) {
			this.minY = this.maxY = 0;
		}
		this.out.flip();
		return new Mesh(this.out, this.vertices, indices, smooth, this.minY, this.maxY);
	}

	Samples samples() {
		return this.samples;
	}

	/** This smooth tile's surface on its corners, for {@link Heights}. */
	private short[] cornerGrid() {
		Samples s = this.samples;
		short[] grid = new short[Heights.V * Heights.V];
		for (int j = 0; j < Heights.V; j++) {
			for (int i = 0; i < Heights.V; i++) {
				int k = s.at(i, j);
				grid[i + j * Heights.V] = s.empty[k] ? Heights.NONE : Heights.clamp(Heights.surface(s.ground[k], s.water[k], s.canopy[k], s.cover[k]));
			}
		}
		return grid;
	}

	/**
	 * The light the vertices after this carry, from sample {@code k}'s horizons: the sun's horizon east and west
	 * and the sky's occlusion, in the spare high bytes of x, z and flags. Unlit builds leave them zero.
	 */
	void light(int k) {
		if (!this.lit) {
			return;
		}
		this.lightX = this.horizons.east[k] << 8;
		this.lightZ = this.horizons.west[k] << 8;
		this.lightFlags = this.horizons.occlusion[k] << 8;
	}

	/** Whether two samples' light is close enough for their faces to merge into one. */
	boolean sameLight(int a, int b) {
		if (!this.lit) {
			return true;
		}
		Horizons h = this.horizons;
		return h.east[a] >> 2 == h.east[b] >> 2 && h.west[a] >> 2 == h.west[b] >> 2 && h.occlusion[a] >> 3 == h.occlusion[b] >> 3;
	}

	/** The index buffer every smooth mesh is drawn with (16-bit triangles), to upload once. */
	public static ByteBuffer smoothIndices() {
		return Smooth.indices();
	}

	public static int smoothIndexCount() {
		return Smooth.INDICES;
	}

	private void begin() {
		this.lightX = this.lightZ = this.lightFlags = 0;
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
		b.putShort((short) (x | this.lightX)).putShort((short) y8).putShort((short) (z | this.lightZ)).putShort((short) (flags | this.lightFlags));
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
	 * one grid index buffer ({@link Smooth#indices}).
	 */
	public record Mesh(ByteBuffer vertices, int vertexCount, int indexCount, boolean smooth, int minY, int maxY) {
		public int bytes() {
			return this.vertexCount * VERTEX_BYTES;
		}

		/** Quads, or quad-equivalents for a smooth mesh: what the statistics count. */
		public int quads() {
			return this.indexCount / 6;
		}
	}
}
