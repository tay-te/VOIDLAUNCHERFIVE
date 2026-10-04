package dev.voidmc.hearth.mixin.world;

import dev.voidmc.hearth.world.VillageReplacement;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Leaves village structure sets out of a world's possible structures while Hearth replaces villages: the same path
 * vanilla takes for a set whose biomes the world never has, so placement, generation and {@code /locate} all skip it.
 */
@Mixin(ChunkGeneratorStructureState.class)
abstract class ChunkGeneratorStructureStateMixin {
	@Inject(method = "hasBiomesForStructureSet", at = @At("HEAD"), cancellable = true)
	private static void hearth$replaceVillages(StructureSet structureSet, BiomeSource biomeSource, CallbackInfoReturnable<Boolean> cir) {
		if (VillageReplacement.active() && VillageReplacement.isVillageSet(structureSet)) {
			cir.setReturnValue(false);
		}
	}
}
