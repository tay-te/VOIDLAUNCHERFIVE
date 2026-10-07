package dev.voidmc.lod.mixin;

import dev.voidmc.lod.source.TreePlacer;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.shapes.DiscreteVoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Neighbour shape updates after a tree is placed mean nothing in the LOD's stand-in world ({@link TreePlacer}). */
@Mixin(StructureTemplate.class)
public abstract class StructureTemplateMixin {
	@Inject(method = "updateShapeAtEdge(Lnet/minecraft/world/level/LevelAccessor;ILnet/minecraft/world/phys/shapes/DiscreteVoxelShape;III)V",
		at = @At("HEAD"), cancellable = true)
	private static void voidLod$skipInStandIn(LevelAccessor level, int flags, DiscreteVoxelShape shape, int x, int y, int z, CallbackInfo ci) {
		if (TreePlacer.isStandIn(level)) {
			ci.cancel();
		}
	}
}
