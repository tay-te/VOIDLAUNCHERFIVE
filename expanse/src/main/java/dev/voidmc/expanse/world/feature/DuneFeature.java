package dev.voidmc.expanse.world.feature;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderSet;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.synth.SimplexNoise;

/**
 * Sand dunes, laid over whatever the terrain generator made, one chunk at a time.
 *
 * <p>Dune height is a function of world position alone, so the ridges carry on unbroken from chunk
 * to chunk. The function:
 * <ul>
 *   <li>Ridges run across the world's prevailing wind, bent into long curves by domain warping.</li>
 *   <li>Each ridge has a long windward slope and a short, steep lee face, as real barchan and
 *       transverse dunes do.</li>
 *   <li>A second, slower noise swells and flattens whole dune fields.</li>
 *   <li>The height fades to nothing within a dozen blocks of the biome's edge, so dunes never end in
 *       a wall of sand.</li>
 * </ul>
 * It only builds on its own sand, and never in water.
 */
public record DuneFeature(BlockState sand, HolderSet<Biome> biomes, int maxHeight, float wavelength) implements Feature {
	public static final MapCodec<DuneFeature> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
		BlockState.CODEC.fieldOf("sand").forGetter(DuneFeature::sand),
		Biome.LIST_CODEC.fieldOf("biomes").forGetter(DuneFeature::biomes),
		Codec.intRange(1, 24).fieldOf("max_height").forGetter(DuneFeature::maxHeight),
		Codec.floatRange(8.0F, 256.0F).fieldOf("wavelength").forGetter(DuneFeature::wavelength)
	).apply(i, DuneFeature::new));

	private static final int EDGE = 12;
	private static volatile Noises noises;

	private record Noises(long seed, double windAngle, SimplexNoise wind, SimplexNoise warp, SimplexNoise field) {
		static Noises of(long seed) {
			RandomSource r = RandomSource.create(seed ^ 0x6475_6E65_7321L);
			return new Noises(seed, r.nextDouble() * Math.PI, new SimplexNoise(r), new SimplexNoise(r), new SimplexNoise(r));
		}
	}

	@Override
	public MapCodec<DuneFeature> codec() {
		return CODEC;
	}

	@Override
	public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
		Noises n = noises;
		if (n == null || n.seed() != level.getSeed()) {
			noises = n = Noises.of(level.getSeed());
		}
		int x0 = origin.getX() & ~15;
		int z0 = origin.getZ() & ~15;
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		boolean placed = false;
		for (int dx = 0; dx < 16; dx++) {
			for (int dz = 0; dz < 16; dz++) {
				int x = x0 + dx;
				int z = z0 + dz;
				int top = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z);
				if (!level.getBlockState(p.set(x, top - 1, z)).is(this.sand.getBlock())) {
					continue;
				}
				int h = Mth.floor(this.height(n, x, z) * this.edgeWeight(level, p.set(x, top, z)));
				for (int y = 0; y < h; y++) {
					level.setBlock(p.set(x, top + y, z), this.sand, 2);
					placed = true;
				}
			}
		}
		return placed;
	}

	private float height(Noises n, int x, int z) {
		// One prevailing wind per world. Its direction must not vary with position: rotating world
		// coordinates by an angle that drifts with position multiplies the ridge frequency by the
		// distance from the origin, and a thousand blocks out the dunes become a comb.
		double angle = n.windAngle();
		double across = (x * Math.cos(angle) + z * Math.sin(angle)) / this.wavelength;
		// Instead the ridge lines are bent by warping: a slow, wide meander so crests curve across a
		// whole dune field, and a quicker, smaller one so they wobble and fork.
		across += noise(n.wind(), x / 320.0, z / 320.0) * 1.6 + noise(n.warp(), x / 90.0, z / 90.0) * 0.35;
		double phase = across - Math.floor(across);
		// Long windward slope (three quarters of the wave), short steep lee face.
		double profile = phase < 0.75 ? phase / 0.75 : (1.0 - phase) / 0.25;
		profile = profile * profile * (3 - 2 * profile);
		double field = 0.55 + 0.45 * noise(n.field(), x / 260.0, z / 260.0);
		return (float) (profile * field * this.maxHeight);
	}

	/** Simplex noise brought to roughly [-1, 1]. */
	private static double noise(SimplexNoise noise, double x, double z) {
		return Mth.clamp(noise.get(x, z), -1.0F, 1.0F);
	}

	/** 1 deep inside the biome, falling to 0 at its edge, from the biome a short way off in each direction. */
	private float edgeWeight(WorldGenLevel level, BlockPos at) {
		int inside = 0;
		int[][] probes = {{EDGE, 0}, {-EDGE, 0}, {0, EDGE}, {0, -EDGE}, {EDGE / 2, EDGE / 2}, {-EDGE / 2, -EDGE / 2}, {EDGE / 2, -EDGE / 2}, {-EDGE / 2, EDGE / 2}};
		for (int[] o : probes) {
			if (this.biomes.contains(level.getBiome(at.offset(o[0], 0, o[1])))) {
				inside++;
			}
		}
		float w = inside / (float) probes.length;
		return w * w;
	}
}
