package dev.voidmc.hearth.build.kit.age;

import dev.voidmc.hearth.build.draft.Draft;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.CarpetBlock;
import net.minecraft.world.level.block.ChainBlock;
import net.minecraft.world.level.block.FlowerPotBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.TorchBlock;
import net.minecraft.world.level.block.VegetationBlock;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * After an addition has cut, moved or cleared part of a house: what hung from, rested on or was fixed to a block that is
 * gone comes down too (a lantern under a removed bracket, a carpet over a lifted floor, a torch on a wall that moved), so
 * nothing is left in the air. Only cells next to what changed are looked at.
 */
public final class Settle {
	private Settle() {
	}

	/** Settles the cells round {@code changed} (keys of {@link Draft#key}), until nothing more comes down. */
	public static void around(Draft d, LongOpenHashSet changed) {
		LongArrayFIFOQueue queue = new LongArrayFIFOQueue();
		for (long k : changed) {
			queue.enqueue(k);
		}
		LongOpenHashSet seen = new LongOpenHashSet();
		while (!queue.isEmpty()) {
			long k = queue.dequeueLong();
			int x = Draft.keyX(k);
			int y = Draft.keyY(k);
			int z = Draft.keyZ(k);
			for (int[] o : new int[][]{{0, -1, 0}, {0, 1, 0}, {1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}}) {
				int nx = x + o[0];
				int ny = y + o[1];
				int nz = z + o[2];
				long nk = Draft.key(nx, ny, nz);
				Draft.Cell c = d.cell(nx, ny, nz);
				if (c == null || c.state().isAir() || !unheld(d, nx, ny, nz, c.state())) {
					continue;
				}
				d.remove(nx, ny, nz);
				if (seen.add(nk)) {
					queue.enqueue(nk);
				}
			}
		}
	}

	/** Whether a block that needs holding has lost what held it. */
	static boolean unheld(Draft d, int x, int y, int z, BlockState s) {
		Block b = s.getBlock();
		if (b instanceof LanternBlock) {
			return s.getValue(LanternBlock.HANGING) ? empty(d, x, y + 1, z) : empty(d, x, y - 1, z) && y - 1 >= d.site().ground(x, z);
		}
		if (b instanceof ChainBlock && s.getValue(BlockStateProperties.AXIS) == Direction.Axis.Y) {
			return empty(d, x, y + 1, z);
		}
		if (b instanceof WallTorchBlock || b instanceof WallSignBlock || b instanceof LadderBlock) {
			Direction f = s.getValue(BlockStateProperties.HORIZONTAL_FACING);
			return empty(d, x - dev.voidmc.hearth.build.draft.Dirs.dx(f), y, z - dev.voidmc.hearth.build.draft.Dirs.dz(f));
		}
		if (b instanceof CarpetBlock || b instanceof TorchBlock || b instanceof CandleBlock || b instanceof FlowerPotBlock
			|| b instanceof VegetationBlock && !(b instanceof net.minecraft.world.level.block.LeavesBlock)) {
			return empty(d, x, y - 1, z) && y - 1 >= d.site().ground(x, z);
		}
		return false;
	}

	private static boolean empty(Draft d, int x, int y, int z) {
		return d.isEmpty(x, y, z);
	}

	/** Puts a draft back as a snapshot had it (cells and points of interest): an addition that started and could not finish. */
	public static void restore(Draft d, Long2ObjectMap<Draft.Cell> before, java.util.List<Draft.LocalPoi> pois) {
		for (long k : changed(before, d)) {
			int x = Draft.keyX(k);
			int y = Draft.keyY(k);
			int z = Draft.keyZ(k);
			Draft.Cell c = before.get(k);
			if (c == null) {
				d.remove(x, y, z);
			} else {
				d.inStage(c.stage(), () -> d.set(x, y, z, c.state(), c.tag()));
			}
		}
		java.util.Set<Draft.LocalPoi> keep = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
		keep.addAll(pois);
		d.retainPois(keep::contains);
	}

	/** The keys whose cell differs between a snapshot and the draft now (placed, replaced or removed). */
	public static LongOpenHashSet changed(Long2ObjectMap<Draft.Cell> before, Draft d) {
		LongOpenHashSet out = new LongOpenHashSet();
		for (var e : d.cells().long2ObjectEntrySet()) {
			if (before.get(e.getLongKey()) != e.getValue()) {
				out.add(e.getLongKey());
			}
		}
		for (long k : before.keySet()) {
			if (!d.cells().containsKey(k)) {
				out.add(k);
			}
		}
		return out;
	}
}
