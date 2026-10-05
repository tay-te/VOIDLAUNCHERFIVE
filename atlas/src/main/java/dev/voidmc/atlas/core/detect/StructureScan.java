package dev.voidmc.atlas.core.detect;

import java.util.Arrays;
import java.util.function.Consumer;

/**
 * Finds structures in one chunk's blocks, on a worker thread, cheaply and conservatively: a false landmark is worse than
 * a missed one.
 *
 * <p>How: every section's palette is counted (no block is visited), which is all the big structures need — an ocean
 * monument is a chunk full of prismarine, an ancient city has reinforced deepslate, nothing else does. Only sections
 * whose palette holds an <em>anchor</em> ({@link Sig#ANCHORS}: a bell, a spawner, crying obsidian...) are walked block by
 * block, and each anchor found is judged by what stands round it, through {@link BlockSource} (which may reach into the
 * neighbouring chunks): a spawner in a room of mossy cobblestone is a dungeon, crying obsidian set in obsidian is a
 * ruined portal, a chest among drowned planks is a shipwreck. Villages need a wider look (beds and job sites within 48
 * blocks of a bell), so a chunk's bells and its beds and job sites are handed on in its {@link Tally} for
 * {@link Detections} to weigh over several chunks.
 *
 * <p>Every rule asks for a combination of blocks that does not occur in nature and is unlikely in a build; the rules
 * and their reasons are in {@code docs/DISCOVERY.md}. Not thread-safe: one per worker.
 */
public final class StructureScan {
	/** Extra slots in the box counts: water (including waterlogged blocks) and open space. */
	static final int WATER_N = Sig.COUNT;
	static final int OPEN_N = Sig.COUNT + 1;
	private static final int MAX_ANCHORS = 64;
	/** Depth below the ground at which a structure counts as underground. */
	public static final int UNDERGROUND_DEPTH = 10;

	/** What a chunk holds of a village: beds, job sites (and which kinds), and its bells (x, y, z, underground 0/1 each). */
	public record Tally(int beds, int jobs, int jobKinds, int[] bells) {
		public static final Tally EMPTY = new Tally(0, 0, 0, new int[0]);

		public boolean isEmpty() {
			return this.beds == 0 && this.jobs == 0 && this.bells.length == 0;
		}
	}

	private final int[] counts = new int[Sig.COUNT];
	private final int[] section = new int[Sig.COUNT];
	private final long[] ySum = new long[Sig.COUNT];
	private final int[] box = new int[Sig.COUNT + 2];
	private final int[] anchorsSeen = new int[Sig.COUNT];
	/** Sightings made in this chunk so far: kind, x, z (anchors near one already made are not judged again). */
	private int[] found = new int[24];
	private int foundCount;
	private int[] bells = new int[16];
	private int bellCount;

	/**
	 * Scans a chunk: hands every structure it sees to {@code out} and returns what it holds of a village.
	 * {@code ceiling}: the dimension has a roof (the Nether), so nothing in it counts as underground.
	 */
	public Tally scan(ChunkBlocks c, BlockSource world, boolean ceiling, Consumer<Sighting> out) {
		Arrays.fill(this.counts, 0);
		Arrays.fill(this.ySum, 0);
		Arrays.fill(this.anchorsSeen, 0);
		this.foundCount = 0;
		this.bellCount = 0;
		int x0 = c.chunkX() << 4;
		int z0 = c.chunkZ() << 4;
		int anchors = 0;
		for (int s = 0; s < c.sections(); s++) {
			Arrays.fill(this.section, 0);
			if (!c.count(s, this.section)) {
				continue;
			}
			int midY = c.minY() + s * 16 + 8;
			boolean anchorHere = false;
			for (int i = 1; i < Sig.COUNT; i++) {
				int n = this.section[i];
				if (n > 0) {
					this.counts[i] += n;
					this.ySum[i] += (long) n * midY;
					anchorHere |= Sig.isAnchor(i);
				}
			}
			if (!anchorHere || !c.mayHaveAnchor(s) || anchors >= MAX_ANCHORS) {
				continue;
			}
			int baseY = c.minY() + s * 16;
			for (int y = baseY; y < baseY + 16 && anchors < MAX_ANCHORS; y++) {
				for (int lz = 0; lz < 16; lz++) {
					for (int lx = 0; lx < 16; lx++) {
						int sig = Sig.signal(c.code(lx, y, lz));
						if (sig != Sig.NONE && Sig.isAnchor(sig) && ++this.anchorsSeen[sig] <= 8) {
							// a handful of each kind of anchor per chunk is plenty (a geode has hundreds of buds, a pyramid
							// nine TNT); only those judged count against the chunk's budget
							anchors++;
							this.anchor(sig, x0 + lx, y, z0 + lz, c, world, ceiling, out);
						}
					}
				}
			}
		}
		this.countRules(c, ceiling, out);
		if (!ceiling) {
			this.caveMouth(c, out);
		}
		int beds = this.counts[Sig.BED];
		int jobs = 0;
		int kinds = 0;
		for (int j = Sig.FIRST_JOB; j <= Sig.LAST_JOB; j++) {
			if (this.counts[j] > 0) {
				jobs += this.counts[j];
				kinds |= 1 << (j - Sig.FIRST_JOB);
			}
		}
		if (beds == 0 && jobs == 0 && this.bellCount == 0) {
			return Tally.EMPTY;
		}
		return new Tally(beds, jobs, kinds, Arrays.copyOf(this.bells, this.bellCount * 4));
	}

	/** How many blocks of a signal the chunk holds (after {@link #scan}). */
	public int count(int signal) {
		return this.counts[signal];
	}

	// --- anchors ---------------------------------------------------------------------------------------------------

	private void anchor(int sig, int x, int y, int z, ChunkBlocks c, BlockSource w, boolean ceiling, Consumer<Sighting> out) {
		switch (sig) {
			case Sig.BELL -> {
				if (this.bellCount * 4 + 4 > this.bells.length) {
					this.bells = Arrays.copyOf(this.bells, this.bells.length * 2);
				}
				this.bells[this.bellCount * 4] = x;
				this.bells[this.bellCount * 4 + 1] = y;
				this.bells[this.bellCount * 4 + 2] = z;
				this.bells[this.bellCount * 4 + 3] = !ceiling && underground(c, x & 15, y, z & 15) ? 1 : 0;
				this.bellCount++;
			}
			case Sig.CRYING_OBSIDIAN -> {
				if (this.near(StructureKind.RUINED_PORTAL, x, z)) {
					return;
				}
				// set in a frame: obsidian (or more crying obsidian) against one of its faces...
				boolean framed = false;
				int[][] faces = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
				for (int[] f : faces) {
					int s = Sig.signal(w.code(x + f[0], y + f[1], z + f[2]));
					if (s == Sig.OBSIDIAN || s == Sig.CRYING_OBSIDIAN) {
						framed = true;
						break;
					}
				}
				if (!framed) {
					return;
				}
				// ...part of a frame of at least a few blocks, and not a bastion's treasure room
				this.box(w, x - 4, y - 4, z - 4, x + 4, y + 4, z + 4);
				// (a Nether portal's frame stands among a few polished blackstone bricks; a bastion is many, and gilded)
				if (this.box[Sig.OBSIDIAN] + this.box[Sig.CRYING_OBSIDIAN] < 5 || this.box[Sig.BLACKSTONE_BRICKS] >= 24
					|| this.box[Sig.GILDED_BLACKSTONE] > 0) {
					return;
				}
				this.emit(StructureKind.RUINED_PORTAL, x, y, z, c, ceiling, out);
			}
			case Sig.SPAWNER -> {
				// a dungeon: a small room of cobblestone with a mossy floor, the spawner in the middle
				this.box(w, x - 5, y - 1, z - 5, x + 5, y + 4, z + 5);
				if (this.box[Sig.MOSSY_COBBLE] >= 4 && this.box[Sig.MOSSY_COBBLE] + this.box[Sig.COBBLE] >= 16) {
					if (!this.near(StructureKind.DUNGEON, x, z)) {
						this.emit(StructureKind.DUNGEON, x, y, z, c, ceiling, out);
					}
				} else if (this.box[Sig.COBWEB] >= 6 && this.box[Sig.PLANKS] + this.box[Sig.WOOD_FENCE] + this.box[Sig.DARK_OAK_PLANKS] + this.box[Sig.RAIL] >= 2) {
					// a cave spider spawner in a web-choked mineshaft corridor
					if (!this.near(StructureKind.MINESHAFT, x, z)) {
						this.emit(StructureKind.MINESHAFT, x, y, z, c, ceiling, out);
					}
				} else if (this.box[Sig.NETHER_BRICKS] >= 16) {
					if (!this.near(StructureKind.FORTRESS, x, z)) {
						this.emit(StructureKind.FORTRESS, x, y, z, c, ceiling, out);
					}
				}
			}
			case Sig.END_PORTAL_FRAME -> {
				if (this.near(StructureKind.STRONGHOLD, x, z)) {
					return;
				}
				this.box(w, x - 6, y - 4, z - 6, x + 6, y + 4, z + 6);
				int bricks = this.box[Sig.STONE_BRICKS] + this.box[Sig.MOSSY_STONE_BRICKS] + this.box[Sig.CRACKED_STONE_BRICKS] + this.box[Sig.INFESTED_BRICKS];
				if (this.box[Sig.END_PORTAL_FRAME] >= 3 && bricks >= 8) {
					this.emit(StructureKind.STRONGHOLD, x, y, z, c, ceiling, out);
				}
			}
			case Sig.TRIAL_SPAWNER, Sig.VAULT -> {
				if (this.near(StructureKind.TRIAL_CHAMBERS, x, z)) {
					return;
				}
				this.box(w, x - 6, y - 3, z - 6, x + 6, y + 4, z + 6);
				if (this.box[Sig.TUFF_BRICKS] + this.box[Sig.COPPER_BUILT] >= 8) {
					this.emit(StructureKind.TRIAL_CHAMBERS, x, y, z, c, ceiling, out);
				}
			}
			case Sig.BUDDING_AMETHYST, Sig.AMETHYST_CLUSTER -> {
				if (this.near(StructureKind.GEODE, x, z)) {
					return;
				}
				// a geode's shell: amethyst inside, calcite round it
				this.box(w, x - 3, y - 3, z - 3, x + 3, y + 3, z + 3);
				if (this.box[Sig.AMETHYST_BLOCK] + this.box[Sig.BUDDING_AMETHYST] >= 4 && this.box[Sig.CALCITE] >= 2) {
					this.emit(StructureKind.GEODE, x, y, z, c, ceiling, out);
				}
			}
			case Sig.SUSPICIOUS_GRAVEL, Sig.SUSPICIOUS_SAND -> {
				if (this.near(StructureKind.TRAIL_RUINS, x, z) || this.near(StructureKind.OCEAN_RUIN, x, z)) {
					return;
				}
				this.box(w, x - 8, y - 4, z - 8, x + 8, y + 4, z + 8);
				int terracotta = this.box[Sig.TERRACOTTA] + this.box[Sig.ORANGE_TERRACOTTA] + this.box[Sig.BLUE_TERRACOTTA];
				int masonry = this.box[Sig.STONE_BRICKS] + this.box[Sig.MOSSY_STONE_BRICKS] + this.box[Sig.CRACKED_STONE_BRICKS]
					+ this.box[Sig.CHISELED_STONE_BRICKS] + this.box[Sig.SANDSTONE] + this.box[Sig.CHISELED_SANDSTONE];
				if (sig == Sig.SUSPICIOUS_GRAVEL && (terracotta >= 4 || this.box[Sig.MUD_BRICKS] >= 4) && this.box[WATER_N] < 32) {
					this.emit(StructureKind.TRAIL_RUINS, x, y, z, c, ceiling, out);
				} else if (masonry >= 6 && this.box[WATER_N] >= 24) {
					this.emit(StructureKind.OCEAN_RUIN, x, y, z, c, ceiling, out);
				}
			}
			case Sig.BLUE_TERRACOTTA -> {
				if (this.near(StructureKind.DESERT_PYRAMID, x, z)) {
					return;
				}
				// the pyramid's floor: one blue terracotta in a star of orange, in a sandstone hall
				this.box(w, x - 3, y, z - 3, x + 3, y, z + 3);
				if (this.box[Sig.ORANGE_TERRACOTTA] < 4) {
					return;
				}
				this.box(w, x - 8, y - 4, z - 8, x + 8, y + 6, z + 8);
				if (this.box[Sig.SANDSTONE] + this.box[Sig.CHISELED_SANDSTONE] >= 24) {
					this.emit(StructureKind.DESERT_PYRAMID, x, y, z, c, ceiling, out);
				}
			}
			case Sig.TNT -> {
				if (this.near(StructureKind.DESERT_PYRAMID, x, z)) {
					return;
				}
				// the pyramid's trap: a grid of TNT under a pressure plate, in sandstone
				this.box(w, x - 2, y, z - 2, x + 2, y, z + 2);
				if (this.box[Sig.TNT] < 4) {
					return;
				}
				this.box(w, x - 4, y - 2, z - 4, x + 4, y + 4, z + 4);
				if (this.box[Sig.SANDSTONE] + this.box[Sig.CHISELED_SANDSTONE] >= 12 && this.box[Sig.STONE_PRESSURE_PLATE] >= 1) {
					this.emit(StructureKind.DESERT_PYRAMID, x, y, z, c, ceiling, out);
				}
			}
			case Sig.DISPENSER, Sig.TRIPWIRE_HOOK, Sig.STICKY_PISTON -> {
				if (this.near(StructureKind.JUNGLE_TEMPLE, x, z)) {
					return;
				}
				// the jungle temple's traps and its puzzle, in a hall of mossy cobblestone
				this.box(w, x - 8, y - 4, z - 8, x + 8, y + 6, z + 8);
				if (this.box[Sig.MOSSY_COBBLE] >= 12 && this.box[Sig.COBBLE] >= 12 && this.box[Sig.CHISELED_STONE_BRICKS] >= 1) {
					this.emit(StructureKind.JUNGLE_TEMPLE, x, y, z, c, ceiling, out);
				}
			}
			case Sig.WHITE_BANNER -> {
				if (this.near(StructureKind.PILLAGER_OUTPOST, x, z)) {
					return;
				}
				// the banner on the outpost's roof, over a tower of dark oak and birch
				this.box(w, x - 9, y - 28, z - 9, x + 9, y + 2, z + 9);
				if (this.box[Sig.DARK_OAK_LOG] >= 8 && this.box[Sig.BIRCH_PLANKS] >= 8) {
					this.emit(StructureKind.PILLAGER_OUTPOST, x, y, z, c, ceiling, out);
				}
			}
			case Sig.FURNACE -> {
				if (this.near(StructureKind.IGLOO, x, z)) {
					return;
				}
				// inside a snow dome with ice windows and a white carpet
				this.box(w, x - 4, y - 2, z - 4, x + 4, y + 4, z + 4);
				if (this.box[Sig.SNOW_BLOCK] >= 20 && this.box[Sig.ICE] >= 1 && this.box[Sig.WHITE_CARPET] >= 2) {
					this.emit(StructureKind.IGLOO, x, y, z, c, ceiling, out);
				}
			}
			case Sig.CHEST -> {
				if (this.near(StructureKind.SHIPWRECK, x, z)) {
					return;
				}
				int[] wood = this.wetWood(w, x, y, z, 7);
				if (wood[0] >= 20 && wood[1] >= 8) {
					this.emit(StructureKind.SHIPWRECK, x, y, z, c, ceiling, out);
				}
			}
			default -> {
			}
		}
	}

	// --- palette counts --------------------------------------------------------------------------------------------

	private void countRules(ChunkBlocks c, boolean ceiling, Consumer<Sighting> out) {
		int[] n = this.counts;
		if (n[Sig.PRISMARINE] + n[Sig.DARK_PRISMARINE] >= 64 && (n[Sig.DARK_PRISMARINE] >= 4 || n[Sig.SEA_LANTERN] >= 2)) {
			this.emitCounted(StructureKind.MONUMENT, c, ceiling, out, Sig.PRISMARINE);
		}
		if (n[Sig.DARK_OAK_PLANKS] >= 160 && (n[Sig.COBBLE] >= 16 || n[Sig.BIRCH_PLANKS] >= 32)) {
			this.emitCounted(StructureKind.MANSION, c, ceiling, out, Sig.DARK_OAK_PLANKS);
		} else if (n[Sig.BIRCH_PLANKS] >= 40 && n[Sig.DARK_OAK_LOG] >= 16 && n[Sig.DARK_OAK_PLANKS] < 64) {
			this.emitCounted(StructureKind.PILLAGER_OUTPOST, c, ceiling, out, Sig.BIRCH_PLANKS);
		}
		if (n[Sig.REINFORCED_DEEPSLATE] >= 4
			|| n[Sig.DEEPSLATE_TILES] + n[Sig.DEEPSLATE_BRICKS] >= 48 && n[Sig.SCULK] >= 8 && n[Sig.SCULK_SHRIEKER] + n[Sig.SCULK_SENSOR] >= 1) {
			this.emitCounted(StructureKind.ANCIENT_CITY, c, ceiling, out, n[Sig.REINFORCED_DEEPSLATE] >= 4 ? Sig.REINFORCED_DEEPSLATE : Sig.DEEPSLATE_TILES);
		}
		if (n[Sig.RAIL] >= 6 && n[Sig.WOOD_FENCE] >= 4 && n[Sig.PLANKS] + n[Sig.DARK_OAK_PLANKS] >= 6 && n[Sig.COBWEB] >= 1) {
			this.emitCounted(StructureKind.MINESHAFT, c, ceiling, out, Sig.RAIL);
		}
		int bricks = n[Sig.STONE_BRICKS] + n[Sig.MOSSY_STONE_BRICKS] + n[Sig.CRACKED_STONE_BRICKS];
		if (bricks >= 96 && n[Sig.MOSSY_STONE_BRICKS] + n[Sig.CRACKED_STONE_BRICKS] >= 12
			&& (n[Sig.INFESTED_BRICKS] >= 1 || n[Sig.BOOKSHELF] >= 8 || n[Sig.IRON_BARS] >= 2 && bricks >= 160)) {
			this.emitCounted(StructureKind.STRONGHOLD, c, ceiling, out, Sig.STONE_BRICKS);
		}
		if (n[Sig.TUFF_BRICKS] >= 96 && n[Sig.COPPER_BUILT] >= 16) {
			this.emitCounted(StructureKind.TRIAL_CHAMBERS, c, ceiling, out, Sig.TUFF_BRICKS);
		}
		if (n[Sig.NETHER_BRICKS] >= 64 && (n[Sig.NETHER_BRICK_FENCE] >= 2 || n[Sig.NETHER_WART] >= 1)) {
			this.emitCounted(StructureKind.FORTRESS, c, ceiling, out, Sig.NETHER_BRICKS);
		}
		if (n[Sig.BLACKSTONE_BRICKS] >= 96 || n[Sig.BLACKSTONE_BRICKS] >= 32 && n[Sig.GILDED_BLACKSTONE] >= 1) {
			this.emitCounted(StructureKind.BASTION, c, ceiling, out, Sig.BLACKSTONE_BRICKS);
		}
		if (n[Sig.PURPUR] >= 64) {
			this.emitCounted(StructureKind.END_CITY, c, ceiling, out, Sig.PURPUR);
		}
	}

	/** A sighting at the middle of the chunk, at the mean height of the signal's blocks. */
	private void emitCounted(StructureKind kind, ChunkBlocks c, boolean ceiling, Consumer<Sighting> out, int signal) {
		int x = (c.chunkX() << 4) + 8;
		int z = (c.chunkZ() << 4) + 8;
		if (this.near(kind, x, z)) {
			return;
		}
		int y = this.counts[signal] > 0 ? (int) (this.ySum[signal] / this.counts[signal]) : c.surface(8, 8);
		this.emit(kind, x, y, z, c, ceiling, out);
	}

	private void emit(StructureKind kind, int x, int y, int z, ChunkBlocks c, boolean ceiling, Consumer<Sighting> out) {
		this.emitAt(kind, x, y, z, !ceiling && underground(c, x & 15, y, z & 15), out);
	}

	/**
	 * Whether a block lies under the ground: at least {@value #UNDERGROUND_DEPTH} blocks of natural earth over it (a
	 * structure's own roof, planks or purpur or sandstone, does not count: a mansion is on the surface, however tall).
	 */
	static boolean underground(ChunkBlocks c, int lx, int y, int lz) {
		int top = c.surface(lx, lz);
		if (top - y < UNDERGROUND_DEPTH) {
			return false;
		}
		int earth = 0;
		for (int yy = y + 1; yy <= top; yy++) {
			int code = c.code(lx, yy, lz);
			if (Sig.signal(code) == Sig.EARTH) {
				earth++;
				if (earth >= UNDERGROUND_DEPTH - 4) {
					return true;
				}
			}
		}
		return false;
	}

	/** Whether this chunk already saw {@code kind} close to (x, z). */
	private boolean near(StructureKind kind, int x, int z) {
		int r = kind.mergeRadius;
		for (int i = 0; i < this.foundCount; i++) {
			if (this.found[i * 3] == kind.ordinal()) {
				int dx = this.found[i * 3 + 1] - x;
				int dz = this.found[i * 3 + 2] - z;
				if (dx * dx + dz * dz <= r * r) {
					return true;
				}
			}
		}
		return false;
	}

	// --- cave mouths -----------------------------------------------------------------------------------------------

	/** How far into the hill an opening must run, roofed, to be a cave mouth and not an overhang. */
	static final int MOUTH_DEPTH = 6;

	private static boolean earth(int code) {
		int s = Sig.signal(code);
		return Sig.solid(code) && (s == Sig.EARTH || s == Sig.SANDSTONE || s == Sig.TERRACOTTA || s == Sig.ORANGE_TERRACOTTA || s == Sig.SNOW_BLOCK
			|| s == Sig.CALCITE || s == Sig.SMOOTH_BASALT || s == Sig.MAGMA);
	}

	/** Open at feet and head height (air, not water), at height {@code y} of a chunk-local column. */
	private static boolean walkable(ChunkBlocks c, int lx, int y, int lz) {
		return Sig.open(c.code(lx, y, lz)) && Sig.open(c.code(lx, y + 1, lz));
	}

	/**
	 * An opening in a hillside: ground in the open, and beside it a passage at least two blocks high running at least
	 * {@value #MOUTH_DEPTH} blocks into the hill under a roof of natural rock (the floor of the passage is earth or
	 * stone, so a house's doorway is not a cave). One per chunk at most, the deepest.
	 */
	private void caveMouth(ChunkBlocks c, Consumer<Sighting> out) {
		int bestDepth = 0;
		int bx = 0, by = 0, bz = 0;
		int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
		for (int lz = 0; lz < 16; lz++) {
			for (int lx = 0; lx < 16; lx++) {
				int g = c.surface(lx, lz);
				if (g < c.minY() || !walkable(c, lx, g + 1, lz) || !earth(c.code(lx, g, lz))) {
					continue;
				}
				for (int[] d : dirs) {
					int depth = 0;
					int floor = g;
					int earthFloors = 0;
					int earthRoofs = 0;
					for (int k = 1; k <= MOUTH_DEPTH + 2; k++) {
						int x = lx + d[0] * k;
						int z = lz + d[1] * k;
						if (x < 0 || z < 0 || x > 15 || z > 15) {
							break;
						}
						// the passage may climb or drop a block a step
						int at = Integer.MIN_VALUE;
						for (int dy : new int[]{0, 1, -1}) {
							if (walkable(c, x, floor + 1 + dy, z) && Sig.solid(c.code(x, floor + dy, z))) {
								at = floor + dy;
								break;
							}
						}
						// under the hill: at least five blocks of it over the passage's floor (an overhang is not a cave)
						if (at == Integer.MIN_VALUE || c.surface(x, z) < at + 6) {
							break;
						}
						floor = at;
						depth++;
						if (earth(c.code(x, at, z))) {
							earthFloors++;
						}
						// the roof over the passage: the first solid block above head height
						for (int ry = at + 3; ry <= Math.min(c.surface(x, z), at + 8); ry++) {
							int code = c.code(x, ry, z);
							if (Sig.solid(code)) {
								if (earth(code)) {
									earthRoofs++;
								}
								break;
							}
						}
					}
					if (depth >= MOUTH_DEPTH && earthFloors * 4 >= depth * 3 && earthRoofs * 4 >= depth * 3 && depth > bestDepth) {
						bestDepth = depth;
						bx = (c.chunkX() << 4) + lx + d[0];
						by = g + 1;
						bz = (c.chunkZ() << 4) + lz + d[1];
					}
				}
			}
		}
		if (bestDepth > 0 && !this.near(StructureKind.CAVE_MOUTH, bx, bz)) {
			this.emitAt(StructureKind.CAVE_MOUTH, bx, by, bz, false, out);
		}
	}

	private void emitAt(StructureKind kind, int x, int y, int z, boolean underground, Consumer<Sighting> out) {
		if (this.foundCount * 3 + 3 > this.found.length) {
			this.found = Arrays.copyOf(this.found, this.found.length * 2);
		}
		this.found[this.foundCount * 3] = kind.ordinal();
		this.found[this.foundCount * 3 + 1] = x;
		this.found[this.foundCount * 3 + 2] = z;
		this.foundCount++;
		out.accept(new Sighting(kind, x, y, z, underground));
	}

	// --- looking round an anchor -----------------------------------------------------------------------------------

	/** Counts every signal (and water, and open space) in a box, inclusive, into {@link #box}. */
	private void box(BlockSource w, int x0, int y0, int z0, int x1, int y1, int z1) {
		Arrays.fill(this.box, 0);
		for (int y = y0; y <= y1; y++) {
			for (int z = z0; z <= z1; z++) {
				for (int x = x0; x <= x1; x++) {
					int code = w.code(x, y, z);
					if (code < 0) {
						continue;
					}
					this.box[code >>> 2]++;
					int base = code & 3;
					if (base == Sig.WATER) {
						this.box[WATER_N]++;
					} else if (base == Sig.OPEN) {
						this.box[OPEN_N]++;
					}
				}
			}
		}
	}

	private static boolean wood(int sig) {
		return sig == Sig.PLANKS || sig == Sig.SHIP_WOOD || sig == Sig.WOOD_FENCE || sig == Sig.DARK_OAK_PLANKS || sig == Sig.BIRCH_PLANKS
			|| sig == Sig.DARK_OAK_LOG;
	}

	/**
	 * The wood round a chest, and how much of it is drowned (waterlogged, or under water): a shipwreck lies in the sea,
	 * a chest in a house or a dock does not sit in a hull full of water.
	 */
	private int[] wetWood(BlockSource w, int x, int y, int z, int r) {
		int total = 0;
		int wet = 0;
		for (int yy = y - 4; yy <= y + 6; yy++) {
			for (int zz = z - r; zz <= z + r; zz++) {
				for (int xx = x - r; xx <= x + r; xx++) {
					int code = w.code(xx, yy, zz);
					if (code < 0 || !wood(code >>> 2)) {
						continue;
					}
					total++;
					if ((code & 3) == Sig.WATER || Sig.water(w.code(xx, yy + 1, zz))) {
						wet++;
					}
				}
			}
		}
		return new int[]{total, wet};
	}
}
