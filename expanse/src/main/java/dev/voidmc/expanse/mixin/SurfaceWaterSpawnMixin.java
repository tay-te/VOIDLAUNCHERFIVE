package dev.voidmc.expanse.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.fish.WaterAnimal;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Fish in rivers above sea level. Vanilla's surface water animals (cod, salmon, tropical fish, squid)
 * only spawn between sea level and 13 blocks below it, which is all the ocean and the river biome need.
 * The Earth terrain's rivers run above sea level, stretch by stretch down to the sea, and so would stay
 * empty. This also lets them spawn in the top 13 blocks of any water open to the sky (a river, a lake,
 * a mountain tarn; also under its ice), wherever the biome lists them. Water below ground, whose column
 * is capped by rock, is still left alone.
 */
@Mixin(WaterAnimal.class)
abstract class SurfaceWaterSpawnMixin {
	@Inject(method = "checkSurfaceWaterAnimalSpawnRules", at = @At("RETURN"), cancellable = true)
	private static void expanse$riversAboveTheSea(EntityType<? extends WaterAnimal> type, LevelAccessor level, EntitySpawnReason reason,
		BlockPos pos, RandomSource random, CallbackInfoReturnable<Boolean> cir) {
		if (cir.getReturnValueZ() || pos.getY() <= level.getSeaLevel()) {
			return;
		}
		if (!level.getFluidState(pos.below()).is(FluidTags.WATER) || !level.getBlockState(pos.above()).is(Blocks.WATER)) {
			return;
		}
		int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, pos.getX(), pos.getZ()) - 1;
		if (top < pos.getY() || top - pos.getY() > 13) {
			return;
		}
		BlockState surface = level.getBlockState(pos.atY(top));
		if (surface.getFluidState().is(FluidTags.WATER) || surface.is(BlockTags.ICE)) {
			cir.setReturnValue(true);
		}
	}
}
