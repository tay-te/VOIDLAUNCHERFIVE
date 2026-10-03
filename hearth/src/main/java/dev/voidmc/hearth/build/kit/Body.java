package dev.voidmc.hearth.build.kit;

import dev.voidmc.hearth.build.draft.Dirs;
import java.util.Arrays;
import net.minecraft.core.Direction;

/**
 * The volume of a rectangular building: the ground storey's wall rectangle (the outer face of its walls, inclusive), the
 * storeys above it with their heights, and how far each storey oversails the one below on each side (jetties). Party
 * walls (shared with a neighbour on the left or right) are flagged so walls, windows and roofs treat them as solid and
 * keep eaves inside the plot.
 *
 * <p>Heights, for storey {@code k} with floor-to-floor height {@code h}: its floor block is at {@code floorY(k) - 1},
 * people stand at {@code floorY(k)}, the room is {@code h - 1} high, and the next floor (or the wall plate under the roof)
 * is at {@code floorY(k + 1) - 1}. {@link #plateY()} is that course for the top storey: roofs start on it.
 */
public final class Body {
	public final int x0;
	public final int z0;
	public final int x1;
	public final int z1;
	public final int baseY;
	private final int[] heights;
	/** Per storey: outset on the front, right, back, left (index by {@link #sideIndex}). */
	private final int[][] outsets;
	public final boolean partyLeft;
	public final boolean partyRight;

	public Body(int x0, int z0, int x1, int z1, int baseY, int[] heights, boolean partyLeft, boolean partyRight) {
		this.x0 = Math.min(x0, x1);
		this.z0 = Math.min(z0, z1);
		this.x1 = Math.max(x0, x1);
		this.z1 = Math.max(z0, z1);
		this.baseY = baseY;
		this.heights = heights.clone();
		this.outsets = new int[heights.length][4];
		this.partyLeft = partyLeft;
		this.partyRight = partyRight;
	}

	/** A one-storey body. */
	public static Body single(int x0, int z0, int x1, int z1, int height) {
		return new Body(x0, z0, x1, z1, 0, new int[]{height}, false, false);
	}

	public static int sideIndex(Direction d) {
		return switch (d) {
			case SOUTH -> 0;
			case EAST -> 1;
			case NORTH -> 2;
			default -> 3;
		};
	}

	/**
	 * Makes storey {@code k} (and every storey above it, cumulatively) oversail by one more block on the given side.
	 * Party sides never jetty.
	 */
	public Body jetty(int k, Direction side) {
		if (side == Dirs.LEFT && this.partyLeft || side == Dirs.RIGHT && this.partyRight) {
			return this;
		}
		for (int s = k; s < this.heights.length; s++) {
			this.outsets[s][sideIndex(side)]++;
		}
		return this;
	}

	/**
	 * Steps storey {@code k} (and every storey above it) back by {@code n} blocks on a side: the storey below's roof
	 * becomes a terrace in front of it (pueblos, hill-town houses, tiered towers).
	 */
	public Body setback(int k, Direction side, int n) {
		for (int s = k; s < this.heights.length; s++) {
			this.outsets[s][sideIndex(side)] -= n;
		}
		return this;
	}

	public int storeys() {
		return this.heights.length;
	}

	public int height(int k) {
		return this.heights[k];
	}

	/** Standing level of storey k (k may equal storeys(): the level above the top storey). */
	public int floorY(int k) {
		int y = this.baseY;
		for (int i = 0; i < k; i++) {
			y += this.heights[i];
		}
		return y;
	}

	/** The course the roof sits on (the top storey's wall plate). */
	public int plateY() {
		return floorY(storeys()) - 1;
	}

	public int outset(int k, Direction side) {
		return this.outsets[k][sideIndex(side)];
	}

	/** Storey k's wall rectangle {x0, z0, x1, z1}. */
	public int[] rect(int k) {
		return new int[]{this.x0 - outset(k, Dirs.LEFT), this.z0 - outset(k, Dirs.FRONT), this.x1 + outset(k, Dirs.RIGHT), this.z1 + outset(k, Dirs.BACK)};
	}

	/** The top storey's rectangle (what the roof covers). */
	public int[] topRect() {
		return rect(storeys() - 1);
	}

	public Side[] sides(int k) {
		int[] r = rect(k);
		return Side.of(r[0], r[1], r[2], r[3]);
	}

	public Side side(int k, Direction out) {
		int[] r = rect(k);
		return Side.of(r[0], r[1], r[2], r[3], out);
	}

	public boolean isParty(Direction out) {
		return out == Dirs.LEFT && this.partyLeft || out == Dirs.RIGHT && this.partyRight;
	}

	public int width() {
		return this.x1 - this.x0 + 1;
	}

	public int depth() {
		return this.z1 - this.z0 + 1;
	}

	@Override
	public String toString() {
		return "Body[" + this.x0 + "," + this.z0 + ".." + this.x1 + "," + this.z1 + " y" + this.baseY + " " + Arrays.toString(this.heights) + "]";
	}
}
