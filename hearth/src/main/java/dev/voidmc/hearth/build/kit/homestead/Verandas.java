package dev.voidmc.hearth.build.kit.homestead;

import dev.voidmc.hearth.api.PoiType;
import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Mat;
import dev.voidmc.hearth.build.draft.Noise;
import dev.voidmc.hearth.build.draft.States;
import dev.voidmc.hearth.build.kit.Furniture;
import dev.voidmc.hearth.build.style.Form;
import dev.voidmc.hearth.build.style.Role;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.Half;

/**
 * Galleries, verandas and porches of raised houses: decks on piles, slender posts, railings, the life on them (rocking
 * chairs, a hammock slung between two posts, pots, a lantern under the beam) and the lattice that skirts the piles of a
 * house standing on dry ground. Stilt houses, round houses on posts and raised farmsteads draw with it.
 */
public final class Verandas {
	private Verandas() {
	}

	/** Whether (x, z) is on the plot. */
	public static boolean in(Draft d, int x, int z) {
		return x >= 0 && z >= 0 && x < d.frontage() && z < d.depth();
	}

	/** A deck block of the floor material under standing level y at (x, z), unless something is drawn there already. */
	public static void deck(Draft d, int x, int z, int y) {
		if (!in(d, x, z)) {
			return;
		}
		try (var s = d.stage(StageKind.FOUNDATION)) {
			if (d.isEmpty(x, y - 1, z)) {
				d.set(x, y - 1, z, d.mat(Role.FLOOR).full());
			}
		}
	}

	/** A pile under (x, z) from a little into the bed (or the ground) up to {@code topY} (inclusive). */
	public static void pile(Draft d, int x, int z, int topY) {
		if (!in(d, x, z)) {
			return;
		}
		Mat stilt = d.mat(Role.STILT);
		int g = d.site().ground(x, z);
		try (var s = d.stage(StageKind.FOUNDATION)) {
			for (int y = g - 2; y <= topY; y++) {
				if (d.isEmpty(x, y, z)) {
					d.set(x, y, z, stilt.log(Direction.Axis.Y));
				}
			}
		}
	}

	/** A post of the frame from standing level y up to {@code top} (inclusive). */
	public static void post(Draft d, int x, int z, int y, int top) {
		if (!in(d, x, z)) {
			return;
		}
		Mat frame = d.mat(Role.FRAME);
		try (var s = d.stage(StageKind.FRAME)) {
			for (int yy = y; yy <= top; yy++) {
				d.set(x, yy, z, frame.log(Direction.Axis.Y));
			}
		}
	}

	/** A railing fence at standing level y where the cell is free. */
	public static void rail(Draft d, int x, int z, int y) {
		if (in(d, x, z) && d.isEmpty(x, y, z)) {
			d.inStage(StageKind.EXTERIOR, () -> d.set(x, y, z, d.mat(Role.RAILING).fence()));
		}
	}

	/** A rocking chair against the wall at (x, y, z), its sitter looking {@code out}; a seat and a view. */
	public static boolean rocker(Draft d, int x, int y, int z, Direction out) {
		if (!in(d, x, z) || !d.isEmpty(x, y, z) || !d.isEmpty(x, y + 1, z)) {
			return false;
		}
		Furniture.chair(d, x, y, z, out.getOpposite());
		d.poi(PoiType.VIEWPOINT, x, y, z, out);
		return true;
	}

	/**
	 * A hammock slung along {@code run} for {@code len} cells from (x, z), its bed a block over the deck at standing level
	 * y: a woven bed (a closed trapdoor high in its cell) under a striped cloth (carpets of the house's two colours).
	 * The caller puts it between two posts or a post and a wall. Returns whether it was drawn.
	 */
	public static boolean hammock(Draft d, int x, int z, Direction run, int len, int y) {
		for (int i = 0; i < len; i++) {
			int cx = x + Dirs.dx(run) * i;
			int cz = z + Dirs.dz(run) * i;
			if (!in(d, cx, cz) || !d.isEmpty(cx, y, cz) || !d.isEmpty(cx, y + 1, cz) || !d.isSolid(cx, y - 1, cz)) {
				return false;
			}
		}
		Mat cloth = d.mat(Role.CLOTH);
		var p = cloth.palette();
		Block a = p.main().block(Form.CARPET);
		Block b = p.entries().size() > 1 ? p.entries().get(1).material().block(Form.CARPET) : Blocks.CARPET.pick(net.minecraft.world.item.DyeColor.WHITE);
		if (a == null) {
			a = Blocks.CARPET.pick(net.minecraft.world.item.DyeColor.WHITE);
		}
		if (b == null) {
			b = Blocks.CARPET.pick(net.minecraft.world.item.DyeColor.WHITE);
		}
		Block weave = d.mat(Role.FURNITURE).mainOr(Form.TRAPDOOR, Blocks.SPRUCE_TRAPDOOR);
		try (var s = d.stage(StageKind.EXTERIOR)) {
			for (int i = 0; i < len; i++) {
				int cx = x + Dirs.dx(run) * i;
				int cz = z + Dirs.dz(run) * i;
				d.set(cx, y, cz, States.trapdoor(weave, run, Half.TOP, false));
				d.set(cx, y + 1, cz, ((i & 1) == 0 ? a : b).defaultBlockState());
			}
		}
		d.poi(PoiType.SEAT, x, y, z, Dirs.right(run));
		return true;
	}

	/**
	 * Lattice skirting the piles under a deck edge: open trapdoors standing on the outer face of the cells below the deck
	 * at (x, z), from the ground up to {@code topY}, facing out along {@code out}. Only over dry ground.
	 */
	public static void lattice(Draft d, int x, int z, Direction out, int topY) {
		if (!in(d, x, z) || d.site().isWater(x, z)) {
			return;
		}
		Block trap = d.mat(Role.SHUTTER).mainOr(Form.TRAPDOOR, d.mat(Role.FENCE).mainOr(Form.TRAPDOOR, Blocks.SPRUCE_TRAPDOOR));
		int g = d.site().ground(x, z);
		try (var s = d.stage(StageKind.EXTERIOR)) {
			for (int y = g; y <= topY; y++) {
				if (d.isAbsent(x, y, z)) {
					d.set(x, y, z, States.trapdoor(trap, out.getOpposite(), Half.BOTTOM, true));
				}
			}
		}
	}

	/** A lantern hanging under whatever is drawn at (x, y + 1, z), if that is solid and the cell is free. */
	public static boolean lantern(Draft d, int x, int y, int z) {
		if (!in(d, x, z) || !d.isEmpty(x, y, z) || !d.isSolid(x, y + 1, z)) {
			return false;
		}
		Furniture.hangingLantern(d, x, y + 1, z, 0);
		return true;
	}

	/** A pot of the culture's plants on the deck at (x, y, z), if the cell is free and floored. */
	public static boolean pot(Draft d, int x, int y, int z) {
		if (!in(d, x, z) || !d.isEmpty(x, y, z) || !d.isSolid(x, y - 1, z)) {
			return false;
		}
		dev.voidmc.hearth.build.kit.Garden.pot(d, x, y, z);
		return true;
	}

	/** A flower pot standing on the head of a rail post at (x, y - 1, z). */
	public static boolean railPot(Draft d, int x, int y, int z) {
		var below = d.get(x, y - 1, z);
		if (!in(d, x, z) || below == null || !(below.getBlock() instanceof net.minecraft.world.level.block.FenceBlock) || !d.isEmpty(x, y, z)) {
			return false;
		}
		dev.voidmc.hearth.build.kit.Garden.pot(d, x, y, z);
		return true;
	}

	/** A small table between rockers: a barrel standing up with a candle or a flower pot on it. */
	public static boolean sideTable(Draft d, int x, int y, int z) {
		if (!in(d, x, z) || !d.isEmpty(x, y, z) || !d.isEmpty(x, y + 1, z)) {
			return false;
		}
		try (var s = d.stage(StageKind.EXTERIOR)) {
			d.set(x, y, z, States.facing(Blocks.BARREL, Direction.UP));
			d.set(x, y + 1, z, Noise.unit(d.seed() ^ 0x51DE, x, y, z) < 0.5 ? States.of(Blocks.FLOWER_POT)
				: Blocks.CANDLE.defaultBlockState().setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.CANDLES, 2));
		}
		return true;
	}
}
