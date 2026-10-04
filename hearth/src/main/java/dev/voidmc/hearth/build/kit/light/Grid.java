package dev.voidmc.hearth.build.kit.light;

import java.util.Arrays;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The blocks of a box, read once from a building and the natural ground under it: the voxels the light flood and the
 * space classification work on. Coordinates are those of the source (a draft's local frame, or the world for a compiled
 * blueprint); {@link #label} says which {@link Direction} a step along an axis is, so that block shapes (stairs, slabs)
 * occlude light on the right faces in either frame.
 *
 * <p>Outside the box everything reads as bedrock: light does not leave it and nobody walks out of it.
 */
public final class Grid {
	/** What a grid is read from. */
	public interface Source {
		/** The block at a position: what the building places, else the natural ground, water or air. */
		BlockState at(int x, int y, int z);

		/** Whether the building places something (air included) at the position. */
		boolean placed(int x, int y, int z);

		/** Whether a column lies inside the plot (the footprint the rules apply to). */
		boolean inPlot(int x, int z);

		/** The direction a step of +1 along z is named by in this frame (SOUTH in the world, NORTH in a draft). */
		Direction plusZ();
	}

	public final int x0;
	public final int y0;
	public final int z0;
	public final int sx;
	public final int sy;
	public final int sz;
	final BlockState[] states;
	final boolean[] placed;
	final boolean[] plot;
	private final Direction plusZ;
	private final Direction minusZ;
	private static final BlockState OUTSIDE = Blocks.BEDROCK.defaultBlockState();

	/** Reads the box between two corners (inclusive). */
	public Grid(Source src, int x0, int y0, int z0, int x1, int y1, int z1) {
		this.x0 = Math.min(x0, x1);
		this.y0 = Math.min(y0, y1);
		this.z0 = Math.min(z0, z1);
		this.sx = Math.abs(x1 - x0) + 1;
		this.sy = Math.abs(y1 - y0) + 1;
		this.sz = Math.abs(z1 - z0) + 1;
		this.states = new BlockState[this.sx * this.sy * this.sz];
		this.placed = new boolean[this.states.length];
		this.plot = new boolean[this.sx * this.sz];
		this.plusZ = src.plusZ();
		this.minusZ = this.plusZ.getOpposite();
		for (int z = 0; z < this.sz; z++) {
			for (int x = 0; x < this.sx; x++) {
				this.plot[z * this.sx + x] = src.inPlot(this.x0 + x, this.z0 + z);
			}
		}
		for (int y = 0; y < this.sy; y++) {
			for (int z = 0; z < this.sz; z++) {
				for (int x = 0; x < this.sx; x++) {
					int i = (y * this.sz + z) * this.sx + x;
					this.states[i] = src.at(this.x0 + x, this.y0 + y, this.z0 + z);
					this.placed[i] = src.placed(this.x0 + x, this.y0 + y, this.z0 + z);
				}
			}
		}
	}

	private Grid(Grid g) {
		this.x0 = g.x0;
		this.y0 = g.y0;
		this.z0 = g.z0;
		this.sx = g.sx;
		this.sy = g.sy;
		this.sz = g.sz;
		this.states = Arrays.copyOf(g.states, g.states.length);
		this.placed = Arrays.copyOf(g.placed, g.placed.length);
		this.plot = g.plot;
		this.plusZ = g.plusZ;
		this.minusZ = g.minusZ;
	}

	/** A copy that can be changed without touching this one. */
	public Grid copy() {
		return new Grid(this);
	}

	public boolean contains(int x, int y, int z) {
		return x >= this.x0 && y >= this.y0 && z >= this.z0 && x < this.x0 + this.sx && y < this.y0 + this.sy && z < this.z0 + this.sz;
	}

	public int index(int x, int y, int z) {
		return ((y - this.y0) * this.sz + (z - this.z0)) * this.sx + (x - this.x0);
	}

	public BlockState at(int x, int y, int z) {
		return contains(x, y, z) ? this.states[index(x, y, z)] : OUTSIDE;
	}

	public boolean placed(int x, int y, int z) {
		return contains(x, y, z) && this.placed[index(x, y, z)];
	}

	/** Whether a column is inside the plot. */
	public boolean inPlot(int x, int z) {
		int ix = x - this.x0;
		int iz = z - this.z0;
		return ix >= 0 && iz >= 0 && ix < this.sx && iz < this.sz && this.plot[iz * this.sx + ix];
	}

	/** Changes a block (the auto-lighter tries fixtures on a copy). */
	public void set(int x, int y, int z, BlockState s) {
		if (contains(x, y, z)) {
			int i = index(x, y, z);
			this.states[i] = s;
			this.placed[i] = true;
		}
	}

	/** The direction a unit step is named by in this grid's frame. */
	public Direction label(int dx, int dy, int dz) {
		if (dy > 0) {
			return Direction.UP;
		}
		if (dy < 0) {
			return Direction.DOWN;
		}
		if (dx > 0) {
			return Direction.EAST;
		}
		if (dx < 0) {
			return Direction.WEST;
		}
		return dz > 0 ? this.plusZ : this.minusZ;
	}

	/** The unit step a direction names in this grid's frame: {dx, dy, dz}. */
	public int[] step(Direction d) {
		if (d == this.plusZ) {
			return new int[]{0, 0, 1};
		}
		if (d == this.minusZ) {
			return new int[]{0, 0, -1};
		}
		return new int[]{d.getStepX(), d.getStepY(), 0};
	}

	public int volume() {
		return this.states.length;
	}
}
