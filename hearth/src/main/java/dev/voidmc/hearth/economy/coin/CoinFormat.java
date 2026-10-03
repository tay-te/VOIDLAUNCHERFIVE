package dev.voidmc.hearth.economy.coin;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** Amounts as words: "2 crowns, 3 marks, 4 bits", from the lang file so it reads right in every language. */
public final class CoinFormat {
	private CoinFormat() {
	}

	/** {@code bits} in the fewest coins, largest first; "0 bits" for nothing. Negative amounts get a minus sign. */
	public static MutableComponent format(long bits) {
		if (bits < 0) {
			return Component.translatable("hearth.economy.coin.negative", format(-bits));
		}
		long[] counts = Change.fewest(bits);
		List<Component> parts = new ArrayList<>(3);
		for (Coin coin : Coin.DESCENDING) {
			long n = counts[coin.ordinal()];
			if (n > 0) {
				parts.add(amount(coin, n));
			}
		}
		if (parts.isEmpty()) {
			return amount(Coin.BIT, 0);
		}
		MutableComponent out = parts.getFirst().copy();
		for (int i = 1; i < parts.size(); i++) {
			out = Component.translatable("hearth.economy.coin.join", out, parts.get(i));
		}
		return out;
	}

	/** "3 marks". */
	public static MutableComponent amount(Coin coin, long count) {
		return Component.translatable(coin.amountKey(count), count);
	}
}
