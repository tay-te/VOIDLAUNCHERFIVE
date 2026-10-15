package dev.voidpvp.client.mixin;

import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * {@code EntityRenderer.method_10208} — MCP's {@code renderName(entity, x, y, z)}, the label-only
 * half of an entity's draw, protected in 1.8.9. The culling mod calls it for a player it has hidden
 * behind a wall so the nametag vanilla shows through walls survives the cull.
 */
@Mixin(EntityRenderer.class)
public interface EntityRendererInvoker {

    @Invoker("method_10208")
    void void$renderName(Entity entity, double x, double y, double z);
}
