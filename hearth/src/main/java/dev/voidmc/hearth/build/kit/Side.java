package dev.voidmc.hearth.build.kit;

import dev.voidmc.hearth.build.draft.Dirs;
import net.minecraft.core.Direction;

/**
 * One wall of a rectangle, walked as a person sees it from outside: cell 0 is at the left end, the last cell at the right
 * end. Lets components treat the four walls alike (a window "at cell 3 of the right wall" without caring which way the
 * right wall runs).
 *
 * @param out   the outward direction (local label, see {@link Dirs})
 * @param x0    local x of cell 0
 * @param z0    local z of cell 0
 * @param length number of cells, corners included
 */
public record Side(Direction out, int x0, int z0, int length) {
	/** The four walls of a rectangle (inclusive corners), front, right, back, left. */
	public static Side[] of(int x0, int z0, int x1, int z1) {
		int ax = Math.min(x0, x1);
		int bx = Math.max(x0, x1);
		int az = Math.min(z0, z1);
		int bz = Math.max(z0, z1);
		return new Side[]{
			new Side(Dirs.FRONT, ax, az, bx - ax + 1),
			new Side(Dirs.RIGHT, bx, az, bz - az + 1),
			new Side(Dirs.BACK, bx, bz, bx - ax + 1),
			new Side(Dirs.LEFT, ax, bz, bz - az + 1)
		};
	}

	/** One wall of a rectangle by its outward direction. */
	public static Side of(int x0, int z0, int x1, int z1, Direction out) {
		for (Side s : of(x0, z0, x1, z1)) {
			if (s.out == out) {
				return s;
			}
		}
		throw new IllegalArgumentException("not a horizontal direction: " + out);
	}

	/** The direction cells advance in (to the right, seen from outside). */
	public Direction along() {
		return Dirs.right(this.out.getOpposite());
	}

	public int x(int i) {
		return this.x0 + Dirs.dx(along()) * i;
	}

	public int z(int i) {
		return this.z0 + Dirs.dz(along()) * i;
	}

	/** The coordinate along the wall's axis of cell i (x for front/back walls, z for side walls). */
	public int column(int i) {
		return this.out.getAxis() == Direction.Axis.Z ? x(i) : z(i);
	}

	/** The cell index of a column (inverse of {@link #column}); may be outside 0..last for columns beyond the wall. */
	public int index(int column) {
		Direction a = along();
		return a.getAxis() == Direction.Axis.X ? (column - this.x0) * Dirs.dx(a) : (column - this.z0) * Dirs.dz(a);
	}

	/** Whether a column lies on this wall. */
	public boolean contains(int column) {
		int i = index(column);
		return i >= 0 && i < this.length;
	}

	/** The cell {@code k} blocks outside the wall at cell i (k = 1 is just outside). */
	public int outX(int i, int k) {
		return x(i) + Dirs.dx(this.out) * k;
	}

	public int outZ(int i, int k) {
		return z(i) + Dirs.dz(this.out) * k;
	}

	public int last() {
		return this.length - 1;
	}

	/** The middle cell (left of middle for even lengths). */
	public int middle() {
		return (this.length - 1) / 2;
	}

	public boolean isCorner(int i) {
		return i == 0 || i == this.length - 1;
	}

	/** The axis the wall runs along. */
	public Direction.Axis axis() {
		return along().getAxis();
	}

	/** The same wall moved {@code k} blocks outward (inward for negative k), keeping its cells. */
	public Side shifted(int k) {
		return new Side(this.out, this.x0 + Dirs.dx(this.out) * k, this.z0 + Dirs.dz(this.out) * k, this.length);
	}

	/** A sub-run of this wall from cell {@code from} for {@code n} cells. */
	public Side sub(int from, int n) {
		return new Side(this.out, x(from), z(from), n);
	}
}
