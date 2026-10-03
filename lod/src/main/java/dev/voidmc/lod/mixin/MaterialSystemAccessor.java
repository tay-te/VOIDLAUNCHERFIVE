package dev.voidmc.lod.mixin;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.material.MaterialSystem;
import net.minecraft.world.level.levelgen.synth.Noise;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The world's own badlands strata, so far canyon walls are striped exactly as the chunks are. */
@Mixin(MaterialSystem.class)
public interface MaterialSystemAccessor {
	@Accessor("clayBands")
	BlockState[] voidLod$clayBands();

	@Accessor("clayBandsOffsetNoise")
	Noise voidLod$clayBandsOffsetNoise();
}
