package dev.voidmc.expanse.mixin;

import dev.voidmc.expanse.world.terrain.SurfaceBands;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunction;
import net.minecraft.world.level.levelgen.densityfunction.DensitySamplerSet;
import net.minecraft.world.level.levelgen.densityfunction.DensityVolume;
import net.minecraft.world.level.levelgen.material.MaterialRuleContext;
import net.minecraft.world.level.levelgen.material.MaterialRules;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A rule context carries the {@link SurfaceBands} of the Earth's surface pass (set by {@link MaterialSystemMixin};
 * null everywhere else), and with them the ore veins fill their density and richness only up to the highest
 * height a vein can be at, not up to the top of the chunk.
 */
@Mixin(MaterialRuleContext.class)
abstract class MaterialRuleContextMixin implements SurfaceBands.Banded {
	@Shadow
	@Final
	private DensityVolume expectedVolume;

	@Shadow
	@Final
	private DensitySamplerSet densitySamplers;

	@Unique
	private @Nullable SurfaceBands expanse$bands;

	@Override
	public @Nullable SurfaceBands expanse$bands() {
		return this.expanse$bands;
	}

	@Override
	public void expanse$setBands(@Nullable SurfaceBands bands) {
		this.expanse$bands = bands;
	}

	@Inject(method = "getDensitiesInChunk", at = @At("HEAD"), cancellable = true)
	private void expanse$fillOnlyTheVeins(DensityFunction function, boolean prefill, CallbackInfoReturnable<MaterialRules.DensityGetter> cir) {
		SurfaceBands bands = this.expanse$bands;
		if (!prefill || bands == null) {
			return;
		}
		MaterialRules.DensityGetter getter = bands.prefill((MaterialRuleContext) (Object) this, this.expectedVolume, () -> this.densitySamplers.get(function));
		if (getter != null) {
			cir.setReturnValue(getter);
		}
	}
}
