package dev.voidmc.hearth.economy;

import dev.voidmc.hearth.api.EconomyService;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.economy.coin.CoinFormat;
import dev.voidmc.hearth.economy.coin.PlayerPurse;
import dev.voidmc.hearth.economy.price.MarketPricer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * {@link EconomyService} for the other modules. Server thread only.
 *
 * <p>{@link #price} prices the whole stack ({@code stack.getCount()} items; one item for a single), which is what a
 * quest paying for "32 stone" wants and avoids rounding each item to a whole bit.
 */
final class EconomyServiceImpl implements EconomyService {
	@Override
	public long balance(ServerPlayer player) {
		return PlayerPurse.balance(player);
	}

	@Override
	public boolean charge(ServerPlayer player, long bits) {
		return PlayerPurse.charge(player, bits);
	}

	@Override
	public void pay(ServerPlayer player, long bits) {
		PlayerPurse.pay(player, bits);
	}

	@Override
	public Component format(long bits) {
		return CoinFormat.format(bits);
	}

	@Override
	public long price(Settlement settlement, ItemStack stack, boolean playerBuys) {
		return new MarketPricer(settlement).price(stack, null, playerBuys);
	}
}
