package dev.voidmc.hearth.mixin.sim;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.voidmc.hearth.sim.gen.WorldgenGuard;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.placement.FeaturePlacer;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The world generation guard's hook: every feature placement passes through {@link FeaturePlacer}, and a top-level one
 * (from the chunk's decoration, with the biome check) tells the guard which feature, hence which step, is running.
 */
@Mixin(FeaturePlacer.class)
abstract class FeaturePlacerMixin {
	@Shadow
	@Final
	private WorldGenLevel level;

	@Inject(method = "place(Lnet/minecraft/world/level/levelgen/placement/PlacedFeature;Lnet/minecraft/util/RandomSource;Lnet/minecraft/core/BlockPos;Z)Z",
		at = @At("HEAD"))
	private void hearth$enter(PlacedFeature feature, RandomSource random, BlockPos origin, boolean biomeCheck, CallbackInfoReturnable<Boolean> cir) {
		if (biomeCheck) {
			WorldgenGuard.enter(this.level, feature);
		}
	}

	@Inject(method = "place(Lnet/minecraft/world/level/levelgen/placement/PlacedFeature;Lnet/minecraft/util/RandomSource;Lnet/minecraft/core/BlockPos;Z)Z",
		at = @At("RETURN"))
	private void hearth$exit(PlacedFeature feature, RandomSource random, BlockPos origin, boolean biomeCheck, CallbackInfoReturnable<Boolean> cir) {
		if (biomeCheck) {
			WorldgenGuard.exit();
		}
	}

	@WrapOperation(method = "place(Lnet/minecraft/world/level/levelgen/placement/PlacedFeature;Lnet/minecraft/util/RandomSource;Lnet/minecraft/core/BlockPos;Z)Z",
		at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/level/levelgen/feature/Feature;place(Lnet/minecraft/world/level/WorldGenLevel;Lnet/minecraft/world/level/chunk/ChunkGenerator;Lnet/minecraft/util/RandomSource;Lnet/minecraft/core/BlockPos;)Z"))
	private boolean hearth$guard(Feature feature, WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos pos,
		Operation<Boolean> original) {
		if (WorldgenGuard.blocks(pos)) {
			return false;
		}
		return original.call(feature, level, generator, random, pos);
	}
}
