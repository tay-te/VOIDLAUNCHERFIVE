package dev.voidmc.mettle.mixin.legend;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ItemCombinerMenu;
import net.minecraft.world.inventory.ResultContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The anvil's slots and smith, for the Mender's Hand. */
@Mixin(ItemCombinerMenu.class)
public interface LegendCombinerAccess {
	@Accessor("inputSlots")
	Container mettle$inputs();

	@Accessor("resultSlots")
	ResultContainer mettle$results();

	@Accessor("player")
	Player mettle$player();
}
