package dev.voidmc.hearth.build.blueprint;

import dev.voidmc.hearth.api.PlotSpec;
import dev.voidmc.hearth.build.draft.Dirs;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Maps a plot's local frame (see {@link Dirs}) to world coordinates. The local frame is the plot seen from the street:
 * {@code x} across the frontage left to right, {@code z} from the front row (0) to the back row (depth - 1), {@code y}
 * from the floor level. Local block states use direction labels as if the plot faced SOUTH, so a state is taken to
 * the world by a plain rotation; positions additionally flip z (the local frame's NORTH is +z).
 */
public final class LocalFrame {
	private final int ox;
	private final int oy;
	private final int oz;
	private final int sizeX;
	private final int sizeZ;
	private final Direction facing;
	private final Rotation rotation;

	public LocalFrame(PlotSpec spec) {
		this(spec.origin(), spec.sizeX(), spec.sizeZ(), spec.facing());
	}

	public LocalFrame(BlockPos origin, int sizeX, int sizeZ, Direction facing) {
		this.ox = origin.getX();
		this.oy = origin.getY();
		this.oz = origin.getZ();
		this.sizeX = sizeX;
		this.sizeZ = sizeZ;
		this.facing = facing;
		this.rotation = switch (facing) {
			case SOUTH -> Rotation.NONE;
			case NORTH -> Rotation.CLOCKWISE_180;
			case EAST -> Rotation.COUNTERCLOCKWISE_90;
			case WEST -> Rotation.CLOCKWISE_90;
			default -> throw new IllegalArgumentException("a plot faces a horizontal direction, not " + facing);
		};
	}

	/** Width along the street. */
	public int frontage() {
		return this.facing.getAxis() == Direction.Axis.Z ? this.sizeX : this.sizeZ;
	}

	/** Depth away from the street. */
	public int depth() {
		return this.facing.getAxis() == Direction.Axis.Z ? this.sizeZ : this.sizeX;
	}

	public Direction facing() {
		return this.facing;
	}

	public Rotation rotation() {
		return this.rotation;
	}

	public int worldX(int lx, int lz) {
		return switch (this.facing) {
			case SOUTH -> this.ox + lx;
			case NORTH -> this.ox + this.sizeX - 1 - lx;
			case EAST -> this.ox + this.sizeX - 1 - lz;
			default -> this.ox + lz;
		};
	}

	public int worldZ(int lx, int lz) {
		return switch (this.facing) {
			case SOUTH -> this.oz + this.sizeZ - 1 - lz;
			case NORTH -> this.oz + lz;
			case EAST -> this.oz + this.sizeZ - 1 - lx;
			default -> this.oz + lx;
		};
	}

	public int worldY(int ly) {
		return this.oy + ly;
	}

	/** The local x of a world position (inverse of {@link #worldX}/{@link #worldZ}). */
	public int localX(int wx, int wz) {
		return switch (this.facing) {
			case SOUTH -> wx - this.ox;
			case NORTH -> this.ox + this.sizeX - 1 - wx;
			case EAST -> this.oz + this.sizeZ - 1 - wz;
			default -> wz - this.oz;
		};
	}

	/** The local z of a world position. */
	public int localZ(int wx, int wz) {
		return switch (this.facing) {
			case SOUTH -> this.oz + this.sizeZ - 1 - wz;
			case NORTH -> wz - this.oz;
			case EAST -> this.ox + this.sizeX - 1 - wx;
			default -> wx - this.ox;
		};
	}

	public BlockPos toWorld(int lx, int ly, int lz) {
		return new BlockPos(worldX(lx, lz), worldY(ly), worldZ(lx, lz));
	}

	/** A local block state turned to the plot's facing. */
	public BlockState toWorld(BlockState local) {
		return this.rotation == Rotation.NONE ? local : local.rotate(this.rotation);
	}

	/** A local direction label turned to the plot's facing. */
	public Direction toWorld(Direction local) {
		return this.rotation.rotate(local);
	}

	/** The local direction label that points the given world direction. */
	public Direction toLocal(Direction world) {
		Direction d = world;
		for (int i = 0; i < 4; i++) {
			if (this.rotation.rotate(d) == world) {
				return d;
			}
			d = d.getClockWise();
		}
		return world;
	}
}
