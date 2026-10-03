package dev.voidmc.expanse.world.tree;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.util.valueproviders.IntProviders;
import net.minecraft.world.level.block.HangingMossBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.treedecorators.TreeDecorator;
import net.minecraft.world.level.levelgen.feature.treedecorators.TreeDecoratorType;

/**
 * Long hanging cascades under a tree's leaves: wisteria racemes, willow moss.
 *
 * <p>Vanilla's attached-to-leaves decorator hangs a single block. A wisteria's whole character is its
 * metre-long racemes, and those cannot be leaves (a leaf that far from a log decays), so they are a
 * hanging plant instead — a column of "body" blocks ending in a "tip", the way pale hanging moss is
 * drawn — which never decays and drops nothing but itself.
 */
public class HangingCascadeDecorator extends TreeDecorator {
	public static final MapCodec<HangingCascadeDecorator> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
		BlockState.CODEC.fieldOf("block").forGetter(d -> d.block),
		Codec.floatRange(0.0F, 1.0F).fieldOf("probability").forGetter(d -> d.probability),
		IntProviders.codec(1, 16).fieldOf("length").forGetter(d -> d.length)
	).apply(i, HangingCascadeDecorator::new));

	private final BlockState block;
	private final float probability;
	private final IntProvider length;

	public HangingCascadeDecorator(BlockState block, float probability, IntProvider length) {
		this.block = block;
		this.probability = probability;
		this.length = length;
	}

	@Override
	protected TreeDecoratorType<?> type() {
		return ExpanseTreePlacers.HANGING_CASCADE;
	}

	@Override
	public void place(Context context) {
		RandomSource random = context.random();
		for (BlockPos leaf : context.leaves()) {
			if (random.nextFloat() >= this.probability || !context.isAir(leaf.below())) {
				continue;
			}
			int n = this.length.sample(random);
			BlockPos.MutableBlockPos p = leaf.below().mutable();
			int placed = 0;
			while (placed < n && context.isAir(p)) {
				placed++;
				p.move(0, -1, 0);
			}
			p.set(leaf.below());
			for (int k = 0; k < placed; k++) {
				BlockState s = this.block.hasProperty(HangingMossBlock.TIP) ? this.block.setValue(HangingMossBlock.TIP, k == placed - 1) : this.block;
				context.setBlock(p, s);
				p.move(0, -1, 0);
			}
		}
	}
}
