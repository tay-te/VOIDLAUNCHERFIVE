package dev.voidmc.hearth.build.kit.industry;

import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Paint;
import dev.voidmc.hearth.build.draft.States;
import dev.voidmc.hearth.build.kit.Roofs;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import org.jspecify.annotations.Nullable;

/**
 * Solid bodies shaped by a height field, the way a roof is ({@link Roofs}), but filled: a charcoal clamp's dome, a
 * heap of salt or ore, the cone of a clay furnace, the bank of a charging ramp. Each column is a stack of the core
 * material with its skin where it shows (the top, and any face its neighbour does not cover), the top a stair facing
 * up the slope where the surface climbs a whole block per cell, a slab where it stands half a block over a course.
 */
public final class Shape {
	private Shape() {
	}

	/** Chooses a block for a cell (patches of turf on a clamp's slopes, say). */
	@FunctionalInterface
	public interface Picker {
		@Nullable Block at(int x, int y, int z);
	}

	/** What a body shows: its full blocks (mixed by position), and the stairs and slab of its surface (or none). */
	public record Skin(Paint full, @Nullable Picker stairs, @Nullable Picker slab) {
		public Skin(Paint full, @Nullable Block stairs, @Nullable Block slab) {
			this(full, stairs == null ? null : (x, y, z) -> stairs, slab == null ? null : (x, y, z) -> slab);
		}

		public static Skin of(Block full, @Nullable Block stairs, @Nullable Block slab) {
			return new Skin(Paint.of(full.defaultBlockState()), stairs, slab);
		}

		@Nullable BlockState stair(int x, int y, int z, Direction up) {
			Block b = this.stairs == null ? null : this.stairs.at(x, y, z);
			return b == null ? null : States.stairs(b, up, Half.BOTTOM);
		}

		@Nullable BlockState slabOf(int x, int y, int z, SlabType t) {
			Block b = this.slab == null ? null : this.slab.at(x, y, z);
			return b == null ? null : States.slab(b, t);
		}
	}

	/**
	 * Rasterises field {@code f} (heights in blocks above {@code y0}, NaN outside) over a rectangle as a solid body
	 * standing on y0: the skin where it shows, {@code core} (or the skin) inside. Only cells nothing else holds are
	 * drawn. Returns the highest y drawn, or MIN_VALUE.
	 */
	public static int body(Draft d, Roofs.Field f, int y0, int x0, int z0, int x1, int z1, Skin skin, @Nullable Paint core, StageKind stage) {
		int w = x1 - x0 + 1;
		int[] t = new int[w * (z1 - z0 + 1)];
		for (int z = z0; z <= z1; z++) {
			for (int x = x0; x <= x1; x++) {
				double h = f.h(x, z);
				t[(z - z0) * w + (x - x0)] = Double.isNaN(h) || !Works.in(d, x, z) ? Integer.MIN_VALUE : Math.max(1, (int) Math.round(h * 2));
			}
		}
		int top = Integer.MIN_VALUE;
		try (var s = d.stage(stage)) {
			for (int z = z0; z <= z1; z++) {
				for (int x = x0; x <= x1; x++) {
					int tv = t[(z - z0) * w + (x - x0)];
					if (tv == Integer.MIN_VALUE) {
						continue;
					}
					int[] nv = new int[4];
					int max = Integer.MIN_VALUE;
					int low = Integer.MAX_VALUE;
					for (int k = 0; k < 4; k++) {
						Direction dir = Dirs.HORIZONTAL[k];
						int nx = x + Dirs.dx(dir);
						int nz = z + Dirs.dz(dir);
						int n = nx < x0 || nx > x1 || nz < z0 || nz > z1 ? Integer.MIN_VALUE : t[(nz - z0) * w + (nx - x0)];
						nv[k] = n == Integer.MIN_VALUE ? 0 : n;
						max = Math.max(max, nv[k]);
						low = Math.min(low, nv[k]);
					}
					Direction up = upslope(nv, tv, max);
					int y;
					BlockState cap;
					BlockState st = up != null && (tv & 1) == 0 && tv - low >= 2 ? skin.stair(x, y0 + tv / 2 - 1, z, up) : null;
					BlockState sl = (tv & 1) == 1 ? skin.slabOf(x, y0 + (tv + 1) / 2 - 1, z, SlabType.BOTTOM) : null;
					if (st != null) {
						y = y0 + tv / 2 - 1;
						cap = st;
					} else if (sl != null) {
						y = y0 + (tv + 1) / 2 - 1;
						cap = sl;
					} else {
						y = y0 + (tv + 1) / 2 - 1;
						cap = skin.full().at(x, y, z);
					}
					for (int yy = y0; yy < y; yy++) {
						if (!d.isEmpty(x, yy, z)) {
							continue;
						}
						boolean shows = false;
						for (int k = 0; k < 4; k++) {
							if (y0 + (nv[k] + 1) / 2 - 1 < yy) {
								shows = true;
							}
						}
						BlockState b = shows || core == null ? skin.full().at(x, yy, z) : core.at(x, yy, z);
						if (b != null) {
							d.set(x, yy, z, b);
						}
					}
					if (d.isEmpty(x, y, z) && cap != null) {
						d.set(x, y, z, cap);
						top = Math.max(top, y);
					}
				}
			}
		}
		return top;
	}

	/** A dome of radius r (cells whose centres lie within it) rising {@code height} blocks at its middle. */
	public static Roofs.Field dome(double cx, double cz, double r, double height) {
		return (x, z) -> {
			double t = Math.hypot(x - cx, z - cz) / r;
			if (t > 1) {
				return Double.NaN;
			}
			return Math.max(0.5, height * Math.sqrt(1 - t * t));
		};
	}

	/** A low mound, a paraboloid of radius r rising {@code height} at its middle: gentle at the foot, rounded on top. */
	public static Roofs.Field mound(double cx, double cz, double r, double height) {
		return (x, z) -> {
			double t = Math.hypot(x - cx, z - cz) / r;
			if (t > 1) {
				return Double.NaN;
			}
			return Math.max(0.5, height * (1 - t * t) + 0.25);
		};
	}

	/** A cone of base radius r rising {@code height} blocks at its point, steep or squat as the two make it. */
	public static Roofs.Field cone(double cx, double cz, double r, double height) {
		return (x, z) -> {
			double t = Math.hypot(x - cx, z - cz) / r;
			if (t > 1) {
				return Double.NaN;
			}
			return Math.max(0.5, height * (1 - t) + 0.5);
		};
	}

	/** As {@link Roofs}: toward the highest neighbour; a column lower on one or two adjacent sides faces away from them. */
	static @Nullable Direction upslope(int[] nv, int t, int max) {
		if (max > t) {
			int best = -1;
			for (int k = 0; k < 4; k++) {
				if (nv[k] == max && (best < 0 || nv[(k + 2) % 4] < nv[(best + 2) % 4])) {
					best = k;
				}
			}
			return Dirs.HORIZONTAL[best];
		}
		boolean[] lower = new boolean[4];
		int count = 0;
		for (int k = 0; k < 4; k++) {
			lower[k] = nv[k] < t;
			if (lower[k]) {
				count++;
			}
		}
		if (count == 0 || count >= 3 || count == 2 && (lower[0] && lower[2] || lower[1] && lower[3])) {
			return null;
		}
		for (int k : new int[]{0, 2, 1, 3}) {
			if (lower[k]) {
				return Dirs.HORIZONTAL[(k + 2) % 4];
			}
		}
		return null;
	}
}
