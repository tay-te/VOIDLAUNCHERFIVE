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
import dev.voidmc.hearth.build.draft.Noise;
import dev.voidmc.hearth.build.draft.States;
import dev.voidmc.hearth.build.kit.Furniture;
import dev.voidmc.hearth.build.kit.Garden;
import dev.voidmc.hearth.build.kit.Roofs;
import dev.voidmc.hearth.build.kit.homestead.Bounds;
import dev.voidmc.hearth.build.kit.homestead.Sheds;
import dev.voidmc.hearth.build.kit.homestead.Land;
import dev.voidmc.hearth.build.kit.homestead.Spots;
import dev.voidmc.hearth.build.kit.homestead.Terraces;
import dev.voidmc.hearth.build.kit.homestead.Trees;
import dev.voidmc.hearth.build.kit.homestead.Yard;
import dev.voidmc.hearth.build.style.Form;
import dev.voidmc.hearth.build.style.Role;
import dev.voidmc.hearth.build.terrain.Groundwork;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * {@code hearth:farm/pasture}: grazing for the settlement's beasts, the land left as it lies (fences ride over the
 * swells, a pond lies in the lowest hollow of a big one). A gate on the street opens onto ground trampled bare round a
 * water trough, the herder's feed cauldron beside it; an open-fronted field shelter along the back holds hay bales and a
 * hay rack; a shade tree stands in the grass, which grows long and flowery away from the gate. The boundary is the
 * culture's, stock-proof: dry-stone on the moor and by the sea, hedges in the vale and the karst, rails elsewhere. No
 * animals: the folk bring their own.
 *
 * <p>Job sites (HERDER): the feed cauldron by the trough, the shelter's hay rack, a stand at the gate and one out in
 * the grass. Storage: the shelter's chest.
 *
 * <p>Params (all optional): {@code shelter} (boolean, default by size), {@code pond} (boolean, default by size and
 * ground), {@code boundary} (string: drystone, hedge, rails, bund, none).
 */
public final class Pasture implements Archetype {
	public static final ArchetypeInfo INFO = new ArchetypeInfo(Hearth.id("farm/pasture"), ArchetypeCategory.FARM, Set.of(JobType.HERDER), 0,
		13, 25, 13, 25, 6, 10, SettlementTier.HAMLET, Set.of(), Set.of(ArchetypeInfo.SiteFlag.OUTSKIRTS), List.of(), 0, 0.8);

	@Override
	public ArchetypeInfo info() {
		return INFO;
	}

	@Override
	public void draw(Draft d) {
		int w = d.frontage();
		int dp = d.depth();
		int gateX = Math.clamp(w / 2 + d.between(-2, 2), 2, w - 3);
		d.entrance(gateX, Terraces.street(d, gateX), -1);
		// the ground where it lies: only water filled where the boundary or the shelter must stand
		Land.mendFill(d, 0, 0, w - 1, dp - 1);
		// trampled ground from the gate to the trough
		int troughZ = Math.min(4, dp / 3);
		boolean troughLeft = gateX > w / 2;
		int tx = troughLeft ? gateX - 4 : gateX + 2;
		tx = Math.clamp(tx, 2, w - 5);
		trample(d, gateX, tx, troughZ, w, dp);
		int ty = Groundwork.surface(d, tx, troughZ);
		boolean troughFlat = true;
		for (int k = 0; k < 3; k++) {
			troughFlat &= Groundwork.surface(d, tx + k, troughZ) == ty && Groundwork.surface(d, tx + k, troughZ + 1) == ty;
		}
		if (troughFlat) {
			Yard.trough(d, tx, ty, troughZ, Dirs.RIGHT, 3);
			int cx = troughLeft ? tx + 3 : tx - 1;
			if (Groundwork.surface(d, cx, troughZ) == ty) {
				Furniture.workstation(d, JobType.HERDER, cx, ty, troughZ, Dirs.FRONT);
			} else {
				Spots.job(d, tx + 1, troughZ - 2, Dirs.BACK, JobType.HERDER);
			}
		} else {
			Spots.job(d, gateX, 2, Dirs.BACK, JobType.HERDER);
		}
		// the shelter along the back, open to the field
		int[] shelter = null;
		if (d.param("shelter", w * dp >= 200 || d.chance(0.6))) {
			shelter = shelter(d, w, dp);
		}
		// a pond in the lowest hollow of a big pasture
		if (d.param("pond", w * dp >= 300 && d.chance(0.55))) {
			pond(d, w, dp, shelter, gateX);
		}
		// the grass: long and flowery away from the gate, a shade tree
		meadow(d, w, dp, gateX);
		int[] tree = treeSpot(d, w, dp, shelter, gateX);
		if (tree != null) {
			Trees.shade(d, tree[0], tree[2], tree[1]);
		}
		// the herder's stands: at the gate and out in the grass
		Spots.job(d, gateX, 1, Dirs.BACK, JobType.HERDER);
		Spots.job(d, w / 2, dp / 2 + 1, Dirs.FRONT, JobType.HERDER);
		// the boundary with its gate (and a second gate out the back onto the open land on a deep one)
		List<int[]> gates = new ArrayList<>();
		gates.add(new int[]{gateX, 0});
		if (dp >= 18 && d.chance(0.5)) {
			gates.add(new int[]{0, dp / 2 + d.between(-2, 2)});
		}
		// beasts need a boundary they cannot step over: a bank or nothing becomes rails
		Bounds.Kind kind = Field.boundary(d);
		if ((kind == Bounds.Kind.BUND || kind == Bounds.Kind.NONE) && !d.hasParam("boundary")) {
			kind = Bounds.Kind.RAILS;
		}
		Bounds.around(d, 0, 0, w - 1, dp - 1, kind, gates);
		Groundwork.clear(d, 0, 0, w - 1, dp - 1, Math.min(d.maxHeight() - 1, d.site().maxGround(0, 0, w - 1, dp - 1) + 8));
	}

	/** Water inside the pasture is filled to the bank's height (a flooded corner would let the beasts out). */
	private static void fillWater(Draft d, int w, int dp) {
		if (!d.site().anyWater(0, 0, w - 1, dp - 1)) {
			return;
		}
		for (int z = 0; z < dp; z++) {
			for (int x = 0; x < w; x++) {
				if (!d.site().isWater(x, z)) {
					continue;
				}
				int wy = d.site().water(x, z);
				int g = d.site().ground(x, z);
				try (var s = d.stage(StageKind.EARTHWORK)) {
					for (int y = g - 1; y < wy - 1; y++) {
						d.set(x, y, z, d.mat(Role.FILL).full());
					}
					d.set(x, wy - 1, z, d.mat(Role.GROUND).full());
					d.drain(x, z);
				}
			}
		}
	}

	/** Bare, trampled earth round the gate and the trough, fading into the grass. */
	private static void trample(Draft d, int gateX, int tx, int tz, int w, int dp) {
		try (var s = d.stage(StageKind.EXTERIOR)) {
			for (int z = 1; z <= tz + 2 && z < dp - 1; z++) {
				for (int x = Math.min(gateX, tx) - 2; x <= Math.max(gateX, tx + 2) + 2; x++) {
					if (x < 1 || x > w - 2) {
						continue;
					}
					double dist = Math.min(Math.abs(x - gateX) + Math.abs(z - 1) * 0.7, Math.abs(x - (tx + 1)) + Math.abs(z - tz) * 0.8);
					double u = Noise.value(d.seed() ^ 0x7A3, x / 1.7, 0, z / 1.7);
					if (dist + u * 2.2 > 3.4) {
						continue;
					}
					int y = Groundwork.surface(d, x, z);
					if (!Garden.groundLike(d, x, y - 1, z)) {
						continue;
					}
					Block b = u < 0.35 ? Blocks.COARSE_DIRT : u < 0.6 ? Blocks.DIRT_PATH : d.mat(Role.GROUND).main(Form.FULL) == Blocks.SAND ? Blocks.SAND
						: u < 0.8 ? Blocks.PACKED_MUD : Blocks.COARSE_DIRT;
					d.set(x, y - 1, z, b.defaultBlockState());
				}
			}
		}
	}

	/** The field shelter along the back, open toward the field, where the ground allows; returns its rectangle. */
	private static int[] shelter(Draft d, int w, int dp) {
		boolean left = d.chance(0.5);
		int z1 = dp - 2;
		for (int sw : new int[]{Math.min(7, w - 6), 5, 4}) {
			int sd = 3;
			int z0 = z1 - sd + 1;
			for (int k = 0; k <= 4; k++) {
				int x0 = left ? 2 + k : w - 2 - sw - k;
				int x1 = x0 + sw - 1;
				if (x0 < 1 || x1 > w - 2) {
					continue;
				}
				int y = Sheds.pad(d, x0, z0, x1, z1, Dirs.FRONT, 2);
				if (y == Integer.MIN_VALUE || y + 6 >= d.maxHeight()) {
					continue;
				}
				shelterAt(d, x0, z0, x1, z1, y);
				return new int[]{x0, z0, x1, z1};
			}
		}
		return null;
	}

	private static void shelterAt(Draft d, int x0, int z0, int x1, int z1, int y) {
		for (int z = z0; z <= z1; z++) {
			for (int x = x0; x <= x1; x++) {
				for (int yy = y; yy <= y + 4; yy++) {
					d.remove(x, yy, z);
				}
			}
		}
		Sheds.lean(d, x0, z0, x1, z1, y, Dirs.FRONT, 3, false);
		try (var s = d.stage(StageKind.INTERIOR)) {
			int row = z1 - 1;
			d.set(x0 + 1, y, row, Blocks.HAY_BLOCK.defaultBlockState());
			d.set(x0 + 1, y + 1, row, Blocks.HAY_BLOCK.defaultBlockState());
			if (x1 - x0 >= 5) {
				d.set(x0 + 2, y, row, Blocks.HAY_BLOCK.defaultBlockState());
			}
		}
		// a hay rack: fence slats against the back wall with hay behind them, the herder's other stand
		int rx = x1 - 2;
		try (var s = d.stage(StageKind.INTERIOR)) {
			d.set(rx, y, z1 - 1, d.mat(Role.FENCE).has(Form.FENCE) ? d.mat(Role.FENCE).fence() : d.mat(Role.FRAME).fence());
			d.set(rx, y + 1, z1 - 1, Blocks.HAY_BLOCK.defaultBlockState());
		}
		Spots.at(d, PoiType.JOB_SITE, rx, y, z1 - 2, Dirs.BACK, JobType.HERDER);
		Furniture.chest(d, x1 - 1, y, z1 - 1, Dirs.FRONT, List.of(new Object[]{"minecraft:wheat", 16}, new Object[]{"minecraft:shears", 1},
			new Object[]{"minecraft:lead", 2}));
	}

	/** A pond in the lowest part away from the gate and the shelter: water a block down, rushes at its edge. */
	private static void pond(Draft d, int w, int dp, int[] shelter, int gateX) {
		int best = Integer.MAX_VALUE;
		int px = -1;
		int pz = -1;
		for (int z = 4; z <= dp - 6; z++) {
			for (int x = 3; x <= w - 6; x++) {
				if (shelter != null && x + 3 >= shelter[0] - 1 && x <= shelter[2] + 1 && z + 2 >= shelter[1] - 1) {
					continue;
				}
				if (Math.abs(x - gateX) < 4 && z < 7) {
					continue;
				}
				int lo = d.site().minGround(x, z, x + 2, z + 1);
				int hi = d.site().maxGround(x - 1, z - 1, x + 3, z + 2);
				if (d.site().anyWater(x - 1, z - 1, x + 3, z + 2) || hi - lo > 1) {
					continue;
				}
				int score = lo * 10 + (hi - lo) * 3;
				if (score < best) {
					best = score;
					px = x;
					pz = z;
				}
			}
		}
		if (px < 0) {
			return;
		}
		int lv = d.site().minGround(px, pz, px + 2, pz + 1);
		int[][] cells = {{0, 0}, {1, 0}, {2, 0}, {0, 1}, {1, 1}, {2, 1}, {1, -1}, {1, 2}};
		try (var s = d.stage(StageKind.EARTHWORK)) {
			// the rim made up to the pond's level so the water stays
			for (int z = pz - 2; z <= pz + 3; z++) {
				for (int x = px - 1; x <= px + 3; x++) {
					int g = d.site().ground(x, z);
					for (int y = g; y < lv; y++) {
						d.set(x, y - 1, z, d.mat(Role.FILL).full());
					}
					if (g < lv) {
						d.set(x, lv - 1, z, d.mat(Role.GROUND).full());
					}
				}
			}
			for (int[] c : cells) {
				int x = px + c[0];
				int z = pz + c[1];
				int g = d.site().ground(x, z);
				for (int y = lv; y < g; y++) {
					d.set(x, y, z, Blocks.AIR.defaultBlockState());
				}
				d.set(x, lv - 2, z, Blocks.MUD.defaultBlockState());
				d.set(x, lv - 1, z, Blocks.WATER.defaultBlockState());
			}
		}
		// rushes and a lily on it
		try (var s = d.stage(StageKind.EXTERIOR)) {
			d.set(px + 2, lv, pz + 1, Blocks.LILY_PAD.defaultBlockState());
			for (int[] o : new int[][]{{-1, 0}, {3, 1}, {0, 2}}) {
				int x = px + o[0];
				int z = pz + o[1];
				if (d.isAbsent(x, lv, z)) {
					d.set(x, lv - 1, z, Blocks.GRASS_BLOCK.defaultBlockState());
					d.set(x, lv, z, States.block("expanse:cattail", Blocks.SHORT_GRASS).defaultBlockState()
						.trySetValue(net.minecraft.world.level.block.DoublePlantBlock.HALF, net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER));
					if (d.get(x, lv, z).hasProperty(net.minecraft.world.level.block.DoublePlantBlock.HALF)) {
						d.tallPlant(x, lv, z, d.get(x, lv, z).getBlock());
					}
				}
			}
		}
	}

	/** Long grass and wild flowers over the pasture, sparse near the gate where the beasts trample it. */
	private static void meadow(Draft d, int w, int dp, int gateX) {
		Block flower = null;
		try (var s = d.stage(StageKind.EXTERIOR)) {
			for (int z = 1; z < dp - 1; z++) {
				for (int x = 1; x < w - 1; x++) {
					int y = Groundwork.surface(d, x, z);
					if (!d.isAbsent(x, y, z) || d.site().isWater(x, z) && !d.drained(x, z)) {
						continue;
					}
					var below = d.get(x, y - 1, z);
					boolean grass = below == null ? !d.site().isWater(x, z) : below.is(Blocks.GRASS_BLOCK);
					if (!grass || d.mat(Role.GROUND).main(Form.FULL) != Blocks.GRASS_BLOCK) {
						continue;
					}
					double near = Math.hypot(x - gateX, z) / 8.0;
					double u = Noise.unit(d.seed() ^ 0x3EA, x, 0, z);
					double v = Noise.value(d.seed() ^ 0x3EB, x / 3.0, 0, z / 3.0);
					if (u > 0.55 * Math.min(1, near) * (0.6 + v)) {
						continue;
					}
					boolean room = y + 2 < d.maxHeight();
					if (u < 0.05 && near > 1 && room) {
						Garden.flower(d, x, y, z);
					} else if (u < 0.09 && near > 1.2 && v > 0.5 && room) {
						d.tallPlant(x, y, z, Blocks.TALL_GRASS);
					} else {
						d.set(x, y, z, (u < 0.12 ? Blocks.FERN : Blocks.SHORT_GRASS).defaultBlockState());
					}
				}
			}
		}
	}

	/** A spot for the shade tree: open grass well inside the pasture, away from the gate, shelter and pond. */
	private static int[] treeSpot(Draft d, int w, int dp, int[] shelter, int gateX) {
		for (int tries = 0; tries < 12; tries++) {
			int x = d.between(3, w - 4);
			int z = d.between(dp / 3, dp - 4);
			if (Math.abs(x - gateX) < 3 && z < 6) {
				continue;
			}
			if (shelter != null && x >= shelter[0] - 3 && x <= shelter[2] + 3 && z >= shelter[1] - 3) {
				continue;
			}
			int y = Groundwork.surface(d, x, z);
			if (y + 9 >= d.maxHeight() || !d.isEmpty(x, y, z) && d.get(x, y, z) != null && !(d.get(x, y, z).getBlock() instanceof net.minecraft.world.level.block.VegetationBlock)) {
				continue;
			}
			boolean clear = true;
			for (int dz = -2; dz <= 2 && clear; dz++) {
				for (int dx = -2; dx <= 2 && clear; dx++) {
					var s = d.get(x + dx, y + 1, z + dz);
					clear = s == null || s.isAir();
					var below = d.get(x + dx, y - 1, z + dz);
					clear &= below == null || !below.is(Blocks.WATER);
				}
			}
			if (clear) {
				d.remove(x, y, z);
				d.remove(x, y + 1, z);
				return new int[]{x, z, y};
			}
		}
		return null;
	}
}
