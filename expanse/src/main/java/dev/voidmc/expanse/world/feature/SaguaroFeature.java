package dev.voidmc.expanse.world.feature;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.util.valueproviders.IntProviders;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;

/**
 * A saguaro: a ribbed column with arms that leave it level and turn upward, built of a pipe-like block.
 * The arms go on alternate sides at different heights and never reach the crown; a short young saguaro
 * has none. Each column's tip may carry a cactus flower.
 *
 * <p>Joints are drawn only along the cactus's own path (up the trunk, out along an arm, up the arm), so
 * an arm that climbs beside the trunk stands clear of it rather than fusing into one fat column. (A
 * player's later block update rejoins any touching pieces, as it would a chorus plant.)
 */
public record SaguaroFeature(BlockState block, IntProvider height, IntProvider arms, float flowerChance, BlockState flower) implements Feature {
	public static final MapCodec<SaguaroFeature> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
		BlockState.CODEC.fieldOf("block").forGetter(SaguaroFeature::block),
		IntProviders.codec(1, 16).fieldOf("height").forGetter(SaguaroFeature::height),
		IntProviders.codec(0, 6).fieldOf("arms").forGetter(SaguaroFeature::arms),
		Codec.floatRange(0.0F, 1.0F).fieldOf("flower_chance").forGetter(SaguaroFeature::flowerChance),
		BlockState.CODEC.fieldOf("flower").forGetter(SaguaroFeature::flower)
	).apply(i, SaguaroFeature::new));

	@Override
	public MapCodec<SaguaroFeature> codec() {
		return CODEC;
	}

	@Override
	public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
		if (!level.getBlockState(origin.below()).is(BlockTags.SUPPORTS_DRY_VEGETATION) || !level.isEmptyBlock(origin)) {
			return false;
		}
		int h = this.height.sample(random);
		for (int y = 0; y <= h; y++) {
			if (!level.isEmptyBlock(origin.above(y))) {
				h = y - 1;
				break;
			}
		}
		if (h < 1) {
			return false;
		}
		Map<BlockPos, EnumSet<Direction>> body = new LinkedHashMap<>();
		List<BlockPos> tips = new ArrayList<>();
		for (int y = 0; y < h; y++) {
			body.put(origin.above(y), EnumSet.noneOf(Direction.class));
		}
		body.get(origin).add(Direction.DOWN);
		for (int y = 1; y < h; y++) {
			join(body, origin.above(y - 1), Direction.UP);
		}
		tips.add(origin.above(h - 1));

		int n = h < 4 ? 0 : this.arms.sample(random);
		Direction side = Direction.Plane.HORIZONTAL.getRandomDirection(random);
		for (int a = 0; a < n; a++) {
			// Alternate sides, turning a quarter now and then, so arms rarely stack on one face.
			side = a % 2 == 1 ? side.getOpposite() : side.getClockWise();
			int at = 2 + random.nextInt(Math.max(1, h - 4));
			int out = h >= 7 && random.nextInt(3) == 0 ? 2 : 1;
			int up = 1 + random.nextInt(Math.max(1, Math.min(4, h - at - 1)));
			BlockPos start = origin.above(at);
			BlockPos elbow = start.relative(side, out);
			List<BlockPos> arm = new ArrayList<>();
			for (int o = 1; o <= out; o++) {
				arm.add(start.relative(side, o));
			}
			for (int k = 1; k <= up; k++) {
				arm.add(elbow.above(k));
			}
			boolean clear = true;
			for (BlockPos q : arm) {
				if (!level.isEmptyBlock(q) || body.containsKey(q) || nearArm(body, q, origin)) {
					clear = false;
					break;
				}
			}
			if (!clear) {
				continue;
			}
			for (BlockPos q : arm) {
				body.put(q, EnumSet.noneOf(Direction.class));
			}
			BlockPos prev = start;
			for (int o = 1; o <= out; o++) {
				join(body, prev, side);
				prev = prev.relative(side);
			}
			for (int k = 1; k <= up; k++) {
				join(body, prev, Direction.UP);
				prev = prev.above();
			}
			tips.add(prev);
		}

		for (Map.Entry<BlockPos, EnumSet<Direction>> e : body.entrySet()) {
			BlockState s = this.block;
			for (Direction d : Direction.values()) {
				s = s.trySetValue(PipeBlock.PROPERTY_BY_DIRECTION.get(d), e.getValue().contains(d));
			}
			level.setBlock(e.getKey(), s, 2);
		}
		for (BlockPos tip : tips) {
			if (random.nextFloat() < this.flowerChance && level.isEmptyBlock(tip.above())) {
				level.setBlock(tip.above(), this.flower, 2);
			}
		}
		return true;
	}

	private static void join(Map<BlockPos, EnumSet<Direction>> body, BlockPos from, Direction d) {
		body.get(from).add(d);
		body.get(from.relative(d)).add(d.getOpposite());
	}

	/** Whether a new arm block would touch another arm (anything but the trunk column itself). */
	private static boolean nearArm(Map<BlockPos, EnumSet<Direction>> body, BlockPos q, BlockPos origin) {
		for (Direction d : Direction.values()) {
			BlockPos r = q.relative(d);
			if (body.containsKey(r) && (r.getX() != origin.getX() || r.getZ() != origin.getZ())) {
				return true;
			}
		}
		return false;
	}
}
