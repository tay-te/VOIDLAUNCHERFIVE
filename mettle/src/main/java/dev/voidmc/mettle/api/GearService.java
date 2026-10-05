package dev.voidmc.mettle.api;

import java.util.List;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** The backpack, belt and quiver worn on the character (BURDEN.md §5–§8). Implemented by the gear module. */
public interface GearService {
	/** What is worn in a slot; empty when nothing is. */
	ItemStack worn(Player player, GearSlot slot);

	/** The stacks inside a worn item's pockets (read-only view). */
	List<ItemStack> pockets(Player player, GearSlot slot);

	boolean isGear(ItemStack stack);
}
