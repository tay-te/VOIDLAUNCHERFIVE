package dev.voidmc.expanse.world.tree;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.TreeFeature;
import net.minecraft.world.level.levelgen.feature.foliageplacers.FoliagePlacer;
import net.minecraft.world.level.levelgen.feature.trunkplacers.TrunkPlacer;
import net.minecraft.world.level.levelgen.feature.trunkplacers.TrunkPlacerType;

/**
 * A corkscrew trunk for the lumenwood: the trunk's centre walks round a small circle as it rises, so
 * the tree looks wrung out, and two or three short limbs at the top hold the glowing canopy.
 */
public class SpiralTrunkPlacer extends TrunkPlacer {
	public static final MapCodec<SpiralTrunkPlacer> CODEC = RecordCodecBuilder.mapCodec(i -> trunkPlacerParts(i)
		.and(Codec.floatRange(0.5F, 3.0F).fieldOf("coil_radius").forGetter(p -> p.coil))
		.apply(i, SpiralTrunkPlacer::new));

	private final float coil;

	public SpiralTrunkPlacer(int baseHeight, int heightRandA, int heightRandB, float coil) {
		super(baseHeight, heightRandA, heightRandB);
		this.coil = coil;
	}

	@Override
	protected TrunkPlacerType<?> type() {
		return ExpanseTreePlacers.SPIRAL_TRUNK;
	}

	@Override
	public List<FoliagePlacer.FoliageAttachment> placeTrunk(
		WorldGenLevel level, BiConsumer<BlockPos, BlockState> setter, RandomSource random, int height, BlockPos origin, TreeFeature tree
	) {
		placeBelowTrunkBlock(level, setter, random, origin.below(), tree);
		Logs.Placer logs = Logs.placer(tree, level, setter, random);
		float phase = random.nextFloat() * Mth.TWO_PI;
		float turn = (random.nextBoolean() ? 1 : -1) * (0.45F + random.nextFloat() * 0.25F);
		BlockPos prev = origin;
		logs.place(prev, Direction.Axis.Y);
		for (int y = 1; y < height; y++) {
			// The coil opens up with height: a firm base, a twisting middle.
			float r = this.coil * Math.min(1.0F, y / 3.0F);
			BlockPos p = BlockPos.containing(origin.getX() + 0.5 + Mth.cos(phase + y * turn) * r, origin.getY() + y,
				origin.getZ() + 0.5 + Mth.sin(phase + y * turn) * r);
			prev = Logs.line(logs, prev, p);
		}
		List<FoliagePlacer.FoliageAttachment> crown = new ArrayList<>();
		crown.add(new FoliagePlacer.FoliageAttachment(prev.above(), 0, 0, 1, 1));
		int limbs = 2 + random.nextInt(2);
		float angle = random.nextFloat() * Mth.TWO_PI;
		for (int b = 0; b < limbs; b++) {
			angle += Mth.TWO_PI / limbs;
			BlockPos from = prev.below(1 + random.nextInt(2));
			BlockPos tip = BlockPos.containing(from.getX() + 0.5 + Mth.cos(angle) * 3, from.getY() + 1, from.getZ() + 0.5 + Mth.sin(angle) * 3);
			crown.add(new FoliagePlacer.FoliageAttachment(Logs.line(logs, from, tip), -1, 0, 1, 1));
		}
		return crown;
	}
}
