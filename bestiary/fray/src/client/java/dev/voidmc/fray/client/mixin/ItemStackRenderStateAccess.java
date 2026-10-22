package dev.voidmc.fray.client.mixin;

import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.item.ItemDisplayContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** First person's crossfade with vanilla's held item reads an item state's layers and display context. */
@Mixin(ItemStackRenderState.class)
public interface ItemStackRenderStateAccess {
	@Accessor("activeLayerCount")
	int fray$layerCount();

	@Accessor("layers")
	ItemStackRenderState.LayerRenderState[] fray$layers();

	@Accessor("displayContext")
	ItemDisplayContext fray$displayContext();
}
