package dev.voidpvp.client.mixin;

import net.minecraft.client.render.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.nio.FloatBuffer;

/**
 * {@code Camera.MODEL_MATRIX}: the world modelview the game itself reads back once per frame in
 * {@code Camera.update} (MCP's {@code ActiveRenderInfo}). The cosmetics place their bloom and
 * trail billboards from it instead of calling {@code glGetFloat} per player, which on a threaded
 * driver is a pipeline sync costing more than everything else the layer does.
 */
@Mixin(Camera.class)
public interface CameraAccessor {

    @Accessor("MODEL_MATRIX")
    static FloatBuffer void$modelMatrix() {
        throw new AssertionError("mixin");
    }
}
