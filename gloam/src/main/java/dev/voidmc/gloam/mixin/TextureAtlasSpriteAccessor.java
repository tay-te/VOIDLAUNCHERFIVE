package dev.voidmc.gloam.mixin;

import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The border a sprite has around it in its atlas (its edge pixels repeated), so a redrawn sprite fills it too. */
@Mixin(TextureAtlasSprite.class)
public interface TextureAtlasSpriteAccessor {
	@Accessor("padding")
	int gloam$padding();
}
