package dev.voidmc.hearth.economy.coin;

import dev.voidmc.hearth.economy.item.CoinPouchItem;
import dev.voidmc.hearth.economy.item.EconomyItems;
import dev.voidmc.hearth.economy.item.PouchContents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Prediction;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * The coin a player carries, read from and written back to their inventory: loose coins in any slot, and coin pouches.
 * Every operation reads a {@link Purse}, decides everything there, and only then writes the slots that changed, so a
 * charge either takes the whole amount or touches nothing.
 */
public final class PlayerPurse {
	/** Main inventory slots (hotbar and the three rows): where change may go into an empty slot. */
	private static final int MAIN_SLOTS = Inventory.INVENTORY_SIZE;

	private PlayerPurse() {
	}

	public static long balance(Player player) {
		return read(player).balance();
	}

	/** Takes {@code bits} from the player, making change; false (and nothing taken) if they cannot pay. */
	public static boolean charge(ServerPlayer player, long bits) {
		if (bits <= 0) {
			return bits == 0;
		}
		Purse purse = read(player);
		long[] overflow = purse.charge(bits);
		if (overflow == null) {
			return false;
		}
		write(player, purse, overflow);
		clink(player, Coin.MARK);
		return true;
	}

	/** Gives the player {@code bits} in the fewest coins: into pouches, onto stacks, into empty slots, the rest at their feet. */
	public static void pay(ServerPlayer player, long bits) {
		if (bits <= 0) {
			return;
		}
		Purse purse = read(player);
		long[] overflow = purse.add(bits);
		write(player, purse, overflow);
		long[] coins = Change.fewest(bits);
		clink(player, coins[Coin.CROWN.ordinal()] > 0 ? Coin.CROWN : coins[Coin.MARK.ordinal()] > 0 ? Coin.MARK : Coin.BIT);
	}

	/** The pickup clink, pitched by metal. Heard by everyone nearby: coin is not quiet. */
	public static void clink(Player player, Coin coin) {
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.CHAIN_STEP, SoundSource.PLAYERS, 0.55F,
			coin.pitch + player.getRandom().nextFloat() * 0.15F);
	}

	static Purse read(Player player) {
		Inventory inv = player.getInventory();
		Purse purse = new Purse(EconomyItems.coin(Coin.BIT).getDefaultMaxStackSize());
		int capacity = CoinPouchItem.capacity();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack stack = inv.getItem(i);
			Coin coin = EconomyItems.coinOf(stack);
			if (coin != null) {
				purse.addSlot(i, coin, stack.getCount());
			} else if (EconomyItems.isPouch(stack)) {
				purse.addPouch(i, CoinPouchItem.contents(stack).counts(), capacity);
			} else if (stack.isEmpty() && i < MAIN_SLOTS) {
				purse.addSlot(i, null, 0);
			}
		}
		return purse;
	}

	static void write(ServerPlayer player, Purse purse, long[] overflow) {
		Inventory inv = player.getInventory();
		for (Purse.Slot slot : purse.slots()) {
			if (slot.changed()) {
				Coin coin = slot.coin();
				inv.setItem(slot.index, coin == null ? ItemStack.EMPTY : new ItemStack(EconomyItems.coin(coin), slot.count()));
			}
		}
		for (Purse.Pouch pouch : purse.pouches()) {
			if (pouch.changed()) {
				CoinPouchItem.setContents(inv.getItem(pouch.index), PouchContents.of(pouch.counts()));
			}
		}
		inv.setChanged();
		for (Coin coin : Coin.VALUES) {
			long n = overflow[coin.ordinal()];
			int max = EconomyItems.coin(coin).getDefaultMaxStackSize();
			while (n > 0) {
				int k = (int) Math.min(n, max);
				player.drop(new ItemStack(EconomyItems.coin(coin), k), false, Prediction.SERVER_ONLY);
				n -= k;
			}
		}
	}
}
