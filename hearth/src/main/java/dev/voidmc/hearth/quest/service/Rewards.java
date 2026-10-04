package dev.voidmc.hearth.quest.service;

import dev.voidmc.hearth.api.EconomyService;
import dev.voidmc.hearth.api.HearthServices;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.quest.QuestConfig;
import dev.voidmc.hearth.quest.model.QuestInstance;
import dev.voidmc.hearth.quest.model.QuestReward;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

/**
 * Paying for quests. Coin comes out of the settlement's treasury when the quest is accepted (held in escrow on the
 * quest, so a town never promises what it cannot pay) and goes to the player on completion: as coin through the
 * {@link EconomyService} when the economy module is present, as emeralds at {@link QuestConfig#bitsPerEmerald} when not.
 * A failed or abandoned quest returns its escrow to the treasury.
 */
public final class Rewards {
	private Rewards() {
	}

	/** Emeralds for a coin reward without the economy module: at least one for any coin at all. */
	public static int emeralds(long bits, int bitsPerEmerald) {
		if (bits <= 0) {
			return 0;
		}
		return (int) Math.max(1, Math.min(64 * 27, Math.round((double) bits / Math.max(1, bitsPerEmerald))));
	}

	/**
	 * Takes the quest's coin from the treasury into escrow; returns what was taken (less than promised if the treasury
	 * has been spent since the offer went up).
	 */
	public static long escrow(Settlement settlement, long bits) {
		if (bits <= 0) {
			return 0;
		}
		long take = Math.min(bits, settlement.treasury());
		if (take > 0 && settlement.withdraw(take)) {
			return take;
		}
		return 0;
	}

	public static void refund(@Nullable Settlement settlement, QuestInstance quest) {
		if (settlement != null && quest.escrow > 0) {
			settlement.deposit(quest.escrow);
			settlement.markDirty();
		}
		quest.escrow = 0;
	}

	/**
	 * Pays a completed quest: the escrowed coin and the items. Returns a line describing it. (Standing moves through the
	 * deed the manager reports, {@link Standings#deed}.)
	 */
	public static Component pay(ServerPlayer player, @Nullable Settlement settlement, QuestInstance quest, QuestConfig config) {
		QuestReward reward = quest.reward;
		MutableComponent line = Component.empty();
		long coin = quest.escrow;
		quest.escrow = 0;
		if (coin > 0) {
			EconomyService economy = HearthServices.economy();
			if (economy != null) {
				economy.pay(player, coin);
				line.append(economy.format(coin));
			} else {
				int n = emeralds(coin, config.bitsPerEmerald());
				giveStacks(player, Items.EMERALD, n);
				line.append(Component.translatable("hearth.quest.reward.emeralds", n));
			}
		}
		for (QuestReward.ItemReward item : reward.items()) {
			Item it = BuiltInRegistries.ITEM.getValue(item.item());
			if (it == Items.AIR || item.count() <= 0) {
				continue;
			}
			giveStacks(player, it, item.count());
			if (!line.getSiblings().isEmpty()) {
				line.append(", ");
			}
			line.append(Component.literal(item.count() + " × ")).append(new ItemStack(it).getHoverName());
		}
		return line;
	}

	/** Whether the player has any coin to hand over (coin and pouches with the economy, emeralds without). */
	public static boolean canPay(ServerPlayer player, QuestConfig config) {
		EconomyService economy = HearthServices.economy();
		if (economy != null) {
			return economy.balance(player) > 0;
		}
		return player.getInventory().countItem(Items.EMERALD) > 0;
	}

	/**
	 * Takes up to {@code bits} from the player for a town's treasury: coin through the economy, or emeralds at
	 * {@link QuestConfig#bitsPerEmerald} without it. Returns the bits taken (0 if the player has nothing).
	 */
	public static long collect(ServerPlayer player, long bits, QuestConfig config) {
		if (bits <= 0) {
			return 0;
		}
		EconomyService economy = HearthServices.economy();
		if (economy != null) {
			long take = Math.min(bits, economy.balance(player));
			return take > 0 && economy.charge(player, take) ? take : 0;
		}
		int per = Math.max(1, config.bitsPerEmerald());
		int carried = player.getInventory().countItem(Items.EMERALD);
		int want = (int) Math.min(carried, (bits + per - 1) / per);
		if (want <= 0) {
			return 0;
		}
		int left = want;
		var inv = player.getInventory();
		for (int i = 0; i < inv.getContainerSize() && left > 0; i++) {
			ItemStack stack = inv.getItem(i);
			if (stack.is(Items.EMERALD)) {
				int n = Math.min(left, stack.getCount());
				stack.shrink(n);
				left -= n;
			}
		}
		return Math.min(bits, (long) (want - left) * per);
	}

	/** How a sum reads: the economy's coin names, or emeralds. */
	public static Component coinText(long bits, QuestConfig config) {
		EconomyService economy = HearthServices.economy();
		return economy != null ? economy.format(bits) : Component.translatable("hearth.quest.reward.emeralds", emeralds(bits, config.bitsPerEmerald()));
	}

	private static void giveStacks(ServerPlayer player, Item item, int count) {
		int max = new ItemStack(item).getMaxStackSize();
		while (count > 0) {
			int n = Math.min(count, max);
			QuestItems.give(player, new ItemStack(item, n));
			count -= n;
		}
	}
}
