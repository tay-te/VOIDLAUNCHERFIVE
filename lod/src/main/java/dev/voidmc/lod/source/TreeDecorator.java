package dev.voidmc.lod.source;

import dev.voidmc.lod.core.BiomeIds;
import dev.voidmc.lod.core.ColumnSample;
import dev.voidmc.lod.core.Decorator;
import dev.voidmc.lod.core.Voxels;
import dev.voidmc.lod.mixin.ChunkGeneratorAccessor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeGenerationSettings;
import net.minecraft.world.level.biome.FeatureSorter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomSupport;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import net.minecraft.world.level.levelgen.placement.FeaturePlacer;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.material.FluidState;
import org.slf4j.LoggerFactory;

/**
 * The world's own trees, block by block, without building a chunk: for each chunk, the tree features of the biomes
 * there run exactly as the chunk generator's decoration step runs them (the same decoration seed from the world seed
 * and the chunk, the same feature seed from each feature's place in the step, the same placement modifiers), against
 * a stand-in world that answers from the terrain source: its ground height and surface block, its water, its biomes,
 * and every block the trees set so far. What they set is kept: logs and leaves, at the spots, heights and shapes the
 * chunks will have them, so the tiles past the chunks show those trees, not stand-ins.
 *
 * <p>Close rather than exact where the chunk itself differs from the terrain source (a carver, a boulder another
 * feature put down first, a tree of the neighbouring chunk the generator happened to place before this one). Chunks
 * are kept once worked out: neighbouring tiles, and the same tile rebuilt, share them. Nothing reaches the real world:
 * the stand-in hands out neither the level nor the server.
 */
public final class TreeDecorator implements Decorator {
	/** How far a tree reaches from the chunk its trunk stands in. */
	private static final int REACH = 12;
	private static final int STEP = GenerationStep.Decoration.VEGETAL_DECORATION.ordinal();
	private static final int CHUNKS_KEPT = 3072;
	private static final BlockState AIR = Blocks.AIR.defaultBlockState();
	private static final BlockState WATER = Blocks.WATER.defaultBlockState();
	private static final BlockState STONE = Blocks.STONE.defaultBlockState();
	private static final BlockState DIRT = Blocks.DIRT.defaultBlockState();

	/** The source's biome at a block. */
	public interface Biomes {
		Holder<Biome> at(int x, int y, int z);
	}

	/** One chunk's trees: positions packed as {@link BlockPos#asLong}, and what stands there. */
	record Placed(long[] positions, BlockState[] states) {
	}

	private final ServerLevel level;
	private final ChunkGenerator generator;
	private final long seed;
	private final Biomes biomes;
	private final BiomeLooks looks;
	private final Materials materials;
	private final List<PlacedFeature> features;
	private final Function<Holder<Biome>, BiomeGenerationSettings> settings;
	/** Which of the step's features place trees. */
	private final boolean[] trees;
	/** Per biome, the indices in the step of its features that place trees. */
	private final Map<Holder<Biome>, int[]> treeFeatures = new ConcurrentHashMap<>();
	private final Map<Integer, Optional<Holder<Biome>>> byId = new ConcurrentHashMap<>();
	private final Map<Long, Placed> chunks = new LinkedHashMap<>(256, 0.75F, true) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<Long, Placed> eldest) {
			return this.size() > CHUNKS_KEPT;
		}
	};
	private volatile boolean warned;
	public final AtomicLong chunksPlaced = new AtomicLong();
	public final AtomicLong chunksRead = new AtomicLong();
	/** Where the trees worked out are kept, or null: worked out again each time. */
	private volatile TreeFiles files;

	/** Keeps the trees worked out in {@code dir}, for the world and game of {@code identity}. */
	public void keepIn(java.nio.file.Path dir, long identity) {
		this.files = new TreeFiles(dir, identity);
	}
	public final AtomicLong placeNanos = new AtomicLong();
	/** Columns the stand-in asked of the ground, and the time that took (the rest of placing is the features'). */
	public final AtomicLong columnsAsked = new AtomicLong();
	public final AtomicLong groundNanos = new AtomicLong();

	private TreeDecorator(ServerLevel level, Biomes biomes, BiomeLooks looks, Materials materials, List<PlacedFeature> features,
		Function<Holder<Biome>, BiomeGenerationSettings> settings) {
		this.level = level;
		this.generator = level.getChunkSource().getGenerator();
		this.seed = level.getSeed();
		this.biomes = biomes;
		this.looks = looks;
		this.materials = materials;
		this.features = features;
		this.settings = settings;
		this.trees = new boolean[features.size()];
		for (int i = 0; i < features.size(); i++) {
			try {
				this.trees[i] = TreeKinds.hasTree(features.get(i));
			} catch (RuntimeException e) {
				this.trees[i] = false;
			}
		}
	}

	/** Whether {@code level} is the stand-in world trees are placed in here, not a real one. */
	public static boolean isStandIn(Object level) {
		return level != null && Proxy.isProxyClass(level.getClass()) && Proxy.getInvocationHandler(level) instanceof Stand;
	}

	/** For {@code level}'s generator, or null if its decoration step cannot be read. */
	public static TreeDecorator of(ServerLevel level, Biomes biomes, BiomeLooks looks, Materials materials) {
		try {
			ChunkGeneratorAccessor generator = (ChunkGeneratorAccessor) level.getChunkSource().getGenerator();
			List<FeatureSorter.StepFeatureData> steps = generator.voidLod$featuresPerStep().get();
			if (steps.size() <= STEP) {
				return null;
			}
			return new TreeDecorator(level, biomes, looks, materials, steps.get(STEP).features(), generator.voidLod$generationSettings());
		} catch (RuntimeException e) {
			LoggerFactory.getLogger("VOID LOD").warn("VOID LOD: cannot place the world's trees, keeping drawn ones", e);
			return null;
		}
	}

	@Override
	public boolean decorate(int minX, int minZ, int maxX, int maxZ, Ground ground, Voxels out) {
		this.keep(minX - REACH, minZ - REACH, maxX + REACH, maxZ + REACH, ground);
		int cx0 = Math.floorDiv(minX - REACH, 16), cx1 = Math.floorDiv(maxX - 1 + REACH, 16);
		int cz0 = Math.floorDiv(minZ - REACH, 16), cz1 = Math.floorDiv(maxZ - 1 + REACH, 16);
		Placed[] chunks = new Placed[(cx1 - cx0 + 1) * (cz1 - cz0 + 1)];
		List<Long> missing = null;
		for (int cz = cz0; cz <= cz1; cz++) {
			for (int cx = cx0; cx <= cx1; cx++) {
				Placed chunk = this.known(cx, cz);
				if (chunk == null) {
					if (missing == null) {
						missing = new ArrayList<>();
					}
					missing.add(chunkKey(cx, cz));
				}
				chunks[(cx - cx0) + (cz - cz0) * (cx1 - cx0 + 1)] = chunk;
			}
		}
		if (missing != null) {
			this.request(minX, minZ, maxX, maxZ, missing);
			return false;
		}
		ColumnSample sample = new ColumnSample();
		for (int cz = cz0; cz <= cz1; cz++) {
			for (int cx = cx0; cx <= cx1; cx++) {
				Placed chunk = chunks[(cx - cx0) + (cz - cz0) * (cx1 - cx0 + 1)];
				for (int b = 0; b < chunk.positions.length; b++) {
					long p = chunk.positions[b];
					int x = BlockPos.getX(p), y = BlockPos.getY(p), z = BlockPos.getZ(p);
					if (x < minX || x >= maxX || z < minZ || z >= maxZ) {
						continue;
					}
					BlockState state = chunk.states[b];
					boolean leaves = state.is(BlockTags.LEAVES);
					if (!leaves && !state.is(BlockTags.LOGS)) {
						continue;
					}
					sample.clear();
					this.column(x, z, ground, sample);
					Holder<Biome> biome = this.biome(sample.biome);
					Block block = state.getBlock();
					int[] colors = biome == null ? null : this.looks.treeColors(biome, block);
					if (leaves) {
						int c = colors != null ? colors[0] : biome != null ? this.looks.get(biome).leaves() : 0x48752C;
						int m = this.materials.id(block.defaultBlockState(), true);
						out.add(x, y, z, Voxels.LEAVES, (byte) 0, c, c, m, m);
					} else {
						byte axis = 0;
						if (state.hasProperty(BlockStateProperties.AXIS)) {
							Direction.Axis a = state.getValue(BlockStateProperties.AXIS);
							axis = (byte) (a == Direction.Axis.X ? 1 : a == Direction.Axis.Z ? 2 : 0);
						}
						BlockState upright = block.defaultBlockState();
						int top = colors != null ? colors[0] : 0x9A7B4F;
						int side = colors != null ? colors[1] : 0x6B5232;
						out.add(x, y, z, Voxels.LOG, axis, top, side, this.materials.id(upright, true), this.materials.id(upright, false));
					}
				}
			}
		}
		return true;
	}

	private static long chunkKey(int cx, int cz) {
		return (long) cx << 32 ^ cz & 0xFFFFFFFFL;
	}

	// ---- placing in the background ---------------------------------------------------------------------------

	/** A tile's area waiting for its chunks' trees: told ready once the last of them is placed. */
	private record Pending(String area, int minX, int minZ, int maxX, int maxZ, java.util.concurrent.atomic.AtomicInteger missing) {
	}

	private record Job(long key, double distance) {
	}

	private final java.util.concurrent.PriorityBlockingQueue<Job> queue = new java.util.concurrent.PriorityBlockingQueue<>(256,
		java.util.Comparator.comparingDouble(Job::distance));
	private final java.util.Set<Long> queued = ConcurrentHashMap.newKeySet();
	private final Map<Long, List<Pending>> waiting = new HashMap<>();
	private final Map<String, Pending> pendingAreas = new HashMap<>();
	private volatile Ready ready;
	private volatile Ground background;
	private volatile double centreX;
	private volatile double centreZ;
	private volatile boolean started;
	private static final int THREADS = Math.max(1, Math.min(3, Runtime.getRuntime().availableProcessors() / 4));

	@Override
	public void onReady(Ready ready) {
		this.ready = ready;
	}

	/** The ground trees placed in the background stand on: the terrain source, column by column. */
	public void ground(Ground ground) {
		this.background = ground;
	}

	/** Where the player is: the trees nearest them are placed first. */
	public void centre(double x, double z) {
		this.centreX = x;
		this.centreZ = z;
	}

	/** Chunks waiting to have their trees placed. */
	public int queued() {
		return this.queued.size();
	}

	private void request(int minX, int minZ, int maxX, int maxZ, List<Long> missing) {
		String area = minX + "," + minZ + "," + maxX + "," + maxZ;
		synchronized (this.waiting) {
			if (this.pendingAreas.containsKey(area)) {
				return;
			}
			Pending p = new Pending(area, minX, minZ, maxX, maxZ, new java.util.concurrent.atomic.AtomicInteger(missing.size()));
			this.pendingAreas.put(area, p);
			for (long key : missing) {
				this.waiting.computeIfAbsent(key, k -> new ArrayList<>()).add(p);
			}
		}
		for (long key : missing) {
			if (this.queued.add(key)) {
				int cx = (int) (key >> 32), cz = (int) key;
				double dx = cx * 16 + 8 - this.centreX, dz = cz * 16 + 8 - this.centreZ;
				this.queue.add(new Job(key, dx * dx + dz * dz));
			}
		}
		this.start();
	}

	private void start() {
		if (this.started) {
			return;
		}
		synchronized (this) {
			if (this.started) {
				return;
			}
			this.started = true;
			for (int i = 0; i < THREADS; i++) {
				Thread t = new Thread(this::work, "VOID LOD trees " + (i + 1));
				t.setDaemon(true);
				t.setPriority(Thread.MIN_PRIORITY);
				t.start();
			}
		}
	}

	private void work() {
		while (true) {
			Job job;
			try {
				job = this.queue.take();
			} catch (InterruptedException e) {
				return;
			}
			long key = job.key();
			int cx = (int) (key >> 32), cz = (int) key;
			try {
				Ground ground = this.background;
				if (this.known(cx, cz) == null && ground != null) {
					this.chunk(cx, cz, ground);
				}
			} catch (RuntimeException | LinkageError e) {
				LoggerFactory.getLogger("VOID LOD").warn("VOID LOD: could not place the trees of chunk {}, {}", cx, cz, e);
				synchronized (this.chunks) {
					this.chunks.put(key, new Placed(new long[0], new BlockState[0]));
				}
			} finally {
				this.queued.remove(key);
			}
			List<Pending> done;
			synchronized (this.waiting) {
				done = this.waiting.remove(key);
			}
			if (done == null) {
				continue;
			}
			for (Pending p : done) {
				if (p.missing().decrementAndGet() == 0) {
					synchronized (this.waiting) {
						this.pendingAreas.remove(p.area());
					}
					Ready r = this.ready;
					if (r != null) {
						r.ready(p.minX(), p.minZ(), p.maxX(), p.maxZ());
					}
				}
			}
		}
	}

	/** The chunk's trees if worked out already: kept in memory, or on disk. */
	private Placed known(int cx, int cz) {
		long key = chunkKey(cx, cz);
		synchronized (this.chunks) {
			Placed p = this.chunks.get(key);
			if (p != null) {
				return p;
			}
		}
		TreeFiles files = this.files;
		Placed p = files == null ? null : files.read(cx, cz);
		if (p != null) {
			this.chunksRead.incrementAndGet();
			synchronized (this.chunks) {
				this.chunks.put(key, p);
			}
		}
		return p;
	}

	private Holder<Biome> biome(int id) {
		if (id < 0) {
			return null;
		}
		return this.byId.computeIfAbsent(id, i -> {
			String name = BiomeIds.name(i);
			if (name == null) {
				return Optional.empty();
			}
			return this.level.registryAccess().lookupOrThrow(Registries.BIOME).get(ResourceKey.create(Registries.BIOME, Identifier.parse(name)))
				.map(h -> (Holder<Biome>) h);
		}).orElse(null);
	}

	/**
	 * Columns known exactly, kept by chunk for the trees of the chunks around (packed: ground, water, top material,
	 * biome, 16 bits each; {@link #UNKNOWN} where not known yet).
	 */
	private final Map<Long, long[]> ground = new LinkedHashMap<>(256, 0.75F, true) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<Long, long[]> eldest) {
			return this.size() > GROUND_KEPT;
		}
	};
	private static final int GROUND_KEPT = 6144;
	private static final long UNKNOWN = Long.MIN_VALUE;

	private long[] groundChunk(long key, boolean create) {
		synchronized (this.ground) {
			long[] g = this.ground.get(key);
			if (g == null && create) {
				g = new long[256];
				Arrays.fill(g, UNKNOWN);
				this.ground.put(key, g);
			}
			return g;
		}
	}

	private static long pack(ColumnSample s) {
		if (s.empty) {
			return UNKNOWN + 1;
		}
		int g = (int) Math.ceil(s.ground - 1e-3F);
		int water = s.water == Integer.MIN_VALUE ? -32768 : Math.max(-32767, Math.min(32767, s.water));
		return (long) (g & 0xFFFF) << 48 | (long) (water & 0xFFFF) << 32 | (long) (s.topMaterial & 0xFFFF) << 16 | s.biome & 0xFFFF;
	}

	private static void unpack(long p, ColumnSample out) {
		if (p == UNKNOWN + 1) {
			out.empty = true;
			return;
		}
		out.empty = false;
		out.ground = (short) (p >>> 48);
		short water = (short) (p >>> 32);
		out.water = water == -32768 ? Integer.MIN_VALUE : water;
		out.topMaterial = (int) (p >>> 16 & 0xFFFF);
		out.biome = (short) p;
	}

	/** Column (x, z): kept from a tile that sampled it, or asked of {@code ground} (and kept, if that is exact). */
	private void column(int x, int z, Ground ground, ColumnSample out) {
		boolean exact = ground.blockExact();
		long key = (long) (x >> 4) << 32 ^ (z >> 4) & 0xFFFFFFFFL;
		int at = (x & 15) + (z & 15) * 16;
		long[] g = exact ? this.groundChunk(key, false) : null;
		if (g != null && g[at] != UNKNOWN) {
			unpack(g[at], out);
			return;
		}
		ground.column(x, z, out);
		if (exact) {
			this.groundChunk(key, true)[at] = pack(out);
		}
	}

	/** Keeps the columns {@code ground} knows exactly over [minX, maxX) x [minZ, maxZ). */
	private void keep(int minX, int minZ, int maxX, int maxZ, Ground ground) {
		if (!ground.blockExact()) {
			return;
		}
		ColumnSample s = new ColumnSample();
		for (int cz = minZ >> 4; cz <= (maxZ - 1) >> 4; cz++) {
			for (int cx = minX >> 4; cx <= (maxX - 1) >> 4; cx++) {
				long[] g = this.groundChunk((long) cx << 32 ^ cz & 0xFFFFFFFFL, true);
				for (int z = Math.max(minZ, cz * 16); z < Math.min(maxZ, cz * 16 + 16); z++) {
					for (int x = Math.max(minX, cx * 16); x < Math.min(maxX, cx * 16 + 16); x++) {
						int at = (x & 15) + (z & 15) * 16;
						if (g[at] == UNKNOWN && ground.exact(x, z)) {
							s.clear();
							ground.column(x, z, s);
							g[at] = pack(s);
						}
					}
				}
			}
		}
	}

	private Placed chunk(int cx, int cz, Ground ground) {
		long key = chunkKey(cx, cz);
		synchronized (this.chunks) {
			Placed known = this.chunks.get(key);
			if (known != null) {
				return known;
			}
		}
		TreeFiles files = this.files;
		Placed chunk;
		{
			long t0 = System.nanoTime();
			chunk = this.place(cx, cz, ground);
			this.placeNanos.addAndGet(System.nanoTime() - t0);
			this.chunksPlaced.incrementAndGet();
			if (files != null) {
				files.write(cx, cz, chunk);
			}
		}
		synchronized (this.chunks) {
			this.chunks.put(key, chunk);
		}
		return chunk;
	}

	/** Runs the tree features of chunk (cx, cz) as the decoration step would, and keeps what they set. */
	private Placed place(int cx, int cz, Ground ground) {
		Stand stand = new Stand(ground);
		// the features of every biome in and around the chunk, in the step's order: each is seeded from its own index,
		// so one more or fewer changes nobody else's trees, and its biome filter keeps each where its biome is
		BitSet wanted = new BitSet(this.features.size());
		for (int dz = -1; dz <= 1; dz++) {
			for (int dx = -1; dx <= 1; dx++) {
				for (int s = 0; s < 4; s++) {
					int x = (cx + dx) * 16 + 4 + (s & 1) * 8, z = (cz + dz) * 16 + 4 + (s >> 1) * 8;
					Column c = stand.column(x, z);
					if (c.empty) {
						continue;
					}
					for (int i : this.treeFeatures(this.biomes.at(x, c.ground, z))) {
						wanted.set(i);
					}
				}
			}
		}
		if (wanted.isEmpty()) {
			return new Placed(new long[0], new BlockState[0]);
		}
		WorldgenRandom random = new WorldgenRandom(new XoroshiroRandomSource(RandomSupport.generateUniqueSeed()));
		long decorationSeed = random.setDecorationSeed(this.seed, cx * 16, cz * 16);
		BlockPos origin = new BlockPos(cx * 16, this.level.getMinY(), cz * 16);
		WorldGenLevel world = stand.world();
		for (int i = wanted.nextSetBit(0); i >= 0; i = wanted.nextSetBit(i + 1)) {
			random.setFeatureSeed(decorationSeed, i, STEP);
			try {
				new FeaturePlacer(world, this.generator).placeWithBiomeCheck(this.features.get(i), random, origin);
			} catch (RuntimeException | LinkageError e) {
				if (!this.warned) {
					this.warned = true;
					LoggerFactory.getLogger("VOID LOD").warn("VOID LOD: a tree feature would not place in the stand-in world (keeping what it set)", e);
				}
			}
		}
		long[] positions = new long[stand.placed.size()];
		BlockState[] states = new BlockState[positions.length];
		int n = 0;
		for (var e : stand.placed.long2ObjectEntrySet()) {
			if (!e.getValue().isAir()) {
				positions[n] = e.getLongKey();
				states[n] = e.getValue();
				n++;
			}
		}
		return new Placed(Arrays.copyOf(positions, n), Arrays.copyOf(states, n));
	}

	private int[] treeFeatures(Holder<Biome> biome) {
		if (biome == null) {
			return new int[0];
		}
		return this.treeFeatures.computeIfAbsent(biome, b -> {
			List<HolderSet<PlacedFeature>> steps = this.settings.apply(b).features();
			if (steps.size() <= STEP) {
				return new int[0];
			}
			List<Integer> found = new ArrayList<>();
			for (Holder<PlacedFeature> placed : steps.get(STEP)) {
				int i = this.features.indexOf(placed.value());
				if (i >= 0 && this.trees[i]) {
					found.add(i);
				}
			}
			return found.stream().mapToInt(Integer::intValue).sorted().toArray();
		});
	}

	/** A column of the stand-in world: its first air above the ground, its water's surface (or none), its blocks. */
	private record Column(int ground, int water, BlockState top, BlockState soil, boolean empty) {
	}

	/** The world the features see while one chunk's trees are placed. */
	private final class Stand implements InvocationHandler {
		final Ground ground;
		final it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<Column> columns = new it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<>();
		final it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap<BlockState> placed = new it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap<>();
		/** Per column, the highest block set and the highest that is not leaves, each plus one. */
		final it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<int[]> tops = new it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<>();
		final ColumnSample sample = new ColumnSample();
		final RandomSource random = RandomSource.create(42);
		/** A chunk of nothing, for features that mark blocks in theirs (fallen trees ask for post-processing). */
		net.minecraft.world.level.chunk.ProtoChunk scratch;

		Stand(Ground ground) {
			this.ground = ground;
		}

		WorldGenLevel world() {
			return (WorldGenLevel) Proxy.newProxyInstance(WorldGenLevel.class.getClassLoader(), new Class<?>[]{WorldGenLevel.class}, this);
		}

		Column column(int x, int z) {
			long key = (long) x << 32 ^ z & 0xFFFFFFFFL;
			Column c = this.columns.get(key);
			if (c != null) {
				return c;
			}
			ColumnSample s = this.sample;
			s.clear();
			long t0 = System.nanoTime();
			TreeDecorator.this.column(x, z, this.ground, s);
			TreeDecorator.this.groundNanos.addAndGet(System.nanoTime() - t0);
			TreeDecorator.this.columnsAsked.incrementAndGet();
			if (s.empty) {
				c = new Column(Integer.MIN_VALUE, Integer.MIN_VALUE, AIR, AIR, true);
			} else {
				int g = (int) Math.ceil(s.ground - 1e-3F);
				int water = s.water != Integer.MIN_VALUE && s.water > g ? s.water : Integer.MIN_VALUE;
				Materials.Face face = TreeDecorator.this.materials.face(s.topMaterial);
				BlockState top = face == null ? Blocks.GRASS_BLOCK.defaultBlockState() : face.state();
				if (!top.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO)) {
					// a snow layer or a carpet over the ground: the block under it is what trees stand on
					top = Blocks.GRASS_BLOCK.defaultBlockState();
				}
				BlockState soil = top.is(Blocks.GRASS_BLOCK) || top.is(Blocks.PODZOL) || top.is(Blocks.MYCELIUM) ? DIRT : top;
				c = new Column(g, water, top, soil, false);
			}
			this.columns.put(key, c);
			return c;
		}

		BlockState state(int x, int y, int z) {
			BlockState set = this.placed.get(BlockPos.asLong(x, y, z));
			if (set != null) {
				return set;
			}
			Column c = this.column(x, z);
			if (c.empty) {
				return AIR;
			}
			if (y >= c.ground) {
				return c.water != Integer.MIN_VALUE && y < c.water ? WATER : AIR;
			}
			return y == c.ground - 1 ? c.top : y >= c.ground - 4 ? c.soil : STONE;
		}

		void set(BlockPos pos, BlockState state) {
			this.placed.put(pos.asLong(), state);
			if (!state.isAir()) {
				long key = (long) pos.getX() << 32 ^ pos.getZ() & 0xFFFFFFFFL;
				int[] t = this.tops.computeIfAbsent(key, k -> new int[]{Integer.MIN_VALUE, Integer.MIN_VALUE});
				t[0] = Math.max(t[0], pos.getY() + 1);
				if (!state.is(BlockTags.LEAVES)) {
					t[1] = Math.max(t[1], pos.getY() + 1);
				}
			}
		}

		int height(Heightmap.Types type, int x, int z) {
			Column c = this.column(x, z);
			if (c.empty) {
				return TreeDecorator.this.level.getMinY();
			}
			int[] t = this.tops.get((long) x << 32 ^ z & 0xFFFFFFFFL);
			int any = t == null ? Integer.MIN_VALUE : t[0];
			int solid = t == null ? Integer.MIN_VALUE : t[1];
			int surface = c.water != Integer.MIN_VALUE ? c.water : c.ground;
			return switch (type.name()) {
				case "OCEAN_FLOOR", "OCEAN_FLOOR_WG" -> Math.max(c.ground, any);
				case "MOTION_BLOCKING_NO_LEAVES" -> Math.max(surface, solid);
				default -> Math.max(surface, any);
			};
		}

		@Override
		public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
			String name = method.getName();
			int n = args == null ? 0 : args.length;
			ServerLevel real = TreeDecorator.this.level;
			switch (name) {
				case "getBlockState" -> {
					if (n == 1 && args[0] instanceof BlockPos p) {
						return this.state(p.getX(), p.getY(), p.getZ());
					}
				}
				case "getFluidState" -> {
					if (n == 1 && args[0] instanceof BlockPos p) {
						return this.state(p.getX(), p.getY(), p.getZ()).getFluidState();
					}
				}
				case "isStateAtPosition" -> {
					if (n == 2 && args[0] instanceof BlockPos p) {
						@SuppressWarnings("unchecked")
						Predicate<BlockState> test = (Predicate<BlockState>) args[1];
						return test.test(this.state(p.getX(), p.getY(), p.getZ()));
					}
				}
				case "isFluidAtPosition" -> {
					if (n == 2 && args[0] instanceof BlockPos p) {
						@SuppressWarnings("unchecked")
						Predicate<FluidState> test = (Predicate<FluidState>) args[1];
						return test.test(this.state(p.getX(), p.getY(), p.getZ()).getFluidState());
					}
				}
				case "setBlock" -> {
					if (n >= 2 && args[0] instanceof BlockPos p && args[1] instanceof BlockState state) {
						this.set(p, state);
						return true;
					}
				}
				case "removeBlock", "destroyBlock" -> {
					if (n >= 1 && args[0] instanceof BlockPos p) {
						this.set(p, AIR);
						return true;
					}
				}
				case "getHeight" -> {
					if (n == 3 && args[0] instanceof Heightmap.Types type) {
						return this.height(type, (Integer) args[1], (Integer) args[2]);
					}
					if (n == 0) {
						return real.getHeight();
					}
				}
				case "getMinY", "getMaxY", "getMinSectionY", "getMaxSectionY", "getSectionsCount", "getSeaLevel", "registryAccess",
					"enabledFeatures", "dimensionType", "getWorldBorder" -> {
					if (n == 0) {
						return method.invoke(real);
					}
				}
				case "getBiome" -> {
					if (n == 1 && args[0] instanceof BlockPos p) {
						return TreeDecorator.this.biomes.at(p.getX(), p.getY(), p.getZ());
					}
				}
				case "getNoiseBiome", "getUncachedNoiseBiome" -> {
					if (n == 3) {
						return TreeDecorator.this.biomes.at((Integer) args[0] << 2, (Integer) args[1] << 2, (Integer) args[2] << 2);
					}
				}
				case "getSeed" -> {
					return TreeDecorator.this.seed;
				}
				case "getRandom" -> {
					return this.random;
				}
				case "getChunk" -> {
					if (this.scratch == null) {
						ServerLevel level = TreeDecorator.this.level;
						this.scratch = new net.minecraft.world.level.chunk.ProtoChunk(net.minecraft.world.level.ChunkPos.ZERO,
							net.minecraft.world.level.chunk.UpgradeData.EMPTY, level, level.palettedContainerFactory(), null);
					}
					return this.scratch;
				}
				case "getRawBrightness", "getMaxLocalRawBrightness" -> {
					// open air in full daylight: what a sapling or a decorator asks for
					return 15;
				}
				case "getBrightness" -> {
					return n == 2 && args[0] == net.minecraft.world.level.LightLayer.SKY ? 15 : 0;
				}
				case "ensureCanWrite", "hasChunk", "hasChunkAt", "isLoaded" -> {
					return true;
				}
				case "isClientSide" -> {
					return false;
				}
				case "toString" -> {
					return "VOID LOD stand-in world";
				}
				case "hashCode" -> {
					return System.identityHashCode(proxy);
				}
				case "equals" -> {
					return proxy == args[0];
				}
				default -> {
				}
			}
			// the level itself and the server are never handed out: nothing may reach the real world through here
			if (name.equals("getLevel") || name.equals("getServer") || name.startsWith("scheduleTick")) {
				return null;
			}
			if (method.isDefault()) {
				return InvocationHandler.invokeDefault(proxy, method, args);
			}
			return empty(method.getReturnType());
		}
	}

	private static Object empty(Class<?> type) {
		if (type == boolean.class) {
			return false;
		}
		if (type == int.class) {
			return 0;
		}
		if (type == long.class) {
			return 0L;
		}
		if (type == float.class) {
			return 0F;
		}
		if (type == double.class) {
			return 0D;
		}
		if (type == Optional.class) {
			return Optional.empty();
		}
		if (type == Stream.class) {
			return Stream.empty();
		}
		if (type == List.class) {
			return List.of();
		}
		return null;
	}

	public String stats() {
		long n = this.chunksPlaced.get();
		return String.format(Locale.ROOT, "trees: %d queued, %d chunks read, %d placed (%.2f ms each, %.2f of it the ground: %d columns a chunk)", this.queued.size(), this.chunksRead.get(), n,
			n == 0 ? 0 : this.placeNanos.get() / 1e6 / n, n == 0 ? 0 : this.groundNanos.get() / 1e6 / n, n == 0 ? 0 : this.columnsAsked.get() / n);
	}
}
