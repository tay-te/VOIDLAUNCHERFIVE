package dev.voidmc.expanse.world.feature;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.voidmc.expanse.world.StructureClearance;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.synth.SimplexNoise;

/**
 * Hedgerows: the old field boundaries of farmed country, lines of hazel, hawthorn and elder (now and then an
 * oak left standing in the hedge) dividing the open land into fields of a few acres, as across the Bohemian
 * and English countryside.
 *
 * <p>The fields are the cells of a grid laid at one angle per world, each row of fields shifted its own way
 * along the row so the boundaries meet in T-junctions, not crossings, and the whole grid wavers a few
 * blocks so no hedge runs ruler-straight. A hedge follows each edge, a shrub every other block along it so
 * their crowns close into one line; some stretches are missing altogether and each has a gap where a gate or
 * a cart track went through. Only in farmed country (about half the open land, by a broad noise), only in
 * the biomes given, and never on water, steep ground or within a few blocks of anything built.
 *
 * <p>Placed once per chunk; it works column by column, so a hedge carries on unbroken from chunk to chunk.
 */
public record HedgerowFeature(Holder<PlacedFeature> shrub, Holder<PlacedFeature> tree, HolderSet<Biome> biomes, int field, float missing,
	float trees) implements Feature {
	public static final MapCodec<HedgerowFeature> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
		PlacedFeature.CODEC.fieldOf("shrub").forGetter(HedgerowFeature::shrub),
		PlacedFeature.CODEC.fieldOf("tree").forGetter(HedgerowFeature::tree),
		Biome.LIST_CODEC.fieldOf("biomes").forGetter(HedgerowFeature::biomes),
		Codec.intRange(24, 160).fieldOf("field").forGetter(HedgerowFeature::field),
		Codec.floatRange(0.0F, 1.0F).fieldOf("missing").forGetter(HedgerowFeature::missing),
		Codec.floatRange(0.0F, 1.0F).fieldOf("trees").forGetter(HedgerowFeature::trees)
	).apply(i, HedgerowFeature::new));

	private static volatile Noises noises;
	/** Dev counters (the client tour prints them): chunks looked at, columns on a hedge line, shrubs and trees placed, time. */
	public static final java.util.concurrent.atomic.AtomicInteger CHUNKS = new java.util.concurrent.atomic.AtomicInteger();
	public static final java.util.concurrent.atomic.AtomicInteger LINES = new java.util.concurrent.atomic.AtomicInteger();
	public static final java.util.concurrent.atomic.AtomicInteger PLACED = new java.util.concurrent.atomic.AtomicInteger();
	public static final java.util.concurrent.atomic.AtomicLong NANOS = new java.util.concurrent.atomic.AtomicLong();
	/** (dev) why line columns were passed over: biome, ground, slope, building, the shrub would not grow */
	public static final java.util.concurrent.atomic.AtomicIntegerArray SKIPPED = new java.util.concurrent.atomic.AtomicIntegerArray(5);

	private record Noises(long seed, double angle, SimplexNoise farmed, SimplexNoise waverU, SimplexNoise waverV) {
		static Noises of(long seed) {
			RandomSource r = RandomSource.create(seed ^ 0x6865_6467_6572_6F77L);
			return new Noises(seed, r.nextDouble() * Math.PI / 2, new SimplexNoise(r), new SimplexNoise(r), new SimplexNoise(r));
		}
	}

	@Override
	public MapCodec<HedgerowFeature> codec() {
		return CODEC;
	}

	@Override
	public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
		long start = System.nanoTime();
		try {
			return this.hedge(level, generator, random, origin);
		} finally {
			NANOS.addAndGet(System.nanoTime() - start);
			CHUNKS.incrementAndGet();
		}
	}

	private boolean hedge(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
		Noises n = noises;
		if (n == null || n.seed() != level.getSeed()) {
			noises = n = Noises.of(level.getSeed());
		}
		int x0 = origin.getX() & ~15;
		int z0 = origin.getZ() & ~15;
		double cos = Math.cos(n.angle());
		double sin = Math.sin(n.angle());
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		List<BoundingBox> buildings = StructureClearance.nearbyBoxes(level);
		boolean placed = false;
		for (int dx = 0; dx < 16; dx++) {
			for (int dz = 0; dz < 16; dz++) {
				int x = x0 + dx;
				int z = z0 + dz;
				if (noise(n.farmed(), x / 700.0, z / 700.0) < -0.1) {
					continue;  // wild country: no fields
				}
				// the grid, wavering, and each row of fields shifted along itself
				double u = x * cos + z * sin + 5 * noise(n.waverU(), x / 90.0, z / 90.0);
				double v = -x * sin + z * cos + 5 * noise(n.waverV(), x / 90.0, z / 90.0);
				long row = Math.floorDiv((long) Math.floor(u), this.field);
				double vs = v + this.field * unit(mix(level.getSeed(), row, 1));
				long col = Math.floorDiv((long) Math.floor(vs), this.field);
				double du = u - row * this.field;
				double dv = vs - col * this.field;
				// on a boundary across the row (between two fields of one row), or along it (between two rows)
				boolean across = dv < 1.0;
				boolean along = du < 1.0;
				if (!across && !along) {
					continue;
				}
				// one stretch of hedge: the edge of one field; a few are gone, and each has its gateway
				long stretch = across ? mix(level.getSeed() ^ 0x41, row, col) : mix(level.getSeed() ^ 0x42, row, col);
				if (unit(stretch) < this.missing) {
					continue;
				}
				double run = across ? du : dv;          // how far along the stretch
				double gate = 6 + (this.field - 12) * unit(stretch >>> 17);
				if (Math.abs(run - gate) < 2.5) {
					continue;
				}
				// a shrub every other block along it; now and then an old tree instead
				if (((long) Math.floor(run) & 1) != 0) {
					continue;
				}
				long spot = mix(stretch, (long) Math.floor(run / 12), 7);
				boolean oak = unit(spot) < this.trees && Math.abs(run - (Math.floor(run / 12) * 12 + 6)) < 1;
				LINES.incrementAndGet();
				if (dev.voidmc.expanse.api.LandClaims.claimed(level, x, z)) {
					continue;  // a settlement's planned land (VOID Hearth): its fields are its own
				}
				int top = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z);
				p.set(x, top, z);
				if (!this.biomes.contains(level.getBiome(p))) {
					SKIPPED.incrementAndGet(0);
					continue;
				}
				BlockState ground = level.getBlockState(p.below());
				if (!ground.is(net.minecraft.tags.BlockTags.SUBSTRATE_OVERWORLD) || !level.getFluidState(p).isEmpty()
					|| ground.hasProperty(BlockStateProperties.WATERLOGGED) && ground.getValue(BlockStateProperties.WATERLOGGED)) {
					SKIPPED.incrementAndGet(1);
					continue;
				}
				if (Math.abs(level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x + 2, z) - top) > 2
					|| Math.abs(level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z + 2) - top) > 2) {
					SKIPPED.incrementAndGet(2);
					continue;  // too steep for a field
				}
				if (!buildings.isEmpty() && StructureClearance.distance(buildings, x, z) < 5) {
					SKIPPED.incrementAndGet(3);
					continue;
				}
				Holder<PlacedFeature> what = oak ? this.tree : this.shrub;
				if (what.value().place(level, generator, random, p.immutable())) {
					placed = true;
					PLACED.incrementAndGet();
				} else {
					SKIPPED.incrementAndGet(4);
				}
			}
		}
		return placed;
	}

	private static double noise(SimplexNoise noise, double x, double z) {
		return Mth.clamp(noise.get(x, z), -1.0F, 1.0F);
	}

	private static long mix(long seed, long a, long b) {
		long h = seed ^ a * 0x9E37_79B9_7F4A_7C15L ^ b * 0xC2B2_AE3D_27D4_EB4FL;
		h = (h ^ h >>> 30) * 0xBF58_476D_1CE4_E5B9L;
		h = (h ^ h >>> 27) * 0x94D0_49BB_1331_11EBL;
		return h ^ h >>> 31;
	}

	private static double unit(long h) {
		return (h >>> 11) * 0x1.0p-53;
	}
}
