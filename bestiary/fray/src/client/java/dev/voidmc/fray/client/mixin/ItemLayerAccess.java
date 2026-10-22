package dev.voidmc.fray.client.mixin;

import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.resources.model.cuboid.ItemTransform;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** A layer's display transform (first person vs third person), for the crossfade with vanilla's held item. */
@Mixin(ItemStackRenderState.LayerRenderState.class)
public interface ItemLayerAccess {
	@Accessor("itemTransform")
	ItemTransform fray$itemTransform();

	@Accessor("localTransform")
	Matrix4f fray$localTransform();
}
