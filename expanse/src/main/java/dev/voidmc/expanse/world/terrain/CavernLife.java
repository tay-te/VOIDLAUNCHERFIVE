package dev.voidmc.expanse.world.terrain;

import dev.voidmc.expanse.registry.ExpanseBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CaveVines;
import net.minecraft.world.level.block.HugeMushroomBlock;
import net.minecraft.world.level.block.MultifaceBlock;
import net.minecraft.world.level.block.SeaPickleBlock;
import net.minecraft.world.level.block.SmallDripleafBlock;
import net.minecraft.world.level.block.SpeleothemBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.SpeleothemThickness;
import net.minecraft.world.level.chunk.ChunkAccess;

/**
 * What grows under the land, after vanilla's own cave features have had their turn.
 *
 * <p>In the great tunnels, as in vanilla's lush caves: moss on the river ledges and dry floors, glowcaps
 * near the water, glow-berry vines from the roofs.
 *
 * <p>In the realms, each after its character ({@link CavernModel#WILDS}...): what the ground is made of,
 * what grows on it and hangs from the roof, and what lies on the lake beds. Most of it gives light
 * (glowcaps, glow berries, shroomlight under the giant mushroom caps, sea pickles on the lake beds,
 * prismite, glow lichen, the lava seas), so a realm is not a black void but a dark land of lit places.
 * Only natural ground is changed, so whatever stands in a realm keeps its floors.
 */
final class CavernLife {
	private static final BlockState MOSS = Blocks.MOSS_BLOCK.defaultBlockState();
	private static final BlockState MOSS_CARPET = Blocks.MOSS_CARPET.defaultBlockState();
	private static final BlockState GLOW_LICHEN_UP = Blocks.GLOW_LICHEN.defaultBlockState().setValue(MultifaceBlock.getFaceProperty(Direction.UP), true);
	/** Giant mushrooms stand one at most in each cell of this many blocks. */
	private static final int MUSHROOM_CELL = 13;

	private CavernLife() {
	}

	static void decorate(WorldGenLevel level, ChunkAccess chunk, CavernModel caverns) {
		ChunkPos pos = chunk.getPos();
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		for (int lz = 0; lz < 16; lz++) {
			for (int lx = 0; lx < 16; lx++) {
				int x = pos.getMinBlockX() + lx;
				int z = pos.getMinBlockZ() + lz;
				CavernModel.Info c = caverns.info(x, z);
				long h = PlateMap.mix(0x4C494645L, x, z);
				if (c.hall && c.hallEdge > 1) {
					realm(level, c, x, z, h, p);
				} else {
					tunnel(level, c, x, z, h, p);
				}
			}
		}
		mushrooms(level, pos, caverns, p);
	}

	// ------------------------------------------------------------------ the tunnels

	private static void tunnel(WorldGenLevel level, CavernModel.Info c, int x, int z, long h, BlockPos.MutableBlockPos p) {
		boolean ledge = c.river && !c.riverFalls && c.riverDist >= c.riverHalfWidth && c.riverDist < c.riverHalfWidth + 3;
		boolean dry = c.tunnel && c.tunnelDist < c.tunnelHalfWidth - 1;
		if (!ledge && !dry) {
			return;
		}
		// thickest by the water
		float near = ledge ? 1.0F : -0.25F;
		int from = ledge ? c.waterTop() - 2 : Math.round(c.tunnelFloor) - 4;
		int to = ledge ? c.waterTop() + 4 : Math.round(c.tunnelFloor) + 6;
		double drift = drift(x, z, h);
		for (int y = from; y < to; y++) {
			BlockState below = level.getBlockState(p.set(x, y - 1, z));
			if (!level.getBlockState(p.set(x, y, z)).isAir() || !below.isSolidRender() || !natural(below)) {
				continue;
			}
			if (drift + near * 0.3 > 0.55) {
				level.setBlock(p.set(x, y - 1, z), MOSS, Block.UPDATE_CLIENTS);
				double roll = PlateMap.unit(h >>> 21);
				if (roll < 0.10 + near * 0.12) {
					place(level, p.set(x, y, z), ExpanseBlocks.GLOWCAP.defaultBlockState());
				} else if (roll < 0.45) {
					level.setBlock(p.set(x, y, z), MOSS_CARPET, Block.UPDATE_CLIENTS);
				}
			}
			break;
		}
		if (PlateMap.unit(h >>> 42) < (ledge ? 0.06 : 0.012)) {
			int top = ledge ? Math.round(c.tunnelRoof) + 6 : Math.round(c.tunnelFloor + c.tunnelHeight) + 6;
			int ceiling = ceiling(level, x, z, top, to, p);
			if (ceiling != Integer.MIN_VALUE) {
				vines(level, x, ceiling - 1, z, h, p);
			}
		}
	}

	// ------------------------------------------------------------------ the realms

	private static void realm(WorldGenLevel level, CavernModel.Info c, int x, int z, long h, BlockPos.MutableBlockPos p) {
		double drift = drift(x, z, h);
		double roll = PlateMap.unit(h >>> 21);
		double roll2 = PlateMap.unit(h >>> 35);

		// the ground: the first thing under open air, scanning down from above the model's floor
		int top = Math.min(Math.round(c.roof) - 1, Math.round(c.floor) + 16);
		boolean open = false;
		for (int y = top; y > Math.round(c.floor) - 12; y--) {
			BlockState here = level.getBlockState(p.set(x, y, z));
			if (here.isAir()) {
				open = true;
				continue;
			}
			if (!open) {
				continue;  // still in the rock over the realm, or inside a spire
			}
			if (here.is(Blocks.WATER)) {
				lakeBed(level, c, x, y, z, roll, roll2, p);
			} else if (here.isSolidRender() && natural(here)) {
				ground(level, c, x, y + 1, z, drift, roll, roll2, p);
			}
			break;
		}

		// the roof: the first open block under solid rock, scanning up
		double hang = PlateMap.unit(h >>> 42);
		double chance = switch (c.theme) {
			case CavernModel.WILDS, CavernModel.MERE -> 0.05;
			case CavernModel.LUSH -> 0.09;
			case CavernModel.DRIPSTONE -> 0.06;
			case CavernModel.CRYSTAL -> 0.04;
			default -> 0.03;
		};
		if (hang < chance + 0.025) {
			int ceiling = ceiling(level, x, z, Math.round(c.roof) + 8, Math.max(Math.round(c.floor) + 4, Math.round(c.roof) - 24), p);
			if (ceiling == Integer.MIN_VALUE || !natural(level.getBlockState(p.set(x, ceiling, z)))) {
				return;
			}
			int y = ceiling - 1;
			if (hang >= chance) {
				level.setBlock(p.set(x, y, z), GLOW_LICHEN_UP, Block.UPDATE_CLIENTS);
				return;
			}
			double kind = PlateMap.unit(h >>> 50);
			switch (c.theme) {
				case CavernModel.LUSH -> {
					if (kind < 0.12) {
						place(level, p.set(x, y, z), Blocks.SPORE_BLOSSOM.defaultBlockState());
					} else if (kind < 0.35) {
						place(level, p.set(x, y, z), Blocks.HANGING_ROOTS.defaultBlockState());
					} else {
						vines(level, x, y, z, h, p);
					}
				}
				case CavernModel.WILDS, CavernModel.MERE -> vines(level, x, y, z, h, p);
				case CavernModel.DRIPSTONE, CavernModel.EMBER -> {
					level.setBlock(p.set(x, ceiling, z), Blocks.DRIPSTONE_BLOCK.defaultBlockState(), Block.UPDATE_CLIENTS);
					speleothem(level, x, y, z, Direction.DOWN, 1 + (int) (kind * 6), p);
				}
				case CavernModel.CRYSTAL -> {
					BlockState crystal = kind < 0.45 ? ExpanseBlocks.PRISMITE_CLUSTER.defaultBlockState() : Blocks.AMETHYST_CLUSTER.defaultBlockState();
					level.setBlock(p.set(x, ceiling, z), kind < 0.45 ? ExpanseBlocks.PRISMITE_BLOCK.defaultBlockState() : Blocks.AMETHYST_BLOCK.defaultBlockState(),
						Block.UPDATE_CLIENTS);
					level.setBlock(p.set(x, y, z), crystal.setValue(AmethystClusterBlock.FACING, Direction.DOWN), Block.UPDATE_CLIENTS);
				}
				default -> level.setBlock(p.set(x, y, z), GLOW_LICHEN_UP, Block.UPDATE_CLIENTS);
			}
		}
	}

	/** Ground at (x, y - 1, z), open above: made over and planted after the realm's character. */
	private static void ground(WorldGenLevel level, CavernModel.Info c, int x, int y, int z, double drift, double roll, double roll2,
		BlockPos.MutableBlockPos p) {
		BlockPos below = new BlockPos(x, y - 1, z);
		BlockPos at = new BlockPos(x, y, z);
		boolean shore = c.liquid() > c.floor - 4 && y - 1 <= c.liquid() + 2;
		switch (c.theme) {
			case CavernModel.WILDS -> {
				if (drift > 0.3) {
					level.setBlock(below, drift > 0.85 && roll2 < 0.3 ? Blocks.ROOTED_DIRT.defaultBlockState() : MOSS, Block.UPDATE_CLIENTS);
					if (roll < 0.12) {
						place(level, at, ExpanseBlocks.GLOWCAP.defaultBlockState());
					} else if (roll < 0.16) {
						place(level, at, (roll2 < 0.5 ? Blocks.BROWN_MUSHROOM : Blocks.RED_MUSHROOM).defaultBlockState());
					} else if (roll < 0.45) {
						level.setBlock(at, MOSS_CARPET, Block.UPDATE_CLIENTS);
					}
				}
			}
			case CavernModel.LUSH -> {
				if (shore && roll2 < 0.6) {
					level.setBlock(below, Blocks.CLAY.defaultBlockState(), Block.UPDATE_CLIENTS);
					if (roll < 0.10 && level.getBlockState(at.above()).isAir()) {
						Direction facing = Direction.Plane.HORIZONTAL.getRandomDirection(net.minecraft.util.RandomSource.create(PlateMap.mix(5, x, z)));
						BlockState leaf = Blocks.SMALL_DRIPLEAF.defaultBlockState().setValue(SmallDripleafBlock.FACING, facing);
						level.setBlock(at, leaf.setValue(SmallDripleafBlock.HALF, DoubleBlockHalf.LOWER), Block.UPDATE_CLIENTS);
						level.setBlock(at.above(), leaf.setValue(SmallDripleafBlock.HALF, DoubleBlockHalf.UPPER), Block.UPDATE_CLIENTS);
					}
				} else if (drift > 0.15) {
					level.setBlock(below, MOSS, Block.UPDATE_CLIENTS);
					if (roll < 0.035) {
						level.setBlock(at, (roll2 < 0.4 ? Blocks.FLOWERING_AZALEA : Blocks.AZALEA).defaultBlockState(), Block.UPDATE_CLIENTS);
					} else if (roll < 0.07) {
						place(level, at, ExpanseBlocks.GLOWCAP.defaultBlockState());
					} else if (roll < 0.22) {
						level.setBlock(at, Blocks.SHORT_GRASS.defaultBlockState(), Block.UPDATE_CLIENTS);
					} else if (roll < 0.5) {
						level.setBlock(at, MOSS_CARPET, Block.UPDATE_CLIENTS);
					}
				}
			}
			case CavernModel.DRIPSTONE -> {
				if (drift > 0.55) {
					level.setBlock(below, Blocks.DRIPSTONE_BLOCK.defaultBlockState(), Block.UPDATE_CLIENTS);
					if (roll < 0.09) {
						speleothem(level, x, y, z, Direction.UP, 1 + (int) (roll2 * 5), new BlockPos.MutableBlockPos());
					}
				} else if (drift < 0.12) {
					level.setBlock(below, Blocks.CALCITE.defaultBlockState(), Block.UPDATE_CLIENTS);
				}
			}
			case CavernModel.CRYSTAL -> {
				level.setBlock(below, (drift > 0.6 ? Blocks.CALCITE : drift > 0.35 ? Blocks.TUFF : Blocks.SMOOTH_BASALT).defaultBlockState(),
					Block.UPDATE_CLIENTS);
				if (roll < 0.035) {
					level.setBlock(below, ExpanseBlocks.PRISMITE_BLOCK.defaultBlockState(), Block.UPDATE_CLIENTS);
					level.setBlock(at, ExpanseBlocks.PRISMITE_CLUSTER.defaultBlockState(), Block.UPDATE_CLIENTS);
				} else if (roll < 0.08) {
					level.setBlock(below, Blocks.AMETHYST_BLOCK.defaultBlockState(), Block.UPDATE_CLIENTS);
					level.setBlock(at, (roll2 < 0.5 ? Blocks.AMETHYST_CLUSTER : Blocks.LARGE_AMETHYST_BUD).defaultBlockState(), Block.UPDATE_CLIENTS);
				}
			}
			case CavernModel.EMBER -> {
				if (shore && drift > 0.25) {
					level.setBlock(below, Blocks.MAGMA_BLOCK.defaultBlockState(), Block.UPDATE_CLIENTS);
				} else if (drift > 0.6) {
					level.setBlock(below, Blocks.SMOOTH_BASALT.defaultBlockState(), Block.UPDATE_CLIENTS);
				} else if (drift < 0.2) {
					level.setBlock(below, Blocks.TUFF.defaultBlockState(), Block.UPDATE_CLIENTS);
				}
			}
			default -> {
				// the lake realms: beaches of sand, gravel and clay, moss above them
				if (shore) {
					level.setBlock(below, (drift > 0.6 ? Blocks.SAND : drift > 0.3 ? Blocks.GRAVEL : Blocks.CLAY).defaultBlockState(), Block.UPDATE_CLIENTS);
					if (roll < 0.05 && drift > 0.6 && nextToWater(level, below)) {
						for (int k = 0; k < 1 + (int) (roll2 * 3); k++) {
							level.setBlock(at.above(k), Blocks.SUGAR_CANE.defaultBlockState(), Block.UPDATE_CLIENTS);
						}
					}
				} else if (drift > 0.3) {
					level.setBlock(below, MOSS, Block.UPDATE_CLIENTS);
					if (roll < 0.07) {
						place(level, at, ExpanseBlocks.GLOWCAP.defaultBlockState());
					} else if (roll < 0.35) {
						level.setBlock(at, roll < 0.2 ? Blocks.SHORT_GRASS.defaultBlockState() : MOSS_CARPET, Block.UPDATE_CLIENTS);
					}
				}
			}
		}
	}

	/**
	 * A lake bed under water whose surface block is at y: sand, clay or gravel, with seagrass, and sea
	 * pickles glowing in the shallows.
	 */
	private static void lakeBed(WorldGenLevel level, CavernModel.Info c, int x, int y, int z, double roll, double roll2, BlockPos.MutableBlockPos p) {
		int bed = y;
		while (bed > y - 24 && level.getBlockState(p.set(x, bed, z)).is(Blocks.WATER)) {
			bed--;
		}
		BlockState floor = level.getBlockState(p.set(x, bed, z));
		if (!floor.isSolidRender() || !natural(floor)) {
			return;
		}
		BlockState cover = c.theme == CavernModel.LUSH ? Blocks.CLAY.defaultBlockState()
			: roll2 < 0.5 ? Blocks.SAND.defaultBlockState() : roll2 < 0.8 ? Blocks.GRAVEL.defaultBlockState() : Blocks.CLAY.defaultBlockState();
		level.setBlock(p.set(x, bed, z), cover, Block.UPDATE_CLIENTS);
		p.set(x, bed + 1, z);
		if (roll < 0.05) {
			level.setBlock(p, Blocks.SEA_PICKLE.defaultBlockState().setValue(SeaPickleBlock.PICKLES, 1 + (int) (roll2 * 4)), Block.UPDATE_CLIENTS);
		} else if (roll < 0.3 && level.getBlockState(p.above()).is(Blocks.WATER)) {
			level.setBlock(p, Blocks.SEAGRASS.defaultBlockState(), Block.UPDATE_CLIENTS);
		}
	}

	/**
	 * Giant glowcaps, in the mushroom realms (and a few in the lush and lake realms): a pale stem under a
	 * red dome or a broad brown cap, with shroomlight among the gills underneath. One at most in each
	 * {@value #MUSHROOM_CELL}-block cell, placed by the chunk its stem stands in.
	 */
	private static void mushrooms(WorldGenLevel level, ChunkPos pos, CavernModel caverns, BlockPos.MutableBlockPos p) {
		int c0x = Math.floorDiv(pos.getMinBlockX(), MUSHROOM_CELL) - 1;
		int c0z = Math.floorDiv(pos.getMinBlockZ(), MUSHROOM_CELL) - 1;
		for (int cx = c0x; cx <= c0x + 3; cx++) {
			for (int cz = c0z; cz <= c0z + 3; cz++) {
				long h = PlateMap.mix(0x4D555348L, cx, cz);
				int x = cx * MUSHROOM_CELL + 2 + (int) (PlateMap.unit(h >>> 8) * (MUSHROOM_CELL - 4));
				int z = cz * MUSHROOM_CELL + 2 + (int) (PlateMap.unit(h >>> 16) * (MUSHROOM_CELL - 4));
				if (x < pos.getMinBlockX() || x > pos.getMaxBlockX() || z < pos.getMinBlockZ() || z > pos.getMaxBlockZ()) {
					continue;
				}
				CavernModel.Info c = caverns.info(x, z);
				if (!c.hall || c.hallEdge < 10) {
					continue;
				}
				double chance = c.theme == CavernModel.WILDS ? 0.55 : c.theme == CavernModel.LUSH || c.theme == CavernModel.MERE ? 0.1 : 0;
				if (PlateMap.unit(h) >= chance) {
					continue;
				}
				// find the ground
				int y = Math.min(Math.round(c.roof) - 1, Math.round(c.floor) + 14);
				while (y > c.floor - 10 && level.getBlockState(p.set(x, y - 1, z)).isAir()) {
					y--;
				}
				BlockState ground = level.getBlockState(p.set(x, y - 1, z));
				if (!ground.isSolidRender() || !natural(ground) || !level.getBlockState(p.set(x, y, z)).isAir() && !level.getBlockState(p).is(Blocks.MOSS_CARPET)
					&& !level.getBlockState(p).is(ExpanseBlocks.GLOWCAP)) {
					continue;
				}
				boolean red = PlateMap.unit(h >>> 24) < 0.45;
				int stem = (red ? 5 : 7) + (int) (PlateMap.unit(h >>> 32) * (red ? 6 : 10));
				int radius = red ? 3 + (int) (PlateMap.unit(h >>> 40) * 2.5) : 4 + (int) (PlateMap.unit(h >>> 40) * 3.5);
				giantMushroom(level, new BlockPos(x, y, z), stem, radius, red, h, p);
			}
		}
	}

	private static void giantMushroom(WorldGenLevel level, BlockPos foot, int stem, int radius, boolean red, long h, BlockPos.MutableBlockPos p) {
		int capHeight = red ? radius : 2;
		// room for it all: open air over the cap's whole footprint
		for (int dy = 0; dy < stem + capHeight + 2; dy++) {
			for (int dx = -radius; dx <= radius; dx += radius) {
				for (int dz = -radius; dz <= radius; dz += radius) {
					BlockState s = level.getBlockState(p.set(foot.getX() + dx, foot.getY() + dy, foot.getZ() + dz));
					boolean low = dy < stem - 2;
					if (!s.isAir() && !(low && (dx != 0 || dz != 0)) && !s.is(Blocks.MOSS_CARPET) && !s.is(ExpanseBlocks.GLOWCAP)) {
						return;
					}
				}
			}
		}
		BlockState stemState = Blocks.MUSHROOM_STEM.defaultBlockState().setValue(HugeMushroomBlock.UP, false).setValue(HugeMushroomBlock.DOWN, false);
		for (int dy = 0; dy < stem + (red ? 1 : 0); dy++) {
			level.setBlock(p.set(foot.getX(), foot.getY() + dy, foot.getZ()), stemState, Block.UPDATE_CLIENTS);
		}
		BlockState cap = (red ? Blocks.RED_MUSHROOM_BLOCK : Blocks.BROWN_MUSHROOM_BLOCK).defaultBlockState().setValue(HugeMushroomBlock.DOWN, false);
		BlockState light = Blocks.SHROOMLIGHT.defaultBlockState();
		int base = foot.getY() + stem;
		for (int dx = -radius; dx <= radius; dx++) {
			for (int dz = -radius; dz <= radius; dz++) {
				double d = Math.sqrt(dx * dx + dz * dz);
				if (d > radius + 0.4) {
					continue;
				}
				if (red) {
					// a dome: the shell only, highest in the middle, skirt hanging at the rim
					int top = base + (int) Math.round(capHeight * Math.sqrt(Math.max(0, 1 - sq(d / (radius + 0.4)))));
					int bottom = d > radius - 1.2 ? base - 1 : top;
					for (int y = bottom; y <= top; y++) {
						set(level, p.set(foot.getX() + dx, y, foot.getZ() + dz), cap);
					}
					if (d > radius - 2.2 && d <= radius - 1.2 && PlateMap.unit(PlateMap.mix(h, dx, dz)) < 0.35) {
						set(level, p.set(foot.getX() + dx, base - 1, foot.getZ() + dz), light);
					}
				} else {
					// a broad flat cap, turned up a little at the rim, its gills lit
					int y = base + (d > radius - 1.5 ? 1 : 0);
					set(level, p.set(foot.getX() + dx, y, foot.getZ() + dz), cap);
					if (d > 1.5 && d < radius - 1 && PlateMap.unit(PlateMap.mix(h, dx, dz)) < 0.18) {
						set(level, p.set(foot.getX() + dx, y - 1, foot.getZ() + dz), light);
					}
				}
			}
		}
	}

	// ------------------------------------------------------------------ pieces

	/** A stalagmite (up) or stalactite (down) of pointed dripstone, {@code length} long from (x, y, z). */
	private static void speleothem(WorldGenLevel level, int x, int y, int z, Direction tip, int length, BlockPos.MutableBlockPos p) {
		for (int k = 0; k < length; k++) {
			if (!level.getBlockState(p.set(x, y + (tip == Direction.UP ? k : -k), z)).isAir()) {
				length = k;
				break;
			}
		}
		for (int k = 0; k < length; k++) {
			int fromTip = length - 1 - k;
			SpeleothemThickness t = fromTip == 0 ? SpeleothemThickness.TIP : fromTip == 1 ? SpeleothemThickness.FRUSTUM
				: k == 0 ? SpeleothemThickness.BASE : SpeleothemThickness.MIDDLE;
			BlockState s = Blocks.POINTED_DRIPSTONE.defaultBlockState().setValue(SpeleothemBlock.TIP_DIRECTION, tip).setValue(SpeleothemBlock.THICKNESS, t);
			level.setBlock(p.set(x, y + (tip == Direction.UP ? k : -k), z), s, Block.UPDATE_CLIENTS);
		}
	}

	/** Glow-berry vines hanging from (x, y, z) down. */
	private static void vines(WorldGenLevel level, int x, int y, int z, long h, BlockPos.MutableBlockPos p) {
		int length = 2 + (int) (PlateMap.unit(h >>> 13) * 9);
		for (int k = 0; k < length; k++) {
			BlockPos at = p.set(x, y - k, z).immutable();
			if (!level.getBlockState(at).isAir()) {
				break;
			}
			boolean tip = k == length - 1 || !level.getBlockState(at.below()).isAir();
			BlockState vine = (tip ? Blocks.CAVE_VINES : Blocks.CAVE_VINES_PLANT).defaultBlockState()
				.setValue(CaveVines.BERRIES, PlateMap.unit(h >>> (k + 3)) < 0.4);
			level.setBlock(at, vine, Block.UPDATE_CLIENTS);
			if (tip) {
				break;
			}
		}
	}

	/** The first rock above open air, scanning up from {@code from} to {@code to}; MIN_VALUE if none. */
	private static int ceiling(WorldGenLevel level, int x, int z, int to, int from, BlockPos.MutableBlockPos p) {
		for (int y = from; y < to; y++) {
			if (level.getBlockState(p.set(x, y, z)).isAir() && level.getBlockState(p.set(x, y + 1, z)).isSolidRender()) {
				return y + 1;
			}
		}
		return Integer.MIN_VALUE;
	}

	/** Patches: a slow noise with a little scatter, so growth comes in drifts. */
	private static double drift(int x, int z, long h) {
		return PlateMap.unit(h) * 0.35 + 0.65 * (0.5 + 0.5 * Math.sin(x * 0.07 + 1.3 * Math.cos(z * 0.05)) * Math.cos(z * 0.06));
	}

	/** Rock and earth as the land made them, never anything built. */
	private static boolean natural(BlockState s) {
		return s.is(BlockTags.BASE_STONE_OVERWORLD) || s.is(BlockTags.DIRT) || s.is(Blocks.GRAVEL) || s.is(Blocks.CLAY) || s.is(Blocks.SAND)
			|| s.is(Blocks.DRIPSTONE_BLOCK) || s.is(Blocks.CALCITE) || s.is(Blocks.SMOOTH_BASALT) || s.is(Blocks.COAL_ORE) || s.is(Blocks.DEEPSLATE_COAL_ORE)
			|| s.is(BlockTags.IRON_ORES) || s.is(BlockTags.COPPER_ORES);
	}

	private static boolean nextToWater(WorldGenLevel level, BlockPos pos) {
		for (Direction d : Direction.Plane.HORIZONTAL) {
			if (level.getFluidState(pos.relative(d)).is(net.minecraft.tags.FluidTags.WATER)) {
				return true;
			}
		}
		return false;
	}

	private static void place(WorldGenLevel level, BlockPos pos, BlockState state) {
		if (state.canSurvive(level, pos)) {
			level.setBlock(pos, state, Block.UPDATE_CLIENTS);
		}
	}

	private static void set(WorldGenLevel level, BlockPos pos, BlockState state) {
		if (level.getBlockState(pos).isAir() || level.getBlockState(pos).is(Blocks.MOSS_CARPET) || level.getBlockState(pos).is(Blocks.CAVE_VINES)
			|| level.getBlockState(pos).is(Blocks.CAVE_VINES_PLANT)) {
			level.setBlock(pos, state, Block.UPDATE_CLIENTS);
		}
	}

	private static double sq(double v) {
		return v * v;
	}
}
