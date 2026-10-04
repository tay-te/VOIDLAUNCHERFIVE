package dev.voidmc.hearth.build.kit.trade;

import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Mat;
import dev.voidmc.hearth.build.draft.Paint;
import dev.voidmc.hearth.build.style.Form;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * The machines that make a mill a mill, built from blocks: a water wheel (rims, spokes and paddles round a hub on its
 * axle) and windmill sails (stocks and whips with their cloth or lattice), each in a vertical plane. Drawn so a
 * wheel or a sail reads from across the valley and on the far horizon.
 */
public final class Mills {
	private Mills() {
	}

	/**
	 * A water wheel in the vertical plane across {@code axle}, centred on (cx, cy, cz), {@code radius} to the rim,
	 * {@code thick} rims deep along the axle (toward +axle), with eight spokes and paddles round the rim, and its axle
	 * log through the hub. Cells it covers are reported to {@code cells} (for clearing and checks) if not null.
	 */
	public static void waterWheel(Draft d, int cx, int cy, int cz, Direction.Axis axle, int radius, int thick, Mat rim, Mat spoke, Mat paddle,
		List<int[]> cells) {
		boolean alongX = axle == Direction.Axis.X;
		Direction.Axis across = alongX ? Direction.Axis.Z : Direction.Axis.X;
		try (var s = d.stage(StageKind.FRAME)) {
			for (int t = 0; t < thick; t++) {
				for (int u = -radius - 1; u <= radius + 1; u++) {
					for (int v = -radius - 1; v <= radius + 1; v++) {
						double r = Math.hypot(u, v);
						int x = alongX ? cx + t : cx + u;
						int z = alongX ? cz + u : cz + t;
						int y = cy + v;
						Paint p = null;
						boolean onRim = Math.abs(r - radius) < 0.55;
						boolean spokeCell = r < radius - 0.4 && r > 0.5 && (u == 0 || v == 0 || Math.abs(u) == Math.abs(v));
						boolean paddleCell = r >= radius + 0.45 && r < radius + 1.45 && (u == 0 || v == 0 || Math.abs(Math.abs(u) - Math.abs(v)) <= 0
							|| (radius >= 4 && (Math.abs(u) == 1 || Math.abs(v) == 1)));
						if (onRim) {
							p = rim.has(Form.LOG) ? rim.log(across) : rim.full();
						} else if (spokeCell) {
							p = spoke.has(Form.FENCE) ? spoke.fence() : spoke.log(across);
						} else if (paddleCell) {
							Direction outward = Math.abs(u) >= Math.abs(v) ? (alongX ? (u > 0 ? Direction.SOUTH : Direction.NORTH)
								: (u > 0 ? Direction.EAST : Direction.WEST)) : null;
							if (outward != null && paddle.has(Form.TRAPDOOR)) {
								p = paddle.trapdoor(outward.getOpposite(), Half.BOTTOM, true);
							} else {
								p = paddle.slab(v > 0 ? SlabType.BOTTOM : SlabType.TOP);
							}
						}
						if (p != null) {
							d.set(x, y, z, p);
							if (cells != null) {
								cells.add(new int[]{x, y, z});
							}
						}
					}
				}
			}
		}
	}

	/** How the sails of a windmill are laid out. */
	public enum SailPlan {
		/** Four sails in a cross, each a stock with cloth on its trailing side (Dutch, English mills). */
		CROSS,
		/** Four sails turned forty-five degrees. */
		SALTIRE,
		/** Eight spars, each with a small triangle of cloth (Mediterranean mills). */
		STAR
	}

	/**
	 * Windmill sails in the plane across {@code shaft} (the windshaft's axis), centred on the hub at (cx, cy, cz):
	 * {@code length} cells per arm, the cloth {@code width} wide on each arm's trailing side from the third cell out.
	 * Stocks of {@code stock} (fences, logs near the hub), cloth of {@code cloth} (full blocks), lattice bars of
	 * {@code lattice} where the cloth is furled ({@code furled} share of the cloth). Returns the cells drawn.
	 */
	public static List<int[]> sails(Draft d, int cx, int cy, int cz, Direction.Axis shaft, int length, int width, SailPlan plan, Mat stock, Paint cloth,
		Paint lattice, double furled) {
		List<int[]> drawn = new ArrayList<>();
		boolean planeX = shaft == Direction.Axis.Z;
		int[][] arms = switch (plan) {
			case CROSS -> new int[][]{{0, 1}, {-1, 0}, {0, -1}, {1, 0}};
			case SALTIRE -> new int[][]{{1, 1}, {-1, 1}, {-1, -1}, {1, -1}};
			case STAR -> new int[][]{{0, 1}, {-1, 1}, {-1, 0}, {-1, -1}, {0, -1}, {1, -1}, {1, 0}, {1, 1}};
		};
		try (var s = d.stage(StageKind.EXTERIOR)) {
			for (int[] a : arms) {
				boolean diag = a[0] != 0 && a[1] != 0;
				int len = diag && plan == SailPlan.STAR ? Math.max(2, length * 7 / 10) : diag ? Math.max(2, length * 8 / 10) : length;
				// the trailing side: the arm turned a quarter (counter-clockwise seen from the front)
				int tu = -a[1];
				int tv = a[0];
				for (int i = 1; i <= len; i++) {
					int u = a[0] * i;
					int v = a[1] * i;
					boolean star = plan == SailPlan.STAR;
					Paint st = i <= 1 && !star ? stock.log(Direction.Axis.Y) : stock.has(Form.FENCE) ? stock.fence() : stock.full();
					put(d, cx, cy, cz, planeX, u, v, st, drawn);
					if (diag && i < len) {
						// a diagonal stock reads as a line when its steps are joined
						put(d, cx, cy, cz, planeX, u + a[0], v, stock.has(Form.FENCE) ? stock.fence() : stock.full(), drawn);
					}
					if (star) {
						// a small sail of cloth toward the outer end of each spar
						if (i >= len - 2) {
							for (int k = 1; k <= 2; k++) {
								put(d, cx, cy, cz, planeX, u + tu * k, v + tv * k, cloth, drawn);
								if (diag) {
									put(d, cx, cy, cz, planeX, u + tu * k + a[0], v + tv * k, cloth, drawn);
								}
							}
						}
						continue;
					}
					if (i < 2) {
						continue;
					}
					for (int k = 1; k <= width + 1; k++) {
						int pu = u + tu * k;
						int pv = v + tv * k;
						// the cloth, and the hemlath framing its trailing edge
						boolean hem = k == width + 1;
						boolean furl = !hem && furled > 0 && ((i + k) % 3 == 0 && furled > 0.3 || i > len - Math.round(furled * len));
						Paint p = hem || furl ? lattice : cloth;
						if (hem && i % 2 == 1 && i != len) {
							continue;
						}
						put(d, cx, cy, cz, planeX, pu, pv, p, drawn);
						if (diag) {
							put(d, cx, cy, cz, planeX, pu + a[0], pv, p, drawn);
						}
					}
				}
			}
		}
		return drawn;
	}

	private static void put(Draft d, int cx, int cy, int cz, boolean planeX, int u, int v, Paint p, List<int[]> drawn) {
		int x = planeX ? cx + u : cx;
		int z = planeX ? cz : cz + u;
		int y = cy + v;
		if (!d.allowed(x, y, z)) {
			return;
		}
		BlockState st = p.at(x, y, z);
		if (st == null) {
			return;
		}
		d.set(x, y, z, p);
		drawn.add(new int[]{x, y, z});
	}
}
