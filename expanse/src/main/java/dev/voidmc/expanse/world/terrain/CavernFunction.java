package dev.voidmc.expanse.world.terrain;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.util.Interval;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunction;
import net.minecraft.world.level.levelgen.densityfunction.DensitySampler;
import net.minecraft.world.level.levelgen.densityfunction.DfRewriteRule;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;

/**
 * {@code expanse:caverns}: the {@link CavernModel} as density functions.
 *
 * <ul>
 *   <li>{@code "output": "carve"} (the default): its great tunnels and realms as a density, negative inside
 *       open space and positive in the rock, on the same scale as the terrain density (a block is worth
 *       0.08). The Earth noise settings take the minimum of this and the rest of the final density, inside
 *       vanilla's interpolation, so it only ever carves.</li>
 *   <li>{@code "output": "fine"}: the same for the passages, chambers, fissures and ways in, which are too
 *       small for vanilla's 4 x 8 x 4 cells; the Earth final density interpolates it on a finer grid of its
 *       own and takes the minimum with everything else.</li>
 *   <li>{@code "output": "dry"}: 1 where the aquifers are kept out ({@link CavernModel#dry}) and -1
 *       elsewhere, for the aquifers' {@code exclusion}.</li>
 * </ul>
 */
public record CavernFunction(String output) implements DensityFunction {
	public static final MapCodec<CavernFunction> CODEC = Codec.STRING.optionalFieldOf("output", "carve")
		.xmap(CavernFunction::new, CavernFunction::output);

	@Override
	public DensitySampler compileSampler(CompileContext context) {
		long seed = context.createRandom(TerrainModel.SEED_ID).nextLong();
		CavernModel model = TerrainModel.forSeed(seed).caverns();
		int kind = switch (this.output) {
			case "dry" -> 2;
			case "fine" -> 1;
			default -> 0;
		};
		return new DensitySampler() {
			/** A column at a time (the buffer runs y fastest), so the model's column is looked up once for all its heights. */
			@Override
			public void sampleVolume(SamplerContext ctx, net.minecraft.world.level.levelgen.densityfunction.DensityBuffer out,
				net.minecraft.world.level.levelgen.densityfunction.DensityVolume volume) {
				int index = 0;
				int height = volume.sizeY();
				for (int z = 0; z < volume.sizeZ(); z++) {
					int blockZ = volume.blockZ(z);
					for (int x = 0; x < volume.sizeX(); x++) {
						int blockX = volume.blockX(x);
						if (kind == 2) {
							out.setRange(index, height, model.dry(blockX, 0, blockZ) ? 1.0F : -1.0F);
							index += height;
							continue;
						}
						CavernModel.Info c = model.info(blockX, blockZ);
						if (kind == 1 && !c.fine) {
							out.setRange(index, height, 1.0F);
							index += height;
							continue;
						}
						for (int y = 0; y < height; y++) {
							int blockY = volume.blockY(y);
							float inside = kind == 1 ? model.fine(c, blockX, blockY, blockZ) : model.coarse(c, blockX, blockY, blockZ);
							out.set(index++, Mth.clamp(-0.08F * inside, -1.0F, 1.0F));
						}
					}
				}
			}

			@Override
			public float sampleValue(SamplerContext ctx, int x, int y, int z) {
				return switch (kind) {
					case 2 -> model.dry(x, y, z) ? 1.0F : -1.0F;
					case 1 -> Mth.clamp(-0.08F * model.fine(x, y, z), -1.0F, 1.0F);
					default -> Mth.clamp(-0.08F * model.coarse(x, y, z), -1.0F, 1.0F);
				};
			}
		};
	}

	@Override
	public DensityFunction rewriteChildren(DfRewriteRule rule) {
		return this;
	}

	@Override
	public Interval range() {
		return Interval.of(-1.0F, 1.0F);
	}

	@Override
	public @Axes int domainAxes() {
		return ALL_AXES;
	}

	@Override
	public MapCodec<CavernFunction> codec() {
		return CODEC;
	}
}
