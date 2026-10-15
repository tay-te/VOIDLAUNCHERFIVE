package dev.voidpvp.client.mixin;

import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

/**
 * Reaches {@code LivingEntityRenderer.features}, which is protected, so the cosmetics layer can be
 * appended to the player renderers from {@link PlayerEntityRendererMixin}. Declared on
 * {@code LivingEntityRenderer}, the class that owns the field (see {@link LivingEntityInvoker} for
 * why that distinction has already cost a wave).
 */
@Mixin(LivingEntityRenderer.class)
public interface LivingEntityRendererAccessor {

    @Accessor("features")
    @SuppressWarnings("rawtypes")
    List<FeatureRenderer> void$features();
}
