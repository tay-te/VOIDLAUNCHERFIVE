package dev.voidmc.atlas.core.detect;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A synthetic world for the detector and reveal tests: solid ground ({@code minecraft:stone} unless told otherwise) at
 * and below a ground height, air above, and any block set over that by id. Ids are registry ids; a {@code [waterlogged]}
 * suffix marks a waterlogged block. Chunks in a square range are loaded, the rest read as {@link Sig#UNKNOWN}. Blocks
 * are classified much as the client does: air open, water and anything waterlogged water, lava lava, the small
 * see-through things open, everything else solid.
 */
public final class FakeWorld implements BlockSource {
	public static final int MIN_Y = -64;
	public static final int HEIGHT = 384;
	public static final int SECTIONS = HEIGHT / 16;

	private static final Set<String> OPEN_IDS = Set.of("air", "cave_air", "void_air", "cobweb", "tripwire", "tripwire_hook", "snow", "ladder",
		"short_grass", "grass", "tall_grass", "fern", "large_fern", "dead_bush", "dandelion", "poppy", "blue_orchid", "allium", "azure_bluet",
		"oxeye_daisy", "cornflower", "lily_of_the_valley", "sunflower", "lilac", "rose_bush", "peony", "amethyst_cluster", "large_amethyst_bud",
		"medium_amethyst_bud", "small_amethyst_bud", "sculk_vein", "vine", "glow_lichen", "seagrass", "tall_seagrass", "kelp", "kelp_plant");

	private final int ground;
	private final int groundIndex;
	private int loadMin = -8;
	private int loadMax = 8;
	private final Set<Long> unloaded = new HashSet<>();
	/** Block ids by index, and the code of each. */
	private final List<String> ids = new ArrayList<>();
	private final List<Integer> codes = new ArrayList<>();
	private final Map<String, Integer> index = new HashMap<>();
	private final Map<Long, Integer> overrides = new HashMap<>();
	/** Sections (chunk x, section, chunk z) holding an override. */
	private final Set<Long> touched = new HashSet<>();
	private int topOverride = Integer.MIN_VALUE;
	private final int air;

	public FakeWorld() {
		this(63);
	}

	public FakeWorld(int ground) {
		this(ground, "minecraft:stone");
	}

	public FakeWorld(int ground, String groundId) {
		this.ground = ground;
		this.groundIndex = this.intern(groundId);
		this.air = this.intern("minecraft:air");
	}

	/** Loads only the chunks with both coordinates in [min, max]. */
	public FakeWorld load(int min, int max) {
		this.loadMin = min;
		this.loadMax = max;
		return this;
	}

	public FakeWorld unload(int cx, int cz) {
		this.unloaded.add(chunkKey(cx, cz));
		return this;
	}

	public boolean loaded(int cx, int cz) {
		return cx >= this.loadMin && cx <= this.loadMax && cz >= this.loadMin && cz <= this.loadMax && !this.unloaded.contains(chunkKey(cx, cz));
	}

	public int ground() {
		return this.ground;
	}

	public FakeWorld set(int x, int y, int z, String id) {
		this.put(x, y, z, this.intern(id));
		return this;
	}

	/** Sets every block of a box, corners inclusive, in any order. */
	public FakeWorld fill(int x0, int y0, int z0, int x1, int y1, int z1, String id) {
		int i = this.intern(id);
		for (int y = Math.min(y0, y1); y <= Math.max(y0, y1); y++) {
			for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) {
				for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) {
					this.put(x, y, z, i);
				}
			}
		}
		return this;
	}

	/** The walls, floor and roof of a box (corners inclusive), its inside left as it was. */
	public FakeWorld shell(int x0, int y0, int z0, int x1, int y1, int z1, String id) {
		int i = this.intern(id);
		for (int y = y0; y <= y1; y++) {
			for (int z = z0; z <= z1; z++) {
				for (int x = x0; x <= x1; x++) {
					if (x == x0 || x == x1 || y == y0 || y == y1 || z == z0 || z == z1) {
						this.put(x, y, z, i);
					}
				}
			}
		}
		return this;
	}

	public String id(int x, int y, int z) {
		return this.ids.get(this.indexAt(x, y, z));
	}

	@Override
	public int code(int x, int y, int z) {
		if (!this.loaded(x >> 4, z >> 4)) {
			return Sig.UNKNOWN;
		}
		if (y < MIN_Y) {
			return Sig.code(Sig.NONE, Sig.SOLID);
		}
		if (y >= MIN_Y + HEIGHT) {
			return Sig.code(Sig.NONE, Sig.OPEN);
		}
		return this.codes.get(this.indexAt(x, y, z));
	}

	/** One chunk as the structure scan reads it. */
	public ChunkBlocks chunk(int cx, int cz) {
		return new Chunk(cx, cz);
	}

	/** The code a block id is read as. */
	public static int codeOf(String id) {
		String bare = bare(id);
		return Sig.code(Sig.of(bare), base(id, bare));
	}

	private static String bare(String id) {
		int b = id.indexOf('[');
		return b < 0 ? id : id.substring(0, b);
	}

	private static int base(String id, String bare) {
		String p = bare.substring(bare.indexOf(':') + 1);
		if (p.equals("water") || id.contains("waterlogged")) {
			return Sig.WATER;
		}
		if (p.equals("lava")) {
			return Sig.LAVA;
		}
		if (OPEN_IDS.contains(p) || p.contains("torch") || p.endsWith("rail") || p.endsWith("_carpet") || p.endsWith("_banner")
			|| p.endsWith("_button") || p.endsWith("_pressure_plate") || p.endsWith("_tulip") || p.endsWith("_sapling")) {
			return Sig.OPEN;
		}
		return Sig.SOLID;
	}

	private int intern(String id) {
		Integer i = this.index.get(id);
		if (i == null) {
			i = this.ids.size();
			this.ids.add(id);
			this.codes.add(codeOf(id));
			this.index.put(id, i);
		}
		return i;
	}

	private void put(int x, int y, int z, int i) {
		this.overrides.put(blockKey(x, y, z), i);
		this.touched.add(sectionKey(x >> 4, (y - MIN_Y) >> 4, z >> 4));
		this.topOverride = Math.max(this.topOverride, y);
	}

	private int indexAt(int x, int y, int z) {
		Integer i = this.overrides.get(blockKey(x, y, z));
		if (i != null) {
			return i;
		}
		return y <= this.ground ? this.groundIndex : this.air;
	}

	private boolean isAir(int i) {
		String id = this.ids.get(i);
		return id.equals("minecraft:air") || id.equals("minecraft:cave_air") || id.equals("minecraft:void_air");
	}

	private static long blockKey(int x, int y, int z) {
		return ((long) x & 0x3FFFFFF) << 38 | ((long) z & 0x3FFFFFF) << 12 | (y - MIN_Y) & 0xFFF;
	}

	private static long chunkKey(int cx, int cz) {
		return (long) cx << 32 | cz & 0xFFFFFFFFL;
	}

	private static long sectionKey(int cx, int s, int cz) {
		return ((long) cx & 0xFFFFFF) << 40 | ((long) cz & 0xFFFFFF) << 16 | s & 0xFFFF;
	}

	private final class Chunk implements ChunkBlocks {
		private final int cx;
		private final int cz;
		private int[] surfaces;

		Chunk(int cx, int cz) {
			this.cx = cx;
			this.cz = cz;
		}

		@Override
		public int chunkX() {
			return this.cx;
		}

		@Override
		public int chunkZ() {
			return this.cz;
		}

		@Override
		public int minY() {
			return MIN_Y;
		}

		@Override
		public int sections() {
			return SECTIONS;
		}

		@Override
		public boolean count(int s, int[] counts) {
			FakeWorld w = FakeWorld.this;
			int y0 = MIN_Y + s * 16;
			if (!w.touched.contains(sectionKey(this.cx, s, this.cz))) {
				// untouched: all ground, all air, or ground under air
				int solid = Math.max(0, Math.min(16, w.ground - y0 + 1));
				if (solid > 0) {
					counts[Sig.signal(w.codes.get(w.groundIndex))] += solid * 256;
				}
				return solid > 0;
			}
			boolean any = false;
			for (int y = y0; y < y0 + 16; y++) {
				for (int lz = 0; lz < 16; lz++) {
					for (int lx = 0; lx < 16; lx++) {
						int i = w.indexAt((this.cx << 4) + lx, y, (this.cz << 4) + lz);
						if (!w.isAir(i)) {
							any = true;
						}
						counts[Sig.signal(w.codes.get(i))]++;
					}
				}
			}
			return any;
		}

		@Override
		public boolean mayHaveAnchor(int s) {
			FakeWorld w = FakeWorld.this;
			if (!w.touched.contains(sectionKey(this.cx, s, this.cz))) {
				return Sig.isAnchor(Sig.signal(w.codes.get(w.groundIndex)));
			}
			int y0 = MIN_Y + s * 16;
			for (int y = y0; y < y0 + 16; y++) {
				for (int lz = 0; lz < 16; lz++) {
					for (int lx = 0; lx < 16; lx++) {
						int sig = Sig.signal(w.code((this.cx << 4) + lx, y, (this.cz << 4) + lz));
						if (sig != Sig.NONE && Sig.isAnchor(sig)) {
							return true;
						}
					}
				}
			}
			return false;
		}

		@Override
		public int code(int lx, int y, int lz) {
			return FakeWorld.this.code((this.cx << 4) + lx, y, (this.cz << 4) + lz);
		}

		@Override
		public int surface(int lx, int lz) {
			if (this.surfaces == null) {
				this.surfaces = new int[256];
				FakeWorld w = FakeWorld.this;
				int top = Math.min(MIN_Y + HEIGHT - 1, Math.max(w.ground, w.topOverride));
				for (int z = 0; z < 16; z++) {
					for (int x = 0; x < 16; x++) {
						int found = MIN_Y - 1;
						for (int y = top; y >= MIN_Y; y--) {
							int i = w.indexAt((this.cx << 4) + x, y, (this.cz << 4) + z);
							if (Sig.solid(w.codes.get(i)) && !bare(w.ids.get(i)).endsWith("_leaves")) {
								found = y;
								break;
							}
						}
						this.surfaces[z << 4 | x] = found;
					}
				}
			}
			return this.surfaces[lz << 4 | lx];
		}
	}
}
