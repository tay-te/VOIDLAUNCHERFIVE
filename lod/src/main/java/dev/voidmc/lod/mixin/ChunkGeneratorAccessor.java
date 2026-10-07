package dev.voidmc.lod.mixin;

import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeGenerationSettings;
import net.minecraft.world.level.biome.FeatureSorter;
import net.minecraft.world.level.chunk.ChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The generator's features in decoration order and its biomes' settings, for placing the world's trees as it does. */
@Mixin(ChunkGenerator.class)
public interface ChunkGeneratorAccessor {
	@Accessor("featuresPerStep")
	Supplier<List<FeatureSorter.StepFeatureData>> voidLod$featuresPerStep();

	@Accessor("generationSettingsGetter")
	Function<Holder<Biome>, BiomeGenerationSettings> voidLod$generationSettings();
}
