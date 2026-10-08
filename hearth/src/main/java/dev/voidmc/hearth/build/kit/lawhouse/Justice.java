package dev.voidmc.hearth.build.kit.lawhouse;

import dev.voidmc.hearth.api.PoiType;
import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Paint;
import dev.voidmc.hearth.build.draft.States;
import dev.voidmc.hearth.build.kit.landmark.Stone;
import dev.voidmc.hearth.build.kit.light.Lighting;
import dev.voidmc.hearth.build.style.Form;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * The square's instruments of shame: a stone dais to raise them before the crowd, the pillory (two posts, the pale
 * neck board with the gap where the prisoner's head goes, a beam and a little cap with a lantern), the stocks (a low
 * board between posts, a bench behind), and the bin of rotten vegetables the crowd throws from. Nothing bloody: the
 * law here is public, not gory.
 */
public final class Justice {
	private Justice() {
	}

	/**
	 * A dais over {@code x0..x1 × z0..z1} whose deck is at standing level {@code h} (blocks from y 0 to h - 1): a body of
	 * footing stone, a coping of the dressing round its top edge, the deck paved in the dressing, and a flight of
	 * {@code h} steps in the front row {@code z0 - h .. z0 - 1} across {@code s0..s1}, cheeked at its ends.
	 */
	public static void dais(Draft d, int x0, int z0, int x1, int z1, int h, int s0, int s1) {
		Stone body = Stone.footing(d);
		Stone dress = Stone.dressing(d);
		try (var st = d.stage(StageKind.FOUNDATION)) {
			for (int z = z0; z <= z1; z++) {
				for (int x = x0; x <= x1; x++) {
					int g = Math.min(-1, d.site().ground(x, z) - 1);
					for (int y = g; y < h - 1; y++) {
						d.set(x, y, z, body.full());
					}
				}
			}
		}
		try (var st = d.stage(StageKind.WALLS)) {
			for (int z = z0; z <= z1; z++) {
				for (int x = x0; x <= x1; x++) {
					boolean edge = x == x0 || x == x1 || z == z0 || z == z1;
					d.set(x, h - 1, z, edge ? dress.full() : paving(d));
				}
			}
		}
		// the steps, one row per course, cheeked by blocks of the body
		Paint step = dress.has(Form.STAIRS) ? dress.stairs(Dirs.BACK, Half.BOTTOM) : body.stairsOrSlab(Dirs.BACK, Half.BOTTOM);
		for (int k = 0; k < h; k++) {
			int z = z0 - 1 - k;
			int top = h - 1 - k;
			if (z < 0) {
				break;
			}
			try (var st = d.stage(StageKind.FOUNDATION)) {
				for (int x = s0; x <= s1; x++) {
					for (int y = Math.min(-1, d.site().ground(x, z) - 1); y < top; y++) {
						d.set(x, y, z, body.full());
					}
				}
			}
			try (var st = d.stage(StageKind.EXTERIOR)) {
				for (int x = s0; x <= s1; x++) {
					d.set(x, top, z, step);
				}
			}
			for (int x : new int[]{s0 - 1, s1 + 1}) {
				if (x < x0 || x > x1 || top < 1) {
					continue;
				}
				try (var st = d.stage(StageKind.WALLS)) {
					for (int y = Math.min(-1, d.site().ground(x, z) - 1); y <= top; y++) {
						d.set(x, y, z, y == top ? dress.full() : body.full());
					}
				}
			}
		}
	}

	/** The deck of a dais: the style's paving where it is stone, else the dressing. */
	static Paint paving(Draft d) {
		var p = d.mat(dev.voidmc.hearth.build.style.Role.PAVING);
		Stone dress = Stone.dressing(d);
		return Stone.stony(d, dev.voidmc.hearth.build.style.Role.PAVING) ? p.full() : dress.full();
	}

	/**
	 * The pillory: the prisoner stands at (x, y, z) facing {@code out} (toward the crowd), between two dark posts; a pale
	 * neck board runs from each post toward the head at head height, a rail of slabs crosses over the head and carries a
	 * lantern. Registers the PILLORY point (facing the crowd). Returns the top.
	 */
	public static int pillory(Draft d, int x, int y, int z, Direction out) {
		Timber t = Timber.frame(d);
		Timber board = Timber.joinery(d);
		Direction along = Dirs.right(out.getOpposite());
		Direction.Axis ax = along.getAxis();
		int lx = x - Dirs.dx(along);
		int lz = z - Dirs.dz(along);
		int rx = x + Dirs.dx(along);
		int rz = z + Dirs.dz(along);
		try (var st = d.stage(StageKind.FRAME)) {
			for (int[] p : new int[][]{{lx, lz}, {rx, rz}}) {
				d.set(p[0], y, p[1], t.post());
				// the neck board: a pale hewn timber lying across, from the post to the head
				d.set(p[0], y + 1, p[1], board.hewn(ax));
			}
			// the rail over the head and the boards, the lantern standing on its middle
			for (int[] p : new int[][]{{lx, lz}, {x, z}, {rx, rz}}) {
				d.set(p[0], y + 2, p[1], t.slab(SlabType.BOTTOM));
			}
		}
		Lighting.lamp(d, x, y + 3, z);
		d.poi(PoiType.PILLORY, x, y, z, out);
		return y + 3;
	}

	/**
	 * The stone pillory of a market town (the Bohemian <i>pranýř</i>, the Scots mercat cross with its jougs, the Italian
	 * <i>colonna infame</i>): a column of the dressing on a foot block, a corbelled capital, a lantern on top; the iron
	 * collar's chain hangs from the capital's front corbel over the prisoner, who stands at (x, y, z) against the column's
	 * front with their back to it, facing {@code out}. Registers the PILLORY point. Returns the top.
	 */
	public static int column(Draft d, int x, int y, int z, Direction out) {
		Stone dress = Stone.dressing(d);
		Direction back = out.getOpposite();
		int cx = x + Dirs.dx(back);
		int cz = z + Dirs.dz(back);
		int shaft = 2;
		try (var st = d.stage(StageKind.WALLS)) {
			d.set(cx, y, cz, dress.full());
			for (int k = 1; k <= shaft; k++) {
				d.set(cx, y + k, cz, dress.wallOrFull());
			}
			int cap = y + shaft + 1;
			d.set(cx, cap, cz, dress.full());
			// the corbels round the capital, the front one carrying the collar's chain
			for (Direction dir : Dirs.HORIZONTAL) {
				int ox = cx + Dirs.dx(dir);
				int oz = cz + Dirs.dz(dir);
				if (d.isEmpty(ox, cap, oz) && ox >= 0 && oz >= 0 && ox < d.frontage() && oz < d.depth()) {
					d.set(ox, cap, oz, dress.stairsOrSlab(dir.getOpposite(), Half.TOP));
				}
			}
		}
		int cap = y + shaft + 1;
		try (var st = d.stage(StageKind.INTERIOR)) {
			d.set(x, cap - 1, z, chain());
		}
		Lighting.lamp(d, cx, cap + 1, cz);
		d.poi(PoiType.PILLORY, x, y, z, out);
		return cap + 1;
	}

	/**
	 * The stocks for {@code n} (1 or 2): a low board (top trapdoors) between short posts along {@code along} from
	 * (x, y, z), the bench behind it a row toward {@code back}. Takes {@code n + 2} cells along and 2 deep.
	 */
	public static void stocks(Draft d, int x, int y, int z, Direction along, Direction back, int n) {
		Timber t = Timber.joinery(d);
		Timber f = Timber.frame(d);
		try (var st = d.stage(StageKind.INTERIOR)) {
			for (int i = 0; i < n + 2; i++) {
				int px = x + Dirs.dx(along) * i;
				int pz = z + Dirs.dz(along) * i;
				if (i == 0 || i == n + 1) {
					// (low posts and one board: a capped post or a second board is a stair up onto the dais's edge)
					d.set(px, y, pz, f.fence());
				} else {
					d.set(px, y, pz, t.trapdoor(back.getOpposite(), Half.TOP, false));
				}
				if (i >= 1 && i <= n) {
					int bx = px + Dirs.dx(back);
					int bz = pz + Dirs.dz(back);
					d.set(bx, y, bz, t.stairs(back, Half.BOTTOM));
				}
			}
		}
	}

	/** The bin of rotten vegetables (a composter, a pumpkin, a melon) beside the pillory, at standing level y. */
	public static void bin(Draft d, int x, int y, int z) {
		try (var st = d.stage(StageKind.EXTERIOR)) {
			d.set(x, y, z, Blocks.COMPOSTER.defaultBlockState().trySetValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.LEVEL_COMPOSTER, 7));
		}
	}

	/** A pumpkin or melon lying about. */
	public static void produce(Draft d, int x, int y, int z, boolean melon) {
		try (var st = d.stage(StageKind.EXTERIOR)) {
			d.set(x, y, z, melon ? Blocks.MELON.defaultBlockState() : Blocks.PUMPKIN.defaultBlockState());
		}
	}

	/** A slab of the dressing (a coping, a kerb's cap), or nothing where it has none. */
	static Paint dressSlab(Draft d) {
		return Stone.dressing(d).slabOrNothing(SlabType.BOTTOM);
	}

	/**
	 * The whipping post of the plains, the tropics and the north: a tall hewn post on a foot block of the dressing, an
	 * iron ring and chain at the prisoner's head height on its front, a lantern on a crossbar at its top. The prisoner
	 * stands at (x, y, z) before it facing {@code out}. Registers the PILLORY point. Returns the top.
	 */
	public static int post(Draft d, int x, int y, int z, Direction out) {
		Timber t = Timber.frame(d);
		Direction back = out.getOpposite();
		int px = x + Dirs.dx(back);
		int pz = z + Dirs.dz(back);
		Stone dress = Stone.dressing(d);
		try (var st = d.stage(StageKind.FRAME)) {
			d.set(px, y, pz, dress.full());
			for (int k = 1; k <= 3; k++) {
				d.set(px, y + k, pz, t.post());
			}
			d.set(px, y + 4, pz, t.fence());
		}
		// the collar's chain hangs from an iron-shod arm over the prisoner's head
		try (var st = d.stage(StageKind.FRAME)) {
			d.set(x, y + 3, z, t.slab(SlabType.TOP));
		}
		try (var st = d.stage(StageKind.INTERIOR)) {
			d.set(x, y + 2, z, chain());
		}
		Lighting.lamp(d, px, y + 5, pz);
		d.poi(PoiType.PILLORY, x, y, z, out);
		return y + 5;
	}

	/**
	 * The cangue stand of the karst's yamen gate: two red posts and a lintel, the cangue (a heavy square board about the
	 * neck) hung at head height as a collar of top slabs round the prisoner standing at (x, y, z) facing {@code out}, a
	 * lantern under the lintel. Registers the PILLORY point. Returns the top.
	 */
	public static int cangue(Draft d, int x, int y, int z, Direction out) {
		Timber t = Timber.frame(d);
		Timber board = Timber.joinery(d);
		Direction along = Dirs.right(out.getOpposite());
		try (var st = d.stage(StageKind.FRAME)) {
			for (int s : new int[]{-1, 1}) {
				int px = x + Dirs.dx(along) * s;
				int pz = z + Dirs.dz(along) * s;
				for (int k = 0; k <= 2; k++) {
					d.set(px, y + k, pz, t.post());
				}
				// the board's wings either side of the neck
				d.set(px, y + 1, pz, board.slab(SlabType.TOP));
			}
			for (int s = -1; s <= 1; s++) {
				d.set(x + Dirs.dx(along) * s, y + 3, z + Dirs.dz(along) * s, t.beam(along.getAxis()));
			}
		}
		Lighting.lamp(d, x, y + 4, z);
		d.poi(PoiType.PILLORY, x, y, z, out);
		return y + 4;
	}

	/** The chain's state (for hanging ropes and collars). */
	public static net.minecraft.world.level.block.state.BlockState chainState() {
		return chain();
	}

	/** States helper. */
	static net.minecraft.world.level.block.state.BlockState chain() {
		return States.axis(Blocks.IRON_CHAIN, Direction.Axis.Y);
	}
}
