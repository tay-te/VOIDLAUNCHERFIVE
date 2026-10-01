package dev.voidmc.expanse.mixin;

import com.mojang.datafixers.util.Pair;
import dev.voidmc.expanse.world.biome.BiomePlacement;
import java.util.function.Consumer;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.OverworldBiomeBuilder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Every overworld biome entry passes through {@link BiomePlacement} on its way out of the builder. */
@Mixin(OverworldBiomeBuilder.class)
abstract class OverworldBiomeBuilderMixin {
	@ModifyVariable(method = "addBiomes", at = @At("HEAD"), argsOnly = true)
	private Consumer<Pair<Climate.ParameterPoint, ResourceKey<Biome>>> expanse$placeBiomes(
		Consumer<Pair<Climate.ParameterPoint, ResourceKey<Biome>>> biomes
	) {
		return BiomePlacement.wrap(biomes);
	}
}
