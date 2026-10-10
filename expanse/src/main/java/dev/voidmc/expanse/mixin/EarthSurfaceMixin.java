package dev.voidmc.expanse.mixin;

import dev.voidmc.expanse.world.terrain.SurfaceBands;
import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.material.rule.MaterialRule;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The Earth's surface pass skips the stone no rule can change ({@link SurfaceBands}). The pass belongs to the
 * level's material system, which is not told whose it is, so the noise generator the Earth generator wraps,
 * known by its {@code expanse:earth} settings, marks it before its first chunk. Other worlds' systems are
 * never marked and keep vanilla's pass.
 */
@Mixin(NoiseBasedChunkGenerator.class)
abstract class EarthSurfaceMixin {
	@Shadow
	@Final
	private Holder<NoiseGeneratorSettings> settings;

	@Inject(method = "buildSurface", at = @At("HEAD"))
	private void expanse$earthSurface(ChunkAccess protoChunk, NoiseChunk noiseChunk, RandomState randomState, BiomeManager biomeManager,
		Set<Holder<Biome>> possibleBiomes, MaterialRule materialRule, CallbackInfo ci) {
		if (this.settings.is(SurfaceBands.EARTH)) {
			((SurfaceBands.Earthly) randomState.surfaceSystem()).expanse$markEarth();
		}
	}
}
