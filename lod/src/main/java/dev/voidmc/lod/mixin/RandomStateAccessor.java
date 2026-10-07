package dev.voidmc.lod.mixin;

import net.minecraft.world.level.levelgen.NoiseRouter;
import net.minecraft.world.level.levelgen.RandomState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The world's noise router, for the ground as the chunks lay it ({@code source.NoiseSurface}). */
@Mixin(RandomState.class)
public interface RandomStateAccessor {
	@Accessor("router")
	NoiseRouter voidLod$router();
}
