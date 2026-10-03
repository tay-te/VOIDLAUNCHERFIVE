package dev.voidmc.hearth.build.kit.trade;

import dev.voidmc.hearth.api.PoiType;
import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Mat;
import dev.voidmc.hearth.build.draft.Noise;
import dev.voidmc.hearth.build.kit.Openings;
import dev.voidmc.hearth.build.kit.Side;
import dev.voidmc.hearth.build.style.Form;
import dev.voidmc.hearth.build.style.Role;
import java.util.function.IntFunction;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * A shop's street front and counter: a wide glazed window under a lintel with its wares set out on a sill board
 * inside, an awning of the house's cloth over it, and a counter across the shop with the trader behind it and the
 * customer before it ({@code COUNTER} where the trader stands, facing the customer).
 */
public final class Shop {
	private Shop() {
	}

	/**
	 * A shop window in wall {@code wall} from column {@code col}, {@code width} wide and two high from y + 1, its sill a
	 * board inside at y with {@code wares} on it (cakes, wool, pots; null for none) and a cloth awning over it outside.
	 */
	public static void window(Draft d, Side wall, int col, int width, int y, IntFunction<BlockState> wares, boolean awning) {
		Openings.window(d, wall, col, y + 1, width, 2, Openings.WindowStyle.PLAIN, new Openings.WindowOpts(false, false, false, true));
		Direction in = wall.out().getOpposite();
		Mat wood = d.mat(Role.FURNITURE);
		try (var s = d.stage(StageKind.INTERIOR)) {
			for (int c = 0; c < width; c++) {
				int i = wall.index(col) + c;
				int x = wall.x(i) + Dirs.dx(in);
				int z = wall.z(i) + Dirs.dz(in);
				if (!d.isEmpty(x, y, z)) {
					continue;
				}
				d.set(x, y, z, wood.slab(SlabType.TOP));
				BlockState ware = wares == null ? null : wares.apply(c);
				if (ware != null && d.isEmpty(x, y + 1, z)) {
					d.set(x, y + 1, z, ware);
				}
			}
		}
		if (awning) {
			awning(d, wall, col - 1, col + width, y + 3);
		}
	}

	/**
	 * Where a shop window goes beside a door in the wall's inner columns {@code x0..x1}: {start, width} on the roomier
	 * side of the door, up to {@code maxW} wide and clear of the door by a column; null if there is no room for one two
	 * wide.
	 */
	public static int[] span(int x0, int x1, int door, int maxW) {
		int left = door - 1 - x0;
		int right = x1 - (door + 1);
		boolean useRight = right >= left;
		int room = useRight ? right : left;
		int w = Math.min(maxW, room);
		if (w < 2) {
			return null;
		}
		int start = useRight ? door + 2 : door - 1 - w;
		if (useRight && start + w - 1 > x1) {
			start = x1 - w + 1;
		}
		return new int[]{start, w};
	}

	/** A cloth awning of stairs (striped with white now and then) over columns c0..c1 of a wall at height y. */
	public static void awning(Draft d, Side wall, int c0, int c1, int y) {
		Mat cloth = d.mat(Role.CLOTH);
		boolean stripes = Noise.unit(d.seed(), c0, y, c1) < 0.5;
		var white = Blocks.WOOL.pick(net.minecraft.world.item.DyeColor.WHITE);
		var whiteStairs = Props.block("minecraft:white_wool_stairs", white);
		try (var s = d.stage(StageKind.EXTERIOR)) {
			for (int c = c0; c <= c1; c++) {
				if (!wall.contains(c)) {
					continue;
				}
				int i = wall.index(c);
				int x = wall.outX(i, 1);
				int z = wall.outZ(i, 1);
				if (!d.allowed(x, y, z) || !d.isAbsent(x, y, z)) {
					continue;
				}
				if (stripes && (c - c0) % 2 == 1) {
					d.set(x, y, z, dev.voidmc.hearth.build.draft.States.stairs(whiteStairs, wall.out().getOpposite(), Half.BOTTOM));
				} else {
					d.set(x, y, z, cloth.stairsOrSlab(wall.out().getOpposite(), Half.BOTTOM));
				}
			}
		}
	}

	/**
	 * A counter along {@code run} from (x, y, z) for {@code length} cells (slab tops on posts, a barrel at one end),
	 * the trader standing on the {@code trader} side; registers COUNTER at the trader's cell behind its middle, facing
	 * the customer. Wares (or null) are set on the slab tops.
	 */
	public static void counter(Draft d, int x, int y, int z, Direction run, int length, Direction trader, IntFunction<BlockState> wares) {
		Mat wood = d.mat(Role.FURNITURE);
		try (var s = d.stage(StageKind.INTERIOR)) {
			for (int i = 0; i < length; i++) {
				int px = x + Dirs.dx(run) * i;
				int pz = z + Dirs.dz(run) * i;
				if (i == length - 1 && length >= 3) {
					d.set(px, y, pz, dev.voidmc.hearth.build.draft.States.facing(Blocks.BARREL, trader));
				} else {
					d.set(px, y, pz, wood.has(Form.STAIRS) ? wood.stairs(trader, Half.TOP) : wood.slab(SlabType.TOP));
					BlockState ware = wares == null ? null : wares.apply(i);
					if (ware != null) {
						d.set(px, y + 1, pz, ware);
					}
				}
			}
		}
		int mi = (length - 1) / 2;
		int mx = x + Dirs.dx(run) * mi + Dirs.dx(trader);
		int mz = z + Dirs.dz(run) * mi + Dirs.dz(trader);
		d.poi(PoiType.COUNTER, mx, y, mz, trader.getOpposite());
	}
}
