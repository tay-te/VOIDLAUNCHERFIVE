package dev.voidmc.hearth.build.age;

import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.terrain.Site;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CarpetBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.ScaffoldingBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.VegetationBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import org.jspecify.annotations.Nullable;

/**
 * The way in: the walk from the street to the main entrance in the building as drawn, by the walking rules of the
 * blueprint checks (feet and head free, something to stand on, a block up or three down a step, ladders climbed).
 * Decay keeps it (the steps, decks and ladders under it stand; nothing grows or falls on it), so a ruin can always be
 * walked into from the street, as the invariants ask.
 */
final class Access {
	private Access() {
	}

	/** Collects what a failed search reached (tests only). */
	static @Nullable LongOpenHashSet DEBUG;

	/** The feet cells of the way from the street to the main entrance (empty if the drawing has no entrance or no way). */
	static LongOpenHashSet route(Draft d) {
		LongOpenHashSet out = new LongOpenHashSet();
		if (d.entrances().isEmpty()) {
			return out;
		}
		int[] main = d.entrances().get(0);
		long target = -1;
		LongArrayFIFOQueue q = new LongArrayFIFOQueue();
		Long2LongOpenHashMap parent = new Long2LongOpenHashMap();
		parent.defaultReturnValue(Long.MIN_VALUE);
		int w = d.frontage();
		int dp = d.depth();
		for (int x = 0; x < w; x++) {
			int g = Math.max(d.site().ground(x, -1), 0);
			for (int y = g - 2; y <= g + 1; y++) {
				if (walkable(d, x, y, -1)) {
					long k = Draft.key(x, y, -1);
					if (!parent.containsKey(k)) {
						parent.put(k, k);
						q.enqueue(k);
					}
					break;
				}
			}
		}
		while (!q.isEmpty()) {
			long k = q.dequeueLong();
			int x = Draft.keyX(k);
			int y = Draft.keyY(k);
			int z = Draft.keyZ(k);
			if (x == main[0] && z == main[2] && Math.abs(y - main[1]) <= 2) {
				target = k;
				break;
			}
			for (Direction dir : Dirs.HORIZONTAL) {
				int nx = x + Dirs.dx(dir);
				int nz = z + Dirs.dz(dir);
				if (nx < -2 || nx > w + 1 || nz < -2 || nz > dp + 1) {
					continue;
				}
				for (int dy = 1; dy >= -3; dy--) {
					int ny = y + dy;
					if (dy == 1 && !passable(at(d, x, y + 2, z))) {
						continue;
					}
					if (walkable(d, nx, ny, nz)) {
						long n = Draft.key(nx, ny, nz);
						if (!parent.containsKey(n)) {
							parent.put(n, k);
							q.enqueue(n);
						}
						break;
					}
					if (dy == 0 && step(at(d, nx, ny, nz)) && walkable(d, nx, ny + 1, nz) && passable(at(d, x, y + 2, z))) {
						long n = Draft.key(nx, ny + 1, nz);
						if (!parent.containsKey(n)) {
							parent.put(n, k);
							q.enqueue(n);
						}
						break;
					}
					if (!passable(at(d, nx, ny, nz)) || dy < 0 && !passable(at(d, nx, ny + 1, nz))) {
						if (dy <= 0) {
							break;
						}
					}
				}
			}
			BlockState here = at(d, x, y, z);
			if (here.getBlock() instanceof LadderBlock || here.getBlock() instanceof ScaffoldingBlock) {
				for (int dy : new int[]{1, -1}) {
					if (walkable(d, x, y + dy, z) || at(d, x, y + dy, z).getBlock() instanceof LadderBlock) {
						long n = Draft.key(x, y + dy, z);
						if (!parent.containsKey(n)) {
							parent.put(n, k);
							q.enqueue(n);
						}
					}
				}
			}
		}
		if (target == -1) {
			if (DEBUG != null) {
				DEBUG.addAll(parent.keySet());
			}
			return out;
		}
		long k = target;
		while (true) {
			out.add(k);
			long p = parent.get(k);
			if (p == k || p == Long.MIN_VALUE) {
				break;
			}
			k = p;
		}
		return out;
	}

	/** The block at a cell: drawn, natural ground (a stand-in), water, or air. */
	static BlockState at(Draft d, int x, int y, int z) {
		BlockState s = d.get(x, y, z);
		if (s != null) {
			return s;
		}
		if (y < d.site().ground(x, z)) {
			return Blocks.DIRT.defaultBlockState();
		}
		int wy = d.site().water(x, z);
		return wy != Site.NONE && y < wy ? Blocks.WATER.defaultBlockState() : Blocks.AIR.defaultBlockState();
	}

	static boolean passable(BlockState s) {
		Block b = s.getBlock();
		if (s.isAir() || b instanceof DoorBlock || b instanceof FenceGateBlock || b instanceof CarpetBlock || b instanceof VegetationBlock || b instanceof LadderBlock
			|| b instanceof ScaffoldingBlock || b == Blocks.WATER || b == Blocks.VINE || b == Blocks.COBWEB) {
			return true;
		}
		if (b instanceof TrapDoorBlock) {
			return s.getValue(TrapDoorBlock.OPEN) || s.getValue(TrapDoorBlock.HALF) == Half.BOTTOM;
		}
		return s.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).isEmpty();
	}

	static boolean standable(BlockState s) {
		Block b = s.getBlock();
		if (s.isAir() || b instanceof FenceBlock || b instanceof WallBlock || b instanceof FenceGateBlock) {
			return false;
		}
		return b == Blocks.WATER || !s.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).isEmpty();
	}

	static boolean walkable(Draft d, int x, int y, int z) {
		BlockState feet = at(d, x, y, z);
		if (!passable(feet) || !passable(at(d, x, y + 1, z))) {
			return false;
		}
		if (feet.getBlock() instanceof LadderBlock || feet.getBlock() instanceof ScaffoldingBlock) {
			return true;
		}
		BlockState below = at(d, x, y - 1, z);
		return standable(below) || below.getBlock() instanceof LadderBlock;
	}

	static boolean step(@Nullable BlockState s) {
		return s != null && (s.getBlock() instanceof StairBlock && s.getValue(StairBlock.HALF) == Half.BOTTOM
			|| s.getBlock() instanceof SlabBlock && s.getValue(SlabBlock.TYPE) == SlabType.BOTTOM || s.getBlock() instanceof BedBlock);
	}
}
