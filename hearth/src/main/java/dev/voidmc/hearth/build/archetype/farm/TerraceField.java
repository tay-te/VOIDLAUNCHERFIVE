package dev.voidmc.hearth.build.archetype.farm;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.ArchetypeCategory;
import dev.voidmc.hearth.api.ArchetypeInfo;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.PoiType;
import dev.voidmc.hearth.api.SettlementTier;
import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.Archetype;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Mat;
import dev.voidmc.hearth.build.draft.Noise;
import dev.voidmc.hearth.build.draft.States;
import dev.voidmc.hearth.build.kit.Furniture;
import dev.voidmc.hearth.build.kit.Garden;
import dev.voidmc.hearth.build.kit.homestead.Crops;
import dev.voidmc.hearth.build.kit.homestead.Sheds;
import dev.voidmc.hearth.build.kit.homestead.Spots;
import dev.voidmc.hearth.build.kit.homestead.Terraces;
import dev.voidmc.hearth.build.kit.homestead.Trees;
import dev.voidmc.hearth.build.style.Form;
import dev.voidmc.hearth.build.style.Role;
import dev.voidmc.hearth.build.terrain.Groundwork;
import java.util.List;
import java.util.Set;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * {@code hearth:farm/terrace_field}: a hillside worked in narrow level terraces, each held by a dry-stone retaining wall
 * capped here and there with a flat stone, a stepped path climbing one side through every wall. What the terraces grow
 * follows the land's people: vines on posts and a row of olives in the Mediterranean, tea bushes in close rows in the
 * karst, potatoes, grain and hay meadow with a drying rack in the mountains, the culture's crops in watered beds
 * elsewhere. A tool hut stands on the lowest terrace by the gate.
 *
 * <p>Job sites (FARMER): the hut's compost bin, a stand on the path beside every terrace.
 *
 * <p>Params (all optional): {@code crop} (string: vines, tea, meadow, beds: what the terraces grow), {@code hut} (boolean).
 */
public final class TerraceField implements Archetype {
	public static final ArchetypeInfo INFO = new ArchetypeInfo(Hearth.id("farm/terrace_field"), ArchetypeCategory.FARM, Set.of(JobType.FARMER), 0,
		9, 21, 7, 13, 7, 14, SettlementTier.HAMLET, Set.of(), Set.of(ArchetypeInfo.SiteFlag.HILLSIDE, ArchetypeInfo.SiteFlag.OUTSKIRTS), List.of(), 0, 1.0);

	/** What a terrace grows. */
	enum Crop {
		VINES, TEA, MEADOW, BEDS
	}

	@Override
	public ArchetypeInfo info() {
		return INFO;
	}

	@Override
	public void draw(Draft d) {
		int w = d.frontage();
		int dp = d.depth();
		boolean leftSide = d.chance(0.5);
		int access = leftSide ? 1 : w - 2;
		Terraces t = Terraces.plan(d, 0, 0, w - 1, dp - 1, 2, 5, 3, access, w / 2);
		int path = t.alongX ? 1 : access;
		int gateX = t.alongX ? Math.clamp(w / 2, 2, w - 3) : access;
		d.entrance(gateX, Terraces.street(d, gateX), -1);
		t.shape(d, Terraces.Edge.DRYSTONE, path);
		t.accessPath(d);
		// the gate cell
		int glv = t.level(gateX, 0);
		try (var s = d.stage(StageKind.EXTERIOR)) {
			d.set(gateX, glv - 1, 0, d.mat(Role.PATH).full());
			d.set(gateX, glv, 0, Blocks.AIR.defaultBlockState());
		}
		Crop crop = crop(d);
		Direction.Axis rows = t.alongX ? Direction.Axis.Z : Direction.Axis.X;
		int bi = 0;
		for (Terraces.Band b : t.bands) {
			int from = Math.max(1, t.plantFrom(b));
			int to = Math.min(t.alongX ? w - 2 : dp - 2, t.plantTo(b));
			if (to < from) {
				continue;
			}
			int c0 = t.acrossMin() + 1;
			int c1 = t.acrossMax() - 1;
			// what this terrace grows (the meadow and beds cultures alternate with grain now and then)
			Crop here = crop == Crop.MEADOW && (bi & 1) == 1 ? Crop.BEDS : crop;
			switch (here) {
				case VINES -> vines(d, t, b, from, to, c0, c1, path);
				case TEA -> tea(d, t, b, from, to, c0, c1, path);
				case MEADOW -> meadow(d, t, b, from, to, c0, c1, path);
				default -> beds(d, t, b, from, to, c0, c1, path, rows, bi);
			}
			bi++;
		}
		t.coping(d);
		// the tool hut by the gate; where it does not fit (a slope's narrow bottom terrace), the bin and the chest stand by
		// the path up, so the field always has its job block and its store
		boolean hut = d.param("hut", w >= 11) && t.bands.size() >= 2 && hut(d, t, w, leftSide);
		if (!hut) {
			PathStore.beside(d, t, path, JobType.FARMER, List.of(new Object[]{"minecraft:iron_hoe", 1}, new Object[]{"minecraft:wheat_seeds", 12}));
		}
		// stands on the path beside each terrace
		Direction into = t.alongX ? Dirs.BACK : leftSide ? Dirs.RIGHT : Dirs.LEFT;
		for (Terraces.Band b : t.bands) {
			int[] xz = t.cell(Math.max(t.plantFrom(b), Math.min(t.plantTo(b), (b.from() + b.to()) / 2)), path);
			Spots.onGround(d, PoiType.JOB_SITE, xz[0], b.level(), xz[1], into, JobType.FARMER);
		}
		// head room over every step through the banks
		StepRoom.clear(d, t.minLevel() - 3, t.maxLevel() + 1);
		Groundwork.clear(d, 0, 0, w - 1, dp - 1, Math.min(d.maxHeight() - 1, t.maxLevel() + 7));
	}

	/** What the culture grows on terraces: vines and olives where olives grow, tea in the karst, meadow and grain in the mountains. */
	private static Crop crop(Draft d) {
		String p = d.param("crop", "");
		for (Crop c : Crop.values()) {
			if (c.name().equalsIgnoreCase(p)) {
				return c;
			}
		}
		Trees.Kind k = Trees.kind(d);
		if (k == Trees.Kind.OLIVE) {
			return d.chance(0.7) ? Crop.VINES : Crop.BEDS;
		}
		if (k == Trees.Kind.BLOSSOM) {
			return d.chance(0.7) ? Crop.TEA : Crop.BEDS;
		}
		if (d.site().temperature() < 0.45F || d.mat(Role.TREE_LEAVES).main(Form.FULL) == Blocks.SPRUCE_LEAVES) {
			return d.chance(0.6) ? Crop.MEADOW : Crop.BEDS;
		}
		return Crop.BEDS;
	}

	private static boolean plantable(Draft d, int x, int y, int z) {
		BlockState here = d.get(x, y, z);
		BlockState below = d.get(x, y - 1, z);
		return (here == null || here.isAir()) && (below == null || !(below.getBlock() instanceof net.minecraft.world.level.block.StairBlock));
	}

	/**
	 * Vines on posts along the terrace (a post every other cell carrying leaves over its head, the rows two apart),
	 * grass between them; an olive at the terrace's end where it is wide enough.
	 */
	private static void vines(Draft d, Terraces t, Terraces.Band b, int from, int to, int c0, int c1, int path) {
		Mat post = d.mat(Role.FENCE).has(Form.FENCE) && d.mat(Role.FENCE).main(Form.FENCE) != null
			&& !String.valueOf(d.mat(Role.FENCE).main(Form.FENCE)).contains("quartz") ? d.mat(Role.FENCE) : d.mat(Role.FRAME);
		int y = b.level();
		try (var s = d.stage(StageKind.EXTERIOR)) {
			for (int a = from; a <= to; a++) {
				boolean vineRow = ((a - from) % 2) == 0 && a != to || to == from;
				for (int c = c0; c <= c1; c++) {
					if (c == path) {
						continue;
					}
					int[] xz = t.cell(a, c);
					if (!plantable(d, xz[0], y, xz[1])) {
						continue;
					}
					d.set(xz[0], y - 1, xz[1], Blocks.GRASS_BLOCK.defaultBlockState());
					if (vineRow && y + 2 < d.maxHeight()) {
						boolean isPost = (c & 1) == 0;
						if (isPost) {
							d.set(xz[0], y, xz[1], post.fence());
						} else {
							d.set(xz[0], y, xz[1], Blocks.SWEET_BERRY_BUSH.defaultBlockState()
								.setValue(net.minecraft.world.level.block.SweetBerryBushBlock.AGE, 2));
						}
						Block leaf = Noise.unit(d.seed() ^ 0x71E, xz[0], 0, xz[1]) < 0.75 ? Blocks.OAK_LEAVES : Blocks.AZALEA_LEAVES;
						if (isPost) {
							d.set(xz[0], y + 1, xz[1], States.leaves(leaf));
						}
					} else if (Noise.unit(d.seed() ^ 0x71F, xz[0], 0, xz[1]) < 0.3) {
						d.set(xz[0], y, xz[1], Blocks.SHORT_GRASS.defaultBlockState());
					}
				}
			}
		}
	}

	/** Tea: close rows of clipped bushes (azalea) along the terrace, a picker's walk between every two rows. */
	private static void tea(Draft d, Terraces t, Terraces.Band b, int from, int to, int c0, int c1, int path) {
		int y = b.level();
		try (var s = d.stage(StageKind.EXTERIOR)) {
			for (int a = from; a <= to; a++) {
				boolean bushRow = ((a - from) % 3) != 2;
				for (int c = c0; c <= c1; c++) {
					if (c == path) {
						continue;
					}
					int[] xz = t.cell(a, c);
					if (!plantable(d, xz[0], y, xz[1])) {
						continue;
					}
					d.set(xz[0], y - 1, xz[1], Blocks.GRASS_BLOCK.defaultBlockState());
					if (bushRow && y + 1 < d.maxHeight()) {
						double u = Noise.unit(d.seed() ^ 0x7EA, xz[0], 0, xz[1]);
						d.set(xz[0], y, xz[1], States.leaves(u < 0.12 ? Blocks.FLOWERING_AZALEA_LEAVES : Blocks.AZALEA_LEAVES));
					} else {
						d.set(xz[0], y - 1, xz[1], d.mat(Role.PATH).full());
					}
				}
			}
		}
	}

	/** Hay meadow: long grass and flowers, a drying rack of fence with hay over it. */
	private static void meadow(Draft d, Terraces t, Terraces.Band b, int from, int to, int c0, int c1, int path) {
		int y = b.level();
		boolean rack = false;
		try (var s = d.stage(StageKind.EXTERIOR)) {
			for (int a = from; a <= to; a++) {
				for (int c = c0; c <= c1; c++) {
					if (c == path) {
						continue;
					}
					int[] xz = t.cell(a, c);
					if (!plantable(d, xz[0], y, xz[1])) {
						continue;
					}
					d.set(xz[0], y - 1, xz[1], Blocks.GRASS_BLOCK.defaultBlockState());
					double u = Noise.unit(d.seed() ^ 0x3E4, xz[0], 0, xz[1]);
					if (!rack && a == (from + to) / 2 && c == (c0 + c1) / 2 && y + 2 < d.maxHeight()) {
						// the drying rack: two posts with hay laid over the rail between them
						d.set(xz[0], y, xz[1], d.mat(Role.FENCE).has(Form.FENCE) ? d.mat(Role.FENCE).fence() : d.mat(Role.FRAME).fence());
						d.set(xz[0], y + 1, xz[1], Blocks.HAY_BLOCK.defaultBlockState());
						rack = true;
					} else if (u < 0.08 && y + 2 < d.maxHeight()) {
						Garden.flower(d, xz[0], y, xz[1]);
					} else if (u < 0.5) {
						d.set(xz[0], y, xz[1], Blocks.SHORT_GRASS.defaultBlockState());
					}
				}
			}
		}
	}

	/** The culture's crops in a watered bed along the terrace (a single channel down a wide one). */
	private static void beds(Draft d, Terraces t, Terraces.Band b, int from, int to, int c0, int c1, int path, Direction.Axis rows, int bi) {
		int y = b.level();
		// keep the path column out of the bed: plant either side of it
		int ca = path <= c0 ? c0 + 1 : c0;
		int cb = path >= c1 ? c1 - 1 : c1;
		int[] p0 = t.cell(from, ca);
		int[] p1 = t.cell(to, cb);
		if (Math.abs(p1[0] - p0[0]) < 1 && Math.abs(p1[1] - p0[1]) < 1) {
			return;
		}
		Crops.bed(d, Math.min(p0[0], p1[0]), Math.min(p0[1], p1[1]), Math.max(p0[0], p1[0]), Math.max(p0[1], p1[1]), y, rows, 0x7E44 + bi);
	}

	/** A tool hut on the lowest terrace by the gate: a little gabled shed with the compost bin, the tools and seed. */
	private static boolean hut(Draft d, Terraces t, int w, boolean leftSide) {
		Terraces.Band low = t.bands.get(0);
		for (Terraces.Band b : t.bands) {
			if (b.level() < low.level()) {
				low = b;
			}
		}
		int x0 = leftSide ? w - 5 : 1;
		int x1 = x0 + 3;
		// clear of the path, which runs along the front row when the terraces step across the plot
		int z0 = t.alongX ? 2 : 1;
		int z1 = z0 + 2;
		for (int z = z0; z <= z1 + 1; z++) {
			for (int x = x0; x <= x1; x++) {
				if (t.band(x, z) != low || t.along(x, z) < t.plantFrom(low) || t.along(x, z) > t.plantTo(low)) {
					return false;
				}
			}
		}
		int y = low.level();
		if (y + 6 >= d.maxHeight()) {
			return false;
		}
		for (int z = z0; z <= z1; z++) {
			for (int x = x0; x <= x1; x++) {
				for (int yy = y; yy <= y + 4; yy++) {
					d.remove(x, yy, z);
				}
			}
		}
		Sheds.lean(d, x0, z0, x1, z1, y, Dirs.BACK, 3, false);
		Furniture.workstation(d, JobType.FARMER, x0 + 1, y, z0 + 1, Dirs.BACK);
		Furniture.chest(d, x1 - 1, y, z0 + 1, Dirs.BACK, List.of(new Object[]{"minecraft:iron_hoe", 1}, new Object[]{"minecraft:wheat_seeds", 12}));
		return true;
	}
}
