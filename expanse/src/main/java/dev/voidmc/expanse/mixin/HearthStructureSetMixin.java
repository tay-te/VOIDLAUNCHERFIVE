package dev.voidmc.expanse.mixin;

import dev.voidmc.expanse.world.SitedJigsawStructure;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Leaves a structure set out of the world's possible structures when every structure in it stands down in this game
 * (SitedJigsawStructure "peopled" with VOID Hearth installed, "abandoned" without): the path vanilla takes for a set
 * whose biomes the world never has, so {@code /locate} gives up at once rather than searching for something that never generates. A set that also
 * holds wild sights stays, and tries those in their place; the stood-down structure itself is then given no
 * placements, which only the search for the nearest one ({@code /locate}, explorer maps) asks for.
 */
@Mixin(ChunkGeneratorStructureState.class)
abstract class HearthStructureSetMixin {
	@Inject(method = "hasBiomesForStructureSet", at = @At("HEAD"), cancellable = true)
	private static void expanse$standsDown(StructureSet structureSet, BiomeSource biomeSource, CallbackInfoReturnable<Boolean> cir) {
		if (!structureSet.structures().isEmpty() && structureSet.structures().stream()
			.allMatch(e -> e.structure().value() instanceof SitedJigsawStructure s && s.standsDown())) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "getPlacementsForStructure", at = @At("HEAD"), cancellable = true)
	private void expanse$noPlacements(Holder<Structure> structure, CallbackInfoReturnable<List<StructurePlacement>> cir) {
		if (structure.value() instanceof SitedJigsawStructure s && s.standsDown()) {
			cir.setReturnValue(List.of());
		}
	}
}
