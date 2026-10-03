package dev.voidmc.hearth.economy.coin;

import dev.voidmc.hearth.economy.item.CoinPouchItem;
import dev.voidmc.hearth.economy.item.EconomyItems;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** Coins picked up off the ground go into the first pouch with room (called from the ItemEntity mixin). */
public final class CoinPickup {
	private CoinPickup() {
	}

	/** Moves as much of this coin stack as fits into the player's pouches; returns how many coins went in. */
	public static int intoPouches(Player player, ItemStack coins) {
		Coin coin = EconomyItems.coinOf(coins);
		if (coin == null) {
			return 0;
		}
		Inventory inv = player.getInventory();
		int moved = 0;
		for (int i = 0; i < inv.getContainerSize() && moved < coins.getCount(); i++) {
			ItemStack stack = inv.getItem(i);
			if (EconomyItems.isPouch(stack)) {
				moved += CoinPouchItem.insert(stack, coin, coins.getCount() - moved);
			}
		}
		if (moved > 0) {
			inv.setChanged();
		}
		return moved;
	}
}
