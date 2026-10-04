package dev.voidmc.hearth.build.blueprint;

import dev.voidmc.hearth.api.Blueprint;
import dev.voidmc.hearth.api.Goods;
import dev.voidmc.hearth.api.PlotSpec;
import dev.voidmc.hearth.api.Poi;
import dev.voidmc.hearth.api.Silhouette;
import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.api.StepSink;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jspecify.annotations.Nullable;

/**
 * The packed, immutable form of a building. Steps are stored per stage as parallel arrays: positions packed relative to
 * the bounds' minimum corner (11 bits x, 10 bits y, 11 bits z), and indices into a shared state palette; block-entity
 * tags are kept sparsely by step. A chunk index lists, per chunk and stage, the step indices that fall in it, in build
 * order, so {@link #forEachInChunk} touches only those.
 *
 * <p>A 2,500-block cottage takes about 25 KB; tags are copied on every emit so callers cannot change the blueprint.
 */
public final class BlueprintImpl implements Blueprint {
	static final int X_BITS = 11;
	static final int Y_BITS = 10;
	static final int Z_BITS = 11;
	static final int MAX_X = 1 << X_BITS;
	static final int MAX_Y = 1 << Y_BITS;
	static final int MAX_Z = 1 << Z_BITS;

	private final PlotSpec spec;
	private final BoundingBox bounds;
	private final StageKind[] kinds;
	/** The part each stage belongs to (a blueprint worked in parts, Draft#part; all 0 otherwise). */
	private final int[] parts;
	private final int[][] positions;
	private final int[][] states;
	private final BlockState[] palette;
	private final Int2ObjectMap<CompoundTag>[] tags;
	private final Long2ObjectMap<int[][]> chunks;
	private final int[] work;
	private final int[][] goods;
	private final List<Poi> pois;
	private final List<BlockPos> entrances;
	private final int beds;
	private final Silhouette silhouette;

	BlueprintImpl(PlotSpec spec, BoundingBox bounds, StageKind[] kinds, int[][] positions, int[][] states, BlockState[] palette,
		Int2ObjectMap<CompoundTag>[] tags, Long2ObjectMap<int[][]> chunks, int[] work, int[][] goods, List<Poi> pois, List<BlockPos> entrances,
		int beds, Silhouette silhouette) {
		this(spec, bounds, kinds, new int[kinds.length], positions, states, palette, tags, chunks, work, goods, pois, entrances, beds, silhouette);
	}

	BlueprintImpl(PlotSpec spec, BoundingBox bounds, StageKind[] kinds, int[] parts, int[][] positions, int[][] states, BlockState[] palette,
		Int2ObjectMap<CompoundTag>[] tags, Long2ObjectMap<int[][]> chunks, int[] work, int[][] goods, List<Poi> pois, List<BlockPos> entrances,
		int beds, Silhouette silhouette) {
		this.spec = spec;
		this.bounds = bounds;
		this.kinds = kinds;
		this.parts = parts;
		this.positions = positions;
		this.states = states;
		this.palette = palette;
		this.tags = tags;
		this.chunks = chunks;
		this.work = work;
		this.goods = goods;
		this.pois = List.copyOf(pois);
		this.entrances = List.copyOf(entrances);
		this.beds = beds;
		this.silhouette = silhouette;
	}

	static int pack(int dx, int dy, int dz) {
		return (dx << (Y_BITS + Z_BITS)) | (dy << Z_BITS) | dz;
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

	/**
	 * The part a stage belongs to: 0 for an ordinary building; for a blueprint worked in parts (an excavation, see
	 * {@code Draft#part}) the opening works are part 0 and each bench, gallery or lift after it a part of its own, its
	 * stages in {@link StageKind} order.
	 */
	public int part(int stage) {
		return this.parts[stage];
	}

	/** How many parts the blueprint is worked in (1 for an ordinary building). */
	public int partCount() {
		return this.parts.length == 0 ? 1 : this.parts[this.parts.length - 1] + 1;
	}

	/** The index of the first stage of a kind, or -1 if the blueprint has none of that kind. */
	public int stageIndex(StageKind kind) {
		for (int i = 0; i < this.kinds.length; i++) {
			if (this.kinds[i] == kind) {
				return i;
			}
		}
		return -1;
	}

	@Override
	public int stepCount(int stage) {
		return this.positions[stage].length;
	}

	/** Total steps over all stages. */
	public int totalSteps() {
		int n = 0;
		for (int[] p : this.positions) {
			n += p.length;
		}
		return n;
	}

	@Override
	public void step(int stage, int index, StepSink sink) {
		emit(stage, index, sink);
	}

	private void emit(int stage, int index, StepSink sink) {
		int p = this.positions[stage][index];
		int x = this.bounds.minX() + (p >>> (Y_BITS + Z_BITS));
		int y = this.bounds.minY() + ((p >>> Z_BITS) & (MAX_Y - 1));
		int z = this.bounds.minZ() + (p & (MAX_Z - 1));
		CompoundTag tag = this.tags[stage].get(index);
		sink.accept(x, y, z, this.palette[this.states[stage][index]], tag == null ? null : tag.copy());
	}

	/** The state of a step without emitting it. */
	public BlockState state(int stage, int index) {
		return this.palette[this.states[stage][index]];
	}

	/** The world position of a step. */
	public BlockPos pos(int stage, int index) {
		int p = this.positions[stage][index];
		return new BlockPos(this.bounds.minX() + (p >>> (Y_BITS + Z_BITS)), this.bounds.minY() + ((p >>> Z_BITS) & (MAX_Y - 1)),
			this.bounds.minZ() + (p & (MAX_Z - 1)));
	}

	public @Nullable CompoundTag tag(int stage, int index) {
		CompoundTag t = this.tags[stage].get(index);
		return t == null ? null : t.copy();
	}

	@Override
	public void forEachInChunk(int chunkX, int chunkZ, int fromStage, int toStage, StepSink sink) {
		int[][] perStage = this.chunks.get(ChunkPos.pack(chunkX, chunkZ));
		if (perStage == null) {
			return;
		}
		for (int s = Math.max(0, fromStage); s < Math.min(toStage, this.kinds.length); s++) {
			for (int i : perStage[s]) {
				emit(s, i, sink);
			}
		}
	}

	/** The chunks this blueprint touches, as packed chunk positions. */
	public long[] chunks() {
		return this.chunks.keySet().toLongArray();
	}

	@Override
	public int work(int stage) {
		return this.work[stage];
	}

	@Override
	public int[] goods(int stage) {
		return this.goods[stage].clone();
	}

	/** Goods over all stages. */
	public int[] totalGoods() {
		int[] t = new int[Goods.VALUES.length];
		for (int[] g : this.goods) {
			for (int i = 0; i < t.length; i++) {
				t[i] += g[i];
			}
		}
		return t;
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

	@Override
	public String toString() {
		return "Blueprint[" + this.spec.archetype() + " " + this.spec.style() + " seed " + this.spec.seed() + ", " + totalSteps() + " steps]";
	}
}
