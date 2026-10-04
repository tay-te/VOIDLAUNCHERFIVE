package dev.voidmc.hearth.client.mixin.law;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Where a container screen's panel sits, so the law can write whose chest it is under the title. */
@Mixin(AbstractContainerScreen.class)
public interface ContainerScreenAccessor {
	@Accessor("leftPos")
	int hearth$leftPos();

	@Accessor("topPos")
	int hearth$topPos();

	@Accessor("imageWidth")
	int hearth$imageWidth();
}
