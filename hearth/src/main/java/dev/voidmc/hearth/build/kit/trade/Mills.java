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
	 * Windmill sails in the plane across {@code shaft} (the windshaft's axis), centred on the hub at (cx, cy, cz),
	 * {@code length} cells per arm. Common sails (cross, saltire) are lattices: a whip of timber from the hub, sail bars
	 * across it every third cell out to a hemlath along the trailing edge {@code width + 1} out, and cloth spread over the
	 * frame between the bars; {@code furled} of the sails (by share, opposite pairs together) have their cloth taken in,
	 * so the bare lattice shows against the sky. The Mediterranean star has eight spars, each with a triangle of cloth
	 * widening from a third of the way out to the spar's tip. Stocks of {@code stock} (logs near the hub, fences beyond),
	 * cloth of {@code cloth}, the lattice of {@code lattice} (fences). Returns the cells drawn.
	 */
	public static List<int[]> sails(Draft d, int cx, int cy, int cz, Direction.Axis shaft, int length, int width, SailPlan plan, Mat stock, Paint cloth,
		Paint lattice, double furled) {
		List<int[]> drawn = new ArrayList<>();
		boolean planeX = shaft == Direction.Axis.Z;
		Direction.Axis across = planeX ? Direction.Axis.X : Direction.Axis.Z;
		int[][] arms = switch (plan) {
			case CROSS -> new int[][]{{0, 1}, {-1, 0}, {0, -1}, {1, 0}};
			case SALTIRE -> new int[][]{{1, 1}, {-1, 1}, {-1, -1}, {1, -1}};
			case STAR -> new int[][]{{0, 1}, {-1, 1}, {-1, 0}, {-1, -1}, {0, -1}, {1, -1}, {1, 0}, {1, 1}};
		};
		Paint fence = stock.has(Form.FENCE) ? stock.fence() : lattice;
		try (var s = d.stage(StageKind.EXTERIOR)) {
			for (int n = 0; n < arms.length; n++) {
				int[] a = arms[n];
				boolean diag = a[0] != 0 && a[1] != 0;
				boolean star = plan == SailPlan.STAR;
				int len = diag && star ? Math.max(2, length * 8 / 10) : diag ? Math.max(2, length * 8 / 10) : length;
				// the trailing side: the arm turned a quarter (counter-clockwise seen from the front)
				int tu = -a[1];
				int tv = a[0];
				// opposite sails go together: the second pair furled when asked
				boolean bare = !star && furled > 0 && (n % 2 == 1) && furled >= 0.25 || furled >= 0.75;
				for (int i = 1; i <= len; i++) {
					int u = a[0] * i;
					int v = a[1] * i;
					// the whip: a beam near the hub, lighter timber out along the sail
					Paint whip;
					if (diag) {
						whip = i <= 1 ? stock.full() : fence;
					} else {
						whip = i <= 2 ? stock.log(a[0] != 0 ? across : Direction.Axis.Y) : fence;
					}
					put(d, cx, cy, cz, planeX, u, v, whip, drawn);
					if (diag && i < len) {
						// a diagonal stock reads as a line when its steps are joined
						put(d, cx, cy, cz, planeX, u + a[0], v, i <= 1 ? stock.full() : fence, drawn);
					}
					if (star) {
						// a triangle of cloth on the trailing side, widening from a third of the way out to the tip
						int s0 = Math.max(2, len / 3);
						if (i >= s0) {
							int kMax = 1 + (int) Math.round((i - s0) / (double) Math.max(1, len - s0) * (width - 1));
							for (int k = 1; k <= kMax; k++) {
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
					// a bare sail shows its bars every other cell and at the tip; a spread one is cloth from whip to hemlath
					boolean bar = bare && ((i - 2) % 2 == 0 || i == len);
					for (int k = 1; k <= width + 1; k++) {
						int pu = u + tu * k;
						int pv = v + tv * k;
						boolean hem = k == width + 1;
						Paint p;
						if (hem || bar) {
							p = fence;
						} else if (bare) {
							continue;
						} else {
							p = cloth;
						}
						put(d, cx, cy, cz, planeX, pu, pv, p, drawn);
						if (diag) {
							put(d, cx, cy, cz, planeX, pu + a[0], pv, p, drawn);
						}
					}
				}
			}
			// the hub: a boss over the windshaft's end
			put(d, cx, cy, cz, planeX, 0, 0, stock.log(planeX ? Direction.Axis.Z : Direction.Axis.X), drawn);
		}
		return drawn;
	}

	private static void put(Draft d, int cx, int cy, int cz, boolean planeX, int u, int v, Paint p, List<int[]> drawn) {
		int x = planeX ? cx + u : cx;
		int z = planeX ? cz : cz + u;
		int y = cy + v;
		// a sail never sweeps lower than a man's height over the yard
		if (!d.allowed(x, y, z) || y < 2) {
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
