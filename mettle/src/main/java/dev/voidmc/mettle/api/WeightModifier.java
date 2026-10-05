package dev.voidmc.mettle.api;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemInstance;

/**
 * Changes how much one item weighs for one player (Rock Hauler, Armour Training, Light Hand, Coin Sense…). Returns a
 * multiplier on the item's own weight; 1 means unchanged. Runs on both sides (tooltips use it), so it may only read
 * synced state such as the player's perks.
 */
@FunctionalInterface
public interface WeightModifier {
	float multiplier(Player player, ItemInstance stack);
}
