package dev.voidmc.hearth.sim.apply;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.Blueprint;
import dev.voidmc.hearth.api.StepSink;
import dev.voidmc.hearth.sim.settlement.Cursor;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * Writes a range of a plot's steps into one chunk, with edit safety: a step may only replace air, plants, snow, natural
 * terrain ({@code #hearth:buildable_over}), natural trees, and blocks this plot (or the plot it rebuilds) put there
 * earlier — never a player's blocks or block entities. Pure logic over a {@link Blocks} view, so the rules are unit
 * tested without a world.
 */
public final class StepWriter {
	/** Natural ground and debris that construction may build over. Sim module data. */
	public static final TagKey<Block> BUILDABLE_OVER = TagKey.create(Registries.BLOCK, Hearth.id("buildable_over"));

	private StepWriter() {
	}

	/** The world as the writer sees it: one chunk's blocks. */
	public interface Blocks {
		BlockState get(int x, int y, int z);

		/** Places a block without neighbour updates, loading block-entity data if given. */
		void set(int x, int y, int z, BlockState state, @Nullable CompoundTag blockEntityTag);

		/** Whether a log here belongs to a natural tree (non-persistent leaves above it). */
		boolean naturalTree(int x, int y, int z);
	}

	/** What a write did. */
	public static final class Result {
		public int placed;
		/** Already as the step wants it. */
		public int same;
		/** Refused: something not ours and not natural stands there. */
		public int conflicts;

		public int attempted() {
			return this.placed + this.same + this.conflicts;
		}
	}

	/**
	 * The blocks that count as ours at each position: what earlier steps of this plot (and of the plot it rebuilds) put
	 * there. Built lazily, the first time a step meets a block that is not plainly replaceable.
	 */
	public interface Owned {
		@Nullable BlockState at(long pos);

		void put(long pos, BlockState state);

		/**
		 * Whether what stands at a position is ours: a state this plot (or a plot it replaces, or what it changes in place)
		 * put there.
		 */
		default boolean owns(long pos, BlockState current) {
			return at(pos) == current;
		}
	}

	/**
	 * An owned map filled on first use by {@code filler}. Where the filler names several states for one position (the
	 * stages of a building changed in place: as first built, with its first addition...), each of them counts as ours;
	 * {@link Owned#at} gives the last.
	 */
	public static Owned lazyOwned(java.util.function.Consumer<StepSink> filler) {
		return new Owned() {
			private @Nullable Long2ObjectOpenHashMap<BlockState> map;
			private @Nullable Long2ObjectOpenHashMap<java.util.List<BlockState>> earlier;

			private Long2ObjectOpenHashMap<BlockState> map() {
				if (this.map == null) {
					Long2ObjectOpenHashMap<BlockState> m = new Long2ObjectOpenHashMap<>();
					this.map = m;
					filler.accept((x, y, z, state, tag) -> {
						long k = BlockPos.asLong(x, y, z);
						BlockState was = m.put(k, state);
						if (was != null && was != state) {
							if (this.earlier == null) {
								this.earlier = new Long2ObjectOpenHashMap<>();
							}
							this.earlier.computeIfAbsent(k, q -> new java.util.ArrayList<>(2)).add(was);
						}
					});
				}
				return this.map;
			}

			@Override
			public @Nullable BlockState at(long pos) {
				return map().get(pos);
			}

			@Override
			public boolean owns(long pos, BlockState current) {
				if (map().get(pos) == current) {
					return true;
				}
				java.util.List<BlockState> e = this.earlier == null ? null : this.earlier.get(pos);
				return e != null && e.contains(current);
			}

			@Override
			public void put(long pos, BlockState state) {
				if (this.map != null) {
					this.map.put(pos, state);
				}
			}
		};
	}

	/**
	 * Every step of {@code bp} in {@code [from, to)} (cursors) lying in the chunk, in build order. Whole stages go
	 * through the blueprint's chunk index; partial ones (embodied, step-by-step progress) are walked by index.
	 */
	public static void forRange(Blueprint bp, int chunkX, int chunkZ, long from, long to, StepSink sink) {
		if (to <= from) {
			return;
		}
		int s0 = Cursor.stage(from);
		int i0 = Cursor.step(from);
		int s1 = Math.min(Cursor.stage(to), bp.stageCount());
		int i1 = Cursor.stage(to) >= bp.stageCount() ? 0 : Cursor.step(to);
		StepSink inChunk = (x, y, z, state, tag) -> {
			if (x >> 4 == chunkX && z >> 4 == chunkZ) {
				sink.accept(x, y, z, state, tag);
			}
		};
		if (s0 >= bp.stageCount()) {
			return;
		}
		if (s0 == s1) {
			for (int i = i0; i < Math.min(i1, bp.stepCount(s0)); i++) {
				bp.step(s0, i, inChunk);
			}
			return;
		}
		int fullFrom = s0;
		if (i0 > 0) {
			for (int i = i0; i < bp.stepCount(s0); i++) {
				bp.step(s0, i, inChunk);
			}
			fullFrom = s0 + 1;
		}
		bp.forEachInChunk(chunkX, chunkZ, fullFrom, s1, sink);
		if (s1 < bp.stageCount()) {
			for (int i = 0; i < Math.min(i1, bp.stepCount(s1)); i++) {
				bp.step(s1, i, inChunk);
			}
		}
	}

	/** Writes steps {@code [from, to)} of {@code bp} into the chunk, safely. */
	public static Result write(Blueprint bp, int chunkX, int chunkZ, long from, long to, Blocks world, Owned owned) {
		Result r = new Result();
		forRange(bp, chunkX, chunkZ, from, to, (x, y, z, state, tag) -> {
			BlockState current = world.get(x, y, z);
			long key = BlockPos.asLong(x, y, z);
			if (current == state) {
				r.same++;
				owned.put(key, state);
				return;
			}
			if (!replaceable(current, x, y, z, world) && !owned.owns(key, current)) {
				r.conflicts++;
				return;
			}
			world.set(x, y, z, state, tag);
			owned.put(key, state);
			r.placed++;
		});
		return r;
	}

	/** Whether construction may replace this block without asking whose it is. */
	public static boolean replaceable(BlockState current, int x, int y, int z, Blocks world) {
		if (current.isAir()) {
			return true;
		}
		if (current.hasBlockEntity()) {
			// chests, signs, beds, furnaces: somebody's, unless the plot's own record says otherwise
			return false;
		}
		if (current.is(BUILDABLE_OVER) || current.canBeReplaced()) {
			return true;
		}
		if (current.is(BlockTags.LEAVES)) {
			return current.hasProperty(LeavesBlock.PERSISTENT) && !current.getValue(LeavesBlock.PERSISTENT);
		}
		if (current.is(BlockTags.LOGS)) {
			return world.naturalTree(x, y, z);
		}
		return false;
	}

	/** Steps {@code [0, cursor)} of a blueprint in a chunk: what a plot has already put there. */
	public static void forApplied(Blueprint bp, int chunkX, int chunkZ, long cursor, StepSink sink) {
		forRange(bp, chunkX, chunkZ, Cursor.ZERO, cursor, sink);
	}
}
