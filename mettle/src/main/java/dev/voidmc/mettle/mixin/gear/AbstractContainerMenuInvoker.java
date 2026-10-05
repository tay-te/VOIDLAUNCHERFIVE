package dev.voidmc.mettle.mixin.gear;

import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** {@code addSlot} for menus whose mixin cannot extend the menu's own superclass. */
@Mixin(AbstractContainerMenu.class)
public interface AbstractContainerMenuInvoker {
	@Invoker("addSlot")
	Slot mettle$addSlot(Slot slot);
}
