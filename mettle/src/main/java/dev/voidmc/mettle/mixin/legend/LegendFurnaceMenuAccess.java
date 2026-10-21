package dev.voidmc.mettle.mixin.legend;

import net.minecraft.world.Container;
import net.minecraft.world.inventory.AbstractFurnaceMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The furnace behind a furnace menu (the block entity on the server), for the Ever-Ember. */
@Mixin(AbstractFurnaceMenu.class)
public interface LegendFurnaceMenuAccess {
	@Accessor("container")
	Container mettle$container();
}
