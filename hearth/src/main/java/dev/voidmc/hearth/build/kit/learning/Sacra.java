package dev.voidmc.hearth.build.kit.learning;

import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Mat;
import dev.voidmc.hearth.build.draft.Noise;
import dev.voidmc.hearth.build.draft.Paint;
import dev.voidmc.hearth.build.draft.States;
import dev.voidmc.hearth.build.kit.Garden;
import dev.voidmc.hearth.build.kit.landmark.Stone;
import dev.voidmc.hearth.build.style.Form;
import dev.voidmc.hearth.build.style.Role;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * Small pieces of holy places shared by the clergy and learning family: crosses (wrought iron, timber and stone, the
 * three-barred cross of the north), a cypress, offerings of candles and flowers, a kneeling step. Each checks the plot's
 * bounds and height itself and stands on what is below it.
 */
public final class Sacra {
	private Sacra() {
	}

	/** Whether (x, y, z) may be drawn: inside the plot and under its height. */
	public static boolean ok(Draft d, int x, int y, int z) {
		return x >= 0 && x < d.frontage() && z >= 0 && z < d.depth() && y < d.maxHeight() && y >= -8;
	}

	/** A dark timber fence post (the beam's wood, else the frame's, else the fence's): wooden crosses, posts, stakes. */
	public static Paint fence(Draft d) {
		for (Role r : new Role[]{Role.BEAM, Role.FRAME, Role.FENCE, Role.RAILING}) {
			Mat m = d.mat(r);
			if (m.has(Form.FENCE)) {
				return m.fence();
			}
		}
		return Paint.of(Blocks.DARK_OAK_FENCE.defaultBlockState());
	}

	/** Wrought iron bars: what a smith makes the crosses of gables and spires from. */
	public static Paint iron() {
		return Paint.of(Blocks.IRON_BARS.defaultBlockState());
	}

	/**
	 * A cross standing on (x, y - 1, z): a shaft {@code height} tall (3 or more) of {@code post}, the arms one under the
	 * top across {@code arms} (bars, fences and walls join the shaft on their own). Arms that would leave the plot are
	 * left off (a shaft alone is still a mark). Returns the top row.
	 */
	public static int cross(Draft d, int x, int y, int z, Direction.Axis arms, Paint post, Paint arm, int height) {
		int top = Math.min(y + height - 1, d.maxHeight() - 1);
		if (top < y + 1 || !ok(d, x, y, z)) {
			return y - 1;
		}
		try (var s = d.stage(StageKind.ROOF)) {
			for (int yy = y; yy <= top; yy++) {
				d.set(x, yy, z, post);
			}
			int ay = top - 1;
			if (ay > y) {
				for (int k : new int[]{-1, 1}) {
					int ax = arms == Direction.Axis.X ? x + k : x;
					int az = arms == Direction.Axis.Z ? z + k : z;
					if (ok(d, ax, ay, az) && d.isEmpty(ax, ay, az)) {
						d.set(ax, ay, az, arm);
					}
				}
			}
		}
		return top;
	}

	/** A cross of dark timber posts on a gable, a ridge or a crest, standing on (x, y - 1, z), its arms across {@code arms}. */
	public static int woodCross(Draft d, int x, int y, int z, Direction.Axis arms) {
		return cross(d, x, y, z, arms, fence(d), fence(d), 3);
	}

	/** An iron cross on a gable or a ridge, standing on (x, y - 1, z), its arms across {@code arms}. */
	public static int ironCross(Draft d, int x, int y, int z, Direction.Axis arms) {
		return cross(d, x, y, z, arms, iron(), iron(), 3);
	}

	/**
	 * The three-barred cross of the north on its post: a short bar near the top, the long bar under it and the foot bar
	 * slanting (left low, right high). The post from y to {@code top}, arms across {@code arms}.
	 */
	public static void northernCross(Draft d, int x, int y, int z, int top, Direction.Axis arms, Paint post, Paint arm) {
		try (var s = d.stage(StageKind.FRAME)) {
			for (int yy = y; yy <= top; yy++) {
				d.set(x, yy, z, post);
			}
			// the long bar two under the top (the short one is the post's head between them at this scale), the foot bar
			// slanting: low on the left, high on the right
			setArm(d, x, top - 2, z, arms, -1, arm);
			setArm(d, x, top - 2, z, arms, 1, arm);
			if (top - 5 > y) {
				setArm(d, x, top - 5, z, arms, -1, arm);
				setArm(d, x, top - 4, z, arms, 1, arm);
			}
		}
	}

	private static void setArm(Draft d, int x, int y, int z, Direction.Axis arms, int k, Paint arm) {
		int ax = arms == Direction.Axis.X ? x + k : x;
		int az = arms == Direction.Axis.Z ? z + k : z;
		if (ok(d, ax, y, az) && d.isEmpty(ax, y, az)) {
			d.set(ax, y, az, arm);
		}
	}

	/**
	 * A Celtic high cross on (x, y - 1, z): a shaft of dressed stone, arms {@code reach} out (1 or 2) across {@code arms},
	 * and where the arms reach two, the ring through the four quarters (wall posts joining shaft and arms). Returns the
	 * top row.
	 */
	public static int highCross(Draft d, int x, int y, int z, Direction.Axis arms, int shaft, int reach, Stone st) {
		int ac = y + shaft;          // the crossing
		int top = Math.min(ac + (reach >= 2 ? 2 : 1), d.maxHeight() - 1);
		if (top <= ac) {
			return y - 1;
		}
		try (var s = d.stage(StageKind.WALLS)) {
			for (int yy = y; yy <= top; yy++) {
				d.set(x, yy, z, st.full());
			}
			for (int k = 1; k <= reach; k++) {
				for (int sgn : new int[]{-1, 1}) {
					int ax = arms == Direction.Axis.X ? x + sgn * k : x;
					int az = arms == Direction.Axis.Z ? z + sgn * k : z;
					if (ok(d, ax, ac, az)) {
						d.set(ax, ac, az, k == reach && reach >= 2 ? st.stairsOrSlab(arms == Direction.Axis.X ? (sgn < 0 ? Dirs.RIGHT : Dirs.LEFT)
							: (sgn < 0 ? Dirs.BACK : Dirs.FRONT), Half.TOP) : st.full());
					}
				}
			}
			if (reach >= 2) {
				for (int sgn : new int[]{-1, 1}) {
					for (int dy : new int[]{-1, 1}) {
						int ax = arms == Direction.Axis.X ? x + sgn : x;
						int az = arms == Direction.Axis.Z ? z + sgn : z;
						if (ok(d, ax, ac + dy, az)) {
							d.set(ax, ac + dy, az, st.wallOrFull());
						}
					}
				}
			}
		}
		return top;
	}

	/** Lit candles (1–4) standing at (x, y, z) on what is below, in stage EXTERIOR or INTERIOR as given. */
	public static void candles(Draft d, int x, int y, int z, int n, StageKind stage) {
		if (!ok(d, x, y, z)) {
			return;
		}
		BlockState c = States.of(Blocks.CANDLE).setValue(BlockStateProperties.CANDLES, Math.clamp(n, 1, 4)).setValue(BlockStateProperties.LIT, true);
		Block coloured = d.mat(Role.CLOTH).main(Form.CANDLE);
		if (coloured != null && Noise.unit(d.seed(), x, y, z) < 0.35) {
			c = coloured.defaultBlockState().trySetValue(BlockStateProperties.CANDLES, Math.clamp(n, 1, 4)).trySetValue(BlockStateProperties.LIT, true);
		}
		BlockState cc = c;
		d.inStage(stage, () -> d.set(x, y, z, cc));
	}

	/** A lantern standing on what is below (x, y, z) (not hanging). */
	public static void standingLantern(Draft d, int x, int y, int z, StageKind stage) {
		if (!ok(d, x, y, z)) {
			return;
		}
		Block l = d.mat(Role.LIGHT).mainOr(Form.FULL, Blocks.LANTERN);
		if (!l.defaultBlockState().hasProperty(BlockStateProperties.HANGING)) {
			l = Blocks.LANTERN;
		}
		Block ll = l;
		d.inStage(stage, () -> d.set(x, y, z, States.lantern(ll, false)));
	}

	/** A lantern hung from the block above (x, y, z). */
	public static void hangingLantern(Draft d, int x, int y, int z, StageKind stage) {
		if (!ok(d, x, y, z)) {
			return;
		}
		Block l = d.mat(Role.LIGHT).mainOr(Form.FULL, Blocks.LANTERN);
		if (!l.defaultBlockState().hasProperty(BlockStateProperties.HANGING)) {
			l = Blocks.LANTERN;
		}
		Block ll = l;
		d.inStage(stage, () -> d.set(x, y, z, States.lantern(ll, true)));
	}

	/**
	 * A cypress of the south: a trunk and a slim column of dark leaves {@code height} tall, a little fuller low down.
	 * Lays its soil. Only where the column is free.
	 */
	public static void cypress(Draft d, int x, int y, int z, int height) {
		if (!ok(d, x, y, z) || height < 3) {
			return;
		}
		int top = Math.min(y + height - 1, d.maxHeight() - 1);
		// only up to whatever is already drawn over the spot (an eave, a sill)
		for (int yy = y; yy <= top; yy++) {
			if (!d.isAbsent(x, yy, z)) {
				top = yy - 1;
				break;
			}
		}
		if (top - y < 2) {
			return;
		}
		BlockState leaves = States.leaves(Blocks.SPRUCE_LEAVES);
		try (var s = d.stage(StageKind.EXTERIOR)) {
			if (d.isEmpty(x, y - 1, z) || d.get(x, y - 1, z) == null) {
				d.set(x, y - 1, z, Blocks.GRASS_BLOCK.defaultBlockState());
			}
			d.set(x, y, z, States.axis(Blocks.SPRUCE_LOG, Direction.Axis.Y));
			d.set(x, y + 1, z, States.axis(Blocks.SPRUCE_LOG, Direction.Axis.Y));
			for (int yy = y + 2; yy <= top; yy++) {
				d.set(x, yy, z, leaves);
			}
			// a little fuller in the lower crown
			int full = y + 2 + (top - y - 2) / 3;
			for (Direction dir : Dirs.HORIZONTAL) {
				for (int yy = y + 2; yy <= full; yy++) {
					int nx = x + Dirs.dx(dir);
					int nz = z + Dirs.dz(dir);
					if (ok(d, nx, yy, nz) && d.isAbsent(nx, yy, nz) && Noise.unit(d.seed() ^ 0xC9, nx, yy, nz) < 0.7) {
						d.set(nx, yy, nz, leaves);
					}
				}
			}
		}
	}

	/** A pot of the culture's flowers standing at (x, y, z) on what is below. */
	public static void pot(Draft d, int x, int y, int z) {
		if (ok(d, x, y, z) && d.isEmpty(x, y, z)) {
			Garden.pot(d, x, y, z);
		}
	}

	/** Flowers on ground at (x, y, z), only where the cell is free and in the plot. */
	public static void flower(Draft d, int x, int y, int z) {
		if (ok(d, x, y, z) && d.isEmpty(x, y, z)) {
			Garden.flower(d, x, y, z);
		}
	}

	/** The colour of the style's first cloth as a dye name, for banners. */
	public static String cloth(Draft d, int k) {
		var entries = d.mat(Role.CLOTH).palette().entries();
		Block wool = entries.get(Math.floorMod(k, entries.size())).material().block(Form.FULL);
		if (wool == null) {
			return "white";
		}
		String p = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(wool).getPath();
		return p.endsWith("_wool") ? p.substring(0, p.length() - "_wool".length()) : "white";
	}

	/** The roof's slab or the trim's, or a full block, for small caps. */
	public static Paint capSlab(Draft d) {
		Mat roof = d.mat(Role.ROOF);
		if (roof.has(Form.SLAB)) {
			return roof.slab(SlabType.BOTTOM);
		}
		Mat trim = d.mat(Role.ROOF_TRIM);
		return trim.has(Form.SLAB) ? trim.slab(SlabType.BOTTOM) : roof.full();
	}

	/** Roof stairs facing {@code facing} (climbing toward it), or a slab where the roof has no stairs. */
	public static Paint roofStair(Draft d, Direction facing, Half half) {
		Mat roof = d.mat(Role.ROOF);
		if (roof.has(Form.STAIRS)) {
			return roof.stairs(facing, half);
		}
		Mat trim = d.mat(Role.ROOF_TRIM);
		return trim.has(Form.STAIRS) ? trim.stairs(facing, half) : roof.stairsOrSlab(facing, half);
	}
}
