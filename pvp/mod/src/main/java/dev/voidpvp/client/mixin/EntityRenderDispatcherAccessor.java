package dev.voidpvp.client.mixin;

import net.minecraft.client.render.entity.EntityRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * The dispatcher's render origin — MCP's {@code renderPosX/Y/Z}, private in 1.8.9 — which every
 * entity's draw position is relative to. {@code perf/CullingHooks.renderLabelOnly} needs it to put
 * a culled player's nametag where vanilla would have drawn it.
 */
@Mixin(EntityRenderDispatcher.class)
public interface EntityRenderDispatcherAccessor {

    @Accessor("CAMERA_X")
    double void$cameraX();

    @Accessor("CAMERA_Y")
    double void$cameraY();

    @Accessor("CAMERA_Z")
    double void$cameraZ();
}
