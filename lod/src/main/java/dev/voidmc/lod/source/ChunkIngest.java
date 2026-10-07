package dev.voidmc.lod.source;

import dev.voidmc.lod.core.ChunkColumns;
import dev.voidmc.lod.core.ColumnStore;
import dev.voidmc.lod.core.Surface;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FluidState;

/**
 * Reads the chunks the client has into the LOD's {@link ColumnStore}, so the far view shows the world as it is,
 * with its builds, its dug-out land, its villages and its structures, and not only as it was generated. On a
 * server it is the only terrain the LOD has.
 *
 * <p>Each column is followed down from its highest block: past plants, torches and thin snow (which only colours
 * the ground), through leaves (the canopy: where its top is, and what leaves) and the trunks under them, through
 * water or lava (its surface), to the first solid block, which is the ground; then a few blocks further for the
 * soil and the rock under it. What those blocks look like (their textures' colours, tinted for the column's biome,
 * and their material ids) is worked out once per kind of column ({@link Surface}) and shared by every column and
 * chunk of that kind, so most columns cost a dozen block reads and a short palette search: 0.04 ms a chunk of
 * plain land, 0.13 ms of forest.
 *
 * <p>Client thread only: it reads the client's chunks and the loaded textures. Chunks queue as they arrive and are
 * read within a time budget each frame; a chunk whose blocks change is read again once the changes have been
 * quiet for a moment, so an explosion or a long /fill is one read, not thousands.
 */
public final class ChunkIngest {
	/** How far down a column is followed: through deep water to its bed, through a tall canopy to the ground. */
	private static final int MAX_WALK = 128;
	/** How far under the surface the soil and the rock under it are looked for. */
	private static final int SOIL_WALK = 4;
	private static final int ROCK_WALK = 10;
	/** A changed chunk is read again once its changes have been quiet this long, or this long after the first. */
	private static final long QUIET_NANOS = 750_000_000L;
	private static final long LONGEST_NANOS = 4_000_000_000L;
	private static final long SETTLE_NANOS = 100_000_000L;
	private static final int NONE = Integer.MIN_VALUE;

	/** One kind of column: the blocks it is made of, in its biome. */
	private record Kind(BlockState top, BlockState side, BlockState soil, BlockState rock, BlockState leaves, int fluid, int soilDepth,
		Biome biome) {
	}

	private final ClientLevel level;
	private final ColumnStore store;
	private final Materials materials;
	private final TextureColors textures = new TextureColors();
	private final LongLinkedOpenHashSet queue = new LongLinkedOpenHashSet();
	/** Chunks with block changes waiting to settle: when their first and their latest change came. */
	private final Long2LongOpenHashMap firstChange = new Long2LongOpenHashMap();
	private final Long2LongOpenHashMap lastChange = new Long2LongOpenHashMap();
	private long settledNanos;
	private final Map<Kind, Surface> surfaces = new HashMap<>();

	// one chunk's palette, reused chunk to chunk: the kinds as fields, so finding a column's needs no allocation
	private final int[] columns = new int[256];
	private final byte[] light = new byte[256];
	private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
	private final BlockState[] kindTop = new BlockState[ChunkColumns.MAX_PALETTE];
	private final BlockState[] kindSide = new BlockState[ChunkColumns.MAX_PALETTE];
	private final BlockState[] kindSoil = new BlockState[ChunkColumns.MAX_PALETTE];
	private final BlockState[] kindRock = new BlockState[ChunkColumns.MAX_PALETTE];
	private final BlockState[] kindLeaves = new BlockState[ChunkColumns.MAX_PALETTE];
	private final int[] kindFluid = new int[ChunkColumns.MAX_PALETTE];
	private final int[] kindDepth = new int[ChunkColumns.MAX_PALETTE];
	private final Biome[] kindBiome = new Biome[ChunkColumns.MAX_PALETTE];
	private final Surface[] palette = new Surface[ChunkColumns.MAX_PALETTE];
	private int kinds;
	private int lastKind;

	// statistics
	public long chunks;
	public long unchanged;
	public long nanos;
	public long maxNanos;

	public ChunkIngest(ClientLevel level, ColumnStore store, Materials materials) {
		this.level = level;
		this.store = store;
		this.materials = materials;
	}

	public ColumnStore store() {
		return this.store;
	}

	/** A chunk arrived. */
	public void loaded(int cx, int cz) {
		this.queue.add(ChunkPos.pack(cx, cz));
	}

	/** Queues every chunk the client already holds within {@code radius} chunks: those that came before this did. */
	public void loadedAround(int cx, int cz, int radius) {
		for (int r = 0; r <= radius; r++) {
			for (int dz = -r; dz <= r; dz++) {
				for (int dx = -r; dx <= r; dx++) {
					if ((Math.abs(dx) == r || Math.abs(dz) == r)
						&& this.level.getChunkSource().getChunk(cx + dx, cz + dz, ChunkStatus.FULL, false) != null) {
						this.loaded(cx + dx, cz + dz);
					}
				}
			}
		}
	}

	/** A block changed at {@code (x, z)}: its chunk is read again once the changes there settle. */
	public void changed(int x, int z) {
		long key = ChunkPos.pack(x >> 4, z >> 4);
		long now = System.nanoTime();
		this.lastChange.put(key, now);
		this.firstChange.putIfAbsent(key, now);
	}

	public int queued() {
		return this.queue.size() + this.lastChange.size();
	}

	/** Reads queued chunks for up to {@code budgetNanos} (at least one, if any is waiting). */
	public void run(long budgetNanos) {
		long start = System.nanoTime();
		if (!this.lastChange.isEmpty() && start - this.settledNanos > SETTLE_NANOS) {
			this.settledNanos = start;
			this.settle(start);
		}
		while (!this.queue.isEmpty()) {
			long key = this.queue.removeFirstLong();
			LevelChunk chunk = this.level.getChunkSource().getChunk(ChunkPos.getX(key), ChunkPos.getZ(key), ChunkStatus.FULL, false);
			if (chunk == null) {
				// gone again before its turn: it is read when it comes back
				continue;
			}
			long t0 = System.nanoTime();
			this.ingest(chunk);
			long t = System.nanoTime() - t0;
			this.nanos += t;
			this.maxNanos = Math.max(this.maxNanos, t);
			this.chunks++;
			if (t0 + t - start > budgetNanos) {
				return;
			}
		}
	}

	private void settle(long now) {
		var it = this.lastChange.long2LongEntrySet().fastIterator();
		while (it.hasNext()) {
			var e = it.next();
			long key = e.getLongKey();
			if (now - e.getLongValue() >= QUIET_NANOS || now - this.firstChange.get(key) >= LONGEST_NANOS) {
				this.queue.add(key);
				this.firstChange.remove(key);
				it.remove();
			}
		}
	}

	/** Reads one chunk's columns into the store. */
	public void ingest(LevelChunk chunk) {
		LevelChunkSection[] sections = chunk.getSections();
		int minY = chunk.getMinY();
		this.kinds = 0;
		this.lastKind = -1;
		boolean anyUnder = false;
		for (int z = 0; z < 16; z++) {
			for (int x = 0; x < 16; x++) {
				this.lastUnder = 0;
				int packed = this.column(chunk, sections, minY, x, z);
				this.columns[x + z * 16] = packed;
				this.under[x + z * 16] = this.lastUnder;
				anyUnder |= this.lastUnder != 0;
				this.light[x + z * 16] = packed == ChunkColumns.VOID ? 0 : this.light(chunk, x, z, ChunkColumns.ground(packed));
			}
		}
		ChunkColumns data = new ChunkColumns(this.columns.clone(), Arrays.copyOf(this.palette, this.kinds), this.light.clone(),
			anyUnder ? this.under.clone() : null);
		Arrays.fill(this.palette, 0, this.kinds, null);
		Arrays.fill(this.kindBiome, 0, this.kinds, null);
		ChunkPos pos = chunk.getPos();
		if (!this.store.put(pos.x(), pos.z(), data)) {
			this.unchanged++;
		}
	}

	/**
	 * The block light over a column: on its ground (a path by a torch, the floor of a lit yard) or on whatever stands
	 * highest there (a roof under a lantern, a torch on a wall), whichever is brighter.
	 */
	private byte light(LevelChunk chunk, int x, int z, int ground) {
		ChunkPos c = chunk.getPos();
		int wx = c.getMinBlockX() + x, wz = c.getMinBlockZ() + z;
		// the air over the highest block, and over the ground (the first y above it)
		int over = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) + 1;
		int a = this.level.getBrightness(LightLayer.BLOCK, this.pos.set(wx, ground, wz));
		int b = over == ground ? a : this.level.getBrightness(LightLayer.BLOCK, this.pos.set(wx, over, wz));
		return (byte) Math.max(a, b);
	}

	private static BlockState state(LevelChunkSection[] sections, int minY, int x, int y, int z) {
		return sections[(y - minY) >> 4].getBlockState(x, y & 15, z);
	}

	private int column(LevelChunk chunk, LevelChunkSection[] sections, int minY, int x, int z) {
		int top = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
		if (top < minY) {
			return ChunkColumns.VOID;
		}
		int leavesTop = NONE;
		int waterTop = NONE;
		int fluid = 0;
		BlockState leaves = null;
		BlockState cover = null;
		BlockState ground = null;
		int floor = Math.max(minY, top - MAX_WALK);
		int y = top;
		for (; y >= floor; y--) {
			BlockState state = state(sections, minY, x, y, z);
			if (state.isAir()) {
				continue;
			}
			if (state.is(BlockTags.LEAVES)) {
				if (leavesTop == NONE) {
					leavesTop = y;
					leaves = seen(state);
				}
				// snow on a canopy is the canopy's, not the ground's
				cover = null;
				continue;
			}
			FluidState fluidState = state.getFluidState();
			if (!fluidState.isEmpty() && !state.isSolid()) {
				// water and what grows in it (kelp, seagrass); a waterlogged solid block is ground
				if (waterTop == NONE) {
					waterTop = y;
					fluid = fluidState.is(FluidTags.LAVA) ? 2 : 1;
				}
				continue;
			}
			if (leaves != null && state.is(BlockTags.LOGS)) {
				// a trunk under its crown
				continue;
			}
			if (!state.isSolid()) {
				// plants, torches, rails, thin snow: only snow shows from afar, as the ground's colour
				if (waterTop == NONE && state.getBlock() instanceof SnowLayerBlock) {
					cover = state;
				}
				continue;
			}
			ground = seen(state);
			break;
		}
		if (ground == null) {
			if (floor == minY && waterTop == NONE && leavesTop == NONE) {
				// nothing solid down to the bottom of the world
				return ChunkColumns.VOID;
			}
			// deeper than the walk goes: call it ground just under it
			ground = waterTop != NONE ? Blocks.GRAVEL.defaultBlockState() : Blocks.STONE.defaultBlockState();
			y = floor - 1;
		}
		int groundY = y;
		// the soil (the first solid block under the surface), how deep it goes, and the rock under it
		BlockState soil = ground;
		int sy = groundY - 1;
		for (int lim = Math.max(minY, groundY - SOIL_WALK); sy >= lim; sy--) {
			BlockState state = state(sections, minY, x, sy, z);
			if (state.isSolid()) {
				soil = seen(state);
				break;
			}
		}
		Block soilBlock = soil.getBlock();
		BlockState rock = null;
		int soilDepth = 0;
		for (int lim = Math.max(minY, groundY - ROCK_WALK); sy >= lim; sy--) {
			BlockState state = state(sections, minY, x, sy, z);
			if (state.getBlock() == soilBlock) {
				soilDepth++;
			} else if (state.isSolid()) {
				rock = seen(state);
				break;
			}
		}
		if (rock == null) {
			rock = soil;
		}
		int depth = waterTop != NONE ? waterTop - groundY : 0;
		// a crown under water is weed: the water is what shows
		int canopy = leavesTop != NONE && leavesTop > waterTop ? leavesTop - groundY : 0;
		if (canopy == 0) {
			leaves = null;
		}
		Biome biome = chunk.getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(groundY), QuartPos.fromBlock(z)).value();
		int kind = this.kind(cover != null ? cover : ground, ground, soil, rock, leaves, fluid, Math.min(soilDepth, 4), biome);
		if (y >= floor && canopy == 0) {
			this.under(sections, minY, x, z, groundY, biome);
		}
		return ChunkColumns.pack(groundY + 1, depth, canopy, kind);
	}

	/** An overhang at most this thick has land under it worth drawing; and the gap under it must be this open. */
	private static final int MAX_DECK = 31;
	private static final int MIN_GAP = 3;
	private static final int MAX_GAP = 64;
	private final int[] under = new int[256];
	/** The land under the overhang of the column just read ({@link ChunkColumns#packUnder}), or 0. */
	private int lastUnder;

	/**
	 * Under a column's ground (its top solid block at {@code groundY}): if that is a thin overhang (a bridge, a ledge, a
	 * roof) over an open gap, the land below, kept as the column's second surface; thick ground over a cave is not.
	 */
	private void under(LevelChunkSection[] sections, int minY, int x, int z, int groundY, Biome biome) {
		int lim = Math.max(minY, groundY - MAX_DECK);
		int by = groundY - 1;
		while (by >= lim && state(sections, minY, x, by, z).isSolid()) {
			by--;
		}
		if (by < lim) {
			return;
		}
		int deck = groundY - by;
		int glim = Math.max(minY, by - MAX_GAP);
		int gy = by;
		int waterTop = NONE;
		int fluid = 0;
		for (; gy >= glim; gy--) {
			BlockState state = state(sections, minY, x, gy, z);
			FluidState fluidState = state.getFluidState();
			if (!fluidState.isEmpty() && !state.isSolid()) {
				if (waterTop == NONE) {
					waterTop = gy;
					fluid = fluidState.is(FluidTags.LAVA) ? 2 : 1;
				}
				continue;
			}
			if (state.isSolid()) {
				break;
			}
		}
		int gap = by - gy;
		if (gy < glim || gap < MIN_GAP) {
			return;
		}
		// the open gap is what matters: water filling it up to the overhang is a dam's, not a bridge's
		int open = waterTop == NONE ? gap : by - waterTop;
		if (open < MIN_GAP) {
			return;
		}
		BlockState lower = seen(state(sections, minY, x, gy, z));
		int depth = waterTop != NONE ? waterTop - gy : 0;
		int look = this.kind(lower, lower, lower, lower, null, fluid, 1, biome);
		this.lastUnder = ChunkColumns.packUnder(deck, gap, depth, look);
	}

	/**
	 * A block as far as its looks go. Leaves all look alike whatever their distance to a log, which keeps changing
	 * for a while after a chunk is generated (and whenever a tree is cut); a waterlogged block looks like a dry one.
	 * Without this every such update would read as a change to the land.
	 */
	private static BlockState seen(BlockState state) {
		if (state.is(BlockTags.LEAVES)) {
			return state.getBlock().defaultBlockState();
		}
		return state.hasProperty(BlockStateProperties.WATERLOGGED) ? state.setValue(BlockStateProperties.WATERLOGGED, false) : state;
	}

	/** The chunk's palette index for a kind of column, adding it (and working out its looks, once ever) if new. */
	private int kind(BlockState top, BlockState side, BlockState soil, BlockState rock, BlockState leaves, int fluid, int soilDepth, Biome biome) {
		int last = this.lastKind;
		if (last >= 0 && this.is(last, top, side, soil, rock, leaves, fluid, soilDepth, biome)) {
			return last;
		}
		for (int i = 0; i < this.kinds; i++) {
			if (this.is(i, top, side, soil, rock, leaves, fluid, soilDepth, biome)) {
				this.lastKind = i;
				return i;
			}
		}
		if (this.kinds == ChunkColumns.MAX_PALETTE) {
			// more kinds of column in one chunk than a palette holds (a wall of every wool): the nearest will do
			for (int i = 0; i < this.kinds; i++) {
				if (this.kindTop[i] == top) {
					return i;
				}
			}
			return 0;
		}
		Kind key = new Kind(top, side, soil, rock, leaves, fluid, soilDepth, biome);
		Surface surface = this.surfaces.get(key);
		if (surface == null) {
			surface = this.surface(key);
			this.surfaces.put(key, surface);
		}
		int i = this.kinds++;
		this.kindTop[i] = top;
		this.kindSide[i] = side;
		this.kindSoil[i] = soil;
		this.kindRock[i] = rock;
		this.kindLeaves[i] = leaves;
		this.kindFluid[i] = fluid;
		this.kindDepth[i] = soilDepth;
		this.kindBiome[i] = biome;
		this.palette[i] = surface;
		this.lastKind = i;
		return i;
	}

	private boolean is(int i, BlockState top, BlockState side, BlockState soil, BlockState rock, BlockState leaves, int fluid, int soilDepth,
		Biome biome) {
		return this.kindTop[i] == top && this.kindSide[i] == side && this.kindSoil[i] == soil && this.kindRock[i] == rock
			&& this.kindLeaves[i] == leaves && this.kindFluid[i] == fluid && this.kindDepth[i] == soilDepth && this.kindBiome[i] == biome;
	}

	/** What a kind of column looks like: its faces' texture colours in its biome, and their material ids. */
	private Surface surface(Kind k) {
		Biome b = k.biome();
		int water;
		if (k.fluid() == 2) {
			int lava = this.textures.top(Blocks.LAVA.defaultBlockState(), b);
			water = lava == 0x7F7F7F ? 0xD96F21 : lava;
		} else {
			water = b.getWaterColor() & 0xFFFFFF;
		}
		BlockState leaves = k.leaves();
		return this.store.intern(new Surface(
			this.textures.top(k.top(), b), this.textures.side(k.side(), b), this.textures.side(k.soil(), b), this.textures.side(k.rock(), b),
			leaves == null ? 0 : this.textures.top(leaves, b), water, k.soilDepth(), k.fluid() == 2, leaves != null && coniferous(leaves),
			this.materials.id(k.top(), true), this.materials.id(k.side(), false), this.materials.id(k.soil(), false),
			this.materials.id(k.rock(), false), leaves == null ? 0 : this.materials.id(leaves, true), this.biomeId(b)));
	}

	/** A biome's {@link BiomeIds} id, by its name in the level's registry (-1 for one it does not know). */
	private int biomeId(Biome biome) {
		var key = this.level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.BIOME).getKey(biome);
		return key == null ? -1 : dev.voidmc.lod.core.BiomeIds.id(key.toString());
	}

	/** Needle trees, drawn as cones far out: by name, so modded firs and pines count too. */
	private static boolean coniferous(BlockState leaves) {
		String name = BuiltInRegistries.BLOCK.getKey(leaves.getBlock()).getPath();
		return name.contains("spruce") || name.contains("pine") || name.contains("fir") || name.contains("larch") || name.contains("cedar");
	}
}
