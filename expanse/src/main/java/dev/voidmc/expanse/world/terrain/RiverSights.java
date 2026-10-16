package dev.voidmc.expanse.world.terrain;

import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChainBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.chunk.ChunkAccess;

/**
 * The traces of the folk who used the rivers under the land: rope bridges slung across the river tunnels from ledge to
 * ledge (a walk of slabs sagging toward the middle, chains for hand ropes, posts at either end), and landings on their
 * banks (a jetty of planks on posts out over the water, a boat tied up beside it, barrels and a lantern on the ledge).
 * Long abandoned, mostly; but they say the rivers were roads once.
 *
 * <p>One at most in each {@value #CELL}-block cell (about two cells in three), where a river tunnel runs through it with
 * room over the water: a bridge or a landing at a point on the river's line, across the river along whichever axis
 * crosses it more squarely. Each column places its own blocks, so it comes out the same in any chunk order.
 */
public final class RiverSights {
	public static final int CELL = 192;

	/**
	 * A crossing or a landing at (x, z) on the river's line; {@code acrossX} if the river is crossed along x. {@code half}
	 * the river's half-width there, {@code water} its {@link CavernModel.Info#waterTop}, {@code side} the bank a landing
	 * is on (1 or -1).
	 */
	public record Sight(boolean exists, boolean bridge, int x, int z, boolean acrossX, int half, int water, int side) {
		static final Sight NONE = new Sight(false, false, 0, 0, false, 0, 0, 0);
	}

	private final CavernModel caverns;
	private final ConcurrentHashMap<Long, Sight> sights = new ConcurrentHashMap<>();
	private final long salt;

	RiverSights(CavernModel caverns, long salt) {
		this.caverns = caverns;
		this.salt = salt;
	}

	public Sight sight(int cx, int cz) {
		Long key = PlateMap.scramble((long) cx << 32 ^ cz & 0xFFFF_FFFFL);
		Sight s = this.sights.get(key);
		if (s == null) {
			s = this.make(cx, cz);
			if (this.sights.size() > 100_000) {
				this.sights.clear();
			}
			this.sights.putIfAbsent(key, s);
		}
		return s;
	}

	private Sight make(int cx, int cz) {
		long h = PlateMap.mix(0x5249_5645L ^ this.salt, cx, cz);
		double kind = PlateMap.unit(h);
		if (kind >= 0.65) {
			return Sight.NONE;
		}
		// a point on the river's line, if one runs through the cell (looked for in a scattered order)
		int start = (int) (PlateMap.unit(h >>> 21) * 144);
		for (int n = 0; n < 144; n++) {
			int k = (start + n * 37) % 144;
			int x = cx * CELL + 6 + (k % 12) * 16;
			int z = cz * CELL + 6 + (k / 12) * 16;
			CavernModel.Info c = this.caverns.info(x, z);
			if (!c.river || c.riverFalls || c.riverDist > 2 || c.tunnelRoof - c.riverWater < 9 || c.hall) {
				continue;
			}
			int half = Math.round(c.riverHalfWidth);
			int water = c.waterTop();
			// across: the axis along which the river's line falls away fastest on both sides
			float ex = this.caverns.info(x + 6, z).riverDist + this.caverns.info(x - 6, z).riverDist;
			float ez = this.caverns.info(x, z + 6).riverDist + this.caverns.info(x, z - 6).riverDist;
			boolean acrossX = ex > ez;
			// both banks open, not running into a realm or a fall
			boolean ok = true;
			for (int side = -1; side <= 1; side += 2) {
				int bx = acrossX ? x + side * (half + 2) : x;
				int bz = acrossX ? z : z + side * (half + 2);
				CavernModel.Info b = this.caverns.info(bx, bz);
				if (!b.river || b.riverFalls || b.hall || b.riverDist < b.riverHalfWidth || Math.abs(b.waterTop() - water) > 0) {
					ok = false;
				}
			}
			if (!ok || half > 26) {
				continue;
			}
			return new Sight(true, kind < 0.38, x, z, acrossX, half, water, PlateMap.unit(h >>> 40) < 0.5 ? 1 : -1);
		}
		return Sight.NONE;
	}

	// ------------------------------------------------------------------ what stands there

	static void decorate(WorldGenLevel level, ChunkAccess chunk, CavernModel caverns) {
		ChunkPos pos = chunk.getPos();
		int x0 = pos.getMinBlockX();
		int z0 = pos.getMinBlockZ();
		RiverSights sights = caverns.riverSights();
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		for (int cx = Math.floorDiv(x0 - 40, CELL); cx <= Math.floorDiv(x0 + 55, CELL); cx++) {
			for (int cz = Math.floorDiv(z0 - 40, CELL); cz <= Math.floorDiv(z0 + 55, CELL); cz++) {
				Sight s = sights.sight(cx, cz);
				if (!s.exists() || x0 + 15 < s.x() - 40 || x0 > s.x() + 40 || z0 + 15 < s.z() - 40 || z0 > s.z() + 40
					|| dev.voidmc.expanse.api.LandClaims.anyClaimed(level, s.x() - s.half() - 5, s.z() - s.half() - 5, s.x() + s.half() + 5,
						s.z() + s.half() + 5, 8)) {
					continue;     // (where a settlement has claimed the land, its own landing if any)
				}
				long h = PlateMap.mix(0x4252L, s.x(), s.z());
				for (int lz = 0; lz < 16; lz++) {
					for (int lx = 0; lx < 16; lx++) {
						int x = x0 + lx;
						int z = z0 + lz;
						int t = s.acrossX() ? x - s.x() : z - s.z();      // across the river
						int u = s.acrossX() ? z - s.z() : x - s.x();      // along it
						if (s.bridge()) {
							bridge(level, s, t, u, x, z, h, p);
						} else {
							landing(level, s, t * s.side(), u, x, z, h, p);
						}
					}
				}
			}
		}
	}

	/**
	 * A rope bridge: slabs along the line across, from ledge to ledge, sagging a half block over the middle third, a
	 * plank or two gone; chains either side for hand ropes, from posts at both ends.
	 */
	private static void bridge(WorldGenLevel level, Sight s, int t, int u, int x, int z, long h, BlockPos.MutableBlockPos p) {
		int reach = s.half() + 2;
		if (Math.abs(t) > reach || Math.abs(u) > 1) {
			return;
		}
		int water = s.water();
		boolean sag = Math.abs(t) < reach * 0.35F;
		int deck = sag ? water + 1 : water + 2;
		Direction.Axis axis = s.acrossX() ? Direction.Axis.X : Direction.Axis.Z;
		if (u == 0) {
			if (Math.abs(t) == reach || PlateMap.unit(PlateMap.mix(h, t, 1)) > 0.06) {
				set(level, p.set(x, deck, z), Blocks.SPRUCE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, sag ? SlabType.TOP : SlabType.BOTTOM));
			}
			return;
		}
		// the end posts, and the hand ropes between them
		if (Math.abs(t) == reach) {
			for (int y = water + 1; y <= water + 4; y++) {
				set(level, p.set(x, y, z), Blocks.SPRUCE_FENCE.defaultBlockState());
			}
			if (u == 1 && t > 0) {
				set(level, p.set(x, water + 5, z), Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, false));
			}
			return;
		}
		set(level, p.set(x, deck + (sag ? 2 : 1), z), Blocks.IRON_CHAIN.defaultBlockState().setValue(ChainBlock.AXIS, axis));
	}

	/**
	 * A landing on one bank: a jetty three wide from the ledge out over the water on posts, a boat tied up alongside,
	 * barrels and a lantern post on the ledge behind it. {@code t} runs from the river's line toward that bank.
	 */
	private static void landing(WorldGenLevel level, Sight s, int t, int u, int x, int z, long h, BlockPos.MutableBlockPos p) {
		int half = s.half();
		int water = s.water();
		int from = Math.max(1, half - 5);
		if (t < from - 1 || t > half + 4 || Math.abs(u) > 3) {
			return;
		}
		Direction.Axis cross = s.acrossX() ? Direction.Axis.Z : Direction.Axis.X;
		if (t >= from && t <= half + 1 && Math.abs(u) <= 1) {
			// the jetty, on posts at its corners and ends
			level.setBlock(p.set(x, water, z), Blocks.SPRUCE_PLANKS.defaultBlockState(), Block.UPDATE_CLIENTS);
			if (Math.abs(u) == 1 && (t == from || t == from + 3)) {
				for (int y = water - 1; y > water - 8; y--) {
					if (!level.getBlockState(p.set(x, y, z)).liquid() && !level.getBlockState(p).isAir()) {
						break;
					}
					level.setBlock(p, Blocks.SPRUCE_LOG.defaultBlockState(), Block.UPDATE_CLIENTS);
				}
				set(level, p.set(x, water + 1, z), Blocks.SPRUCE_FENCE.defaultBlockState());
			}
			return;
		}
		// the boat, tied up alongside
		if (t == from + 1 && u == 2 && level.getLevel() != null && level.getBlockState(p.set(x, water - 1, z)).liquid()) {
			var boat = net.minecraft.world.entity.EntityTypes.SPRUCE_BOAT.create(level.getLevel(), net.minecraft.world.entity.EntitySpawnReason.CHUNK_GENERATION);
			if (boat != null) {
				boat.snapTo(x + 0.5, water - 0.45, z + 0.5, s.acrossX() ? 0 : 90, 0);
				level.addFreshEntity(boat);
			}
			return;
		}
		// on the ledge: barrels, a crate of planks, the lantern post
		if (t >= half + 2) {
			int ground = ledge(level, x, z, water, p);
			if (ground == Integer.MIN_VALUE) {
				return;
			}
			if (u == -2 && t == half + 2) {
				for (int y = ground; y < ground + 3; y++) {
					set(level, p.set(x, y, z), Blocks.SPRUCE_FENCE.defaultBlockState());
				}
				set(level, p.set(x, ground + 3, z), Blocks.LANTERN.defaultBlockState());
			} else if (u >= 1 && t <= half + 3 && PlateMap.unit(PlateMap.mix(h, t * 7 + u, 3)) < 0.6) {
				set(level, p.set(x, ground, z), Blocks.BARREL.defaultBlockState());
			} else if (u == 0 && t == half + 3) {
				set(level, p.set(x, ground, z), Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, cross));
			}
		}
	}

	/** The first open block over the ledge's floor near the water's height, or MIN_VALUE. */
	private static int ledge(WorldGenLevel level, int x, int z, int water, BlockPos.MutableBlockPos p) {
		for (int y = water - 1; y <= water + 5; y++) {
			BlockState below = level.getBlockState(p.set(x, y - 1, z));
			if (level.getBlockState(p.set(x, y, z)).isAir() && below.isSolidRender()) {
				return y;
			}
		}
		return Integer.MIN_VALUE;
	}

	private static void set(WorldGenLevel level, BlockPos pos, BlockState state) {
		BlockState here = level.getBlockState(pos);
		if (here.isAir() || here.is(Blocks.MOSS_CARPET) || here.is(Blocks.GLOW_LICHEN)) {
			level.setBlock(pos, state, Block.UPDATE_CLIENTS);
		}
	}
}
