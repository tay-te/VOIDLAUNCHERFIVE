package dev.voidmc.expanse.world.terrain;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.util.Interval;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.levelgen.densityfunction.DensityBuffer;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunction;
import net.minecraft.world.level.levelgen.densityfunction.DensitySampler;
import net.minecraft.world.level.levelgen.densityfunction.DensityVolume;
import net.minecraft.world.level.levelgen.densityfunction.DfRewriteRule;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;

/**
 * {@code expanse:terrain}: one value of the terrain model as a density function, for the noise router.
 * {@code output} picks which: the ground height, or one of the climate values the biome source reads.
 * All are two-dimensional (the same at every y), so they compile to samplers that fill a whole column
 * with one value.
 *
 * <p>Compiling is where the world seed arrives: the compile context hands out a random seeded for this
 * world, and its first long is the model's seed (see {@link TerrainModel#seedFor}, which the chunk
 * generator uses to find the same model).
 */
public record TerrainFunction(Output output) implements DensityFunction {
	public enum Output implements StringRepresentable {
		HEIGHT("height", -64, 384),
		CONTINENTS("continents", -1, 1),
		EROSION("erosion", -1, 1),
		RIDGES("ridges", -1, 5),
		LAPSE("lapse", -1, 0),
		TEMPERATURE("temperature", -3, 3),
		VEGETATION("vegetation", -3, 3);

		public static final Codec<Output> CODEC = StringRepresentable.fromEnum(Output::values);
		private final String name;
		private final Interval range;

		Output(String name, float min, float max) {
			this.name = name;
			this.range = Interval.of(min, max);
		}

		@Override
		public String getSerializedName() {
			return this.name;
		}
	}

	public static final MapCodec<TerrainFunction> CODEC = Output.CODEC.fieldOf("output").xmap(TerrainFunction::new, TerrainFunction::output);

	@Override
	public DensitySampler compileSampler(CompileContext context) {
		long seed = context.createRandom(TerrainModel.SEED_ID).nextLong();
		return new Sampler(TerrainModel.forSeed(seed), this.output);
	}

	@Override
	public DensityFunction rewriteChildren(DfRewriteRule rule) {
		return this;
	}

	@Override
	public Interval range() {
		return this.output.range;
	}

	@Override
	public @Axes int domainAxes() {
		return AXIS_X | AXIS_Z;
	}

	@Override
	public MapCodec<TerrainFunction> codec() {
		return CODEC;
	}

	private record Sampler(TerrainModel model, Output output) implements DensitySampler {
		@Override
		public void sampleVolume(SamplerContext context, DensityBuffer outputBuffer, DensityVolume volume) {
			for (int z = 0; z < volume.sizeZ(); z++) {
				int blockZ = volume.blockZ(z);
				for (int x = 0; x < volume.sizeX(); x++) {
					int blockX = volume.blockX(x);
					outputBuffer.setRange(volume.indexUnchecked(x, 0, z), volume.sizeY(), this.sampleValue(context, blockX, 0, blockZ));
				}
			}
		}

		@Override
		public float sampleValue(SamplerContext context, int blockX, int blockY, int blockZ) {
			Column c = this.model.sample(blockX, blockZ);
			return switch (this.output) {
				case HEIGHT -> c.height;
				case CONTINENTS -> c.continentalness;
				case EROSION -> c.erosion;
				case RIDGES -> c.weirdness;
				case LAPSE -> c.lapse;
				case TEMPERATURE -> c.temperature;
				case VEGETATION -> c.humidity;
			};
		}
	}
}
