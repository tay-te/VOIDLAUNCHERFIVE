package dev.voidmc.hearth.build.kit.deep;

import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Mat;
import dev.voidmc.hearth.build.draft.Noise;
import dev.voidmc.hearth.build.draft.Paint;
import dev.voidmc.hearth.build.draft.States;
import dev.voidmc.hearth.build.style.Form;
import dev.voidmc.hearth.build.style.Role;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * Masonry of the deep town (LIVING_WORLD.md §8.2; the style bible's deep region): the parts a mason cuts for a house in
 * a realm — piers standing proud of the wall, cornices, arched door heads with a fanlight and a keystone of the realm's
 * glow, framed windows with sills and lintels, parapets, planters of glowcaps and moss, lamp posts, flights of stone
 * steps. Every part reads its materials from the style's roles (the deep region dresses them: deepslate brick walls,
 * polished tuff dressings, deepslate tile copings, amber accents), so a realm's own stone (the plot's {@code local})
 * comes through. Parts check the plot's bounds through the draft and nothing else: the archetype knows its plan.
 */
public final class Rock {
	private Rock() {
	}

	/** A dressing material: the wall accent (polished tuff, tuff bricks), else the plinth. */
	public static Mat dressing(Draft d) {
		Mat a = d.mat(Role.WALL_ACCENT);
		return a.has(Form.STAIRS) ? a : d.mat(Role.PLINTH);
	}

	/** A stair of a material, or its slab, or the full block. */
	public static Paint stair(Mat m, Direction facing, Half half) {
		return m.stairsOrSlab(facing, half);
	}

	/**
	 * A pier standing proud of a wall at (x, z) from {@code y0} to {@code y1} inclusive: a plinth block at its foot, the
	 * dressing up the shaft, a capital (an upside-down stair turned to the wall) at the top if {@code capital}.
	 */
	public static void pier(Draft d, int x, int z, int y0, int y1, Direction toWall, boolean capital) {
		Mat dress = dressing(d);
		Mat plinth = d.mat(Role.PLINTH);
		try (var s = d.stage(StageKind.WALLS)) {
			for (int y = y0; y <= y1; y++) {
				if (y == y0) {
					d.set(x, y, z, plinth.full());
				} else if (y == y1 && capital) {
					d.set(x, y, z, dress.stairsOrSlab(toWall, Half.TOP));
				} else {
					d.set(x, y, z, dress.full());
				}
			}
		}
	}

	/** A cornice along a run of cells at height y: upside-down stairs whose backs are to the wall ({@code toWall}). */
	public static void cornice(Draft d, int x0, int z0, int x1, int z1, int y, Direction toWall, Mat m) {
		try (var s = d.stage(StageKind.WALLS)) {
			for (int[] c : line(x0, z0, x1, z1)) {
				if (d.allowed(c[0], y, c[1])) {
					d.set(c[0], y, c[1], m.stairsOrSlab(toWall, Half.TOP));
				}
			}
		}
	}

	/**
	 * An arched doorway head in a wall facing {@code out} over a one-wide door at (x, y0, z): dressed jambs either side,
	 * shoulders of upside-down stairs at {@code y0 + 2} curving into a fanlight over the door (a pane, iron bars or a
	 * lantern-lit grille), voussoirs over them and a keystone at {@code y0 + 3}: the realm's glow ({@code glowKey}) or the
	 * carved stone. Needs the wall to stand four high over the door's step.
	 */
	public static void archedDoorHead(Draft d, int x, int y0, int z, Direction out, boolean glowKey, Glow g) {
		Direction along = Dirs.right(out.getOpposite());
		Mat dress = dressing(d);
		int lx = x - Dirs.dx(along);
		int lz = z - Dirs.dz(along);
		int rx = x + Dirs.dx(along);
		int rz = z + Dirs.dz(along);
		try (var s = d.stage(StageKind.WALLS)) {
			for (int y = y0; y < y0 + 2; y++) {
				d.set(lx, y, lz, dress.full());
				d.set(rx, y, rz, dress.full());
			}
			d.set(lx, y0 + 2, lz, dress.stairsOrSlab(along.getOpposite(), Half.TOP));
			d.set(rx, y0 + 2, rz, dress.stairsOrSlab(along, Half.TOP));
			d.set(lx, y0 + 3, lz, dress.full());
			d.set(rx, y0 + 3, rz, dress.full());
			if (glowKey) {
				d.set(x, y0 + 3, z, g.accent.defaultBlockState());
			} else {
				d.set(x, y0 + 3, z, carved(d).defaultBlockState());
			}
		}
		try (var s = d.stage(StageKind.OPENINGS)) {
			d.set(x, y0 + 2, z, fanlight(d));
		}
	}

	/** The fill of an arch's head over a door: a pane of the house's glass, or iron bars. */
	public static BlockState fanlight(Draft d) {
		Mat glass = d.mat(Role.GLASS);
		if (Noise.unit(d.seed() ^ 0xFA11, 0, 0, 0) < 0.35) {
			return Blocks.IRON_BARS.defaultBlockState();
		}
		BlockState pane = glass.pane().at(0, 0, 0);
		return pane == null ? Blocks.GLASS_PANE.defaultBlockState() : pane;
	}

	/** A carved block for keystones and capitals: chiselled tuff or deepslate, by the plot. */
	public static Block carved(Draft d) {
		Block a = d.mat(Role.WALL_ACCENT).main(Form.FULL);
		if (a == Blocks.POLISHED_TUFF || a == Blocks.TUFF_BRICKS || a == Blocks.TUFF) {
			return Blocks.CHISELED_TUFF;
		}
		if (a != null && a.toString().contains("deepslate")) {
			return Blocks.CHISELED_DEEPSLATE;
		}
		return Noise.unit(d.seed() ^ 0xCA2, 1, 1, 1) < 0.5 ? Blocks.CHISELED_TUFF : Blocks.CHISELED_DEEPSLATE;
	}

	/**
	 * A window in a wall facing {@code out}: panes of the house's glass ({@code width} × {@code height} from (x, y, z)
	 * along the wall to the right as seen from outside), a dressed lintel over it, and a sill under it standing out a
	 * cell (if that cell is free and in the plot). {@code arched}: the head's corner cells become stair shoulders.
	 */
	public static void window(Draft d, int x, int y, int z, Direction out, int width, int height, boolean arched) {
		Direction along = Dirs.right(out.getOpposite());
		Mat glass = d.mat(Role.GLASS);
		Mat dress = dressing(d);
		Mat sill = d.mat(Role.WINDOW).has(Form.STAIRS) ? d.mat(Role.WINDOW) : dress;
		try (var s = d.stage(StageKind.OPENINGS)) {
			for (int c = 0; c < width; c++) {
				int px = x + Dirs.dx(along) * c;
				int pz = z + Dirs.dz(along) * c;
				for (int yy = y; yy < y + height; yy++) {
					d.set(px, yy, pz, glass.pane());
				}
				int ox = px + Dirs.dx(out);
				int oz = pz + Dirs.dz(out);
				if (inPlot(d, ox, oz) && d.isAbsent(ox, y - 1, oz) && d.allowed(ox, y - 1, oz)) {
					d.set(ox, y - 1, oz, sill.stairsOrSlab(out.getOpposite(), Half.TOP));
				}
			}
		}
		try (var s = d.stage(StageKind.WALLS)) {
			int top = y + height;
			for (int c = 0; c < width; c++) {
				int px = x + Dirs.dx(along) * c;
				int pz = z + Dirs.dz(along) * c;
				if (d.allowed(px, top, pz)) {
					d.set(px, top, pz, dress.full());
				}
			}
			if (arched && width >= 2 && height >= 2) {
				d.set(x, top - 1, z, dress.stairsOrSlab(along.getOpposite(), Half.TOP));
				int ex = x + Dirs.dx(along) * (width - 1);
				int ez = z + Dirs.dz(along) * (width - 1);
				d.set(ex, top - 1, ez, dress.stairsOrSlab(along, Half.TOP));
			}
		}
	}

	/**
	 * A parapet along a run of cells at height y: the wall's own stone as a low wall, a dressed full block at each end
	 * (and every {@code postEvery} cells) where a lamp may stand.
	 */
	public static void parapet(Draft d, int x0, int z0, int x1, int z1, int y, int postEvery) {
		Mat wall = d.mat(Role.WALL);
		Mat dress = dressing(d);
		java.util.List<int[]> cells = line(x0, z0, x1, z1);
		try (var s = d.stage(StageKind.ROOF)) {
			for (int i = 0; i < cells.size(); i++) {
				int[] c = cells.get(i);
				if (!d.allowed(c[0], y, c[1])) {
					continue;
				}
				boolean post = i == 0 || i == cells.size() - 1 || postEvery > 0 && i % postEvery == 0;
				// a guard a person cannot step up onto: a wall block (a block and a half), or a block under a capping slab;
				// where there is no room over it for the cap (the plot's top), a wall block or the railing's fence
				boolean room = d.allowed(c[0], y + 1, c[1]);
				Paint low = wall.has(Form.WALL) ? wall.wall() : dress.has(Form.WALL) ? dress.wall()
					: (d.mat(Role.RAILING).has(Form.FENCE) ? d.mat(Role.RAILING) : d.mat(Role.FURNITURE)).fence();
				if (post && room) {
					d.set(c[0], y, c[1], dress.full());
					d.set(c[0], y + 1, c[1], dress.slab(SlabType.BOTTOM));
				} else if (wall.has(Form.WALL) || !room) {
					d.set(c[0], y, c[1], low);
				} else {
					d.set(c[0], y, c[1], dress.full());
					d.set(c[0], y + 1, c[1], dress.slab(SlabType.BOTTOM));
				}
			}
		}
	}

	/**
	 * A planter at (x, y, z): the realm's soil in a stone box (the box's edge is whatever stands round it), and a plant of
	 * the realm on it at y + 1. Returns false if the plant could not stand there.
	 */
	public static void planter(Draft d, int x, int y, int z, Glow g) {
		try (var s = d.stage(StageKind.EXTERIOR)) {
			d.set(x, y, z, g.soil.defaultBlockState());
			Block plant = g.plant(d.seed(), x, y, z);
			if (!Glow.grows(g.soil, plant)) {
				plant = Blocks.BROWN_MUSHROOM;
			}
			if (d.allowed(x, y + 1, z) && d.isAbsent(x, y + 1, z)) {
				d.set(x, y + 1, z, Glow.plantState(plant));
			}
		}
	}

	/**
	 * A lamp post standing at (x, y, z) (y the standing level): a dressed stone post two high and a lantern on it; or,
	 * {@code glowCap}, the realm's glowing block capped with a slab. Three high in all.
	 */
	public static void lampPost(Draft d, int x, int y, int z, boolean glowCap, Glow g) {
		Mat dress = dressing(d);
		Mat wall = d.mat(Role.WALL);
		try (var s = d.stage(StageKind.EXTERIOR)) {
			d.set(x, y, z, dress.full());
			d.set(x, y + 1, z, wall.has(Form.WALL) ? wall.wall() : dress.full());
			if (glowCap && d.allowed(x, y + 3, z)) {
				d.set(x, y + 2, z, g.lamp.defaultBlockState());
				d.set(x, y + 3, z, dress.slab(SlabType.BOTTOM));
			} else {
				d.set(x, y + 2, z, States.lantern(Blocks.LANTERN, false));
			}
		}
	}

	/**
	 * A straight flight of stone steps: {@code rise} steps from (x, y, z) climbing toward {@code dir} (the first step's
	 * top is at y, standing at y + 1 above it... as {@code kit.Stairs.straight}: the first step stands in the cell
	 * (x, y, z)), {@code width} wide to the right of {@code dir}, a solid string under each step down to {@code footY},
	 * three of headroom cleared over each. Returns the cell beyond the top step at the top level.
	 */
	public static int[] steps(Draft d, int x, int y, int z, Direction dir, int rise, int width, int footY, Mat m) {
		Direction across = Dirs.right(dir);
		BlockState air = Blocks.AIR.defaultBlockState();
		Mat fill = d.mat(Role.FOUNDATION);
		try (var s = d.stage(StageKind.FLOORS)) {
			for (int i = 0; i < rise; i++) {
				for (int k = 0; k < width; k++) {
					int px = x + Dirs.dx(dir) * i + Dirs.dx(across) * k;
					int pz = z + Dirs.dz(dir) * i + Dirs.dz(across) * k;
					int py = y + i;
					if (!d.allowed(px, py, pz)) {
						continue;
					}
					d.set(px, py, pz, m.stairsOrSlab(dir, Half.BOTTOM));
					for (int h = 1; h <= 3; h++) {
						if (d.allowed(px, py + h, pz)) {
							d.set(px, py + h, pz, air);
						}
					}
					for (int yy = py - 1; yy >= footY; yy--) {
						if (!d.isAbsent(px, yy, pz) && !d.isEmpty(px, yy, pz)) {
							break;
						}
						d.set(px, yy, pz, fill.full());
					}
				}
			}
		}
		return new int[]{x + Dirs.dx(dir) * rise, y + rise, z + Dirs.dz(dir) * rise};
	}

	/** A bench of the furniture's wood along {@code run} from (x, y, z), its back to {@code back}. Registers seats. */
	public static void bench(Draft d, int x, int y, int z, Direction back, Direction run, int length) {
		Mat wood = d.mat(Role.FURNITURE);
		try (var s = d.stage(StageKind.EXTERIOR)) {
			for (int i = 0; i < length; i++) {
				int px = x + Dirs.dx(run) * i;
				int pz = z + Dirs.dz(run) * i;
				d.set(px, y, pz, wood.stairsOrSlab(back, Half.BOTTOM));
			}
		}
		d.poi(dev.voidmc.hearth.api.PoiType.SEAT, x, y, z, back.getOpposite());
	}

	/**
	 * Sheathes in copper what a roof material drew in a box: every block of {@code roof}'s materials there (full blocks,
	 * stairs, slabs) becomes the same shape in waxed copper of the given weathering ({@code weathered}, {@code oxidized},
	 * {@code exposed}), so a roof rasterised by the kit in the style's tiles is laid in copper instead. The deep town's
	 * hall and its towers wear copper (a mining town's wealth, gone green in the realm's damp).
	 */
	public static void copper(Draft d, int x0, int y0, int z0, int x1, int y1, int z1, Mat roof, String weathering) {
		java.util.Set<Block> mine = new java.util.HashSet<>();
		for (var e : roof.palette().entries()) {
			for (Form f : new Form[]{Form.FULL, Form.STAIRS, Form.SLAB}) {
				Block b = e.material().block(f);
				if (b != null) {
					mine.add(b);
				}
			}
		}
		// copper weathers in patches: the roof's own stage, streaks a stage on where the drip runs
		String[] stages = {"", "exposed", "weathered", "oxidized"};
		int k = Math.max(0, java.util.Arrays.asList(stages).indexOf(weathering));
		Block[][] forms = new Block[stages.length][];
		for (int i = 0; i < stages.length; i++) {
			String w = stages[i].isEmpty() ? "" : stages[i] + "_";
			forms[i] = new Block[]{States.block("minecraft:waxed_" + w + "cut_copper", Blocks.AIR), States.block("minecraft:waxed_" + w + "cut_copper_stairs",
				Blocks.AIR), States.block("minecraft:waxed_" + w + "cut_copper_slab", Blocks.AIR)};
			for (Block b : forms[i]) {
				if (b == Blocks.AIR) {
					return;
				}
			}
		}
		long seed = Noise.hash(d.seed(), 0xC0BB);
		for (int y = y0; y <= y1; y++) {
			for (int z = z0; z <= z1; z++) {
				for (int x = x0; x <= x1; x++) {
					BlockState s = d.get(x, y, z);
					if (s == null || !mine.contains(s.getBlock())) {
						continue;
					}
					double u = Noise.value(seed, x / 3.0, y / 2.0, z / 3.0) + 0.25 * (Noise.unit(seed, x, y, z) - 0.5);
					// streaks a stage further on; a fully green roof keeps patches of the weathered stage (never back to the
					// bright metal, which reads as rust spots)
					int stage = Math.clamp(k + (u > 0.68 ? 1 : u < 0.3 && k == 3 ? -1 : 0), 0, stages.length - 1);
					Block full = forms[stage][0];
					Block stairs = forms[stage][1];
					Block slab = forms[stage][2];
					BlockState n;
					if (s.getBlock() instanceof net.minecraft.world.level.block.StairBlock) {
						n = stairs.withPropertiesOf(s);
					} else if (s.getBlock() instanceof net.minecraft.world.level.block.SlabBlock) {
						n = slab.withPropertiesOf(s);
					} else {
						n = full.defaultBlockState();
					}
					d.restate(x, y, z, n);
				}
			}
		}
	}

	/** The copper's weathering for a building: weathered or oxidized, the odd roof still bright. */
	public static String weathering(Draft d) {
		double u = Noise.unit(d.seed() ^ 0xC0FFE, 3, 1, 4);
		return u < 0.5 ? "weathered" : u < 0.85 ? "oxidized" : "exposed";
	}

	/** Whether a local column is inside the plot's footprint. */
	public static boolean inPlot(Draft d, int x, int z) {
		return x >= 0 && x < d.frontage() && z >= 0 && z < d.depth();
	}

	/** The cells of a straight run (inclusive), in order from the first. */
	public static java.util.List<int[]> line(int x0, int z0, int x1, int z1) {
		java.util.List<int[]> out = new java.util.ArrayList<>();
		int n = Math.max(Math.abs(x1 - x0), Math.abs(z1 - z0));
		for (int i = 0; i <= n; i++) {
			out.add(new int[]{x0 + Integer.signum(x1 - x0) * i, z0 + Integer.signum(z1 - z0) * i});
		}
		return out;
	}
}
