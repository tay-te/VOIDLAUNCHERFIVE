package dev.voidmc.expanse.mixin;

import dev.voidmc.expanse.Expanse;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * No carvers under the Earth terrain: its underground is the cavern model's alone
 * ({@link dev.voidmc.expanse.world.terrain.CavernModel}). Carvers come from the biomes, whose lists are shared
 * by every world, so they are turned off where they run instead: in the noise generator that the Earth
 * generator wraps, known by its {@code expanse:earth} settings. Other worlds carve as vanilla does.
 */
@Mixin(NoiseBasedChunkGenerator.class)
abstract class EarthCarversMixin {
	private static final ResourceKey<NoiseGeneratorSettings> EARTH = ResourceKey.create(Registries.NOISE_SETTINGS, Expanse.id("earth"));

	@Shadow
	@Final
	private Holder<NoiseGeneratorSettings> settings;

	@Inject(method = "generateCarvers", at = @At("HEAD"), cancellable = true)
	private void expanse$noCarversOnEarth(CallbackInfo ci) {
		if (this.settings.is(EARTH)) {
			ci.cancel();
		}
	}
}
