package dev.voidmc.hearth.build.archetype.landscape;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.ArchetypeCategory;
import dev.voidmc.hearth.api.ArchetypeInfo;
import dev.voidmc.hearth.api.PoiType;
import dev.voidmc.hearth.api.SettlementTier;
import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.Archetype;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Mat;
import dev.voidmc.hearth.build.draft.Noise;
import dev.voidmc.hearth.build.draft.Paint;
import dev.voidmc.hearth.build.draft.States;
import dev.voidmc.hearth.build.kit.landscape.Lamps;
import dev.voidmc.hearth.build.kit.landscape.Manner;
import dev.voidmc.hearth.build.kit.landscape.Paving;
import dev.voidmc.hearth.build.kit.landscape.Planters;
import dev.voidmc.hearth.build.style.Form;
import dev.voidmc.hearth.build.style.Role;
import dev.voidmc.hearth.build.terrain.Groundwork;
import java.util.List;
import java.util.Set;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * {@code hearth:landscape/fountain}: water in the square, in the culture's manner.
 *
 * <ul>
 *   <li><b>A basin with a jet</b> (temperate, wisteria, maritime, alpine, moorland, steppe): a square, octagonal or round
 *   basin whose rim is a ring of stone seats looking out, a column in the water carrying a bowl the jet spills over
 *   (a second, wider bowl on the bigger plots), lamps at the corners of the larger fountains.</li>
 *   <li><b>A Moorish fountain</b> (desert, mediterranean, mesa): a low octagonal basin rimmed in tiles, a short spout in
 *   the middle, pots of flowers round it.</li>
 *   <li><b>A spring pool</b> (karst, tropical, bayou): an irregular pool edged with mossy stones (palm or cypress logs
 *   in the timber cultures), lily pads, a bamboo spout pouring into a stone bowl, a stone lantern or torches.</li>
 *   <li><b>A trough and pump</b> (northern, tundra): a long stone trough, a timber pump post with its spout, a bucket.</li>
 * </ul>
 * Everything stands on the paving (nothing is dug below the square), so a fountain set into a paved plaza keeps the
 * plaza's floor as its basin's bed. The water is still where it is drawn; the jet runs when the game first updates it,
 * spilling over the bowls into the basin.
 *
 * <p>POIs: a MEETING_POINT in front, SEATs on the rim. Params: {@code shape} ({@code square}, {@code octagon},
 * {@code round}; default by culture and size).
 */
public final class Fountain implements Archetype {
	public static final ArchetypeInfo INFO = new ArchetypeInfo(Hearth.id("landscape/fountain"), ArchetypeCategory.LANDSCAPE, Set.of(), 0,
		5, 9, 5, 9, 4, 8, SettlementTier.VILLAGE, Set.of(), Set.of(ArchetypeInfo.SiteFlag.CENTRAL), List.of(), 0, 1.0);

	@Override
	public ArchetypeInfo info() {
		return INFO;
	}

	enum Kind {
		BASIN, MOORISH, SPRING, TROUGH
	}

	static Kind kind(Draft d) {
		return switch (Manner.culture(d)) {
			case DESERT, MEDITERRANEAN, MESA -> Kind.MOORISH;
			case KARST, TROPICAL, BAYOU -> Kind.SPRING;
			case NORTHERN, TUNDRA -> Kind.TROUGH;
			default -> Kind.BASIN;
		};
	}

	@Override
	public void draw(Draft d) {
		int w = d.frontage();
		int dp = d.depth();
		int cx = (w - 1) / 2;
		int cz = (dp - 1) / 2;
		d.entrance(cx, 0, -1);
		d.poi(PoiType.MEETING_POINT, cx, 0, -1, Dirs.BACK);
		Groundwork.level(d, 0, 0, w - 1, dp - 1, 0, true);
		Paving.lay(d, 0, 0, w - 1, dp - 1, 0, Paving.paint(d, w >= 7 ? Paving.Pattern.CIRCLES : Paving.pattern(d), (w - 1) / 2.0, (dp - 1) / 2.0, 2,
			false));
		int top = switch (kind(d)) {
			case MOORISH -> moorish(d, w, dp, cx, cz);
			case SPRING -> spring(d, w, dp, cx, cz);
			case TROUGH -> trough(d, w, dp, cx, cz);
			default -> basin(d, w, dp, cx, cz);
		};
		Groundwork.clear(d, 0, 0, w - 1, dp - 1, Math.min(d.maxHeight() - 1, top + 2));
		dev.voidmc.hearth.build.kit.refine.Voids.finish(d);
	}

	/** The basin's outline: which cells are inside (water or rim) for a shape over the plot inset by {@code m}. */
	static boolean inside(String shape, int x, int z, int w, int dp, int m) {
		int x0 = m;
		int z0 = m;
		int x1 = w - 1 - m;
		int z1 = dp - 1 - m;
		if (x < x0 || x > x1 || z < z0 || z > z1) {
			return false;
		}
		return switch (shape) {
			case "octagon" -> {
				int cut = Math.max(1, Math.min(x1 - x0, z1 - z0) / 3);
				yield Math.min(x - x0, x1 - x) + Math.min(z - z0, z1 - z) >= cut;
			}
			case "round" -> Draft.insideEllipse(x, z, (x0 + x1) / 2.0, (z0 + z1) / 2.0, (x1 - x0) / 2.0, (z1 - z0) / 2.0);
			default -> true;
		};
	}

	/** Whether a cell inside is on the rim (has a neighbour outside the shape). */
	static boolean rim(String shape, int x, int z, int w, int dp, int m) {
		if (!inside(shape, x, z, w, dp, m)) {
			return false;
		}
		for (Direction dir : Dirs.HORIZONTAL) {
			if (!inside(shape, x + Dirs.dx(dir), z + Dirs.dz(dir), w, dp, m)) {
				return true;
			}
		}
		return false;
	}

	/** The inward direction of a rim cell: toward the nearest inside neighbour that is water. */
	static Direction inward(String shape, int x, int z, int w, int dp, int m) {
		double cx = (w - 1) / 2.0;
		double cz = (dp - 1) / 2.0;
		Direction best = Dirs.nearest(cx - x, cz - z);
		if (inside(shape, x + Dirs.dx(best), z + Dirs.dz(best), w, dp, m) && !rim(shape, x + Dirs.dx(best), z + Dirs.dz(best), w, dp, m)) {
			return best;
		}
		for (Direction dir : Dirs.HORIZONTAL) {
			if (inside(shape, x + Dirs.dx(dir), z + Dirs.dz(dir), w, dp, m) && !rim(shape, x + Dirs.dx(dir), z + Dirs.dz(dir), w, dp, m)) {
				return dir;
			}
		}
		return best;
	}

	/** Fills a shaped basin: a rim of seats (or the given rim paint) round still water on the floor. */
	static void fill(Draft d, String shape, int w, int dp, int m, Mat rimMat, boolean seats) {
		BlockState water = Blocks.WATER.defaultBlockState();
		try (var s = d.stage(StageKind.EXTERIOR)) {
			for (int z = 0; z < dp; z++) {
				for (int x = 0; x < w; x++) {
					if (!inside(shape, x, z, w, dp, m)) {
						continue;
					}
					if (rim(shape, x, z, w, dp, m)) {
						Direction in = inward(shape, x, z, w, dp, m);
						boolean corner = !inside(shape, x + Dirs.dx(in), z + Dirs.dz(in), w, dp, m) || isCorner(shape, x, z, w, dp, m);
						if (seats && rimMat.has(Form.STAIRS) && !corner) {
							d.set(x, 0, z, rimMat.stairs(in, Half.BOTTOM));
						} else {
							d.set(x, 0, z, rimMat.full());
						}
					} else {
						d.set(x, 0, z, water);
					}
				}
			}
		}
	}

	private static boolean isCorner(String shape, int x, int z, int w, int dp, int m) {
		int outs = 0;
		for (Direction dir : Dirs.HORIZONTAL) {
			if (!inside(shape, x + Dirs.dx(dir), z + Dirs.dz(dir), w, dp, m)) {
				outs++;
			}
		}
		return outs >= 2;
	}

	private static String shape(Draft d, int w, String fallback) {
		return d.param("shape", fallback);
	}

	/** The classic basin: seats round the rim, a column and bowls, a jet. */
	private static int basin(Draft d, int w, int dp, int cx, int cz) {
		Mat rimMat = Manner.masonry(d);
		Mat accent = Paving.inlay(d);
		int m = w >= 7 && dp >= 7 ? 1 : 0;
		String fallback = Manner.culture(d) == dev.voidmc.hearth.api.Culture.STEPPE || Manner.culture(d) == dev.voidmc.hearth.api.Culture.WISTERIA
			? "round" : w >= 7 ? (d.chance(0.5) ? "octagon" : "square") : "square";
		String shape = shape(d, w, fallback);
		fill(d, shape, w, dp, m, rimMat, true);
		seats(d, shape, w, dp, m);
		int inner = Math.min(w, dp) - 2 * m - 2;
		int top;
		Mat col = rimMat;
		Paint shaft = col.has(Form.WALL) ? col.wall() : col.full();
		try (var s = d.stage(StageKind.EXTERIOR)) {
			if (inner >= 5 && d.maxHeight() >= 7) {
				// a wide lower bowl spilling all round, a little upper one with the jet
				d.set(cx, 0, cz, col.full());
				d.set(cx, 1, cz, shaft);
				d.set(cx, 2, cz, accent.full());
				for (Direction dir : Dirs.HORIZONTAL) {
					d.set(cx + Dirs.dx(dir), 2, cz + Dirs.dz(dir), col.stairsOrSlab(dir.getOpposite(), Half.TOP));
				}
				for (int[] c : new int[][]{{1, 1}, {1, -1}, {-1, 1}, {-1, -1}}) {
					d.set(cx + c[0], 2, cz + c[1], col.slab(SlabType.TOP));
				}
				d.set(cx, 3, cz, shaft);
				d.set(cx, 4, cz, accent.full());
				d.set(cx, 5, cz, Blocks.WATER.defaultBlockState());
				top = 5;
			} else if (d.maxHeight() >= 7) {
				// a small basin still stands its column tall: a tazza over the water spilling into it, a finial with the jet
				d.set(cx, 0, cz, accent.full());
				d.set(cx, 1, cz, shaft);
				d.set(cx, 2, cz, col.full());
				for (Direction dir : Dirs.HORIZONTAL) {
					d.set(cx + Dirs.dx(dir), 2, cz + Dirs.dz(dir), col.stairsOrSlab(dir.getOpposite(), Half.TOP));
				}
				for (int[] c : new int[][]{{1, 1}, {1, -1}, {-1, 1}, {-1, -1}}) {
					d.set(cx + c[0], 2, cz + c[1], col.slab(SlabType.TOP));
				}
				d.set(cx, 3, cz, shaft);
				d.set(cx, 4, cz, accent.full());
				d.set(cx, 5, cz, Blocks.WATER.defaultBlockState());
				top = 5;
			} else {
				d.set(cx, 0, cz, col.full());
				d.set(cx, 1, cz, shaft);
				d.set(cx, 2, cz, accent.full());
				d.set(cx, 3, cz, Blocks.WATER.defaultBlockState());
				top = 3;
			}
			}
			if (m == 0 && "square".equals(shape)) {
				// lanterns on the rim's corner stones, so the water glints at night
				try (var s = d.stage(StageKind.EXTERIOR)) {
					for (int[] c : new int[][]{{0, 0}, {w - 1, 0}, {0, dp - 1}, {w - 1, dp - 1}}) {
						if (d.isSolid(c[0], 0, c[1]) && d.isEmpty(c[0], 1, c[1])) {
							d.set(c[0], 1, c[1], States.lantern(Lamps.lantern(d), false));
						}
					}
				}
			}
		if (m >= 1) {
			for (int[] c : new int[][]{{0, 0}, {w - 1, 0}, {0, dp - 1}, {w - 1, dp - 1}}) {
				if (d.isAbsent(c[0], 0, c[1])) {
					if (w >= 9) {
						Lamps.standard(d, c[0], 0, c[1], 4, Manner.lamp(d), null);
					} else {
						Planters.bed(d, c[0], 0, c[1]);
					}
				}
			}
		}
		return top;
	}

	/** SEAT points on the rim's straight runs (the stairs there look out of the basin). */
	private static void seats(Draft d, String shape, int w, int dp, int m) {
		int cx = (w - 1) / 2;
		int cz = (dp - 1) / 2;
		for (int[] c : new int[][]{{cx, m}, {cx, dp - 1 - m}, {m, cz}, {w - 1 - m, cz}}) {
			BlockState st = d.get(c[0], 0, c[1]);
			if (st != null && st.getBlock() instanceof net.minecraft.world.level.block.StairBlock) {
				d.poi(PoiType.SEAT, c[0], 0, c[1], st.getValue(BlockStateProperties.HORIZONTAL_FACING).getOpposite());
			}
		}
	}

	/** A low octagon rimmed in tiles, a short spout, pots round it. */
	private static int moorish(Draft d, int w, int dp, int cx, int cz) {
		Mat tile = Paving.inlay(d);
		Mat stone = Manner.masonry(d);
		int m = w >= 7 && dp >= 7 ? 1 : 0;
		String shape = shape(d, w, "octagon");
		fill(d, shape, w, dp, m, stone, false);
		// tiles every other block of the rim
		try (var s = d.stage(StageKind.EXTERIOR)) {
			for (int z = 0; z < dp; z++) {
				for (int x = 0; x < w; x++) {
					if (rim(shape, x, z, w, dp, m) && ((x + z) & 1) == 0) {
						d.set(x, 0, z, tile.full());
					}
				}
			}
		}
		int top;
		try (var s = d.stage(StageKind.EXTERIOR)) {
			d.set(cx, 0, cz, tile.full());
			d.set(cx, 1, cz, stone.has(Form.WALL) ? stone.wall() : stone.full());
			if (w >= 7 && d.maxHeight() >= 6) {
				d.set(cx, 2, cz, tile.full());
				d.set(cx, 3, cz, Blocks.WATER.defaultBlockState());
				top = 3;
			} else {
				d.set(cx, 2, cz, Blocks.WATER.defaultBlockState());
				top = 2;
			}
		}
		// pots and a palm or an orange tree in tubs at the corners outside the basin
		for (int[] c : new int[][]{{0, 0}, {w - 1, 0}, {0, dp - 1}, {w - 1, dp - 1}}) {
			if (!d.isAbsent(c[0], 0, c[1])) {
				continue;
			}
			if (m >= 1 && Noise.unit(d.seed() ^ 0x0A, c[0], 0, c[1]) < 0.5) {
				try (var s = d.stage(StageKind.EXTERIOR)) {
					d.set(c[0], 0, c[1], States.of(Blocks.DECORATED_POT));
				}
			} else {
				dev.voidmc.hearth.build.kit.Garden.pot(d, c[0], 0, c[1]);
			}
		}
		return top;
	}

	/** An irregular spring pool edged with stones or logs, lily pads, a bamboo spout into a stone bowl. */
	private static int spring(Draft d, int w, int dp, int cx, int cz) {
		boolean timber = Manner.timberForts(d);
		Mat edge = timber ? d.mat(Role.FRAME) : d.mat(Role.FOUNDATION);
		BlockState water = Blocks.WATER.defaultBlockState();
		long seed = d.seed() ^ 0x5B21;
		double rx = (w - 1) / 2.0 - 0.3;
		double rz = (dp - 1) / 2.0 - 0.3;
		boolean[][] pool = new boolean[w][dp];
		for (int z = 0; z < dp; z++) {
			for (int x = 0; x < w; x++) {
				double ex = (x - (w - 1) / 2.0) / (rx + 0.5);
				double ez = (z - (dp - 1) / 2.0) / (rz + 0.5);
				pool[x][z] = ex * ex + ez * ez <= 0.95 + 0.25 * (Noise.unit(seed, x, 0, z) - 0.5);
			}
		}
		int top = 1;
		try (var s = d.stage(StageKind.EXTERIOR)) {
			for (int z = 0; z < dp; z++) {
				for (int x = 0; x < w; x++) {
					if (!pool[x][z]) {
						continue;
					}
					boolean border = x == 0 || z == 0 || x == w - 1 || z == dp - 1;
					for (Direction dir : Dirs.HORIZONTAL) {
						int nx = x + Dirs.dx(dir);
						int nz = z + Dirs.dz(dir);
						border |= nx < 0 || nz < 0 || nx >= w || nz >= dp || !pool[nx][nz];
					}
					if (border) {
						double u = Noise.unit(seed, x, 1, z);
						if (timber) {
							Direction along = Math.abs(x - cx) > Math.abs(z - cz) ? Dirs.FRONT : Dirs.RIGHT;
							d.set(x, 0, z, edge.log(along.getAxis()).orElse(edge.full()));
						} else {
							d.set(x, 0, z, u < 0.3 ? Blocks.MOSSY_COBBLESTONE.defaultBlockState() : edge.full().at(x, 0, z));
							if (u > 0.85 && d.maxHeight() > 2) {
								d.set(x, 1, z, u > 0.93 ? Blocks.MOSS_CARPET.defaultBlockState() : edge.slab(SlabType.BOTTOM).at(x, 1, z));
							}
						}
					} else {
						d.set(x, 0, z, water);
						if (Noise.unit(seed, x, 2, z) < 0.22) {
							d.set(x, 1, z, Blocks.LILY_PAD.defaultBlockState());
						}
					}
				}
			}
		}
		// the spout at the back: a post with a bamboo pipe pouring into a stone bowl standing in the water
		int bx = cx;
		int bz = Math.max(1, dp - 3);
		if (pool[bx][bz] && d.maxHeight() >= 5) {
			try (var s = d.stage(StageKind.EXTERIOR)) {
				d.set(bx, 0, bz, Manner.masonry(d).full());
				d.set(bx, 1, bz, Blocks.WATER_CAULDRON.defaultBlockState().setValue(BlockStateProperties.LEVEL_CAULDRON, 3));
				int px = bx;
				int pz = bz + 1;
				d.set(px, 0, pz, edge.full());
				Paint stake = d.mat(Role.FENCE).has(Form.FENCE) ? d.mat(Role.FENCE).fence() : Lamps.post(d);
				d.set(px, 1, pz, stake);
				d.set(px, 2, pz, stake);
				d.set(px, 2, bz, Blocks.BAMBOO_BLOCK.defaultBlockState().trySetValue(BlockStateProperties.AXIS, Direction.Axis.Z));
				top = 2;
			}
		}
		// a stone lantern (or torches) at the front corner
		int lx = w - 1;
		int lz = 0;
		if (pool[lx][lz]) {
			lx = 0;
		}
		if (!pool[lx][lz] && d.isAbsent(lx, 0, lz)) {
			top = Math.max(top, Lamps.standard(d, lx, 0, lz, 4, Manner.lamp(d), null));
		}
		return top;
	}

	/** A long stone trough with a timber pump post and its spout, a bucket beside. */
	private static int trough(Draft d, int w, int dp, int cx, int cz) {
		Mat stone = Manner.masonry(d);
		Mat wood = d.mat(Role.FRAME);
		int x0 = 0;
		int x1 = w - 1;
		int z0 = Math.max(0, cz - 1);
		int z1 = Math.min(dp - 1, z0 + 2);
		try (var s = d.stage(StageKind.EXTERIOR)) {
			for (int z = z0; z <= z1; z++) {
				for (int x = x0; x <= x1; x++) {
					boolean wall = x == x0 || x == x1 || z == z0 || z == z1;
					d.set(x, 0, z, wall ? stone.full() : Paint.of(Blocks.WATER.defaultBlockState()));
				}
			}
			// the pump behind the trough's middle: a post, a handle, a spout over the water
			int pz = z1 + 1 < dp ? z1 + 1 : z1;
			if (pz != z1) {
				for (int y = 0; y <= 2; y++) {
					d.set(cx, y, pz, wood.log(Direction.Axis.Y).orElse(wood.full()));
				}
				d.set(cx, 3, pz, wood.slab(SlabType.BOTTOM));
				d.set(cx, 2, z1, wood.trapdoor(Dirs.FRONT, Half.TOP, false));
				d.set(cx + 1 <= x1 ? cx + 1 : cx - 1, 2, pz, wood.fence());
			}
			// a bucket and a lantern on the trough's end
			if (z0 - 1 >= 0) {
				d.set(x0, 0, z0 - 1, Blocks.CAULDRON.defaultBlockState());
			}
		}
		Lamps.lanternOn(d, x1, 1, z0);
		return 3;
	}
}
