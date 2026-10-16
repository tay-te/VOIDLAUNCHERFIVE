package dev.voidmc.hearth.npc.work;

import dev.voidmc.hearth.api.Blueprint;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * Which steps of a stage a crew may place now (docs/FOLK.md §6). The sim keeps one progress counter per plot,
 * {@code (stage, step)}, and {@code advanceConstruction} moves it on; a blueprint's steps are in build order, supports
 * first. A builder on a scaffold reaches a stretch of wall several courses high, and placing strictly one step after
 * another would send them along the whole course before the next one up. So builders may place steps <b>ahead of the
 * counter</b>: any step of the current stage whose supports are in place — no earlier step still to do at its own
 * position or the one below it, nor, for a block with nothing to stand on of its own (a torch, a sign, a flower, a
 * rail: no collision shape), beside or above it.
 * The counter itself only ever moves over steps that are done, in order: the steps from the cursor on that are in the
 * world as the blueprint wants them (or were placed by a builder, or are passed because a block entity is there), so the
 * sim's single counter, its chunk ledgers and the chunk-load replay see exactly what a strictly sequential builder would
 * have left. A step placed ahead and then lost (the chunk folded back to the record) is simply placed again by the
 * applier or the next builder, as the same block.
 */
final class StepPlan {
	private StepPlan() {
	}

	/** What a scan reads from the world. */
	interface World {
		/** The block at a position, or null when its chunk is not loaded. */
		@Nullable BlockState at(long pos);
	}

	/** A step still to do: its index in the stage, where, what, and whether its supports are in place. */
	record Pending(int index, long pos, BlockState state, @Nullable CompoundTag tag, boolean ready) {
		BlockPos blockPos() {
			return BlockPos.of(this.pos);
		}
	}

	/**
	 * A stage read from the cursor on.
	 *
	 * @param donePrefix how many steps from the cursor are done in a row: what {@code advanceConstruction} may be told
	 * @param pending    the steps still to do in order (up to the scan's limits), each marked ready or not
	 * @param complete   true when the scan reached the end of the stage (nothing beyond {@code pending} is still to do)
	 */
	record Scan(int stage, int from, int donePrefix, List<Pending> pending, boolean complete) {
		int readyCount() {
			int n = 0;
			for (Pending p : this.pending) {
				if (p.ready) {
					n++;
				}
			}
			return n;
		}
	}

	/** Whether the world already shows a step as the blueprint wants it. */
	static boolean sameAs(BlockState there, BlockState wanted) {
		return there == wanted || wanted.isAir() && there.isAir();
	}

	/**
	 * Done: in place, placed by a builder this stage ({@code placed}, by index), or passed — somebody's chest, sign or bed
	 * of another block stands there, which no builder or applier ever builds over.
	 */
	static boolean done(@Nullable BlockState there, BlockState wanted, boolean placed) {
		if (placed) {
			return true;
		}
		if (there == null) {
			return false;
		}
		return sameAs(there, wanted) || there.hasBlockEntity() && there.getBlock() != wanted.getBlock();
	}

	/**
	 * A block with no body of its own (a torch, a sign, a flower, a rail) hangs on whatever is
	 * beside or above it as well as below; a block with one (a wall, a stair, a slab, a pane) rests on what is under it.
	 */
	static boolean hangs(BlockState state) {
		return !state.isAir() && state.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).isEmpty();
	}

	/**
	 * Reads stage {@code stage} from step {@code from} (the plot's cursor): the run of done steps from the cursor, then up
	 * to {@code maxPending} steps still to do (looking at most {@code maxScan} steps ahead), each ready if nothing before
	 * it that is still to do shares its position, lies under it, or (for a block that {@link #hangs}) beside or above it.
	 */
	static Scan scan(Blueprint blueprint, int stage, int from, BitSet placed, World world, int maxScan, int maxPending) {
		int count = blueprint.stepCount(stage);
		int end = (int) Math.min(count, (long) from + maxScan);
		List<Pending> pending = new ArrayList<>();
		// positions of the steps before the one being read that are still to do
		LongOpenHashSet todo = new LongOpenHashSet();
		int prefix = 0;
		boolean inPrefix = true;
		Object[] read = new Object[2];
		long[] at = new long[1];
		int i = from;
		for (; i < end && pending.size() < maxPending; i++) {
			blueprint.step(stage, i, (x, y, z, state, tag) -> {
				at[0] = BlockPos.asLong(x, y, z);
				read[0] = state;
				read[1] = tag;
			});
			BlockState state = (BlockState) read[0];
			if (state == null) {
				// a blueprint that emits nothing for an index: nothing to do there
				if (inPrefix) {
					prefix++;
				}
				continue;
			}
			long pos = at[0];
			if (done(world.at(pos), state, placed.get(i))) {
				if (inPrefix) {
					prefix++;
				}
				continue;
			}
			inPrefix = false;
			boolean ready = world.at(pos) != null && !blocked(todo, pos, state);
			pending.add(new Pending(i, pos, state, (CompoundTag) read[1], ready));
			todo.add(pos);
		}
		return new Scan(stage, from, prefix, pending, i >= count);
	}

	private static boolean blocked(LongOpenHashSet todo, long pos, BlockState state) {
		if (todo.isEmpty()) {
			return false;
		}
		if (todo.contains(pos) || todo.contains(BlockPos.offset(pos, Direction.DOWN))) {
			return true;
		}
		if (!hangs(state)) {
			return false;
		}
		for (Direction d : Direction.values()) {
			if (d != Direction.DOWN && todo.contains(BlockPos.offset(pos, d))) {
				return true;
			}
		}
		return false;
	}
}
