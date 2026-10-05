package dev.voidmc.expanse.world.feature;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.voidmc.expanse.api.LandClaims;
import dev.voidmc.expanse.world.StructureClearance;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.util.valueproviders.IntProviders;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * A fishpond, as the Bohemian countryside is strewn with them: a shallow pond dug into gently falling ground and held
 * by an earth dam across its lower end, the water a block below the dam's grassed crest. By the dam stands the sluice
 * (the "monk"): a wooden box tower in the water that lets the pond down when the fish are taken, a plank walk out to
 * it from the crest. The reeds, lily pads and the alders and willows along its banks come later, from the water's-edge
 * features, as they come to any water.
 *
 * <p>The pond is laid out from a short survey: the ground must fall no more than a few blocks across it, all of it
 * dry natural ground, clear of buildings and of any land a settlement has claimed ({@link LandClaims}). The long axis
 * runs downhill, the dam across the downhill end. The water level comes from the rim: the median ground round the
 * pond less a block, so the uphill shore is a low cut bank and the dam makes up the rest.
 */
public record FishpondFeature(IntProvider length, IntProvider width) implements Feature {
	public static final MapCodec<FishpondFeature> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
		IntProviders.codec(6, 13).fieldOf("half_length").forGetter(FishpondFeature::length),
		IntProviders.codec(4, 11).fieldOf("half_width").forGetter(FishpondFeature::width)
	).apply(i, FishpondFeature::new));

	/** Everything stays within this many blocks of the origin, so within the chunks a feature may write. */
	private static final int REACH = 15;
	private static final int BERM = 2;
	private static final BlockState WATER = Blocks.WATER.defaultBlockState();
	private static final BlockState DIRT = Blocks.DIRT.defaultBlockState();
	private static final BlockState GRASS = Blocks.GRASS_BLOCK.defaultBlockState();
	private static final BlockState AIR = Blocks.AIR.defaultBlockState();

	@Override
	public MapCodec<FishpondFeature> codec() {
		return CODEC;
	}

	/** Dev counters (the client tour prints them): tries and ponds dug. */
	public static final java.util.concurrent.atomic.AtomicInteger TRIES = new java.util.concurrent.atomic.AtomicInteger();
	public static final java.util.concurrent.atomic.AtomicInteger DUG = new java.util.concurrent.atomic.AtomicInteger();
	/** (dev) where the first few were dug, and their water level */
	public static final List<BlockPos> WHERE = java.util.Collections.synchronizedList(new java.util.ArrayList<>());

	@Override
	public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
		TRIES.incrementAndGet();
		if (this.dig(level, random, origin)) {
			if (DUG.incrementAndGet() <= 8) {
				WHERE.add(new BlockPos(origin.getX(), level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, origin.getX(), origin.getZ()), origin.getZ()));
			}
			return true;
		}
		return false;
	}

	private boolean dig(WorldGenLevel level, RandomSource random, BlockPos origin) {
		int a = this.length.sample(random);
		int b = Math.min(a, this.width.sample(random));
		int ox = origin.getX();
		int oz = origin.getZ();
		// downhill: from the ground a few blocks out each way
		int east = ground(level, ox + 6, oz) - ground(level, ox - 6, oz);
		int south = ground(level, ox, oz + 6) - ground(level, ox, oz - 6);
		Direction down = Math.abs(east) >= Math.abs(south) ? (east < 0 ? Direction.EAST : Direction.WEST) : (south < 0 ? Direction.SOUTH : Direction.NORTH);
		int ux = down.getStepX();
		int uz = down.getStepZ();
		int vx = -uz;
		int vz = ux;
		int reach = Math.max(a, b) + BERM + 1;
		if (reach > REACH) {
			return false;
		}
		if (LandClaims.anyClaimed(level, ox - reach, oz - reach, ox + reach, oz + reach, 4)) {
			return false;
		}
		List<BoundingBox> buildings = StructureClearance.nearbyBoxes(level);
		if (!buildings.isEmpty() && StructureClearance.distance(buildings, ox, oz) < reach + 6) {
			return false;
		}

		// survey: dry natural ground, gentle; the rim's ground for the water level
		int lo = Integer.MAX_VALUE;
		int hi = Integer.MIN_VALUE;
		int[] rim = new int[64];
		int rims = 0;
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		for (int u = -reach; u <= reach; u++) {
			for (int v = -reach; v <= reach; v++) {
				float d = this.inside(u, v, a, b);
				if (d > 1 + (float) BERM / b) {
					continue;
				}
				int x = ox + u * ux + v * vx;
				int z = oz + u * uz + v * vz;
				int top = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z);
				if (top != level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, x, z)) {
					return false;  // water already here
				}
				BlockState under = level.getBlockState(p.set(x, top - 1, z));
				if (!under.is(BlockTags.SUBSTRATE_OVERWORLD)) {
					return false;
				}
				lo = Math.min(lo, top);
				hi = Math.max(hi, top);
				if (d > 0.85F && d <= 1.05F && rims < rim.length) {
					rim[rims++] = top;
				}
			}
		}
		if (hi - lo > 5 || rims < 8) {
			return false;
		}
		java.util.Arrays.sort(rim, 0, rims);
		int water = rim[rims / 2] - 2;   // the y of the top water block: a block under the median rim's ground

		// dig the pond, build the berm and the dam
		float damAt = a * 0.72F;
		for (int u = -reach; u <= reach; u++) {
			for (int v = -reach; v <= reach; v++) {
				float d = this.inside(u, v, a, b);
				if (u > damAt + BERM + 1 || d > 1 + (float) (BERM + 1) / b) {
					continue;
				}
				int x = ox + u * ux + v * vx;
				int z = oz + u * uz + v * vz;
				int top = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z);
				boolean pond = d <= 1 && u <= damAt;
				if (pond) {
					// shallow: deepest in the middle and by the dam, a couple of blocks at most up the shallows
					float depth = 1 + 2.4F * (1 - d * d) * (0.6F + 0.4F * Mth.clamp((u + a) / (2.0F * a), 0, 1));
					int bed = water - Math.round(depth);
					for (int y = Math.max(top, water + 1) + 2; y > water; y--) {
						level.setBlock(p.set(x, y, z), AIR, 2);
					}
					for (int y = water; y > bed; y--) {
						level.setBlock(p.set(x, y, z), WATER, 2);
					}
					level.setBlock(p.set(x, bed, z), random.nextFloat() < 0.6F ? Blocks.MUD.defaultBlockState()
						: random.nextBoolean() ? Blocks.CLAY.defaultBlockState() : Blocks.GRAVEL.defaultBlockState(), 2);
					for (int y = bed - 1; y >= bed - 2; y--) {
						if (level.getBlockState(p.set(x, y, z)).isAir()) {
							level.setBlock(p, DIRT, 2);
						}
					}
				} else {
					// the bank and the dam: solid up to the crest a block over the water, grassed
					boolean dam = u > damAt - 0.5F;
					int crest = water + 1;
					if (top - 1 < crest) {
						for (int y = top - 1; y <= crest; y++) {
							level.setBlock(p.set(x, y, z), y == crest ? GRASS : DIRT, 2);
						}
					} else if (dam && top - 1 > crest + 1) {
						continue;  // the ground is already higher than the dam needs to be
					} else if (!dam && top - 1 > crest + 1 && d <= 1 + 1.2F / b) {
						// the first ring of the uphill shore comes down to a step above the water, so the bank is
						// a low edge rather than a cut wall
						for (int y = top; y > crest + 1; y--) {
							level.setBlock(p.set(x, y, z), AIR, 2);
						}
						level.setBlock(p.set(x, crest + 1, z), GRASS, 2);
					}
					if (dam) {
						// the dam's downhill face slopes away rather than standing as a wall
						for (int k = 1; k <= 2; k++) {
							int fx = x + ux * k;
							int fz = z + uz * k;
							int ft = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, fx, fz);
							for (int y = ft; y < crest - k + 1; y++) {
								level.setBlock(p.set(fx, y, fz), y == crest - k ? GRASS : DIRT, 2);
							}
						}
					}
				}
			}
		}

		// the monk: a wooden sluice tower in the water by the middle of the dam, and a plank walk out to it
		int mu = Mth.floor(damAt) - 1;
		int mx = ox + mu * ux;
		int mz = oz + mu * uz;
		for (int y = water - 2; y <= water + 2; y++) {
			level.setBlock(p.set(mx, y, mz), y <= water + 1 ? Blocks.OAK_PLANKS.defaultBlockState()
				: Blocks.SPRUCE_TRAPDOOR.defaultBlockState().setValue(TrapDoorBlock.HALF, Half.BOTTOM), 2);
		}
		BlockState walk = Blocks.SPRUCE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM);
		for (int k = 1; k <= 3; k++) {
			int wx = mx + ux * k;
			int wz = mz + uz * k;
			if (level.getBlockState(p.set(wx, water + 1, wz)).isAir()) {
				level.setBlock(p, walk, 2);
			} else {
				break;
			}
		}
		return true;
	}

	/** How far (u, v) is out from the pond's middle, 1 on its shore: an ellipse, blunt at the dam end. */
	private float inside(int u, int v, int a, int b) {
		float fu = u / (float) a;
		float fv = v / (float) b;
		return Mth.sqrt(fu * fu + fv * fv);
	}

	private static int ground(WorldGenLevel level, int x, int z) {
		return level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z);
	}
}
