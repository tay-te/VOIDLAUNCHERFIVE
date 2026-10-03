package dev.voidmc.hearth.sim.dev;

import dev.voidmc.hearth.api.Blueprint;
import dev.voidmc.hearth.api.Goods;
import dev.voidmc.hearth.api.PlotSpec;
import dev.voidmc.hearth.api.Poi;
import dev.voidmc.hearth.api.Silhouette;
import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.api.StepSink;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jspecify.annotations.Nullable;

/**
 * A blueprint as plain step lists, indexed by chunk: what the placeholder archetypes produce, and a correct reference
 * implementation of the {@link Blueprint} contract (stable indexing, build order within stages, per-chunk iteration
 * without filtering). Built by {@link Builder}; immutable afterwards.
 */
public final class PlaceholderBlueprint implements Blueprint {
	private final PlotSpec spec;
	private final StageKind[] kinds;
	private final int[][] xs;
	private final int[][] ys;
	private final int[][] zs;
	private final BlockState[][] states;
	private final CompoundTag[][] tags;
	/** Per stage: chunk to the indices of its steps, in build order. */
	private final List<Long2ObjectOpenHashMap<int[]>> byChunk;
	private final int[] work;
	private final int[][] goods;
	private final List<Poi> pois;
	private final List<BlockPos> entrances;
	private final int beds;
	private final BoundingBox bounds;
	private final Silhouette silhouette;

	private PlaceholderBlueprint(Builder b) {
		this.spec = b.spec;
		int n = b.stages.size();
		this.kinds = new StageKind[n];
		this.xs = new int[n][];
		this.ys = new int[n][];
		this.zs = new int[n][];
		this.states = new BlockState[n][];
		this.tags = new CompoundTag[n][];
		this.byChunk = new ArrayList<>(n);
		this.work = new int[n];
		this.goods = new int[n][];
		BoundingBox box = null;
		int roofSum = 0;
		int wallSum = 0;
		int ridge = Integer.MIN_VALUE;
		for (int s = 0; s < n; s++) {
			Stage st = b.stages.get(s);
			int m = st.states.size();
			this.kinds[s] = st.kind;
			this.xs[s] = st.x.toIntArray();
			this.ys[s] = st.y.toIntArray();
			this.zs[s] = st.z.toIntArray();
			this.states[s] = st.states.toArray(BlockState[]::new);
			this.tags[s] = st.tags.toArray(CompoundTag[]::new);
			Long2ObjectOpenHashMap<IntArrayList> index = new Long2ObjectOpenHashMap<>();
			int[] g = new int[Goods.VALUES.length];
			int w = 0;
			for (int i = 0; i < m; i++) {
				index.computeIfAbsent(ChunkPos.pack(this.xs[s][i] >> 4, this.zs[s][i] >> 4), k -> new IntArrayList()).add(i);
				BlockState state = this.states[s][i];
				if (!state.isAir()) {
					w++;
					Goods goods = goodsOf(state);
					if (goods != null) {
						g[goods.ordinal()]++;
					}
					ridge = Math.max(ridge, this.ys[s][i]);
				}
				BlockPos p = new BlockPos(this.xs[s][i], this.ys[s][i], this.zs[s][i]);
				if (box == null) {
					box = new BoundingBox(p);
				} else {
					box.encapsulate(p);
				}
			}
			Long2ObjectOpenHashMap<int[]> frozen = new Long2ObjectOpenHashMap<>(index.size());
			index.forEach((k, v) -> frozen.put((long) k, v.toIntArray()));
			this.byChunk.add(frozen);
			this.work[s] = w;
			this.goods[s] = g;
		}
		this.bounds = box == null ? new BoundingBox(b.spec.origin()) : box;
		this.pois = List.copyOf(b.pois);
		this.entrances = List.copyOf(b.entrances);
		this.beds = b.beds;
		this.silhouette = new Silhouette(b.wallColor, b.roofColor, b.spec.floorY() + b.eave, ridge == Integer.MIN_VALUE ? b.spec.floorY() : ridge);
	}

	/** The material bill: what goods a block costs. */
	static @Nullable Goods goodsOf(BlockState state) {
		if (state.is(BlockTags.LOGS) || state.is(BlockTags.PLANKS) || state.is(BlockTags.WOODEN_SLABS) || state.is(BlockTags.WOODEN_STAIRS)
			|| state.is(BlockTags.WOODEN_FENCES) || state.is(BlockTags.WOODEN_DOORS) || state.is(BlockTags.WOODEN_TRAPDOORS)) {
			return Goods.WOOD;
		}
		if (state.is(BlockTags.BEDS) || state.is(BlockTags.WOOL) || state.is(BlockTags.WOOL_CARPETS)) {
			return Goods.CLOTH;
		}
		String id = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
		if (id.contains("glass")) {
			return Goods.GLASS;
		}
		if (id.contains("lantern") || id.contains("chain") || id.contains("iron") || id.contains("copper")) {
			return Goods.METAL;
		}
		if (id.contains("ladder")) {
			return Goods.WOOD;
		}
		if (id.contains("stone") || id.contains("deepslate") || id.contains("andesite") || id.contains("cobble")) {
			// stone bricks are dressed stone, not clay
			return Goods.STONE;
		}
		if (id.contains("brick") || id.contains("terracotta") || id.contains("mud") || id.contains("clay")) {
			return Goods.CLAY;
		}
		if (id.contains("torch")) {
			return Goods.FUEL;
		}
		if (id.contains("crafting") || id.contains("chest") || id.contains("barrel")) {
			return Goods.WOOD;
		}
		if (id.contains("farmland") || id.contains("dirt") || id.contains("grass") || id.contains("wheat") || id.contains("water")
			|| id.contains("flower") || id.contains("poppy") || id.contains("dandelion")) {
			return null;
		}
		return Goods.STONE;
	}

	@Override
	public PlotSpec spec() {
		return this.spec;
	}

	@Override
	public BoundingBox bounds() {
		return this.bounds;
	}

	@Override
	public int stageCount() {
		return this.kinds.length;
	}

	@Override
	public StageKind stageKind(int stage) {
		return this.kinds[stage];
	}

	@Override
	public int stepCount(int stage) {
		return this.states[stage].length;
	}

	@Override
	public void step(int stage, int index, StepSink sink) {
		sink.accept(this.xs[stage][index], this.ys[stage][index], this.zs[stage][index], this.states[stage][index], copy(this.tags[stage][index]));
	}

	@Override
	public void forEachInChunk(int chunkX, int chunkZ, int fromStage, int toStage, StepSink sink) {
		long key = ChunkPos.pack(chunkX, chunkZ);
		for (int s = Math.max(0, fromStage); s < Math.min(toStage, this.kinds.length); s++) {
			int[] idx = this.byChunk.get(s).get(key);
			if (idx == null) {
				continue;
			}
			for (int i : idx) {
				sink.accept(this.xs[s][i], this.ys[s][i], this.zs[s][i], this.states[s][i], copy(this.tags[s][i]));
			}
		}
	}

	private static @Nullable CompoundTag copy(@Nullable CompoundTag tag) {
		return tag == null ? null : tag.copy();
	}

	@Override
	public int work(int stage) {
		return this.work[stage];
	}

	@Override
	public int[] goods(int stage) {
		return this.goods[stage].clone();
	}

	@Override
	public List<Poi> pois() {
		return this.pois;
	}

	@Override
	public List<BlockPos> entrances() {
		return this.entrances;
	}

	@Override
	public int beds() {
		return this.beds;
	}

	@Override
	public Silhouette silhouette() {
		return this.silhouette;
	}

	private static final class Stage {
		final StageKind kind;
		final IntArrayList x = new IntArrayList();
		final IntArrayList y = new IntArrayList();
		final IntArrayList z = new IntArrayList();
		final List<BlockState> states = new ArrayList<>();
		final List<CompoundTag> tags = new ArrayList<>();

		Stage(StageKind kind) {
			this.kind = kind;
		}
	}

	/** Collects steps stage by stage, keeping every one inside the plot's footprint. */
	static final class Builder {
		final PlotSpec spec;
		private final List<Stage> stages = new ArrayList<>();
		private final List<Poi> pois = new ArrayList<>();
		private final List<BlockPos> entrances = new ArrayList<>();
		private int beds;
		private int wallColor = 0x9C7F4E;
		private int roofColor = 0x5A3A22;
		private int eave = 4;
		private @Nullable Stage current;

		Builder(PlotSpec spec) {
			this.spec = spec;
		}

		Builder stage(StageKind kind) {
			this.current = new Stage(kind);
			this.stages.add(this.current);
			return this;
		}

		boolean inside(int x, int z) {
			int ox = this.spec.origin().getX();
			int oz = this.spec.origin().getZ();
			return x >= ox && x < ox + this.spec.sizeX() && z >= oz && z < oz + this.spec.sizeZ();
		}

		void set(int x, int y, int z, BlockState state) {
			set(x, y, z, state, null);
		}

		void set(int x, int y, int z, BlockState state, @Nullable CompoundTag tag) {
			if (this.current == null || !inside(x, z) || y > this.spec.floorY() + this.spec.maxHeight()) {
				return;
			}
			this.current.x.add(x);
			this.current.y.add(y);
			this.current.z.add(z);
			this.current.states.add(state);
			this.current.tags.add(tag);
		}

		void poi(Poi poi) {
			this.pois.add(poi);
		}

		void entrance(BlockPos pos) {
			this.entrances.add(pos.immutable());
		}

		void beds(int n) {
			this.beds += n;
		}

		void colours(int wall, int roof, int eave) {
			this.wallColor = wall;
			this.roofColor = roof;
			this.eave = eave;
		}

		PlaceholderBlueprint build() {
			// a stage with no steps is still a stage (stable indexing), but trailing empty ones are dropped
			while (!this.stages.isEmpty() && this.stages.getLast().states.isEmpty()) {
				this.stages.removeLast();
			}
			return new PlaceholderBlueprint(this);
		}
	}
}
