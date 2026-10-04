package dev.voidmc.hearth.build.kit.landscape;

import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Mat;
import dev.voidmc.hearth.build.draft.Noise;
import dev.voidmc.hearth.build.style.Form;
import dev.voidmc.hearth.build.style.Role;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * Planting set into hard ground: a flower bed let into the paving, a clipped bush, a raised planter box with a rim of
 * the masonry (or boards) and a tree or flowers in it. Each lays its own soil, so nothing grows out of stone.
 */
public final class Planters {
	private Planters() {
	}

	/** The soil a plant of the style wants (sand for desert plants, else grass). */
	public static Block soilFor(Block plant) {
		String p = BuiltInRegistries.BLOCK.getKey(plant).getPath();
		if (p.contains("cactus") || p.contains("dead_bush") || p.contains("dry_grass")) {
			return Blocks.SAND;
		}
		return Blocks.GRASS_BLOCK;
	}

	/** A plant of the style's garden on soil laid at y - 1 (replacing paving or earth there). */
	public static void bed(Draft d, int x, int y, int z) {
		Block plant = d.mat(Role.GARDEN).block(Form.FULL, x, y, z);
		try (var s = d.stage(StageKind.EXTERIOR)) {
			d.set(x, y - 1, z, soilFor(plant).defaultBlockState());
			if (plant.defaultBlockState().hasProperty(DoublePlantBlock.HALF)) {
				if (y + 1 < d.maxHeight()) {
					d.tallPlant(x, y, z, plant);
				}
			} else {
				d.set(x, y, z, plant.defaultBlockState());
			}
		}
	}

	/** A clipped bush of the style's hedge, one or two high. */
	public static void topiary(Draft d, int x, int y, int z) {
		Mat hedge = d.mat(Role.HEDGE);
		try (var s = d.stage(StageKind.EXTERIOR)) {
			d.set(x, y, z, hedge.leaves());
			if (y + 1 < d.maxHeight() && Noise.unit(d.seed() ^ 0x70B, x, y, z) < 0.6) {
				d.set(x, y + 1, z, hedge.leaves());
			}
		}
	}

	/** The rim of planters and kerbs: the masonry's slab or stair, or boards in timber cultures. */
	public static Mat rim(Draft d) {
		Mat m = Manner.masonry(d);
		if (Manner.timberForts(d)) {
			return d.mat(Role.FURNITURE);
		}
		return m;
	}

	/**
	 * A raised planter over a rectangle at standing level y: a rim one block high round soil, flowers and grass in it,
	 * and (when {@code tree} and there is height) a garden tree in the middle. Returns the tree's top or y.
	 */
	public static int box(Draft d, int x0, int z0, int x1, int z1, int y, boolean tree) {
		Mat rim = rim(d);
		int top = y;
		try (var s = d.stage(StageKind.EXTERIOR)) {
			for (int z = z0; z <= z1; z++) {
				for (int x = x0; x <= x1; x++) {
					boolean edge = x == x0 || x == x1 || z == z0 || z == z1;
					boolean small = x1 - x0 < 2 || z1 - z0 < 2;
					if (edge && !small) {
						boolean corner = (x == x0 || x == x1) && (z == z0 || z == z1);
						d.set(x, y, z, corner || !rim.has(Form.SLAB) ? rim.full() : rim.slab(SlabType.TOP));
					} else {
						d.set(x, y - 1, z, Blocks.DIRT.defaultBlockState());
						d.set(x, y, z, Blocks.GRASS_BLOCK.defaultBlockState());
					}
				}
			}
		}
		int cx = (x0 + x1) / 2;
		int cz = (z0 + z1) / 2;
		boolean small = x1 - x0 < 2 || z1 - z0 < 2;
		int room = d.maxHeight() - (y + 1);
		if (tree && room >= 3) {
			top = Trees.tree(d, cx, y + 1, cz, Math.min(room, 7), Trees.kind(d), x0 - 1, z0 - 1, x1 + 1, z1 + 1);
		} else if (tree && room >= 1) {
			try (var s = d.stage(StageKind.EXTERIOR)) {
				d.set(cx, y + 1, cz, d.mat(Role.HEDGE).leaves());
				if (room >= 2) {
					d.set(cx, y + 2, cz, d.mat(Role.HEDGE).leaves());
				}
			}
			top = y + Math.min(room, 2);
		}
		// flowers in the soil round it
		for (int z = z0; z <= z1; z++) {
			for (int x = x0; x <= x1; x++) {
				boolean edge = x == x0 || x == x1 || z == z0 || z == z1;
				if (edge && !small || !d.isAbsent(x, y + 1, z) || y + 1 >= d.maxHeight()) {
					continue;
				}
				double u = Noise.unit(d.seed() ^ 0xF70, x, y, z);
				if (u < 0.55) {
					Block plant = d.mat(Role.GARDEN).block(Form.FULL, x, y + 1, z);
					if (soilFor(plant) == Blocks.SAND) {
						try (var s = d.stage(StageKind.EXTERIOR)) {
							d.set(x, y, z, Blocks.SAND.defaultBlockState());
						}
					}
					try (var s = d.stage(StageKind.EXTERIOR)) {
						if (plant.defaultBlockState().hasProperty(DoublePlantBlock.HALF)) {
							if (y + 2 < d.maxHeight()) {
								d.tallPlant(x, y + 1, z, plant);
							}
						} else {
							d.set(x, y + 1, z, plant.defaultBlockState());
						}
					}
				} else if (u < 0.75) {
					try (var s = d.stage(StageKind.EXTERIOR)) {
						d.set(x, y + 1, z, Blocks.SHORT_GRASS.defaultBlockState());
					}
				}
			}
		}
		return top;
	}

	/** A single raised planter block: soil a step up with a plant in it. */
	public static void tub(Draft d, int x, int y, int z) {
		try (var s = d.stage(StageKind.EXTERIOR)) {
			d.set(x, y, z, Blocks.ROOTED_DIRT.defaultBlockState());
			Block plant = d.mat(Role.GARDEN).block(Form.FULL, x, y + 1, z);
			if (soilFor(plant) == Blocks.SAND) {
				d.set(x, y, z, Blocks.SAND.defaultBlockState());
			} else {
				d.set(x, y, z, Blocks.GRASS_BLOCK.defaultBlockState());
			}
			if (!plant.defaultBlockState().hasProperty(DoublePlantBlock.HALF) && y + 1 < d.maxHeight()) {
				d.set(x, y + 1, z, plant.defaultBlockState());
			}
		}
	}

	/** A potted plant standing on a block of the masonry (a pot on a pedestal). */
	public static void pedestalPot(Draft d, int x, int y, int z) {
		try (var s = d.stage(StageKind.EXTERIOR)) {
			d.set(x, y, z, Manner.masonry(d).full());
		}
		dev.voidmc.hearth.build.kit.Garden.pot(d, x, y + 1, z);
	}
}
