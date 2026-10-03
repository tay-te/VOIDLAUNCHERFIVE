package dev.voidmc.expanse.world.feature;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.util.valueproviders.IntProviders;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.WallSide;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;

/**
 * An old dry-stone field wall: a run of wall blocks along the lie of the land, sometimes turning a
 * corner, with gaps where stones have fallen and moss on some of them. It steps up and down a block with
 * the ground but stops at anything steeper, at water, or where the ground is not solid.
 *
 * <p>The wall's own pieces are joined to each other here (posts at the ends, corners and steps, low
 * runs between), since a feature's blocks get no neighbour updates.
 */
public record FieldWallFeature(BlockState wall, BlockState mossyWall, IntProvider length, float mossChance, float gapChance) implements Feature {
	public static final MapCodec<FieldWallFeature> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
		BlockState.CODEC.fieldOf("wall").forGetter(FieldWallFeature::wall),
		BlockState.CODEC.fieldOf("mossy_wall").forGetter(FieldWallFeature::mossyWall),
		IntProviders.codec(2, 14).fieldOf("length").forGetter(FieldWallFeature::length),
		Codec.floatRange(0.0F, 1.0F).fieldOf("moss_chance").forGetter(FieldWallFeature::mossChance),
		Codec.floatRange(0.0F, 1.0F).fieldOf("gap_chance").forGetter(FieldWallFeature::gapChance)
	).apply(i, FieldWallFeature::new));

	@Override
	public MapCodec<FieldWallFeature> codec() {
		return CODEC;
	}

	@Override
	public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
		Direction dir = Direction.Plane.HORIZONTAL.getRandomDirection(random);
		int len = this.length.sample(random);
		int turnAt = random.nextInt(3) == 0 ? 2 + random.nextInt(Math.max(1, len - 2)) : -1;
		Direction turn = random.nextBoolean() ? dir.getClockWise() : dir.getCounterClockWise();
		Map<BlockPos, BlockState> wall = new LinkedHashMap<>();
		int x = origin.getX();
		int z = origin.getZ();
		int lastY = Integer.MIN_VALUE;
		for (int i = 0; i < len; i++) {
			if (i == turnAt) {
				dir = turn;
			}
			if (i > 0) {
				x += dir.getStepX();
				z += dir.getStepZ();
			}
			// Keep within the chunk and its neighbours, which is all a feature may write to.
			if (Math.abs(x - origin.getX()) > 12 || Math.abs(z - origin.getZ()) > 12) {
				break;
			}
			int y = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z);
			BlockPos at = new BlockPos(x, y, z);
			if (lastY != Integer.MIN_VALUE && Math.abs(y - lastY) > 1) {
				break;
			}
			BlockState ground = level.getBlockState(at.below());
			if (!ground.isSolid() || !level.getFluidState(at).isEmpty() || !level.getFluidState(at.below()).isEmpty()) {
				break;
			}
			lastY = y;
			if (random.nextFloat() < this.gapChance || !level.getBlockState(at).canBeReplaced()) {
				continue;
			}
			wall.put(at, random.nextFloat() < this.mossChance ? this.mossyWall : this.wall);
		}
		if (wall.size() < 2) {
			return false;
		}
		for (Map.Entry<BlockPos, BlockState> e : wall.entrySet()) {
			BlockPos p = e.getKey();
			BlockState s = e.getValue();
			boolean n = wall.containsKey(p.north());
			boolean so = wall.containsKey(p.south());
			boolean ea = wall.containsKey(p.east());
			boolean we = wall.containsKey(p.west());
			boolean straight = n && so && !ea && !we || ea && we && !n && !so;
			s = s.trySetValue(WallBlock.NORTH, n ? WallSide.LOW : WallSide.NONE)
				.trySetValue(WallBlock.SOUTH, so ? WallSide.LOW : WallSide.NONE)
				.trySetValue(WallBlock.EAST, ea ? WallSide.LOW : WallSide.NONE)
				.trySetValue(WallBlock.WEST, we ? WallSide.LOW : WallSide.NONE)
				.trySetValue(WallBlock.UP, !straight);
			level.setBlock(p, s, 2);
		}
		return true;
	}
}
