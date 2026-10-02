package dev.voidmc.expanse.mixin;

import dev.voidmc.expanse.world.terrain.EarthChunkGenerator;
import java.util.Optional;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.structures.MineshaftStructure;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * No mineshafts near the realms under the Earth terrain. A mineshaft that runs into open space shores its
 * corridors up on planks and chains, which in a cave is a bridge and in a realm four hundred blocks across
 * is a lattice hanging in the dark. So a mineshaft that would start within {@value #CLEARANCE} blocks of a
 * realm's outline is not begun; elsewhere, mineshafts are as vanilla makes them.
 */
@Mixin(MineshaftStructure.class)
abstract class MineshaftStructureMixin {
	private static final int CLEARANCE = 100;

	@Inject(method = "findGenerationPoint", at = @At("HEAD"), cancellable = true)
	private void expanse$notNearRealms(Structure.GenerationContext context, CallbackInfoReturnable<Optional<Structure.GenerationStub>> cir) {
		if (context.chunkGenerator() instanceof EarthChunkGenerator earth) {
			var chunk = context.chunkPos();
			var column = earth.model(context.randomState()).caverns().info(chunk.getMiddleBlockX(), chunk.getMiddleBlockZ());
			if (column.hallEdge > -CLEARANCE) {
				cir.setReturnValue(Optional.empty());
			}
		}
	}
}
