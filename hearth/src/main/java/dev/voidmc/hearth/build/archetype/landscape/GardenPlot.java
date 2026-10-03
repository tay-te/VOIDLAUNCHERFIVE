package dev.voidmc.hearth.build.archetype.landscape;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.ArchetypeCategory;
import dev.voidmc.hearth.api.ArchetypeInfo;
import dev.voidmc.hearth.api.Culture;
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
import dev.voidmc.hearth.build.kit.Garden;
import dev.voidmc.hearth.build.kit.landscape.Benches;
import dev.voidmc.hearth.build.kit.landscape.Lamps;
import dev.voidmc.hearth.build.kit.landscape.Manner;
import dev.voidmc.hearth.build.kit.landscape.Paving;
import dev.voidmc.hearth.build.kit.landscape.Planters;
import dev.voidmc.hearth.build.kit.landscape.Terraces;
import dev.voidmc.hearth.build.kit.landscape.Trees;
import dev.voidmc.hearth.build.style.Form;
import dev.voidmc.hearth.build.style.Habits.GardenKind;
import dev.voidmc.hearth.build.style.Role;
import dev.voidmc.hearth.build.terrain.Groundwork;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * {@code hearth:landscape/garden}: a garden on a plot of its own among the houses, laid out in the culture's manner and
 * terraced where the ground rises or falls behind the street (a retaining wall with a coping, steps on the path).
 *
 * <ul>
 *   <li><b>Cottage</b> (temperate, alpine, wisteria): fenced or hedged, a path from the gate to an arbour with a bench at
 *   the back, a cross path, borders of flowers, a fruit tree, a beehive, a bird bath.</li>
 *   <li><b>Kitchen</b> (northern, mediterranean, steppe): beds of crops in rows with water between, a scarecrow, a
 *   compost bin and a water butt, a trellis of vines on the back fence, an olive or fruit tree, pots.</li>
 *   <li><b>Riad</b> (desert): walled, quartered by tiled walks meeting at a little fountain, palms in two quarters, a dry
 *   garden of sand, cacti and pots in the others, stone benches in the shade. On the mesa a cactus garden under a
 *   ramada of poles.</li>
 *   <li><b>Zen</b> (karst): raked gravel with moss islands and weathered stones, a blossom tree, a stone lantern, a pool
 *   crossed by stepping stones, a viewing deck with a bench at the back.</li>
 *   <li><b>Tropical</b>: ferns and big leaves, palms, a lily pool, bamboo.</li>
 *   <li><b>Rock</b> (moorland, maritime): dry-stone walled, heather and boulders, a flagged path, a birch, a bench.</li>
 *   <li><b>Bog</b> (bayou): pools and mud, a boardwalk, a tree hung with moss. <b>Winter garden</b> (tundra): spruces,
 *   stones, a fire bowl with log seats.</li>
 * </ul>
 *
 * <p>POIs: SEATs on the benches, a VIEWPOINT at the back bench. Params: {@code kind} (a garden kind name to force one).
 */
public final class GardenPlot implements Archetype {
	public static final ArchetypeInfo INFO = new ArchetypeInfo(Hearth.id("landscape/garden"), ArchetypeCategory.LANDSCAPE, Set.of(), 0,
		7, 15, 7, 15, 1, 9, SettlementTier.HAMLET, Set.of(), Set.of(), List.of(), 0, 1.0);

	@Override
	public ArchetypeInfo info() {
		return INFO;
	}

	enum Kind {
		COTTAGE, KITCHEN, RIAD, CACTUS, ZEN, TROPICAL, ROCK, BOG, WINTER
	}

	static Kind kind(Draft d) {
		String forced = d.param("kind", "");
		for (Kind k : Kind.values()) {
			if (k.name().equalsIgnoreCase(forced)) {
				return k;
			}
		}
		Culture c = Manner.culture(d);
		if (c == Culture.DESERT) {
			return Kind.RIAD;
		}
		if (c == Culture.MESA) {
			return Kind.CACTUS;
		}
		if (c == Culture.BAYOU) {
			return Kind.BOG;
		}
		if (c == Culture.TUNDRA) {
			return Kind.WINTER;
		}
		GardenKind g = d.habits().yard().garden();
		return switch (g) {
			case KITCHEN -> Kind.KITCHEN;
			case ZEN -> Kind.ZEN;
			case TROPICAL -> Kind.TROPICAL;
			case ROCK -> Kind.ROCK;
			case XERIC -> Kind.RIAD;
			default -> Kind.COTTAGE;
		};
	}

	/** What the layout functions share. */
	record Lay(Draft d, int w, int dp, int cx, int[] level, int x0, int z0, int x1, int z1) {
		int y(int z) {
			return this.level[Math.clamp(z, 0, this.level.length - 1)];
		}
	}

	@Override
	public void draw(Draft d) {
		int w = d.frontage();
		int dp = d.depth();
		int cx = (w - 1) / 2;
		Kind kind = kind(d);
		d.entrance(cx, 0, -1);
		int[] level = Terraces.plan(d, 0, w - 1, 4, 2);
		Terraces.build(d, 0, w - 1, level);
		// lawn or bare earth over the levelled ground, by kind
		ground(d, kind, w, dp, level);
		Lay l = new Lay(d, w, dp, cx, level, 1, 1, w - 2, dp - 2);
		// the boundary and its gate
		boundary(d, kind, l);
		// the main path from the gate to the back, the steps where it climbs
		mainPath(d, kind, l);
		Terraces.steps(d, cx, 1, level);
		int top = switch (kind) {
			case KITCHEN -> kitchen(l);
			case RIAD -> riad(l);
			case CACTUS -> cactus(l);
			case ZEN -> zen(l);
			case TROPICAL -> tropical(l);
			case ROCK -> rock(l);
			case BOG -> bog(l);
			case WINTER -> winter(l);
			default -> cottage(l);
		};
		// a lamp inside the gate
		if (d.isAbsent(cx + 1, level[1], 1) && w >= 7) {
			top = Math.max(top, Lamps.standard(d, cx + 1, level[1], 1, 4, Manner.lamp(d), null));
		}
		Groundwork.clear(d, 0, 0, w - 1, dp - 1, Math.min(d.maxHeight() - 1, top + 1));
	}

	private static void ground(Draft d, Kind kind, int w, int dp, int[] level) {
		Block surface = switch (kind) {
			case RIAD, CACTUS -> d.mat(Role.GROUND).mainOr(Form.FULL, Blocks.SAND);
			case ZEN -> Blocks.GRAVEL;
			case BOG -> Blocks.MUD;
			default -> Blocks.GRASS_BLOCK;
		};
		try (var s = d.stage(StageKind.EXTERIOR)) {
			for (int z = 0; z < dp; z++) {
				for (int x = 0; x < w; x++) {
					if (d.site().isWater(x, z)) {
						continue;
					}
					BlockState below = d.get(x, level[z] - 1, z);
					if (below == null || Garden.groundLike(d, x, level[z] - 1, z)) {
						d.set(x, level[z] - 1, z, surface.defaultBlockState());
					}
				}
			}
		}
	}

	private static void boundary(Draft d, Kind kind, Lay l) {
		int w = l.w();
		int dp = l.dp();
		List<int[]> front = Garden.line(0, 0, w - 1, 0);
		List<int[]> rest = new ArrayList<>();
		rest.addAll(Garden.line(0, 1, 0, dp - 1));
		rest.addAll(Garden.line(1, dp - 1, w - 1, dp - 1));
		rest.addAll(Garden.line(w - 1, 1, w - 1, dp - 2));
		if (kind == Kind.RIAD) {
			// a walled garden: two high, a coping, an arched gateway
			Mat wall = Manner.masonry(d);
			Mat cope = d.mat(Role.WALL_ACCENT);
			List<int[]> all = new ArrayList<>(front);
			all.addAll(rest);
			try (var s = d.stage(StageKind.WALLS)) {
				for (int[] c : all) {
					int g = l.y(c[1]);
					if (c[1] == 0 && Math.abs(c[0] - l.cx()) <= 0) {
						d.set(c[0], g + 2, c[1], wall.full());
						continue;
					}
					d.set(c[0], g, c[1], wall.full());
					d.set(c[0], g + 1, c[1], wall.full());
					d.set(c[0], g + 2, c[1], Manner.wood(cope) ? wall.slab(SlabType.BOTTOM) : cope.slab(SlabType.BOTTOM).orElse(wall.full()));
				}
				if (wall.has(Form.STAIRS)) {
					d.set(l.cx() - 1, l.y(0) + 1, 0, wall.stairs(Dirs.LEFT, Half.TOP));
					d.set(l.cx() + 1, l.y(0) + 1, 0, wall.stairs(Dirs.RIGHT, Half.TOP));
				}
			}
			return;
		}
		Garden.fence(d, front, l.cx(), Dirs.FRONT);
		Garden.fence(d, rest, -1, Dirs.FRONT);
	}

	private static void mainPath(Draft d, Kind kind, Lay l) {
		List<int[]> cells = new ArrayList<>();
		for (int z = 0; z < l.dp() - 1; z++) {
			cells.add(new int[]{l.cx(), z});
		}
		switch (kind) {
			case RIAD, CACTUS, ROCK -> {
				Mat p = kind == Kind.RIAD ? Paving.inlay(d) : d.mat(Role.PAVING);
				Mat band = Manner.masonry(d);
				try (var s = d.stage(StageKind.EXTERIOR)) {
					for (int[] c : cells) {
						int y = l.y(c[1]);
						if (Garden.groundLike(d, c[0], y - 1, c[1])) {
							d.set(c[0], y - 1, c[1], kind == Kind.RIAD && (c[1] & 1) == 0 ? p.full().at(c[0], y - 1, c[1]) : band.full().at(c[0], y - 1, c[1]));
						}
					}
				}
			}
			case ZEN -> {
				// stepping stones over the gravel
				try (var s = d.stage(StageKind.EXTERIOR)) {
					for (int[] c : cells) {
						int y = l.y(c[1]);
						if ((c[1] & 1) == 1 && Garden.groundLike(d, c[0], y - 1, c[1])) {
							d.set(c[0], y - 1, c[1], Manner.masonry(d).full().at(c[0], y - 1, c[1]));
						}
					}
				}
			}
			case BOG -> {
				Mat boards = d.mat(Role.FURNITURE);
				try (var s = d.stage(StageKind.EXTERIOR)) {
					for (int[] c : cells) {
						int y = l.y(c[1]);
						if (d.isAbsent(c[0], y, c[1]) || d.isEmpty(c[0], y, c[1])) {
							d.set(c[0], y - 1, c[1], boards.full());
						}
					}
				}
			}
			default -> {
				for (int[] c : cells) {
					Garden.path(d, List.of(c), l.y(c[1]));
				}
			}
		}
	}

	/** Cells of a rectangle's interior on one terrace level (same level as the given row), for beds. */
	private static List<int[]> cells(Lay l, int x0, int z0, int x1, int z1) {
		List<int[]> out = new ArrayList<>();
		for (int z = Math.max(1, z0); z <= Math.min(l.dp() - 2, z1); z++) {
			for (int x = Math.max(1, x0); x <= Math.min(l.w() - 2, x1); x++) {
				if (x == l.cx() || l.d().site().isWater(x, z)) {
					continue;
				}
				if (z > 0 && l.level()[z] != l.level()[z - 1] || z + 1 < l.dp() && l.level()[z] != l.level()[z + 1]) {
					continue;
				}
				out.add(new int[]{x, z});
			}
		}
		return out;
	}

	/** The style's garden over a rectangle, terrace by terrace (each band at its own level, the wall rows left out). */
	private static void bands(Lay l, int x0, int z0, int x1, int z1) {
		int z = Math.max(1, z0);
		int end = Math.min(l.dp() - 2, z1);
		while (z <= end) {
			int a = z;
			while (z + 1 <= end && l.y(z + 1) == l.y(a)) {
				z++;
			}
			int b = z;
			// leave out a row against a retaining wall either side
			int ra = a > 0 && l.y(a - 1) != l.y(a) ? a + 1 : a;
			int rb = b + 1 < l.dp() && l.y(b + 1) != l.y(b) ? b - 1 : b;
			if (rb >= ra && x1 >= x0) {
				Garden.garden(l.d(), x0, ra, x1, rb, l.y(a));
			}
			z++;
		}
	}

	/** The arbour at the back: two posts, beams, a roof of leaves, a bench under it looking down the path. */
	private static int arbour(Lay l, Block leaves) {
		Draft d = l.d();
		int z = l.dp() - 2;
		int y = l.y(z);
		int cx = l.cx();
		Mat post = d.mat(Role.FRAME);
		if (y + 3 >= d.maxHeight()) {
			Benches.bench(d, cx, y, z, Dirs.FRONT, Dirs.RIGHT, 1, true);
			return y + 1;
		}
		try (var s = d.stage(StageKind.EXTERIOR)) {
			for (int x : new int[]{cx - 1, cx + 1}) {
				if (x < 1 || x > l.w() - 2) {
					continue;
				}
				d.set(x, y, z, post.fence());
				d.set(x, y + 1, z, post.fence());
				d.set(x, y + 2, z, post.log(Direction.Axis.Y).orElse(post.full()));
			}
			for (int x = cx - 1; x <= cx + 1; x++) {
				d.set(x, y + 3, z, States.leaves(leaves));
				if (z - 1 >= 1) {
					d.set(x, y + 3, z - 1, States.leaves(leaves));
				}
			}
			d.set(cx, y + 2, z, post.log(Direction.Axis.X).orElse(post.full()));
		}
		Benches.bench(d, cx, y, z, Dirs.FRONT, Dirs.RIGHT, 1, false);
		d.poi(PoiType.VIEWPOINT, cx, y, z, Dirs.FRONT);
		return y + 3;
	}

	private static int cottage(Lay l) {
		Draft d = l.d();
		int mid = l.dp() / 2;
		// the cross path
		for (int x = 1; x <= l.w() - 2; x++) {
			Garden.path(d, List.of(new int[]{x, mid}), l.y(mid));
		}
		// borders along the fences, beds in the quarters
		List<int[]> beds = new ArrayList<>();
		for (int[] c : cells(l, 1, 1, l.w() - 2, l.dp() - 3)) {
			if (c[1] != mid) {
				beds.add(c);
			}
		}
		for (int[] c : beds) {
			Garden.flowerBed(d, List.of(c), l.y(c[1]), 0.6);
		}
		int top = l.y(0) + 1;
		// a fruit tree in a back quarter, a beehive and a bird bath in the front ones
		int tx = l.cx() + Math.max(2, (l.w() - 1) / 4);
		int tz = Math.min(l.dp() - 3, mid + 2);
		if (tx <= l.w() - 2 && tz > mid) {
			clearCell(d, tx, l.y(tz), tz);
			top = Math.max(top, Trees.tree(d, tx, l.y(tz), tz, Math.min(6, d.maxHeight() - l.y(tz)), Trees.kind(d), 1, mid + 1, l.w() - 2, l.dp() - 2));
		}
		int bx = Math.max(1, l.cx() - 2);
		int bz = Math.max(1, mid - 2);
		if (bz < mid) {
			clearCell(d, bx, l.y(bz), bz);
			Garden.beehive(d, bx, l.y(bz), bz, Dirs.FRONT);
		}
		int wx = Math.min(l.w() - 2, l.cx() + 2);
		if (bz < mid && wx != l.cx()) {
			clearCell(d, wx, l.y(bz), bz);
			try (var s = d.stage(StageKind.EXTERIOR)) {
				Mat st = Manner.masonry(d);
				d.set(wx, l.y(bz), bz, st.has(Form.WALL) ? st.wall() : st.full());
				d.set(wx, l.y(bz) + 1, bz, Blocks.WATER_CAULDRON.defaultBlockState().setValue(BlockStateProperties.LEVEL_CAULDRON, 3));
			}
		}
		Block flowers = Manner.culture(d) == Culture.WISTERIA ? Blocks.FLOWERING_AZALEA_LEAVES : Blocks.FLOWERING_AZALEA_LEAVES;
		return Math.max(top, arbour(l, flowers));
	}

	private static void clearCell(Draft d, int x, int y, int z) {
		d.remove(x, y, z);
		d.remove(x, y + 1, z);
	}

	private static int kitchen(Lay l) {
		Draft d = l.d();
		int top = l.y(0) + 1;
		// beds of rows either side of the path, a walk between bed and fence
		int x0 = 2;
		int x1 = l.w() - 3;
		int zEnd = l.dp() - 3;
		for (int side = 0; side < 2; side++) {
			int a = side == 0 ? x0 : l.cx() + 1;
			int b = side == 0 ? l.cx() - 1 : x1;
			if (b - a < 1) {
				continue;
			}
			int z = 2;
			while (z + 2 <= zEnd) {
				int lv = l.y(z);
				if (l.y(z + 2) != lv) {
					z++;
					continue;
				}
				Garden.kitchenRows(d, a, z, b, z + 2, lv, Dirs.RIGHT);
				z += 4;
			}
		}
		// a scarecrow in the middle of a bed, the compost bin and the water butt at the back
		int sx = Math.max(x0, l.cx() - 2);
		int sz = 3;
		if (sz < zEnd) {
			try (var s = d.stage(StageKind.EXTERIOR)) {
				int y = l.y(sz);
				d.set(sx, y - 1, sz, Blocks.DIRT.defaultBlockState());
				d.set(sx, y, sz, d.mat(Role.FENCE).has(Form.FENCE) ? d.mat(Role.FENCE).fence() : Lamps.post(d));
				d.set(sx, y + 1, sz, Blocks.HAY_BLOCK.defaultBlockState());
				d.set(sx, y + 2, sz, States.facing(Blocks.CARVED_PUMPKIN, Dirs.FRONT));
				top = Math.max(top, y + 2);
			}
		}
		int bz = l.dp() - 2;
		try (var s = d.stage(StageKind.EXTERIOR)) {
			d.set(1, l.y(bz), bz, Blocks.COMPOSTER.defaultBlockState());
		}
		Garden.waterButt(d, l.w() - 2, l.y(bz), bz);
		// a fruit tree (an olive in the south) in the back corner
		if (l.w() >= 9) {
			int tx = l.w() - 3;
			int tz = l.dp() - 3;
			clearCell(d, tx, l.y(tz), tz);
			top = Math.max(top, Trees.tree(d, tx, l.y(tz), tz, Math.min(6, d.maxHeight() - l.y(tz)), Trees.kind(d), l.cx() + 1, l.dp() - 6, l.w() - 2,
				l.dp() - 2));
		}
		Benches.bench(d, l.cx() - 1 >= 1 ? l.cx() - 1 : l.cx(), l.y(bz), bz, Dirs.FRONT, Dirs.RIGHT, 1, false);
		return top;
	}

	/** A little tiled basin with a jet at (x, z) on level y (the riad's crossing). */
	private static void basin(Draft d, int x, int y, int z) {
		Mat tile = Paving.inlay(d);
		Mat stone = Manner.masonry(d);
		try (var s = d.stage(StageKind.EXTERIOR)) {
			for (int dz = -1; dz <= 1; dz++) {
				for (int dx = -1; dx <= 1; dx++) {
					if (dx == 0 && dz == 0) {
						d.set(x, y - 1, z, stone.full());
						d.set(x, y, z, stone.has(Form.WALL) ? stone.wall() : stone.full());
						d.set(x, y + 1, z, Blocks.WATER.defaultBlockState());
					} else if (dx == 0 || dz == 0) {
						d.set(x + dx, y - 1, z + dz, tile.full());
						d.set(x + dx, y, z + dz, Blocks.AIR.defaultBlockState());
						d.set(x + dx, y - 1, z + dz, Blocks.WATER.defaultBlockState());
						d.set(x + dx, y - 2, z + dz, tile.full());
					} else {
						d.set(x + dx, y, z + dz, tile.slab(SlabType.BOTTOM).orElse(tile.full()));
					}
				}
			}
		}
	}

	private static int riad(Lay l) {
		Draft d = l.d();
		int mid = l.dp() / 2;
		int top = l.y(0) + 3;
		// the cross walk
		Mat tile = Paving.inlay(d);
		Mat band = Manner.masonry(d);
		try (var s = d.stage(StageKind.EXTERIOR)) {
			for (int x = 1; x <= l.w() - 2; x++) {
				int y = l.y(mid);
				d.set(x, y - 1, mid, (x & 1) == 0 ? tile.full() : band.full());
			}
		}
		if (l.y(mid - 1) == l.y(mid) && l.y(mid + 1) == l.y(mid)) {
			basin(d, l.cx(), l.y(mid), mid);
		}
		// palms in two quarters, the dry garden in the others
		int[][] q = {{(1 + l.cx() - 1) / 2, (1 + mid - 1) / 2}, {(l.cx() + 1 + l.w() - 2) / 2, (mid + 1 + l.dp() - 2) / 2},
			{(l.cx() + 1 + l.w() - 2) / 2, (1 + mid - 1) / 2}, {(1 + l.cx() - 1) / 2, (mid + 1 + l.dp() - 2) / 2}};
		for (int i = 0; i < 4; i++) {
			int qx = q[i][0];
			int qz = q[i][1];
			int x0 = qx < l.cx() ? 1 : l.cx() + 1;
			int x1 = qx < l.cx() ? l.cx() - 1 : l.w() - 2;
			int z0 = qz < mid ? 1 : mid + 1;
			int z1 = qz < mid ? mid - 1 : l.dp() - 2;
			if (i < 2) {
				bands(l, x0, z0, x1, z1);
				clearCell(d, qx, l.y(qz), qz);
				try (var s = d.stage(StageKind.EXTERIOR)) {
					d.set(qx, l.y(qz) - 1, qz, Blocks.GRASS_BLOCK.defaultBlockState());
				}
				top = Math.max(top, Trees.tree(d, qx, l.y(qz), qz, Math.min(7, d.maxHeight() - l.y(qz)), Trees.kind(d), x0, z0, x1, z1));
			} else {
				bands(l, x0, z0, x1, z1);
			}
		}
		// stone benches along the cross walk, in the shade
		int by = l.y(mid);
		if (l.cx() - 2 >= 1) {
			clearCell(d, l.cx() - 2, by, mid + 1);
			Benches.bench(d, l.cx() - 2, by, mid + 1, Dirs.FRONT, Dirs.RIGHT, 1, false);
		}
		if (l.cx() + 2 <= l.w() - 2) {
			clearCell(d, l.cx() + 2, by, mid - 1);
			Benches.bench(d, l.cx() + 2, by, mid - 1, Dirs.BACK, Dirs.RIGHT, 1, false);
		}
		return top;
	}

	private static int cactus(Lay l) {
		Draft d = l.d();
		bands(l, 1, 1, l.cx() - 1, l.dp() - 2);
		bands(l, l.cx() + 1, 1, l.w() - 2, l.dp() - 4);
		// a ramada of poles and laths over a bench at the back right
		int z = l.dp() - 2;
		int y = l.y(z);
		int x0 = l.cx() + 1;
		int x1 = Math.min(l.w() - 2, x0 + 2);
		Mat post = d.mat(Role.FRAME);
		int top = y + 3;
		if (y + 3 < d.maxHeight()) {
			try (var s = d.stage(StageKind.EXTERIOR)) {
				for (int x = x0; x <= x1; x++) {
					for (int zz = z - 1; zz <= z; zz++) {
						d.remove(x, y, zz);
						d.remove(x, y + 1, zz);
					}
				}
				for (int[] c : new int[][]{{x0, z - 1}, {x1, z - 1}, {x0, z}, {x1, z}}) {
					d.set(c[0], y, c[1], post.fence());
					d.set(c[0], y + 1, c[1], post.fence());
					d.set(c[0], y + 2, c[1], post.log(Direction.Axis.Y).orElse(post.full()));
				}
				for (int x = x0; x <= x1; x++) {
					for (int zz = z - 1; zz <= z; zz++) {
						if (d.isEmpty(x, y + 2, zz)) {
							d.set(x, y + 2, zz, post.slab(SlabType.TOP));
						}
					}
				}
			}
			Benches.bench(d, x0 + 1, y, z, Dirs.FRONT, Dirs.RIGHT, 1, false);
		}
		return top;
	}

	private static int zen(Lay l) {
		Draft d = l.d();
		long seed = d.seed() ^ 0x2E27;
		int top = l.y(0) + 2;
		// raked gravel: lines of a paler stone across the gravel round the moss islands
		try (var s = d.stage(StageKind.EXTERIOR)) {
			for (int z = 1; z <= l.dp() - 2; z++) {
				for (int x = 1; x <= l.w() - 2; x++) {
					int y = l.y(z);
					if (x == l.cx() || d.get(x, y - 1, z) == null || !d.get(x, y - 1, z).is(Blocks.GRAVEL)) {
						continue;
					}
					double u = Noise.value(seed, x / 2.0, 0, z / 2.0);
					if (u > 0.72) {
						d.set(x, y - 1, z, Blocks.MOSS_BLOCK.defaultBlockState());
						if (Noise.unit(seed, x, 1, z) < 0.25) {
							d.set(x, y, z, Blocks.MOSS_CARPET.defaultBlockState());
						}
					} else if ((z & 1) == 0 && u < 0.6) {
						d.set(x, y - 1, z, States.block("minecraft:light_gray_concrete_powder", Blocks.GRAVEL).defaultBlockState());
					}
				}
			}
		}
		// weathered stones on the moss
		for (int k = 0; k < 3; k++) {
			int x = 1 + (int) (Noise.unit(seed, k, 5, 1) * (l.w() - 2));
			int z = 1 + (int) (Noise.unit(seed, k, 6, 1) * (l.dp() - 3));
			if (x == l.cx() || x < 1 || x > l.w() - 2) {
				continue;
			}
			try (var s = d.stage(StageKind.EXTERIOR)) {
				d.set(x, l.y(z), z, (k == 0 ? Blocks.MOSSY_COBBLESTONE : Blocks.ANDESITE).defaultBlockState());
			}
		}
		// a pool with lilies to one side of the stepping stones, a blossom tree and a lantern to the other
		int px = Math.max(2, l.cx() - 2);
		int pz = l.dp() / 2;
		if (l.y(pz) == l.y(pz - 1) && l.y(pz) == l.y(pz + 1) && px - 1 >= 1) {
			try (var s = d.stage(StageKind.EXTERIOR)) {
				for (int dz = -1; dz <= 1; dz++) {
					for (int dx = -1; dx <= 0; dx++) {
						int x = px + dx;
						int z = pz + dz;
						int y = l.y(z);
						d.set(x, y - 1, z, Blocks.WATER.defaultBlockState());
						d.set(x, y - 2, z, Blocks.MUD.defaultBlockState());
						d.remove(x, y, z);
						if (Noise.unit(seed, x, 7, z) < 0.35) {
							d.set(x, y, z, Blocks.LILY_PAD.defaultBlockState());
						}
					}
				}
			}
		}
		int tx = Math.min(l.w() - 2, l.cx() + 2);
		int tz = Math.max(2, l.dp() / 2 - 1);
		clearCell(d, tx, l.y(tz), tz);
		try (var s = d.stage(StageKind.EXTERIOR)) {
			d.set(tx, l.y(tz) - 1, tz, Blocks.MOSS_BLOCK.defaultBlockState());
		}
		top = Math.max(top, Trees.tree(d, tx, l.y(tz), tz, Math.min(6, d.maxHeight() - l.y(tz)), Trees.kind(d), l.cx() + 1, 1, l.w() - 2, l.dp() - 2));
		int lz = Math.min(l.dp() - 3, tz + 3);
		clearCell(d, tx, l.y(lz), lz);
		top = Math.max(top, Lamps.standard(d, tx, l.y(lz), lz, 4, Manner.LampKind.STONE_LANTERN, null));
		// the viewing deck at the back: boards a step up, a bench looking down the garden
		int z = l.dp() - 2;
		int y = l.y(z);
		Mat boards = d.mat(Role.FLOOR);
		try (var s = d.stage(StageKind.EXTERIOR)) {
			for (int x = Math.max(1, l.cx() - 1); x <= Math.min(l.w() - 2, l.cx() + 1); x++) {
				d.remove(x, y, z);
				d.set(x, y - 1, z, boards.full());
			}
		}
		Benches.bench(d, l.cx(), y, z, Dirs.FRONT, Dirs.RIGHT, 1, true);
		d.poi(PoiType.VIEWPOINT, l.cx(), y, z, Dirs.FRONT);
		return top;
	}

	private static int tropical(Lay l) {
		Draft d = l.d();
		bands(l, 1, 1, l.cx() - 1, l.dp() - 2);
		bands(l, l.cx() + 1, 1, l.w() - 2, l.dp() - 2);
		int top = l.y(0) + 2;
		for (int[] t : new int[][]{{2, l.dp() - 3}, {l.w() - 3, 2}}) {
			if (t[0] < 1 || t[0] > l.w() - 2 || t[0] == l.cx()) {
				continue;
			}
			clearCell(d, t[0], l.y(t[1]), t[1]);
			top = Math.max(top, Trees.tree(d, t[0], l.y(t[1]), t[1], Math.min(7, d.maxHeight() - l.y(t[1])), Trees.kind(d), 1, 1, l.w() - 2, l.dp() - 2));
		}
		Benches.bench(d, l.cx(), l.y(l.dp() - 2), l.dp() - 2, Dirs.FRONT, Dirs.RIGHT, 1, true);
		return top;
	}

	private static int rock(Lay l) {
		Draft d = l.d();
		bands(l, 1, 1, l.cx() - 1, l.dp() - 2);
		bands(l, l.cx() + 1, 1, l.w() - 2, l.dp() - 2);
		int top = l.y(0) + 2;
		int tx = Math.min(l.w() - 2, l.cx() + 2);
		int tz = l.dp() - 3;
		clearCell(d, tx, l.y(tz), tz);
		top = Math.max(top, Trees.tree(d, tx, l.y(tz), tz, Math.min(6, d.maxHeight() - l.y(tz)), Trees.kind(d), l.cx() + 1, 1, l.w() - 2, l.dp() - 2));
		int bz = l.dp() - 2;
		clearCell(d, l.cx() - 1, l.y(bz), bz);
		Benches.bench(d, Math.max(1, l.cx() - 1), l.y(bz), bz, Dirs.FRONT, Dirs.LEFT, 1, false);
		return top;
	}

	private static int bog(Lay l) {
		Draft d = l.d();
		long seed = d.seed() ^ 0xB06;
		try (var s = d.stage(StageKind.EXTERIOR)) {
			for (int z = 1; z <= l.dp() - 2; z++) {
				for (int x = 1; x <= l.w() - 2; x++) {
					if (Math.abs(x - l.cx()) <= 0) {
						continue;
					}
					int y = l.y(z);
					double u = Noise.value(seed, x / 2.0, 0, z / 2.0);
					if (u < 0.35 && l.y(z - 1) == y && z + 1 < l.dp() && l.y(z + 1) == y) {
						d.set(x, y - 1, z, Blocks.WATER.defaultBlockState());
						d.set(x, y - 2, z, Blocks.MUD.defaultBlockState());
						if (Noise.unit(seed, x, 3, z) < 0.3) {
							d.set(x, y, z, Blocks.LILY_PAD.defaultBlockState());
						}
					} else if (Noise.unit(seed, x, 4, z) < 0.45) {
						Planters.bed(d, x, y, z);
					}
				}
			}
		}
		int tx = Math.min(l.w() - 2, l.cx() + 2);
		int tz = l.dp() - 3;
		clearCell(d, tx, l.y(tz), tz);
		try (var s = d.stage(StageKind.EXTERIOR)) {
			d.set(tx, l.y(tz) - 1, tz, Blocks.GRASS_BLOCK.defaultBlockState());
		}
		int top = Trees.tree(d, tx, l.y(tz), tz, Math.min(7, d.maxHeight() - l.y(tz)), Trees.kind(d), 1, 1, l.w() - 2, l.dp() - 2);
		Benches.bench(d, l.cx(), l.y(l.dp() - 2), l.dp() - 2, Dirs.FRONT, Dirs.RIGHT, 1, false);
		return top;
	}

	private static int winter(Lay l) {
		Draft d = l.d();
		int top = l.y(0) + 2;
		for (int[] t : new int[][]{{2, 2}, {l.w() - 3, l.dp() - 3}, {2, l.dp() - 3}}) {
			if (t[0] < 1 || t[0] > l.w() - 2 || t[0] == l.cx() || t[1] < 1) {
				continue;
			}
			top = Math.max(top, Trees.tree(d, t[0], l.y(t[1]), t[1], Math.min(7, d.maxHeight() - l.y(t[1])), Trees.Kind.CONIFER, 1, 1, l.w() - 2, l.dp() - 2));
		}
		// a fire bowl in a ring of stones with log seats round it
		int fx = Math.min(l.w() - 3, l.cx() + 2);
		int fz = l.dp() / 2;
		if (fx > l.cx() && fx + 1 <= l.w() - 2) {
			int y = l.y(fz);
			try (var s = d.stage(StageKind.EXTERIOR)) {
				d.set(fx, y - 1, fz, Blocks.COBBLESTONE.defaultBlockState());
				d.set(fx, y, fz, States.lit(Blocks.CAMPFIRE, true).trySetValue(BlockStateProperties.SIGNAL_FIRE, false));
			}
			d.poi(PoiType.HEARTH, fx, y, fz - 1, Dirs.BACK);
			if (fz - 1 >= 1 && l.y(fz - 1) == y) {
				Benches.bench(d, fx, y, fz - 1 - 1 >= 1 ? fz - 2 : fz - 1, Dirs.BACK, Dirs.RIGHT, 1, false);
			}
			if (fz + 2 <= l.dp() - 2 && l.y(fz + 2) == y) {
				Benches.bench(d, fx, y, fz + 2, Dirs.FRONT, Dirs.RIGHT, 1, false);
			}
		}
		return top;
	}
}
