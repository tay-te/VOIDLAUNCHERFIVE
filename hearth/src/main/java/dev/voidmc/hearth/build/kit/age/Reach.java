package dev.voidmc.hearth.build.kit.age;

import dev.voidmc.hearth.api.PoiType;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.terrain.Site;
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
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.ScaffoldingBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.VegetationBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.pathfinder.PathComputationType;

/**
 * Who can still get where after an addition, read on the drawing itself (the walk of the blueprint checks, in the plot's
 * frame, before the compiler runs): the beds, job sites and stores no one can walk to from an entrance. An addition that
 * leaves one more of them out of reach is taken down again ({@code build.age.Accretion}).
 */
public final class Reach {
	private Reach() {
	}

	/** The beds (head cells), job sites and stores that cannot be walked to from an entrance, as {@link Draft#key}s. */
	public static LongOpenHashSet unreached(Draft d) {
		LongOpenHashSet inside = walk(d);
		LongOpenHashSet out = new LongOpenHashSet();
		for (var e : d.cells().long2ObjectEntrySet()) {
			BlockState s = e.getValue().state();
			if (s.getBlock() instanceof BedBlock && s.getValue(BedBlock.PART) == BedPart.HEAD) {
				long k = e.getLongKey();
				int x = Draft.keyX(k);
				int y = Draft.keyY(k);
				int z = Draft.keyZ(k);
				boolean ok = false;
				Direction foot = s.getValue(BedBlock.FACING).getOpposite();
				for (Direction dir : Dirs.HORIZONTAL) {
					ok |= near(inside, x + Dirs.dx(dir), y, z + Dirs.dz(dir))
						|| near(inside, x + Dirs.dx(foot) + Dirs.dx(dir), y, z + Dirs.dz(foot) + Dirs.dz(dir));
				}
				if (!ok) {
					out.add(k);
				}
			}
		}
		for (Draft.LocalPoi p : d.pois()) {
			if ((p.type() == PoiType.JOB_SITE || p.type() == PoiType.STORAGE) && !near(inside, p.x(), p.y(), p.z())) {
				out.add(Draft.key(p.x(), p.y(), p.z()));
			}
		}
		return out;
	}

	private static boolean near(LongOpenHashSet set, int x, int y, int z) {
		return set.contains(Draft.key(x, y, z)) || set.contains(Draft.key(x, y + 1, z)) || set.contains(Draft.key(x, y - 1, z));
	}

	static BlockState at(Draft d, int x, int y, int z) {
		Draft.Cell c = d.cell(x, y, z);
		if (c != null) {
			return c.state();
		}
		Site site = d.site();
		if (y < site.ground(x, z)) {
			return Blocks.STONE.defaultBlockState();
		}
		int w = site.water(x, z);
		if (w != Site.NONE && y < w) {
			return Blocks.WATER.defaultBlockState();
		}
		return Blocks.AIR.defaultBlockState();
	}

	private static boolean passable(BlockState s) {
		Block b = s.getBlock();
		if (s.isAir() || b instanceof DoorBlock || b instanceof FenceGateBlock || b instanceof CarpetBlock || b instanceof VegetationBlock
			|| b instanceof LadderBlock || b instanceof ScaffoldingBlock || b instanceof LiquidBlock
			|| b instanceof LanternBlock && s.getValue(LanternBlock.HANGING)) {
			return true;
		}
		if (b instanceof TrapDoorBlock) {
			return s.getValue(TrapDoorBlock.OPEN) || s.getValue(TrapDoorBlock.HALF) == Half.BOTTOM;
		}
		var shape = s.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
		return shape.isEmpty() || s.isPathfindable(PathComputationType.LAND) && !(b instanceof FenceBlock) && !(b instanceof WallBlock)
			&& shape.max(Direction.Axis.Y) <= 0.2;
	}

	private static boolean standable(BlockState s) {
		Block b = s.getBlock();
		if (s.isAir() || b instanceof FenceBlock || b instanceof WallBlock || b instanceof FenceGateBlock) {
			return false;
		}
		return b instanceof LiquidBlock || !s.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).isEmpty();
	}

	private static boolean climbable(BlockState s) {
		return s.getBlock() instanceof LadderBlock || s.getBlock() instanceof ScaffoldingBlock;
	}

	private static boolean walkable(Draft d, int x, int y, int z) {
		BlockState feet = at(d, x, y, z);
		if (!passable(feet) || !passable(at(d, x, y + 1, z))) {
			return false;
		}
		if (climbable(feet)) {
			return true;
		}
		BlockState below = at(d, x, y - 1, z);
		return standable(below) || climbable(below);
	}

	private static boolean step(BlockState s) {
		return s.getBlock() instanceof StairBlock && s.getValue(StairBlock.HALF) == Half.BOTTOM
			|| s.getBlock() instanceof SlabBlock && s.getValue(SlabBlock.TYPE) == SlabType.BOTTOM || s.getBlock() instanceof BedBlock;
	}

	/** Every cell walkable from the entrances (or the street before the plot), within the plot and a margin. */
	static LongOpenHashSet walk(Draft d) {
		int x0 = -2;
		int x1 = d.frontage() + 1;
		int z0 = -3;
		int z1 = d.depth() + 1;
		int y0 = -8;
		int y1 = d.maxHeight() + 2;
		LongOpenHashSet seen = new LongOpenHashSet();
		LongArrayFIFOQueue q = new LongArrayFIFOQueue();
		java.util.List<int[]> starts = new java.util.ArrayList<>(d.entrances());
		if (starts.isEmpty()) {
			for (int x = 0; x < d.frontage(); x++) {
				starts.add(new int[]{x, Math.max(0, d.site().ground(x, -1)), -1});
			}
		}
		for (int[] p : starts) {
			for (int dy = 1; dy >= -2; dy--) {
				if (walkable(d, p[0], p[1] + dy, p[2])) {
					long k = Draft.key(p[0], p[1] + dy, p[2]);
					if (seen.add(k)) {
						q.enqueue(k);
					}
				}
			}
		}
		while (!q.isEmpty()) {
			long k = q.dequeueLong();
			int x = Draft.keyX(k);
			int y = Draft.keyY(k);
			int z = Draft.keyZ(k);
			boolean climb = climbable(at(d, x, y, z));
			for (Direction dir : Dirs.HORIZONTAL) {
				int nx = x + Dirs.dx(dir);
				int nz = z + Dirs.dz(dir);
				if (nx < x0 || nx > x1 || nz < z0 || nz > z1) {
					continue;
				}
				for (int dy = 1; dy >= -3; dy--) {
					int ny = y + dy;
					if (ny < y0 || ny > y1) {
						continue;
					}
					if (dy == 1 && !passable(at(d, x, y + 2, z))) {
						continue;
					}
					if (walkable(d, nx, ny, nz)) {
						if (seen.add(Draft.key(nx, ny, nz))) {
							q.enqueue(Draft.key(nx, ny, nz));
						}
						break;
					}
					if (dy == 0 && step(at(d, nx, ny, nz)) && walkable(d, nx, ny + 1, nz) && passable(at(d, x, y + 2, z))) {
						if (seen.add(Draft.key(nx, ny + 1, nz))) {
							q.enqueue(Draft.key(nx, ny + 1, nz));
						}
						break;
					}
					if ((!passable(at(d, nx, ny, nz)) || dy < 0 && !passable(at(d, nx, ny + 1, nz))) && dy <= 0) {
						break;
					}
				}
			}
			if (climb) {
				for (int dy : new int[]{1, -1}) {
					if (walkable(d, x, y + dy, z) || climbable(at(d, x, y + dy, z))) {
						if (seen.add(Draft.key(x, y + dy, z))) {
							q.enqueue(Draft.key(x, y + dy, z));
						}
					}
				}
			}
		}
		return seen;
	}
}
