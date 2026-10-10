package dev.voidmc.expanse.world.terrain;

import dev.voidmc.expanse.Expanse;
import java.util.Arrays;
import java.util.function.Supplier;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.Beardifier;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.NoiseSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.densityfunction.DensityBufferPool;
import net.minecraft.world.level.levelgen.densityfunction.DensitySampler;
import net.minecraft.world.level.levelgen.densityfunction.DensitySamplerSet;
import net.minecraft.world.level.levelgen.densityfunction.DensityVolume;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;
import net.minecraft.world.level.levelgen.densityfunction.ScopedDensityBuffer;
import org.jspecify.annotations.Nullable;

/**
 * The noise generator's base heights ({@code NoiseBasedChunkGenerator.getBaseHeight}) for the Earth: the same
 * answers, every heightmap type of a column from one short look at it, and the answers kept.
 *
 * <p>Vanilla builds a {@code NoiseChunk} and its aquifer for the whole column, samples the final density for all
 * 704 blocks of it, and reads it from the top of the world down, block by block through the aquifer, until the
 * heightmap's test is met; over lowland some 500 of those blocks are sky, and it does it all again for each
 * heightmap type. This samples the final density only from a height it knows to be open sky down, in stretches,
 * reads every type's answer off the same blocks, asks the aquifer only about the few blocks it has a say in
 * (building it only then), and keeps the six answers by column. The blocks it reads come out as vanilla's:
 *
 * <ul>
 *   <li><b>The open sky.</b> Above {@code max(sea level + 5, the highest ground at the interpolation cell's four
 *       corners + }{@link #NOISE_REACH}{@code )}, rounded up to a cell boundary, every block is air. The Earth's
 *       final density (final_density.json) takes the minimum of the terrain with the caverns, squeezes it, takes
 *       the minimum with the fine caverns and adds the beardifier, which is 0 here (no structures in a base-height
 *       query); the caverns only lower it, and the squeeze keeps its sign. The terrain (terrain.json) is
 *       {@code 0.04 * (height - y) + 0.15 * base_3d_noise}, and base_3d_noise stays within 2.17
 *       ({@code BlendedNoise.range()}), so 8.2 blocks above the model's ground it is below 0 whatever the noise
 *       does. It is interpolated in 4 x 8 x 4 cells, so a block takes the ground of the four columns at its cell's
 *       corners: in a cell whose bottom is {@link #NOISE_REACH} blocks above the highest of those, every corner is
 *       below 0 by a clear margin, and so is every block between them. And there the aquifer answers air (next).</li>
 *   <li><b>No aquifer from sea level + 5 up.</b> The Earth keeps its aquifers out of everywhere (its
 *       {@code exclusion} is {@code expanse:earth/caverns_dry}, 1 everywhere: {@link CavernModel#dry}), so every
 *       aquifer centre's fluid status is the sea's (level 63), the lava sea's (level -54), or dry (the status at
 *       {@code DimensionType.WAY_BELOW_MIN_Y}): no fluid stands above y 63. For an open block (density at most 0)
 *       at y of sea level + 5 or more, {@code NoiseBasedAquifer.computeSubstance} then answers air on every one of
 *       its ways: above its {@code skipSamplingAboveY} the global fluid, air from sea level up; the lava test fails;
 *       the nearest centre's status is air there; and every barrier is at most 0, so none closes the block. A
 *       barrier between levels {@code a > b} is {@code 2 * (noise + gradient)}, where for a block at y above
 *       both the gradient is {@code (a - y - 0.5) / 2.5}, which from {@code y >= a + 5} on is below -2, so the noise
 *       is left out (0) and the barrier is below -4 (and a barrier between equal levels is 0); the lava-against-
 *       water barrier of 2 needs water at y, of which there is none. So from there up a block is stone where the
 *       density is above 0 and air elsewhere, without the aquifer.</li>
 *   <li><b>The blocks it does read.</b> Every density function in the router is sampled point by point except the
 *       interpolations (4 x 8 x 4, and the fine caverns' 2 x 2 x 2), whose cells lie on a fixed grid and are filled
 *       from their bottom corner up (the value stepped up block by block); so a stretch that starts on a cell
 *       boundary ({@link #CELL_HEIGHT}, a multiple of both), or where vanilla's own column starts, gets the very
 *       same floats. Below sea level + 5 each block goes through the aquifer as in vanilla: one built for the
 *       whole column as vanilla builds it, from the same samplers, random and fluids (its answers depend on the
 *       column's x and z only), and asked about the same blocks in the same order, from the top down.</li>
 * </ul>
 *
 * <p>All of which holds for the Earth's own noise settings ({@link #SETTINGS}) only, so the generator uses it for
 * those alone; GenBench holds it against vanilla for every heightmap type.
 */
final class NoiseColumnScan {
	/** The noise settings this is proven for. */
	static final ResourceKey<NoiseGeneratorSettings> SETTINGS = ResourceKey.create(Registries.NOISE_SETTINGS, Expanse.id("earth"));
	private static final BlockState AIR = Blocks.AIR.defaultBlockState();
	private static final Heightmap.Types[] TYPES = Heightmap.Types.values();
	/** Blocks above the highest cell corner's ground beyond which the terrain density is below 0 (8.2, and a margin). */
	private static final int NOISE_REACH = 10;
	/** Blocks above sea level from which the aquifers answer every open block with air (see the class comment). */
	private static final int AQUIFER_REACH = 5;
	/** The final density's interpolation cell width (final_density.json). */
	private static final int CELL_WIDTH = 4;
	/** Stretches start on multiples of this: the final density's interpolation cell height, a multiple of the fine caverns' 2. */
	private static final int CELL_HEIGHT = 8;
	/** Each further stretch down, where a cave or the sea goes deeper; a multiple of {@link #CELL_HEIGHT}. */
	private static final int SPAN = 64;
	/** Columns whose answers are kept, direct-mapped. */
	private static final int KEPT = 4096;

	private final Holder<NoiseGeneratorSettings> settings;
	private final Supplier<Aquifer.FluidPicker> fluids;
	/** The answers of recent columns, by {@link #slot}; shared between threads, each entry whole and never changed. */
	private final @Nullable Heights[] kept = new Heights[KEPT];

	NoiseColumnScan(Holder<NoiseGeneratorSettings> settings, Supplier<Aquifer.FluidPicker> fluids) {
		this.settings = settings;
		this.fluids = fluids;
	}

	/** As {@code NoiseBasedChunkGenerator.getBaseHeight}. */
	int height(TerrainModel model, int x, int z, Heightmap.Types type, LevelHeightAccessor heightAccessor, RandomState randomState) {
		int slot = slot(x, z);
		Heights kept = this.kept[slot];
		if (kept == null || !kept.is(x, z, heightAccessor, randomState)) {
			kept = new Heights(x, z, heightAccessor.getMinY(), heightAccessor.getHeight(), randomState, this.scan(model, x, z, heightAccessor, randomState));
			this.kept[slot] = kept;
		}
		return kept.heights[type.ordinal()];
	}

	/** Forgets every answer kept, so the next ones are worked out afresh (for timing them). */
	void forget() {
		Arrays.fill(this.kept, null);
	}

	private static int slot(int x, int z) {
		return (int) (PlateMap.mix(0, x, z) & KEPT - 1);
	}

	/** A column's answers, by heightmap type, for one height range and world. */
	private record Heights(int x, int z, int minY, int height, RandomState randomState, int[] heights) {
		boolean is(int x, int z, LevelHeightAccessor heightAccessor, RandomState randomState) {
			return this.x == x && this.z == z && this.minY == heightAccessor.getMinY() && this.height == heightAccessor.getHeight()
				&& this.randomState == randomState;
		}
	}

	/** Every heightmap type's base height at (x, z), by {@link Heightmap.Types#ordinal}. */
	private int[] scan(TerrainModel model, int x, int z, LevelHeightAccessor heightAccessor, RandomState randomState) {
		int[] heights = new int[TYPES.length];
		// vanilla's answer where no block of the column passes the test
		Arrays.fill(heights, heightAccessor.getMinY());
		NoiseGeneratorSettings settings = this.settings.value();
		NoiseSettings noiseSettings = settings.noiseSettings().clampToHeightAccessor(heightAccessor);
		if (noiseSettings.height() <= 0) {
			return heights;
		}
		DensityVolume column = new DensityVolume(1, noiseSettings.height(), 1, x, noiseSettings.minY(), z);
		int minY = column.minBlockY();
		int maxY = column.maxBlockY();

		// the ground at the corners of the column's interpolation cell
		int cornerX = Math.floorDiv(x, CELL_WIDTH) * CELL_WIDTH;
		int cornerZ = Math.floorDiv(z, CELL_WIDTH) * CELL_WIDTH;
		float high = -Float.MAX_VALUE;
		float low = Float.MAX_VALUE;
		for (int dz = 0; dz <= CELL_WIDTH; dz += CELL_WIDTH) {
			for (int dx = 0; dx <= CELL_WIDTH; dx += CELL_WIDTH) {
				float h = model.sample(cornerX + dx, cornerZ + dz).height;
				high = Math.max(high, h);
				low = Math.min(low, h);
			}
		}

		// from here up the aquifer answers every open block with air; above the open sky every block is air (see the class comment)
		int dry = settings.seaLevel() + AQUIFER_REACH;
		int sky = Math.max(dry, Mth.ceil(high) + NOISE_REACH);
		int from = Math.min(maxY, Math.floorDiv(sky + CELL_HEIGHT - 1, CELL_HEIGHT) * CELL_HEIGHT - 1);
		int open = (1 << TYPES.length) - 1;
		if (from < maxY) {
			int passed = passes(AIR);
			for (int i = 0; i < TYPES.length; i++) {
				if ((passed >> i & 1) != 0) {
					heights[i] = maxY + 1;
				}
			}
			open &= ~passed;
		}

		DensityBufferPool bufferPool = randomState.acquireDensityBufferPool();
		try {
			// the samplers as NoiseChunk binds them for vanilla's column (no beardifier, no blending)
			DensitySamplerSet samplers = randomState.samplersWithContext(SamplerContext.builder()
				.setUserFields(ContextMap.builder().set(Beardifier.CONTEXT_KEY, null).build())
				.useBufferArena(bufferPool)
				.enableCaches()
				.build());
			DensitySampler.Bound finalDensity = samplers.get(settings.noiseRouter().finalDensity());
			BlockState defaultState = settings.defaultBlock();
			Aquifer aquifer = null;
			BlockState last = null;
			int lastPassed = 0;
			int top = from;
			// the first stretch reaches a little below the lowest corner's ground, where the ground almost always is
			int bottom = Math.max(minY, Math.min(Math.floorDiv(Mth.floor(low) - NOISE_REACH, CELL_HEIGHT), Math.floorDiv(top, CELL_HEIGHT)) * CELL_HEIGHT);
			while (open != 0 && top >= minY) {
				DensityVolume stretch = new DensityVolume(1, top - bottom + 1, 1, x, bottom, z);
				try (ScopedDensityBuffer densityBuffer = finalDensity.sampleVolume(stretch)) {
					for (int y = stretch.sizeY() - 1; y >= 0 && open != 0; y--) {
						float density = densityBuffer.get(stretch.indexUnchecked(0, y, 0));
						int blockY = stretch.blockY(y);
						BlockState state;
						if (density > 0.0) {
							// solid, as the aquifer answers first of all
							state = defaultState;
						} else if (blockY >= dry) {
							state = AIR;
						} else {
							if (aquifer == null) {
								aquifer = this.aquifer(settings, samplers, randomState, column);
							}
							BlockState baseState = aquifer.computeSubstance(x, blockY, z, density);
							state = baseState == null ? defaultState : baseState;
						}
						if (state != last) {
							last = state;
							lastPassed = passes(state);
						}
						int passed = lastPassed & open;
						if (passed != 0) {
							for (int i = 0; i < TYPES.length; i++) {
								if ((passed >> i & 1) != 0) {
									heights[i] = blockY + 1;
								}
							}
							open &= ~passed;
						}
					}
				}
				top = bottom - 1;
				bottom = Math.max(minY, bottom - SPAN);
			}
		} finally {
			randomState.releaseDensityBufferPool(bufferPool);
		}
		return heights;
	}

	/** The column's aquifer, as NoiseChunk builds it for vanilla's whole column. */
	private Aquifer aquifer(NoiseGeneratorSettings settings, DensitySamplerSet samplers, RandomState randomState, DensityVolume column) {
		Aquifer.FluidPicker fluids = this.fluids.get();
		return settings.aquifers().isPresent()
			? settings.aquifers().get().create(samplers, randomState.getOrCreateRandomFactory(Identifier.withDefaultNamespace("aquifer")), column, fluids)
			: Aquifer.createDisabled(fluids);
	}

	/** The heightmap types whose test a block passes, as bits by {@link Heightmap.Types#ordinal}. */
	private static int passes(BlockState state) {
		int passed = 0;
		for (int i = 0; i < TYPES.length; i++) {
			if (TYPES[i].isOpaque().test(state)) {
				passed |= 1 << i;
			}
		}
		return passed;
	}
}
