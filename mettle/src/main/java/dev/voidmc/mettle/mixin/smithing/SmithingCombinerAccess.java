package dev.voidmc.mettle.mixin.smithing;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ItemCombinerMenu;
import net.minecraft.world.inventory.ResultContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ItemCombinerMenu.class)
public interface SmithingCombinerAccess {
	@Accessor("player")
	Player mettle$smithingPlayer();

	@Accessor("inputSlots")
	Container mettle$smithingInputs();

	@Accessor("resultSlots")
	ResultContainer mettle$smithingResults();
}
