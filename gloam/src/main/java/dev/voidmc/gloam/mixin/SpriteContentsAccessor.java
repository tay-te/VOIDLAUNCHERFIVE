package dev.voidmc.gloam.mixin;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.MipmapStrategy;
import net.minecraft.client.renderer.texture.SpriteContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** A sprite's pixels as loaded and how its mipmaps are made: what the bare winter crowns are drawn from. */
@Mixin(SpriteContents.class)
public interface SpriteContentsAccessor {
	@Accessor("originalImage")
	NativeImage gloam$originalImage();

	@Accessor("byMipLevel")
	NativeImage[] gloam$byMipLevel();

	@Accessor("mipmapStrategy")
	MipmapStrategy gloam$mipmapStrategy();

	@Accessor("alphaCutoffBias")
	float gloam$alphaCutoffBias();
}
