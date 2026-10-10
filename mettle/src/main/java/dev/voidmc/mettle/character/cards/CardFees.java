package dev.voidmc.mettle.character.cards;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

/**
 * How a rebuild's fee is paid (docs/CARDS.md §7.1): in Hearth coin when Hearth's economy is there (to a trainer's till or
 * purse, or burned by a shrine), else in emeralds from the player's inventory. compat.hearth sets the Hearth one.
 */
public interface CardFees {
	/** Whether the player can pay now. {@code payee} is what the sitting carries (a trainer), or null (a shrine burns it). */
	boolean canPay(ServerPlayer player, long bits, int emeralds, @Nullable Object payee);

	/** Takes the fee; false (and nothing taken) when they can't. */
	boolean pay(ServerPlayer player, long bits, int emeralds, @Nullable Object payee);

	/** "120 bits" or "3 emeralds". */
	Component describe(long bits, int emeralds);

	/** Whether fees are in coin (the overlay says bits) rather than emeralds. */
	boolean coin();

	/** Without Hearth: emeralds from the inventory. */
	CardFees EMERALDS = new CardFees() {
		@Override
		public boolean canPay(ServerPlayer player, long bits, int emeralds, @Nullable Object payee) {
			return player.hasInfiniteMaterials() || count(player) >= emeralds;
		}

		@Override
		public boolean pay(ServerPlayer player, long bits, int emeralds, @Nullable Object payee) {
			if (!canPay(player, bits, emeralds, payee)) {
				return false;
			}
			if (player.hasInfiniteMaterials()) {
				return true;
			}
			int left = emeralds;
			for (int i = 0; i < player.getInventory().getContainerSize() && left > 0; i++) {
				ItemStack s = player.getInventory().getItem(i);
				if (s.is(Items.EMERALD)) {
					int take = Math.min(left, s.getCount());
					s.shrink(take);
					left -= take;
				}
			}
			player.containerMenu.broadcastChanges();
			return true;
		}

		@Override
		public Component describe(long bits, int emeralds) {
			return Component.translatable(emeralds == 1 ? "mettle.cards.fee.emerald" : "mettle.cards.fee.emeralds", emeralds);
		}

		@Override
		public boolean coin() {
			return false;
		}

		private int count(ServerPlayer player) {
			int n = 0;
			for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
				ItemStack s = player.getInventory().getItem(i);
				if (s.is(Items.EMERALD)) {
					n += s.getCount();
				}
			}
			return n;
		}
	};
}
