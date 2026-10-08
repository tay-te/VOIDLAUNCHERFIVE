package dev.voidmc.expanse.world.terrain;

import dev.voidmc.expanse.api.LandClaims;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.RailBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;

/**
 * What stands in the great mines ({@link GreatMines}): the pithead on the mountain (a deck of planks on stilts over
 * the shaft, a timber headframe over it carrying the winding chain, a spoil heap tipped down the slope), the ladder
 * up the shaft's north wall and timber sets round it every eight blocks, the stations at each level with their
 * railings, barrels and lanterns, and the drifts framed every four blocks (posts and a cap beam), rails down the
 * middle, a lantern every twelve blocks, the odd frame fallen in and cobwebs in the corners, ore showing in the walls
 * and at the faces. The great drift to the realm is framed to its mouth.
 *
 * <p>Every block is placed by the column it stands in, so the mine comes out the same whichever order the chunks are
 * decorated in. Nothing at the pithead where a settlement has claimed the land (Hearth may build its own).
 */
final class MineLife {
	private static final BlockState POST = Blocks.SPRUCE_LOG.defaultBlockState();
	private static final BlockState PLANKS = Blocks.SPRUCE_PLANKS.defaultBlockState();
	private static final BlockState FENCE = Blocks.SPRUCE_FENCE.defaultBlockState();
	private static final BlockState HANGING = Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true);

	private MineLife() {
	}

	static void decorate(WorldGenLevel level, ChunkAccess chunk, CavernModel caverns) {
		ChunkPos pos = chunk.getPos();
		int x0 = pos.getMinBlockX();
		int z0 = pos.getMinBlockZ();
		int reach = GreatMines.REACH;
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		for (int mx = Math.floorDiv(x0 - reach, GreatMines.CELL); mx <= Math.floorDiv(x0 + 15 + reach, GreatMines.CELL); mx++) {
			for (int mz = Math.floorDiv(z0 - reach, GreatMines.CELL); mz <= Math.floorDiv(z0 + 15 + reach, GreatMines.CELL); mz++) {
				GreatMines.Mine m = caverns.mine(mx, mz);
				if (!m.exists() || x0 + 15 < m.x() - reach || x0 > m.x() + reach || z0 + 15 < m.z() - reach || z0 > m.z() + reach) {
					continue;
				}
				boolean pithead = x0 + 15 >= m.x() - 32 && x0 <= m.x() + 32 && z0 + 15 >= m.z() - 32 && z0 <= m.z() + 32
					&& !LandClaims.anyClaimed(level, m.x() - 12, m.z() - 12, m.x() + 12, m.z() + 12, 6);
				double downhill = pithead ? downhill(caverns, m) : 0;
				List<BlockPos> fences = new ArrayList<>();
				for (int lz = 0; lz < 16; lz++) {
					for (int lx = 0; lx < 16; lx++) {
						column(level, m, x0 + lx, z0 + lz, pithead, downhill, fences, p);
					}
				}
				joinFences(level, fences);
			}
		}
	}

	private static void column(WorldGenLevel level, GreatMines.Mine m, int x, int z, boolean pithead, double downhill, List<BlockPos> fences,
		BlockPos.MutableBlockPos p) {
		int dx = x - m.x();
		int dz = z - m.z();
		int ch = Math.max(Math.abs(dx), Math.abs(dz));
		long h = PlateMap.mix(0x4D49_4E45L, x, z);
		if (pithead) {
			pithead(level, m, dx, dz, ch, x, z, h, downhill, fences, p);
		}
		if (ch <= 2) {
			shaft(level, m, dx, dz, ch, x, z, p);
		}
		for (GreatMines.Drift d : m.drifts()) {
			if (ch <= GreatMines.STATION) {
				station(level, m, d, dx, dz, ch, x, z, h, fences, p);
			}
			drift(level, m, d, dx, dz, ch, x, z, PlateMap.mix(h, d.floor(), d.alongX() ? d.sign() : 2 * d.sign()), p);
		}
	}

	// ------------------------------------------------------------------ the pithead

	/** The shaft house's outer wall (Chebyshev distance from the shaft's centre), and the deck's edge. */
	private static final int HOUSE = 5;
	private static final int DECK = 7;

	/** The height of the shaft house's roof over a column {@code dz} across from its ridge (which runs along x). */
	private static int roof(int deck, int dz) {
		return deck + 4 + (HOUSE + 1 - Math.abs(dz));
	}

	/**
	 * The pithead: a deck of planks fifteen blocks square on the mountain's shoulder at the shaft's mouth (the slope cut
	 * back over it, stilts under it, a railing round it open on the south), and on it the shaft house: plank walls on
	 * log corners, a door to the south, a gabled roof of spruce shingles, a windlass and barrels inside, a railing round
	 * the shaft. Out of its roof rises the headframe tower, boarded, with the sheave beam carrying the winding chain at
	 * its top under a little roof. Down the slope, the spoil heap of the rock they brought up.
	 */
	private static void pithead(WorldGenLevel level, GreatMines.Mine m, int dx, int dz, int ch, int x, int z, long h, double downhill,
		List<BlockPos> fences, BlockPos.MutableBlockPos p) {
		int deck = m.ground() + 1;
		if (ch <= DECK) {
			// cut back what stands over the deck (the slope, trees, snow)
			for (int y = deck + 1; y <= deck + 20; y++) {
				BlockState st = level.getBlockState(p.set(x, y, z));
				if (!st.isAir() && (CavernLife.natural(st) || st.is(BlockTags.LOGS) || st.is(BlockTags.LEAVES) || st.canBeReplaced()
					|| st.is(BlockTags.SNOW) || st.is(Blocks.POWDER_SNOW))) {
					level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
				}
			}
			if (ch >= 3) {
				level.setBlock(p.set(x, deck, z), PLANKS, Block.UPDATE_CLIENTS);
				// stilts at the deck's edge and the corners, down to the ground
				boolean corner = Math.abs(dx) == ch && Math.abs(dz) == ch;
				if ((ch == DECK || ch == HOUSE || ch == 3) && corner || ch == DECK && Math.floorMod(dx + dz, 3) == 0) {
					for (int y = deck - 1; y > deck - 40; y--) {
						BlockState st = level.getBlockState(p.set(x, y, z));
						if (!st.isAir() && !st.canBeReplaced()) {
							break;
						}
						level.setBlock(p, POST, Block.UPDATE_CLIENTS);
					}
				}
			}
			// the railing round the deck, open on the south where the way comes up
			if (ch == DECK && !(dz == DECK && Math.abs(dx) <= 1)) {
				fence(level, p.set(x, deck + 1, z), fences);
			}
			// the shaft house's walls, the door on the south, a window each side
			if (ch == HOUSE) {
				boolean corner = Math.abs(dx) == HOUSE && Math.abs(dz) == HOUSE;
				for (int y = deck + 1; y <= deck + 3; y++) {
					boolean door = dz == HOUSE && Math.abs(dx) <= 1 && y <= deck + 2;
					boolean window = y == deck + 2 && !corner && (Math.abs(dx) == HOUSE && Math.abs(dz) == 2 || dz == -HOUSE && Math.abs(dx) == 2);
					if (corner) {
						level.setBlock(p.set(x, y, z), POST, Block.UPDATE_CLIENTS);
					} else if (window) {
						Direction out = Math.abs(dx) == HOUSE ? (dx > 0 ? Direction.EAST : Direction.WEST) : Direction.NORTH;
						level.setBlock(p.set(x, y, z), Blocks.SPRUCE_TRAPDOOR.defaultBlockState().setValue(TrapDoorBlock.OPEN, true)
							.setValue(TrapDoorBlock.FACING, out), Block.UPDATE_CLIENTS);
					} else if (door) {
						level.setBlock(p.set(x, y, z), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
					} else {
						level.setBlock(p.set(x, y, z), y == deck + 3 ? Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState()
							.setValue(RotatedPillarBlock.AXIS, Math.abs(dz) == HOUSE ? Direction.Axis.X : Direction.Axis.Z) : PLANKS, Block.UPDATE_CLIENTS);
					}
				}
				// the gable ends, up under the roof
				if (Math.abs(dx) == HOUSE) {
					for (int y = deck + 4; y < roof(deck, dz); y++) {
						level.setBlock(p.set(x, y, z), PLANKS, Block.UPDATE_CLIENTS);
					}
				}
			}
			// the roof, a gable along x with eaves a block out; the tower comes up through it
			if (ch <= HOUSE + 1 && ch > 3) {
				int y = roof(deck, dz);
				BlockState shingle = dz == 0 ? PLANKS : Blocks.SPRUCE_STAIRS.defaultBlockState()
					.setValue(StairBlock.FACING, dz > 0 ? Direction.NORTH : Direction.SOUTH);
				level.setBlock(p.set(x, y, z), shingle, Block.UPDATE_CLIENTS);
			}
			// the headframe tower: posts at its corners from the deck to the top, boarded above the roof
			int top = deck + 16;
			if (Math.abs(dx) == 3 && Math.abs(dz) == 3) {
				for (int y = deck + 1; y < top; y++) {
					level.setBlock(p.set(x, y, z), POST, Block.UPDATE_CLIENTS);
				}
			} else if (ch == 3) {
				for (int y = roof(deck, dz) + (Math.abs(dz) == 3 ? 1 : 0); y < top; y++) {
					// louvres near the top, where the chain runs over the sheave
					boolean louvre = y == top - 2 && (Math.abs(dx) == 1 || Math.abs(dz) == 1);
					level.setBlock(p.set(x, y, z), louvre ? Blocks.SPRUCE_FENCE.defaultBlockState() : PLANKS, Block.UPDATE_CLIENTS);
				}
				// the railing round the shaft, open to the ladder
				if (!(dz == -3 && Math.abs(dx) <= 1)) {
					fence(level, p.set(x, deck + 1, z), fences);
				}
			}
			if (ch <= 3 && dz == 0) {
				// the sheave beam across the tower
				level.setBlock(p.set(x, top, z), Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState()
					.setValue(RotatedPillarBlock.AXIS, Direction.Axis.X), Block.UPDATE_CLIENTS);
			}
			if (ch <= 4) {
				// the tower's own little roof
				level.setBlock(p.set(x, top + 1, z), ch == 4 ? Blocks.SPRUCE_SLAB.defaultBlockState() : PLANKS, Block.UPDATE_CLIENTS);
			}
			// inside: the windlass on the north side by the ladder, barrels, lanterns
			if (dz == -4 && Math.abs(dx) <= 2) {
				level.setBlock(p.set(x, deck + 1, z), Math.abs(dx) == 2 ? POST : Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
				level.setBlock(p.set(x, deck + 2, z), Math.abs(dx) == 2 ? POST
					: Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.X), Block.UPDATE_CLIENTS);
			} else if (Math.abs(dx) == 4 && dz == -4 || dx == 4 && dz == 3) {
				level.setBlock(p.set(x, deck + 1, z), Blocks.BARREL.defaultBlockState(), Block.UPDATE_CLIENTS);
			} else if (Math.abs(dx) == 4 && dz == 3) {
				level.setBlock(p.set(x, roof(deck, dz) - 1, z), HANGING, Block.UPDATE_CLIENTS);
			} else if (dx == 2 && dz == 0) {
				level.setBlock(p.set(x, top - 1, z), HANGING, Block.UPDATE_CLIENTS);
			}
			// a lantern on a post by the door
			if (dx == 2 && dz == DECK - 1) {
				level.setBlock(p.set(x, deck + 1, z), Blocks.SPRUCE_FENCE.defaultBlockState(), Block.UPDATE_CLIENTS);
				level.setBlock(p.set(x, deck + 2, z), Blocks.LANTERN.defaultBlockState(), Block.UPDATE_CLIENTS);
			}
			return;
		}
		// the spoil heap, tipped down the slope
		double sx = 17 * Math.cos(downhill);
		double sz = 17 * Math.sin(downhill);
		double r = Math.sqrt(sq(dx - sx) + sq(dz - sz)) + 1.5 * PlateMap.unit(h);
		if (r < 10 && ch > DECK + 1) {
			int heap = (int) Math.round(7 * (1 - sq(r / 10)));
			int ground = level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, x, z);
			if (Math.abs(ground - deck) > 20) {
				return;
			}
			for (int k = 0; k < heap; k++) {
				BlockState at = level.getBlockState(p.set(x, ground + k, z));
				if (!at.isAir() && !at.canBeReplaced()) {
					continue;
				}
				double roll = PlateMap.unit(PlateMap.mix(h, k, 5));
				BlockState spoil = roll < 0.5 ? Blocks.GRAVEL.defaultBlockState() : roll < 0.8 ? Blocks.COBBLESTONE.defaultBlockState()
					: roll < 0.93 ? Blocks.TUFF.defaultBlockState() : Blocks.COARSE_DIRT.defaultBlockState();
				level.setBlock(p, spoil, Block.UPDATE_CLIENTS);
			}
		}
	}

	/** Which way the ground falls away from the shaft (radians, from +x toward +z), from the land round it. */
	private static double downhill(CavernModel caverns, GreatMines.Mine m) {
		TerrainModel t = caverns.terrain();
		float gx = t.sample(m.x() + 14, m.z()).height - t.sample(m.x() - 14, m.z()).height;
		float gz = t.sample(m.x(), m.z() + 14).height - t.sample(m.x(), m.z() - 14).height;
		return Math.atan2(-gz, -gx);
	}

	/** Puts a fence at (pos), to be joined up to what is round it once the chunk's mine work is done. */
	private static void fence(WorldGenLevel level, BlockPos pos, List<BlockPos> fences) {
		level.setBlock(pos, FENCE, Block.UPDATE_CLIENTS);
		fences.add(pos.immutable());
	}

	/**
	 * Joins the fences up: world generation places blocks without telling their neighbours, so a fence would stand as
	 * a bare post. Each is joined to the fences and solid faces beside it, and the fences beside it (perhaps in the
	 * chunk next door, placed earlier) are joined back to it.
	 */
	static void joinFences(WorldGenLevel level, List<BlockPos> fences) {
		for (BlockPos pos : fences) {
			BlockState st = level.getBlockState(pos);
			if (!(st.getBlock() instanceof FenceBlock)) {
				continue;
			}
			for (Direction d : Direction.Plane.HORIZONTAL) {
				BlockPos n = pos.relative(d);
				BlockState ns = level.getBlockState(n);
				boolean fence = ns.getBlock() instanceof FenceBlock;
				boolean join = fence || !ns.isAir() && ns.isFaceSturdy(level, n, d.getOpposite());
				st = st.setValue(CrossCollisionBlock.PROPERTY_BY_DIRECTION.get(d), join);
				if (fence) {
					level.setBlock(n, ns.setValue(CrossCollisionBlock.PROPERTY_BY_DIRECTION.get(d.getOpposite()), true), Block.UPDATE_CLIENTS);
				}
			}
			level.setBlock(pos, st, Block.UPDATE_CLIENTS);
		}
	}

	// ------------------------------------------------------------------ the shaft

	/**
	 * The shaft: the ladder up its north wall from the foot to the deck, timber sets round the walls every eight
	 * blocks (but where a level opens), and the winding chain down the middle from the headframe's sheave beam to a
	 * barrel hanging over the foot.
	 */
	private static void shaft(WorldGenLevel level, GreatMines.Mine m, int dx, int dz, int ch, int x, int z, BlockPos.MutableBlockPos p) {
		int deck = m.ground() + 1;
		int bottom = m.bottom();
		if (dx == 0 && dz == -2) {
			BlockState ladder = Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.SOUTH);
			for (int y = bottom; y <= deck; y++) {
				if (level.getBlockState(p.set(x, y, z)).isAir()) {
					level.setBlock(p, ladder, Block.UPDATE_CLIENTS);
					// where a level opens behind it, a post to hold it
					if (level.getBlockState(p.set(x, y, z - 1)).isAir()) {
						level.setBlock(p, POST, Block.UPDATE_CLIENTS);
					}
				}
			}
			return;
		}
		if (dx == 0 && dz == 0) {
			for (int y = deck + 11; y > bottom + 3; y--) {
				if (level.getBlockState(p.set(x, y, z)).isAir()) {
					level.setBlock(p, Blocks.IRON_CHAIN.defaultBlockState(), Block.UPDATE_CLIENTS);
				}
			}
			level.setBlock(p.set(x, bottom + 3, z), Blocks.BARREL.defaultBlockState(), Block.UPDATE_CLIENTS);
			return;
		}
		if (ch == 2) {
			BlockState set = ch == Math.abs(dx) && ch == Math.abs(dz) ? POST
				: Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Math.abs(dz) == 2 ? Direction.Axis.X : Direction.Axis.Z);
			for (int y = bottom + 8; y < deck - 2; y += 8) {
				if (!level.getBlockState(p.set(x, y, z)).isAir() || !level.getBlockState(p.set(x, y + 1, z)).isAir()
					|| !level.getBlockState(p.set(x, y - 1, z)).isAir()) {
					continue;   // a level opens here
				}
				level.setBlock(p.set(x, y, z), set, Block.UPDATE_CLIENTS);
			}
		}
	}

	// ------------------------------------------------------------------ the levels

	/**
	 * A station round the shaft at a drift's level: a plank floor, a railing round the shaft (open to the ladder and
	 * the drifts), barrels and a lantern in the corners. At the lowest station above the great drift, a chest.
	 */
	private static void station(WorldGenLevel level, GreatMines.Mine m, GreatMines.Drift d, int dx, int dz, int ch, int x, int z, long h,
		List<BlockPos> fences, BlockPos.MutableBlockPos p) {
		int floor = d.floor();
		if (ch <= 2 || !level.getBlockState(p.set(x, floor, z)).isAir()) {
			return;
		}
		BlockState under = level.getBlockState(p.set(x, floor - 1, z));
		if (CavernLife.natural(under)) {
			level.setBlock(p, PLANKS, Block.UPDATE_CLIENTS);
		}
		if (d.toRealm()) {
			return;
		}
		if (ch == 3 && Math.abs(dx) > 1 && Math.abs(dz) > 1) {
			fence(level, p.set(x, floor, z), fences);
		} else if (Math.abs(dx) == 5 && Math.abs(dz) == 5) {
			long corner = PlateMap.mix(h, floor, 11);
			if (dx > 0 && dz > 0) {
				level.setBlock(p.set(x, floor + GreatMines.STATION_HEIGHT - 1, z), HANGING, Block.UPDATE_CLIENTS);
			} else if (dx < 0 && dz > 0 && floor == lowestLevel(m)) {
				level.setBlock(p.set(x, floor, z), Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.EAST),
					Block.UPDATE_CLIENTS);
				if (level.getBlockEntity(p) instanceof RandomizableContainerBlockEntity chest) {
					chest.setLootTable(BuiltInLootTables.ABANDONED_MINESHAFT, PlateMap.mix(corner, x, z));
				}
			} else if (PlateMap.unit(corner) < 0.6) {
				level.setBlock(p.set(x, floor, z), Blocks.BARREL.defaultBlockState(), Block.UPDATE_CLIENTS);
				if (PlateMap.unit(corner >>> 20) < 0.5) {
					level.setBlock(p.set(x, floor + 1, z), Blocks.BARREL.defaultBlockState(), Block.UPDATE_CLIENTS);
				}
			}
		}
	}

	private static int lowestLevel(GreatMines.Mine m) {
		int low = Integer.MAX_VALUE;
		for (GreatMines.Drift d : m.drifts()) {
			if (!d.toRealm()) {
				low = Math.min(low, d.floor());
			}
		}
		return low;
	}

	/**
	 * One column of a drift: inside it the frames (every four blocks: posts at the walls, a cap beam across), rails
	 * down the middle of the working drifts, lanterns, cobwebs, a frame here and there fallen in; in its walls and
	 * at its face, ore.
	 */
	private static void drift(WorldGenLevel level, GreatMines.Mine m, GreatMines.Drift d, int dx, int dz, int ch, int x, int z, long h, BlockPos.MutableBlockPos p) {
		int along = d.along(dx, dz);
		int across = d.across(dx, dz);
		int floor = d.floor();
		int top = floor + d.height() - 1;
		if (along < GreatMines.STATION + 1 || along > d.length() + 1 || Math.abs(across) > d.half() + 1) {
			return;
		}
		// the rock round it: ore in the walls, and at the face where they stopped
		if (Math.abs(across) == d.half() + 1 || along == d.length() + 1) {
			double chance = along == d.length() + 1 ? 0.3 : 0.05;
			for (int y = floor; y <= top; y++) {
				if (PlateMap.unit(PlateMap.mix(h, y, 13)) < chance) {
					ore(level, p.set(x, y, z), y);
				}
			}
			return;
		}
		if (!level.getBlockState(p.set(x, floor, z)).isAir()) {
			return;
		}
		long frame = PlateMap.mix(h ^ 0x4652L, along, 0);
		// the lowest working level, where the water came in: dammed off at the station, flooded behind the dam
		if (!d.toRealm() && floor == lowestLevel(m) && PlateMap.unit(PlateMap.mix(0x464CL, m.x(), m.z())) < 0.5) {
			if (along == GreatMines.STATION + 2) {
				for (int y = floor; y <= floor + 1; y++) {
					level.setBlock(p.set(x, y, z), Blocks.COBBLESTONE.defaultBlockState(), Block.UPDATE_CLIENTS);
				}
				return;
			}
			if (along > GreatMines.STATION + 2) {
				for (int y = floor; y <= floor + 1; y++) {
					if (level.getBlockState(p.set(x, y, z)).isAir()) {
						level.setBlock(p, Blocks.WATER.defaultBlockState(), Block.UPDATE_CLIENTS);
					}
				}
			}
		}
		boolean fallen = !d.toRealm() && PlateMap.unit(PlateMap.mix(0x4641L, d.floor() * 31 + along, d.sign())) < 0.1;
		Direction.Axis cross = d.alongX() ? Direction.Axis.Z : Direction.Axis.X;
		if (along % 4 == 0) {
			if (fallen) {
				// the frame has come down: its cap beam lies across the floor, gravel spilled over it
				if (across == 0) {
					level.setBlock(p.set(x, floor, z), Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, cross),
						Block.UPDATE_CLIENTS);
				} else if (PlateMap.unit(frame) < 0.6) {
					level.setBlock(p.set(x, floor, z), Blocks.GRAVEL.defaultBlockState(), Block.UPDATE_CLIENTS);
				}
				return;
			}
			if (Math.abs(across) == d.half()) {
				for (int y = floor; y < top; y++) {
					set(level, p.set(x, y, z), POST);
				}
			}
			set(level, p.set(x, top, z), Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, cross));
			return;
		}
		if (across == 0 && !d.toRealm() && along % 12 == 2 && !fallen) {
			set(level, p.set(x, top, z), HANGING);
		}
		if (d.toRealm() && across == 0 && along % 8 == 2) {
			set(level, p.set(x, top, z), HANGING);
		}
		// rails down the middle of the working drifts, a length missing here and there
		if (across == 0 && !d.toRealm() && PlateMap.unit(PlateMap.mix(0x5241L, d.floor(), d.sign() * 3 + (d.alongX() ? 1 : 0))) < 0.7
			&& PlateMap.unit(frame >>> 7) > 0.08) {
			set(level, p.set(x, floor, z), Blocks.RAIL.defaultBlockState()
				.setValue(RailBlock.SHAPE, d.alongX() ? RailShape.EAST_WEST : RailShape.NORTH_SOUTH));
		}
		// cobwebs in the top corners, more of them further in
		if (Math.abs(across) == d.half() && PlateMap.unit(frame >>> 21) < 0.04 + 0.1 * along / Math.max(1, d.length())) {
			set(level, p.set(x, top, z), Blocks.COBWEB.defaultBlockState());
		}
	}

	/** Ore in the rock at (pos): coal and copper high in the mountain, iron below, gold and redstone deep. */
	private static void ore(WorldGenLevel level, BlockPos pos, int y) {
		BlockState here = level.getBlockState(pos);
		boolean deep = here.is(Blocks.DEEPSLATE) || here.is(Blocks.TUFF);
		if (!here.is(BlockTags.BASE_STONE_OVERWORLD)) {
			return;
		}
		long roll = PlateMap.mix(0x4F52L, pos.getX() * 7 + y, pos.getZ());
		double u = PlateMap.unit(roll);
		Block ore;
		if (y > 90) {
			ore = u < 0.55 ? (deep ? Blocks.DEEPSLATE_COAL_ORE : Blocks.COAL_ORE) : (deep ? Blocks.DEEPSLATE_COPPER_ORE : Blocks.COPPER_ORE);
		} else if (y > 20) {
			ore = u < 0.7 ? (deep ? Blocks.DEEPSLATE_IRON_ORE : Blocks.IRON_ORE) : (deep ? Blocks.DEEPSLATE_COPPER_ORE : Blocks.COPPER_ORE);
		} else {
			ore = u < 0.4 ? (deep ? Blocks.DEEPSLATE_GOLD_ORE : Blocks.GOLD_ORE) : u < 0.75 ? (deep ? Blocks.DEEPSLATE_IRON_ORE : Blocks.IRON_ORE)
				: (deep ? Blocks.DEEPSLATE_REDSTONE_ORE : Blocks.REDSTONE_ORE);
		}
		level.setBlock(pos, ore.defaultBlockState(), Block.UPDATE_CLIENTS);
	}

	/** Sets the block where there is only air. */
	private static void set(WorldGenLevel level, BlockPos pos, BlockState state) {
		if (level.getBlockState(pos).isAir()) {
			level.setBlock(pos, state, Block.UPDATE_CLIENTS);
		}
	}

	private static double sq(double v) {
		return v * v;
	}
}
