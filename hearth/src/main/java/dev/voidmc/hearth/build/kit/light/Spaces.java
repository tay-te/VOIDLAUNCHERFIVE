package dev.voidmc.hearth.build.kit.light;

import it.unimi.dsi.fastutil.ints.IntArrayFIFOQueue;
import java.util.BitSet;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CarpetBlock;
import net.minecraft.world.level.block.ChainBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.ScaffoldingBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TransparentBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.VegetationBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.pathfinder.PathComputationType;

/**
 * Where people stand in a {@link Grid}, and what kind of space each standing cell is: walked to from the doors or not,
 * under a roof or open to the sky, inside a room (walls all round) or on a porch, a gallery or an open hall. The walking
 * rules are those of {@code BlueprintChecks} (a cell is walkable when feet and head pass and the block below can be
 * stood on; folk step up one block and down three).
 *
 * <p>A cell is <b>roofed</b> when something solid or glazed stands anywhere above the head (leaves, plants, chains and
 * lanterns are not roofs). A roofed cell is <b>enclosed</b> when, looking out level at head height in each of the four
 * directions, a wall (any block a person cannot walk through: masonry, glass, a door, a fence) comes before the roof
 * ends: a room. Porches, loggias, galleries round a court, arcades, the space under a stilt house and an open-sided
 * market floor are roofed but not enclosed.
 */
public final class Spaces {
	private final Grid g;
	/** Feet cells that are walkable. */
	private final BitSet walkable = new BitSet();
	private final BitSet reached = new BitSet();
	private final int[] roofAbove;

	/** Columns folk may walk in: the plot and {@value #WALK_MARGIN} round it (the ground a draft knows exactly). */
	public static final int WALK_MARGIN = 4;
	private final boolean[] near;

	public Spaces(Grid g, List<BlockPos> starts) {
		this.g = g;
		this.near = new boolean[g.sx * g.sz];
		for (int z = 0; z < g.sz; z++) {
			for (int x = 0; x < g.sx; x++) {
				if (!g.plot[z * g.sx + x]) {
					continue;
				}
				for (int dz = -WALK_MARGIN; dz <= WALK_MARGIN; dz++) {
					for (int dx = -WALK_MARGIN; dx <= WALK_MARGIN; dx++) {
						int nx = x + dx;
						int nz = z + dz;
						if (nx >= 0 && nz >= 0 && nx < g.sx && nz < g.sz) {
							this.near[nz * g.sx + nx] = true;
						}
					}
				}
			}
		}
		// what each block is to someone walking, classified once per kind of block
		this.flags = new byte[g.volume()];
		it.unimi.dsi.fastutil.objects.Reference2ByteOpenHashMap<BlockState> kinds = new it.unimi.dsi.fastutil.objects.Reference2ByteOpenHashMap<>();
		kinds.defaultReturnValue((byte) -1);
		for (int i = 0; i < this.flags.length; i++) {
			BlockState s = g.states[i];
			byte f = kinds.getByte(s);
			if (f == -1) {
				f = classify(s);
				kinds.put(s, f);
			}
			this.flags[i] = f;
		}
		for (int y = 0; y < g.sy; y++) {
			for (int z = 0; z < g.sz; z++) {
				for (int x = 0; x < g.sx; x++) {
					if (this.near[z * g.sx + x] && walkableAt(g.x0 + x, g.y0 + y, g.z0 + z)) {
						this.walkable.set((y * g.sz + z) * g.sx + x);
					}
				}
			}
		}
		// the lowest roof above each cell, per column scanning down
		this.roofAbove = new int[g.volume()];
		for (int z = 0; z < g.sz; z++) {
			for (int x = 0; x < g.sx; x++) {
				int roof = Integer.MAX_VALUE;
				for (int y = g.sy - 1; y >= 0; y--) {
					int i = (y * g.sz + z) * g.sx + x;
					this.roofAbove[i] = roof;
					if ((this.flags[i] & ROOF) != 0) {
						roof = g.y0 + y;
					}
				}
			}
		}
		walk(starts);
	}

	/** Per grid cell: what its block is to someone walking ({@link #PASS}, {@link #STAND}, ...). */
	private final byte[] flags;
	private static final byte PASS = 1;
	private static final byte STAND = 2;
	private static final byte CLIMB = 4;
	private static final byte ROOF = 8;
	private static final byte WALL = 16;
	private static final byte STEP = 32;
	private static final byte LIQUID = 64;

	private static byte classify(BlockState s) {
		int f = 0;
		if (passable(s)) {
			f |= PASS;
		}
		if (standable(s)) {
			f |= STAND;
		}
		Block b = s.getBlock();
		if (b instanceof LadderBlock || b instanceof ScaffoldingBlock) {
			f |= CLIMB;
		}
		if (roofLike(s)) {
			f |= ROOF;
		}
		if (wallLike(s)) {
			f |= WALL;
		}
		if (step(s)) {
			f |= STEP;
		}
		if (b instanceof LiquidBlock) {
			f |= LIQUID;
		}
		return (byte) f;
	}

	/** The flags of the block at a position ({@link #classify}); outside the grid, bedrock's. */
	private int flagsAt(int x, int y, int z) {
		return this.g.contains(x, y, z) ? this.flags[this.g.index(x, y, z)] : OUTSIDE_FLAGS;
	}

	private static final int OUTSIDE_FLAGS = classify(net.minecraft.world.level.block.Blocks.BEDROCK.defaultBlockState());

	public Grid grid() {
		return this.g;
	}

	// ------------------------------------------------------------------------------------------- block classes

	/** Feet or head can be in this block (BlueprintChecks' rules). */
	public static boolean passable(BlockState s) {
		Block b = s.getBlock();
		if (s.isAir() || b instanceof DoorBlock || b instanceof FenceGateBlock || b instanceof CarpetBlock || b instanceof VegetationBlock
			|| b instanceof LadderBlock || b instanceof ScaffoldingBlock || b instanceof LiquidBlock || hangs(s)) {
			return true;
		}
		if (b instanceof TrapDoorBlock) {
			return s.getValue(TrapDoorBlock.OPEN) || s.getValue(TrapDoorBlock.HALF) == Half.BOTTOM;
		}
		if (b instanceof SnowLayerBlock) {
			return s.getValue(SnowLayerBlock.LAYERS) <= 4;
		}
		var shape = s.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
		return shape.isEmpty() || s.isPathfindable(PathComputationType.LAND) && !(b instanceof FenceBlock) && !(b instanceof WallBlock)
			&& shape.max(Direction.Axis.Y) <= 0.2;
	}

	/**
	 * A block a person can stand on: not a fence, a wall or a gate, nor the small things set on furniture (candles, a
	 * lantern, a flower pot, a cake, a skull) — a candle stood on a table makes no step up onto it.
	 */
	public static boolean standable(BlockState s) {
		Block b = s.getBlock();
		// (nobody stands on a door's lower half, inside its upper one)
		if (s.isAir() || b instanceof FenceBlock || b instanceof WallBlock || b instanceof FenceGateBlock || b instanceof DoorBlock
			|| b instanceof net.minecraft.world.level.block.CandleBlock
			|| b instanceof LanternBlock || b instanceof net.minecraft.world.level.block.FlowerPotBlock || b instanceof net.minecraft.world.level.block.CakeBlock
			|| b instanceof net.minecraft.world.level.block.CandleCakeBlock || b instanceof net.minecraft.world.level.block.AbstractSkullBlock
			|| b instanceof net.minecraft.world.level.block.SeaPickleBlock) {
			return false;
		}
		if (b instanceof LiquidBlock) {
			return true;
		}
		return !s.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).isEmpty();
	}

	/** Lanterns hung up, vertical chains, hanging signs, vines and roots. */
	public static boolean hangs(BlockState s) {
		Block b = s.getBlock();
		return b instanceof LanternBlock && s.getValue(LanternBlock.HANGING) || b instanceof ChainBlock
			|| b instanceof net.minecraft.world.level.block.CeilingHangingSignBlock || b instanceof net.minecraft.world.level.block.VineBlock
			|| b instanceof net.minecraft.world.level.block.HangingRootsBlock;
	}

	/** Something a roof is made of: anything solid or glazed above a person, but not leaves, plants or hung fixtures. */
	public static boolean roofLike(BlockState s) {
		Block b = s.getBlock();
		if (s.isAir() || b instanceof LeavesBlock || b instanceof VegetationBlock || b instanceof LiquidBlock || hangs(s) || b instanceof ScaffoldingBlock) {
			return false;
		}
		if (b instanceof TransparentBlock || b instanceof IronBarsBlock) {
			return true;
		}
		return !s.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).isEmpty();
	}

	/** A block that closes a view at head height: anything a person cannot pass, and doors. */
	private static boolean wallLike(BlockState s) {
		return !passable(s) || s.getBlock() instanceof DoorBlock || s.getBlock() instanceof FenceGateBlock;
	}

	/** A stair, a bottom slab or a bed: walked up onto from beside it. */
	private static boolean step(BlockState s) {
		return s.getBlock() instanceof StairBlock && s.getValue(StairBlock.HALF) == Half.BOTTOM
			|| s.getBlock() instanceof SlabBlock && s.getValue(SlabBlock.TYPE) == SlabType.BOTTOM || s.getBlock() instanceof BedBlock;
	}

	// ------------------------------------------------------------------------------------------- cells

	private boolean walkableAt(int x, int y, int z) {
		int feet = flagsAt(x, y, z);
		if ((feet & PASS) == 0 || (flagsAt(x, y + 1, z) & PASS) == 0) {
			return false;
		}
		if ((feet & CLIMB) != 0) {
			return true;
		}
		return (flagsAt(x, y - 1, z) & (STAND | CLIMB)) != 0;
	}

	/** Feet can be at this cell. */
	public boolean walkable(int x, int y, int z) {
		return this.g.contains(x, y, z) && this.walkable.get(this.g.index(x, y, z));
	}

	/** Something now stands where one could stand (a lantern the pass set on the floor): nobody stands there any more. */
	void stoodOn(int x, int y, int z) {
		if (this.g.contains(x, y, z)) {
			this.walkable.clear(this.g.index(x, y, z));
			this.reached.clear(this.g.index(x, y, z));
		}
	}

	/** Walked to from the starts (the doors, the street). */
	public boolean reached(int x, int y, int z) {
		return this.g.contains(x, y, z) && this.reached.get(this.g.index(x, y, z));
	}

	/** Feet or floor are in water or lava (folk swim; the river is not the building's to light). */
	public boolean wet(int x, int y, int z) {
		return ((flagsAt(x, y, z) | flagsAt(x, y - 1, z)) & LIQUID) != 0;
	}

	/** Whether the feet cell is a doorway (a door or a gate). */
	public boolean doorway(int x, int y, int z) {
		Block b = this.g.at(x, y, z).getBlock();
		return b instanceof DoorBlock || b instanceof FenceGateBlock;
	}

	/** The y of the lowest roof block above the head, or {@link Integer#MAX_VALUE} when open to the sky. */
	public int roofY(int x, int y, int z) {
		if (!this.g.contains(x, y + 1, z)) {
			return Integer.MAX_VALUE;
		}
		return this.roofAbove[this.g.index(x, y + 1, z)];
	}

	public boolean roofed(int x, int y, int z) {
		return roofY(x, y, z) != Integer.MAX_VALUE;
	}

	/**
	 * A roofed cell with walls all round: looking out level at head height (and at feet height, for low rooms) in each
	 * horizontal direction, a wall comes before the roof ends.
	 */
	public boolean enclosed(int x, int y, int z) {
		if (!roofed(x, y, z)) {
			return false;
		}
		for (Direction d : Direction.Plane.HORIZONTAL) {
			int[] s = this.g.step(d);
			if (!closedToward(x, y + 1, z, s[0], s[2])) {
				return false;
			}
		}
		return true;
	}

	private boolean closedToward(int x, int y, int z, int dx, int dz) {
		int cx = x;
		int cz = z;
		for (int n = 0; n < 96; n++) {
			cx += dx;
			cz += dz;
			if (!this.g.contains(cx, y, cz)) {
				return true;
			}
			if ((flagsAt(cx, y, cz) & WALL) != 0) {
				return true;
			}
			if (!roofed(cx, y - 1, cz)) {
				return false;
			}
		}
		return true;
	}

	// ------------------------------------------------------------------------------------------- walking

	private void walk(List<BlockPos> starts) {
		Grid g = this.g;
		IntArrayFIFOQueue q = new IntArrayFIFOQueue();
		for (BlockPos p : starts) {
			for (int dy = 0; dy >= -2; dy--) {
				if (walkable(p.getX(), p.getY() + dy, p.getZ())) {
					enqueue(q, p.getX(), p.getY() + dy, p.getZ());
					break;
				}
			}
			if (walkable(p.getX(), p.getY() + 1, p.getZ())) {
				enqueue(q, p.getX(), p.getY() + 1, p.getZ());
			}
		}
		while (!q.isEmpty()) {
			int i = q.dequeueInt();
			int x = g.x0 + i % g.sx;
			int rest = i / g.sx;
			int z = g.z0 + rest % g.sz;
			int y = g.y0 + rest / g.sz;
			boolean climb = (flagsAt(x, y, z) & CLIMB) != 0;
			boolean headroom = (flagsAt(x, y + 2, z) & PASS) != 0;
			for (Direction d : Direction.Plane.HORIZONTAL) {
				int[] s = g.step(d);
				int nx = x + s[0];
				int nz = z + s[2];
				for (int dy = 1; dy >= -3; dy--) {
					int ny = y + dy;
					if (dy == 1 && !headroom) {
						continue;
					}
					if (walkable(nx, ny, nz)) {
						enqueue(q, nx, ny, nz);
						break;
					}
					int there = flagsAt(nx, ny, nz);
					if (dy == 0 && (there & STEP) != 0 && walkable(nx, ny + 1, nz) && headroom) {
						enqueue(q, nx, ny + 1, nz);
						break;
					}
					if ((there & PASS) == 0 || dy < 0 && (flagsAt(nx, ny + 1, nz) & PASS) == 0) {
						if (dy <= 0) {
							break;
						}
					}
				}
			}
			if (climb) {
				for (int dy : new int[]{1, -1}) {
					if (walkable(x, y + dy, z) || g.at(x, y + dy, z).getBlock() instanceof LadderBlock) {
						enqueue(q, x, y + dy, z);
					}
				}
			}
		}
	}

	private void enqueue(IntArrayFIFOQueue q, int x, int y, int z) {
		if (!this.g.contains(x, y, z)) {
			return;
		}
		int i = this.g.index(x, y, z);
		if (!this.reached.get(i)) {
			this.reached.set(i);
			q.enqueue(i);
		}
	}
}
