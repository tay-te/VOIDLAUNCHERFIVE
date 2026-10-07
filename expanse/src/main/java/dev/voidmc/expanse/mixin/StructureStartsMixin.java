package dev.voidmc.expanse.mixin;

import dev.voidmc.expanse.world.SitedJigsawStructure;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Tells the Expanse's structures which level they are being sited in while a chunk's structure starts are made, so a
 * site can be checked against land another mod has claimed (LandClaims asks with the level). The level's own
 * structure manager is the one passed in here, and it knows its level.
 */
@Mixin(ChunkGenerator.class)
abstract class StructureStartsMixin {
	@Inject(method = "createStructures", at = @At("HEAD"))
	private void expanse$enter(RegistryAccess registryAccess, ChunkGeneratorStructureState state, StructureManager structureManager,
		ChunkAccess chunk, StructureTemplateManager templates, ResourceKey<Level> dimension, CallbackInfo ci) {
		if (((StructureManagerAccessor) structureManager).expanse$level() instanceof ServerLevel level) {
			SitedJigsawStructure.BUILDING.set(level);
		}
	}

	@Inject(method = "createStructures", at = @At("RETURN"))
	private void expanse$leave(RegistryAccess registryAccess, ChunkGeneratorStructureState state, StructureManager structureManager,
		ChunkAccess chunk, StructureTemplateManager templates, ResourceKey<Level> dimension, CallbackInfo ci) {
		SitedJigsawStructure.BUILDING.remove();
	}
}
