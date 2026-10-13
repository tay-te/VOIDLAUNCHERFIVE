package dev.voidmc.expanse.mixin;

import java.util.function.Supplier;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * The noise generator's sea and lava levels, so the Earth generator's own column scan
 * ({@link dev.voidmc.expanse.world.terrain.NoiseColumnScan}) fills its aquifers from the very same picker.
 */
@Mixin(NoiseBasedChunkGenerator.class)
public interface NoiseBasedChunkGeneratorAccessor {
	@Accessor("globalFluidPicker")
	Supplier<Aquifer.FluidPicker> expanse$globalFluidPicker();
}
