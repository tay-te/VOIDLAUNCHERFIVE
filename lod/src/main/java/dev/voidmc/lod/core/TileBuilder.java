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
 * flags are the face ({@link #UP}, {@link #SMOOTH}, a wall: 2 north, 3 south, 4 west, 5 east, or {@link #DOWN}),
 * {@link #WATER} marks water and {@link #EMPTY} a smooth vertex over the void. The high bytes of x, z and flags carry
 * the light ({@link Horizons}): the sun's horizon to the east and to the west, and how much of the sky the land
 * around hides. A blocky face carries its colour in the first RGBA (alpha: ambient occlusion on tops, height above
 * the floor on walls) and what it is made of in the second: a 16-bit id in the world's material registry, low byte
 * first, 0 for untextured ({@link #material}). A smooth vertex carries the colour of the tops and of the risers, with
 * the surface's slope in the two alphas.
 */
public final class TileBuilder {
	public static final int VERTEX_BYTES = 16;
	/**
	 * A far blocky tile's vertex ({@link Mesh#compact}): two 32-bit words. The first is x and z (7 bits each), the
	 * face and water flag (4 bits) and y in whole blocks (14 bits, signed); the second the colour (5 bits a channel),
	 * the corner occlusion level (2 bits), and the light (the sun's horizon east and west, the sky's occlusion: 5 bits
	 * each). Out there a block texture is under a pixel and so is the lowest block of a wall, so the near tiles'
	 * material id and per-block floor shading go, and so does half the memory.
	 */
	public static final int COMPACT_BYTES = 8;
	/** The first level whose blocky tiles are compact. */
	public static final int COMPACT_FROM = 2;
	/**
	 * Whether tiles are built for a shader pack's Distant Horizons programs ({@code compat.iris.PackLodRenderer}): every
	 * level blocky, in Distant Horizons' vertex layout (16 bytes: x, y, z and light as unsigned shorts, x and z in blocks
	 * from the tile's corner and y plus {@link #PACK_Y_OFFSET}; the colour; the material, the face and no texture), with
	 * the water last so it can go through the pack's water program. Set before the engine's tiles are rebuilt.
	 */
	public static volatile boolean packFormat;
	/** Whether builds take what their tile shows from above ({@link TileTerrain}): while another mod listens. */
	public static volatile boolean captureTerrain;
	private TileTerrain terrain;
	/** Added to every height in the pack layout, whose positions are unsigned. */
	public static final int PACK_Y_OFFSET = 4096;
	/**
	 * The Distant Horizons material of a face's material id (leaves, stone, grass, water...), which packs shade by: set
	 * with the world's material registry. Null: everything is stone.
	 */
	public static volatile java.util.function.IntUnaryOperator packMaterials;
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
	/** Time all builders have spent working out horizons ({@link Horizons}). */
	public static final java.util.concurrent.atomic.AtomicLong LIGHT_NANOS = new java.util.concurrent.atomic.AtomicLong();

	/** The first level drawn smooth; the levels below it are blocky. */
	public static volatile int smoothFrom = Integer.getInteger("void.lod.smoothFrom", TileKey.MAX_LEVEL + 1);

	private final Samples samples;
	private final SampleCache cache;
	private final Blocky blocky;
	private final Smooth smooth;
	private final Horizons horizons = new Horizons();
	/** Whether this build has horizons, and the light the next vertices carry ({@link #light}). */
	private boolean lit;
	private int lightX;
	private int lightZ;
	private int lightFlags;
	/** The block light (0..15) the vertices after this carry: in 3 bits of the flags' high byte, 2 of a compact word. */
	private int lightBlock;
	/** Whether this build writes compact vertices ({@link #COMPACT_BYTES}). */
	private boolean compact;
	/** What the tile just built shows from above, if {@link #captureTerrain} was on; once (it is handed over). */
	public TileTerrain takeTerrain() {
		TileTerrain t = this.terrain;
		this.terrain = null;
		return t;
	}

	/** Whether this build writes the Distant Horizons layout ({@link #packFormat}). */
	private boolean pack;
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
		this.pack = packFormat;
		boolean smooth = !this.pack && level >= smoothFrom;
		this.compact = !this.pack && !smooth && level >= COMPACT_FROM;
		int indices;
		int firstIndex = 0;
		boolean holes = false;
		long t0 = System.nanoTime();
		// a source that keeps its answers is asked every sample, so all of them are kept
		this.samples.sample(source, key, smooth, smooth ? 2 : 1, source.keepsAnswers() ? null : this.cache);
		SAMPLE_NANOS.addAndGet(System.nanoTime() - t0);
		SAMPLES.addAndGet((long) this.samples.size * this.samples.size);
		this.terrain = captureTerrain ? TileTerrain.of(key, this.samples) : null;
		this.lit = heights != null;
		if (this.lit) {
			heights.put(key, this.grid(smooth));
			long t1 = System.nanoTime();
			this.horizons.compute(this.samples, heights, level, smooth ? TileKey.TILE + 1 : TileKey.TILE);
			LIGHT_NANOS.addAndGet(System.nanoTime() - t1);
		}
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
		int land = this.pack ? this.waterLast() * 6 : indices;
		this.out.flip();
		return new Mesh(this.out, this.vertices, indices, smooth, this.minY, this.maxY, firstIndex, this.occluders(smooth), holes, this.compact, land);
	}

	/**
	 * Moves the water's quads after the land's, in a pack-layout mesh, so the two draw as separate ranges (the land through
	 * the pack's terrain program, the water through its water program). Returns the land's quads.
	 */
	private int waterLast() {
		int quads = this.vertices / 4;
		int quadBytes = 4 * VERTEX_BYTES;
		byte[] water = null;
		int waterQuads = 0;
		int land = 0;
		byte[] quad = new byte[quadBytes];
		for (int q = 0; q < quads; q++) {
			// the material, in the first vertex's extra byte
			boolean wet = (this.out.get(q * quadBytes + 12) & 0xFF) == PACK_WATER;
			if (wet) {
				if (water == null) {
					water = new byte[quads * quadBytes];
				}
				this.out.get(q * quadBytes, water, waterQuads * quadBytes, quadBytes);
				waterQuads++;
			} else {
				if (land != q) {
					this.out.get(q * quadBytes, quad, 0, quadBytes);
					this.out.put(land * quadBytes, quad, 0, quadBytes);
				}
				land++;
			}
		}
		if (waterQuads > 0) {
			this.out.put(land * quadBytes, water, 0, waterQuads * quadBytes);
		}
		return land;
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
	 * This tile's surface, for {@link Heights}: a smooth tile's corners, or a blocky tile's cell centres with the ring
	 * of one beyond them, so the grid covers the tile edge to edge.
	 */
	private Heights.Grid grid(boolean corners) {
		Samples s = this.samples;
		int first = corners ? 0 : -1;
		int n = corners ? TileKey.TILE + 1 : TileKey.TILE + 2;
		short[] grid = new short[n * n];
		for (int j = 0; j < n; j++) {
			for (int i = 0; i < n; i++) {
				int k = s.at(first + i, first + j);
				grid[i + j * n] = s.empty[k] ? Heights.NONE : Heights.clamp(Heights.surface(s.ground[k], s.water[k], s.canopy[k], s.cover[k]));
			}
		}
		return new Heights.Grid(grid, n, corners ? 0 : -0.5);
	}

	/**
	 * The light the vertices after this carry, from sample {@code k}: the sun's horizon east and west and the sky's
	 * occlusion (Horizons), in the spare high bytes of x, z and flags (the occlusion in the top 5 bits of the last), and
	 * the block light over its surface in the 3 bits under the occlusion. Unlit builds leave the horizons zero.
	 */
	void light(int k) {
		this.lightBlock = this.samples.light[k];
		int occlusion = this.lit ? this.horizons.occlusion[k] & 0xF8 : 0;
		this.lightFlags = (occlusion | this.lightBlock >> 1) << 8;
		if (!this.lit) {
			return;
		}
		this.lightX = this.horizons.east[k] << 8;
		this.lightZ = this.horizons.west[k] << 8;
	}

	/** Whether this build writes the Distant Horizons layout, for a shader pack: its water shows what lies under it. */
	boolean pack() {
		return this.pack;
	}

	/** Whether this build writes compact vertices: untextured, so what a face is made of does not matter. */
	boolean compact() {
		return this.compact;
	}

	/** No light for the vertices after this: faces the sun and the sky never reach, such as an island's underside. */
	void unlit() {
		this.lightX = this.lightZ = this.lightFlags = this.lightBlock = 0;
	}

	/** Whether two samples' light is close enough for their faces to merge into one. */
	boolean sameLight(int a, int b) {
		if (this.samples.light[a] != this.samples.light[b]) {
			return false;
		}
		if (!this.lit) {
			return true;
		}
		Horizons h = this.horizons;
		return h.east[a] >> 2 == h.east[b] >> 2 && h.west[a] >> 2 == h.west[b] >> 2 && h.occlusion[a] >> 3 == h.occlusion[b] >> 3;
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
		this.lightX = this.lightZ = this.lightFlags = 0;
		this.out.clear();
		this.vertices = 0;
		this.minY = Integer.MAX_VALUE;
		this.maxY = Integer.MIN_VALUE;
	}

	/** A blocky face's material id, as the {@code side} colour of {@link #vertex}: low byte in red, high byte in green. */
	static int material(int id) {
		return (id & 0xFF) << 16 | id & 0xFF00;
	}

	void vertex(int x, float y, int z, int flags, int top, int topAlpha, int side, int sideAlpha) {
		if (this.pack) {
			this.packVertex(x, y, z, flags, top, side);
			return;
		}
		if (this.compact) {
			this.compactVertex(x, y, z, flags, top, topAlpha);
			return;
		}
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

	/** Distant Horizons' water material, which packs draw through their water program. */
	static final int PACK_WATER = 12;
	private static final int PACK_STONE = 2;
	/** Our faces (0 up, 2..5 north, south, west, east, 6 down) as Distant Horizons numbers them (down, up, n, s, w, e). */
	private static final int[] PACK_NORMAL = {1, 1, 2, 3, 4, 5, 0, 0};

	private void packVertex(int x, float y, int z, int flags, int top, int side) {
		if (this.out.remaining() < VERTEX_BYTES) {
			ByteBuffer bigger = ByteBuffer.allocateDirect(this.out.capacity() * 2).order(ByteOrder.nativeOrder());
			this.out.flip();
			bigger.put(this.out);
			this.out = bigger;
		}
		int cell = this.samples.cell;
		int yb = Math.max(-PACK_Y_OFFSET, Math.min(65535 - PACK_Y_OFFSET, Math.round(y)));
		// sky light from how much of the sky the land around hides: the pack's ambient light, darker down in valleys
		int occlusion = this.lightFlags >> 8 & 0xF8;
		int sky = Math.max(8, 15 - Math.round(occlusion * 7 / 255.0F));
		int material;
		if ((flags & WATER) != 0) {
			material = PACK_WATER;
		} else {
			int id = (side >> 16 & 0xFF) | (side & 0xFF00);
			java.util.function.IntUnaryOperator materials = packMaterials;
			material = id == 0 || materials == null ? PACK_STONE : materials.applyAsInt(id);
		}
		ByteBuffer b = this.out;
		b.putShort((short) (x * cell)).putShort((short) (yb + PACK_Y_OFFSET)).putShort((short) (z * cell)).putShort((short) (sky | this.lightBlock << 4));
		// water at about the opacity of vanilla's water texture, over the bed drawn under it (Blocky.beds)
		b.put((byte) (top >> 16)).put((byte) (top >> 8)).put((byte) top).put((byte) (material == PACK_WATER ? 180 : 255));
		b.put((byte) material).put((byte) PACK_NORMAL[flags & 7]).put((byte) 0).put((byte) 0);
		this.vertices++;
		if (yb < this.minY) {
			this.minY = yb;
		}
		if (yb > this.maxY) {
			this.maxY = yb;
		}
	}

	private void compactVertex(int x, float y, int z, int flags, int top, int topAlpha) {
		if (this.out.remaining() < COMPACT_BYTES) {
			ByteBuffer bigger = ByteBuffer.allocateDirect(this.out.capacity() * 2).order(ByteOrder.nativeOrder());
			this.out.flip();
			bigger.put(this.out);
			this.out = bigger;
		}
		// blocky surfaces stand on whole blocks
		int yb = Math.max(-8192, Math.min(8191, Math.round(y)));
		int face = flags & 15;
		int w0 = x & 127 | (z & 127) << 7 | face << 14 | yb << 18;
		// tops carry the corner occlusion's level; walls have no floor shading out here
		int ao = (flags & 7) == UP ? aoLevel(topAlpha) : 0;
		int rgb = (top >> 19 & 31) | (top >> 11 & 31) << 5 | (top >> 3 & 31) << 10;
		// the horizons in 4 bits each, the block light in 2, the occlusion in 5
		int east = this.lightX >> 12 & 15, west = this.lightZ >> 12 & 15, occ = this.lightFlags >> 11 & 31;
		int w1 = rgb | ao << 15 | east << 17 | west << 21 | (this.lightBlock >> 2) << 25 | occ << 27;
		this.out.putInt(w0).putInt(w1);
		this.vertices++;
		if (yb < this.minY) {
			this.minY = yb;
		}
		if (yb > this.maxY) {
			this.maxY = yb;
		}
	}

	/**
	 * Reads vertex {@code i} of {@code mesh} back, whichever way it is packed, into {@code out}: x and z in cells, y in
	 * eighths of a block, and the flags (face, water, void). For tests and tools.
	 */
	public static void decode(Mesh mesh, int i, int[] out) {
		ByteBuffer v = mesh.vertices();
		if (mesh.compact()) {
			int w0 = v.getInt(i * COMPACT_BYTES);
			out[0] = w0 & 127;
			out[1] = (w0 >> 18) * 8;
			out[2] = w0 >> 7 & 127;
			out[3] = w0 >> 14 & 15;
			return;
		}
		int o = i * VERTEX_BYTES;
		out[0] = v.getShort(o) & 255;
		out[1] = v.getShort(o + 2);
		out[2] = v.getShort(o + 4) & 255;
		out[3] = v.getShort(o + 6) & 255;
	}

	/** The occlusion level (0 open .. 3 darkest) whose shade {@code alpha} (255ths) is. */
	private static int aoLevel(int alpha) {
		int best = 0;
		float a = alpha / 255.0F;
		for (int l = 1; l < Colors.AO.length; l++) {
			if (Math.abs(Colors.AO[l] - a) < Math.abs(Colors.AO[best] - a)) {
				best = l;
			}
		}
		return best;
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
		short[] occluders, boolean holes, boolean compact, int landIndices) {
		/** The same mesh over other vertices: the copy handed to the renderer. */
		public Mesh withVertices(ByteBuffer vertices) {
			return new Mesh(vertices, this.vertexCount, this.indexCount, this.smooth, this.minY, this.maxY, this.firstIndex, this.occluders,
				this.holes, this.compact, this.landIndices);
		}

		/** Bytes a vertex of this mesh takes. */
		public int vertexBytes() {
			return this.compact ? COMPACT_BYTES : VERTEX_BYTES;
		}

		public int bytes() {
			return this.vertexCount * this.vertexBytes();
		}

		/** Quads, or quad-equivalents for a smooth mesh: what the statistics count. */
		public int quads() {
			return this.indexCount / 6;
		}
	}
}
