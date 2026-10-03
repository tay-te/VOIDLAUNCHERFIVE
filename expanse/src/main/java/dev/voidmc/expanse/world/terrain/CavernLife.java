package dev.voidmc.expanse.world.terrain;

import dev.voidmc.expanse.registry.DeepBlocks;
import dev.voidmc.expanse.registry.ExpanseBlocks;
import dev.voidmc.expanse.world.biome.UndergroundBiomes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.BigDripleafBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CaveVines;
import net.minecraft.world.level.block.HangingMossBlock;
import net.minecraft.world.level.block.HugeMushroomBlock;
import net.minecraft.world.level.block.MultifaceBlock;
import net.minecraft.world.level.block.PotentSulfurBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SeaPickleBlock;
import net.minecraft.world.level.block.SmallDripleafBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.SpeleothemBlock;
import net.minecraft.world.level.block.entity.BrushableBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.PotentSulfurState;
import net.minecraft.world.level.block.state.properties.SpeleothemThickness;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;

/**
 * Everything that grows, stands or hangs under the land: the Earth terrain carves no vanilla caves and its
 * underground biomes carry no vanilla decoration, so all of it is placed here.
 *
 * <p>In the caves between the realms (passages, chambers, the ways in and the great tunnels) the floors and
 * roofs take after the cave biome they run through (moss in the Mossgrown Caves, dripstone in the Grottos,
 * tuff in the Echoing Depths, sulfur in the Seeps) and otherwise after the land above: snow and icicles
 * under the cold lands, sand under the deserts, moss under the wet ones, bare stone and gravel elsewhere,
 * with glow lichen here and there. The river tunnels keep moss and glowcaps along their ledges.
 *
 * <p>In the realms, each after its character ({@link CavernModel#WILDS}...): what the ground is made of, what
 * grows on it and hangs from the roof, what lies on the lake beds, and the great shapes of each: giant
 * mushrooms, stalagmite fields, ice spikes, sandstone hoodoos, the giant roots of the root hollows hanging
 * from the vault and arching across, the bones of giants lying in the fossil deeps, amber boulders, the
 * basalt causeways of the brimstone vaults, tide pools on the grottos' terraces, geysers in hot springs.
 * Most of it gives light (glowcaps, glow berries, shroomlight, sea pickles, prismite, amber, glowroots,
 * glowworm silk, frostblooms, magma, glow lichen), so a realm is a dark land of lit places, not a black
 * void. Only natural ground is changed, so whatever stands in a realm keeps its floors.
 *
 * <p>Everything is placed by the chunk its column (or, for the few shapes that spread, its foot) lies in,
 * so it comes out the same whichever order the chunks are decorated in.
 */
final class CavernLife {
	private static final BlockState MOSS = Blocks.MOSS_BLOCK.defaultBlockState();
	private static final BlockState MOSS_CARPET = Blocks.MOSS_CARPET.defaultBlockState();
	private static final BlockState GLOW_LICHEN_UP = Blocks.GLOW_LICHEN.defaultBlockState().setValue(MultifaceBlock.getFaceProperty(Direction.UP), true);
	/** Giant mushrooms stand one at most in each cell of this many blocks. */
	private static final int MUSHROOM_CELL = 13;
	/** Spikes (stalagmites, ice spikes, hoodoos...) stand one at most in each cell of this many blocks. */
	private static final int SPIKE_CELL = 9;
	private static final int ROOT_CELL = 36;
	private static final int FOSSIL_CELL = 60;
	private static final int BOULDER_CELL = 26;
	private static final double CAUSEWAY_CELL = 4.5;
	private static final int LAVA = CavernModel.LAVA_LEVEL;

	// what the caves between the realms are like
	private static final int PLAIN = 0;
	private static final int MOSSY = 1;
	private static final int DRIP = 2;
	private static final int ECHO = 3;
	private static final int SULFUR = 4;
	private static final int COLD = 5;
	private static final int DRY = 6;

	/** The great shapes in one realm column, worked out (from other columns of the model) before it is decorated. */
	private static final class Shapes {
		static final int MAX = 12;
		int count;
		final int[] low = new int[MAX];
		final int[] high = new int[MAX];
		final BlockState[] block = new BlockState[MAX];
		final boolean[] root = new boolean[MAX];
		/** The top of the basalt column here, MIN_VALUE if none. */
		int causeway;
		/** Near a fossil: the sand round it is worth brushing. */
		boolean fossil;

		void clear() {
			this.count = 0;
			this.causeway = Integer.MIN_VALUE;
			this.fossil = false;
		}

		void add(int low, int high, BlockState block, boolean root) {
			if (this.count < MAX && high >= low) {
				this.low[this.count] = low;
				this.high[this.count] = high;
				this.block[this.count] = block;
				this.root[this.count] = root;
				this.count++;
			}
		}
	}

	private static final ThreadLocal<Shapes> SHAPES = ThreadLocal.withInitial(Shapes::new);

	private CavernLife() {
	}

	static void decorate(WorldGenLevel level, ChunkAccess chunk, CavernModel caverns) {
		ChunkPos pos = chunk.getPos();
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		Shapes shapes = SHAPES.get();
		for (int lz = 0; lz < 16; lz++) {
			for (int lx = 0; lx < 16; lx++) {
				int x = pos.getMinBlockX() + lx;
				int z = pos.getMinBlockZ() + lz;
				long h = PlateMap.mix(0x4C494645L, x, z);
				CavernModel.Info c = caverns.info(x, z);
				if (c.hall && c.hallEdge > 1) {
					int theme = c.theme;
					shapes.clear();
					shapes(caverns, theme, x, z, shapes);
					// (the shapes read other columns of the model, which may have taken this column's place in its cache)
					realm(level, caverns.info(x, z), shapes, x, z, h, p);
				} else if (c.fine || c.tunnel || c.river) {
					Column land = caverns.terrain().sample(x, z);
					float temperature = land.temperature;
					float humidity = land.humidity;
					c = caverns.info(x, z);
					tunnel(level, c, x, z, h, temperature, humidity, p);
					small(level, c, x, z, h, temperature, humidity, p);
				}
			}
		}
		mushrooms(level, pos, caverns, p);
		spikes(level, pos, caverns, p);
	}

	// ------------------------------------------------------------------ the caves between the realms

	/** The great tunnels: moss and glowcaps along the river ledges, the dry tunnels as the caves round them. */
	private static void tunnel(WorldGenLevel level, CavernModel.Info c, int x, int z, long h, float temperature, float humidity,
		BlockPos.MutableBlockPos p) {
		boolean ledge = c.river && !c.riverFalls && c.riverDist >= c.riverHalfWidth && c.riverDist < c.riverHalfWidth + 3;
		if (c.tunnel && c.tunnelDist < c.tunnelHalfWidth - 1) {
			cave(level, x, z, c.tunnelFloor, c.tunnelFloor + c.tunnelHeight, h, temperature, humidity, p);
		}
		if (!ledge) {
			return;
		}
		double drift = drift(x, z, h);
		int kind = kind(level, x, c.waterTop(), z, temperature, humidity);
		for (int y = c.waterTop() - 2; y < c.waterTop() + 4; y++) {
			BlockState below = level.getBlockState(p.set(x, y - 1, z));
			if (!level.getBlockState(p.set(x, y, z)).isAir() || !below.isSolidRender() || !natural(below)) {
				continue;
			}
			if (kind == COLD || kind == DRY) {
				caveFloor(level, kind, x, y - 1, z, drift, PlateMap.unit(h >>> 21), PlateMap.unit(h >>> 35), p);
			} else if (drift + 0.3 > 0.55) {
				level.setBlock(p.set(x, y - 1, z), MOSS, Block.UPDATE_CLIENTS);
				double roll = PlateMap.unit(h >>> 21);
				if (roll < 0.22) {
					place(level, p.set(x, y, z), ExpanseBlocks.GLOWCAP.defaultBlockState());
				} else if (roll < 0.45) {
					level.setBlock(p.set(x, y, z), MOSS_CARPET, Block.UPDATE_CLIENTS);
				}
			}
			break;
		}
		if (PlateMap.unit(h >>> 42) < 0.06 && kind != COLD && kind != DRY) {
			int ceiling = ceiling(level, x, z, Math.round(c.tunnelRoof) + 6, c.waterTop() + 4, p);
			if (ceiling != Integer.MIN_VALUE) {
				vines(level, x, ceiling - 1, z, h, p);
			}
		}
	}

	/** The passages, chambers and ways in through a column. */
	private static void small(WorldGenLevel level, CavernModel.Info c, int x, int z, long h, float temperature, float humidity,
		BlockPos.MutableBlockPos p) {
		if (!c.fine) {
			return;
		}
		for (int k = 0; k < 3; k++) {
			CavernModel.Passage s = c.passages[k];
			if (s.chamber && s.chamberIn > 1) {
				cave(level, x, z, s.chamberFloor, s.chamberRoof, PlateMap.mix(h, k, 1), temperature, humidity, p);
			} else if (s.on && s.dist < s.halfWidth - 0.8F) {
				cave(level, x, z, s.floor, s.floor + s.height, PlateMap.mix(h, k, 2), temperature, humidity, p);
			}
		}
		if (c.adit && c.aditDist < c.aditHalfWidth - 0.8F) {
			cave(level, x, z, c.aditFloor, c.aditFloor + c.aditHeight, PlateMap.mix(h, 3, 3), temperature, humidity, p);
		}
	}

	/** One cave's floor and roof in a column, between about {@code low} and {@code high}. */
	private static void cave(WorldGenLevel level, int x, int z, float low, float high, long h, float temperature, float humidity,
		BlockPos.MutableBlockPos p) {
		int floor = Integer.MIN_VALUE;
		for (int y = Math.round(low) + 2; y >= Math.round(low) - 4 && y > LAVA + 1; y--) {
			BlockState here = level.getBlockState(p.set(x, y, z));
			if (here.isAir()) {
				continue;
			}
			if (here.isSolidRender() && natural(here) && level.getBlockState(p.set(x, y + 1, z)).isAir()) {
				floor = y;
			}
			break;
		}
		int kind = kind(level, x, floor != Integer.MIN_VALUE ? floor + 1 : Math.round(low), z, temperature, humidity);
		double drift = drift(x, z, h);
		if (floor != Integer.MIN_VALUE) {
			caveFloor(level, kind, x, floor, z, drift, PlateMap.unit(h >>> 21), PlateMap.unit(h >>> 35), p);
		}
		int roof = ceiling(level, x, z, Math.round(high) + 3, Math.max(Math.round(low) + 1, LAVA + 2), p);
		if (roof != Integer.MIN_VALUE && natural(level.getBlockState(p.set(x, roof, z)))) {
			caveRoof(level, kind, x, roof, z, PlateMap.unit(h >>> 13), PlateMap.unit(h >>> 40), p);
		}
	}

	/** What a cave is like: after the cave biome it runs through, else after the land above. */
	private static int kind(WorldGenLevel level, int x, int y, int z, float temperature, float humidity) {
		Holder<Biome> biome = level.getNoiseBiome(x >> 2, y >> 2, z >> 2);
		if (biome.is(UndergroundBiomes.MOSSY_CAVES)) {
			return MOSSY;
		}
		if (biome.is(UndergroundBiomes.DRIPSTONE_GROTTOS)) {
			return DRIP;
		}
		if (biome.is(UndergroundBiomes.ECHOING_DEPTHS)) {
			return ECHO;
		}
		if (biome.is(UndergroundBiomes.SULFUR_SEEPS)) {
			return SULFUR;
		}
		if (temperature < -0.45F) {
			return COLD;
		}
		if (temperature > 0.55F && humidity < 0.1F) {
			return DRY;
		}
		if (humidity > 0.3F && temperature > -0.15F) {
			return MOSSY;
		}
		return PLAIN;
	}

	/** A cave floor block at (x, y, z), open above. */
	private static void caveFloor(WorldGenLevel level, int kind, int x, int y, int z, double drift, double roll, double roll2, BlockPos.MutableBlockPos p) {
		BlockPos at = new BlockPos(x, y + 1, z);
		switch (kind) {
			case MOSSY -> {
				if (drift > 0.45) {
					cover(level, p.set(x, y, z), MOSS);
					if (roll < 0.05) {
						place(level, at, ExpanseBlocks.GLOWCAP.defaultBlockState());
					} else if (roll < 0.06) {
						place(level, at, Blocks.AZALEA.defaultBlockState());
					} else if (roll < 0.3) {
						level.setBlock(at, MOSS_CARPET, Block.UPDATE_CLIENTS);
					}
				}
			}
			case DRIP -> {
				if (drift > 0.5) {
					cover(level, p.set(x, y, z), Blocks.DRIPSTONE_BLOCK.defaultBlockState());
					if (roll < 0.07) {
						speleothem(level, x, y + 1, z, Direction.UP, 1 + (int) (roll2 * 4), Blocks.POINTED_DRIPSTONE, p);
					}
				} else if (drift < 0.1) {
					cover(level, p.set(x, y, z), Blocks.CALCITE.defaultBlockState());
				}
			}
			case ECHO -> {
				if (drift > 0.6) {
					cover(level, p.set(x, y, z), Blocks.TUFF.defaultBlockState());
				} else if (drift < 0.15) {
					cover(level, p.set(x, y, z), Blocks.SMOOTH_BASALT.defaultBlockState());
				} else if (drift > 0.42 && drift < 0.46) {
					cover(level, p.set(x, y, z), Blocks.CALCITE.defaultBlockState());
				}
				if (roll < 0.008) {
					cover(level, p.set(x, y, z), Blocks.AMETHYST_BLOCK.defaultBlockState());
					level.setBlock(at, (roll2 < 0.5 ? Blocks.AMETHYST_CLUSTER : Blocks.MEDIUM_AMETHYST_BUD).defaultBlockState(), Block.UPDATE_CLIENTS);
				}
			}
			case SULFUR -> {
				if (drift > 0.55) {
					cover(level, p.set(x, y, z), Blocks.SULFUR.defaultBlockState());
					if (roll < 0.05) {
						speleothem(level, x, y + 1, z, Direction.UP, 1 + (int) (roll2 * 2), Blocks.SULFUR_SPIKE, p);
					}
				} else if (drift < 0.08) {
					cover(level, p.set(x, y, z), Blocks.MAGMA_BLOCK.defaultBlockState());
				}
			}
			case COLD -> {
				if (drift > 0.5) {
					cover(level, p.set(x, y, z), Blocks.SNOW_BLOCK.defaultBlockState());
					if (roll < 0.012) {
						place(level, at, ExpanseBlocks.FROSTBLOOM.defaultBlockState());
					} else if (roll < 0.4) {
						place(level, at, Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, 1 + (int) (roll2 * 2)));
					}
				} else if (drift < 0.2) {
					cover(level, p.set(x, y, z), Blocks.PACKED_ICE.defaultBlockState());
				}
			}
			case DRY -> {
				if (drift > 0.4) {
					cover(level, p.set(x, y, z), Blocks.SAND.defaultBlockState());
					cover(level, p.set(x, y - 1, z), Blocks.SANDSTONE.defaultBlockState());
					if (roll < 0.01) {
						place(level, at, Blocks.DEAD_BUSH.defaultBlockState());
					}
				} else if (drift < 0.12) {
					cover(level, p.set(x, y, z), Blocks.SANDSTONE.defaultBlockState());
				}
			}
			default -> {
				if (drift > 0.82) {
					cover(level, p.set(x, y, z), Blocks.GRAVEL.defaultBlockState());
				} else if (roll < 0.006) {
					speleothem(level, x, y + 1, z, Direction.UP, 1 + (int) (roll2 * 2), Blocks.POINTED_DRIPSTONE, p);
				}
			}
		}
	}

	/** A cave roof block at (x, y, z), open below. */
	private static void caveRoof(WorldGenLevel level, int kind, int x, int y, int z, double roll, double roll2, BlockPos.MutableBlockPos p) {
		int under = y - 1;
		switch (kind) {
			case MOSSY -> {
				if (roll < 0.05) {
					vines(level, x, under, z, PlateMap.mix(x, y, z), p);
				} else if (roll < 0.07) {
					place(level, p.set(x, under, z), Blocks.HANGING_ROOTS.defaultBlockState());
				} else if (roll < 0.095) {
					level.setBlock(p.set(x, under, z), GLOW_LICHEN_UP, Block.UPDATE_CLIENTS);
				}
			}
			case DRIP -> {
				if (roll < 0.07) {
					cover(level, p.set(x, y, z), Blocks.DRIPSTONE_BLOCK.defaultBlockState());
					speleothem(level, x, under, z, Direction.DOWN, 1 + (int) (roll2 * 5), Blocks.POINTED_DRIPSTONE, p);
				}
			}
			case ECHO -> {
				if (roll < 0.035) {
					level.setBlock(p.set(x, under, z), GLOW_LICHEN_UP, Block.UPDATE_CLIENTS);
				} else if (roll < 0.041) {
					cover(level, p.set(x, y, z), Blocks.AMETHYST_BLOCK.defaultBlockState());
					level.setBlock(p.set(x, under, z), Blocks.AMETHYST_CLUSTER.defaultBlockState().setValue(AmethystClusterBlock.FACING, Direction.DOWN),
						Block.UPDATE_CLIENTS);
				}
			}
			case SULFUR -> {
				if (roll < 0.04) {
					cover(level, p.set(x, y, z), Blocks.SULFUR.defaultBlockState());
					speleothem(level, x, under, z, Direction.DOWN, 1 + (int) (roll2 * 3), Blocks.SULFUR_SPIKE, p);
				}
			}
			case COLD -> {
				if (roll < 0.05) {
					cover(level, p.set(x, y, z), Blocks.PACKED_ICE.defaultBlockState());
					speleothem(level, x, under, z, Direction.DOWN, 1 + (int) (roll2 * 4), DeepBlocks.ICICLE, p);
				} else if (roll < 0.06) {
					level.setBlock(p.set(x, under, z), GLOW_LICHEN_UP, Block.UPDATE_CLIENTS);
				}
			}
			default -> {
				if (roll < (kind == DRY ? 0.015 : 0.02)) {
					level.setBlock(p.set(x, under, z), GLOW_LICHEN_UP, Block.UPDATE_CLIENTS);
				} else if (kind == PLAIN && roll < 0.03) {
					speleothem(level, x, under, z, Direction.DOWN, 1 + (int) (roll2 * 3), Blocks.POINTED_DRIPSTONE, p);
				}
			}
		}
	}

	// ------------------------------------------------------------------ the realms

	private static void realm(WorldGenLevel level, CavernModel.Info c, Shapes shapes, int x, int z, long h, BlockPos.MutableBlockPos p) {
		double drift = drift(x, z, h);
		double roll = PlateMap.unit(h >>> 21);
		double roll2 = PlateMap.unit(h >>> 35);
		double roll3 = PlateMap.unit(PlateMap.mix(h, 3, 3));

		// the ground: the first thing under open air, scanning down from above the model's floor (or its lake)
		int top = Math.min(Math.round(c.roof) - 1, Math.max(Math.round(c.floor) + 16, Mth.floor(c.liquid()) + 2));
		boolean open = false;
		int ground = Integer.MIN_VALUE;
		boolean wet = false;
		for (int y = top; y > Math.round(c.floor) - 12 && y > LAVA; y--) {
			BlockState here = level.getBlockState(p.set(x, y, z));
			if (here.isAir()) {
				open = true;
				continue;
			}
			if (!open) {
				continue;  // still in the rock over the realm, or inside a spire
			}
			if (here.is(Blocks.WATER)) {
				wet = true;
				lakeBed(level, c, x, y, z, drift, roll, roll2, p);
			} else if (here.isSolidRender() && natural(here)) {
				ground = y;
				ground(level, c, shapes, x, y + 1, z, drift, roll, roll2, roll3, p);
			}
			break;
		}

		// under a cenote, a patch of green in the daylight
		if (c.cenote && c.cenoteDist < c.cenoteRadius * 1.5F && ground != Integer.MIN_VALUE && !wet) {
			cover(level, p.set(x, ground, z), Blocks.GRASS_BLOCK.defaultBlockState());
			BlockPos at = new BlockPos(x, ground + 1, z);
			if (level.getBlockState(at).isAir() || level.getBlockState(at).is(Blocks.MOSS_CARPET)) {
				BlockState plant = roll < 0.35 ? Blocks.SHORT_GRASS.defaultBlockState() : roll < 0.47 ? Blocks.FERN.defaultBlockState()
					: roll < 0.5 ? Blocks.AZALEA.defaultBlockState() : roll < 0.52 ? Blocks.MELON.defaultBlockState() : Blocks.AIR.defaultBlockState();
				if (!plant.isAir()) {
					level.setBlock(at, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
					place(level, at, plant);
				}
			}
		}

		// the great shapes
		if (shapes.causeway != Integer.MIN_VALUE && ground != Integer.MIN_VALUE && !wet) {
			int causeway = Math.min(shapes.causeway, ground + 6);
			BlockState basalt = Blocks.BASALT.defaultBlockState();
			for (int y = ground; y <= causeway; y++) {
				BlockState here = level.getBlockState(p.set(x, y, z));
				if (here.isAir() || here.canBeReplaced() || natural(here)) {
					level.setBlock(p, basalt, Block.UPDATE_CLIENTS);
				}
			}
			if (causeway > ground && roll3 < 0.08) {
				level.setBlock(p.set(x, causeway, z), (roll2 < 0.6 ? Blocks.SULFUR : Blocks.MAGMA_BLOCK).defaultBlockState(), Block.UPDATE_CLIENTS);
			}
		}
		for (int i = 0; i < shapes.count; i++) {
			int lowest = Integer.MAX_VALUE;
			for (int y = Math.max(shapes.low[i], LAVA + 1); y <= shapes.high[i]; y++) {
				BlockState here = level.getBlockState(p.set(x, y, z));
				if (here.isAir() || here.canBeReplaced() || natural(here) || shapes.root[i] && here.is(Blocks.WATER)) {
					BlockState block = shapes.block[i];
					if (block.is(DeepBlocks.AMBER) && PlateMap.unit(PlateMap.mix(h, y, 77)) < 0.07) {
						block = DeepBlocks.INSECT_AMBER.defaultBlockState();
					}
					level.setBlock(p, block, Block.UPDATE_CLIENTS);
					lowest = Math.min(lowest, y);
				}
			}
			// roots hang from the giant roots
			if (shapes.root[i] && lowest != Integer.MAX_VALUE && level.getBlockState(p.set(x, lowest - 1, z)).isAir()) {
				double r = PlateMap.unit(PlateMap.mix(h, lowest, 5));
				if (r < 0.12) {
					level.setBlock(p, Blocks.HANGING_ROOTS.defaultBlockState(), Block.UPDATE_CLIENTS);
				} else if (r < 0.2) {
					level.setBlock(p, DeepBlocks.GLOWROOTS.defaultBlockState(), Block.UPDATE_CLIENTS);
				}
			}
		}

		roof(level, c, x, z, h, p);
	}

	/** The roof: the first open block under solid rock, scanning up, hung after the realm's character. */
	private static void roof(WorldGenLevel level, CavernModel.Info c, int x, int z, long h, BlockPos.MutableBlockPos p) {
		double hang = PlateMap.unit(h >>> 42);
		double chance = switch (c.theme) {
			case CavernModel.WILDS, CavernModel.MERE -> 0.05;
			case CavernModel.LUSH -> 0.09;
			case CavernModel.DRIPSTONE, CavernModel.TIDAL, CavernModel.AMBER -> 0.07;
			case CavernModel.FROZEN, CavernModel.ROOTS -> 0.08;
			case CavernModel.CRYSTAL, CavernModel.BRIMSTONE -> 0.045;
			default -> 0.03;
		};
		if (hang >= chance + 0.025) {
			return;
		}
		int ceiling = ceiling(level, x, z, Math.round(c.roof) + 8, Math.max(Math.round(c.floor) + 4, Math.round(c.roof) - 24), p);
		if (ceiling == Integer.MIN_VALUE || !natural(level.getBlockState(p.set(x, ceiling, z)))) {
			return;
		}
		int y = ceiling - 1;
		if (hang >= chance) {
			level.setBlock(p.set(x, y, z), GLOW_LICHEN_UP, Block.UPDATE_CLIENTS);
			return;
		}
		double kind = PlateMap.unit(PlateMap.mix(h, 9, 9));
		double length = PlateMap.unit(PlateMap.mix(h, 9, 10));
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
			case CavernModel.WILDS, CavernModel.MERE -> {
				if (kind < 0.15) {
					place(level, p.set(x, y, z), Blocks.HANGING_ROOTS.defaultBlockState());
				} else {
					vines(level, x, y, z, h, p);
				}
			}
			case CavernModel.DRIPSTONE, CavernModel.EMBER -> {
				level.setBlock(p.set(x, ceiling, z), Blocks.DRIPSTONE_BLOCK.defaultBlockState(), Block.UPDATE_CLIENTS);
				speleothem(level, x, y, z, Direction.DOWN, 1 + (int) (length * 6), Blocks.POINTED_DRIPSTONE, p);
			}
			case CavernModel.CRYSTAL -> {
				boolean prismite = kind < 0.45;
				level.setBlock(p.set(x, ceiling, z), (prismite ? ExpanseBlocks.PRISMITE_BLOCK : Blocks.AMETHYST_BLOCK).defaultBlockState(), Block.UPDATE_CLIENTS);
				BlockState crystal = (prismite ? ExpanseBlocks.PRISMITE_CLUSTER : Blocks.AMETHYST_CLUSTER).defaultBlockState();
				level.setBlock(p.set(x, y, z), crystal.setValue(AmethystClusterBlock.FACING, Direction.DOWN), Block.UPDATE_CLIENTS);
			}
			case CavernModel.FROZEN -> {
				level.setBlock(p.set(x, ceiling, z), (kind < 0.2 ? Blocks.BLUE_ICE : Blocks.PACKED_ICE).defaultBlockState(), Block.UPDATE_CLIENTS);
				speleothem(level, x, y, z, Direction.DOWN, 1 + (int) (length * 5), DeepBlocks.ICICLE, p);
			}
			case CavernModel.ROOTS -> {
				level.setBlock(p.set(x, ceiling, z), Blocks.ROOTED_DIRT.defaultBlockState(), Block.UPDATE_CLIENTS);
				if (kind < 0.55) {
					place(level, p.set(x, y, z), Blocks.HANGING_ROOTS.defaultBlockState());
				} else if (kind < 0.85) {
					place(level, p.set(x, y, z), DeepBlocks.GLOWROOTS.defaultBlockState());
				} else {
					vines(level, x, y, z, h, p);
				}
			}
			case CavernModel.TIDAL -> {
				if (kind < 0.4) {
					silk(level, x, y, z, 2 + (int) (length * 6), p);
				} else if (kind < 0.75) {
					level.setBlock(p.set(x, ceiling, z), Blocks.DRIPSTONE_BLOCK.defaultBlockState(), Block.UPDATE_CLIENTS);
					speleothem(level, x, y, z, Direction.DOWN, 1 + (int) (length * 4), Blocks.POINTED_DRIPSTONE, p);
				} else {
					level.setBlock(p.set(x, ceiling, z), (kind < 0.9 ? Blocks.PRISMARINE : Blocks.DARK_PRISMARINE).defaultBlockState(), Block.UPDATE_CLIENTS);
					level.setBlock(p.set(x, y, z), GLOW_LICHEN_UP, Block.UPDATE_CLIENTS);
				}
			}
			case CavernModel.AMBER -> {
				if (kind < 0.45) {
					level.setBlock(p.set(x, ceiling, z), DeepBlocks.AMBER.defaultBlockState(), Block.UPDATE_CLIENTS);
					speleothem(level, x, y, z, Direction.DOWN, 1 + (int) (length * 4), DeepBlocks.AMBER_SPIKE, p);
				} else {
					level.setBlock(p.set(x, ceiling, z), Blocks.RESIN_BLOCK.defaultBlockState(), Block.UPDATE_CLIENTS);
					level.setBlock(p.set(x, y, z), Blocks.RESIN_CLUMP.defaultBlockState().setValue(MultifaceBlock.getFaceProperty(Direction.UP), true),
						Block.UPDATE_CLIENTS);
				}
			}
			case CavernModel.BRIMSTONE -> {
				level.setBlock(p.set(x, ceiling, z), (kind < 0.7 ? Blocks.SULFUR : Blocks.BASALT).defaultBlockState(), Block.UPDATE_CLIENTS);
				if (kind < 0.7) {
					speleothem(level, x, y, z, Direction.DOWN, 1 + (int) (length * 3), Blocks.SULFUR_SPIKE, p);
				}
			}
			default -> level.setBlock(p.set(x, y, z), GLOW_LICHEN_UP, Block.UPDATE_CLIENTS);
		}
	}

	/** Ground at (x, y - 1, z), open above: made over and planted after the realm's character. */
	private static void ground(WorldGenLevel level, CavernModel.Info c, Shapes shapes, int x, int y, int z, double drift, double roll, double roll2,
		double roll3, BlockPos.MutableBlockPos p) {
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
						Direction facing = Direction.Plane.HORIZONTAL.getRandomDirection(RandomSource.create(PlateMap.mix(5, x, z)));
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
						speleothem(level, x, y, z, Direction.UP, 1 + (int) (roll2 * 5), Blocks.POINTED_DRIPSTONE, new BlockPos.MutableBlockPos());
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
			case CavernModel.FROZEN -> {
				if (shore && drift < 0.5 || drift < 0.4 && drift >= 0.12) {
					level.setBlock(below, Blocks.PACKED_ICE.defaultBlockState(), Block.UPDATE_CLIENTS);
				} else if (drift < 0.12) {
					level.setBlock(below, Blocks.BLUE_ICE.defaultBlockState(), Block.UPDATE_CLIENTS);
				} else {
					level.setBlock(below, Blocks.SNOW_BLOCK.defaultBlockState(), Block.UPDATE_CLIENTS);
					if (roll < 0.018) {
						place(level, at, ExpanseBlocks.FROSTBLOOM.defaultBlockState());
					} else if (roll < 0.4) {
						place(level, at, Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, 1 + (int) (roll2 * 2.5)));
					}
				}
			}
			case CavernModel.ROOTS -> {
				if (shore && drift < 0.4) {
					level.setBlock(below, Blocks.MUD.defaultBlockState(), Block.UPDATE_CLIENTS);
				} else if (drift > 0.55) {
					level.setBlock(below, MOSS, Block.UPDATE_CLIENTS);
					if (roll < 0.06) {
						place(level, at, Blocks.FERN.defaultBlockState());
					} else if (roll < 0.1) {
						place(level, at, ExpanseBlocks.GLOWCAP.defaultBlockState());
					} else if (roll < 0.13) {
						place(level, at, Blocks.FIREFLY_BUSH.defaultBlockState());
					} else if (roll < 0.16) {
						place(level, at, (roll2 < 0.5 ? Blocks.BROWN_MUSHROOM : Blocks.RED_MUSHROOM).defaultBlockState());
					} else if (roll < 0.4) {
						level.setBlock(at, MOSS_CARPET, Block.UPDATE_CLIENTS);
					}
				} else if (drift > 0.3) {
					level.setBlock(below, Blocks.ROOTED_DIRT.defaultBlockState(), Block.UPDATE_CLIENTS);
					if (roll < 0.05) {
						place(level, at, Blocks.BROWN_MUSHROOM.defaultBlockState());
					} else if (roll < 0.09) {
						place(level, at, Blocks.FERN.defaultBlockState());
					}
				} else {
					level.setBlock(below, (roll2 < 0.5 ? Blocks.PODZOL : Blocks.COARSE_DIRT).defaultBlockState(), Block.UPDATE_CLIENTS);
					if (roll < 0.15) {
						place(level, at, Blocks.LEAF_LITTER.defaultBlockState());
					}
				}
			}
			case CavernModel.FOSSIL -> {
				boolean red = PlateMap.unit(PlateMap.mix(0x5245_44L, c.hallX, c.hallZ)) < 0.35;
				BlockState sand = (red ? Blocks.RED_SAND : Blocks.SAND).defaultBlockState();
				BlockState sandstone = (red ? Blocks.RED_SANDSTONE : Blocks.SANDSTONE).defaultBlockState();
				if (drift > 0.35) {
					level.setBlock(below, sand, Block.UPDATE_CLIENTS);
					cover(level, p.set(x, y - 2, z), sand);
					cover(level, p.set(x, y - 3, z), sandstone);
					if (!red && roll < (shapes.fossil ? 0.05 : 0.004)) {
						level.setBlock(below, Blocks.SUSPICIOUS_SAND.defaultBlockState(), Block.UPDATE_CLIENTS);
						if (level.getBlockEntity(below) instanceof BrushableBlockEntity brushable) {
							brushable.setLootTable(BuiltInLootTables.DESERT_PYRAMID_ARCHAEOLOGY, below.asLong());
						}
					} else if (roll < 0.02) {
						place(level, at, Blocks.DEAD_BUSH.defaultBlockState());
					} else if (roll < 0.023) {
						level.setBlock(at, Blocks.BONE_BLOCK.defaultBlockState(), Block.UPDATE_CLIENTS);
					}
				} else if (drift > 0.15) {
					level.setBlock(below, sandstone, Block.UPDATE_CLIENTS);
				} else {
					level.setBlock(below, (red ? Blocks.SMOOTH_RED_SANDSTONE : Blocks.SMOOTH_SANDSTONE).defaultBlockState(), Block.UPDATE_CLIENTS);
				}
			}
			case CavernModel.TIDAL -> {
				if (shore) {
					level.setBlock(below, (drift > 0.5 ? Blocks.SAND : Blocks.GRAVEL).defaultBlockState(), Block.UPDATE_CLIENTS);
				} else if (drift < 0.07) {
					level.setBlock(below, (roll2 < 0.3 ? Blocks.DARK_PRISMARINE : Blocks.PRISMARINE).defaultBlockState(), Block.UPDATE_CLIENTS);
				} else if (drift < 0.35) {
					level.setBlock(below, Blocks.CALCITE.defaultBlockState(), Block.UPDATE_CLIENTS);
				} else if (drift < 0.6) {
					level.setBlock(below, Blocks.DRIPSTONE_BLOCK.defaultBlockState(), Block.UPDATE_CLIENTS);
				}
				if (!shore) {
					tidePool(level, x, y - 1, z, roll, roll2, roll3, p);
				}
			}
			case CavernModel.AMBER -> {
				if (shore) {
					level.setBlock(below, (roll2 < 0.5 ? Blocks.HONEYCOMB_BLOCK : Blocks.CLAY).defaultBlockState(), Block.UPDATE_CLIENTS);
				} else if (drift > 0.6) {
					level.setBlock(below, Blocks.PODZOL.defaultBlockState(), Block.UPDATE_CLIENTS);
					if (roll < 0.04) {
						place(level, at, Blocks.FERN.defaultBlockState());
					} else if (roll < 0.07) {
						place(level, at, Blocks.BROWN_MUSHROOM.defaultBlockState());
					}
				} else if (drift > 0.35) {
					level.setBlock(below, Blocks.COARSE_DIRT.defaultBlockState(), Block.UPDATE_CLIENTS);
				} else if (roll2 < 0.5) {
					level.setBlock(below, Blocks.RESIN_BLOCK.defaultBlockState(), Block.UPDATE_CLIENTS);
				}
				if (roll3 < 0.02) {
					level.setBlock(below, DeepBlocks.AMBER.defaultBlockState(), Block.UPDATE_CLIENTS);
				} else if (roll3 < 0.024) {
					level.setBlock(below, DeepBlocks.INSECT_AMBER.defaultBlockState(), Block.UPDATE_CLIENTS);
				}
				if (roll >= 0.9 && roll < 0.97 && level.getBlockState(at).isAir()) {
					level.setBlock(at, Blocks.RESIN_CLUMP.defaultBlockState().setValue(MultifaceBlock.getFaceProperty(Direction.DOWN), true),
						Block.UPDATE_CLIENTS);
				}
			}
			case CavernModel.BRIMSTONE -> {
				if (shore && drift > 0.4) {
					level.setBlock(below, (drift > 0.85 ? Blocks.MAGMA_BLOCK : Blocks.SULFUR).defaultBlockState(), Block.UPDATE_CLIENTS);
				} else if (drift > 0.62) {
					level.setBlock(below, Blocks.SULFUR.defaultBlockState(), Block.UPDATE_CLIENTS);
					if (roll < 0.04) {
						speleothem(level, x, y, z, Direction.UP, 1 + (int) (roll2 * 2), Blocks.SULFUR_SPIKE, new BlockPos.MutableBlockPos());
					}
				} else if (drift < 0.25) {
					level.setBlock(below, Blocks.TUFF.defaultBlockState(), Block.UPDATE_CLIENTS);
				} else {
					level.setBlock(below, Blocks.SMOOTH_BASALT.defaultBlockState(), Block.UPDATE_CLIENTS);
				}
				// now and then a vent: lava welling up in a pit ringed by solid ground
				if (!shore && roll3 < 0.0025 && enclosed(level, x, y - 1, z, p)) {
					level.setBlock(below, Blocks.LAVA.defaultBlockState(), Block.UPDATE_CLIENTS);
					cover(level, p.set(x, y - 2, z), Blocks.MAGMA_BLOCK.defaultBlockState());
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
	 * A tide pool sunk in the ground block at (x, y, z), on a terrace's flat top: water where the ground round
	 * about holds it (every side solid or water at that height, and the deeper block too for a pool two deep),
	 * with sea pickles, seagrass and coral in it.
	 */
	private static void tidePool(WorldGenLevel level, int x, int y, int z, double roll, double roll2, double roll3, BlockPos.MutableBlockPos p) {
		// pools in patches
		double patch = 0.5 + 0.5 * Math.sin(x * 0.19 + 2.1 * Math.cos(z * 0.13)) * Math.cos(z * 0.17 + 1.4 * Math.sin(x * 0.11));
		if (patch < 0.62 || !enclosed(level, x, y, z, p)) {
			return;
		}
		boolean deep = patch > 0.75 && enclosed(level, x, y - 1, z, p);
		BlockState water = Blocks.WATER.defaultBlockState();
		int bottom = deep ? y - 1 : y;
		if (deep) {
			level.setBlock(p.set(x, y, z), water, Block.UPDATE_CLIENTS);
		}
		BlockState life = water;
		if (roll3 < 0.08) {
			life = Blocks.SEA_PICKLE.defaultBlockState().setValue(SeaPickleBlock.PICKLES, 1 + (int) (roll2 * 3.9));
		} else if (roll3 < 0.3) {
			life = Blocks.SEAGRASS.defaultBlockState();
		} else if (roll3 < 0.34) {
			Block[] fans = {Blocks.TUBE_CORAL_FAN, Blocks.BRAIN_CORAL_FAN, Blocks.BUBBLE_CORAL_FAN, Blocks.FIRE_CORAL_FAN, Blocks.HORN_CORAL_FAN};
			life = fans[(int) (roll2 * 4.99)].defaultBlockState();
		}
		level.setBlock(p.set(x, bottom, z), life, Block.UPDATE_CLIENTS);
		if (roll < 0.05) {
			Block[] corals = {Blocks.TUBE_CORAL_BLOCK, Blocks.BRAIN_CORAL_BLOCK, Blocks.BUBBLE_CORAL_BLOCK, Blocks.FIRE_CORAL_BLOCK, Blocks.HORN_CORAL_BLOCK};
			cover(level, p.set(x, bottom - 1, z), corals[(int) (roll2 * 4.99)].defaultBlockState());
		}
	}

	/** Whether every side of (x, y, z) is solid ground or water, so a fluid there stays put. */
	private static boolean enclosed(WorldGenLevel level, int x, int y, int z, BlockPos.MutableBlockPos p) {
		for (Direction d : Direction.Plane.HORIZONTAL) {
			BlockState side = level.getBlockState(p.set(x + d.getStepX(), y, z + d.getStepZ()));
			if (!side.isSolidRender() && !side.is(Blocks.WATER)) {
				return false;
			}
		}
		return level.getBlockState(p.set(x, y - 1, z)).isSolidRender();
	}

	/**
	 * A lake bed under water whose surface block is at y, after the realm's character: sand, clay or gravel
	 * with seagrass and sea pickles; frozen over; lily pads; kelp and coral; honeycomb; sulfur and geysers.
	 */
	private static void lakeBed(WorldGenLevel level, CavernModel.Info c, int x, int y, int z, double drift, double roll, double roll2,
		BlockPos.MutableBlockPos p) {
		int theme = c.theme;
		if (theme == CavernModel.FROZEN && drift < 0.93) {
			level.setBlock(p.set(x, y, z), Blocks.ICE.defaultBlockState(), Block.UPDATE_CLIENTS);
		}
		int bed = y - 1;
		while (bed > y - 24 && level.getBlockState(p.set(x, bed, z)).is(Blocks.WATER)) {
			bed--;
		}
		BlockState floor = level.getBlockState(p.set(x, bed, z));
		if (!floor.isSolidRender() || !natural(floor)) {
			return;
		}
		int depth = y - bed;
		BlockState cover = switch (theme) {
			case CavernModel.LUSH -> Blocks.CLAY.defaultBlockState();
			case CavernModel.ROOTS -> (roll2 < 0.6 ? Blocks.MUD : Blocks.CLAY).defaultBlockState();
			case CavernModel.FROZEN -> (roll2 < 0.5 ? Blocks.GRAVEL : Blocks.PACKED_ICE).defaultBlockState();
			case CavernModel.AMBER -> (roll2 < 0.35 ? Blocks.HONEY_BLOCK : roll2 < 0.55 ? Blocks.HONEYCOMB_BLOCK : Blocks.CLAY).defaultBlockState();
			case CavernModel.BRIMSTONE -> (roll2 < 0.55 ? Blocks.SULFUR : roll2 < 0.7 ? Blocks.MAGMA_BLOCK : Blocks.TUFF).defaultBlockState();
			default -> (roll2 < 0.5 ? Blocks.SAND : roll2 < 0.8 ? Blocks.GRAVEL : Blocks.CLAY).defaultBlockState();
		};
		level.setBlock(p.set(x, bed, z), cover, Block.UPDATE_CLIENTS);
		p.set(x, bed + 1, z);
		switch (theme) {
			case CavernModel.FROZEN, CavernModel.AMBER -> {
			}
			case CavernModel.BRIMSTONE -> {
				// a geyser: potent sulfur over magma under shallow water erupts now and then
				if (roll < 0.03 && depth <= 4) {
					level.setBlock(p.set(x, bed, z), Blocks.POTENT_SULFUR.defaultBlockState().setValue(PotentSulfurBlock.STATE, PotentSulfurState.DORMANT),
						Block.UPDATE_CLIENTS);
					level.setBlock(p.set(x, bed - 1, z), Blocks.MAGMA_BLOCK.defaultBlockState(), Block.UPDATE_CLIENTS);
				} else if (roll < 0.06) {
					level.setBlock(p.set(x, bed, z), Blocks.POTENT_SULFUR.defaultBlockState().setValue(PotentSulfurBlock.STATE, PotentSulfurState.WET),
						Block.UPDATE_CLIENTS);
				}
			}
			case CavernModel.ROOTS -> {
				if (roll < 0.06 && depth <= 3 && level.getBlockState(p.set(x, y + 1, z)).isAir()) {
					level.setBlock(p, Blocks.LILY_PAD.defaultBlockState(), Block.UPDATE_CLIENTS);
				}
			}
			case CavernModel.LUSH -> {
				if (roll < 0.06 && depth <= 4 && depth >= 2) {
					Direction facing = Direction.Plane.HORIZONTAL.getRandomDirection(RandomSource.create(PlateMap.mix(6, x, z)));
					BigDripleafBlock.placeWithRandomHeight(level, RandomSource.create(PlateMap.mix(7, x, z)), p.immutable(), facing);
				} else if (roll < 0.3) {
					level.setBlock(p, Blocks.SEAGRASS.defaultBlockState(), Block.UPDATE_CLIENTS);
				}
			}
			case CavernModel.TIDAL -> {
				if (roll < 0.07) {
					level.setBlock(p, Blocks.SEA_PICKLE.defaultBlockState().setValue(SeaPickleBlock.PICKLES, 1 + (int) (roll2 * 3.9)), Block.UPDATE_CLIENTS);
				} else if (roll < 0.19 && depth >= 3) {
					// kelp, from the bed nearly to the surface
					int length = 1 + (int) (PlateMap.unit(PlateMap.mix(x, z, 8)) * (depth - 2));
					for (int k = 0; k < length; k++) {
						level.setBlock(p.set(x, bed + 1 + k, z), (k == length - 1 ? Blocks.KELP : Blocks.KELP_PLANT).defaultBlockState(), Block.UPDATE_CLIENTS);
					}
				} else if (roll < 0.45) {
					level.setBlock(p, Blocks.SEAGRASS.defaultBlockState(), Block.UPDATE_CLIENTS);
				}
			}
			default -> {
				if (roll < 0.05) {
					level.setBlock(p, Blocks.SEA_PICKLE.defaultBlockState().setValue(SeaPickleBlock.PICKLES, 1 + (int) (roll2 * 3.9)), Block.UPDATE_CLIENTS);
				} else if (roll < 0.3 && level.getBlockState(p.above()).is(Blocks.WATER)) {
					level.setBlock(p, Blocks.SEAGRASS.defaultBlockState(), Block.UPDATE_CLIENTS);
				}
			}
		}
	}

	// ------------------------------------------------------------------ the great shapes of the realms

	/** The great shapes of a realm's character that reach column (x, z). */
	private static void shapes(CavernModel caverns, int theme, int x, int z, Shapes s) {
		switch (theme) {
			case CavernModel.ROOTS -> roots(caverns, x, z, s);
			case CavernModel.FOSSIL -> fossils(caverns, x, z, s);
			case CavernModel.AMBER -> boulders(caverns, x, z, s);
			case CavernModel.BRIMSTONE -> causeway(caverns, x, z, s);
			default -> {
			}
		}
	}

	/**
	 * The giant roots of the root hollows: thick roots of deeproot coming down out of the vault and curving out
	 * across the floor as buttresses, and others hanging in a sagging arch from one part of the vault to another.
	 * Each is a curve from a cell of {@value #ROOT_CELL} blocks, measured against every column it passes.
	 */
	private static void roots(CavernModel caverns, int x, int z, Shapes s) {
		int cx = Math.floorDiv(x, ROOT_CELL);
		int cz = Math.floorDiv(z, ROOT_CELL);
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				long h = PlateMap.mix(0x524F_4F54L, cx + dx, cz + dz);
				if (PlateMap.unit(h) >= 0.5) {
					continue;
				}
				double ax = (cx + dx + 0.15 + 0.7 * PlateMap.unit(h >>> 21)) * ROOT_CELL;
				double az = (cz + dz + 0.15 + 0.7 * PlateMap.unit(h >>> 42)) * ROOT_CELL;
				double angle = Mth.TWO_PI * PlateMap.unit(h >>> 7);
				boolean arch = PlateMap.unit(h >>> 13) < 0.3;
				double reach = arch ? 24 + 20 * PlateMap.unit(h >>> 17) : 14 + 18 * PlateMap.unit(h >>> 17);
				double bx = ax + Math.cos(angle) * reach;
				double bz = az + Math.sin(angle) * reach;
				if (segment(x, z, ax, az, bx, bz) > 6) {
					continue;
				}
				CavernModel.Info a = caverns.info((int) Math.floor(ax), (int) Math.floor(az));
				if (!a.hall || a.hallEdge < 4) {
					continue;
				}
				double ya = a.roof + 3;
				double floorA = a.floor;
				CavernModel.Info b = caverns.info((int) Math.floor(bx), (int) Math.floor(bz));
				if (!b.hall || b.hallEdge < 4) {
					continue;
				}
				double yb = arch ? b.roof + 3 : b.floor - 2;
				double floorB = b.floor;
				double low = Math.max(floorA, floorB);
				if (Math.min(ya, arch ? yb : ya) - low < 18) {
					continue;
				}
				double r0 = 1.4 + 1.6 * PlateMap.unit(h >>> 25);
				double mx;
				double my;
				double mz;
				if (arch) {
					mx = (ax + bx) / 2;
					mz = (az + bz) / 2;
					double sag = low + 0.45 * (Math.min(ya, yb) - low);
					my = 2 * sag - 0.5 * (ya + yb);
				} else {
					// leaving the vault steeply, meeting the floor at a slant
					mx = ax + 0.15 * (bx - ax);
					mz = az + 0.15 * (bz - az);
					my = yb + 0.35 * (ya - yb);
				}
				double lowest = Double.MAX_VALUE;
				double highest = -Double.MAX_VALUE;
				double best = Double.MAX_VALUE;
				double tx = 0;
				double ty = 1;
				double tz = 0;
				for (int i = 0; i <= 40; i++) {
					double t = i / 40.0;
					double px = bezier(ax, mx, bx, t);
					double pz = bezier(az, mz, bz, t);
					double r = arch ? r0 * (1 - 0.4 * Math.sin(Math.PI * t)) : r0 * (1 - 0.55 * t);
					double d = Math.hypot(x + 0.5 - px, z + 0.5 - pz);
					if (d < r + 0.3) {
						double py = bezier(ya, my, yb, t);
						double half = Math.sqrt(Math.max(0.3, r * r - d * d));
						lowest = Math.min(lowest, py - half);
						highest = Math.max(highest, py + half);
						if (d < best) {
							best = d;
							tx = 2 * (1 - t) * (mx - ax) + 2 * t * (bx - mx);
							ty = 2 * (1 - t) * (my - ya) + 2 * t * (yb - my);
							tz = 2 * (1 - t) * (mz - az) + 2 * t * (bz - mz);
						}
					}
				}
				if (lowest <= highest) {
					Direction.Axis axis = Math.abs(ty) > 0.8 * Math.max(Math.abs(tx), Math.abs(tz)) ? Direction.Axis.Y
						: Math.abs(tx) > Math.abs(tz) ? Direction.Axis.X : Direction.Axis.Z;
					s.add((int) Math.round(lowest), (int) Math.round(highest), DeepBlocks.DEEPROOT.defaultBlockState().setValue(RotatedPillarBlock.AXIS, axis),
						true);
				}
			}
		}
	}

	/**
	 * The fossil deeps' giants: a skeleton of bone blocks lying half buried in the sand, twenty to forty blocks
	 * long, at most one in each cell of {@value #FOSSIL_CELL} blocks: a spine arched over the hips, a cage of
	 * ribs, a skull, a long tail and four legs splayed on the ground.
	 */
	private static void fossils(CavernModel caverns, int x, int z, Shapes s) {
		int cx = Math.floorDiv(x, FOSSIL_CELL);
		int cz = Math.floorDiv(z, FOSSIL_CELL);
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				long h = PlateMap.mix(0x464F_5353L, cx + dx, cz + dz);
				if (PlateMap.unit(h) >= 0.42) {
					continue;
				}
				double ax = (cx + dx + 0.25 + 0.5 * PlateMap.unit(h >>> 21)) * FOSSIL_CELL;
				double az = (cz + dz + 0.25 + 0.5 * PlateMap.unit(h >>> 42)) * FOSSIL_CELL;
				double length = 20 + 18 * PlateMap.unit(h >>> 7);
				double angle = Mth.TWO_PI * PlateMap.unit(h >>> 13);
				double ca = Math.cos(angle);
				double sa = Math.sin(angle);
				double u = (x + 0.5 - ax) * ca + (z + 0.5 - az) * sa + length / 2;
				double v = -(x + 0.5 - ax) * sa + (z + 0.5 - az) * ca;
				double width = 0.22 * length;
				if (u < -3 || u > length + 3 || Math.abs(v) > width + 0.3 * length) {
					continue;
				}
				// level ground under it, inside the realm and clear of water
				double g0 = Double.MAX_VALUE;
				double g1 = -Double.MAX_VALUE;
				boolean ok = true;
				for (double f : new double[]{0.1, 0.5, 0.92}) {
					double along = f * length - length / 2;
					CavernModel.Info at = caverns.info((int) Math.floor(ax + along * ca), (int) Math.floor(az + along * sa));
					if (!at.hall || at.hallEdge < 8 || at.liquid() > at.floor - 1) {
						ok = false;
						break;
					}
					g0 = Math.min(g0, at.floor);
					g1 = Math.max(g1, at.floor);
				}
				if (!ok || g1 - g0 > 5) {
					continue;
				}
				s.fossil = true;
				bones(s, u, v, length, width, (int) Math.floor(g0) - 1, Math.abs(ca) > Math.abs(sa));
			}
		}
	}

	private static void bones(Shapes s, double u, double v, double length, double width, int base, boolean alongX) {
		BlockState along = Blocks.BONE_BLOCK.defaultBlockState().setValue(RotatedPillarBlock.AXIS, alongX ? Direction.Axis.X : Direction.Axis.Z);
		BlockState across = Blocks.BONE_BLOCK.defaultBlockState().setValue(RotatedPillarBlock.AXIS, alongX ? Direction.Axis.Z : Direction.Axis.X);
		BlockState upright = Blocks.BONE_BLOCK.defaultBlockState();
		double spine = 0.2 * length + 1;
		// the spine and tail
		if (Math.abs(v) < 0.75 && u >= 0 && u <= 0.88 * length) {
			int y = base + 1 + (int) Math.round(spine * profile(u / length));
			s.add(u > 0.3 * length && u < 0.75 * length ? y - 1 : y, y, along, false);
		}
		// the ribs
		for (double rib = 0.34 * length; rib <= 0.74 * length; rib += 2.4) {
			if (Math.abs(u - rib) >= 0.55) {
				continue;
			}
			double span = Math.max(2, width * (1 - 0.6 * sq((rib - 0.54 * length) / (0.22 * length))));
			double a = Math.abs(v);
			if (a > span + 0.3 || a < 0.75) {
				continue;
			}
			double top = spine * profile(rib / length);
			double inner = top * Math.sqrt(Math.max(0, 1 - sq(Math.min(1, (a - 0.6) / span))));
			double outer = top * Math.sqrt(Math.max(0, 1 - sq(Math.min(1, (a + 0.6) / span))));
			s.add(base + 1 + (int) Math.round(outer), base + 1 + (int) Math.round(inner), upright, false);
		}
		// the skull
		double ru = 0.06 * length + 1.2;
		double rv = 0.05 * length + 1;
		double ry = 0.045 * length + 1;
		double q = sq((u - 0.94 * length) / ru) + sq(v / rv);
		if (q < 1) {
			double sy = base + 1 + spine * 0.75;
			double half = ry * Math.sqrt(1 - q);
			int top = (int) Math.round(sy + half);
			int bottom = (int) Math.round(sy - half);
			if (q > 0.5) {
				s.add(bottom, top, upright, false);
			} else {
				s.add(top, top, along, false);
				s.add(bottom, bottom, along, false);
			}
		}
		// the legs, splayed out on the ground
		for (double leg : new double[]{0.38, 0.7}) {
			for (int side = -1; side <= 1; side += 2) {
				double u0 = leg * length;
				double v0 = side * 0.6 * width;
				double u1 = u0 + 0.08 * length;
				double v1 = side * (0.6 * width + 0.26 * length);
				if (segment(u, v, u0, v0, u1, v1) < 0.7) {
					s.add(base + 1, base + 1, across, false);
				}
			}
		}
	}

	/** The height of a skeleton's back along it, as a share of its tallest: low at the tail, high over the hips. */
	private static double profile(double t) {
		if (t < 0.35) {
			return 0.1 + 0.9 * Math.sin(t / 0.35 * Math.PI / 2);
		}
		if (t < 0.7) {
			return 1 - 0.1 * (t - 0.35) / 0.35;
		}
		return 0.9 - 0.15 * Math.min(1, (t - 0.7) / 0.18);
	}

	/** Amber boulders: lumps of amber half sunk in the ground of the amber hollows, insects caught in some of it. */
	private static void boulders(CavernModel caverns, int x, int z, Shapes s) {
		int cx = Math.floorDiv(x, BOULDER_CELL);
		int cz = Math.floorDiv(z, BOULDER_CELL);
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				long h = PlateMap.mix(0x414D_4245L, cx + dx, cz + dz);
				if (PlateMap.unit(h) >= 0.45) {
					continue;
				}
				double ax = (cx + dx + 0.2 + 0.6 * PlateMap.unit(h >>> 21)) * BOULDER_CELL;
				double az = (cz + dz + 0.2 + 0.6 * PlateMap.unit(h >>> 42)) * BOULDER_CELL;
				double r = 2.2 + 2.8 * PlateMap.unit(h >>> 7);
				double d = Math.hypot(x + 0.5 - ax, z + 0.5 - az);
				if (d >= r) {
					continue;
				}
				CavernModel.Info at = caverns.info((int) Math.floor(ax), (int) Math.floor(az));
				if (!at.hall || at.hallEdge < 4 || at.liquid() > at.floor) {
					continue;
				}
				double cy = at.floor + 0.3 * r;
				double half = 0.85 * r * Math.sqrt(1 - sq(d / r));
				s.add((int) Math.floor(cy - half), (int) Math.round(cy + half), DeepBlocks.AMBER.defaultBlockState(), false);
			}
		}
	}

	/**
	 * The basalt causeways of the brimstone vaults: in fields across the floor, columns of basalt a few blocks
	 * across, each standing to its own height, with narrow cracks between them.
	 */
	private static void causeway(CavernModel caverns, int x, int z, Shapes s) {
		double field = Math.sin(x * 0.031 + 1.7 * Math.cos(z * 0.023)) * Math.cos(z * 0.029 + 0.8 * Math.sin(x * 0.019));
		if (field < -0.15) {
			return;
		}
		double gx = (x + 0.5) / CAUSEWAY_CELL;
		double gz = (z + 0.5) / CAUSEWAY_CELL;
		int ix = Mth.floor(gx);
		int iz = Mth.floor(gz);
		double d1 = Double.MAX_VALUE;
		double d2 = Double.MAX_VALUE;
		double fx = 0;
		double fz = 0;
		long id = 0;
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				long h = PlateMap.mix(0x4341_5553L, ix + dx, iz + dz);
				double px = ix + dx + 0.15 + 0.7 * PlateMap.unit(h);
				double pz = iz + dz + 0.15 + 0.7 * PlateMap.unit(h >>> 21);
				double d = Math.hypot(gx - px, gz - pz);
				if (d < d1) {
					d2 = d1;
					d1 = d;
					fx = px;
					fz = pz;
					id = h;
				} else if (d < d2) {
					d2 = d;
				}
			}
		}
		CavernModel.Info cell = caverns.info((int) Math.floor(fx * CAUSEWAY_CELL), (int) Math.floor(fz * CAUSEWAY_CELL));
		if (!cell.hall || cell.hallEdge < 6 || cell.liquid() > cell.floor - 1) {
			return;
		}
		int bump = 1 + (int) (PlateMap.unit(id >>> 33) * Math.min(1, field + 0.4) * 4.0);
		int top = Math.round(cell.floor) + bump;
		if (d2 - d1 < 0.12) {
			top -= 1;
		}
		s.causeway = top;
	}

	// ------------------------------------------------------------------ the features that spread

	/**
	 * Giant glowcaps, in the mushroom realms (and a few in the lush, lake and root realms): a pale stem under a
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
				double chance = switch (c.theme) {
					case CavernModel.WILDS -> 0.55;
					case CavernModel.ROOTS -> 0.14;
					case CavernModel.LUSH, CavernModel.MERE -> 0.1;
					default -> 0;
				};
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

	/**
	 * Spikes standing on realm floors, one at most in each {@value #SPIKE_CELL}-block cell, placed by the chunk
	 * its foot stands in: stalagmite fields of dripstone, ice spikes, sandstone hoodoos under a cap rock, cones of
	 * amber and of sulfur, each with a point of its own kind; amethyst spires in the geode vaults.
	 */
	private static void spikes(WorldGenLevel level, ChunkPos pos, CavernModel caverns, BlockPos.MutableBlockPos p) {
		int c0x = Math.floorDiv(pos.getMinBlockX(), SPIKE_CELL);
		int c0z = Math.floorDiv(pos.getMinBlockZ(), SPIKE_CELL);
		for (int cx = c0x; cx <= Math.floorDiv(pos.getMaxBlockX(), SPIKE_CELL); cx++) {
			for (int cz = c0z; cz <= Math.floorDiv(pos.getMaxBlockZ(), SPIKE_CELL); cz++) {
				long h = PlateMap.mix(0x5350_494BL, cx, cz);
				int x = cx * SPIKE_CELL + 1 + (int) (PlateMap.unit(h >>> 8) * (SPIKE_CELL - 2));
				int z = cz * SPIKE_CELL + 1 + (int) (PlateMap.unit(h >>> 30) * (SPIKE_CELL - 2));
				if (x < pos.getMinBlockX() || x > pos.getMaxBlockX() || z < pos.getMinBlockZ() || z > pos.getMaxBlockZ()) {
					continue;
				}
				CavernModel.Info c = caverns.info(x, z);
				if (!c.hall || c.hallEdge < 6) {
					continue;
				}
				int theme = c.theme;
				double chance = switch (theme) {
					case CavernModel.DRIPSTONE -> 0.5;
					case CavernModel.FROZEN -> 0.3;
					case CavernModel.BRIMSTONE -> 0.22;
					case CavernModel.AMBER -> 0.2;
					case CavernModel.FOSSIL -> 0.14;
					case CavernModel.TIDAL -> 0.14;
					case CavernModel.EMBER, CavernModel.CRYSTAL -> 0.08;
					default -> 0;
				};
				if (PlateMap.unit(h) >= chance) {
					continue;
				}
				int y = Math.min(Math.round(c.roof) - 1, Math.round(c.floor) + 14);
				int bottom = Math.round(c.floor) - 10;
				while (y > bottom && level.getBlockState(p.set(x, y - 1, z)).isAir()) {
					y--;
				}
				BlockState ground = level.getBlockState(p.set(x, y - 1, z));
				if (y <= bottom || y < LAVA + 2 || !ground.isSolidRender() || !natural(ground)) {
					continue;
				}
				int height = 3 + (int) (PlateMap.unit(h >>> 16) * (theme == CavernModel.DRIPSTONE ? 13 : 8));
				double radius = 0.9 + PlateMap.unit(h >>> 40) * (theme == CavernModel.DRIPSTONE ? 1.8 : 1.3);
				// room overhead
				boolean room = true;
				for (int dy = 0; dy < height + 3; dy++) {
					BlockState s = level.getBlockState(p.set(x, y + dy, z));
					if (!s.isAir() && !s.canBeReplaced()) {
						room = false;
						break;
					}
				}
				if (!room) {
					continue;
				}
				if (theme == CavernModel.FOSSIL) {
					hoodoo(level, x, y, z, height + 3, h, c.hallX, c.hallZ, p);
					continue;
				}
				BlockState body;
				Block point;
				switch (theme) {
					case CavernModel.FROZEN -> {
						body = Blocks.PACKED_ICE.defaultBlockState();
						point = DeepBlocks.ICICLE;
					}
					case CavernModel.BRIMSTONE -> {
						body = Blocks.SULFUR.defaultBlockState();
						point = Blocks.SULFUR_SPIKE;
					}
					case CavernModel.AMBER -> {
						body = DeepBlocks.AMBER.defaultBlockState();
						point = DeepBlocks.AMBER_SPIKE;
					}
					case CavernModel.EMBER -> {
						body = Blocks.BASALT.defaultBlockState();
						point = Blocks.POINTED_DRIPSTONE;
					}
					case CavernModel.CRYSTAL -> {
						body = Blocks.AMETHYST_BLOCK.defaultBlockState();
						point = null;
					}
					default -> {
						body = Blocks.DRIPSTONE_BLOCK.defaultBlockState();
						point = Blocks.POINTED_DRIPSTONE;
					}
				}
				int top = cone(level, x, y, z, height, radius, body, p);
				if (point != null) {
					speleothem(level, x, top + 1, z, Direction.UP, 1 + (int) (PlateMap.unit(PlateMap.mix(h, 11, 11)) * 3), point, p);
				} else {
					level.setBlock(p.set(x, top + 1, z), Blocks.AMETHYST_CLUSTER.defaultBlockState(), Block.UPDATE_CLIENTS);
				}
			}
		}
	}

	/** A cone of {@code body} standing on the ground at y, sunk two blocks in; returns the y of its top block. */
	private static int cone(WorldGenLevel level, int x, int y, int z, int height, double radius, BlockState body, BlockPos.MutableBlockPos p) {
		int reach = (int) Math.ceil(radius);
		int top = y;
		for (int dy = -2; dy < height; dy++) {
			double r = dy < 0 ? radius : radius * Math.pow(1 - dy / (double) height, 0.8);
			for (int dx = -reach; dx <= reach; dx++) {
				for (int dz = -reach; dz <= reach; dz++) {
					if (dx * dx + dz * dz > r * r + 0.3) {
						continue;
					}
					BlockState s = level.getBlockState(p.set(x + dx, y + dy, z + dz));
					if (s.isAir() || s.canBeReplaced() || dy < 0 && natural(s)) {
						level.setBlock(p, body, Block.UPDATE_CLIENTS);
						if (dx == 0 && dz == 0) {
							top = Math.max(top, y + dy);
						}
					}
				}
			}
		}
		return top;
	}

	/** A hoodoo: a pillar of sandstone, wider at its foot, under a cap rock of the other colour. */
	private static void hoodoo(WorldGenLevel level, int x, int y, int z, int height, long h, int hallX, int hallZ, BlockPos.MutableBlockPos p) {
		boolean red = PlateMap.unit(PlateMap.mix(0x5245_44L, hallX, hallZ)) < 0.35;
		BlockState pillar = (red ? Blocks.SMOOTH_RED_SANDSTONE : Blocks.SMOOTH_SANDSTONE).defaultBlockState();
		BlockState band = (red ? Blocks.RED_SANDSTONE : Blocks.SANDSTONE).defaultBlockState();
		BlockState cap = (red ? Blocks.SANDSTONE : Blocks.RED_SANDSTONE).defaultBlockState();
		double capRadius = 1.6 + PlateMap.unit(PlateMap.mix(h, 12, 12)) * 1.2;
		for (int dy = -2; dy < height; dy++) {
			boolean capped = dy >= height - 2;
			double r = capped ? capRadius : dy < 2 ? 1.8 : 1.1;
			BlockState block = capped ? cap : (y + dy) % 4 == 0 ? band : pillar;
			for (int dx = -2; dx <= 2; dx++) {
				for (int dz = -2; dz <= 2; dz++) {
					if (dx * dx + dz * dz > r * r + 0.3) {
						continue;
					}
					BlockState s = level.getBlockState(p.set(x + dx, y + dy, z + dz));
					if (s.isAir() || s.canBeReplaced() || dy < 0 && natural(s)) {
						level.setBlock(p, block, Block.UPDATE_CLIENTS);
					}
				}
			}
		}
	}

	// ------------------------------------------------------------------ pieces

	/** A speleothem of {@code block} (pointed dripstone, an icicle...), pointing {@code tip}, {@code length} long from (x, y, z). */
	private static void speleothem(WorldGenLevel level, int x, int y, int z, Direction tip, int length, Block block, BlockPos.MutableBlockPos p) {
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
			BlockState s = block.defaultBlockState().setValue(SpeleothemBlock.TIP_DIRECTION, tip).setValue(SpeleothemBlock.THICKNESS, t);
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

	/** Glowworm silk hanging from (x, y, z) down, beaded with light, {@code length} long at most. */
	private static void silk(WorldGenLevel level, int x, int y, int z, int length, BlockPos.MutableBlockPos p) {
		for (int k = 0; k < length; k++) {
			if (!level.getBlockState(p.set(x, y - k, z)).isAir()) {
				length = k;
				break;
			}
		}
		for (int k = 0; k < length; k++) {
			level.setBlock(p.set(x, y - k, z), DeepBlocks.GLOWWORM_SILK.defaultBlockState().setValue(HangingMossBlock.TIP, k == length - 1),
				Block.UPDATE_CLIENTS);
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

	/** Rock and earth as the land (or this decoration) made them, never anything built. */
	private static boolean natural(BlockState s) {
		return s.is(BlockTags.BASE_STONE_OVERWORLD) || s.is(BlockTags.DIRT) || s.is(BlockTags.SAND) || s.is(Blocks.GRAVEL) || s.is(Blocks.CLAY)
			|| s.is(Blocks.DRIPSTONE_BLOCK) || s.is(Blocks.CALCITE) || s.is(Blocks.SMOOTH_BASALT) || s.is(Blocks.BASALT) || s.is(Blocks.SANDSTONE)
			|| s.is(Blocks.RED_SANDSTONE) || s.is(Blocks.SNOW_BLOCK) || s.is(Blocks.PACKED_ICE) || s.is(Blocks.SULFUR) || s.is(Blocks.MAGMA_BLOCK)
			|| s.is(Blocks.COAL_ORE) || s.is(Blocks.DEEPSLATE_COAL_ORE) || s.is(BlockTags.IRON_ORES) || s.is(BlockTags.COPPER_ORES);
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

	/** Sets the block where there is only air, a carpet or vines. */
	private static void set(WorldGenLevel level, BlockPos pos, BlockState state) {
		BlockState here = level.getBlockState(pos);
		if (here.isAir() || here.is(Blocks.MOSS_CARPET) || here.is(Blocks.CAVE_VINES) || here.is(Blocks.CAVE_VINES_PLANT)) {
			level.setBlock(pos, state, Block.UPDATE_CLIENTS);
		}
	}

	/** Makes natural ground over into {@code state}; anything else is left. */
	private static void cover(WorldGenLevel level, BlockPos pos, BlockState state) {
		if (natural(level.getBlockState(pos))) {
			level.setBlock(pos, state, Block.UPDATE_CLIENTS);
		}
	}

	/** Distance from (x, z) to the segment from (ax, az) to (bx, bz). */
	private static double segment(double x, double z, double ax, double az, double bx, double bz) {
		double vx = bx - ax;
		double vz = bz - az;
		double t = Mth.clamp(((x - ax) * vx + (z - az) * vz) / Math.max(1.0E-6, vx * vx + vz * vz), 0, 1);
		return Math.hypot(x - ax - t * vx, z - az - t * vz);
	}

	private static double bezier(double a, double m, double b, double t) {
		return (1 - t) * (1 - t) * a + 2 * t * (1 - t) * m + t * t * b;
	}

	private static double sq(double v) {
		return v * v;
	}
}
