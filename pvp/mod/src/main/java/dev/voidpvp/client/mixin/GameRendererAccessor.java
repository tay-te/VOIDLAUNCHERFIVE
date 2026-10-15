package dev.voidpvp.client.mixin;

import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The two private third-person distance fields, read by {@link CosmeticsGameRendererMixin}. */
@Mixin(GameRenderer.class)
public interface GameRendererAccessor {

    @Accessor("thirdPersonDistance")
    float void$thirdPersonDistance();

    @Accessor("lastThirdPersonDistance")
    float void$lastThirdPersonDistance();
}
