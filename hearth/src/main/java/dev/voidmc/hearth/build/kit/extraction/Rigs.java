package dev.voidmc.hearth.build.kit.extraction;

import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Mat;
import dev.voidmc.hearth.build.draft.States;
import dev.voidmc.hearth.build.kit.trade.Hall;
import dev.voidmc.hearth.build.style.Form;
import dev.voidmc.hearth.build.style.Role;
import java.util.List;
import java.util.function.BiPredicate;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import org.jspecify.annotations.Nullable;

/**
 * The lifting gear of a working, each in a culture's manner, standing at the edge of a pit (or over a shaft) and
 * reaching out over it:
 *
 * <ul>
 *   <li>{@link Kind#TREADWHEEL} — the medieval treadwheel crane (Bohemia, the Alps, the Mediterranean, the moors and the
 *   coasts): a tall mast on a footing with raking stays, a jib out over the pit on a stepped brace with a counterweight
 *   on its tail, and beside it the great wheel a man walks in, its axle on two posts.</li>
 *   <li>{@link Kind#DERRICK} — the timber gallows derrick of the north and the steppe: two posts and a cross beam over the
 *   edge, a jib run out from its middle, a windlass with its cranks between the posts.</li>
 *   <li>{@link Kind#SHEERLEGS} — shear legs (the karst, the tropics, the bayou, the desert and the mesa): two poles leaning
 *   together to an apex over the edge, a short beam out from the apex, a back stay to the ground and a windlass, in
 *   bamboo where the culture builds in bamboo.</li>
 * </ul>
 * Every rig returns its {@link Hook}: the cell under the end of its jib, where the rope comes down. The rope is drawn by
 * the archetype ({@link #rope}), part by part as the pit deepens below it.
 */
public final class Rigs {
	private Rigs() {
	}

	public enum Kind {
		TREADWHEEL, DERRICK, SHEERLEGS
	}

	/** Where the rope comes down: the cell under the end of the jib (x, top - 1, z). */
	public record Hook(int x, int z, int top) {
	}

	/** The culture's rig (a seed may give a derrick anywhere: a plain derrick is everybody's tool). */
	public static Kind kind(Draft d) {
		Culture c = d.style().culture() == null ? Culture.TEMPERATE : d.style().culture();
		Kind k = switch (c) {
			case TEMPERATE, ALPINE, MEDITERRANEAN, MARITIME, MOORLAND, WISTERIA -> Kind.TREADWHEEL;
			case NORTHERN, TUNDRA, STEPPE -> Kind.DERRICK;
			default -> Kind.SHEERLEGS;
		};
		if (k != Kind.DERRICK && d.chance(0.2)) {
			k = Kind.DERRICK;
		}
		return k;
	}

	/** The local cell {@code a} along {@code reach} and {@code b} to its right from (x, z). */
	private static int px(int x, Direction reach, int a, int b) {
		return x + Dirs.dx(reach) * a + Dirs.dx(Dirs.right(reach)) * b;
	}

	private static int pz(int z, Direction reach, int a, int b) {
		return z + Dirs.dz(reach) * a + Dirs.dz(Dirs.right(reach)) * b;
	}

	/** Timber for poles: the frame's logs (bamboo where the frame is bamboo). */
	private static Mat pole(Draft d) {
		return d.mat(Role.FRAME);
	}

	/**
	 * How much room a rig needs on the ground: {a from, a to, b from, b to} relative to its foot (a along the reach, b to
	 * the right), for the side the treadwheel goes on ({@code side} = ±1).
	 */
	public static int[] footprint(Kind kind, int side) {
		return switch (kind) {
			case TREADWHEEL -> side > 0 ? new int[]{-2, 0, -1, 7} : new int[]{-2, 0, -7, 1};
			case DERRICK -> new int[]{-1, 0, -2, 2};
			case SHEERLEGS -> new int[]{-3, 0, -2, 2};
		};
	}

	/**
	 * Raises a rig with its foot at (x, z) on standing level y, reaching {@code len} out over the pit toward
	 * {@code reach}. {@code ground} says where the ground at level y is solid to stand a post or a stay on. Returns its
	 * hook, or null if it did not fit under the plot's height.
	 */
	public static @Nullable Hook raise(Draft d, Kind kind, int x, int y, int z, Direction reach, int len, int side, BiPredicate<Integer, Integer> ground,
		BlockState counterweight) {
		return switch (kind) {
			case TREADWHEEL -> treadwheel(d, x, y, z, reach, len, side, ground, counterweight);
			case DERRICK -> derrick(d, x, y, z, reach, len, ground);
			case SHEERLEGS -> sheerlegs(d, x, y, z, reach, len, ground);
		};
	}

	private static @Nullable Hook treadwheel(Draft d, int x, int y, int z, Direction reach, int len, int side, BiPredicate<Integer, Integer> ground,
		BlockState counterweight) {
		int top = Math.min(y + 9, d.maxHeight() - 2);
		if (top - y < 6) {
			return null;
		}
		Mat mast = pole(d);
		Mat beam = d.mat(Role.BEAM);
		Mat stone = d.mat(Role.FOUNDATION);
		Direction back = reach.getOpposite();
		try (var s = d.stage(StageKind.FOUNDATION)) {
			d.set(x, y - 1, z, stone.full());
		}
		try (var s = d.stage(StageKind.FRAME)) {
			for (int yy = y; yy < top; yy++) {
				d.set(x, yy, z, mast.log(Direction.Axis.Y));
			}
			// raking stays at the foot, where there is ground to stand them on
			for (Direction dir : Dirs.HORIZONTAL) {
				int fx = x + Dirs.dx(dir);
				int fz = z + Dirs.dz(dir);
				if (dir != reach && ground.test(fx, fz) && d.isAbsent(fx, y, fz)) {
					d.set(fx, y, fz, beam.stairsOrSlab(dir.getOpposite(), Half.BOTTOM));
				}
			}
			// the jib out over the pit, its tail with a counterweight
			for (int a = -2; a <= len; a++) {
				d.set(px(x, reach, a, 0), top, pz(z, reach, a, 0), beam.log(reach.getAxis()));
			}
			d.set(px(x, reach, -2, 0), top - 1, pz(z, reach, -2, 0), counterweight);
			d.set(x, top + 1, z, Hall.woodFence(d).fence());
			// the stepped brace from the mast up to the jib
			d.set(px(x, reach, 1, 0), top - 2, pz(z, reach, 1, 0), beam.stairsOrSlab(back, Half.TOP));
			d.set(px(x, reach, 2, 0), top - 1, pz(z, reach, 2, 0), beam.stairsOrSlab(back, Half.TOP));
			d.set(px(x, reach, 1, 0), top - 1, pz(z, reach, 1, 0), beam.log(reach.getAxis()));
		}
		// the treadwheel beside the mast, a row behind it, its plane across the reach
		wheel(d, px(x, reach, -1, side * 4), y, pz(z, reach, -1, side * 4), reach, Math.min(3, (top - y) / 2 - 1));
		return new Hook(px(x, reach, len, 0), pz(z, reach, len, 0), top);
	}

	/**
	 * A treadwheel with its rim's foot at (x, y, z), its plane across {@code axle} (the axle runs along it), radius r:
	 * a ring of boards, four spokes, the hub on an axle log carried by two posts.
	 */
	public static void wheel(Draft d, int x, int y, int z, Direction axle, int r) {
		r = Math.max(2, r);
		Mat beam = d.mat(Role.BEAM);
		Mat boards = beam.has(Form.PLANKS) ? beam : d.mat(Role.FLOOR);
		Mat spoke = Hall.woodFence(d);
		Direction across = Dirs.right(axle);
		int hub = y + r;
		List<int[]> ring = Draft.ellipseCells(0, 0, r - 0.15, r - 0.15, false);
		try (var s = d.stage(StageKind.FRAME)) {
			// the posts and the axle
			for (int k : new int[]{-1, 1}) {
				int ax = x + Dirs.dx(axle) * k;
				int az = z + Dirs.dz(axle) * k;
				for (int yy = y; yy < hub; yy++) {
					d.set(ax, yy, az, spoke.fence());
				}
				d.set(ax, hub, az, beam.log(axle.getAxis()));
			}
			d.set(x, hub, z, beam.log(axle.getAxis()));
			for (int[] c : ring) {
				int u = c[0];
				int v = c[1];
				d.set(x + Dirs.dx(across) * u, hub + v, z + Dirs.dz(across) * u, boards.full());
			}
			for (int k = 1; k < r; k++) {
				d.set(x + Dirs.dx(across) * k, hub, z + Dirs.dz(across) * k, spoke.fence());
				d.set(x - Dirs.dx(across) * k, hub, z - Dirs.dz(across) * k, spoke.fence());
				d.set(x, hub + k, z, spoke.fence());
				d.set(x, hub - k, z, spoke.fence());
			}
		}
	}

	private static @Nullable Hook derrick(Draft d, int x, int y, int z, Direction reach, int len, BiPredicate<Integer, Integer> ground) {
		int top = Math.min(y + 7, d.maxHeight() - 2);
		if (top - y < 5) {
			return null;
		}
		Mat post = pole(d);
		Mat beam = d.mat(Role.BEAM);
		Mat stone = d.mat(Role.FOUNDATION);
		Direction across = Dirs.right(reach);
		try (var s = d.stage(StageKind.FOUNDATION)) {
			for (int b : new int[]{-2, 2}) {
				d.set(px(x, reach, 0, b), y - 1, pz(z, reach, 0, b), stone.full());
			}
		}
		try (var s = d.stage(StageKind.FRAME)) {
			for (int b : new int[]{-2, 2}) {
				int fx = px(x, reach, 0, b);
				int fz = pz(z, reach, 0, b);
				for (int yy = y; yy < top; yy++) {
					d.set(fx, yy, fz, post.log(Direction.Axis.Y));
				}
				// a raking stay behind each post
				int bx = px(x, reach, -1, b);
				int bz = pz(z, reach, -1, b);
				if (ground.test(bx, bz) && d.isAbsent(bx, y, bz)) {
					d.set(bx, y, bz, beam.stairsOrSlab(reach, Half.BOTTOM));
				}
			}
			for (int b = -2; b <= 2; b++) {
				d.set(px(x, reach, 0, b), top, pz(z, reach, 0, b), beam.log(across.getAxis()));
			}
			// the jib from the middle of the cross beam, braced from below
			for (int a = 1; a <= len; a++) {
				d.set(px(x, reach, a, 0), top, pz(z, reach, a, 0), beam.log(reach.getAxis()));
			}
			d.set(px(x, reach, 1, 0), top - 1, pz(z, reach, 1, 0), beam.stairsOrSlab(reach.getOpposite(), Half.TOP));
			// the windlass between the posts: a drum on the posts' cleats, cranks at both ends
			int wy = y + 1;
			for (int b = -1; b <= 1; b++) {
				d.set(px(x, reach, 0, b), wy, pz(z, reach, 0, b), beam.log(across.getAxis()));
			}
			d.set(px(x, reach, 0, 0), y, pz(z, reach, 0, 0), Hall.woodFence(d).fence());
		}
		return new Hook(px(x, reach, len, 0), pz(z, reach, len, 0), top);
	}

	private static @Nullable Hook sheerlegs(Draft d, int x, int y, int z, Direction reach, int len, BiPredicate<Integer, Integer> ground) {
		int top = Math.min(y + 5, d.maxHeight() - 2);
		if (top - y < 4) {
			return null;
		}
		Mat leg = pole(d);
		Mat beam = d.mat(Role.BEAM);
		Direction back = reach.getOpposite();
		try (var s = d.stage(StageKind.FRAME)) {
			// two legs leaning together in the plane across the reach
			int h = top - y;
			for (int k = 0; k < h; k++) {
				int b = 2 - (k * 2) / h;
				for (int sgn : new int[]{-1, 1}) {
					d.set(px(x, reach, 0, sgn * b), y + k, pz(z, reach, 0, sgn * b), leg.log(Direction.Axis.Y));
				}
			}
			// the apex and the beam out over the pit
			for (int a = -1; a <= len; a++) {
				d.set(px(x, reach, a, 0), top, pz(z, reach, a, 0), beam.log(reach.getAxis()));
			}
			// the back stay down to the ground in steps
			for (int k = 1; k <= 3; k++) {
				int sx = px(x, reach, -1 - k, 0);
				int sz = pz(z, reach, -1 - k, 0);
				int sy = top - k;
				if (sy >= y && ground.test(sx, sz)) {
					d.set(sx, sy, sz, beam.stairsOrSlab(back, Half.BOTTOM));
					if (k == 3) {
						for (int yy = y; yy < sy; yy++) {
							d.set(sx, yy, sz, beam.log(Direction.Axis.Y));
						}
					}
				}
			}
			// the windlass behind the legs
			int wx = px(x, reach, -1, 0);
			int wz = pz(z, reach, -1, 0);
			if (ground.test(wx, wz)) {
				d.set(wx, y, wz, beam.log(Dirs.right(reach).getAxis()));
			}
		}
		return new Hook(px(x, reach, len, 0), pz(z, reach, len, 0), top);
	}

	/**
	 * A rope (an iron chain) from the hook down to {@code bottom} (exclusive), and what hangs on it at {@code bottom} if
	 * {@code load} is not null. {@code partAt} gives the part each cell belongs to (a rope down a pit goes in with the
	 * bench it hangs through); negative: the part open now.
	 */
	public static void rope(Draft d, Hook hook, int bottom, @Nullable BlockState load, java.util.function.IntUnaryOperator partAt) {
		for (int y = hook.top() - 1; y > bottom; y--) {
			int p = partAt.applyAsInt(y);
			BlockState chain = States.axis(Blocks.IRON_CHAIN, Direction.Axis.Y);
			int yy = y;
			if (p >= 0) {
				try (var ps = d.part(p); var s = d.stage(StageKind.EXTERIOR)) {
					d.set(hook.x(), yy, hook.z(), chain);
				}
			} else {
				d.inStage(StageKind.EXTERIOR, () -> d.set(hook.x(), yy, hook.z(), chain));
			}
		}
		if (load != null) {
			int p = partAt.applyAsInt(bottom);
			try (var ps = d.part(Math.max(0, p)); var s = d.stage(StageKind.EXTERIOR)) {
				d.set(hook.x(), bottom, hook.z(), load);
			}
		}
	}
}
