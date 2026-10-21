package dev.voidmc.hearth.npc.crowd;

import dev.voidmc.hearth.api.Blueprint;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The window shutters of a building, from its blueprint (docs/LIVING_WORLD_2.md §5.3): open trapdoors hung on the outer
 * face of a wall, flat against it, with glass or a pane beside them in the wall — the leaves {@code Openings.window}
 * hangs beside a dressed window. Lattice screens (trapdoors in the wall's own plane), hatches and cellar doors have no
 * glass beside them and are left alone, as are doors. Pure: runs off the server thread on an immutable blueprint.
 */
public final class ShutterScan {
	private ShutterScan() {
	}

	/** Every shutter's position (as {@link BlockPos#asLong}) in the blueprint's first {@code stages} stages. */
	public static long[] scan(Blueprint bp, int stages) {
		Long2ObjectOpenHashMap<BlockState> cells = new Long2ObjectOpenHashMap<>();
		int to = Math.min(stages, bp.stageCount());
		for (int s = 0; s < to; s++) {
			int n = bp.stepCount(s);
			for (int i = 0; i < n; i++) {
				bp.step(s, i, (x, y, z, state, tag) -> cells.put(BlockPos.asLong(x, y, z), state));
			}
		}
		return scan(cells);
	}

	/** The shutters among a map of placed blocks (position to state). */
	public static long[] scan(Long2ObjectOpenHashMap<BlockState> cells) {
		LongArrayList out = new LongArrayList();
		BlockPos.MutableBlockPos q = new BlockPos.MutableBlockPos();
		for (Long2ObjectOpenHashMap.Entry<BlockState> e : cells.long2ObjectEntrySet()) {
			BlockState state = e.getValue();
			if (!isShutter(state)) {
				continue;
			}
			long at = e.getLongKey();
			int x = BlockPos.getX(at);
			int y = BlockPos.getY(at);
			int z = BlockPos.getZ(at);
			Direction out2 = state.getValue(TrapDoorBlock.FACING);
			Direction in = out2.getOpposite();
			// the wall behind the leaf
			q.set(x + in.getStepX(), y, z + in.getStepZ());
			BlockState wall = cells.get(q.asLong());
			if (wall == null || glass(wall) || wall.isAir()) {
				continue;
			}
			// the window beside it, in the wall, at the same course (a leaf hangs beside every course of the window)
			boolean beside = false;
			for (Direction side : new Direction[]{out2.getClockWise(), out2.getCounterClockWise()}) {
				q.set(x + in.getStepX() + side.getStepX(), y, z + in.getStepZ() + side.getStepZ());
				BlockState w = cells.get(q.asLong());
				if (w != null && glass(w)) {
					beside = true;
					break;
				}
			}
			if (beside) {
				out.add(at);
			}
		}
		return out.toLongArray();
	}

	/** An open trapdoor: a shutter leaf folded back against the wall. */
	public static boolean isShutter(BlockState state) {
		return state.getBlock() instanceof TrapDoorBlock && state.hasProperty(TrapDoorBlock.OPEN) && state.getValue(TrapDoorBlock.OPEN)
			&& !state.is(Blocks.IRON_TRAPDOOR);
	}

	/** Glass or a glass pane (panes and bars are the same family of block). */
	public static boolean glass(BlockState state) {
		return state.getBlock() instanceof IronBarsBlock && !state.is(Blocks.IRON_BARS) || state.is(BlockTags.IMPERMEABLE);
	}
}
