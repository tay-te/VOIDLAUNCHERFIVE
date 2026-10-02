package dev.voidmc.expanse.world.feature;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.util.valueproviders.IntProviders;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;

/**
 * Rows of a plant across a field, the way lavender is grown in Provence: a few parallel lines a couple of
 * blocks apart, following the ground, each plant set only where it can live, with the odd one missing.
 */
public record PlantRowsFeature(Holder<BlockStateProvider> plant, IntProvider rows, IntProvider length, int spacing) implements Feature {
	public static final MapCodec<PlantRowsFeature> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
		BlockStateProvider.CODEC.fieldOf("plant").forGetter(PlantRowsFeature::plant),
		IntProviders.codec(1, 6).fieldOf("rows").forGetter(PlantRowsFeature::rows),
		IntProviders.codec(2, 16).fieldOf("length").forGetter(PlantRowsFeature::length),
		Codec.intRange(1, 4).fieldOf("spacing").forGetter(PlantRowsFeature::spacing)
	).apply(i, PlantRowsFeature::new));

	@Override
	public MapCodec<PlantRowsFeature> codec() {
		return CODEC;
	}

	@Override
	public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
		boolean alongX = random.nextBoolean();
		int rows = this.rows.sample(random);
		int len = Math.min(16, this.length.sample(random));
		boolean placed = false;
		for (int r = 0; r < rows; r++) {
			int across = r * this.spacing - (rows - 1) * this.spacing / 2;
			for (int i = -len / 2; i < len - len / 2; i++) {
				if (random.nextInt(12) == 0) {
					continue;
				}
				int x = origin.getX() + (alongX ? i : across);
				int z = origin.getZ() + (alongX ? across : i);
				int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
				if (Math.abs(y - origin.getY()) > 3) {
					continue;
				}
				BlockPos p = new BlockPos(x, y, z);
				BlockState s = this.plant.value().getState(level, random, p);
				if (level.isEmptyBlock(p) && s.canSurvive(level, p)) {
					level.setBlock(p, s, 2);
					placed = true;
				}
			}
		}
		return placed;
	}
}
