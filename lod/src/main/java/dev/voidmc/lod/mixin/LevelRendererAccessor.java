package dev.voidmc.lod.mixin;

import com.mojang.renderpearl.api.textures.GpuSampler;
import net.minecraft.client.renderer.LevelRenderer;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Vanilla's sampler for the block atlas on terrain (mipmapped, anisotropic as the options say), so the LOD's
 * textured tiles filter their textures exactly as the chunks next to them do.
 */
@Mixin(LevelRenderer.class)
public interface LevelRendererAccessor {
	@Accessor("chunkLayerSampler")
	@Nullable GpuSampler voidLod$chunkLayerSampler();
}
