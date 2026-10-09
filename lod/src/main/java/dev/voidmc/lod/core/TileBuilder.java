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
	 * A far blocky tile's quad ({@link Mesh#compact}) is one record of two 32-bit words ({@link #RECORD_BYTES}), which
	 * the shader reads for each of the quad's corners. Its shape: its lowest corner's x and z in cells (6 bits each; a
	 * wall on the far edge of its cells, facing south or east, one less) and its face (3 bits); y in whole blocks above
	 * the tile's lowest ({@link Mesh#minY}, 13 bits) and its size along the face. A wall is {@code du} across (6 bits,
	 * less one) and {@code dv} up (13 bits); a top or an underside either one shade of occlusion all over, {@code du}
	 * by {@code dv} (6 bits each, less one) with the shade's level (2 bits), or a single cell shaded corner by corner,
	 * with each corner's level (2 bits each) and the rotation of its corners, the diagonal it splits along (2 bits).
	 *
	 * <p>And its style ({@link #STYLE_BITS}), an index into the tile's palette, which follows the records, one entry
	 * the size of a record for each way of drawing a quad that the tile has: the colour along its top and along its
	 * foot (5 bits a channel each), the block light (2 bits), the light (the sun's horizon east and west, 4 bits each;
	 * the sky's occlusion, 5), the season code (7) and whether it is water. A tile draws its many quads in a few
	 * thousand styles at most (about a quarter as many as it has quads), so the palette costs little.
	 *
	 * <p>Out there a block texture is under a pixel and so is the lowest block of a wall, so the near tiles' material
	 * id and per-block floor shading go: a quad takes an eighth of the memory, and a little more for its share of the
	 * palette. Counted per vertex: {@value} bytes a vertex.
	 */
	public static final int COMPACT_BYTES = 2;
	/** A compact quad's record, and a palette entry: four of its vertices' worth ({@link #COMPACT_BYTES}). */
	public static final int RECORD_BYTES = 4 * COMPACT_BYTES;
	/** The bits of a compact record that pick its style from the tile's palette. */
	static final int STYLE_BITS = 17;
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
	/** The widest tiles built for a shader pack: their x and z, in blocks from the corner, must fit an unsigned short. */
	public static final int PACK_MAX_LEVEL = 9;
	/**
	 * The Distant Horizons material of a face's material id (leaves, stone, grass, water...), which packs shade by: set
	 * with the world's material registry. Null: everything is stone.
	 */
	public static volatile java.util.function.IntUnaryOperator packMaterials;
	public static final int UP = 0;
	public static final int SMOOTH = 1;
	/** Walls, by the way they face: north (toward -z), south, west (toward -x), east. */
	static final int NORTH = 2, SOUTH = 3, WEST = 4, EAST = 5;
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
	/** Time all builders have spent standing the world's trees on their tiles ({@link Decorator}). */
	public static final java.util.concurrent.atomic.AtomicLong TREE_NANOS = new java.util.concurrent.atomic.AtomicLong();
	/** Time all builders have spent working out horizons ({@link Horizons}). */
	public static final java.util.concurrent.atomic.AtomicLong LIGHT_NANOS = new java.util.concurrent.atomic.AtomicLong();

	/**
	 * The levels whose tiles stand the world's own trees, block by block, where the source can place them
	 * ({@link Decorator}): those right outside the chunks, where a tree is still several pixels across.
	 */
	public static volatile int treeLevels = Integer.getInteger("void.lod.treeLevels", 2);
	private final Voxels voxels = new Voxels();

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
		this.level = level;
		int indices;
		int firstIndex = 0;
		boolean holes = false;
		long t0 = System.nanoTime();
		// a source that keeps its answers is asked every sample, so all of them are kept
		this.samples.sample(source, key, smooth, smooth ? 2 : 1, source.keepsAnswers() ? null : this.cache);
		SAMPLE_NANOS.addAndGet(System.nanoTime() - t0);
		SAMPLES.addAndGet((long) this.samples.size * this.samples.size);
		this.terrain = captureTerrain ? TileTerrain.of(key, this.samples) : null;
		dev.voidmc.lod.api.LodApi.Seasons seasons = smooth ? null : dev.voidmc.lod.api.LodApi.seasons();
		this.seasonTable = this.pack ? dev.voidmc.lod.api.LodApi.seasonTints() : null;
		this.classify(seasons);
		this.lit = heights != null;
		if (this.lit) {
			heights.put(key, this.grid(smooth));
			long t1 = System.nanoTime();
			this.horizons.compute(this.samples, heights, level, smooth ? TileKey.TILE + 1 : TileKey.TILE);
			LIGHT_NANOS.addAndGet(System.nanoTime() - t1);
		}
		Decorator decorator = !smooth && !this.compact && level < treeLevels ? source.decorator() : null;
		this.voxels.clear();
		if (decorator != null) {
			long t2 = System.nanoTime();
			Samples s = this.samples;
			int minX = TileKey.minX(key), minZ = TileKey.minZ(key), span = TileKey.span(level);
			boolean ready = decorator.decorate(minX, minZ, minX + span, minZ + span, new Decorator.Ground() {
				@Override
				public void column(int x, int z, ColumnSample out) {
					// the tile's own sample where it has one (on a farther tile, the cell's: rounded ground), else the source's
					int i = Math.floorDiv(x - minX, s.cell), j = Math.floorDiv(z - minZ, s.cell);
					if (i >= -s.border && j >= -s.border && i < s.size - s.border && j < s.size - s.border) {
						int k = s.at(i, j);
						out.ground = s.ground[k];
						out.water = s.water[k];
						out.topMaterial = s.topMaterial[k];
						out.biome = s.biome[k];
						out.empty = s.empty[k];
					} else {
						source.sample(x, z, s.cell, out);
					}
				}

				@Override
				public boolean exact(int x, int z) {
					int i = x - minX, j = z - minZ;
					return s.cell == 1 && i >= -s.border && j >= -s.border && i < s.size - s.border && j < s.size - s.border;
				}

				@Override
				public boolean blockExact() {
					return s.cell == 1;
				}
			}, this.voxels);
			TREE_NANOS.addAndGet(System.nanoTime() - t2);
			if (!ready) {
				// placed in the background: drawn crowns until the tile is built again with them
				decorator = null;
			}
		}
		if (decorator != null && seasons != null) {
			Voxels v = this.voxels;
			Samples s = this.samples;
			for (int n = 0; n < v.size; n++) {
				if (v.kind[n] == Voxels.LEAVES) {
					int i = Math.clamp(Math.floorDiv(v.x[n] - TileKey.minX(key), s.cell), 0, TileKey.TILE - 1);
					int j = Math.clamp(Math.floorDiv(v.z[n] - TileKey.minZ(key), s.cell), 0, TileKey.TILE - 1);
					v.season[n] = this.seasonCode(seasons, dev.voidmc.lod.api.LodApi.Seasons.LEAVES, s.biome[s.at(i, j)], v.topMaterial[n], -1,
						v.x[n], v.z[n]);
				}
			}
		}
		this.blocky.trees(decorator != null ? this.voxels : null);
		if (smooth) {
			indices = this.smooth.mesh();
			holes = indices > 0 && this.smooth.holes;
			firstIndex = this.smooth.flat ? Smooth.FLAT_FIRST_INDEX : 0;
		} else {
			this.meshLayers(decorator != null);
			indices = this.vertices / 4 * 6;
		}
		if (this.vertices == 0) {
			this.minY = this.maxY = 0;
		} else if (this.compact) {
			this.finishCompact();
		}
		int land = this.pack ? this.waterLast() * 6 : indices;
		this.out.flip();
		// a pack's mesh groups its land only: its water is drawn whole, through the pack's water program
		int[] groups = smooth || this.vertices == 0 ? null : this.groupByFace(this.pack ? land / 6 : this.vertices / 4);
		return new Mesh(this.out, this.vertices, indices, smooth, this.minY, this.maxY, firstIndex, this.occluders(smooth), holes, this.compact, land,
			groups);
	}

	/**
	 * Meshes the tile's blocky surface; where the chunks showed land under an overhang (a bridge, a ledge, a roof on
	 * posts), in two layers: first the land, with the land under each overhang in its place, then the overhangs
	 * themselves as slabs over it, with their undersides and rims, as islands over the void are drawn.
	 */
	private void meshLayers(boolean trees) {
		Samples s = this.samples;
		int n = s.size * s.size;
		boolean any = false;
		for (int k = 0; k < n; k++) {
			// an overhang only where it makes sense: its underside under its top, the land under it lower still
			if (s.deckBottom[k] != Integer.MIN_VALUE && (s.empty[k] || s.deckBottom[k] >= s.ground[k] || s.lowerGround[k] >= s.deckBottom[k])) {
				s.deckBottom[k] = Integer.MIN_VALUE;
			}
			any |= s.deckBottom[k] != Integer.MIN_VALUE;
		}
		if (!any) {
			this.blocky.mesh();
			return;
		}
		float[] ground = java.util.Arrays.copyOf(s.ground, n);
		float[] bottom = java.util.Arrays.copyOf(s.bottom, n);
		int[] water = java.util.Arrays.copyOf(s.water, n);
		int[] top = java.util.Arrays.copyOf(s.top, n), side = java.util.Arrays.copyOf(s.side, n), soil = java.util.Arrays.copyOf(s.soil, n);
		int[] topMaterial = java.util.Arrays.copyOf(s.topMaterial, n), sideMaterial = java.util.Arrays.copyOf(s.sideMaterial, n);
		int[] soilMaterial = java.util.Arrays.copyOf(s.soilMaterial, n), waterColor = java.util.Arrays.copyOf(s.waterColor, n);
		float[] canopy = java.util.Arrays.copyOf(s.canopy, n);
		boolean[] empty = java.util.Arrays.copyOf(s.empty, n);
		// the land, the land under each overhang in its place
		for (int k = 0; k < n; k++) {
			if (s.deckBottom[k] == Integer.MIN_VALUE || empty[k]) {
				continue;
			}
			s.ground[k] = s.lowerGround[k];
			s.water[k] = s.lowerWater[k];
			s.top[k] = s.lowerTop[k];
			s.side[k] = s.soil[k] = s.lowerSide[k];
			s.topMaterial[k] = s.lowerTopMaterial[k];
			s.sideMaterial[k] = s.soilMaterial[k] = s.lowerSideMaterial[k];
			s.waterColor[k] = s.lowerWaterColor[k];
			s.canopy[k] = 0;
		}
		this.blocky.mesh();
		// the overhangs, alone over nothing
		this.blocky.trees(null);
		for (int k = 0; k < n; k++) {
			boolean deck = s.deckBottom[k] != Integer.MIN_VALUE && !empty[k];
			s.ground[k] = ground[k];
			s.water[k] = Integer.MIN_VALUE;
			s.top[k] = top[k];
			s.side[k] = side[k];
			s.soil[k] = soil[k];
			s.topMaterial[k] = topMaterial[k];
			s.sideMaterial[k] = sideMaterial[k];
			s.soilMaterial[k] = soilMaterial[k];
			s.canopy[k] = 0;
			s.bottom[k] = deck ? s.deckBottom[k] : bottom[k];
			s.empty[k] = !deck;
		}
		this.blocky.mesh();
		// as it was, for whatever reads the samples after
		System.arraycopy(ground, 0, s.ground, 0, n);
		System.arraycopy(bottom, 0, s.bottom, 0, n);
		System.arraycopy(water, 0, s.water, 0, n);
		System.arraycopy(top, 0, s.top, 0, n);
		System.arraycopy(side, 0, s.side, 0, n);
		System.arraycopy(soil, 0, s.soil, 0, n);
		System.arraycopy(topMaterial, 0, s.topMaterial, 0, n);
		System.arraycopy(sideMaterial, 0, s.sideMaterial, 0, n);
		System.arraycopy(soilMaterial, 0, s.soilMaterial, 0, n);
		System.arraycopy(waterColor, 0, s.waterColor, 0, n);
		System.arraycopy(canopy, 0, s.canopy, 0, n);
		System.arraycopy(empty, 0, s.empty, 0, n);
	}

	/** The season code the vertices after this carry ({@link #season}): 0 none. */
	private int season;
	/** The season table this build bakes in (through a shader pack, whose vertices have no room for the code), or null. */
	private float[] seasonTable;
	/** What the seasons hook answered, per kind, biome, material and conifer share: it is asked once each. */
	private final it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap seasonClasses = new it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap();
	private dev.voidmc.lod.api.LodApi.Seasons seasonsAsked;

	/** The season code ({@code LodApi.Seasons}) the faces after this carry, until the next {@link #light}. */
	void season(int code) {
		this.season = code;
	}

	/**
	 * The samples' season codes: what the seasons hook makes of the top, the side and the leaves of each ({@code
	 * LodApi.Seasons}). Leaves take a variant by where they stand, so each crown has its own shade of the season.
	 */
	private void classify(dev.voidmc.lod.api.LodApi.Seasons seasons) {
		Samples s = this.samples;
		int n = s.size * s.size;
		if (seasons == null) {
			java.util.Arrays.fill(s.topSeason, 0, n, (byte) 0);
			java.util.Arrays.fill(s.sideSeason, 0, n, (byte) 0);
			java.util.Arrays.fill(s.leavesSeason, 0, n, (byte) 0);
			return;
		}
		for (int j = -s.border; j < s.size - s.border; j++) {
			for (int i = -s.border; i < s.size - s.border; i++) {
				int k = s.at(i, j);
				if (s.empty[k]) {
					s.topSeason[k] = s.sideSeason[k] = s.leavesSeason[k] = 0;
					continue;
				}
				int x = s.x(i), z = s.z(j), biome = s.biome[k];
				s.topSeason[k] = this.seasonCode(seasons, dev.voidmc.lod.api.LodApi.Seasons.GROUND_TOP, biome, s.topMaterial[k], 0, x, z);
				s.sideSeason[k] = this.seasonCode(seasons, dev.voidmc.lod.api.LodApi.Seasons.GROUND_SIDE, biome, s.sideMaterial[k], 0, x, z);
				s.leavesSeason[k] = s.canopy[k] > 0
					? this.seasonCode(seasons, dev.voidmc.lod.api.LodApi.Seasons.LEAVES, biome, s.leavesMaterial[k], s.conifer[k], x, z) : 0;
			}
		}
	}

	/** A face's season code: its class from the hook (remembered), and for leaves the variant where it stands. */
	private byte seasonCode(dev.voidmc.lod.api.LodApi.Seasons seasons, int kind, int biome, int material, float conifer, int x, int z) {
		if (seasons != this.seasonsAsked) {
			this.seasonClasses.clear();
			this.seasonsAsked = seasons;
		}
		int eighths = conifer < 0 ? 15 : Math.round(conifer * 8);
		long key = (long) kind << 56 | (long) (eighths & 15) << 52 | (long) (biome & 0xFFFFF) << 32 | material & 0xFFFFFFFFL;
		byte cls;
		if (this.seasonClasses.containsKey(key)) {
			cls = this.seasonClasses.get(key);
		} else {
			int asked;
			try {
				asked = seasons.classify(kind, biome, material, conifer);
			} catch (RuntimeException e) {
				asked = 0;
			}
			cls = (byte) Math.clamp(asked, 0, 31);
			this.seasonClasses.put(key, cls);
		}
		if (cls == 0) {
			return 0;
		}
		return (byte) (kind == dev.voidmc.lod.api.LodApi.Seasons.LEAVES ? cls | dev.voidmc.lod.api.LodApi.seasonVariant(x, z) << 5 : cls);
	}

	/**
	 * The order faces are grouped in ({@link #groupByFace}): north, west, up, east, south, down, so that from any side
	 * of a tile the groups facing the camera are few runs: from the south-east, up, east and south are one.
	 */
	public static final int GROUP_NORTH = 0, GROUP_WEST = 1, GROUP_UP = 2, GROUP_EAST = 3, GROUP_SOUTH = 4, GROUP_DOWN = 5;
	/** A blocky face (0 up, 2 north, 3 south, 4 west, 5 east, 6 down) to its group. */
	private static final int[] FACE_GROUP = {GROUP_UP, GROUP_UP, GROUP_NORTH, GROUP_SOUTH, GROUP_WEST, GROUP_EAST, GROUP_DOWN, GROUP_UP};
	/** Distant Horizons' normal numbers (down, up, north, south, west, east) as our faces. */
	private static final int[] PACK_FACE = {6, 0, 2, 3, 4, 5, 0, 0};

	/**
	 * Sorts a blocky mesh's quads by the way they face, and returns where each group starts, in quads (seven entries,
	 * the last the end): the renderer draws only the groups that can face the camera, and a wall facing away from it,
	 * which is half the walls of most tiles and the walls are most of the quads, never reaches the GPU.
	 */
	private int[] groupByFace(int quads) {
		int quadBytes = 4 * (this.compact ? COMPACT_BYTES : VERTEX_BYTES);
		int[] group = new int[quads];
		int[] starts = new int[7];
		for (int q = 0; q < quads; q++) {
			int face;
			if (this.pack) {
				// Distant Horizons' normals: down, up, north, south, west, east
				face = PACK_FACE[this.out.get(q * quadBytes + 13) & 7];
			} else if (this.compact) {
				face = this.out.getInt(q * quadBytes) >>> 29;
			} else {
				face = this.out.getShort(q * quadBytes + 6) & 7;
			}
			group[q] = FACE_GROUP[face];
			starts[group[q] + 1]++;
		}
		for (int g = 1; g < 7; g++) {
			starts[g] += starts[g - 1];
		}
		byte[] sorted = new byte[quads * quadBytes];
		int[] next = java.util.Arrays.copyOf(starts, 6);
		for (int q = 0; q < quads; q++) {
			this.out.get(q * quadBytes, sorted, next[group[q]]++ * quadBytes, quadBytes);
		}
		this.out.put(0, sorted, 0, sorted.length);
		return starts;
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
		this.season = 0;
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

	/** The level being built. */
	private int level;

	/**
	 * Whether faces merge at the precision a far vertex keeps colour at (5 bits a channel): compact tiles, and from the
	 * same level the tiles built for a shader pack, whose colours are under a pixel's difference out there too.
	 */
	boolean coarseColour() {
		return this.compact || this.pack && this.level >= COMPACT_FROM;
	}

	/** Whether two faces' materials draw alike: the same, or through a pack the same Distant Horizons material. */
	boolean sameMaterial(int a, int b) {
		if (a == b) {
			return true;
		}
		java.util.function.IntUnaryOperator materials = packMaterials;
		return this.pack && materials != null && a != 0 && b != 0 && materials.applyAsInt(a) == materials.applyAsInt(b);
	}

	/** No light for the vertices after this: faces the sun and the sky never reach, such as an island's underside. */
	void unlit() {
		this.season = 0;
		this.lightX = this.lightZ = this.lightFlags = this.lightBlock = 0;
	}

	/** Whether two samples' light is close enough for their faces to merge into one. */
	boolean sameLight(int a, int b) {
		if (this.pack) {
			// a pack's vertex keeps the block light and a sky level made of the occlusion, and nothing of the horizons
			if (this.samples.light[a] != this.samples.light[b]) {
				return false;
			}
			return !this.lit || packSky(this.horizons.occlusion[a] & 0xF8) == packSky(this.horizons.occlusion[b] & 0xF8);
		}
		if (this.compact) {
			// exactly as finely as a compact vertex keeps it: what it cannot tell apart may as well be one face
			if (this.samples.light[a] >> 2 != this.samples.light[b] >> 2) {
				return false;
			}
			if (!this.lit) {
				return true;
			}
			Horizons h = this.horizons;
			return h.east[a] >> 4 == h.east[b] >> 4 && h.west[a] >> 4 == h.west[b] >> 4 && h.occlusion[a] >> 3 == h.occlusion[b] >> 3;
		}
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
		this.corners = 0;
		this.records = 0;
		this.styles.clear();
		this.palette = 0;
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
		b.put((byte) (side >> 16)).put((byte) (side >> 8)).put((byte) ((flags & 7) == SMOOTH ? side : this.season)).put((byte) sideAlpha);
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

	/** The sky light a pack's vertex carries for a sample whose land hides {@code occlusion} 255ths of the sky. */
	private static int packSky(int occlusion) {
		return Math.max(8, 15 - Math.round(occlusion * 7 / 255.0F));
	}

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
		int sky = packSky(this.lightFlags >> 8 & 0xF8);
		int material;
		if ((flags & WATER) != 0) {
			material = PACK_WATER;
		} else {
			int id = (side >> 16 & 0xFF) | (side & 0xFF00);
			java.util.function.IntUnaryOperator materials = packMaterials;
			material = id == 0 || materials == null ? PACK_STONE : materials.applyAsInt(id);
		}
		if (this.season != 0) {
			top = dev.voidmc.lod.api.LodApi.seasonColour(top & 0xFFFFFF, this.season, this.seasonTable);
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

	/**
	 * A compact quad's corners in the order {@link Blocky} emits them unrotated, per face: each corner's place along
	 * the face's two axes ({@code u | v << 1}; u along x, or z on west and east walls; v along z on tops and
	 * undersides, up on walls). The shader holds the same table.
	 */
	private static final int[] CORNERS = {
		0, 2, 3, 1, // up
		0, 0, 0, 0, // (smooth)
		2, 3, 1, 0, // north
		3, 2, 0, 1, // south
		3, 2, 0, 1, // west
		2, 3, 1, 0, // east
		0, 1, 3, 2, // down
		0, 0, 0, 0,
	};
	/**
	 * Compact quads whose corners came in no order {@link #CORNERS} knows, or that a record cannot hold (a top shaded
	 * corner by corner bigger than a cell, a size or a place out of range, more styles than {@link #STYLE_BITS} can
	 * pick), drawn wrong: for tests, which want none.
	 */
	static final java.util.concurrent.atomic.AtomicInteger MISFITS = new java.util.concurrent.atomic.AtomicInteger();
	/**
	 * For tests: told each compact quad as it goes into its record, before it is packed: per corner in drawing order its
	 * x, y, z, colour and occlusion level, then the face, the block light, the horizons, the occlusion and the season.
	 */
	static volatile java.util.function.Consumer<int[]> recordTrace;
	private final int[] cornerX = new int[4], cornerY = new int[4], cornerZ = new int[4], cornerFace = new int[4];
	private final int[] cornerRgb = new int[4], cornerAo = new int[4];
	private int corners;
	/** Compact records written so far, and each one's y (whole blocks), made relative once the tile's lowest is known. */
	private int records;
	private int[] recordY = new int[4096];
	/** This tile's palette: each style to its index, and the entries in order ({@link #COMPACT_BYTES}). */
	private final it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap styles = new it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap();
	private long[] paletteEntries = new long[1024];
	private int palette;

	{
		this.styles.defaultReturnValue(-1);
	}

	/** A compact vertex: kept until its quad's four are in, then written as one record ({@link #RECORD_BYTES}). */
	private void compactVertex(int x, float y, int z, int flags, int top, int topAlpha) {
		// blocky surfaces stand on whole blocks
		int yb = Math.max(-8192, Math.min(8191, Math.round(y)));
		int k = this.corners++;
		this.cornerX[k] = x;
		this.cornerY[k] = yb;
		this.cornerZ[k] = z;
		this.cornerFace[k] = flags & 15;
		this.cornerRgb[k] = (top >> 19 & 31) | (top >> 11 & 31) << 5 | (top >> 3 & 31) << 10;
		// tops carry the corner occlusion's level; walls have no floor shading out here
		this.cornerAo[k] = (flags & 7) == UP ? aoLevel(topAlpha) : 0;
		this.vertices++;
		if (yb < this.minY) {
			this.minY = yb;
		}
		if (yb > this.maxY) {
			this.maxY = yb;
		}
		if (k == 3) {
			this.corners = 0;
			this.compactRecord();
		}
	}

	private void compactRecord() {
		if (this.out.remaining() < RECORD_BYTES) {
			ByteBuffer bigger = ByteBuffer.allocateDirect(this.out.capacity() * 2).order(ByteOrder.nativeOrder());
			this.out.flip();
			bigger.put(this.out);
			this.out = bigger;
		}
		int face = this.cornerFace[0], f = face & 7;
		int[] us = f == WEST || f == EAST ? this.cornerZ : this.cornerX;
		int[] vs = f == UP || f == DOWN ? this.cornerZ : this.cornerY;
		int x0 = min(this.cornerX), y0 = min(this.cornerY), z0 = min(this.cornerZ), u0 = min(us), v0 = min(vs);
		int[] codes = new int[4];
		for (int k = 0; k < 4; k++) {
			codes[k] = (us[k] != u0 ? 1 : 0) | (vs[k] != v0 ? 2 : 0);
		}
		int rot = -1;
		for (int r = 0; r < 4 && rot < 0; r++) {
			boolean fits = true;
			for (int k = 0; k < 4 && fits; k++) {
				fits = codes[k] == CORNERS[f * 4 + (k + r & 3)];
			}
			if (fits) {
				rot = r;
			}
		}
		boolean misfit = rot < 0;
		if (misfit) {
			rot = 0;
		}
		int du = max(us) - u0, dv = max(vs) - v0;
		// walls run from the colour along their top (A) to the one along their foot (B); tops and undersides are one colour
		int high = this.cornerRgb[0], low = high;
		int ao = 0;
		for (int k = 0; k < 4; k++) {
			if ((codes[k] & 2) != 0) {
				high = this.cornerRgb[k];
			} else if (f != UP && f != DOWN) {
				low = this.cornerRgb[k];
			}
			ao |= this.cornerAo[k] << 2 * (k + rot & 3);
		}
		int shape;
		if (f == UP || f == DOWN) {
			high = low = this.cornerRgb[0];
			if (ao == (ao & 3) * 0x55) {
				// one shade all over: the diagonal it splits along changes nothing
				shape = du - 1 | dv - 1 << 6 | (ao & 3) << 12;
				misfit |= du < 1 || du > 64 || dv < 1 || dv > 64;
			} else {
				shape = ao | rot << 8 | 1 << 18;
				misfit |= du != 1 || dv != 1;
			}
		} else {
			// a wall's colour runs straight down it: either diagonal draws it alike
			shape = du - 1 | dv << 6;
			misfit |= du < 1 || du > 64 || dv < 1 || dv > 8191;
		}
		// a wall on the far edge of its cells (south, east) stands one past them
		int x = x0 - (f == EAST ? 1 : 0), z = z0 - (f == SOUTH ? 1 : 0);
		misfit |= x < 0 || x > 63 || z < 0 || z > 63;
		int east = this.lightX >> 12 & 15, west = this.lightZ >> 12 & 15, occ = this.lightFlags >> 11 & 31;
		long style = (high | low << 15 | (this.lightBlock >> 2) << 30) & 0xFFFFFFFFL
			| (long) (east | west << 4 | occ << 8 | (this.season & 127) << 13 | (face & WATER) << 17) << 32;
		int index = this.styles.get(style);
		if (index < 0) {
			index = this.palette;
			if (index >= 1 << STYLE_BITS) {
				misfit = true;
				index = 0;
			} else {
				if (index == this.paletteEntries.length) {
					this.paletteEntries = java.util.Arrays.copyOf(this.paletteEntries, index * 2);
				}
				this.paletteEntries[index] = style;
				this.palette++;
				this.styles.put(style, index);
			}
		}
		if (misfit) {
			MISFITS.incrementAndGet();
		}
		java.util.function.Consumer<int[]> trace = recordTrace;
		if (trace != null) {
			trace.accept(new int[] {this.cornerX[0], this.cornerY[0], this.cornerZ[0], this.cornerRgb[0], this.cornerAo[0], this.cornerX[1],
				this.cornerY[1], this.cornerZ[1], this.cornerRgb[1], this.cornerAo[1], this.cornerX[2], this.cornerY[2], this.cornerZ[2],
				this.cornerRgb[2], this.cornerAo[2], this.cornerX[3], this.cornerY[3], this.cornerZ[3], this.cornerRgb[3], this.cornerAo[3], face,
				this.lightBlock >> 2, east, west, occ, this.season & 127});
		}
		if (this.records == this.recordY.length) {
			this.recordY = java.util.Arrays.copyOf(this.recordY, this.records * 2);
		}
		this.recordY[this.records++] = y0;
		this.out.putInt(index | (x & 63) << 17 | (z & 63) << 23 | f << 29);
		// y goes in once the tile's lowest is known (finishCompact)
		this.out.putInt((shape & 0x7FFFF) << 13);
	}

	/** Puts each compact record's y in, above the tile's lowest, and the palette after the records. */
	private void finishCompact() {
		for (int q = 0; q < this.records; q++) {
			int y = this.recordY[q] - this.minY;
			if (y > 8191) {
				MISFITS.incrementAndGet();
				y = 8191;
			}
			int at = q * RECORD_BYTES + 4;
			this.out.putInt(at, this.out.getInt(at) | y);
		}
		if (this.out.remaining() < this.palette * RECORD_BYTES) {
			ByteBuffer bigger = ByteBuffer.allocateDirect(this.out.position() + this.palette * RECORD_BYTES).order(ByteOrder.nativeOrder());
			this.out.flip();
			bigger.put(this.out);
			this.out = bigger;
		}
		for (int i = 0; i < this.palette; i++) {
			long style = this.paletteEntries[i];
			this.out.putInt((int) style).putInt((int) (style >>> 32));
		}
	}

	private static int min(int[] a) {
		return Math.min(Math.min(a[0], a[1]), Math.min(a[2], a[3]));
	}

	private static int max(int[] a) {
		return Math.max(Math.max(a[0], a[1]), Math.max(a[2], a[3]));
	}

	/**
	 * Corner {@code corner} (in drawing order) of the compact record whose words are {@code w0} and {@code w1}, in a
	 * tile whose lowest is {@code minY}, into {@code out}: x, y (whole blocks) and z, its canonical corner, and its
	 * occlusion level (on a top). As the shader does it.
	 */
	static void recordCorner(int w0, int w1, int minY, int corner, int[] out) {
		int face = w0 >>> 29;
		boolean flat = face == UP || face == DOWN, corners = flat && w1 < 0;
		int c = corner + (corners ? w1 >> 21 & 3 : 0) & 3;
		int code = CORNERS[face * 4 + c];
		int du = corners ? 1 : (w1 >> 13 & 63) + 1;
		int dv = corners ? 1 : flat ? (w1 >> 19 & 63) + 1 : w1 >>> 19;
		int x = (w0 >> 17 & 63) + (face == EAST ? 1 : 0), z = (w0 >> 23 & 63) + (face == SOUTH ? 1 : 0), y = minY + (w1 & 8191);
		du *= code & 1;
		dv *= code >> 1;
		if (flat) {
			x += du;
			z += dv;
		} else if (face == WEST || face == EAST) {
			z += du;
			y += dv;
		} else {
			x += du;
			y += dv;
		}
		out[0] = x;
		out[1] = y;
		out[2] = z;
		out[3] = c;
		out[4] = face != UP ? 0 : corners ? w1 >> 13 + 2 * c & 3 : w1 >> 25 & 3;
	}

	/**
	 * The style of quad {@code quad} of compact {@code mesh}, its palette entry: the first word in the low half (colour
	 * A, colour B, block light), the second in the high half (horizons, occlusion, season code, water). For tests and tools.
	 */
	static long style(Mesh mesh, int quad) {
		ByteBuffer v = mesh.vertices();
		int at = (mesh.quads() + (v.getInt(quad * RECORD_BYTES) & (1 << STYLE_BITS) - 1)) * RECORD_BYTES;
		return v.getInt(at) & 0xFFFFFFFFL | (long) v.getInt(at + 4) << 32;
	}

	/**
	 * Reads vertex {@code i} of {@code mesh} back, whichever way it is packed, into {@code out}: x and z in cells, y in
	 * eighths of a block, and the flags (face, water, void). For tests and tools.
	 */
	public static void decode(Mesh mesh, int i, int[] out) {
		ByteBuffer v = mesh.vertices();
		if (mesh.compact()) {
			int w0 = v.getInt((i >> 2) * RECORD_BYTES);
			int[] corner = new int[5];
			recordCorner(w0, v.getInt((i >> 2) * RECORD_BYTES + 4), mesh.minY(), i & 3, corner);
			out[0] = corner[0];
			out[1] = corner[1] * 8;
			out[2] = corner[2];
			out[3] = w0 >>> 29 | (int) (style(mesh, i >> 2) >>> 32 + 17) & WATER;
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
		short[] occluders, boolean holes, boolean compact, int landIndices, int[] groups) {
		public Mesh(ByteBuffer vertices, int vertexCount, int indexCount, boolean smooth, int minY, int maxY, int firstIndex,
			short[] occluders, boolean holes, boolean compact, int landIndices) {
			this(vertices, vertexCount, indexCount, smooth, minY, maxY, firstIndex, occluders, holes, compact, landIndices, null);
		}

		/** The same mesh over other vertices: the copy handed to the renderer. */
		public Mesh withVertices(ByteBuffer vertices) {
			return new Mesh(vertices, this.vertexCount, this.indexCount, this.smooth, this.minY, this.maxY, this.firstIndex, this.occluders,
				this.holes, this.compact, this.landIndices, this.groups);
		}

		/** Bytes a vertex of this mesh takes (a compact mesh's palette left out). */
		public int vertexBytes() {
			return this.compact ? COMPACT_BYTES : VERTEX_BYTES;
		}

		/** Bytes the mesh takes on the GPU: a compact mesh's palette follows its records. */
		public int bytes() {
			return this.compact ? this.vertices.limit() : this.vertexCount * this.vertexBytes();
		}

		/** Quads, or quad-equivalents for a smooth mesh: what the statistics count. */
		public int quads() {
			return this.indexCount / 6;
		}
	}
}
