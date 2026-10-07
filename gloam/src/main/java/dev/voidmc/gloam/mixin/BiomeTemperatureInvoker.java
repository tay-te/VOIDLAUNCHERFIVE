package dev.voidmc.gloam.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** A biome's temperature at a place, as vanilla works it out for rain or snow: its own, falling with height. */
@Mixin(Biome.class)
public interface BiomeTemperatureInvoker {
	@Invoker("getHeightAdjustedTemperature")
	float gloam$heightAdjustedTemperature(BlockPos pos, int seaLevel);
}
