package dev.voidmc.hearth.build.kit.sociallife;

import dev.voidmc.hearth.build.draft.Dirs;
import net.minecraft.core.Direction;

/**
 * A room seen from one of its walls, so a plan drawn once fits the room whichever way it lies on the plot: {@code u} runs
 * along the chosen wall from a corner, {@code v} away from that wall into the room. (ox, oz) is the local cell at u = 0,
 * v = 0 (inside the room, against the wall); {@code uLen} × {@code vLen} cells.
 */
public record RoomFrame(int ox, int oz, Direction uDir, Direction vDir, int uLen, int vLen) {
	public int x(int u, int v) {
		return this.ox + Dirs.dx(this.uDir) * u + Dirs.dx(this.vDir) * v;
	}

	public int z(int u, int v) {
		return this.oz + Dirs.dz(this.uDir) * u + Dirs.dz(this.vDir) * v;
	}

	public int[] at(int u, int v) {
		return new int[]{x(u, v), z(u, v)};
	}

	/** The wall the frame is drawn from lies toward this direction (behind v = 0). */
	public Direction wall() {
		return this.vDir.getOpposite();
	}

	public boolean contains(int u, int v) {
		return u >= 0 && v >= 0 && u < this.uLen && v < this.vLen;
	}

	/** The local rectangle {x0, z0, x1, z1} of the frame's cells u0..u1 × v0..v1. */
	public int[] rect(int u0, int v0, int u1, int v1) {
		int xa = x(u0, v0);
		int za = z(u0, v0);
		int xb = x(u1, v1);
		int zb = z(u1, v1);
		return new int[]{Math.min(xa, xb), Math.min(za, zb), Math.max(xa, xb), Math.max(za, zb)};
	}

	/**
	 * The frame of the room {@code [x0..x1] × [z0..z1]} drawn from its wall toward {@code wall}, starting at the corner on
	 * the side {@code startToward} (a direction along that wall).
	 */
	public static RoomFrame of(int x0, int z0, int x1, int z1, Direction wall, Direction startToward) {
		int ax = Math.min(x0, x1);
		int bx = Math.max(x0, x1);
		int az = Math.min(z0, z1);
		int bz = Math.max(z0, z1);
		Direction v = wall.getOpposite();
		Direction u = startToward.getOpposite();
		// the corner: against the wall, at the start end
		int ox = wall == Dirs.LEFT ? ax : wall == Dirs.RIGHT ? bx : startToward == Dirs.LEFT ? ax : bx;
		int oz = wall == Dirs.FRONT ? az : wall == Dirs.BACK ? bz : startToward == Dirs.FRONT ? az : bz;
		boolean alongX = wall.getAxis() == Direction.Axis.Z;
		int uLen = alongX ? bx - ax + 1 : bz - az + 1;
		int vLen = alongX ? bz - az + 1 : bx - ax + 1;
		return new RoomFrame(ox, oz, u, v, uLen, vLen);
	}
}
