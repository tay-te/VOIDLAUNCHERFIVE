package dev.voidmc.lod.mixin;

import dev.voidmc.lod.source.TreePlacer;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.levelgen.feature.TreeFeature;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.shapes.BitSetDiscreteVoxelShape;
import net.minecraft.world.phys.shapes.DiscreteVoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * In the LOD's stand-in world ({@link TreePlacer}) a tree's last pass is skipped: walking its leaves to set how far
 * each is from a log changes no block that is drawn, only the leaves' decay counter, and it was about half the time.
 * Real worlds are untouched.
 */
@Mixin(TreeFeature.class)
public abstract class TreeFeatureMixin {
	@Inject(method = "updateLeaves", at = @At("HEAD"), cancellable = true)
	private static void voidLod$skipInStandIn(LevelAccessor level, BoundingBox bounds, Set<BlockPos> logs, Set<BlockPos> decorations,
		Set<BlockPos> roots, CallbackInfoReturnable<DiscreteVoxelShape> cir) {
		if (TreePlacer.isStandIn(level)) {
			cir.setReturnValue(new BitSetDiscreteVoxelShape(1, 1, 1));
		}
	}
}
