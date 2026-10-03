package dev.voidmc.hearth.plan.model;

import dev.voidmc.hearth.plan.grid.Cell;

/**
 * Land the layout holds back from ordinary plots: the main square before its plaza is laid, the site of a landmark,
 * a market's ground. Inclusive bounds; {@code code} is the {@link Cell} the occupancy grid shows for it.
 */
public record Reservation(String tag, int x0, int z0, int x1, int z1, byte code) {
	public boolean contains(int x, int z) {
		return x >= this.x0 && x <= this.x1 && z >= this.z0 && z <= this.z1;
	}

	int[] pack() {
		return new int[]{this.x0, this.z0, this.x1, this.z1, this.code};
	}

	static Reservation unpack(String tag, int[] p) {
		return new Reservation(tag, p[0], p[1], p[2], p[3], (byte) (p.length > 4 ? p[4] : Cell.RESERVED));
	}
}
