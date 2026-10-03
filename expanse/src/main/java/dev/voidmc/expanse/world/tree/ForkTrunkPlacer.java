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
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.util.valueproviders.IntProviders;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.TreeFeature;
import net.minecraft.world.level.levelgen.feature.foliageplacers.FoliagePlacer;
import net.minecraft.world.level.levelgen.feature.trunkplacers.TrunkPlacer;
import net.minecraft.world.level.levelgen.feature.trunkplacers.TrunkPlacerType;

/**
 * A trunk that leans as it rises and then forks into a few limbs, some of which fork again; every limb
 * end carries a clump of leaves. One placer, several trees, by its numbers:
 * <ul>
 *   <li>olive: short, gnarled ({@code root_flare} thickens the foot), three or four limbs spreading low
 *       into a wide round crown;</li>
 *   <li>ghost gum: a tall pale trunk with a lean, limbs climbing steeply and forking into an open,
 *       broken crown;</li>
 *   <li>teak: a straight bole, limbs rising steeply to a layered flat-topped crown;</li>
 *   <li>flame tree and mesquite: small, crooked, spreading.</li>
 * </ul>
 */
public class ForkTrunkPlacer extends TrunkPlacer {
	public static final MapCodec<ForkTrunkPlacer> CODEC = RecordCodecBuilder.mapCodec(i -> trunkPlacerParts(i)
		.and(i.group(
			IntProviders.codec(0, 8).fieldOf("fork_count").forGetter(p -> p.forkCount),
			IntProviders.codec(1, 10).fieldOf("fork_length").forGetter(p -> p.forkLength),
			Codec.floatRange(0.0F, 4.0F).fieldOf("fork_rise").forGetter(p -> p.forkRise),
			Codec.floatRange(0.0F, 1.0F).fieldOf("lean").forGetter(p -> p.lean),
			Codec.floatRange(0.0F, 1.0F).fieldOf("subfork_chance").forGetter(p -> p.subforkChance),
			IntProviders.codec(0, 8).fieldOf("root_flare").forGetter(p -> p.rootFlare)
		))
		.apply(i, ForkTrunkPlacer::new));

	private final IntProvider forkCount;
	private final IntProvider forkLength;
	private final float forkRise;
	private final float lean;
	private final float subforkChance;
	private final IntProvider rootFlare;

	public ForkTrunkPlacer(
		int baseHeight, int heightRandA, int heightRandB, IntProvider forkCount, IntProvider forkLength, float forkRise, float lean,
		float subforkChance, IntProvider rootFlare
	) {
		super(baseHeight, heightRandA, heightRandB);
		this.forkCount = forkCount;
		this.forkLength = forkLength;
		this.forkRise = forkRise;
		this.lean = lean;
		this.subforkChance = subforkChance;
		this.rootFlare = rootFlare;
	}

	@Override
	protected TrunkPlacerType<?> type() {
		return WarmTreePlacers.FORK_TRUNK;
	}

	@Override
	public List<FoliagePlacer.FoliageAttachment> placeTrunk(
		WorldGenLevel level, BiConsumer<BlockPos, BlockState> setter, RandomSource random, int height, BlockPos origin, TreeFeature tree
	) {
		placeBelowTrunkBlock(level, setter, random, origin.below(), tree);
		Logs.Placer logs = Logs.placer(tree, level, setter, random);
		List<FoliagePlacer.FoliageAttachment> crown = new ArrayList<>();

		// The bole: drifts steadily one way (the lean), never more than three blocks off its root.
		float leanAngle = random.nextFloat() * Mth.TWO_PI;
		double dx = Mth.cos(leanAngle) * this.lean * 0.45;
		double dz = Mth.sin(leanAngle) * this.lean * 0.45;
		double x = origin.getX() + 0.5;
		double z = origin.getZ() + 0.5;
		BlockPos cur = origin;
		logs.place(cur, Direction.Axis.Y);
		for (int y = 1; y < height; y++) {
			if (y > 1) {
				x = Mth.clamp(x + dx, origin.getX() - 2.5, origin.getX() + 3.5);
				z = Mth.clamp(z + dz, origin.getZ() - 2.5, origin.getZ() + 3.5);
			}
			BlockPos next = BlockPos.containing(x, origin.getY() + y, z);
			cur = Logs.line(logs, cur, next);
		}

		// Root flare: knuckles of trunk round the foot, a block or two high, running down into the ground.
		int flare = this.rootFlare.sample(random);
		for (int f = 0; f < flare; f++) {
			Direction d = Direction.Plane.HORIZONTAL.getRandomDirection(random);
			BlockPos p = origin.relative(d);
			int up = random.nextInt(f < 2 ? 3 : 2);
			for (int y = up; y >= -3; y--) {
				BlockPos q = p.above(y);
				if (y < 0 && !TreeFeature.validTreePos(level, q)) {
					break;
				}
				logs.place(q, Direction.Axis.Y);
			}
		}

		// The forks, spread evenly round the trunk with a little wobble.
		int n = this.forkCount.sample(random);
		float angle = random.nextFloat() * Mth.TWO_PI;
		for (int f = 0; f < n; f++) {
			angle += Mth.TWO_PI / n + (random.nextFloat() - 0.5F) * 0.7F;
			int len = this.forkLength.sample(random);
			BlockPos from = f == 0 || height < 4 ? cur : cur.below(random.nextInt(2));
			BlockPos end = this.limb(logs, from, angle, len);
			if (random.nextFloat() < this.subforkChance) {
				int len2 = Math.max(1, Math.round(len * 0.6F));
				float spread = 0.5F + random.nextFloat() * 0.4F;
				crown.add(new FoliagePlacer.FoliageAttachment(this.limb(logs, end, angle - spread, len2), 0, 0, 1, 1));
				crown.add(new FoliagePlacer.FoliageAttachment(this.limb(logs, end, angle + spread, len2), 0, 0, 1, 1));
			} else {
				crown.add(new FoliagePlacer.FoliageAttachment(end, 0, 0, 1, 1));
			}
		}
		// A smaller clump on the trunk's own top fills the middle of the crown.
		crown.add(new FoliagePlacer.FoliageAttachment(cur, n == 0 ? 0 : -1, 0, 1, 1));
		return crown;
	}

	private BlockPos limb(Logs.Placer logs, BlockPos from, float angle, int len) {
		int rise = Math.max(1, Math.round(len * this.forkRise));
		BlockPos tip = BlockPos.containing(from.getX() + 0.5 + Mth.cos(angle) * len, from.getY() + rise, from.getZ() + 0.5 + Mth.sin(angle) * len);
		return Logs.line(logs, from, tip);
	}
}
